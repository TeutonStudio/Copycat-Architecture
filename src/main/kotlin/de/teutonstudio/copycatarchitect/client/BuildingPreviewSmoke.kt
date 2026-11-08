package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.Json
import de.teutonstudio.copycatarchitect.server.Library
import de.teutonstudio.copycatarchitect.server.Snapshot
import net.minecraft.client.MouseHandler
import org.lwjgl.glfw.GLFW
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen

/** Real table widgets verify local 2D/3D views without touching the building or its edit history. */
internal object BuildingPreviewSmoke {
    private var ticks=0
    private var failed=false
    private var previous:Screen?=null
    private var previousScale=1
    private lateinit var previousSnapshot:Snapshot
    private lateinit var screen:BuildingScreen
    private var original=""
    private var originalHistory=false to false
    private var buildingYaw=0f
    private var segmentId=""
    private var roomId=""
    private var storeyId=""
    private var roofId=""
    private var pendingRoofId=""
    private val mirrorId="preview_mirror"
    private val yaw=mutableMapOf<String,Float>()
    private var previewSection="3d"
    private var sharedSections=mapOf("properties" to true,"2d" to false,"3d" to true)
    private val plans=mutableMapOf<String,Plan>()

    fun tick():Boolean {
        if(failed) return false
        val mc=Minecraft.getInstance()
        if(ticks==0 && (mc.screen==null || mc.overlay!=null)) return false
        ticks++
        var acted=true
        try {
            when(ticks) {
                1 -> begin(mc)
                10 -> select(BuildingPanelSection.SEGMENTS,segmentId)
                15 -> mode("2d")
                20 -> screenshot(mc,"segment-2d-normal")
                25 -> { mode("3d");orbit() }
                30 -> screenshot(mc,"segment-3d-normal")
                35 -> select(BuildingPanelSection.ROOMS,roomId)
                40 -> mode("2d")
                45 -> screenshot(mc,"room-2d-normal")
                50 -> { mode("3d");orbit() }
                55 -> screenshot(mc,"room-3d-normal")
                60 -> select(BuildingPanelSection.ROOF,roofId)
                65 -> mode("2d")
                70 -> screenshot(mc,"roof-2d-normal")
                75 -> { mode("3d");orbit() }
                80 -> screenshot(mc,"roof-3d-normal")
                85 -> select(BuildingPanelSection.ROOF,pendingRoofId)
                90 -> mode("2d")
                95 -> screenshot(mc,"roof-pending-2d-normal")
                100 -> mode("3d")
                105 -> screenshot(mc,"roof-pending-3d-normal")
                110 -> {
                    unchanged();mc.options.guiScale().set(2);mc.resizeDisplay()
                    check(screen.width in 400..499 && screen.height in 230..299) { "Compact previews require the 427 x 240 GUI viewport" }
                    unchanged()
                }
                115 -> select(BuildingPanelSection.SEGMENTS,segmentId)
                120 -> { properties();mode("2d") }
                125 -> screenshot(mc,"segment-2d-compact")
                130 -> { mode("3d");orbit() }
                135 -> screenshot(mc,"segment-3d-compact")
                140 -> select(BuildingPanelSection.ROOMS,roomId)
                145 -> { properties();mode("2d") }
                150 -> screenshot(mc,"room-2d-compact")
                155 -> { mode("3d");orbit() }
                160 -> screenshot(mc,"room-3d-compact")
                165 -> select(BuildingPanelSection.ROOF,roofId)
                170 -> { properties();mode("2d") }
                175 -> screenshot(mc,"roof-2d-compact")
                180 -> { mode("3d");orbit() }
                185 -> screenshot(mc,"roof-3d-compact")
                190 -> select(BuildingPanelSection.ROOF,pendingRoofId)
                195 -> { properties();mode("2d") }
                200 -> screenshot(mc,"roof-pending-2d-compact")
                205 -> mode("3d")
                210 -> screenshot(mc,"roof-pending-3d-compact")
                215 -> select(BuildingPanelSection.SEGMENTS,segmentId)
                220 -> select(BuildingPanelSection.ROOMS,roomId)
                225 -> select(BuildingPanelSection.ROOF,roofId)
                230 -> storey3d()
                235 -> orbit()
                240 -> screenshot(mc,"storey-3d-compact")
                245 -> mode("2d")
                250 -> screenshot(mc,"storey-2d-compact")
                255 -> {
                    unchanged();action("panel_storey_layout")
                    check(screen.floorPlanCanvas!=null && !canvas().visible) { "The storey layout must remain accessible after its local preview" }
                    action("panel_storey_properties")
                    check(canvas().visible && canvas().sectionExpanded("properties")) { "The storey properties action must restore editable parameters after a 3D preview" }
                    previewSection="properties";sharedSections=sharedSections+("properties" to true)
                    action("panel_storey_layout")
                    unchanged()
                }
                260 -> resize(mc,1)
                265 -> storey3d()
                270 -> orbit()
                275 -> screenshot(mc,"storey-3d-normal")
                280 -> mode("2d")
                285 -> screenshot(mc,"storey-2d-normal")
                290 -> select(BuildingPanelSection.SEGMENTS,segmentId)
                295 -> select(BuildingPanelSection.STOREYS,storeyId)
                300 -> mode("3d")
                305 -> select(BuildingPanelSection.ROOMS,roomId)
                310 -> mode("2d")
                315 -> select(BuildingPanelSection.SEGMENTS,segmentId)
                320 -> resize(mc,2)
                325 -> select(BuildingPanelSection.ROOMS,roomId)
                330 -> properties()
                335 -> select(BuildingPanelSection.SEGMENTS,segmentId)
                340 -> select(BuildingPanelSection.ROOF,roofId)
                345 -> resize(mc,1)
                350 -> select(BuildingPanelSection.ROOMS,roomId)
                355 -> resize(mc,2)
                360 -> select(BuildingPanelSection.SEGMENTS,segmentId)
                365 -> mode("3d")
                370 -> select(BuildingPanelSection.ROOMS,roomId)
                375 -> resize(mc,1)
                380 -> select(BuildingPanelSection.STOREYS,storeyId)
                385 -> select(BuildingPanelSection.SEGMENTS,mirrorId)
                390 -> { localPlan();screenshot(mc,"mirror-3d-normal") }
                395 -> mode("2d")
                400 -> screenshot(mc,"mirror-2d-normal")
                405 -> resize(mc,2)
                410 -> properties()
                415 -> mirrorAxisEdit()
                420 -> mode("3d")
                425 -> screenshot(mc,"mirror-3d-compact")
                430 -> { resize(mc,1);screen.openBuildingGraph(false) }
                435 -> {
                    unchanged();val editor=checkNotNull(screen.kilaBuildingGraph)
                    editor.fit();click(editor.x+editor.width-8.0 to editor.y+editor.height-8.0,1)
                    check(editor.hasOpenMenu())
                }
                440 -> {
                    val editor=checkNotNull(screen.kilaBuildingGraph)
                    val point=checkNotNull(editor.menuItemPoint(ui("add_node")))
                    editor.mouseMoved(point.first,point.second)
                    val rawX=point.first*mc.window.screenWidth/screen.width
                    val rawY=point.second*mc.window.screenHeight/screen.height
                    GLFW.glfwSetCursorPos(mc.window.window,rawX,rawY)
                    MouseHandler::class.java.getDeclaredMethod("onMove",java.lang.Long.TYPE,java.lang.Double.TYPE,java.lang.Double.TYPE)
                        .apply { isAccessible=true }.invoke(mc.mouseHandler,mc.window.window,rawX,rawY)
                }
                443 -> {
                    val editor=checkNotNull(screen.kilaBuildingGraph)
                    val point=checkNotNull(editor.menuItemPoint(ui("node_category_segments")))
                    editor.mouseMoved(point.first,point.second)
                    val rawX=point.first*mc.window.screenWidth/screen.width
                    val rawY=point.second*mc.window.screenHeight/screen.height
                    GLFW.glfwSetCursorPos(mc.window.window,rawX,rawY)
                    MouseHandler::class.java.getDeclaredMethod("onMove",java.lang.Long.TYPE,java.lang.Double.TYPE,java.lang.Double.TYPE)
                        .apply { isAccessible=true }.invoke(mc.mouseHandler,mc.window.window,rawX,rawY)
                }
                445 -> click(checkNotNull(screen.kilaBuildingGraph?.menuItemPoint(ui("bg_segment_mirror"))))
                450 -> {
                    val originalGraph=checkNotNull(Json.decode<Building>(original).graph)
                    val added=checkNotNull(screen.building.graph).nodes.single { n -> originalGraph.nodes.none { it.id==n.id } }
                    check(added.kind==BuildingKind.SEGMENT_MIRROR && added.inputs==listOf("") && added.rotationAxis==RotationAxis.X)
                    val inserted=document();action("undo");unchanged();action("redo");check(document()==inserted);action("undo");unchanged()
                }
                455 -> {
                    unchanged()
                    ClientState.snapshot=previousSnapshot
                    mc.options.guiScale().set(previousScale);mc.resizeDisplay();mc.setScreen(previous)
                    println("ARCHITECT BUILDING PREVIEW UI SMOKE PASSED: actual segment/room/storey/roof section and list selection, isolated local plans despite an incomplete house, direct storey 3D access and layout return, shared 2D/3D/properties selection retained across previously visited nodes and normal/compact GUI resizes, bounded viewports, independent persistent right-drag orbit angles, unchanged building JSON and Undo/Redo, explicit pending-roof states, mirrored segment 2D/3D previews, real axis selection with Undo/Redo, and native mirror-node insertion verified")
                    mc.stop()
                }
                else -> acted=false
            }
        } catch(error:Throwable) {
            failed=true
            System.err.println("ARCHITECT BUILDING PREVIEW UI SMOKE FAILED at tick $ticks: ${error.message}")
            screenshot(mc,"failure-$ticks")
            throw IllegalStateException("Building preview UI smoke failed at tick $ticks",error)
        }
        return acted
    }

