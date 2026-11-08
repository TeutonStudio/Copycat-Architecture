package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.*
import net.minecraft.client.Minecraft
import net.minecraft.client.MouseHandler
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import org.lwjgl.glfw.GLFW
import java.math.BigInteger

/** Actual printer/table controls cover mirrors and building Copycat/segment rotations. */
internal object CopycatMirrorSmoke {
    private var ticks=0
    private var failed=false
    private var previous:Screen?=null
    private var previousScale=1
    private lateinit var previousSnapshot:Snapshot
    private lateinit var shape:ShapeScreen
    private lateinit var building:BuildingScreen
    private val planes=CopycatMirrorPlane.entries
    private const val mirrorId="smoke_copycat_mirror"
    private const val segmentId="smoke_segment"
    private const val segmentMirrorId="smoke_segment_mirror"
    private const val formKey="smoke_centered@1"
    private var beforeInsertion=""
    private val move by lazy {
        MouseHandler::class.java.getDeclaredMethod("onMove",java.lang.Long.TYPE,java.lang.Double.TYPE,java.lang.Double.TYPE).apply { isAccessible=true }
    }

    fun tick():Boolean {
        if(failed) return false
        val mc=Minecraft.getInstance()
        if(ticks==0 && (mc.screen==null || mc.overlay!=null)) return false
        ticks++
        try {
            when {
                ticks==1 && java.lang.Boolean.getBoolean("architect.buildingRotationSmoke") -> { beginShape(mc);beginBuilding(mc);ticks=123 }
                ticks==1 -> beginShape(mc)
                ticks==5 -> { check(checkNotNull(shape.graphCanvas).previewParts().isNotEmpty());screenshot(mc,"printer-normal") }
                ticks in 10..22 -> shapePlane(planes[ticks-10])
                ticks==25 -> screenshot(mc,"printer-body-normal")
                ticks==28 -> { resize(mc,2);shape.selectShapeNode(mirrorId);shape.openGraph(true) }
                ticks in 32..44 -> shapePlane(planes[ticks-32])
                ticks==46 -> { screenshot(mc,"printer-body-compact");shape.openGraph(false);checkNotNull(shape.kilaGraph).fit() }
                ticks==47 -> {
                    beforeInsertion=Json.encode(shape.form)
                    val editor=checkNotNull(shape.kilaGraph)
                    click(shape,editor.x+editor.width-8.0 to editor.y+editor.height-8.0,1)
                    check(editor.hasOpenMenu())
                }
                ticks==48 -> hover(mc,shape,checkNotNull(shape.kilaGraph?.menuItemPoint(ui("add_node"))))
                ticks==49 -> hover(mc,shape,checkNotNull(shape.kilaGraph?.menuItemPoint(ui("copycat_group_operations"))))
                ticks==50 -> {
                    click(shape,checkNotNull(shape.kilaGraph?.menuItemPoint(ui("node_mirror"))))
                    val original=checkNotNull(Json.decode<Form>(beforeInsertion).graph)
                    val added=checkNotNull(shape.form.graph).nodes.single { n -> original.nodes.none { it.id==n.id } }
                    check(added.kind==NodeKind.MIRROR && added.inputs==listOf(""))
                    undoRedo(shape,beforeInsertion,Json.encode(shape.form)) { Json.encode(shape.form) }
                    action(shape,"undo");check(Json.encode(shape.form)==beforeInsertion)
                }
                ticks==55 -> beginBuilding(mc)
                ticks in 60..72 -> buildingPlane(planes[ticks-60])
                ticks==75 -> { canvas().showSection("3d");check(canvas().previewPlan().isSuccess) }
                ticks==77 -> screenshot(mc,"table-body-normal")
                ticks==80 -> { resize(mc,2);properties() }
                ticks in 84..96 -> buildingPlane(planes[ticks-84])
                ticks==99 -> { canvas().showSection("3d");check(canvas().previewPlan().isSuccess) }
                ticks==101 -> screenshot(mc,"table-body-compact")
                ticks==104 -> {
                    click(building,checkNotNull(building.buildingDefinitionList).definitionPoint(segmentMirrorId));properties()
                    scope(true)
                }
                ticks==107 -> canvas().showSection("2d")
                ticks==108 -> screenshot(mc,"segment-positions-compact")
                ticks==110 -> { resize(mc,1);properties();scope(false) }
                ticks==113 -> canvas().showSection("3d")
                ticks==114 -> screenshot(mc,"segment-whole-normal")
                ticks==116 -> {
                    beforeInsertion=Json.encode(building.building)
                    building.openBuildingGraph(false)
                    val editor=checkNotNull(building.kilaBuildingGraph);editor.fit()
                    click(building,editor.x+editor.width-8.0 to editor.y+editor.height-8.0,1)
                    check(editor.hasOpenMenu())
                }
                ticks==118 -> hover(mc,building,checkNotNull(building.kilaBuildingGraph?.menuItemPoint(ui("add_node"))))
                ticks==119 -> hover(mc,building,checkNotNull(building.kilaBuildingGraph?.menuItemPoint(ui("node_category_copycats"))))
                ticks==120 -> {
                    click(building,checkNotNull(building.kilaBuildingGraph?.menuItemPoint(ui("bg_copycat_mirror"))))
                    val original=checkNotNull(Json.decode<Building>(beforeInsertion).graph)
                    val added=checkNotNull(building.building.graph).nodes.single { n -> original.nodes.none { it.id==n.id } }
                    check(added.kind==BuildingKind.COPYCAT_MIRROR && added.inputs==listOf("") && added.mirror==CopycatMirror())
                    undoRedo(building,beforeInsertion,Json.encode(building.building)) { Json.encode(building.building) }
                    action(building,"undo");check(Json.encode(building.building)==beforeInsertion)
                }
                ticks==124 -> { building.openBuildingSection(BuildingPanelSection.SEGMENTS);selectRotation("smoke_copycat_rotation") }
                ticks in 125..136 -> buildingRotation("smoke_copycat_rotation",ticks-125)
                ticks==138 -> { canvas().showSection("3d");check(canvas().previewPlan().isSuccess) }
                ticks==140 -> { screenshot(mc,"copycat-rotation-normal");selectRotation("smoke_segment_rotation") }
                ticks in 142..153 -> buildingRotation("smoke_segment_rotation",ticks-142)
                ticks==155 -> canvas().showSection("3d")
                ticks==157 -> { screenshot(mc,"segment-rotation-normal");resize(mc,2);selectRotation("smoke_copycat_rotation") }
                ticks in 160..171 -> buildingRotation("smoke_copycat_rotation",ticks-160)
                ticks==173 -> { canvas().showSection("3d");check(canvas().previewPlan().isSuccess) }
                ticks==175 -> { screenshot(mc,"copycat-rotation-compact");selectRotation("smoke_segment_rotation") }
                ticks in 177..188 -> buildingRotation("smoke_segment_rotation",ticks-177)
                ticks==190 -> canvas().showSection("3d")
                ticks==192 -> screenshot(mc,"segment-rotation-compact")
                ticks==195 -> { resize(mc,1);beginRotationInsertion() }
                ticks==196 || ticks==201 -> hover(mc,building,checkNotNull(building.kilaBuildingGraph?.menuItemPoint(ui("add_node"))))
                ticks==197 -> hover(mc,building,checkNotNull(building.kilaBuildingGraph?.menuItemPoint(ui("node_category_copycats"))))
                ticks==198 -> finishRotationInsertion(BuildingKind.COPYCAT_ROTATE)
                ticks==200 -> beginRotationInsertion()
                ticks==202 -> hover(mc,building,checkNotNull(building.kilaBuildingGraph?.menuItemPoint(ui("node_category_segments"))))
                ticks==203 -> finishRotationInsertion(BuildingKind.SEGMENT_ROTATE)
                ticks==205 -> {
                    ClientState.snapshot=previousSnapshot;resize(mc,previousScale);mc.setScreen(previous)
                    if(java.lang.Boolean.getBoolean("architect.buildingRotationSmoke")) println("ARCHITECT BUILDING ROTATION UI SMOKE PASSED: real Copycat/segment rotation dropdowns for X/Y/Z and 0/90/180/270 degrees, normal and compact inspectors, local geometry, JSON roundtrips, per-control Undo/Redo, and native insertion from Copycats and Segments categories verified")
                    else println("ARCHITECT COPYCAT MIRROR UI SMOKE PASSED: printer and architect table actual plane dropdowns exercised all 13 centered Householder planes in normal and compact inspectors, exact rational body geometry, real Undo/Redo and JSON roundtrips, segment positions-only versus whole-object geometry after source rotation, native shape/table mirror insertion, and actual building Copycat/segment rotation controls for X/Y/Z and all four angles in normal and compact inspectors verified")
                    mc.stop()
                }
                else -> return false
            }
        } catch(error:Throwable) {
            failed=true
            System.err.println("ARCHITECT COPYCAT MIRROR UI SMOKE FAILED at tick $ticks: ${error.message}")
            screenshot(mc,"failure-$ticks")
            throw IllegalStateException("Copycat mirror UI smoke failed at tick $ticks",error)
        }
        return true
    }

