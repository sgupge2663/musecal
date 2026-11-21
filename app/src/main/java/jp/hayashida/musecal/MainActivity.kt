package jp.hayashida.musecal

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields

// ---------- 定数 ----------

/** スクロール範囲の起点（日曜）。2024-12-29 〜 約6年分。 */
private val BASE_SUNDAY: LocalDate = LocalDate.of(2024, 12, 29)
private const val WEEK_COUNT = 52 * 6
private val INITIAL_MONTH: YearMonth = YearMonth.of(2026, 12)
private const val VISIBLE_WEEKS = 6

// 行の高さの下限（通常の文字倍率ではこの値。文字倍率を上げると実測した文字の高さに合わせて伸びる）
// T-107：BC2 スクショ実測（同一端末・921px 幅で約 2.56px/dp）に合わせた値。
//   BC2：日付行 ≈ 49px ≈ 19dp、予定1行（＝帯の高さ）≈ 34px ≈ 13.3dp。v0.1 は 22dp / 17dp で間延びしていた。
private val DATE_H_MIN = 19.dp   // 日付数字の行
private val LINE_H_MIN = 13.dp   // 予定1行

private val GridLine = Color(0xFFDDDDDD)
private val OutsideBg = Color(0xFFEDEDED)
private val TodayBg = Color(0xFFFFF4C2)
private val SundayRed = Color(0xFFD32F2F)
private val SaturdayBlue = Color(0xFF1E63C4)
private val WeekNoGray = Color(0xFF9E9E9E)

// T-107：文字の大きさ・太さも BC2 実測に合わせる。
//   予定文字の字面の高さ BC2 ≈ 27.5px / v0.1 ≈ 31px → 12.5sp × 27.5/31 ≈ 11sp。行送りは帯の高さ 13.3dp に合わせて 13sp。
//   帯の文字は BC2 では標準の太さ（線幅 ≈ 字高の 12%）。v0.1 は Bold（≈ 29%）で重く見えていたので標準に戻す。
//   時間指定予定の文字は v0.1 も BC2 も標準の太さで同等（線幅/字高 ≈ 0.165）なので太さは変えない。
private val CellText = TextStyle(fontSize = 11.sp, lineHeight = 13.sp)
private val BarText = CellText
// 日付数字の字面の高さ BC2 ≈ 28px / v0.1 ≈ 30px → 15sp。
private val DateText = TextStyle(fontSize = 15.sp, lineHeight = 17.sp)   // 17sp + 上下 1dp ずつ = 19dp
private val DateTextToday = DateText.copy(fontWeight = FontWeight.Bold)
private val WeekNoText = TextStyle(fontSize = 10.sp, lineHeight = 12.sp)
private val MoreText = TextStyle(fontSize = 10.sp, lineHeight = 12.sp)   // 予定1行（13sp）の中に収める

private fun sundayOf(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value % 7).toLong())
private fun weekIndexOf(d: LocalDate): Int =
    (ChronoUnit.DAYS.between(BASE_SUNDAY, sundayOf(d)) / 7).toInt().coerceIn(0, WEEK_COUNT - 1)
private fun weekStartOf(index: Int): LocalDate = BASE_SUNDAY.plusWeeks(index.toLong())

class MainActivity : ComponentActivity() {
    /** 現在日。固定せず、画面復帰・日付変更・時刻/タイムゾーン変更のたびに取り直す（T-102）。 */
    private val today = mutableStateOf(LocalDate.now())

    private fun refreshToday() {
        val now = LocalDate.now()
        if (today.value != now) today.value = now
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val auth = CalendarAuth(this)

        // 画面が見えている間だけ：①開始（復帰）時に取り直す ②次の0時に取り直す ③端末の日付・時刻・TZ変更通知で取り直す
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) = refreshToday()
                }
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_DATE_CHANGED)
                    addAction(Intent.ACTION_TIME_CHANGED)
                    addAction(Intent.ACTION_TIMEZONE_CHANGED)
                }
                ContextCompat.registerReceiver(this@MainActivity, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                try {
                    while (true) {
                        refreshToday()
                        val now = ZonedDateTime.now()
                        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
                        delay(Duration.between(now, nextMidnight).toMillis() + 500)
                    }
                } finally {
                    unregisterReceiver(receiver)
                }
            }
        }

        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                MuseCalRoot(auth, today)
            }
        }
    }
}

