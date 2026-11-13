package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.Architect
import de.teutonstudio.copycatarchitect.server.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.network.chat.Component
import net.minecraft.client.renderer.Sheets
import net.minecraft.client.renderer.texture.OverlayTexture
import net.neoforged.neoforge.client.model.data.ModelData
import com.mojang.math.Axis
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.*

class BuildingGraphView {
    var panX=10.0;var panY=10.0;var zoom=0.65;var fitted=false;var selected="building";var wire:String?=null;var yaw=35f
    var inspectorScroll=0.0;var inspectorNode="";var inspectorCompact=false
    val room=mutableMapOf<String,Int>()
    val inspectorSections=InspectorSections()
    val previewYaws=mutableMapOf<String,Float>()
}
object BuildingScene {
    private data class PreviewStyle(val yaw:Float,val filter:String)

    internal fun drawCached(g:GuiGraphics,plan:Plan,x:Int,y:Int,w:Int,h:Int,yaw:Float,filter:String="all") {
        GraphPreviewThumbnails.draw(g,x,y,w,h,plan,PreviewStyle(yaw,filter)) {
            val shown=if(filter=="all") plan else plan.copy(placements=plan.placements.filter {
                if(filter=="walls") it.component in listOf("wall","corner","trim","gable") else it.component==filter
            })
            val renderScene=if(shown.placements.isEmpty()) null
                else thumbnail(shown,BuildingPreviewViewport(0,0,w,h),yaw,neighbors=plan.placements.associateBy { it.pos })
            var first=true
            val render:(GuiGraphics)->Boolean={ target ->
                if(first) { ArchitectTheme.inset(target,0,0,w,h,ArchitectTheme.blueprint);first=false }
                renderScene?.invoke(target) ?: true
            }
            render
        }
    }

    fun draw(g:GuiGraphics,plan:Plan,x:Int,y:Int,w:Int,h:Int,yaw:Float,filter:String="all",projection:String="3d") {
        val cells=if(filter=="all") plan.placements else plan.placements.filter {
            if(filter=="walls") it.component in listOf("wall","corner","trim","gable") else it.component==filter
        }
        if(cells.isEmpty()) return
        Scene(cells,plan.placements.associateBy { it.pos },orientation(yaw,projection)).draw(g,BuildingPreviewViewport(x,y,w,h),0)
    }

    private fun orientation(yaw:Float,projection:String)=when(projection) {
        "front" -> Quaternionf()
        "top" -> Quaternionf().rotationX(Math.toRadians(90.0).toFloat())
        else -> Quaternionf().rotationX(Math.toRadians(25.0).toFloat()).rotateY(Math.toRadians(yaw.toDouble()).toFloat())
    }

    internal fun thumbnail(plan:Plan,viewport:BuildingPreviewViewport,yaw:Float=35f,projection:String="3d",neighbors:Map<P,Placement> = plan.placements.associateBy { it.pos }): (GuiGraphics)->Boolean {
        val rotation=orientation(yaw,projection)
        val x=rotation.transform(Vector3f(1f,0f,0f)).z
        val y=rotation.transform(Vector3f(0f,1f,0f)).z
        val z=rotation.transform(Vector3f(0f,0f,1f)).z
        // Paint from back to front across batches. The full neighbor index remains available for CT.
        val cells=plan.placements.sortedBy { x*it.pos.x+y*it.pos.y+z*it.pos.z }
        val scene=Scene(cells,neighbors,rotation)
        var next=0
        return { graphics ->
            next=scene.draw(graphics,viewport,next,thumbnail=true)
            next==cells.size
        }
    }

    private class Scene(private val cells:List<Placement>,private val neighbors:Map<P,Placement>,private val rotation:Quaternionf) {
        private val lo=P(cells.minOf { it.pos.x },cells.minOf { it.pos.y },cells.minOf { it.pos.z })
        private val hi=P(cells.maxOf { it.pos.x }+1,cells.maxOf { it.pos.y }+1,cells.maxOf { it.pos.z }+1)
        private val corners=buildList {
            val sx=(hi.x-lo.x)*0.5f;val sy=(hi.y-lo.y)*0.5f;val sz=(hi.z-lo.z)*0.5f
            for(a in listOf(-sx,sx)) for(b in listOf(-sy,sy)) for(c in listOf(-sz,sz)) add(rotation.transform(Vector3f(a,b,c)))
        }

