package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.Json
import de.teutonstudio.copycatarchitect.server.Library
import de.teutonstudio.copycatarchitect.server.Snapshot
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen

internal object FloorPlanClientSmoke {
    private var ticks=0
    private var failed=false
    private var previous:Screen?=null
    private var previousScale=1
    private lateinit var previousSnapshot:Snapshot
    private lateinit var building:BuildingScreen
    private var fixtureJson=""
    private var clickedJson=""
    private var rectangleJson=""
    private var annexJson=""
    private var assignedJson=""
    private var compactJson=""
    private var erasedJson=""
    private var addedJson=""
    private var addedRoom=""
    private lateinit var dragEnd:Pair<Double,Double>

    fun tick():Boolean {
        if(failed) return false
        val mc=Minecraft.getInstance()
        if(ticks==0 && (mc.screen==null || mc.overlay!=null)) return false
        ticks++
        try {
            if(ticks==1) begin(mc)
            else if(advance(mc,ticks-1)) {
                println("ARCHITECT FLOOR PLAN UI SMOKE PASSED: normal/compact room colors, scrolled selection, offset-aware cell/rectangle painting and erasing, atomic undo/redo, drag cancellation, pan/zoom, rejected holes/disconnections/overlaps, add/delete rooms with stable colors, dynamic wall widths, and typed wall sources verified")
                mc.stop()
            }
        } catch(error:Throwable) {
            failed=true
            System.err.println("ARCHITECT FLOOR PLAN UI SMOKE FAILED at tick $ticks: ${error.message}")
            screenshot(mc,"failure-$ticks")
            throw IllegalStateException("Floor plan UI smoke failed at tick $ticks",error)
        }
        return true
    }

    private fun begin(mc:Minecraft) {
        previous=mc.screen;previousScale=mc.options.guiScale().get();previousSnapshot=ClientState.snapshot
        ClientState.snapshot=Snapshot(library=Library().apply { seed() })
        mc.options.guiScale().set(1);mc.resizeDisplay()
        val initial=BuildingGraph.assembledStarter()
        val room=initial.nodes.single { it.id=="room" }
        val graph=initial.copy(nodes=initial.nodes.map { node -> when(node.id) {
            "storey" -> node.copy(inputs=listOf("room","room_annex"),offsets=listOf(P(),P(6,0,0)))
            "roof" -> node.copy(roofType="flat",walls=emptyMap())
            "room" -> node.copy(name="Wohnraum")
            else -> node
        } }+room.copy(id="room_annex",name="Nebenraum",y=340))
        building=BuildingScreen().apply {
            building=Building(schema=2,id="floor_plan_smoke",name="Raumgrundriss",graph=graph)
            floorPlanView.selected="room"
        }
        fixtureJson=document();mc.setScreen(building);building.openFloorPlan();canvas().fit()
        check(building.tab==8 && building.graphMode && building.graphInspector)
        check(canvas().roomColor("room")!=canvas().roomColor("room_annex")) { "Each room must have its own color" }
    }