/**
 * V1-1: サインイン状態で画面を出し分ける入口。
 * 未サインイン時はサンプル予定を一切出さず、サインイン案内だけを出す（V1-5 の未ログイン画面を兼ねる）。
 * サインイン済みなら従来の月表示（データは V1-3 の実データ接続までサンプルのまま）。
 */
@Composable
private fun MuseCalRoot(auth: CalendarAuth, todayState: State<LocalDate>) {
    val account by auth.account.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Calendar 認可の同意画面（AuthorizationClient の resolution）の結果受け口。
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val granted = runCatching {
                auth.authorizationResultFromIntent(result.data!!).accessToken != null
            }.getOrDefault(false)
            Toast.makeText(
                context,
                if (granted) "カレンダーへのアクセスが許可されました" else "カレンダーへのアクセスが許可されませんでした",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun requestCalendarAccess() {
        scope.launch {
            when (val access = auth.authorizeCalendar()) {
                // V1-1 ではトークンを使わない。実データ取得（V1-3）が呼ぶたびに取り直す。
                is CalendarAuth.CalendarAccess.Granted -> Unit
                is CalendarAuth.CalendarAccess.ConsentRequired ->
                    consentLauncher.launch(IntentSenderRequest.Builder(access.intentSender).build())
                is CalendarAuth.CalendarAccess.Failed ->
                    Toast.makeText(
                        context,
                        "カレンダーの許可を確認できませんでした: ${access.error.message}",
                        Toast.LENGTH_LONG,
                    ).show()
            }
        }
    }

    val current = account
    if (current == null) {
        SignInScreen(auth) { requestCalendarAccess() }
    } else {
        val events = remember { loadEvents(context) }
        MuseCalApp(
            events, todayState,
            accountEmail = current.email,
            onSignOut = { scope.launch { auth.signOut() } },
        )
    }
}

/** V1-1 / V1-5: 未サインイン時の案内画面。アプリ名・説明・「Google でログイン」ボタンだけを出す。 */
@Composable
private fun SignInScreen(auth: CalendarAuth, onSignedIn: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    var signingIn by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(
            Modifier.fillMaxWidth().background(Color.Black).statusBarsPadding().height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("MuseCal", color = Color.White, fontSize = 22.sp)
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("MuseCal", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111111))
            Spacer(Modifier.height(16.dp))
            Text(
                "Google カレンダーから直接予定を読み込むカレンダーです。\n" +
                    "予定を表示するには、Google アカウントでログインしてください。\n" +
                    "アクセスはカレンダーの読み取り専用です。",
                fontSize = 14.sp, color = Color(0xFF444444), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = {
                    val act = activity ?: return@Button
                    signingIn = true
                    error = null
                    scope.launch {
                        val result = auth.signIn(act)
                        signingIn = false
                        result.fold(
                            onSuccess = { onSignedIn() },
                            onFailure = { e ->
                                // キャンセルはエラーとして見せない
                                if (e !is GetCredentialCancellationException) {
                                    error = "ログインできませんでした: ${e.message}"
                                }
                            },
                        )
                    }
                },
                enabled = !signingIn && activity != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black, contentColor = Color.White,
                ),
            ) {
                Text(if (signingIn) "ログイン中…" else "Google でログイン", fontSize = 16.sp)
            }
            error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, fontSize = 13.sp, color = SundayRed, textAlign = TextAlign.Center)
            }
        }
    }
}

