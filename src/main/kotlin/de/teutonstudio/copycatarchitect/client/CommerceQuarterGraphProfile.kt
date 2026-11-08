package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.CommerceQuarterExample
import de.teutonstudio.copycatarchitect.domain.BuildingNode
import de.teutonstudio.copycatarchitect.domain.Plan
import de.teutonstudio.copycatarchitect.server.Library
import net.minecraft.client.Minecraft
import kotlin.math.ceil

/** Opt-in measurement of the real editor with the Commerce Quarter fixture. */
internal object CommerceQuarterGraphProfile {
    val enabled=java.lang.Boolean.getBoolean("architect.commerceQuarterGraphProfile")
    private lateinit var editor:BuildingScreen
    private var started=0L
    private var interactive=0L
    private var lastFrame=0L
    private var pass=0
    private var step=0
    private var press:Pair<Double,Double>?=null
    private var beforeDrag:BuildingNode?=null
    private lateinit var plan:Plan
    private val samples=mutableListOf<Long>()

    private fun open(mc:Minecraft) {
        step=0
        samples.clear()
        lastFrame=0
        interactive=0
        started=System.nanoTime()
        mc.setScreen(editor)
    }

    fun frame() {
        if(!::editor.isInitialized || Minecraft.getInstance().screen!==editor) return
        val now=System.nanoTime()
        if(interactive==0L && editor.kilaBuildingGraph?.nodeHeaderPoint("building")!=null) interactive=now
        if(step>0 && lastFrame!=0L) samples+=now-lastFrame
        lastFrame=now
    }

    fun tick() {
        val mc=Minecraft.getInstance()
        if(mc.overlay!=null || mc.screen==null) return
        if(!::editor.isInitialized) {
            mc.options.guiScale().set(2)
            mc.resizeDisplay()
            val library=Library().apply {
                seed()
                forms.putAll(CommerceQuarterExample.forms)
                modules.putAll(CommerceQuarterExample.modules)
            }
            ClientState.snapshot=ClientState.snapshot.copy(library=library,table=null,tableBuilding=null)
            editor=BuildingScreen().apply { building=CommerceQuarterExample.building }
            open(mc)
            return
        }
        val graph=checkNotNull(editor.kilaBuildingGraph)
        check(System.nanoTime()-started<120_000_000_000L) { "Commerce Quarter graph profile timed out" }
        if(step==0) {
            if(interactive==0L) return
            val ready=editor.checkedResultPreview() ?: return
            check(ready.placements.size==4289)
            check(editor.planCompilationCount()==1)
            if(pass==0) plan=ready else check(ready===plan)
            println("ARCHITECT COMMERCE GRAPH ${if(pass==0) "cold" else "warm"}: first interactive frame ${ms(interactive-started)} ms, plan observed ${ms(System.nanoTime()-started)} ms")
        }
        val x=graph.x+graph.width-25.0
        val y=graph.y+graph.height-25.0
        when(step++) {
            1,4 -> {
                val zoom=editor.kilaBuildingGraphState.zoom
                graph.mouseMoved(x,y)
                graph.mouseScrolled(x,y,0.0,if(step==2) 1.0 else -1.0)
                check(editor.kilaBuildingGraphState.zoom!=zoom)
            }
            2 -> graph.mouseClicked(x,y,2)
            3 -> {
                val state=editor.kilaBuildingGraphState
                val offset=state.offsetX to state.offsetY
                graph.mouseDragged(x+20,y+12,2,20.0,12.0)
                graph.mouseReleased(x+20,y+12,2)
                check((state.offsetX to state.offsetY)!=offset)
                graph.fit()
            }
            5,6 -> {
                val (px,py)=checkNotNull(graph.nodeHeaderPoint("building"))
                graph.mouseMoved(px,py)
                graph.mouseScrolled(px,py,0.0,1.0)
            }
            7 -> {
                val (px,py)=checkNotNull(graph.nodeHeaderPoint("building"))
                check(graph.isMouseOver(px,py))
                press=px to py
                beforeDrag=editor.building.graph?.nodes?.single { it.id=="building" }
                graph.mouseClicked(px,py,0)
            }
            8 -> {
                val (px,py)=checkNotNull(press)
                graph.mouseDragged(px-12,py+6,0,-12.0,6.0)
                graph.mouseReleased(px-12,py+6,0)
            }
            12 -> {
                val moved=editor.building.graph?.nodes?.single { it.id=="building" }
                check(moved!=beforeDrag) { "Native node drag did not move the result node" }
                val (px,py)=checkNotNull(graph.nodePreviewTogglePoint("building"))
                graph.mouseClicked(px,py,0)
                graph.mouseReleased(px,py,0)
                check(!graph.isNodePreviewExpanded("building"))
            }
            18 -> {
                val (px,py)=checkNotNull(graph.nodePreviewTogglePoint("building"))
                graph.mouseClicked(px,py,0)
                graph.mouseReleased(px,py,0)
                check(graph.isNodePreviewExpanded("building"))
            }
            22 -> check((graph.nodePreviewViewport("building")?.height ?: 0)>=12) {
                "The result preview must be large enough to render its thumbnail"
            }
        }
        if(step<25 || samples.size<120) return
        check(editor.checkedResultPreview()===plan && editor.planCompilationCount()==1) {
            "Layout and preview interactions must retain the compiled plan"
        }
        val sorted=samples.sorted()
        fun percentile(p:Double)=ms(sorted[ceil(p*sorted.size).toInt()-1])
        println("ARCHITECT COMMERCE GRAPH ${if(pass==0) "cold" else "warm"}: frame p95 ${percentile(0.95)} ms, p99 ${percentile(0.99)} ms, ${samples.size} samples, ${editor.planCompilationCount()} total compilations")
        if(pass++==0) open(mc) else {
            println("ARCHITECT COMMERCE GRAPH PROFILE PASSED")
            mc.stop()
        }
    }

    private fun ms(nanos:Long)=nanos/1_000_000.0
}
