package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class RoomArrangementTest {
    private fun area(index:Int,interior:Set<P>,walls:Set<P>,low:Int=0,high:Int=4)=RoomArrangementArea(index,"Room ${index+1}",interior,walls,low,high)
    @Test fun `shared wall cells are legal while walls invading another interior are reported with direction`() {
        val wall=setOf(P(1,0,0),P(1,1,0));val a=area(0,setOf(P()),wall)
        val shared=area(1,setOf(P(2,0,0)),wall)
        assertTrue(RoomArrangement.conflicts(listOf(a,shared)).isEmpty())
        val intrusion=RoomArrangement.conflicts(listOf(a,shared.copy(interior=setOf(P(1,0,0))))).single()
        assertEquals(RoomConflictKind.WALL_IN_INTERIOR,intrusion.kind)
        assertEquals(a,intrusion.first);assertEquals(shared.index,intrusion.second.index)
        assertEquals(setOf(P(1,0,0)),intrusion.cells)
        assertContains(intrusion.description(),"Wall of 1. Room 1")
    }
    @Test fun `interior overlaps are distinct from wall conflicts and keep exact grid locations`() {
        val cells=setOf(P(-3,0,5),P(-2,0,5))
        val conflicts=RoomArrangement.conflicts(listOf(area(0,cells,emptySet()),area(1,cells,emptySet())))
        assertEquals(RoomConflictKind.INTERIORS,conflicts.single().kind)
        assertEquals(cells,conflicts.single().cells)
    }
    @Test fun `vertically disjoint rooms do not conflict and wall height is checked independently`() {
        val a=area(0,setOf(P()),setOf(P(1,0,0)),0,2)
        val b=area(1,setOf(P(1,0,0)),emptySet(),2,4)
        assertTrue(RoomArrangement.conflicts(listOf(a,b)).isEmpty())
        assertTrue(RoomArrangement.conflicts(listOf(a.copy(maximum=4),b)).isEmpty())
        assertEquals(RoomConflictKind.WALL_IN_INTERIOR,RoomArrangement.conflicts(listOf(a.copy(maximum=4,walls=setOf(P(1,2,0))),b)).single().kind)
    }
    private fun graph():BuildingGraph {
        val initial=BuildingGraph.explicitStarter(joinWallEnds=false)
        return initial.replace(initial.nodes.single { it.id=="wall_layout" }.copy(depth=2))
            .replace(initial.nodes.single { it.id=="plate_layout" }.copy(length=7,height=7,depth=2))
            .replace(initial.nodes.single { it.id=="storey" }.copy(inputs=listOf("room","room"),offsets=listOf(P(),P(4,0,0))))
    }
    private fun evaluate(graph:BuildingGraph)=BuildingGraphCompiler.Evaluation(graph,Building().palette,
        mapOf("full@1" to Form(id="full",parts=listOf(Part("copycats:copycat_block")))),emptyMap())
    @Test fun `two block wall collision is rejected by the compiler even when interiors do not overlap`() {
        val graph=graph();val evaluation=evaluate(graph)
        val error=assertFails { evaluation.storey("storey") }
        assertContains(error.message.orEmpty(),"intrudes into the interior")
        val room=evaluation.room("room")
        val node=graph.nodes.single { it.id=="room" }
        val conflicts=RoomArrangement.conflicts(listOf(RoomArrangementArea.of(0,node,room),RoomArrangementArea.of(1,node,room,P(4,0,0))))
        assertTrue(conflicts.isNotEmpty())
        assertTrue(conflicts.all { it.kind==RoomConflictKind.WALL_IN_INTERIOR })
        assertTrue(conflicts.any { P(2,0,0) in it.cells })
    }
    @Test fun `moving two block walls apart makes the same arrangement legal`() {
        val initial=graph();val node=initial.nodes.single { it.id=="storey" }
        val graph=initial.replace(node.copy(offsets=listOf(P(),P(5,0,0))))
        assertTrue(evaluate(graph).storey("storey").plan.placements.isNotEmpty())
    }
}
