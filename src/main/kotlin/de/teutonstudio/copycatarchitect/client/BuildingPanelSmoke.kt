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

/** Real client acceptance of the sectioned panel and its shared building graph document. */
internal object BuildingPanelSmoke {
    private var ticks=0
    private var failed=false
    private var previous:Screen?=null
    private var previousScale=1
    private lateinit var previousSnapshot:Snapshot
    private lateinit var building:BuildingScreen
    private var fixtureJson=""
    private var addedJson=""
    private var nativeBefore=""
    private var nativeAddedJson=""
    private var nativeAddedId=""
    private var nativePosition=0 to 0
    private var movedJson=""
    private var draggingBefore=""
    private lateinit var dragEnd:Pair<Double,Double>

    fun tick():Boolean {
        if(failed) return false
        val mc=Minecraft.getInstance()
        if(ticks==0 && (mc.screen==null || mc.overlay!=null)) return false
        ticks++
        try {
            if(ticks==1) begin(mc)
            else if(if(java.lang.Boolean.getBoolean("architect.landscapeOnlySmoke")) advanceLandscape(mc,ticks-1) else advance(mc,ticks-1)) {
                println(if(java.lang.Boolean.getBoolean("architect.landscapeOnlySmoke")) "ARCHITECT LANDSCAPE UI SMOKE PASSED: normal/compact placement from the BUILDING node, independent building body and garden dragging, path handle dragging/insertion/deletion/heights, numeric placement, source preservation, one shared Undo step per action, Escape cancellation and complete output verified" else "ARCHITECT BUILDING PANEL UI SMOKE PASSED: normal/compact sections and definition lists, lossless selection/navigation, nested horizontal/vertical segment patterns, panel/native graph additions with shared undo/redo, repeated room instance placement, deferred drag commits and overlap rejection, garden fencing, path width, foundation depth, clearance, ordered 3D waypoints and shared undo/redo verified")
                mc.stop()
            }
        } catch(error:Throwable) {
            failed=true
            System.err.println("ARCHITECT BUILDING PANEL UI SMOKE FAILED at tick $ticks: ${error.message}")
            screenshot(mc,"failure-$ticks")
            throw IllegalStateException("Building panel UI smoke failed at tick $ticks",error)
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
            "room" -> node.copy(name="Wohnraum")
            "storey" -> node.copy(name="Erdgeschoss",inputs=listOf("room","room_annex","room"),offsets=listOf(P(),P(6,0,0),P(12,0,0)))
            "roof" -> node.copy(roofType="flat",walls=emptyMap())
            "building" -> node.copy(inputs=listOf("garden","path"))
            else -> node
        } }+listOf(
            room.copy(id="room_annex",name="Nebenraum",y=340),
            BuildingNode("pattern_piece",BuildingKind.SEGMENT,"Teilsegment",290,850,inputs=listOf("wall_copycat"),module="",length=2,height=2),
            BuildingNode("pattern_pair",BuildingKind.PATTERN,"Doppelsegment",580,850,inputs=listOf("pattern_piece","pattern_piece")),
            BuildingNode("pattern_nested",BuildingKind.PATTERN,"Zusammengesetztes Segment",850,850,inputs=listOf("pattern_pair","pattern_pair")),
            BuildingAssembly.newNode(BuildingKind.GARDEN,"garden","Garten").copy(coordinates=(20..24).flatMap { x -> (20..24).map { z -> P(x,0,z) } }),
            BuildingAssembly.newNode(BuildingKind.PATH,"path","Weg").copy(coordinates=listOf(P(20,0,16),P(24,2,16)))
        ))
        BuildingGraphCompiler.structure(graph)
        building=BuildingScreen().apply {
            building=Building(schema=2,id="building_panel_smoke",name="Gebäudebedienfeld",graph=graph)
            buildingGraphView.selected="pattern_nested"
        }
        fixtureJson=document();mc.setScreen(building);building.openBuildingSection(BuildingPanelSection.SEGMENTS)
    }

    private fun graph()=checkNotNull(building.building.graph)
    private fun node(id:String)=graph().nodes.single { it.id==id }
    private fun document()=Json.encode(building.building)
    private fun canvas()=checkNotNull(building.floorPlanCanvas)
    private fun inspector()=checkNotNull(building.buildingGraphCanvas)
    private fun native()=checkNotNull(building.kilaBuildingGraph)
    private fun control(key:String)=building.children().filterIsInstance<Button>().filter { it.message.string==ui(key) }.minBy { it.y }
    private fun history()=control("undo").active to control("redo").active
    private fun click(point:Pair<Double,Double>,button:Int=0) {
        check(point.first>=0 && point.first<building.width && point.second>=0 && point.second<building.height) {
            "Building panel hit target must fit ${building.width}x${building.height}: $point"
        }
        check(building.mouseClicked(point.first,point.second,button)) { "The actual building screen must receive the click" }
        building.mouseReleased(point.first,point.second,button)
    }
    private fun action(key:String) {
        val button=control(key);check(button.active && button.visible) { "Building panel action must be available: $key" }
        click(button.x+button.width/2.0 to button.y+button.height/2.0)
    }
    private fun section(section:BuildingPanelSection) {
        val before=document();val history=history()
        action(section.key)
        check(building.buildingPanelSection==section && building.tab==7 && building.graphMode && building.graphInspector)
        check(document()==before && history()==history) { "Changing a building section must preserve the document and history" }
    }
    private fun select(id:String) {
        val before=document();val history=history()
        click(checkNotNull(building.buildingDefinitionList).definitionPoint(id))
        check(building.buildingGraphView.selected==id && document()==before && history()==history) {
            "Selecting a definition must preserve the document and history"
        }
    }
    private fun layout() {
        building.children().filterIsInstance<AbstractWidget>().filter { it.visible }.forEach { widget ->
            check(widget.x>=0 && widget.y>=0 && widget.x+widget.width<=building.width && widget.y+widget.height<=building.height) {
                "Visible building widget extends beyond ${building.width}x${building.height}: ${widget.message.string}"
            }
        }
        if(building.tab==7) {
            check(checkNotNull(building.buildingDefinitionList).section==building.buildingPanelSection)
            check(graph().nodes.any { it.id==building.buildingGraphView.selected && it.kind in building.buildingPanelSection.kinds })
        }
    }
    private fun restored()=check(document()==fixtureJson) { "Shared Undo must restore every field of the original building" }
    private fun roundTrip(expected:String) {
        val history=history()
        action("node_graph");check(building.kilaBuildingGraph!=null && !building.graphInspector)
        check(document()==expected && history()==history)
        action("control_panel");check(building.buildingDefinitionList!=null && building.graphInspector)
        check(document()==expected && history()==history) { "Graph/panel roundtrips must preserve the complete document and shared history" }
    }
    private fun revealProperty(id:String,row:Int,column:Int=16):Pair<Double,Double> {
        val inspector=inspector();val point=inspector.propertyPoint(id,row,column)
        val target=point.second.coerceIn(inspector.y+70.0,inspector.y+inspector.height-18.0)
        if(target!=point.second) check(building.mouseScrolled(inspector.x+inspector.width/2.0,inspector.y+inspector.height-8.0,0.0,(target-point.second)/25.0))
        return inspector.propertyPoint(id,row,column).also {
            check(it.second>=inspector.y+35 && it.second<inspector.y+inspector.height)
        }
    }
    private fun changePatternAxis() {
        restored();check(building.buildingGraphView.selected=="pattern_nested")
        click(revealProperty("pattern_nested",40));check(inspector().hasOpenMenu())
        check(building.keyPressed(264,0,0));check(building.keyPressed(257,0,0))
        check(node("pattern_nested").patternAxis=="vertical" && node("pattern_pair").patternAxis=="horizontal")
        val library=ClientState.snapshot.library
        val segment=BuildingGraphCompiler.Evaluation(graph(),building.building.palette,library.forms,library.modules).segment("pattern_nested")
        check(segment.length==4 && segment.height==4) { "The axis menu must compose nested wall segments above one another" }
        val changed=document();roundTrip(changed)
        action("undo");restored();action("redo");check(document()==changed);action("undo");restored()
    }
    private fun copycatSourceAndMaterial() {
        restored();select("wall_copycat")
        val original=node("wall_copycat")
        val beforeHistory=history()
        click(revealProperty(original.id,0));check(inspector().hasOpenMenu())
        check(building.keyPressed(256,0,0));restored()
        check(history()==beforeHistory) { "Cancelling the source list must preserve history" }
        click(revealProperty(original.id,0))
        check(building.keyPressed(268,0,0));check(building.keyPressed(257,0,0))
        check(node(original.id)==original.copy(block="minecraft:air")) { "Air must be a valid Copycat source, preserving its role and saved form" }
        val library=ClientState.snapshot.library
        val wall=node("wall")
        val airPlan=BuildingGraphCompiler.Evaluation(graph(),building.building.palette,library.forms,library.modules).preview(wall.id)
        check(airPlan.placements.isEmpty() && airPlan.clear.size==wall.length*wall.height*wall.depth) { "Air Copycats in a wall must become explicit clear positions" }
        val airJson=document();action("undo");restored();action("redo");check(document()==airJson);action("undo");restored()

        click(revealProperty(original.id,44));check(inspector().hasOpenMenu())
        check(building.keyPressed(269,0,0));check(building.keyPressed(257,0,0))
        check(building.building.palette.getValue(original.role)==de.teutonstudio.copycatarchitect.server.Economy.palette.last())
        check(node(original.id)==original) { "Editing the material must preserve every Copycat geometry field" }
        val materialJson=document();check(materialJson!=fixtureJson)
        roundTrip(materialJson)
        action("undo");restored();action("redo");check(document()==materialJson);action("undo");restored()
        select("pattern_nested")
    }
    private fun addFromPanel() {
        restored();val before=graph()
        action("panel_add_definition")
        check(building.keyPressed(268,0,0));check(building.keyPressed(264,0,0));check(building.keyPressed(257,0,0))
        val added=graph().nodes.single { candidate -> before.nodes.none { it.id==candidate.id } }
        check(added.kind==BuildingKind.PATTERN && added.inputs==listOf("","") && building.buildingGraphView.selected==added.id)
        checkNotNull(building.buildingDefinitionList).definitionPoint(added.id)
        addedJson=document();BuildingGraphCompiler.structure(graph())
        roundTrip(addedJson);action("undo");restored();action("redo");check(document()==addedJson);action("undo");restored()
    }
    private fun beginNativeInsert() {
        restored()
        val editor=native();val point=editor.x+editor.width-8.0 to editor.y+editor.height-8.0
        nativeBefore=document();nativePosition=editor.graphPoint(point.first,point.second)
        click(point,1);check(editor.hasOpenMenu()) { "The native graph context menu must open through the actual screen" }
    }
    private fun openNativeInsertBranch() {
        val editor=native();val point=checkNotNull(editor.menuItemPoint(ui("add_node")))
        editor.mouseMoved(point.first,point.second)
    }
    private fun finishNativeInsert() {
        click(checkNotNull(native().menuItemPoint(ui("bg_copycat"))))
        // KilaGraph runs the selected native command when its widget ticks.
    }
    private fun verifyNativeInsert() {
        val original=Json.decode<Building>(nativeBefore).graph!!
        val added=graph().nodes.single { candidate -> original.nodes.none { it.id==candidate.id } }
        check(added.kind==BuildingKind.COPYCAT && (added.x to added.y)==nativePosition)
        nativeAddedId=added.id;nativeAddedJson=document()
        action("control_panel")
        check(building.buildingPanelSection==BuildingPanelSection.SEGMENTS)
        select(nativeAddedId)
        action("undo");restored();action("redo");check(document()==nativeAddedJson);action("undo");restored()
        BuildingGraphCompiler.structure(graph())
    }
    private fun selectRepeatedInstance() {
        restored();val before=document();val history=history()
        click(canvas().instancePoint(2))
        check(canvas().selectedInstanceIndex()==2 && building.buildingGraphView.selected=="storey")
        check(document()==before && history()==history) { "Clicking a repeated room instance must select its slot without an edit" }
    }
    private fun startPlacement(overlap:Boolean=false) {
        restored();val start=canvas().instancePoint(2)
        dragEnd=if(overlap) canvas().gridPoint(0.5,0.5) else canvas().gridPoint(12.5,1.5)
        draggingBefore=document()
        check(building.mouseClicked(start.first,start.second,0))
        check(building.mouseDragged(dragEnd.first,dragEnd.second,0,dragEnd.first-start.first,dragEnd.second-start.second))
        check(document()==draggingBefore) { "A room placement preview must remain outside history until mouse release" }
    }
    private fun finishPlacement() {
        check(building.mouseReleased(dragEnd.first,dragEnd.second,0))
        check(node("storey").offsets==listOf(P(),P(6,0,0),P(12,0,1))) { "Dragging a repeated room must move only the selected storey instance" }
        val original=Json.decode<Building>(fixtureJson).graph!!
        check(node("room")==original.nodes.single { it.id=="room" } && node("room_annex")==original.nodes.single { it.id=="room_annex" })
        movedJson=document();check(movedJson!=fixtureJson)
        action("undo");restored();action("redo");check(document()==movedJson);action("undo");restored()
    }
    private fun rejectOverlap() {
        val history=history();check(history.second)
        startPlacement(overlap=true)
        check(building.mouseReleased(dragEnd.first,dragEnd.second,0))
        restored();check(history()==history) { "Rejected overlap must preserve both history stacks" }
        action("redo");check(document()==movedJson);action("undo");restored()
    }
    private fun landscapeProperties() {
        restored()
        building.buildingGraphView.inspectorSections.setExpanded("properties",true)
        select("garden")
        click(revealProperty("garden",320));check(inspector().hasOpenMenu())
        check(building.keyPressed(264,0,0));check(building.keyPressed(257,0,0))
        check(node("garden").fenced)
        val library=ClientState.snapshot.library
        check(BuildingGraphCompiler.Evaluation(graph(),building.building.palette,library.forms,library.modules).preview("garden").materials["minecraft:oak_fence"]==16)
        val fenced=document();roundTrip(fenced);action("undo");restored();action("redo");check(document()==fenced);action("undo");restored()
        fun number(id:String,row:Int,value:String,column:Int=16) {
            click(revealProperty(id,row,column))
            val dialog=checkNotNull(Minecraft.getInstance().screen)
            check(dialog!==building)
            dialog.children().filterIsInstance<EditorTextField>().single().value=value
            check(dialog.keyPressed(257,0,0));check(Minecraft.getInstance().screen===building)
        }
        number("garden",364,"2");check(node("garden").fenceHeight==2);action("undo");restored()
        select("path");number("path",320,"3");check(node("path").pathWidth==3);action("undo");restored()
        number("path",254,"3");check(node("path").groundDepth==3);action("undo");restored()
        number("path",298,"2");check(node("path").clearance==2);action("undo");restored()
        click(revealProperty("path",0))
        val dialog=checkNotNull(Minecraft.getInstance().screen)
        check(dialog!==building)
        val points=listOf(P(20,0,16),P(22,1,16),P(22,2,19))
        dialog.children().filterIsInstance<EditorTextField>().single().value=BuildingSourceEditors.pathText(points)
        check(dialog.keyPressed(257,0,0));check(Minecraft.getInstance().screen===building)
        check(node("path").coordinates==points)
        action("undo");restored()
    }
    private fun properties() {
        val before=document();action("panel_storey_properties")
        check(building.floorPlanCanvas==null && building.children().any { it===building.buildingGraphCanvas })
        check(document()==before)
    }
    private fun screenshot(mc:Minecraft,name:String) {
        if(!failed && mc.screen===building) layout()
        Screenshot.grab(mc.gameDirectory,"architect-building-panel-$name.png",mc.mainRenderTarget) { println(it.string) }
    }

    private fun site()=Minecraft.getInstance().screen as? BuildingSiteScreen ?: error("The building node must own the site editor")
    private var siteKey=""
    private var sitePoint=-1
    private fun openSite() {
        section(BuildingPanelSection.BUILDING);select("building")
        click(revealProperty("building",0));site()
    }
    private fun startSiteDrag(key:String,point:Int=-1,cancel:Boolean=false) {
        val site=site();site.selectPart(key,point.coerceAtLeast(0));siteKey=key;sitePoint=point
        val start=if(point<0) site.partPoint(key) else site.pathPoint(key,point)
        val unit=site.gridPoint(1.0,0.0).first-site.gridPoint(0.0,0.0).first
        val delta=if(point<0) P(3,0,-2) else P(-1,0,1)
        dragEnd=start.first+delta.x*unit to start.second+delta.z*unit
        draggingBefore=document();val history=history()
        check(site.mouseClicked(start.first,start.second,0))
        check(site.mouseDragged(dragEnd.first,dragEnd.second,0,dragEnd.first-start.first,dragEnd.second-start.second))
        check(document()==draggingBefore && history()==history) { "The building layout must keep drag previews outside history" }
        if(cancel) {
            check(site.keyPressed(256,0,0));check(Minecraft.getInstance().screen===site)
            site.mouseReleased(dragEnd.first,dragEnd.second,0)
            check(document()==draggingBefore && history()==history)
        }
    }
    private fun finishSiteDrag() {
        val site=site();check(site.mouseReleased(dragEnd.first,dragEnd.second,0))
        val original=Json.decode<Building>(draggingBefore).graph!!;val root=node("building")
        check(graph().nodes.filter { it.id!="building" }==original.nodes.filter { it.id!="building" }) { "Site edits must preserve every source node" }
        if(sitePoint>=0) check(root.sitePaths.getValue(1).first()==P(19,0,17))
        else if(siteKey==BuildingSite.BODY) check(root.buildingOffset==P(3,0,-2))
        else check(root.offsets[0]==P(3,0,-2))
        val library=ClientState.snapshot.library
        BuildingGraphCompiler.Evaluation(graph(),building.building.palette,library.forms,library.modules).building()
        val changed=document();check(changed!=draggingBefore)
        check(site.keyPressed(90,0,2));restored()
        check(site.keyPressed(89,0,2));check(document()==changed)
        check(site.keyPressed(90,0,2));restored()
    }
    private fun sitePointActions() {
        val site=site();site.selectPart("in:1",0)
        val at=site.gridPoint(22.5,17.5)
        check(site.mouseClicked(at.first,at.second,1));site.mouseReleased(at.first,at.second,1)
        check(node("building").sitePaths.getValue(1).size==3 && node("path").coordinates.size==2)
        val added=document();check(site.keyPressed(261,0,0))
        check(!node("building").sitePaths.containsKey(1));restored()
        check(site.keyPressed(90,0,2));check(document()==added)
        check(site.keyPressed(90,0,2));restored()
        fun number(prefix:String,value:String) {
            val button=site.children().filterIsInstance<Button>().single { it.message.string.startsWith(prefix+" ") }
            check(site.mouseClicked(button.x+button.width/2.0,button.y+button.height/2.0,0))
            val dialog=checkNotNull(Minecraft.getInstance().screen);check(dialog!==site)
            dialog.children().filterIsInstance<EditorTextField>().single().value=value
            check(dialog.keyPressed(257,0,0));check(Minecraft.getInstance().screen===site)
        }
        site.selectPart("in:1",0);number("Y","1");check(node("building").sitePaths.getValue(1).first().y==1)
        site.keyPressed(90,0,2);restored()
        site.selectPart("in:0");number("X","4");number("Z","-3");check(node("building").offsets[0]==P(4,0,-3))
        site.keyPressed(90,0,2);site.keyPressed(90,0,2);restored()
    }
    private fun advanceLandscape(mc:Minecraft,elapsed:Int):Boolean {
        when(elapsed) {
            5 -> { section(BuildingPanelSection.LANDSCAPE);landscapeProperties() }
            10 -> openSite()
            15 -> screenshot(mc,"building-site-normal")
            20 -> startSiteDrag("in:0")
            25 -> screenshot(mc,"building-site-garden-drag-normal")
            30 -> finishSiteDrag()
            35 -> startSiteDrag(BuildingSite.BODY)
            40 -> finishSiteDrag()
            45 -> startSiteDrag("in:1",0)
            50 -> finishSiteDrag()
            55 -> { sitePointActions();startSiteDrag("in:0",cancel=true) }
            60 -> { site().onClose();mc.options.guiScale().set(2);mc.resizeDisplay();building.openBuildingSection(BuildingPanelSection.LANDSCAPE) }
            65 -> landscapeProperties()
            70 -> openSite()
            75 -> screenshot(mc,"building-site-compact")
            80 -> startSiteDrag("in:0")
            85 -> screenshot(mc,"building-site-garden-drag-compact")
            90 -> finishSiteDrag()
            95 -> startSiteDrag(BuildingSite.BODY)
            100 -> finishSiteDrag()
            105 -> startSiteDrag("in:1",0)
            110 -> finishSiteDrag()
            115 -> { sitePointActions();startSiteDrag("in:0",cancel=true) }
            120 -> {
                site().onClose();restored();layout();CreativeBuildScreenSmoke.verify(mc);ClientState.snapshot=previousSnapshot
                mc.options.guiScale().set(previousScale);mc.resizeDisplay();mc.setScreen(previous)
                return true
            }
        }
        return false
    }
    private fun advance(mc:Minecraft,elapsed:Int):Boolean {
        check(mc.screen===building)
        when(elapsed) {
            5 -> { layout();select("pattern_nested");screenshot(mc,"segments-normal") }
            10 -> changePatternAxis()
            12 -> copycatSourceAndMaterial()
            15 -> { section(BuildingPanelSection.ROOMS);select("room_annex") }
            20 -> screenshot(mc,"rooms-normal")
            25 -> { section(BuildingPanelSection.STOREYS);select("storey");canvas().fit() }
            30 -> { selectRepeatedInstance();screenshot(mc,"storeys-normal") }
            35 -> startPlacement()
            40 -> screenshot(mc,"storey-drag-preview")
            45 -> finishPlacement()
            50 -> rejectOverlap()
            55 -> properties()
            60 -> { screenshot(mc,"storey-properties-normal");action("panel_storey_layout");section(BuildingPanelSection.BUILDING) }
            65 -> screenshot(mc,"building-normal")
            70 -> section(BuildingPanelSection.ROOF)
            75 -> { screenshot(mc,"roof-normal");section(BuildingPanelSection.SEGMENTS) }
            80 -> { addFromPanel();action("node_graph") }
            85 -> beginNativeInsert()
            90 -> openNativeInsertBranch()
            95 -> { val point=checkNotNull(native().menuItemPoint(ui("node_category_copycats")));native().mouseMoved(point.first,point.second) }
            98 -> screenshot(mc,"graph-add-menu")
            100 -> finishNativeInsert()
            105 -> verifyNativeInsert()
            110 -> { restored();mc.options.guiScale().set(2);mc.resizeDisplay();building.openBuildingSection(BuildingPanelSection.SEGMENTS);select("pattern_nested") }
            115 -> screenshot(mc,"segments-compact")
            120 -> changePatternAxis()
            122 -> copycatSourceAndMaterial()
            125 -> { section(BuildingPanelSection.ROOMS);select("room_annex") }
            130 -> screenshot(mc,"rooms-compact")
            135 -> { section(BuildingPanelSection.STOREYS);select("storey");canvas().fit() }
            140 -> { selectRepeatedInstance();screenshot(mc,"storeys-compact") }
            145 -> startPlacement()
            150 -> finishPlacement()
            155 -> rejectOverlap()
            160 -> properties()
            165 -> screenshot(mc,"storey-properties-compact")
            170 -> { action("panel_storey_layout");section(BuildingPanelSection.BUILDING) }
            175 -> screenshot(mc,"building-compact")
            180 -> section(BuildingPanelSection.ROOF)
            185 -> screenshot(mc,"roof-compact")
            190 -> {
                restored();layout()
                ClientState.snapshot=previousSnapshot
                mc.options.guiScale().set(previousScale);mc.resizeDisplay();mc.setScreen(previous)
                return true
            }
        }
        return false
    }
}
