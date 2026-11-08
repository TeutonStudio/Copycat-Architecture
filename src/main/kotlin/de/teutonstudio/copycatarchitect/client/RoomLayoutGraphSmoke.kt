package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.Json
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox

/** Real inspector input for ROOM cells and independent ROOM instances within a STOREY. */
internal object RoomLayoutGraphSmoke {
    private lateinit var previous:ShapeScreen
    private lateinit var building:BuildingScreen
    private var previousJson=""
    private var previousScale=1
    private var fixtureJson=""
    private var shapedJson=""
    private var movedJson=""
    private var tableMovedJson=""
    private var tableHeightJson=""
    private var tableTurnJson=""

    fun begin(mc:Minecraft,original:ShapeScreen) {
        previous=original;previousJson=Json.encode(original.form);previousScale=mc.options.guiScale().get()
        mc.options.guiScale().set(1);mc.resizeDisplay()
        val initial=BuildingGraph.assembledStarter();val room=initial.nodes.single { it.id=="room" }
        val graph=initial.copy(nodes=initial.nodes.map { node -> when(node.id) {
            "storey" -> node.copy(inputs=listOf("room","room_annex"),offsets=listOf(P(),P(4,0,0)))
            "roof" -> node.copy(roofType="flat",walls=emptyMap())
            else -> node
        } }+room.copy(id="room_annex",name="Annex",y=340))
        building=BuildingScreen().apply {
            building=Building(schema=2,id="room_layout_smoke",name="Room layout",graph=graph)
            graphMode=true;graphInspector=false;selectBuildingNode("room")
        }
        fixtureJson=Json.encode(building.building);mc.setScreen(building)
        checkNotNull(building.buildingGraphCanvas).result().getOrThrow()
    }

