package jp.hayashida.musecal

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/**
 * EM-2: 実エミュレータ上の操作シナリオ（月表示側）。
 *
 * 実アプリは未サインインだと案内画面しか出さず（MuseCalRoot）、実アカウントは使わない方針なので、
 * 月表示は EM-1 の ScreenshotTest と同じ前提（サンプル予定・固定の「今日」・既存3タブ）で
 * MuseCalApp を直接描画し、その上で実際にタップ・スクロールする。
 * 未サインインの実アプリ起動は [SignedOutLaunchTest] が確かめる。
 */
@RunWith(AndroidJUnit4::class)
class MonthViewScenarioTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val fixedToday = LocalDate.of(2026, 12, 16)

    @Test
    fun scenario() {
        val ev = Evidence("month_view", compose)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val events = loadEvents(context, ZoneId.of("Asia/Tokyo"))
        // 既存3タブの正本（KNOWN_TABS）から calendarList 相当を作る（色は fallback をそのまま使う）
        val tabs = mapCalendarTabs(KNOWN_TABS.map { CalendarListEntry(it.calendarId, it.label) })
        val todayState = mutableStateOf(fixedToday)
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                MuseCalApp(
                    events, todayState,
                    accountEmail = "emulator@example.jp",
                    onSignOut = {},
                    tabs = tabs,
                )
            }
        }

        // 1. 初期表示: 2026年12月
        compose.onNodeWithText("2026年12月").assertIsDisplayed()
        val initial = ev.capture("month_initial")

        // 2. タブ切替: 「メイン」を押して非表示にする → 予定の描画が変わる
        compose.onNodeWithText("メイン").performClick()
        val mainHidden = ev.capture("tab_main_hidden")
        val tabDiff = diffRatio(initial, mainHidden)
        ev.note("diff(month_initial, tab_main_hidden) = $tabDiff")
        assertTrue("タブ切替で画面が変わらない (diff=$tabDiff)", tabDiff > 0.005)

        // 3. もう一度押して戻す → 初期表示と同じ描画に戻る
        compose.onNodeWithText("メイン").performClick()
        val mainShown = ev.capture("tab_main_shown")
        val backDiff = diffRatio(initial, mainShown)
        ev.note("diff(month_initial, tab_main_shown) = $backDiff")
        assertTrue("タブを戻しても初期表示に戻らない (diff=$backDiff, 切替時=$tabDiff)", backDiff < tabDiff / 4)

        // 4. 日タップ → 日ポップアップ（2026-12-16 水曜 = 列3、12/13 始まりの週 = 2行目）
        grid().performTouchInput { click(Offset(width * (3.5f / 7f), height * (2.5f / 6f))) }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(isDialog()).assertIsDisplayed()
        ev.capture("day_popup_dec16")

        // 5. 戻るキーでポップアップを閉じる
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty()
        }
        ev.capture("popup_closed")

        // 6. 月表示を上へスクロール（先の週へ） → 見出しの月が進む
        repeat(2) { grid().performTouchInput { swipeUp() } }
        compose.waitForIdle()
        val scrolledUp = ev.capture("scrolled_forward")
        assertTrue(
            "上スクロールで見出しが 2026年12月 のまま",
            compose.onAllNodes(hasText("2026年12月")).fetchSemanticsNodes().isEmpty(),
        )

        // 7. 下へスクロールして戻る（前の週へ）
        repeat(2) { grid().performTouchInput { swipeDown() } }
        compose.waitForIdle()
        val scrolledDown = ev.capture("scrolled_back")
        val scrollDiff = diffRatio(scrolledUp, scrolledDown)
        ev.note("diff(scrolled_forward, scrolled_back) = $scrollDiff")
        assertTrue("下スクロールで画面が変わらない (diff=$scrollDiff)", scrollDiff > 0.005)
    }

    /** 月グリッド（LazyColumn）= スクロール可能なノードのうち最も背が高いもの（下部タブの横スクロールと区別）。 */
    private fun grid() = compose.onAllNodes(hasScrollAction()).let { nodes ->
        val heights = nodes.fetchSemanticsNodes().map { it.boundsInRoot.height }
        nodes[heights.indices.maxByOrNull { heights[it] } ?: error("月グリッドが見つからない")]
    }
}

/**
 * EM-2: 実アプリ（MainActivity）を未サインインで起動し、案内画面だけが出ることを確かめる。
 * サインインはしない（ボタンは押さない）。前提: ワークフローが `pm clear` でアプリのデータを消してから実行する。
 */
@RunWith(AndroidJUnit4::class)
class SignedOutLaunchTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun signInGuideShown() {
        val ev = Evidence("signed_out", compose)
        compose.onNodeWithText("Google でログイン").assertIsDisplayed()
        compose.onNodeWithText("予定を表示するには", substring = true).assertIsDisplayed()
        // 未サインインでは月表示（サインアウト帯・月見出し）を出さない
        assertTrue(
            "未サインインなのにサインアウトが表示されている",
            compose.onAllNodes(hasText("サインアウト")).fetchSemanticsNodes().isEmpty(),
        )
        ev.capture("sign_in_guide")
    }
}