        fun draw(g:GuiGraphics,viewport:BuildingPreviewViewport,start:Int,thumbnail:Boolean=false):Int {
            val (x,y,w,h)=viewport
            val scale=minOf(w/(corners.maxOf { it.x }-corners.minOf { it.x }),h/(corners.maxOf { it.y }-corners.minOf { it.y }))*0.88f
            val end=if(thumbnail) minOf(cells.size,start+128) else cells.size
            val deadline=if(thumbnail) System.nanoTime()+4_000_000L else Long.MAX_VALUE
            val renderer=Minecraft.getInstance().blockRenderer
            g.flush();val pose=g.pose();pose.pushPose()
            var index=start
            try {
                pose.translate(x+w/2.0,y+h/2.0,160.0);pose.scale(scale,-scale,scale);pose.mulPose(rotation);pose.translate(-(hi.x+lo.x)/2.0,-(hi.y+lo.y)/2.0,-(hi.z+lo.z)/2.0)
                while(index<end) {
                    val p=cells[index++]
                    pose.pushPose()
                    try {
                        pose.translate(p.pos.x.toDouble(),p.pos.y.toDouble(),p.pos.z.toDouble())
                        if(p.block.isEmpty()) {
                            val adjacent=PlanMeshNeighbors.at(p.pos,neighbors)
                            Meshes.draw(p.parts,p.material,pose,g.bufferSource(),15728880,neighbors=adjacent.parts,groups=p.groups,surfaces=p.surfaces,neighborGroups=adjacent.groups,neighborTransparent=adjacent.transparent,textures=CopycatTextureContext.plan(p.pos,neighbors))
                        } else {
                            val state=LandscapeBlockPreview.state(p,neighbors)
                            // Fabulous' item translucent layer would bind the world framebuffer.
                            if(thumbnail && Minecraft.useShaderTransparency())
                                renderer.renderSingleBlock(state,pose,g.bufferSource(),15728880,OverlayTexture.NO_OVERLAY,ModelData.EMPTY,Sheets.translucentCullBlockSheet())
                            else renderer.renderSingleBlock(state,pose,g.bufferSource(),15728880,OverlayTexture.NO_OVERLAY)
                        }
                    } finally {
                        pose.popPose()
                    }
                    if(thumbnail && System.nanoTime()>=deadline) break
                }
            } finally { g.flush();pose.popPose() }
            return index
        }
    }
}
class BuildingGraphCanvas(x:Int,y:Int,w:Int,h:Int,private val definition:()->Building,private val library:()->Library,
    val view:BuildingGraphView,private val commit:(BuildingGraph)->Unit,private val failure:(String)->Unit,private val inspector:Boolean=false,
    private val onCreateRole:(String)->Unit={},private val preview:(()->Result<CompiledBuilding>?)?=null,
    private val readOnly:Boolean=false,private val definitionKinds:Set<BuildingKind>?=null,
    private val onSelectDefinition:((String)->Unit)?=null,
    private val onSelectMaterial:((String,String)->Unit)?=null,private val allowedNodes:(()->Set<String>)?=null):AbstractWidget(x,y,w,h,tr("building_graph")) {
    private val font get()=Minecraft.getInstance().font
    private val graph get()=definition().graph!!
    private var oldBuilding:Building?=null;private var oldLibrary:Library?=null;private var oldRecipes=0;private var evaluator:Result<BuildingGraphCompiler.Evaluation>?=null;private var compiled:Result<CompiledBuilding>?=null
    private val nodePlans=mutableMapOf<String,Result<Plan>>()
    private var headerDrag:String?=null;private var moving:BuildingNode?=null;private var panning=false;private var orbit=false;private var orbitNode:String?=null
    private var rightStart:Pair<Double,Double>?=null;private var rightGraph:Pair<Double,Double>?=null
    private var inspectorScroll:Double
        get()=view.inspectorScroll
        set(value) { view.inspectorScroll=value }
    private var pointerX=Double.NEGATIVE_INFINITY;private var pointerY=Double.NEGATIVE_INFINITY
    private val selected get()=graph.nodes.firstOrNull { it.id==view.selected } ?: graph.nodes.first()
    private val nodePreview get()=inspector && selected.kind in (SegmentBooleans.kinds+CopycatBooleans.kinds+setOf(BuildingKind.BAY_WINDOW,BuildingKind.DORMER,BuildingKind.COPYCAT_MIRROR,BuildingKind.COPYCAT_ROTATE,BuildingKind.SEGMENT,BuildingKind.PATTERN,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_ROTATE,BuildingKind.ROOM_LAYOUT,BuildingKind.ROOM,BuildingKind.STOREY,BuildingKind.ROOF,BuildingKind.ROOF_LAYOUT,BuildingKind.ROOF_LEVEL,BuildingKind.STAIRWELL,BuildingKind.ELEVATOR_SHAFT,BuildingKind.SERVICE_SHAFT,BuildingKind.GARDEN,BuildingKind.PATH))
    private val previewHeader get()=35
    private val inspectorWidth get()=width
    private val scale get()=if(inspector) minOf(1.0,(inspectorWidth-28.0)/nodeWidth(selected)).coerceAtLeast(0.2) else view.zoom
    private val panX get()=if(inspector) (inspectorWidth-nodeWidth(selected)*scale)/2 else view.panX
    private val panY get()=if(inspector) previewHeader+sections().first().contentTop+7.0-inspectorScroll else view.panY
    private data class MapDrag(val original:BuildingNode,val index:Int,val startX:Double,val startY:Double,val projection:BuildingGridProjection)
    private var mapDrag:MapDrag?=null;private val projections=mutableMapOf<String,BuildingGridProjection>()
    private data class AreaStroke(val original:BuildingGraph,val node:String,val cells:Set<P>,val first:P,var last:P,
        val button:Int,val projection:BuildingGridProjection,var dragged:Boolean=false)
    private var areaStroke:AreaStroke?=null
    private data class TableDrag(val node:String,val original:BuildingProjection,val startX:Double,val startY:Double,val projection:BuildingGridProjection)
    private var tableDrag:TableDrag?=null;private var movingTable:BuildingProjection?=null
    private val tableReference get()=movingTable ?: graph.projection
    private data class Menu(val node:String,val type:String,val anchor:Int,var first:Int=0,val column:Int=8,val width:Int=0,val at:Pair<Double,Double>?=null,val socket:String="",val insertion:Pair<Double,Double>?=null,var choices:List<Pair<String,String>>?=null,var focus:Int=0)
    private var menu:Menu?=null
    private val groupInspector=GraphGroupInspector(x,y,w,h,
        { if(inspector) graph.nodeGroups.firstOrNull { "@group:${it.id}"==view.selected } else null },
        { graph.nodeGroups.firstOrNull { "@group:${it.id}"==view.selected }?.nodes.orEmpty().mapNotNull { id ->
            graph.nodes.firstOrNull { it.id==id }?.let { id to name(it) }
        } },{ id -> showSource(id) })
    fun hasOpenMenu()=menu!=null
    fun groupMemberPoint(id:String)=groupInspector.memberPoint(id)
    private fun evaluation():Result<BuildingGraphCompiler.Evaluation> {
        val b=definition();val lib=library()
        if(oldBuilding!=b || oldLibrary!=lib || oldRecipes!=Extrusion.outputs.hashCode()) { oldBuilding=b;oldLibrary=lib;oldRecipes=Extrusion.outputs.hashCode();evaluator=runCatching { BuildingGraphCompiler.Evaluation(graph,b.palette,lib.forms,lib.modules) };compiled=null;nodePlans.clear() }
        return evaluator!!
    }
    fun result():Result<CompiledBuilding> { preview?.invoke()?.let { return it };evaluation();if(compiled==null) compiled=runCatching { evaluation().getOrThrow().building() };return compiled!! }
    fun previewPlan():Result<Plan> {
        evaluation()
        return nodePlans.getOrPut(selected.id) { runCatching { evaluation().getOrThrow().preview(selected.id) } }
    }
    fun previewYaw():Float = view.previewYaws[selected.id] ?: 35f
    private fun sections()=view.inspectorSections.layout(listOf("properties" to (ceil(nodeHeight(shown(selected))*scale).toInt()+14))+
        if(nodePreview) listOf("2d" to previewHeight(),"3d" to previewHeight()) else emptyList())
    private fun previewHeight()=(height-previewHeader-38).coerceIn(24,220)
    private fun contentHeight()=sections().last().let { it.top+it.height+4 }
    private fun maxInspectorScroll()=(contentHeight()-(height-previewHeader-4)).coerceAtLeast(0).toDouble()
    fun sectionExpanded(key:String)=view.inspectorSections.isExpanded(key)
    fun revealSection(key:String) {
        val section=sections().firstOrNull { it.key==key } ?: return
        val available=height-previewHeader-4
        if(section.top<inspectorScroll || section.height>available) inspectorScroll=section.top.toDouble()
        else if(section.top+section.height>inspectorScroll+available) inspectorScroll=(section.top+section.height-available).toDouble()
        inspectorScroll=inspectorScroll.coerceIn(0.0,maxInspectorScroll())
    }
    fun showSection(key:String) { view.inspectorSections.setExpanded(key,true);revealSection(key) }
    fun sectionHeaderPoint(key:String):Pair<Double,Double> {
        revealSection(key)
        val section=sections().first { it.key==key }
        return x+width/2.0 to y+previewHeader+section.top-inspectorScroll+11
    }
    fun previewViewport(mode:String="3d"):BuildingPreviewViewport? = sections().firstOrNull { it.key==mode && it.expanded }?.let {
        BuildingPreviewViewport(x+10,(y+previewHeader+it.contentTop-inspectorScroll+3).roundToInt(),width-24,(it.contentHeight-6).coerceAtLeast(1))
    }
    private fun drawNodePreview(g:GuiGraphics,mx:Int,my:Int) {
        for(mode in listOf("2d","3d")) previewViewport(mode)?.let { viewport ->
            if(viewport.y+viewport.height<=y+previewHeader || viewport.y>=y+height) return@let
            val sample=selected.kind in setOf(BuildingKind.BAY_WINDOW,BuildingKind.DORMER) || selected.kind==BuildingKind.ROOF && evaluation().getOrNull()?.let { runCatching { it.roofPreviewUsesSample(selected.id) }.getOrDefault(false) }==true
            val footprint=if(selected.kind==BuildingKind.ROOM && mode=="2d") evaluation().getOrNull()?.let { runCatching { it.room(selected.id).footprint }.getOrNull() }.orEmpty() else emptySet()
            BuildingPreviewRenderer.drawCached(g,viewport,selected,previewPlan(),mode,previewYaw(),sample,footprint)
        }
    }
    private fun shown(n:BuildingNode):BuildingNode { val actual=moving?.takeIf { it.id==n.id } ?: n;return if(inspector) actual.copy(x=0,y=0) else actual }
    private fun ports(n:BuildingNode):List<BuildingPort> = BuildingPorts.of(n,graph,true)
    private val explicitAssembly get()=graph.nodes.any { it.assembly || it.kind in setOf(BuildingKind.AREA,BuildingKind.ROOM_LAYOUT,BuildingKind.SEGMENT_LAYOUT,BuildingKind.ROOM_GEOMETRY,BuildingKind.ROOF_LAYOUT) }
    private fun insertionKinds()=BuildingKind.entries.filter { GraphModels.buildingInsertable(it,explicitAssembly) }
    private fun rectangles(n:BuildingNode)=if(n.kind in setOf(BuildingKind.AREA,BuildingKind.ROOM_GEOMETRY)) if(graph.resolvedNode(n).coordinates.isEmpty()) n.rectangles else RoomGeometry.fromCells(graph.resolvedNode(n).coordinates.toSet()) else graph.resolvedNode(n).rectangles
    private fun layoutKind(n:BuildingNode)=if(n.kind==BuildingKind.STOREY && n.storeyMode=="walls") BuildingKind.ROOM else n.kind
    fun nodeWidth(n:BuildingNode)=when(n.kind) { BuildingKind.ROOM,BuildingKind.ROOM_LAYOUT,BuildingKind.AREA,BuildingKind.ROOM_GEOMETRY,BuildingKind.GARDEN,BuildingKind.PATH->274;BuildingKind.STOREY->302;BuildingKind.BUILDING->324;else->210 }
    private fun mapFirst(n:BuildingNode)=inspector && layoutKind(n) in setOf(BuildingKind.ROOM,BuildingKind.STOREY,BuildingKind.BUILDING)
    private fun body(n:BuildingNode)=(if(mapFirst(n) || ports(n).isEmpty()) 29 else 45+ports(n).size*19)+(if(n.kind==BuildingKind.STOREY) 22 else 0)
    private fun copycatGroups(n:BuildingNode)=library().forms[n.form]?.groups ?: DEFAULT_GROUPS
    private fun copycatMaterialEnd(n:BuildingNode)=44+22*copycatGroups(n).size
    private fun details(n:BuildingNode)=when(layoutKind(n)) { BuildingKind.ROTATION,BuildingKind.MIRRORING->72;BuildingKind.SEGMENT_UNION,BuildingKind.SEGMENT_INTERSECTION,BuildingKind.SEGMENT_DIFFERENCE,BuildingKind.SEGMENT_SYMMETRIC_DIFFERENCE,BuildingKind.COPYCAT_UNION,BuildingKind.COPYCAT_INTERSECTION,BuildingKind.COPYCAT_DIFFERENCE,BuildingKind.COPYCAT_SYMMETRIC_DIFFERENCE->120; BuildingKind.BAY_WINDOW,BuildingKind.DORMER->120; BuildingKind.GARDEN->440;BuildingKind.PATH->396;BuildingKind.COPYCAT->copycatMaterialEnd(n)+(if(n.surfaces.isNullOrEmpty()) 68 else 90);BuildingKind.COPYCAT_ROTATE->160;BuildingKind.SEGMENT_ROTATE->187;BuildingKind.COPYCAT_MIRROR->140;BuildingKind.SEGMENT_LAYOUT->252;BuildingKind.ROOM_LAYOUT->136;BuildingKind.AREA->246;BuildingKind.ROOM_GEOMETRY->272;BuildingKind.ROOF_LAYOUT->90;BuildingKind.ROOF_LEVEL->198;BuildingKind.STAIRWELL,BuildingKind.ELEVATOR_SHAFT,BuildingKind.SERVICE_SHAFT->120;BuildingKind.SEGMENT->209;BuildingKind.PATTERN->181;BuildingKind.SEGMENT_MIRROR->206;BuildingKind.FLOOR,BuildingKind.CEILING->158;BuildingKind.ROOM->if(n.kind==BuildingKind.STOREY) 444 else 389;BuildingKind.STOREY->392;BuildingKind.ROOF->160;BuildingKind.BUILDING->332 }
    fun nodeHeight(n:BuildingNode)=body(n)+details(n)+(if(mapFirst(n)) ports(n).size*19 else 0)
    private fun nx(mx:Double)=(mx-x-panX)/scale
    private fun ny(my:Double)=(my-y-panY)/scale
    private fun point(n:BuildingNode,i:Int)=Pair(n.x.toDouble(),n.y+(if(mapFirst(n)) body(n)+details(n)+8.0 else 36.0)+i*19)
    private fun output(n:BuildingNode)=Pair(n.x+nodeWidth(n).toDouble(),n.y+36.0)
    private fun kindName(kind:BuildingKind)=ui(GraphModels.building(kind).labelKey)
    private fun name(n:BuildingNode):String {
        val label=if(n.id=="empty_wall") ui("empty_wall") else if(n.name in listOf("Room","Storey","Floor","Ceiling","Roof","Building")) kindName(n.kind) else if(n.name=="Solid wall") ui("bg_solid_wall") else n.name.ifEmpty { kindName(n.kind) }
        return "${graph.nodes.indexOfFirst { it.id==n.id }+1}. $label"
    }
    private fun roleLabel(role:String)=if(net.minecraft.client.resources.language.I18n.exists("gui.copycat_architect.role_$role")) ui("role_$role") else role
    private fun text(g:GuiGraphics,s:String,x:Int,y:Int,c:Int=ArchitectTheme.text) { g.drawString(font,s,x,y,c,false) }
    private fun row(g:GuiGraphics,s:String,x:Int,y:Int,w:Int) {
        val hover=!readOnly && pointerX>=x && pointerX<x+w && pointerY>=y && pointerY<y+17
        val marker=s.endsWith(" ▾");val dropdown=marker && !readOnly
        ArchitectTheme.button(g,x,y,w,17,hover && menu==null,enabled=!readOnly)
        text(g,font.plainSubstrByWidth(if(marker) s.dropLast(2) else s,w-if(dropdown) 19 else 9),x+5,y+5,if(readOnly) ArchitectTheme.disabled else ArchitectTheme.text)
        if(dropdown) text(g,"▾",x+w-11,y+5,ArchitectTheme.brass)
    }
    private fun color(k:BuildingKind)=when(k) { BuildingKind.SEGMENT,BuildingKind.PATTERN,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_ROTATE,BuildingKind.FLOOR,BuildingKind.CEILING,BuildingKind.SEGMENT_LAYOUT->0xffff8a27.toInt();BuildingKind.AREA,BuildingKind.ROOM_GEOMETRY,BuildingKind.ROOM_LAYOUT->0xff74b4cf.toInt();BuildingKind.ROOF_LAYOUT,BuildingKind.DORMER->0xffb77865.toInt();BuildingKind.ROOM,BuildingKind.GARDEN->0xff21b15b.toInt();BuildingKind.PATH->0xffc8a46a.toInt();BuildingKind.STOREY->0xffeeeeee.toInt();BuildingKind.ROOF->0xffb77865.toInt();else->ArchitectTheme.green }
    fun fit() {
        if(inspector) { inspectorScroll=0.0;return }
        val a=graph.nodes.minOf { it.x };val b=graph.nodes.minOf { it.y };val width=graph.nodes.maxOf { it.x+nodeWidth(it) }-a+50;val height=graph.nodes.maxOf { it.y+nodeHeight(it) }-b+50
        view.zoom=minOf(this.width.toDouble()/width,this.height.toDouble()/height,1.0).coerceIn(0.2,1.75)
        view.panX=(this.width-width*view.zoom)/2+(25-a)*view.zoom;view.panY=(this.height-height*view.zoom)/2+(25-b)*view.zoom;view.fitted=true
    }
    fun add(kind:BuildingKind) = addAt(kind,((width/2-view.panX)/view.zoom),((height/2-view.panY)/view.zoom))
    fun addAt(kind:BuildingKind,atX:Double,atY:Double) {
        if(readOnly) return
        safe {
            require(graph.nodes.size<GraphModels.building.maxNodes && GraphModels.building(kind).output!=null) { ui("bg_node_limit") }
            var n=GraphModels.newBuilding(kind,explicitAssembly).copy(id=UUID.randomUUID().toString(),name=kindName(kind),
                x=atX.roundToInt().coerceIn(-8000,8000),y=atY.roundToInt().coerceIn(-8000,8000))
            if(kind==BuildingKind.ROOM && !explicitAssembly) n=n.copy(
                floor=graph.nodes.firstOrNull { it.kind==BuildingKind.FLOOR }?.id.orEmpty(),
                ceiling=graph.nodes.firstOrNull { it.kind==BuildingKind.CEILING }?.id.orEmpty())
            view.selected=n.id;inspectorScroll=0.0;commit(graph.copy(nodes=graph.nodes+n))
        }
    }
    fun deleteSelected() { if(readOnly) return;safe { if(graph.nodes.any { it.id==view.selected && it.kind!=BuildingKind.BUILDING && it.id!="empty_wall" }) { val changed=graph.remove(view.selected);view.wire=null;view.selected=changed.nodes.first { it.kind==BuildingKind.BUILDING }.id;inspectorScroll=0.0;commit(changed) } } }
    fun renameSelected() {
        if(readOnly) return
        val n=selected
        EditorValueInput.text(tr("bg_definition_name"),n.name,maxLength=64) { value ->
            if(value!=n.name) safe { graph.nodes.firstOrNull { it.id==n.id }?.let { edit(it.copy(name=value)) } }
        }
    }
    private fun safe(action:()->Unit) { try { action() } catch(e:Exception) { failure(e.message ?: "Invalid building graph") } }
    private fun openSpecialPlacement(n:BuildingNode) {
        val lib=library();val palette=definition().palette
        SpecialPlacementScreen.open(n,graph,{ context -> BuildingGraphCompiler.Evaluation(context,palette,lib.forms,lib.modules) }) { edit(it) }
    }
    private fun edit(n:BuildingNode) { if(readOnly) return;val original=graph.nodes.first { it.id==n.id };val changed=graph.replace(if(inspector) n.copy(x=original.x,y=original.y) else n);BuildingGraphCompiler.structure(changed);commit(changed) }
    fun connect(source:String,target:String,key:String):Boolean = if(readOnly) false else try {
        var changed=graph.connect(target,key,source)
        val n=graph.nodes.first { it.id==target }
        if(source.isNotEmpty() && n.kind==BuildingKind.STOREY && key.startsWith("in:")) {
            val i=key.removePrefix("in:").toInt()
            if(i>0 && n.inputs.getOrNull(i).isNullOrEmpty()) {
                val next=changed.nodes.first { it.id==target };val room=graph.nodes.first { it.id==source }
                val max=n.inputs.mapIndexedNotNull { j,s -> graph.nodes.firstOrNull { it.id==s }?.takeIf { it.kind==BuildingKind.ROOM }?.let { RoomGeometry.cells(rectangles(it)).maxOf { it.x }+(n.offsets.getOrNull(j)?.x ?: 0) } }.maxOrNull() ?: -2
                val offset=P(max+2-RoomGeometry.cells(rectangles(room)).minOf { it.x },0,0)
                changed=changed.replace(next.copy(offsets=List(next.inputs.size) { j -> if(j==i) offset else next.offsets.getOrNull(j) ?: P() }))
            }
        }
        BuildingGraphCompiler.structure(changed);commit(changed);true
    } catch(e:Exception) { failure(e.message.orEmpty());false }
    private fun curve(g:GuiGraphics,a:Pair<Double,Double>,b:Pair<Double,Double>,c:Int) {
        val d=maxOf(35.0,abs(a.first-b.first)*0.4);var last=a
        for(i in 1..40) { val t=i/40.0;val k=1-t;val p=Pair(k*k*k*a.first+3*k*k*t*(a.first+d)+3*k*t*t*(b.first-d)+t*t*t*b.first,k*k*k*a.second+3*k*k*t*a.second+3*k*t*t*b.second+t*t*t*b.second)
            val steps=ceil(max(abs(p.first-last.first),abs(p.second-last.second))).toInt().coerceAtLeast(1)
            for(j in 0..steps) { val u=j.toDouble()/steps;val xx=(last.first+(p.first-last.first)*u).roundToInt();val yy=(last.second+(p.second-last.second)*u).roundToInt();g.fill(xx-1,yy-1,xx+2,yy+2,c) };last=p }
    }
    private fun label(p:BuildingPort,owner:BuildingNode):String=when {
        p.key.startsWith("semantic:") -> SemanticPropertyEditors.portLabel(p.key)
        p.key.startsWith("wall:") -> if(p.length>0) ui("bg_wall_port",ui(listOf("north","east","south","west")[p.key.removePrefix("wall:").substringBefore(':').toInt()]),p.length,p.height) else ui("bg_roof_wall_port",ui(listOf("north","east","south","west")[p.key.removePrefix("wall:").toInt()]))
        p.key.startsWith("level:") -> if(p.number==0) ui("bg_ground") else ui(if(p.number>0) "bg_upper" else "bg_lower",abs(p.number))
        p.key.startsWith("access:") -> ui("structure_port",p.key.substringAfter(':').toInt()+1)
        p.key=="area" -> ui("roof_area")
        p.key=="tread" -> ui("stairs_tread")
        p.key=="landing" -> ui("stairs_landing")
        p.key=="enclosure" -> ui("structure_enclosure")
        BuildingKind.ROOF_LEVEL in p.accepts -> ui("roof_level_index",p.number+1)
        owner.kind==BuildingKind.ROOF && owner.assembly && p.key.startsWith("in:") && p.number<6 -> ui("roof_variant_"+listOf("slope","eave","edge","ridge","hip","valley")[p.number])
        p.key=="floor"->ui("bg_floor");p.key=="ceiling"->ui("bg_ceiling");p.key=="roof"->ui("bg_roof")
        p.key=="roofPeak" -> ui("bg_roof_peak")
        p.key=="roofEave" -> ui("bg_roof_eave")
        p.key=="corner" -> ui("bg_corner_copycat")
        p.key.startsWith("edge:")->ui("bg_vertical_edge_port",p.key.removePrefix("edge:").substringBefore(':'),p.key.substringAfterLast(':'),p.height)
        p.key=="layout"->ui(when { BuildingKind.ROOF_LAYOUT in p.accepts -> "bg_roof_layout";BuildingKind.ROOM_LAYOUT in p.accepts -> "bg_room_layout";else -> "bg_segment_layout" })
        p.key=="geometry"->ui(if(BuildingKind.AREA in p.accepts) "bg_area" else "bg_room_geometry")
        p.key.startsWith("region:")->segmentRegionLabel(p.key)
        else->ui(if(p.accepts.any { it in LandscapeGeometry.kinds }) "bg_landscape_index" else if(BuildingKind.COPYCAT in p.accepts) "bg_copycat_index" else if(BuildingKind.ROOM in p.accepts) "bg_room_index" else "bg_segment_index",p.number+1)
    }
    private fun materialRows(g:GuiGraphics,n:BuildingNode,y:Int) {
        val w=nodeWidth(n)-16;row(g,ui("bg_material_mode",ui(if(n.materialMode==MaterialMode.GROUPED) "grouped" else "separated"))+" ▾",n.x+8,y,w)
        row(g,ui("role",roleLabel(n.role)),n.x+8,y+21,w)
    }
    private fun projection(g:GuiGraphics,n:BuildingNode,points:Set<P>,y:Int,h:Int):BuildingGridProjection {
        val visibleHeight=if(inspector && height<220) minOf(h-12,((height-previewHeader-14)/scale).toInt().coerceAtLeast(24)) else h-12
        val p=areaStroke?.takeIf { it.node==n.id }?.projection ?: mapDrag?.takeIf { it.original.id==n.id }?.projection ?: tableDrag?.takeIf { it.node==n.id }?.projection
            ?: BuildingGridProjection.fit(n.x+12,y+6,nodeWidth(n)-24,visibleHeight,points)
        projections[n.id]=p
        ArchitectTheme.inset(g,n.x+8,y,nodeWidth(n)-16,h,ArchitectTheme.blueprint)
        // Continue the same coordinate grid through the unused margins of the viewport.
        val left=n.x+10;val right=n.x+nodeWidth(n)-10;val top=y+2;val bottom=y+h-2
        val first=p.cell(left.toDouble(),top.toDouble());val last=p.cell(right.toDouble(),bottom.toDouble())
        for(xx in first.x..last.x+1) {
            val screen=p.px(xx).toInt()
            if(screen in left until right) g.fill(screen,top,screen+1,bottom,ArchitectTheme.grid)
        }
        for(zz in first.z..last.z+1) {
            val screen=p.py(zz).toInt()
            if(screen in top until bottom) g.fill(left,screen,right,screen+1,ArchitectTheme.grid)
        }
        return p
    }
    fun gridPoint(id:String,cellX:Double,cellZ:Double):Pair<Double,Double> {
        val p=projections[id] ?: error("Map has not rendered")
        return Pair(x+panX+(p.x+(cellX-p.minX)*p.step)*scale,y+panY+(p.y+(cellZ-p.minZ)*p.step)*scale)
    }
    fun tablePoint(id:String)=gridPoint(id,tableReference.table.x+0.5,tableReference.table.z+0.5)
    private fun tableMarker(g:GuiGraphics,p:BuildingGridProjection) {
        val table=tableReference.table;val xx=p.px(table.x).toInt();val yy=p.py(table.z).toInt();val size=p.step.toInt().coerceAtLeast(1)
        g.fill(xx,yy,xx+size,yy+size,ArchitectTheme.brassDark)
        g.renderOutline(xx,yy,size,size,ArchitectTheme.brass)
        val pose=g.pose();pose.pushPose();pose.translate(xx.toDouble(),yy.toDouble(),10.0);pose.scale(size/16f,size/16f,1f)
        g.renderItem(net.minecraft.world.item.ItemStack(Architect.TABLE_ITEM.get()),0,0);g.flush();pose.popPose()
    }
    private fun tableRows(g:GuiGraphics,n:BuildingNode,y:Int) {
        val reference=tableReference
        text(g,ui("bg_table_reference"),n.x+8,y,ArchitectTheme.brass)
        row(g,ui("bg_table_x",reference.table.x),n.x+8,y+14,136);row(g,ui("bg_table_z",reference.table.z),n.x+152,y+14,142)
        row(g,ui("bg_table_y",reference.table.y),n.x+8,y+36,136);row(g,ui("rotation")+": ${reference.turn*90}° ▾",n.x+152,y+36,142)
        text(g,ui("bg_table_drag"),n.x+8,y+58,ArchitectTheme.muted)
    }
    fun inputPoint(id:String,key:String):Pair<Double,Double> {
        val n=shown(graph.nodes.first { it.id==id });val i=ports(n).indexOfFirst { it.key==key };require(i>=0) { "Unknown building input" }
        return Pair(x+panX+(n.x+nodeWidth(n)/2.0)*scale,y+panY+point(n,i).second*scale)
    }
    fun propertyPoint(id:String,row:Int,column:Int=16):Pair<Double,Double> {
        val n=shown(graph.nodes.first { it.id==id })
        return Pair(x+panX+(n.x+column)*scale,y+panY+(n.y+body(n)+row+8)*scale)
    }
    private fun mapScissor(g:GuiGraphics,n:BuildingNode,y:Int,h:Int) {
        g.enableScissor((x+panX+(n.x+8)*scale).toInt(),(this.y+panY+y*scale).toInt(),(x+panX+(n.x+nodeWidth(n)-8)*scale).toInt(),(this.y+panY+(y+h)*scale).toInt())
    }
    private fun strokeRectangle(stroke:AreaStroke)=buildSet {
        for(xx in minOf(stroke.first.x,stroke.last.x)..maxOf(stroke.first.x,stroke.last.x))
            for(zz in minOf(stroke.first.z,stroke.last.z)..maxOf(stroke.first.z,stroke.last.z)) add(P(xx,0,zz))
    }
    private fun strokeCells(stroke:AreaStroke):Set<P> {
        val rectangle=strokeRectangle(stroke)
        return if(stroke.button==1 || !stroke.dragged && stroke.first in stroke.cells) stroke.cells-rectangle else stroke.cells+rectangle
    }
    private fun updateAreaStroke(stroke:AreaStroke,mx:Double,my:Double) {
        val cell=stroke.projection.cell(nx(mx),ny(my))
        stroke.last=P(cell.x.coerceIn(-128,127),0,cell.z.coerceIn(-128,127))
        if(stroke.last!=stroke.first) stroke.dragged=true
    }
    private fun roomMap(g:GuiGraphics,n:BuildingNode,y:Int) {
        val stroke=areaStroke?.takeIf { it.node==n.id }
        val cells=stroke?.let(::strokeCells) ?: when(n.kind) {
            BuildingKind.GARDEN,BuildingKind.PATH -> runCatching { LandscapeGeometry.localFootprint(graph,n) }.getOrDefault(emptySet())
            BuildingKind.AREA,BuildingKind.ROOM_GEOMETRY -> graph.resolvedNode(n).coordinates.toSet()
            else -> RoomGeometry.cells(rectangles(n))
        }
        val reference=emptySet<P>()
        val p=projection(g,n,if(n.kind==BuildingKind.STOREY) cells+tableReference.table.copy(y=0) else cells+reference,y,164);mapScissor(g,n,y,164)
        for(cell in reference) g.fill(p.px(cell.x).toInt()+1,p.py(cell.z).toInt()+1,p.px(cell.x+1).toInt(),p.py(cell.z+1).toInt(),ArchitectTheme.muted)
        val hovered=if(!readOnly && stroke==null && p.contains(pointerX,pointerY)) p.cell(pointerX,pointerY) else null
        val gap=if(p.step>=3) 1 else 0
        for(cell in cells+listOfNotNull(hovered)) {
            val a=p.px(cell.x).toInt();val b=p.py(cell.z).toInt();val c=p.px(cell.x+1).toInt();val d=p.py(cell.z+1).toInt()
            val active=cell in cells
            g.fill(a+gap,b+gap,c,d,if(cell==hovered) (if(active) 0xff9a6250.toInt() else 0xff487a59.toInt()) else color(if(n.kind in LandscapeGeometry.kinds) n.kind else BuildingKind.ROOM))
        }
        for(cell in cells) {
            val a=p.px(cell.x).toInt();val b=p.py(cell.z).toInt();val c=p.px(cell.x+1).toInt();val d=p.py(cell.z+1).toInt();val wall=color(BuildingKind.SEGMENT)
            if(cell+P(0,0,-1) !in cells) g.fill(a,b,c,b+1,wall)
            if(cell+P(1,0,0) !in cells) g.fill(c-1,b,c,d,wall)
            if(cell+P(0,0,1) !in cells) g.fill(a,d-1,c,d,wall)
            if(cell+P(-1,0,0) !in cells) g.fill(a,b,a+1,d,wall)
        }
        if(stroke!=null) {
            for(cell in stroke.cells-cells) g.fill(p.px(cell.x).toInt()+gap,p.py(cell.z).toInt()+gap,p.px(cell.x+1).toInt(),p.py(cell.z+1).toInt(),0xff9a6250.toInt())
            val left=p.px(minOf(stroke.first.x,stroke.last.x)).toInt();val top=p.py(minOf(stroke.first.z,stroke.last.z)).toInt()
            val right=p.px(maxOf(stroke.first.x,stroke.last.x)+1).toInt();val bottom=p.py(maxOf(stroke.first.z,stroke.last.z)+1).toInt()
            g.renderOutline(left,top,(right-left).coerceAtLeast(1),(bottom-top).coerceAtLeast(1),ArchitectTheme.brass)
        }
        if(n.kind==BuildingKind.PATH) n.coordinates.forEachIndexed { i,point -> text(g,"${i+1}",p.px(point.x).toInt()+2,p.py(point.z).toInt()+2,ArchitectTheme.brass) }
        if(n.kind==BuildingKind.STOREY) tableMarker(g,p)
        g.flush();g.disableScissor()
    }
    private fun storeyMap(g:GuiGraphics,n:BuildingNode,y:Int) {
        val rooms=n.inputs.mapIndexedNotNull { i,s -> graph.nodes.firstOrNull { it.id==s && it.kind==BuildingKind.ROOM }?.let { Triple(i,graph.resolvedNode(it),n.offsets.getOrNull(i) ?: P()) } }
        val cells=rooms.flatMap { (_,room,offset) -> RoomGeometry.cells(room.rectangles).map { it+offset } }.toSet()
        val shells=rooms.flatMap { (_,room,offset) -> RoomWallJoints.shell(room).map { it+offset } }.toSet()
        val p=projection(g,n,shells+tableReference.table.copy(y=0),y,143)
        mapScissor(g,n,y,143)
        for(cell in shells-cells) g.fill(p.px(cell.x).toInt()+1,p.py(cell.z).toInt()+1,p.px(cell.x+1).toInt(),p.py(cell.z+1).toInt(),ArchitectTheme.brassDark)
        val counts=rooms.flatMap { (_,room,offset) -> RoomGeometry.cells(room.rectangles).map { it+offset } }.groupingBy { it }.eachCount()
        for((i,room,offset) in rooms) for(cell in RoomGeometry.cells(rectangles(room))) {
            val a=cell+offset;val xx=p.px(a.x).toInt();val yy=p.py(a.z).toInt()
            g.fill(xx+1,yy+1,p.px(a.x+1).toInt(),p.py(a.z+1).toInt(),if(counts.getValue(a)>1) 0xffc75b4d.toInt() else if(i==(view.room[n.id] ?: 0)) 0xffeeeeee.toInt() else color(BuildingKind.ROOM))
        }
        for((i,room,offset) in rooms) { val a=RoomGeometry.cells(room.rectangles).minWith(compareBy({it.z},{it.x}))+offset;text(g,"${i+1}",p.px(a.x).toInt()+2,p.py(a.z).toInt()+2,ArchitectTheme.shadow) }
        tableMarker(g,p)
        g.flush();g.disableScissor()
    }
    fun openSourceCoordinates(id:String) {
        val node=graph.nodes.first { it.id==id && it.kind in setOf(BuildingKind.AREA,BuildingKind.ROOM_GEOMETRY,BuildingKind.GARDEN,BuildingKind.PATH) }
        if(!readOnly) BuildingSourceEditors.coordinates(node,{graph},commit) { BuildingSourceEditors.validateBuilding(definition().copy(graph=it)) }
    }
    fun openSourceLayout(id:String) {
        val node=graph.nodes.first { it.id==id && it.kind==BuildingKind.SEGMENT_LAYOUT }
        if(!readOnly) BuildingSourceEditors.layout(node,{graph},commit) { BuildingSourceEditors.validateBuilding(definition().copy(graph=it)) }
    }
    private fun showSource(id:String) {
        if(graph.nodes.none { it.id==id }) return
        view.selected=id;inspectorScroll=0.0;view.inspectorNode=""
        onSelectDefinition?.invoke(id)
    }
    private fun layoutMap(g:GuiGraphics,n:BuildingNode,top:Int) {
        val geometry=graph.resolvedNode(n)
        val area=graph.areaOf(n)
        val occupied=area?.segmentCells()?.map { it.x to it.y }?.toSet()
        val cells=(if(n.layoutCells.isEmpty()) (0 until geometry.height).flatMap { row -> (0 until geometry.length).mapNotNull { column ->
            if(occupied==null || (column to row) in occupied) SegmentLayoutCell(column,row) else null
        } } else n.layoutCells).filter { occupied==null || (it.x to it.y) in occupied }
        val extent=setOf(P(),P(geometry.length-1,0,geometry.height-1))
        val p=projection(g,n,extent,top,128);mapScissor(g,n,top,128)
        val colors=listOf(ArchitectTheme.brass,0xff85b9ce.toInt(),0xffb599d0.toInt(),ArchitectTheme.green)
        val gap=if(p.step>=3) 1 else 0
        for(cell in cells) {
            val displayRow=geometry.height-1-cell.y
            val left=p.px(cell.x).toInt();val upper=p.py(displayRow).toInt()
            val right=p.px(cell.x+1).toInt();val bottom=p.py(displayRow+1).toInt()
            g.fill(left+gap,upper+gap,right,bottom,colors[cell.sourceIndex%colors.size])
            if(n.layoutCells.isNotEmpty() && p.step>=12) text(g,"${cell.sourceIndex+1}",left+3,upper+3,ArchitectTheme.shadow)
        }
        g.flush();g.disableScissor()
    }
    override fun renderWidget(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        if(groupInspector.available) { menu=null;groupInspector.render(g,mx,my,delta);return }
        if(inspector) {
            view.inspectorNode=selected.id;view.inspectorCompact=height<220
            inspectorScroll=inspectorScroll.coerceIn(0.0,maxInspectorScroll())
        }
        if(!view.fitted && !inspector) fit();val lx=nx(mx.toDouble());val ly=ny(my.toDouble());pointerX=lx;pointerY=ly
        if(WorldPeek.active) g.fill(x,y,x+width,y+height,0x3017241c) else ArchitectTheme.inset(g,x,y,width,height,ArchitectTheme.blueprint)
        g.enableScissor(x,y+if(inspector) previewHeader else 0,x+width,y+height);g.pose().pushPose();g.pose().translate(x+panX,y+panY,0.0);g.pose().scale(scale.toFloat(),scale.toFloat(),1f)
        if(!inspector) {
            val gx=floor(-panX/scale/24).toInt()*24;val gy=floor(-panY/scale/24).toInt()*24
            val right=((width-panX)/scale).toInt()+24;val bottom=((height-panY)/scale).toInt()+24
            for(xx in gx..right step 24) g.fill(xx,gy,xx+1,bottom,if(WorldPeek.active) 0x302a4133 else ArchitectTheme.grid)
            for(yy in gy..bottom step 24) g.fill(gx,yy,right,yy+1,if(WorldPeek.active) 0x302a4133 else ArchitectTheme.grid)
            graph.nodes.forEach { raw -> val n=shown(raw);ports(n).forEachIndexed { i,p -> graph.nodes.firstOrNull { it.id==p.source }?.let { curve(g,output(shown(it)),point(n,i),color(it.kind)) } } }
            view.wire?.let { id -> graph.nodes.firstOrNull { it.id==id }?.let { curve(g,output(shown(it)),lx to ly,ArchitectTheme.brass) } }
        }
        for(raw in if(inspector && !sectionExpanded("properties")) emptyList() else if(inspector) listOf(selected) else graph.nodes) {
            val n=shown(raw);val w=nodeWidth(n);val h=nodeHeight(n);val by=n.y+body(n)
            ArchitectTheme.panel(g,n.x,n.y,w,h)
            ArchitectTheme.bevel(g,n.x+2,n.y+2,w-4,21,ArchitectTheme.raised)
            g.fill(n.x+5,n.y+5,n.x+8,n.y+19,color(n.kind))
            val renameWidth=if(inspector && !readOnly) font.width(ui("bg_definition_rename"))+13 else 0
            text(g,font.plainSubstrByWidth(name(n),w-24-renameWidth),n.x+13,n.y+8)
            if(renameWidth>0) text(g,ui("bg_definition_rename"),n.x+w-renameWidth,n.y+8,ArchitectTheme.brass)
            if(n.id==view.selected) g.renderOutline(n.x-1,n.y-1,w+2,h+2,ArchitectTheme.brass)
            ports(n).forEachIndexed { i,p ->
                val sy=point(n,i).second.toInt();val source=graph.nodes.firstOrNull { it.id==p.source }
                row(g,ui("bg_connection",label(p,n),source?.let { name(it) } ?: ui(if(p.key=="roof") if(explicitAssembly) "bg_roof_pending_connection" else "panel_flat_roof" else "bg_no_connection"))+" ▾",n.x+9,sy-8,w-18)
                if(!inspector) { ArchitectTheme.inset(g,n.x-5,sy-5,10,10);g.fill(n.x-2,sy-2,n.x+2,sy+2,if(p.source.isEmpty()) ArchitectTheme.disabled else color(p.accepts.first())) }
            }
            if(!inspector && n.kind!=BuildingKind.BUILDING) { ArchitectTheme.inset(g,n.x+w-5,n.y+31,10,10);g.fill(n.x+w-2,n.y+34,n.x+w+2,n.y+38,color(n.kind)) }
            if(n.kind==BuildingKind.STOREY) row(g,ui("bg_storey_mode",ui("bg_storey_"+n.storeyMode))+" ▾",n.x+8,by-22,w-16)
            when(layoutKind(n)) {
                BuildingKind.ROTATION,BuildingKind.MIRRORING -> row(g,"${n.rotations?.size ?: n.mirrors?.size ?: 0} Schritte",n.x+8,by,w-16)
                BuildingKind.COPYCAT -> {
                    row(g,(if(n.block==BuildingSourceEditors.AIR_SOURCE) ui("bg_air_source") else library().forms[n.form]?.name ?: n.form)+" ▾",n.x+8,by,w-16)
                    row(g,ui("role",roleLabel(n.role)),n.x+8,by+22,w-16)
                    copycatGroups(n).forEachIndexed { index,group ->
                        val role=if(group.role=="surface") n.role else group.role
                        val material=n.surfaces.orEmpty()[group.id] ?: definition().palette[role].orEmpty()
                        row(g,ui("bg_source_group_material",group.name,if(material.isEmpty()) ui("none") else Economy.item(material).description.string)+if(onSelectMaterial!=null) " …" else "",n.x+8,by+44+index*22,w-16)
                    }
                    val end=copycatMaterialEnd(n)
                    row(g,ui("rotation_axis",(n.rotationAxis ?: RotationAxis.Y).name)+" ▾",n.x+8,by+end,w-16)
                    row(g,ui("rotation_angle",n.quarterTurns*90)+" ▾",n.x+8,by+end+22,w-16)
                    text(g,font.plainSubstrByWidth(ui(if(n.surfaces.isNullOrEmpty()) "bg_source_material_palette" else "bg_source_material_local"),w-16),n.x+8,by+end+47,ArchitectTheme.muted)
                    if(!n.surfaces.isNullOrEmpty()) row(g,ui("bg_source_material_inherit"),n.x+8,by+end+66,w-16)
                }
                BuildingKind.COPYCAT_ROTATE,BuildingKind.SEGMENT_ROTATE -> {
                    if(n.kind==BuildingKind.SEGMENT_ROTATE) {
                        val segment=evaluation().getOrNull()?.let { runCatching { it.segment(n.id) }.getOrNull() }
                        text(g,segment?.let { ui("bg_dimensions_3d",it.length,it.height,it.depth) } ?: ui("bg_mirror_connect"),n.x+8,by)
                    } else text(g,ui("port_copycat"),n.x+8,by)
                    row(g,ui("rotation_axis",n.rotationAxis.name)+" ▾",n.x+8,by+20,w-16)
                    row(g,ui("rotation_angle",n.quarterTurns*90)+" ▾",n.x+8,by+42,w-16)
                    materialRows(g,n,by+68)
                    font.split(tr(if(n.kind==BuildingKind.SEGMENT_ROTATE) "bg_segment_rotate_hint" else "bg_copycat_rotate_hint"),w-16).forEachIndexed { i,line -> g.drawString(font,line,n.x+8,by+116+i*11,ArchitectTheme.muted,false) }
                    if(n.kind==BuildingKind.SEGMENT_ROTATE) row(g,ui("semantic_segment_settings"),n.x+8,by+162,w-16)
                }
                BuildingKind.COPYCAT_MIRROR -> {
                    val plane=n.mirror?.plane ?: CopycatMirrorPlane.X
                    row(g,ui("mirror_plane",ui("mirror_plane_"+plane.name.lowercase()))+" ▾",n.x+8,by,w-16)
                    font.split(tr("copycat_mirror_hint"),w-16).forEachIndexed { i,line -> g.drawString(font,line,n.x+8,by+25+i*11,ArchitectTheme.muted,false) }
                    materialRows(g,n,by+68)
                }
                BuildingKind.SEGMENT_LAYOUT -> {
                    val geometry=graph.resolvedNode(n)
                    row(g,ui("bg_fixed_length",geometry.length),n.x+8,by,94);row(g,ui("height",geometry.height),n.x+108,by,94)
                    row(g,ui("bg_depth",n.depth),n.x+8,by+22,w-16)
                    text(g,ui(if(n.layoutCells.isEmpty()) "bg_layout_repeat" else "bg_layout_explicit",n.layoutCells.size),n.x+8,by+47,ArchitectTheme.muted)
                    row(g,ui("bg_layout_cell_list"),n.x+8,by+66,w-16)
                    layoutMap(g,n,by+92)
                    font.split(tr("bg_layout_cell_format"),w-16).forEachIndexed { i,line -> g.drawString(font,line,n.x+8,by+226+i*11,ArchitectTheme.muted,false) }
                }
                BuildingKind.BAY_WINDOW,BuildingKind.DORMER -> {
                    row(g,ui("special_definition_settings"),n.x+8,by,w-16)
                    text(g,ui("special_sample_hint"),n.x+8,by+28,ArchitectTheme.muted)
                }
                BuildingKind.ROOM_LAYOUT -> {
                    row(g,ui("semantic_layout_edit"),n.x+8,by,w-16)
                    row(g,"+ ${ui("input")}",n.x+8,by+22,94);row(g,"− ${ui("input")}",n.x+108,by+22,94)
                    val layout=evaluation().getOrNull()?.let { ev -> runCatching { RoomTopology.resolve(n,ev::segment) }.getOrNull() }
                    text(g,ui("semantic_layout_instances",n.roomLayout?.instances.orEmpty().size),n.x+8,by+49,ArchitectTheme.brass)
                    if(layout!=null) {
                        text(g,ui("bg_dimensions_3d",layout.width,layout.height,layout.depth),n.x+8,by+65)
                        text(g,ui(if(layout.closed) "semantic_closed" else "semantic_open"),n.x+8,by+81,ArchitectTheme.muted)
                    }
                    font.split(tr("semantic_layout_short_help"),w-16).forEachIndexed { i,line -> g.drawString(font,line,n.x+8,by+101+i*11,ArchitectTheme.muted,false) }
                }
                BuildingKind.AREA,BuildingKind.ROOM_GEOMETRY -> {
                    row(g,ui("bg_coordinate_list"),n.x+8,by,w-16)
                    roomMap(g,n,by+24)
                    text(g,ui("bg_room_cells",graph.resolvedNode(n).coordinates.size,RoomGeometry.boundary(graph.resolvedNode(n).coordinates.toSet()).size),n.x+8,by+194,ArchitectTheme.muted)
                    text(g,ui("bg_area_grid_help"),n.x+8,by+209,ArchitectTheme.brass)
                    text(g,ui("bg_geometry_shared"),n.x+8,by+224,ArchitectTheme.muted)
                    if(n.kind==BuildingKind.ROOM_GEOMETRY) row(g,ui("room_height",n.height),n.x+8,by+244,w-16)
                }
                BuildingKind.ROOF_LAYOUT -> {
                    text(g,ui("roof_level_count",n.inputs.count { it.isNotEmpty() }),n.x+8,by,ArchitectTheme.brass)
                    font.split(tr("roof_layout_hint"),w-16).forEachIndexed { i,line -> g.drawString(font,line,n.x+8,by+22+i*12,ArchitectTheme.muted,false) }
                }
                BuildingKind.ROOF_LEVEL -> {
                    val c=n.roofLevel ?: RoofLevelConfig()
                    row(g,ui("roof_level_settings"),n.x+8,by,w-16)
                    text(g,ui("roof_form_"+c.form.name.lowercase()),n.x+8,by+27,ArchitectTheme.brass)
                    font.split(Component.literal(BuildingStructureEditors.slopeLabel(c.slope)),w-16).forEachIndexed { i,line -> g.drawString(font,line,n.x+8,by+44+i*12,ArchitectTheme.text,false) }
                    text(g,ui("roof_repeat",c.slope.q,c.slope.p),n.x+8,by+76,ArchitectTheme.muted)
                    text(g,ui("roof_mode_"+c.mode.name.lowercase()),n.x+8,by+96,ArchitectTheme.text)
                    row(g,ui("dormer_instances"),n.x+8,by+132,w-16)
                    row(g,ui("special_place_instances"),n.x+8,by+154,w-16)
                    font.split(Component.literal(BuildingStructureEditors.storeyLabel(graph,c.storey)),w-16).forEachIndexed { i,line -> g.drawString(font,line,n.x+8,by+115+i*12,ArchitectTheme.muted,false) }
                }
                BuildingKind.STAIRWELL,BuildingKind.ELEVATOR_SHAFT,BuildingKind.SERVICE_SHAFT -> {
                    row(g,ui("structure_settings"),n.x+8,by,w-16)
                    val s=n.stairwell ?: StairwellConfig()
                    val description=if(n.kind==BuildingKind.STAIRWELL) ui("stairs_layout_"+s.layout.name.lowercase())+" · "+ui("stairs_width")+" ${s.width}" else ui("structure_connect_hint")
                    font.split(Component.literal(description),w-16).forEachIndexed { i,line -> g.drawString(font,line,n.x+8,by+28+i*12,ArchitectTheme.brass,false) }
                    font.split(tr(if(n.kind==BuildingKind.ELEVATOR_SHAFT) "elevator_preparation_hint" else "structure_core_hint"),w-16).forEachIndexed { i,line -> g.drawString(font,line,n.x+8,by+63+i*12,ArchitectTheme.muted,false) }
                }
                BuildingKind.SEGMENT -> {
                    val source=if(n.assembly) ui("bg_open_layout") else if(n.inputs.any { it.isNotEmpty() }) ui("bg_connected_copycats") else if(n.block=="minecraft:air") ui("empty_wall") else if(n.block.isNotEmpty()) n.block.substringAfter(':') else library().modules[n.module]?.name ?: ui("bg_solid_wall")
                    row(g,source+if(n.id!="empty_wall") " ▾" else "",n.x+8,by,w-16)
                    val s=evaluation().getOrNull()?.let { runCatching { it.segment(n.id) }.getOrNull() }
                    row(g,ui("bg_fixed_length",s?.length ?: n.length),n.x+8,by+22,94);row(g,ui("height",s?.height ?: n.height),n.x+108,by+22,94)
                    row(g,ui("role",roleLabel(n.role)),n.x+8,by+44,w-16)
                    row(g,ui("bg_depth",s?.depth ?: n.depth),n.x+8,by+66,w-16)
                    text(g,if(n.block=="minecraft:air") ui("bg_adaptive_air") else ui("bg_fixed_segment"),n.x+8,by+93,ArchitectTheme.muted)
                    text(g,ui(if(n.assembly) "bg_segment_planes" else "bg_segment_one_sided"),n.x+8,by+106,ArchitectTheme.brass)
                    font.split(tr(if(n.assembly) "bg_segment_layout_hint" else "bg_segment_opposite"),w-16).forEachIndexed { i,line -> g.drawString(font,line,n.x+8,by+119+i*11,ArchitectTheme.muted,false) }
                    row(g,ui("semantic_segment_settings"),n.x+8,by+160,w-16)
                    row(g,ui("semantic_attachment_settings"),n.x+8,by+186,w-16)
                }
                BuildingKind.SEGMENT_UNION, BuildingKind.SEGMENT_INTERSECTION, BuildingKind.SEGMENT_DIFFERENCE, BuildingKind.SEGMENT_SYMMETRIC_DIFFERENCE, BuildingKind.COPYCAT_UNION, BuildingKind.COPYCAT_INTERSECTION, BuildingKind.COPYCAT_DIFFERENCE, BuildingKind.COPYCAT_SYMMETRIC_DIFFERENCE -> {
                    if(n.kind !in setOf(BuildingKind.SEGMENT_DIFFERENCE,BuildingKind.COPYCAT_DIFFERENCE)) { row(g,"+ ${ui("input")}",n.x+8,by,94);row(g,"− ${ui("input")}",n.x+108,by,94) }
                    materialRows(g,n,by+28)
                }
                BuildingKind.PATTERN -> {
                    val s=evaluation().getOrNull()?.let { runCatching { it.segment(n.id) }.getOrNull() };text(g,s?.let { ui("bg_dimensions_3d",it.length,it.height,it.depth) } ?: ui("bg_connect_pattern"),n.x+8,by)
                    row(g,"+ ${ui("input")}",n.x+8,by+17,94);row(g,"− ${ui("input")}",n.x+108,by+17,94)
                    row(g,ui("bg_pattern_axis",ui("bg_pattern_"+n.patternAxis))+" ▾",n.x+8,by+40,w-16)
                    row(g,ui("bg_segment_arrange"),n.x+8,by+62,w-16)
                    materialRows(g,n,by+84)
                    row(g,ui("semantic_segment_settings"),n.x+8,by+158,w-16)
                }
                BuildingKind.SEGMENT_MIRROR -> {
                    val s=evaluation().getOrNull()?.let { runCatching { it.segment(n.id) }.getOrNull() }
                    text(g,s?.let { ui("bg_dimensions_3d",it.length,it.height,it.depth) } ?: ui("bg_mirror_connect"),n.x+8,by)
                    val axis=n.rotationAxis ?: RotationAxis.X
                    row(g,ui("bg_mirror_axis",axis.name)+" ▾",n.x+8,by+20,w-16)
                    row(g,ui("bg_mirror_scope",ui(if(n.mirrorPositionsOnly==true) "bg_mirror_positions_only" else "bg_mirror_whole_object"))+" ▾",n.x+8,by+40,w-16)
                    text(g,ui("bg_mirror_axis_"+axis.name.lowercase()),n.x+8,by+65,ArchitectTheme.brass)
                    materialRows(g,n,by+88)
                    font.split(tr("bg_mirror_hint"),w-16).forEachIndexed { i,line -> g.drawString(font,line,n.x+8,by+135+i*11,ArchitectTheme.muted,false) }
                    row(g,ui("semantic_segment_settings"),n.x+8,by+182,w-16)
                }
                BuildingKind.FLOOR,BuildingKind.CEILING -> {
                    row(g,(if(n.inputs.any { it.isNotEmpty() }) ui("bg_connected_copycats") else library().forms[n.form]?.name ?: n.form)+" ▾",n.x+8,by,w-16)
                    row(g,ui("bg_thickness",n.thickness),n.x+8,by+22,w-16)
                    row(g,ui(if(n.occupied) "bg_boundary_occupied" else "bg_boundary_free")+" ▾",n.x+8,by+44,w-16)
                    if(n.kind==BuildingKind.FLOOR) row(g,ui("bg_finish",n.finish.substringAfter(':').ifEmpty { ui("none") })+" ▾",n.x+8,by+66,w-16)
                    row(g,ui("role",roleLabel(n.role)),n.x+8,by+88,w-16)
                    if(n.kind==BuildingKind.CEILING) row(g,ui("semantic_attachment_settings"),n.x+8,by+116,w-16)
                }
                BuildingKind.GARDEN,BuildingKind.PATH -> {
                    val garden=n.kind==BuildingKind.GARDEN
                    row(g,ui(if(garden) "bg_coordinate_list" else "bg_path_coordinates"),n.x+8,by,w-16)
                    roomMap(g,n,by+24)
                    text(g,ui(if(garden) "bg_cell_toggle" else "bg_path_map_hint"),n.x+8,by+194,ArchitectTheme.muted)
                    row(g,ui("bg_surface_block",Economy.item(n.block).description.string)+" ▾",n.x+8,by+210,w-16)
                    row(g,ui("bg_elevation",n.elevation),n.x+8,by+232,w-16)
                    row(g,ui("bg_ground_depth",n.groundDepth),n.x+8,by+254,w-16)
                    row(g,ui("bg_ground_block",Economy.item(n.groundBlock).description.string)+" ▾",n.x+8,by+276,w-16)
                    row(g,ui("bg_clearance",n.clearance),n.x+8,by+298,w-16)
                    if(garden) {
                        row(g,ui(if(n.fenced) "bg_fenced_on" else "bg_fenced_off")+" ▾",n.x+8,by+320,w-16)
                        row(g,ui("bg_fence_block",Economy.item(n.fenceBlock).description.string)+" ▾",n.x+8,by+342,w-16)
                        row(g,ui("bg_fence_height",n.fenceHeight),n.x+8,by+364,w-16)
                        materialRows(g,n,by+386)
                    } else {
                        row(g,ui("bg_path_width",n.pathWidth),n.x+8,by+320,w-16)
                        materialRows(g,n,by+342)
                    }
                }
                BuildingKind.ROOM -> {
                    if(n.kind==BuildingKind.ROOM) {
                        row(g,ui("bay_instances"),n.x+8,by+340,w-16)
                        row(g,ui("special_place_instances"),n.x+8,by+362,w-16)
                    }
                    if(n.kind==BuildingKind.ROOM && n.roomConfig?.mode==RoomMode.SEGMENTS) {
                        row(g,ui("semantic_room_settings"),n.x+8,by,w-16)
                        row(g,ui("semantic_layout_edit"),n.x+8,by+22,w-16)
                        val layout=graph.nodes.firstOrNull { it.id==n.layout && it.kind==BuildingKind.ROOM_LAYOUT }
                        val shape=layout?.let { source -> evaluation().getOrNull()?.let { ev -> runCatching { RoomTopology.resolve(source,ev::segment) }.getOrNull() } }
                        if(shape!=null) {
                            text(g,ui("semantic_layout_summary",shape.width,shape.depth,n.roomConfig.heightOverride ?: shape.height,shape.interior.size,ui(if(shape.closed) "semantic_closed" else "semantic_open")),n.x+8,by+51,ArchitectTheme.brass)
                        }
                        font.split(tr("semantic_room_help"),w-16).forEachIndexed { i,line -> g.drawString(font,line,n.x+8,by+76+i*11,ArchitectTheme.muted,false) }
                    } else {
                    val geometry=graph.resolvedNode(n)
                    row(g,ui("room_height",geometry.height),n.x+8,by,115)
                    val floor=graph.nodes.firstOrNull { it.id==n.floor };val ceiling=graph.nodes.firstOrNull { it.id==n.ceiling }
                    text(g,ui("bg_buildable",geometry.height-(if(floor?.occupied==true) 1 else 0)-(if(ceiling?.occupied==true) 1 else 0)),n.x+132,by+5)
                    roomMap(g,n,by+24)
                    if(n.assembly) row(g,ui("bg_open_geometry"),n.x+8,by+190,w-16) else text(g,ui("bg_cell_toggle"),n.x+8,by+194,ArchitectTheme.muted)
                    val footprint=RoomGeometry.cells(rectangles(n));val shell=RoomWallJoints.shell(n.copy(rectangles=rectangles(n)))
                    val room=if(n.kind==BuildingKind.ROOM) evaluation().getOrNull()?.let { runCatching { it.room(n.id) }.getOrNull() } else null
                    val interior=room?.footprint ?: footprint
                    val innerWidth=if(interior.isEmpty()) 0 else interior.maxOf { it.x }-interior.minOf { it.x }+1;val innerDepth=if(interior.isEmpty()) 0 else interior.maxOf { it.z }-interior.minOf { it.z }+1
                    val outerWidth=room?.outerWidth ?: (shell.maxOf { it.x }-shell.minOf { it.x }+1);val outerDepth=room?.outerDepth ?: (shell.maxOf { it.z }-shell.minOf { it.z }+1)
                    val totalHeight=room?.totalHeight ?: (geometry.height+(floor?.thickness ?: 0)+(ceiling?.thickness ?: 0)-(if(floor?.occupied==true) 1 else 0)-(if(ceiling?.occupied==true) 1 else 0))
                    text(g,ui("bg_room_cells",footprint.size,RoomGeometry.edges(rectangles(n)).size),n.x+8,by+207,ArchitectTheme.muted)
                    text(g,ui("bg_room_inner_size",innerWidth,innerDepth),n.x+8,by+220)
                    text(g,ui("bg_room_outer_size",outerWidth,outerDepth),n.x+8,by+233)
                    text(g,ui("bg_room_total_height",totalHeight),n.x+8,by+246,ArchitectTheme.brass);materialRows(g,n,by+264)
                    if(n.kind==BuildingKind.STOREY) { tableRows(g,n,by+310);row(g,ui("semantic_facade_settings"),n.x+8,by+394,w-16);row(g,ui("semantic_convert_storey"),n.x+8,by+420,w-16) }
                    else row(g,ui("semantic_room_settings"),n.x+8,by+318,w-16)
                    }
                }
                BuildingKind.STOREY -> {
                    val index=(view.room[n.id] ?: 0).coerceIn(0,n.inputs.lastIndex.coerceAtLeast(0));val offset=n.offsets.getOrNull(index) ?: P()
                    row(g,ui("bg_room_index",index+1)+" ▾",n.x+8,by,100);row(g,"+",n.x+114,by,35);row(g,"−",n.x+154,by,35)
                    row(g,"X ${offset.x}",n.x+8,by+22,100);row(g,"Z ${offset.z}",n.x+114,by+22,100)
                    storeyMap(g,n,by+45);text(g,ui("bg_room_drag"),n.x+8,by+194,ArchitectTheme.muted);materialRows(g,n,by+211)
                    tableRows(g,n,by+258)
                    row(g,ui("semantic_facade_settings"),n.x+8,by+342,w-16)
                    row(g,ui("semantic_convert_storey"),n.x+8,by+368,w-16)
                }
                BuildingKind.ROOF -> if(n.assembly) {
                    row(g,ui("bg_open_layout"),n.x+8,by,w-16)
                    row(g,ui("roof_templates"),n.x+8,by+22,w-16)
                    font.split(tr("roof_layout_hint"),w-16).forEachIndexed { i,line -> g.drawString(font,line,n.x+8,by+50+i*12,ArchitectTheme.muted,false) }
                } else {
                    row(g,ui(n.roofType)+" ▾",n.x+8,by,w-16);row(g,ui("bg_ridge",if(n.ridge==0) "Z" else "X")+" ▾",n.x+8,by+22,w-16)
                    row(g,ui("overhang",n.overhang),n.x+8,by+44,94);row(g,ui("bg_thickness",n.thickness),n.x+108,by+44,94)
                    row(g,ui("role",roleLabel(n.role)),n.x+8,by+66,w-16)
                    font.split(tr("bg_roof_covering"),w-16).forEachIndexed { i,line -> g.drawString(font,line,n.x+8,by+91+i*11,ArchitectTheme.muted,false) }
                    font.split(tr("bg_roof_fill"),w-16).forEachIndexed { i,line -> g.drawString(font,line,n.x+8,by+117+i*11,ArchitectTheme.muted,false) }
                }
                BuildingKind.BUILDING -> {
                    row(g,ui("bg_site_edit"),n.x+8,by,w-16)
                    row(g,ui("bg_filter_"+n.preview)+" ▾",n.x+8,by+22,w-16)
                    ArchitectTheme.viewport(g,n.x+8,by+45,w-16,150)
                    val ready=if(n.id!=graph.resultNode().id) runCatching { CompiledBuilding(evaluation().getOrThrow().preview(n.id),emptyList()) } else if(preview!=null) preview.invoke() else result()
                    if(ready==null) text(g,ui("preview_calculating"),n.x+10,by+55,ArchitectTheme.muted)
                    else ready.getOrNull()?.let { b -> BuildingScene.drawCached(g,b.plan,n.x+8,by+45,w-16,150,view.yaw,n.preview);text(g,ui("bg_plan_cost",b.plan.placements.size,b.plan.raw),n.x+8,by+202,ArchitectTheme.brass) }
                        ?: font.split(Component.literal(ready.exceptionOrNull()?.message.orEmpty()),w-20).take(12).forEachIndexed { i,line -> g.drawString(font,line,n.x+10,by+55+i*11,ArchitectTheme.danger,false) }
                    text(g,ui("bg_site_hint"),n.x+8,by+221,ArchitectTheme.muted);text(g,ui("node_preview_hint"),n.x+8,by+235,ArchitectTheme.muted);materialRows(g,n,by+257)
                    row(g,ui("structure_instances")+" · ${n.accessInstances.size}",n.x+8,by+306,w-16)
                }
            }
        }
        g.flush();g.pose().popPose();g.disableScissor()
        if(inspector) {
            ArchitectTheme.bevel(g,x,y,width,35,ArchitectTheme.panel)
            g.fill(x+10,y+33,x+width-10,y+34,ArchitectTheme.brassDark)
            val hover=mx>=x+10 && mx<x+width-10 && my>=y+8 && my<y+27
            ArchitectTheme.button(g,x+10,y+8,width-20,19,hover)
            text(g,font.plainSubstrByWidth(ui("bg_node_picker",name(selected)),width-45),x+15,y+14)
            text(g,"▾",x+width-23,y+14,ArchitectTheme.brass)
            g.enableScissor(x,y+previewHeader,x+width,y+height)
            for(section in sections()) {
                val top=(y+previewHeader+section.top-inspectorScroll).roundToInt()
                val hovered=mx>=x+8 && mx<x+width-14 && my>=top && my<top+22 && my>=y+previewHeader
                ArchitectTheme.button(g,x+8,top,width-22,22,hovered)
                val label=when(section.key) { "properties"->ui("node_properties");"2d"->ui("inspector_preview_2d");else->ui("preview3d") }
                text(g,font.plainSubstrByWidth("${if(section.expanded) "▾" else "▸"} $label",(width-36).coerceAtLeast(1)),x+14,top+7,ArchitectTheme.brass)
            }
            if(nodePreview) drawNodePreview(g,mx,my)
            val overflow=maxInspectorScroll()
            if(overflow>0) {
                val track=(height-previewHeader-6).coerceAtLeast(1)
                val thumb=(track.toDouble()*track/(track+overflow)).roundToInt().coerceIn(minOf(10,track),track)
                val top=y+previewHeader+3+((track-thumb)*inspectorScroll/overflow).roundToInt()
                g.fill(x+width-6,y+previewHeader+3,x+width-4,y+height-3,ArchitectTheme.well)
                g.fill(x+width-6,top,x+width-4,top+thumb,ArchitectTheme.brass)
            }
            g.disableScissor()
        }
        g.enableScissor(x,y,x+width,y+height);drawMenu(g,mx,my);g.disableScissor()
    }
    private fun options(m:Menu):List<Pair<String,String>> {
        m.choices?.let { return it }
        val n=graph.nodes.firstOrNull { it.id==m.node }
        return when(m.type) {
            "insert" -> insertionKinds().map { it.name to kindName(it) }
            "nodes" -> graph.nodes.filter { (allowedNodes==null || it.id in allowedNodes.invoke()) && (definitionKinds==null || it.kind in definitionKinds) }.map { it.id to (name(it)+" · "+kindName(it.kind)) }
            "input" -> {
                val port=n?.let { ports(it).firstOrNull { p -> p.key==m.socket } }
                listOf("" to ui(if(m.socket=="roof") if(explicitAssembly) "bg_roof_pending_connection" else "panel_flat_roof" else "bg_no_connection"))+graph.nodes.filter { candidate -> (allowedNodes==null || candidate.id in allowedNodes.invoke()) && candidate.id!=m.node && candidate.kind in port?.accepts.orEmpty() && runCatching { graph.connect(m.node,m.socket,candidate.id) }.isSuccess }.map { it.id to name(it) }
            }
            "source" -> listOf("_solid" to ui("bg_solid_wall"),"_air" to ui("empty_wall"))+library().modules.map { it.key to it.value.name }
            "form" -> (if(n?.kind==BuildingKind.COPYCAT) listOf(BuildingSourceEditors.AIR_SOURCE to ui("bg_air_source")) else emptyList())+library().forms.map { it.key to it.value.name }
            "finish" -> listOf("" to ui("none"))+BuildingGraphCompiler.CARPETS.map { it to Economy.item(it).description.string }
            "roof" -> listOf("flat","gable").map { it to ui(it) }
            "ridge" -> listOf("0" to ui("bg_ridge","Z"),"1" to ui("bg_ridge","X"))
            "surface_block" -> LandscapeGeometry.surfaceBlocks.map { it to Economy.item(it).description.string }
            "ground_block" -> LandscapeGeometry.groundBlocks.map { it to Economy.item(it).description.string }
            "fence_block" -> LandscapeGeometry.fenceBlocks.map { it to Economy.item(it).description.string }
            "fenced" -> listOf("false" to ui("bg_fenced_off"),"true" to ui("bg_fenced_on"))
            "occupied" -> listOf("false" to ui("bg_boundary_free"),"true" to ui("bg_boundary_occupied"))
            "storey_mode" -> listOf("walls","rooms").map { it to ui("bg_storey_"+it) }
            "pattern_axis" -> listOf("horizontal","vertical","depth","transform").map { it to ui("bg_pattern_"+it) }
            "copycat_axis" -> listOf(RotationAxis.X,RotationAxis.Y,RotationAxis.Z).map { it.name to it.name }
            "mirror_axis" -> listOf(RotationAxis.X,RotationAxis.Y,RotationAxis.Z).map { it.name to ui("bg_mirror_axis_"+it.name.lowercase()) }
            "mirror_scope" -> listOf("false" to ui("bg_mirror_whole_object"),"true" to ui("bg_mirror_positions_only"))
            "copycat_mirror_plane" -> CopycatMirrorPlane.entries.map { it.name to ui("mirror_plane_"+it.name.lowercase()) }
            "copycat_turn" -> (0..3).map { it.toString() to "${it*90}°" }
            "table_turn" -> (0..3).map { it.toString() to "${it*90}°" }
            "mode" -> listOf("GROUPED" to ui("grouped"),"SEPARATED" to ui("separated"))
            "room" -> n!!.inputs.indices.map { it.toString() to ui("bg_room_index",it+1) }
            else -> listOf("all","walls","floor","ceiling","roof").map { it to ui("bg_filter_"+it) }
        }.also { m.choices=it }
    }
    private fun current(m:Menu):String {
        val n=graph.nodes.firstOrNull { it.id==m.node } ?: return ""
        return when(m.type) {
            "nodes" -> view.selected
            "input" -> ports(n).firstOrNull { it.key==m.socket }?.source.orEmpty()
            "source" -> if(n.block=="minecraft:air") "_air" else n.module.ifEmpty { "_solid" }
            "storey_mode" -> n.storeyMode;"pattern_axis" -> n.patternAxis;"form" -> if(n.kind==BuildingKind.COPYCAT) BuildingSourceEditors.copycatSource(n) else n.form;"finish" -> n.finish;"role" -> n.role;"roof" -> n.roofType;"ridge" -> n.ridge.toString();"occupied" -> n.occupied.toString();"mode" -> n.materialMode.name
            "surface_block" -> n.block;"ground_block" -> n.groundBlock;"fence_block" -> n.fenceBlock;"fenced" -> n.fenced.toString()
            "number:elevation" -> n.elevation.toString();"number:ground_depth" -> n.groundDepth.toString();"number:clearance" -> n.clearance.toString();"number:path_width" -> n.pathWidth.toString();"number:fence_height" -> n.fenceHeight.toString()
            "room" -> (view.room[n.id] ?: 0).toString()
            "number:length" -> n.length.toString();"number:depth" -> n.depth.toString();"number:height" -> n.height.toString();"number:thickness" -> n.thickness.toString();"number:overhang" -> n.overhang.toString()
            "number:x","number:z" -> (n.offsets.getOrNull(view.room[n.id] ?: 0) ?: P()).let { if(m.type.endsWith("x")) it.x else it.z }.toString()
            "number:table_x" -> graph.projection.table.x.toString()
            "number:table_y" -> graph.projection.table.y.toString()
            "number:table_z" -> graph.projection.table.z.toString()
            "table_turn" -> graph.projection.turn.toString()
            "copycat_axis" -> (n.rotationAxis ?: RotationAxis.Y).name
            "mirror_axis" -> (n.rotationAxis ?: RotationAxis.X).name
            "mirror_scope" -> (n.mirrorPositionsOnly==true).toString()
            "copycat_mirror_plane" -> (n.mirror?.plane ?: CopycatMirrorPlane.X).name
            "copycat_turn" -> n.quarterTurns.toString()
            else -> n.preview
        }
    }
    private fun openMenu(m:Menu) {
        if(readOnly && m.type!="nodes") return
        if(m.type=="form") {
            val n=graph.nodes.first { it.id==m.node }
            menu=null;view.wire=null
            SavedCopycatSelectionScreen.open(Minecraft.getInstance().screen,current(m),n.kind==BuildingKind.COPYCAT) { key -> safe { select(m,key) } }
            return
        }
        if(m.type.startsWith("number:")) {
            val n=graph.nodes.first { it.id==m.node };val number=current(m).toInt()
            val range=when(m.type) {
                "number:length","number:depth"->1..128;"number:height"->1..if(n.kind==BuildingKind.SEGMENT_LAYOUT) 128 else 16;"number:thickness"->(if(n.finish.isNotEmpty()) 2 else 1)..4
                "number:overhang"->0..2
                "number:ground_depth","number:path_width"->1..16;"number:clearance"->0..16;"number:fence_height"->1..4
                "number:table_x","number:table_z"->-128..128;"number:table_y"->-256..256;else->-64..64
            }
            val title=when(m.type) {
                "number:length"->ui("bg_fixed_length",number);"number:depth"->ui("bg_depth",number);"number:height"->ui(if(n.kind==BuildingKind.ROOM) "room_height" else "height",number)
                "number:elevation"->ui("bg_elevation",number);"number:ground_depth"->ui("bg_ground_depth",number);"number:clearance"->ui("bg_clearance",number);"number:path_width"->ui("bg_path_width",number);"number:fence_height"->ui("bg_fence_height",number)
                "number:thickness"->ui("bg_thickness",number);"number:overhang"->ui("overhang",number)
                "number:table_x"->ui("bg_table_x",number);"number:table_y"->ui("bg_table_y",number);"number:table_z"->ui("bg_table_z",number)
                else->"${m.type.last().uppercase()} $number"
            }
            EditorValueInput.integer(Component.literal(title),number,range) { value -> if(value!=number) select(m,value.toString()) }
            return
        }
        if(m.type=="role") {
            val n=graph.nodes.first { it.id==m.node }
            EditorValueInput.text(tr("role_identifier"),n.role,maxLength=32,validate={ it.matches(Regex("[a-zA-Z0-9_-]{1,32}")) }) { role ->
                if(role!=n.role) { onCreateRole(role);select(m,role) }
            }
            return
        }
        menu=m;val entries=options(m);val rows=menuBox(m)[3];val index=entries.indexOfFirst { it.first==current(m) }.coerceAtLeast(0);m.focus=index;m.first=(index-rows/2).coerceIn(0,(entries.size-rows).coerceAtLeast(0))
    }
    private fun menuBox(m:Menu):IntArray {
        val n=graph.nodes.firstOrNull { it.id==m.node }?.let { shown(it) }
        val w=(if(m.width>0) m.width else if(m.type=="insert") 210 else if(m.type=="nodes") width-20 else n?.let { nodeWidth(it)-16 } ?: 210).coerceIn(1,(width-8).coerceAtLeast(1))
        val rows=minOf(options(m).size,((height-8)/19).coerceAtLeast(1))
        val ax=m.at?.first?.roundToInt() ?: (x+panX+((n?.x ?: 0)+m.column)*scale).roundToInt()
        val ay=m.at?.second?.roundToInt() ?: (y+panY+((n?.y ?: 0)+m.anchor+18)*scale).roundToInt()
        val top=y+4;val bottom=y+height-4
        val yy=if(ay+rows*19<=bottom) ay else (ay-rows*19-(if(m.at==null) (18*scale).toInt() else 0)).coerceAtLeast(top)
        return intArrayOf(ax.coerceIn(x+4,maxOf(x+4,x+width-w-4)),yy.coerceIn(top,maxOf(top,bottom-rows*19)),w,rows)
    }
    private fun drawMenu(g:GuiGraphics,mx:Int,my:Int) {
        val m=menu ?: return;val options=options(m);if(options.isEmpty()) return
        val (a,b,w,rows)=menuBox(m);m.first=m.first.coerceIn(0,(options.size-rows).coerceAtLeast(0));val chosen=current(m)
        g.flush();g.pose().pushPose();g.pose().translate(0f,0f,500f)
        ArchitectTheme.bevel(g,a-2,b-2,w+4,rows*19+4,ArchitectTheme.panel)
        repeat(rows) { i ->
            val entry=options[i+m.first];val hover=mx>=a && mx<a+w && my>=b+i*19 && my<b+(i+1)*19
            val active=entry.first==chosen && m.type!="insert"
            ArchitectTheme.button(g,a,b+i*19,w,19,hover || i+m.first==m.focus,active)
            text(g,font.plainSubstrByWidth(entry.second,w-16),a+6,b+i*19+5,if(active) ArchitectTheme.brass else ArchitectTheme.text)
        }
        if(options.size>rows) { val track=rows*19;val thumb=maxOf(10,track*rows/options.size);val sy=b+(track-thumb)*m.first/(options.size-rows);g.fill(a+w-3,sy,a+w-1,sy+thumb,ArchitectTheme.brass) }
        g.flush();g.pose().popPose()
    }
    private fun select(m:Menu,value:String) {
        if(m.type=="insert") { val at=m.insertion ?: return;addAt(BuildingKind.valueOf(value),at.first,at.second);return }
        if(m.type=="nodes") { view.selected=value;view.wire=null;inspectorScroll=0.0;onSelectDefinition?.invoke(value);return }
        if(m.type=="input") { connect(value,m.node,m.socket);return }
        if(m.type=="room") { view.room[m.node]=value.toInt();return }
        if(m.type in listOf("number:table_x","number:table_y","number:table_z","table_turn")) {
            val p=graph.projection;val table=p.table;val number=value.toInt()
            val changed=when(m.type) { "number:table_x"->p.copy(table=table.copy(x=number));"number:table_y"->p.copy(table=table.copy(y=number));"number:table_z"->p.copy(table=table.copy(z=number));else->p.copy(turn=number) }
            commit(graph.copy(projection=changed.validate()));return
        }
        val n=graph.nodes.first { it.id==m.node }
        edit(when(m.type) {
            "source" -> if(n.id=="empty_wall") n else when(value) { "_air"->n.copy(block="minecraft:air",module="");"_solid"->n.copy(block="",module="");else->n.copy(block="",module=value,length=library().modules.getValue(value).width,height=library().modules.getValue(value).height) }
            "storey_mode"->n.copy(storeyMode=value,inputs=if(value=="rooms") n.inputs.ifEmpty { listOf("") } else n.inputs)
            "surface_block"->n.copy(block=value);"ground_block"->n.copy(groundBlock=value);"fence_block"->n.copy(fenceBlock=value);"fenced"->n.copy(fenced=value.toBoolean())
            "number:elevation"->n.copy(elevation=value.toInt());"number:ground_depth"->n.copy(groundDepth=value.toInt());"number:clearance"->n.copy(clearance=value.toInt());"number:path_width"->n.copy(pathWidth=value.toInt());"number:fence_height"->n.copy(fenceHeight=value.toInt())
            "pattern_axis"->n.copy(patternAxis=value)
            "copycat_axis","mirror_axis"->n.copy(rotationAxis=RotationAxis.valueOf(value));
            "mirror_scope"->n.copy(mirrorPositionsOnly=value.toBoolean());"copycat_mirror_plane"->n.copy(mirror=CopycatMirror(CopycatMirrorPlane.valueOf(value)));"copycat_turn"->n.copy(quarterTurns=value.toInt())
            "form"->if(n.kind==BuildingKind.COPYCAT) BuildingSourceEditors.copycatSource(n,value,library().forms) else n.copy(form=value);"finish"->n.copy(finish=value,thickness=if(value.isNotEmpty()) maxOf(n.thickness,2) else n.thickness)
            "role"->n.copy(role=value);"roof"->n.copy(roofType=value);"ridge"->n.copy(ridge=value.toInt());"occupied"->n.copy(occupied=value.toBoolean());"mode"->n.copy(materialMode=MaterialMode.valueOf(value))
            "number:length"->n.copy(length=value.toInt());"number:depth"->n.copy(depth=value.toInt());"number:height"->n.copy(height=value.toInt());"number:thickness"->n.copy(thickness=value.toInt());"number:overhang"->n.copy(overhang=value.toInt())
            "number:x","number:z" -> { val index=(view.room[n.id] ?: 0).coerceIn(0,n.inputs.lastIndex);val old=n.offsets.getOrNull(index) ?: P();n.copy(offsets=List(n.inputs.size) { i -> if(i!=index) n.offsets.getOrNull(i) ?: P() else if(m.type.endsWith("x")) old.copy(x=value.toInt()) else old.copy(z=value.toInt()) }) }
            else->n.copy(preview=value)
        })
    }
    override fun mouseClicked(mx:Double,my:Double,button:Int):Boolean {
        if(groupInspector.available) return groupInspector.mouseClicked(mx,my,button)
        if(areaStroke!=null) return true
        menu?.let { m -> val (a,b,w,rows)=menuBox(m);menu=null
            if(button==0 && mx>=a && mx<a+w && my>=b && my<b+rows*19) safe { select(m,options(m)[m.first+((my-b)/19).toInt()].first) }
            view.wire=null
            return true
        }
        if(!isMouseOver(mx,my)) return false
        val lx=nx(mx);val ly=ny(my)
        if(inspector) {
            if(my<y+previewHeader) { if(button==0) openMenu(Menu(selected.id,"nodes",0,width=width-20,at=(x+10.0 to y+29.0)));return true }
            val section=sections().firstOrNull { mx>=x+8 && mx<x+width-14 && my>=y+previewHeader+it.top-inspectorScroll && my<y+previewHeader+it.top+22-inspectorScroll }
            if(section!=null) {
                if(button==0) { view.inspectorSections.toggle(section.key);menu=null;inspectorScroll=inspectorScroll.coerceIn(0.0,maxInspectorScroll()) }
                return true
            }
            for(mode in listOf("2d","3d")) if(previewViewport(mode)?.contains(mx,my)==true) {
                if(button==1 && mode=="3d") { orbit=true;orbitNode=selected.id }
                return true
            }
            if(!sectionExpanded("properties")) return true
            val properties=sections().first()
            if(my<y+previewHeader+properties.contentTop-inspectorScroll || my>=y+previewHeader+properties.top+properties.height-inspectorScroll) return true
        }
        if(button==2) { if(!inspector) panning=true;return true }
        for(raw in if(inspector) listOf(selected) else graph.nodes.asReversed()) {
            val n=shown(raw);val ports=ports(n)
            for((i,p) in ports.withIndex()) { val s=point(n,i)
                if(!inspector && abs(lx-s.first)<=8 && abs(ly-s.second)<=8) {
                    if(button==1) { view.wire=null;connect("",n.id,p.key) }
                    else if(button==0) view.wire?.let { source -> if(connect(source,n.id,p.key)) view.wire=null } ?: run { view.wire=p.source.ifEmpty { null } }
                    view.selected=n.id;return true
                }
                if(button==0 && lx>=n.x+9 && lx<n.x+nodeWidth(n)-9 && ly>=s.second-8 && ly<s.second+9) { view.selected=n.id;openMenu(Menu(n.id,"input",(s.second-n.y-8).toInt(),socket=p.key));return true }
            }
            if(!inspector && n.kind!=BuildingKind.BUILDING) { val s=output(n);if(abs(lx-s.first)<=8 && abs(ly-s.second)<=8 && button==0) { view.wire=n.id;view.selected=n.id;return true } }
            val px=lx-n.x;val py=ly-n.y;val by=body(n);val w=nodeWidth(n)
            if(px !in 0.0..w.toDouble() || py !in 0.0..nodeHeight(n).toDouble()) continue
            view.selected=n.id
            if(button==0 && py<=23) { if(inspector) renameSelected() else if(!readOnly) { headerDrag=n.id;moving=n };return true }
            if(n.kind==BuildingKind.BUILDING && button==1) { orbit=true;return true }
            if(readOnly) return true
            if(button!=0 && button!=1) return true
            safe {
                fun inside(row:Int)=py>=by+row && py<by+row+17
                fun open(type:String,row:Int,column:Int=8,menuWidth:Int=0) { openMenu(Menu(n.id,type,by+row,column=column,width=menuWidth)) }
                fun material(row:Int):Boolean {
                    if(inside(row)) { open("mode",row);return true }
                    if(inside(row+21)) { open("role",row+21);return true };return false
                }
                if(n.kind==BuildingKind.STOREY && inside(-22)) { open("storey_mode",-22);return@safe }
                if(n.kind==BuildingKind.STOREY) {
                    val anchor=if(layoutKind(n)==BuildingKind.ROOM) 310 else 258
                    if(inside(anchor+14)) { open(if(px<148) "number:table_x" else "number:table_z",anchor+14);return@safe }
                    if(inside(anchor+36)) { open(if(px<148) "number:table_y" else "table_turn",anchor+36);return@safe }
                    val p=projections[n.id]
                    val top=by+if(layoutKind(n)==BuildingKind.ROOM) 24 else 45
                    if(button==0 && py>=top && py<by+188 && p!=null) {
                        val table=tableReference.table
                        if(lx>=p.px(table.x) && lx<p.px(table.x+1) && ly>=p.py(table.z) && ly<p.py(table.z+1)) {
                            tableDrag=TableDrag(n.id,graph.projection,lx,ly,p);movingTable=graph.projection;return@safe
                        }
                    }
                }
                when(layoutKind(n)) {
                    BuildingKind.ROTATION,BuildingKind.MIRRORING -> Unit
                    BuildingKind.COPYCAT -> {
                        val groups=copycatGroups(n);val end=copycatMaterialEnd(n)
                        val group=groups.firstOrNull { inside(44+groups.indexOf(it)*22) }
                        when {
                            inside(0)->open("form",0)
                            inside(22)->open("role",22)
                            group!=null && onSelectMaterial!=null->onSelectMaterial.invoke(n.id,group.id)
                            inside(end)->open("copycat_axis",end)
                            inside(end+22)->open("copycat_turn",end+22)
                            inside(end+66) && !n.surfaces.isNullOrEmpty()->edit(n.copy(surfaces=null))
                        }
                    }
                    BuildingKind.SEGMENT_LAYOUT -> when {
                        inside(0)->if(n.geometry.isNotEmpty()) showSource(n.geometry) else if(px<105) open("number:length",0,8,190) else open("number:height",0,108,190)
                        inside(22)->open("number:depth",22)
                        inside(66)->openSourceLayout(n.id)
                    }
                    BuildingKind.ROOM_LAYOUT -> when {
                        inside(0)->RoomSegmentLayoutScreen.open(n,graph,evaluation().getOrThrow()) { edit(it) }
                        inside(22)-> { val inputs=if(px<105 && n.inputs.size<16) n.inputs+"" else if(px>=105 && n.inputs.size>1) n.inputs.dropLast(1) else n.inputs
                            val c=n.roomLayout ?: RoomLayoutConfig();edit(n.copy(inputs=inputs,roomLayout=c.copy(instances=c.instances.filter { it.sourceIndex<inputs.size }))) }
                    }
                    BuildingKind.AREA,BuildingKind.ROOM_GEOMETRY -> when {
                        inside(244) && n.kind==BuildingKind.ROOM_GEOMETRY->open("number:height",244)
                        inside(0)->openSourceCoordinates(n.id)
                        py>=by+24 && py<by+188 -> {
                            val p=projections[n.id] ?: return@safe
                            if(!p.contains(lx,ly)) return@safe
                            val cell=p.cell(lx,ly)
                            require(cell.x in -128..127 && cell.z in -128..127) { "Area points need x,z coordinates between -128 and 127" }
                            areaStroke=AreaStroke(graph,n.id,graph.resolvedNode(n).coordinates.toSet(),cell,cell,button,p)
                        }
                    }
                    BuildingKind.BAY_WINDOW,BuildingKind.DORMER -> if(inside(0)) SpecialRoomEditors.definition(n,graph) { edit(it) }
                    BuildingKind.ROOF_LAYOUT -> Unit
                    BuildingKind.ROOF_LEVEL -> when {
                        inside(0)->BuildingStructureEditors.roofLevel(n,{graph}) { edit(it) }
                        inside(132)->SpecialRoomEditors.instances(n,graph) { edit(it) }
                        inside(154)->openSpecialPlacement(n)
                    }
                    BuildingKind.STAIRWELL,BuildingKind.ELEVATOR_SHAFT,BuildingKind.SERVICE_SHAFT -> if(inside(0)) BuildingStructureEditors.core(n,{graph}) { edit(it) }
                    BuildingKind.GARDEN,BuildingKind.PATH -> when {
                        inside(0)->openSourceCoordinates(n.id)
                        inside(210)->open("surface_block",210)
                        inside(232)->open("number:elevation",232)
                        inside(254)->open("number:ground_depth",254)
                        inside(276)->open("ground_block",276)
                        inside(298)->open("number:clearance",298)
                        inside(320)->open(if(n.kind==BuildingKind.GARDEN) "fenced" else "number:path_width",320)
                        inside(342) && n.kind==BuildingKind.GARDEN->open("fence_block",342)
                        inside(364) && n.kind==BuildingKind.GARDEN->open("number:fence_height",364)
                        material(if(n.kind==BuildingKind.GARDEN) 386 else 342)->Unit
                        py>=by+24 && py<by+188 -> {
                            val p=projections[n.id] ?: return@safe
                            if(!p.contains(lx,ly)) return@safe
                            val cell=p.cell(lx,ly)
                            if(n.kind==BuildingKind.GARDEN) {
                                val cells=runCatching { LandscapeGeometry.gardenCells(graph,n) }.getOrDefault(emptySet()).toMutableSet()
                                if(!cells.add(cell)) cells.remove(cell)
                                commit(graph.editLandscapeCoordinates(n.id,cells.toList()))
                            } else {
                                val index=n.coordinates.indexOfFirst { it.x==cell.x && it.z==cell.z }
                                val points=if(button==1) n.coordinates.filterIndexed { i,_ -> i!=index }
                                    else if(index<0) n.coordinates+cell.copy(y=n.coordinates.lastOrNull()?.y ?: 0) else n.coordinates
                                commit(graph.editLandscapeCoordinates(n.id,points))
                            }
                        }
                    }
                    BuildingKind.SEGMENT -> when {
                        inside(160)->SemanticPropertyEditors.features(n,graph) { edit(it) }
                        inside(186)->SemanticPropertyEditors.attachments(n,graph) { edit(it) }
                        inside(0) && n.assembly->if(n.layout.isEmpty()) openMenu(Menu(n.id,"input",by,socket="layout")) else showSource(n.layout)
                        inside(22) && n.assembly->showSource(n.layout)
                        inside(0) && n.id!="empty_wall"->open("source",0)
                        inside(22) && (n.inputs.any { it.isNotEmpty() } || n.module.isEmpty() || n.block.isNotEmpty())->if(px<105) open("number:length",22,8,190) else open("number:height",22,108,190)
                        inside(44)->open("role",44)
                        inside(66)->if(n.assembly) showSource(n.layout) else open("number:depth",66)
                    }
                    BuildingKind.SEGMENT_UNION, BuildingKind.SEGMENT_INTERSECTION, BuildingKind.SEGMENT_DIFFERENCE, BuildingKind.SEGMENT_SYMMETRIC_DIFFERENCE, BuildingKind.COPYCAT_UNION, BuildingKind.COPYCAT_INTERSECTION, BuildingKind.COPYCAT_DIFFERENCE, BuildingKind.COPYCAT_SYMMETRIC_DIFFERENCE -> when {
                        inside(0) && n.kind !in setOf(BuildingKind.SEGMENT_DIFFERENCE,BuildingKind.COPYCAT_DIFFERENCE) -> { val inputs=if(px<105 && n.inputs.size<16) n.inputs+"" else if(px>=105 && n.inputs.size>2 && n.inputs.last().isEmpty()) n.inputs.dropLast(1) else n.inputs;edit(n.copy(inputs=inputs)) }
                        else -> material(28)
                    }
                    BuildingKind.PATTERN -> when {
                        inside(158)->SemanticPropertyEditors.features(n,graph) { edit(it) }
                        inside(17)-> { val inputs=if(px<105 && n.inputs.size<16) n.inputs+"" else if(px>=105 && n.inputs.size>2) n.inputs.dropLast(1) else n.inputs;edit(n.copy(inputs=inputs,transforms=n.transforms.take(inputs.size))) }
                        inside(40)->open("pattern_axis",40)
                        inside(62)->SegmentArrangementScreen.open(n,graph,evaluation().getOrThrow()) { edit(it) }
                        else->material(84)
                    }
                    BuildingKind.SEGMENT_MIRROR -> when {
                        inside(182)->SemanticPropertyEditors.features(n,graph) { edit(it) }
                        inside(20)->open("mirror_axis",20)
                        inside(40)->open("mirror_scope",40)
                        else->material(88)
                    }
                    BuildingKind.COPYCAT_ROTATE,BuildingKind.SEGMENT_ROTATE -> when {
                        inside(162) && n.kind==BuildingKind.SEGMENT_ROTATE->SemanticPropertyEditors.features(n,graph) { edit(it) }
                        inside(20)->open("copycat_axis",20)
                        inside(42)->open("copycat_turn",42)
                        else->material(68)
                    }
                    BuildingKind.COPYCAT_MIRROR -> when {
                        inside(0)->open("copycat_mirror_plane",0)
                        else->material(68)
                    }
                    BuildingKind.FLOOR,BuildingKind.CEILING -> when {
                        inside(0)->open("form",0);inside(22)->open("number:thickness",22)
                        inside(44)->open("occupied",44);inside(66) && n.kind==BuildingKind.FLOOR->open("finish",66);inside(88)->open("role",88)
                        inside(116) && n.kind==BuildingKind.CEILING->SemanticPropertyEditors.attachments(n,graph) { edit(it) }
                    }
                    BuildingKind.ROOM -> {
                        if(n.kind==BuildingKind.ROOM && inside(340)) { SpecialRoomEditors.instances(n,graph) { edit(it) };return@safe }
                        if(n.kind==BuildingKind.ROOM && inside(362)) { openSpecialPlacement(n);return@safe }
                        if(n.kind==BuildingKind.ROOM && n.roomConfig?.mode==RoomMode.SEGMENTS) {
                            if(inside(0)) SemanticPropertyEditors.room(n,graph) { edit(it) }
                            else if(inside(22)) {
                                val layout=graph.nodes.firstOrNull { it.id==n.layout && it.kind==BuildingKind.ROOM_LAYOUT }
                                if(layout!=null) RoomSegmentLayoutScreen.open(layout,graph,evaluation().getOrThrow()) { edit(it) }
                                else openMenu(Menu(n.id,"input",by+22,socket="layout"))
                            }
                            return@safe
                        }
                        when {
                            inside(318) && n.kind==BuildingKind.ROOM->SemanticPropertyEditors.room(n,graph) { edit(it) }
                            inside(394) && n.kind==BuildingKind.STOREY->SemanticPropertyEditors.facade(n,graph) { edit(it) }
                            inside(420) && n.kind==BuildingKind.STOREY->commit(SemanticConversion.convert(graph,n.id))
                            inside(0) && px<125->if(n.assembly) showSource(n.geometry) else open("number:height",0,8,200)
                            inside(190) && n.assembly->showSource(n.geometry)
                            py>=by+24 && py<by+188 && button==0 -> {
                                val p=projections[n.id] ?: return@safe
                                val cell=p.cell(lx,ly)
                                if(p.contains(lx,ly)) {
                                    if(n.kind==BuildingKind.ROOM) commit(DiagramEdits.toggleRoomCell(graph,n.id,cell)) else edit(RoomGeometry.toggleCell(n,cell))
                                }
                            }
                            else->material(264)
                        }
                    }
                    BuildingKind.STOREY -> {
                        val index=(view.room[n.id] ?: 0).coerceIn(0,n.inputs.lastIndex.coerceAtLeast(0))
                        when {
                            inside(342)->SemanticPropertyEditors.facade(n,graph) { edit(it) }
                            inside(368)->commit(SemanticConversion.convert(graph,n.id))
                            inside(0)->when { px<110->open("room",0,8,190);px<150 && n.inputs.size<16->edit(n.copy(inputs=n.inputs+"",offsets=n.offsets+P()));px>=150 && n.inputs.size>1->{view.room[n.id]=0;edit(n.copy(inputs=n.inputs.filterIndexed { i,_ -> i!=index },offsets=List(n.inputs.size) { n.offsets.getOrNull(it) ?: P() }.filterIndexed { i,_ -> i!=index }))} }
                            inside(22)->if(px<110) open("number:x",22,8,190) else open("number:z",22,114,190)
                            py>=by+45 && py<by+188 -> {
                                val projection=projections[n.id] ?: return@safe
                                val cell=P(floor((lx-projection.x)/projection.step).toInt()+projection.minX,0,floor((ly-projection.y)/projection.step).toInt()+projection.minZ)
                                val hit=n.inputs.indices.reversed().firstOrNull { i -> val room=graph.nodes.firstOrNull { it.id==n.inputs[i] && it.kind==BuildingKind.ROOM };room!=null && cell+P(-(n.offsets.getOrNull(i)?.x ?: 0),0,-(n.offsets.getOrNull(i)?.z ?: 0)) in RoomGeometry.cells(rectangles(room)) }
                                if(hit!=null && button==0) { view.room[n.id]=hit;mapDrag=MapDrag(n,hit,lx,ly,projection);moving=n }
                            }
                            else->material(211)
                        }
                    }
                    BuildingKind.ROOF -> when { n.assembly && inside(0)->showSource(n.layout);n.assembly && inside(22)->BuildingStructureEditors.roofTemplates(n,{graph}) { edit(it) };n.assembly->Unit;inside(0)->open("roof",0);inside(22)->open("ridge",22);inside(44)->if(px<105) open("number:overhang",44,8,190) else open("number:thickness",44,108,190);inside(66)->open("role",66) }
                    BuildingKind.BUILDING -> when { inside(0)->BuildingSiteScreen.open(n.id,{graph},commit);inside(22)->open("preview",22);inside(306)->BuildingStructureEditors.instances(n,{graph}) { edit(it) };else->material(257) }
                }
            };return true
        }
        if(button==1 && !inspector && !readOnly) { rightStart=mx to my;rightGraph=lx to ly;panning=false } else view.wire=null
        return true
    }
    override fun mouseDragged(mx:Double,my:Double,button:Int,dx:Double,dy:Double):Boolean {
        areaStroke?.let { stroke -> if(button==stroke.button) updateAreaStroke(stroke,mx,my);return true }
        if(menu!=null) return true
        if(headerDrag!=null) { moving=moving!!.copy(x=(moving!!.x+dx/scale).roundToInt().coerceIn(-8192,8192),y=(moving!!.y+dy/scale).roundToInt().coerceIn(-8192,8192));return true }
        tableDrag?.let { drag ->
            val du=((nx(mx)-drag.startX)/drag.projection.step).roundToInt();val dv=((ny(my)-drag.startY)/drag.projection.step).roundToInt();val old=drag.original.table
            movingTable=drag.original.copy(table=old.copy(x=(old.x+du).coerceIn(-128,128),z=(old.z+dv).coerceIn(-128,128)));return true
        }
        mapDrag?.let { drag ->
            val du=((nx(mx)-drag.startX)/drag.projection.step).roundToInt();val dv=((ny(my)-drag.startY)/drag.projection.step).roundToInt();val n=drag.original
            val old=n.offsets.getOrNull(drag.index) ?: P();val next=n.copy(offsets=List(n.inputs.size) { i -> if(i==drag.index) P((old.x+du).coerceIn(-64,64),0,(old.z+dv).coerceIn(-64,64)) else n.offsets.getOrNull(i) ?: P() })
            moving=next;return true
        }
        rightStart?.let { start -> if(!panning && hypot(mx-start.first,my-start.second)>=4.0) { panning=true;view.panX+=mx-start.first;view.panY+=my-start.second;return true } }
        if(panning) { view.panX+=dx;view.panY+=dy;return true };if(orbit) { orbitNode?.let { view.previewYaws[it]=(view.previewYaws[it] ?: 35f)+dx.toFloat() } ?: run { view.yaw+=dx.toFloat() };return true };return view.wire!=null || rightStart!=null
    }
    override fun mouseReleased(mx:Double,my:Double,button:Int):Boolean {
        areaStroke?.let { stroke ->
            if(button!=stroke.button) return true
            updateAreaStroke(stroke,mx,my);areaStroke=null
            if(graph==stroke.original) safe {
                val cells=strokeCells(stroke)
                if(cells!=stroke.cells) commit(graph.editRoomGeometry(stroke.node,cells.toList()))
            }
            return true
        }
        movingTable?.let { changed -> tableDrag=null;movingTable=null;safe { commit(graph.copy(projection=changed.validate())) };return true }
        val changed=moving;val drag=mapDrag;val context=button==1 && rightStart?.let { hypot(mx-it.first,my-it.second)<4.0 }==true && !panning && isMouseOver(mx,my);val insertion=rightGraph;headerDrag=null;mapDrag=null;moving=null;panning=false;orbit=false;orbitNode=null;rightStart=null;rightGraph=null
        if(context && insertion!=null) { view.wire=null;openMenu(Menu("","insert",0,at=mx to my,insertion=insertion));return true }
        if(changed!=null) { safe { if(drag!=null) commit(DiagramEdits.moveStoreyRoom(graph,changed.id,drag.index,changed.offsets[drag.index])) else edit(changed) };return true }
        if(menu!=null) return true
        if(!inspector && button==0 && view.wire!=null) {
            val lx=nx(mx);val ly=ny(my)
            for(n in graph.nodes.asReversed()) for((i,p) in ports(n).withIndex()) { val s=point(n,i);if(abs(lx-s.first)<=8 && abs(ly-s.second)<=8) { val source=view.wire!!;if(connect(source,n.id,p.key)) view.wire=null;return true } }
        };return true
    }
    override fun mouseScrolled(mx:Double,my:Double,dx:Double,dy:Double):Boolean {
        if(groupInspector.available) return groupInspector.mouseScrolled(mx,my,dx,dy)
        if(areaStroke!=null) return true
        menu?.let { m ->
            val (a,b,w,rows)=menuBox(m)
            if(mx>=a && mx<a+w && my>=b && my<b+rows*19) m.first=(m.first-dy.sign.toInt()).coerceIn(0,(options(m).size-rows).coerceAtLeast(0)) else menu=null
            return true
        }
        if(!isMouseOver(mx,my)) return false
        if(inspector) { inspectorScroll=(inspectorScroll-dy*25).coerceIn(0.0,maxInspectorScroll());return true }
        val a=nx(mx);val b=ny(my);view.zoom=(view.zoom*1.12.pow(dy)).coerceIn(0.2,1.75);view.panX=mx-x-a*view.zoom;view.panY=my-y-b*view.zoom;return true
    }
    override fun keyPressed(key:Int,scan:Int,mods:Int):Boolean {
        if(groupInspector.available) return false
        if(areaStroke!=null) { if(key==256) areaStroke=null;return true }
        if(key==256 && (menu!=null || view.wire!=null || rightStart!=null)) { menu=null;view.wire=null;rightStart=null;rightGraph=null;return true }
        menu?.let { m ->
            val entries=options(m);val rows=menuBox(m)[3]
            if(entries.isEmpty()) return true
            if(key==257 || key==335) { menu=null;safe { select(m,entries[m.focus].first) };view.wire=null;return true }
            val next=when(key) { 264->m.focus+1;265->m.focus-1;268->0;269->entries.lastIndex;266->m.focus-rows;267->m.focus+rows;else->return true }
            m.focus=next.coerceIn(0,entries.lastIndex);m.first=m.first.coerceIn((m.focus-rows+1).coerceAtLeast(0),m.focus).coerceAtMost((entries.size-rows).coerceAtLeast(0));return true
        }
        if(key==291) { renameSelected();return true }
        if(key==261 || key==259) { deleteSelected();return true };return false
    }
    override fun updateWidgetNarration(output:NarrationElementOutput) { output.add(NarratedElementType.TITLE,tr("building_graph"));output.add(NarratedElementType.USAGE,tr("bg_help")) }
}
