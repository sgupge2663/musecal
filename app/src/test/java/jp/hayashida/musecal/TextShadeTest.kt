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

    @Test
    fun blackOnWhite() {
        // #000000 (S=0, L=0): L' = 0 × 0.72 = 0 -> RGB=0 (#000000)
        assertEquals(Color(0xFF000000), textShade(Color.Black, Color.White))
    }

    @Test
    fun blackOnBlack() {
        // #000000 (S=0, L=0): L' = 1 − (1 − 0) × 0.72 = 0.28
        // S=0 なので RGB = round(0.28 × 255) = 71 (#474747)。色相は不定。
        assertEquals(Color(0xFF474747), textShade(Color.Black, Color.Black))
    }

    @Test
    fun whiteOnWhite() {
        // #FFFFFF (S=0, L=1): L' = 1 × 0.72 = 0.72
        // S=0 なので RGB = round(0.72 × 255) = 184 (#B8B8B8)。色相は不定。
        assertEquals(Color(0xFFB8B8B8), textShade(Color.White, Color.White))
    }

    @Test
    fun whiteOnBlack() {
        // #FFFFFF (S=0, L=1): L' = 1 − (1 − 1) × 0.72 = 1 -> RGB=255 (#FFFFFF)
        assertEquals(Color(0xFFFFFFFF), textShade(Color.White, Color.Black))
    }

    @Test
    fun grayOnWhite() {
        // 中間グレー #808080 (S=0, L=128/255): L' = (128/255) × 0.72
        // S=0 なので RGB = round(128 × 0.72) = round(92.16) = 92 (#5C5C5C)。色相は不定。
        assertEquals(Color(0xFF5C5C5C), textShade(Color(0xFF808080), Color.White))
    }

    @Test
    fun grayOnBlack() {
        // 中間グレー #808080 (S=0, L=128/255): L' = 1 − (1 − 128/255) × 0.72
        // S=0 なので RGB = round(255 − 127 × 0.72) = round(163.56) = 164 (#A4A4A4)。色相は不定。
        assertEquals(Color(0xFFA4A4A4), textShade(Color(0xFF808080), Color.Black))
    }
}