    private fun beginShape(mc:Minecraft) {
        previous=mc.screen;previousScale=mc.options.guiScale().get();previousSnapshot=ClientState.snapshot
        val centered=Form(id="smoke_centered",name="Centered mirror body",parts=listOf(Part("copycats:copycat_byte",offset=P(4,4,4))))
        val library=Library().apply { seed();forms[formKey]=centered;forms["smoke_byte@1"]=Form(id="smoke_byte",parts=listOf(Part("copycats:copycat_byte"))) }
        val form=Form(id="mirror_ui_smoke",name="Copycat mirror controls",graph=NodeGraph(nodes=listOf(
            GraphNode("source",part=centered.parts.single(),x=20,y=30),
            GraphNode(mirrorId,NodeKind.MIRROR,listOf("source"),x=260,y=30,mirror=CopycatMirror()),
            GraphNode("result",NodeKind.OUTPUT,listOf(mirrorId),x=500,y=30))))
        val view=PrinterView(P(1,1,1),"mirror-ui-smoke",form,mapOf("body" to "minecraft:oak_planks"),slots=List(7) { CartridgeView() },placed=true)
        ClientState.snapshot=Snapshot(library=library,printer=view)
        resize(mc,1)
        shape=ShapeScreen(printerSession=view).apply { graphMode=true;graphInspector=true;selectShapeNode(mirrorId) }
        mc.setScreen(shape);shape.openGraph(true)
        check(shape.width==854 && shape.height==480)
        check(planes.size==13) { "The actual mirror picker must include three axes, six face normals, and four body normals" }
    }

