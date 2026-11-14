package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.UUID
import kotlin.math.*

/** The room plan places complete segment instances, independently of the Copycat raster. */
class RoomSegmentLayoutScreen private constructor(private val parent:Screen?,initial:BuildingNode,
    private val graph:BuildingGraph,private val evaluation:BuildingGraphCompiler.Evaluation,
    private val commit:(BuildingNode)->Unit):EditorScreen(tr("bg_room_layout")) {
    var draft=initial;private set
    var selected="";private set
    private var source=0;private var turn=0;private var drawing=false
    private var boundaryDraft=emptyList<P>();private var showClosure=false
    private var projection:BuildingGridProjection?=null
    private var drag:Pair<P,RoomSegmentInstance>?=null;private var panning=false
    private var status=""
    private val undo=ArrayDeque<BuildingNode>();private val redo=ArrayDeque<BuildingNode>()
    private val mapTop=104
    private val mapHeight get()=(height-182).coerceAtLeast(16)
    private val config get()=draft.roomLayout ?: RoomLayoutConfig()
    private val selectedInstance get()=config.instances.firstOrNull { it.id==selected }
    private val colors=listOf(0xff65accb.toInt(),0xffbf956b.toInt(),0xff8fb972.toInt(),0xffb48bcc.toInt())
    private fun segment(id:String)=evaluation.segment(id)
    private fun resolved()=runCatching { RoomTopology.resolve(draft,::segment) }
    private fun suggestions()=runCatching { RoomTopology.closureSuggestions(draft,::segment) }.getOrDefault(emptyList())
    private fun sourceName(index:Int)=draft.inputs.getOrNull(index)?.let { id -> graph.nodes.firstOrNull { it.id==id }?.let { it.name.ifBlank { ui("bg_"+it.kind.name.lowercase()) } } } ?: ui("bg_no_connection")
    private fun change(value:BuildingNode) {
        if(value==draft) return
        undo.addLast(draft);redo.clear();draft=value;status="";rebuildWidgets()
    }
    private fun layout(value:RoomLayoutConfig)=change(draft.copy(roomLayout=value))
    private fun update(change:(RoomSegmentInstance)->RoomSegmentInstance) {
        val id=selected;layout(config.copy(instances=config.instances.map { if(it.id==id) change(it) else it }))
    }
    override fun titleAvailableWidth()=width-16
    override fun init() {
        resetDropdown()
        if(projection?.let { it.width!=width-16 || it.height!=mapHeight }==true) projection=null
        val w=((width-28)/4).coerceAtLeast(20)
        select(8,28,w*2+4,source,draft.inputs.indices.toList(),{ "${it+1}. ${sourceName(it)}" }) { source=it;rebuildWidgets() }
        select(16+w*2,28,w,turn,(0..3).toList(),{ "${it*90}°" }) { turn=it;rebuildWidgets() }
        button(20+w*3,28,w,"semantic_boundary_draw") { drawing=!drawing;boundaryDraft=emptyList();rebuildWidgets() }.apply { (this as EditorButton).selected=drawing }
        selectedInstance?.let { instance ->
            select(8,52,w,instance.policy,WallPolicy.entries,{ ui("semantic_policy_"+it.name.lowercase()) }) { value -> update { it.copy(policy=value) } }
            select(12+w,52,w,instance.contribution,RoomContribution.entries,{ ui("semantic_contribution_"+it.name.lowercase()) }) { value -> update { it.copy(contribution=value) } }
            button(16+w*2,52,w,"semantic_rotate") { update { it.copy(quarterTurns=Math.floorMod(it.quarterTurns+1,4)) } }
            button(20+w*3,52,w,"remove") { layout(config.copy(instances=config.instances.filterNot { it.id==selected }));selected="";rebuildWidgets() }
        } ?: run {
            button(8,52,w*2+4,"semantic_closure_preview") { showClosure=!showClosure;rebuildWidgets() }.apply { (this as EditorButton).selected=showClosure }
            button(16+w*2,52,w,"semantic_boundary_settings") { boundarySettings() }
            button(20+w*3,52,w,"semantic_boundary_clear") { drawing=false;boundaryDraft=emptyList();layout(config.copy(boundary=emptyList(),boundaryRules=emptyList())) }.active=config.boundary.isNotEmpty()
        }
        if(drawing) {
            button(8,76,w*2+4,"semantic_boundary_accept") { acceptDrawnBoundary() }.active=boundaryDraft.size>=4
            button(16+w*2,76,w*2+4,"cancel") { drawing=false;boundaryDraft=emptyList();rebuildWidgets() }
        } else {
            button(8,76,w*2+4,"semantic_closure_open") { acceptClosure(false) }.active=showClosure && suggestions().isNotEmpty()
            button(16+w*2,76,w*2+4,"semantic_closure_wall") { chooseFiller() }.active=showClosure && suggestions().isNotEmpty()
        }
        button(8,height-30,w,"fit_graph") { projection=null }
        button(12+w,height-30,w,"semantic_instance_settings") { instanceProperties() }.active=selectedInstance!=null
        button(16+w*2,height-30,w,"cancel") { onClose() }
        button(20+w*3,height-30,w,"apply") {
            runCatching { BuildingSemantics.validate(draft,graph.nodes.associateBy { it.id });commit(draft) }.onSuccess { onClose() }.onFailure { status=it.message.orEmpty() }
        }
    }
    private fun instanceProperties() {
        val original=selectedInstance ?: return;var value=original
        StructurePropertyScreen.open(tr("semantic_instance_settings"),{
            for(axis in 0..2) int(listOf("X","Y","Z")[axis],listOf(value.position.x,value.position.y,value.position.z)[axis],if(axis==1) 0..127 else -128..128) { number ->
                value=value.copy(position=when(axis) { 0->value.position.copy(x=number);1->value.position.copy(y=number);else->value.position.copy(z=number) })
            }
            choice(ui("rotation"),value.quarterTurns,(0..3).toList(),{ "${it*90}°" }) { value=value.copy(quarterTurns=it) }
            choice(ui("semantic_policy"),value.policy,WallPolicy.entries,{ ui("semantic_policy_"+it.name.lowercase()) }) { value=value.copy(policy=it) }
            choice(ui("semantic_contribution"),value.contribution,RoomContribution.entries,{ ui("semantic_contribution_"+it.name.lowercase()) }) { value=value.copy(contribution=it) }
        }) { update { value } }
    }
    private fun editBoundary() {
        EditorValueInput.text(tr("semantic_boundary_text"),config.boundary.joinToString("; ") { "${it.x},${it.z}" },4096,{ runCatching { parseBoundary(it) }.isSuccess }) {
            layout(config.copy(boundary=parseBoundary(it),boundaryRules=emptyList()))
        }
    }
    private fun boundarySettings() {
        var c=config
        StructurePropertyScreen.open(tr("semantic_boundary_settings"),{
            text(ui("semantic_boundary_text"),c.boundary.joinToString("; ") { "${it.x},${it.z}" },4096,{ runCatching { parseBoundary(it) }.isSuccess }) {
                c=c.copy(boundary=parseBoundary(it),boundaryRules=emptyList())
            }
            val shape=runCatching { RoomTopology.resolve(draft.copy(roomLayout=c),::segment) }.getOrNull()
            val gaps=mutableListOf<Pair<P,P>>()
            for(span in shape?.spans.orEmpty().filter { it.instanceId.isEmpty() }) {
                val previous=gaps.lastOrNull()
                if(previous!=null && previous.second==span.start && (previous.first.x==previous.second.x && span.start.x==span.end.x || previous.first.z==previous.second.z && span.start.z==span.end.z))
                    gaps[gaps.lastIndex]=previous.first to span.end
                else gaps+=span.start to span.end
            }
            for((a,b) in gaps) {
                val current=shape?.spans.orEmpty().firstOrNull { it.start==a }?.policy ?: WallPolicy.AUTO
                choice("${a.x},${a.z} → ${b.x},${b.z}",current,listOf(WallPolicy.AUTO,WallPolicy.OPEN),{ ui("semantic_policy_"+it.name.lowercase()) }) { value ->
                    val affected=shape?.spans.orEmpty().filter { pointOn(it.start,a,b) && pointOn(it.end,a,b) }
                    val retained=c.boundaryRules.filterNot { rule -> affected.any { pointOn(it.start,rule.start,rule.end) && pointOn(it.end,rule.start,rule.end) } }
                    c=c.copy(boundaryRules=retained+RoomBoundaryRule(a,b,value))
                }
            }
            hint(ui("semantic_boundary_rules_help"))
        }) { layout(c) }
    }
    private fun pointOn(p:P,a:P,b:P)=if(a.x==b.x) p.x==a.x && p.z in minOf(a.z,b.z)..maxOf(a.z,b.z)
        else p.z==a.z && p.x in minOf(a.x,b.x)..maxOf(a.x,b.x)
    private fun parseBoundary(text:String):List<P> {
        if(text.isBlank()) return emptyList()
        val points=text.split(';','\n').map { entry -> val p=entry.trim().split(',');require(p.size==2);P(p[0].trim().toInt(),0,p[1].trim().toInt()) }
        BuildingSemantics.validate(draft.copy(roomLayout=config.copy(boundary=points)),graph.nodes.associateBy { it.id })
        return points
    }
    private fun acceptDrawnBoundary() {
        runCatching { parseBoundary(boundaryDraft.joinToString(";") { "${it.x},${it.z}" }) }.onSuccess {
            drawing=false;boundaryDraft=emptyList();layout(config.copy(boundary=it,boundaryRules=emptyList()))
        }.onFailure { status=it.message.orEmpty() }
    }
    private fun chooseFiller() {
        var filler=config.filler
        StructurePropertyScreen.open(tr("semantic_closure_wall"),{
            val sources=graph.nodes.filter { it.kind in (setOf(BuildingKind.ROTATION_SEGMENT,BuildingKind.SEGMENT,BuildingKind.PATTERN,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_ROTATE)+SegmentBooleans.kinds) && it.id!="empty_wall" }
            choice(ui("semantic_filler_source"),filler,listOf("")+sources.map { it.id },{ id -> sources.firstOrNull { it.id==id }?.name ?: ui("bg_no_connection") }) { filler=it }
            hint(ui("semantic_closure_help"))
        }) { require(filler.isNotEmpty()) { ui("semantic_choose_filler") };acceptClosure(true,filler) }
    }
    private fun acceptClosure(walls:Boolean,filler:String=config.filler) {
        runCatching {
            val r=resolved().getOrThrow()
            val edges=r.instances.filter { it.definition.contribution in setOf(RoomContribution.BOUNDARY,RoomContribution.OPENING) }.map { it.start to it.end }+suggestions()
            val points=mutableListOf<P>();val remaining=edges.toMutableList()
            require(remaining.isNotEmpty()) { ui("semantic_closure_none") }
            var edge=remaining.removeAt(0);points+=edge.first;var end=edge.second
            while(end!=points.first()) {
                points+=end
                val next=remaining.indexOfFirst { it.first==end || it.second==end }
                require(next>=0 && points.size<=256) { ui("semantic_closure_ambiguous") }
                edge=remaining.removeAt(next);end=if(edge.first==end) edge.second else edge.first
            }
            require(remaining.isEmpty()) { ui("semantic_closure_ambiguous") }
            val simplified=points.filterIndexed { i,p -> val prev=points[Math.floorMod(i-1,points.size)];val next=points[(i+1)%points.size];!(prev.x==p.x && p.x==next.x || prev.z==p.z && p.z==next.z) }
            require(simplified.size>=4) { ui("semantic_closure_ambiguous") }
            val proposed=suggestions()
            val retainedRules=config.boundaryRules.filterNot { rule -> proposed.any { (a,b) -> rule.start==a && rule.end==b || rule.start==b && rule.end==a } }
            val rules=retainedRules+proposed.map { (a,b) -> RoomBoundaryRule(a,b,if(walls) WallPolicy.AUTO else WallPolicy.OPEN) }
            val value=config.copy(boundary=simplified,filler=if(walls) filler else "",boundaryRules=rules)
            BuildingSemantics.validate(draft.copy(roomLayout=value),graph.nodes.associateBy { it.id })
            layout(value);showClosure=false;rebuildWidgets()
        }.onFailure { status=it.message.orEmpty() }
    }
    private fun cells():Set<P> = resolved().getOrNull()?.shell.orEmpty()+config.boundary+boundaryDraft
    private fun fitted()=projection ?: BuildingGridProjection.fit(8,mapTop,width-16,mapHeight,cells(),24.0).also { projection=it }
    fun gridPoint(x:Double,z:Double):Pair<Double,Double> { val p=fitted();return p.x+(x-p.minX)*p.step to p.y+(z-p.minZ)*p.step }
    fun instancePoint(id:String):Pair<Double,Double> {
        val instance=resolved().getOrThrow().instances.first { it.definition.id==id };return gridPoint(instance.position.x+instance.value.length/2.0,instance.position.z+instance.value.depth/2.0)
    }
    private fun line(g:GuiGraphics,a:P,b:P,color:Int) {
        val p=fitted();val ax=p.px(a.x).roundToInt();val az=p.py(a.z).roundToInt();val bx=p.px(b.x).roundToInt();val bz=p.py(b.z).roundToInt()
        if(az==bz) g.fill(minOf(ax,bx),az,maxOf(ax,bx)+2,az+2,color)
        else if(ax==bx) g.fill(ax,minOf(az,bz),ax+2,maxOf(az,bz)+2,color)
    }
    override fun renderWorkspace(g:GuiGraphics) {
        val p=fitted();ArchitectTheme.inset(g,8,mapTop,width-16,mapHeight,ArchitectTheme.blueprint)
        g.enableScissor(8,mapTop,width-8,mapTop+mapHeight)
        for(x in p.columns) { val px=p.px(x).roundToInt();g.fill(px,mapTop,px+1,mapTop+mapHeight,ArchitectTheme.grid) }
        for(z in p.rows) { val py=p.py(z).roundToInt();g.fill(8,py,width-8,py+1,ArchitectTheme.grid) }
        val r=resolved().getOrNull()
        r?.interior?.forEach { cell -> g.fill(p.px(cell.x).roundToInt()+1,p.py(cell.z).roundToInt()+1,p.px(cell.x+1).roundToInt(),p.py(cell.z+1).roundToInt(),0x304ca178) }
        r?.instances?.forEachIndexed { i,instance ->
            val a=instance.position;val value=instance.value
            val x=p.px(a.x).roundToInt();val z=p.py(a.z).roundToInt();val ex=p.px(a.x+value.length).roundToInt();val ez=p.py(a.z+value.depth).roundToInt()
            val color=if(instance.definition.policy==WallPolicy.OPEN) ArchitectTheme.muted else colors[i%colors.size]
            g.fill(x+1,z+1,ex,ez,color)
            if(instance.definition.id==selected) { g.fill(x,z,ex,z+2,ArchitectTheme.brass);g.fill(x,ez-2,ex,ez,ArchitectTheme.brass);g.fill(x,z,x+2,ez,ArchitectTheme.brass);g.fill(ex-2,z,ex,ez,ArchitectTheme.brass) }
            line(g,instance.start,instance.end,ArchitectTheme.text)
            if(p.step>=8) g.drawString(font,"${i+1}",x+3,z+3,ArchitectTheme.text,false)
        }
        for(points in listOf(config.boundary,boundaryDraft)) points.forEachIndexed { i,a ->
            if(i<points.lastIndex || points===config.boundary) line(g,a,points[(i+1)%points.size],ArchitectTheme.brass)
            val x=p.px(a.x).roundToInt();val y=p.py(a.z).roundToInt();g.fill(x-2,y-2,x+3,y+3,ArchitectTheme.brass)
        }
        if(showClosure) for((a,b) in suggestions()) line(g,a,b,0xffe4c260.toInt())
        config.boundaryRules.filter { it.policy==WallPolicy.OPEN }.forEach { line(g,it.start,it.end,0xff9abfc5.toInt()) }
        g.flush();g.disableScissor()
    }
    override fun renderContents(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        val r=resolved();val summary=r.getOrNull()?.let { ui("semantic_layout_summary",it.width,it.depth,it.height,it.interior.size,ui(if(it.closed) "semantic_closed" else "semantic_open")) }.orEmpty()
        label(g,font.plainSubstrByWidth(status.ifEmpty { r.exceptionOrNull()?.message ?: summary },width-16),8,height-68,if(status.isNotEmpty() || r.isFailure) ArchitectTheme.danger else ArchitectTheme.brass)
        label(g,font.plainSubstrByWidth(ui(if(drawing) "semantic_boundary_draw_help" else "semantic_layout_help"),width-16),8,height-55,ArchitectTheme.muted)
        r.getOrNull()?.warnings?.firstOrNull()?.let { label(g,font.plainSubstrByWidth(it,width-16),8,height-42,ArchitectTheme.muted) }
    }
    override fun mouseClicked(mx:Double,my:Double,button:Int):Boolean {
        if(dropdownClick(mx,my,button) || super.mouseClicked(mx,my,button)) return true
        val p=fitted();if(!p.contains(mx,my)) return false
        if(button==2) { panning=true;return true }
        val cell=p.cell(mx,my)
        if(drawing) {
            if(button==1) boundaryDraft=boundaryDraft.dropLast(1)
            else if(button==0) {
                val corner=P(round((mx-p.x)/p.step).toInt()+p.minX,0,round((my-p.y)/p.step).toInt()+p.minZ)
                if(corner==boundaryDraft.firstOrNull() && boundaryDraft.size>=4) acceptDrawnBoundary() else { boundaryDraft=boundaryDraft+corner;rebuildWidgets() }
            }
            return true
        }
        val hit=resolved().getOrNull()?.instances?.asReversed()?.firstOrNull { cell.x in it.position.x until it.position.x+it.value.length && cell.z in it.position.z until it.position.z+it.value.depth }
        if(button==1) { hit?.let { selected=it.definition.id;update { v -> v.copy(quarterTurns=Math.floorMod(v.quarterTurns+1,4)) } };return true }
        if(button!=0) return true
        if(hit!=null) { selected=hit.definition.id;drag=cell to hit.definition;rebuildWidgets();return true }
        if(selected.isNotEmpty()) { selected="";rebuildWidgets();return true }
        if(source !in draft.inputs.indices || draft.inputs[source].isBlank()) { status=ui("semantic_connect_segments");return true }
        val instance=RoomSegmentInstance(UUID.randomUUID().toString(),source,cell,turn,WallPolicy.EXPLICIT)
        selected=instance.id;layout(config.copy(instances=config.instances+instance));return true
    }
    override fun mouseDragged(mx:Double,my:Double,button:Int,dx:Double,dy:Double):Boolean {
        if(panning && button==2) { val p=fitted();projection=p.copy(x=p.x+dx,y=p.y+dy);return true }
        val d=drag ?: return false;if(button!=0) return false
        val cell=fitted().cell(mx,my);val position=d.second.position+P(cell.x-d.first.x,0,cell.z-d.first.z)
        if(position.x !in -128..128 || position.z !in -128..128) return true
        draft=draft.copy(roomLayout=config.copy(instances=config.instances.map { if(it.id==d.second.id) it.copy(position=position) else it }));return true
    }
    override fun mouseReleased(mx:Double,my:Double,button:Int):Boolean {
        if(button==2 && panning) { panning=false;return true }
        val d=drag ?: return super.mouseReleased(mx,my,button);if(button!=0) return false
        val original=draft.copy(roomLayout=config.copy(instances=config.instances.map { if(it.id==d.second.id) d.second else it }))
        if(original!=draft) { undo.addLast(original);redo.clear() };drag=null;rebuildWidgets();return true
    }
    override fun mouseScrolled(mx:Double,my:Double,dx:Double,dy:Double):Boolean {
        if(super.mouseScrolled(mx,my,dx,dy)) return true
        val p=fitted();if(!p.contains(mx,my)) return false
        val step=(p.step*1.15.pow(dy)).coerceIn(2.0,48.0);val ratio=step/p.step
        projection=p.copy(x=mx-(mx-p.x)*ratio,y=my-(my-p.y)*ratio,step=step);return true
    }
    override fun keyPressed(key:Int,scan:Int,mods:Int):Boolean {
        if(dropdownKey(key)) return true
        if(hasControlDown() && key in setOf(90,89)) {
            val backward=key==90 && !hasShiftDown();val from=if(backward) undo else redo;val to=if(backward) redo else undo
            if(from.isNotEmpty()) { to.addLast(draft);draft=from.removeLast();rebuildWidgets() };return true
        }
        if(key==261 && selectedInstance!=null) { layout(config.copy(instances=config.instances.filterNot { it.id==selected }));selected="";rebuildWidgets();return true }
        if(key==82 && selectedInstance!=null) { update { it.copy(quarterTurns=Math.floorMod(it.quarterTurns+1,4)) };return true }
        return super.keyPressed(key,scan,mods)
    }
    override fun onClose() { minecraft?.setScreen(parent) }
    companion object {
        fun open(node:BuildingNode,graph:BuildingGraph,evaluation:BuildingGraphCompiler.Evaluation,commit:(BuildingNode)->Unit) {
            val mc=Minecraft.getInstance();mc.setScreen(RoomSegmentLayoutScreen(mc.screen,node,graph,evaluation,commit))
        }
    }
}