    private fun begin(mc:Minecraft) {
        previous=mc.screen;previousScale=mc.options.guiScale().get();previousSnapshot=ClientState.snapshot
        ClientState.snapshot=Snapshot(library=Library().apply { seed() })
        mc.options.guiScale().set(1);mc.resizeDisplay()
        val initial=BuildingGraph.explicitStarter(joinWallEnds=false)
        val legacy=BuildingGraph.assembledStarter()
        val wall=initial.nodes.first { it.kind==BuildingKind.SEGMENT && it.assembly && it.id!="empty_wall" }
        val volumeGeometry=initial.nodes.single { it.id==wall.layout }.copy(id="preview_volume_geometry",depth=3)
        val accent=initial.nodes.single { it.id==wall.inputs.first() }.copy(id="preview_volume_accent",role="frame")
        val volume=wall.copy(id="preview_volume",layout=volumeGeometry.id,regionCopies=mapOf(
            "region:xi_yi_z1" to accent.id,"region:x1_y1_z1" to accent.id))
        segmentId=volume.id
        roomId=initial.nodes.first { it.kind==BuildingKind.ROOM }.id
        storeyId=initial.nodes.single { it.kind==BuildingKind.STOREY }.id
        pendingRoofId=initial.nodes.single { it.kind==BuildingKind.ROOF }.id
        val roof=legacy.nodes.single { it.kind==BuildingKind.ROOF }.copy(id="preview_legacy_roof",name="Giebeldach",inputs=listOf("plate_copycat"))
        roofId=roof.id
        val result=initial.nodes.single { it.kind==BuildingKind.BUILDING }
        val storey=initial.nodes.single { it.id==storeyId }.copy(inputs=listOf(roomId,roomId),offsets=listOf(P(),P(7,0,0)))
        val base=initial.replace(result.copy(levels=emptyMap())).replace(storey)
        val mirror=BuildingAssembly.newNode(BuildingKind.SEGMENT_MIRROR,mirrorId,"Gespiegeltes Segment",620,940).copy(inputs=listOf(volume.id))
        val graph=base.copy(nodes=base.nodes+listOf(volumeGeometry,accent,volume,legacy.nodes.single { it.id=="gable_wall" },roof,mirror))
        BuildingGraphCompiler.structure(graph)
        screen=BuildingScreen().apply {
            building=Building(schema=2,id="building_preview_smoke",name="Lokale Vorschauen",graph=graph)
            buildingGraphView.selected=segmentId
        }
        mc.setScreen(screen);screen.openBuildingSection(BuildingPanelSection.SEGMENTS)
        check(screen.width==854 && screen.height==480) { "Building preview acceptance requires the configured 854 x 480 window" }
        // Both stacks contain existing work, so presentation controls must preserve a usable shared history.
        val selected=graph.nodes.single { it.id==segmentId }
        screen.commitBuildingGraph(graph.replace(selected.copy(name="Segmentvorschau")))
        screen.commitBuildingGraph(checkNotNull(screen.building.graph).replace(selected.copy(name="Segmentvorschau bearbeitet")))
        action("undo")
        original=document();originalHistory=history();check(originalHistory==(true to true))
        buildingYaw=screen.buildingGraphView.yaw
        val lib=ClientState.snapshot.library
        val evaluation=BuildingGraphCompiler.Evaluation(checkNotNull(screen.building.graph),screen.building.palette,lib.forms,lib.modules)
        check(runCatching { evaluation.building() }.isFailure) { "The fixture's house must be incomplete while its local definitions remain previewable" }
        for(id in listOf(segmentId,roomId,storeyId,roofId)) plans[id]=evaluation.preview(id).also {
            check(it.placements.isNotEmpty()) { "Each independent preview fixture must contain actual geometry" }
        }
        val volumePlan=plans.getValue(segmentId)
        check(evaluation.segment(segmentId).depth==3 && volumePlan.placements.map { it.pos.z }.toSet()==setOf(0,1,2)) { "The segment's actual local preview must span all three depth layers" }
        val regionPorts=BuildingPorts.of(graph.nodes.single { it.id==segmentId },graph).filter { it.key.startsWith("region:") }
        check(regionPorts.size==27 && regionPorts.map { it.key }.distinct().size==27) { "A 3 x 4 x 3 segment must expose 27 distinct interior, face, edge and corner connections" }
        check(volumePlan.placements.count { it.material==screen.building.palette.getValue("frame") }==3) { "The preview must apply the face and corner Copycat assignments to their own volume cells" }
        check(plans.values.distinct().size==4) { "The selected segment, room, storey and roof must have different local geometry" }
        check(plans.getValue(storeyId).placements.size==plans.getValue(roomId).placements.size*2) { "The storey preview must include both positioned room instances" }
        plans[mirrorId]=evaluation.preview(mirrorId).also { mirrored ->
            check(mirrored.placements.isNotEmpty() && mirrored!=volumePlan) { "The asymmetric segment mirror fixture must produce distinct real geometry" }
            check(mirrored.raw==volumePlan.raw && mirrored.materials==volumePlan.materials) { "Reflection must preserve material and zinc costs" }
        }
        check(runCatching { evaluation.preview(pendingRoofId) }.isFailure) { "Undefined explicit roof arrangements must remain pending" }
    }

