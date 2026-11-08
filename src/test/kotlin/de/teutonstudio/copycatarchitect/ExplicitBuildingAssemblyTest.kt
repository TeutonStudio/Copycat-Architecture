package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class ExplicitBuildingAssemblyTest {
    private val forms=mapOf("full@1" to Form(id="full",parts=listOf(Part("copycats:copycat_block"))))
    private fun eval(graph:BuildingGraph,definitions:Map<String,Form> = forms)=BuildingGraphCompiler.Evaluation(graph,Building().palette,definitions,emptyMap())
    private fun node(graph:BuildingGraph,id:String)=graph.nodes.single { it.id==id }
    private fun coordinates(width:Int,depth:Int)=buildList { for(z in 0 until depth) for(x in 0 until width) add(P(x,0,z)) }

    @Test fun `explicit starter contains the complete typed chain and compiles through its top ceiling`() {
        val graph=BuildingGraph.explicitStarter(joinWallEnds=false)
        BuildingGraphCompiler.structure(graph)
        assertEquals(BuildingKind.SEGMENT_LAYOUT,node(graph,"wall_layout").kind)
        assertEquals(BuildingKind.ROOM_GEOMETRY,node(graph,"room_geometry").kind)
        assertEquals(BuildingKind.ROOF_LAYOUT,node(graph,"roof_layout").kind)
        assertEquals("wall_layout",node(graph,"wall").layout)
        assertEquals("room_geometry",node(graph,"room").geometry)
        assertEquals("roof_layout",node(graph,"roof").layout)
        assertEquals(emptyList(),node(graph,"roof").inputs)
        assertEquals("",node(graph,"building").roof)
        assertEquals(setOf(BuildingKind.SEGMENT_LAYOUT),BuildingPorts.of(node(graph,"wall"),graph).first { it.key=="layout" }.accepts)
        assertEquals(setOf(BuildingKind.ROOM_GEOMETRY),BuildingPorts.of(node(graph,"room"),graph).first { it.key=="geometry" }.accepts)
        assertEquals(setOf(BuildingKind.SEGMENT,BuildingKind.PATTERN,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_ROTATE),BuildingPorts.of(node(graph,"roof"),graph,true).first { it.key=="in:0" }.accepts)
        val room=eval(graph).room("room")
        assertEquals(114,room.plan.placements.size)
        assertEquals(36,room.plan.clear.size)
        assertEquals(-1,room.minimum);assertEquals(5,room.maximum)
        assertEquals(4,room.usableHeight)
        val building=eval(graph).building()
        assertEquals(114,building.plan.placements.size)
        assertTrue(building.plan.placements.none { it.component=="roof" })
        assertEquals(0,building.plan.placements.minOf { it.pos.y })
        assertEquals(graph,Gson().fromJson(Gson().toJson(graph),BuildingGraph::class.java))
    }

    @Test fun `typed sources cannot be exchanged and explicit drafts never fall back to inline recipes`() {
        val graph=BuildingGraph.explicitStarter(joinWallEnds=false)
        assertFails { graph.connect("wall","layout","roof_layout") }
        assertFails { graph.connect("wall","layout","wall_copycat") }
        assertFails { graph.connect("room","geometry","wall_layout") }
        assertFails { graph.connect("roof","in:0","wall_copycat") }
        assertFails { graph.connect("roof","layout","wall_layout") }
        assertFails { graph.connect("room","floor","wall_copycat") }
        val withoutLayout=graph.remove("wall_layout")
        BuildingGraphCompiler.structure(withoutLayout)
        assertTrue(node(withoutLayout,"wall").assembly)
        assertContains(assertFails { eval(withoutLayout).segment("wall") }.message.orEmpty(),"arrangement source")
        val withoutGeometry=graph.remove("room_geometry")
        BuildingGraphCompiler.structure(withoutGeometry)
        assertTrue(node(withoutGeometry,"room").assembly)
        assertContains(assertFails { eval(withoutGeometry).room("room") }.message.orEmpty(),"geometry source")
        assertEquals(node(withoutGeometry,"room").rectangles,withoutGeometry.resolvedNode("room").rectangles)
        val emptyGeometry=graph.replace(node(graph,"room_geometry").copy(coordinates=emptyList()))
        BuildingGraphCompiler.structure(emptyGeometry)
        assertContains(assertFails { eval(emptyGeometry).room("room") }.message.orEmpty(),"occupied coordinates")
        val missingInput=graph.connect("wall","in:0","")
        BuildingGraphCompiler.structure(missingInput)
        assertFails { eval(missingInput).segment("wall") }
    }

    @Test fun `segment arrangements own dimensions and choose explicit Copycat cells and turns`() {
        val original=BuildingGraph.explicitStarter(joinWallEnds=false)
        val second=BuildingAssembly.newNode(BuildingKind.COPYCAT,"second").copy(role="frame")
        val graph=original.copy(nodes=original.nodes+second)
            .replace(node(original,"wall").copy(length=1,height=1,inputs=listOf("wall_copycat","second")))
        val repeated=eval(graph).segment("wall")
        assertEquals(3,repeated.length);assertEquals(4,repeated.height)
        assertEquals("wall",repeated.cells.single { it.cell.pos==P() }.cell.role)
        assertEquals("frame",repeated.cells.single { it.cell.pos==P(1,0,0) }.cell.role)
        assertEquals("frame",repeated.cells.single { it.cell.pos==P(0,1,0) }.cell.role)
        val layout=node(graph,"wall_layout").copy(layoutCells=listOf(SegmentLayoutCell(0,0,0,0),SegmentLayoutCell(1,2,1,3)))
        val sparse=eval(graph.replace(layout)).segment("wall")
        assertEquals(12,sparse.cells.size)
        assertEquals(10,sparse.cells.count { it.cell.empty })
        assertEquals(3,sparse.cells.single { it.cell.pos==P(1,2,0) }.cell.turn)
        assertEquals("frame",sparse.cells.single { it.cell.pos==P(1,2,0) }.cell.role)
        val invalid=graph.replace(layout.copy(layoutCells=listOf(SegmentLayoutCell(0,0,8))))
        BuildingGraphCompiler.structure(invalid)
        assertContains(assertFails { eval(invalid).segment("wall") }.message.orEmpty(),"missing Copycat")
        assertFails { BuildingGraphCompiler.structure(graph.replace(layout.copy(layoutCells=listOf(SegmentLayoutCell(),SegmentLayoutCell())))) }
        assertFails { BuildingGraphCompiler.structure(graph.replace(layout.copy(layoutCells=listOf(SegmentLayoutCell(3,0,0))))) }
        assertFails { BuildingGraphCompiler.structure(graph.replace(layout.copy(layoutCells=listOf(SegmentLayoutCell(turn=4))))) }
    }

    @Test fun `horizontal segments mount asymmetric clipped wall panels while preserving receipts and finishes`() {
        val graph=BuildingGraph.explicitStarter(joinWallEnds=false)
        val panel=Form(id="panel",groups=listOf(MaterialGroup(),MaterialGroup("accent","Accent","surface",1)),graph=NodeGraph(nodes=listOf(
            GraphNode("panel",NodeKind.INPUT,part=Part("create:copycat_panel",tilt=3)),
            GraphNode("cut",NodeKind.INPUT,part=Part("copycats:copycat_byte",turn=2)),
            GraphNode("difference",NodeKind.DIFFERENCE,listOf("panel","cut")),
            GraphNode("width_cut",NodeKind.INPUT,part=Part("copycats:copycat_slab",turn=1,tilt=1)),
            GraphNode("body",NodeKind.DIFFERENCE,listOf("difference","width_cut")),
            GraphNode("accent",NodeKind.INTERSECTION,listOf("difference","width_cut")),
            GraphNode("union",NodeKind.UNION,listOf("body","accent"),inputGroups=listOf("body","accent")),
            GraphNode("result",NodeKind.OUTPUT,listOf("union"))
        )))
        val definitions=forms+("panel@1" to panel)
        val changed=graph.replace(node(graph,"plate_copycat").copy(form="panel@1",role="frame"))
        val source=Geometry.validate(panel).parts
        assertEquals(2,source.size)
        assertTrue(source.all { it.cutMask.isNotEmpty() && it.recipeSource=="panel" })
        assertEquals(576,Geometry.solid(source).count())
        val room=eval(changed,definitions).room("room")
        for(component in listOf("floor","ceiling")) {
            val expected=Geometry.solid(source).rotate(RotationAxis.X,if(component=="floor") 3 else 1)
            assertNotEquals(Geometry.solid(source).key(),expected.key())
            val placements=room.plan.placements.filter { it.component==component }
            assertEquals(25,placements.size)
            assertEquals(if(component=="floor") setOf(-1) else setOf(4),placements.map { it.pos.y }.toSet())
            assertTrue(placements.all { Geometry.solid(it.parts).key()==expected.key() })
            assertTrue(placements.all { it.material==Building().palette.getValue("frame") && it.surfaces.values.all { value -> value==Building().palette.getValue("frame") } })
            assertTrue(placements.all { it.parts.map(Part::recipeSource)==source.map(Part::recipeSource) })
            val ys=buildList { expected.voxels { _,y,_ -> add(y) } }
            assertTrue(ys.all { it in if(component=="floor") 13..15 else 0..2 })
        }
    }

    @Test fun `horizontal fields match the effective shell bounds and mask concave room corners`() {
        val graph=BuildingGraph.explicitStarter(joinWallEnds=false)
        val cells=listOf(P(),P(1,0,0),P(2,0,0),P(0,0,1),P(0,0,2))
        val resized=graph.editRoomGeometry("room_geometry",cells)
        val changed=resized.replace(node(resized,"room").copy(walls=emptyMap()))
        val room=eval(changed).room("room")
        for(component in listOf("floor","ceiling")) assertEquals(room.shell,room.plan.placements.filter { it.component==component }.map { it.pos.copy(y=0) }.toSet())
        val wrong=changed.replace(node(changed,"plate_layout").copy(height=4))
        assertContains(assertFails { eval(wrong).room("room") }.message.orEmpty(),"5 × 5")
        val longGeometry=coordinates(3,20)
        val longRoom=graph.editRoomGeometry("room_geometry",longGeometry)
        val longGraph=longRoom.replace(node(longRoom,"room").copy(walls=emptyMap()))
            .replace(node(longRoom,"plate_layout").copy(length=5,height=22))
        assertEquals(110,eval(longGraph).room("room").plan.placements.count { it.component=="floor" })
    }

    @Test fun `shared geometry remaps every room and rejects overlapping storey instances atomically`() {
        val original=BuildingGraph.explicitStarter(joinWallEnds=false)
        val second=node(original,"room").copy(id="second_room",name="Second room")
        val storey=node(original,"storey").copy(inputs=listOf("room","second_room"),offsets=listOf(P(),P(4,0,0)))
        val graph=original.copy(nodes=original.nodes+second).replace(storey)
        val before=Gson().toJson(graph)
        val changed=graph.reshapeRoom("room",listOf(Rect(width=4,length=3)))
        assertEquals(coordinates(4,3),node(changed,"room_geometry").coordinates)
        for(id in listOf("room","second_room")) {
            val room=changed.resolvedNode(id)
            assertEquals(coordinates(4,3).toSet(),RoomGeometry.cells(room.rectangles))
            assertEquals(RoomGeometry.edges(room.rectangles).map { it.id }.toSet(),room.walls.keys)
            assertTrue(room.walls.values.all { it=="wall" })
            assertEquals(4,BuildingPorts.of(node(changed,id),changed).first { it.key.startsWith("wall:0:") }.length)
        }
        // Wall segment sizes deliberately remain unfinished; shared-source editing must not compile them.
        assertFails { eval(changed).room("room") }
        assertContains(assertFails { graph.editRoomGeometry("room_geometry",coordinates(5,3)) }.message.orEmpty(),"overlap")
        assertEquals(before,Gson().toJson(graph))
        assertEquals(graph,Gson().fromJson(before,BuildingGraph::class.java))
        assertEquals(changed,Gson().fromJson(Gson().toJson(changed),BuildingGraph::class.java))
        val repeated=graph.replace(storey.copy(inputs=listOf("room","room")))
        assertFails { repeated.editRoomGeometry("room",coordinates(5,3)) }
    }

    @Test fun `explicit segment patterns compose large horizontal floor fields while legacy patterns retain their wall limit`() {
        val initial=BuildingGraph.explicitStarter(joinWallEnds=false)
        val resized=initial.editRoomGeometry("room_geometry",coordinates(3,20))
        val direct=resized.replace(node(resized,"room").copy(walls=emptyMap()))
            .replace(node(resized,"plate_layout").copy(length=5,height=22))
        val leftLayout=BuildingAssembly.newNode(BuildingKind.SEGMENT_LAYOUT,"left_layout").copy(length=2,height=22)
        val rightLayout=leftLayout.copy(id="right_layout",length=3)
        val left=BuildingAssembly.newNode(BuildingKind.SEGMENT,"left_segment").copy(layout=leftLayout.id,inputs=listOf("plate_copycat"))
        val right=left.copy(id="right_segment",layout=rightLayout.id)
        val pattern=BuildingAssembly.newNode(BuildingKind.PATTERN,"plate_pattern").copy(inputs=listOf(left.id,right.id))
        val patterned=direct.copy(nodes=direct.nodes+listOf(leftLayout,rightLayout,left,right,pattern))
            .replace(node(direct,"room").copy(floor=pattern.id))
        assertEquals(5,eval(patterned).segment(pattern.id).length)
        assertEquals(22,eval(patterned).segment(pattern.id).height)
        assertEquals(eval(direct).room("room").plan,eval(patterned).room("room").plan)
        assertFails { eval(patterned.replace(pattern.copy(assembly=false))).segment(pattern.id) }
    }

    @Test fun `new coordinate sources require unique bounded connected cells without enclosed holes`() {
        val graph=BuildingGraph.explicitStarter(joinWallEnds=false)
        for(cells in listOf(listOf(P(),P()),listOf(P(0,1,0)),listOf(P(64,0,0)),listOf(P(),P(2,0,0)),coordinates(3,3)-P(1,0,1))) {
            assertFails { BuildingGraphCompiler.structure(graph.replace(node(graph,"room_geometry").copy(coordinates=cells))) }
            assertFails { graph.editRoomGeometry("room_geometry",cells) }
        }
        assertFails { graph.editRoomGeometry("room_geometry",emptyList()) }
        // Existing inline footprints intentionally retain their previous, more general geometry schema.
        val legacy=BuildingGraph.starter()
        BuildingGraphCompiler.structure(legacy.replace(RoomGeometry.toggleCell(node(legacy,"room"),P(1,0,1))))
    }

    @Test fun `an unrelated overlapping draft does not block another shared geometry edit`() {
        val graph=BuildingGraph.explicitStarter(joinWallEnds=false)
        val geometry=BuildingAssembly.newNode(BuildingKind.ROOM_GEOMETRY,"draft_geometry").copy(coordinates=listOf(P()))
        val room=BuildingAssembly.newNode(BuildingKind.ROOM,"draft_room").copy(geometry=geometry.id)
        val draft=BuildingAssembly.newNode(BuildingKind.STOREY,"draft_storey").copy(inputs=listOf(room.id,room.id),offsets=listOf(P(),P()))
        val withDraft=graph.copy(nodes=graph.nodes+listOf(geometry,room,draft))
        BuildingGraphCompiler.structure(withDraft)
        val changed=withDraft.editRoomGeometry("room_geometry",coordinates(2,3))
        assertEquals(coordinates(2,3),node(changed,"room_geometry").coordinates)
        assertEquals(draft,node(changed,draft.id))
        assertFails { eval(changed).storey(draft.id) }
    }

    @Test fun `geometry rewiring through sockets and native diagrams rejects overlap without touching unrelated drafts`() {
        val initial=BuildingGraph.explicitStarter(joinWallEnds=false)
        val second=node(initial,"room").copy(id="second_room")
        val storey=node(initial,"storey").copy(inputs=listOf("room",second.id),offsets=listOf(P(),P(4,0,0)))
        val safe=BuildingAssembly.newNode(BuildingKind.ROOM_GEOMETRY,"safe_geometry").copy(coordinates=coordinates(4,3))
        val overlapping=safe.copy(id="overlapping_geometry",coordinates=coordinates(5,3))
        val draftGeometry=BuildingAssembly.newNode(BuildingKind.ROOM_GEOMETRY,"draft_geometry").copy(coordinates=listOf(P()))
        val draftRoom=BuildingAssembly.newNode(BuildingKind.ROOM,"draft_room").copy(geometry=draftGeometry.id)
        val draftStorey=BuildingAssembly.newNode(BuildingKind.STOREY,"draft_storey").copy(inputs=listOf(draftRoom.id,draftRoom.id),offsets=listOf(P(),P()))
        val graph=initial.copy(nodes=initial.nodes+listOf(second,safe,overlapping,draftGeometry,draftRoom,draftStorey)).replace(storey)
        BuildingGraphCompiler.structure(graph)
        val before=Gson().toJson(graph)
        val rewires:List<(BuildingGraph,String)->BuildingGraph> = listOf(
            { value,source -> value.connect("room","geometry",source) },
            { value,source ->
                val positions=value.nodes.map { DiagramPosition(it.id,it.x,it.y) }
                val connections=value.nodes.flatMap { target -> BuildingPorts.of(target,value).filter { it.source.isNotEmpty() }.map { port ->
                    DiagramConnection(if(target.id=="room" && port.key=="geometry") source else port.source,target.id,port.key)
                } }
                DiagramEdits.building(value,positions,connections)
            }
        )
        for(rewire in rewires) {
            assertContains(assertFails { rewire(graph,overlapping.id) }.message.orEmpty(),"overlap")
            assertEquals(before,Gson().toJson(graph))
            val changed=rewire(graph,safe.id)
            assertEquals(safe.id,node(changed,"room").geometry)
            assertEquals(coordinates(4,3).toSet(),RoomGeometry.cells(changed.resolvedNode("room").rectangles))
            assertEquals("room_geometry",node(changed,second.id).geometry)
            assertEquals(draftStorey,node(changed,draftStorey.id))
            assertTrue(changed.resolvedNode("room").walls.values.all { it=="wall" })
            assertFails { eval(changed).room("room") }
        }
    }

    @Test fun `roof layout is a typed placeholder and cannot invent a roof geometry`() {
        val graph=BuildingGraph.explicitStarter(joinWallEnds=false)
        val connected=graph.connect("building","roof","roof")
        BuildingGraphCompiler.structure(connected)
        val error=assertFails { eval(connected).building() }
        assertContains(error.message.orEmpty(),"pending definition")
        assertTrue(eval(graph).building().plan.placements.none { it.component=="roof" })
        assertContains(assertFails { eval(connected.remove("roof_layout")).building() }.message.orEmpty(),"arrangement source")
        assertEquals(connected,graph.connect("building","roof","roof"))
    }

    @Test fun `native diagram edits preserve source links and source deletion remains an incomplete explicit draft`() {
        val graph=BuildingGraph.explicitStarter(joinWallEnds=false)
        fun positions(value:BuildingGraph)=value.nodes.map { DiagramPosition(it.id,it.x,it.y) }
        fun connections(value:BuildingGraph)=value.nodes.flatMap { target -> BuildingPorts.of(target,value).filter { it.source.isNotEmpty() }.map { DiagramConnection(it.source,target.id,it.key) } }
        assertEquals(graph,DiagramEdits.building(graph,positions(graph),connections(graph)))
        val removed=DiagramEdits.building(graph,positions(graph).filter { it.id!="room_geometry" },connections(graph).filter { it.source!="room_geometry" })
        assertTrue(node(removed,"room").assembly)
        assertEquals("",node(removed,"room").geometry)
        assertFails { eval(removed).room("room") }
        val edited=DiagramEdits.toggleRoomCell(graph,"room",P(2,0,2))
        assertEquals(coordinates(3,3).toSet()-P(2,0,2),node(edited,"room_geometry").coordinates.toSet())
        assertEquals(graph,DiagramEdits.building(graph,positions(graph),connections(graph)))
    }

    @Test fun `legacy documents default to inline semantics and keep their exact compiled plans`() {
        val gson=Gson()
        for(graph in listOf(BuildingGraph.starter(),BuildingGraph.assembledStarter())) {
            val json=gson.toJsonTree(graph).asJsonObject
            json.getAsJsonArray("nodes").forEach { item -> for(field in listOf("assembly","layout","geometry","coordinates","layoutCells")) item.asJsonObject.remove(field) }
            val restored=gson.fromJson(json,BuildingGraph::class.java)
            assertTrue(restored.nodes.none { it.assembly })
            assertEquals(graph,restored)
            assertEquals(eval(graph).building(),eval(restored).building())
            assertFails { graph.connect("room","floor","wall") }
        }
    }
}