/** 月グリッドの寸法。文字倍率込みの実測値から行の高さと1セルの表示行数を決める（T-103）。 */
private class GridMetrics(val measurer: TextMeasurer, density: Density, widthPx: Float, val rowHPx: Float) {
    val px1 = density.density                     // 1dp
    val cellW = widthPx / 7f
    val cellTextH: Float = measurer.measure("あg", CellText, density = density).size.height.toFloat()
    val lineH: Float = maxOf(with(density) { LINE_H_MIN.toPx() }, cellTextH + px1)
    val dateH: Float = maxOf(
        with(density) { DATE_H_MIN.toPx() },
        measurer.measure("30", DateText, density = density).size.height + 2 * px1,
    )
    /** 1セルに収まる予定の行数（0 もありうる。その場合は予定を描かない）。 */
    val cap: Int = ((rowHPx - dateH) / lineH).toInt().coerceAtLeast(0)
}

@Composable
fun MuseCalApp(
    all: List<CalEvent>,
    todayState: State<LocalDate>,
    accountEmail: String,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val calendars = remember(all) { all.map { it.calendarName to it.calendarColor }.distinctBy { it.first } }
    var hidden by remember { mutableStateOf(setOf<String>()) }
    val index = remember(all, hidden) { EventIndex(all.filter { it.calendarName !in hidden }) }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = weekIndexOf(INITIAL_MONTH.atDay(1)))
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    var popupDate by remember { mutableStateOf<LocalDate?>(null) }

    // 上端付近の週（半分以上隠れたら次の週）の土曜が属する月をヘッダーに出す。
    // ここ（MuseCalApp 本体）では値を読まない：読むと月が変わるたびに画面全体が再コンポーズされるため。
    // 読むのは見出し文字と、各週の描画（draw）だけ。
    val displayMonthState = remember {
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
            HeaderButton("‹") { scrollToMonth(displayMonthState.value.minusMonths(1)) }
            MonthTitle { displayMonthState.value }
            HeaderButton("›") { scrollToMonth(displayMonthState.value.plusMonths(1)) }
            Spacer(Modifier.weight(1f))
            // 押した時点の現在日へ（T-102）
            HeaderButton("今日") { scrollToMonth(YearMonth.from(todayState.value)) }
        }
        // V1-1：ログイン中アカウントの表示とサインアウト（ヘッダーの黒基調に続く細い帯）
        Row(
            Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                accountEmail, color = Color(0xFFBBBBBB), fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Text(
                "サインアウト", color = Color.White, fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = onSignOut).padding(horizontal = 8.dp, vertical = 2.dp),
            )
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
            val density = LocalDensity.current
            val measurer = rememberTextMeasurer(cacheSize = 512)
            val widthPx = constraints.maxWidth.toFloat()
            val rowHPx = constraints.maxHeight / VISIBLE_WEEKS.toFloat()
            val metrics = remember(measurer, density, widthPx, rowHPx) { GridMetrics(measurer, density, widthPx, rowHPx) }
            val rowH = with(density) { rowHPx.toDp() }
            val todayP = remember(todayState) { { todayState.value } }
            val selectedP = remember { { selected } }
            val monthP = remember { { displayMonthState.value } }
            val onDayClick = remember { { d: LocalDate -> selected = d; popupDate = d } }
            // 週ごとの描画内容のメモ。LazyColumn は画面外に出た週を破棄するので、戻ってきたときに測り直さないよう
            // ここに持つ。表示カレンダー（index）か寸法（metrics：画面サイズ・文字倍率）が変わったら作り直す。
            val renderCache = remember(index, metrics) { HashMap<LocalDate, WeekRender>() }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(count = WEEK_COUNT, key = { it }, contentType = { 0 }) { i ->
                    WeekRow(
                        weekStart = weekStartOf(i), rowH = rowH, metrics = metrics, index = index, cache = renderCache,
                        today = todayP, selected = selectedP, displayMonth = monthP, onDayClick = onDayClick,
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
private fun MonthTitle(month: () -> YearMonth) {
    val m = month()
    Text(
        "${m.year}年${m.monthValue}月",
        color = Color.White, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun HeaderButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, fontSize = if (label.length == 1) 26.sp else 16.sp) }
}

// ---------- 週1行の描画（T-105：事前計算＋1ノード描画） ----------
//
// v0 は週1行ごとに BoxWithConstraints（サブコンポジション）と数十個の Text ノードを作っていたため、
// スクロールで新しい週が現れるたびに重い合成・レイアウトが走っていた。
// v0.1 では、週の中身（帯の矩形・文字の配置・+n）を WeekRender として一度だけ計算してメモ化し、
// 描画は drawBehind 1つで行う。今日・選択日・表示月は draw の中でだけ読むので、
// それらが変わっても再コンポーズ・再レイアウトは起きず、再描画だけで済む。

private class PlacedText(val layout: TextLayoutResult, val x: Float, val y: Float)

private class BarDraw(
    val left: Float, val top: Float, val width: Float, val height: Float,
    val color: Color, val textColor: Color, val text: PlacedText,
)

private class TimedDraw(val col: Int, val color: Color, val text: PlacedText)

private class WeekRender(
    val dates: List<LocalDate>,
    val dayNum: List<TextLayoutResult>,
    val dayNumBold: List<TextLayoutResult>,
    val weekNo: TextLayoutResult,
    val bars: List<BarDraw>,
    val timed: List<TimedDraw>,
    /** 列ごとの「+n」（無ければ null）。 */
    val more: List<PlacedText?>,
)

/**
 * 1行に収まる分だけを、文字（語）の境界で切って測る（T-106）。
 * softWrap=true・maxLines=1 にすると、Android の改行処理が「収まる最後の文字/語」で1行目を終え、
 * 2行目以降は描かれない。v0 の softWrap=false＋Clip は文字の途中で切れていた。
 * BC2 も省略記号は付けず語・文字の境界で切っている（「異文化交」「大掃除」「itto 講師」）ので、それに合わせる。
 */
private fun TextMeasurer.fitOneLine(text: String, style: TextStyle, maxWidthPx: Float): TextLayoutResult =
    measure(
        text, style, overflow = TextOverflow.Clip, softWrap = true, maxLines = 1,
        constraints = Constraints(maxWidth = maxWidthPx.toInt().coerceAtLeast(1)),
    )

private fun buildWeekRender(weekStart: LocalDate, index: EventIndex, m: GridMetrics): WeekRender {
    val layout = index.layoutWeek(weekStart)
    val measurer = m.measurer
    val px = m.px1
    val cw = m.cellW
    val cap = m.cap
    val dates = List(7) { weekStart.plusDays(it.toLong()) }

    // 列ごとの表示可能行数と隠れた件数（v0 と同じ規則：あふれたら最終行を +n に使う）
    val total = IntArray(7) { layout.firstFreeRow[it] + layout.timed[it].size }
    val visRows = IntArray(7) { if (total[it] <= cap) cap else (cap - 1).coerceAtLeast(0) }
    val hiddenCount = IntArray(7) { c ->
        layout.bars.count { c in it.startCol..it.endCol && it.lane >= visRows[c] } +
            layout.timed[c].indices.count { layout.firstFreeRow[c] + it >= visRows[c] }
    }

    fun rowTop(row: Int) = m.dateH + m.lineH * row

    // 帯：表示できる列ごとに区切って描く
    val bars = ArrayList<BarDraw>()
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
            // T-107：BC2 の帯はセルの罫線ぎりぎりまで塗り、隣の帯との間は罫線1本分だけ（実測 ≈ 3px）。
            // v0.1 は始端 1dp・終端 2dp 内側へ寄せていて、罫線と合わせて ≈ 9px の白い隙間が出ていた。
            // 罫線は各セルの右端 1dp に引かれるので、帯の終端だけ 1dp 手前で止める（角丸は BC2 にも無いので付けない）。
            val left = cw * runStart
            val width = cw * (runEnd - runStart + 1) - (if (realEnd) px else 0f)
            val top = rowTop(seg.lane)
            val height = m.lineH - px
            val t = measurer.fitOneLine(ev.title, BarText, width - 4 * px)
            val color = ev.baseColor
            bars += BarDraw(
                left, top, width, height, color, textOnBar(color),
                PlacedText(t, left + 3 * px, top + (height - t.size.height) / 2f),   // 文字の左端は v0.1 と同じ位置（帯を 1dp 左へ広げた分だけ余白を足す）
            )
            c++
        }
    }

    // 単日の時間指定予定（時刻順・タイトルのみ）
    val timed = ArrayList<TimedDraw>()
    for (c in 0..6) {
        layout.timed[c].forEachIndexed { i, ev ->
            val row = layout.firstFreeRow[c] + i
            if (row < visRows[c]) {
                val t = measurer.fitOneLine(ev.title, CellText, cw - 5 * px)
                timed += TimedDraw(c, ev.baseColor, PlacedText(t, cw * c + 3 * px, rowTop(row) + (m.lineH - t.size.height) / 2f))
            }
        }
    }

    val more = List(7) { c ->
        if (hiddenCount[c] > 0 && cap >= 1) {
            val t = measurer.fitOneLine("+${hiddenCount[c]}", MoreText, cw - 5 * px)
            PlacedText(t, cw * c + 3 * px, rowTop(cap - 1) + (m.lineH - t.size.height) / 2f)
        } else null
    }

    return WeekRender(
        dates = dates,
        dayNum = dates.map { measurer.measure(it.dayOfMonth.toString(), DateText) },
        dayNumBold = dates.map { measurer.measure(it.dayOfMonth.toString(), DateTextToday) },
        // ISO 週番号：その行の月曜で数える（BC2 と同じく 2026-11-01 の行 = 45）
        weekNo = measurer.measure(dates[1].get(IsoFields.WEEK_OF_WEEK_BASED_YEAR).toString(), WeekNoText),
        bars = bars, timed = timed, more = more,
    )
}

