package jp.hayashida.musecal

import android.content.Context

/** 表示 OFF の ID だけを保持。未知の ID は ON、一覧から消えた ID は表示に影響しない。 */
internal class CalendarVisibilityPreferences(context: Context) {
    // 認証の signOut().clear() とイベントキャッシュの双方から独立した端末設定。
    private val prefs = context.applicationContext.getSharedPreferences(
        "calendar_visibility", Context.MODE_PRIVATE,
    )

    fun loadHidden(accountEmail: String): Set<String> =
        prefs.getStringSet(key(accountEmail), emptySet())!!.toSet()

    fun saveHidden(accountEmail: String, hidden: Set<String>) {
        prefs.edit().putStringSet(key(accountEmail), hidden.toSet()).apply()
    }

    private fun key(accountEmail: String) = "hidden:$accountEmail"
}
