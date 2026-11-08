package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import org.lwjgl.glfw.GLFW

/** Actual room sockets and inspector edits, including Undo/Redo and both GUI sizes. */
internal object RoomMountingSmoke {
    private var ticks=0
    private var failed=false
    private var previous:Screen?=null
    private var scale=1
    private lateinit var snapshot:Snapshot
    private lateinit var screen:BuildingScreen
    private var before=""
    private var after=""
    private fun graph()=checkNotNull(screen.building.graph)
    private fun canvas()=checkNotNull(screen.buildingGraphCanvas)
    fun tick():Boolean {
        if(failed) return false
        val mc=Minecraft.getInstance()
        if(ticks==0 && (mc.screen==null || mc.overlay!=null)) return false
        ticks++
        try {
            when(ticks) {
                1 -> {
                    previous=mc.screen;scale=mc.options.guiScale().get();snapshot=ClientState.snapshot
                    val library=Library().apply { seed();forms["skin@1"]=Form(id="skin",parts=listOf(Part("create:copycat_panel",tilt=3))) }
                    ClientState.snapshot=Snapshot(library=library)
                    val initial=BuildingGraph.explicitStarter().editRoomGeometry("room_geometry",RoomGeometry.cells(listOf(Rect(width=7,length=7))).toList())
                    val skin=BuildingNode("skin",BuildingKind.SEGMENT,name="3/16 · stone",module="",form="skin@1",length=7,height=4)
                    val back=BuildingAssembly.newNode(BuildingKind.SEGMENT_MIRROR,"back","3/16 · wood").copy(inputs=listOf(skin.id),rotationAxis=RotationAxis.Z,materialMode=MaterialMode.GROUPED,role="frame")
                    val pair=BuildingAssembly.newNode(BuildingKind.PATTERN,"pair","3/16 + 3/16").copy(inputs=listOf(skin.id,back.id),patternAxis="depth")
                    val edge=BuildingNode("edge",BuildingKind.SEGMENT,name="Vertical edge segment",module="",form="bar@1",length=2,height=4,depth=2,role="frame")
                    val room=initial.nodes.single { it.id=="room" }.copy(walls=initial.nodes.single { it.id=="room" }.walls.mapValues { pair.id })
                    val g=initial.copy(nodes=initial.nodes+listOf(skin,back,pair,edge)).replace(room)
                        .replace(initial.nodes.single { it.id=="plate_layout" }.copy(length=11,height=11))
                    mc.options.guiScale().set(1);mc.resizeDisplay()
                    screen=BuildingScreen().apply { building=Building(id="room_mounting_smoke",name="Layered wall and vertical edges",graph=g);buildingGraphView.selected="room" }
                    mc.setScreen(screen);screen.openBuildingGraph(false)
                }
                5 -> {
                    val native=checkNotNull(screen.kilaBuildingGraph)
                    val ports=BuildingPorts.of(graph().resolvedNode("room"),graph(),true)
                    check(ports.count { it.key.startsWith("edge:") }==4)
                    for((index,port) in ports.withIndex()) if(port.key.startsWith("edge:")) check(native.inputPortType("room",index)==native.outputPortType("edge"))
                    native.fit()
                }
                8 -> { shot(mc,"graph-normal");screen.openBuildingSection(BuildingPanelSection.ROOMS) }
                11 -> {
                    before=Json.encode(screen.building);revealEdge()
                    click(canvas().inputPoint("room","edge:0:0"));check(canvas().hasOpenMenu())
                    val port=BuildingPorts.of(graph().resolvedNode("room"),graph()).single { it.key=="edge:0:0" }
                    val choices=graph().nodes.filter { it.id!="room" && it.kind in port.accepts && runCatching { graph().connect("room",port.key,it.id) }.isSuccess }
                    check(screen.keyPressed(GLFW.GLFW_KEY_HOME,0,0))
                    repeat(choices.indexOfFirst { it.id=="edge" }+1) { check(screen.keyPressed(GLFW.GLFW_KEY_DOWN,0,0)) }
                    check(screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0))
                    check(graph().resolvedNode("room").verticalEdges?.get("0:0")=="edge")
                    check(canvas().previewPlan().isSuccess)
                    after=Json.encode(screen.building)
                    action("undo");check(Json.encode(screen.building)==before)
                    action("redo");check(Json.encode(screen.building)==after)
                    check(Json.decode<Building>(after)==screen.building)
                }
                15 -> { revealEdge("edge:7:7");shot(mc,"ports-normal") }
                18 -> { mc.options.guiScale().set(2);mc.resizeDisplay();revealEdge("edge:7:7") }
                22 -> { check(screen.width==427 && screen.height==240);shot(mc,"ports-compact") }
                25 -> { mc.options.guiScale().set(1);mc.resizeDisplay();screen.openBuildingSection(BuildingPanelSection.SEGMENTS) }
                28 -> {
                    val point=checkNotNull(screen.buildingDefinitionList).definitionPoint("pair")
                    click(point);canvas().showSection("3d")
                    val plan=canvas().previewPlan().getOrThrow()
                    val occupied=mutableSetOf<Int>()
                    for(p in plan.placements.filter { it.pos.x==0 && it.pos.y==0 }) Geometry.solid(p.parts).voxels { x,y,z -> if(x==8 && y==8) occupied+=p.pos.z*16+z }
                    check(occupied==(13..18).toSet())
                }
                32 -> shot(mc,"joined-layers-normal")
                33 -> {
                    screen.openBuildingSection(BuildingPanelSection.ROOMS);canvas().showSection("3d")
                    check(graph().nodes.single { it.id==screen.buildingGraphView.selected }.kind==BuildingKind.ROOM)
                    val plan=canvas().previewPlan().getOrThrow()
                    check(plan.placements.all { it.pos.x in 0..6 && it.pos.z in 0..6 })
                    check(plan.placements.none { it.pos in plan.clear })
                }
                36 -> shot(mc,"shared-corners-normal")
                39 -> {
                    mc.options.guiScale().set(scale);mc.resizeDisplay();ClientState.snapshot=snapshot;mc.setScreen(previous)
                    println("ARCHITECT ROOM MOUNTING UI SMOKE PASSED: four actual typed vertical edge sockets, real inspector selection, Undo/Redo, JSON, normal and compact ports, six joined layer pixels in the segment preview, and shared terminal corner columns in the room preview")
                    mc.stop()
                }
                else -> return false
            }
        } catch(error:Throwable) {
            failed=true;System.err.println("ARCHITECT ROOM MOUNTING UI SMOKE FAILED at $ticks: ${error.message}");error.printStackTrace();shot(mc,"failure-$ticks");mc.stop()
        }
        return true
    }
    private fun revealEdge(key:String="edge:0:0") {
        canvas().showSection("properties")
        val point=canvas().inputPoint("room",key)
        val target=point.second.coerceIn(canvas().y+70.0,canvas().y+canvas().height-18.0)
        if(target!=point.second) check(screen.mouseScrolled(canvas().x+canvas().width/2.0,canvas().y+canvas().height-8.0,0.0,(target-point.second)/25.0))
    }
    private fun click(point:Pair<Double,Double>) {
        check(point.first in 0.0..screen.width.toDouble() && point.second in 0.0..screen.height.toDouble()) { "Room input is off screen: $point" }
        check(screen.mouseClicked(point.first,point.second,0));screen.mouseReleased(point.first,point.second,0)
    }
    private fun action(key:String) {
        val button=screen.children().filterIsInstance<Button>().first { it !is PrinterEdgeTab && it.visible && it.message.string==ui(key) }
        check(button.active);click(button.x+button.width/2.0 to button.y+button.height/2.0)
    }
    private fun shot(mc:Minecraft,label:String)=Screenshot.grab(mc.gameDirectory,"architect-room-mounting-$label.png",mc.mainRenderTarget) { println(it.string) }
}
