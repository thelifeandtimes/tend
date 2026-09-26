package io.omabit.tend

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlinx.serialization.json.*
import kotlin.random.Random

enum class Connection { OFFLINE, CHECKING, ONLINE, SIGN_IN, INCOMPATIBLE }
data class ClientState(
    val accounts: List<ShipAccount> = emptyList(), val active: ShipAccount? = null,
    val data: TendState = TendState(), val connection: Connection = Connection.OFFLINE,
    val busy: Boolean = false, val error: String? = null, val initialized: Boolean = false,
)
fun operationId(): String = "mobile-" + Clock.System.now().toEpochMilliseconds().toString(16) + "-" + Random.nextBytes(16).joinToString("") { (it.toInt() and 255).toString(16).padStart(2,'0') }

class TendRepository(private val api: Eyre, private val credentials: CredentialStore, private val cache: CacheStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutable = MutableStateFlow(ClientState())
    val state: StateFlow<ClientState> = mutable.asStateFlow()
    private val dataLock = Mutex()
    private val writeLock = Mutex()
    private var sync: Job? = null
    private var foreground = true
    private var generation = 0L
    init { scope.launch { try { val vault = credentials.read(); mutable.update { it.copy(accounts = vault.accounts, initialized = true) }; vault.accounts.firstOrNull { it.id == vault.activeId }?.let { select(it) } } catch (_: Exception) { mutable.update { it.copy(initialized = true, error = "Stored sessions could not be unlocked. Sign in again; your ship data is safe.") } } } }
    fun dismissError() { mutable.update { it.copy(error = null) } }
    fun report(message: String) { mutable.update { it.copy(error = message) } }
    fun launch(block: suspend TendRepository.() -> Unit) = scope.launch {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { report(e.message ?: "The operation could not be completed") }
    }
    suspend fun login(url: String, code: String) {
        mutable.update { it.copy(busy = true, error = null) }
        try {
            val (account, data) = api.login(url, code)
            val all = state.value.accounts.filterNot { it.id == account.id } + account
            // Reauthentication must not erase the durable ambiguity journal.
            val previous = cache.read(account.id)
            cache.write(account.id, data.copy(pending = previous?.pending.orEmpty(), settings = previous?.settings ?: data.settings))
            credentials.write(AccountVault(all,account.id))
            mutable.update { it.copy(accounts = all) }
            select(account)
        } finally { mutable.update { it.copy(busy = false) } }
    }
    suspend fun select(account: ShipAccount) {
        writeLock.withLock {
            generation++; sync?.cancelAndJoin()
            mutable.update { it.copy(active = account, data = TendState(), connection = Connection.CHECKING, error = null) }
            credentials.write(AccountVault(state.value.accounts,account.id))
            val cached = cache.read(account.id) ?: TendState()
            mutable.update { it.copy(data = cached) }
            startSync()
        }
    }
    suspend fun forget(account: ShipAccount) {
        writeLock.withLock {
            if (state.value.active?.id == account.id) { generation++; sync?.cancelAndJoin(); mutable.update { it.copy(active = null, data = TendState(), connection = Connection.OFFLINE) } }
            val all = state.value.accounts.filterNot { it.id == account.id }
            credentials.write(AccountVault(all, state.value.active?.id))
            cache.remove(account.id)
            mutable.update { it.copy(accounts = all) }
        }
        if (state.value.active == null) state.value.accounts.firstOrNull()?.let { select(it) }
    }
    fun setForeground(value: Boolean) {
        foreground = value
        if (value) startSync() else { sync?.cancel(); mutable.update { it.copy(connection = Connection.OFFLINE) } }
    }
    fun reconnect() { sync?.cancel(); sync = null; startSync() }
    private fun startSync() {
        val account = state.value.active ?: return
        if (!foreground || sync?.isActive == true) return
        val token = generation
        sync = scope.launch {
            var delayMs = 1000L
            while (isActive && token == generation) {
                mutable.update { it.copy(connection = Connection.CHECKING) }
                try {
                    apply(account,api.scry(account,"state").jsonObject)
                    apply(account,api.scry(account,"accesses").jsonObject)
                    apply(account,api.scry(account,"invitations").jsonObject)
                    apply(account,api.scry(account,"settings").jsonObject)
                    recover(account)
                    var snapshot = false; var accesses = false
                    coroutineScope {
                    val recovery = launch { while (isActive) { delay(5000); recover(account) } }
                    try { api.subscribe(account,operationId()) { fact ->
                        if (token != generation) return@subscribe
                        apply(account,fact)
                        snapshot = snapshot || fact.containsKey("snapshot")
                        accesses = accesses || fact.containsKey("accesses")
                        if (snapshot && accesses) { mutable.update { it.copy(connection = Connection.ONLINE) }; delayMs = 1000 }
                    } } finally { recovery.cancel() }
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: AuthenticationException) { mutable.update { it.copy(connection = Connection.SIGN_IN, error = e.message) }; break }
                catch (e: ProtocolException) { mutable.update { it.copy(connection = Connection.INCOMPATIBLE, error = e.message) }; break }
                catch (_: Exception) { mutable.update { it.copy(connection = Connection.OFFLINE) } }
                delay(delayMs + Random.nextLong(250)); delayMs = minOf(delayMs * 2,30000)
            }
        }
    }
    private suspend fun apply(account: ShipAccount, fact: JsonObject) = dataLock.withLock {
        if (state.value.active?.id != account.id) return@withLock
        val reduced = reduce(state.value.data,fact)
        if (reduced == state.value.data) return@withLock
        val updated = reduced.copy(syncedAt = Clock.System.now().toEpochMilliseconds())
        cache.write(account.id,updated)
        mutable.update { it.copy(data = updated) }
    }
    private suspend fun pending(account: ShipAccount, change: (List<PendingOperation>) -> List<PendingOperation>) = dataLock.withLock {
        if (state.value.active?.id != account.id) return@withLock
        val updated = state.value.data.copy(pending = change(state.value.data.pending))
        cache.write(account.id,updated); mutable.update { it.copy(data = updated) }
    }
    private suspend fun recover(account: ShipAccount) {
        for (op in state.value.data.pending.filterNot { it.state.startsWith("Rejected") }) {
            api.receipt(account,op.id)?.let { receipt ->
                if (!receipt.containsKey("operation-pending")) {
                    apply(account,receipt)
                    if (!receipt.containsKey("rejected")) pending(account) { entries -> entries.filterNot { it.id == op.id } }
                }
            }
        }
    }
    private suspend fun settle(account: ShipAccount, id: String, receipt: JsonObject): JsonObject {
        apply(account,receipt)
        val rejected = receipt["rejected"]?.jsonObject
        if (rejected != null) error("The host rejected this change: ${rejected["reason"]?.jsonPrimitive?.content ?: "please refresh and try again"}")
        pending(account) { entries -> entries.filterNot { it.id == id } }
        return receipt
    }
    fun canEdit(listId: Long): Boolean = state.value.connection == Connection.ONLINE && state.value.data.access(listId)?.online == true && state.value.data.pending.none { op -> !op.state.startsWith("Rejected") && op.action.values.firstOrNull()?.jsonObject?.get("list-id")?.jsonPrimitive?.longOrNull == listId }
    private suspend fun execute(kind: String, listId: Long?, fields: JsonObject): JsonObject {
        val account = state.value.active ?: error("Sign in first")
        check(state.value.connection == Connection.ONLINE) { "Offline: changes require a live connection to your ship" }
        if (listId != null) check(canEdit(listId)) { "The list host is unavailable or a previous change is still pending" }
        val id = operationId(); val action = buildJsonObject { put(kind,buildJsonObject {
            put("operation-id",id)
            if (listId != null) { put("list-id",listId); put("base-revision",state.value.data.list(listId)?.revision ?: error("List no longer exists")) }
            fields.forEach { (k,v) -> put(k,v) }
        }) }
        pending(account) { it + PendingOperation(id,action,Clock.System.now().toEpochMilliseconds()) }
        val channel = operationId()
        try {
            withTimeout(20000) { api.poke(account,action,channel) }
            repeat(50) {
                val receipt = api.receipt(account,id)
                if (receipt != null && !receipt.containsKey("operation-pending")) return settle(account,id,receipt)
                delay(400)
            }
            throw PendingConfirmation("Sent to your ship; still waiting for the list host. This change will be checked on reconnect. Do not submit it again.")
        } catch (e: CancellationException) { throw e }
        catch (e: PokeRejected) {
            pending(account) { entries -> entries.map { if (it.id == id) it.copy(state = "Rejected: ${e.message}") else it } }
            throw e
        }
        catch (e: Exception) {
            pending(account) { entries -> entries.map { if (it.id == id && !it.state.startsWith("Rejected")) it.copy(state = "Awaiting confirmation") else it } }
            throw e
        } finally { withContext(NonCancellable) { withTimeoutOrNull(2000) { api.closeChannel(account,channel) } } }
    }
    suspend fun mutate(kind: String, listId: Long? = null, fields: JsonObject = JsonObject(emptyMap())): JsonObject = writeLock.withLock {
        mutable.update { it.copy(busy = true, error = null) }
        try { execute(kind,listId,fields) } finally { mutable.update { it.copy(busy = false) } }
    }
    suspend fun createList(title: String) = mutate("create-list",fields=buildJsonObject { put("title",checkedTitle(title)) })
    suspend fun addSection(listId: Long, title: String) = mutate("add-section",listId,buildJsonObject { put("title",checkedTitle(title)); put("rank",(state.value.data.list(listId)?.sections?.maxOfOrNull { it.rank } ?: 0) + 1024) })
    suspend fun complete(listId: Long, reminder: Reminder) = mutate("set-completed",listId,buildJsonObject { put("reminder-id",reminder.id); put("completed",!reminder.completed); put("advance",if (!reminder.completed) recurrenceAdvance(reminder.schedule) else JsonNull) })
    suspend fun update(listId: Long, r: Reminder) = mutate("update-reminder",listId,metadata(r))
    suspend fun schedule(listId: Long, reminderId: Long, schedule: Schedule?) = mutate("set-schedule",listId,buildJsonObject { put("reminder-id",reminderId); put("schedule",schedule?.let { Wire.encodeToJsonElement(it).jsonObject.let { o -> JsonObject(o - "occurrence") } } ?: JsonNull) })
    suspend fun move(listId: Long, reminderId: Long, parentId: Long?, sectionId: Long?, rank: Long) = mutate("move-reminder",listId,buildJsonObject { put("reminder-id",reminderId); put("parent-id",parentId?.let(::JsonPrimitive) ?: JsonNull); put("section-id",sectionId?.let(::JsonPrimitive) ?: JsonNull); put("rank",rank) })
    suspend fun addReminder(listId: Long, title: String, assignee: String?, schedule: Schedule?, parentId: Long? = null, sectionId: Long? = null): Long = writeLock.withLock {
        mutable.update { it.copy(busy = true, error = null) }
        var created: Long? = null
        try {
            val cleaned = checkedTitle(title)
            val receipt = execute("add-reminder",listId,buildJsonObject { put("title",cleaned); put("tags",JsonArray(emptyList())) })
            val list = receipt["list-upserted"]?.jsonObject?.get("list")?.let { Wire.decodeFromJsonElement<TaskList>(it) } ?: error("The ship did not return the created reminder")
            val reminder = list.reminders.filter { it.title == cleaned }.maxByOrNull { it.id } ?: error("Created reminder was not returned")
            created = reminder.id
            if (assignee != null) execute("update-reminder",listId,metadata(reminder.copy(assignee=assignee)))
            if (schedule != null) execute("set-schedule",listId,buildJsonObject { put("reminder-id",reminder.id); put("schedule",JsonObject(Wire.encodeToJsonElement(schedule).jsonObject - "occurrence")) })
            if (parentId != null || sectionId != null) execute("move-reminder",listId,buildJsonObject { put("reminder-id",reminder.id); put("parent-id",parentId?.let(::JsonPrimitive) ?: JsonNull); put("section-id",sectionId?.let(::JsonPrimitive) ?: JsonNull); put("rank",reminder.rank) })
            reminder.id
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (created != null) throw PartialCreateException(created, "Reminder was created, but its details were not fully saved. Open it to finish editing. ${e.message}")
            throw e
        } finally { mutable.update { it.copy(busy = false) } }
    }
    suspend fun dismissRejected(id: String) { state.value.active?.let { a -> pending(a) { list -> list.filterNot { it.id == id && it.state.startsWith("Rejected") } } } }
}
class PartialCreateException(val reminderId: Long, message: String) : Exception(message)
fun checkedTitle(title: String): String = title.trim().also { require(it.isNotEmpty() && it.encodeToByteArray().size <= 1024) { "Enter a title of at most 1,024 bytes" } }
fun metadata(r: Reminder): JsonObject = buildJsonObject {
    put("reminder-id",r.id); put("title",checkedTitle(r.title)); put("notes",r.notes)
    require(r.notes.encodeToByteArray().size <= 65536) { "Notes are too long" }
    require(r.url.isNullOrBlank() || r.url.startsWith("https://") || r.url.startsWith("http://") || r.url.startsWith("mailto:")) { "Link must begin with https://, http:// or mailto:" }
    put("url",r.url?.takeIf { it.isNotBlank() }?.let(::JsonPrimitive) ?: JsonNull); put("priority",r.priority); put("flagged",r.flagged)
    put("tags",JsonArray(r.tags.map(::JsonPrimitive))); put("assignee",r.assignee?.let(::JsonPrimitive) ?: JsonNull)
}
