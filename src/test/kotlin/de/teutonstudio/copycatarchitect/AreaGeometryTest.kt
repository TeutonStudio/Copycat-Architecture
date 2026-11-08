package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.client.BuildingSourceEditors
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class AreaGeometryTest {
    private val forms=mapOf("full@1" to Form(id="full",parts=listOf(Part("copycats:copycat_block"))))
    private fun node(graph:BuildingGraph,id:String)=graph.nodes.single { it.id==id }
    private fun eval(graph:BuildingGraph)=BuildingGraphCompiler.Evaluation(graph,Building().palette,forms,emptyMap())
    private fun rectangle(width:Int,length:Int)=buildList { for(z in 0 until length) for(x in 0 until width) add(P(x,0,z)) }
    private fun rewireArea(graph:BuildingGraph,geometry:String,source:String,diagram:Boolean):BuildingGraph {
        if(!diagram) return graph.connect(geometry,"geometry",source)
        val positions=graph.nodes.map { DiagramPosition(it.id,it.x,it.y) }
        val connections=graph.nodes.flatMap { target -> BuildingPorts.of(target,graph).filter { it.source.isNotEmpty() }.map { port ->
            DiagramConnection(if(target.id==geometry && port.key=="geometry") source else port.source,target.id,port.key)
        } }
        return DiagramEdits.building(graph,positions,connections)
    }

    @Test fun `generic areas accept sparse disconnected and holed two dimensional raster lists`() {
        val scattered=listOf(P(127,0,127),P(-128,0,-128),P())
        val area=Area(scattered)
        assertEquals(scattered,area.points)
        assertEquals(-128,area.minX);assertEquals(-128,area.minZ)
        assertEquals(256,area.width);assertEquals(256,area.length)
        val ring=rectangle(3,3)-P(1,0,1)
        assertEquals(8,Area(ring).points.size)
        assertFails { RoomVolume(area,4) }
        assertFails { RoomVolume(Area(ring),4) }
        assertFails { RoomVolume(Area(listOf(P(),P(2,0,0))),4) }
        assertEquals(scattered,BuildingSourceEditors.parseAreaCoordinates("127,127; -128,-128; 0,0"))
        assertFails { BuildingSourceEditors.parseCoordinates("0,0;2,0") }
    }

    @Test fun `area validation rejects empty duplicate nonplanar and out of range points`() {
        for(points in listOf(emptyList(),listOf(P(),P()),listOf(P(0,1,0)),listOf(P(-129,0,0)),listOf(P(0,0,128)))) {
            assertFails { Area(points) }
        }
        val tooMany=buildList { for(z in -64..64) for(x in -64..63) add(P(x,0,z)) }.take(16385)
        assertFails { Area(tooMany) }
        for(text in listOf("","0,0;0,0","0,0,0","128,0","0,-129","0.5,0","0,0;")) {
            assertFails { BuildingSourceEditors.parseAreaCoordinates(text) }
        }
    }

    @Test fun `room volumes retain room topology grid and height limits`() {
        assertEquals(16,RoomVolume(Area(rectangle(64,64)),16).height)
        assertFails { RoomVolume(Area(listOf(P(-33,0,0))),1) }
        assertFails { RoomVolume(Area(listOf(P(64,0,0))),1) }
        assertFails { RoomVolume(Area(rectangle(65,64)),1) }
        for(height in listOf(0,17)) assertFails { RoomVolume(Area(listOf(P())),height) }
    }

    @Test fun `segment volumes constrain the bounding box and depth without requiring room topology`() {
        val full=Area(rectangle(128,128))
        assertEquals(16,SegmentVolume(full,16).depth)
        assertFails { SegmentVolume(full,17) }
        assertFails { SegmentVolume(Area(listOf(P(),P(128-1,0,0),P(-1,0,0))),1) }
        for(depth in listOf(0,129)) assertFails { SegmentVolume(Area(listOf(P())),depth) }
        val ring=Area(rectangle(3,3)-P(1,0,1))
        assertEquals(128,SegmentVolume(ring,128).depth)
    }

    @Test fun `area to room geometry carries its footprint and height through the room and preview`() {
        val original=BuildingGraph.explicitStarter(joinWallEnds=false)
        val points=listOf(P(-2,0,5),P(-1,0,5),P(-2,0,6),P(-1,0,6),P(-2,0,7),P(-1,0,7))
        val area=BuildingNode("room_area",BuildingKind.AREA,coordinates=points)
        val geometry=node(original,"room_geometry").copy(geometry=area.id,height=6)
        val room=node(original,"room").copy(height=2,walls=emptyMap())
        val graph=original.copy(nodes=original.nodes+area).replace(geometry).replace(room)
            .replace(node(original,"plate_layout").copy(length=4,height=5))
        BuildingGraphCompiler.structure(graph)
        assertEquals(setOf(BuildingKind.AREA),BuildingPorts.of(geometry,graph).single().accepts)
        assertEquals(points,graph.resolvedNode(geometry).coordinates)
        assertEquals(points.toSet(),RoomGeometry.cells(graph.resolvedNode(room).rectangles))
        assertEquals(6,graph.resolvedNode(room).height)
        assertTrue(BuildingPorts.of(room,graph).filter { it.key.startsWith("wall:") }.all { it.height==6 })
        val compiled=eval(graph).room(room.id)
        assertEquals(6,compiled.usableHeight)
        assertEquals(8,compiled.totalHeight)
        assertEquals(40,compiled.plan.placements.count { it.component in setOf("floor","ceiling") })
        assertEquals(compiled.plan,eval(graph).preview(room.id))
    }

    @Test fun `area to segment geometry extrudes only occupied points and clears mask holes`() {
        val original=BuildingGraph.explicitStarter(joinWallEnds=false)
        val points=listOf(P(-2,0,-1),P(-1,0,-1),P(-2,0,0))
        val area=BuildingNode("segment_area",BuildingKind.AREA,coordinates=points)
        val geometry=node(original,"wall_layout").copy(geometry=area.id,depth=3)
        val graph=original.copy(nodes=original.nodes+area).replace(geometry)
        val resolved=graph.resolvedNode("wall")
        assertEquals(2,resolved.length);assertEquals(2,resolved.height);assertEquals(3,resolved.depth)
        val segment=eval(graph).segment("wall")
        assertEquals(12,segment.cells.size)
        assertEquals(9,segment.cells.count { !it.cell.empty })
        val preview=eval(graph).preview("wall")
        assertEquals(9,preview.placements.size)
        assertEquals((0..2).map { P(1,0,it) }.toSet(),preview.clear.toSet())
        // The first raster row is the top row of the vertical segment, including shifted areas.
        assertEquals(setOf(P(0,1,0),P(1,1,0),P()),preview.placements.filter { it.pos.z==0 }.map { it.pos }.toSet())
        val layoutBoxes=BuildingNodePreviewGeometry.boxes(graph,geometry)
        val occupied=buildSet { for(box in layoutBoxes) for(x in box.x until box.maxX) for(y in box.y until box.maxY) for(z in box.z until box.maxZ) add(P(x,y,z)) }
        assertEquals(preview.placements.map { it.pos }.toSet(),occupied)
        assertTrue(preview.placements.all { it.pos.x in 0..1 && it.pos.y in 0..1 && it.pos.z in 0..2 })
        assertEquals(graph,Gson().fromJson(Gson().toJson(graph),BuildingGraph::class.java))
    }

    @Test fun `generic area editing preserves sparse lists but validates every room consumer atomically`() {
        val original=BuildingGraph.explicitStarter(joinWallEnds=false)
        val area=BuildingNode("shared_area",BuildingKind.AREA,coordinates=rectangle(2,2))
        val standalone=original.copy(nodes=original.nodes+area)
        val points=listOf(P(-100,0,-100),P(-98,0,-100))
        val changed=standalone.editRoomGeometry(area.id,points)
        assertEquals(points,node(changed,area.id).coordinates)
        val linked=standalone.replace(node(standalone,"room_geometry").copy(geometry=area.id))
        val before=Gson().toJson(linked)
        assertFails { linked.editRoomGeometry(area.id,listOf(P(),P(2,0,0))) }
        assertEquals(before,Gson().toJson(linked))
        val resized=linked.editRoomGeometry(area.id,rectangle(3,2))
        assertEquals(rectangle(3,2).toSet(),RoomGeometry.cells(resized.resolvedNode("room").rectangles))
        assertEquals(node(linked,"room").geometry,node(resized,"room").geometry)
    }

    @Test fun `legacy inline room and segment geometry keep their dimensions without an area link`() {
        val graph=BuildingGraph.explicitStarter(joinWallEnds=false)
        assertEquals(node(graph,"room_geometry").coordinates,graph.resolvedNode("room_geometry").coordinates)
        assertEquals(node(graph,"wall_layout").length,eval(graph).segment("wall").length)
        assertEquals(node(graph,"wall_layout").height,eval(graph).segment("wall").height)
        assertEquals(114,eval(graph).room("room").plan.placements.size)
    }

    @Test fun `empty connected areas remain incomplete instead of compiling cached inline geometry`() {
        val original=BuildingGraph.explicitStarter(joinWallEnds=false)
        val area=BuildingNode("empty_area",BuildingKind.AREA)
        val graph=original.copy(nodes=original.nodes+area)
            .replace(node(original,"room_geometry").copy(geometry=area.id))
            .replace(node(original,"wall_layout").copy(geometry=area.id))
        BuildingGraphCompiler.structure(graph)
        assertTrue(graph.resolvedNode("room_geometry").coordinates.isEmpty())
        assertFails { eval(graph).room("room") }
        assertFails { eval(graph).segment("wall") }
    }

    @Test fun `rewiring a shared area remaps every dependent room and rejects overlapping room interiors`() {
        val original=BuildingGraph.explicitStarter(joinWallEnds=false)
        val firstArea=BuildingNode("first_area",BuildingKind.AREA,coordinates=rectangle(3,3))
        val safeArea=firstArea.copy(id="safe_area",coordinates=rectangle(4,3))
        val overlapArea=firstArea.copy(id="overlap_area",coordinates=rectangle(5,3))
        val secondRoom=node(original,"room").copy(id="second_room")
        val geometry=node(original,"room_geometry").copy(geometry=firstArea.id)
        val storey=node(original,"storey").copy(inputs=listOf("room",secondRoom.id),offsets=listOf(P(),P(4,0,0)))
        val graph=original.copy(nodes=original.nodes+listOf(firstArea,safeArea,overlapArea,secondRoom)).replace(geometry).replace(storey)
        BuildingGraphCompiler.structure(graph)
        val changed=graph.replace(geometry.copy(geometry=safeArea.id)).remapGeometryDependents(graph)
        for(id in listOf("room",secondRoom.id)) {
            val room=changed.resolvedNode(id)
            assertEquals(rectangle(4,3).toSet(),RoomGeometry.cells(room.rectangles))
            assertEquals(RoomGeometry.edges(room.rectangles).map { it.id }.toSet(),room.walls.keys)
            assertTrue(room.walls.values.all { it=="wall" })
        }
        val before=Gson().toJson(graph)
        assertFails { graph.replace(geometry.copy(geometry=overlapArea.id)).remapGeometryDependents(graph) }
        assertEquals(before,Gson().toJson(graph))
        // The graph's public rewire must use the same dependent-room remapping path.
        assertEquals(changed,graph.connect(geometry.id,"geometry",safeArea.id))
        assertFails { graph.connect(geometry.id,"geometry",overlapArea.id) }
    }

    @Test fun `rewiring an empty area draft to a populated area retains its cached directional wall sources`() {
        val original=BuildingGraph.explicitStarter(joinWallEnds=false)
        val empty=BuildingNode("empty_area",BuildingKind.AREA)
        val populated=empty.copy(id="populated_area",coordinates=rectangle(4,3))
        val geometry=node(original,"room_geometry").copy(geometry=empty.id)
        val graph=original.copy(nodes=original.nodes+listOf(empty,populated)).replace(geometry)
        BuildingGraphCompiler.structure(graph)
        assertTrue(graph.resolvedNode(geometry).coordinates.isEmpty())
        val before=Gson().toJson(graph)
        for(diagram in listOf(false,true)) {
            val changed=rewireArea(graph,geometry.id,populated.id,diagram)
            val room=changed.resolvedNode("room")
            assertEquals(populated.id,node(changed,geometry.id).geometry)
            assertEquals(rectangle(4,3).toSet(),RoomGeometry.cells(room.rectangles))
            assertEquals(RoomGeometry.edges(room.rectangles).map { it.id }.toSet(),room.walls.keys)
            assertEquals(setOf("wall"),room.walls.values.toSet())
            assertTrue(BuildingPorts.of(node(changed,"room"),changed).filter { it.key.startsWith("wall:") }.all { it.source=="wall" })
            assertEquals(before,Gson().toJson(graph))
        }
    }

    @Test fun `shared area rewires validate affected room instances without blocking on unrelated overlapping drafts`() {
        val original=BuildingGraph.explicitStarter(joinWallEnds=false)
        val first=BuildingNode("first_area",BuildingKind.AREA,coordinates=rectangle(3,3))
        val safe=first.copy(id="safe_area",coordinates=rectangle(4,3))
        val overlap=first.copy(id="overlap_area",coordinates=rectangle(5,3))
        val geometry=node(original,"room_geometry").copy(geometry=first.id)
        val second=node(original,"room").copy(id="second_room")
        val storey=node(original,"storey").copy(inputs=listOf("room",second.id),offsets=listOf(P(),P(4,0,0)))
        val draftGeometry=BuildingAssembly.newNode(BuildingKind.ROOM_GEOMETRY,"draft_geometry").copy(coordinates=listOf(P()))
        val draftRoom=BuildingAssembly.newNode(BuildingKind.ROOM,"draft_room").copy(geometry=draftGeometry.id)
        val draftStorey=BuildingAssembly.newNode(BuildingKind.STOREY,"draft_storey").copy(inputs=listOf(draftRoom.id,draftRoom.id),offsets=listOf(P(),P()))
        val graph=original.copy(nodes=original.nodes+listOf(first,safe,overlap,second,draftGeometry,draftRoom,draftStorey)).replace(geometry).replace(storey)
        BuildingGraphCompiler.structure(graph)
        val before=Gson().toJson(graph)
        for(diagram in listOf(false,true)) {
            assertContains(assertFails { rewireArea(graph,geometry.id,overlap.id,diagram) }.message.orEmpty(),"overlap")
            assertEquals(before,Gson().toJson(graph))
            val changed=rewireArea(graph,geometry.id,safe.id,diagram)
            assertEquals(safe.id,node(changed,geometry.id).geometry)
            for(id in listOf("room",second.id)) {
                val room=changed.resolvedNode(id)
                assertEquals(rectangle(4,3).toSet(),RoomGeometry.cells(room.rectangles))
                assertEquals(RoomGeometry.edges(room.rectangles).map { it.id }.toSet(),room.walls.keys)
                assertTrue(room.walls.values.all { it=="wall" })
            }
            for(draft in listOf(draftGeometry,draftRoom,draftStorey)) assertEquals(draft,node(changed,draft.id))
            assertFails { eval(changed).storey(draftStorey.id) }
            assertEquals(before,Gson().toJson(graph))
        }
    }
}