    private fun shapePlane(plane:CopycatMirrorPlane) {
        val before=Json.encode(shape.form)
        val inspector=checkNotNull(shape.graphCanvas)
        val point=checkNotNull(inspector.mirrorPlanePoint())
        val target=point.second.coerceIn(inspector.y+48.0,inspector.y+inspector.height-18.0)
        if(target!=point.second) check(shape.mouseScrolled(inspector.x+inspector.width/2.0,inspector.y+inspector.height-8.0,0.0,(target-point.second)/21.0))
        click(shape,checkNotNull(shape.graphCanvas?.mirrorPlanePoint()))
        check(checkNotNull(shape.graphCanvas).hasOpenMenu()) { "The printer mirror dropdown must survive its real click and release at $point" }
        choose(shape,plane.ordinal)
        val node=checkNotNull(shape.form.graph).nodes.single { it.id==mirrorId }
        check(node.mirror==CopycatMirror(plane)) { "The printer's real dropdown did not select $plane" }
        check(shape.shapeNodePreview(mirrorId).error.isEmpty())
        geometry(plane,checkNotNull(shape.graphCanvas).previewParts())
        val after=Json.encode(shape.form)
        check(Json.decode<Form>(after)==shape.form)
        undoRedo(shape,before,after) { Json.encode(shape.form) }
    }

    private fun beginBuilding(mc:Minecraft) {
        resize(mc,1)
        val initial=BuildingGraph.explicitStarter(joinWallEnds=false)
        val source=BuildingNode("smoke_source",BuildingKind.COPYCAT,name="Centered Copycat",form=formKey,role="frame")
        val reflected=BuildingAssembly.newNode(BuildingKind.COPYCAT_MIRROR,mirrorId,"Copycat spiegeln").copy(inputs=listOf(source.id))
        val byte=source.copy(id="smoke_byte",form="smoke_byte@1",rotationAxis=RotationAxis.X,quarterTurns=1)
        val layout=BuildingAssembly.newNode(BuildingKind.SEGMENT_LAYOUT,"smoke_layout").copy(layoutCells=listOf(SegmentLayoutCell(0,0,0,1)))
        val segment=BuildingAssembly.newNode(BuildingKind.SEGMENT,segmentId).copy(layout=layout.id,inputs=listOf(byte.id))
        val segmentMirror=BuildingAssembly.newNode(BuildingKind.SEGMENT_MIRROR,segmentMirrorId,"Segment spiegeln").copy(inputs=listOf(segment.id))
        val copycatRotation=BuildingAssembly.newNode(BuildingKind.COPYCAT_ROTATE,"smoke_copycat_rotation").copy(inputs=listOf(byte.id))
        val segmentRotation=BuildingAssembly.newNode(BuildingKind.SEGMENT_ROTATE,"smoke_segment_rotation").copy(inputs=listOf(segment.id))
        val graph=initial.copy(nodes=listOf(source,reflected,byte,layout,segment,segmentMirror,copycatRotation,segmentRotation)+initial.nodes)
        BuildingGraphCompiler.structure(graph)
        building=BuildingScreen().apply {
            building=Building(schema=2,id="copycat_mirror_smoke",name="Copycat spiegeln",graph=graph)
            buildingGraphView.selected=mirrorId;buildingGraphView.inspectorSections.setExpanded("properties",true)
        }
        mc.setScreen(building);building.openBuildingSection(BuildingPanelSection.SEGMENTS)
        click(building,checkNotNull(building.buildingDefinitionList).definitionPoint(mirrorId));properties()
    }

