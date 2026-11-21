package jp.hayashida.musecal

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T-108: textShade の変換値を固定する。
 * 定義： HSL で色相・彩度を保持し、明るい背景では L' = L × 0.72、
 * 暗い背景（選択セルの黒）では L' = 1 − (1 − L) × 0.72。
 * 期待値はパレット原色から同定義で計算した値。
 */
class TextShadeTest {

    @Test
    fun tomatoOnWhite() {
        // colorId 11 #DC2127 (H=358.1, S=0.739, L=0.496 -> 0.357)
        assertEquals(Color(0xFF9E181C), textShade(Color(0xFFDC2127), Color.White))
    }

    @Test
    fun bananaOnWhite() {
        // colorId 5 #FBD75B (H=46.5, S=0.952, L=0.671 -> 0.483)
        assertEquals(Color(0xFFF0BC06), textShade(Color(0xFFFBD75B), Color.White))
    }

    @Test
    fun tangerineOnWhite() {
        // colorId 6 #FFB878 (H=28.4, S=1.000, L=0.735 -> 0.529)
        assertEquals(Color(0xFFFF810F), textShade(Color(0xFFFFB878), Color.White))
    }

    @Test
    fun blueberryOnWhite() {
        // colorId 9 #5484ED (H=221.2, S=0.810, L=0.629 -> 0.453)
        assertEquals(Color(0xFF1651D1), textShade(Color(0xFF5484ED), Color.White))
    }

    @Test
    fun bananaOnBlack() {
        // 暗い背景では対称変換 (L=0.671 -> 0.763)
        assertEquals(Color(0xFFFCE289), textShade(Color(0xFFFBD75B), Color.Black))
    }

    @Test
    fun tomatoOnBlack() {
        // 暗い背景では対称変換 (L=0.496 -> 0.637)
        assertEquals(Color(0xFFE75E62), textShade(Color(0xFFDC2127), Color.Black))
    }
}
