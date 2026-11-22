package jp.hayashida.musecal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * V1-3 予定の取得と描画への接続。
 *
 * - [EventWindow] / [windowToFetch]: 月表示が見ている6週分を取得単位とし、表示中の週が取得済みの範囲から
 *   はみ出したら、はみ出した最初の週から6週分を取り直す
 * - [googleEventToCalEvent] / [parseEventsPage]: Calendar API v3 events.list の JSON を既存モデル [CalEvent] へ変換する
 * - [EventsApi]: events.list を REST で直接呼ぶ（singleEvents=true、orderBy=startTime、pageToken で全ページ）
 * - [EventWindowStore]: 取得結果の保持と永続化用 snapshot
 */

/** 取得単位。[start]（日曜）から6週分（42日）。 */
data class EventWindow(val start: LocalDate) {
    val endExclusive: LocalDate get() = start.plusWeeks(WEEKS.toLong())

    /** events.list に渡す timeMin（予定の終了時刻の排他的下限）。[zone] は取得時の端末タイムゾーン。 */
    fun timeMin(zone: ZoneId): Instant = start.atStartOfDay(zone).toInstant()

    /** events.list に渡す timeMax（予定の開始時刻の排他的上限）。 */
    fun timeMax(zone: ZoneId): Instant = endExclusive.atStartOfDay(zone).toInstant()
    val weekStarts: List<LocalDate> get() = List(WEEKS) { start.plusWeeks(it.toLong()) }

    companion object {
        const val WEEKS = 6
    }
}

/**
 * 表示中の週（各週の日曜）のうち、取得済み・取得中（[covered]）に含まれない最初の週から始まる6週分を返す。
 * 全部含まれていれば null（取り直さない）。
 */
fun windowToFetch(visibleWeekStarts: List<LocalDate>, covered: Set<LocalDate>): EventWindow? =
    visibleWeekStarts.sorted().firstOrNull { it !in covered }?.let(::EventWindow)

/**
 * V1-4a 今すぐ同期: 取得済みかどうかに関係なく、表示中の週をすべて覆う窓。表示中の最初の週から6週、
 * 覆いきれない週（行の途中で止まって7週目が見えている等）があれば、その週から6週を続ける。
 * 表示外も含め、失敗した窓を重複なく加える（重なる別開始日の窓も、それぞれ再取得する）。
 */
fun windowsToRefresh(
    visibleWeekStarts: List<LocalDate>,
    failedWindows: Collection<EventWindow> = emptyList(),
): List<EventWindow> {
    val out = mutableListOf<EventWindow>()
    val covered = HashSet<LocalDate>()
    while (true) {
        val w = windowToFetch(visibleWeekStarts, covered) ?: return (out + failedWindows).distinct()
        out += w
        covered += w.weekStarts
    }
}

// null はアクセス許可のエラー。予定の取得成功では消さず、成功した窓のエラーだけを消す。
internal fun Map<EventWindow?, String>.withoutSuccessfulWindows(
    windows: List<EventWindow>,
): Map<EventWindow?, String> = this - windows.toSet()

/**
 * events.list の予定1件を [CalEvent] へ変換する。変換しない（null を返す）のは status=cancelled と、
 * start が読めないものだけ。
 *
 * - 終日: start.date / end.date（end は排他的）。時間指定: start.dateTime / end.dateTime（RFC3339）を [zone] へ。
 * - タイトル: summary をそのまま使う。API はタイトルが空の予定では summary を返さないので、そのときは空文字
 *   （「(無題)」等の装飾はしない）。
 * - 色: colorId はそのまま持たせ、[CalEvent.baseColor] が標準11色（[GoogleEventColors]）へ変換する。
 *   colorId が無い（またはパレット外）ならカレンダーの backgroundColor（[CalendarTab.color]）。
 * - calendarId: 取得したカレンダーの id（タブのフィルタに使う）。
 */
