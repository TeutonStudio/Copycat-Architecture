package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/** Real commerce-quarter documents must remain editable after saving several versions. */
class LargeLibraryTest {
    private fun library(versions:Int)=Library().apply {
        forms.putAll(CommerceQuarterExample.forms)
        modules.putAll(CommerceQuarterExample.modules)
        repeat(versions) { index ->
            val building=CommerceQuarterExample.building.copy(version=index+1)
            buildings["${building.id}@${building.version}"]=building
        }
    }

    @Test fun `four real commerce versions fit storage and the bound table snapshot round trips`() {
        val library=library(4)
        val libraryJson=Json.encode(library)
        assertTrue(libraryJson.length>180000,"This fixture must exceed the former storage limit")
        assertTrue(libraryJson.length<=Library.MAX_JSON_CHARS)
        library.validateStorageSize()
        val current=library.buildings.getValue("commerce_quarter@4")
        val snapshot=Snapshot(editor="building",library=library,creative=true,
            table=P(15,80,-6),tableOrigin=P(0,80,0),tableBuilding=current)
        val json=Json.encode(snapshot)
        assertTrue(json.length>CommandTransport.MAX_JSON_CHARS)
        assertTrue(json.length>500000,"The real bound-table response must exceed the former snapshot limit")
        assertTrue(json.length<=Snapshot.MAX_SNAPSHOT_JSON_CHARS)
        val wire=CommandTransport.encode(json,Snapshot.MAX_SNAPSHOT_JSON_CHARS,250000)
        assertTrue(wire.length<=250000)
        val restoredJson=CommandTransport.decode(wire,Snapshot.MAX_SNAPSHOT_JSON_CHARS,250000)
        assertEquals(json,restoredJson)
        assertEquals(snapshot,Json.decode<Snapshot>(restoredJson))
        // Larger responses must never relax the request/document transport budget.
        assertEquals(250000,CommandTransport.MAX_JSON_CHARS)
        assertFailsWith<IllegalArgumentException> { CommandTransport.encode(json) }
    }

    @Test fun `history saves use the same library budget and reject oversized candidates atomically`() {
        val library=library(3)
        library.history+=PrintRecord(id="commerce_print",form=CommerceQuarterExample.forms.getValue("cq_window@1"))
        val key=library.savePrintHistory("commerce_print","Handelshof Fensterkopie")
        assertTrue(key in library.forms)
        assertTrue(key in library.printSettings)
        library.validateStorageSize()

        val oversized=library(7)
        assertTrue(Json.encode(oversized).length>Library.MAX_JSON_CHARS)
        assertFailsWith<IllegalArgumentException> { oversized.validateStorageSize() }
        oversized.history+=PrintRecord(id="too_large",form=CommerceQuarterExample.forms.getValue("cq_window@1"))
        val before=Json.encode(oversized)
        assertFailsWith<IllegalArgumentException> { oversized.savePrintHistory("too_large","Fenster") }
        assertEquals(before,Json.encode(oversized),"Rejected history saves must not alter forms or print settings")
    }

    @Test fun `larger snapshot budget remains bounded on encode and decode`() {
        assertEquals(500000,Library.MAX_JSON_CHARS)
        assertEquals(1000000,Snapshot.MAX_SNAPSHOT_JSON_CHARS)
        val boundary="x".repeat(Snapshot.MAX_SNAPSHOT_JSON_CHARS)
        val wire=CommandTransport.encode(boundary,Snapshot.MAX_SNAPSHOT_JSON_CHARS,250000)
        assertEquals(boundary,CommandTransport.decode(wire,Snapshot.MAX_SNAPSHOT_JSON_CHARS,250000))
        assertFailsWith<IllegalArgumentException> {
            CommandTransport.encode(boundary+"x",Snapshot.MAX_SNAPSHOT_JSON_CHARS,250000)
        }
        // A compact payload expanded under a larger caller budget must still be rejected by the snapshot budget.
        val oversized=CommandTransport.encode(boundary+"x",Snapshot.MAX_SNAPSHOT_JSON_CHARS+1,250000)
        assertFailsWith<IllegalArgumentException> {
            CommandTransport.decode(oversized,Snapshot.MAX_SNAPSHOT_JSON_CHARS,250000)
        }
    }
}
