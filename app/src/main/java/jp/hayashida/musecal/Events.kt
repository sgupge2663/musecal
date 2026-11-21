package jp.hayashida.musecal

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

/** Google Calendar 標準イベント色（colorId 1〜11）。 */
val GoogleEventColors: Map<String, Color> = mapOf(
    "1" to Color(0xFFA4BDFC), "2" to Color(0xFF7AE7BF), "3" to Color(0xFFDBADFF),
    "4" to Color(0xFFFF887C), "5" to Color(0xFFFBD75B), "6" to Color(0xFFFFB878),
    "7" to Color(0xFF46D6DB), "8" to Color(0xFFE1E1E1), "9" to Color(0xFF5484ED),
    "10" to Color(0xFF51B749), "11" to Color(0xFFDC2127),
)

data class CalEvent(
    val title: String,
    val allDay: Boolean,
    /** 表示上の開始日・終了日（両端含む、端末のタイムゾーン）。 */
    val startDate: LocalDate,
    val endDateInclusive: LocalDate,
    /** 時間指定のみ。 */
    val start: ZonedDateTime?,
    val end: ZonedDateTime?,
    val colorId: String?,
    val calendarName: String,
    val calendarColor: Color,
    val location: String,
) {
    /** 終日、または日をまたぐ時間指定 → 色帯で描く。 */
    val isBar: Boolean get() = allDay || endDateInclusive > startDate

    /** 帯の色・文字色の元：colorId があればその色、無ければカレンダー色。 */
    val baseColor: Color get() = colorId?.let { GoogleEventColors[it] } ?: calendarColor
}

private fun JSONObject.str(name: String): String? =
    if (!has(name) || isNull(name)) null else getString(name).ifEmpty { null }

private fun parseHex(hex: String?, fallback: Color): Color =
    try {
        if (hex == null) fallback else Color(android.graphics.Color.parseColor(hex))
    } catch (_: IllegalArgumentException) {
        fallback
    }

fun loadEvents(context: Context, zone: ZoneId = ZoneId.systemDefault()): List<CalEvent> {
    val text = context.assets.open("sample_events.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
    val arr = JSONArray(text)
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val allDay = o.optBoolean("allDay", false)
        val calColor = parseHex(o.str("calendarColor"), Color(0xFF9E9E9E))
        val common = { s: LocalDate, e: LocalDate, zs: ZonedDateTime?, ze: ZonedDateTime? ->
            CalEvent(
                title = o.str("title") ?: "(無題)",
                allDay = allDay,
                startDate = s,
                endDateInclusive = if (e < s) s else e,
                start = zs, end = ze,
                colorId = o.str("colorId"),
                calendarName = o.str("calendarName") ?: "",
                calendarColor = calColor,
                location = o.str("location") ?: "",
            )
        }
        if (allDay) {
            // 終日の end は排他的（翌日）
            val s = LocalDate.parse(o.getString("start"))
            val e = LocalDate.parse(o.getString("end")).minusDays(1)
            common(s, e, null, null)
        } else {
            val zs = OffsetDateTime.parse(o.getString("start")).atZoneSameInstant(zone)
            val ze = OffsetDateTime.parse(o.getString("end")).atZoneSameInstant(zone)
            // 0:00 ちょうどに終わる予定は前日までとみなす
            val endDate = if (ze.toLocalTime() == LocalTime.MIDNIGHT && ze.toLocalDate() > zs.toLocalDate())
                ze.toLocalDate().minusDays(1) else ze.toLocalDate()
            common(zs.toLocalDate(), endDate, zs, ze)
        }
    }
}

// ---------- 色ユーティリティ ----------

fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return max(la, lb) / min(la, lb)
}

/** 帯の上の文字色：黒と白のうちコントラストの高い方。 */
fun textOnBar(bar: Color): Color =
    if (contrastRatio(Color.Black, bar) >= contrastRatio(Color.White, bar)) Color.Black else Color.White

/** 背景 bg の上で読めるよう、色味を保ったまま黒（または白）へ寄せる。 */
fun readableOn(c: Color, bg: Color, target: Float = 3.2f): Color {
    if (contrastRatio(c, bg) >= target) return c
    val toward = if (bg.luminance() > 0.4f) Color.Black else Color.White
    var t = 0f
    var r = c
    while (t < 1f) {
        t += 0.05f
        r = lerp(c, toward, t)
        if (contrastRatio(r, bg) >= target) break
    }
    return r
}

// ---------- 週ごとのレイアウト ----------

class BarSeg(val event: CalEvent, val startCol: Int, val endCol: Int, val lane: Int)

class WeekLayout(
    val bars: List<BarSeg>,
    /** 列ごとの単日時間指定予定（時刻順）。 */
    val timed: List<List<CalEvent>>,
    /** 列ごとに、帯が使っている最下レーンの次の行番号。時間指定はここから並ぶ。 */
    val firstFreeRow: IntArray,
)

class EventIndex(events: List<CalEvent>) {
    private val bars = events.filter { it.isBar }.sortedWith(
        compareBy<CalEvent> { it.startDate }
            .thenByDescending { ChronoUnit.DAYS.between(it.startDate, it.endDateInclusive) }
            .thenBy { it.start }
            .thenBy { it.title }
    )
    private val timedByDate: Map<LocalDate, List<CalEvent>> =
        events.filter { !it.isBar }.groupBy { it.startDate }.mapValues { (_, v) -> v.sortedBy { it.start } }

    fun layoutWeek(weekStart: LocalDate): WeekLayout {
        val weekEnd = weekStart.plusDays(6)
        val occ = mutableListOf<BooleanArray>()
        val segs = mutableListOf<BarSeg>()
        for (e in bars) {
            if (e.endDateInclusive < weekStart || e.startDate > weekEnd) continue
            val sc = if (e.startDate < weekStart) 0 else ChronoUnit.DAYS.between(weekStart, e.startDate).toInt()
            val ec = if (e.endDateInclusive > weekEnd) 6 else ChronoUnit.DAYS.between(weekStart, e.endDateInclusive).toInt()
            var lane = occ.indexOfFirst { row -> (sc..ec).none { row[it] } }
            if (lane < 0) {
                occ.add(BooleanArray(7)); lane = occ.lastIndex
            }
            for (c in sc..ec) occ[lane][c] = true
            segs += BarSeg(e, sc, ec, lane)
        }
        val firstFree = IntArray(7) { c -> (occ.indices.lastOrNull { occ[it][c] } ?: -1) + 1 }
        val timed = List(7) { timedByDate[weekStart.plusDays(it.toLong())].orEmpty() }
        return WeekLayout(segs, timed, firstFree)
    }

    /** 日ポップアップ用：その日にかかる予定（帯→時間指定の順）。 */
    fun eventsOn(date: LocalDate): List<CalEvent> =
        bars.filter { date >= it.startDate && date <= it.endDateInclusive } + timedByDate[date].orEmpty()
}
