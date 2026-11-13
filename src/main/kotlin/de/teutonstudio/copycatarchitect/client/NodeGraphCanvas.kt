package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.network.chat.Component
import java.util.UUID
import kotlin.math.*

class GraphView {
    var panX=12.0;var panY=12.0;var zoom=0.75;var fitted=false
    var selected="result";var wire:String?=null;var yaw=35f
    var inspectorScroll=0
    val inspectorSections=InspectorSections()
}
/** Both presentations edit the same nodes. Only completed edits enter the screen's undo history. */
class NodeGraphCanvas(x:Int,y:Int,w:Int,h:Int,private val definition:()->Form,private val surfaces:()->Map<String,String>,
                      val view:GraphView,private val commit:(NodeGraph)->Unit,private val failure:(String)->Unit,
                      private val inspector:Boolean=false,
                      private val onCreateGroup:(name:String,nodeId:String,material:Boolean,slot:Int)->Unit={_,_,_,_->},
                      private val preview:(()->Result<Form>)?=null,private val readOnly:Boolean=false,
                      private val onSelectNode:(String)->Unit={},private val previewMaterials:()->Boolean={true},
                      private val onSavedSource:(String,Int,Int)->Unit={_,_,_->},private val allowedNodes:(()->Set<String>)?=null):AbstractWidget(x,y,w,h,tr("node_graph")) {
    private val font get()=Minecraft.getInstance().font
    private val graph get()=definition().graph!!
    private val nodePreviews=ShapeNodePreviews(definition) { graph }
    private var dragging:String?=null;private var moveX=0;private var moveY=0
    private var panning=false;private var orbit=false
    private var contextStart:Pair<Double,Double>?=null
    private var mouseX=0.0;private var mouseY=0.0
    private var screenMouseX=0.0;private var screenMouseY=0.0
    private var inspectorScroll:Int
        get()=view.inspectorScroll
        set(value) { view.inspectorScroll=value }
    private var inspectorContentHeight=0
    private enum class Field { SELECT,CONNECT,INSERT,SOURCE,DEFINITION,TURN,TOP,SIZE,GROUP,TILT,X,Y,Z,GROUPED,SEPARATED,MATERIAL,PREVIEW,ADD,REMOVE,SWAP,ROTATION_AXIS,ROTATE,INPUT_GROUP,ROOF_CONFIG,MIRROR_PLANE }
    private data class Control(val field:Field,val x:Int,val y:Int,val w:Int,val label:String,val slot:Int=-1,val active:Boolean=false,val enabled:Boolean=true,val icon:String="",val floating:Boolean=false) {
        fun contains(px:Double,py:Double)=px>=x && px<x+w && py>=y && py<y+18
    }
    private data class Choice(val id:String,val label:String)
    private data class Menu(val field:Field,val node:String,val anchorX:Int,val anchorY:Int,val slot:Int=-1,
                            val insertX:Int=0,val insertY:Int=0,var first:Int=0,var cursor:Int=0,var keyboard:Boolean=false)
    private var menu:Menu?=null
    private val groupInspector=GraphGroupInspector(x,y,w,h,
        { if(inspector) graph.nodeGroups.firstOrNull { "@group:${it.id}"==view.selected } else null },
        { graph.nodeGroups.firstOrNull { "@group:${it.id}"==view.selected }?.nodes.orEmpty().mapNotNull { id ->
            graph.nodes.firstOrNull { it.id==id }?.let { id to nodeLabel(it) }
        } },{ id -> view.selected=id;inspectorScroll=0;onSelectNode(id) })
    fun hasOpenMenu()=menu!=null
    fun groupMemberPoint(id:String)=groupInspector.memberPoint(id)
    private var cachedSourcePart:Part?=null;private var cachedSourceDefinition:Form?=null
    private var outputPreviewForm:Form?=null
    private var outputPreviewGroup=""
    private var outputPreview:ShapeNodePreview?=null
    private var costPreview:ShapeNodePreview?=null
    private var costGroups:List<MaterialGroup>?=null
    private var previewCost=0
    private fun previewCost(value:ShapeNodePreview):Int {
        val groups=definition().groups
        if(costPreview!==value || costGroups!=groups) {
            previewCost=if(value.parts.isEmpty()) 0 else Extrusion.cost(value.parts,groups)
            costPreview=value;costGroups=groups
        }
        return previewCost
    }
    private fun sourceDefinition(n:GraphNode):Form? {
        if(n.kind!=NodeKind.INPUT) return null
        if(cachedSourcePart!=n.part) { cachedSourcePart=n.part;cachedSourceDefinition=runCatching { SourceDefinitions.definition(n.part) }.getOrNull() }
        return cachedSourceDefinition
    }
    private fun previewValue(n:GraphNode):ShapeNodePreview {
        if(n.kind==NodeKind.OUTPUT) preview?.let { legacy ->
            return legacy().fold({ f ->
                if(outputPreviewForm!==f || outputPreviewGroup!=n.previewGroup || outputPreview==null) {
                    outputPreview=ShapeNodePreview(parts=Geometry.previewParts(f.parts,f.groups,n.previewGroup))
                    outputPreviewForm=f;outputPreviewGroup=n.previewGroup
                }
                outputPreview!!
            },{ ShapeNodePreview(error=it.message.orEmpty()) })
        }
        return nodePreviews.get(n.id)
    }
    fun nodeWidth(n:GraphNode)=if(n.kind==NodeKind.OUTPUT) 206 else 184
    fun nodeHeight(n:GraphNode)=(if(n.kind in setOf(NodeKind.INPUT,NodeKind.SAVED,NodeKind.ROTATION,NodeKind.OUTPUT)) 29 else materialY(n))+propertyHeight(n)+12
    fun fit() {
        if(inspector) { inspectorScroll=0;return }
        val minX=graph.nodes.minOf { it.x };val minY=graph.nodes.minOf { it.y }
        val spanX=graph.nodes.maxOf { it.x+nodeWidth(it) }-minX+40
        val spanY=graph.nodes.maxOf { it.y+nodeHeight(it) }-minY+40
        view.zoom=minOf(width.toDouble()/spanX,height.toDouble()/spanY,1.0).coerceIn(0.25,1.75)
        view.panX=(width-spanX*view.zoom)/2+(20-minX)*view.zoom
        view.panY=(height-spanY*view.zoom)/2+(20-minY)*view.zoom;view.fitted=true
    }
    fun add(kind:NodeKind,source:String=Adapters.all.keys.first(),discCut:Int=0) {
        addAt(kind,source,((width/2-view.panX)/view.zoom).roundToInt(),((height/2-view.panY)/view.zoom).roundToInt(),discCut)
    }
    fun addAt(kind:NodeKind,source:String,px:Int,py:Int,discCut:Int=0) {
        if(readOnly) return
        if(kind==NodeKind.SAVED) { onSavedSource("",px,py);return }
        safe {
            require(GraphModels.shape(kind).output!=null && graph.nodes.size<GraphModels.copycat.maxNodes) { ui("node_limit") }
            require(kind!=NodeKind.INPUT || graph.nodes.count { it.kind==NodeKind.INPUT || it.kind==NodeKind.SAVED }<GraphModels.copycat.maxSources) { ui("source_limit") }
            val node=GraphModels.newShape(kind).copy(id=UUID.randomUUID().toString(),
                part=BaseShapes.find(source,discCut).part(definition().groups.first().id),
                x=px.coerceIn(-8000,8000),y=py.coerceIn(-8000,8000))
            view.selected=node.id;view.wire=null;inspectorScroll=0;commit(graph.copy(nodes=graph.nodes+node))
        }
    }
    fun deleteSelected() { if(readOnly) return;graph.nodes.firstOrNull { it.id==view.selected && it.kind!=NodeKind.OUTPUT }?.let { menu=null;view.wire=null;commit(graph.remove(it.id));view.selected=graph.nodes.firstOrNull { n -> n.kind==NodeKind.OUTPUT }?.id.orEmpty();inspectorScroll=0 } }
    fun connect(source:String,target:String,slot:Int):Boolean = if(readOnly) false else try {
        val n=graph.nodes.first { it.id==target }
        require(slot in n.inputs.indices)
        val changed=graph.replace(n.copy(inputs=n.inputs.mapIndexed { i,v -> if(i==slot) source else v }))
        GraphCompiler.structure(changed,definition().groups);commit(changed);true
    } catch(e:Exception) { failure(e.message.orEmpty());false }
    private fun safe(action:()->Unit) { try { action() } catch(e:Exception) { failure(e.message.orEmpty()) } }
    private fun edit(node:GraphNode) { if(readOnly) return;val changed=graph.replace(node);GraphCompiler.structure(changed,definition().groups);commit(changed) }
    private fun localX(mx:Double)=(mx-x-view.panX)/view.zoom
    private fun localY(my:Double)=(my-y-view.panY)/view.zoom
    private fun position(n:GraphNode)=if(n.id==dragging) Pair(moveX,moveY) else Pair(n.x,n.y)
    private fun title(n:GraphNode)=ui(GraphModels.shape(n.kind).labelKey)
    private fun nodeLabel(n:GraphNode)="${graph.nodes.indexOfFirst { it.id==n.id }+1} · ${title(n)}"
    private fun selectedNode()=graph.nodes.firstOrNull { it.id==view.selected } ?: graph.nodes.first { it.kind==NodeKind.OUTPUT }.also { view.selected=it.id;inspectorScroll=0 }
    private fun inputSocket(n:GraphNode,i:Int)=Pair(position(n).first.toDouble(),position(n).second+36.0+i*19)
    private fun outputSocket(n:GraphNode)=Pair(position(n).first+nodeWidth(n).toDouble(),position(n).second+36.0)
    private fun materialY(n:GraphNode)=40+n.inputs.size*19
    fun previewSelection():String=selectedNode().previewGroup
    fun previewParts():List<Part> = previewValue(selectedNode()).parts
    private fun groupName(id:String)=definition().groups.firstOrNull { it.id==id }?.name ?: definition().groups.first().name
    private fun supportsTilt(n:GraphNode)=Adapters.get(n.part.source).tiltable

    private fun axisLabel(axis:RotationAxis):String {
        if(axis!=RotationAxis.NORMAL) return axis.name
        return ui("rotation_normal_generic")
    }
    private fun rotationControls(n:GraphNode,px:Int,py:Int,w:Int):List<Control> {
        val third=(w-6)/3
        val enabled=n.kind!=NodeKind.ROTATE || n.inputs.getOrNull(1).isNullOrEmpty()
        return listOf(Control(Field.ROTATION_AXIS,px,py,w,ui("rotation_axis",axisLabel(n.rotationAxis)),enabled=enabled),
            Control(Field.TURN,px,py+42,w,ui("rotation_angle",n.quarterTurns*90),enabled=enabled))+
            listOf(-1,2,1).mapIndexed { index,turns ->
                val label=when(turns) { -1->"−90°";2->"180°";else->"+90°" }
                Control(Field.ROTATE,px+index*(third+3),py+21,if(index==2) w-2*(third+3) else third,label,slot=turns,enabled=enabled)
            }
    }
    fun rotateSource(nodeId:String,axis:RotationAxis,quarterTurns:Int) {
        if(readOnly) return
        val node=graph.nodes.firstOrNull { it.id==nodeId && it.kind in setOf(NodeKind.INPUT,NodeKind.SAVED,NodeKind.ROTATE,NodeKind.ROTATION) } ?: return
        if(node.kind==NodeKind.ROTATE && !node.inputs.getOrNull(1).isNullOrEmpty()) return
        safe { edit(node.copy(rotationAxis=axis,quarterTurns=Math.floorMod(node.quarterTurns+quarterTurns,4))) }
    }
    /** Actual source rotation hit regions, including inspector scroll and graph zoom. */
    fun rotationButtonPoint(axis:RotationAxis,quarterTurns:Int):Pair<Double,Double>? {
        val node=selectedNode()
        if(axis!=node.rotationAxis) return null
        val control=(if(inspector) inspectorControls(node) else graphControls(node)).firstOrNull { it.field==Field.ROTATE && it.slot==quarterTurns } ?: return null
        val screen=screenControl(control)
        return screen.x+screen.w/2.0 to screen.y+(if(inspector) 9.0 else 9.0*view.zoom)
    }
    fun rotationAxisPoint():Pair<Double,Double>? {
        val node=selectedNode()
        val control=(if(inspector) inspectorControls(node) else graphControls(node)).firstOrNull { it.field==Field.ROTATION_AXIS } ?: return null
        val screen=screenControl(control)
        return screen.x+screen.w/2.0 to screen.y+(if(inspector) 9.0 else 9.0*view.zoom)
    }
    fun mirrorPlanePoint():Pair<Double,Double>? {
        val node=selectedNode()
        val control=(if(inspector) inspectorControls(node) else graphControls(node)).firstOrNull { it.field==Field.MIRROR_PLANE } ?: return null
        val screen=screenControl(control)
        return screen.x+screen.w/2.0 to screen.y+(if(inspector) 9.0 else 9.0*view.zoom)
    }

    /** Coordinates vary with the presentation; fields and edits deliberately do not. */
    private fun propertyControls(n:GraphNode,px:Int,py:Int,w:Int):List<Control> {
        val third=(w-6)/3;val half=(w-4)/2
        val hasDefinition=inspector && sourceDefinition(n)!=null
        return (when(n.kind) {
            NodeKind.SAVED -> listOf(Control(Field.SOURCE,px,py,w,n.savedCopycat?.let { "${it.name} · v${it.version}" } ?: ui("node_saved")))+
                rotationControls(n,px,py+21,w)
            NodeKind.INPUT -> listOf(
                Control(Field.SOURCE,px,py,w-if(hasDefinition) 26 else 0,ui(BaseShapes.find(n.part.source,n.part.discCut).name),icon=n.part.source),
                Control(Field.TOP,px,py+21,half,ui(if(n.part.top) "top" else "bottom")),
                Control(Field.SIZE,px+half+4,py+21,w-half-4,ui("size",n.part.size),enabled=Adapters.get(n.part.source).max>1),
                Control(Field.X,px,py+42,third,"X ${n.part.offset.x}"),
                Control(Field.Y,px+third+3,py+42,third,"Y ${n.part.offset.y}"),
                Control(Field.Z,px+2*(third+3),py+42,w-2*(third+3),"Z ${n.part.offset.z}"))+
                (if(hasDefinition) listOf(Control(Field.DEFINITION,px+w-22,py,22,"")) else emptyList())+
                (if(inspector) rotationControls(n,px,py+63,w) else emptyList())+
                listOf(Control(Field.GROUP,px,py+if(inspector) 126 else 63,w,ui("part_group",groupName(n.part.group))))+
                (if(n.part.source==RoofPartParameters.SOURCE) listOf(Control(Field.ROOF_CONFIG,px,py+if(inspector) 147 else 84,w,ui("roof_source_settings"))) else emptyList())
            NodeKind.ROTATE,NodeKind.ROTATION -> rotationControls(n,px,py,w)
            NodeKind.MIRROR -> listOf(Control(Field.MIRROR_PLANE,px,py,w,ui("mirror_plane",ui("mirror_plane_"+(n.mirror ?: CopycatMirror()).plane.name.lowercase()))))
            NodeKind.OUTPUT -> listOf(Control(Field.PREVIEW,px,py,w,definition().groups.firstOrNull { it.id==n.previewGroup }?.name ?: ui("all_groups")))
            NodeKind.UNION,NodeKind.SYMMETRIC_DIFFERENCE -> {
                val assignments=if(n.materialMode==MaterialMode.GROUPED) listOf(Control(Field.MATERIAL,px,py+21,w,groupName(n.materialGroup)))
                    else n.inputs.indices.map { slot -> Control(Field.INPUT_GROUP,px,py+21+slot*21,w,ui("input_group",slot+1,n.inputGroups.getOrNull(slot)?.takeIf { it.isNotEmpty() }?.let(::groupName) ?: ui("inherit_groups")),slot=slot) }
                val controlsY=py+21+assignments.size*21
                listOf(Control(Field.GROUPED,px,py,half,ui("grouped"),active=n.materialMode==MaterialMode.GROUPED),
                    Control(Field.SEPARATED,px+half+4,py,w-half-4,ui("separated"),active=n.materialMode==MaterialMode.SEPARATED))+assignments+
                    listOf(Control(Field.ADD,px,controlsY,half,"+ ${ui("input")}",enabled=n.inputs.size<checkNotNull(GraphModels.shape(n.kind).inputs).max),
                        Control(Field.REMOVE,px+half+4,controlsY,w-half-4,"− ${ui("input")}",enabled=n.inputs.size>checkNotNull(GraphModels.shape(n.kind).inputs).min))
            }
            NodeKind.DIFFERENCE -> listOf(Control(Field.SWAP,px,py,w,ui("swap_inputs")))
            NodeKind.INTERSECTION -> listOf(
                Control(Field.ADD,px,py,half,"+ ${ui("input")}",enabled=n.inputs.size<checkNotNull(GraphModels.shape(n.kind).inputs).max),
                Control(Field.REMOVE,px+half+4,py,w-half-4,"− ${ui("input")}",enabled=n.inputs.size>checkNotNull(GraphModels.shape(n.kind).inputs).min))
        }).map { it.copy(enabled=it.enabled && (!readOnly || it.field==Field.DEFINITION)) }
    }
    /** Actual inspector eye hit region, including scroll; absent for primitive sources. */
    fun sourceDefinitionPoint():Pair<Double,Double>? {
        val n=selectedNode()
        val control=(if(inspector) inspectorControls(n) else graphControls(n)).firstOrNull { it.field==Field.DEFINITION } ?: return null
        val screen=screenControl(control)
        return screen.x+screen.w/2.0 to screen.y+(if(inspector) 9.0 else 9.0*view.zoom)
    }
    fun materialModePoint(grouped:Boolean):Pair<Double,Double>? {
        val c=inspectorControls(selectedNode()).firstOrNull { it.field==(if(grouped) Field.GROUPED else Field.SEPARATED) } ?: return null
        return c.x+c.w/2.0 to c.y+9.0
    }
    fun hasUniformGroupWarning()=uniformGroupWarning(selectedNode())
    fun materialGroupPoint(slot:Int=-1):Pair<Double,Double>? {
        val n=selectedNode()
        val c=inspectorControls(n).firstOrNull { if(slot<0) it.field==Field.MATERIAL else it.field==Field.INPUT_GROUP && it.slot==slot } ?: return null
        return c.x+c.w/2.0 to c.y+9.0
    }
    private fun uniformGroupWarning(n:GraphNode)=n.kind in setOf(NodeKind.UNION,NodeKind.SYMMETRIC_DIFFERENCE) && n.materialMode==MaterialMode.SEPARATED &&
        runCatching { GraphCompiler.uniformSeparatedGroups(graph,n,definition().groups) }.getOrDefault(false)
    private fun graphControls(n:GraphNode):List<Control> {
        val (nx,ny)=position(n)
        return propertyControls(n,nx+7,ny+if(n.kind in setOf(NodeKind.INPUT,NodeKind.SAVED,NodeKind.ROTATION,NodeKind.OUTPUT)) 29 else materialY(n),nodeWidth(n)-14)
    }
    private fun inspectorPropertyY(n:GraphNode)=y+42+inspectorSections(n).first { it.key=="properties" }.contentTop+4-inspectorScroll
    private fun inspectorSections(n:GraphNode)=view.inspectorSections.layout(
        (if(n.inputs.isEmpty()) emptyList() else listOf("connections" to (n.inputs.size*21+8)))+
        listOf("properties" to ((if(n.kind==NodeKind.OUTPUT) 21 else propertyHeight(n))+8),"3d" to inspectorPreviewHeight()))
    private fun inspectorPreviewHeight()=(height-68).coerceIn(40,190)
    private fun inspectorHeight(n:GraphNode)=inspectorSections(n).last().let { it.top+it.height+4 }
    fun sectionExpanded(key:String)=view.inspectorSections.isExpanded(key)
    fun revealSection(key:String) {
        val n=selectedNode();val section=inspectorSections(n).firstOrNull { it.key==key } ?: return
        val available=height-46
        if(section.top<inspectorScroll || section.height>available) inspectorScroll=section.top
        else if(section.top+section.height>inspectorScroll+available) inspectorScroll=section.top+section.height-available
        inspectorScroll=inspectorScroll.coerceIn(0,(inspectorHeight(n)-available).coerceAtLeast(0))
    }
    fun showSection(key:String) { view.inspectorSections.setExpanded(key,true);revealSection(key) }
    fun sectionHeaderPoint(key:String):Pair<Double,Double> {
        revealSection(key)
        return x+width/2.0 to y+42+inspectorSections(selectedNode()).first { it.key==key }.top-inspectorScroll+11.0
    }
    private fun propertyHeight(n:GraphNode)=when(n.kind) {
        NodeKind.INPUT->(if(inspector) 147 else 84)+(if(n.part.source==RoofPartParameters.SOURCE) 21 else 0);NodeKind.SAVED->84;NodeKind.ROTATE,NodeKind.ROTATION->63;NodeKind.OUTPUT->171
        NodeKind.UNION,NodeKind.SYMMETRIC_DIFFERENCE->42+21*(if(n.materialMode==MaterialMode.GROUPED) 1 else n.inputs.size)+(if(uniformGroupWarning(n)) 48 else 0)
        else->21
    }
    private fun inspectorColumnWidth(n:GraphNode)=width-28
    private fun inspectorPreviewBox(n:GraphNode):IntArray? {
        val section=inspectorSections(n).first { it.key=="3d" }.takeIf { it.expanded } ?: return null
        return intArrayOf(x+12,y+42+section.contentTop+2-inspectorScroll,width-28)
    }
    private fun inspectorControls(n:GraphNode):List<Control> {
        val sections=inspectorSections(n)
        val connections=sections.firstOrNull { it.key=="connections" && it.expanded }
        val inputs=if(connections==null) emptyList() else n.inputs.mapIndexed { i,id ->
            Control(Field.CONNECT,x+12,y+42+connections.contentTop+4+i*21-inspectorScroll,inspectorColumnWidth(n),"${if(n.kind==NodeKind.ROTATE) ui(if(i==0) "port_copycat" else "port_rotation") else if(n.kind==NodeKind.DIFFERENCE) if(i==0) "A" else "B" else ui("input")+" "+(i+1)}: ${graph.nodes.firstOrNull { it.id==id }?.let { nodeLabel(it) } ?: ui("disconnected")}",slot=i,enabled=!readOnly)
        }
        return listOf(Control(Field.SELECT,x+12,y+10,width-24,"${ui("selected_node")}: ${nodeLabel(n)}"))+inputs+
            if(sectionExpanded("properties")) propertyControls(n,x+12,inspectorPropertyY(n),inspectorColumnWidth(n)) else emptyList()
    }
    private fun dropdown(field:Field)=field in setOf(Field.SELECT,Field.CONNECT,Field.TURN,Field.TOP,Field.GROUP,Field.TILT,Field.MATERIAL,Field.PREVIEW,Field.ROTATION_AXIS,Field.INPUT_GROUP,Field.MIRROR_PLANE)
    private fun screenControl(c:Control):Control=if(inspector) c else c.copy(x=(x+view.panX+c.x*view.zoom).roundToInt(),y=(y+view.panY+c.y*view.zoom).roundToInt(),w=(c.w*view.zoom).roundToInt())
    private fun choices(m:Menu):List<Choice> {
        if(m.field==Field.SELECT) return graph.nodes.filter { allowedNodes==null || it.id in allowedNodes.invoke() }.map { Choice(it.id,nodeLabel(it)) }
        if(m.field==Field.INSERT) return listOf(Choice(NodeKind.SAVED.name,ui("node_saved")))+BaseShapes.all.map { Choice(it.id,ui(it.name)) }+GraphModels.shapeOperations.map { Choice(it.name,ui(GraphModels.shape(it).labelKey)) }
        val n=graph.nodes.firstOrNull { it.id==m.node } ?: return emptyList()
        return when(m.field) {
            Field.CONNECT -> listOf(Choice("",ui("disconnected")))+graph.nodes.filter { source -> (allowedNodes==null || source.id in allowedNodes.invoke()) && source.kind!=NodeKind.OUTPUT && source.id!=n.id && runCatching {
                GraphCompiler.structure(graph.replace(n.copy(inputs=n.inputs.mapIndexed { i,id -> if(i==m.slot) source.id else id })),definition().groups)
            }.isSuccess }.map { Choice(it.id,nodeLabel(it)) }
            Field.TURN,Field.TILT -> (0..3).map { Choice(it.toString(),"${it*90}°") }
            Field.ROTATION_AXIS -> RotationAxis.entries.map { Choice(it.name,axisLabel(it)) }
            Field.MIRROR_PLANE -> CopycatMirrorPlane.entries.map { Choice(it.name,ui("mirror_plane_"+it.name.lowercase())) }
            Field.TOP -> listOf(Choice("false",ui("bottom")),Choice("true",ui("top")))
            Field.GROUP,Field.MATERIAL,Field.PREVIEW,Field.INPUT_GROUP -> (if(m.field==Field.INPUT_GROUP) listOf(Choice("",ui("inherit_groups"))) else if(m.field==Field.PREVIEW) listOf(Choice("",ui("all_groups"))) else emptyList())+definition().groups.map { Choice(it.id,it.name) }+
                (if(m.field!=Field.PREVIEW && definition().groups.size<8) listOf(Choice("@new",ui("new_group"))) else emptyList())
            else -> emptyList()
        }
    }
    private fun selected(m:Menu):String {
        if(m.field==Field.SELECT) return view.selected
        val n=graph.nodes.firstOrNull { it.id==m.node } ?: return ""
        return when(m.field) {
            Field.CONNECT->n.inputs.getOrNull(m.slot).orEmpty();Field.SOURCE->n.part.source;Field.TURN->n.quarterTurns.toString();Field.TOP->n.part.top.toString();Field.SIZE->n.part.size.toString()
            Field.ROTATION_AXIS->n.rotationAxis.name;Field.MIRROR_PLANE->(n.mirror ?: CopycatMirror()).plane.name;Field.INPUT_GROUP->n.inputGroups.getOrNull(m.slot).orEmpty();Field.GROUP->n.part.group;Field.TILT->n.part.tilt.toString();Field.MATERIAL->n.materialGroup.ifEmpty { definition().groups.first().id };Field.PREVIEW->n.previewGroup;else->""
        }
    }
    private fun menuBox(m:Menu):IntArray {
        val options=choices(m);val rows=minOf(options.size,12,((height-4)/18).coerceAtLeast(1))
        val w=minOf((width-4).coerceAtLeast(1),maxOf(160,(options.maxOfOrNull { font.width(it.label) } ?: 0)+18).coerceAtMost(280))
        val xx=m.anchorX.coerceIn(x+2,maxOf(x+2,x+width-w-2))
        val yy=(if(m.anchorY+rows*18<=y+height-2) m.anchorY else m.anchorY-rows*18-18).coerceIn(y+2,maxOf(y+2,y+height-rows*18-2))
        return intArrayOf(xx,yy,w,rows)
    }
    private fun openMenu(n:GraphNode,c:Control) {
        view.wire=null
        val sc=screenControl(c);val bottom=sc.y+(if(inspector) 18 else (18*view.zoom).roundToInt())
        val next=Menu(c.field,n.id,sc.x,bottom+1,c.slot)
        val options=choices(next);val rows=menuBox(next)[3]
        next.cursor=options.indexOfFirst { it.id==selected(next) }.coerceAtLeast(0)
        next.first=(next.cursor-rows+1).coerceIn(0,(options.size-rows).coerceAtLeast(0));menu=next
    }
    private fun choose(m:Menu,id:String) {
        if(m.field==Field.SELECT) { view.selected=id;view.wire=null;inspectorScroll=0;onSelectNode(id);return }
        if(readOnly) return
        if(m.field==Field.INSERT) {
            val base=BaseShapes.all.firstOrNull { it.id==id }
            if(base!=null) addAt(NodeKind.INPUT,base.source,m.insertX,m.insertY,base.discCut) else addAt(NodeKind.valueOf(id),Adapters.all.keys.first(),m.insertX,m.insertY)
            return
        }
        val n=graph.nodes.firstOrNull { it.id==m.node } ?: return
        if(m.field==Field.CONNECT) { connect(id,n.id,m.slot);return }
        if(id=="@new" && m.field in setOf(Field.GROUP,Field.MATERIAL,Field.INPUT_GROUP)) {
            EditorValueInput.text(tr("new_group"),"",maxLength=32) { name -> onCreateGroup(name,n.id,m.field==Field.MATERIAL,m.slot.takeIf { m.field==Field.INPUT_GROUP } ?: -1) }
            return
        }
        safe {
            val next=when(m.field) {
                Field.ROTATION_AXIS->n.copy(rotationAxis=RotationAxis.valueOf(id));Field.TURN->n.copy(quarterTurns=id.toInt());
                Field.MIRROR_PLANE->n.copy(mirror=CopycatMirror(CopycatMirrorPlane.valueOf(id)))
                Field.INPUT_GROUP->n.copy(inputGroups=List(n.inputs.size) { i -> if(i==m.slot) id else n.inputGroups.getOrNull(i).orEmpty() });Field.TOP->n.copy(part=n.part.copy(top=id.toBoolean()));Field.SIZE->n.copy(part=n.part.copy(size=id.toInt()))
                Field.GROUP->n.copy(part=n.part.copy(group=id));Field.TILT->n.copy(part=n.part.copy(tilt=id.toInt()));Field.MATERIAL->n.copy(materialGroup=id);Field.PREVIEW->n.copy(previewGroup=id);else->n
            }
            if(next!=n) edit(next)
        }
    }
    fun openSourceSelection(nodeId:String) {
        if(readOnly) return
        graph.nodes.firstOrNull { it.id==nodeId && it.kind==NodeKind.SAVED }?.let {
            menu=null;view.wire=null;onSavedSource(it.id,it.x,it.y);return
        }
        val node=graph.nodes.firstOrNull { it.id==nodeId && it.kind==NodeKind.INPUT } ?: return
        menu=null;view.wire=null
        CopycatSelectionScreen.open(BaseShapes.find(node.part.source,node.part.discCut)) { shape ->
            val current=graph.nodes.firstOrNull { it.id==nodeId && it.kind==NodeKind.INPUT } ?: return@open
            if(shape.id!=BaseShapes.find(current.part.source,current.part.discCut).id) safe {
                edit(current.copy(part=current.part.copy(source=shape.source,discCut=shape.discCut,size=1,offset=P(),tilt=0,spin=0,exact=null,exactClip=null,roof=null)))
            }
        }
    }
    fun openSourceDefinition(nodeId:String) {
        val node=graph.nodes.firstOrNull { it.id==nodeId && it.kind==NodeKind.INPUT } ?: return
        val source=sourceDefinition(node) ?: return
        menu=null;view.wire=null
        SourceDefinitionScreen.open(node.part,source,surfaces())
    }
    private fun activate(n:GraphNode,c:Control,px:Double,button:Int) {
        if(!c.enabled) return
        if(readOnly && c.field!=Field.SELECT && c.field!=Field.DEFINITION) return
        if(c.field==Field.DEFINITION) { openSourceDefinition(n.id);return }
        if(c.field==Field.SOURCE) { openSourceSelection(n.id);return }
        if(c.field==Field.ROOF_CONFIG) { openRoofSource(n);return }
        if(c.field==Field.ROTATE) { rotateSource(n.id,n.rotationAxis,c.slot);return }
        if(dropdown(c.field)) { openMenu(n,c);return }
        if(c.field in setOf(Field.SIZE,Field.X,Field.Y,Field.Z)) {
            val p=n.part
            val value=when(c.field) { Field.SIZE->p.size;Field.X->p.offset.x;Field.Y->p.offset.y;else->p.offset.z }
            EditorValueInput.integer(Component.literal(c.label),value,if(c.field==Field.SIZE) 1..Adapters.get(p.source).max else -15..15) { number ->
                val updated=when(c.field) {
                    Field.SIZE->p.copy(size=number);Field.X->p.copy(offset=p.offset.copy(x=number));Field.Y->p.copy(offset=p.offset.copy(y=number));else->p.copy(offset=p.offset.copy(z=number))
                }
                if(updated!=p) edit(n.copy(part=updated))
            }
            return
        }
        safe {
            val next=when(c.field) {
                Field.GROUPED->n.copy(materialMode=MaterialMode.GROUPED,materialGroup=if(uniformGroupWarning(n)) GraphCompiler.inputMaterialGroups(graph,n,0,definition().groups).single() else n.materialGroup);Field.SEPARATED->n.copy(materialMode=MaterialMode.SEPARATED)
                Field.ADD->n.copy(inputs=n.inputs+"");Field.REMOVE->n.copy(inputs=n.inputs.dropLast(1),inputGroups=n.inputGroups.take(n.inputs.size-1));Field.SWAP->n.copy(inputs=n.inputs.reversed());else->n
            }
            if(next!=n) edit(next)
        }
    }
    private fun openRoofSource(node:GraphNode) {
        var parameters=node.part.roof ?: RoofPartParameters()
        StructurePropertyScreen.open(tr("roof_source_settings"),{
            val ratio=RoofSlope(parameters.rise,parameters.run)
            choice(ui("roof_slope"),ratio,RoofSlope.presets,{ BuildingStructureEditors.slopeLabel(it) }) { parameters=parameters.copy(rise=it.p,run=it.q,phase=0,slice=0) }
            hint(ui("roof_repeat",parameters.run,parameters.rise))
            int(ui("roof_source_phase"),parameters.phase,0 until parameters.run) { parameters=parameters.copy(phase=it) }
            int(ui("roof_source_slice"),parameters.slice,-1..parameters.rise) { parameters=parameters.copy(slice=it) }
            int(ui("roof_skin_thickness"),parameters.thickness,1..16) { parameters=parameters.copy(thickness=it) }
            choice(ui("roof_source_variant"),parameters.variant,listOf("slope","eave","edge","ridge","hip","valley","corner","cap"),{ ui("roof_variant_$it") }) { parameters=parameters.copy(variant=it) }
        }) {
            val current=graph.nodes.first { it.id==node.id }
            val part=current.part.copy(roof=parameters,exact=null,exactClip=null)
            require(!Geometry.exact(part).isEmpty()) { ui("roof_source_empty_slice") }
            edit(current.copy(part=part))
        }
    }
    private fun text(g:GuiGraphics,s:String,x:Int,y:Int,c:Int=ArchitectTheme.text) { g.drawString(font,s,x,y,c,false) }
    private fun drawControl(g:GuiGraphics,c:Control) {
        val over=if(inspector) c.contains(screenMouseX,screenMouseY) else c.contains(mouseX,mouseY)
        if(!c.floating || over) ArchitectTheme.button(g,c.x,c.y,c.w,18,over && menu==null,c.active,c.enabled)
        else g.renderOutline(c.x,c.y,c.w,18,ArchitectTheme.brassDark)
        if(c.field==Field.DEFINITION) {
            val left=c.x+(c.w-16)/2;val color=if(over) ArchitectTheme.brass else ArchitectTheme.text
            g.fill(left, c.y+7,left+2,c.y+11,color);g.fill(left+2,c.y+5,left+14,c.y+7,color)
            g.fill(left+2,c.y+11,left+14,c.y+13,color);g.fill(left+14,c.y+7,left+16,c.y+11,color)
            g.fill(left+6,c.y+7,left+10,c.y+11,color)
            return
        }
        if(c.icon.isNotEmpty()) g.renderItem(net.minecraft.world.item.ItemStack(de.teutonstudio.copycatarchitect.server.Economy.item(c.icon)),c.x+2,c.y+1)
        val inset=if(c.icon.isEmpty()) 5 else 21
        text(g,font.plainSubstrByWidth(c.label,(c.w-inset-if(dropdown(c.field) && c.enabled) 14 else 4).coerceAtLeast(0)),c.x+inset,c.y+5,if(c.enabled) ArchitectTheme.text else ArchitectTheme.disabled)
        if(dropdown(c.field) && c.enabled) text(g,"▾",c.x+c.w-11,c.y+5,ArchitectTheme.brass)
    }
    private fun drawMenu(g:GuiGraphics) {
        val m=menu ?: return;val options=choices(m)
        if(options.isEmpty()) { menu=null;return }
        val (xx,yy,w,rows)=menuBox(m);m.first=m.first.coerceIn(0,(options.size-rows).coerceAtLeast(0))
        g.flush();g.pose().pushPose();g.pose().translate(0f,0f,400f)
        ArchitectTheme.bevel(g,xx-2,yy-2,w+4,rows*18+4,ArchitectTheme.panel)
        for(i in 0 until rows) {
            val choice=options[i+m.first];val hover=screenMouseX>=xx && screenMouseX<xx+w && screenMouseY>=yy+i*18 && screenMouseY<yy+(i+1)*18
            val active=selected(m)==choice.id
            ArchitectTheme.button(g,xx,yy+i*18,w,18,hover,active)
            if(m.keyboard && m.cursor==i+m.first) g.renderOutline(xx,yy+i*18,w,18,ArchitectTheme.brass)
            text(g,font.plainSubstrByWidth(choice.label,w-18),xx+6,yy+i*18+5,if(active) ArchitectTheme.brass else ArchitectTheme.text)
        }
        if(rows<options.size) { if(m.first>0) text(g,"↑",xx+w-9,yy+3,ArchitectTheme.brass);if(m.first+rows<options.size) text(g,"↓",xx+w-9,yy+rows*18-12,ArchitectTheme.brass) }
        g.flush();g.pose().popPose()
    }
    private fun drawPreview(g:GuiGraphics,n:GraphNode,px:Int,py:Int,w:Int) {
        val value=previewValue(n)
        ShapeNodePreviewRenderer.draw(g,px,py+23,w,115,value,definition().groups,surfaces(),view.yaw,showMaterials=previewMaterials())
        if(value.error.isEmpty() && value.rotation==null) text(g,ui("total_cost",previewCost(value)),px+2,py+145,ArchitectTheme.brass)
        text(g,ui("node_preview_hint"),px+2,py+158,ArchitectTheme.muted)
    }
    private fun drawInspectorPreview(g:GuiGraphics,n:GraphNode,px:Int,py:Int,w:Int) {
        val value=previewValue(n);val h=inspectorPreviewHeight()
        ShapeNodePreviewRenderer.drawThumbnail(g,px,py,w,h-34,value,definition().groups,surfaces(),showMaterials=previewMaterials(),yaw=view.yaw)
        if(value.error.isEmpty() && value.rotation==null) text(g,ui("total_cost",previewCost(value)),px+2,py+h-27,ArchitectTheme.brass)
        text(g,font.plainSubstrByWidth(ui("node_preview_hint"),w),px+2,py+h-14,ArchitectTheme.muted)
    }
    private fun curve(g:GuiGraphics,a:Pair<Double,Double>,b:Pair<Double,Double>,color:Int) {
        val span=maxOf(40.0,abs(a.first-b.first)*0.45);var last=a
        for(i in 1..50) {
            val t=i/50.0;val k=1-t
            val p=Pair(k*k*k*a.first+3*k*k*t*(a.first+span)+3*k*t*t*(b.first-span)+t*t*t*b.first,k*k*k*a.second+3*k*k*t*a.second+3*k*t*t*b.second+t*t*t*b.second)
            val steps=ceil(max(abs(p.first-last.first),abs(p.second-last.second))).toInt().coerceAtLeast(1)
            for(j in 0..steps) { val v=j.toDouble()/steps;val xx=(last.first+(p.first-last.first)*v).roundToInt();val yy=(last.second+(p.second-last.second)*v).roundToInt();g.fill(xx-1,yy-1,xx+2,yy+2,color) };last=p
        }
    }
    override fun renderWidget(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        if(groupInspector.available) { menu=null;groupInspector.render(g,mx,my,delta);return }
        if(!inspector && !view.fitted) fit()
        screenMouseX=mx.toDouble();screenMouseY=my.toDouble();mouseX=localX(screenMouseX);mouseY=localY(screenMouseY)
        if(WorldPeek.active) g.fill(x,y,x+width,y+height,0x3017241c) else ArchitectTheme.inset(g,x,y,width,height,ArchitectTheme.blueprint)
        g.enableScissor(x,y,x+width,y+height)
        if(inspector) renderInspector(g) else renderGraph(g)
        drawMenu(g);g.flush();g.disableScissor()
        if(menu==null && inspector && mx>=x && mx<x+width && my>=y+42 && my<y+height) {
            val node=selectedNode()
            inspectorControls(node).firstOrNull { it.contains(mx.toDouble(),my.toDouble()) && it.field in setOf(Field.DEFINITION,Field.ROTATE,Field.ROTATION_AXIS,Field.MIRROR_PLANE) }?.let { control ->
                val tooltip=if(control.field==Field.MIRROR_PLANE) tr("copycat_mirror_hint") else if(control.field==Field.DEFINITION) tr("source_definition_eye") else if(control.field==Field.ROTATION_AXIS) tr("rotation_axis_hint") else if(!control.enabled) tr("rotation_unavailable")
                    else tr("rotate_about_axis",control.slot*90,axisLabel(node.rotationAxis))
                g.renderTooltip(font,tooltip,mx,my)
            }
        }
    }
    private fun renderGraph(g:GuiGraphics) {
        g.pose().pushPose();g.pose().translate(x+view.panX,y+view.panY,0.0);g.pose().scale(view.zoom.toFloat(),view.zoom.toFloat(),1f)
        val gx=floor((-view.panX/view.zoom)/24).toInt()*24;val gy=floor((-view.panY/view.zoom)/24).toInt()*24
        for(xx in gx..(gx+(width/view.zoom).toInt()+48) step 24) g.fill(xx,gy,xx+1,gy+(height/view.zoom).toInt()+48,if(WorldPeek.active) 0x302a4133 else ArchitectTheme.grid)
        for(yy in gy..(gy+(height/view.zoom).toInt()+48) step 24) g.fill(gx,yy,gx+(width/view.zoom).toInt()+48,yy+1,if(WorldPeek.active) 0x302a4133 else ArchitectTheme.grid)
        graph.nodes.forEach { n -> n.inputs.forEachIndexed { i,id -> graph.nodes.firstOrNull { it.id==id }?.let { curve(g,outputSocket(it),inputSocket(n,i),ArchitectTheme.green) } } }
        view.wire?.let { id -> graph.nodes.firstOrNull { it.id==id }?.let { curve(g,outputSocket(it),Pair(mouseX,mouseY),ArchitectTheme.brass) } }
        graph.nodes.forEach { n ->
            val (nx,ny)=position(n);val w=nodeWidth(n);val h=nodeHeight(n)
            val c=when(n.kind) { NodeKind.INPUT->ArchitectTheme.green;NodeKind.OUTPUT->ArchitectTheme.brass;else->ArchitectTheme.casing }
            ArchitectTheme.panel(g,nx,ny,w,h)
            ArchitectTheme.bevel(g,nx+2,ny+2,w-4,21,ArchitectTheme.raised)
            g.fill(nx+5,ny+5,nx+8,ny+19,c)
            text(g,font.plainSubstrByWidth(nodeLabel(n),w-24),nx+13,ny+8)
            if(n.id==view.selected) g.renderOutline(nx-1,ny-1,w+2,h+2,ArchitectTheme.brass)
            n.inputs.forEachIndexed { i,id -> val (_,sy)=inputSocket(n,i)
                ArchitectTheme.inset(g,nx-5,sy.toInt()-5,10,10)
                g.fill(nx-2,sy.toInt()-2,nx+2,sy.toInt()+2,if(id.isEmpty()) ArchitectTheme.disabled else ArchitectTheme.green)
                text(g,if(n.kind==NodeKind.DIFFERENCE) if(i==0) "A" else "B" else "${i+1}",nx+10,sy.toInt()-3)
            }
            if(n.kind!=NodeKind.OUTPUT) { val (_,sy)=outputSocket(n);ArchitectTheme.inset(g,nx+w-5,sy.toInt()-5,10,10);g.fill(nx+w-2,sy.toInt()-2,nx+w+2,sy.toInt()+2,ArchitectTheme.green) }
            graphControls(n).forEach { drawControl(g,it) }
            if(n.kind==NodeKind.OUTPUT) drawPreview(g,n,nx+7,ny+29,w-14)
        }
        g.flush();g.pose().popPose()
        text(g,ui("node_insert_hint"),x+7,y+height-13,ArchitectTheme.muted)
    }
    private fun renderInspector(g:GuiGraphics) {
        val n=selectedNode()
        inspectorContentHeight=inspectorHeight(n)
        inspectorScroll=inspectorScroll.coerceIn(0,(inspectorContentHeight-(height-46)).coerceAtLeast(0))
        g.enableScissor(x,y+42,x+width,y+height)
        for(section in inspectorSections(n)) {
            val top=y+42+section.top-inspectorScroll
            ArchitectTheme.button(g,x+8,top,width-22,22,screenMouseX>=x+8 && screenMouseX<x+width-14 && screenMouseY>=maxOf(y+42,top) && screenMouseY<top+22)
            val label=ui(when(section.key) { "connections"->"node_connections";"properties"->"node_properties";else->"preview3d" })
            text(g,font.plainSubstrByWidth("${if(section.expanded) "▾" else "▸"} $label",(width-36).coerceAtLeast(1)),x+14,top+7,ArchitectTheme.brass)
            if(section.expanded) ArchitectTheme.bevel(g,x+8,top+22,width-22,section.contentHeight,ArchitectTheme.panel)
        }
        inspectorControls(n).filter { it.field!=Field.SELECT }.forEach { drawControl(g,it) }
        if(sectionExpanded("properties") && uniformGroupWarning(n)) {
            val warningY=inspectorPropertyY(n)+42+21*n.inputs.size+5
            font.split(tr("separated_same_group_warning"),inspectorColumnWidth(n)).forEachIndexed { i,line ->
                g.drawString(font,line,x+12,warningY+i*11,ArchitectTheme.brass,false)
            }
        }
        inspectorPreviewBox(n)?.let { (px,py,w) -> if(py+inspectorPreviewHeight()>y+42 && py<y+height) drawInspectorPreview(g,n,px,py,w) }
        g.disableScissor()
        ArchitectTheme.bevel(g,x,y,width,41,ArchitectTheme.panel);drawControl(g,inspectorControls(n).first())
        g.fill(x+12,y+36,x+width-12,y+37,ArchitectTheme.brassDark)
        val track=(height-46).coerceAtLeast(1)
        if(inspectorContentHeight>track) {
            val thumb=maxOf(12,track*track/inspectorContentHeight).coerceAtMost(track);val top=y+43+(track-thumb)*inspectorScroll/(inspectorContentHeight-track)
            g.fill(x+width-6,y+43,x+width-4,y+height-3,ArchitectTheme.well);g.fill(x+width-6,top,x+width-4,top+thumb,ArchitectTheme.brass)
        }
    }
    override fun mouseClicked(mx:Double,my:Double,button:Int):Boolean {
        if(groupInspector.available) return groupInspector.mouseClicked(mx,my,button)
        menu?.let { m ->
            val (xx,yy,w,rows)=menuBox(m);val options=choices(m);menu=null
            if(button==0 && mx>=xx && mx<xx+w && my>=yy && my<yy+rows*18) options.getOrNull(m.first+((my-yy)/18).toInt())?.let { choose(m,it.id) }
            return true
        }
        if(!isMouseOver(mx,my)) return false
        screenMouseX=mx;screenMouseY=my
        if(inspector) {
            val n=selectedNode()
            if(my>=y+42 && mx>=x+8 && mx<x+width-14) inspectorSections(n).firstOrNull { my>=y+42+it.top-inspectorScroll && my<y+42+it.top+22-inspectorScroll }?.let {
                if(button==0) { view.inspectorSections.toggle(it.key);inspectorScroll=inspectorScroll.coerceIn(0,(inspectorHeight(n)-(height-46)).coerceAtLeast(0)) }
                return true
            }
            val controls=inspectorControls(n)
            controls.firstOrNull { it.contains(mx,my) && (it.field==Field.SELECT || my>=y+42) }?.let { if(button==0 || button==1) activate(n,it,mx,button);return true }
            inspectorPreviewBox(n)?.let { (px,py,w) ->
                if(button==1 && mx>=px && mx<px+w && my>=maxOf(y+42,py) && my<py+inspectorPreviewHeight()-34) orbit=true
            }
            return true
        }
        val lx=localX(mx);val ly=localY(my);mouseX=lx;mouseY=ly
        if(button==2) { panning=true;return true }
        for(n in graph.nodes.asReversed()) {
            n.inputs.indices.forEach { i -> val s=inputSocket(n,i)
                if(abs(lx-s.first)<=9 && abs(ly-s.second)<=8) {
                    view.selected=n.id
                    if(readOnly) { view.wire=null;return true }
                    if(button==1) { connect("",n.id,i);view.wire=null }
                    else if(button==0) { view.wire?.let { connect(it,n.id,i);view.wire=null } ?: run { view.wire=n.inputs[i].ifEmpty { null } } }
                    return true
                }
            }
            if(n.kind!=NodeKind.OUTPUT) { val s=outputSocket(n)
                if(abs(lx-s.first)<=9 && abs(ly-s.second)<=8 && button==0) { view.wire=if(readOnly) null else n.id;view.selected=n.id;return true }
            }
            val (nx,ny)=position(n);val px=lx-nx;val py=ly-ny
            if(px<0 || px>=nodeWidth(n) || py<0 || py>=nodeHeight(n)) continue
            view.selected=n.id
            if(button==0 && py<23) { if(!readOnly) { dragging=n.id;moveX=n.x;moveY=n.y };return true }
            graphControls(n).firstOrNull { it.contains(lx,ly) }?.let { if(button==0 || button==1) activate(n,it,lx,button);return true }
            if(button==1 && n.kind==NodeKind.OUTPUT) orbit=true
            return true
        }
        if(button==1 && !readOnly) contextStart=mx to my else if(button==0) view.wire=null
        return true
    }
    override fun mouseDragged(mx:Double,my:Double,button:Int,dx:Double,dy:Double):Boolean {
        if(menu!=null) return true
        contextStart?.let { start ->
            if(hypot(mx-start.first,my-start.second)>=4) { contextStart=null;panning=true;view.panX+=mx-start.first;view.panY+=my-start.second };return true
        }
        when { dragging!=null -> { moveX=(moveX+dx/view.zoom).roundToInt().coerceIn(-8192,8192);moveY=(moveY+dy/view.zoom).roundToInt().coerceIn(-8192,8192) }
            panning->{view.panX+=dx;view.panY+=dy};orbit->view.yaw+=dx.toFloat();else->return view.wire!=null }
        return true
    }
    override fun mouseReleased(mx:Double,my:Double,button:Int):Boolean {
        contextStart?.let { start ->
            contextStart=null
            if(button==1 && hypot(mx-start.first,my-start.second)<4 && isMouseOver(mx,my)) {
                view.wire=null;menu=Menu(Field.INSERT,"",mx.roundToInt(),my.roundToInt(),insertX=localX(mx).roundToInt(),insertY=localY(my).roundToInt())
            }
        }
        dragging?.let { id -> val node=graph.nodes.firstOrNull { it.id==id };dragging=null;if(node!=null && (node.x!=moveX || node.y!=moveY)) edit(node.copy(x=moveX,y=moveY)) }
        if(!inspector && button==0 && view.wire!=null) {
            val lx=localX(mx);val ly=localY(my)
            for(n in graph.nodes) for(i in n.inputs.indices) { val s=inputSocket(n,i);if(abs(lx-s.first)<=9 && abs(ly-s.second)<=8) { val source=view.wire!!;view.wire=null;connect(source,n.id,i);panning=false;orbit=false;return true } }
        }
        panning=false;orbit=false;return true
    }
    override fun mouseScrolled(mx:Double,my:Double,dx:Double,dy:Double):Boolean {
        if(groupInspector.available) return groupInspector.mouseScrolled(mx,my,dx,dy)
        menu?.let { m ->
            val (xx,yy,w,rows)=menuBox(m)
            if(mx>=xx && mx<xx+w && my>=yy && my<yy+rows*18) m.first=(m.first-dy.sign.toInt()).coerceIn(0,(choices(m).size-rows).coerceAtLeast(0)) else menu=null
            return true
        }
        if(!isMouseOver(mx,my)) return false
        if(inspector) { inspectorScroll=(inspectorScroll-(dy*21).roundToInt()).coerceIn(0,(inspectorHeight(selectedNode())-(height-46)).coerceAtLeast(0));return true }
        val lx=localX(mx);val ly=localY(my)
        view.zoom=(view.zoom*1.12.pow(dy)).coerceIn(0.25,1.75)
        view.panX=mx-x-lx*view.zoom;view.panY=my-y-ly*view.zoom;return true
    }
    override fun keyPressed(key:Int,scan:Int,mods:Int):Boolean {
        if(groupInspector.available) return false
        if(menu!=null) {
            if(key==256) { menu=null;return true }
            val m=menu!!;val options=choices(m);val rows=menuBox(m)[3]
            if(key==257 || key==335) { menu=null;options.getOrNull(m.cursor)?.let { choose(m,it.id) };return true }
            if(key in listOf(264,265,266,267,268,269)) {
                m.keyboard=true
                m.cursor=when(key) { 264->m.cursor+1;265->m.cursor-1;266->m.cursor-rows;267->m.cursor+rows;268->0;else->options.lastIndex }.coerceIn(0,options.lastIndex.coerceAtLeast(0))
                m.first=m.first.coerceIn(maxOf(0,m.cursor-rows+1),m.cursor).coerceIn(0,(options.size-rows).coerceAtLeast(0));return true
            }
            return true
        }
        if(key==261 || key==259) { deleteSelected();return true }
        if(key==256 && view.wire!=null) { view.wire=null;return true };return false
    }
    override fun updateWidgetNarration(output:NarrationElementOutput) { output.add(NarratedElementType.TITLE,tr("node_graph"));output.add(NarratedElementType.USAGE,tr("graph_help")) }
}
