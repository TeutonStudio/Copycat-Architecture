package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen

/** Exercises the real native grid, typed ports, dimensions, and document transaction. */
internal object CountArrangementSmoke {
    private var ticks=0
    private lateinit var screen:BuildingScreen
    private lateinit var snapshot:Snapshot
    private var scale=1
    private var original=""
    private var applied=""
    fun tick():Boolean {
        val mc=Minecraft.getInstance()
        if(ticks==0 && (mc.screen==null || mc.overlay!=null)) return false
        ticks++
        try {
            when(ticks) {
                1 -> {
                    snapshot=ClientState.snapshot;scale=mc.options.guiScale().get()
                    ClientState.snapshot=Snapshot(library=Library().apply { seed() })
                    mc.options.guiScale().set(1);mc.resizeDisplay()
                    val base=BuildingGraph.starter()
                    val counts=listOf(BuildingNode("two",BuildingKind.INT,countValue=2,x=20,y=20),BuildingNode("three",BuildingKind.INT,countValue=3,x=20,y=160),
                        BuildingNode("sum",BuildingKind.INT_ADD,inputs=listOf("two","three"),x=230,y=20))
                    val sources=listOf(BuildingNode("stone",BuildingKind.COPYCAT,block="minecraft:stone"),BuildingNode("glass",BuildingKind.COPYCAT,block="minecraft:glass"))
                    val wall=BuildingNode("positioned",BuildingKind.SEGMENT,name="Copycat arrangement",inputs=sources.map { it.id },length=4,height=4,depth=3,
                        dimensions=mapOf("length" to "sum"),x=450,y=20)
                    val graph=base.copy(nodes=base.nodes+counts+sources+wall)
                    check(graph.resolvedNode(wall).length==5)
                    screen=BuildingScreen().apply { building=Building(id="count_smoke",graph=graph);buildingGraphView.selected=wall.id;buildingGraphView.inspectorSections.setExpanded("properties",true) }
                    mc.setScreen(screen);screen.openBuildingSection(BuildingPanelSection.SEGMENTS)
                    original=Json.encode(screen.building)
                }
                5 -> open()
                8 -> {
                    val editor=mc.screen as SegmentArrangementScreen
                    val start=editor.segmentPoint(1);val origin=editor.gridPoint(0.0,0.0);val target=editor.gridPoint(3.0,-2.0)
                    check(editor.mouseClicked(start.first,start.second,0))
                    check(editor.mouseDragged(start.first+target.first-origin.first,start.second+target.second-origin.second,0,target.first-origin.first,target.second-origin.second))
                    editor.mouseReleased(start.first+target.first-origin.first,start.second+target.second-origin.second,0)
                    check(editor.draft.transforms[1].offset==P(4,2,0))
                    check(Json.encode(screen.building)==original)
                    shot("normal")
                }
                10 -> {
                    val editor=mc.screen as SegmentArrangementScreen
                    editor.changeView(SegmentView.X)
                    check(editor.keyPressed(262,0,0));check(editor.keyPressed(265,0,0))
                    check(editor.draft.transforms[1].offset==P(4,3,1))
                    mc.options.guiScale().set(2);mc.resizeDisplay()
                }
                13 -> {
                    val editor=mc.screen as SegmentArrangementScreen
                    check(editor.width==427 && editor.height==240)
                    check(editor.children().filterIsInstance<Button>().all { it.x>=0 && it.y>=0 && it.x+it.width<=editor.width && it.y+it.height<=editor.height })
                    shot("compact")
                }
                15 -> {
                    click(mc.screen!!,"apply")
                    check(mc.screen===screen)
                    applied=Json.encode(screen.building);check(applied!=original)
                    val graph=screen.building.graph!!;val n=graph.nodes.single { it.id=="positioned" }
                    check(n.length==4 && n.copycatArrangement==true && n.offsets[1]==P(4,3,1))
                    val result=BuildingGraphCompiler.Evaluation(graph,screen.building.palette,ClientState.snapshot.library.forms,ClientState.snapshot.library.modules).segment(n.id)
                    check(result.length==5 && result.cells.map { it.cell.pos }.toSet()==setOf(P(),P(4,3,1)))
                    check(Json.decode<Building>(applied)==screen.building)
                    click(screen,"undo");check(Json.encode(screen.building)==original)
                    click(screen,"redo");check(Json.encode(screen.building)==applied)
                }
                18 -> open()
                21 -> {
                    val editor=mc.screen as SegmentArrangementScreen
                    editor.keyPressed(263,0,0);click(editor,"cancel")
                    check(Json.encode(screen.building)==applied)
                    mc.options.guiScale().set(scale);mc.resizeDisplay();ClientState.snapshot=snapshot
                    println("ARCHITECT COUNT ARRANGEMENT UI SMOKE PASSED: typed integer dimensions, unchanged fallback, real Copycat dragging, keyboard movement, hidden axis, two GUI sizes, one Apply/Undo/Redo transaction, cancellation and JSON roundtrip")
                    mc.stop()
                }
                else -> return false
            }
        } catch(error:Throwable) { shot("failure-$ticks");throw IllegalStateException("Count arrangement smoke failed at tick $ticks",error) }
        return true
    }
    private fun open() {
        val graph=screen.building.graph!!;val n=graph.nodes.single { it.id=="positioned" }
        SegmentArrangementScreen.open(n,graph,BuildingGraphCompiler.Evaluation(graph,screen.building.palette,ClientState.snapshot.library.forms,ClientState.snapshot.library.modules)) {
            screen.commitBuildingGraph(graph.replace(it))
        }
    }
    private fun click(screen:Screen,key:String) { val b=screen.children().filterIsInstance<Button>().first { it.visible && it.message.string==ui(key) };check(b.active);check(screen.mouseClicked(b.x+b.width/2.0,b.y+b.height/2.0,0));screen.mouseReleased(b.x+b.width/2.0,b.y+b.height/2.0,0) }
    private fun shot(name:String) { val mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"architect-count-arrangement-$name.png",mc.mainRenderTarget) { println(it.string) } }
}
