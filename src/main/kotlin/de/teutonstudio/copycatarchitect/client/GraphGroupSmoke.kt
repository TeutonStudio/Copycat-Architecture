package de.teutonstudio.copycatarchitect.client

import com.lowdragmc.lowdraglib2.gui.ui.utils.KeyState
import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.Json
import de.teutonstudio.copycatarchitect.server.Library
import net.minecraft.client.Minecraft
import net.minecraft.client.MouseHandler
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import org.lwjgl.glfw.GLFW

/** Real KilaGraph controls exercise saved group instances and view-only group navigation. */
internal object GraphGroupSmoke {
    private var ticks=0
    private var failed=false
    private var previous:Screen?=null
    private var previousScale=1
    private lateinit var screen:GroupScreen
    private var navigationJson=""
    private var navigationCommits=0
    private var dragBefore:Map<String,Pair<Int,Int>> = emptyMap()
    private var dragJson=""
    private var dragCommits=0
    private val mouseMove by lazy {
        MouseHandler::class.java.getDeclaredMethod("onMove",java.lang.Long.TYPE,java.lang.Double.TYPE,java.lang.Double.TYPE).apply { isAccessible=true }
    }

    fun tick():Boolean {
        if(failed) return false
        val mc=Minecraft.getInstance()
        if(ticks==0 && (mc.screen==null || mc.overlay!=null)) return false
        ticks++
        var acted=true
        try {
            when(ticks) {
                1 -> {
                    previous=mc.screen;previousScale=mc.options.guiScale().get()
                    mc.options.guiScale().set(1);mc.resizeDisplay();screen=GroupScreen(false);mc.setScreen(screen)
                }
                10 -> screen.editor.fit()
                20 -> {
                    select("angle");select("rotate",true)
                    check(screen.view.selection==setOf("angle","rotate"))
                    check(screen.editor.keyPressed(GLFW.GLFW_KEY_G,0,GLFW.GLFW_MOD_CONTROL))
                    check(mc.screen!==screen) { "Ctrl+G must open the real group-name modal" }
                }
                30 -> {
                    val modal=checkNotNull(mc.screen)
                    modal.children().filterIsInstance<EditBox>().single().value="Reusable rotation"
                    check(modal.keyPressed(GLFW.GLFW_KEY_ENTER,0,0));check(mc.screen===screen)
                }
                40 -> {
                    check(screen.groups().single().nodes.toSet()==setOf("angle","rotate"))
                    check(!screen.editor.isGroupExpanded(screen.groupId()))
                    screen.persisted();rememberNavigation();toggleGroup(true)
                }
                50 -> {
                    check(screen.editor.nodeHeaderPoint("angle")!=null && screen.editor.nodeHeaderPoint("rotate")!=null)
                    navigationUnchanged();screenshot(mc,"shape-open-normal");toggleGroup(false)
                }
                60 -> {
                    navigationUnchanged();check(screen.editor.nodeHeaderPoint("angle")==null)
                    screen.rebuild();mc.options.guiScale().set(2);mc.resizeDisplay()
                }
                70 -> screen.editor.fit()
                80 -> { navigationUnchanged();check(!screen.editor.isGroupExpanded(screen.groupId()));screenshot(mc,"shape-closed-compact");openInstanceMenu() }
                90 -> hoverInstanceMenu(mc)
                100 -> clickInstanceMenu()
                110 -> { screen.verifyInstances();screen.persisted();screen.editor.fit();mc.options.guiScale().set(1);mc.resizeDisplay() }
                120 -> { screen.verifySharedEdit();screen.editor.fit() }
                130 -> { screenshot(mc,"shape-instances-normal");startDrag() }
                140 -> verifyDragAndHistory()
                150 -> { screen.persisted();mc.options.guiScale().set(1);mc.resizeDisplay();screen=GroupScreen(true);mc.setScreen(screen) }
                160 -> screen.editor.fit()
                170 -> { rememberNavigation();toggleGroup(true) }
                180 -> { navigationUnchanged();check(screen.editor.nodeHeaderPoint("wall")!=null);screenshot(mc,"building-open-normal");toggleGroup(false) }
                190 -> { navigationUnchanged();openInstanceMenu() }
                200 -> hoverInstanceMenu(mc)
                210 -> clickInstanceMenu()
                220 -> { screen.verifyInstances();screen.verifySharedEdit();screen.persisted();screen.editor.fit() }
                230 -> { screenshot(mc,"building-instances-normal");startDrag() }
                240 -> { verifyDragAndHistory();rememberNavigation();mc.options.guiScale().set(2);mc.resizeDisplay() }
                250 -> screen.editor.fit()
                260 -> { navigationUnchanged();toggleGroup(true) }
                270 -> { navigationUnchanged();screenshot(mc,"building-open-compact");screen.persisted() }
                280 -> {
                    mc.options.guiScale().set(previousScale);mc.resizeDisplay();mc.setScreen(previous)
                    println("ARCHITECT GRAPH GROUP UI SMOKE PASSED: actual Ctrl+G naming modal, native group open/close and context-menu instantiation, shape/building shared settings with independent external inputs, collapsed group drag as one undoable edit, JSON round trips and persistent navigation across normal/compact rebuilds verified")
                    mc.stop()
                }
                else -> acted=false
            }
        } catch(error:Throwable) {
            failed=true;screenshot(mc,"failure-$ticks")
            throw IllegalStateException("Graph group UI smoke failed at tick $ticks",error)
        }
        return acted
    }

