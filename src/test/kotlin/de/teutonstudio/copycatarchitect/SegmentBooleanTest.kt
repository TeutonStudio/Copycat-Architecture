package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class SegmentBooleanTest {
    private val sources=mapOf("a@1" to Form(parts=listOf(Part("copycats:copycat_block"))),
        "b@1" to Form(parts=listOf(Part("copycats:copycat_slab")) ))
    private fun source(id:String)=BuildingNode(id,BuildingKind.SEGMENT,module="",form="$id@1",length=1,height=1)
    private fun graph(kind:BuildingKind,refs:List<String> = listOf("a","b"))=BuildingGraph.starter().let { it.copy(nodes=it.nodes+listOf(source("a"),source("b"),BuildingNode("op",kind,inputs=refs))) }
    private fun eval(g:BuildingGraph)=BuildingGraphCompiler.Evaluation(g,Building().palette,sources,emptyMap())
    private fun volume(plan:Plan)=plan.placements.sumOf { Geometry.solid(it.parts).count() }

    @Test fun `all operators clip subcell geometry and round trip through JSON`() {
        for((kind,count) in listOf(BuildingKind.SEGMENT_UNION to 4096,BuildingKind.SEGMENT_INTERSECTION to 2048,
            BuildingKind.SEGMENT_DIFFERENCE to 2048,BuildingKind.SEGMENT_SYMMETRIC_DIFFERENCE to 2048)) {
            val g=graph(kind);assertEquals(count,volume(eval(g).preview("op")))
            val restored=Gson().fromJson(Gson().toJson(g),BuildingGraph::class.java)
            assertEquals(count,volume(eval(restored).preview("op")))
            assertTrue(BuildingPorts.of(g.nodes.last()).all { SegmentBooleans.kinds.all(it.accepts::contains) })
        }
    }
    @Test fun `xor is odd parity and empty results can feed another operator`() {
        assertEquals(4096,volume(eval(graph(BuildingKind.SEGMENT_SYMMETRIC_DIFFERENCE,listOf("a","a","a"))).preview("op")))
        val g=graph(BuildingKind.SEGMENT_SYMMETRIC_DIFFERENCE,listOf("a","a"))
        assertEquals(0,volume(eval(g).preview("op")))
        val outer=g.copy(nodes=g.nodes+BuildingNode("outer",BuildingKind.SEGMENT_UNION,inputs=listOf("op","b")))
        assertEquals(2048,volume(eval(outer).preview("outer")))
    }
    @Test fun `difference preserves A dimensions and grouped materials`() {
        val g=graph(BuildingKind.SEGMENT_DIFFERENCE).let { it.copy(nodes=it.nodes.map { n -> if(n.id=="a") n.copy(length=3) else if(n.id=="op") n.copy(materialMode=MaterialMode.GROUPED,role="frame") else n }) }
        val e=eval(g);val result=e.segment("op")
        assertEquals(3,result.length);assertEquals(setOf(P(),P(1,0,0),P(2,0,0)),result.cells.map { it.cell.pos }.toSet())
        assertTrue(e.preview("op").placements.all { it.surfaces.values.all { material -> material==Building().palette["frame"] } })
    }
    @Test fun `normal blocks keep their item cost and partial conversion is explicit`() {
        val g=graph(BuildingKind.SEGMENT_DIFFERENCE).let { it.copy(nodes=it.nodes.map { n -> if(n.id=="a") n.copy(block="minecraft:stone") else n }) }
        assertFailsWith<IllegalArgumentException> { eval(g).preview("op") }
        val removed=g.copy(nodes=g.nodes.map { n -> if(n.id=="op") n.copy(inputs=listOf("a","a")) else n })
        assertEquals(0,eval(removed).preview("op").placements.size)
    }
    @Test fun `incomplete inputs and cycles remain invalid`() {
        val g=graph(BuildingKind.SEGMENT_UNION,listOf("a",""))
        BuildingGraphCompiler.structure(g)
        assertFails { eval(g).preview("op") }
        assertFails { BuildingGraphCompiler.structure(graph(BuildingKind.SEGMENT_UNION,listOf("a","op"))) }
        assertFails { BuildingGraphCompiler.structure(graph(BuildingKind.SEGMENT_DIFFERENCE,listOf("a","b","a"))) }
    }
    @Test fun `rational cuts match the exact Copycat operation without raster rounding`() {
        val reflected=PartRotations.mirror(Part("copycats:copycat_byte",offset=P(4,4,4)),CopycatMirrorPlane.BODY_PPP)
        val saved=GraphNode("saved",NodeKind.SAVED,savedCopycat=SavedCopycat(parts=listOf(reflected)))
        val exactForm=Form(graph=NodeGraph(nodes=listOf(saved,GraphNode("result",NodeKind.OUTPUT,listOf("saved")))))
        val g=graph(BuildingKind.SEGMENT_DIFFERENCE).let { it.copy(nodes=it.nodes.map { n -> if(n.id=="b") n.copy(form="exact@1") else n }) }
        val e=BuildingGraphCompiler.Evaluation(g,Building().palette,sources+("exact@1" to exactForm),emptyMap())
        val actual=Geometry.exact(e.preview("op").placements.single().parts)
        val expected=Geometry.exact(Part("copycats:copycat_block")).minus(Geometry.exact(reflected))
        assertTrue(actual.contains(expected));assertTrue(expected.contains(actual))
    }

}
