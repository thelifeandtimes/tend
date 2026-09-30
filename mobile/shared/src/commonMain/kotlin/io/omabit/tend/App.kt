@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.omabit.tend

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlinx.serialization.json.*

@Composable
fun TendApp(repository: TendRepository) {
    val client by repository.state.collectAsState()
    TendTheme(client.data.appearance.palette) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            var addingAccount by remember { mutableStateOf(false) }
            val snack = remember { SnackbarHostState() }
            LaunchedEffect(client.error) {
                client.error?.let {
                    snack.showSnackbar(
                        it,
                        withDismissAction = true,
                        duration = SnackbarDuration.Long,
                    )
                    repository.dismissError()
                }
            }
            Box(Modifier.fillMaxSize()) {
                when {
                    !client.initialized ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    client.active == null || addingAccount ->
                        LoginScreen(
                            repository,
                            client,
                            client.accounts.isNotEmpty(),
                            onDone = { addingAccount = false },
                            onCancel = { addingAccount = false },
                        )
                    else ->
                        key(client.active!!.id) {
                            HomeScreen(repository, client, onAddAccount = { addingAccount = true })
                        }
                }
                SnackbarHost(
                    snack,
                    Modifier.align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun BrandMark(size: Dp = 48.dp) {
    Box(
        Modifier.size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Check,
            null,
            Modifier.size(size * .58f),
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun Eyebrow(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LoginScreen(
    repo: TendRepository,
    client: ClientState,
    canCancel: Boolean,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (canCancel)
            TextButton(onClick = onCancel) {
                Icon(Icons.Outlined.ArrowBack, null)
                Spacer(Modifier.width(8.dp))
                Text("Back to Tend")
            }
        Spacer(Modifier.height(32.dp))
        BrandMark(60.dp)
        Spacer(Modifier.height(32.dp))
        Eyebrow("OMABIT · YOUR SHIP, YOUR THINGS")
        Spacer(Modifier.height(12.dp))
        Text("A little space\nto tend to things.", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(16.dp))
        Text(
            "Reminders, shared lists, and a quieter mind.\nConnect to the Tend already on your ship.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            url,
            { url = it },
            Modifier.fillMaxWidth(),
            label = { Text("Ship URL") },
            placeholder = { Text("https://your-ship.example") },
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            shape = RoundedCornerShape(16.dp),
            enabled = !client.busy,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            code,
            { code = it },
            Modifier.fillMaxWidth(),
            label = { Text("Ship +code") },
            singleLine = true,
            visualTransformation =
                if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        if (visible) "Hide code" else "Show code",
                    )
                }
            },
            keyboardOptions =
                KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            shape = RoundedCornerShape(16.dp),
            enabled = !client.busy,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                scope.launch {
                    try {
                        repo.login(url, code)
                        code = ""
                        onDone()
                    } catch (e: Exception) {
                        repo.report(e.message ?: "Could not connect")
                    }
                }
            },
            enabled = url.isNotBlank() && code.isNotBlank() && !client.busy,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            if (client.busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            }
            Text(if (client.busy) "Connecting to your ship…" else "Connect to Tend")
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Outlined.ArrowForward, null, Modifier.size(18.dp))
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                Icons.Outlined.Lock,
                null,
                Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Direct to your ship over HTTPS. Your +code is never stored. Sessions stay encrypted on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (client.accounts.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            Eyebrow("Saved ships")
            client.accounts.forEach { a ->
                TextButton(
                    onClick = {
                        repo.launch {
                            select(a)
                            onDone()
                        }
                    },
                    enabled = !client.busy,
                ) {
                    Text(a.ship)
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

private fun viewIcon(view: View): ImageVector =
    when (view) {
        View.TODAY -> Icons.Outlined.WbSunny
        View.ALL -> Icons.Outlined.Inbox
        View.FLAGGED -> Icons.Outlined.Flag
        View.ASSIGNED -> Icons.Outlined.Person
        View.SCHEDULED -> Icons.Outlined.CalendarMonth
        View.COMPLETED -> Icons.Outlined.CheckCircle
    }

@kotlinx.serialization.Serializable data class DetailTarget(val listId: Long, val reminderId: Long)

@Composable
private fun HomeScreen(repo: TendRepository, client: ClientState, onAddAccount: () -> Unit) {
    var view by rememberSaveable { mutableStateOf(View.TODAY) }
    var listId by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var detail by
        rememberSaveable(stateSaver = jsonStateSaver<DetailTarget?>()) {
            mutableStateOf<DetailTarget?>(null)
        }
    var compose by rememberSaveable { mutableStateOf(false) }
    var child by
        rememberSaveable(stateSaver = jsonStateSaver<DetailTarget?>()) {
            mutableStateOf<DetailTarget?>(null)
        }
    var manage by remember { mutableStateOf(false) }
    var accounts by remember { mutableStateOf(false) }
    var newList by remember { mutableStateOf(false) }
    var newSection by remember { mutableStateOf(false) }
    var showCompleted by rememberSaveable { mutableStateOf(false) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val ship = client.active!!.ship
    val selected = client.data.list(listId ?: -1)
    val shown =
        remember(client.data.lists, selected, view, query, showCompleted) {
            val lists =
                if (selected != null) listOf(selected)
                else client.data.lists.sortedBy { it.title.lowercase() }
            val base = visibleRows(lists, if (selected != null) View.ALL else view, ship, query)
            if (selected != null && showCompleted)
                base +
                    visibleRows(lists, View.COMPLETED, ship, query).filterNot { r ->
                        base.any { it.key == r.key }
                    }
            else base
        }
    val total =
        if (selected != null) selected.reminders.count { !it.completed }
        else client.data.lists.sumOf { it.reminders.count { r -> !r.completed } }
    LaunchedEffect(client.data.lists, listId) {
        if (listId != null && selected == null && client.connection == Connection.ONLINE)
            listId = null
    }
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(Modifier.widthIn(max = 330.dp)) {
                Column(
                    Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(18.dp)
                ) {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BrandMark(40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Tend", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                ship,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { accounts = true }) {
                            Icon(Icons.Outlined.SwitchAccount, "Switch ship")
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    Eyebrow("Your views")
                    Spacer(Modifier.height(10.dp))
                    View.entries.forEach { v ->
                        NavigationDrawerItem(
                            label = { Text(v.label) },
                            icon = { Icon(viewIcon(v), null) },
                            selected = listId == null && view == v,
                            onClick = {
                                listId = null
                                view = v
                                scope.launch { drawer.close() }
                            },
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { Eyebrow("Your lists") }
                        IconButton(
                            onClick = { newList = true },
                            enabled = client.connection == Connection.ONLINE,
                        ) {
                            Icon(Icons.Outlined.Add, "New list")
                        }
                    }
                    client.data.lists
                        .sortedBy { it.title.lowercase() }
                        .forEach { list ->
                            NavigationDrawerItem(
                                label = {
                                    Text(list.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                },
                                icon = {
                                    Icon(
                                        Icons.Outlined.List,
                                        null,
                                        tint =
                                            parseColor(list.color)
                                                ?: MaterialTheme.colorScheme.primary,
                                    )
                                },
                                badge = { Text(list.reminders.count { !it.completed }.toString()) },
                                selected = listId == list.id,
                                onClick = {
                                    listId = list.id
                                    query = ""
                                    scope.launch { drawer.close() }
                                },
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    if (client.data.lists.isEmpty())
                        Text(
                            "Your lists will appear here.",
                            Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    Spacer(Modifier.height(24.dp))
                    TextButton(onClick = { accounts = true }) {
                        Icon(Icons.Outlined.Settings, null)
                        Spacer(Modifier.width(10.dp))
                        Text("Accounts & connection")
                    }
                    Text(
                        "Native alpha · 0.1.0\nNo notification service. No analytics.",
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Tend",
                                style = MaterialTheme.typography.headlineMedium,
                                fontStyle = FontStyle.Italic,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                ship,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawer.open() } }) {
                            Icon(Icons.Outlined.Menu, "Open navigation")
                        }
                    },
                    actions = {
                        IconButton(onClick = { repo.reconnect() }, enabled = !client.busy) {
                            if (client.connection == Connection.CHECKING)
                                CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.Sync, "Reconnect")
                        }
                        IconButton(onClick = { accounts = true }) {
                            Icon(Icons.Outlined.AccountCircle, "Accounts")
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        ),
                )
            },
            floatingActionButton = {
                if (client.data.lists.isNotEmpty())
                    ExtendedFloatingActionButton(
                        onClick = { compose = true },
                        modifier = Modifier.semantics { contentDescription = "New reminder" },
                        icon = { Icon(Icons.Outlined.Add, null) },
                        text = { Text("New reminder") },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
            },
        ) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item("header") {
                    Column(Modifier.padding(top = 14.dp, bottom = 12.dp)) {
                        Eyebrow(
                            if (selected == null) "$total THINGS TO TEND TO"
                            else "SHARED WITH YOUR SHIP"
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                selected?.title ?: view.label,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.displaySmall,
                            )
                            if (selected != null)
                                IconButton(onClick = { manage = true }) {
                                    Icon(Icons.Outlined.MoreHoriz, "List options")
                                }
                        }
                        Spacer(Modifier.height(12.dp))
                        ConnectionLine(client, selected)
                        Spacer(Modifier.height(22.dp))
                        OutlinedTextField(
                            query,
                            { query = it },
                            Modifier.fillMaxWidth(),
                            placeholder = { Text("Find a reminder") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            trailingIcon = {
                                if (query.isNotEmpty())
                                    IconButton(onClick = { query = "" }) {
                                        Icon(Icons.Outlined.Close, "Clear search")
                                    }
                            },
                            shape = RoundedCornerShape(18.dp),
                        )
                        if (selected != null)
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = { newSection = true },
                                    enabled = repo.canEdit(selected.id) && !client.busy,
                                ) {
                                    Icon(Icons.Outlined.Add, null, Modifier.size(16.dp))
                                    Text("Section")
                                }
                                Spacer(Modifier.weight(1f))
                                FilterChip(
                                    selected = showCompleted,
                                    onClick = { showCompleted = !showCompleted },
                                    label = { Text("Completed") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Check, null, Modifier.size(16.dp))
                                    },
                                )
                            }
                    }
                }
                if (client.data.pending.isNotEmpty())
                    item("pending") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Eyebrow("Changes in transit")
                                client.data.pending.forEach { op ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            op.state,
                                            Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        if (op.state.startsWith("Rejected"))
                                            IconButton(
                                                onClick = { repo.launch { dismissRejected(op.id) } }
                                            ) {
                                                Icon(Icons.Outlined.Close, "Dismiss rejection")
                                            }
                                    }
                                }
                                Text(
                                    "Canonical state is confirmed by the list host.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                if (client.data.invitations.isNotEmpty())
                    item("invitations") { InvitationCard(repo, client) }
                if (shown.isEmpty())
                    item("empty") {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                if (query.isNotBlank()) Icons.Outlined.Search
                                else Icons.Outlined.CheckCircle,
                                null,
                                Modifier.size(42.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(18.dp))
                            Text(
                                if (query.isNotBlank()) "Nothing matches just yet."
                                else if (client.data.lists.isEmpty()) "A fresh place to begin."
                                else "A little breathing room.",
                                style = MaterialTheme.typography.headlineMedium,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                if (client.data.lists.isEmpty())
                                    "Create a list for the things on your mind."
                                else "No reminders in this view.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            if (client.data.lists.isEmpty()) {
                                Spacer(Modifier.height(20.dp))
                                Button(
                                    onClick = { newList = true },
                                    enabled = client.connection == Connection.ONLINE,
                                ) {
                                    Text("Create your first list")
                                }
                            }
                        }
                    }
                var previousSection: Pair<Long, Long?>? = null
                shown.forEach { row ->
                    val sectionKey = row.list.id to row.reminder.sectionId
                    if (row.depth == 0 && sectionKey != previousSection) {
                        previousSection = sectionKey
                        row.list.sections
                            .firstOrNull { it.id == row.reminder.sectionId }
                            ?.let { section ->
                                item("section:${row.key}") {
                                    Text(
                                        section.title,
                                        Modifier.padding(top = 16.dp, bottom = 6.dp),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                    }
                    item(row.key) {
                        AnimatedContent(
                            row,
                            modifier =
                                Modifier.animateItem(
                                    fadeInSpec = tween(420),
                                    fadeOutSpec = tween(420),
                                ),
                            contentKey = {
                                "${it.key}:${it.reminder.schedule?.occurrence}:${it.reminder.schedule?.dueAt}"
                            },
                            transitionSpec = {
                                fadeIn(tween(420, easing = LinearEasing)) togetherWith
                                    fadeOut(tween(420, easing = LinearEasing))
                            },
                        ) { animated ->
                            val outgoing =
                                animated.reminder.schedule?.occurrence !=
                                    row.reminder.schedule?.occurrence
                            ReminderCard(
                                if (outgoing)
                                    animated.copy(
                                        reminder = animated.reminder.copy(completed = true)
                                    )
                                else animated,
                                selected == null,
                                !outgoing && repo.canEdit(row.list.id) && !client.busy,
                                onOpen = { detail = DetailTarget(row.list.id, row.reminder.id) },
                                onComplete = {
                                    repo.launch { complete(row.list.id, row.reminder) }
                                },
                                onFlag = {
                                    repo.launch {
                                        update(
                                            row.list.id,
                                            row.reminder.copy(flagged = !row.reminder.flagged),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
                if (selected != null)
                    selected.sections
                        .filter { sec -> selected.reminders.none { it.sectionId == sec.id } }
                        .forEach { sec ->
                            item("empty-section:${sec.id}") {
                                Text(
                                    sec.title,
                                    Modifier.padding(vertical = 16.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
            }
        }
    }
    if (accounts)
        AccountSheet(
            repo,
            client,
            onDismiss = { accounts = false },
            onAdd = {
                accounts = false
                onAddAccount()
            },
        )
    if (newList)
        TextPrompt("A new list", "List name", client.busy, onDismiss = { newList = false }) { title
            ->
            repo.launch {
                createList(title)
                newList = false
            }
        }
    if (newSection && selected != null)
        TextPrompt(
            "A little structure",
            "Section title",
            client.busy,
            onDismiss = { newSection = false },
        ) { title ->
            repo.launch {
                addSection(selected.id, title)
                newSection = false
            }
        }
    if (manage && selected != null)
        ListSheet(repo, client, selected, onDismiss = { manage = false })
    if (compose)
        NewReminderSheet(
            repo,
            client,
            child?.listId ?: selected?.id,
            child?.reminderId,
            onDismiss = {
                compose = false
                child = null
            },
            onOpen = { l, r ->
                compose = false
                child = null
                detail = DetailTarget(l, r)
            },
        )
    detail?.let { target ->
        val list = client.data.list(target.listId)
        val r = list?.reminders?.firstOrNull { it.id == target.reminderId }
        if (list != null && r != null)
            ReminderSheet(
                repo,
                client,
                list,
                r,
                onDismiss = { detail = null },
                onChild = {
                    child = target
                    detail = null
                    compose = true
                },
            )
        else detail = null
    }
}

@Composable
private fun ConnectionLine(client: ClientState, list: TaskList?) {
    val access = list?.let { client.data.access(it.id) }
    val live = client.connection == Connection.ONLINE && (list == null || access?.online == true)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(6.dp)
                .clip(CircleShape)
                .background(
                    if (live) LocalTendPalette.current.color("green")
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
        )
        Text(
            if (live) "${access?.host?:client.active?.ship} · Connected"
            else
                when (client.connection) {
                    Connection.SIGN_IN -> "Session expired · sign in again"
                    Connection.INCOMPATIBLE -> "Incompatible Tend version"
                    Connection.CHECKING -> "Catching up with your ship…"
                    else ->
                        if (access != null && client.connection == Connection.ONLINE)
                            "${access.host} · Host unavailable · read-only"
                        else "Offline · cached reminders · read-only"
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReminderCard(
    row: ReminderRow,
    showList: Boolean,
    editable: Boolean,
    onOpen: () -> Unit,
    onComplete: () -> Unit,
    onFlag: () -> Unit,
) {
    val r = row.reminder
    val emphasis = remember { Animatable(if ((r.schedule?.occurrence ?: 0) > 0) .1f else 0f) }
    LaunchedEffect(Unit) { emphasis.animateTo(0f, tween(1600)) }
    val baseColor =
        if (row.context) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface
    Surface(
        onClick = onOpen,
        modifier =
            Modifier.fillMaxWidth()
                .padding(start = (row.depth.coerceAtMost(4) * 14).dp)
                .animateContentSize(),
        shape = RoundedCornerShape(18.dp),
        color =
            androidx.compose.ui.graphics.lerp(
                baseColor,
                MaterialTheme.colorScheme.primary,
                emphasis.value,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                r.completed,
                { onComplete() },
                enabled = editable,
                modifier =
                    Modifier.semantics {
                        contentDescription =
                            if (r.completed) "Uncomplete ${r.title}" else "Complete ${r.title}"
                    },
            )
            Column(
                Modifier.weight(1f).padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    r.title,
                    style = MaterialTheme.typography.titleMedium,
                    color =
                        if (r.completed || row.context) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                    textDecoration =
                        if (r.completed) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = buildList {
                    if (showList) add(row.list.title)
                    r.assignee?.let { add(it) }
                    if (r.priority != "none") add("${r.priority} priority")
                    if (row.depth > 4) add("Level ${row.depth+1}")
                }
                if (meta.isNotEmpty())
                    Text(
                        meta.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                r.schedule?.let { schedule ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            if (schedule.recurrence != null) Icons.Outlined.Repeat
                            else Icons.Outlined.CalendarToday,
                            null,
                            Modifier.size(12.dp),
                        )
                        Text(dueLabel(schedule), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (r.tags.isNotEmpty())
                    Text(
                        r.tags.joinToString("  ") { "#$it" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
            }
            IconButton(onClick = onFlag, enabled = editable) {
                Icon(
                    Icons.Outlined.Flag,
                    if (r.flagged) "Unflag ${r.title}" else "Flag ${r.title}",
                    Modifier.size(20.dp),
                    tint =
                        if (r.flagged) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f),
                )
            }
        }
    }
}

@Composable
private fun TextPrompt(
    title: String,
    label: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title, style = MaterialTheme.typography.headlineMedium) },
        text = {
            OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }, enabled = value.isNotBlank() && !busy) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

@Composable
private fun InvitationCard(repo: TendRepository, client: ClientState) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(18.dp)) {
            Eyebrow("You're invited")
            client.data.invitations.forEach { invite ->
                Spacer(Modifier.height(12.dp))
                Text(invite.title, style = MaterialTheme.typography.titleMedium)
                Text("From ${invite.host}", style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(
                        onClick = {
                            repo.launch {
                                mutate(
                                    "accept-invitation",
                                    fields =
                                        buildJsonObject {
                                            put("host", invite.host)
                                            put("token", invite.token)
                                        },
                                )
                            }
                        },
                        enabled = client.connection == Connection.ONLINE && !client.busy,
                    ) {
                        Text("Accept")
                    }
                    TextButton(
                        onClick = {
                            repo.launch {
                                mutate(
                                    "decline-invitation",
                                    fields =
                                        buildJsonObject {
                                            put("host", invite.host)
                                            put("token", invite.token)
                                        },
                                )
                            }
                        },
                        enabled = client.connection == Connection.ONLINE && !client.busy,
                    ) {
                        Text("Decline")
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountSheet(
    repo: TendRepository,
    client: ClientState,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
) {
    var remove by remember { mutableStateOf<ShipAccount?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, properties = tendSheetProperties()) {
        TendSheetSystemBars()
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Your ships", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Separate identities. One place to tend.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            client.accounts.forEach { a ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                repo.launch {
                                    select(a)
                                    onDismiss()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !client.busy,
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(a.ship, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    a.url.removePrefix("https://"),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (client.active?.id == a.id)
                                    Text(
                                        "Current identity",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                            }
                        }
                        IconButton(onClick = { remove = a }, enabled = !client.busy) {
                            Icon(Icons.Outlined.Logout, "Forget ${a.ship}")
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                enabled = !client.busy,
            ) {
                Icon(Icons.Outlined.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Connect another ship")
            }
            HorizontalDivider()
            Eyebrow("Appearance")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Follow ship theme")
                    Text(
                        client.data.appearance.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = client.data.appearance.followShip,
                    onCheckedChange = { follow -> repo.launch { followShipTheme(follow) } },
                    modifier = Modifier.semantics { contentDescription = "Follow ship theme" },
                )
            }
            Text(
                "Colors and light/dark mode only. This choice is saved separately for each ship on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            Eyebrow("Connection & privacy")
            ConnectionLine(client, null)
            Text(
                "Sessions are protected by Android Keystore. Cached reminders stay in this app's private storage. Forgetting a ship removes its session and cache from this device; it does not delete anything on the ship.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Notifications are not part of this alpha. Reconnect or reopen Tend to refresh after backgrounding.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
    remove?.let { a ->
        AlertDialog(
            onDismissRequest = { remove = null },
            title = { Text("Forget ${a.ship}?") },
            text = {
                Text(
                    "Remove this device's saved session and offline cache. Nothing on the ship will be changed."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        repo.launch {
                            forget(a)
                            remove = null
                        }
                    }
                ) {
                    Text("Forget ship")
                }
            },
            dismissButton = { TextButton(onClick = { remove = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun ChoiceField(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onChoose: (String) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Box {
            OutlinedButton(
                onClick = {
                    focus.clearFocus()
                    expanded = true
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    options.firstOrNull { it.first == value }?.second ?: value,
                    Modifier.weight(1f),
                    textAlign = TextAlign.Start,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(20.dp))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 360.dp),
            ) {
                options.forEach { (key, text) ->
                    DropdownMenuItem(
                        text = { Text(text, maxLines = 3) },
                        onClick = {
                            onChoose(key)
                            expanded = false
                        },
                        leadingIcon = {
                            if (key == value) Icon(Icons.Outlined.Check, null, Modifier.size(18.dp))
                        },
                    )
                }
            }
        }
    }
}