    private fun select(id:String,shift:Boolean=false) {
        val point=checkNotNull(screen.editor.nodeHeaderPoint(id))
        val held=KeyState.Source { it==GLFW.GLFW_KEY_LEFT_SHIFT && shift }
        KeyState.setSource(held)
        try { click(point) } finally { KeyState.clearSource(held) }
    }
    private fun click(point:Pair<Double,Double>) {
        check(screen.mouseClicked(point.first,point.second,0));screen.mouseReleased(point.first,point.second,0)
    }
    private fun rememberNavigation() { navigationJson=screen.json();navigationCommits=screen.commits }
    private fun navigationUnchanged() {
        check(screen.json()==navigationJson && screen.commits==navigationCommits) { "Group navigation must preserve JSON and document history" }
    }
    private fun toggleGroup(expanded:Boolean) {
        val point=checkNotNull(screen.editor.groupTogglePoint(screen.groupId()))
        check(screen.editor.isMouseOver(point.first,point.second)) { "The group toggle must fit inside the native viewport" }
        click(point);check(screen.editor.isGroupExpanded(screen.groupId())==expanded)
    }
    private fun openInstanceMenu() {
        val editor=screen.editor;val x=editor.x+editor.width-8.0;val y=editor.y+editor.height-8.0
        click(x to y);check(screen.view.selection.isEmpty())
        screen.mouseClicked(x,y,1);screen.mouseReleased(x,y,1);check(editor.hasOpenMenu())
    }
    private fun hoverInstanceMenu(mc:Minecraft) {
        val point=checkNotNull(screen.editor.menuItemPoint(ui("instantiate_node_group")))
        screen.mouseMoved(point.first,point.second)
        val rawX=point.first*mc.window.screenWidth/screen.width;val rawY=point.second*mc.window.screenHeight/screen.height
        GLFW.glfwSetCursorPos(mc.window.window,rawX,rawY)
        mouseMove.invoke(mc.mouseHandler,mc.window.window,rawX,rawY)
    }
    private fun clickInstanceMenu() {
        val point=checkNotNull(screen.editor.menuItemPoint(screen.groups().first().name)) { "The actual Add group instance submenu must expose the saved definition" }
        click(point)
    }
    private fun startDrag() {
        dragBefore=screen.positions();dragJson=screen.json();dragCommits=screen.commits
        val point=checkNotNull(screen.editor.groupHeaderPoint(screen.groupId()))
        check(screen.editor.isMouseOver(point.first,point.second))
        check(screen.mouseClicked(point.first,point.second,0))
        screen.mouseDragged(point.first+31,point.second+19,0,31.0,19.0)
        screen.mouseReleased(point.first+31,point.second+19,0)
    }
    private fun verifyDragAndHistory() {
        val after=screen.positions();val members=screen.groups().first().nodes.toSet()
        val deltas=members.map { id -> after.getValue(id).first-dragBefore.getValue(id).first to after.getValue(id).second-dragBefore.getValue(id).second }.distinct()
        check(deltas.size==1 && deltas.single()!=(0 to 0)) { "Dragging the folded group must move every member exactly once" }
        check(after.filterKeys { it !in members }==dragBefore.filterKeys { it !in members }) { "Moving one instance must preserve every other node position" }
        check(screen.commits==dragCommits+1) { "One native group drag must create exactly one document edit" }
        val moved=screen.json()
        check(screen.keyPressed(GLFW.GLFW_KEY_Z,0,GLFW.GLFW_MOD_CONTROL));check(screen.json()==dragJson)
        check(screen.keyPressed(GLFW.GLFW_KEY_Y,0,GLFW.GLFW_MOD_CONTROL));check(screen.json()==moved)
    }
    private fun screenshot(mc:Minecraft,name:String) {
        Screenshot.grab(mc.gameDirectory,"architect-graph-group-$name.png",mc.mainRenderTarget) { println(it.string) }
    }

