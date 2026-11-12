package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.Json
import org.junit.jupiter.api.Test
import kotlin.test.*

class BuildingCountsTest {
    private fun count(id:String,value:Int)=BuildingNode(id,BuildingKind.INT,countValue=value)
    private fun graph(vararg extra:BuildingNode)=BuildingGraph.starter().let { it.copy(nodes=it.nodes+extra) }
    @Test fun `integer expressions are exact and division truncates toward zero`() {
        for((kind,expected) in listOf(BuildingKind.INT_ADD to -4,BuildingKind.INT_SUBTRACT to -10,BuildingKind.INT_MULTIPLY to -21,
            BuildingKind.INT_DIVIDE to -2,BuildingKind.INT_MIN to -7,BuildingKind.INT_MAX to 3)) {
            val g=graph(count("a",-7),count("b",3),BuildingNode("op",kind,inputs=listOf("a","b")))
            assertEquals(expected,BuildingCounts.Evaluation(g).value("op"))
        }
    }
    @Test fun `integer failures are explicit including minimum divided by minus one`() {
        for((kind,a,b) in listOf(Triple(BuildingKind.INT_ADD,Int.MAX_VALUE,1),Triple(BuildingKind.INT_SUBTRACT,Int.MIN_VALUE,1),
            Triple(BuildingKind.INT_MULTIPLY,Int.MAX_VALUE,2),Triple(BuildingKind.INT_DIVIDE,1,0),Triple(BuildingKind.INT_DIVIDE,Int.MIN_VALUE,-1))) {
            assertFailsWith<IllegalArgumentException> { BuildingCounts.Evaluation(graph(count("a",a),count("b",b),BuildingNode("op",kind,inputs=listOf("a","b")))).value("op") }
        }
        assertFails { BuildingCounts.Evaluation(graph(count("a",1),BuildingNode("op",BuildingKind.INT_ADD,inputs=listOf("op","a")))).value("op") }
        assertFails { BuildingCounts.Evaluation(graph(BuildingNode("bad",BuildingKind.ROOM))).value("bad") }
    }
    @Test fun `dimension wiring changes the real plan and disconnect restores its saved fallback`() {
        val g=graph(count("two",2),count("three",3),BuildingNode("sum",BuildingKind.INT_ADD,inputs=listOf("two","three")))
        val connected=g.connect("wall","length","sum")
        assertEquals(3,connected.nodes.first { it.id=="wall" }.length)
        assertEquals(5,connected.resolvedNode("wall").length)
        val forms=mapOf("full@1" to Form(id="full"))
        assertEquals(5,BuildingGraphCompiler.Evaluation(connected,Building().palette,forms,emptyMap()).segment("wall").length)
        assertEquals(connected,Json.decode<BuildingGraph>(Json.encode(connected)))
        assertEquals(3,connected.connect("wall","length","").resolvedNode("wall").length)
        for(value in listOf(0,129)) assertFails { graph(count("bad",value)).connect("wall","length","bad") }
        assertFails { g.connect("wall","length","room") }
    }
    @Test fun `axis colors follow uses instead of changing the integer type`() {
        val g=graph(count("two",2),count("three",3),BuildingNode("sum",BuildingKind.INT_ADD,inputs=listOf("two","three")))
        val x=g.connect("wall","length","sum")
        assertEquals(setOf("length"),BuildingCounts.axisUsage(x)["two"])
        val xz=x.connect("wall","depth","two")
        assertEquals(setOf("length","depth"),BuildingCounts.axisUsage(xz)["two"])
        assertEquals(setOf("depth"),BuildingCounts.axisUsage(xz.connect("wall","length",""))["two"])
    }
    @Test fun `layout dimensions have one authoritative source and validate derived geometry`() {
        val g=graph(count("five",5),BuildingNode("layout",BuildingKind.SEGMENT_LAYOUT))
        val layout=g.connect("layout","length","five")
        val wall=layout.nodes.first { it.id=="wall" }.copy(assembly=true,layout="layout")
        assertEquals(5,layout.replace(wall).resolvedNode("wall").length)
        assertFails { layout.replace(wall).connect("wall","length","five") }
        val area=BuildingNode("area",BuildingKind.AREA,coordinates=listOf(P(),P(1,0,0)))
        assertFails { layout.copy(nodes=layout.nodes+area).connect("layout","geometry","area") }
    }
    @Test fun `connected dimensions enforce the combined volume budget before allocation`() {
        val g=graph(count("large",128))
        val wall=g.nodes.first { it.id=="wall" }.copy(dimensions=mapOf("length" to "large","height" to "large","depth" to "large"))
        assertFailsWith<IllegalArgumentException> { BuildingCounts.resolve(g,wall) }
    }
    @Test fun `deleting an operand preserves a repairable draft and never compiles fallback as a valid result`() {
        val g=graph(count("two",2),count("three",3),BuildingNode("sum",BuildingKind.INT_ADD,inputs=listOf("two","three"))).connect("wall","length","sum")
        val removed=g.remove("two")
        BuildingGraphCompiler.structure(removed)
        assertEquals(mapOf("length" to "sum"),removed.nodes.first { it.id=="wall" }.dimensions)
        assertFailsWith<BuildingCounts.IncompleteCount> { BuildingGraphCompiler.Evaluation(removed,Building().palette,mapOf("full@1" to Form()),emptyMap()).segment("wall") }
        val repaired=removed.connect("sum","in:0","three")
        assertEquals(6,repaired.resolvedNode("wall").length)
    }
    @Test fun `incomplete layout dimensions retain assignments and area references for repair`() {
        val area=BuildingNode("area",BuildingKind.AREA,coordinates=(0..4).map { P(it,0,0) })
        val layout=BuildingNode("layout",BuildingKind.SEGMENT_LAYOUT,geometry="area",dimensions=mapOf("length" to "sum"),layoutCells=listOf(SegmentLayoutCell(4,0)))
        val g=graph(count("two",2),count("three",3),BuildingNode("sum",BuildingKind.INT_ADD,inputs=listOf("two","three")),area,layout)
        BuildingGraphCompiler.structure(g)
        val removed=g.remove("two")
        BuildingGraphCompiler.structure(removed)
        assertEquals(layout.layoutCells,removed.nodes.first { it.id=="layout" }.layoutCells)
        assertFailsWith<BuildingCounts.IncompleteCount> { BuildingGraphCompiler.Evaluation(removed,Building().palette,emptyMap(),emptyMap()) }
    }
}
