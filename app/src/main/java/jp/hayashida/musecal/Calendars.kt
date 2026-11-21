package jp.hayashida.musecal

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * V1-2 カレンダー一覧とタブ対応。
 *
 * - [CalendarListApi] が Calendar API v3 の calendarList を REST で取得する（読み取りのみ）
 * - [mapCalendarTabs] が取得結果を下部タブ（[CalendarTab]）へ対応づける
 *
 * 各タブは対応する calendarId を持つ。予定（[CalEvent.calendarId]）の表示 ON/OFF は calendarId で判定するので、
 * V1-3 で events.list の結果に calendarId を付ければ、そのままタブ単位でフィルタできる。
 */

/** calendarList の1件（使う項目だけ）。 */
data class CalendarListEntry(
    val id: String,
    val summary: String,
    val summaryOverride: String? = null,
    /** "#rrggbb"。 */
    val backgroundColor: String? = null,
    val primary: Boolean = false,
)

/** 下部タブ1つ。[color] は色線（カレンダー色）。 */
data class CalendarTab(
    val calendarId: String,
    val label: String,
    val color: Color,
)

/** 既存の3タブの定義。対応づけは calendarId の完全一致で行う。 */
data class KnownTab(val calendarId: String, val label: String, val fallbackColor: Color)

/**
 * 既存タブと実カレンダーの対応の正本（calendarList の実物、2026-11-21 取得。
 * changes/2026-11-21-v1-google-sync/tasks.md の V1-3 に記録）。並びはタブの表示順。
 * [KnownTab.fallbackColor] は calendarList の backgroundColor が読めないときだけ使う。
 */
val KNOWN_TABS: List<KnownTab> = listOf(
    KnownTab("kou1995yurin530@gmail.com", "メイン", Color(0xFF9FE1E7)),
    KnownTab("mokuyoukai.ai@gmail.com", "AI木曜会", Color(0xFFFAD165)),
    KnownTab("ja.japanese#holiday@group.v.calendar.google.com", "日本の祝日", Color(0xFF9A9CFF)),
)

/** 色が読めない未知カレンダーの色線（灰）。 */
private val UnknownCalendarColor = Color(0xFF9E9E9E)

/**
 * calendarList の結果をタブへ対応づける。
 *
 * - 既存タブ（[known]）: calendarId が完全一致したエントリだけを対応づける（大文字小文字・前後空白の違いも別物）。
 *   ラベルは既存タブ名、色はエントリの backgroundColor（読めなければ [KnownTab.fallbackColor]）。
 *   並びは [known] の順。calendarList に無い既存タブは出さない（予定が来ない空のタブを見せないため）。
 * - 未知のカレンダー（既存タブのどれとも一致しない）: 既存タブの後ろに calendarList の順で追加する。
 *   ラベルは summaryOverride → summary → id の順で最初に空でないもの、色は backgroundColor（読めなければ灰）。
 * - 同じ id が重複して来た場合は最初の1件だけを使う。
 */
fun mapCalendarTabs(entries: List<CalendarListEntry>, known: List<KnownTab> = KNOWN_TABS): List<CalendarTab> {
    val byId = LinkedHashMap<String, CalendarListEntry>()
    for (e in entries) if (e.id.isNotEmpty()) byId.putIfAbsent(e.id, e)
    val knownIds = known.map { it.calendarId }.toSet()
    val knownTabs = known.mapNotNull { k ->
        val e = byId[k.calendarId] ?: return@mapNotNull null
        CalendarTab(k.calendarId, k.label, parseHexColor(e.backgroundColor) ?: k.fallbackColor)
    }
    val unknownTabs = byId.values.filter { it.id !in knownIds }.map { e ->
        val label = listOf(e.summaryOverride, e.summary, e.id).first { !it.isNullOrBlank() }!!
        CalendarTab(e.id, label, parseHexColor(e.backgroundColor) ?: UnknownCalendarColor)
    }
    return knownTabs + unknownTabs
}

/** 既存タブ名 → calendarId（同梱サンプル予定に calendarId を付けるため。V1-3 で実データに替われば不要）。 */
fun knownCalendarIdForName(name: String, known: List<KnownTab> = KNOWN_TABS): String? =
    known.firstOrNull { it.label == name }?.calendarId

/** "#rrggbb" / "#aarrggbb" を Color にする（android.graphics に依存しないので JVM 単体テストで使える）。 */
fun parseHexColor(hex: String?): Color? {
    val h = hex?.trim()?.removePrefix("#") ?: return null
    if (h.length != 6 && h.length != 8) return null
    val v = h.toLongOrNull(16) ?: return null
    return Color(if (h.length == 6) 0xFF000000 or v else v)
}

/** calendarList の1ページ分の JSON を読む。 */
fun parseCalendarListPage(json: String): Pair<List<CalendarListEntry>, String?> {
    val o = JSONObject(json)
    val items = o.optJSONArray("items")
    val entries = (0 until (items?.length() ?: 0)).mapNotNull { i ->
        val it = items!!.getJSONObject(i)
        val id = it.optString("id", "")
        if (id.isEmpty()) return@mapNotNull null
        CalendarListEntry(
            id = id,
            summary = it.optString("summary", ""),
            summaryOverride = it.optString("summaryOverride", "").ifEmpty { null },
            backgroundColor = it.optString("backgroundColor", "").ifEmpty { null },
            primary = it.optBoolean("primary", false),
        )
    }
    val next = o.optString("nextPageToken", "").ifEmpty { null }
    return entries to next
}

/** Calendar API v3 calendarList.list を直接呼ぶ（google-api-client は入れない。必要なのは GET 1種だけのため）。 */
object CalendarListApi {
    private const val ENDPOINT = "https://www.googleapis.com/calendar/v3/users/me/calendarList"
    private const val FIELDS = "items(id,summary,summaryOverride,backgroundColor,primary),nextPageToken"

    class HttpError(val code: Int, message: String) : IOException(message)

    suspend fun fetchAll(accessToken: String): List<CalendarListEntry> = withContext(Dispatchers.IO) {
        val all = mutableListOf<CalendarListEntry>()
        var pageToken: String? = null
        var pages = 0
        do {
            val query = buildString {
                append("?maxResults=250&fields=").append(URLEncoder.encode(FIELDS, "UTF-8"))
                pageToken?.let { append("&pageToken=").append(URLEncoder.encode(it, "UTF-8")) }
            }
            val (entries, next) = parseCalendarListPage(get(ENDPOINT + query, accessToken))
            all += entries
            pageToken = next
        } while (pageToken != null && ++pages < 20)
        all
    }

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
                throw HttpError(code, "calendarList の取得に失敗しました（HTTP $code）${body.take(200)}")
            }
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
