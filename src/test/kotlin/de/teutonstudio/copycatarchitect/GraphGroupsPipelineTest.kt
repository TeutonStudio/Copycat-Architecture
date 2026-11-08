package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.client.*
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/** The native canvas prunes deleted members before Screens restores their definition slots. */
class GraphGroupsPipelineTest {
    private fun shapeDiagram(graph:NodeGraph)=KilaGraphData(
        graph.nodes.map { KilaNode(it.id,it.id,it.x,it.y,it.inputs.indices.map(Int::toString),output=it.kind!=NodeKind.OUTPUT) },
        graph.nodes.flatMap { node -> node.inputs.mapIndexedNotNull { slot,source -> source.takeIf(String::isNotEmpty)?.let { KilaLink(it,node.id,slot) } } },graph.nodeGroups)

    private fun buildingDiagram(graph:BuildingGraph):KilaGraphData {
        val ports=graph.nodes.associate { it.id to BuildingPorts.of(it,graph,true) }
        return KilaGraphData(graph.nodes.map { KilaNode(it.id,it.id,it.x,it.y,ports.getValue(it.id).map { port -> port.key },output=it.kind!=BuildingKind.BUILDING) },
            graph.nodes.flatMap { node -> ports.getValue(node.id).mapIndexedNotNull { slot,port -> port.source.takeIf(String::isNotEmpty)?.let { KilaLink(it,node.id,slot) } } },graph.nodeGroups)
    }

    private fun restoredAfterDeletion(diagram:KilaGraphData,removed:String,expanded:Boolean):KilaGraphData {
        val projection=KilaGroupProjection(diagram,if(expanded) diagram.nodeGroups.map { it.id }.toSet() else emptySet())
        val view=projection.display
        return projection.restore(view.copy(nodes=view.nodes.filterNot { it.id==removed },links=view.links.filterNot { it.source==removed || it.target==removed }))
    }

    @Test fun `shape member deletion survives the projected Screens metadata merge and domain validation`() {
        val graph=NodeGraph(nodes=listOf(
            GraphNode("source",part=Part("copycats:copycat_block")),
            GraphNode("external",part=Part("copycats:copycat_slab")),
            GraphNode("union",NodeKind.UNION,listOf("source","external")),
            GraphNode("result",NodeKind.OUTPUT,listOf("union"))))
        val grouped=GraphGroups.create(graph,setOf("source","union"))
        val instances=GraphGroups.instantiate(grouped,grouped.nodeGroups.single().id)
        for(entireInstance in listOf(false,true)) {
            val removed=if(entireInstance) "@group:${instances.nodeGroups.first().id}" else "source"
            val data=restoredAfterDeletion(shapeDiagram(instances),removed,!entireInstance)
            val original=instances.copy(nodeGroups=data.nodeGroups.map { group -> instances.nodeGroups.firstOrNull { it.id==group.id }?.let { group.copy(nodes=it.nodes) } ?: group })
            val changed=DiagramEdits.shape(original,DEFAULT_GROUPS,data.nodes.map { DiagramPosition(it.id,it.x,it.y) },data.links.map { DiagramConnection(it.source,it.target,it.slot.toString()) })
            GraphCompiler.structure(changed,DEFAULT_GROUPS)
            if(entireInstance) assertEquals(listOf(instances.nodeGroups.last()),changed.nodeGroups)
            else {
                assertTrue(changed.nodeGroups.all { it.nodes.size==1 })
                assertTrue(changed.nodes.filter { it.kind==NodeKind.UNION }.all { it.inputs.first().isEmpty() })
            }
        }
    }

    @Test fun `building member deletion survives the projected Screens metadata merge and domain validation`() {
        val grouped=GraphGroups.create(BuildingGraph.starter(),setOf("floor","ceiling","room"))
        val instances=GraphGroups.instantiate(grouped,grouped.nodeGroups.single().id)
        val ports=instances.nodes.associate { it.id to BuildingPorts.of(it,instances,true) }
        for(entireInstance in listOf(false,true)) {
            val removed=if(entireInstance) "@group:${instances.nodeGroups.first().id}" else "floor"
            val data=restoredAfterDeletion(buildingDiagram(instances),removed,!entireInstance)
            val original=instances.copy(nodeGroups=data.nodeGroups.map { group -> instances.nodeGroups.firstOrNull { it.id==group.id }?.let { group.copy(nodes=it.nodes) } ?: group })
            val changed=DiagramEdits.building(original,data.nodes.map { DiagramPosition(it.id,it.x,it.y) },data.links.map { DiagramConnection(it.source,it.target,ports.getValue(it.target)[it.slot].key) })
            BuildingGraphCompiler.structure(changed)
            if(entireInstance) assertEquals(listOf(instances.nodeGroups.last()),changed.nodeGroups)
            else {
                assertTrue(changed.nodeGroups.all { it.nodes.size==2 })
                assertTrue(changed.nodes.filter { it.kind==BuildingKind.ROOM }.all { it.floor.isEmpty() })
            }
        }
    }
}