    private fun graph()=checkNotNull(building.building.graph)
    private fun node(id:String)=graph().nodes.single { it.id==id }
    private fun canvas()=checkNotNull(building.buildingGraphCanvas)
    private fun editor()=checkNotNull(building.kilaBuildingGraph)
    private fun restored()=check(Json.encode(building.building)==fixtureJson) { "Room/storey actions must restore the full original fixture" }
    private fun click(point:Pair<Double,Double>,button:Int=0) {
        check(point.first>=0 && point.first<building.width && point.second>=0 && point.second<building.height)
        check(building.mouseClicked(point.first,point.second,button)) { "Actual room inspector must receive the click" }
        building.mouseReleased(point.first,point.second,button)
    }
    private fun press(key:String) {
        val control=building.children().filterIsInstance<Button>().single { it.message.string==ui(key) }
        check(control.active);click(control.x+control.width/2.0 to control.y+control.height/2.0)
    }
    private fun openStoreyInspector() {
        building.selectBuildingNode("storey");building.openBuildingGraph(true)
        if(building.floorPlanCanvas!=null && !canvas().visible) press("panel_storey_properties")
        check(canvas().visible && building.children().any { it===canvas() }) { "Storey regression must exercise the visible standard properties inspector" }
    }
    private fun revealGrid(id:String,x:Double,z:Double):Pair<Double,Double> {
        val inspector=canvas();val point=inspector.gridPoint(id,x,z)
        val target=point.second.coerceIn(inspector.y+55.0,inspector.y+inspector.height-18.0)
        if(target!=point.second) check(inspector.mouseScrolled(inspector.x+inspector.width/2.0,inspector.y+inspector.height-8.0,0.0,(target-point.second)/25.0))
        return inspector.gridPoint(id,x,z).also { check(it.second>=inspector.y+35 && it.second<inspector.y+inspector.height) }
    }
    private fun visibleFootprint(id:String) {
        val inspector=canvas();val selected=node(id)
        val cells=if(selected.kind==BuildingKind.ROOM) RoomGeometry.cells(selected.rectangles) else selected.inputs.flatMapIndexed { i,source ->
            RoomGeometry.cells(node(source).rectangles).map { it+(selected.offsets.getOrNull(i) ?: P()) }
        }.toSet()
        for(cell in cells) {
            val point=inspector.gridPoint(id,cell.x+0.5,cell.z+0.5)
            check(point.first>=inspector.x && point.first<inspector.x+inspector.width && point.second>=inspector.y+35 && point.second<inspector.y+inspector.height) {
                "Opening a room or storey inspector must show the complete footprint at the current window size"
            }
        }
    }
    private fun visibleTable() {
        val inspector=canvas();val point=inspector.tablePoint("storey")
        check(point.first>=inspector.x && point.first<inspector.x+inspector.width && point.second>=inspector.y+35 && point.second<inspector.y+inspector.height) {
            "The actual architect table block must be visible with the storey layout at both window sizes"
        }
    }
    private fun revealProperty(row:Int,column:Int=16):Pair<Double,Double> {
        val inspector=canvas();val point=inspector.propertyPoint("storey",row,column)
        val target=point.second.coerceIn(inspector.y+55.0,inspector.y+inspector.height-18.0)
        if(target!=point.second) check(inspector.mouseScrolled(inspector.x+inspector.width/2.0,inspector.y+inspector.height-8.0,0.0,(target-point.second)/25.0))
        return inspector.propertyPoint("storey",row,column).also { check(it.second>=inspector.y+35 && it.second<inspector.y+inspector.height) }
    }
    private fun hierarchyPorts() {
        val native=editor();val room=node("room");val storey=node("storey")
        check(room.kind==BuildingKind.ROOM && storey.kind==BuildingKind.STOREY && storey.storeyMode=="rooms")
        check(storey.inputs==listOf("room","room_annex")) { "Storey must compose multiple explicit room nodes" }
        val roomType=checkNotNull(native.outputPortType("room"));val wallType=checkNotNull(native.outputPortType("wall"))
        for((index,port) in BuildingPorts.of(room,true).withIndex()) {
            val expected=when {
                port.key.startsWith("wall:") || port.key.startsWith("edge:") -> wallType
                port.key=="floor" -> native.outputPortType("floor")
                port.key=="ceiling" -> native.outputPortType("ceiling")
                else -> native.outputPortType("wall_copycat")
            }
            check(native.inputPortType(room.id,index)==expected) { "Actual dynamic room socket ${port.key} has the wrong native type" }
        }
        check(native.inputPortType(room.id,BuildingPorts.of(room,true).size)==null) { "Removed wall runs must not leave stale native sockets" }
        check((0..2).all { native.inputPortType("storey",it)==roomType })
        check(native.nodeAccent("wall")==0xffff8a27.toInt() && native.nodeAccent("floor")==0xffff8a27.toInt() && native.nodeAccent("ceiling")==0xffff8a27.toInt())
        check(native.nodeAccent("room")==0xff21b15b.toInt() && native.nodeAccent("storey")==0xffeeeeee.toInt())
    }
    private fun toggleRoom() {
        restored();val before=node("room");val annex=node("room_annex")
        val point=revealGrid("room",2.5,2.5)
        click(point,1);restored()
        click(point)
        val after=node("room")
        check(RoomGeometry.cells(after.rectangles)==RoomGeometry.cells(before.rectangles)-P(2,0,2)) { "Left clicking a filled cell must remove exactly that room cell" }
        check(RoomGeometry.edges(after.rectangles).size==6 && BuildingPorts.of(after).count { it.key.startsWith("wall:") }==6)
        check(after.floor==before.floor && after.ceiling==before.ceiling && after.height==before.height)
        check(node("room_annex")==annex) { "Editing a room must preserve the other room definition" }
        shapedJson=Json.encode(building.building)
    }
    private fun shapeHistory() {
        check(Json.encode(building.building)==shapedJson)
        press("undo");restored()
        press("redo");check(Json.encode(building.building)==shapedJson)
        press("undo");restored()
    }
    private fun dragAnnex() {
        restored();val first=node("storey").offsets[0]
        val start=revealGrid("storey",4.5,0.5);val target=canvas().gridPoint("storey",4.5,1.5)
        check(building.mouseClicked(start.first,start.second,0))
        check(building.mouseDragged(target.first,target.second,0,target.first-start.first,target.second-start.second))
        building.mouseReleased(target.first,target.second,0)
        check(node("storey").offsets==listOf(first,P(4,0,1))) { "Dragging the second room in the storey graphic must move only that room by one grid cell" }
        canvas().result().getOrThrow()
        movedJson=Json.encode(building.building)
    }
    private fun moveHistory() {
        check(Json.encode(building.building)==movedJson)
        press("undo");restored()
        press("redo");check(Json.encode(building.building)==movedJson)
        press("undo");restored()
    }
    private fun dragTable() {
        restored();visibleTable()
        val before=graph().projection;val rooms=node("storey");val localPlan=canvas().result().getOrThrow().plan
        val start=revealGrid("storey",before.table.x+0.5,before.table.z+0.5)
        check(start==canvas().tablePoint("storey"))
        val target=canvas().gridPoint("storey",before.table.x+1.5,before.table.z+1.5)
        check(building.mouseClicked(start.first,start.second,0))
        check(building.mouseDragged(target.first,target.second,0,target.first-start.first,target.second-start.second))
        building.mouseReleased(target.first,target.second,0)
        check(graph().projection==before.copy(table=before.table+P(1,0,1))) { "Dragging the table block must move its model reference by exactly one grid cell" }
        check(node("storey")==rooms && canvas().result().getOrThrow().plan==localPlan) { "Moving the projection reference must preserve room positions and the local building plan" }
        tableMovedJson=Json.encode(building.building)
    }
    private fun tableHistory(expected:String) {
        check(Json.encode(building.building)==expected)
        press("undo");restored()
        press("redo");check(Json.encode(building.building)==expected)
        press("undo");restored()
    }
    private fun editTableHeight(mc:Minecraft) {
        restored();val before=graph().projection;val rooms=node("storey")
        click(revealProperty(294))
        val dialog=checkNotNull(mc.screen);check(dialog!==building)
        val entry=dialog.children().filterIsInstance<EditBox>().single()
        entry.value="257";check(dialog.keyPressed(257,0,0))
        check(mc.screen===dialog && graph().projection==before) { "An out-of-range table height must leave the document unchanged" }
        entry.value="-3";check(dialog.keyPressed(257,0,0));check(mc.screen===building)
        check(graph().projection==before.copy(table=before.table.copy(y=-3)) && node("storey")==rooms) { "The real table height field must change only the saved projection height" }
        tableHeightJson=Json.encode(building.building)
    }
    private fun editTableTurn() {
        restored();val before=graph().projection
        click(revealProperty(294,160));check(canvas().hasOpenMenu())
        check(building.keyPressed(264,0,0));check(building.keyPressed(257,0,0))
        check(!canvas().hasOpenMenu() && graph().projection==before.copy(turn=1)) { "The actual rotation menu must save a quarter-turn for the world projection" }
        tableTurnJson=Json.encode(building.building)
    }
    private fun screenshot(mc:Minecraft,name:String) {
        Screenshot.grab(mc.gameDirectory,"architect-kilagraph-$name.png",mc.mainRenderTarget) { println(it.string) }
    }

