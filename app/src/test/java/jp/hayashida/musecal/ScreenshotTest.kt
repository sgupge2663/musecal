package jp.hayashida.musecal

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * EM-1: JVM スクショテスト（Roborazzi + Robolectric、エミュレータなし）。
 *
 * ベースラインは app/src/test/screenshots/ に置き git 管理する。
 * 更新は `./gradlew recordRoborazziDebug`、検証は通常の `testDebugUnitTest`
 * （check.sh / CI）でベースラインと比較され、差分があれば失敗する。
 * 手順の正本は docs/qc.md の「スクショテスト（EM-1）」節。
 *
 * 前提を固定して決定的にする:
 * - 画面サイズ・密度: w360dp-h800dp-xxhdpi（1080x2400px 相当）
 * - 「今日」: 2026-12-16（水）に固定（注入した日付から初期表示月も12月になる）
 * - タイムゾーン: Asia/Tokyo（サンプル予定の +09:00 をそのまま解釈する）
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
class ScreenshotTest {
    private var originalZone = java.util.TimeZone.getDefault()

    @org.junit.Before fun fixTimezone() {
        originalZone = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Tokyo"))
    }

    @org.junit.After fun restoreTimezone() {
        java.util.TimeZone.setDefault(originalZone)
    }


    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val fixedToday = LocalDate.of(2026, 12, 16)

    private fun setAppContent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val events = loadEvents(context, ZoneId.of("Asia/Tokyo"))
        val todayState = mutableStateOf(fixedToday)
        composeRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                // V1-1 統合後のシグネチャ: サインイン済み前提の固定ダミーで描画する
                MuseCalApp(
                    events, todayState,
                    accountEmail = "screenshot@example.jp",
                    onSignOut = {},
                    // V1-2: 実物の calendarList（2026-11-21 取得）と同じ3件を対応づけたタブ
                    tabs = mapCalendarTabs(
                        listOf(
                            CalendarListEntry("kou1995yurin530@gmail.com", "kou1995yurin530@gmail.com", backgroundColor = "#9fe1e7", primary = true),
                            CalendarListEntry("mokuyoukai.ai@gmail.com", "AI木曜会", backgroundColor = "#fad165"),
                            CalendarListEntry("ja.japanese#holiday@group.v.calendar.google.com", "日本の祝日", backgroundColor = "#9a9cff"),
                        )
                    ),
                    // V1-4a: 今すぐ同期ボタン。最終取得日時の表示も固定するため、取得済み（12/16 09:41）の状態で描く
                    sync = SyncUi(syncing = false, fetchedAt = java.time.Instant.parse("2026-12-16T00:41:00Z"), enabled = true, onSyncNow = {}),
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun initialMonth_usesInjectedToday_andDoesNotResetOnTodayChange() {
        val today = mutableStateOf(LocalDate.of(2026, 11, 22))
        composeRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                MuseCalApp(emptyList(), today, "test@example.jp", {}, emptyList())
            }
        }
        composeRule.onNodeWithText("2026年11月").assertIsDisplayed()
        composeRule.onNodeWithText("›").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("2026年12月").assertIsDisplayed()
        composeRule.runOnIdle {
            today.value = LocalDate.of(2027, 1, 1)
        }
        composeRule.onNodeWithText("2026年12月").assertIsDisplayed()
        composeRule.onNodeWithText("今日").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("2027年1月").assertIsDisplayed()
    }

    @Test
    fun monthView_dec2026() {
        setAppContent()
        composeRule.onRoot().captureRoboImage(File("src/test/screenshots/month_view.png"))
    }

    /** EM-1 3画面目: 未ログイン案内画面（V1-1 の SignInScreen、サインイン前の初期状態）。 */
    @Test
    fun signInGuide_signedOut() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val auth = CalendarAuth(context)
        composeRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                SignInScreen(auth) {}
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(File("src/test/screenshots/sign_in_guide.png"))
    }


    @Test
    fun signedOutRoot_hasOnlyGuide() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val auth = CalendarAuth(context)
        composeRule.setContent {
            MuseCalRoot(auth, androidx.compose.runtime.remember { mutableStateOf(fixedToday) })
        }
        composeRule.onNodeWithText("Google でログイン").assertExists()
        composeRule.onAllNodes(hasScrollAction()).assertCountEquals(0)
        composeRule.onNodeWithText("サインアウト").assertDoesNotExist()
        composeRule.onNodeWithText("今日").assertDoesNotExist()
    }

    private fun setFailure(cached: Boolean, retrying: Boolean = false, onRetry: () -> Unit = {}) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // 予定を渡しても、取得成功の記録がなければグリッドを描かない。
        val events = loadEvents(context, ZoneId.of("Asia/Tokyo"))
        composeRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                MuseCalApp(
                    events, androidx.compose.runtime.remember { mutableStateOf(fixedToday) }, "screenshot@example.jp", {}, emptyList(),
                    failure = FetchFailureUi(
                        "通信エラーで取得できませんでした。通信環境を確認して再試行してください。",
                        if (cached) java.time.Instant.parse("2026-12-16T00:41:00Z") else null,
                        retrying, onRetry,
                    ),
                )
            }
        }
    }

    @Test
    fun failureWithoutCache_replacesMonth_andRetries() {
        var retries = 0
        setFailure(false) { retries++ }
        composeRule.onNodeWithText("予定はまだ取得できていません。").assertExists()
        composeRule.onAllNodes(hasScrollAction()).assertCountEquals(0)
        composeRule.onNodeWithText("再試行").performClick()
        org.junit.Assert.assertEquals(1, retries)
        composeRule.onRoot().captureRoboImage(File("src/test/screenshots/fetch_error_empty.png"))
    }

    @Test
    fun failureWithCache_keepsMonth_andWarnsOfAge() {
        setFailure(true)
        composeRule.onNodeWithText("最終取得 12/16 09:41 の予定を表示しています。最新ではない可能性があります。").assertExists()
        org.junit.Assert.assertTrue(composeRule.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("再試行").assertIsEnabled()
        composeRule.onRoot().captureRoboImage(File("src/test/screenshots/fetch_error_cached.png"))
    }

    @Test
    fun retryInProgress_disablesRetry() {
        setFailure(false, true)
        composeRule.onNodeWithText("再試行中…").assertIsNotEnabled()
    }

    @Test
    fun dayPopup_dec16() {
        setAppContent()
        // 月グリッド（LazyColumn）はスクロール可能なノードのうち最も背が高いもの。
        // 下部タブの横スクロールと区別するために高さで選ぶ。
        val scrollables = composeRule.onAllNodes(hasScrollAction())
        val heights = scrollables.fetchSemanticsNodes().map { it.boundsInRoot.height }
        val gridIndex = heights.indices.maxByOrNull { heights[it] } ?: error("月グリッドが見つからない")
        // 2026-12-16 は水曜（列 3）。表示週は 11/29 開始が 0 行目、12/13 開始が 2 行目。
        scrollables[gridIndex].performTouchInput {
            click(Offset(width * (3.5f / 7f), height * (2.5f / 6f)))
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(isDialog()).captureRoboImage(File("src/test/screenshots/day_popup_dec16.png"))
    }
}
