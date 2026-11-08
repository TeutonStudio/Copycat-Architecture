package de.teutonstudio.copycatarchitect.client

import com.lowdragmc.lowdraglib2.gui.ui.utils.KeyState
import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.Json
import de.teutonstudio.copycatarchitect.server.Library
import de.teutonstudio.copycatarchitect.server.PrinterView
import de.teutonstudio.copycatarchitect.server.Snapshot
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.lwjgl.glfw.GLFW

/** Selection must survive mouse-up, modifier release and subsequent enclosing-screen renders. */
internal object GraphSelectionSmoke {
    private var ticks=0
    private var mode=0
    private var failed=false
    private lateinit var screen:Screen
    private lateinit var editor:KilaGraphEditor
    private lateinit var state:KilaGraphState
    private var point=0.0 to 0.0
    private var before=""
    private val expected=setOf("first","second")
    private fun json()=when(val s=screen) {
        is ShapeScreen -> Json.encode(s.form)
        is BuildingScreen -> Json.encode(s.building)
        else -> error("Unexpected screen")
    }
    private fun click(x:Double,y:Double) {
        check(screen.mouseClicked(x,y,0));screen.mouseReleased(x,y,0)
        refreshEditor()
    }
    private fun selection() {
        check(state.selection==expected) { "Mode $mode: selection lost: ${state.selection}, inspector=${state.selected}" }
        check(json()==before) { "Selection must not edit the document" }
    }
    private fun open(mc:Minecraft) {
        mc.options.guiScale().set(if(mode in setOf(2,3,5)) 2 else 1);mc.resizeDisplay()
        val form=Form(graph=NodeGraph(nodes=listOf(
                GraphNode("first",part=Part("copycats:copycat_block"),x=20,y=20),
                GraphNode("second",part=Part("copycats:copycat_slab"),x=250,y=20),
                GraphNode("result",NodeKind.OUTPUT,listOf("first"),x=480,y=20))))
        val printer=if(mode>=4) PrinterView(form=form) else null
        ClientState.snapshot=ClientState.snapshot.copy(printer=printer)
        screen=if(mode%2==0 || mode>=4) ShapeScreen(printerSession=printer).apply {
            this.form=form
            graphMode=true;graphInspector=false
        } else BuildingScreen().apply {
            building=Building(schema=2,graph=BuildingGraph(nodes=listOf(
                BuildingNode("first",BuildingKind.COPYCAT,"First",20,20),
                BuildingNode("second",BuildingKind.COPYCAT,"Second",250,20),
                BuildingNode("result",BuildingKind.BUILDING,"Result",480,20))))
            graphMode=true;graphInspector=false
        }
        mc.setScreen(screen)
        refreshEditor()
        before=json()
    }
    private fun refreshEditor() {
        when(val s=screen) {
            is ShapeScreen -> { editor=checkNotNull(s.kilaGraph);state=s.kilaGraphState }
            is BuildingScreen -> { editor=checkNotNull(s.kilaBuildingGraph);state=s.kilaBuildingGraphState }
        }
    }
    fun tick():Boolean {
        if(failed) return false
        val mc=Minecraft.getInstance()
        if(ticks==0 && (mc.screen==null || mc.overlay!=null)) return false
        ticks++
        val step=(ticks-1)%60
        try {
            when(step) {
                0 -> {
                    if(mode==0) ClientState.snapshot=Snapshot(library=Library().apply { seed() })
                    open(mc)
                }
                5 -> editor.fit()
                10 -> {
                    val first=checkNotNull(editor.nodeHeaderPoint("first"));click(first.first,first.second)
                    point=checkNotNull(editor.nodeHeaderPoint("second"))
                    val shift=KeyState.Source { it==GLFW.GLFW_KEY_LEFT_SHIFT }
                    KeyState.setSource(shift)
                    try { check(screen.mouseClicked(point.first,point.second,0)) }
                    finally { KeyState.clearSource(shift) }
                    selection()
                }
                15 -> { selection();screen.mouseReleased(point.first,point.second,0);refreshEditor();selection() }
                17 -> {
                    selection();screen.resize(mc,mc.window.guiScaledWidth,mc.window.guiScaledHeight)
                    refreshEditor()
                }
                20 -> {
                    selection()
                    // A selection rectangle ends over the nodes after several real render frames.
                    point=editor.x+2.0 to editor.y+2.0
                    check(screen.mouseClicked(point.first,point.second,0))
                    screen.mouseDragged(editor.x+editor.width-2.0,editor.y+editor.height-2.0,0,
                        editor.width-4.0,editor.height-4.0)
                }
                25 -> { screen.mouseReleased(editor.x+editor.width-2.0,editor.y+editor.height-2.0,0);refreshEditor() }
                30 -> {
                    check(state.selection.containsAll(expected) && state.selection.size>=2) { "Mode $mode: box selection lost: ${state.selection}" }
                    check(json()==before)
                    point=checkNotNull(editor.nodeHeaderPoint("first"));click(point.first,point.second)
                }
                35 -> {
                    check(state.selection==setOf("first")) { "Plain click must select a single node" }
                    click(editor.x+2.0,editor.y+2.0)
                }
                40 -> {
                    check(state.selection.isEmpty()) { "Blank click must clear selection" }
                    check(json()==before)
                }
                45 -> {
                    when(val s=screen) {
                        is ShapeScreen -> s.selectShapeNode("second")
                        is BuildingScreen -> s.selectBuildingNode("second")
                    }
                    screen.resize(mc,mc.window.guiScaledWidth,mc.window.guiScaledHeight);refreshEditor()
                }
                50 -> {
                    check(state.selection==setOf("second")) { "An explicit inspector choice must survive rebuilding" }
                    check(json()==before)
                    println("ARCHITECT GRAPH SELECTION MODE $mode PASSED: held Shift click, early modifier release, box selection, plain/blank clicks across rendered frames")
                }
                59 -> {
                    mode++
                    if(mode==6) {
                        println("ARCHITECT GRAPH SELECTION SMOKE PASSED: shape/building/printer, normal/compact, rebuilds, document unchanged")
                        mc.stop()
                    }
                }
                else -> return false
            }
        } catch(error:Throwable) {
            failed=true
            throw IllegalStateException("Graph selection smoke failed in mode $mode at step $step",error)
        }
        return true
    }
}
