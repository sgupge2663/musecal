package jp.hayashida.musecal

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields

// ---------- 定数 ----------

/** スクロール範囲の起点（日曜）。2024-12-29 〜 約6年分。 */
private val BASE_SUNDAY: LocalDate = LocalDate.of(2024, 12, 29)
private const val WEEK_COUNT = 52 * 6
private val INITIAL_MONTH: YearMonth = YearMonth.of(2026, 12)
private const val VISIBLE_WEEKS = 6

private val DATE_H = 22.dp   // 日付数字の行
private val LINE_H = 17.dp   // 予定1行

private val GridLine = Color(0xFFDDDDDD)
private val OutsideBg = Color(0xFFEDEDED)
private val TodayBg = Color(0xFFFFF4C2)
private val SundayRed = Color(0xFFD32F2F)
private val SaturdayBlue = Color(0xFF1E63C4)
private val WeekNoGray = Color(0xFF9E9E9E)

private fun sundayOf(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value % 7).toLong())
private fun weekIndexOf(d: LocalDate): Int =
    (ChronoUnit.DAYS.between(BASE_SUNDAY, sundayOf(d)) / 7).toInt().coerceIn(0, WEEK_COUNT - 1)
private fun weekStartOf(index: Int): LocalDate = BASE_SUNDAY.plusWeeks(index.toLong())

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val events = loadEvents(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                MuseCalApp(events)
            }
        }
    }
}

@Composable
fun MuseCalApp(all: List<CalEvent>) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    val calendars = remember(all) { all.map { it.calendarName to it.calendarColor }.distinctBy { it.first } }
    var hidden by remember { mutableStateOf(setOf<String>()) }
    val index = remember(all, hidden) { EventIndex(all.filter { it.calendarName !in hidden }) }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = weekIndexOf(INITIAL_MONTH.atDay(1)))
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    var popupDate by remember { mutableStateOf<LocalDate?>(null) }

    // 上端付近の週（半分以上隠れたら次の週）の土曜が属する月をヘッダーに出す
    val displayMonth by remember {
        derivedStateOf {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            var i = listState.firstVisibleItemIndex
            if (first != null && listState.firstVisibleItemScrollOffset > first.size / 2) i += 1
            YearMonth.from(weekStartOf(i).plusDays(6))
        }
    }
    fun scrollToMonth(m: YearMonth) {
        scope.launch { listState.animateScrollToItem(weekIndexOf(m.atDay(1))) }
    }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        // ヘッダー
        Row(
            Modifier.fillMaxWidth().background(Color.Black).statusBarsPadding().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderButton("‹") { scrollToMonth(displayMonth.minusMonths(1)) }
            Text(
                "${displayMonth.year}年${displayMonth.monthValue}月",
                color = Color.White, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 4.dp),
            )
            HeaderButton("›") { scrollToMonth(displayMonth.plusMonths(1)) }
            Spacer(Modifier.weight(1f))
            HeaderButton("今日") { scrollToMonth(YearMonth.from(today)) }
        }
        // 曜日ヘッダー（固定）
        Row(Modifier.fillMaxWidth().background(Color.White).padding(vertical = 4.dp)) {
            listOf("日", "月", "火", "水", "木", "金", "土").forEachIndexed { i, s ->
                Text(
                    s, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(start = 6.dp),
                    color = when (i) { 0 -> SundayRed; 6 -> SaturdayBlue; else -> Color(0xFF444444) },
                )
            }
        }
        HorizontalDivider(color = GridLine)

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val rowH = maxHeight / VISIBLE_WEEKS
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(count = WEEK_COUNT, key = { it }) { i ->
                    WeekRow(
                        weekStart = weekStartOf(i), rowH = rowH, displayMonth = displayMonth,
                        today = today, selected = selected, index = index,
                        onDayClick = { d -> selected = d; popupDate = d },
                    )
                }
            }
            FloatingActionButton(
                onClick = { Toast.makeText(context, "v0 では新規作成は未対応です", Toast.LENGTH_SHORT).show() },
                shape = CircleShape, containerColor = Color.Black, contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) { Text("+", fontSize = 30.sp) }
        }

        CalendarTabBar(calendars, hidden) { name ->
            hidden = if (name in hidden) hidden - name else hidden + name
        }
    }

    popupDate?.let { d -> DayPopup(d, index.eventsOn(d)) { popupDate = null } }
}

