package de.teutonstudio.copycatarchitect
import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.client.*
import de.teutonstudio.copycatarchitect.server.Library
import kotlin.test.*
class CopycatParameterPreviewTest {
    @Test fun `typed values have a value preview instead of a geometry failure`() {
        for(kind in listOf(NodeKind.MIRRORING,NodeKind.POSITION)) {
            val graph=NodeGraph(schema=4,nodes=listOf(GraphNode("value",kind),GraphNode("result",NodeKind.OUTPUT,listOf(""))))
            val preview=ShapeNodePreviews({Form(graph=graph)},{"disconnected"},{graph}).get("value")
            assertEquals("",preview.error);assertEquals(kind,preview.parameter);assertTrue(preview.value.isNotEmpty())
        }
        for(kind in listOf(BuildingKind.ROTATION,BuildingKind.MIRRORING,BuildingKind.POSITION)) {
            val graph=BuildingGraph(schema=5,nodes=listOf(BuildingNode("empty_wall",BuildingKind.SEGMENT,block="minecraft:air",length=1),BuildingNode("value",kind),BuildingNode("result",BuildingKind.BUILDING)))
            val preview=BuildingNodePreviews({Building(schema=2,graph=graph)},{Library()}).get("value")
            assertTrue(preview.plan.isSuccess,preview.plan.exceptionOrNull()?.message);assertTrue(preview.plan.getOrThrow().placements.isEmpty())
        }
    }
}