private fun DrawScope.drawWeek(
    r: WeekRender, m: GridMetrics, today: LocalDate, selected: LocalDate?, month: YearMonth,
) {
    val px = m.px1
    val cw = m.cellW
    val bg = Array(7) { c ->
        val d = r.dates[c]
        when {
            d == selected -> Color.Black
            d == today -> TodayBg
            YearMonth.from(d) != month -> OutsideBg
            else -> Color.White
        }
    }
    // 1) セル背景・罫線・日付・週番号
    for (c in 0..6) {
        drawRect(bg[c], topLeft = Offset(cw * c, 0f), size = Size(cw, size.height))
        val x = cw * (c + 1) - px / 2
        drawLine(GridLine, Offset(x, 0f), Offset(x, size.height), px)
    }
    drawLine(GridLine, Offset(0f, size.height - px / 2), Offset(size.width, size.height - px / 2), px)
    for (c in 0..6) {
        val d = r.dates[c]
        val isSel = d == selected
        val numColor = when {
            isSel -> Color.White
            c == 0 -> SundayRed
            c == 6 -> SaturdayBlue
            else -> Color(0xFF333333)
        }
        val outside = YearMonth.from(d) != month && !isSel
        val num = if (d == today) r.dayNumBold[c] else r.dayNum[c]
        drawText(num, color = if (outside) numColor.copy(alpha = 0.6f) else numColor, topLeft = Offset(cw * c + 4 * px, 2 * px))
        if (c == 0) {
            drawText(
                r.weekNo, color = if (isSel) Color(0xFFBBBBBB) else WeekNoGray,
                topLeft = Offset(cw * c + 4 * px + num.size.width + 8 * px, 2 * px),
            )
        }
    }
    // 2) 色帯
    for (b in r.bars) {
        drawRect(b.color, topLeft = Offset(b.left, b.top), size = Size(b.width, b.height))
        drawText(b.text.layout, color = b.textColor, topLeft = Offset(b.text.x, b.text.y))
    }
    // 3) 単日の時間指定予定 ＋ +n
    for (t in r.timed) {
        drawText(t.text.layout, color = textShade(t.color, bg[t.col]), topLeft = Offset(t.text.x, t.text.y))
    }
    r.more.forEachIndexed { c, t ->
        if (t != null) {
            drawText(
                t.layout, color = if (bg[c] == Color.Black) Color(0xFFCCCCCC) else Color(0xFF777777),
                topLeft = Offset(t.x, t.y),
            )
        }
    }
}

