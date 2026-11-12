package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import kotlin.math.*

/** Orthographic block placement, committed as one ordinary graph edit. */
class SegmentArrangementScreen private constructor(private val parent:Screen?,private val node:BuildingNode,
    val draft:SegmentArrangement,private val names:List<String>,private val commit:(BuildingNode)->Unit):EditorScreen(tr(if(node.kind==BuildingKind.SEGMENT) "bg_copycat_arrange" else "bg_segment_arrange")) {
    var selected=0;private set
    var view=SegmentView.Z;private set
    private var projection:BuildingGridProjection?=null
    private var status=""
    private var validation:Result<FixedSegment> = runCatching { draft.result() }
    private data class Drag(val index:Int,val start:P,val original:SegmentTransform)
    private var drag:Drag?=null
    private var panning=false
    private val mapTop=90
    private val mapWidth get()=(width-16).coerceAtLeast(1)
    private val mapHeight get()=(height-154).coerceAtLeast(12)
    private val colors=listOf(0xff61b8d0.toInt(),0xffd19c65.toInt(),0xff97bc73.toInt(),0xffbe8bd0.toInt(),0xffd87880.toInt(),0xff85a1de.toInt())
    private var projected:List<Set<P>> = emptyList()
    override fun titleAvailableWidth()=width-16
    private fun refresh() {
        projected=draft.sources.indices.map { draft.projected(it,view) }
        validation=runCatching { draft.result() }
    }
    override fun init() {
        resetDropdown();refresh()
        if(projection?.let { it.width!=mapWidth || it.height!=mapHeight }==true) projection=null
        val selectorWidth=width-180
        select(8,28,selectorWidth,selected,names.indices.toList(),{ "${it+1}. ${names[it]}" }) { selected=it;rebuildWidgets() }
        SegmentView.entries.forEachIndexed { i,axis ->
            (button(width-164+i*52,28,48,Component.literal(ui("bg_segment_view",axis.name))) { changeView(axis) } as EditorButton).selected=axis==view
        }
        val cellWidth=(width-32)/5
        val t=draft.transforms[selected]
        listOf("X","Y","Z").forEachIndexed { i,axis ->
            val value=listOf(t.offset.x,t.offset.y,t.offset.z)[i]
            number(8+i*(cellWidth+4),52,cellWidth,value,draft.offsetRange,{ "$axis $it" }) { value -> change {
                val current=draft.transforms[selected]
                draft.update(selected,current.copy(offset=when(i) { 0->current.offset.copy(x=value);1->current.offset.copy(y=value);else->current.offset.copy(z=value) }))
            } }
        }
        if(!draft.copycats) select(8+3*(cellWidth+4),52,cellWidth,t.axis,listOf(RotationAxis.X,RotationAxis.Y,RotationAxis.Z),{ ui("bg_segment_turn_axis",it.name) }) { axis -> change { draft.update(selected,draft.transforms[selected].copy(axis=axis)) } }
        if(!draft.copycats) select(8+4*(cellWidth+4),52,cellWidth,t.quarterTurns,(0..3).toList(),{ "${it*90}°" }) { q -> change { draft.update(selected,draft.transforms[selected].copy(quarterTurns=q)) } }
        val buttonWidth=(width-28)/4
        button(8,height-30,buttonWidth,"fit_graph") { projection=null }
        if(!draft.copycats) button(12+buttonWidth,height-30,buttonWidth,"bg_segment_text") {
            EditorValueInput.text(tr("bg_segment_transforms"),SegmentTransformEditor.format(draft.transforms,node.inputs.size),2048,
                { runCatching { SegmentTransformEditor.parse(it,node.inputs.size) }.isSuccess }) { text ->
                change { SegmentTransformEditor.parse(text,node.inputs.size).forEachIndexed { i,value -> draft.update(i,value) } }
            }
        }
        button(16+buttonWidth*2,height-30,buttonWidth,"cancel") { onClose() }
        button(20+buttonWidth*3,height-30,buttonWidth,"apply") {
            runCatching { commit(draft.apply(node)) }.onSuccess { onClose() }.onFailure { status=it.message.orEmpty() }
        }.active=validation.isSuccess
    }
    private fun change(action:()->Unit) {
        runCatching(action).onSuccess { status="";refresh();rebuildWidgets() }.onFailure { status=it.message.orEmpty() }
    }
    fun changeView(axis:SegmentView) {
        cancelDrag();view=axis;projection=null;refresh();rebuildWidgets()
    }
    private fun cancelDrag() {
        drag?.let { draft.update(it.index,it.original) };drag=null;panning=false;status="";refresh()
    }
    private fun fitted():BuildingGridProjection {
        projection?.let { return it }
        return BuildingGridProjection.fit(8,mapTop,mapWidth,mapHeight,projected.flatten().toSet(),24.0).also { projection=it }
    }
    fun gridPoint(u:Double,v:Double):Pair<Double,Double> {
        val p=fitted();return p.x+(u-p.minX)*p.step to p.y+(v-p.minZ)*p.step
    }
    fun segmentPoint(index:Int):Pair<Double,Double> {
        val cell=projected[index].first();return gridPoint(cell.x+0.5,cell.z+0.5)
    }
    override fun renderWorkspace(g:GuiGraphics) {
        val p=fitted()
        ArchitectTheme.inset(g,8,mapTop,mapWidth,mapHeight,ArchitectTheme.blueprint)
        g.enableScissor(8,mapTop,8+mapWidth,mapTop+mapHeight)
        for(u in p.columns) { val x=p.px(u).roundToInt();g.fill(x,mapTop,x+1,mapTop+mapHeight,ArchitectTheme.grid) }
        for(v in p.rows) { val y=p.py(v).roundToInt();g.fill(8,y,8+mapWidth,y+1,ArchitectTheme.grid) }
        // Draw the selected input last so it stays reachable even when projected silhouettes overlap.
        for(i in projected.indices.filter { it!=selected }+selected) {
            val cells=projected[i];val color=colors[i%colors.size]
            for(cell in cells) {
                val x=p.px(cell.x).roundToInt();val y=p.py(cell.z).roundToInt()
                val right=p.px(cell.x+1).roundToInt();val bottom=p.py(cell.z+1).roundToInt()
                g.fill(x+if(p.step>=4) 1 else 0,y+if(p.step>=4) 1 else 0,right,bottom,color)
                val edge=if(i==selected) ArchitectTheme.brass else ArchitectTheme.text
                if(cell+P(0,0,-1) !in cells) g.fill(x,y,right,y+1,edge)
                if(cell+P(0,0,1) !in cells) g.fill(x,bottom-1,right,bottom,edge)
                if(cell+P(-1,0,0) !in cells) g.fill(x,y,x+1,bottom,edge)
                if(cell+P(1,0,0) !in cells) g.fill(right-1,y,right,bottom,edge)
            }
            cells.minWithOrNull(compareBy({ it.z },{ it.x }))?.let { cell ->
                val x=p.px(cell.x).roundToInt()+2;val y=p.py(cell.z).roundToInt()+2
                if(p.step>=10) { g.fill(x,y,x+font.width("${i+1}")+3,y+10,ArchitectTheme.shadow);g.drawString(font,"${i+1}",x+1,y+1,ArchitectTheme.text,false) }
            }
        }
        g.flush();g.disableScissor()
    }
    override fun renderContents(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        val s=draft.oriented(selected)
        label(g,font.plainSubstrByWidth(ui("bg_segment_plane",view.horizontal,view.vertical,s.length,s.height,s.depth),width-16),8,76,colors[selected%colors.size])
        val error=validation.exceptionOrNull()?.message ?: status.takeIf { it.isNotBlank() }
        val summary=validation.getOrNull()?.let { ui("bg_segment_size",it.length,it.height,it.depth) }.orEmpty()
        label(g,font.plainSubstrByWidth(error ?: summary,width-16),8,height-59,if(error!=null) ArchitectTheme.danger else ArchitectTheme.text)
        label(g,font.plainSubstrByWidth(ui("bg_segment_drag_hint"),width-16),8,height-46,ArchitectTheme.muted)
    }
    override fun mouseClicked(mx:Double,my:Double,button:Int):Boolean {
        if(dropdownClick(mx,my,button) || super.mouseClicked(mx,my,button)) return true
        val p=fitted();if(!p.contains(mx,my)) return false
        if(button==2) { panning=true;return true }
        if(button!=0) return true
        val cell=p.cell(mx,my)
        val hit=(listOf(selected)+projected.indices.reversed().filter { it!=selected }).firstOrNull { cell in projected[it] } ?: return true
        selected=hit;drag=Drag(hit,cell,draft.transforms[hit]);rebuildWidgets();return true
    }
    override fun mouseDragged(mx:Double,my:Double,button:Int,dx:Double,dy:Double):Boolean {
        if(panning && button==2) { val p=fitted();projection=p.copy(x=p.x+dx,y=p.y+dy);return true }
        val d=drag ?: return false;if(button!=0) return false
        val cell=fitted().cell(mx,my)
        runCatching { draft.move(d.index,d.original,view,cell.x-d.start.x,cell.z-d.start.z) }
            .onSuccess { status="" }.onFailure { status=it.message.orEmpty() }
        refresh();return true
    }
    override fun mouseReleased(mx:Double,my:Double,button:Int):Boolean {
        if(button==2 && panning) { panning=false;return true }
        if(button!=0 || drag==null) return super.mouseReleased(mx,my,button)
        drag=null;rebuildWidgets();return true
    }
    override fun mouseScrolled(mx:Double,my:Double,dx:Double,dy:Double):Boolean {
        if(super.mouseScrolled(mx,my,dx,dy)) return true
        if(drag!=null || panning) return true
        val p=fitted();if(!p.contains(mx,my)) return false
        val step=(p.step*1.15.pow(dy)).coerceIn(1.0,48.0);val ratio=step/p.step
        projection=p.copy(x=mx-(mx-p.x)*ratio,y=my-(my-p.y)*ratio,step=step);return true
    }
    override fun keyPressed(key:Int,scan:Int,mods:Int):Boolean {
        if(dropdownKey(key)) return true
        if(key==256 && (drag!=null || panning)) { cancelDrag();rebuildWidgets();return true }
        val delta=when(key) { 263->-1 to 0;262->1 to 0;265->0 to -1;264->0 to 1;else->null }
        if(delta!=null) { change { draft.move(selected,draft.transforms[selected],view,delta.first,delta.second) };return true }
        return super.keyPressed(key,scan,mods)
    }
    override fun onClose() { cancelDrag();minecraft?.setScreen(parent) }
    companion object {
        fun open(node:BuildingNode,graph:BuildingGraph,evaluation:BuildingGraphCompiler.Evaluation,commit:(BuildingNode)->Unit) {
            val sources=if(node.kind==BuildingKind.SEGMENT) evaluation.copycatInputSegments(node) else node.inputs.map { evaluation.segment(it) }
            require(sources.isNotEmpty()) { "Connect at least one Copycat input" }
            val names=node.inputs.map { id -> graph.nodes.first { it.id==id }.let { it.name.ifBlank { ui("bg_"+it.kind.name.lowercase()) } } }
            val mc=Minecraft.getInstance()
            mc.setScreen(SegmentArrangementScreen(mc.screen,node,SegmentArrangement(sources,graph.resolvedNode(node)),names,commit))
        }
    }
}
