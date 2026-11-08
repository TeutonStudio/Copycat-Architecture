package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class RoomVerticalEdgesTest {
    private val forms=mapOf("full@1" to Form(id="full",parts=listOf(Part("copycats:copycat_block"))),
        "detail@1" to Form(id="detail",parts=listOf(Part("copycats:copycat_byte",offset=P(1,2,3)))))
    private fun evaluation(graph:BuildingGraph)=BuildingGraphCompiler.Evaluation(graph,Building().palette,forms,emptyMap())
    private fun source(id:String="edge")=BuildingNode(id,BuildingKind.SEGMENT,module="",form="detail@1",length=1,height=4)

    @Test fun `every convex and concave room vertex has its own typed segment socket`() {
        val graph=BuildingGraph.explicitStarter(joinWallEnds=false);val room=graph.resolvedNode("room")
        val edges=RoomVerticalEdges.of(room.rectangles)
        assertEquals(setOf("0:0","3:0","0:3","3:3"),edges.map { it.id }.toSet())
        val ports=BuildingPorts.of(room,graph).filter { it.key.startsWith("edge:") }
        assertEquals(4,ports.size)
        assertTrue(ports.all { it.source.isEmpty() && it.height==4 && it.accepts==setOf(BuildingKind.SEGMENT,BuildingKind.PATTERN,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_ROTATE) })
        val irregular=listOf(Rect(width=3,length=1),Rect(width=1,length=3))
        val corners=RoomVerticalEdges.of(irregular)
        assertEquals(6,corners.size);assertEquals(1,corners.count { it.concave })
        assertEquals(P(1,0,1),corners.single { it.concave }.pos)
        assertEquals(6,BuildingPorts.of(room.copy(rectangles=irregular)).count { it.key.startsWith("edge:") })
    }

    @Test fun `edge segments replace automatic corners with their rotated geometry and finishes`() {
        val initial=BuildingGraph.explicitStarter(joinWallEnds=false);val edge=source()
        var graph=initial.copy(nodes=initial.nodes+edge)
        for(corner in RoomVerticalEdges.of(graph.resolvedNode("room").rectangles)) graph=graph.connect("room","edge:${corner.id}",edge.id)
        val compiled=evaluation(graph).room("room")
        val details=compiled.plan.placements.filter { it.component=="trim" }
        assertEquals(16,details.size)
        for(corner in RoomVerticalEdges.of(graph.resolvedNode("room").rectangles)) for(y in 0..3) {
            val actual=details.single { it.pos==corner.pos+P(0,y,0) }
            assertEquals(Geometry.solid(Geometry.rotate(forms.getValue("detail@1").parts,corner.turn)).key(),Geometry.solid(actual.parts).key())
        }
        assertTrue(compiled.plan.placements.none { it.pos in compiled.plan.clear })
        assertFails { graph.connect("room","edge:0:0","wall_copycat") }
        assertFails { graph.connect("room","edge:0:0","room") }
        assertFails { graph.connect("room","edge:999:999",edge.id) }
        val wrong=graph.replace(edge.copy(height=3))
        assertContains(assertFails { evaluation(wrong).room("room") }.message.orEmpty(),"Vertical edge 0:0: needs 1 × 4 × 1")
    }

    @Test fun `concave edge input replaces overlapping wall contributions and air explicitly clears it`() {
        val initial=BuildingGraph.explicitStarter(joinWallEnds=false);val geometry=initial.nodes.single { it.id=="room_geometry" }
        val points=RoomGeometry.cells(listOf(Rect(width=3,length=1),Rect(width=1,length=3))).toList()
        val edited=initial.editRoomGeometry(geometry.id,points)
        val walls=RoomGeometry.edges(edited.resolvedNode("room").rectangles).mapIndexed { i,run ->
            run to source("wall$i").copy(length=run.length,form="full@1")
        }
        val graph=edited.copy(nodes=edited.nodes+source()+walls.map { it.second })
            .replace(edited.nodes.single { it.id=="room" }.copy(walls=walls.associate { it.first.id to it.second.id }))
            .replace(initial.nodes.single { it.id=="plate_layout" }.copy(length=5,height=5))
        val concave=RoomVerticalEdges.of(graph.resolvedNode("room").rectangles).single { it.concave }
        val connected=graph.connect("room","edge:${concave.id}","edge")
        val actual=evaluation(connected).room("room").plan
        assertEquals(4,actual.placements.count { it.pos.copy(y=0)==concave.pos && it.component=="trim" })
        assertTrue(actual.placements.none { it.pos in actual.clear })
        val empty=evaluation(connected.connect("room","edge:${concave.id}","empty_wall")).room("room").plan
        assertTrue((0..3).all { concave.pos+P(0,it,0) in empty.clear })
        assertTrue(empty.placements.none { it.pos.y in 0..3 && it.pos.copy(y=0)==concave.pos })
    }

    @Test fun `deep corners require an entire segment with matching cross section`() {
        val initial=BuildingGraph.explicitStarter(joinWallEnds=false)
        val graph=initial.copy(nodes=initial.nodes+source().copy(length=2,depth=2))
            .replace(initial.nodes.single { it.id=="wall_layout" }.copy(depth=2))
            .replace(initial.nodes.single { it.id=="plate_layout" }.copy(length=7,height=7))
            .connect("room","edge:0:0","edge")
        val plan=evaluation(graph).room("room").plan
        assertEquals(16,plan.placements.count { it.component=="trim" })
        assertEquals(setOf(P(-2,0,-2),P(-2,0,-1),P(-1,0,-2),P(-1,0,-1)),plan.placements.filter { it.component=="trim" }.map { it.pos.copy(y=0) }.toSet())
    }

    @Test fun `geometry edits prune removed vertices and deletion disconnects edge sources`() {
        val initial=BuildingGraph.explicitStarter(joinWallEnds=false).let { it.copy(nodes=it.nodes+source()) }
        val graph=initial.connect("room","edge:0:0","edge").connect("room","edge:3:3","edge")
        val changed=graph.editRoomGeometry("room_geometry",RoomGeometry.cells(listOf(Rect(width=4,length=3))).toList())
        assertEquals(mapOf("0:0" to "edge"),changed.resolvedNode("room").verticalEdges)
        assertTrue(graph.remove("edge").resolvedNode("room").verticalEdges.isNullOrEmpty())
        val restored=Gson().fromJson(Gson().toJson(graph),BuildingGraph::class.java)
        assertEquals(graph,restored)
        assertEquals(evaluation(graph).room("room").plan,evaluation(restored).room("room").plan)
        assertNull(Gson().fromJson("""{"id":"old","kind":"ROOM"}""",BuildingNode::class.java).verticalEdges)
    }

    @Test fun `native graph edits and reusable groups retain independent edge references`() {
        val initial=BuildingGraph.explicitStarter(joinWallEnds=false).let { it.copy(nodes=it.nodes+source()) }.connect("room","edge:0:0","edge")
        val positions=initial.nodes.map { DiagramPosition(it.id,it.x,it.y) }
        val connections=initial.nodes.flatMap { node -> BuildingPorts.of(node,initial,true).mapNotNull { port ->
            port.source.takeIf(String::isNotEmpty)?.let { DiagramConnection(it,node.id,port.key) }
        } }
        assertEquals(initial,DiagramEdits.building(initial,positions,connections))
        val disconnected=DiagramEdits.building(initial,positions,connections.filter { it.port!="edge:0:0" })
        assertTrue(disconnected.resolvedNode("room").verticalEdges.isNullOrEmpty())
        val grouped=GraphGroups.create(initial,setOf("room","edge"),"Edged room")
        val instanced=GraphGroups.instantiate(grouped,grouped.nodeGroups.single().id)
        val members=instanced.nodeGroups.last().nodes.map { id -> instanced.nodes.single { it.id==id } }
        val copiedRoom=members.single { it.kind==BuildingKind.ROOM };val copiedSource=members.single { it.kind==BuildingKind.SEGMENT }
        assertEquals(copiedSource.id,copiedRoom.verticalEdges?.get("0:0"))
        val changed=instanced.replace(instanced.nodes.single { it.id=="edge" }.copy(form="full@1"))
        assertEquals("full@1",changed.nodes.single { it.id==copiedSource.id }.form)
        assertEquals(evaluation(changed).room("room").plan,evaluation(changed).room(copiedRoom.id).plan)
    }

    @Test fun `linked room instances keep different external edge sockets when shared properties change`() {
        val initial=BuildingGraph.explicitStarter(joinWallEnds=false).let { it.copy(nodes=it.nodes+source("edge_a")+source("edge_b")) }
            .connect("room","edge:0:0","edge_a")
        val grouped=GraphGroups.create(initial,setOf("room","room_geometry"),"Room definition")
        val instances=GraphGroups.instantiate(grouped,grouped.nodeGroups.single().id)
        val copied=instances.nodeGroups.last().nodes.map { id -> instances.nodes.single { it.id==id } }.single { it.kind==BuildingKind.ROOM }
        val connected=instances.connect(copied.id,"edge:0:0","").connect(copied.id,"edge:3:3","edge_b")
        assertEquals(mapOf("0:0" to "edge_a"),connected.nodes.single { it.id=="room" }.verticalEdges)
        val changed=connected.replace(connected.nodes.single { it.id=="room" }.copy(role="frame"))
        assertEquals("frame",changed.nodes.single { it.id==copied.id }.role)
        assertEquals(mapOf("3:3" to "edge_b"),changed.nodes.single { it.id==copied.id }.verticalEdges)
        assertEquals(mapOf("0:0" to "edge_a"),changed.nodes.single { it.id=="room" }.verticalEdges)
        BuildingGraphCompiler.structure(changed)
    }
}