    private fun graph()=checkNotNull(building.building.graph)
    private fun node(id:String)=graph().nodes.single { it.id==id }
    private fun canvas()=checkNotNull(building.floorPlanCanvas)
    private fun document()=Json.encode(building.building)
    private fun cells(id:String)=RoomGeometry.cells(node(id).rectangles)
    private fun control(key:String)=building.children().filterIsInstance<Button>().single { it.message.string==ui(key) }
    private fun click(point:Pair<Double,Double>,button:Int=0) {
        check(point.first>=0 && point.first<building.width && point.second>=0 && point.second<building.height) { "Floor plan hit target must fit the screen" }
        check(building.mouseClicked(point.first,point.second,button)) { "Floor plan must receive the actual screen click" }
        building.mouseReleased(point.first,point.second,button)
    }
    private fun action(key:String) {
        val button=control(key);check(button.active) { "Floor plan action must be available: $key" }
        click(button.x+button.width/2.0 to button.y+button.height/2.0)
    }
    private fun select(id:String) {
        val before=document();canvas().revealRoom(id);click(canvas().roomPoint(id))
        check(building.floorPlanView.selected==id && document()==before) { "Selecting a room must preserve the document" }
    }
    private fun layout() {
        check(building.tab==8 && building.floorPlanCanvas!=null)
        building.children().filterIsInstance<AbstractWidget>().filter { it.visible }.forEach { widget ->
            check(widget.x>=0 && widget.y>=0 && widget.x+widget.width<=building.width && widget.y+widget.height<=building.height) {
                "Visible floor plan control extends beyond ${building.width}x${building.height}: ${widget.message.string}"
            }
        }
        check(canvas().roomColor("room")!=canvas().roomColor("room_annex"))
        val storey=node("storey")
        for((index,id) in storey.inputs.withIndex()) for(local in cells(id)) {
            val cell=local+storey.offsets[index];val point=canvas().gridPoint(cell.x+0.5,cell.z+0.5)
            check(point.first>canvas().x && point.first<canvas().x+canvas().width && point.second>=canvas().y+27 && point.second<canvas().y+canvas().height-24) {
                "The complete room arrangement must fit the floor plan at both scales"
            }
        }
    }
    private fun dynamicWalls(id:String) {
        check(building.floorPlanView.selected==id)
        val edges=RoomGeometry.edges(node(id).rectangles)
        val ports=BuildingPorts.of(node(id)).filter { it.key.startsWith("wall:") }
        check(edges.size==ports.size)
        edges.forEach { edge ->
            val port=ports.single { it.key=="wall:${edge.id}" }
            check(port.length==edge.length && port.accepts==(setOf(BuildingKind.ROTATION_SEGMENT,BuildingKind.SEGMENT,BuildingKind.PATTERN,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_ROTATE)+SegmentBooleans.kinds))
            canvas().wallPoint(port.key)
        }
    }
    private fun invalidEdits() {
        val before=document();val undo=control("undo").active;val redo=control("redo").active
        click(canvas().gridPoint(1.5,1.5))
        check(document()==before) { "Removing the center cell must reject an enclosed hole" }
        click(canvas().gridPoint(4.5,0.5))
        check(document()==before) { "Painting an isolated cell must reject a disconnected room" }
        check(control("undo").active==undo && control("redo").active==redo) { "Rejected topology edits must preserve history" }
    }
    private fun boundaryClick() {
        check(document()==fixtureJson)
        val room=node("room");val annex=node("room_annex")
        click(canvas().gridPoint(2.5,2.5))
        check(cells("room")==RoomGeometry.cells(room.rectangles)-P(2,0,2))
        check(node("room_annex")==annex)
        check(RoomGeometry.edges(node("room").rectangles).size==6)
        dynamicWalls("room");clickedJson=document()
    }
    private fun clickHistory() {
        action("undo");check(document()==fixtureJson && !control("undo").active)
        action("redo");check(document()==clickedJson)
        action("undo");check(document()==fixtureJson)
    }
    private fun startRectangle() {
        check(document()==fixtureJson)
        val start=canvas().gridPoint(-0.5,0.5);dragEnd=canvas().gridPoint(-0.5,1.5)
        check(building.mouseClicked(start.first,start.second,0))
        check(building.mouseDragged(dragEnd.first,dragEnd.second,0,dragEnd.first-start.first,dragEnd.second-start.second))
        check(document()==fixtureJson) { "Rectangle preview must remain outside document history until release" }
    }
    private fun finishRectangle() {
        check(building.mouseReleased(dragEnd.first,dragEnd.second,0))
        check(cells("room")==RoomGeometry.cells(listOf(Rect(width=3,length=3)))+setOf(P(-1,0,0),P(-1,0,1)))
        dynamicWalls("room");rectangleJson=document()
    }
    private fun rectangleHistory() {
        action("undo");check(document()==fixtureJson && !control("undo").active) { "One rectangle gesture must create exactly one undo step" }
        invalidEdits()
        action("redo");check(document()==rectangleJson) { "Rejected edits must retain the previous rectangle redo" }
        action("undo");check(document()==fixtureJson)
    }
    private fun paintAnnex() {
        val room=node("room");val annex=node("room_annex");val offsets=node("storey").offsets
        click(canvas().gridPoint(9.5,0.5))
        check(cells("room_annex")==RoomGeometry.cells(annex.rectangles)+P(3,0,0)) { "Room painting must account for the selected room's storey offset" }
        check(node("room")==room && node("storey").offsets==offsets) { "Painting one room must preserve other rooms and their positions" }
        dynamicWalls("room_annex");annexJson=document()
    }
    private fun openWallMenu() {
        canvas().revealRoom("room_annex")
        val key="wall:"+RoomGeometry.edges(node("room_annex").rectangles).first().id
        click(canvas().wallPoint(key));check(canvas().hasOpenMenu())
        check(runCatching { canvas().sourcePoint("floor") }.isFailure) { "Wall sources must exclude incompatible floor nodes" }
        canvas().sourcePoint("empty_wall")
    }
    private fun assignWall() {
        val key=RoomGeometry.edges(node("room_annex").rectangles).first().id
        val room=node("room")
        click(canvas().sourcePoint("empty_wall"))
        check(!canvas().hasOpenMenu() && node("room_annex").walls[key]=="empty_wall")
        check(node("room")==room);BuildingGraphCompiler.structure(graph());assignedJson=document()
    }
    private fun assignmentHistory() {
        action("undo");check(document()==annexJson)
        action("redo");check(document()==assignedJson)
        action("undo");check(document()==annexJson)
    }
    private fun compactRectangle() {
        val before=document();val first=canvas().gridPoint(-0.5,0.5);val last=canvas().gridPoint(-0.5,1.5)
        check(building.mouseClicked(first.first,first.second,0))
        check(building.mouseDragged(last.first,last.second,0,last.first-first.first,last.second-first.second))
        check(building.mouseReleased(last.first,last.second,0));compactJson=document()
        check(compactJson!=before);dynamicWalls("room")
        action("undo");check(document()==before)
        action("redo");check(document()==compactJson)
        action("undo");check(document()==before)
    }
    private fun eraseRectangle() {
        check(document()==fixtureJson && !control("undo").active)
        val before=cells("room");val annex=node("room_annex")
        val first=canvas().gridPoint(0.5,0.5);val last=canvas().gridPoint(0.5,2.5)
        check(building.mouseClicked(first.first,first.second,1))
        check(building.mouseDragged(last.first,last.second,1,last.first-first.first,last.second-first.second))
        check(document()==fixtureJson) { "Right-drag erase must remain a preview until release" }
        check(building.mouseReleased(last.first,last.second,1))
        check(cells("room")==before-(0..2).map { P(0,0,it) }.toSet() && node("room_annex")==annex)
        erasedJson=document();check(erasedJson!=fixtureJson)
        action("undo");check(document()==fixtureJson && !control("undo").active) { "One erase rectangle must create exactly one undo step" }
        action("redo");check(document()==erasedJson)
        action("undo");check(document()==fixtureJson)
    }
    private fun cancelRectangle() {
        val before=document();val undo=control("undo").active;val redo=control("redo").active
        val first=canvas().gridPoint(-0.5,0.5);val last=canvas().gridPoint(-0.5,1.5)
        check(building.mouseClicked(first.first,first.second,0))
        check(building.mouseDragged(last.first,last.second,0,last.first-first.first,last.second-first.second))
        check(building.keyPressed(256,0,0)) { "Escape must cancel an active rectangle preview" }
        building.mouseReleased(last.first,last.second,0)
        check(Minecraft.getInstance().screen===building && document()==before)
        check(control("undo").active==undo && control("redo").active==redo) { "Cancelling a rectangle must preserve history" }
        action("redo");check(document()==erasedJson)
        action("undo");check(document()==before)
    }
    private fun panAndZoom() {
        val before=document();val undo=control("undo").active;val redo=control("redo").active
        val first=canvas().gridPoint(1.5,1.5);val last=first.first+13.0 to first.second+7.0
        check(building.mouseClicked(first.first,first.second,2))
        check(building.mouseDragged(last.first,last.second,2,13.0,7.0))
        check(building.mouseReleased(last.first,last.second,2))
        val moved=canvas().gridPoint(1.5,1.5)
        check(kotlin.math.abs(moved.first-last.first)<0.000001 && kotlin.math.abs(moved.second-last.second)<0.000001) { "Middle-button dragging must pan the grid by the actual screen movement" }
        val step=building.floorPlanView.step
        check(building.mouseScrolled(moved.first,moved.second,0.0,1.0))
        check(building.floorPlanView.step>step) { "Scrolling over the grid must zoom its cell spacing" }
        val anchored=canvas().gridPoint(1.5,1.5)
        check(kotlin.math.abs(anchored.first-moved.first)<0.000001 && kotlin.math.abs(anchored.second-moved.second)<0.000001) { "Grid zoom must retain the cell under the cursor" }
        check(document()==before && control("undo").active==undo && control("redo").active==redo) { "Panning and zooming must preserve document history" }
        canvas().fit()
    }
    private fun rejectOverlap() {
        val before=document();val undo=control("undo").active;val redo=control("redo").active
        val first=canvas().gridPoint(3.5,0.5);val last=canvas().gridPoint(6.5,0.5)
        check(building.mouseClicked(first.first,first.second,0))
        check(building.mouseDragged(last.first,last.second,0,last.first-first.first,last.second-first.second))
        check(building.mouseReleased(last.first,last.second,0))
        check(document()==before) { "A connected rectangle extending into another room must reject the whole gesture" }
        check(control("undo").active==undo && control("redo").active==redo)
        action("redo");check(document()==erasedJson) { "Rejected overlap must retain the preceding redo state" }
        action("undo");check(document()==before)
    }
    private fun addRoom() {
        val before=graph();val template=node("room");val storey=node("storey")
        val colors=before.nodes.filter { it.kind==BuildingKind.ROOM }.associate { it.id to canvas().roomColor(it.id) }
        action("floorplan_add_room")
        val added=graph().nodes.filter { node -> before.nodes.none { it.id==node.id } }.single()
        check(added.kind==BuildingKind.ROOM && building.floorPlanView.selected==added.id) { "Adding a room must create and select one distinct room node" }
        check(added.floor==template.floor && added.ceiling==template.ceiling)
        check(before.nodes.filter { it.id!=storey.id }.all { original -> node(original.id)==original }) { "Adding a room must preserve every existing source and room" }
        val changed=node("storey");val index=changed.inputs.indexOf(added.id)
        check(index>=0 && changed.inputs.filter { it!=added.id }==storey.inputs)
        val occupied=storey.inputs.flatMapIndexed { position,id -> RoomGeometry.shell(node(id).rectangles).map { it+storey.offsets[position] } }.toSet()
        val placed=RoomGeometry.cells(added.rectangles).map { it+changed.offsets[index] }
        check(placed.isNotEmpty() && placed.none { it in occupied }) { "The new room must receive a free storey position outside the existing room shells" }
        check(colors.all { (id,color) -> canvas().roomColor(id)==color } && canvas().roomColor(added.id) !in colors.values)
        BuildingGraphCompiler.structure(graph());addedRoom=added.id;addedJson=document()
    }
    private fun deleteRoomHistory() {
        check(document()==addedJson)
        val colors=graph().nodes.filter { it.kind==BuildingKind.ROOM }.associate { it.id to canvas().roomColor(it.id) }
        select("room");action("remove")
        check(graph().nodes.none { it.id=="room" } && graph().nodes.any { it.id==addedRoom })
        check(graph().nodes.any { it.id==building.floorPlanView.selected && it.kind==BuildingKind.ROOM }) { "Deleting a selected room must select a remaining room" }
        check(colors.filterKeys { it!="room" }.all { (id,color) -> canvas().roomColor(id)==color }) { "Deleting the first room must preserve the colors of all surviving room IDs" }
        val library=ClientState.snapshot.library
        BuildingGraphCompiler.compile(building.building,library.forms,library.modules)
        val deleted=document()
        action("undo");check(document()==addedJson) { "Undo must restore the full document from before room deletion" }
        check(colors.all { (id,color) -> canvas().roomColor(id)==color })
        action("redo");check(document()==deleted)
        action("undo");check(document()==addedJson)
        action("undo");check(document()==fixtureJson && !control("undo").active) { "Undoing room addition must restore the original fixture" }
    }
    private fun repeatedRoomInstance() {
        check(document()==fixtureJson)
        val original=graph();val room=node("room");val annex=node("room_annex")
        val storey=node("storey").copy(inputs=listOf("room","room"),offsets=listOf(P(),P(6,0,0)))
        building.commitBuildingGraph(original.replace(storey));select("room");canvas().fit()
        val repeated=document();val expected=RoomGeometry.cells(room.rectangles)-P(2,0,2)
        click(canvas().gridPoint(8.5,2.5))
        check(cells("room")==expected && node("room_annex")==annex) { "Clicking the second instance must edit its room using that instance's offset" }
        check(node("storey")==storey)
        storey.offsets.forEach { offset ->
            val instance=cells("room").map { it+offset }.toSet()
            check(instance==expected.map { it+offset }.toSet() && P(2,0,2)+offset !in instance) { "Every instance of a shared room definition must display the same edited footprint" }
        }
        val edited=document()
        action("undo");check(document()==repeated)
        action("redo");check(document()==edited)
        action("undo");check(document()==repeated)
        action("undo");check(document()==fixtureJson && !control("undo").active)
    }
    private fun screenshot(mc:Minecraft,name:String) {
        Screenshot.grab(mc.gameDirectory,"architect-floorplan-$name.png",mc.mainRenderTarget) { println(it.string) }
    }
    private fun advance(mc:Minecraft,elapsed:Int):Boolean {
        check(mc.screen===building)
        when(elapsed) {
            5 -> { layout();screenshot(mc,"normal") }
            10 -> { check(!control("undo").active && !control("redo").active);invalidEdits() }
            15 -> boundaryClick()
            20 -> screenshot(mc,"walls-normal")
            25 -> clickHistory()
            30 -> startRectangle()
            35 -> screenshot(mc,"rectangle-preview")
            40 -> finishRectangle()
            45 -> { layout();screenshot(mc,"rectangle-normal") }
            50 -> rectangleHistory()
            55 -> select("room_annex")
            60 -> paintAnnex()
            65 -> { layout();screenshot(mc,"annex-normal") }
            70 -> openWallMenu()
            75 -> screenshot(mc,"wall-sources")
            80 -> assignWall()
            85 -> assignmentHistory()
            90 -> { mc.options.guiScale().set(2);mc.resizeDisplay();canvas().fit() }
            95 -> { layout();screenshot(mc,"annex-compact") }
            100 -> select("room")
            105 -> compactRectangle()
            110 -> { action("redo");check(document()==compactJson);canvas().fit() }
            115 -> { layout();screenshot(mc,"compact") }
            120 -> {
                action("undo");check(document()==annexJson)
                action("undo");check(document()==fixtureJson)
                mc.options.guiScale().set(1);mc.resizeDisplay();canvas().fit()
            }
            125 -> eraseRectangle()
            130 -> cancelRectangle()
            135 -> panAndZoom()
            140 -> rejectOverlap()
            145 -> addRoom()
            150 -> deleteRoomHistory()
            155 -> repeatedRoomInstance()
            160 -> {
                check(document()==fixtureJson)
                ClientState.snapshot=previousSnapshot
                mc.options.guiScale().set(previousScale);mc.resizeDisplay();mc.setScreen(previous)
                return true
            }
        }
        return false
    }
}
