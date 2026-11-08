package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class SegmentCompositionTest {
    private val forms=mapOf("full@1" to Form(id="full",parts=listOf(Part("copycats:copycat_block"))))
    private fun eval(graph:BuildingGraph)=BuildingGraphCompiler.Evaluation(graph,Building().palette,forms,emptyMap())
    private fun graph(vararg definitions:BuildingNode)=BuildingGraph.starter().let { it.copy(nodes=it.nodes+definitions) }
    private fun solid(id:String,length:Int,height:Int,block:String="minecraft:stone")=
        BuildingNode(id,BuildingKind.SEGMENT,module="",block=block,length=length,height=height)

    @Test fun `segments compose in both axes and preserve ordered nested geometry`() {
        val left=solid("left",2,1)
        val right=solid("right",1,1,"minecraft:glass")
        val row=BuildingNode("row",BuildingKind.PATTERN,inputs=listOf("left","right"))
        val top=solid("top",3,2,"minecraft:bricks")
        val stacked=BuildingNode("stacked",BuildingKind.PATTERN,inputs=listOf("row","top"),patternAxis="vertical")
        val repeated=BuildingNode("repeated",BuildingKind.PATTERN,inputs=listOf("stacked","empty_wall","stacked"))
        val g=graph(left,right,row,top,stacked,repeated)
        val result=eval(g).segment("stacked")
        assertEquals(3,result.length);assertEquals(3,result.height);assertEquals(9,result.cells.size)
        assertEquals("minecraft:stone",result.cells.single { it.cell.pos==P(1,0,0) }.cell.block)
        assertEquals("minecraft:glass",result.cells.single { it.cell.pos==P(2,0,0) }.cell.block)
        assertTrue(result.cells.filter { it.cell.pos.y in 1..2 }.all { it.cell.block=="minecraft:bricks" })
        val nested=eval(g).segment("repeated")
        assertEquals(7,nested.length);assertEquals(3,nested.height);assertEquals(21,nested.cells.size)
        assertEquals((0..2).map { P(3,it,0) }.toSet(),nested.cells.filter { it.cell.empty }.map { it.cell.pos }.toSet())
        val grouped=eval(g.replace(stacked.copy(materialMode=MaterialMode.GROUPED,role="frame"))).segment("stacked")
        assertTrue(grouped.cells.all { it.overrideRole=="frame" })
        assertTrue(BuildingPorts.of(stacked).all { BuildingKind.PATTERN in it.accepts && BuildingKind.SEGMENT in it.accepts })
    }

    @Test fun `vertical air layers adapt to segment width and clear the corresponding room cells`() {
        val base=solid("base",3,2)
        val gap=solid("gap",1,1,"minecraft:air")
        val stacked=BuildingNode("stacked",BuildingKind.PATTERN,inputs=listOf("base","gap","base"),patternAxis="vertical")
        var g=graph(base,gap,stacked)
        val segment=eval(g).segment("stacked")
        assertEquals(3,segment.length);assertEquals(5,segment.height)
        assertEquals((0..2).map { P(it,2,0) }.toSet(),segment.cells.filter { it.cell.empty }.map { it.cell.pos }.toSet())
        val room=g.nodes.single { it.id=="room" }.copy(height=5,walls=RoomGeometry.edges(listOf(Rect(width=3,length=3))).associate { it.id to "stacked" })
        g=g.replace(room)
        val compiled=eval(g).room("room")
        assertTrue(compiled.plan.clear.containsAll(RoomGeometry.edges(room.rectangles).flatMap { edge -> edge.cells.map { it+P(0,2,0) } }))
        assertTrue(compiled.plan.placements.none { it.component=="wall" && it.pos.y==2 })
        assertTrue(compiled.plan.materials.keys.none { it=="minecraft:air" })
    }

    @Test fun `composed segments reject incompatible axes and dimensions beyond wall limits`() {
        val a=solid("a",2,8)
        val b=solid("b",2,8)
        val stack=BuildingNode("stack",BuildingKind.PATTERN,inputs=listOf("a","b"),patternAxis="vertical")
        val g=graph(a,b,stack)
        assertEquals(16,eval(g).segment("stack").height)
        assertFailsWith<IllegalArgumentException> { eval(g.replace(b.copy(height=9))).segment("stack") }
        assertFailsWith<IllegalArgumentException> { eval(g.replace(b.copy(length=3))).segment("stack") }
        assertFailsWith<IllegalArgumentException> { eval(g.replace(stack.copy(patternAxis="invalid"))) }
        assertFails { eval(g.replace(stack.copy(inputs=listOf("a","")))).segment("stack") }
        val horizontal=stack.copy(patternAxis="horizontal")
        assertFailsWith<IllegalArgumentException> { eval(g.replace(horizontal).replace(b.copy(height=7))).segment("stack") }
        assertEquals(128,eval(g.replace(horizontal).replace(a.copy(length=64)).replace(b.copy(length=64))).segment("stack").length)
        assertFailsWith<IllegalArgumentException> { eval(g.replace(horizontal).replace(a.copy(length=65)).replace(b.copy(length=64))).segment("stack") }
    }

    @Test fun `room dimensions include the wall shell and structural floor and ceiling layers`() {
        val original=BuildingGraph.starter()
        val room=original.nodes.single { it.id=="room" }.copy(rectangles=listOf(Rect(-7,4,7,5)),walls=emptyMap(),height=9)
        val floor=original.nodes.single { it.id=="floor" }.copy(thickness=2)
        val ceiling=original.nodes.single { it.id=="ceiling" }.copy(thickness=3,occupied=true)
        val compiled=eval(original.replace(room).replace(floor).replace(ceiling)).room("room")
        assertEquals(9,compiled.outerWidth);assertEquals(7,compiled.outerDepth)
        assertEquals(13,compiled.totalHeight);assertEquals(8,compiled.usableHeight)
        assertEquals(-2,compiled.minimum);assertEquals(11,compiled.maximum)
    }

    @Test fun `a building without separate roof uses its top ceiling as the flat roof`() {
        val original=BuildingGraph.starter()
        val output=original.nodes.single { it.kind==BuildingKind.BUILDING }.copy(roof="")
        val g=original.replace(output)
        val evaluation=eval(g);val storey=evaluation.storey("storey");val building=evaluation.building()
        assertEquals(storey.plan.placements.size,building.plan.placements.size)
        assertTrue(building.plan.placements.none { it.component=="roof" })
        assertEquals(25,building.plan.placements.count { it.pos.y==building.storeys.single().maximum-1 && it.component=="ceiling" })
        assertEquals(building,eval(g.remove("roof")).building())
        assertTrue(building.plan.placements.none { it.pos in building.plan.clear })
    }

    @Test fun `legacy graph JSON defaults to horizontal and new axes roundtrip without schema changes`() {
        val a=solid("a",2,2);val b=solid("b",3,2)
        val pattern=BuildingNode("pattern",BuildingKind.PATTERN,inputs=listOf("a","b"))
        val g=graph(a,b,pattern);val gson=Gson();val legacy=gson.toJsonTree(g).asJsonObject
        legacy.getAsJsonArray("nodes").forEach { it.asJsonObject.remove("patternAxis") }
        val restored=gson.fromJson(legacy,BuildingGraph::class.java)
        assertEquals(1,restored.schema);assertTrue(restored.nodes.all { it.patternAxis=="horizontal" })
        assertEquals(eval(g).segment("pattern"),eval(restored).segment("pattern"))
        val stacked=g.replace(b.copy(length=2,height=3)).replace(pattern.copy(patternAxis="vertical"))
        val roundtrip=gson.fromJson(gson.toJson(stacked),BuildingGraph::class.java)
        assertEquals(stacked,roundtrip);assertEquals(1,roundtrip.schema)
        assertEquals(eval(stacked).segment("pattern"),eval(roundtrip).segment("pattern"))
    }
}