    private fun buildingPlane(plane:CopycatMirrorPlane) {
        properties()
        val before=Json.encode(building.building)
        property(mirrorId,0);check(canvas().hasOpenMenu())
        choose(building,plane.ordinal)
        val node=checkNotNull(building.building.graph).nodes.single { it.id==mirrorId }
        check(node.mirror==CopycatMirror(plane)) { "The table's real dropdown did not select $plane" }
        val plan=canvas().previewPlan().getOrThrow()
        geometry(plane,plan.placements.single().parts)
        val after=Json.encode(building.building)
        check(Json.decode<Building>(after)==building.building)
        undoRedo(building,before,after) { Json.encode(building.building) }
    }

    private fun beginRotationInsertion() {
        beforeInsertion=Json.encode(building.building)
        building.openBuildingGraph(false)
        val editor=checkNotNull(building.kilaBuildingGraph);editor.fit()
        click(building,editor.x+editor.width-8.0 to editor.y+editor.height-8.0,1)
        check(editor.hasOpenMenu())
    }
    private fun finishRotationInsertion(kind:BuildingKind) {
        click(building,checkNotNull(building.kilaBuildingGraph?.menuItemPoint(ui("bg_"+kind.name.lowercase()))))
        val original=checkNotNull(Json.decode<Building>(beforeInsertion).graph)
        val added=checkNotNull(building.building.graph).nodes.single { n -> original.nodes.none { it.id==n.id } }
        check(added.kind==kind && added.inputs==listOf("") && added.quarterTurns==1)
        undoRedo(building,beforeInsertion,Json.encode(building.building)) { Json.encode(building.building) }
        action(building,"undo");check(Json.encode(building.building)==beforeInsertion)
    }

    private fun selectRotation(id:String) {
        click(building,checkNotNull(building.buildingDefinitionList).definitionPoint(id));properties()
    }
    private fun buildingRotation(id:String,index:Int) {
        properties()
        val before=Json.encode(building.building)
        property(id,20);check(canvas().hasOpenMenu());choose(building,index/4)
        val beforeTurn=Json.encode(building.building)
        undoRedo(building,before,beforeTurn) { Json.encode(building.building) }
        property(id,42);check(canvas().hasOpenMenu());choose(building,index%4)
        val graph=checkNotNull(building.building.graph)
        val node=graph.nodes.single { it.id==id }
        val axis=listOf(RotationAxis.X,RotationAxis.Y,RotationAxis.Z)[index/4];val q=index%4
        check(node.rotationAxis==axis && node.quarterTurns==q)
        val evaluation=BuildingGraphCompiler.Evaluation(graph,building.building.palette,ClientState.snapshot.library.forms,ClientState.snapshot.library.modules)
        val plan=canvas().previewPlan().getOrThrow()
        if(node.kind==BuildingKind.COPYCAT_ROTATE) {
            val source=evaluation.preview(node.inputs.single()).placements.single()
            check(Geometry.exact(source.parts.map { checkNotNull(PartRotations.rotate(it,axis,q)) }).equivalent(Geometry.exact(plan.placements.single().parts)))
        } else check(evaluation.segment(id)==SegmentGeometry.rotated(evaluation.segment(node.inputs.single()),axis,q))
        val after=Json.encode(building.building)
        check(Json.decode<Building>(after)==building.building)
        undoRedo(building,beforeTurn,after) { Json.encode(building.building) }
    }

