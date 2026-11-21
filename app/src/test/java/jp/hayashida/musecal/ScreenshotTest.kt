package jp.hayashida.musecal

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
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
 * - 「今日」: 2026-12-16（水）に固定（初期表示月も 2026年12月でコード側固定）
 * - タイムゾーン: Asia/Tokyo（サンプル予定の +09:00 をそのまま解釈する）
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val fixedToday = LocalDate.of(2026, 12, 16)

    private fun setAppContent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val events = loadEvents(context, ZoneId.of("Asia/Tokyo"))
        val todayState = mutableStateOf(fixedToday)
        composeRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                MuseCalApp(events, todayState)
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun monthView_dec2026() {
        setAppContent()
        composeRule.onRoot().captureRoboImage(File("src/test/screenshots/month_view.png"))
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
