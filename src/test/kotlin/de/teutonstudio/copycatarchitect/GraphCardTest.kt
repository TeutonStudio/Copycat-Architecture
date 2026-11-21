package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import kotlin.test.*

class GraphCardTest {
    @Test fun `result type constrains only the terminal while other value families coexist`() {
        var g=GraphCards.legacyStarter()
        val count=BuildingNode("number",BuildingKind.INT,countValue=6)
        val vector=BuildingNode("vector",BuildingKind.POSITION,inputs=listOf(count.id,count.id,count.id))
        g=g.copy(nodes=g.nodes+count+vector)
        BuildingGraphCompiler.structure(g)
        val result=g.resultNode()
        val changed=g.replace(result.copy(resultType="POSITION",inputs=listOf(vector.id)))
        BuildingGraphCompiler.structure(changed)
        assertEquals(setOf("POSITION"),BuildingPorts.of(changed.resultNode(),changed).single().accepts.filter { it!=BuildingKind.CONDITIONAL && it !in GraphPrograms.kinds }.map(::buildingGraphType).toSet())
        assertFails { BuildingGraphCompiler.structure(changed.replace(changed.resultNode().copy(inputs=listOf(count.id)))) }
        assertEquals(8,changed.withCurrentSchema().schema)
        assertFails { BuildingGraphCompiler.structure(changed.copy(schema=7)) }
        assertEquals(changed,Gson().fromJson(Gson().toJson(changed),BuildingGraph::class.java))
    }
    @Test fun `every semantic result accepts every producer of its type`() {
        val base=GraphCards.legacyStarter()
        for(kind in BuildingKind.entries.filter { it !in GraphPrograms.kinds }) {
            val type=buildingGraphType(kind)
            val source=GraphModels.newBuilding(kind,true).copy(id="candidate")
            val result=base.resultNode().copy(resultType=type,inputs=listOf(source.id))
            val graph=base.copy(nodes=listOf(base.nodes.first { it.id=="empty_wall" },source,result))
            val port=BuildingPorts.of(result,graph).single()
            assertTrue(kind in port.accepts,"$kind must be accepted by $type")
        }
    }
    @Test fun `inline Copycat sources remain editable geometry with native materials and transforms`() {
        val base=GraphCards.legacyStarter()
        val source=base.nodes.first { it.id=="source" }.copy(inlinePart=Part("copycats:copycat_byte"),surfaces=mapOf("body" to "minecraft:gold_block"),copycatTransforms=listOf(CopycatTransform(CopycatTransformKind.POSITION,offset=P(1,0,0))))
        val graph=base.replace(source)
        val plan=BuildingGraphCompiler.Evaluation(graph,Building().palette,emptyMap(),emptyMap()).preview(graph.resultNode().id)
        assertEquals(1,plan.placements.size)
        assertEquals("minecraft:gold_block",plan.placements.single().surfaces["body"])
        assertTrue(Geometry.exact(plan.placements.single().parts).cells.flatMap { it.vertices() }.minOf { it.x.toDouble() }>=1)
    }
    @Test fun `buildings become ordinary nodes and only the card terminal is protected`() {
        val old=BuildingGraph.starter()
        val result=BuildingNode("result",BuildingKind.BUILDING,resultType="BUILDING",inputs=listOf("building"))
        val graph=old.copy(nodes=old.nodes+result).withCurrentSchema()
        BuildingGraphCompiler.structure(graph)
        val duplicate=GraphDuplicates.building(graph,listOf("building"),listOf("building_copy"))
        assertEquals(2,duplicate.nodes.count { it.kind==BuildingKind.BUILDING && it.resultType==null })
        assertTrue(duplicate.remove("building_copy").nodes.none { it.id=="building_copy" })
        assertFails { graph.remove("result") }
    }
}
