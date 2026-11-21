package jp.hayashida.musecal

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.printToString
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File

/**
 * EM-2: エミュレータ上の各ステップの証拠を残す。
 *
 * 1ステップにつき次の3つを `<アプリの filesDir>/em2/<シナリオ名>/` に書く。
 * ワークフロー（.github/workflows/emulator.yml）が `run-as` で取り出して成果物に上げる。
 * - `NN_<step>.png` … 端末の画面そのもの（UiAutomation のスクリーンショット。ダイアログも含む）
 * - `NN_<step>.xml` … UI Automator の画面構造ダンプ（uiautomator dump と同じ形式）
 * - `NN_<step>_semantics.txt` … Compose のセマンティクスツリー（未マージ）
 */
class Evidence(private val scenario: String, private val compose: ComposeTestRule) {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private var seq = 0

    val dir: File = File(instrumentation.targetContext.filesDir, "em2/$scenario").apply {
        deleteRecursively()
        mkdirs()
    }

    /** 判定に使った数値などを notes.txt に1行ずつ残す。 */
    fun note(line: String) {
        File(dir, "notes.txt").appendText(line + "\n")
    }

    /** 現在の画面を撮る。返り値はスクリーンショット（前後比較の判定に使う）。 */
    fun capture(step: String): Bitmap {
        compose.waitForIdle()
        device.waitForIdle()
        val name = "%02d_%s".format(++seq, step)
        // HARDWARE 形式で返る版があるため、画素を読める ARGB_8888 に写す
        val shot = (instrumentation.uiAutomation.takeScreenshot()
            ?: error("スクリーンショットを取得できない: $name")).copy(Bitmap.Config.ARGB_8888, false)
        File(dir, "$name.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(dir, "$name.xml").outputStream().use { device.dumpWindowHierarchy(it) }
        val semantics = runCatching {
            // ダイアログ表示中はルートが2つ（本体＋ダイアログ）になるので、全ルートを出す
            compose.onAllNodes(isRoot(), useUnmergedTree = true).printToString(maxDepth = Int.MAX_VALUE)
        }.getOrElse { "（セマンティクスを取得できない: ${it.message}）" }
        File(dir, "${name}_semantics.txt").writeText(semantics)
        return shot
    }
}

/**
 * 2枚の画面の画素が異なる割合（0〜1）。サイズが違えば 1。
 * 上端の [skipTopFraction]（ステータスバーの時計など、操作と無関係に変わる部分）は比べない。
 */
fun diffRatio(a: Bitmap, b: Bitmap, skipTopFraction: Float = 0.06f): Double {
    if (a.width != b.width || a.height != b.height) return 1.0
    val w = a.width
    val top = (a.height * skipTopFraction).toInt()
    val h = a.height - top
    val pa = IntArray(w * h).also { a.getPixels(it, 0, w, 0, top, w, h) }
    val pb = IntArray(w * h).also { b.getPixels(it, 0, w, 0, top, w, h) }
    var diff = 0
    for (i in pa.indices) if (pa[i] != pb[i]) diff++
    return diff.toDouble() / pa.size
}
