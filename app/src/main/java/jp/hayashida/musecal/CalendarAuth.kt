package jp.hayashida.musecal

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.google.android.gms.auth.api.identity.AuthorizationRequest as GmsAuthorizationRequest

/**
 * V1-1 ログイン基盤。Google 認証まわりはこのクラスに集約し、画面側は状態と結果だけを見る。
 *
 * 方式（Android の現在の標準）:
 * - サインイン状態の確立: Credential Manager（androidx.credentials）＋ GetGoogleIdOption で
 *   Google ID トークンを取得する。serverClientId には Google Cloud 側の
 *   「ウェブアプリケーション」タイプの OAuth クライアント ID（R.string.google_web_client_id）が要る。
 * - Calendar API のアクセス権: Play services auth の AuthorizationClient で
 *   scope [CALENDAR_EVENTS_SCOPE] のみを要求する。アクセストークンは永続化せず、
 *   V1-3 以降が API を呼ぶたびに [authorizeCalendar] で取り直す（認可済みなら無言で返る）。
 *
 * 永続化するのはサインインしたアカウントのメールアドレスと表示名のみ（アプリ内 SharedPreferences）。
 * ID トークン自体は有効期限が短いため保存しない。再起動後に月表示へ入れるのは
 * 「アカウント選択済み」という状態の復元であり、API アクセスは都度 AuthorizationClient が担う。
 */
class CalendarAuth(context: Context) {

    data class SignedInAccount(val email: String, val displayName: String?)

    /** Calendar 認可の結果。トークンは呼び出し側でその場だけ使い、保存しない。 */
    sealed interface CalendarAccess {
        data class Granted(val accessToken: String) : CalendarAccess
        data class ConsentRequired(val intentSender: IntentSender) : CalendarAccess
        data class Failed(val error: Throwable) : CalendarAccess
    }

    private val appContext = context.applicationContext
    private val credentialManager = CredentialManager.create(appContext)
    private val authorizationClient = Identity.getAuthorizationClient(appContext)
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _account = MutableStateFlow(loadPersistedAccount())
    /** サインイン中アカウント。未サインインなら null。 */
    val account: StateFlow<SignedInAccount?> = _account.asStateFlow()

    private fun loadPersistedAccount(): SignedInAccount? {
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        return SignedInAccount(email, prefs.getString(KEY_DISPLAY_NAME, null))
    }

    /**
     * Credential Manager で Google サインインする。アカウント選択 UI が出る。
     * ユーザーがキャンセルした場合は Result.failure（[GetCredentialCancellationException]）。
     */
    suspend fun signIn(activity: Activity): Result<SignedInAccount> {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(appContext.getString(R.string.google_web_client_id))
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        return try {
            val response = credentialManager.getCredential(activity, request)
            val credential = response.credential
            val googleCredential = when {
                credential is GoogleIdTokenCredential -> credential
                credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ->
                    GoogleIdTokenCredential.createFrom(credential.data)
                else -> return Result.failure(
                    IllegalStateException("Google ID トークンではない応答でした: ${credential.type}")
                )
            }
            // GoogleIdTokenCredential.id はアカウントのメールアドレス。
            val account = SignedInAccount(
                email = googleCredential.id,
                displayName = googleCredential.displayName,
            )
            prefs.edit()
                .putString(KEY_EMAIL, account.email)
                .putString(KEY_DISPLAY_NAME, account.displayName)
                .apply()
            _account.value = account
            Result.success(account)
        } catch (e: GetCredentialException) {
            Result.failure(e)
        }
    }

    /** サインアウト。Credential Manager の状態と永続化したアカウント情報を消す。 */
    suspend fun signOut() {
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
        prefs.edit().clear().apply()
        _account.value = null
    }

    /**
     * Calendar 予定の読み書き権限の認可を得る。認可済みなら [CalendarAccess.Granted] が即返る。
     * 同意画面が必要な場合は [CalendarAccess.ConsentRequired] を返すので、呼び出し側が
     * IntentSender を起動し、結果を [authorizationResultFromIntent] で受け取ること。
     */
    suspend fun authorizeCalendar(): CalendarAccess {
        val request = GmsAuthorizationRequest.builder()
            .setRequestedScopes(requestedScopes())
            .build()
        return try {
            val result = authorizationClient.authorize(request).await()
            calendarAccessFromResult(
                result.accessToken,
                if (result.hasResolution()) result.pendingIntent?.intentSender else null,
            )
        } catch (e: Exception) {
            CalendarAccess.Failed(e)
        }
    }

    /** 同意画面の結果 Intent から認可結果を取り出す。アクセストークンがあればそれを返す。 */
    fun authorizationResultFromIntent(data: Intent): AuthorizationResult =
        authorizationClient.getAuthorizationResultFromIntent(data)

    companion object {
        /** 要求する権限は Calendar 予定の読み書きのみ（V2-1）。 */
        const val CALENDAR_EVENTS_SCOPE = "https://www.googleapis.com/auth/calendar.events"

        /**
         * AuthorizationClient へ実際に要求する scope の集合（URI の一覧）。
         * 認可要求は必ずこの一覧から組み立てる（CalendarAuthTest がこの一覧そのものを検証する）。
         */
        val REQUESTED_SCOPE_URIS: List<String> = listOf(CALENDAR_EVENTS_SCOPE)

        /** [REQUESTED_SCOPE_URIS] を AuthorizationClient 用の [Scope] 一覧に変換する（1対1のラップのみ）。 */
        fun requestedScopes(): List<Scope> = REQUESTED_SCOPE_URIS.map(::Scope)

        /** SDK の応答をアプリの状態へ変換する。トークン優先の既存判定を維持する。 */
        internal fun calendarAccessFromResult(token: String?, consent: IntentSender?): CalendarAccess = when {
            token != null -> CalendarAccess.Granted(token)
            consent != null -> CalendarAccess.ConsentRequired(consent)
            else -> CalendarAccess.Failed(
                IllegalStateException("認可結果にアクセストークンも同意要求も含まれていません")
            )
        }

        private const val PREFS_NAME = "calendar_auth"
        private const val KEY_EMAIL = "email"
        private const val KEY_DISPLAY_NAME = "display_name"
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result -> if (!cont.isCompleted) cont.resume(result) }
    addOnFailureListener { e -> if (!cont.isCompleted) cont.resumeWithException(e) }
    addOnCanceledListener { if (!cont.isCompleted) cont.cancel() }
}
