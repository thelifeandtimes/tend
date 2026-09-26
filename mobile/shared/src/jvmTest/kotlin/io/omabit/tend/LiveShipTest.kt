package io.omabit.tend

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue

/** Opt-in only. Creates/deletes its own QA list; never edits pre-existing ship data. */
class LiveShipTest {
    private class Vault : CredentialStore {
        var value = AccountVault()

        override suspend fun read() = value

        override suspend fun write(vault: AccountVault) {
            value = vault
        }
    }

    private class Cache : CacheStore {
        val values = mutableMapOf<String, TendState>()

        override suspend fun read(account: String) = values[account]

        override suspend fun write(account: String, state: TendState) {
            values[account] = state
        }

        override suspend fun remove(account: String) {
            values.remove(account)
        }
    }

    private suspend fun until(condition: () -> Boolean) =
        withTimeout(30000) { while (!condition()) delay(100) }

    @Test
    fun actualEyreCanonicalCrudAndRecurrence(): Unit = runBlocking {
        val codeFile = System.getenv("TEND_LIVE_CODE_JSON")
        assumeTrue("Set TEND_LIVE_CODE_JSON and TEND_LIVE_URL to opt in", codeFile != null)
        val code =
            Wire.parseToJsonElement(Files.readString(Path.of(codeFile)))
                .jsonPrimitive
                .content
                .trim()
                .removePrefix("~")
        val http = HttpClient(OkHttp) { followRedirects = false }
        val repo = TendRepository(Eyre(http, true), Vault(), Cache())
        val peer = TendRepository(Eyre(http, true), Vault(), Cache())
        var createdList: Long? = null
        try {
            until { repo.state.value.initialized }
            repo.login(System.getenv("TEND_LIVE_URL") ?: "http://127.0.0.1:8081", code)
            until { repo.state.value.connection == Connection.ONLINE }
            println(
                "Live Tend authenticated; protocol ${repo.state.value.data.protocol}; subscription online"
            )
            val receipt = repo.createList("Native mobile QA ${System.currentTimeMillis()}")
            val id =
                receipt
                    .getValue("list-upserted")
                    .jsonObject
                    .getValue("list")
                    .jsonObject
                    .getValue("id")
                    .jsonPrimitive
                    .long
            createdList = id
            until { repo.canEdit(id) }
            repo.addSection(id, "Native QA section")
            val section = repo.state.value.data.list(id)!!.sections.single().id
            val due =
                Schedule("~2026.12.1..12.00.00", timezone = "UTC", recurrence = RepeatRule("daily"))
            val reminder =
                repo.addReminder(
                    id,
                    "Native QA recurring parent",
                    repo.state.value.active!!.ship,
                    due,
                    sectionId = section,
                )
            val child =
                repo.addReminder(
                    id,
                    "Native QA child",
                    null,
                    null,
                    parentId = reminder,
                    sectionId = section,
                )
            var r = repo.state.value.data.list(id)!!.reminders.first { it.id == reminder }
            assertEquals(parseUrbit(due.dueAt), parseUrbit(r.schedule!!.dueAt))
            assertEquals(
                2,
                visibleRows(
                        listOf(repo.state.value.data.list(id)!!),
                        View.ALL,
                        repo.state.value.active!!.ship,
                    )
                    .size,
            )
            repo.update(
                id,
                r.copy(
                    notes = "Native canonical receipt verified",
                    tags = listOf("qa"),
                    flagged = true,
                ),
            )
            r = repo.state.value.data.list(id)!!.reminders.first { it.id == reminder }
            repo.complete(id, r)
            r = repo.state.value.data.list(id)!!.reminders.first { it.id == reminder }
            assertFalse(r.completed)
            assertEquals(1L, r.schedule?.occurrence)
            assertEquals(parseUrbit("~2026.12.2..12.00.00"), parseUrbit(r.schedule!!.dueAt))
            assertTrue(repo.state.value.data.pending.isEmpty())
            assertTrue(
                repo.state.value.data.list(id)!!.reminders.any {
                    it.id == child && it.parentId == reminder
                }
            )
            println(
                "Live Tend canonical create / section / metadata / assignee / schedule / nesting / recurring completion PASS"
            )
            System.getenv("TEND_PEER_CODE_JSON")?.let { peerCodeFile ->
                val peerCode =
                    Wire.parseToJsonElement(Files.readString(Path.of(peerCodeFile)))
                        .jsonPrimitive
                        .content
                        .trim()
                        .removePrefix("~")
                until { peer.state.value.initialized }
                peer.login(System.getenv("TEND_PEER_URL") ?: "http://127.0.0.1:8080", peerCode)
                until { peer.state.value.connection == Connection.ONLINE }
                repo.mutate(
                    "invite-member",
                    fields =
                        buildJsonObject {
                            put("list-id", id)
                            put("ship", peer.state.value.active!!.ship)
                            put("can-invite", false)
                        },
                )
                until {
                    peer.state.value.data.invitations.any {
                        it.hostListId == id && it.host == repo.state.value.active!!.ship
                    }
                }
                val invitation =
                    peer.state.value.data.invitations.first {
                        it.hostListId == id && it.host == repo.state.value.active!!.ship
                    }
                peer.mutate(
                    "accept-invitation",
                    fields =
                        buildJsonObject {
                            put("host", invitation.host)
                            put("token", invitation.token)
                        },
                )
                until {
                    peer.state.value.data.accesses.any {
                        it.host == invitation.host && it.hostListId == id && it.online
                    }
                }
                val alias =
                    peer.state.value.data.accesses
                        .first { it.host == invitation.host && it.hostListId == id }
                        .alias
                peer.addReminder(
                    alias,
                    "Written through the peer ship",
                    peer.state.value.active!!.ship,
                    null,
                )
                until {
                    repo.state.value.data.list(id)!!.reminders.any {
                        it.title == "Written through the peer ship"
                    }
                }
                assertTrue(peer.state.value.data.pending.isEmpty())
                println(
                    "Live Tend invitation / remote replica / peer -> owner canonical write and receipt PASS"
                )
            }
        } finally {
            createdList?.let { id ->
                repo.mutate("delete-list", id)
                assertNull(repo.state.value.data.list(id))
                println("Removed the isolated mobile QA list; pre-existing lists untouched")
            }
            repo.setForeground(false)
            peer.setForeground(false)
            http.close()
        }
    }
}
