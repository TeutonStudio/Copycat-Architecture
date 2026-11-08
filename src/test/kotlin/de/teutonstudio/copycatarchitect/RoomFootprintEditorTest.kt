package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class RoomFootprintEditorTest {
    private fun room(rectangles:List<Rect> = listOf(Rect(width=3,length=3))):BuildingNode = BuildingNode(
        "room",BuildingKind.ROOM,rectangles=rectangles,
        walls=RoomGeometry.edges(rectangles).associate { it.id to "source_${it.direction}" }
    )

    @Test fun `click edits accept concavity while preserving wall assignments`() {
        val original=room()
        val edited=RoomFootprintEditor.toggleCell(original,P(1,0,0))
        assertEquals(RoomGeometry.cells(original.rectangles)-P(1,0,0),RoomGeometry.cells(edited.rectangles))
        val north=RoomGeometry.edges(edited.rectangles).filter { it.direction==0 && it.cells.all { cell -> cell.z==-1 } }
        assertEquals(2,north.size)
        assertTrue(north.all { edited.walls[it.id]=="source_0" })
        assertEquals(RoomGeometry.cells(original.rectangles),RoomGeometry.cells(RoomFootprintEditor.toggleCell(edited,P(1,0,0)).rectangles))
    }

    @Test fun `click edits reject holes isolated cells bridges and an empty footprint`() {
        val solid=room()
        assertFailsWith<IllegalArgumentException> { RoomFootprintEditor.toggleCell(solid,P(1,0,1)) }
        assertFailsWith<IllegalArgumentException> { RoomFootprintEditor.toggleCell(solid,P(4,0,0)) }
        val bridge=room(listOf(Rect(width=5,length=1)))
        assertFailsWith<IllegalArgumentException> { RoomFootprintEditor.toggleCell(bridge,P(2,0,0)) }
        val single=room(listOf(Rect(width=1,length=1)))
        assertFailsWith<IllegalArgumentException> { RoomFootprintEditor.toggleCell(single,P()) }
        assertEquals(setOf(P()),RoomGeometry.cells(single.rectangles))
        assertEquals(9,RoomGeometry.cells(solid.rectangles).size)
    }

    @Test fun `diagonal contact cannot join a room or open an enclosed hole`() {
        assertFailsWith<IllegalArgumentException> { RoomFootprintEditor.validate(setOf(P(),P(1,0,1))) }
        val pinched=RoomGeometry.cells(room().rectangles)-setOf(P(1,0,1),P())
        assertFailsWith<IllegalArgumentException> { RoomFootprintEditor.validate(pinched) }
        RoomFootprintEditor.validate(pinched-P(1,0,0))
    }

    @Test fun `rectangle edits validate their completed transaction in either drag direction`() {
        val original=room(listOf(Rect(width=1,length=1)))
        val expanded=RoomFootprintEditor.rectangle(original,P(3,0,2),P())
        assertEquals(RoomGeometry.cells(listOf(Rect(width=4,length=3))),RoomGeometry.cells(expanded.rectangles))
        assertEquals(expanded,RoomFootprintEditor.rectangle(original,P(),P(3,0,2)))
        assertTrue(expanded.walls.values.all { it.startsWith("source_") })
        val cut=RoomFootprintEditor.rectangle(expanded,P(3,0,2),P(2,0,0),erase=true)
        assertEquals(RoomGeometry.cells(listOf(Rect(width=2,length=3))),RoomGeometry.cells(cut.rectangles))
        val opened=RoomFootprintEditor.rectangle(expanded,P(1,0,0),P(1,0,1),erase=true)
        assertEquals(RoomGeometry.cells(expanded.rectangles)-setOf(P(1,0,0),P(1,0,1)),RoomGeometry.cells(opened.rectangles))
    }

    @Test fun `invalid rectangles leave the existing room unchanged`() {
        val original=room(listOf(Rect(width=5,length=5)))
        assertFailsWith<IllegalArgumentException> { RoomFootprintEditor.rectangle(original,P(1,0,1),P(3,0,3),erase=true) }
        assertFailsWith<IllegalArgumentException> { RoomFootprintEditor.rectangle(original,P(2,0,0),P(2,0,4),erase=true) }
        assertFailsWith<IllegalArgumentException> { RoomFootprintEditor.rectangle(original,P(),P(4,0,4),erase=true) }
        assertEquals(25,RoomGeometry.cells(original.rectangles).size)
        assertEquals(4,original.walls.size)
    }

    @Test fun `interactive edits retain legacy packing and support direct wall storeys`() {
        val legacy=room(listOf(Rect(32,0,32,1)))
        val edited=RoomFootprintEditor.toggleCell(legacy,P(32,0,0))
        assertEquals(RoomGeometry.cells(legacy.rectangles)-P(32,0,0),RoomGeometry.cells(edited.rectangles))
        val storey=room().copy(kind=BuildingKind.STOREY,storeyMode="walls")
        assertEquals(10,RoomGeometry.cells(RoomFootprintEditor.toggleCell(storey,P(3,0,0)).rectangles).size)
        assertFailsWith<IllegalArgumentException> { RoomFootprintEditor.toggleCell(storey.copy(storeyMode="rooms"),P()) }
        assertFailsWith<IllegalArgumentException> { RoomFootprintEditor.toggleCell(room(),P(64,0,0)) }
        assertFailsWith<IllegalArgumentException> { RoomFootprintEditor.rectangle(room(),P(),P(1,1,1)) }
    }
}