fun googleEventToCalEvent(
    o: JSONObject,
    calendar: CalendarTab,
    zone: ZoneId,
    /** カレンダーのタイムゾーン（events.list 応答の最上位 timeZone）。終日予定の照合用区間（matchStart/End）に使う。 */
    calendarZone: ZoneId = zone,
): CalEvent? {
    if (o.optString("status") == "cancelled") return null
    val start = o.optJSONObject("start") ?: return null
    val end = o.optJSONObject("end") ?: start
    val common = { allDay: Boolean, s: LocalDate, e: LocalDate, zs: java.time.ZonedDateTime?, ze: java.time.ZonedDateTime?,
                   ms: Instant, me: Instant ->
        CalEvent(
            title = o.optString("summary", ""),
            allDay = allDay,
            startDate = s,
            endDateInclusive = if (e < s) s else e,
            start = zs, end = ze,
            colorId = o.optString("colorId", "").ifEmpty { null },
            calendarId = calendar.calendarId,
            calendarName = calendar.label,
            calendarColor = calendar.color,
            location = o.optString("location", ""),
            eventId = o.optString("id", "").ifEmpty { null },
            matchStart = ms,
            matchEnd = me,
        )
    }
    val startDate = start.optString("date", "")
    val startDateTime = start.optString("dateTime", "")
    return when {
        startDate.isNotEmpty() -> {
            val s = LocalDate.parse(startDate)
            // end.date は排他的（翌日）
            val endExclusive = end.optString("date", "").ifEmpty { null }?.let(LocalDate::parse) ?: s.plusDays(1)
            // 表示日は date 文字列のまま。API との照合区間はカレンダーのタイムゾーンで時刻化する
            common(true, s, endExclusive.minusDays(1), null, null,
                s.atStartOfDay(calendarZone).toInstant(), endExclusive.atStartOfDay(calendarZone).toInstant())
        }
        startDateTime.isNotEmpty() -> {
            val zs = OffsetDateTime.parse(startDateTime).atZoneSameInstant(zone)
            val ze = end.optString("dateTime", "").ifEmpty { null }
                ?.let { OffsetDateTime.parse(it).atZoneSameInstant(zone) } ?: zs
            common(false, zs.toLocalDate(), timedEndDate(zs, ze), zs, ze, zs.toInstant(), ze.toInstant())
        }
        else -> null
    }
}

/**
 * 月表示のスクロール停止の検出（V1-3 Codex レビュー指摘3）。
 *
 * スクロールが止まるたびに、表示中の週が前回と同じでも必ず1回知らせる（取得に失敗したウィンドウを、
 * 同じ範囲での次のスクロール停止で取り直せるように）。スクロール中は知らせない。
 * スクロールせずに状態が再評価されただけ（同じ週・停止のまま）のときは知らせない。
 */
class ScrollStopDetector {
    private var wasScrolling = true
    private var last: List<Int>? = null

    /** 状態を渡す。知らせるべきときだけ表示中の週の番号を返す。 */
    fun onState(scrolling: Boolean, visibleIndices: List<Int>): List<Int>? {
        if (scrolling) {
            wasScrolling = true
            return null
        }
        if (visibleIndices.isEmpty()) return null
        val emit = wasScrolling || visibleIndices != last
        wasScrolling = false
        last = visibleIndices
        return if (emit) visibleIndices else null
    }
}

/** events.list の1ページ分の JSON を読む。 */
fun parseEventsPage(json: String, calendar: CalendarTab, zone: ZoneId): Pair<List<CalEvent>, String?> {
    val o = JSONObject(json)
    // 応答の最上位 timeZone は「カレンダーのタイムゾーン」（events.list リファレンス）。読めなければ端末のタイムゾーン
    val calendarZone = o.optString("timeZone", "").ifEmpty { null }
        ?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: zone
    val items = o.optJSONArray("items")
    val events = (0 until (items?.length() ?: 0)).mapNotNull { i ->
        googleEventToCalEvent(items!!.getJSONObject(i), calendar, zone, calendarZone)
    }
    return events to o.optString("nextPageToken", "").ifEmpty { null }
}

/** Calendar API v3 events.list を直接呼ぶ（読み取りのみ。トークンは呼び出し側がその場で渡し、保存しない）。 */
object EventsApi {
    private const val BASE = "https://www.googleapis.com/calendar/v3/calendars/"
    private const val FIELDS =
        "timeZone,items(id,status,summary,start,end,colorId,location,recurringEventId),nextPageToken"
    private val RFC3339: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    class HttpError(val code: Int, message: String) : IOException(message)

    /** [calendar] の [window] 内の予定を全ページ取得する。 */
    suspend fun fetchWindow(
        accessToken: String,
        calendar: CalendarTab,
        window: EventWindow,
        zone: ZoneId,
    ): List<CalEvent> = withContext(Dispatchers.IO) {
        val timeMin = window.timeMin(zone).atZone(zone).toOffsetDateTime().format(RFC3339)
        val timeMax = window.timeMax(zone).atZone(zone).toOffsetDateTime().format(RFC3339)
        fetchAllPages { pageToken ->
            val url = buildString {
                append(BASE).append(pathSegment(calendar.calendarId)).append("/events")
                append("?singleEvents=true&orderBy=startTime&maxResults=2500")
                append("&timeMin=").append(enc(timeMin))
                append("&timeMax=").append(enc(timeMax))
                append("&fields=").append(enc(FIELDS))
                pageToken?.let { append("&pageToken=").append(enc(it)) }
            }
            parseEventsPage(get(url, accessToken), calendar, zone)
        }
    }