@Composable
private fun HeaderButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, fontSize = if (label.length == 1) 26.sp else 16.sp) }
}

@Composable
private fun WeekRow(
    weekStart: LocalDate,
    rowH: Dp,
    displayMonth: YearMonth,
    today: LocalDate,
    selected: LocalDate?,
    index: EventIndex,
    onDayClick: (LocalDate) -> Unit,
) {
    val layout = remember(weekStart, index) { index.layoutWeek(weekStart) }
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(rowH).pointerInput(weekStart) {
            detectTapGestures { off ->
                val col = (off.x / (size.width / 7f)).toInt().coerceIn(0, 6)
                onDayClick(weekStart.plusDays(col.toLong()))
            }
        }
    ) {
        val cellW = maxWidth / 7
        val cap = ((rowH - DATE_H) / LINE_H).toInt().coerceAtLeast(1)
        val dates = List(7) { weekStart.plusDays(it.toLong()) }
        val cellBg = List(7) { c ->
            val d = dates[c]
            when {
                d == selected -> Color.Black
                d == today -> TodayBg
                YearMonth.from(d) != displayMonth -> OutsideBg
                else -> Color.White
            }
        }

        // 列ごとの表示可能行数と隠れた件数
        val total = IntArray(7) { layout.firstFreeRow[it] + layout.timed[it].size }
        val visRows = IntArray(7) { if (total[it] <= cap) cap else cap - 1 }
        val hiddenCount = IntArray(7) { c ->
            layout.bars.count { c in it.startCol..it.endCol && it.lane >= visRows[c] } +
                layout.timed[c].indices.count { layout.firstFreeRow[c] + it >= visRows[c] }
        }

        // 1) セル背景・罫線・日付
        Row(Modifier.fillMaxSize()) {
            for (c in 0..6) {
                val d = dates[c]
                val isSel = d == selected
                Box(
                    Modifier.width(cellW).fillMaxHeight().background(cellBg[c]).drawBehind {
                        val w = 1.dp.toPx()
                        drawLine(GridLine, Offset(size.width - w / 2, 0f), Offset(size.width - w / 2, size.height), w)
                        drawLine(GridLine, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
                    }
                ) {
                    Row(Modifier.padding(start = 4.dp, top = 1.dp)) {
                        val numColor = when {
                            isSel -> Color.White
                            c == 0 -> SundayRed
                            c == 6 -> SaturdayBlue
                            else -> Color(0xFF333333)
                        }
                        val outside = YearMonth.from(d) != displayMonth && !isSel
                        Text(
                            d.dayOfMonth.toString(), fontSize = 16.sp, lineHeight = 20.sp,
                            fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal,
                            color = if (outside) numColor.copy(alpha = 0.6f) else numColor,
                        )
                        if (c == 0) {
                            // ISO 週番号：その行の月曜で数える（BC2 と同じく 2026-11-01 の行 = 45）
                            Text(
                                d.plusDays(1).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR).toString(),
                                fontSize = 10.sp, lineHeight = 12.sp,
                                color = if (isSel) Color(0xFFBBBBBB) else WeekNoGray,
                                modifier = Modifier.padding(start = 8.dp, top = 1.dp),
                            )
                        }
                    }
                }
            }
        }

        // 2) 色帯（終日・複数日）。表示できる列ごとに区切って描く
        for (seg in layout.bars) {
            var c = seg.startCol
            while (c <= seg.endCol) {
                if (seg.lane >= visRows[c]) { c++; continue }
                val runStart = c
                while (c + 1 <= seg.endCol && seg.lane < visRows[c + 1]) c++
                val runEnd = c
                val ev = seg.event
                val realStart = dates[runStart] == ev.startDate
                val realEnd = dates[runEnd] == ev.endDateInclusive
                val barColor = ev.baseColor
                Box(
                    Modifier
                        .offset(x = cellW * runStart + (if (realStart) 1.dp else 0.dp), y = DATE_H + LINE_H * seg.lane)
                        .size(
                            width = cellW * (runEnd - runStart + 1) - (if (realStart) 1.dp else 0.dp) - (if (realEnd) 2.dp else 0.dp),
                            height = LINE_H - 1.dp,
                        )
                        .background(barColor),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        ev.title, color = textOnBar(barColor), fontWeight = FontWeight.Bold,
                        style = CellText, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
                c++
            }
        }

        // 3) 単日の時間指定予定（時刻順、タイトルのみ・セル幅で切り捨て）＋ +n
        for (c in 0..6) {
            val bg = cellBg[c]
            layout.timed[c].forEachIndexed { i, ev ->
                val row = layout.firstFreeRow[c] + i
                if (row < visRows[c]) {
                    Text(
                        ev.title, color = readableOn(ev.baseColor, bg), style = CellText,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                        modifier = Modifier.offset(x = cellW * c + 3.dp, y = DATE_H + LINE_H * row).width(cellW - 4.dp),
                    )
                }
            }
            if (hiddenCount[c] > 0) {
                Text(
                    "+${hiddenCount[c]}", fontSize = 11.sp, lineHeight = 14.sp,
                    color = if (bg == Color.Black) Color(0xFFCCCCCC) else Color(0xFF777777),
                    modifier = Modifier.offset(x = cellW * c + 3.dp, y = DATE_H + LINE_H * (cap - 1) + 1.dp),
                )
            }
        }
    }
}

