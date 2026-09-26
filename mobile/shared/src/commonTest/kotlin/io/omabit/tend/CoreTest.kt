package io.omabit.tend

import kotlin.test.*
import kotlinx.datetime.*
import kotlin.time.Instant
import kotlinx.serialization.json.*

class CoreTest {
    @Test fun snapshotRequiresExactProtocol() {
        for(version in listOf("null","1","3")) assertFailsWith<ProtocolException> { reduce(TendState(),Wire.parseToJsonElement("""{"snapshot":{"protocol-version":$version,"lists":[]}}""").jsonObject) }
        assertEquals(emptyList(),reduce(TendState(),Wire.parseToJsonElement("""{"snapshot":{"protocol-version":2,"lists":[]}}""").jsonObject).lists)
    }
    @Test fun staleReceiptCannotRollbackNewerList() {
        val state=TendState(lists=listOf(TaskList(1,"Latest",revision=8)))
        val reduced=reduce(state,Wire.parseToJsonElement("""{"list-upserted":{"operation-id":"a","list":{"id":1,"title":"Old","revision":2}}}""").jsonObject)
        assertEquals("Latest",reduced.list(1)?.title)
    }
    @Test fun parentAlwaysPrecedesChildrenInAll() {
        val list=TaskList(1,"List",reminders=listOf(Reminder(2,"Child",parentId=1,rank=0),Reminder(3,"Grandchild",parentId=2,rank=0),Reminder(1,"Parent",rank=999)))
        val rows=visibleRows(listOf(list),View.ALL,"~zod")
        assertEquals(listOf(1L,2L,3L),rows.map {it.reminder.id});assertEquals(listOf(0,1,2),rows.map {it.depth})
    }
    @Test fun filteredChildKeepsAncestorContext() {
        val list=TaskList(1,"List",reminders=listOf(Reminder(1,"Parent"),Reminder(2,"Child",parentId=1,flagged=true)))
        val rows=visibleRows(listOf(list),View.FLAGGED,"~zod")
        assertEquals(2,rows.size);assertTrue(rows[0].context);assertFalse(rows[1].context)
    }
    @Test fun completedParentRemainsContextForIncompleteChild() {
        val list=TaskList(1,"List",reminders=listOf(Reminder(1,"Parent",completed=true),Reminder(2,"Child",parentId=1)))
        assertEquals(listOf(1L,2L),visibleRows(listOf(list),View.ALL,"~zod").map {it.reminder.id})
    }
    @Test fun cyclesAndOrphansRemainFiniteAndVisible() {
        val list=TaskList(1,"List",reminders=listOf(Reminder(1,"A",parentId=2),Reminder(2,"B",parentId=1),Reminder(3,"C",parentId=100)))
        assertEquals(3,visibleRows(listOf(list),View.ALL,"~zod").size)
        assertEquals(setOf(2L),descendantIds(list,1))
    }
    @Test fun deepHierarchyDoesNotOverflowCallStack() {
        val list=TaskList(1,"List",reminders=(1L..10000L).map {Reminder(it,"Reminder $it",parentId=if(it==1L)null else it-1)})
        assertEquals(10000,visibleRows(listOf(list),View.ALL,"~zod").size)
    }
    @Test fun idsAreScopedToLists() {
        val lists=listOf(TaskList(1,"A",reminders=listOf(Reminder(1,"A"))),TaskList(2,"B",reminders=listOf(Reminder(1,"B"))))
        assertEquals(listOf("1:1","2:1"),visibleRows(lists,View.ALL,"~zod").map {it.key})
    }
    @Test fun endpointValidation() {
        assertEquals("https://ship.example",normalizeEndpoint("ship.example/"))
        listOf("http://example.com","https://user:secret@example.com","https://example.com/apps/tend","https://example.com?q=x","https://example.com#x").forEach {assertFails {normalizeEndpoint(it)}}
        assertEquals("http://10.0.2.2:18765",normalizeEndpoint("http://10.0.2.2:18765",true))
        assertFails {normalizeEndpoint("http://192.168.1.10",true)}
    }
    @Test fun datesRoundTrip() {
        val input="~2026.9.26..09.04.05"
        assertEquals(input,wireDate(parseUrbit(input)))
        assertEquals(Instant.parse("2026-09-26T09:04:05Z"),parseUrbit(input+"..1234"))
    }
    private fun next(s:Schedule,now:String)=recurrenceAdvance(s,Instant.parse(now)).jsonObject
    @Test fun dailyKeepsWallTimeAcrossDst() {
        val s=Schedule("~2026.3.7..17.00.00",timezone="America/Los_Angeles",recurrence=RepeatRule("daily"))
        assertEquals("~2026.3.8..16.00.00",next(s,"2026-03-07T18:00:00Z")["due-at"]?.jsonPrimitive?.content)
    }
    @Test fun dstGapMovesForward() {
        val s=Schedule("~2026.3.7..10.30.00",timezone="America/Los_Angeles",recurrence=RepeatRule("daily"))
        assertEquals("~2026.3.8..10.30.00",next(s,"2026-03-07T18:00:00Z")["due-at"]?.jsonPrimitive?.content)
    }
    @Test fun dstFoldChoosesEarlierInstant() {
        val s=Schedule("~2026.10.31..08.30.00",timezone="America/Los_Angeles",recurrence=RepeatRule("daily"))
        assertEquals("~2026.11.1..08.30.00",next(s,"2026-10-31T12:00:00Z")["due-at"]?.jsonPrimitive?.content)
    }
    @Test fun hourlySkipsOverdueSlotsAndCountsThem() {
        val s=Schedule("~2026.9.1..09.00.00",recurrence=RepeatRule("hourly",interval=2))
        val next=next(s,"2026-09-01T14:01:00Z")
        assertEquals("~2026.9.1..15.00.00",next["due-at"]?.jsonPrimitive?.content);assertEquals(3,next["occurrence"]?.jsonPrimitive?.int)
    }
    @Test fun countEndTerminates() {
        val s=Schedule("~2026.9.1..09.00.00",recurrence=RepeatRule("daily",maxOccurrences=1))
        assertEquals(JsonNull,next(s,"2026-09-01T10:00:00Z")["due-at"])
    }
    @Test fun weeklyHonorsWeekdaysAndInterval() {
        assertEquals(LocalDateTime(2026,9,30,9,0),nextWall(LocalDateTime(2026,9,28,9,0),RepeatRule("weekly",2,listOf(1,3))))
        assertEquals(LocalDateTime(2026,10,12,9,0),nextWall(LocalDateTime(2026,9,30,9,0),RepeatRule("weekly",2,listOf(1,3))))
    }
    @Test fun monthEndClamps() {assertEquals(LocalDateTime(2026,2,28,9,0),nextWall(LocalDateTime(2026,1,31,9,0),RepeatRule("monthly")))}
    @Test fun leapYearClamps() {assertEquals(LocalDateTime(2025,2,28,9,0),nextWall(LocalDateTime(2024,2,29,9,0),RepeatRule("yearly")))}
    @Test fun lastWeekdayOfMonth() {assertEquals(LocalDateTime(2026,9,28,9,0),nextWall(LocalDateTime(2026,9,1,9,0),RepeatRule("monthly",monthWeek=MonthWeek(5,1))))}
    @Test fun multipleMonthDates() {assertEquals(LocalDateTime(2026,9,15,9,0),nextWall(LocalDateTime(2026,9,1,9,0),RepeatRule("monthly",monthDays=listOf(1,15))))}
    @Test fun rejectUnsafeLinks() {assertFails {metadata(Reminder(1,"x",url="javascript:alert(1)"))};assertEquals(JsonNull,metadata(Reminder(1,"x",url=""))["url"])}
    @Test fun validationUsesUtf8ByteLimit() {assertFails {checkedTitle("猫".repeat(400))};assertEquals("Tend",checkedTitle("  Tend  "))}
    @Test fun unknownFieldsRemainForwardCompatible() {assertEquals("List",Wire.decodeFromString<TaskList>("""{"id":1,"title":"List","future":true}""").title)}
    @Test fun operationIdsAreUnique() {assertEquals(1000,(1..1000).map {operationId()}.toSet().size)}
}
