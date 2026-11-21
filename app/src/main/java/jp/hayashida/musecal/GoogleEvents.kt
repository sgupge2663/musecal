package jp.hayashida.musecal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
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
 * - [EventWindowStore]: 取得結果の在メモリ保持（永続キャッシュは V1-4）
 */

/** 取得単位。[start]（日曜）から6週分（42日）。 */
data class EventWindow(val start: LocalDate) {
    val endExclusive: LocalDate get() = start.plusWeeks(WEEKS.toLong())
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
fun googleEventToCalEvent(o: JSONObject, calendar: CalendarTab, zone: ZoneId): CalEvent? {
    if (o.optString("status") == "cancelled") return null
    val start = o.optJSONObject("start") ?: return null
    val end = o.optJSONObject("end") ?: start
    val common = { allDay: Boolean, s: LocalDate, e: LocalDate, zs: java.time.ZonedDateTime?, ze: java.time.ZonedDateTime? ->
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
        )
    }
    val startDate = start.optString("date", "")
    val startDateTime = start.optString("dateTime", "")
    return when {
        startDate.isNotEmpty() -> {
            val s = LocalDate.parse(startDate)
            val endDate = end.optString("date", "").ifEmpty { null }
            // end.date は排他的（翌日）
            val e = endDate?.let { LocalDate.parse(it).minusDays(1) } ?: s
            common(true, s, e, null, null)
        }
        startDateTime.isNotEmpty() -> {
            val zs = OffsetDateTime.parse(startDateTime).atZoneSameInstant(zone)
            val ze = end.optString("dateTime", "").ifEmpty { null }
                ?.let { OffsetDateTime.parse(it).atZoneSameInstant(zone) } ?: zs
            common(false, zs.toLocalDate(), timedEndDate(zs, ze), zs, ze)
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
    val items = o.optJSONArray("items")
    val events = (0 until (items?.length() ?: 0)).mapNotNull { i ->
        googleEventToCalEvent(items!!.getJSONObject(i), calendar, zone)
    }
    return events to o.optString("nextPageToken", "").ifEmpty { null }
}

/** Calendar API v3 events.list を直接呼ぶ（読み取りのみ。トークンは呼び出し側がその場で渡し、保存しない）。 */
object EventsApi {
    private const val BASE = "https://www.googleapis.com/calendar/v3/calendars/"
    private const val FIELDS =
        "items(id,status,summary,start,end,colorId,location,recurringEventId),nextPageToken"
    private val RFC3339: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    class HttpError(val code: Int, message: String) : IOException(message)

    /** [calendar] の [window] 内の予定を全ページ取得する。 */
    suspend fun fetchWindow(
        accessToken: String,
        calendar: CalendarTab,
        window: EventWindow,
        zone: ZoneId,
    ): List<CalEvent> = withContext(Dispatchers.IO) {
        val timeMin = window.start.atStartOfDay(zone).toOffsetDateTime().format(RFC3339)
        val timeMax = window.endExclusive.atStartOfDay(zone).toOffsetDateTime().format(RFC3339)
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
    private val inFlight = HashSet<LocalDate>()

    /** reset のたびに増える。取得開始時の値と反映時の値が違えば、その結果は捨てる。 */
    var generation = 0L
        private set

    fun reset() {
        loaded.clear(); inFlight.clear(); generation++
    }

    /** 取得済み＋取得中の週。 */
    fun coveredWeeks(): Set<LocalDate> =
        (loaded.keys + inFlight).flatMapTo(HashSet()) { EventWindow(it).weekStarts }

    fun markInFlight(w: EventWindow) {
        inFlight += w.start
    }

    fun fail(w: EventWindow) {
        inFlight -= w.start
    }

    fun put(w: EventWindow, events: List<CalEvent>) {
        inFlight -= w.start
        loaded.remove(w.start)
        for (key in loaded.keys.toList()) {
            loaded[key] = loaded.getValue(key).filterNot { it.overlaps(w) }
        }
        loaded[w.start] = events // 末尾 = 最新
    }

    /** 予定の表示日がウィンドウ [start, endExclusive) にかかるか（events.list の timeMin/timeMax と同じ境界）。 */
    private fun CalEvent.overlaps(w: EventWindow): Boolean = startDate < w.endExclusive && endDateInclusive >= w.start

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
