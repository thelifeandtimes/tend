@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.omabit.tend

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlinx.datetime.*
import kotlinx.serialization.json.*

@Composable
private fun SheetHeader(
    title: String,
    busy: Boolean,
    canSave: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onDismiss, enabled = !busy) {
            Icon(Icons.Outlined.Close, "Close editor")
        }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
        if (busy) CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
        else TextButton(onClick = onSave, enabled = canSave) { Text("Save") }
    }
}

@Composable
fun NewReminderSheet(
    repo: TendRepository,
    client: ClientState,
    initialList: Long?,
    parentId: Long?,
    onDismiss: () -> Unit,
    onOpen: (Long, Long) -> Unit,
) {
    var listId by rememberSaveable {
        mutableStateOf(
            initialList
                ?: client.data.preferences["default-list"]?.jsonPrimitive?.longOrNull
                ?: client.data.lists.firstOrNull()?.id
                ?: 0
        )
    }
    var title by rememberSaveable { mutableStateOf("") }
    var assignee by rememberSaveable { mutableStateOf(client.active?.ship.orEmpty()) }
    var due by
        rememberSaveable(stateSaver = jsonStateSaver<Schedule?>()) {
            mutableStateOf<Schedule?>(null)
        }
    var dateOpen by rememberSaveable { mutableStateOf(false) }
    val list = client.data.list(listId)
    val access = client.data.access(listId)
    val choices =
        listOf("" to "Unassigned") +
            access?.assignees().orEmpty().map {
                it to if (it == client.active?.ship) "$it (me)" else it
            }
    val selectedAssignee = assignee.takeIf { a -> choices.any { it.first == a } }.orEmpty()
    val save: () -> Unit = {
        repo.launch {
            try {
                addReminder(
                    listId,
                    title,
                    selectedAssignee.ifBlank { null },
                    due,
                    parentId,
                    list?.reminders?.firstOrNull { it.id == parentId }?.sectionId,
                )
                onDismiss()
            } catch (e: PartialCreateException) {
                onOpen(listId, e.reminderId)
                throw e
            }
        }
    }
    ModalBottomSheet(
        properties = tendSheetProperties(),
        onDismissRequest = { if (!client.busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        TendSheetSystemBars()
        Column(
            Modifier.fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SheetHeader(
                if (parentId != null) "New subtask" else "A new reminder",
                client.busy,
                repo.canEdit(listId) && title.isNotBlank(),
                onDismiss,
                save,
            )
            if (parentId != null)
                Text(
                    "Under ${list?.reminders?.firstOrNull {it.id==parentId}?.title.orEmpty()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            ChoiceField(
                "List",
                listId.toString(),
                client.data.lists.map { it.id.toString() to it.title },
                { listId = it.toLong() },
                enabled = parentId == null && !client.busy,
            )
            OutlinedTextField(
                title,
                { title = it },
                Modifier.fillMaxWidth(),
                label = { Text("What needs tending?") },
                minLines = 2,
                maxLines = 5,
                shape = RoundedCornerShape(16.dp),
                enabled = !client.busy,
            )
            ChoiceField(
                "Assigned to",
                selectedAssignee,
                choices,
                { assignee = it },
                enabled = !client.busy,
            )
            ScheduleButton(
                due,
                onClick = { dateOpen = true },
                onClear = { due = null },
                enabled = !client.busy,
            )
            if (!repo.canEdit(listId))
                Text(
                    "This list is read-only until both your ship and the list host are connected. Your draft stays here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            Button(
                onClick = save,
                enabled = repo.canEdit(listId) && title.isNotBlank() && !client.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(if (client.busy) "Saving…" else "Add reminder")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (dateOpen)
        ScheduleEditor(
            due,
            onDismiss = { dateOpen = false },
            onSave = {
                due = it
                dateOpen = false
            },
            allDayMinute =
                client.data.preferences["all-day-alert-minute"]?.jsonPrimitive?.intOrNull ?: 540,
        )
}

@Composable
fun ReminderSheet(
    repo: TendRepository,
    client: ClientState,
    list: TaskList,
    reminder: Reminder,
    onDismiss: () -> Unit,
    onChild: () -> Unit,
) {
    // Drafts are keyed by identity, never by streamed revision; remote events cannot erase edits.
    var baseline by
        rememberSaveable(reminder.id, stateSaver = jsonStateSaver<Reminder>()) {
            mutableStateOf(reminder)
        }
    var title by rememberSaveable(reminder.id) { mutableStateOf(reminder.title) }
    var notes by rememberSaveable(reminder.id) { mutableStateOf(reminder.notes) }
    var url by rememberSaveable(reminder.id) { mutableStateOf(reminder.url.orEmpty()) }
    var priority by rememberSaveable(reminder.id) { mutableStateOf(reminder.priority) }
    var flagged by rememberSaveable(reminder.id) { mutableStateOf(reminder.flagged) }
    var tags by rememberSaveable(reminder.id) { mutableStateOf(reminder.tags.joinToString(", ")) }
    var assignee by rememberSaveable(reminder.id) { mutableStateOf(reminder.assignee.orEmpty()) }
    var section by
        rememberSaveable(reminder.id) { mutableStateOf(reminder.sectionId?.toString().orEmpty()) }
    var parent by
        rememberSaveable(reminder.id) { mutableStateOf(reminder.parentId?.toString().orEmpty()) }
    var schedule by
        rememberSaveable(reminder.id, stateSaver = jsonStateSaver<Schedule?>()) {
            mutableStateOf(reminder.schedule)
        }
    var dateOpen by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val editable = repo.canEdit(list.id) && !client.busy
    val changed = reminder.revision != baseline.revision
    val draft =
        baseline.copy(
            title = title,
            notes = notes,
            url = url.ifBlank { null },
            priority = priority,
            flagged = flagged,
            tags =
                tags
                    .split(',')
                    .map { it.trim().removePrefix("#") }
                    .filter { it.isNotEmpty() }
                    .distinct(),
            assignee = assignee.ifBlank { null },
        )
    val save: () -> Unit = {
        repo.launch {
            check(
                state.value.data
                    .list(list.id)
                    ?.reminders
                    ?.firstOrNull { it.id == reminder.id }
                    ?.revision == baseline.revision
            ) {
                "This reminder changed on the ship while you were editing. Close and reopen it to review the current version; your draft has not been sent."
            }
            if (metadata(draft) != metadata(baseline)) {
                update(list.id, draft)
                baseline = state.value.data.list(list.id)!!.reminders.first { it.id == reminder.id }
            }
            if (schedule != baseline.schedule) {
                schedule(list.id, reminder.id, schedule)
                baseline = state.value.data.list(list.id)!!.reminders.first { it.id == reminder.id }
            }
            if (
                parent.toLongOrNull() != baseline.parentId ||
                    section.toLongOrNull() != baseline.sectionId
            )
                move(
                    list.id,
                    reminder.id,
                    parent.toLongOrNull(),
                    section.toLongOrNull(),
                    baseline.rank,
                )
            onDismiss()
        }
    }
    ModalBottomSheet(
        properties = tendSheetProperties(),
        onDismissRequest = { if (!client.busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        TendSheetSystemBars()
        Column(
            Modifier.fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetHeader(
                "Tend to this",
                client.busy,
                editable && title.isNotBlank() && !changed,
                onDismiss,
                save,
            )
            Text(
                list.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (changed)
                Text(
                    "This reminder was updated elsewhere. Your draft is preserved; reopen the reminder before saving.",
                    color = MaterialTheme.colorScheme.error,
                )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    reminder.completed,
                    {
                        repo.launch {
                            complete(list.id, reminder)
                            baseline =
                                state.value.data.list(list.id)!!.reminders.first {
                                    it.id == reminder.id
                                }
                            schedule = baseline.schedule
                        }
                    },
                    enabled = editable && !changed,
                )
                Text(if (reminder.completed) "Completed" else "Mark complete", Modifier.weight(1f))
                IconToggleButton(flagged, { flagged = it }) {
                    Icon(
                        Icons.Outlined.Flag,
                        "Flag reminder",
                        tint =
                            if (flagged) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedTextField(
                title,
                { title = it },
                Modifier.fillMaxWidth(),
                label = { Text("Title") },
                minLines = 1,
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
                enabled = !client.busy,
            )
            OutlinedTextField(
                notes,
                { notes = it },
                Modifier.fillMaxWidth(),
                label = { Text("Notes") },
                minLines = 3,
                maxLines = 8,
                shape = RoundedCornerShape(16.dp),
                enabled = !client.busy,
            )
            ScheduleButton(schedule, { dateOpen = true }, { schedule = null }, !client.busy)
            ChoiceField(
                "Assigned to",
                assignee,
                listOf("" to "Unassigned") +
                    client.data.access(list.id)?.assignees().orEmpty().map {
                        it to if (it == client.active?.ship) "$it (me)" else it
                    },
                { assignee = it },
                enabled = !client.busy,
            )
            ChoiceField(
                "Priority",
                priority,
                listOf(
                    "none" to "No priority",
                    "low" to "Low",
                    "medium" to "Medium",
                    "high" to "High",
                ),
                { priority = it },
                enabled = !client.busy,
            )
            OutlinedTextField(
                tags,
                { tags = it },
                Modifier.fillMaxWidth(),
                label = { Text("Tags, separated by commas") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                enabled = !client.busy,
            )
            OutlinedTextField(
                url,
                { url = it },
                Modifier.fillMaxWidth(),
                label = { Text("Link") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = RoundedCornerShape(16.dp),
                enabled = !client.busy,
            )
            HorizontalDivider()
            val descendants =
                remember(list.reminders, reminder.id) { descendantIds(list, reminder.id) }
            ChoiceField(
                "Parent reminder",
                parent,
                listOf("" to "Top-level reminder") +
                    list.reminders
                        .filter { it.id != reminder.id && it.id !in descendants }
                        .map { it.id.toString() to it.title },
                {
                    parent = it
                    section =
                        list.reminders
                            .firstOrNull { r -> r.id == it.toLongOrNull() }
                            ?.sectionId
                            ?.toString()
                            .orEmpty()
                },
                enabled = !client.busy,
            )
            ChoiceField(
                "Section",
                section,
                listOf("" to "No section") + list.sections.map { it.id.toString() to it.title },
                { section = it },
                enabled = !client.busy && parent.isEmpty(),
            )
            if (descendants.isNotEmpty())
                Text(
                    "${descendants.size} subtask${if(descendants.size==1) "" else "s"} · Completing a parent also completes its children.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            OutlinedButton(
                onClick = onChild,
                enabled = editable,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            ) {
                Icon(Icons.Outlined.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add a subtask")
            }
            TextButton(
                onClick = { deleting = true },
                enabled = editable,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text("Delete reminder", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (dateOpen)
        ScheduleEditor(
            schedule,
            { dateOpen = false },
            {
                schedule = it
                dateOpen = false
            },
            allDayMinute =
                client.data.preferences["all-day-alert-minute"]?.jsonPrimitive?.intOrNull ?: 540,
        )
    if (deleting)
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete this reminder?") },
            text = {
                Text("This also deletes all of its subtasks on the ship. This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        repo.launch {
                            mutate(
                                "delete-reminder",
                                list.id,
                                buildJsonObject { put("reminder-id", reminder.id) },
                            )
                            deleting = false
                            onDismiss()
                        }
                    },
                    enabled = !client.busy,
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
}

fun descendantIds(list: TaskList, id: Long): Set<Long> {
    val grouped = list.reminders.groupBy { it.parentId }
    val result = mutableSetOf<Long>()
    val stack = ArrayDeque<Long>()
    stack.add(id)
    while (stack.isNotEmpty()) grouped[stack.removeLast()].orEmpty().forEach {
        if (it.id != id && result.add(it.id)) stack.add(it.id)
    }
    return result
}

@Composable
private fun ScheduleButton(
    schedule: Schedule?,
    onClick: () -> Unit,
    onClear: () -> Unit,
    enabled: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            shape = RoundedCornerShape(16.dp),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    containerColor =
                        if (schedule != null) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                ),
        ) {
            Icon(
                if (schedule?.recurrence != null) Icons.Outlined.EventRepeat
                else Icons.Outlined.CalendarToday,
                "Choose due date",
                Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(schedule?.let(::dueLabel) ?: "Set a due date")
                schedule?.recurrence?.let {
                    Text(recurrenceLabel(it), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (schedule != null)
            IconButton(onClick = onClear, enabled = enabled) {
                Icon(Icons.Outlined.Close, "Clear due date")
            }
    }
}

@Composable
fun ScheduleEditor(
    initial: Schedule?,
    onDismiss: () -> Unit,
    onSave: (Schedule?) -> Unit,
    allDayMinute: Int = 540,
) {
    val tz = remember {
        runCatching { TimeZone.of(initial?.timezone ?: TimeZone.currentSystemDefault().id) }
            .getOrElse { TimeZone.currentSystemDefault() }
            .id
    }
    val initialWall = remember {
        initial?.let { runCatching { localDue(it) }.getOrNull() }
            ?: Clock.System.now().toLocalDateTime(TimeZone.of(tz))
    }
    var date by
        rememberSaveable(stateSaver = jsonStateSaver<LocalDate>()) {
            mutableStateOf(initialWall.date)
        }
    var hour by rememberSaveable { mutableStateOf(initialWall.hour.toString()) }
    var minute by rememberSaveable { mutableStateOf(initialWall.minute.toString()) }
    var allDay by rememberSaveable { mutableStateOf(initial?.allDay ?: false) }
    var frequency by rememberSaveable { mutableStateOf(initial?.recurrence?.frequency ?: "none") }
    var interval by rememberSaveable {
        mutableStateOf((initial?.recurrence?.interval ?: 1).toString())
    }
    var week by
        rememberSaveable(stateSaver = jsonStateSaver<Set<Int>>()) {
            mutableStateOf(initial?.recurrence?.weekdays.orEmpty().toSet())
        }
    var monthDays by rememberSaveable {
        mutableStateOf(initial?.recurrence?.monthDays.orEmpty().joinToString(","))
    }
    var endCount by rememberSaveable {
        mutableStateOf(initial?.recurrence?.maxOccurrences?.toString().orEmpty())
    }
    var calendar by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (calendar) "Choose a day" else "A time for this",
                style = MaterialTheme.typography.headlineMedium,
            )
        },
        text = {
            if (calendar) {
                MonthCalendar(date) { date = it }
            } else {
                Column(
                    Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    OutlinedButton(
                        onClick = { calendar = true },
                        modifier =
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics {
                                contentDescription = "Choose calendar day"
                            },
                    ) {
                        Icon(Icons.Outlined.CalendarMonth, null)
                        Spacer(Modifier.width(10.dp))
                        Text(date.toString())
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(allDay, { allDay = it })
                        Text("All day")
                    }
                    if (!allDay)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                hour,
                                { hour = it.filter(Char::isDigit).take(2) },
                                Modifier.weight(1f),
                                label = { Text("Hour (24h)") },
                                singleLine = true,
                                keyboardOptions =
                                    KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                            OutlinedTextField(
                                minute,
                                { minute = it.filter(Char::isDigit).take(2) },
                                Modifier.weight(1f),
                                label = { Text("Minute") },
                                singleLine = true,
                                keyboardOptions =
                                    KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                        }
                    Text(
                        tz,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ChoiceField(
                        "Repeat",
                        frequency,
                        listOf(
                            "none" to "Does not repeat",
                            "hourly" to "Hourly",
                            "daily" to "Daily",
                            "weekly" to "Weekly",
                            "monthly" to "Monthly",
                            "yearly" to "Yearly",
                        ),
                        { frequency = it },
                    )
                    if (frequency != "none") {
                        OutlinedTextField(
                            interval,
                            { interval = it.filter(Char::isDigit).take(5) },
                            label = { Text("Every how many?") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (frequency == "weekly")
                            Column {
                                Text("Weekdays", style = MaterialTheme.typography.labelMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                                        .forEachIndexed { day, name ->
                                            FilterChip(
                                                selected = day in week,
                                                onClick = {
                                                    week =
                                                        if (day in week) week - day else week + day
                                                },
                                                label = { Text(name) },
                                            )
                                        }
                                }
                            }
                        if (frequency == "monthly" || frequency == "yearly")
                            OutlinedTextField(
                                monthDays,
                                { monthDays = it },
                                label = { Text("Month dates (optional, e.g. 1,15)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        OutlinedTextField(
                            endCount,
                            { endCount = it.filter(Char::isDigit).take(6) },
                            label = { Text("Stop after occurrences (optional)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (initial?.recurrence?.endAt != null)
                            Text(
                                "Existing end date is preserved: ${initial.recurrence.endAt}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        if (initial?.recurrence?.monthWeek != null)
                            Text(
                                "Existing ordinal weekday rule is preserved unless month dates are entered.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                    }
                    error?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (calendar) calendar = false
                    else
                        try {
                            val alertMinute =
                                if (initial?.allDay == true)
                                    initialWall.hour * 60 + initialWall.minute
                                else allDayMinute.coerceIn(0, 1439)
                            val h = if (allDay) alertMinute / 60 else hour.toInt()
                            val m = if (allDay) alertMinute % 60 else minute.toInt()
                            require(h in 0..23 && m in 0..59) { "Enter a valid hour and minute" }
                            val wall = LocalDateTime(date, LocalTime(h, m))
                            val instant = wall.toInstant(TimeZone.of(tz))
                            require(instant.toLocalDateTime(TimeZone.of(tz)) == wall) {
                                "That time does not exist during the daylight-saving change. Choose another time."
                            }
                            val n = interval.toInt()
                            require(n in 1..10000) {
                                "Repeat interval must be between 1 and 10,000"
                            }
                            val days =
                                monthDays
                                    .split(',')
                                    .filter { it.isNotBlank() }
                                    .map { it.trim().toInt() }
                                    .distinct()
                            require(days.all { it in 1..31 }) { "Month dates must be 1–31" }
                            val count = endCount.toLongOrNull()
                            require(endCount.isBlank() || count != null && count > 0) {
                                "Occurrence count must be positive"
                            }
                            val repeat =
                                if (frequency == "none") null
                                else
                                    RepeatRule(
                                        frequency,
                                        n,
                                        if (frequency == "weekly") week.sorted() else emptyList(),
                                        if (frequency in listOf("monthly", "yearly")) days
                                        else emptyList(),
                                        if (frequency == "monthly" && days.isEmpty())
                                            initial?.recurrence?.monthWeek
                                        else null,
                                        initial?.recurrence?.endAt,
                                        count,
                                    )
                            onSave(
                                Schedule(
                                    wireDate(instant),
                                    allDay,
                                    tz,
                                    initial?.earlySeconds.orEmpty(),
                                    repeat,
                                    initial?.occurrence ?: 0,
                                )
                            )
                        } catch (e: Exception) {
                            error = e.message ?: "Check the date and repeat rule"
                        }
                }
            ) {
                Text(if (calendar) "Choose" else "Set date")
            }
        },
        dismissButton = {
            TextButton(onClick = { if (calendar) calendar = false else onDismiss() }) {
                Text(if (calendar) "Back" else "Cancel")
            }
        },
    )
}

/** Flexible calendar in the same dialog: no stacked windows or fixed 360dp grid. */
@Composable
private fun MonthCalendar(selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    var month by remember { mutableStateOf(LocalDate(selected.year, selected.month, 1)) }
    val title = month.month.name.lowercase().replaceFirstChar { it.uppercase() } + " ${month.year}"
    val offset = month.dayOfWeek.isoDayNumber % 7
    val count = month.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day
    Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minus(1, DateTimeUnit.MONTH) }) {
                Icon(Icons.Outlined.ChevronLeft, "Previous month")
            }
            Text(
                title,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = { month = month.plus(1, DateTimeUnit.MONTH) }) {
                Icon(Icons.Outlined.ChevronRight, "Next month")
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                Text(
                    it,
                    Modifier.weight(1f).padding(vertical = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        repeat((offset + count + 6) / 7) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { weekday ->
                    val day = week * 7 + weekday - offset + 1
                    if (day !in 1..count) Spacer(Modifier.weight(1f).height(44.dp))
                    else {
                        val value = LocalDate(month.year, month.month, day)
                        TextButton(
                            onClick = { onSelect(value) },
                            modifier =
                                Modifier.weight(1f).height(44.dp).semantics {
                                    contentDescription = "Select $value"
                                },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors =
                                ButtonDefaults.textButtonColors(
                                    containerColor =
                                        if (value == selected) MaterialTheme.colorScheme.primary
                                        else androidx.compose.ui.graphics.Color.Transparent,
                                    contentColor =
                                        if (value == selected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface,
                                ),
                        ) {
                            Text(day.toString())
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Selected: $selected",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ListSheet(repo: TendRepository, client: ClientState, list: TaskList, onDismiss: () -> Unit) {
    var title by rememberSaveable(list.id) { mutableStateOf(list.title) }
    var color by rememberSaveable(list.id) { mutableStateOf(list.color) }
    var invite by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }
    val access = client.data.access(list.id)
    val editable = repo.canEdit(list.id) && !client.busy
    ModalBottomSheet(onDismissRequest = onDismiss, properties = tendSheetProperties()) {
        TendSheetSystemBars()
        Column(
            Modifier.fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("About this list", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Hosted by ${access?.host?:"unknown"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                title,
                { title = it },
                Modifier.fillMaxWidth(),
                label = { Text("List title") },
                singleLine = true,
                enabled = editable,
            )
            ChoiceField(
                "List color",
                color,
                listOf(
                    "#c5ccd6" to "Silver",
                    "#7d9a7a" to "Sage",
                    "#8ba5c6" to "Blue",
                    "#cbb679" to "Gold",
                    "#c45c4a" to "Terracotta",
                    "#b393b9" to "Orchid",
                ),
                { color = it },
                enabled = editable,
            )
            Button(
                onClick = {
                    repo.launch {
                        mutate(
                            "update-list",
                            list.id,
                            buildJsonObject {
                                put("title", checkedTitle(title))
                                put("color", color)
                                put("symbol", list.symbol)
                            },
                        )
                        onDismiss()
                    }
                },
                enabled = editable && title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save appearance")
            }
            HorizontalDivider()
            Text("People", style = MaterialTheme.typography.titleMedium)
            access?.assignees()?.forEach { ship ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ship, Modifier.weight(1f))
                    Text(
                        if (ship == access.host) "Host"
                        else if (ship in access.pending) "Invited" else "Member",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (access?.owner == true) {
                OutlinedTextField(
                    invite,
                    { invite = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Invite a ship, e.g. ~sampel-palnet") },
                    singleLine = true,
                    enabled = editable,
                )
                OutlinedButton(
                    onClick = {
                        repo.launch {
                            mutate(
                                "invite-member",
                                fields =
                                    buildJsonObject {
                                        put("list-id", list.id)
                                        put(
                                            "ship",
                                            if (invite.startsWith("~")) invite.trim()
                                            else "~${invite.trim()}",
                                        )
                                        put("can-invite", false)
                                    },
                            )
                            invite = ""
                        }
                    },
                    enabled = editable && invite.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Invite to list")
                }
                TextButton(
                    onClick = { deleting = true },
                    enabled = editable,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Delete list", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (deleting)
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete “${list.title}”?") },
            text = {
                Text(
                    "Permanently deletes the list, its reminders, and sharing access. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        repo.launch {
                            mutate("delete-list", list.id)
                            deleting = false
                            onDismiss()
                        }
                    },
                    enabled = !client.busy,
                ) {
                    Text("Delete list", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
}
