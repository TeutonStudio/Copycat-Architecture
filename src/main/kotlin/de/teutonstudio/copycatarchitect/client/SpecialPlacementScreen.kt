package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import kotlin.math.*

/** A valid release is one graph edit; invalid drags remain visible but never change the document. */
class SpecialPlacementScreen private constructor(private val parent:Screen?,initial:BuildingNode,
    private var context:BuildingGraph,private val evaluation:(BuildingGraph)->BuildingGraphCompiler.Evaluation,
    private val commit:(BuildingNode)->Unit):EditorScreen(tr("special_place_instances")) {
    private var node=initial
    private var candidate:BuildingNode?=null
    var selected=0;private set
    private val bay get()=node.kind==BuildingKind.ROOM
    private var projection:BuildingGridProjection?=null
    private var dragStart:P?=null
    private var original:BuildingNode?=null
    private var validity:Result<Unit> = Result.success(Unit)
    private var status=""
    private val count get()=if(bay) node.bayWindowInstances.size else node.dormerInstances.size
    private val mapTop=54
    private val mapHeight get()=(height-120).coerceAtLeast(12)
    private val background:Set<P> by lazy {
        runCatching {
            val bare=if(bay) node.copy(bayWindows=emptyList()) else node.copy(dormers=emptyList())
            val ev=evaluation(context.replace(bare))
            if(bay) ev.semanticRoom(node.id).compiled.shell else ev.preview(node.id).placements.map { it.pos.copy(y=0) }.toSet()
        }.getOrDefault(emptySet())
    }
    private val hostGeometry by lazy { if(bay) runCatching { evaluation(context.replace(node.copy(bayWindows=emptyList()))).semanticRoom(node.id) }.getOrNull() else null }
    private val anchors by lazy { if(bay) runCatching { SpecialRooms.anchors(context,node) }.getOrDefault(emptyList()) else emptyList() }
    override fun titleAvailableWidth()=width-16
    override fun init() {
        resetDropdown()
        if(count>0) {
            selected=selected.coerceIn(0,count-1)
            select(8,28,width-16,selected,(0 until count).toList(),{ "${ui(if(bay) "bg_bay_window" else "bg_dormer")} ${it+1}" }) { selected=it;rebuildWidgets() }
        }
        button(8,height-30,(width-24)/2,"fit_graph") { projection=null }
        button(16+(width-24)/2,height-30,(width-24)/2,"close") { onClose() }
        if(projection?.let { it.width!=width-16 || it.height!=mapHeight }==true) projection=null
    }
    private fun shapes(value:BuildingNode):List<Set<P>> = if(bay) value.bayWindowInstances.map { item ->
        val config=context.nodes.firstOrNull { it.id==item.source }?.bayWindow ?: BayWindowConfig()
        val anchor=anchors.firstOrNull { it.edge==item.edge }
        val start=anchor?.cells?.getOrNull(item.offset)
        if(anchor==null || start==null) emptySet() else {
            val origin=hostGeometry?.let { room -> runCatching { SpecialRooms.bayFrame(context,node,room,item,config).origin }.getOrNull() }
                ?: (start-P(1,0,config.depth+1).turn(anchor.direction))
            buildSet { for(x in 0..config.width+1) for(z in 0..config.depth) add(origin+P(x,0,z).turn(anchor.direction)) }
        }
    } else value.dormerInstances.map { item ->
        val c=context.nodes.firstOrNull { it.id==item.source }?.dormer ?: DormerConfig()
        buildSet { for(x in 0 until c.width+2) for(z in 0 until c.depth+2) add(item.position+P(x,0,z).turn(item.direction)) }
    }
    private fun fitted():BuildingGridProjection=projection ?: BuildingGridProjection.fit(8,mapTop,width-16,mapHeight,background+shapes(node).flatten(),24.0).also { projection=it }
    fun gridPoint(x:Double,z:Double):Pair<Double,Double> { val p=fitted();return p.x+(x-p.minX)*p.step to p.y+(z-p.minZ)*p.step }
    private fun validate(value:BuildingNode,full:Boolean=false):Result<Unit> = runCatching {
        val changed=context.replace(value);val ev=evaluation(changed)
        if(bay) {
            ev.room(value.id)
            changed.nodes.filter { it.kind==BuildingKind.STOREY && value.id in it.inputs }.forEach { ev.storey(it.id) }
        } else ev.validateDormerPlacement(value.id,full)
        Unit
    }
    private fun moved(base:BuildingNode,cell:P,start:P):BuildingNode {
        if(!bay) return base.copy(dormers=base.dormerInstances.mapIndexed { i,v -> if(i==selected) v.copy(position=v.position+cell-start) else v })
        val item=base.bayWindowInstances[selected]
        val c=context.nodes.firstOrNull { it.id==item.source }?.bayWindow ?: BayWindowConfig()
        val choices=anchors.flatMap { a -> (0..(a.cells.size-c.width)).map { offset -> a to offset } }
        val best=choices.minByOrNull { (a,offset) ->
            val middle=a.cells[offset]+P(c.width/2,0,-c.depth/2-1).turn(a.direction)
            abs(middle.x-cell.x)+abs(middle.z-cell.z)
        } ?: error(ui("bay_no_wall"))
        return base.copy(bayWindows=base.bayWindowInstances.mapIndexed { i,v -> if(i==selected) v.copy(edge=best.first.edge,offset=best.second) else v })
    }
    override fun renderWorkspace(g:GuiGraphics) {
        val p=fitted();ArchitectTheme.inset(g,8,mapTop,width-16,mapHeight,ArchitectTheme.blueprint)
        g.enableScissor(8,mapTop,width-8,mapTop+mapHeight)
        for(x in p.columns) { val xx=p.px(x).roundToInt();g.fill(xx,mapTop,xx+1,mapTop+mapHeight,ArchitectTheme.grid) }
        for(z in p.rows) { val yy=p.py(z).roundToInt();g.fill(8,yy,width-8,yy+1,ArchitectTheme.grid) }
        fun cells(values:Set<P>,color:Int) { for(cell in values) g.fill(p.px(cell.x).roundToInt()+1,p.py(cell.z).roundToInt()+1,p.px(cell.x+1).roundToInt(),p.py(cell.z+1).roundToInt(),color) }
        cells(background,0xff456071.toInt())
        val shown=candidate ?: node
        shapes(shown).forEachIndexed { i,shape ->
            cells(shape,if(i!=selected) 0xff6b9469.toInt() else if(validity.isFailure) ArchitectTheme.danger else ArchitectTheme.brass)
            shape.minWithOrNull(compareBy({it.z},{it.x}))?.let { cell -> g.drawString(font,"${i+1}",p.px(cell.x).roundToInt()+2,p.py(cell.z).roundToInt()+2,ArchitectTheme.text,false) }
        }
        if(bay && count>0) {
            val item=shown.bayWindowInstances[selected];val a=anchors.firstOrNull { it.edge==item.edge }
            val c=context.nodes.firstOrNull { it.id==item.source }?.bayWindow ?: BayWindowConfig()
            val opening=item.openingWidth.takeUnless { it==0 } ?: c.width
            val frame=hostGeometry?.let { room -> runCatching { SpecialRooms.bayFrame(context,node,room,item,c) }.getOrNull() }
            val openingCells=if(frame==null) a?.cells?.drop(item.offset+(c.width-opening)/2)?.take(opening)?.toSet().orEmpty()
                else buildSet { for(x in 1+(c.width-opening)/2 until 1+(c.width-opening)/2+opening) for(z in c.depth+1 until c.depth+1+frame.wallDepth) add(frame.origin+P(x,0,z).turn(frame.direction)) }
            cells(openingCells,if(validity.isFailure) ArchitectTheme.danger else 0xff6bd4ca.toInt())
        }
        g.flush();g.disableScissor()
    }
    override fun renderContents(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        val error=validity.exceptionOrNull()?.message
        label(g,font.plainSubstrByWidth(error ?: status.ifEmpty { ui(if(count==0) "special_instance_hint" else "special_drag_hint") },width-16),8,height-58,if(error!=null) ArchitectTheme.danger else ArchitectTheme.text)
        label(g,font.plainSubstrByWidth(ui("special_grid_hint"),width-16),8,height-45,ArchitectTheme.muted)
    }
    override fun mouseClicked(mx:Double,my:Double,button:Int):Boolean {
        if(dropdownClick(mx,my,button) || super.mouseClicked(mx,my,button)) return true
        if(button!=0 || count==0 || !fitted().contains(mx,my)) return false
        val cell=fitted().cell(mx,my);val all=shapes(node)
        val hit=(listOf(selected)+all.indices.filter { it!=selected }).firstOrNull { cell in all[it] } ?: selected
        selected=hit;original=node;dragStart=cell;candidate=node;validity=validate(node);return true
    }
    override fun mouseDragged(mx:Double,my:Double,button:Int,dx:Double,dy:Double):Boolean {
        val start=dragStart ?: return super.mouseDragged(mx,my,button,dx,dy)
        if(button!=0) return false
        val result=runCatching { moved(original!!,fitted().cell(mx,my),start) }
        result.onSuccess { next -> if(next!=candidate) { candidate=next;validity=validate(next) } }.onFailure { validity=Result.failure(it) }
        return true
    }
    override fun mouseReleased(mx:Double,my:Double,button:Int):Boolean {
        if(button!=0 || dragStart==null) return super.mouseReleased(mx,my,button)
        val next=candidate
        if(next!=null && next!=node && validity.isSuccess) {
            validate(next,true).mapCatching { commit(next) }.onSuccess { node=next;context=context.replace(next);status=ui("special_placement_saved") }.onFailure { status=it.message.orEmpty() }
        } else if(validity.isFailure) status=validity.exceptionOrNull()?.message.orEmpty()
        original=null;candidate=null;dragStart=null;validity=Result.success(Unit);rebuildWidgets();return true
    }
    override fun mouseScrolled(mx:Double,my:Double,dx:Double,dy:Double):Boolean {
        if(super.mouseScrolled(mx,my,dx,dy)) return true
        if(dragStart!=null || !fitted().contains(mx,my)) return false
        val p=fitted();val step=(p.step*1.15.pow(dy)).coerceIn(1.0,48.0);val ratio=step/p.step
        projection=p.copy(x=mx-(mx-p.x)*ratio,y=my-(my-p.y)*ratio,step=step);return true
    }
    override fun onClose() { original=null;candidate=null;dragStart=null;minecraft?.setScreen(parent) }
    companion object {
        fun open(node:BuildingNode,graph:BuildingGraph,evaluation:(BuildingGraph)->BuildingGraphCompiler.Evaluation,commit:(BuildingNode)->Unit) {
            val mc=Minecraft.getInstance();mc.setScreen(SpecialPlacementScreen(mc.screen,node,graph,evaluation,commit))
        }
    }
}
