package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import kotlin.math.*

/** Placement and path instances belong to the building node, with the building editor's shared history. */
class BuildingSiteScreen private constructor(private val parent:BuildingScreen,private val buildingId:String,
    private val graph:()->BuildingGraph,private val commit:(BuildingGraph)->Unit):EditorScreen(tr("bg_site_edit")) {
    var selectedPart=BuildingSite.BODY;private set
    var selectedPoint=0;private set
    private var insertion=false
    private var status=""
    private var footprintGraph:BuildingGraph?=null
    private var accessFootprints:Map<String,Set<P>> = emptyMap()
    private var projection:BuildingGridProjection?=null
    private var panStart:Pair<Double,Double>?=null
    private data class Drag(val original:BuildingGraph,val part:BuildingSite.Part,val point:Int,val start:P,
        var preview:Result<BuildingGraph>)
    private var drag:Drag?=null
    private val mapLeft get()=8
    private val mapTop get()=80
    private val mapWidth get()=(width-16).coerceAtLeast(1)
    private val mapHeight get()=(height-106).coerceAtLeast(24)
    private fun parts(value:BuildingGraph=graph())=BuildingSite.parts(value,buildingId)
    private fun part()=parts().firstOrNull { it.key==selectedPart } ?: parts().first()
    private fun label(part:BuildingSite.Part)=if(part.isBody) ui("bg_site_body") else
        "${part.index+1}. ${part.source.name.ifBlank { ui("bg_"+part.source.kind.name.lowercase()) }}"
    override fun titleAvailableWidth()=width-16

    override fun init() {
        resetDropdown()
        if(projection?.let { it.width!=mapWidth || it.height!=mapHeight }==true) projection=null
        val choices=parts();val part=part();selectedPart=part.key
        val closeWidth=54;val fitWidth=54;val historyWidth=38
        val selectorWidth=(width-16-closeWidth-fitWidth-historyWidth*2-16).coerceAtLeast(60)
        choice(8,28,selectorWidth,label(part),choices.map { value -> label(value) to { selectPart(value.key) } },choices.indexOf(part))
        var x=12+selectorWidth
        val history=parent.siteHistory()
        button(x,28,historyWidth,"undo") { undo() }.active=history.first;x+=historyWidth+4
        button(x,28,historyWidth,"redo") { redo() }.active=history.second;x+=historyWidth+4
        button(x,28,fitWidth,"fit_graph") { projection=null };x+=fitWidth+4
        button(x,28,closeWidth,"close") { onClose() }
        number(8,52,74,part.position.x,-128..128,{ "X $it" }) { value -> change { BuildingSite.move(graph(),buildingId,selectedPart,part().position.copy(x=value)) } }
        number(86,52,74,part.position.z,-128..128,{ "Z $it" }) { value -> change { BuildingSite.move(graph(),buildingId,selectedPart,part().position.copy(z=value)) } }
        if(part.isPath) {
            val points=BuildingSite.points(part);selectedPoint=selectedPoint.coerceIn(0,points.lastIndex)
            choice(164,52,68,ui("bg_site_point",selectedPoint+1),points.indices.map { i -> ui("bg_site_point",i+1) to { selectedPoint=i;rebuildWidgets() } },selectedPoint)
            number(236,52,64,points[selectedPoint].y,-128..128,{ "Y $it" }) { value ->
                change { val current=BuildingSite.points(part()).toMutableList();current[selectedPoint]=current[selectedPoint].copy(y=value);BuildingSite.pathPoints(graph(),buildingId,selectedPart,current) }
            }
            button(304,52,32,Component.literal("+")) { insertion=true;status=ui("bg_site_insert_hint") }.active=points.size<64
            button(340,52,32,Component.literal("−")) { removePoint() }.active=points.size>2
        } else if(part.isAccess) {
            val root=graph().nodes.first { it.id==buildingId };val turns=root.accessInstances[part.index].quarterTurns
            select(164,52,100,turns,(0..3).toList(),{ "${it*90}°" }) { q -> change { BuildingSite.rotateAccess(graph(),buildingId,selectedPart,q) } }
        }
    }
    fun selectPart(key:String,point:Int=0) {
        require(parts().any { it.key==key });cancelDrag();selectedPart=key;selectedPoint=point;rebuildWidgets()
    }
    private fun change(action:()->BuildingGraph) {
        runCatching(action).onSuccess { changed ->
            if(changed!=graph()) commit(changed)
            status="";rebuildWidgets()
        }.onFailure { status=it.message.orEmpty() }
    }
    private fun undo() { cancelDrag();parent.undoSiteEdit();rebuildWidgets() }
    private fun redo() { cancelDrag();parent.redoSiteEdit();rebuildWidgets() }
    private fun cancelDrag() { drag=null;panStart=null;insertion=false;status="" }
    private fun removePoint() {
        val part=part();if(!part.isPath || part.points.size<=2) return
        change { BuildingSite.pathPoints(graph(),buildingId,selectedPart,BuildingSite.points(part).filterIndexed { i,_ -> i!=selectedPoint }) }
        selectedPoint=selectedPoint.coerceAtMost(part.points.lastIndex-1);rebuildWidgets()
    }
    private fun insertPoint(cell:P) {
        val part=part();if(!part.isPath) return
        val points=BuildingSite.points(part).toMutableList();val index=selectedPoint+1
        points.add(index,cell.copy(y=points[selectedPoint].y))
        change { BuildingSite.pathPoints(graph(),buildingId,selectedPart,points) }
        if(BuildingSite.points(part()).size==points.size) selectedPoint=index
        insertion=false;rebuildWidgets()
    }
    private fun fitted():BuildingGridProjection {
        projection?.let { return it }
        val cells=parts().flatMap { p -> runCatching { footprint(graph(),p) }.getOrDefault(emptySet()) }.toSet()
        return BuildingGridProjection.fit(mapLeft,mapTop,mapWidth,mapHeight,cells,maxStep=24.0).also { projection=it }
    }
    fun gridPoint(x:Double,z:Double):Pair<Double,Double> { val p=fitted();return p.x+(x-p.minX)*p.step to p.y+(z-p.minZ)*p.step }
    fun partPoint(key:String):Pair<Double,Double> {
        val cells=footprint(graph(),BuildingSite.part(graph(),buildingId,key))
        val cell=cells.minBy { it.x+it.z };return gridPoint(cell.x+0.5,cell.z+0.5)
    }
    fun pathPoint(key:String,index:Int):Pair<Double,Double> = BuildingSite.points(BuildingSite.part(graph(),buildingId,key))[index].let { gridPoint(it.x+0.5,it.z+0.5) }
    private fun footprint(value:BuildingGraph,part:BuildingSite.Part):Set<P> {
        if(!part.isAccess) return BuildingSite.footprint(value,part)
        val current=graph()
        if(footprintGraph!=current) {
            footprintGraph=current
            val lib=ClientState.snapshot.library
            accessFootprints=runCatching { BuildingGraphCompiler.Evaluation(current,parent.building.palette,lib.forms,lib.modules).building().accessFootprints }.getOrDefault(emptyMap())
        }
        val original=BuildingSite.part(current,buildingId,part.key)
        val instance=current.nodes.first { it.id==buildingId }.accessInstances[part.index]
        val delta=part.position+P(-original.position.x,0,-original.position.z)
        return accessFootprints[instance.id]?.map { it+delta }?.toSet() ?: BuildingSite.footprint(value,part)
    }
    private fun line(g:GuiGraphics,a:Pair<Double,Double>,b:Pair<Double,Double>,color:Int) {
        val steps=ceil(max(abs(a.first-b.first),abs(a.second-b.second))).toInt().coerceAtLeast(1)
        for(i in 0..steps) { val t=i.toDouble()/steps;val x=(a.first+(b.first-a.first)*t).roundToInt();val y=(a.second+(b.second-a.second)*t).roundToInt();g.fill(x-1,y-1,x+1,y+1,color) }
    }
    override fun renderWorkspace(g:GuiGraphics) {
        val p=fitted();val visible=drag?.preview?.getOrNull() ?: graph()
        ArchitectTheme.inset(g,mapLeft,mapTop,mapWidth,mapHeight,ArchitectTheme.blueprint)
        g.enableScissor(mapLeft,mapTop,mapLeft+mapWidth,mapTop+mapHeight)
        for(x in p.columns) { val at=p.px(x).roundToInt();g.fill(at,mapTop,at+1,mapTop+mapHeight,ArchitectTheme.grid) }
        for(z in p.rows) { val at=p.py(z).roundToInt();g.fill(mapLeft,at,mapLeft+mapWidth,at+1,ArchitectTheme.grid) }
        for(part in parts(visible)) {
            val cells=runCatching { footprint(visible,part) }.getOrDefault(emptySet())
            val color=if(part.isBody) ArchitectTheme.muted else if(part.isAccess) 0xff9b80d6.toInt() else if(part.isPath) 0xffc8a46a.toInt() else 0xff21b15b.toInt()
            for(cell in cells) {
                val x=p.px(cell.x).roundToInt();val y=p.py(cell.z).roundToInt();val right=p.px(cell.x+1).roundToInt();val bottom=p.py(cell.z+1).roundToInt()
                g.fill(x+if(p.step>=4) 1 else 0,y+if(p.step>=4) 1 else 0,right,bottom,color)
                if(part.key==selectedPart) {
                    if(cell+P(0,0,-1) !in cells) g.fill(x,y,right,y+1,ArchitectTheme.brass)
                    if(cell+P(0,0,1) !in cells) g.fill(x,bottom-1,right,bottom,ArchitectTheme.brass)
                    if(cell+P(-1,0,0) !in cells) g.fill(x,y,x+1,bottom,ArchitectTheme.brass)
                    if(cell+P(1,0,0) !in cells) g.fill(right-1,y,right,bottom,ArchitectTheme.brass)
                }
            }
            if(part.isPath) {
                val points=BuildingSite.points(part).map { gridPoint(it.x+0.5,it.z+0.5) }
                points.zipWithNext().forEach { (a,b) -> line(g,a,b,ArchitectTheme.brass) }
                points.forEachIndexed { i,(x,y) ->
                    val active=part.key==selectedPart && i==selectedPoint
                    g.fill(x.toInt()-4,y.toInt()-4,x.toInt()+5,y.toInt()+5,if(active) ArchitectTheme.brass else ArchitectTheme.shadow)
                    g.renderOutline(x.toInt()-4,y.toInt()-4,9,9,ArchitectTheme.text)
                    if(p.step>=10) g.drawString(font,"${i+1}",x.toInt()+6,y.toInt()-4,ArchitectTheme.text,false)
                }
            }
        }
        g.flush();g.disableScissor()
    }
    override fun renderContents(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        val error=drag?.preview?.exceptionOrNull()?.message
        val hint=error ?: status.ifBlank { ui(if(insertion) "bg_site_insert_hint" else "bg_site_drag_hint") }
        g.drawString(font,font.plainSubstrByWidth(hint,width-16),8,height-21,if(error!=null) ArchitectTheme.danger else ArchitectTheme.muted,false)
    }
    override fun mouseClicked(mx:Double,my:Double,button:Int):Boolean {
        if(dropdownClick(mx,my,button)) return true
        if(super.mouseClicked(mx,my,button)) return true
        val p=fitted();if(!p.contains(mx,my)) return false
        if(button==2) { panStart=mx to my;return true }
        val cell=p.cell(mx,my)
        if(insertion || button==1 && part().isPath) { insertPoint(cell);return true }
        if(button!=0) return true
        val graph=graph();val handles=parts().filter { it.isPath }.flatMap { part -> BuildingSite.points(part).mapIndexed { i,point -> Triple(part,i,point) } }
        val hit=handles.filter { (_,_,point) -> val at=gridPoint(point.x+0.5,point.z+0.5);hypot(mx-at.first,my-at.second)<=7 }.minByOrNull { (_,_,point) -> val at=gridPoint(point.x+0.5,point.z+0.5);hypot(mx-at.first,my-at.second) }
        if(hit!=null) {
            selectPart(hit.first.key,hit.second);drag=Drag(graph,hit.first,hit.second,cell,Result.success(graph));return true
        }
        val selected=parts().asReversed().firstOrNull { cell in runCatching { footprint(graph,it) }.getOrDefault(emptySet()) } ?: return true
        selectPart(selected.key);drag=Drag(graph,selected,-1,cell,Result.success(graph));return true
    }
    override fun mouseDragged(mx:Double,my:Double,button:Int,dx:Double,dy:Double):Boolean {
        panStart?.let { val p=fitted();projection=p.copy(x=p.x+dx,y=p.y+dy);return true }
        val drag=drag ?: return false;val cell=fitted().cell(mx,my)
        drag.preview=runCatching {
            if(drag.point<0) BuildingSite.move(drag.original,buildingId,drag.part.key,drag.part.position+P(cell.x-drag.start.x,0,cell.z-drag.start.z))
            else {
                val points=BuildingSite.points(drag.part).toMutableList();points[drag.point]=points[drag.point].copy(x=cell.x,z=cell.z)
                BuildingSite.pathPoints(drag.original,buildingId,drag.part.key,points)
            }
        }
        return true
    }
    override fun mouseReleased(mx:Double,my:Double,button:Int):Boolean {
        panStart=null;val finished=drag ?: return super.mouseReleased(mx,my,button);drag=null
        finished.preview.onSuccess { if(it!=graph()) commit(it) }.onFailure { status=it.message.orEmpty() }
        rebuildWidgets();return true
    }
    override fun mouseScrolled(mx:Double,my:Double,dx:Double,dy:Double):Boolean {
        if(super.mouseScrolled(mx,my,dx,dy)) return true
        if(drag!=null) return true
        val p=fitted();if(!p.contains(mx,my)) return false
        val step=(p.step*1.15.pow(dy)).coerceIn(1.0,48.0);val ratio=step/p.step
        projection=p.copy(x=mx-(mx-p.x)*ratio,y=my-(my-p.y)*ratio,step=step);return true
    }
    override fun keyPressed(key:Int,scan:Int,mods:Int):Boolean {
        if(dropdownKey(key)) return true
        if(key==256 && (drag!=null || insertion || panStart!=null)) { cancelDrag();return true }
        if((mods and 2)!=0 && key==90) { if((mods and 1)!=0) redo() else undo();return true }
        if((mods and 2)!=0 && key==89) { redo();return true }
        if(key==261 && part().isPath) { removePoint();return true }
        return super.keyPressed(key,scan,mods)
    }
    override fun onClose() { cancelDrag();minecraft?.setScreen(parent) }
    companion object {
        fun open(buildingId:String,graph:()->BuildingGraph,commit:(BuildingGraph)->Unit) {
            val mc=Minecraft.getInstance();val parent=mc.screen as? BuildingScreen ?: return
            mc.setScreen(BuildingSiteScreen(parent,buildingId,graph,commit))
        }
    }
}
