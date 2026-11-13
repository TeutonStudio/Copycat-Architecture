package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.Library
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import org.lwjgl.glfw.GLFW

/** Real navigation, inspector terminals, native wires and independent group instances. */
internal object GroupGraphInterfaceSmoke {
    private var ticks=0
    private lateinit var shape:ShapeScreen
    private lateinit var building:BuildingScreen
    private var buildingGroup=""
    private var buildingNodes=emptyList<BuildingNode>()
    private val mc get()=Minecraft.getInstance()
    private val graph get()=checkNotNull(shape.form.graph)
    private val face get()=checkNotNull(graph.nodeGroups.first { it.id=="module" }.graphInterface)
    private var added=""
    private var wired:NodeGraph?=null
    private var layoutBefore:GraphGroupInterface?=null
    private var layoutAfter:NodeGraph?=null
    private val child get()=mc.screen as GroupGraphScreen
    private fun click(point:Pair<Double,Double>,button:Int=0) {
        val screen=checkNotNull(mc.screen)
        check(screen.mouseClicked(point.first,point.second,button)) { "Missed click $point at $ticks" }
        screen.mouseReleased(point.first,point.second,button)
    }
    private fun button(key:String) {
        val button=mc.screen!!.children().filterIsInstance<Button>().single { it.message.string==ui(key) }
        click(button.x+button.width/2.0 to button.y+button.height/2.0)
    }
    private fun name(value:String) {
        val screen=mc.screen!!
        screen.children().filterIsInstance<EditBox>().single().value=value
        check(screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0))
    }
    private fun wire(editor:KilaGraphEditor,source:String,target:String,slot:Int) {
        val from=checkNotNull(editor.outputPortPoint(source));val to=checkNotNull(editor.inputPortPoint(target,slot))
        val screen=mc.screen!!
        check(screen.mouseClicked(from.first,from.second,0))
        screen.mouseDragged(to.first,to.second,0,to.first-from.first,to.second-from.second)
        screen.mouseReleased(to.first,to.second,0)
    }
    private fun screenshot(name:String) { Screenshot.grab(mc.gameDirectory,"architect-group-graph-$name.png",mc.mainRenderTarget) { println(it.string) } }
    fun tick():Boolean {
        if(ticks==0 && (mc.screen==null || mc.overlay!=null)) return false
        ticks++
        when(ticks) {
            1 -> {
                mc.options.guiScale().set(1);mc.resizeDisplay()
                ClientState.snapshot=ClientState.snapshot.copy(library=Library().apply { seed() })
                val face=GraphGroupInterface(listOf(GraphGroupInput("form","Form","shape",listOf(GraphGroupTarget(0,"0")),x=-150)),
                    listOf(GraphGroupOutput("result","Copycat","shape",0,x=480)))
                val group=GraphGroup("module","Modul",listOf("union"),graphInterface=face,inputConnections=mapOf("form" to "source"))
                val graph=NodeGraph(nodes=listOf(
                    GraphNode("source",part=Part("copycats:copycat_slab"),x=-200),
                    GraphNode("other",part=Part("copycats:copycat_slab",top=true),x=-200,y=220),
                    GraphNode("union",NodeKind.UNION,listOf("source",""),x=180),
                    GraphNode("result",NodeKind.OUTPUT,listOf("union"),x=600)),nodeGroups=listOf(group))
                shape=ShapeScreen(parent=BuildingScreen()).apply { form=Form(id="group_graph_smoke",graph=GraphGroups.instantiate(graph,"module"));graphInspector=false }
                mc.setScreen(shape)
            }
            5 -> shape.kilaGraph!!.fit()
            10 -> {
                check(shape.kilaGraph!!.nodeHeaderPoint("union")==null)
                click(checkNotNull(shape.kilaGraph!!.groupTogglePoint("module")))
            }
            15 -> {
                check(mc.screen is GroupGraphScreen)
                child.editor.fit()
                check(child.editor.nodeHeaderPoint("source")==null)
                check(child.editor.nodeHeaderPoint("union")!=null)
                check(child.editor.outputPortType("@input:form")==child.editor.inputPortType("union",0))
                screenshot("open-normal")
            }
            20 -> button("group_add_input")
            25 -> name("Zweite Form")
            30 -> {
                added=face.inputs.single { it.name=="Zweite Form" }.id
                check(graph.nodeGroups.all { it.graphInterface==face })
                button("port_copycat")
                child.keyPressed(GLFW.GLFW_KEY_END,0,0);child.keyPressed(GLFW.GLFW_KEY_ENTER,0,0)
            }
            35 -> {
                check(face.inputs.last().type=="rotation")
                button("port_rotation")
                child.keyPressed(GLFW.GLFW_KEY_HOME,0,0);child.keyPressed(GLFW.GLFW_KEY_ENTER,0,0)
                child.editor.fit()
            }
            40 -> wire(child.editor,"@input:$added","union",1)
            42 -> {
                child.keyPressed(GLFW.GLFW_KEY_Z,0,GLFW.GLFW_MOD_CONTROL)
                check(face.inputs.last().targets.isEmpty())
            }
            44 -> child.keyPressed(GLFW.GLFW_KEY_Y,0,GLFW.GLFW_MOD_CONTROL)
            45 -> {
                check(face.inputs.last().targets==listOf(GraphGroupTarget(0,"1")))
                screenshot("wired-normal")
                child.onClose()
            }
            50 -> shape.kilaGraph!!.fit()
            55 -> wire(shape.kilaGraph!!,"other","@group:module",1)
            60 -> {
                check(graph.nodes.first { it.id=="union" }.inputs[1]=="other")
                val second=graph.nodeGroups.first { it.id!="module" }
                check(graph.nodes.first { it.id==second.nodes.single() }.inputs[1].isEmpty())
                check(GraphCompiler.compile(graph).isNotEmpty())
                wired=graph
                click(checkNotNull(shape.kilaGraph!!.groupTogglePoint("module")))
            }
            65 -> { mc.options.guiScale().set(2);mc.resizeDisplay();child.editor.fit() }
            70 -> { check(graph==wired);screenshot("open-compact");child.onClose() }
            75 -> {
                check(mc.screen===shape && graph==wired)
                check(shape.kilaGraph!!.nodeHeaderPoint("union")==null)
                mc.options.guiScale().set(1);mc.resizeDisplay()
                val grouped=GraphGroups.create(BuildingGraph.explicitStarter(),setOf("wall"),"Wandgraph")
                buildingGroup=grouped.nodeGroups.single().id;buildingNodes=grouped.nodes
                building=BuildingScreen().apply { this.building=Building(schema=2,id="group_graph_building",graph=grouped) }
                mc.setScreen(building);building.openBuildingGraph()
            }
            80 -> building.kilaBuildingGraph!!.fit()
            85 -> click(checkNotNull(building.kilaBuildingGraph!!.groupTogglePoint(buildingGroup)))
            90 -> {
                check(mc.screen is GroupGraphScreen);child.editor.fit()
                val face=building.building.graph!!.nodeGroups.single().graphInterface!!
                check(face.inputs.map { it.type }.toSet()==setOf("SEGMENT_LAYOUT","COPYCAT"))
                check(face.outputs.single().type=="WALL")
                check(child.editor.nodeHeaderPoint("wall_copycat")==null && child.editor.nodeHeaderPoint("wall")!=null)
                check(building.building.graph!!.nodes==buildingNodes)
                screenshot("building-normal")
            }
            95 -> { mc.options.guiScale().set(2);mc.resizeDisplay();child.editor.fit() }
            100 -> { screenshot("building-compact");child.onClose() }
            105 -> {
                check(mc.screen===building && building.building.graph!!.nodes==buildingNodes)
                mc.options.guiScale().set(1);mc.resizeDisplay();mc.setScreen(shape)
            }
            110 -> shape.kilaGraph!!.fit()
            115 -> click(checkNotNull(shape.kilaGraph!!.groupTogglePoint(graph.nodeGroups.last().id)))
            120 -> {
                check(graph==wired)
                val member=graph.nodeGroups.last().nodes.single()
                val output=checkNotNull(child.editor.nodeHeaderPoint("@output:result"))
                check(output.first>checkNotNull(child.editor.nodeHeaderPoint(member)).first) { "Instance output must stay beside its member" }
                layoutBefore=face
                screenshot("instance-layout-normal")
                click(output)
            }
            125 -> {
                val from=checkNotNull(child.editor.nodeHeaderPoint("@output:result"))
                check(child.mouseClicked(from.first,from.second,0))
                child.mouseDragged(from.first+40,from.second+25,0,40.0,25.0)
                child.mouseReleased(from.first+40,from.second+25,0)
            }
            130 -> {
                check(face.outputs.single()!=layoutBefore!!.outputs.single()) { "Native terminal drag must persist" }
                check(graph.nodes==wired!!.nodes) { "Inner layout edits must preserve outer instance positions" }
                check(graph.nodeGroups.all { it.graphInterface==face })
                layoutAfter=graph
                child.keyPressed(GLFW.GLFW_KEY_Z,0,GLFW.GLFW_MOD_CONTROL)
                check(face==layoutBefore && graph.nodes==wired!!.nodes) { "Undo must restore the shared terminal layout" }
                child.keyPressed(GLFW.GLFW_KEY_Y,0,GLFW.GLFW_MOD_CONTROL)
                check(graph==layoutAfter) { "Redo must restore the complete edited layout" }
                child.onClose()
            }
            135 -> shape.kilaGraph!!.fit()
            140 -> click(checkNotNull(shape.kilaGraph!!.groupTogglePoint("module")))
            145 -> { check(graph==layoutAfter);mc.options.guiScale().set(2);mc.resizeDisplay();child.editor.fit() }
            150 -> { check(graph==layoutAfter);screenshot("shared-layout-compact");child.onClose() }
            155 -> {
                check(mc.screen===shape && graph==layoutAfter)
                println("ARCHITECT GROUP GRAPH INTERFACE SMOKE PASSED: separate Copycat and building graph navigation, inspector input creation and type edits, source terminals, native internal and instance wiring, shared automatic ports, independent instance connections, Undo/Redo, typed segment outputs, stable instance layouts, native terminal dragging, unchanged outer positions and reopening at both GUI scales")
                mc.stop()
            }
            else -> return false
        }
        return true
    }
}