@Composable
private fun WeekRow(
    weekStart: LocalDate,
    rowH: Dp,
    metrics: GridMetrics,
    index: EventIndex,
    cache: HashMap<LocalDate, WeekRender>,
    today: () -> LocalDate,
    selected: () -> LocalDate?,
    displayMonth: () -> YearMonth,
    onDayClick: (LocalDate) -> Unit,
) {
    val render = remember(weekStart, index, metrics) {
        cache.getOrPut(weekStart) { buildWeekRender(weekStart, index, metrics) }
    }
    val click by rememberUpdatedState(onDayClick)
    Box(
        Modifier
            .fillMaxWidth()
            .height(rowH)
            .pointerInput(weekStart) {
                detectTapGestures { off ->
                    val col = (off.x / (size.width / 7f)).toInt().coerceIn(0, 6)
                    click(weekStart.plusDays(col.toLong()))
                }
            }
            .drawBehind { drawWeek(render, metrics, today(), selected(), displayMonth()) }
    )
}

@Composable
private fun CalendarTabBar(calendars: List<Pair<String, Color>>, hidden: Set<String>, onToggle: (String) -> Unit) {
    // v0 は各タブの幅が中身任せで、下線（fillMaxWidth）が横スクロールの無制限幅の中で幅0になり見えなかった（§7 差異3）。
    // BC2 と同じく、タブを等幅（画面に最大5つ）にして、カレンダー色の下線をタブ幅いっぱいに引く。
    BoxWithConstraints(Modifier.fillMaxWidth().background(Color.Black).navigationBarsPadding()) {
        val tabW = maxWidth / calendars.size.coerceIn(1, 5)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            calendars.forEach { (name, color) ->
                val off = name in hidden
                Column(
                    Modifier.width(tabW).clickable { onToggle(name) }.padding(top = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        name, fontSize = 15.sp, maxLines = 1, softWrap = true, overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center,
                        color = if (off) Color(0xFF777777) else Color.White,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    // 下線はカレンダー色（§6・BC2 と同じ）。非表示中のカレンダーは文字を灰色にし、下線は薄くする
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = 2.dp).height(4.dp)
                            .background(if (off) color.copy(alpha = 0.35f) else color)
                    )
                    Spacer(Modifier.height(6.dp))
                }
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
                // 見出し：「N 予定」＋右に 編集（レンチ）・表示切替・＋（§2。v0.1 は見た目のみ、押下時の挙動は未定）
                Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${events.size} 予定", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    PopupHeaderIcon { Icon(Icons.Filled.Build, contentDescription = "編集", tint = PopupIconTint) }
                    PopupHeaderIcon { DottedSquareIcon() }
                    PopupHeaderIcon { Icon(Icons.Filled.Add, contentDescription = "予定を追加", tint = PopupIconTint) }
                }
                Text(
                    "${date.year}年${date.monthValue}月${date.dayOfMonth}日（${DOW_JA[date.dayOfWeek]}）",
                    fontSize = 13.sp, color = Color(0xFF666666),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
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

private val PopupIconTint = Color(0xFF666666)

/** 見出しのアイコン枠。v0.1 は見た目のみなので押せる見た目（波紋）は付けない。 */
@Composable
private fun PopupHeaderIcon(content: @Composable () -> Unit) {
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { content() }
}

/** 表示切替アイコン（BC2 の点線の四角）。標準アイコンに無いので描く。 */
@Composable
private fun DottedSquareIcon() {
    Canvas(Modifier.size(22.dp).semantics { contentDescription = "表示切替" }) {
        val n = 5
        val step = size.width / (n - 1)
        val r = 1.3.dp.toPx()
        for (i in 0 until n) for (j in 0 until n) {
            val onEdge = i == 0 || j == 0 || i == n - 1 || j == n - 1
            val center = i == n / 2 && j == n / 2
            if (onEdge || center) drawCircle(PopupIconTint, r, Offset(i * step, j * step))
        }
    }
}
