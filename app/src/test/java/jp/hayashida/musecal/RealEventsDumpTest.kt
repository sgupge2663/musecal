package jp.hayashida.musecal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId

/**
 * V1-3 の照合用（通常の check.sh / CI では何もしない）。
 *
 * 環境変数 MUSECAL_REAL_EVENTS_DIR に events.list の実レスポンス（main.json / mokuyou.json / holiday.json。
 * `hatch_gws_cli calendar events list` の出力）を置いたフォルダを指定すると、アプリの変換結果を
 * 1予定1行の TSV（カレンダー、開始日、終了日、終日/時間、開始時刻、タイトル、帯の色）で
 * そのフォルダの app_summary.tsv に書き出す。独立に作った期待値と diff して照合する。
 * 実データは個人の予定なので、リポジトリには置かない（work/ など Git 管理外に置く）。
 */
class RealEventsDumpTest {
    @Test
    fun dumpRealEventsSummary() {
        val dir = System.getenv("MUSECAL_REAL_EVENTS_DIR")
        assumeTrue("MUSECAL_REAL_EVENTS_DIR 未設定のため省略", !dir.isNullOrBlank())
        val zone = ZoneId.of("Asia/Tokyo")
        val tabs = mapOf(
            "main" to CalendarTab("kou1995yurin530@gmail.com", "メイン", Color(0xFF9FE1E7)),
            "mokuyou" to CalendarTab("mokuyoukai.ai@gmail.com", "AI木曜会", Color(0xFFFAD165)),
            "holiday" to CalendarTab("ja.japanese#holiday@group.v.calendar.google.com", "日本の祝日", Color(0xFF9A9CFF)),
        )
        val lines = tabs.flatMap { (file, tab) ->
            parseEventsPage(File(dir, "$file.json").readText(), tab, zone).first.map { e ->
                listOf(
                    tab.label, e.startDate, e.endDateInclusive, if (e.allDay) "終日" else "時間",
                    e.start?.toLocalTime() ?: "", e.title,
                    "#%06x".format(e.baseColor.toArgb() and 0xFFFFFF),
                ).joinToString("\t")
            }
        }
        File(dir, "app_summary.tsv").writeText(lines.sorted().joinToString("\n", postfix = "\n"))
    }
}