    private fun geometry(plane:CopycatMirrorPlane,parts:List<Part>) {
        check(parts.isNotEmpty() && parts.all { it.source=="copycats:copycat_byte" })
        val original=Geometry.exact(ClientState.snapshot.library.forms.getValue(formKey).parts)
        val actual=Geometry.exact(parts)
        check(actual.equivalent(original.reflected(plane))) { "The selected plane must reach actual Copycat geometry" }
        if(plane.requiresExact) check(actual.cells.flatMap { it.vertices() }.any { p -> listOf(p.x,p.y,p.z).any { it.denominator!=BigInteger.ONE } }) {
            "Body diagonal reflections must retain rational geometry in the actual preview"
        }
    }

    private fun scope(positionsOnly:Boolean) {
        val before=Json.encode(building.building)
        property(segmentMirrorId,40);check(canvas().hasOpenMenu());choose(building,if(positionsOnly) 1 else 0)
        val graph=checkNotNull(building.building.graph)
        check(graph.nodes.single { it.id==segmentMirrorId }.mirrorPositionsOnly==positionsOnly)
        val evaluation=BuildingGraphCompiler.Evaluation(graph,building.building.palette,ClientState.snapshot.library.forms,ClientState.snapshot.library.modules)
        val source=evaluation.segment(segmentId).cells.single { !it.cell.empty }
        val mirrored=evaluation.segment(segmentMirrorId).cells.single { !it.cell.empty }
        check(mirrored.cell.pos==P(2,0,0))
        val original=evaluation.preview(segmentId).placements.single()
        val changed=canvas().previewPlan().getOrThrow().placements.single()
        if(positionsOnly) { check(source.cell.copy(pos=P(2,0,0))==mirrored.cell);check(original.parts==changed.parts) }
        else check(Geometry.solid(original.parts).key()!=Geometry.solid(changed.parts).key())
        undoRedo(building,before,Json.encode(building.building)) { Json.encode(building.building) }
    }

    private fun canvas()=checkNotNull(building.buildingGraphCanvas)
    private fun properties() { canvas().showSection("properties") }
    private fun property(id:String,row:Int) {
        val inspector=canvas();val point=inspector.propertyPoint(id,row)
        val target=point.second.coerceIn(inspector.y+70.0,inspector.y+inspector.height-18.0)
        if(target!=point.second) check(building.mouseScrolled(inspector.x+inspector.width/2.0,inspector.y+inspector.height-8.0,0.0,(target-point.second)/25.0))
        click(building,canvas().propertyPoint(id,row))
    }
    private fun choose(screen:Screen,index:Int) {
        check(screen.keyPressed(GLFW.GLFW_KEY_HOME,0,0))
        repeat(index) { check(screen.keyPressed(GLFW.GLFW_KEY_DOWN,0,0)) }
        check(screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0))
    }
    private fun undoRedo(screen:Screen,before:String,after:String,document:()->String) {
        if(before==after) return
        action(screen,"undo");check(document()==before) { "Undo must restore the complete mirror JSON" }
        action(screen,"redo");check(document()==after) { "Redo must restore the chosen mirror and scope" }
    }
    private fun action(screen:Screen,key:String) {
        val button=screen.children().filterIsInstance<Button>().first { it !is PrinterEdgeTab && it.visible && it.message.string==ui(key) }
        check(button.active);click(screen,button.x+button.width/2.0 to button.y+button.height/2.0)
    }
    private fun click(screen:Screen,point:Pair<Double,Double>,button:Int=0) {
        check(point.first>=0 && point.first<screen.width && point.second>=0 && point.second<screen.height) { "Mirror control must fit ${screen.width} x ${screen.height}: $point" }
        check(screen.mouseClicked(point.first,point.second,button)) { "The actual mirror widget must receive its click" }
        screen.mouseReleased(point.first,point.second,button)
    }
    private fun hover(mc:Minecraft,screen:Screen,point:Pair<Double,Double>) {
        screen.mouseMoved(point.first,point.second)
        val rawX=point.first*mc.window.screenWidth/screen.width;val rawY=point.second*mc.window.screenHeight/screen.height
        GLFW.glfwSetCursorPos(mc.window.window,rawX,rawY);move.invoke(mc.mouseHandler,mc.window.window,rawX,rawY)
    }
    private fun resize(mc:Minecraft,scale:Int) { mc.options.guiScale().set(scale);mc.resizeDisplay() }
    private fun screenshot(mc:Minecraft,label:String) {
        Screenshot.grab(mc.gameDirectory,"architect-copycat-mirror-$label.png",mc.mainRenderTarget) { println(it.string) }
    }
}
