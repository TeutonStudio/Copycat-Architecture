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

/** Real screen events verify the table's shared printer frame at both supported GUI scales. */
internal object BuildingWorkspaceSmoke {
    private var ticks=0
    private var failed=false
    private var previous:Screen?=null
    private var previousScale=1
    private lateinit var previousSnapshot:Snapshot
    private lateinit var screen:BuildingScreen
    private var original=""
    private var edited=""
    private var classic=""
    private var classicHistory=false to false
    private var copycatScreen:ShapeScreen?=null
    private var copycatDraft=""
    private var copycatBuilding=""
    private var copycatSource=""
    private fun copycatControl(key:String) = checkNotNull(copycatScreen).children().filterIsInstance<Button>().first { it.visible && it.message.string==ui(key) }
    private fun copycatClick(point:Pair<Double,Double>) {
        val shape=checkNotNull(copycatScreen)
        check(shape.mouseClicked(point.first,point.second,0));shape.mouseReleased(point.first,point.second,0)
    }
    private fun copycatAction(key:String) { val b=copycatControl(key);check(b.active);copycatClick(b.x+b.width/2.0 to b.y+b.height/2.0) }
    private fun toggleMaterialPreview(enabled:Boolean) {
        val shape=checkNotNull(copycatScreen)
        val before=Json.encode(shape.form);val materials=shape.printerMaterials.toMap()
        val history=copycatControl("undo").active to copycatControl("redo").active
        copycatAction("material_preview")
        check(shape.materialPreview==enabled && Json.encode(shape.form)==before && shape.printerMaterials==materials)
        check((copycatControl("undo").active to copycatControl("redo").active)==history) {
            "Material preview must only change the view, preserving assignments and both history stacks"
        }
    }
    private fun verifyMaterialPreview(mc:Minecraft,enabled:Boolean) {
        val shape=checkNotNull(copycatScreen);check(shape.materialPreview==enabled)
        val output=shape.shapeDisplayGraph().nodes.single { it.kind==NodeKind.OUTPUT }.id
        val viewport=checkNotNull(shape.kilaGraph!!.nodePreviewViewport(output))
        Screenshot.takeScreenshot(mc.mainRenderTarget).use { pixels ->
            val sx=pixels.width.toDouble()/shape.width;val sy=pixels.height.toDouble()/shape.height
            var gold=0;var neutral=0
            val left=((viewport.x+viewport.width*0.15)*sx).toInt().coerceIn(0,pixels.width-1)
            val right=((viewport.x+viewport.width*0.85)*sx).toInt().coerceIn(left+1,pixels.width)
            val top=((viewport.y+viewport.height*0.15)*sy).toInt().coerceIn(0,pixels.height-1)
            val bottom=((viewport.y+viewport.height*0.85)*sy).toInt().coerceIn(top+1,pixels.height)
            for(y in top until bottom) for(x in left until right) {
                val color=pixels.getPixelRGBA(x,y);val r=color and 255;val g=color ushr 8 and 255;val b=color ushr 16 and 255
                if(r>100 && g>80 && b<r*0.7) gold++
                if(minOf(r,g,b)>50 && maxOf(r,g,b)-minOf(r,g,b)<=10) neutral++
            }
            check(if(enabled) gold>0 else gold==0 && neutral>0) {
                "The actual Copycat result must render ${if(enabled) "assigned gold" else "neutral untextured geometry"}: gold=$gold, neutral=$neutral, viewport=$viewport"
            }
        }
    }
    private fun copycatLayout() {
        val shape=checkNotNull(copycatScreen)
        check(Minecraft.getInstance().screen===shape && shape.graphMode)
        shape.children().filterIsInstance<AbstractWidget>().filter { it.visible }.forEach {
            check(it.x>=0 && it.y>=0 && it.x+it.width<=shape.width && it.y+it.height<=shape.height) { "Copycat workspace widget outside screen: ${it.message.string}" }
        }
        checkNotNull(shape.kilaGraph)
        check(document()==copycatBuilding) { "Opening the Copycat workspace must preserve the building" }
    }

    fun tick():Boolean {
        if(failed) return false
        val mc=Minecraft.getInstance()
        if(ticks==0 && (mc.screen==null || mc.overlay!=null)) return false
        ticks++
        try {
            if(ticks==1) begin(mc)
            else if(advance(mc,ticks-1)) {
                println("ARCHITECT BUILDING WORKSPACE UI SMOKE PASSED: top Graph overview tab using the same native CC sprite as both side rails, shared printer frame, full-width graph and toggled inspector, normal/compact section and floor-plan navigation, document menu, module/material/build views, widget bounds, lossless document navigation, shared undo/redo and actual classic document graph/control roundtrips verified")
                mc.stop()
            }
        } catch(error:Throwable) {
            failed=true
            System.err.println("ARCHITECT BUILDING WORKSPACE UI SMOKE FAILED at tick $ticks: ${error.message}")
            screenshot(mc,"failure-$ticks")
            throw IllegalStateException("Building workspace UI smoke failed at tick $ticks",error)
        }
        return true
    }

