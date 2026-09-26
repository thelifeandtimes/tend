package io.omabit.tend

import kotlinx.serialization.*
import kotlinx.serialization.json.*

val Wire = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = true
}

@Serializable
data class ShipAccount(val id: String, val ship: String, val url: String, val cookie: String)

@Serializable
data class AccountVault(
    val accounts: List<ShipAccount> = emptyList(),
    val activeId: String? = null,
)

interface CredentialStore {
    suspend fun read(): AccountVault

    suspend fun write(vault: AccountVault)
}

interface CacheStore {
    suspend fun read(account: String): TendState?

    suspend fun write(account: String, state: TendState)

    suspend fun remove(account: String)
}

@Serializable
data class TaskList(
    val id: Long,
    val title: String,
    val color: String = "#c5ccd6",
    val symbol: String = "",
    val revision: Long = 0,
    val sections: List<Section> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
)

@Serializable data class Section(val id: Long, val title: String, val rank: Long = 0)

@Serializable
data class Reminder(
    val id: Long,
    val title: String,
    val notes: String = "",
    val url: String? = null,
    val priority: String = "none",
    val flagged: Boolean = false,
    val tags: List<String> = emptyList(),
    @SerialName("parent-id") val parentId: Long? = null,
    @SerialName("section-id") val sectionId: Long? = null,
    val rank: Long = 0,
    val assignee: String? = null,
    val schedule: Schedule? = null,
    val completed: Boolean = false,
    val revision: Long = 0,
)

@Serializable data class MonthWeek(val index: Int, val weekday: Int)

@Serializable
data class RepeatRule(
    val frequency: String,
    val interval: Int = 1,
    val weekdays: List<Int> = emptyList(),
    @SerialName("month-days") val monthDays: List<Int> = emptyList(),
    @SerialName("month-week") val monthWeek: MonthWeek? = null,
    @SerialName("end-at") val endAt: String? = null,
    @SerialName("max-occurrences") val maxOccurrences: Long? = null,
)

@Serializable
data class Schedule(
    @SerialName("due-at") val dueAt: String,
    @SerialName("all-day") val allDay: Boolean = false,
    val timezone: String = "UTC",
    @SerialName("early-seconds") val earlySeconds: List<Long> = emptyList(),
    val recurrence: RepeatRule? = null,
    val occurrence: Long = 0,
)

@Serializable data class Member(val ship: String, val policy: JsonObject = JsonObject(emptyMap()))

@Serializable
data class Access(
    val alias: Long,
    val host: String,
    @SerialName("host-list-id") val hostListId: Long = 0,
    val status: String = "checking",
    val owner: Boolean = false,
    val members: List<Member> = emptyList(),
    val pending: List<String> = emptyList(),
) {
    val online
        get() = status.lowercase() == "online"

    fun assignees() = (listOf(host) + members.map { it.ship } + pending).distinct()
}

@Serializable
data class Invitation(
    val token: String,
    val host: String,
    val title: String,
    @SerialName("host-list-id") val hostListId: Long = 0,
)

@Serializable
data class PendingOperation(
    val id: String,
    val action: JsonObject,
    val since: Long,
    val state: String = "Sending",
)

@Serializable
data class TendState(
    val lists: List<TaskList> = emptyList(),
    val accesses: List<Access> = emptyList(),
    val invitations: List<Invitation> = emptyList(),
    val preferences: JsonObject = JsonObject(emptyMap()),
    val settings: JsonObject = JsonObject(emptyMap()),
    val pending: List<PendingOperation> = emptyList(),
    val syncedAt: Long = 0,
    val protocol: Int = 2,
) {
    fun list(id: Long) = lists.firstOrNull { it.id == id }

    fun access(id: Long) = accesses.firstOrNull { it.alias == id }
}

class ProtocolException(message: String) : Exception(message)

class AuthenticationException(message: String) : Exception(message)

class PendingConfirmation(message: String) : Exception(message)

