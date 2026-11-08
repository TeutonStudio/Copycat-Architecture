package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class SemanticConversionTest {
    private val forms=mapOf("full@1" to Form(id="full",parts=listOf(Part("copycats:copycat_block"))))
    private fun compile(graph:BuildingGraph)=BuildingGraphCompiler.compile(Building(schema=2,graph=graph),forms,emptyMap()).plan

    @Test fun `conversion is explicit immutable idempotent and preserves complete prefab geometry`() {
        val before=BuildingGraph.starter().copy(nodes=BuildingGraph.starter().nodes.map { if(it.id=="wall") it.copy(module="") else it })
        val snapshot=before.copy(nodes=before.nodes.toList())
        val after=SemanticConversion.convert(before,"storey")
        assertEquals(snapshot,before)
        assertEquals(3,after.schema)
        assertEquals(FacadeConfig(),after.nodes.first { it.id=="storey" }.facade)
        assertEquals(null,before.nodes.first { it.id=="room" }.roomConfig)
        val target=after.nodes.first { it.id==after.nodes.first { n -> n.id=="storey" }.inputs.single() }
        assertNotEquals("room",target.id)
        assertEquals(RoomMode.FOOTPRINT,target.roomConfig!!.mode)
        assertEquals(before.nodes.first { it.id=="room" }.walls,target.walls)
        assertEquals(before.nodes.first { it.id=="room" }.rectangles,target.rectangles)
        assertEquals(after,SemanticConversion.convert(after,"storey"))
        val original=compile(before);val converted=compile(after)
        assertEquals(original.placements,converted.placements)
        assertEquals(original.clear.toSet(),converted.clear.toSet())
        assertEquals(original.materials,converted.materials)
    }

    @Test fun `shared room sources and group definitions are retained without broken references`() {
        val before=BuildingGraph.starter().let { graph -> graph.copy(nodes=graph.nodes+BuildingNode("other",BuildingKind.STOREY,inputs=listOf("room"))) }
        val grouped=GraphGroups.create(before,setOf("storey"),"Stockwerk")
        val after=SemanticConversion.convert(grouped,"storey")
        assertEquals(listOf("room"),after.nodes.first { it.id=="other" }.inputs)
        assertEquals(null,after.nodes.first { it.id=="room" }.roomConfig)
        assertTrue(after.nodeGroups.none { "storey" in it.nodes })
        assertTrue(after.nodes.map { it.id }.distinct().size==after.nodes.size)
        BuildingGraphCompiler.structure(after)
    }

    @Test fun `direct wall stockworks require explicit room authoring`() {
        val graph=BuildingGraph.starter().let { graph -> graph.copy(nodes=graph.nodes.map { if(it.id=="storey") it.copy(storeyMode="walls") else it }) }
        assertFailsWith<IllegalArgumentException> { SemanticConversion.convert(graph,"storey") }
    }

    @Test fun `UUID source identities and collisions remain within the node ID limit`() {
        val id="27710889-cb38-43e6-a196-cc7fc6320add"
        val before=BuildingGraph.starter().let { graph -> graph.copy(nodes=graph.nodes.map { node ->
            when(node.id) { "room"->node.copy(id=id);"storey"->node.copy(inputs=listOf(id));else->node }
        }+BuildingNode(id.take(27)+"_semantic")) }
        val after=SemanticConversion.convert(before,"storey")
        assertTrue(after.nodes.all { it.id.length<=36 })
        assertEquals(after.nodes.size,after.nodes.map { it.id }.distinct().size)
        BuildingGraphCompiler.structure(after)
    }
}