    private fun begin(mc:Minecraft) {
        previous=mc.screen;previousScale=mc.options.guiScale().get();previousSnapshot=ClientState.snapshot
        ClientState.snapshot=Snapshot(library=Library().apply { seed() })
        mc.options.guiScale().set(1);mc.resizeDisplay()
        screen=BuildingScreen().apply {
            building=Building(schema=2,id="building_workspace_smoke",name="Architektentisch",graph=BuildingGraph.assembledStarter().let { it.copy(nodes=it.nodes+listOf(BuildingAssembly.newNode(BuildingKind.GARDEN,"garden"),BuildingAssembly.newNode(BuildingKind.PATH,"path"))) })
            buildingGraphView.selected="room"
        }
        original=document()
        mc.setScreen(screen)
        screen.openBuildingGraph()
    }

    private fun document()=Json.encode(screen.building)
    private fun control(key:String)=screen.children().filterIsInstance<Button>()
        .filter { it.visible && it.message.string==ui(key) }.minByOrNull { it.y }
        ?: error("Visible workspace control missing: $key")
    private fun history()=control("undo").active to control("redo").active
    private fun action(key:String) {
        val button=control(key)
        check(button.active) { "Workspace control must be active: $key" }
        click(button.x+button.width/2.0 to button.y+button.height/2.0)
    }
    private fun click(point:Pair<Double,Double>) {
        check(point.first>=0 && point.first<screen.width && point.second>=0 && point.second<screen.height) {
            "Workspace click must fit ${screen.width}x${screen.height}: $point"
        }
        check(screen.mouseClicked(point.first,point.second,0)) { "The actual building screen must receive the click" }
        screen.mouseReleased(point.first,point.second,0)
    }
    private fun navigate(key:String) {
        val before=document();val history=history()
        action(key)
        check(document()==before && history()==history) { "Changing workspace view must preserve the document and undo/redo: $key" }
        layout()
    }
    private fun section(section:BuildingPanelSection) {
        navigate(section.key)
        check(screen.tab==7 && screen.graphMode && screen.buildingPanelSection==section)
        val list=checkNotNull(screen.buildingDefinitionList)
        check(list.section==section)
        val definitions=screen.buildingDisplayGraph().nodes.filter { it.kind in section.kinds }
        check(if(definitions.isEmpty()) screen.buildingGraphView.selected.isEmpty() else definitions.any { it.id==screen.buildingGraphView.selected }) {
            "A populated section must select one of its definitions; an empty section must clear selection"
        }
    }
    private fun layout() {
        screen.children().filterIsInstance<AbstractWidget>().filter { it.visible }.forEach { widget ->
            check(widget.x>=0 && widget.y>=0 && widget.x+widget.width<=screen.width && widget.y+widget.height<=screen.height) {
                "Visible workspace widget extends beyond ${screen.width}x${screen.height}: ${widget.message.string} at ${widget.x},${widget.y} ${widget.width}x${widget.height}"
            }
        }
        val l=PrinterWorkspaceLayout(screen.width,screen.height,topTabs=true)
        val top=screen.children().filterIsInstance<ArchitectTopTab>().filter { it.visible }
        check(top.size==1 && top.single().message.string==ui("graph_overview") && top.single().let { it.width==22 && it.height==17 && it.y+it.height==l.top })
        check(screen.children().filterIsInstance<PrinterEdgeTab>().count { it.visible }>=6) {
            "Table navigation must use the shared printer edge tabs"
        }
        check(screen.children().filterIsInstance<PrinterEyeButton>().single().let { it.x>=l.x && it.y==l.y }) {
            "The world-preview eye must share the workspace toolbar"
        }
    }
    private fun graph(panel:Boolean) {
        layout()
        check(screen.graphMode && screen.tab==0 && screen.graphInspector==panel)
        val l=PrinterWorkspaceLayout(screen.width,screen.height,topTabs=true)
        val graph=checkNotNull(screen.kilaBuildingGraph)
        check(graph.x==l.x && graph.y==l.graphY && graph.width==l.graphWidth(panel) && graph.height==l.graphHeight) {
            "The native building graph must use the shared printer workspace, including its inspector split"
        }
        val inspector=screen.buildingGraphCanvas
        if(panel) {
            checkNotNull(inspector)
            check(inspector.visible && screen.children().any { it===inspector })
            check(inspector.x==l.panelX && inspector.y==l.graphY && inspector.width==l.panelWidth && inspector.height==l.graphHeight)
        } else check(screen.children().filterIsInstance<AbstractWidget>().none { it===inspector && it.visible }) {
            "Closing the inspector must return the complete graph area"
        }
    }
    private fun toggleInspector(open:Boolean) {
        navigate("printer_inspector")
        graph(open)
    }
    private fun addDefinition() {
        check(document()==original)
        section(BuildingPanelSection.SEGMENTS)
        val before=screen.buildingDisplayGraph()
        action("panel_add_definition")
        check(screen.keyPressed(268,0,0))
        check(screen.keyPressed(257,0,0))
        val after=screen.buildingDisplayGraph()
        check(after.nodes.size==before.nodes.size+1 && after.nodes.any { node -> before.nodes.none { it.id==node.id } }) {
            "The actual definition menu must add exactly one graph definition"
        }
        edited=document();check(edited!=original && history()==(true to false))
    }
    private fun undoRedo() {
        check(document()==edited)
        action("undo");check(document()==original && history()==(false to true))
        action("redo");check(document()==edited && history()==(true to false))
        action("undo");check(document()==original && history()==(false to true))
    }
    private fun documentMenu() {
        val before=document();val history=history()
        action("document")
        check(screen.keyPressed(256,0,0) && Minecraft.getInstance().screen===screen) {
            "Escape must close the document menu while retaining the table workspace"
        }
        check(document()==before && history()==history) { "Opening and closing the document menu must preserve the draft and undo/redo" }
    }
    private fun newClassicDocument() {
        check(document()==original && history()==(false to true))
        action("document")
        // New classic building is the first document command, preceding New graph document.
        check(screen.keyPressed(268,0,0))
        check(screen.keyPressed(257,0,0))
        check(screen.building.graph==null && screen.building.name==ui("new_classic_building") && !screen.graphMode) {
            "The actual document menu must create a classic building and display its controls"
        }
        check(document()!=original)
        classic=document();classicHistory=history()
        layout()
    }
    private fun classicNavigate(key:String,native:Boolean) {
        val l=PrinterWorkspaceLayout(screen.width,screen.height,topTabs=true)
        val tab=control(key)
        check(tab is PrinterEdgeTab && tab.x==if(key=="footprint") l.left-17 else l.left+l.width) {
            "Classic footprint navigation must use the left rail and graph/control navigation the right rail"
        }
        navigate(key)
        check(screen.building.graph==null && document()==classic && history()==classicHistory) {
            "Actual classic workspace navigation must preserve the complete document and its undo/redo without conversion"
        }
        check(screen.graphMode==native && screen.tab==0)
        if(native) checkNotNull(screen.kilaBuildingGraph)
        else check(screen.kilaBuildingGraph==null)
    }
    private fun screenshot(mc:Minecraft,name:String) {
        if(!failed && mc.screen===screen) layout()
        Screenshot.grab(mc.gameDirectory,"architect-building-workspace-$name.png",mc.mainRenderTarget) { println(it.string) }
    }
    private fun advance(mc:Minecraft,elapsed:Int):Boolean {
        check(mc.screen===screen || mc.screen===copycatScreen)
        when(elapsed) {
            2 -> action("fit_graph")
            5 -> { navigate("graph_overview");graph(false);screenshot(mc,"graph-normal") }
            10 -> toggleInspector(true)
            15 -> screenshot(mc,"inspector-normal")
            20 -> toggleInspector(false)
            25 -> { navigate("control_panel");section(BuildingPanelSection.SEGMENTS) }
            30 -> addDefinition()
            35 -> { navigate("node_graph");graph(false);check(document()==edited) }
            40 -> undoRedo()
            45 -> { navigate("control_panel");section(BuildingPanelSection.ROOMS) }
            50 -> screenshot(mc,"panel-normal")
            55 -> section(BuildingPanelSection.STOREYS)
            60 -> section(BuildingPanelSection.BUILDING)
            65 -> section(BuildingPanelSection.ROOF)
            70 -> { navigate("footprint");checkNotNull(screen.floorPlanCanvas).fit() }
            75 -> screenshot(mc,"floorplan-normal")
            80 -> navigate("modules")
            85 -> navigate("palette")
            90 -> navigate("build")
            95 -> screenshot(mc,"build-normal")
            100 -> documentMenu()
            105 -> { navigate("node_graph");graph(false) }
            110 -> { mc.options.guiScale().set(2);mc.resizeDisplay() }
            112 -> action("fit_graph")
            115 -> { navigate("graph_overview");graph(false);screenshot(mc,"graph-compact") }
            120 -> toggleInspector(true)
            125 -> screenshot(mc,"inspector-compact")
            130 -> { toggleInspector(false);navigate("control_panel");section(BuildingPanelSection.ROOMS) }
            135 -> screenshot(mc,"panel-compact")
            140 -> BuildingPanelSection.entries.forEach(::section)
            145 -> { navigate("footprint");checkNotNull(screen.floorPlanCanvas).fit() }
            150 -> screenshot(mc,"floorplan-compact")
            155 -> navigate("modules")
            160 -> navigate("palette")
            165 -> navigate("build")
            170 -> screenshot(mc,"build-compact")
            175 -> documentMenu()
            180 -> { navigate("node_graph");graph(false);check(document()==original && history()==(false to true)) }
            185 -> newClassicDocument()
            190 -> classicNavigate("footprint",false)
            195 -> { classicNavigate("node_graph",true);action("fit_graph") }
            200 -> screenshot(mc,"classic-graph-compact")
            205 -> classicNavigate("control_panel",false)
            210 -> screenshot(mc,"classic-controls-compact")
            215 -> classicNavigate("node_graph",true)
            220 -> classicNavigate("footprint",false)
            225 -> {
                mc.options.guiScale().set(1);mc.resizeDisplay()
                copycatBuilding=document();action("copycat_graph")
                copycatScreen=mc.screen as ShapeScreen
                check(!checkNotNull(copycatScreen).materialPreview) { "Table Copycat graphs must start without materials" }
                checkNotNull(copycatScreen).form.groups.forEach { checkNotNull(copycatScreen).printerMaterials[it.id]="minecraft:gold_block" }
            }
            230 -> {
                copycatLayout();val shape=checkNotNull(copycatScreen)
                copycatSource=shape.shapeDisplayGraph().nodes.first { it.kind==NodeKind.INPUT }.id
                copycatClick(checkNotNull(shape.kilaGraph!!.nodeHeaderPoint(copycatSource)))
            }
            235 -> { copycatAction("printer_inspector");copycatLayout() }
            240 -> {
                val shape=checkNotNull(copycatScreen);val before=Json.encode(shape.form)
                copycatClick(checkNotNull(shape.graphCanvas!!.rotationButtonPoint(RotationAxis.NORMAL,1)))
                check(Json.encode(shape.form)!=before) { "Every Copycat source must allow inspector rotation" }
                val rotated=Json.encode(shape.form)
                copycatAction("undo");check(Json.encode(shape.form)==before)
                copycatAction("redo");check(Json.encode(shape.form)==rotated)
                val sourceIds=shape.shapeDisplayGraph().nodes.map { it.id }.toSet()
                shape.graphCanvas!!.addAt(NodeKind.INPUT,"copycats:copycat_byte",780,100)
                val added=shape.shapeDisplayGraph().nodes.single { it.id !in sourceIds }.id
                check(shape.kilaGraphState.selection==setOf(added) && shape.graphView.selected==added) { "Table insertion must select the same new source in graph and inspector" }
                copycatAction("undo");check(Json.encode(shape.form)==rotated)
                copycatDraft=rotated
            }
            241 -> checkNotNull(copycatScreen).kilaGraph!!.fit()
            245 -> { copycatLayout();verifyMaterialPreview(mc,false);Screenshot.grab(mc.gameDirectory,"architect-table-copycat-normal.png",mc.mainRenderTarget) {} }
            246 -> toggleMaterialPreview(true)
            248 -> { verifyMaterialPreview(mc,true);Screenshot.grab(mc.gameDirectory,"architect-table-copycat-material-normal.png",mc.mainRenderTarget) {} }
            250 -> { copycatAction("node_graph");check(mc.screen===screen);action("copycat_graph");check(mc.screen===copycatScreen) }
            255 -> { check(checkNotNull(copycatScreen).materialPreview);mc.options.guiScale().set(2);mc.resizeDisplay();checkNotNull(copycatScreen).kilaGraph!!.fit() }
            260 -> {
                copycatLayout();check(Json.encode(checkNotNull(copycatScreen).form)==copycatDraft)
                verifyMaterialPreview(mc,true)
                Screenshot.grab(mc.gameDirectory,"architect-table-copycat-material-compact.png",mc.mainRenderTarget) {}
            }
            261 -> toggleMaterialPreview(false)
            263 -> { verifyMaterialPreview(mc,false);Screenshot.grab(mc.gameDirectory,"architect-table-copycat-compact.png",mc.mainRenderTarget) {} }
            265 -> { copycatAction("graph_overview");check(mc.screen===screen);check(document()==copycatBuilding);graph(false) }
            270 -> {
                ClientState.snapshot=previousSnapshot
                mc.options.guiScale().set(previousScale);mc.resizeDisplay();mc.setScreen(previous)
                return true
            }
        }
        return false
    }
}