/** Pure reducer: Gall data enters the application through one version-checked path. */
fun reduce(state: TendState, fact: JsonObject): TendState {
    fun settled(id: String?) = state.pending.filterNot { it.id == id }
    fun preferences(incoming: JsonElement?): JsonObject {
        val next = incoming as? JsonObject ?: return state.preferences
        val currentRevision = state.preferences["revision"]?.jsonPrimitive?.longOrNull ?: -1
        val nextRevision = next["revision"]?.jsonPrimitive?.longOrNull ?: -1
        return if (nextRevision < currentRevision) state.preferences else next
    }
    fact["snapshot"]?.jsonObject?.let { s ->
        val version = s["protocol-version"]?.jsonPrimitive?.intOrNull
        if (version != 2)
            throw ProtocolException(
                "This client needs Tend protocol 2. Update the ship's Tend desk before connecting."
            )
        return state.copy(
            lists = Wire.decodeFromJsonElement(s["lists"] ?: JsonArray(emptyList())),
            preferences = preferences(s["preferences"]),
            protocol = version,
        )
    }
    fact["list-upserted"]?.jsonObject?.let { body ->
        val list = Wire.decodeFromJsonElement<TaskList>(body.getValue("list"))
        val current = state.list(list.id)
        // A receipt can describe an older committed revision than a later streamed update.
        val lists =
            if (current != null && current.revision > list.revision) state.lists
            else state.lists.filterNot { it.id == list.id } + list
        return state.copy(
            lists = lists,
            preferences = preferences(body["preferences"]),
            pending = settled(body["operation-id"]?.jsonPrimitive?.content),
        )
    }
    fact["list-deleted"]?.jsonObject?.let { b ->
        return state.copy(
            lists = state.lists.filterNot { it.id == b["list-id"]?.jsonPrimitive?.long },
            pending = settled(b["operation-id"]?.jsonPrimitive?.content),
        )
    }
    fact["accesses"]?.let {
        return state.copy(accesses = Wire.decodeFromJsonElement(it))
    }
    fact["invitations-updated"]?.let {
        return state.copy(invitations = Wire.decodeFromJsonElement(it))
    }
    fact["local-settings-updated"]?.jsonObject?.let {
        return state.copy(settings = it)
    }
    fact["preferences-updated"]?.jsonObject?.let {
        return state.copy(
            preferences = preferences(it["preferences"]),
            pending = settled(it["operation-id"]?.jsonPrimitive?.content),
        )
    }
    fact["operation-pending"]?.jsonObject?.let { b ->
        return state.copy(
            pending =
                state.pending.map {
                    if (it.id == b["operation-id"]?.jsonPrimitive?.content)
                        it.copy(state = "Pending host")
                    else it
                }
        )
    }
    fact["rejected"]?.jsonObject?.let { b ->
        return state.copy(
            pending =
                state.pending.map {
                    if (it.id == b["operation-id"]?.jsonPrimitive?.content)
                        it.copy(state = "Rejected: ${b["reason"]?.jsonPrimitive?.content}")
                    else it
                }
        )
    }
    // operation-settled alone is not a canonical receipt; confirmation comes from receipt/fact.
    // Notification events intentionally remain unacknowledged at the Gall application layer.
    return state
}

data class ReminderRow(
    val list: TaskList,
    val reminder: Reminder,
    val depth: Int,
    val context: Boolean = false,
) {
    val key
        get() = "${list.id}:${reminder.id}"
}

enum class View(val label: String) {
    TODAY("Today"),
    ALL("All reminders"),
    FLAGGED("Flagged"),
    ASSIGNED("Assigned to me"),
    SCHEDULED("Scheduled"),
    COMPLETED("Completed"),
}

/** Parent-first traversal, including ancestors of matches. Iterative to tolerate deep trees. */
fun visibleRows(
    lists: List<TaskList>,
    view: View,
    ship: String,
    query: String = "",
    now: kotlin.time.Instant = kotlin.time.Clock.System.now(),
): List<ReminderRow> {
    val rows = mutableListOf<ReminderRow>()
    for (list in lists) {
        val byId = list.reminders.associateBy { it.id }
        val matches =
            list.reminders
                .filter { r ->
                    val matchesView =
                        when (view) {
                            View.COMPLETED -> r.completed
                            View.ALL -> !r.completed
                            View.FLAGGED -> !r.completed && r.flagged
                            View.ASSIGNED ->
                                !r.completed &&
                                    r.assignee?.removePrefix("~") == ship.removePrefix("~")
                            View.SCHEDULED -> !r.completed && r.schedule != null
                            View.TODAY ->
                                !r.completed && r.schedule?.let { isDueToday(it, now) } == true
                        }
                    matchesView &&
                        (query.isBlank() ||
                            (r.title + " " + r.notes + " " + r.tags.joinToString(" ")).contains(
                                query,
                                true,
                            ))
                }
                .mapTo(mutableSetOf()) { it.id }
        val included = matches.toMutableSet()
        for (id in matches) {
            var parent = byId[id]?.parentId
            val seen = mutableSetOf(id)
            while (parent != null && seen.add(parent)) {
                if (!included.add(parent)) break
                parent = byId[parent]?.parentId
            }
        }
        val grouped =
            list.reminders
                .filter { it.id in included }
                .groupBy { it.parentId?.takeIf { p -> p in included && p in byId } }
        val visited = mutableSetOf<Long>()
        fun append(root: Reminder) {
            val stack = ArrayDeque<Pair<Reminder, Int>>()
            stack.addLast(root to 0)
            while (stack.isNotEmpty()) {
                val (r, depth) = stack.removeLast()
                if (!visited.add(r.id)) continue
                rows += ReminderRow(list, r, depth, r.id !in matches)
                grouped[r.id]
                    .orEmpty()
                    .sortedWith(compareBy<Reminder> { it.rank }.thenBy { it.id })
                    .asReversed()
                    .forEach { stack.addLast(it to depth + 1) }
            }
        }
        val sectionOrder = list.sections.associate { it.id to it.rank }
        grouped[null]
            .orEmpty()
            .sortedWith(
                compareBy<Reminder> { it.sectionId?.let { s -> sectionOrder[s] } ?: Long.MIN_VALUE }
                    .thenBy { it.rank }
                    .thenBy { it.id }
            )
            .forEach(::append)
        // Malformed cycle/orphan data remains visible rather than disappearing.
        list.reminders.filter { it.id in included && it.id !in visited }.forEach(::append)
    }
    return rows
}