    fun tick(mc:Minecraft,elapsed:Int):Boolean {
        check(mc.screen===building)
        when(elapsed) {
            10 -> { editor().fit();hierarchyPorts() }
            15 -> screenshot(mc,"room-storey-hierarchy")
            20 -> { building.selectBuildingNode("room");building.openBuildingGraph(true) }
            25 -> { visibleFootprint("room");screenshot(mc,"room-cell-grid") }
            30 -> { toggleRoom();building.openBuildingGraph(false) }
            35 -> hierarchyPorts()
            40 -> { shapeHistory();openStoreyInspector() }
            45 -> { visibleFootprint("storey");visibleTable();screenshot(mc,"storey-room-layout") }
            50 -> dragAnnex()
            55 -> moveHistory()
            60 -> dragTable()
            65 -> tableHistory(tableMovedJson)
            70 -> editTableHeight(mc)
            75 -> { screenshot(mc,"storey-table-controls");tableHistory(tableHeightJson) }
            80 -> editTableTurn()
            85 -> { tableHistory(tableTurnJson);mc.options.guiScale().set(2);mc.resizeDisplay();building.selectBuildingNode("room");building.openBuildingGraph(true) }
            90 -> { visibleFootprint("room");screenshot(mc,"compact-room-cell-grid") }
            95 -> { toggleRoom();building.openBuildingGraph(false) }
            100 -> hierarchyPorts()
            105 -> { shapeHistory();openStoreyInspector() }
            110 -> { visibleFootprint("storey");visibleTable();screenshot(mc,"compact-storey-room-layout") }
            115 -> dragAnnex()
            120 -> moveHistory()
            125 -> dragTable()
            130 -> tableHistory(tableMovedJson)
            135 -> editTableHeight(mc)
            140 -> { screenshot(mc,"compact-storey-table-controls");tableHistory(tableHeightJson) }
            145 -> editTableTurn()
            150 -> {
                tableHistory(tableTurnJson);mc.options.guiScale().set(previousScale);mc.resizeDisplay();mc.setScreen(previous)
                BuildingProjectionScreenSmoke.verify(mc)
                check(Json.encode(previous.form)==previousJson)
                println("ARCHITECT ROOM LAYOUT: orange boundaries, green ROOM, white STOREY, room-cell clicks, dynamic typed sockets, two-room layout drag, architect table marker drag, height validation, world rotation, atomic Undo/Redo and normal/compact inspectors verified")
                return true
            }
        }
        return false
    }
}
