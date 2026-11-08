package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen

/** Real mouse/key events exercise transactional arrangement in both supported GUI sizes. */
internal object SegmentArrangementSmoke {
    private var ticks=0
    private var failed=false
    private var scale=1
    private var parent:Screen?=null
    private lateinit var snapshot:Snapshot
    private lateinit var building:BuildingScreen
    private var original=""
    private var applied=""
    private fun editor()=Minecraft.getInstance().screen as SegmentArrangementScreen
    fun tick():Boolean {
        if(failed) return false
        val mc=Minecraft.getInstance()
        if(ticks==0 && (mc.screen==null || mc.overlay!=null)) return false
        ticks++
        try {
            when(ticks) {
                1 -> {
                    parent=mc.screen;scale=mc.options.guiScale().get();snapshot=ClientState.snapshot
                    val lib=Library().apply { seed() };ClientState.snapshot=Snapshot(library=lib)
                    resize(mc,1)
                    val segments=listOf(
                        BuildingNode("grid_a",BuildingKind.SEGMENT,name="A · 2 × 3 × 1",module="",form="multi_material@1",length=2,height=3),
                        BuildingNode("grid_b",BuildingKind.SEGMENT,name="B · 1 × 1 × 2",module="",form="bar@1",role="frame",length=1,height=1,depth=2),
                        BuildingNode("grid_c",BuildingKind.SEGMENT,name="C · 3 × 2 × 1",module="",block="minecraft:glass",length=3,height=2))
                    val pattern=BuildingNode("grid_pattern",BuildingKind.PATTERN,name="Interactive arrangement",inputs=segments.map { it.id })
                    val graph=BuildingGraph.starter().let { it.copy(nodes=it.nodes+segments+pattern) }
                    building=BuildingScreen().apply {
                        building=Building(id="grid_smoke",name="Segment raster",graph=graph)
                        buildingGraphView.selected=pattern.id;buildingGraphView.inspectorSections.setExpanded("properties",true)
                    }
                    mc.setScreen(building);building.openBuildingSection(BuildingPanelSection.SEGMENTS)
                    original=Json.encode(building.building)
                }
                5 -> open()
                8 -> { check(editor().draft.transforms.map { it.offset }==listOf(P(),P(2,0,0),P(3,0,0)));shot(mc,"normal") }
                10 -> {
                    move(1,-2,0)
                    check(actionButton(editor(),"apply").active) { "Overlapping Copycats must remain applicable" }
                    val graph=checkNotNull(building.building.graph).replace(editor().draft.apply(checkNotNull(building.building.graph).nodes.single { it.id=="grid_pattern" }))
                    val plan=BuildingGraphCompiler.Evaluation(graph,building.building.palette,ClientState.snapshot.library.forms,ClientState.snapshot.library.modules).preview("grid_pattern")
                    check(plan.placements.size==13)
                    check(plan.placements.single { it.pos==P() }.groups.map { it.id }.toSet()==setOf("body","cap"))
                    check(Json.encode(building.building)==original)
                    // Incompatible native blocks still remain editable until moved clear.
                    move(2,-3,0)
                    check(!actionButton(editor(),"apply").active)
                    move(2,3,0)
                    move(1,2,-3)
                    check(editor().draft.transforms[1].offset==P(2,3,0))
                    check(actionButton(editor(),"apply").active)
                }
                12 -> {
                    action(editor(),ui("bg_segment_view","X"),literal=true)
                    move(1,2,-1)
                    check(editor().draft.transforms[1].offset==P(2,4,2))
                }
                14 -> {
                    action(editor(),ui("bg_segment_view","Y"),literal=true)
                    move(2,2,3)
                    check(editor().draft.transforms[2].offset==P(5,0,3))
                    resize(mc,2)
                    check(editor().width==427 && editor().height==240)
                }
                17 -> {
                    val screen=editor();val before=screen.draft.transforms
                    val point=screen.segmentPoint(2);val end=screen.gridPoint(7.5,4.5)
                    clickDown(screen,point,0)
                    screen.mouseDragged(end.first,end.second,0,end.first-point.first,end.second-point.second)
                    screen.mouseReleased(end.first,end.second,1)
                    check(screen.draft.transforms!=before)
                    check(screen.keyPressed(256,0,0));screen.mouseReleased(end.first,end.second,0)
                    check(screen.draft.transforms==before)
                    check(Json.encode(building.building)==original)
                    val at=screen.gridPoint(0.0,0.0)
                    screen.mouseScrolled(200.0,120.0,0.0,2.0)
                    check(screen.gridPoint(0.0,0.0)!=at)
                    clickDown(screen,200.0 to 120.0,2);check(screen.mouseDragged(210.0,125.0,2,10.0,5.0));screen.mouseReleased(210.0,125.0,2)
                    action(screen,"fit_graph")
                    check(screen.children().filterIsInstance<Button>().all { it.x>=0 && it.x+it.width<=screen.width && it.y>=0 && it.y+it.height<=screen.height })
                }
                20 -> shot(mc,"compact")
                22 -> {
                    action(editor(),"apply");check(mc.screen===building)
                    applied=Json.encode(building.building);check(applied!=original)
                    val graph=checkNotNull(building.building.graph)
                    val node=graph.nodes.single { it.id=="grid_pattern" }
                    check(node.patternAxis=="transform" && node.transforms.map { it.offset }==listOf(P(),P(2,4,2),P(5,0,3)))
                    check(Json.decode<Building>(applied)==building.building)
                    check(BuildingGraphCompiler.Evaluation(graph,building.building.palette,ClientState.snapshot.library.forms,ClientState.snapshot.library.modules).segment(node.id).cells.size==14)
                    action(building,"undo");check(Json.encode(building.building)==original)
                    action(building,"redo");check(Json.encode(building.building)==applied)
                }
                25 -> { open();check(editor().draft.transforms[1].offset==P(2,4,2));move(0,-1,0);action(editor(),"cancel");check(Json.encode(building.building)==applied) }
                28 -> {
                    resize(mc,scale);ClientState.snapshot=snapshot;mc.setScreen(parent)
                    println("ARCHITECT SEGMENT ARRANGEMENT UI SMOKE PASSED: overlapping Copycats with material groups, three unequal segments, X/Y/Z dragging, hidden coordinates, native conflict recovery, stable grid, zoom and pan, wrong release, Escape cancellation, compact layout, atomic Apply/Undo/Redo, JSON and reopening")
                    mc.stop()
                }
                else -> return false
            }
        } catch(error:Throwable) {
            failed=true;shot(mc,"failure-$ticks")
            throw IllegalStateException("Segment arrangement smoke failed at tick $ticks",error)
        }
        return true
    }
    private fun open() {
        val canvas=checkNotNull(building.buildingGraphCanvas)
        canvas.showSection("properties")
        val point=canvas.propertyPoint("grid_pattern",62)
        val target=point.second.coerceIn(canvas.y+70.0,canvas.y+canvas.height-18.0)
        if(point.second!=target) building.mouseScrolled(canvas.x+canvas.width/2.0,canvas.y+canvas.height-8.0,0.0,(target-point.second)/25)
        click(building,checkNotNull(building.buildingGraphCanvas).propertyPoint("grid_pattern",62))
        check(Minecraft.getInstance().screen is SegmentArrangementScreen)
    }
    private fun move(index:Int,u:Int,v:Int) {
        val screen=editor();val start=screen.segmentPoint(index)
        val origin=screen.gridPoint(0.0,0.0);val delta=screen.gridPoint(u.toDouble(),v.toDouble())
        val end=start.first+delta.first-origin.first to start.second+delta.second-origin.second
        clickDown(screen,start,0)
        check(screen.mouseDragged(end.first,end.second,0,end.first-start.first,end.second-start.second))
        check(Json.encode(building.building)==original || Json.encode(building.building)==applied)
        check(screen.mouseReleased(end.first,end.second,0))
    }
    private fun actionButton(screen:Screen,key:String,literal:Boolean=false)=screen.children().filterIsInstance<Button>().first { it.visible && it.message.string==if(literal) key else ui(key) }
    private fun action(screen:Screen,key:String,literal:Boolean=false) { val b=actionButton(screen,key,literal);check(b.active);click(screen,b.x+b.width/2.0 to b.y+b.height/2.0) }
    private fun clickDown(screen:Screen,point:Pair<Double,Double>,button:Int) { check(screen.mouseClicked(point.first,point.second,button)) }
    private fun click(screen:Screen,point:Pair<Double,Double>) { clickDown(screen,point,0);screen.mouseReleased(point.first,point.second,0) }
    private fun resize(mc:Minecraft,value:Int) { mc.options.guiScale().set(value);mc.resizeDisplay() }
    private fun shot(mc:Minecraft,name:String) { Screenshot.grab(mc.gameDirectory,"architect-segment-arrangement-$name.png",mc.mainRenderTarget) { println(it.string) } }
}
