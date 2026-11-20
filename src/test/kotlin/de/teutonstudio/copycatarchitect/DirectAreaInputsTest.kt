package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class DirectAreaInputsTest {
    private val forms=mapOf("full@1" to Form(parts=listOf(Part("copycats:copycat_block"))))
    private fun graph(vararg nodes:BuildingNode)=BuildingGraph.starter().let { it.copy(nodes=it.nodes+nodes).withCurrentSchema() }
    private fun eval(graph:BuildingGraph)=BuildingGraphCompiler.Evaluation(graph,Building().palette,forms,emptyMap())
    private fun source(id:String,length:Int=3)=BuildingNode(id,BuildingKind.SEGMENT,module="",form="full@1",length=length,height=2)

    @Test fun `direct sparse segment surface has the same geometry as a legacy arrangement`() {
        val area=BuildingNode("area",BuildingKind.AREA,coordinates=listOf(P(),P(1,0,0),P(0,0,1)),areaMode=AreaMode.DRAW)
        val copy=BuildingNode("copy",BuildingKind.COPYCAT,form="full@1")
        val layout=BuildingNode("layout",BuildingKind.SEGMENT_LAYOUT,geometry="area",depth=2)
        val direct=BuildingNode("direct",BuildingKind.SEGMENT,assembly=true,layout="area",inputs=listOf("copy"),depth=2)
        val g=graph(area,copy,layout,direct,direct.copy(id="old",layout="layout"))
        BuildingGraphCompiler.structure(g)
        assertEquals(11,g.schema)
        assertEquals(eval(g).preview("old"),eval(g).preview("direct"))
        assertEquals(6,eval(g).segment("direct").cells.count { !it.cell.empty })
        val restored=Gson().fromJson(Gson().toJson(g),BuildingGraph::class.java)
        assertEquals(eval(g).preview("direct"),eval(restored).preview("direct"))
    }
    @Test fun `direct room surface leaves height local and shared edits update every consumer`() {
        val area=BuildingNode("area",BuildingKind.AREA,areaMode=AreaMode.RECTANGLE,length=3,height=3)
        val room=BuildingNode("room_a",BuildingKind.ROOM,assembly=true,geometry="area",height=6)
        val g=graph(area,room,room.copy(id="room_b",height=4))
        assertEquals(6,g.resolvedNode("room_a").height)
        assertEquals(4,g.resolvedNode("room_b").height)
        val changed=g.editRoomGeometry("area",listOf(P(),P(1,0,0),P(0,0,1)))
        for(id in listOf("room_a","room_b")) assertEquals(3,RoomGeometry.cells(changed.resolvedNode(id).rectangles).size)
        assertEquals(6,changed.resolvedNode("room_a").height)
    }
    @Test fun `every boolean operation applies the independent integer input offsets`() {
        val expected=mapOf(BuildingKind.SEGMENT_UNION to 8,BuildingKind.SEGMENT_INTERSECTION to 2,BuildingKind.SEGMENT_DIFFERENCE to 4,BuildingKind.SEGMENT_SYMMETRIC_DIFFERENCE to 6)
        for((kind,count) in expected) {
            val op=BuildingNode("op",kind,inputs=listOf("a","b"),operandOffsets=listOf(P(),P(2,0,0)))
            val g=graph(source("a"),source("b",2),op)
            BuildingGraphCompiler.structure(g)
            assertEquals(count,eval(g).preview("op").placements.size,kind.name)
            val moved=g.replace(op.copy(operandOffsets=listOf(P(-3,-2,1),P(-1,-2,1))))
            assertEquals(count,eval(moved).preview("op").placements.size)
            assertTrue(eval(moved).preview("op").placements.any { it.pos.x<0 })
        }
    }
    @Test fun `input reordering moves operand positions and repeated sources keep independent placements`() {
        val op=BuildingNode("op",BuildingKind.SEGMENT_INTERSECTION,inputs=listOf("a","a"),operandOffsets=listOf(P(),P(4,0,0)))
        val g=graph(source("a",1),op)
        assertTrue(eval(g).preview("op").placements.isEmpty())
        val reordered=GraphInputEdits.building(g,"op",listOf(1,0))
        assertEquals(listOf(P(4,0,0),P()),reordered.nodes.last().operandOffsets)
        assertFails { BuildingGraphCompiler.structure(g.replace(op.copy(operandOffsets=listOf(P(65,0,0))))) }
    }
    @Test fun `legacy non union offsets stay ignored and do not force a schema upgrade`() {
        val op=BuildingNode("op",BuildingKind.SEGMENT_INTERSECTION,inputs=listOf("a","a"),offsets=listOf(P(),P(4,0,0)))
        val g=graph(source("a",1),op)
        assertTrue(g.schema<11)
        assertEquals(2,eval(g).preview("op").placements.size)
    }
    @Test fun `embedded roof sections retain stable ports and legacy level compilation`() {
        val base=BuildingGraph.starter()
        val config=RoofLevelConfig(overhang16=0)
        val level=BuildingNode("level",BuildingKind.ROOF_LEVEL,roofLevel=config)
        val layout=BuildingNode("layout",BuildingKind.ROOF_LAYOUT,inputs=listOf("level"))
        val legacy=BuildingNode("legacy_roof",BuildingKind.ROOF,assembly=true,layout="layout",role="roof")
        val direct=legacy.copy(id="direct_roof",layout="",roofSections=listOf(RoofSection("west","West",config)))
        val g=base.copy(nodes=base.nodes+listOf(level,layout,legacy,direct)).withCurrentSchema()
        BuildingGraphCompiler.structure(g)
        assertEquals(eval(g).preview("legacy_roof"),eval(g).preview("direct_roof"))
        assertEquals("area:west",BuildingPorts.of(direct,g,true).first().key)
        assertFails { BuildingGraphCompiler.structure(g.replace(direct.copy(roofSections=List(2) { RoofSection("same") }))) }
    }
    @Test fun `roof storey selectors resolve the building behind a graph card result`() {
        val base=BuildingGraph.starter()
        val card=BuildingNode("card",BuildingKind.BUILDING,resultType="BUILDING",inputs=listOf("building"))
        val g=base.copy(nodes=base.nodes+card).withCurrentSchema()
        assertEquals(base.storeyInstances(),g.storeyInstances())
    }
    @Test fun `new starter uses direct areas while the legacy starter is retained`() {
        val g=BuildingAssembly.directStarter()
        BuildingGraphCompiler.structure(g)
        assertEquals(11,g.schema)
        assertTrue(g.nodes.none { it.kind in setOf(BuildingKind.SEGMENT_LAYOUT,BuildingKind.ROOM_GEOMETRY,BuildingKind.ROOF_LEVEL,BuildingKind.ROOF_LAYOUT) })
        assertTrue(BuildingAssembly.starter().nodes.any { it.kind==BuildingKind.SEGMENT_LAYOUT })
    }
    @Test fun `published operator family applies independent offsets before exact set evaluation`() {
        val expected=mapOf("UNION" to 8,"INTERSECTION" to 2,"DIFFERENCE" to 4,"SYMMETRIC_DIFFERENCE" to 6)
        for((operation,count) in expected) {
            val op=BuildingNode("op",BuildingKind.SEGMENT_OPERATOR,operation=operation,inputs=listOf("a","b"),operandOffsets=listOf(P(-3,-2,1),P(-1,-2,1)))
            val g=graph(source("a"),source("b",2),op)
            val preview=eval(g).preview("op")
            assertEquals(count,preview.placements.size,operation)
            assertTrue(preview.placements.any { it.pos.x<0 })
        }
    }
    @Test fun `published operator input permutations and switches retain repeated source positions`() {
        val op=BuildingNode("op",BuildingKind.SEGMENT_OPERATOR,operation="INTERSECTION",inputs=listOf("a","a"),operandOffsets=listOf(P(),P(4,0,0)))
        val g=graph(source("a",1),op)
        assertTrue(eval(g).preview("op").placements.isEmpty())
        assertEquals(listOf(P(4,0,0),P()),GraphInputEdits.building(g,"op",listOf(1,0)).nodes.last().operandOffsets)
        val changed=UnifiedOperators.switch(op,"DIFFERENCE",g.nodes.associateBy { it.id })
        assertEquals(op.operandOffsets,changed.operandOffsets)
        assertEquals(2,eval(g.replace(changed)).preview("op").placements.size)
        assertNull(UnifiedOperators.switch(op,"ROTATE",g.nodes.associateBy { it.id }).operandOffsets)
    }
}
