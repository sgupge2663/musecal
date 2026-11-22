package jp.hayashida.musecal

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONObject

/** end はフォーム上では終日の最終日（API への変換時だけ翌日にする）。 */
data class EventDraft(
    val title: String = "",
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean = false,
    val calendarId: String,
    val colorId: String? = null,
    val location: String = "",
    val description: String = "",
) {
    fun valid(): Boolean = if (allDay) end.toLocalDate() >= start.toLocalDate() else end > start
}

fun writableCalendars(entries: List<CalendarListEntry>): List<CalendarTab> =
    mapCalendarTabs(entries.filter { it.accessRole == "owner" || it.accessRole == "writer" })

fun createEventBody(d: EventDraft, zone: ZoneId): JSONObject {
    require(d.valid())
    fun endpoint(value: LocalDateTime, end: Boolean): JSONObject = JSONObject().apply {
        if (d.allDay) put("date", (if (end) value.toLocalDate().plusDays(1) else value.toLocalDate()).toString())
        else {
            put("dateTime", value.atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            put("timeZone", zone.id)
        }
    }
    return JSONObject().apply {
        put("summary", d.title)
        put("start", endpoint(d.start, false))
        put("end", endpoint(d.end, true))
        put("location", d.location)
        put("description", d.description)
        d.colorId?.let { put("colorId", it) }
    }
}

fun CalEvent.toDraft(): EventDraft = EventDraft(
    title, start?.toLocalDateTime() ?: startDate.atStartOfDay(),
    end?.toLocalDateTime() ?: endDateInclusive.atStartOfDay(), allDay,
    calendarId, colorId, location, description,
)

fun updateEventBody(current: JSONObject, draft: EventDraft, zone: ZoneId): JSONObject {
    val result = JSONObject(current.toString())
    val edited = createEventBody(draft, zone)
    edited.keys().forEach { result.put(it, edited.get(it)) }
    if (draft.colorId == null) result.remove("colorId")
    return result
}

internal fun writeFailureMessage(error: Throwable): String =
    fetchFailureMessage(error).replace("読み取り", "書き込み").replace("から取得", "に保存").replace("取得", "保存")

/** 作成・後続の編集で使うフォーム。通信と画面遷移は呼び出し側が持つ。 */
@Composable
internal fun EventEditor(
    initial: EventDraft,
    calendars: List<CalendarTab>,
    saving: Boolean = false,
    error: String? = null,
    editing: Boolean = false,
    readOnly: Boolean = false,
    calendarLocked: Boolean = false,
    onCancel: () -> Unit,
    onSave: (EventDraft) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var startText by rememberSaveable { mutableStateOf(initial.start.toString()) }
    var endText by rememberSaveable { mutableStateOf(initial.end.toString()) }
    var allDay by rememberSaveable { mutableStateOf(initial.allDay) }
    var calendarId by rememberSaveable { mutableStateOf(initial.calendarId) }
    var colorId by rememberSaveable { mutableStateOf(initial.colorId) }
    var location by rememberSaveable { mutableStateOf(initial.location) }
    var description by rememberSaveable { mutableStateOf(initial.description) }
    val start = LocalDateTime.parse(startText)
    val end = LocalDateTime.parse(endText)
    val draft = EventDraft(title, start, end, allDay, calendarId, colorId, location, description)
    BackHandler { if (!saving) onCancel() }
    Surface(Modifier.fillMaxSize(), color = Color.White) {
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().background(Color.Black).padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onCancel, enabled = !saving) { Text("キャンセル", color = Color.White) }
            Text(if (readOnly) "予定の詳細" else if (editing) "予定の編集" else "予定の作成", color = Color.White, modifier = Modifier.padding(top = 12.dp))
            TextButton(onClick = { onSave(draft) },
                enabled = !readOnly && !saving && draft.valid() && calendars.any { it.calendarId == calendarId }) {
                Text(if (saving) "保存中…" else if (error != null) "再試行" else "保存", color = Color.White)
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (readOnly) Text("読み取り専用の予定です。")
            if (error != null) Text(error, color = Color(0xFF993300))
            OutlinedTextField(title, { title = it }, label = { Text("タイトル") }, enabled = !saving && !readOnly, modifier = Modifier.fillMaxWidth())
            Row { Text("終日", modifier = Modifier.weight(1f).padding(top = 12.dp)); Switch(allDay, { allDay = it }, enabled = !saving && !readOnly) }
            DateTimeInput("開始", start, allDay, !saving && !readOnly) { startText = it.toString() }
            DateTimeInput("終了", end, allDay, !saving && !readOnly) { endText = it.toString() }
            if (!draft.valid()) Text("終了は開始より後にしてください（終日は同日可）。", color = Color.Red)
            EditorChoice("カレンダー", calendars.firstOrNull { it.calendarId == calendarId }?.label ?: "選択してください", !saving && !readOnly && !calendarLocked) {
                calendars.forEach { c -> DropdownMenuItem(text = { Text(c.label) }, onClick = { calendarId = c.calendarId; it() }) }
            }
            if (calendars.isEmpty()) Text("書き込み可能なカレンダーがありません。")
            EditorChoice("色", colorId?.let { "● 色 $it" } ?: "色なし（カレンダー色）", !saving && !readOnly) { close ->
                DropdownMenuItem(text = { Text("色なし（カレンダー色）") }, onClick = { colorId = null; close() })
                GoogleEventColors.forEach { (id, color) ->
                    DropdownMenuItem(text = { Text("● 色 $id", color = color) }, onClick = { colorId = id; close() })
                }
            }
            OutlinedTextField(location, { location = it }, label = { Text("場所") }, enabled = !saving && !readOnly, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text("説明") }, enabled = !saving && !readOnly, minLines = 3, modifier = Modifier.fillMaxWidth())
        }
    }
    }
}

@Composable
private fun EditorChoice(label: String, value: String, enabled: Boolean, items: @Composable (() -> Unit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("$label: $value") }
        DropdownMenu(expanded, { expanded = false }) { items { expanded = false } }
    }
}

@Composable
private fun DateTimeInput(label: String, value: LocalDateTime, allDay: Boolean, enabled: Boolean, onChange: (LocalDateTime) -> Unit) {
    val context = LocalContext.current
    Row {
        Text(label, modifier = Modifier.padding(top = 12.dp, end = 8.dp))
        OutlinedButton(onClick = {
            DatePickerDialog(context, { _, y, m, d -> onChange(value.withDayOfMonth(1).withYear(y).withMonth(m + 1).withDayOfMonth(d)) },
                value.year, value.monthValue - 1, value.dayOfMonth).show()
        }, enabled = enabled) { Text(value.toLocalDate().toString()) }
        if (!allDay) OutlinedButton(onClick = {
            TimePickerDialog(context, { _, h, m -> onChange(value.withHour(h).withMinute(m)) }, value.hour, value.minute, true).show()
        }, enabled = enabled, modifier = Modifier.padding(start = 8.dp)) { Text(value.format(DateTimeFormatter.ofPattern("HH:mm"))) }
    }
}
