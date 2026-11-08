package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.*
import net.minecraft.client.Minecraft
import net.minecraft.client.MouseHandler
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.Screen

/** Real screen routing, including folded groups, member navigation and nested native menus. */
internal object InspectorWorkflowSmoke {
    private var ticks=0
    private lateinit var building:BuildingScreen
    private lateinit var shape:ShapeScreen
    private var original=""
    private val mc get()=Minecraft.getInstance()
    private val move by lazy { MouseHandler::class.java.getDeclaredMethod("onMove",java.lang.Long.TYPE,java.lang.Double.TYPE,java.lang.Double.TYPE).apply { isAccessible=true } }
    private fun click(screen:Screen,point:Pair<Double,Double>,button:Int=0) {
        check(point.first in 0.0..<screen.width.toDouble() && point.second in 0.0..<screen.height.toDouble()) { "Invisible inspector target $point" }
        check(screen.mouseClicked(point.first,point.second,button));screen.mouseReleased(point.first,point.second,button)
    }
    private fun hover(screen:Screen,point:Pair<Double,Double>) {
        val rawX=point.first*mc.window.screenWidth/screen.width;val rawY=point.second*mc.window.screenHeight/screen.height
        org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.window.window,rawX,rawY)
        move.invoke(mc.mouseHandler,mc.window.window,rawX,rawY)
        screen.mouseMoved(point.first,point.second)
    }
    private fun editor()=checkNotNull(building.kilaBuildingGraph)
    private fun screenshot(name:String) { Screenshot.grab(mc.gameDirectory,"architect-inspector-$name.png",mc.mainRenderTarget) { println(it.string) } }
    private fun begin(scale:Int) {
        mc.options.guiScale().set(scale);mc.resizeDisplay()
        ClientState.snapshot=ClientState.snapshot.copy(library=Library().apply { seed() })
        val graph=BuildingGraph.explicitStarter(joinWallEnds=false).let { graph -> graph.copy(nodes=graph.nodes.map { node ->
            if(node.id=="wall_layout") node.copy(geometry="area") else node
        }+BuildingNode("area",BuildingKind.AREA,x=-240,y=600,coordinates=listOf(P(-2,0,3),P(-1,0,3),P(-2,0,4),P(-2,0,5))),
            nodeGroups=listOf(GraphGroup("wall_group","Wall assembly",listOf("wall_copycat","wall_layout","wall")))) }
        building=BuildingScreen().apply { this.building=Building(id="inspector_workflow",schema=2,graph=graph);selectBuildingNode("building") }
        mc.setScreen(building);building.openBuildingGraph()
        building.children().filterIsInstance<PrinterEdgeTab>().single { it.message.string==ui("printer_inspector") }.onPress()
        original=Json.encode(building.building)
    }
    fun tick():Boolean {
        if(ticks==0 && (mc.screen==null || mc.overlay!=null)) return false
        ticks++
        if(ticks>100) return MaterialSelectionSmoke.tick()
        val step=if(ticks<=50) ticks else ticks-50
        val scale=if(ticks<=50) 1 else 2
        when(step) {
            1 -> begin(scale)
            5 -> editor().fit()
            8 -> {
                click(building,checkNotNull(editor().groupHeaderPoint("wall_group")))
                check(building.buildingGraphView.selected=="@group:wall_group")
                check(building.kilaBuildingGraphState.selection==setOf("wall_copycat","wall_layout","wall")) { "Group selection: ${building.kilaBuildingGraphState.selected} / ${building.kilaBuildingGraphState.selection}" }
            }
            10 -> {
                screenshot("building-group-$scale")
                click(building,checkNotNull(building.buildingGraphCanvas?.groupMemberPoint("wall_copycat")))
                check(building.buildingGraphView.selected=="wall_copycat")
            }
            14 -> {
                check(editor().isGroupExpanded("wall_group"))
                check(editor().nodeHeaderPoint("wall_copycat")!=null)
                check(Json.encode(building.building)==original)
                click(building,checkNotNull(editor().groupHeaderPoint("wall_group")))
                check(building.buildingGraphView.selected=="@group:wall_group")
            }
            16 -> {
                click(building,editor().x+editor().width-8.0 to editor().y+editor().height-8.0,1)
                check(editor().hasOpenMenu())
            }
            18 -> hover(building,checkNotNull(editor().menuItemPoint(ui("add_node"))))
            20 -> hover(building,checkNotNull(editor().menuItemPoint(ui("node_category_geometry"))))
            23 -> {
                screenshot("geometry-menu-$scale")
                click(building,checkNotNull(editor().menuItemPoint(ui("bg_area"))))
                val before=Json.decode<Building>(original).graph!!
                val added=building.building.graph!!.nodes.single { node -> before.nodes.none { it.id==node.id } }
                check(added.kind==BuildingKind.AREA)
                val undo=building.children().filterIsInstance<net.minecraft.client.gui.components.Button>().single { it.message.string==ui("undo") }
                undo.onPress();check(Json.encode(building.building)==original)
            }
            24 -> {
                building.selectBuildingNode("wall")
                checkNotNull(building.buildingGraphCanvas).showSection("2d")
                val plan=checkNotNull(building.buildingGraphCanvas).previewPlan().getOrThrow()
                check(plan.placements.map { it.pos }.toSet()==setOf(P(),P(0,1,0),P(0,2,0),P(1,2,0)))
            }
            25 -> screenshot("segment-front-$scale")
            26 -> {
                val graph=NodeGraph(nodes=listOf(GraphNode("source",part=Part("copycats:copycat_slab")),
                    GraphNode("rotate",NodeKind.ROTATE,listOf("source",""),x=240),GraphNode("result",NodeKind.OUTPUT,listOf("rotate"),x=480)),
                    nodeGroups=listOf(GraphGroup("shape_group","Rotated slab",listOf("source","rotate"))))
                shape=ShapeScreen(parent=building).apply { form=Form(id="shape_inspector_workflow",graph=graph);graphInspector=true }
                mc.setScreen(shape);checkNotNull(shape.kilaGraph).fit();original=Json.encode(shape.form)
            }
            30 -> {
                click(shape,checkNotNull(shape.kilaGraph?.groupHeaderPoint("shape_group")))
                check(shape.graphView.selected=="@group:shape_group")
                check(shape.kilaGraphState.selection==setOf("source","rotate"))
            }
            33 -> screenshot("shape-group-$scale")
            35 -> {
                click(shape,checkNotNull(shape.graphCanvas?.groupMemberPoint("source")))
                check(shape.graphView.selected=="source")
            }
            38 -> {
                check(checkNotNull(shape.kilaGraph).isGroupExpanded("shape_group"))
                check(shape.kilaGraphState.selection==setOf("source"))
                check(Json.encode(shape.form)==original)
            }
            42 -> if(scale==2) println("ARCHITECT INSPECTOR WORKFLOW UI SMOKE PASSED: building and shape group identity, actual member navigation, view-only expansion, nested Geometry insertion with exact Undo, normal and compact routing verified")
        }
        return true
    }
}
