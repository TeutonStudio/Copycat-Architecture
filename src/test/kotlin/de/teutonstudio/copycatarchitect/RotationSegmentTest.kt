package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class RotationSegmentTest {
    private val forms=mapOf("profile@1" to Form(parts=listOf(Part("copycats:copycat_byte"))))
    private fun graph(length:Int=5)=BuildingGraph(nodes=listOf(
        BuildingNode("empty_wall",BuildingKind.SEGMENT,block="minecraft:air",length=1),
        BuildingAssembly.newNode(BuildingKind.INT,"count").copy(countValue=length),
        BuildingAssembly.newNode(BuildingKind.COPYCAT,"source").copy(form="profile@1"),
        BuildingAssembly.newNode(BuildingKind.ROTATION_SEGMENT,"column").copy(inputs=listOf("source"),dimensions=mapOf("length" to "count")),
        BuildingNode("building",BuildingKind.BUILDING)))
    private fun evaluation(g:BuildingGraph)=BuildingGraphCompiler.Evaluation(g,Building().palette,forms,emptyMap())

    @Test fun `four Y rotations form a vertical segment with exact union geometry`() {
        val g=graph();val e=evaluation(g);val segment=e.segment("column")
        assertEquals(Triple(1,5,1),Triple(segment.length,segment.height,segment.depth))
        assertEquals((0..4).map { P(0,it,0) },segment.cells.map { it.cell.pos })
        val expected=(0..3).flatMap { Geometry.rotate(forms.getValue("profile@1").parts,it) }
        val plan=e.preview("column")
        assertEquals(5,plan.placements.size)
        assertTrue(plan.placements.all { Geometry.exact(it.parts).contains(Geometry.exact(expected)) && Geometry.exact(expected).contains(Geometry.exact(it.parts)) })
        val rotated=g.replace(g.nodes.first { it.id=="source" }.copy(quarterTurns=1))
        assertTrue(evaluation(rotated).preview("column").placements.all { Geometry.exact(it.parts).contains(Geometry.exact(expected)) && Geometry.exact(expected).contains(Geometry.exact(it.parts)) })
    }
    @Test fun `overlapping symmetric copies and native blocks are charged only once per layer`() {
        for(block in listOf("","minecraft:stone","minecraft:air")) {
            val g=graph(3).replace(graph().nodes.first { it.id=="source" }.copy(form="full@1",block=block))
            val e=BuildingGraphCompiler.Evaluation(g,Building().palette,mapOf("full@1" to Form(parts=listOf(Part("copycats:copycat_block")))),emptyMap())
            val plan=e.preview("column")
            assertEquals(if(block=="minecraft:air") 0 else 3,plan.placements.size)
            if(block.isEmpty()) {
                val one=e.preview("source")
                assertEquals(one.raw*3,plan.raw)
                assertEquals(one.materials.mapValues { it.value*3 },plan.materials)
            }
        }
    }
    @Test fun `length sockets enforce integer type and range and restore local fallback on deletion`() {
        val g=graph();val n=g.nodes.first { it.id=="column" }
        assertEquals(setOf(BuildingKind.INT),BuildingPorts.of(n,g).first { it.key=="length" }.accepts)
        assertEquals("WALL",buildingGraphType(n.kind))
        assertFails { g.connect("column","length","source") }
        assertFails { g.connect("column","in:0","count") }
        for(value in listOf(0,-1,129)) assertFails { BuildingGraphCompiler.structure(g.replace(g.nodes.first { it.id=="count" }.copy(countValue=value))) }
        for(value in listOf(1,128)) assertEquals(value,evaluation(graph(value)).segment("column").height)
        assertEquals(n.length,evaluation(g.remove("count")).segment("column").height)
        assertEquals(n.length,evaluation(g.connect("column","length","")).segment("column").height)
        assertEquals(g,Gson().fromJson(Gson().toJson(g),BuildingGraph::class.java))
        val grouped=GraphGroups.create(g,setOf("count","source","column"))
        val copy=GraphGroups.instantiate(grouped,grouped.nodeGroups.single().id)
        val duplicate=copy.nodes.first { it.kind==BuildingKind.ROTATION_SEGMENT && it.id!="column" }
        assertNotEquals("count",duplicate.dimensions?.get("length"))
        assertEquals(5,evaluation(copy).segment(duplicate.id).height)
    }
}
