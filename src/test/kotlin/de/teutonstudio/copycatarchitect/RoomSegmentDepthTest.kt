package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class RoomSegmentDepthTest {
    private val forms=mapOf("full@1" to Form(id="full",parts=listOf(Part("copycats:copycat_block"))))
    private fun evaluation(graph:BuildingGraph)=BuildingGraphCompiler.Evaluation(graph,Building().palette,forms,emptyMap())

    @Test fun `wall thickness closes deep corners and determines horizontal plate bounds`() {
        val original=BuildingGraph.explicitStarter(joinWallEnds=false)
        val graph=original.copy(nodes=original.nodes.map { n -> when(n.id) {
            "wall_layout"->n.copy(depth=2)
            "plate_layout"->n.copy(length=7,height=7,depth=2)
            else->n
        } })
        val room=evaluation(graph).room("room")
        assertEquals(7,room.outerWidth);assertEquals(7,room.outerDepth)
        assertEquals(-2,room.minimum);assertEquals(6,room.maximum)
        assertEquals(8,room.totalHeight)
        assertEquals(160,room.plan.placements.count { it.component=="wall" })
        assertEquals(98,room.plan.placements.count { it.component=="floor" })
        assertEquals(98,room.plan.placements.count { it.component=="ceiling" })
        assertEquals((0..3).map { P(-2,it,-2) }.toSet(),room.plan.placements.filter { it.pos.x== -2 && it.pos.z== -2 && it.component=="wall" }.map { it.pos }.toSet())
        assertEquals(49,room.shell.size)
        assertTrue(room.plan.placements.none { it.pos in room.plan.clear })
        assertEquals(room.shell,evaluation(graph).storey("storey").footprint)
        assertEquals(356,evaluation(graph).building().plan.placements.size)
    }

    @Test fun `air corner Copycat requests a clear joint without resolving its stale form`() {
        val initial=BuildingGraph.explicitStarter(joinWallEnds=false)
        val air=BuildingAssembly.newNode(BuildingKind.COPYCAT,"air_corner").copy(block="minecraft:air",form="missing@1")
        val graph=initial.copy(nodes=initial.nodes+air).replace(initial.nodes.single { it.id=="room" }.copy(corner=air.id))
        val room=evaluation(graph).room("room")
        val corners=RoomGeometry.corners(graph.resolvedNode("room").rectangles).keys
        assertTrue(corners.all { p -> (0..3).all { p+P(0,it,0) in room.plan.clear } })
        assertEquals(98,room.plan.placements.size)
        assertTrue(room.plan.materials.keys.none { it=="minecraft:air" })
    }
}