    suspend fun insert(accessToken: String, draft: EventDraft, zone: ZoneId) = withContext(Dispatchers.IO) {
        val conn = URL(BASE + pathSegment(draft.calendarId) + "/events").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.outputStream.use { it.write(createEventBody(draft, zone).toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) throw HttpError(code, "予定の保存に失敗しました")
            conn.inputStream.use { it.readBytes() }
            Unit
        } finally { conn.disconnect() }
    }

    /** 安全上限。1ページ最大2500件なので、6週分でここに届くのは API の異常（同じトークンの繰り返し等）とみなす。 */
    const val MAX_PAGES = 50

    class IncompleteError(message: String) : IOException(message)

    /**
     * nextPageToken が無くなるまで全ページを取る（V1-3 Codex レビュー指摘1）。
     * [maxPages] に届いてもまだ nextPageToken が残っている場合や、同じ nextPageToken が繰り返された場合は
     * 部分結果を返さず [IncompleteError] で失敗にする（不完全なウィンドウを取得済みとして保存しないため）。
     */
    internal inline fun <T> fetchAllPages(
        maxPages: Int = MAX_PAGES,
        fetchPage: (pageToken: String?) -> Pair<List<T>, String?>,
    ): List<T> {
        val all = mutableListOf<T>()
        val seen = HashSet<String>()
        var pageToken: String? = null
        var pages = 0
        while (true) {
            val (items, next) = fetchPage(pageToken)
            all += items
            pages++
            if (next == null) return all
            if (!seen.add(next)) throw IncompleteError("予定の取得が完了しませんでした（同じページが繰り返されました）")
            if (pages >= maxPages) throw IncompleteError("予定の取得が完了しませんでした（${maxPages}ページを超えました）")
            pageToken = next
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** パス用のエンコード（URLEncoder は空白を + にするので %20 に直す。# や @ は %23 / %40 になる）。 */
    internal fun pathSegment(s: String) = enc(s).replace("+", "%20")

    private fun get(url: String, accessToken: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code !in 200..299) {
                val body = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw HttpError(code, "予定の取得に失敗しました（HTTP $code）${body.take(200)}")
            }
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * 取得した予定の在メモリ保持（アカウント・タブの組ごと。変わったら [reset]）。
 *
 * ウィンドウは重なりうる（はみ出した週から6週を取るため）。[put] に渡すのは、そのウィンドウについて
 * 全カレンダー・全ページが揃った完全な応答だけ。完全な応答はそのウィンドウの範囲について正なので、
 * 以前に取った他のウィンドウの予定のうち、新しいウィンドウの範囲にかかるものは捨てる（V1-3 Codex レビュー指摘2:
 * 削除・移動された予定が古いウィンドウから残り続けないように）。範囲にかかる予定は API が必ず返すので、
 * まだ存在するものは新しい応答の側に入っている。UI スレッドからだけ呼ぶ前提。
 */
class EventWindowStore {
    private val loaded = LinkedHashMap<LocalDate, List<CalEvent>>()
    /** 取得中の窓（開始日）→ その窓の最新の要求番号。同じ窓に要求が重なったら最新の要求の結果だけを反映する。 */
    private val inFlight = HashMap<LocalDate, Long>()
    private var requestSeq = 0L
    private data class AppliedWindow(val min: Instant, val max: Instant, val request: Long)
    // 空の成功応答（削除）も記録する。loaded の予定が後で除去されても順序の保証は残す。
    private val appliedWindows = HashMap<LocalDate, AppliedWindow>()

    /** reset のたびに増える。取得開始時の値と反映時の値が違えば、その結果は捨てる。 */
    var generation = 0L
        private set

    /** 1回の取得要求（[markInFlight] が返す札）。結果は [finish] に渡す。 */
    class Pending internal constructor(val window: EventWindow, val request: Long, val generation: Long)

    /** 取得の結果をストアへ反映したか。 */
    sealed interface Outcome {
        /** 反映した（表示を更新する）。 */
        data object Applied : Outcome
        /** 取得に失敗した（既存の予定はそのまま。理由を利用者に知らせる）。 */
        data class Failed(val error: Throwable) : Outcome
        /** 古い要求の結果なので捨てた（成功・失敗とも知らせない）。 */
        data object Discarded : Outcome
    }

    data class Snapshot(val windows: Map<LocalDate, List<CalEvent>>, val fetchedAt: Instant?)
    var lastFetchedAt: Instant? = null
        private set

    fun snapshot() = Snapshot(LinkedHashMap(loaded), lastFetchedAt)

    fun restore(snapshot: Snapshot) {
        reset()
        loaded.putAll(snapshot.windows)
        lastFetchedAt = snapshot.fetchedAt
    }

    fun reset() {
        lastFetchedAt = null
        loaded.clear(); inFlight.clear(); appliedWindows.clear(); generation++
    }

    /** 取得済み＋取得中の週。 */
    fun coveredWeeks(): Set<LocalDate> =
        (loaded.keys + inFlight.keys).flatMapTo(HashSet()) { EventWindow(it).weekStarts }

    /**
     * [w] の取得を始める。取得済みの窓でも呼んでよい（V1-4a 今すぐ同期の強制再取得）。
     * 同じ窓で先に出した要求は、この要求より後に返っても反映されなくなる。
     */
    fun markInFlight(w: EventWindow): Pending {
        val req = ++requestSeq
        inFlight[w.start] = req
        return Pending(w, req, generation)
    }

    /**
     * [markInFlight] で始めた取得の結果を反映する（スクロール由来・今すぐ同期の共通経路）。
     * 捨てるのは: reset 後（アカウント・タブが変わった）、[stillCurrent]=false（札が古い）、
     * 同じ窓に新しい要求が出ている、重なる窓に新しい成功結果が適用済み、取り消し（画面を離れた等）。失敗しても取得済みの予定は消さない。
     * [zone] は取得時の端末タイムゾーン。
     */
    fun finish(p: Pending, result: Result<List<CalEvent>>, zone: ZoneId, stillCurrent: Boolean): Outcome {
        if (p.generation != generation) return Outcome.Discarded
        if (inFlight[p.window.start] != p.request) return Outcome.Discarded
        if (!stillCurrent) {
            fail(p.window)
            return Outcome.Discarded
        }
        val events = result.getOrElse { e ->
            fail(p.window)
            return if (e is kotlinx.coroutines.CancellationException) Outcome.Discarded else Outcome.Failed(e)
        }
        return if (put(p.window, events, zone, p.request)) Outcome.Applied else Outcome.Discarded
    }

    fun fail(w: EventWindow) {
        inFlight -= w.start
    }

    /** [zone] はその窓を取得したときの端末タイムゾーン（events.list に渡した timeMin/timeMax と同じもの）。 */
    fun put(w: EventWindow, events: List<CalEvent>, zone: ZoneId): Boolean =
        put(w, events, zone, ++requestSeq)

    private fun put(w: EventWindow, events: List<CalEvent>, zone: ZoneId, request: Long): Boolean {
        inFlight -= w.start
        val timeMin = w.timeMin(zone)
        val timeMax = w.timeMax(zone)
        // 開始日が異なっても、新しい成功応答の範囲に触れる旧応答は全体を捨てる。
        // 未取得の部分は coveredWeeks に加わらず、破棄後の取得再判定で再試行できる。
        if (appliedWindows.values.any { it.request > request && it.min < timeMax && it.max > timeMin }) {
            return false
        }
        appliedWindows[w.start] = AppliedWindow(timeMin, timeMax, request)
        loaded.remove(w.start)
        for (key in loaded.keys.toList()) {
            loaded[key] = loaded.getValue(key).filterNot { it.matchedBy(w, timeMin, timeMax) }
        }
        lastFetchedAt = Instant.now()
        loaded[w.start] = events // 末尾 = 最新
        return true
    }

    /**
     * その窓の events.list が返す範囲に入る予定か（＝その窓の完全応答が権威を持つ予定か）。
     * API と同じ判定: 予定の終了 > timeMin かつ 予定の開始 < timeMax。区間は表示日でなく [CalEvent.matchStart]/[CalEvent.matchEnd]
     * （終日はカレンダーのタイムゾーンで時刻化した区間。V1-3 Codex 再レビュー 951c042: 端末とカレンダーの TZ が違うと
     * 表示日での判定は API の境界とずれる）。照合区間を持たない予定（同梱サンプル）だけ表示日で判定する。
     */
    private fun CalEvent.matchedBy(w: EventWindow, timeMin: Instant, timeMax: Instant): Boolean {
        val ms = matchStart
        val me = matchEnd
        return if (ms != null && me != null) me > timeMin && ms < timeMax
        else startDate < w.endExclusive && endDateInclusive >= w.start
    }

    /** 全ウィンドウを合わせた予定（重複は最新のウィンドウのものを残す）。 */
    fun merged(): List<CalEvent> {
        val byKey = LinkedHashMap<Any, CalEvent>()
        var anon = 0
        for (events in loaded.values) for (e in events) {
            val key: Any = e.eventId?.let { e.calendarId to it } ?: anon++
            byKey.remove(key)
            byKey[key] = e
        }
        return byKey.values.toList()
    }
}