private val CellText = TextStyle(fontSize = 12.5.sp, lineHeight = 16.sp)

@Composable
private fun CalendarTabBar(calendars: List<Pair<String, Color>>, hidden: Set<String>, onToggle: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.Black).navigationBarsPadding().horizontalScroll(rememberScrollState()),
    ) {
        calendars.forEach { (name, color) ->
            val off = name in hidden
            Column(
                Modifier.widthIn(min = 110.dp).clickable { onToggle(name) }.padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    name, fontSize = 15.sp, maxLines = 1,
                    color = if (off) Color(0xFF777777) else Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().padding(horizontal = 2.dp).height(4.dp).background(if (off) Color(0xFF444444) else color))
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

private val HM = DateTimeFormatter.ofPattern("H:mm")
private val MD = DateTimeFormatter.ofPattern("M/d")
private val MDHM = DateTimeFormatter.ofPattern("M/d H:mm")

private fun timeLabel(e: CalEvent): String = when {
    e.allDay && e.endDateInclusive == e.startDate -> "終日"
    e.allDay -> "${e.startDate.format(MD)} – ${e.endDateInclusive.format(MD)}  終日"
    e.isBar -> "${e.start!!.format(MDHM)} – ${e.end!!.format(MDHM)}"
    else -> "${e.start!!.format(HM)} – ${e.end!!.format(HM)}"
}

private val DOW_JA = mapOf(
    DayOfWeek.SUNDAY to "日", DayOfWeek.MONDAY to "月", DayOfWeek.TUESDAY to "火", DayOfWeek.WEDNESDAY to "水",
    DayOfWeek.THURSDAY to "木", DayOfWeek.FRIDAY to "金", DayOfWeek.SATURDAY to "土",
)

@Composable
private fun DayPopup(date: LocalDate, events: List<CalEvent>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(14.dp), color = Color.White, shadowElevation = 8.dp) {
            Column(Modifier.padding(vertical = 14.dp)) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.Bottom) {
                    Text("${events.size} 予定", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${date.year}年${date.monthValue}月${date.dayOfMonth}日（${DOW_JA[date.dayOfWeek]}）",
                        fontSize = 13.sp, color = Color(0xFF666666),
                    )
                }
                HorizontalDivider(Modifier.padding(top = 10.dp), color = GridLine)
                if (events.isEmpty()) {
                    Text(
                        "この日に予定がありません", color = Color(0xFF777777),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 440.dp)) {
                        items(events) { e ->
                            Row(
                                Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 16.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.Start,
                            ) {
                                Box(Modifier.width(5.dp).fillMaxHeight().background(e.baseColor, RoundedCornerShape(2.dp)))
                                Column(Modifier.padding(start = 10.dp)) {
                                    Text(timeLabel(e), fontSize = 13.sp, color = Color(0xFF555555))
                                    Text(
                                        e.title, fontSize = 16.sp, color = Color(0xFF111111),
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    )
                                    if (e.location.isNotEmpty()) {
                                        Text(e.location, fontSize = 12.sp, color = Color(0xFF777777), maxLines = 2)
                                    }
                                    Text(e.calendarName, fontSize = 11.sp, color = Color(0xFF999999))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
