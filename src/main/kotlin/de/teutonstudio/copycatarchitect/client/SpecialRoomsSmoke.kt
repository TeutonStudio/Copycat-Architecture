package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import org.lwjgl.glfw.GLFW

internal object SpecialRoomsSmoke {
    private var ticks=0
    private var failed=false
    private var scale=1
    private var previous:Screen?=null
    private lateinit var snapshot:Snapshot
    private lateinit var screen:BuildingScreen
    private var before=""
    private var after=""
    private var settledFrames=0
    private fun graph()=checkNotNull(screen.building.graph)
    private fun node(id:String)=graph().nodes.first { it.id==id }
    private fun change(value:BuildingNode)=screen.commitBuildingGraph(graph().replace(value))
    private fun evaluation(g:BuildingGraph)=BuildingGraphCompiler.Evaluation(g,screen.building.palette,ClientState.snapshot.library.forms,ClientState.snapshot.library.modules)
    private fun placement()=Minecraft.getInstance().screen as SpecialPlacementScreen
    private fun openPlacement(id:String)=SpecialPlacementScreen.open(node(id),graph(),::evaluation,::change)
    fun tick():Boolean {
        if(failed) return false
        val mc=Minecraft.getInstance()
        if(ticks==0 && (mc.screen==null || mc.overlay!=null)) return false
        if(ticks in listOf(55,59,63)) {
            val ready=screen.compiledBuildingPreview() ?: return false
            ready.getOrThrow()
            if(GraphPreviewThumbnails.hasPendingVisible()) { settledFrames=0;return false }
            if(++settledFrames<3) return false
        }
        ticks++
        try {
            when(ticks) {
                1 -> {
                    previous=mc.screen;scale=mc.options.guiScale().get();snapshot=ClientState.snapshot
                    ClientState.snapshot=Snapshot(library=Library().apply { seed() })
                    mc.options.guiScale().set(1);mc.resizeDisplay()
                    screen=BuildingScreen().apply { building=SpecialRoomsExample.building() }
                    mc.setScreen(screen);screen.openBuildingGraph(false)
                    check(evaluation(graph()).building().plan.placements.isNotEmpty())
                }
                5 -> {
                    val native=checkNotNull(screen.kilaBuildingGraph)
                    check(native.outputPortType("bay")!=null)
                    val bayPort=BuildingPorts.of(node("room"),graph(),true).indexOfFirst { it.key=="semantic:bay:0:source" }
                    check(native.inputPortType("room",bayPort)==native.outputPortType("bay"))
                    check(native.outputPortType("dormer")!=null)
                    val dormerPort=BuildingPorts.of(node("roof_level"),graph(),true).indexOfFirst { it.key=="semantic:dormer:0:source" }
                    check(native.inputPortType("roof_level",dormerPort)==native.outputPortType("dormer"))
                    SpecialRoomEditors.definition(node("bay"),graph(),::change)
                }
                8 -> shot(mc,"bay-definition-normal")
                10 -> { mc.options.guiScale().set(2);mc.resizeDisplay() }
                13 -> { shot(mc,"bay-definition-compact");mc.screen!!.onClose() }
                15 -> {
                    mc.options.guiScale().set(1);mc.resizeDisplay()
                    SpecialRoomEditors.instances(node("roof_level"),graph(),::change)
                }
                18 -> shot(mc,"dormer-inspector-normal")
                20 -> { mc.options.guiScale().set(2);mc.resizeDisplay();mc.screen!!.mouseScrolled(200.0,120.0,0.0,-20.0) }
                23 -> {
                    shot(mc,"dormer-inspector-compact")
                    val current=mc.screen!!
                    val toggle=current.children().filterIsInstance<Button>().first { it.message.string.startsWith(ui("dormer_open_ceiling")) }
                    click(current,toggle.x+toggle.width/2.0 to toggle.y+toggle.height/2.0)
                    check(current.keyPressed(GLFW.GLFW_KEY_END,0,0));check(current.keyPressed(GLFW.GLFW_KEY_ENTER,0,0))
                    val apply=current.children().filterIsInstance<Button>().first { it.message.string==ui("apply") }
                    click(current,apply.x+apply.width/2.0 to apply.y+apply.height/2.0)
                    check(node("roof_level").dormerInstances.single().openCeiling)
                }
                25 -> { mc.options.guiScale().set(1);mc.resizeDisplay();before=Json.encode(screen.building);openPlacement("room") }
                28 -> shot(mc,"bay-placement-normal")
                30 -> {
                    val p=placement();drag(p,p.gridPoint(5.5,15.5),p.gridPoint(4.5,15.5))
                    check(node("room").bayWindowInstances.single().offset==3)
                    after=Json.encode(screen.building)
                }
                33 -> { mc.options.guiScale().set(2);mc.resizeDisplay() }
                36 -> { shot(mc,"bay-placement-compact");mc.screen!!.onClose();undoRedo() }
                38 -> { mc.options.guiScale().set(1);mc.resizeDisplay();before=Json.encode(screen.building);openPlacement("roof_level") }
                41 -> {
                    shot(mc,"dormer-placement-normal")
                    val p=placement();val a=p.gridPoint(3.5,1.5);val b=p.gridPoint(3.5,20.5)
                    check(p.mouseClicked(a.first,a.second,0));check(p.mouseDragged(b.first,b.second,0,b.first-a.first,b.second-a.second))
                }
                44 -> {
                    shot(mc,"dormer-placement-invalid")
                    val p=placement();val b=p.gridPoint(3.5,20.5);p.mouseReleased(b.first,b.second,0)
                    check(Json.encode(screen.building)==before)
                    drag(p,p.gridPoint(3.5,1.5),p.gridPoint(4.5,1.5))
                    check(node("roof_level").dormerInstances.single().position==P(2,0,-1))
                    after=Json.encode(screen.building)
                }
                47 -> { mc.options.guiScale().set(2);mc.resizeDisplay() }
                50 -> { shot(mc,"dormer-placement-compact");mc.screen!!.onClose();undoRedo() }
                52 -> {
                    mc.options.guiScale().set(1);mc.resizeDisplay();screen.openBuildingSection(BuildingPanelSection.BUILDING)
                    checkNotNull(screen.buildingGraphCanvas).showSection("3d")
                }
                56 -> {
                    shot(mc,"building-normal");settledFrames=0
                    checkNotNull(screen.buildingGraphCanvas).view.yaw=215f
                }
                60 -> {
                    shot(mc,"building-dormer-normal");settledFrames=0
                    mc.options.guiScale().set(2);mc.resizeDisplay()
                    val canvas=checkNotNull(screen.buildingGraphCanvas)
                    val point=canvas.propertyPoint("building",45)
                    val target=canvas.y+50.0
                    screen.mouseScrolled(canvas.x+canvas.width/2.0,canvas.y+canvas.height-8.0,0.0,(target-point.second)/25.0)
                }
                64 -> shot(mc,"building-compact")
                68 -> {
                    mc.options.guiScale().set(scale);mc.resizeDisplay();ClientState.snapshot=snapshot;mc.setScreen(previous)
                    println("ARCHITECT SPECIAL ROOMS UI SMOKE PASSED: typed sources, definition and instance inspectors, ceiling toggle, valid bay/dormer drags, rejected invalid drag, Undo/Redo, JSON, normal/compact maps and building preview")
                    mc.stop()
                }
                else -> return false
            }
        } catch(error:Throwable) {
            failed=true;System.err.println("ARCHITECT SPECIAL ROOMS UI SMOKE FAILED at $ticks: ${error.message}");error.printStackTrace();shot(mc,"failure-$ticks");mc.stop()
        }
        return true
    }
    private fun undoRedo() {
        fun action(key:String) {
            val button=screen.children().filterIsInstance<Button>().first { it !is PrinterEdgeTab && it.visible && it.message.string==ui(key) }
            check(button.active);click(screen,button.x+button.width/2.0 to button.y+button.height/2.0)
        }
        action("undo");check(Json.encode(screen.building)==before)
        action("redo");check(Json.encode(screen.building)==after)
        check(Json.decode<Building>(after)==screen.building)
    }
    private fun click(s:Screen,p:Pair<Double,Double>) { check(s.mouseClicked(p.first,p.second,0));s.mouseReleased(p.first,p.second,0) }
    private fun drag(s:Screen,a:Pair<Double,Double>,b:Pair<Double,Double>) {
        check(s.mouseClicked(a.first,a.second,0));check(s.mouseDragged(b.first,b.second,0,b.first-a.first,b.second-a.second));s.mouseReleased(b.first,b.second,0)
    }
    private fun shot(mc:Minecraft,label:String)=Screenshot.grab(mc.gameDirectory,"architect-special-rooms-$label.png",mc.mainRenderTarget) { println(it.string) }
}