    private fun mirrorAxisEdit() {
        unchanged()
        val inspector=canvas();val point=inspector.propertyPoint(mirrorId,20)
        val target=point.second.coerceIn(inspector.y+70.0,inspector.y+inspector.height-18.0)
        if(target!=point.second) check(screen.mouseScrolled(inspector.x+inspector.width/2.0,inspector.y+inspector.height-8.0,0.0,(target-point.second)/25.0))
        click(inspector.propertyPoint(mirrorId,20))
        check(canvas().hasOpenMenu()) { "The mirror axis must use the real inspector dropdown" }
        check(screen.keyPressed(264,0,0));check(screen.keyPressed(257,0,0))
        check(checkNotNull(screen.building.graph).nodes.single { it.id==mirrorId }.rotationAxis==RotationAxis.Y)
        check(document()!=original)
        val lib=ClientState.snapshot.library
        val changed=BuildingGraphCompiler.Evaluation(checkNotNull(screen.building.graph),screen.building.palette,lib.forms,lib.modules).preview(mirrorId)
        check(changed!=plans.getValue(mirrorId) && changed.raw==plans.getValue(mirrorId).raw)
        val edited=document();action("undo");unchanged();action("redo");check(document()==edited);action("undo");unchanged()
    }

    private fun document()=Json.encode(screen.building)
    private fun canvas()=checkNotNull(screen.buildingGraphCanvas)
    private fun selected()=screen.buildingGraphView.selected
    private fun control(key:String)=screen.children().filterIsInstance<Button>().first { it.visible && it.message.string==ui(key) }
    private fun history()=control("undo").active to control("redo").active
    private fun unchanged() {
        check(document()==original && history()==originalHistory) { "Preview actions must preserve the complete building JSON and both history stacks" }
        check(screen.buildingGraphView.yaw==buildingYaw) { "Orbiting one definition must preserve the house preview's camera" }
        check(sharedSections.all { (key,expanded) -> screen.buildingGraphView.inspectorSections.isExpanded(key)==expanded }) {
            "Inspector sections must retain their independent expansion state across selection and resize"
        }
    }
    private fun resize(mc:Minecraft,scale:Int) { unchanged();mc.options.guiScale().set(scale);mc.resizeDisplay();unchanged();layout() }
    private fun click(point:Pair<Double,Double>,button:Int=0) {
        check(point.first>=0 && point.first<screen.width && point.second>=0 && point.second<screen.height) {
            "Preview control must fit ${screen.width} x ${screen.height}: $point"
        }
        check(screen.mouseClicked(point.first,point.second,button)) { "The actual preview widget must receive the click" }
        screen.mouseReleased(point.first,point.second,button)
    }
    private fun action(key:String) {
        val button=control(key);check(button.active)
        click(button.x+button.width/2.0 to button.y+button.height/2.0)
    }
    private fun select(section:BuildingPanelSection,id:String) {
        unchanged();action(section.key)
        click(checkNotNull(screen.buildingDefinitionList).definitionPoint(id))
        check(screen.tab==7 && screen.buildingPanelSection==section && selected()==id && canvas().visible)
        yaw[id]?.let { check(canvas().previewYaw()==it) { "Changing another definition's camera must leave this definition's angle unchanged" } }
        layout();unchanged()
    }
    private fun storey3d() {
        unchanged();action(BuildingPanelSection.STOREYS.key)
        click(checkNotNull(screen.buildingDefinitionList).definitionPoint(storeyId))
        if(canvas().visible) action("panel_storey_layout")
        check(screen.floorPlanCanvas!=null && !canvas().visible) { "The direct 3D action must be exercised from the editable storey layout" }
        action("preview3d")
        check(selected()==storeyId && canvas().visible && canvas().sectionExpanded("3d")) { "The storey's own 3D button must open its complete local preview" }
        previewSection="3d";sharedSections=sharedSections+("3d" to true)
        yaw[storeyId]?.let { check(canvas().previewYaw()==it) { "Returning to the storey preview must preserve its own camera" } }
        yaw.getOrPut(storeyId) { canvas().previewYaw() }
        viewport();localPlan();layout();unchanged()
    }
    private fun layout() {
        screen.children().filterIsInstance<AbstractWidget>().filter { it.visible }.forEach {
            check(it.x>=0 && it.y>=0 && it.x+it.width<=screen.width && it.y+it.height<=screen.height) {
                "Visible preview widget extends beyond the client window: ${it.message.string}"
            }
        }
    }
    private fun viewport()=checkNotNull(canvas().previewViewport(previewSection)) { "The chosen local preview must expose its actual viewport" }.also {
        check(it.width>0 && it.height>0 && it.x>=canvas().x && it.y>=canvas().y &&
            it.x+it.width<=canvas().x+canvas().width && it.y+it.height<=canvas().y+canvas().height) {
            "The complete preview viewport must fit inside its normal or compact inspector"
        }
    }
    private fun localPlan() {
        val actual=canvas().previewPlan()
        if(selected()==pendingRoofId) check(actual.isFailure) { "An undefined roof arrangement must show a pending state in both preview modes" }
        else check(actual.getOrThrow()==plans.getValue(selected())) { "The preview must render only the selected local definition, independently of the incomplete house" }
    }
    private fun mode(value:String) {
        unchanged()
        if(!canvas().sectionExpanded(value)) click(canvas().sectionHeaderPoint(value))
        canvas().revealSection(value)
        check(canvas().sectionExpanded(value))
        previewSection=value;sharedSections=sharedSections+(value to true)
        yaw.getOrPut(selected()) { canvas().previewYaw() }
        viewport();localPlan();layout();unchanged()
    }
    private fun properties() {
        unchanged()
        if(!canvas().sectionExpanded("properties")) click(canvas().sectionHeaderPoint("properties"))
        canvas().revealSection("properties")
        previewSection="properties";sharedSections=sharedSections+("properties" to true)
        check(canvas().sectionExpanded("properties"))
        layout();unchanged()
    }
    private fun orbit() {
        check(canvas().sectionExpanded("3d"));canvas().revealSection("3d");previewSection="3d"
        unchanged();val before=canvas().previewYaw();val area=viewport()
        val x=area.x+area.width/2.0;val y=area.y+area.height/2.0;val dx=minOf(18.0,area.width/4.0)
        check(screen.mouseClicked(x,y,1)) { "Right clicking the actual 3D viewport must start a local orbit" }
        check(screen.mouseDragged(x+dx,y,1,dx,0.0)) { "The actual 3D viewport must receive the right drag" }
        screen.mouseReleased(x+dx,y,1)
        check(canvas().previewYaw()!=before) { "Right dragging must rotate the selected local definition" }
        yaw[selected()]=canvas().previewYaw()
        localPlan();unchanged()
    }
    private fun screenshot(mc:Minecraft,name:String) {
        Screenshot.grab(mc.gameDirectory,"architect-building-preview-$name.png",mc.mainRenderTarget) { println(it.string) }
    }
}
