package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen

/** Uses actual section headers and native selections at both GUI scales. */
internal object InspectorSectionsSmoke {
    private var ticks=0
    private lateinit var building:BuildingScreen
    private lateinit var shape:ShapeScreen
    private var original=""
    private var expected=emptyMap<String,Boolean>()
    private var originalHistory=false to false
    private var wallYaw=35f
    private val mc get()=Minecraft.getInstance()
    private val shapeMode get()=ticks>70
    private val screen:Screen get()=if(shapeMode) shape else building
    private val state get()=if(shapeMode) shape.graphView.inspectorSections else building.buildingGraphView.inspectorSections
    private fun document()=if(shapeMode) Json.encode(shape.form) else Json.encode(building.building)
    private fun history()=listOf("undo","redo").map { key -> screen.children().filterIsInstance<Button>().single { it.message.string==ui(key) }.active }.let { it[0] to it[1] }
    private fun unchanged() {
        check(document()==original && history()==originalHistory) { "Section navigation changed document or history" }
        check(expected.all { (key,value) -> state.isExpanded(key)==value }) { "Section state changed with selection or GUI scale" }
    }
    private fun click(point:Pair<Double,Double>,button:Int=0) {
        check(point.first in 0.0..<screen.width.toDouble() && point.second in 0.0..<screen.height.toDouble()) { "Section target is outside the screen: $point" }
        check(screen.mouseClicked(point.first,point.second,button));screen.mouseReleased(point.first,point.second,button)
    }
    private fun set(key:String,expanded:Boolean) {
        if(state.isExpanded(key)!=expanded) click(if(shapeMode) shape.graphCanvas!!.sectionHeaderPoint(key) else building.buildingGraphCanvas!!.sectionHeaderPoint(key))
        check(state.isExpanded(key)==expanded)
        expected=expected+(key to expanded);unchanged()
    }
    private fun select(id:String) {
        click(checkNotNull(if(shapeMode) shape.kilaGraph?.nodeHeaderPoint(id) else building.kilaBuildingGraph?.nodeHeaderPoint(id)))
        check((if(shapeMode) shape.graphView.selected else building.buildingGraphView.selected)==id) { "Selection $id failed at tick $ticks" }
        unchanged()
    }
    private fun screenshot(name:String) { Screenshot.grab(mc.gameDirectory,"architect-inspector-sections-$name.png",mc.mainRenderTarget) { println(it.string) } }
    private fun beginBuilding() {
        mc.options.guiScale().set(1);mc.resizeDisplay()
        ClientState.snapshot=ClientState.snapshot.copy(library=Library().apply { seed() })
        building=BuildingScreen().apply { this.building=Building(id="section_smoke",schema=2,graph=BuildingGraph.explicitStarter(joinWallEnds=false)) }
        mc.setScreen(building);building.openBuildingGraph()
        building.children().filterIsInstance<PrinterEdgeTab>().single { it.message.string==ui("printer_inspector") }.onPress()
        expected=mapOf("properties" to true,"2d" to false,"3d" to true)
        original=document();originalHistory=history()
    }
    private fun beginShape() {
        mc.options.guiScale().set(1);mc.resizeDisplay()
        shape=ShapeScreen(parent=building).apply {
            form=Form(id="shape_sections",graph=NodeGraph(nodes=listOf(
                GraphNode("a",part=Part("copycats:copycat_slab")),
                GraphNode("b",part=Part("copycats:copycat_slab",top=true),y=200),
                GraphNode("join",NodeKind.UNION,listOf("a","b"),x=240),
                GraphNode("result",NodeKind.OUTPUT,listOf("join"),x=480))))
            graphInspector=true
        }
        mc.setScreen(shape)
        expected=mapOf("connections" to true,"properties" to true,"3d" to true)
        original=document();originalHistory=history()
    }
    fun tick():Boolean {
        if(ticks==0 && (mc.screen==null || mc.overlay!=null)) return false
        ticks++
        val phase=(ticks-1)%35+1
        val compact=(ticks-1)/35%2==1
        when(phase) {
            1 -> when(ticks) {
                1->beginBuilding();71->beginShape()
                else->{ unchanged();mc.options.guiScale().set(2);mc.resizeDisplay();unchanged() }
            }
            3 -> if(shapeMode) shape.kilaGraph!!.fit() else building.kilaBuildingGraph!!.fit()
            5 -> {
                select(if(shapeMode) "join" else "wall")
                set("properties",false)
                if(shapeMode) set("connections",false) else set("2d",true)
                set("3d",true)
            }
            8 -> screenshot("${if(shapeMode) "copycat" else "building"}-${if(compact) "compact" else "normal"}")
            10 -> select(if(shapeMode) "a" else "room")
            12 -> {
                if(!shapeMode) select("room_geometry")
                unchanged()
            }
            15 -> select(if(shapeMode) "join" else "wall")
            18 -> {
                if(!shapeMode) {
                    val canvas=building.buildingGraphCanvas!!;canvas.revealSection("3d")
                    val viewport=checkNotNull(canvas.previewViewport("3d"))
                    check(viewport.y>=canvas.y+35 && viewport.y+viewport.height<=canvas.y+canvas.height)
                    val point=viewport.x+viewport.width/2.0 to viewport.y+viewport.height/2.0
                    val before=canvas.previewYaw();click(point,1)
                    check(screen.mouseClicked(point.first,point.second,1));check(screen.mouseDragged(point.first+12,point.second,1,12.0,0.0));screen.mouseReleased(point.first+12,point.second,1)
                    check(canvas.previewYaw()!=before);wallYaw=canvas.previewYaw()
                }
                unchanged()
            }
            20 -> { set("3d",false);if(!shapeMode) set("2d",false);check(!shapeMode || !state.isExpanded("connections")) }
            21 -> screenshot("${if(shapeMode) "copycat" else "building"}-collapsed-${if(compact) "compact" else "normal"}")
            22 -> set("properties",true)
            23 -> screenshot("${if(shapeMode) "copycat" else "building"}-properties-${if(compact) "compact" else "normal"}")
            24 -> {
                select(if(shapeMode) "a" else "room")
            }
            25 -> {
                select(if(shapeMode) "join" else "wall")
                if(!shapeMode) check(building.buildingGraphCanvas!!.previewYaw()==wallYaw)
                set("3d",true)
            }
            27 -> {
                if(shapeMode) {
                    shape.graphCanvas!!.revealSection("3d")
                    val before=shape.graphView.inspectorScroll
                    val canvas=shape.graphCanvas!!
                    check(screen.mouseScrolled(canvas.x+canvas.width/2.0,canvas.y+canvas.height-15.0,0.0,1.0))
                    check(shape.graphView.inspectorScroll<before)
                } else {
                    val canvas=building.buildingGraphCanvas!!;canvas.revealSection("3d")
                    val viewport=checkNotNull(canvas.previewViewport("3d"));val before=building.buildingGraphView.inspectorScroll
                    check(screen.mouseScrolled(viewport.x+viewport.width/2.0,viewport.y+viewport.height/2.0,0.0,1.0))
                    check(building.buildingGraphView.inspectorScroll<before)
                }
                unchanged()
            }
            30 -> unchanged()
            35 -> if(ticks==140) {
                println("ARCHITECT INSPECTOR SECTIONS UI SMOKE PASSED: actual independent section headers, simultaneous 2D and 3D, building and Copycat selections, temporarily absent sections, persistent resize state, scrolling over previews, per-node orbit and unchanged JSON/Undo/Redo at both GUI scales")
                mc.stop()
            }
        }
        return true
    }
}