    private class GroupScreen(private val buildingMode:Boolean):EditorScreen(tr(if(buildingMode) "building_graph" else "node_graph")) {
        val view=KilaGraphState()
        private var shape=NodeGraph(nodes=listOf(
            GraphNode("slab",part=Part("copycats:copycat_slab"),x=20,y=40),
            GraphNode("full",part=Part("copycats:copycat_block"),x=20,y=180),
            GraphNode("angle",NodeKind.ROTATION,x=250,y=180,rotationAxis=RotationAxis.Z,quarterTurns=1),
            GraphNode("rotate",NodeKind.ROTATE,listOf("slab","angle"),x=460,y=40),
            GraphNode("result",NodeKind.OUTPUT,listOf("rotate"),x=700,y=40)))
        private var building=GraphGroups.create(BuildingGraph(nodes=listOf(
            BuildingNode("empty_wall",BuildingKind.SEGMENT,"Empty wall",20,310,block="minecraft:air",length=1),
            BuildingNode("wall_copycat",BuildingKind.COPYCAT,"Wall Copycat",20,30),
            BuildingNode("alternate",BuildingKind.COPYCAT,"Alternative Copycat",20,170,form="slab@1"),
            BuildingNode("wall",BuildingKind.SEGMENT,"Wall segment",260,40,inputs=listOf("wall_copycat"),module=""),
            BuildingNode("building",BuildingKind.BUILDING,"Building",520,40))),setOf("wall"),"Reusable wall")
        private val library=Library().apply { seed() }
        private val shapePreviews=ShapeNodePreviews({ Form(graph=shape) }) { shape }
        private val buildingPreviews=BuildingNodePreviews({ Building(graph=building) }) { library }
        private data class State(val shape:NodeGraph,val building:BuildingGraph)
        private val undo=ArrayDeque<State>();private val redo=ArrayDeque<State>()
        var commits=0;private set
        private var instances=0
        lateinit var editor:KilaGraphEditor;private set
        override fun titleAvailableWidth()=width-16
        override fun init() {
            if(::editor.isInitialized) editor.dispose()
            editor=addRenderableWidget(KilaGraphEditor(8,29,width-16,height-53,this,view,::diagram,{},::commit,{ error(it) },
                nodePreview=if(buildingMode) null else shapePreviews::get,
                buildingNodePreview=if(buildingMode) buildingPreviews::get else null,
                instantiateGroup={ id -> instances++;if(buildingMode) changeBuilding(GraphGroups.instantiate(building,id)) else changeShape(GraphGroups.instantiate(shape,id)) }))
        }
        fun groups()=if(buildingMode) building.nodeGroups else shape.nodeGroups
        fun groupId()=groups().first().id
        fun json()=if(buildingMode) Json.encode(building) else Json.encode(shape)
        fun positions():Map<String,Pair<Int,Int>> = if(buildingMode) building.nodes.associate { it.id to (it.x to it.y) } else shape.nodes.associate { it.id to (it.x to it.y) }
        fun persisted() {
            if(buildingMode) check(Json.decode<BuildingGraph>(json())==building) else check(Json.decode<NodeGraph>(json())==shape)
        }
        fun rebuild()=rebuildWidgets()
        private fun remember() { undo.addLast(State(shape,building));redo.clear();commits++ }
        private fun changeShape(value:NodeGraph) {
            val next=GraphGroups.synchronize(shape,value);GraphCompiler.structure(next,DEFAULT_GROUPS)
            if(next!=shape) { remember();shape=next }
        }
        private fun changeBuilding(value:BuildingGraph) {
            val next=GraphGroups.synchronize(building,value);BuildingGraphCompiler.structure(next)
            if(next!=building) { remember();building=next }
        }
        private fun commit(data:KilaGraphData) {
            if(buildingMode) {
                val graph=building.copy(nodeGroups=data.nodeGroups)
                val ports=graph.nodes.associate { it.id to BuildingPorts.of(it,graph,true) }
                changeBuilding(DiagramEdits.building(graph,data.nodes.map { DiagramPosition(it.id,it.x,it.y) },data.links.map { DiagramConnection(it.source,it.target,ports.getValue(it.target)[it.slot].key) }))
            } else changeShape(DiagramEdits.shape(shape.copy(nodeGroups=data.nodeGroups),DEFAULT_GROUPS,data.nodes.map { DiagramPosition(it.id,it.x,it.y) },data.links.map { DiagramConnection(it.source,it.target,it.slot.toString()) }))
        }
        private fun diagram():KilaGraphData = if(buildingMode) KilaGraphData(building.nodes.map { n ->
            val ports=BuildingPorts.of(n,building,true)
            KilaNode(n.id,n.name,n.x,n.y,ports.map { it.key },output=n.kind!=BuildingKind.BUILDING,deletable=n.kind!=BuildingKind.BUILDING && n.id!="empty_wall",inputTypes=ports.map { it.accepts.joinToString("|") { kind -> kind.name } },outputType=n.kind.name,previewExpandedByDefault=n.kind==BuildingKind.BUILDING)
        },building.nodes.flatMap { n -> BuildingPorts.of(n,building,true).mapIndexedNotNull { slot,p -> p.source.takeIf(String::isNotEmpty)?.let { KilaLink(it,n.id,slot) } } },building.nodeGroups)
        else KilaGraphData(shape.nodes.map { n -> KilaNode(n.id,ui("node_"+n.kind.name.lowercase()),n.x,n.y,n.inputs.indices.map { "${ui("input")} ${it+1}" },output=n.kind!=NodeKind.OUTPUT,deletable=n.kind!=NodeKind.OUTPUT,inputTypes=n.inputs.indices.map { if(n.kind==NodeKind.ROTATE && it==1) "rotation" else "shape" },outputType=if(n.kind==NodeKind.ROTATION) "rotation" else "shape",previewExpandedByDefault=n.kind==NodeKind.OUTPUT) },
            shape.nodes.flatMap { n -> n.inputs.mapIndexedNotNull { slot,id -> id.takeIf(String::isNotEmpty)?.let { KilaLink(it,n.id,slot) } } },shape.nodeGroups)
        fun verifyInstances() {
            check(instances==1 && groups().size==2)
            check(groups().map { it.effectiveDefinitionId }.distinct().size==1)
            check(groups()[0].nodes.toSet().intersect(groups()[1].nodes.toSet()).isEmpty())
        }
        fun verifySharedEdit() {
            val first=groups()[0];val second=groups()[1]
            if(buildingMode) {
                val copy=building.nodes.single { it.id==second.nodes.single() }
                changeBuilding(building.replace(copy.copy(inputs=listOf("alternate"))))
                val source=building.nodes.single { it.id==first.nodes.single() }
                changeBuilding(building.replace(source.copy(height=source.height+1)))
                check(building.nodes.filter { it.id in first.nodes+second.nodes }.all { it.height==source.height+1 })
                check(building.nodes.single { it.id==first.nodes.single() }.inputs==listOf("wall_copycat"))
                check(building.nodes.single { it.id==second.nodes.single() }.inputs==listOf("alternate"))
            } else {
                val copy=shape.nodes.single { it.id==second.nodes[1] }
                changeShape(shape.replace(copy.copy(inputs=listOf("full",second.nodes[0]))))
                val source=shape.nodes.single { it.id==first.nodes[0] }
                changeShape(shape.replace(source.copy(quarterTurns=2)))
                check(shape.nodes.single { it.id==second.nodes[0] }.quarterTurns==2)
                check(shape.nodes.single { it.id==first.nodes[1] }.inputs==listOf("slab",first.nodes[0]))
                check(shape.nodes.single { it.id==second.nodes[1] }.inputs==listOf("full",second.nodes[0]))
            }
        }
        override fun keyPressed(key:Int,scan:Int,mods:Int):Boolean {
            if(mods and GLFW.GLFW_MOD_CONTROL!=0 && key in setOf(GLFW.GLFW_KEY_Z,GLFW.GLFW_KEY_Y)) {
                val from=if(key==GLFW.GLFW_KEY_Z) undo else redo;val to=if(key==GLFW.GLFW_KEY_Z) redo else undo
                if(from.isNotEmpty()) { to.addLast(State(shape,building));val state=from.removeLast();shape=state.shape;building=state.building;rebuildWidgets() };return true
            }
            return editor.keyPressed(key,scan,mods) || super.keyPressed(key,scan,mods)
        }
        override fun renderContents(g:GuiGraphics,mx:Int,my:Int,delta:Float) { editor.renderPopupOverlay(g,mx,my,delta) }
        override fun mouseClicked(x:Double,y:Double,button:Int):Boolean { if(editor.mouseClicked(x,y,button)) { focused=editor;return true };return super.mouseClicked(x,y,button) }
        override fun mouseReleased(x:Double,y:Double,button:Int)=editor.mouseReleased(x,y,button)
        override fun mouseDragged(x:Double,y:Double,button:Int,dx:Double,dy:Double)=editor.mouseDragged(x,y,button,dx,dy)
        override fun mouseMoved(x:Double,y:Double)=editor.mouseMoved(x,y)
        override fun removed() { editor.dispose();super.removed() }
    }
}
