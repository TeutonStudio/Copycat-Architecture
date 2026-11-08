package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.*
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.*
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import com.mojang.math.Axis
import java.util.UUID

fun ui(key:String,vararg args:Any)=net.minecraft.client.resources.language.I18n.get("gui.copycat_architect.$key",*args)
fun tr(key:String,vararg args:Any)=Component.translatable("gui.copycat_architect.$key",*args)
private fun KilaGraphData.isLayoutEditOf(original:KilaGraphData):Boolean {
    if(links!=original.links || nodeGroups!=original.nodeGroups || nodes.size!=original.nodes.size) return false
    if(nodes.indices.any { nodes[it].id!=original.nodes[it].id }) return false
    require(nodes.all { it.x in -8192..8192 && it.y in -8192..8192 }) { "Node position exceeds graph bounds" }
    return true
}

private fun KilaGraphData.withPositionsFrom(layout:KilaGraphData):KilaGraphData =
    copy(nodes=nodes.zip(layout.nodes) { node,position ->
        if(node.x==position.x && node.y==position.y) node else node.copy(x=position.x,y=position.y)
    })

abstract class EditorScreen(title:Component):Screen(title) {
    var yaw=35f
    private var dropdown:EditorDropdown?=null
    protected fun resetDropdown() { dropdown=null }
    protected fun openDropdown(anchor:Button,options:List<Pair<String,()->Unit>>) { dropdown=EditorDropdown(anchor,options,-1) }
    protected fun button(x:Int,y:Int,w:Int,label:Component,action:()->Unit):Button = addRenderableWidget(EditorButton(x,y,w,label,action).apply { setTooltip(Tooltip.create(label)) })
    protected fun button(x:Int,y:Int,w:Int,key:String,action:()->Unit)=button(x,y,w,tr(key),action).apply {
        (this as EditorButton).primary=key in setOf("save","printer_print","equip","create_job","start","resume","apply","ps_load")
    }
    protected fun label(g:GuiGraphics,s:String,x:Int,y:Int,color:Int=ArchitectTheme.text)=g.drawString(font,s,x,y,color,false)
    protected fun graphSummary(g:GuiGraphics,cost:String,hint:String) {
        label(g,cost,8,height-26,ArchitectTheme.brass)
        val hintX=font.width(cost)+20
        label(g,font.plainSubstrByWidth(hint,(width-hintX-8).coerceAtLeast(0)),hintX,height-26,ArchitectTheme.muted)
    }
    protected fun field(x:Int,y:Int,w:Int,value:String):EditBox = addRenderableWidget(EditorTextField(font,x,y,w,18,Component.empty()).apply { setMaxLength(64);this.value=value })
    protected fun cycle(x:Int,y:Int,w:Int,text:String,action:()->Unit)=button(x,y,w,Component.literal(text),action)
    protected fun choice(x:Int,y:Int,w:Int,text:String,options:List<Pair<String,()->Unit>>,selected:Int=-1):Button {
        lateinit var anchor:Button
        anchor=button(x,y,w,Component.literal(text)) { dropdown=EditorDropdown(anchor,options,selected) }
        (anchor as EditorButton).dropdown=true;anchor.active=options.isNotEmpty()
        anchor.setTooltip(Tooltip.create(Component.literal(text)))
        return anchor
    }
    protected fun <T> select(x:Int,y:Int,w:Int,value:T,values:List<T>,display:(T)->String,action:(T)->Unit)=
        choice(x,y,w,display(value),values.map { v -> display(v) to { if(v!=value) action(v) } },values.indexOf(value))
    protected fun number(x:Int,y:Int,w:Int,value:Int,range:IntRange,display:(Int)->String,action:(Int)->Unit)=
        button(x,y,w,Component.literal(display(value))) { EditorValueInput.integer(Component.literal(display(value)),value,range,action) }
    protected fun dropdownClick(x:Double,y:Double,button:Int):Boolean {
        val menu=dropdown ?: return false;dropdown=null;menu.click(x,y,button,width,height)?.invoke();return true
    }
    protected fun dropdownKey(key:Int):Boolean {
        val menu=dropdown ?: return false
        if(key==256) dropdown=null else { val action=menu.key(key,width,height);if(action!=null) { dropdown=null;action() } };return true
    }
    protected fun error(e:Exception) { ClientState.snapshot=ClientState.snapshot.copy(message=e.message ?: "Invalid design") }
    protected fun safe(action:()->Unit) { try { action() } catch(e:Exception) { error(e) } }
    protected open val worldPeekButton:PrinterEyeButton? get()=null
    protected open fun titleAvailableWidth()=(width-270).coerceAtLeast(60)
    override fun render(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        val eye=worldPeekButton
        WorldPeek.active=eye?.let { it.visible && it.active && it.isMouseOver(mx.toDouble(),my.toDouble()) }==true
        if(WorldPeek.active) {
            // Reveal the world, including beneath nodes, inspectors and popup menus.
            // Keep the hover target visible without queuing a tooltip over the preview.
            eye?.renderWorldPeek(g,mx,my,delta)
            return
        }
        renderChrome(g)
        renderWorkspace(g)
        super.render(g,mx,my,delta)
        renderContents(g,mx,my,delta)
        dropdown?.render(g,mx,my,width,height)
    }
    protected open fun renderChrome(g:GuiGraphics) {
        ArchitectTheme.background(g,width,height,WorldPeek.active)
        ArchitectTheme.header(g,width,title.string,titleAvailableWidth())
        ArchitectTheme.footer(g,width,height,ClientState.snapshot.message.ifBlank { ui("ui_ready") })
    }
    protected open fun renderWorkspace(g:GuiGraphics) {}
    protected open fun renderContents(g:GuiGraphics,mx:Int,my:Int,delta:Float) {}
    override fun mouseMoved(x:Double,y:Double) {
        // Vanilla containers do not forward movement. Native menu branches need it,
        // including when a submenu extends beyond the graph widget's bounds.
        children().toList().forEach { it.mouseMoved(x,y) }
    }
    override fun mouseScrolled(x:Double,y:Double,dx:Double,dy:Double):Boolean {
        dropdown?.let { it.scroll(dy,width,height);return true };return super.mouseScrolled(x,y,dx,dy)
    }
    override fun renderBackground(g:GuiGraphics,mx:Int,my:Int,delta:Float) {}
    override fun removed() { WorldPeek.active=false;super.removed() }
    override fun isPauseScreen()=false
    override fun mouseDragged(x:Double,y:Double,button:Int,dx:Double,dy:Double):Boolean {
        if(button==1) { yaw+=dx.toFloat();return true };return super.mouseDragged(x,y,button,dx,dy)
    }
}
class ShapeScreen(private val parent:BuildingScreen?=null,private val printerSession:PrinterView?=null):EditorScreen(tr(if(printerSession!=null) "printer_editor" else "shape_editor")) {
    override fun titleAvailableWidth()=if(printerSession!=null) (width-337).coerceAtLeast(24) else super.titleAvailableWidth()
    var form=Form(id=UUID.randomUUID().toString(),name="Inner corner",parts=listOf(Part(),Part(turn=1)))
    private var eyeButton:PrinterEyeButton?=null
    private var previewSent=""
    private var nextPreview=0L
    // Explicit coating IDs for both table and printer drafts; included in the shared edit history.
    val printerMaterials=mutableMapOf<String,String>()
    private var placementTurn=0;private var selected=0;private var view=0;var material=0
    var groupMode=false;var groupIndex=0
    var graphMode=true
    var graphInspector=false
    var materialPreview=parent==null;private set
    var graphView=GraphView();private set
    var graphCanvas:NodeGraphCanvas?=null;private set
    var kilaGraphState=KilaGraphState();private set
    var kilaGraph:KilaGraphEditor?=null;private set
    private val shapeInspectorVisible get()=if(printerSession!=null || parent!=null) graphInspector && (kilaGraphState.selection.size==1 || kilaGraphState.selected.startsWith("@group:")) else graphInspector || width>=700
    private var previewKey:Form?=null;private var previewCache:Result<Form>?=null
    private fun sourceParts(f:Form)=f.graph?.nodes?.filter { it.kind==NodeKind.INPUT }?.map { it.part } ?: f.parts
    private var shapeProjectionKey:Form?=null
    private var shapeProjection:NodeGraph?=null
    private var shapeDiagramCache:Pair<NodeGraph,KilaGraphData>?=null
    private val shapeNodePreviews=ShapeNodePreviews({ form },graph=::shapeDisplayGraph)
    private val shapeThumbnailJobs=PreviewJobs<String,ShapeNodePreview>()
    private val shapeCostJob=PreviewJobs<Unit,Int>()
    private var previewForm:Form?=null
    private var thumbnailSource:ShapeNodePreviews?=null
    private var observedForm:Form?=null
    private var observedShapeGraph:NodeGraph?=null
    private fun refreshShapePreviewSource() {
        val graph=shapeDisplayGraph()
        if(observedForm===form && observedShapeGraph===graph) return
        observedForm=form;observedShapeGraph=graph
        val geometryGraph=graph.copy(nodes=graph.nodes.map { it.copy(x=0,y=0) })
        val input=form.copy(graph=geometryGraph.takeIf { form.graph!=null })
        if(input==previewForm && thumbnailSource!=null) return
        previewForm=input
        val disconnected=ui("disconnected")
        thumbnailSource=ShapeNodePreviews({ input },{ disconnected }) { geometryGraph }
        shapeThumbnailJobs.clear();shapeCostJob.clear()
    }
    private fun shapeThumbnail(id:String):ShapeNodePreview? {
        refreshShapePreviewSource()
        val source=checkNotNull(thumbnailSource)
        return shapeThumbnailJobs.request(id) { source.get(id) }
            ?.getOrElse { ShapeNodePreview(error=it.message.orEmpty()) }
    }
    private fun shapeGraphRevision():NodeGraph=shapeDisplayGraph()
    fun shapeNodePreview(id:String)=shapeNodePreviews.get(id)
    fun shapeDisplayGraph():NodeGraph {
        form.graph?.let { return it }
        if(shapeProjectionKey!=form) { shapeProjectionKey=form;shapeProjection=GraphPresentation.shape(form) }
        return shapeProjection!!
    }
    fun displayShape():Form=if(form.graph!=null) form else form.copy(graph=shapeDisplayGraph())
    fun selectShapeNode(id:String) {
        graphView.selected=id;graphView.inspectorScroll=0
        if(printerSession!=null || parent!=null) {
            kilaGraphState.selected=id;kilaGraphState.selection=shapeDisplayGraph().nodeGroups.firstOrNull { "@group:${it.id}"==id }?.nodes?.toSet() ?: if(id.isEmpty()) emptySet() else setOf(id)
            printerSelectionChanged(kilaGraphState.selection)
        }
    }
    private fun shapeDiagram():KilaGraphData {
        val graph=shapeDisplayGraph()
        shapeDiagramCache?.let { (key,value) -> if(key===graph) return value }
        return KilaGraphData(graph.nodes.mapIndexed { index,n ->
            KilaNode(n.id,"${index+1} · ${ui(GraphModels.shape(n.kind).labelKey)}",n.x,n.y,n.inputs.indices.map { i ->
                if(n.kind==NodeKind.ROTATE) ui(if(i==0) "port_copycat" else "port_rotation") else if(n.kind==NodeKind.DIFFERENCE) if(i==0) "A" else "B" else "${ui("input")} ${i+1}"
            },n.kind!=NodeKind.OUTPUT,n.kind!=NodeKind.OUTPUT,inputTypes=n.inputs.indices.map { checkNotNull(GraphModels.shape(n.kind).inputs).type(it) },outputType=GraphModels.shape(n.kind).output.orEmpty(),
                source=if(n.kind==NodeKind.INPUT) n.part.source else "",discCut=n.part.discCut,part=if(n.kind==NodeKind.INPUT) n.part else null,rotatable=n.kind==NodeKind.ROTATION || n.kind==NodeKind.ROTATE && n.inputs.getOrNull(1).isNullOrEmpty(),rotationAxis=n.rotationAxis,previewExpandedByDefault=n.kind==NodeKind.OUTPUT,sourceLabel=n.savedCopycat?.let { "${it.name} · v${it.version}" }.orEmpty())
        },graph.nodes.flatMap { n -> n.inputs.mapIndexedNotNull { i,id -> id.takeIf { it.isNotEmpty() }?.let { KilaLink(it,n.id,i) } } },graph.nodeGroups)
            .also { shapeDiagramCache=graph to it }
    }
    private fun commitShapeDiagram(data:KilaGraphData) {
        val original=shapeDisplayGraph()
        val diagram=shapeDiagram()
        if(form.graph!=null && data.isLayoutEditOf(diagram)) {
            val moved=original.copy(nodes=original.nodes.zip(data.nodes) { node,position -> node.copy(x=position.x,y=position.y) })
            val before=editState()
            val next=form.copy(name=if(::name.isInitialized) name.value else form.name,graph=moved)
            if(form!=next) {
                undo.addLast(before);redo.clear();form=next;groupTextEdit=""
                shapeDiagramCache=moved to diagram.withPositionsFrom(data)
                refreshShapeHistoryButtons()
            }
            return
        }
        val graph=original.copy(nodeGroups=data.nodeGroups.map { group -> original.nodeGroups.firstOrNull { it.id==group.id }?.let { group.copy(nodes=it.nodes) } ?: group })
        commitShapeGraph(DiagramEdits.shape(graph,form.groups,data.nodes.map { DiagramPosition(it.id,it.x,it.y) },data.links.map { DiagramConnection(it.source,it.target,it.slot.toString()) }))
    }
    private fun instantiateShapeGroup(id:String) { safe { commitShapeGraph(GraphGroups.instantiate(shapeDisplayGraph(),id)) } }
    private fun shapeInsertions():List<KilaInsertion> = BaseShapes.all.map { shape ->
        val category=when(shape.name) {
            "full","slab","stairs","beam","byte"->"copycat_group_basic"
            "half_layer","vertical_half_layer","stacked_half_layer"->"copycat_group_half_layers"
            "flat_pane","half_disc","quarter_disc"->"copycat_group_discs"
            else->"copycat_group_trim"
        }
        KilaInsertion(ui(shape.name),ui(category),shape.source) { x,y -> graphCanvas?.addAt(NodeKind.INPUT,shape.source,x,y,shape.discCut) }
    }+listOf(KilaInsertion(ui("node_saved"),ui("copycat_library")) { x,y -> openSavedCopycatSource("",x,y) })+GraphModels.shapeOperations.map { kind ->
        KilaInsertion(ui(GraphModels.shape(kind).labelKey),ui("copycat_group_operations")) { x,y -> graphCanvas?.addAt(kind,Adapters.all.keys.first(),x,y) }
    }
    private fun addShapeNode(kind:NodeKind,source:String=Adapters.all.keys.first(),discCut:Int=0) {
        val point=kilaGraph?.insertionPoint()
        if(point==null) graphCanvas?.add(kind,source,discCut) else graphCanvas?.addAt(kind,source,point.first,point.second,discCut)
    }
    fun commitShapeGraph(value:NodeGraph) {
        val original=shapeDisplayGraph()
        val graph=GraphGroups.synchronize(original,value.copy(nodeGroups=GraphGroups.prune(value.nodeGroups,value.nodes.map { it.id }.toSet())))
        if(graph==original) return
        if(form.graph==null && form.parts.any { it.cutMask.isNotEmpty() || it.recipeSource.isNotEmpty() }) {
            shapeProjection=graph;return
        }
        safe {
            if(printerSession!=null || parent!=null) {
                val added=graph.nodes.map { it.id }.toSet()-shapeDisplayGraph().nodes.map { it.id }.toSet()
                if(added.isNotEmpty()) { kilaGraphState.selection=added;kilaGraphState.selected=added.first();graphView.selected=added.singleOrNull().orEmpty() }
            }
            edit { it.copy(graph=graph) }
        }
    }
    init { printerSession?.let { form=it.form;placementTurn=it.turn;printerMaterials.putAll(it.surfaces);graphView.selected="" } }
    private var groupTextEdit=""
    private fun editGroupText(id:String,property:String,value:String) {
        val group=form.groups.first { it.id==id }
        if(property=="name" && (value.isBlank() || value==group.name)) return
        if(property=="role" && (!value.matches(Regex("[a-zA-Z0-9_-]{1,32}")) || value==group.role)) return
        val key="$id:$property"
        if(groupTextEdit!=key) { undo.addLast(editState());redo.clear();groupTextEdit=key }
        form=form.copy(groups=form.groups.map { if(it.id!=id) it else if(property=="name") it.copy(name=value) else it.copy(role=value) })
    }
    private fun createGroup(groupName:String,nodeId:String="",materialGroup:Boolean=false,slot:Int=-1) {
        require(groupName.isNotBlank() && groupName.length<=32)
        require(form.groups.size<8) { ui("group_limit") }
        edit { f ->
            val priorities=f.groups.map { it.priority }.toSet()
            val priority=((f.groups.maxOf { it.priority }+1)..32).firstOrNull { it !in priorities }
                ?: (-32..32).last { it !in priorities }
            val group=MaterialGroup(UUID.randomUUID().toString(),groupName,"surface",priority)
            val graph=if(nodeId.isEmpty()) f.graph else shapeDisplayGraph().let { displayed -> displayed.replace(displayed.nodes.first { it.id==nodeId }.let { n ->
                if(slot>=0) n.copy(inputGroups=List(n.inputs.size) { i -> if(i==slot) group.id else n.inputGroups.getOrNull(i).orEmpty() }) else if(materialGroup) n.copy(materialMode=MaterialMode.GROUPED,materialGroup=group.id) else n.copy(part=n.part.copy(group=group.id))
            }) }
            groupIndex=f.groups.size
            f.copy(groups=f.groups+group,graph=graph)
        }
    }
    private fun preview():Result<Form> { if(previewKey!=form) { previewKey=form;previewCache=runCatching { Geometry.validate(form) } };return previewCache!! }
    internal fun graphZincText():String {
        refreshShapePreviewSource()
        val input=checkNotNull(previewForm)
        val cost=shapeCostJob.request(Unit) {
            val checked=Geometry.validate(input)
            Extrusion.cost(checked.parts,checked.groups)
        }
        return ui("total_cost",if(cost==null) ui("preview_calculating") else cost.getOrNull() ?: "—")
    }
    private fun changeParts(f:Form,parts:List<Part>):Form {
        val graph=f.graph ?: return f.copy(parts=parts)
        val leaves=graph.nodes.filter { it.kind==NodeKind.INPUT }
        var updated=graph
        if(parts.size==leaves.size) updated=graph.copy(nodes=graph.nodes.map { n -> val i=leaves.indexOf(n);if(i<0) n else n.copy(part=parts[i]) })
        else if(parts.size<leaves.size) updated=graph.remove(leaves[selected].id)
        else {
            val node=GraphNode(UUID.randomUUID().toString(),part=parts.last(),x=20,y=leaves.size*150)
            val output=graph.nodes.single { it.kind==NodeKind.OUTPUT };val old=output.inputs[0]
            val join=GraphNode(UUID.randomUUID().toString(),NodeKind.UNION,listOf(old,node.id),x=280,y=node.y)
            updated=graph.copy(nodes=graph.nodes+node+(if(old.isEmpty()) emptyList() else listOf(join)))
                .replace(output.copy(inputs=listOf(if(old.isEmpty()) node.id else join.id)))
        }
        updated=GraphGroups.synchronize(graph,updated)
        GraphCompiler.structure(updated,f.groups)
        return f.copy(parts=parts,graph=updated)
    }
    fun openGraph(inspector:Boolean=false) {
        if(printerSession!=null) { printerHistory=false;graphInspector=inspector;printerPrintPanel=false;rebuildWidgets();return }
        graphMode=true;graphInspector=inspector;groupMode=false;graphView.wire=null
        rebuildWidgets()
    }
    val surfaceMaterials=mutableMapOf<String,Int>()
    private val activeGroup get()=form.groups[groupIndex.coerceIn(0,form.groups.lastIndex)]
    private data class FormEditState(val form:Form,val materials:Map<String,String>,val turn:Int)
    private var undo=ArrayDeque<FormEditState>();private var redo=ArrayDeque<FormEditState>();private lateinit var name:EditBox
    private data class PrinterCard(val state:FormEditState,val view:GraphView,val graph:KilaGraphState,
                                   val undo:ArrayDeque<FormEditState>,val redo:ArrayDeque<FormEditState>,val group:Int)
    private val printerCards=mutableListOf<PrinterCard>()
    private var printerCardIndex=0
    private var printerCardFirst=0
    private var printerPrintPanel=false
    private var printerHistory=false
    private val printerHistoryState=PrinterHistoryState()
    var printerHistoryPanel:PrinterHistoryPanel?=null;private set
    var printerInventoryPanel:PrinterCartridgePanel?=null;private set
    private var attachedPrinterMenu:PrinterStorageMenu?=null
    private var observedPrinterLibrary:Library?=null
    val isPrinterWorkspace get()=printerSession!=null
    val printerHistoryVisible get()=printerHistory
    private fun printerMenu():PrinterStorageMenu?=if(minecraft?.player!=null) minecraft?.player?.containerMenu as? PrinterStorageMenu else attachedPrinterMenu
    fun attachPrinterInventory(menu:PrinterStorageMenu) { attachedPrinterMenu=menu }
    fun showPrinterPrint() { printerHistory=false;graphInspector=false;printerPrintPanel=true;printerPanelScroll=0;rebuildWidgets() }
    fun refreshPrinterLibrary() { if(printerHistory && minecraft!=null) rebuildWidgets() }
    private fun openPrinterInventory() {
        if(printerMenu()==null && minecraft?.player!=null) ClientState.send(printerCommand("printerStorage"))
    }
    private var printerGroups=false
    private var printerInspectorDirty=false
    private var printerPanelScroll=0
    private var printerPanelRows=0
    private var printerPanelControls:List<GuiEventListener> = emptyList()
    private var printerPrintTab:PrinterEdgeTab?=null
    private var shapeUndoButton:Button?=null
    private var shapeRedoButton:Button?=null
    private fun refreshShapeHistoryButtons() {
        shapeUndoButton?.active=!printerHistory && undo.isNotEmpty()
        shapeRedoButton?.active=!printerHistory && redo.isNotEmpty()
    }
    private val printerLayout get()=PrinterWorkspaceLayout(width,height,topTabs=parent!=null)
    val printerCardCount get()=printerCards.size
    val printerSelection get()=kilaGraphState.selection
    val printerPrintTabVisible get()=printerSession!=null && !printerHistory && printerSelection.isEmpty()
    private fun currentPrinterCard()=PrinterCard(editState(),graphView,kilaGraphState,undo,redo,groupIndex)
    fun switchPrinterCard(index:Int) {
        if(printerSession==null || index !in printerCards.indices) return
        if(index==printerCardIndex) { if(printerHistory) { printerHistory=false;rebuildWidgets() };return }
        kilaGraph?.dispose();if(printerCardIndex in printerCards.indices) printerCards[printerCardIndex]=currentPrinterCard();printerCardIndex=index
        val card=printerCards[index];restore(card.state);graphView=card.view;kilaGraphState=card.graph
        undo=card.undo;redo=card.redo;groupIndex=card.group
        printerCardFirst=when {
            index<printerCardFirst -> index
            index>=printerCardFirst+printerVisibleCards() -> index-printerVisibleCards()+1
            else -> printerCardFirst
        }
        printerHistory=false;printerPrintPanel=false;printerGroups=false;printerPanelScroll=0;rebuildWidgets()
    }
    fun openPrinterCard(saved:Form,settings:PrintSettings=PrintSettings(),newCard:Boolean=false) {
        if(printerSession==null) return
        if(!newCard) printerCards.indexOfFirst { it.state.form.id==saved.id && it.state.form.version==saved.version }.takeIf { it>=0 }?.let { switchPrinterCard(it);return }
        kilaGraph?.dispose()
        if(printerCards.isNotEmpty()) printerCards[printerCardIndex]=currentPrinterCard()
        form=saved;printerMaterials.clear();printerMaterials.putAll(settings.surfaces);placementTurn=settings.turn
        graphView=GraphView().apply { selected="" };kilaGraphState=KilaGraphState()
        undo=ArrayDeque();redo=ArrayDeque();groupIndex=0
        printerCards+=currentPrinterCard();printerCardIndex=printerCards.lastIndex
        printerCardFirst=maxOf(0,printerCardIndex-printerVisibleCards()+1)
        printerHistory=false;printerPrintPanel=false;printerGroups=false;printerPanelScroll=0;rebuildWidgets()
    }
    private fun closePrinterCard() {
        if(printerCards.size<=1) { onClose();return }
        kilaGraph?.dispose();printerCards.removeAt(printerCardIndex)
        val target=printerCardIndex.coerceAtMost(printerCards.lastIndex);printerCardIndex=-1
        switchPrinterCard(target)
    }
    private fun printerVisibleCards()=((printerLayout.height-114)/24).coerceAtLeast(1)
    private fun printerSelectionChanged(ids:Set<String>) {
        graphView.selected=kilaGraphState.selected.takeIf { it.startsWith("@group:") } ?: ids.singleOrNull().orEmpty();graphView.inspectorScroll=0
        if(ids.isNotEmpty()) printerPrintPanel=false
        printerGroups=false;printerPanelScroll=0;printerInspectorDirty=true
    }
    private fun refreshPrinterInspector() {
        if(!printerInspectorDirty) return
        printerInspectorDirty=false
        val graph=kilaGraph ?: return
        graphCanvas?.visible=shapeInspectorVisible
        if(parent!=null) graph.resizeViewport(printerLayout.graphWidth(shapeInspectorVisible),printerLayout.graphHeight)
        if(printerSession!=null) refreshPrinterPanel()
    }
    private fun editState()=FormEditState(form,printerMaterials.toMap(),placementTurn)
    private fun restore(state:FormEditState) { form=state.form;printerMaterials.clear();printerMaterials.putAll(state.materials);placementTurn=state.turn }
    private fun editPrinterSettings(action:()->Unit) {
        val before=editState();action()
        if(editState()!=before) { undo.addLast(before);redo.clear();groupTextEdit="";rebuildWidgets() }
    }
    private fun assignments():Map<String,String> = form.groups.associate { group -> group.id to
        (printerMaterials[group.id] ?: if(printerSession!=null) "" else Economy.palette[surfaceMaterials[group.id] ?: material]) }
    internal fun openMaterialSelection(group:String=activeGroup.id) {
        val parts=preview().map { it.parts }.recoverCatching { error ->
            val intermediate=shapeNodePreview(graphView.selected)
            if(intermediate.error.isNotEmpty() || intermediate.rotation!=null) throw error
            intermediate.parts
        }.map { Geometry.rotate(it,placementTurn) }
        MaterialSelectionScreen.open(form.groups,parts,assignments(),group,printerSession!=null) { surfaces ->
            if(surfaces!=assignments()) editPrinterSettings { printerMaterials.putAll(surfaces) }
        }
    }
    private fun materialButton(x:Int,y:Int,w:Int):Button {
        val surface=assignments().getValue(activeGroup.id)
        val title=if(surface.isEmpty()) tr("printer_uncoated") else Economy.item(surface).description
        return button(x,y,w,title) { openMaterialSelection() }.apply { setTooltip(Tooltip.create(tr("select_material"))) }
    }
    fun loadPrinterDraft(view:PrinterView?) {
        if(printerSession==null || view==null || view.session!=printerSession.session) return
        if(view.form.id!=form.id) { openPrinterCard(view.form,PrintSettings(view.surfaces,view.turn));return }
        val incoming=view.form.groups.associate { it.id to view.surfaces[it.id].orEmpty() }
        if(form.copy(parts=emptyList())==view.form.copy(parts=emptyList()) && assignments()==incoming && placementTurn==view.turn) return
        undo.addLast(editState());redo.clear();form=view.form;printerMaterials.clear();printerMaterials.putAll(view.surfaces);placementTurn=view.turn;rebuildWidgets()
    }
    fun printerCommand(action:String)=Command(action,payload=Json.encode(form.copy(parts=if(form.graph!=null) emptyList() else form.parts)),key=printerSession?.session.orEmpty(),turn=placementTurn,surfaces=assignments())
    private fun canPrint():Boolean {
        val ready=preview().getOrNull() ?: return false
        val player=minecraft?.player ?: return false
        if(player.isCreative) return true
        val raw=Extrusion.cost(ready.parts,ready.groups)
        val slots=ClientState.snapshot.printer?.slots.orEmpty()
        val stored=printerMenu()?.let { m -> (0..6).sumOf { i -> val stack=m.getSlot(i).item;if(Economy.isCreativeCartridge(stack)) Int.MAX_VALUE.toLong() else Economy.units(stack).toLong() } }
            ?: slots.sumOf { if(it.creative) Int.MAX_VALUE.toLong() else it.units.toLong() }
        val available=stored+(0 until player.inventory.containerSize).sumOf { i -> val stack=player.inventory.getItem(i);when { Economy.isCreativeCartridge(stack)->Int.MAX_VALUE.toLong();Economy.isCartridge(stack)->Economy.units(stack).toLong();net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.item).toString()=="create:zinc_ingot"->stack.count*144L;else->0L } }
        if(available<raw) return false
        return Placement(parts=ready.parts,material="",groups=ready.groups,surfaces=assignments()).surfaceItems().all { (id,count) -> (0 until player.inventory.containerSize).sumOf { val stack=player.inventory.getItem(it);if(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.item).toString()==id) stack.count else 0 }>=count }
    }
    override val worldPeekButton get()=eyeButton.takeIf { printerSession!=null }
    override fun tick() {
        super.tick()
        if(printerHistory && observedPrinterLibrary!==ClientState.snapshot.library) { observedPrinterLibrary=ClientState.snapshot.library;rebuildWidgets() }
        if(printerSession!=null && System.currentTimeMillis()>=nextPreview) {
            val command=printerCommand("printerPreview");val json=Json.encode(command)
            if(json!=previewSent) { previewSent=json;ClientState.send(command) }
            nextPreview=System.currentTimeMillis()+200
        }
        if(printerSession!=null) children().filterIsInstance<Button>().filter { it !is PrinterEdgeTab && it.message.string==ui("printer_print") }.forEach { it.active=canPrint() }
    }
    private fun edit(action:(Form)->Form) {
        if(::name.isInitialized) form=form.copy(name=name.value)
        val changed=action(form)
        val updated=if(form.graph!=null && changed.graph!=null) changed.copy(graph=GraphGroups.synchronize(form.graph!!,changed.graph!!)) else changed
        if(updated==form) return;undo.addLast(editState());redo.clear();form=updated;groupTextEdit=""
        selected=selected.coerceIn(0,sourceParts(form).lastIndex.coerceAtLeast(0));groupIndex=groupIndex.coerceIn(0,form.groups.lastIndex);rebuildWidgets()
    }
    private fun part(action:(Part)->Part)=edit { f -> changeParts(f,sourceParts(f).mapIndexed { i,p -> if(i==selected) action(p) else p }) }
    private fun moveGroup(direction:Int) {
        val ordered=form.groups.sortedBy { it.priority };val index=ordered.indexOfFirst { it.id==activeGroup.id };val other=ordered.getOrNull(index+direction) ?: return
        val current=activeGroup
        edit { it.copy(groups=it.groups.map { g -> when(g.id) { current.id->g.copy(priority=other.priority);other.id->g.copy(priority=current.priority);else->g } }) }
    }
    override fun init() {
        shapeDiagramCache=null
        kilaGraph?.dispose();kilaGraph=null
        printerPanelControls=emptyList();printerPrintTab=null
        shapeUndoButton=null;shapeRedoButton=null
        resetDropdown()
        groupTextEdit=""
        if(printerSession!=null) { initPrinterWorkspace();return }
        if(parent!=null) { initTableWorkspace();return }
        if(!groupMode) graphMode=!(graphInspector && form.graph==null && form.parts.any { it.cutMask.isNotEmpty() || it.recipeSource.isNotEmpty() })
        selected=selected.coerceIn(0,sourceParts(form).lastIndex.coerceAtLeast(0));groupIndex=groupIndex.coerceIn(0,form.groups.lastIndex)
        name=field(8,28,119,form.name);name.setResponder { form=form.copy(name=it) }
        button(132,28,54,"save") { safe { val checked=Geometry.validate(form.copy(name=name.value));form=checked
            ClientState.send(Command("saveForm",Json.encode(if(checked.graph==null) checked else checked.copy(parts=emptyList())),
                surfaces=assignments(),turn=placementTurn));rebuildWidgets() } }
        button(189,28,48,"load") { openSavedCopycat() }
        button(240,28,48,"copy") { edit { it.copy(id=UUID.randomUUID().toString(),version=1,name=it.name+" copy") } }
        shapeUndoButton=button(291,28,48,"undo") { undoEdit() }.apply { active=undo.isNotEmpty() }
        shapeRedoButton=button(342,28,48,"redo") { redoEdit() }.apply { active=redo.isNotEmpty() }
        button(width-252,3,74,"control_panel") { openGraph(true) }.apply { (this as EditorButton).selected=!groupMode && graphInspector }
        button(width-176,3,54,"groups") { groupMode=true;graphMode=false;rebuildWidgets() }.apply { (this as EditorButton).selected=groupMode }
        button(width-120,3,58,"node_graph") { openGraph() }.apply { (this as EditorButton).selected=graphMode && !graphInspector }
        button(width-60,3,54,"close") { onClose() }
        graphCanvas=null
        if(graphMode) {
            val types=GraphModels.shapeOperations
            choice(8,51,192,ui("insert_node"),listOf(ui("node_saved") to { addShapeNode(NodeKind.SAVED) })+BaseShapes.all.map { shape -> ui(shape.name) to { addShapeNode(NodeKind.INPUT,shape.source,shape.discCut) } }+
                types.map { kind -> ui(GraphModels.shape(kind).labelKey) to { addShapeNode(kind) } })
            button(204,51,62,"fit_graph") { kilaGraph?.fit() ?: graphCanvas?.fit() }
            button(270,51,62,"remove") { if(kilaGraph!=null) kilaGraph!!.deleteSelection() else graphCanvas?.deleteSelected() }
            button(336,51,24,Component.literal("?")) {}.setTooltip(Tooltip.create(tr("graph_controls")))
            val side=if(!graphInspector && width>=700) minOf(340,width/3) else width-16
            val left=if(!graphInspector && width>=700) width-side-8 else 8
            val legacy=form.graph==null && form.parts.any { it.cutMask.isNotEmpty() || it.recipeSource.isNotEmpty() }
            graphCanvas=NodeGraphCanvas(left,75,side,height-133,{displayShape()},{assignments()},graphView,::commitShapeGraph,{ message -> ClientState.snapshot=ClientState.snapshot.copy(message=message) },true,onCreateGroup={ value,node,material,slot -> safe { createGroup(value,node,material,slot) } },preview=if(legacy) ({ Result.success(form) }) else null,readOnly=legacy,onSavedSource=::openSavedCopycatSource,onSelectNode={ id -> selectShapeNode(id);kilaGraph?.selectNode(id,true) })
            addRenderableWidget(graphCanvas!!.apply { visible=shapeInspectorVisible })
            if(!graphInspector) kilaGraph=addRenderableWidget(KilaGraphEditor(8,75,if(width>=700) left-12 else width-16,height-133,this,kilaGraphState,::shapeDiagram,::selectShapeNode,::commitShapeDiagram,{ message -> ClientState.snapshot=ClientState.snapshot.copy(message=message) },legacy,insertions=shapeInsertions(),selectSource={ node -> graphCanvas?.openSourceSelection(node) },rotateSource={ node,axis,turns -> graphCanvas?.rotateSource(node,axis,turns) },nodePreview=::shapeThumbnail,previewGroups={ form.groups },previewSurfaces=::assignments,instantiateGroup=::instantiateShapeGroup,dataRevision=::shapeGraphRevision))
            val gw=(width/4).coerceIn(80,180);val ew=146;val sw=(width-gw-ew-32).coerceAtLeast(40)
            choice(8,height-53,gw,activeGroup.name,form.groups.mapIndexed { i,group -> group.name to { groupIndex=i;rebuildWidgets() } }+
                if(form.groups.size<8) listOf(ui("new_group") to { EditorValueInput.text(tr("new_group"),"") { value -> safe { createGroup(value) } } }) else emptyList(),groupIndex)
            materialButton(12+gw,height-53,sw)
            button(16+gw+sw,height-53,ew,"equip") { equip() }
            return
        }
        if(groupMode) groupWidgets() else partWidgets()
        // One live material assignment per group, independent of saved geometry.
        select(274,55,125,groupIndex,form.groups.indices.toList(),{ form.groups[it].name }) { groupIndex=it;rebuildWidgets() }
        select(274,78,125,view,(0..2).toList(),{ listOf(ui("union"),ui("selected_part"),ui("collision"))[it] }) { view=it;rebuildWidgets() }
        materialButton(274,height-75,(width-282).coerceAtLeast(110))
        button(274,height-54,(width-282).coerceAtLeast(110),"equip") { equip() }
    }
    internal fun openSavedCopycatSource(nodeId:String="",x:Int=0,y:Int=0) {
        val current=shapeDisplayGraph().nodes.firstOrNull { it.id==nodeId }?.savedCopycat
        val key=current?.let { saved -> ClientState.snapshot.library.forms.entries.firstOrNull { it.value.id==saved.id && it.value.version==saved.version }?.key }.orEmpty()
        SavedCopycatSelectionScreen.open(minecraft?.screen ?: this,key) { selectedKey -> safe {
            val library=ClientState.snapshot.library
            val saved=library.forms.getValue(selectedKey)
            if(current!=null && current.id==saved.id && current.version==saved.version) return@safe
            val settings=library.printSettings[selectedKey] ?: PrintSettings()
            val inserted=SavedCopycats.insert(form,saved,x,y,nodeId.takeIf(String::isNotEmpty),settings.turn)
            editPrinterSettings {
                form=inserted.form
                printerMaterials.keys.retainAll(form.groups.map { it.id }.toSet())
                inserted.groups.forEach { (source,target) -> printerMaterials[target]=settings.surfaces[source].orEmpty() }
            }
            selectShapeNode(inserted.nodeId)
            kilaGraph?.selectNode(inserted.nodeId,true)
        } }
    }
    private fun openSavedCopycat() {
        val key=ClientState.snapshot.library.forms.entries.firstOrNull { it.value.id==form.id && it.value.version==form.version }?.key.orEmpty()
        SavedCopycatSelectionScreen.open(minecraft?.screen ?: this,key) { selectedKey ->
            ClientState.snapshot.library.forms[selectedKey]?.let { saved ->
                if(printerSession!=null) openPrinterCard(saved,ClientState.snapshot.library.printSettings[selectedKey] ?: PrintSettings())
                else editPrinterSettings {
                    val settings=ClientState.snapshot.library.printSettings[selectedKey] ?: PrintSettings()
                    form=saved;printerMaterials.clear();printerMaterials.putAll(settings.surfaces.filterValues { it.isNotEmpty() });placementTurn=settings.turn
                }
            }
        }
    }
    /** The architect's form draft keeps its own graph navigation and history when changing workspaces. */
    private fun initTableWorkspace() {
        graphMode=true;groupMode=false
        val ids=shapeDisplayGraph().nodes.map { it.id }.toSet()
        kilaGraphState.selection=kilaGraphState.selection.intersect(ids)
        if(!kilaGraphState.selected.startsWith("@group:") || shapeDisplayGraph().nodeGroups.none { "@group:${it.id}"==kilaGraphState.selected }) kilaGraphState.selected=kilaGraphState.selection.singleOrNull().orEmpty()
        graphView.selected=kilaGraphState.selected
        val l=printerLayout
        parent?.createTopTabs(width,height,true)?.forEach { addRenderableWidget(it) }
        addRenderableWidget(PrinterEdgeTab(l.left-17,l.top+12,tr("node_graph"),"G",selected={ false }) { minecraft?.setScreen(parent) })
        addRenderableWidget(PrinterEdgeTab(l.left-17,l.top+36,tr("copycat_graph"),"C",selected={ true }) {})
        shapeUndoButton=button(l.x,l.y,46,"undo") { undoEdit() }.apply { active=undo.isNotEmpty() }
        shapeRedoButton=button(l.x+49,l.y,46,"redo") { redoEdit() }.apply { active=redo.isNotEmpty() }
        button(l.x+98,l.y,54,"fit_graph") { kilaGraph?.fit() }
        name=field(l.x+158,l.y,(l.innerWidth-212).coerceAtLeast(36),form.name).apply {
            setResponder { form=form.copy(name=it) };setTooltip(Tooltip.create(tr("copycat_library_hint")))
        }
        button(l.x+l.innerWidth-50,l.y,50,"save") { safe {
            val checked=Geometry.validate(form);form=checked
            ClientState.send(Command("saveForm",Json.encode(if(checked.graph==null) checked else checked.copy(parts=emptyList())),surfaces=assignments(),turn=placementTurn))
            rebuildWidgets()
        } }
        addRenderableWidget(PrinterEdgeTab(l.left+l.width,l.top+12,tr("printer_inspector"),"I",true,{ graphInspector }) { graphInspector=!graphInspector;rebuildWidgets() })
        addRenderableWidget(PrinterEdgeTab(l.left+l.width,l.top+36,tr("load"),"+",true,{ false }) { openSavedCopycat() })
        addRenderableWidget(PrinterEdgeTab(l.left+l.width,l.top+60,tr("new"),"N",true,{ false }) {
            edit { Form(id=UUID.randomUUID().toString(),name=ui("copycat_graph"),graph=NodeGraph()) }
            kilaGraphState.fitted=false
        })
        addRenderableWidget(PrinterEdgeTab(l.left+l.width,l.top+84,tr("copy"),"D",true,{ false }) { edit { it.copy(id=UUID.randomUUID().toString(),version=1,name=it.name+" copy") } })
        addRenderableWidget(PrinterEdgeTab(l.left+l.width,l.top+108,tr("select_material"),"M",true,{ false }) { openMaterialSelection() })
        addRenderableWidget(PrinterEdgeTab(l.left+l.width,l.top+132,tr("material_preview"),"V",true,{ materialPreview }) { materialPreview=!materialPreview;rebuildWidgets() })
        addRenderableWidget(PrinterEdgeTab(l.left+l.width,l.top+l.height-24,tr("close"),"×",true,{ false }) { onClose() })
        val legacy=form.graph==null && form.parts.any { it.cutMask.isNotEmpty() || it.recipeSource.isNotEmpty() }
        graphCanvas=NodeGraphCanvas(l.panelX,l.graphY,l.panelWidth,l.graphHeight,{displayShape()},{assignments()},graphView,::commitShapeGraph,
            { message -> ClientState.snapshot=ClientState.snapshot.copy(message=message) },true,
            onCreateGroup={ value,node,material,slot -> safe { createGroup(value,node,material,slot) } },
            preview=if(legacy) ({ Result.success(form) }) else null,readOnly=legacy,
            onSavedSource=::openSavedCopycatSource,onSelectNode={ id -> selectShapeNode(id);kilaGraph?.selectNode(id,true) },previewMaterials={ materialPreview })
        addRenderableWidget(graphCanvas!!.apply { visible=shapeInspectorVisible })
        kilaGraph=addRenderableWidget(KilaGraphEditor(l.x,l.graphY,l.graphWidth(shapeInspectorVisible),l.graphHeight,this,kilaGraphState,
            ::shapeDiagram,::selectShapeNode,::commitShapeDiagram,{ message -> ClientState.snapshot=ClientState.snapshot.copy(message=message) },legacy,
            insertions=shapeInsertions(),selectSource={ graphCanvas?.openSourceSelection(it) },rotateSource={ node,axis,turns -> graphCanvas?.rotateSource(node,axis,turns) },selectionChanged=::printerSelectionChanged,
            nodePreview=::shapeThumbnail,previewGroups={ form.groups },previewSurfaces=::assignments,instantiateGroup=::instantiateShapeGroup,previewMaterials={ materialPreview },dataRevision=::shapeGraphRevision))
    }
    private fun initPrinterWorkspace() {
        graphMode=true;groupMode=false;printerInspectorDirty=false
        printerHistoryPanel=null;printerInventoryPanel=null;graphCanvas=null
        observedPrinterLibrary=ClientState.snapshot.library
        val ids=shapeDisplayGraph().nodes.map { it.id }.toSet()
        kilaGraphState.selection=kilaGraphState.selection.intersect(ids)
        if(!kilaGraphState.selected.startsWith("@group:") || shapeDisplayGraph().nodeGroups.none { "@group:${it.id}"==kilaGraphState.selected }) kilaGraphState.selected=kilaGraphState.selection.singleOrNull().orEmpty()
        graphView.selected=kilaGraphState.selected
        groupIndex=groupIndex.coerceIn(0,form.groups.lastIndex)
        if(printerCards.isEmpty()) printerCards+=currentPrinterCard() else printerCards[printerCardIndex]=currentPrinterCard()
        val l=printerLayout
        name=EditorTextField(font,0,0,100,18,Component.empty()).apply { value=form.name }
        val panel=graphInspector || printerPrintPanel
        val toolbarY=l.y
        shapeUndoButton=button(l.x,toolbarY,46,"undo") { undoEdit() }.apply { active=!printerHistory && undo.isNotEmpty() }
        shapeRedoButton=button(l.x+49,toolbarY,46,"redo") { redoEdit() }.apply { active=!printerHistory && redo.isNotEmpty() }
        button(l.x+98,toolbarY,54,"fit_graph") { kilaGraph?.fit() }.active=!printerHistory
        eyeButton=addRenderableWidget(PrinterEyeButton(l.x+l.innerWidth-24,toolbarY))
        addRenderableWidget(PrinterEdgeTab(l.left+l.width,l.top+12,tr("printer_inspector"),"I",true,{ !printerHistory && graphInspector }) {
            val show=printerHistory || !graphInspector
            printerHistory=false;graphInspector=show;printerPrintPanel=false;printerGroups=false;printerPanelScroll=0;rebuildWidgets()
        })
        printerPrintTab=addRenderableWidget(PrinterEdgeTab(l.left+l.width,l.top+36,tr("printer_print"),"P",true,{ printerPrintPanel }) {
            printerPrintPanel=!printerPrintPanel;graphInspector=false;printerGroups=false;printerPanelScroll=0
            if(printerPrintPanel) openPrinterInventory()
            rebuildWidgets()
        }).apply { visible=printerPrintTabVisible }
        val count=printerVisibleCards()
        printerCardFirst=printerCardFirst.coerceIn(0,(printerCards.size-count).coerceAtLeast(0))
        printerCards.indices.drop(printerCardFirst).take(count).forEachIndexed { row,index ->
            val title=if(index==printerCardIndex) form.name else printerCards[index].state.form.name
            addRenderableWidget(PrinterEdgeTab(l.left-17,l.top+12+row*24,Component.literal(title),"${index+1}",selected={ !printerHistory && index==printerCardIndex }) { switchPrinterCard(index) })
        }
        if(printerCards.size>count) {
            addRenderableWidget(PrinterEdgeTab(l.left-17,l.top+l.height-96,tr("printer_previous_cards"),"↑",selected={ false }) { printerCardFirst=(printerCardFirst-1).coerceAtLeast(0);rebuildWidgets() })
            addRenderableWidget(PrinterEdgeTab(l.left-17,l.top+l.height-72,tr("printer_next_cards"),"↓",selected={ false }) { printerCardFirst=(printerCardFirst+1).coerceAtMost(printerCards.size-count);rebuildWidgets() })
        }
        addRenderableWidget(PrinterEdgeTab(l.left-17,l.top+l.height-48,tr("printer_open_card"),"+",selected={ false }) {
            printerSelectionChanged(emptySet());kilaGraphState.selected="";kilaGraphState.selection=emptySet()
            printerHistory=false;graphInspector=true;printerPrintPanel=false;rebuildWidgets()
        })
        addRenderableWidget(PrinterEdgeTab(l.left-17,l.top+l.height-24,tr("printer_history"),"H",selected={ printerHistory }) {
            printerHistory=!printerHistory;rebuildWidgets()
        })
        if(printerHistory) { initPrinterHistory();return }
        val legacy=form.graph==null && form.parts.any { it.cutMask.isNotEmpty() || it.recipeSource.isNotEmpty() }
        graphCanvas=NodeGraphCanvas(l.panelX,l.graphY,l.panelWidth,l.graphHeight,{displayShape()},{assignments()},graphView,::commitShapeGraph,
            { message -> ClientState.snapshot=ClientState.snapshot.copy(message=message) },true,
            onCreateGroup={ value,node,material,slot -> safe { createGroup(value,node,material,slot) } },preview=if(legacy) ({ Result.success(form) }) else null,readOnly=legacy,
            onSavedSource=::openSavedCopycatSource,onSelectNode={ id -> selectShapeNode(id);kilaGraph?.selectNode(id,true) })
        addRenderableWidget(graphCanvas!!.apply { visible=shapeInspectorVisible })
        kilaGraph=addRenderableWidget(KilaGraphEditor(l.x,l.graphY,l.graphWidth(panel),l.graphHeight,this,kilaGraphState,::shapeDiagram,::selectShapeNode,::commitShapeDiagram,
            { message -> ClientState.snapshot=ClientState.snapshot.copy(message=message) },legacy,insertions=shapeInsertions(),
            selectSource={ node -> graphCanvas?.openSourceSelection(node) },rotateSource={ node,axis,turns -> graphCanvas?.rotateSource(node,axis,turns) },selectionChanged=::printerSelectionChanged,
            nodePreview=::shapeThumbnail,previewGroups={ form.groups },previewSurfaces=::assignments,instantiateGroup=::instantiateShapeGroup,dataRevision=::shapeGraphRevision))
        refreshPrinterPanel()
    }
    private fun refreshPrinterPanel() {
        printerPrintTab?.visible=printerPrintTabVisible
        if(focused in printerPanelControls) focused=null
        printerPanelControls.forEach(::removeWidget)
        printerInventoryPanel=null
        printerPanelRows=0
        val before=children().toSet()
        if((graphInspector || printerPrintPanel) && !shapeInspectorVisible) {
            when {
                printerPrintPanel -> printerPrintWidgets()
                printerSelection.size>1 -> printerSelectionWidgets(form.graph==null && form.parts.any { it.cutMask.isNotEmpty() || it.recipeSource.isNotEmpty() })
                printerGroups -> printerGroupWidgets()
                else -> printerCardWidgets()
            }
        }
        printerPanelControls=children().filter { it !in before }
    }
    private fun printerRow(row:Int):Int {
        printerPanelRows=maxOf(printerPanelRows,row+1)
        return printerLayout.graphY+27+(row-printerPanelScroll)*22
    }
    private fun <T:AbstractWidget> printerControl(widget:T,row:Int):T=widget.apply {
        visible=y>=printerLayout.graphY+26 && y+height<=printerLayout.bottom-4
        printerPanelRows=maxOf(printerPanelRows,row+1)
    }
    private fun printerPanelButton(row:Int,key:String,action:()->Unit)=printerControl(button(printerLayout.panelX+10,printerRow(row),printerLayout.panelWidth-20,key,action),row)
    private fun printerCardWidgets() {
        val l=printerLayout;val x=l.panelX+10;val w=l.panelWidth-20;val half=(w-4)/2
        name=printerControl(field(x,printerRow(0),w,form.name),0).apply { setResponder { form=form.copy(name=it) } }
        printerControl(button(x,printerRow(1),half,"save") { savePrinterCard() },1)
        printerControl(button(x+half+4,printerRow(1),w-half-4,"load") { openSavedCopycat() },1)
        printerControl(button(x,printerRow(2),half,"printer_new_card") { openPrinterCard(Form(id=UUID.randomUUID().toString(),name=ui("printer_new_card"),graph=NodeGraph())) },2)
        printerControl(button(x+half+4,printerRow(2),w-half-4,"copy") { openPrinterCard(form.copy(id=UUID.randomUUID().toString(),version=1,name=form.name+" copy"),PrintSettings(assignments(),placementTurn)) },2)
        printerPanelButton(3,"printer_close_card") { closePrinterCard() }
        printerPanelButton(4,"groups") { printerGroups=true;printerPanelScroll=0;rebuildWidgets() }
        printerPanelButton(5,"close") { onClose() }
    }
    private fun savePrinterCard()=safe {
        val checked=Geometry.validate(form);form=checked
        ClientState.send(Command("saveForm",Json.encode(if(checked.graph==null) checked else checked.copy(parts=emptyList())),key=printerSession!!.session,surfaces=assignments(),turn=placementTurn))
        rebuildWidgets()
    }
    private fun printerPrintWidgets() {
        val l=printerLayout;val x=l.panelX+10;val w=l.panelWidth-20
        printerControl(select(x,printerRow(0),w,groupIndex,form.groups.indices.toList(),{ form.groups[it].name }) { groupIndex=it;rebuildWidgets() },0)
        printerControl(materialButton(x,printerRow(1),w),1)
        printerControl(select(x,printerRow(2),w,placementTurn,(0..3).toList(),{ ui("rotation_angle",it*90) }) { value -> editPrinterSettings { placementTurn=value } },2)
        printerPanelButton(3,"printer_print") { ClientState.send(printerCommand("printerPrint")) }.active=canPrint()
        val inventory=PrinterCartridgePanel(x,printerRow(4),w,l.graphY+26,l.bottom-4,::printerMenu,{ ClientState.snapshot.printer }) { slot,button,type ->
            val menu=printerMenu() ?: return@PrinterCartridgePanel
            minecraft?.player?.let { player -> minecraft?.gameMode?.handleInventoryMouseClick(menu.containerId,slot,button,type,player) }
        }
        printerPanelRows=maxOf(printerPanelRows,4+(inventory.height+21)/22)
        printerInventoryPanel=addRenderableWidget(inventory)
    }
    private fun initPrinterHistory() {
        val l=printerLayout
        val values=ClientState.snapshot.library.history
        if(values.none { it.id==printerHistoryState.selected }) printerHistoryState.selected=values.firstOrNull()?.id.orEmpty()
        val history=PrinterHistoryPanel(l.x,l.graphY,l.innerWidth,l.graphHeight-24,printerHistoryState,{ ClientState.snapshot.library.history }) { rebuildWidgets() }
        printerHistoryPanel=addRenderableWidget(history)
        val half=(l.innerWidth-4)/2
        button(l.x,l.bottom-20,half,"printer_history_open") {
            history.selection()?.let { openPrinterCard(it.form,PrintSettings(it.surfaces,it.turn),newCard=true) }
        }.active=history.selection()!=null
        button(l.x+half+4,l.bottom-20,l.innerWidth-half-4,"ps_save_named") {
            history.selection()?.let { entry -> EditorValueInput.text(tr("ps_save_named"),entry.form.name,64) { name -> ClientState.send(Command("historySave",key=entry.id,payload=name)) } }
        }.active=history.selection()!=null
    }
    private fun printerSelectionWidgets(readOnly:Boolean) {
        printerPanelButton(0,"duplicate") { safe {
            val (graph,selection)=GraphSelectionEdits.duplicate(shapeDisplayGraph(),printerSelection)
            applyPrinterSelectionEdit(graph,selection)
        } }.active=!readOnly && shapeDisplayGraph().nodes.any { it.id in printerSelection && it.kind!=NodeKind.OUTPUT }
        printerPanelButton(1,"remove") { safe { applyPrinterSelectionEdit(GraphSelectionEdits.remove(shapeDisplayGraph(),printerSelection),emptySet()) } }.active=!readOnly
        val l=printerLayout
        printerControl(choice(l.panelX+10,printerRow(2),l.panelWidth-20,ui("assign_group"),form.groups.map { group -> group.name to {
            safe { applyPrinterSelectionEdit(GraphSelectionEdits.assignGroup(shapeDisplayGraph(),printerSelection,group.id),printerSelection) }
        } }),2).active=!readOnly && shapeDisplayGraph().nodes.any { it.id in printerSelection && it.kind in setOf(NodeKind.INPUT,NodeKind.SAVED,NodeKind.UNION,NodeKind.SYMMETRIC_DIFFERENCE) }
        printerPanelButton(3,"printer_align_horizontal") { safe { applyPrinterSelectionEdit(GraphSelectionEdits.align(shapeDisplayGraph(),printerSelection,true),printerSelection) } }.active=!readOnly
        printerPanelButton(4,"printer_align_vertical") { safe { applyPrinterSelectionEdit(GraphSelectionEdits.align(shapeDisplayGraph(),printerSelection,false),printerSelection) } }.active=!readOnly
    }
    private fun applyPrinterSelectionEdit(graph:NodeGraph,selection:Set<String>) {
        GraphCompiler.structure(graph,form.groups)
        kilaGraphState.selection=selection;kilaGraphState.selected=selection.firstOrNull().orEmpty();graphView.selected=selection.singleOrNull().orEmpty()
        commitShapeGraph(graph)
    }
    private fun printerGroupWidgets() {
        val l=printerLayout;val x=l.panelX+10;val w=l.panelWidth-20;val half=(w-4)/2;val id=activeGroup.id
        printerControl(select(x,printerRow(0),w,groupIndex,form.groups.indices.toList(),{ form.groups[it].name }) { groupIndex=it;rebuildWidgets() },0)
        printerControl(field(x,printerRow(1),w,activeGroup.name),1).apply { setMaxLength(32);setResponder { editGroupText(id,"name",it) } }
        printerControl(field(x,printerRow(2),w,activeGroup.role),2).apply { setMaxLength(32);setTooltip(Tooltip.create(tr("role_identifier")));setResponder { editGroupText(id,"role",it) } }
        printerControl(button(x,printerRow(3),half,"forward") { moveGroup(1) },3)
        printerControl(button(x+half+4,printerRow(3),w-half-4,"backward") { moveGroup(-1) },3)
        printerControl(materialButton(x,printerRow(4),w),4)
        printerPanelButton(5,"new_group") { EditorValueInput.text(tr("new_group"),"",32) { value -> safe { createGroup(value) } } }.active=form.groups.size<8
        printerPanelButton(6,"delete_group") { deleteShapeGroup(id) }.active=form.groups.size>1
        printerPanelButton(7,"printer_card") { printerGroups=false;printerPanelScroll=0;rebuildWidgets() }
    }
    private fun deleteShapeGroup(id:String)=edit { f ->
        val target=f.groups.first { it.id!=id }.id
        val changed=changeParts(f,sourceParts(f).map { if(it.group==id) it.copy(group=target) else it })
        changed.copy(groups=f.groups.filter { it.id!=id },graph=changed.graph?.let { graph -> graph.copy(nodes=graph.nodes.map { n ->
            n.copy(inputGroups=n.inputGroups.map { if(it==id) target else it },materialGroup=if(n.materialGroup==id) target else n.materialGroup,previewGroup=if(n.previewGroup==id) "" else n.previewGroup)
        }) })
    }
    override fun renderChrome(g:GuiGraphics) {
        if(printerSession!=null || parent!=null) printerLayout.render(g,WorldPeek.active) else super.renderChrome(g)
    }
    private fun renderPrinterWorkspace(g:GuiGraphics) {
        val l=printerLayout
        if(!printerHistory && !WorldPeek.active && (graphInspector || printerPrintPanel) && !shapeInspectorVisible) {
            ArchitectTheme.inset(g,l.panelX,l.graphY,l.panelWidth,l.graphHeight)
            val heading=when { printerPrintPanel->ui("printer_print");printerSelection.size>1->ui("printer_multi_selection",printerSelection.size);printerGroups->ui("groups");else->ui("printer_card") }
            label(g,font.plainSubstrByWidth(heading,l.panelWidth-20),l.panelX+10,l.graphY+9,ArchitectTheme.brass)
            if(printerPanelRows*22>l.graphHeight-30) label(g,"↕",l.panelX+l.panelWidth-12,l.bottom-12,ArchitectTheme.muted)
        }
    }
    private fun renderPrinterContents(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        val l=printerLayout
        label(g,font.plainSubstrByWidth("${form.name} · v${form.version}",(l.innerWidth-220).coerceAtLeast(0)),l.x+158,l.y+6,ArchitectTheme.muted)
        val cost=graphZincText();label(g,cost,l.x,l.bottom+4,ArchitectTheme.brass)
        val available=l.innerWidth-font.width(cost)-8
        val hint=when { printerHistory->ui("printer_history_hint");printerPrintPanel->ui("ps_choose_inventory_compact");else->ui("printer_workspace_hint") }
        label(g,font.plainSubstrByWidth(ClientState.snapshot.message.ifBlank { hint },available.coerceAtLeast(0)),l.x+font.width(cost)+8,l.bottom+4,ArchitectTheme.muted)
        kilaGraph?.renderPopupOverlay(g,mx,my,delta)
        printerInventoryPanel?.renderTooltip(g,mx,my)
        printerMenu()?.carried?.takeIf { !it.isEmpty }?.let { carried ->
            g.pose().pushPose();g.pose().translate(0f,0f,300f)
            g.renderItem(carried,mx-8,my-8);g.renderItemDecorations(font,carried,mx-8,my-8);g.pose().popPose()
        }
    }
    private fun partWidgets() {
        Adapters.all.values.forEachIndexed { i,a -> button(8,55+i*20,120,tr(a.name)) { if(sourceParts(form).size<32) edit { selected=sourceParts(it).size;changeParts(it,sourceParts(it)+Part(a.source,group=activeGroup.id)) } } }
        val first=(selected/4)*4
        for(i in first until minOf(first+4,sourceParts(form).size)) {
            val p=sourceParts(form)[i];button(136,55+(i-first)*20,130,Component.literal("${if(i==selected) ">" else " "}${i+1}: ").append(tr(BaseShapes.find(p.source,p.discCut).name))) { selected=i;groupIndex=form.groups.indexOfFirst { it.id==sourceParts(form)[i].group }.coerceAtLeast(0);rebuildWidgets() }
        }
        button(136,137,32,Component.literal("<")) { selected=(selected-1).mod(sourceParts(form).size.coerceAtLeast(1));rebuildWidgets() }
        button(171,137,32,Component.literal(">")) { selected=(selected+1).mod(sourceParts(form).size.coerceAtLeast(1));rebuildWidgets() }
        button(206,137,60,"remove") { if(sourceParts(form).size>1) edit { changeParts(it,sourceParts(it).filterIndexed { i,_ -> i!=selected }) } }
        button(136,158,62,"duplicate") { if(sourceParts(form).isNotEmpty() && sourceParts(form).size<32) edit { val duplicate=sourceParts(it)[selected];val next=changeParts(it,sourceParts(it)+duplicate);selected=sourceParts(it).size;next } }
        button(201,158,65,"rotate") { part { it.copy(turn=(it.turn+1)%4) } }
        val p=sourceParts(form).getOrNull(selected) ?: return;val a=Adapters.get(p.source)
        cycle(136,179,62,(if(p.top) ui("top") else ui("bottom"))+if(p.tilt>0) " ${p.tilt*90}°" else "") { part { it.copy(top=!it.top,tilt=if(it.top && Adapters.get(it.source).tiltable) (it.tilt+1)%4 else it.tilt) } }
        cycle(201,179,65,ui("size",p.size)) { part { it.copy(size=it.size%a.max+1) } }

    }
    private fun groupWidgets() {
        val first=(groupIndex/4)*4
        for(i in first until minOf(first+4,form.groups.size)) {
            val group=form.groups[i]
            cycle(8,55+(i-first)*20,120,"${group.priority}: ${group.name}") { groupIndex=i;rebuildWidgets() }
        }
        button(8,141,58,Component.literal("<")) { groupIndex=(groupIndex-1).mod(form.groups.size);rebuildWidgets() }
        button(70,141,58,Component.literal(">")) { groupIndex=(groupIndex+1).mod(form.groups.size);rebuildWidgets() }
        button(8,163,120,"new_group") {
            EditorValueInput.text(tr("new_group"),"",32) { value -> safe { createGroup(value) } }
        }.active=form.groups.size<8
        val id=activeGroup.id
        val groupName=field(136,55,130,activeGroup.name);groupName.setMaxLength(32)
        groupName.setResponder { value -> editGroupText(id,"name",value) }
        field(136,78,130,activeGroup.role).apply { setMaxLength(32);setTooltip(Tooltip.create(tr("role_identifier")));setResponder { value -> editGroupText(id,"role",value) } }
        button(136,101,62,"forward") { moveGroup(1) };button(201,101,65,"backward") { moveGroup(-1) }
        if(form.graph==null) {
            select(136,124,130,selected,sourceParts(form).indices.toList(),{ ui("part_number",it+1) }) { selected=it;rebuildWidgets() }
            button(136,147,130,"assign_group") { part { it.copy(group=id) } }
        }
        button(136,170,130,"delete_group") { if(form.groups.size>1) edit { f -> val target=f.groups.first { it.id!=id }.id;val changed=changeParts(f,sourceParts(f).map { if(it.group==id) it.copy(group=target) else it });changed.copy(groups=f.groups.filter { it.id!=id },graph=changed.graph?.let { graph -> graph.copy(nodes=graph.nodes.map { n -> n.copy(inputGroups=n.inputGroups.map { if(it==id) target else it },materialGroup=if(n.materialGroup==id) target else n.materialGroup,previewGroup=if(n.previewGroup==id) "" else n.previewGroup) }) }) } }
    }
    override fun renderWorkspace(g:GuiGraphics) {
        if(printerSession!=null) { renderPrinterWorkspace(g);return }
        if(parent!=null) return
        if(WorldPeek.active) return
        ArchitectTheme.bevel(g,4,26,width-8,22,ArchitectTheme.panel)
        if(width>=700) label(g,ui("ui_document"),402,33,ArchitectTheme.muted)
        if(graphMode) {
            ArchitectTheme.bevel(g,4,49,width-8,22,ArchitectTheme.panel)
            ArchitectTheme.bevel(g,4,height-56,width-8,23,ArchitectTheme.panel)
        } else {
            ArchitectTheme.panel(g,4,50,128,(height-84).coerceAtLeast(150))
            ArchitectTheme.panel(g,132,50,138,(height-84).coerceAtLeast(150))
            ArchitectTheme.panel(g,274,50,width-278,height-84)
            ArchitectTheme.viewport(g,278,101,width-286,(height-181).coerceAtLeast(24))
        }
        g.fill(8,height-29,width-8,height-18,ArchitectTheme.well)
    }
    override fun renderContents(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        if(printerSession!=null) { renderPrinterContents(g,mx,my,delta);return }
        if(parent!=null) {
            val l=printerLayout
            label(g,font.plainSubstrByWidth(graphZincText()+" · "+ClientState.snapshot.message.ifBlank { ui("copycat_library_hint") },l.innerWidth),l.x,l.bottom+4,ArchitectTheme.muted)
            kilaGraph?.renderPopupOverlay(g,mx,my,delta)
            return
        }
        kilaGraph?.syncInspectorSelection(graphView.selected)
        if(WorldPeek.active && !graphMode) return
        if(graphMode) {
            graphSummary(g,graphZincText(),ui(if(graphInspector) "node_control_hint" else "graph_help"))
            kilaGraph?.renderPopupOverlay(g,mx,my,delta)
            return
        }
        safe {
            val compiled=preview().getOrThrow()
            val parts=Geometry.rotate(if(view==1) listOfNotNull(sourceParts(form).getOrNull(selected)) else compiled.parts,placementTurn)
            val surfaces=if(view==2) form.groups.associate { it.id to "minecraft:glass" } else assignments()
            val previewHeight=(height-181).coerceAtLeast(24)
            g.enableScissor(279,102,width-9,101+previewHeight-1)
            try {
                Meshes.preview(g,(width+274)/2,101+previewHeight/2,minOf((width-290)*0.3f,previewHeight*0.36f),yaw,parts,if(view==2) "minecraft:glass" else Economy.palette[material],groups=form.groups,surfaces=surfaces)
            } finally { g.disableScissor() }
            val count=Placement(parts=compiled.parts,groups=form.groups,surfaces=assignments()).surfaceItems().size
            label(g,ui("group_cost",Extrusion.cost(compiled.parts,form.groups),count),8,height-26)
        }
        if(groupMode) g.drawWordWrap(font,tr("foreground_priority",activeGroup.priority),8,190,118,ArchitectTheme.muted)
        else sourceParts(form).getOrNull(selected)?.let { p -> label(g,ui("part_group",form.groups.first { it.id==p.group }.name).take(21),136,201) }
    }
    override fun mouseClicked(x:Double,y:Double,button:Int):Boolean {
        if(dropdownClick(x,y,button)) return true
        if(graphCanvas?.visible==true && graphCanvas?.hasOpenMenu()==true) return graphCanvas!!.mouseClicked(x,y,button)
        if(kilaGraph?.hasOpenMenu()==true) { val handled=kilaGraph!!.mouseClicked(x,y,button);focused=kilaGraph;return handled }
        if(printerInventoryPanel?.mouseClicked(x,y,button)==true) { focused=printerInventoryPanel;return true }
        if(printerHistoryPanel?.mouseClicked(x,y,button)==true) { focused=printerHistoryPanel;return true }
        if(kilaGraph?.mouseClicked(x,y,button)==true) { focused=kilaGraph;return true }
        if(graphMode && shapeInspectorVisible && graphCanvas?.mouseClicked(x,y,button)==true) { focused=graphCanvas;return true }
        return super.mouseClicked(x,y,button)
    }
    override fun mouseDragged(x:Double,y:Double,button:Int,dx:Double,dy:Double):Boolean {
        if(printerInventoryPanel?.dragging==true) return printerInventoryPanel!!.mouseDragged(x,y,button,dx,dy)
        if(focused===kilaGraph && kilaGraph!=null) return kilaGraph!!.mouseDragged(x,y,button,dx,dy)
        if(graphMode && shapeInspectorVisible && focused===graphCanvas) return graphCanvas?.mouseDragged(x,y,button,dx,dy) ?: false
        return super.mouseDragged(x,y,button,dx,dy)
    }
    override fun mouseReleased(x:Double,y:Double,button:Int):Boolean {
        if(printerInventoryPanel?.dragging==true) return printerInventoryPanel!!.mouseReleased(x,y,button)
        if(focused===kilaGraph && kilaGraph!=null) { val handled=kilaGraph!!.mouseReleased(x,y,button);refreshPrinterInspector();return handled }
        if(graphMode && shapeInspectorVisible && focused===graphCanvas) { val handled=graphCanvas?.mouseReleased(x,y,button) ?: false;refreshPrinterInspector();return handled }
        return super.mouseReleased(x,y,button)
    }
    override fun mouseScrolled(x:Double,y:Double,dx:Double,dy:Double):Boolean {
        if(printerHistoryPanel?.mouseScrolled(x,y,dx,dy)==true) return true
        if(graphCanvas?.visible==true && graphCanvas?.hasOpenMenu()==true) { graphCanvas!!.mouseScrolled(x,y,dx,dy);return true }
        if(kilaGraph?.hasOpenMenu()==true) { kilaGraph!!.mouseScrolled(x,y,dx,dy);return true }
        if(printerSession!=null && (graphInspector || printerPrintPanel) && !shapeInspectorVisible && x>=printerLayout.panelX && x<printerLayout.x+printerLayout.innerWidth && y>=printerLayout.graphY && y<printerLayout.bottom) {
            val visible=((printerLayout.graphHeight-28)/22).coerceAtLeast(1)
            printerPanelScroll=(printerPanelScroll-dy.toInt()).coerceIn(0,(printerPanelRows-visible).coerceAtLeast(0));refreshPrinterPanel();return true
        }
        return super.mouseScrolled(x,y,dx,dy)
    }
    override fun keyPressed(key:Int,scan:Int,mods:Int):Boolean {
        if(dropdownKey(key)) return true
        if(printerHistory) return super.keyPressed(key,scan,mods)
        if(hasControlDown() && focused !is EditBox && key==90) { if(hasShiftDown()) redoEdit() else undoEdit();return true }
        if(hasControlDown() && focused !is EditBox && key==89) { redoEdit();return true }
        if(focused===kilaGraph && kilaGraph?.keyPressed(key,scan,mods)==true) { refreshPrinterInspector();return true }
        if(graphMode && shapeInspectorVisible && focused !is EditBox && graphCanvas?.keyPressed(key,scan,mods)==true) return true
        if(key==82 && !name.isFocused && !hasControlDown() && focused !is EditBox) {
            if(printerSession!=null) editPrinterSettings { placementTurn=(placementTurn+1)%4 } else placementTurn=(placementTurn+1)%4
            return true
        }
        if(hasControlDown() && key==67 && focused !is EditBox) { minecraft?.keyboardHandler?.clipboard=Json.encode(form.copy(name=name.value,parts=if(form.graph!=null) emptyList() else form.parts));return true }
        if(hasControlDown() && key==86 && focused !is EditBox) { safe { val text=minecraft?.keyboardHandler?.clipboard.orEmpty();require(text.length<=28000);edit { Geometry.validate(Json.decode<Form>(text)).copy(id=UUID.randomUUID().toString(),version=1) } };return true }
        return super.keyPressed(key,scan,mods)
    }
    override fun onClose() {
        if(printerSession!=null && printerMenu()!=null) minecraft?.player?.closeContainer()
        if(printerSession!=null) ClientState.send(Command("printerCancel",key=printerSession.session))
        WorldPeek.active=false;minecraft?.setScreen(parent)
    }
    override fun removed() { kilaGraph?.dispose();shapeThumbnailJobs.cancelPending();shapeCostJob.cancelPending();super.removed() }
    private fun equip() {
        val saved=ClientState.snapshot.library.forms.entries.filter { it.value.id==form.id }.maxByOrNull { it.value.version }
        if(saved!=null) {
            val palettes=saved.value.groups.associate { it.id to (assignments()[it.id] ?: Economy.palette[material]) }
            ClientState.send(Command("select",key=saved.key,material=Economy.palette[material],turn=placementTurn,surfaces=palettes))
        } else ClientState.snapshot=ClientState.snapshot.copy(message=ui("save_before_equip"))
    }
    private fun undoEdit() { if(undo.isNotEmpty()) { redo.addLast(editState());restore(undo.removeLast());rebuildWidgets() } }
    private fun redoEdit() { if(redo.isNotEmpty()) { undo.addLast(editState());restore(redo.removeLast());rebuildWidgets() } }
}
class BuildingScreen:EditorScreen(tr("building_editor")) {
    var graphMode=true
    var graphInspector=false
    val buildingGraphView=BuildingGraphView()
    var buildingGraphCanvas:BuildingGraphCanvas?=null;private set
    val floorPlanView=FloorPlanView()
    var floorPlanCanvas:FloorPlanCanvas?=null;private set
    var buildingPanelSection=BuildingPanelSection.BUILDING;private set
    var buildingDefinitionList:BuildingDefinitionList?=null;private set
    private val panelSelections=mutableMapOf<BuildingPanelSection,String>()
    private val panelScroll=mutableMapOf<BuildingPanelSection,Double>()
    private var storeyProperties=false
    val kilaBuildingGraphState=KilaGraphState()
    var kilaBuildingGraph:KilaGraphEditor?=null;private set
    private val buildingNodePreviews=BuildingNodePreviews({displayBuilding()},{ClientState.snapshot.library})
    private var copycatWorkspace:ShapeScreen?=null
    fun openCopycatGraph() {
        val screen=copycatWorkspace ?: ShapeScreen(this).also { copycatWorkspace=it }
        minecraft?.setScreen(screen)
    }
    private val buildingInspectorVisible get()=graphInspector
    private val workspace get()=PrinterWorkspaceLayout(width,height,topTabs=true)
    private val classicWidth get()=workspace.innerWidth+16
    private val classicHeight get()=workspace.graphHeight+110
    private val classicOffsetX get()=workspace.x-8
    private val classicOffsetY get()=workspace.graphY-74
    private val buildingUndo=ArrayDeque<Building>();private val buildingRedo=ArrayDeque<Building>()
    private var buildingProjectionKey:Building?=null
    private var buildingProjection:BuildingGraph?=null
    private var buildingDiagramCache:Pair<BuildingGraph,KilaGraphData>?=null
    private var lastClassicTab=0
    private var presentationInitialized=false
    private var fitBuildingGraph=true
    private var buildingUndoButton:Button?=null
    private var buildingRedoButton:Button?=null
    fun createTopTabs(screenWidth:Int,screenHeight:Int,fromCopycat:Boolean=false):List<ArchitectTopTab> {
        val l=PrinterWorkspaceLayout(screenWidth,screenHeight,topTabs=true)
        return listOf(ArchitectTopTab(l.x,l.top-17,tr("graph_overview"),"G",!fromCopycat) {
            openBuildingGraph()
            if(fromCopycat) minecraft?.setScreen(this)
        })
    }
    fun buildingDisplayGraph():BuildingGraph {
        building.graph?.let { return it }
        if(buildingProjectionKey!=building) { buildingProjectionKey=building;buildingProjection=GraphPresentation.building(building) }
        return buildingProjection!!
    }
    private fun editRoomSegmentLayout(id:String) {
        safe {
            val graph=buildingDisplayGraph()
            val layout=graph.nodes.first { it.id==id && it.kind==BuildingKind.ROOM_LAYOUT }
            val evaluation=BuildingGraphCompiler.Evaluation(graph,building.palette,lib.forms,lib.modules)
            RoomSegmentLayoutScreen.open(layout,graph,evaluation) { changed -> commitBuildingGraph(graph.replace(changed)) }
        }
    }
    fun displayBuilding():Building=if(building.graph!=null) building else building.copy(graph=buildingDisplayGraph())
    fun selectBuildingNode(id:String) {
        if(building.graph==null) { id.removePrefix("classic_").toIntOrNull()?.let { lastClassicTab=if(it==6) 0 else it };return }
        buildingGraphView.selected=id;buildingGraphView.inspectorScroll=0.0
    }
    private fun buildingDiagram():KilaGraphData {
        if(building.graph==null) {
            val labels=listOf(ui("footprint")+" · "+building.wings.joinToString(" + ") { "${it.width}×${it.length}" },
                ui("modules"),ui("walls"),ui("plates")+" · ${building.floorThickness}",ui("roof")+" · "+ui(building.roof),ui("palette"),ui("floors",building.floors))
            val nodes=labels.mapIndexed { i,title -> KilaNode("classic_$i",title,20+(i/4)*230,20+(i%4)*100,emptyList(),true,false) }+
                KilaNode("classic_result",building.name,520,140,labels.indices.map { labels[it] },false,false,previewExpandedByDefault=true)
            return KilaGraphData(nodes,labels.indices.map { KilaLink("classic_$it","classic_result",it) })
        }
        val graph=buildingDisplayGraph()
        buildingDiagramCache?.let { (key,value) ->
            if(key.nodes===graph.nodes && key.nodeGroups===graph.nodeGroups && key.schema==graph.schema) return value
        }
        val ports=graph.nodes.associate { it.id to BuildingPorts.of(it,graph,true) }
        return KilaGraphData(graph.nodes.mapIndexed { index,n ->
            KilaNode(n.id,"${index+1} · ${if(n.id=="empty_wall") ui("empty_wall") else if(n.kind==BuildingKind.BUILDING) ui("bg_table_building") else ui(GraphModels.building(n.kind).labelKey)}",n.x,n.y,ports.getValue(n.id).map { p -> buildingPortLabel(p,n) },n.kind!=BuildingKind.BUILDING,n.kind!=BuildingKind.BUILDING && n.id!="empty_wall",inputTypes=ports.getValue(n.id).map { it.accepts.map(::buildingDiagramType).distinct().joinToString("|") },outputType=buildingDiagramType(n.kind),accent=when(n.kind) {
                BuildingKind.SEGMENT,BuildingKind.PATTERN,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_ROTATE,BuildingKind.FLOOR,BuildingKind.CEILING,BuildingKind.SEGMENT_LAYOUT -> 0xffff8a27.toInt()
                BuildingKind.ROOM,BuildingKind.GARDEN -> 0xff21b15b.toInt()
                BuildingKind.PATH -> 0xffc8a46a.toInt()
                BuildingKind.AREA,BuildingKind.ROOM_GEOMETRY,BuildingKind.ROOM_LAYOUT -> 0xff74b4cf.toInt()
                BuildingKind.ROOF_LAYOUT,BuildingKind.ROOF -> 0xffb77865.toInt()
                BuildingKind.STOREY -> 0xffeeeeee.toInt()
                else -> null
            },previewExpandedByDefault=n.kind==BuildingKind.BUILDING)
        },graph.nodes.flatMap { n -> ports.getValue(n.id).mapIndexedNotNull { i,p -> p.source.takeIf { it.isNotEmpty() }?.let { KilaLink(it,n.id,i) } } },graph.nodeGroups)
            .also { buildingDiagramCache=graph to it }
    }
    private fun buildingDiagramType(kind:BuildingKind)=when(kind) {
        BuildingKind.SEGMENT,BuildingKind.PATTERN,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_ROTATE->"WALL"
        BuildingKind.COPYCAT_MIRROR,BuildingKind.COPYCAT_ROTATE->"COPYCAT"
        else->kind.name
    }
    private val explicitAssembly get()=buildingDisplayGraph().nodes.any { it.assembly || it.kind in setOf(BuildingKind.SEGMENT_LAYOUT,BuildingKind.ROOM_LAYOUT,BuildingKind.ROOM_GEOMETRY,BuildingKind.ROOF_LAYOUT) }
    private fun buildingInsertions():List<KilaInsertion> = BuildingKind.entries.filter { GraphModels.buildingInsertable(it,explicitAssembly) }.map { kind ->
        KilaInsertion(ui(GraphModels.building(kind).labelKey),ui(BuildingInsertionCategories.key(kind))) { x,y -> buildingGraphCanvas?.addAt(kind,x.toDouble(),y.toDouble()) }
    }
    private fun addBuildingNode(kind:BuildingKind) {
        val point=kilaBuildingGraph?.insertionPoint()
        if(point==null) buildingGraphCanvas?.add(kind) else buildingGraphCanvas?.addAt(kind,point.first.toDouble(),point.second.toDouble())
    }
    private fun buildingPortLabel(p:BuildingPort,owner:BuildingNode):String=when {
        p.key.startsWith("semantic:") -> SemanticPropertyEditors.portLabel(p.key)
        p.key.startsWith("wall:") -> if(p.length>0) ui("bg_wall_port",ui(listOf("north","east","south","west")[p.key.removePrefix("wall:").substringBefore(':').toInt()]),p.length,p.height) else ui("bg_roof_wall_port",ui(listOf("north","east","south","west")[p.key.removePrefix("wall:").toInt()]))
        p.key.startsWith("access:") -> ui("structure_port",p.key.substringAfter(':').toInt()+1)
        p.key=="area" -> ui("roof_area")
        p.key=="tread" -> ui("stairs_tread")
        p.key=="landing" -> ui("stairs_landing")
        p.key=="enclosure" -> ui("structure_enclosure")
        BuildingKind.ROOF_LEVEL in p.accepts -> ui("roof_level_index",p.number+1)
        owner.kind==BuildingKind.ROOF && owner.assembly && p.key.startsWith("in:") && p.number<6 -> ui("roof_variant_"+listOf("slope","eave","edge","ridge","hip","valley")[p.number])
        p.key.startsWith("level:") -> if(p.number==0) ui("bg_ground") else ui(if(p.number>0) "bg_upper" else "bg_lower",kotlin.math.abs(p.number))
        p.key in listOf("floor","ceiling","roof") -> ui("bg_"+p.key)
        p.key=="corner" -> ui("bg_corner_copycat")
        p.key.startsWith("edge:") -> ui("bg_vertical_edge_port",p.key.removePrefix("edge:").substringBefore(':'),p.key.substringAfterLast(':'),p.height)
        p.key=="geometry" -> ui(if(BuildingKind.AREA in p.accepts) "bg_area" else "bg_room_geometry")
        p.key.startsWith("region:") -> segmentRegionLabel(p.key)
        p.key=="layout" -> ui(when { BuildingKind.ROOF_LAYOUT in p.accepts -> "bg_roof_layout";BuildingKind.ROOM_LAYOUT in p.accepts -> "bg_room_layout";else -> "bg_segment_layout" })
        else -> ui(if(p.accepts.any { it in LandscapeGeometry.kinds }) "bg_landscape_index" else if(BuildingKind.COPYCAT in p.accepts) "bg_copycat_index" else if(BuildingKind.ROOM in p.accepts) "bg_room_index" else "bg_segment_index",p.number+1)
    }
    private fun commitBuildingDiagram(data:KilaGraphData) {
        val original=buildingDisplayGraph()
        val diagram=buildingDiagram()
        if(building.graph!=null && data.isLayoutEditOf(diagram)) {
            val moved=original.copy(nodes=original.nodes.zip(data.nodes) { node,position -> node.copy(x=position.x,y=position.y) })
            if(moved!=original) {
                val next=building.copy(graph=moved)
                BuildingSourceEditors.validateBuilding(next)
                buildingUndo.addLast(building);if(buildingUndo.size>64) buildingUndo.removeFirst();buildingRedo.clear()
                building=next
                buildingDiagramCache=moved to diagram.withPositionsFrom(data)
                buildingUndoButton?.active=true
                buildingRedoButton?.active=false
            }
            return
        }
        val graph=original.copy(nodeGroups=data.nodeGroups.map { group -> original.nodeGroups.firstOrNull { it.id==group.id }?.let { group.copy(nodes=it.nodes) } ?: group })
        val ports=graph.nodes.associate { it.id to BuildingPorts.of(it,graph,true) }
        val wires=data.links.map { link -> DiagramConnection(link.source,link.target,ports.getValue(link.target).getOrNull(link.slot)?.key ?: error("Unknown building input")) }
        commitBuildingGraph(DiagramEdits.building(graph,data.nodes.map { DiagramPosition(it.id,it.x,it.y) },wires))
    }
    private fun instantiateBuildingGroup(id:String) { safe { commitBuildingGraph(GraphGroups.instantiate(buildingDisplayGraph(),id)) } }
    fun openBuildingGraph(inspector:Boolean=false) {
        graphInspector=inspector;buildingGraphView.wire=null
        if(inspector) buildingGraphView.inspectorNode=""
        if(inspector && building.graph==null) { graphMode=false;tab=lastClassicTab }
        else {
            if(!graphMode && tab !in listOf(1,5,6)) lastClassicTab=tab
            graphMode=true;tab=if(inspector) 7 else 0
            if(inspector) buildingDisplayGraph().nodes.firstOrNull { it.id==buildingGraphView.selected }?.let { buildingPanelSection=BuildingPanelSection.of(it.kind) }
        }
        presentationInitialized=true;rebuildWidgets()
    }
    fun openBuildingSection(section:BuildingPanelSection) {
        if(building.graph==null) return
        val current=buildingDisplayGraph().nodes.firstOrNull { it.id==buildingGraphView.selected }
        if(current!=null) panelSelections[BuildingPanelSection.of(current.kind)]=current.id
        buildingPanelSection=section
        val nodes=buildingDisplayGraph().nodes.filter { it.kind in section.kinds }
        val next=nodes.firstOrNull { it.id==panelSelections[section] } ?: nodes.firstOrNull()
        buildingGraphView.selected=next?.id.orEmpty();buildingGraphView.inspectorScroll=0.0;buildingGraphView.inspectorNode=""
        graphMode=true;graphInspector=true;tab=7;buildingGraphView.wire=null
        presentationInitialized=true;rebuildWidgets()
    }
    private fun selectPanelDefinition(id:String) {
        selectBuildingNode(id);panelSelections[buildingPanelSection]=id
        if(buildingPanelSection==BuildingPanelSection.STOREYS) {
            floorPlanView.storey=id;floorPlanView.selected="";floorPlanView.fitted=false
        }
        rebuildWidgets()
    }
    fun openFloorPlan() {
        if(building.graph==null) return
        val selected=buildingDisplayGraph().nodes.firstOrNull { it.id==buildingGraphView.selected }
        if(selected?.kind==BuildingKind.ROOM) floorPlanView.selected=selected.id
        if(selected?.kind==BuildingKind.STOREY && selected.storeyMode=="rooms") {
            floorPlanView.storey=selected.id
            floorPlanView.selected=selected.inputs.firstOrNull { source -> buildingDisplayGraph().nodes.any { it.id==source && it.kind==BuildingKind.ROOM } }.orEmpty()
        }
        floorPlanView.fitted=false
        graphMode=true;graphInspector=true;tab=8;buildingGraphView.wire=null
        presentationInitialized=true;rebuildWidgets()
    }
    fun commitBuildingGraph(value:BuildingGraph) {
        val original=buildingDisplayGraph()
        val graph=GraphGroups.synchronize(original,value.copy(nodeGroups=GraphGroups.prune(value.nodeGroups,value.nodes.map { it.id }.toSet())))
        if(graph==original) return
        if(building.graph==null) { buildingProjection=graph;return }
        safe { editBuildingGraph(graph) }
    }
    private fun editBuildingGraph(value:BuildingGraph) {
        val graph=GraphGroups.synchronize(buildingDisplayGraph(),value).withCurrentSchema()
        if(graph==building.graph) return
        BuildingGraphCompiler.structure(graph)
        val missing=graph.nodes.map { it.role }.distinct().filter { it !in building.palette }
        require(building.palette.size+missing.size<=16) { ui("role_limit") }
        val palette=building.palette+missing.associateWith { building.palette["wall"] ?: "minecraft:stone" }
        BuildingSourceEditors.validateBuilding(building.copy(schema=2,graph=graph,palette=palette))
        buildingUndo.addLast(building);if(buildingUndo.size>64) buildingUndo.removeFirst();buildingRedo.clear()
        building=building.copy(schema=2,graph=graph,palette=palette);rebuildWidgets()
    }
    var building=ClientState.snapshot.tableBuilding ?: ClientState.snapshot.library.buildings.values.lastOrNull { saved -> saved.graph?.nodes?.any { it.assembly }==true }
        ?: Building(schema=2,id=UUID.randomUUID().toString(),name=ui("new_graph_document"),graph=BuildingGraph.explicitStarter())
    private var module=ClientState.snapshot.library.modules["window@1"] ?: Module()
    var tab=0;private var level=0;private var role="wall";private var cellForm="full@1";private var cellTurn=0;private var cellMode=0
    var module3d=false;private var wall=0;private var selectedCell:P?=null;private var jobIndex=0;private var visibleFloor=-1;private var component=0
    private var boundTable=ClientState.snapshot.table
    private var lastProjection:BuildingProjection?=null
    private var origin=ClientState.snapshot.tableOrigin ?: (building.graph?.projection ?: BuildingProjection()).origin(boundTable ?: ClientState.snapshot.position)
    private var dock=(boundTable ?: ClientState.snapshot.position)+P(0,1,0)
    private var rotation=if(ClientState.snapshot.tableOrigin!=null) ClientState.snapshot.tableTurn else building.graph?.projection?.turn ?: 0;private var computer=-1
    private var keepWorldPreview=false
    private var eyeButton:PrinterEyeButton?=null
    private var creativeBuildButton:Button?=null
    private var observedCreative=ClientState.snapshot.creative
    private lateinit var name:EditBox
    private val planJob=PreviewJobs<Unit,CompiledBuilding>()
    private val planCompilations=java.util.concurrent.atomic.AtomicInteger()
    private val buildingThumbnailJobs=PreviewJobs<String,BuildingNodePreview>()
    private var previewBuilding:Building?=null
    private var observedBuilding:Building?=null
    private var previewLibrary:Library?=null
    private var previewLibrarySource:Library?=null
    private var buildingThumbnailSource:BuildingNodePreviews?=null
    private var resultThumbnail:BuildingNodePreview?=null

    private fun refreshBuildingPreviewSource() {
        val library=lib
        val documentChanged=observedBuilding!==building
        val libraryChanged=previewLibrarySource!==library
        if(!documentChanged && !libraryChanged) return
        observedBuilding=building;previewLibrarySource=library
        val graph=building.graph
        val input=if(documentChanged) building.copy(graph=graph?.copy(
            nodes=graph.nodes.map { if(it.x==0 && it.y==0) it else it.copy(x=0,y=0) },
            // The table placement transforms the finished plan. Invalid imported values still fail compilation.
            projection=if(runCatching { graph.projection.validate() }.isSuccess) BuildingProjection() else graph.projection
        )) else checkNotNull(previewBuilding)
        val sources=previewLibrary
        if(input==previewBuilding && sources!=null && sources.forms==library.forms && sources.modules==library.modules) return
        val snapshot=Library(forms=library.forms.toMutableMap(),modules=library.modules.toMutableMap())
        previewBuilding=input;previewLibrary=snapshot
        buildingThumbnailSource=BuildingNodePreviews({ input },{ snapshot })
        resultThumbnail=null
        planJob.clear();buildingThumbnailJobs.clear()
    }

    private fun compiledPlan():Result<CompiledBuilding>? {
        refreshBuildingPreviewSource()
        val input=checkNotNull(previewBuilding)
        val sources=checkNotNull(previewLibrary)
        return planJob.request(Unit) {
            planCompilations.incrementAndGet()
            if(input.graph!=null) BuildingGraphCompiler.compile(input,sources.forms,sources.modules)
            else CompiledBuilding(Generator.generate(input,sources.forms,sources.modules),emptyList())
        }
    }

    private fun plan():Result<Plan>?=compiledPlan()?.map(CompiledBuilding::plan)

    internal fun planCompilationCount()=planCompilations.get()
    internal fun checkedResultPreview():Plan? {
        val result=plan()?.getOrThrow() ?: return null
        val id=building.graph?.nodes?.single { it.kind==BuildingKind.BUILDING }?.id ?: "classic_result"
        check(buildingThumbnail(id)?.plan?.getOrThrow()===result)
        return result
    }

    private fun buildingThumbnail(id:String):BuildingNodePreview? {
        refreshBuildingPreviewSource()
        val input=checkNotNull(previewBuilding)
        val node=input.graph?.nodes?.firstOrNull { it.id==id }
        if(id=="classic_result" || node?.kind==BuildingKind.BUILDING) {
            resultThumbnail?.let { return it }
            val result=plan() ?: return null
            return BuildingNodePreview(node ?: BuildingNode(id=id,kind=BuildingKind.BUILDING),result).also { resultThumbnail=it }
        }
        val source=checkNotNull(buildingThumbnailSource)
        return buildingThumbnailJobs.request(id) { source.get(id) }
            ?.getOrElse { BuildingNodePreview(node ?: BuildingNode(id=id),Result.failure(it)) }
    }
    private fun buildingGraphRevision():Any=building.graph ?: building
    private var coordinateFields=emptyList<EditBox>()
    private val lib get()=ClientState.snapshot.library
    /** A sync refreshes the table binding without replacing the unsaved editor document or inputs. */
    fun refreshProjectionBinding() {
        val next=ClientState.snapshot.table
        val changed=next!=boundTable
        boundTable=next
        if(changed && next!=null) {
            building.graph?.projection?.let { origin=it.origin(next);rotation=it.turn;lastProjection=it }
            coordinateFields.take(3).zip(listOf(origin.x,origin.y,origin.z)).forEach { (field,value) -> field.value=value.toString() }
        }
        eyeButton?.active=next!=null
        creativeBuildButton?.active=next!=null && ClientState.snapshot.creative
        if(observedCreative!=ClientState.snapshot.creative) { observedCreative=ClientState.snapshot.creative;rebuildWidgets() }
        updateWorldPreview()
    }
    private fun synchronizeProjection() {
        val projection=building.graph?.projection
        if(projection!=null && projection!=lastProjection) {
            boundTable?.let { origin=projection.origin(it) }
            rotation=projection.turn
        }
        lastProjection=projection
    }
    private fun placementChanged() {
        val graph=building.graph
        val table=boundTable
        if(graph!=null && table!=null) {
            val offset=P(table.x-origin.x,table.y-origin.y,table.z-origin.z).turn(-rotation)
            val projection=runCatching { BuildingProjection(offset,rotation).validate() }.getOrNull()
            if(projection!=null && projection!=graph.projection) {
                buildingUndo.addLast(building);if(buildingUndo.size>64) buildingUndo.removeFirst();buildingRedo.clear()
                building=building.copy(graph=graph.copy(projection=projection))
            }
            // Incomplete or distant manual coordinates remain a transient build override.
            lastProjection=projection ?: graph.projection
        }
        updateWorldPreview()
    }
    private fun updateWorldPreview() {
        boundTable?.let { table ->
            val level=minecraft?.level
            val pos=PlacementService.world(table)
            if(level!=null && level.hasChunkAt(pos) && !level.getBlockState(pos).`is`(de.teutonstudio.copycatarchitect.Architect.TABLE.get())) {
                boundTable=null;eyeButton?.active=false;creativeBuildButton?.active=false
                ClientState.snapshot=ClientState.snapshot.copy(message=ui("bg_projection_table_missing"))
            }
        }
        BuildingWorldPreview.update(this,if(boundTable!=null) plan()?.getOrNull() else null,origin,rotation,boundTable,keepWorldPreview)
    }
    override fun tick() { super.tick();synchronizeProjection();updateWorldPreview() }
    override val worldPeekButton get()=eyeButton
    private fun update(b:Building) {
        BuildingSourceEditors.validateBuilding(b)
        if(b.id!=building.id) fitBuildingGraph=true
        if(b!=building) { buildingUndo.addLast(building);buildingRedo.clear() }
        building=b
        if(tab !in listOf(1,5,6)) {
            if(b.graph==null && (graphMode || tab==7)) { graphMode=false;tab=lastClassicTab }
            else if(b.graph!=null && !graphMode) { graphMode=true;graphInspector=true;tab=7 }
        }
        rebuildWidgets()
    }
    private fun createRole(value:String) {
        require(value.matches(Regex("[a-zA-Z0-9_-]{1,32}")))
        if(value in building.palette) return
        require(building.palette.size<16) { ui("role_limit") }
        buildingUndo.addLast(building);buildingRedo.clear()
        building=building.copy(palette=building.palette+(value to (building.palette["wall"] ?: "minecraft:stone")))
    }
    private fun validateRole(value:String) {
        require(value.matches(Regex("[a-zA-Z0-9_-]{1,32}")))
        require(value in building.palette || building.palette.size<16) { ui("role_limit") }
    }
    private fun openBuildingMaterialSelection(id:String,group:String) {
        val node=buildingDisplayGraph().nodes.single { it.id==id }
        val form=lib.forms[node.form]
        val groups=form?.groups ?: DEFAULT_GROUPS
        val roles=BuildingSourceEditors.copycatMaterialRoles(node,groups)
        val surfaces=roles.mapValues { (group,role) -> node.surfaces.orEmpty()[group] ?: building.palette[role] ?: error("Missing material role $role") }
        val parts=buildingNodePreviews.get(id).plan.map { plan -> plan.placements.flatMap { it.parts } }
        MaterialSelectionScreen.open(groups,parts,surfaces,group,materialRoles=roles) { selected ->
            selected.values.distinct().forEach { Economy.state(it) }
            update(BuildingSourceEditors.copycatMaterials(building,node,groups,selected))
        }
    }
    private fun roleName(value:String):String {
        val key="role_"+value
        return if(net.minecraft.client.resources.language.I18n.exists("gui.copycat_architect.$key")) ui(key) else value
    }
    private fun modUpdate(m:Module) { module=m;rebuildWidgets() }
    private fun pattern(action:(Pattern)->Pattern) = update(building.copy(patterns=building.patterns.mapIndexed { i,p -> if(i==wall) action(p) else p }))
    override fun init() {
        buildingDiagramCache=null
        synchronizeProjection()
        creativeBuildButton=null
        coordinateFields=emptyList()
        kilaBuildingGraph?.dispose();kilaBuildingGraph=null
        resetDropdown()
        if(!presentationInitialized) { graphMode=tab !in listOf(1,5,6);presentationInitialized=true }
        buildingGraphCanvas=null
        floorPlanCanvas=null
        buildingDefinitionList=null
        initWorkspaceChrome()
        updateWorldPreview()
        if(graphMode) {
            if(tab==7 && building.graph!=null) { initBuildingPanel();return }
            val l=workspace
            if(tab==8) {
                floorPlanCanvas=addRenderableWidget(FloorPlanCanvas(l.x,l.graphY,l.innerWidth,l.graphHeight,::buildingDisplayGraph,floorPlanView,buildingGraphView,::commitBuildingGraph,{ message -> ClientState.snapshot=ClientState.snapshot.copy(message=message) },roomFootprint=buildingNodePreviews::roomFootprint,roomShell=buildingNodePreviews::roomShell,editRoomLayout=::editRoomSegmentLayout))
                return
            }
            buildingGraphCanvas=BuildingGraphCanvas(l.panelX,l.graphY,l.panelWidth,l.graphHeight,{displayBuilding()},{lib},buildingGraphView,::commitBuildingGraph,{ message -> ClientState.snapshot=ClientState.snapshot.copy(message=message) },true,onCreateRole=::validateRole,preview=::compiledPlan,readOnly=building.graph==null,onSelectDefinition={ id -> selectBuildingNode(id);kilaBuildingGraph?.selectNode(id,true) },onSelectMaterial=::openBuildingMaterialSelection)
            buildingGraphCanvas!!.visible=graphInspector
            if(graphInspector) addRenderableWidget(buildingGraphCanvas!!)
            kilaBuildingGraph=addRenderableWidget(KilaGraphEditor(l.x,l.graphY,l.graphWidth(graphInspector),l.graphHeight,this,kilaBuildingGraphState,::buildingDiagram,::selectBuildingNode,::commitBuildingDiagram,{ message -> ClientState.snapshot=ClientState.snapshot.copy(message=message) },building.graph==null,insertions=buildingInsertions(),buildingNodePreview=::buildingThumbnail,instantiateGroup=::instantiateBuildingGroup,dataRevision=::buildingGraphRevision,previewAvailable={ id -> building.graph!=null || id=="classic_result" }))
            return
        }
        initClassicWorkspace()
    }
    private fun initWorkspaceChrome() {
        val l=workspace
        createTopTabs(width,height).forEach { addRenderableWidget(it) }
        val tabs=if(building.graph!=null) listOf(0 to "node_graph",7 to "control_panel",8 to "footprint",1 to "modules",5 to "palette",6 to "build")
            else listOf("footprint","modules","walls","plates","roof","palette","build").mapIndexed { i,key -> i to key }
        tabs.forEachIndexed { index,(i,key) ->
            val symbol=when(key) { "node_graph"->"G";"control_panel"->"I";"footprint"->"F";"modules"->"M";"palette"->"C";"build"->"P";"walls"->"W";"plates"->"L";else->"R" }
            addRenderableWidget(PrinterEdgeTab(l.left-17,l.top+12+index*24,tr(key),symbol,selected={ tab==i }) {
                if(i==8) openFloorPlan()
                else if(i==7) openBuildingGraph(true)
                else if(i==0 && building.graph!=null) openBuildingGraph()
                else {
                    tab=i;graphMode=false;graphInspector=false
                    if(building.graph==null) lastClassicTab=i
                    buildingGraphView.wire=null;rebuildWidgets()
                }
            })
        }
        addRenderableWidget(PrinterEdgeTab(l.left-17,l.top+12+tabs.size*24,tr("copycat_graph"),"C",selected={ false }) { openCopycatGraph() })
        buildingUndoButton=button(l.x,l.y,46,"undo") { undoEdit() }.apply { active=buildingUndo.isNotEmpty() }
        buildingRedoButton=button(l.x+49,l.y,46,"redo") { redoEdit() }.apply { active=buildingRedo.isNotEmpty() }
        button(l.x+98,l.y,54,"fit_graph") { floorPlanCanvas?.fit() ?: kilaBuildingGraph?.fit() ?: buildingGraphCanvas?.fit() }.active=graphMode
        name=field(l.x+158,l.y,(l.innerWidth-242).coerceAtLeast(36),if(tab==1) module.name else building.name)
        name.setResponder { if(tab==1) module=module.copy(name=it) else building=building.copy(name=it) }
        button(l.x+l.innerWidth-78,l.y,50,"save") {
            if(tab==1) ClientState.send(Command("saveModule",Json.encode(module)))
            else ClientState.send(Command("saveBuilding",Json.encode(building)))
        }
        eyeButton=addRenderableWidget(PrinterEyeButton(l.x+l.innerWidth-24,l.y).apply {
            message=tr("bg_projection_eye");setTooltip(Tooltip.create(message));active=boundTable!=null
        })
        if(building.graph==null) {
            addRenderableWidget(PrinterEdgeTab(l.left+l.width,l.top+12,tr("node_graph"),"G",true,{ graphMode }) { openBuildingGraph() })
            addRenderableWidget(PrinterEdgeTab(l.left+l.width,l.top+36,tr("control_panel"),"I",true,{ !graphMode }) { openBuildingGraph(true) })
        }
        if(graphMode && tab==0) addRenderableWidget(PrinterEdgeTab(l.left+l.width,l.top+if(building.graph==null) 60 else 12,tr("printer_inspector"),"I",true,{ graphInspector }) {
            graphInspector=!graphInspector;buildingGraphView.inspectorNode="";rebuildWidgets()
        })
        if(graphMode && tab==8) addRenderableWidget(PrinterEdgeTab(l.left+l.width,l.top+12,tr("floorplan_add_room"),"+",true,{ false }) { floorPlanCanvas?.addRoom() })
        if(graphMode && tab in listOf(0,8) && building.graph!=null) addRenderableWidget(PrinterEdgeTab(l.left+l.width,l.top+36,tr("remove"),"−",true,{ false }) {
            if(floorPlanCanvas!=null) floorPlanCanvas!!.deleteSelected() else kilaBuildingGraph?.deleteSelection()
        }.apply { active=building.graph!=null })
        lateinit var document:PrinterEdgeTab
        document=addRenderableWidget(PrinterEdgeTab(l.left+l.width,l.top+l.height-48,tr("document"),"+",true,{ false }) {
            openDropdown(document,documentOptions())
        })
        addRenderableWidget(PrinterEdgeTab(l.left+l.width,l.top+l.height-24,tr("close"),"×",true,{ false }) { onClose() })
    }
    private fun documentOptions():List<Pair<String,()->Unit>> {
        val saved=if(tab==1) lib.modules.values.map { value -> "${ui("load")}: ${value.name} · v${value.version}" to { modUpdate(value) } }
            else lib.buildings.values.map { value -> "${ui("load")}: ${value.name} · v${value.version}" to { update(value) } }
        val create=if(tab==1) listOf(ui("new_module") to { modUpdate(Module(id=UUID.randomUUID().toString(),name=ui("new_module"))) }) else listOf(
            ui("new_classic_building") to { graphMode=false;tab=0;update(Building(id=UUID.randomUUID().toString(),name=ui("new_classic_building"))) },
            ui("new_graph_document") to {
                graphMode=true;graphInspector=false;tab=0;fitBuildingGraph=true;kilaBuildingGraphState.fitted=false
                buildingGraphView.selected="room";buildingGraphView.inspectorScroll=0.0;kilaBuildingGraphState.selected="room"
                update(Building(schema=2,id=UUID.randomUUID().toString(),name=ui("new_graph_document"),graph=BuildingGraph.explicitStarter()))
            })
        return create+listOf(ui("copy") to {
            if(tab==1) modUpdate(module.copy(id=UUID.randomUUID().toString(),version=1,name=module.name+" copy"))
            else update(building.copy(id=UUID.randomUUID().toString(),version=1,name=building.name+" copy"))
        },ui("export") to {
            minecraft?.keyboardHandler?.clipboard=if(tab==1) Json.encode(module) else Json.encode(building)
            ClientState.snapshot=ClientState.snapshot.copy(message=ui("building_copied"))
        },ui("import") to { safe {
            val text=minecraft?.keyboardHandler?.clipboard.orEmpty();require(text.length<=CommandTransport.MAX_JSON_CHARS)
            if(tab==1) modUpdate(Json.decode<Module>(text).validate().copy(id=UUID.randomUUID().toString(),version=1))
            else update(Json.decode<Building>(text).copy(id=UUID.randomUUID().toString(),version=1))
        } })+saved
    }
    /** Classic editors retain their grid coordinates inside the same framed workspace. */
    private fun initClassicWorkspace() {
        val width=classicWidth;val height=classicHeight
        val existing=children().toSet()
        when(tab) {
            0 -> {
                select(8,78,135,building.wings.size>1,listOf(false,true),{ ui(if(it) "l_footprint" else "rectangle") }) { isL -> update(building.copy(wings=if(isL) listOf(building.wings[0],Rect(0,building.wings[0].length,5,6)) else listOf(building.wings[0]))) }
                for(i in building.wings.indices) {
                    val r=building.wings[i]
                    number(8,102+i*22,68,r.width,3..20,{ ui("width",it) }) { value -> val wings=building.wings.toMutableList();wings[i]=r.copy(width=value);update(building.copy(wings=wings)) }
                    number(79,102+i*22,68,r.length,3..20,{ ui("bg_depth",it) }) { value -> val wings=building.wings.toMutableList();wings[i]=r.copy(length=value);if(i==0 && wings.size==2) wings[1]=wings[1].copy(z=wings[0].length);update(building.copy(wings=wings)) }
                }
                number(8,153,135,building.floors,1..4,{ ui("floors",it) }) { update(building.copy(floors=it)) }
                number(8,175,135,building.roomHeight,2..8,{ ui("room_height",it) }) { update(building.copy(roomHeight=it)) }
            }
            1 -> {
                number(8,78,74,module.width,1..8,{ ui("width",it) }) { modUpdate(module.copy(width=it,cells=emptyList(),minWidth=it,maxWidth=it)) }
                number(85,78,74,module.height,1..8,{ ui("height",it) }) { modUpdate(module.copy(height=it,cells=emptyList())) }
                cycle(164,100,84,if(module3d) ui("preview3d") else ui("edit_grid")) { module3d=!module3d;rebuildWidgets() }
                select(8,100,74,level,(0 until module.depth).toList(),{ ui("layer_index",it) }) { level=it;rebuildWidgets() }
                select(85,100,74,cellTurn,(0..3).toList(),{ "${it*90}°" }) { cellTurn=it;rebuildWidgets() }
                cycle(8,122,151,lib.forms[cellForm]?.name ?: cellForm) {
                    SavedCopycatSelectionScreen.open(this,cellForm) { cellForm=it;rebuildWidgets() }
                }
                button(8,144,151,Component.literal(ui("role",roleName(role)))) {
                    EditorValueInput.text(tr("role_identifier"),role,32,{ it.matches(Regex("[a-zA-Z0-9_-]{1,32}")) }) { value -> safe { createRole(value);role=value;rebuildWidgets() } }
                }
                select(8,166,151,cellMode,(0..4).toList(),{ listOf(ui("place_form"),ui("require_opening"),ui("erase"),ui("flexible_fill"),ui("native_block"))[it] }) { cellMode=it;rebuildWidgets() }
                button(8,188,151,"edit_shape") { openCopycatGraph() }
                number(164,78,84,module.maxWidth,module.width..8,{ ui("variable_width",it) }) { modUpdate(module.copy(minWidth=module.width,maxWidth=it)) }
                button(251,78,65,"new") { modUpdate(Module(id=UUID.randomUUID().toString(),name="New module",width=3,height=4)) }
            }
            2 -> {
                val p=building.patterns[wall]
                select(8,78,160,wall,(0..3).toList(),{ listOf(ui("north"),ui("east"),ui("south"),ui("west"))[it] }) { wall=it;rebuildWidgets() }
                select(8,100,160,p.mode,(0..2).toList(),{ listOf(ui("repeat_segment"),ui("segment_interlude"),ui("repeat_sequence"))[it] }) { value -> pattern { it.copy(mode=value) } }
                for(i in p.sequence.indices.take(3)) select(8,122+i*22,160,p.sequence[i],lib.modules.keys.toList(),{ "${('A'.code+i).toChar()}: ${lib.modules[it]?.name ?: it}" }) { value -> pattern { it.copy(sequence=it.sequence.mapIndexed { j,k -> if(j==i) value else k }) } }
                select(174,78,140,p.interlude,lib.modules.keys.toList(),{ ui("pattern_interlude",lib.modules[it]?.name ?: it) }) { value -> pattern { it.copy(interlude=value) } }
                select(174,100,140,p.remainder,Remainder.entries,{ ui("pattern_remainder",ui("remainder_"+it.name.lowercase())) }) { value -> pattern { it.copy(remainder=value) } }
                select(174,122,140,p.start,listOf("")+lib.modules.keys,{ ui("pattern_start",lib.modules[it]?.name ?: ui("none")) }) { value -> pattern { it.copy(start=value) } }
                select(174,144,140,p.end,listOf("")+lib.modules.keys,{ ui("pattern_end",lib.modules[it]?.name ?: ui("none")) }) { value -> pattern { it.copy(end=value) } }
                select(174,166,140,p.filler,lib.modules.keys.toList(),{ ui("pattern_filler",lib.modules[it]?.name ?: it) }) { value -> pattern { it.copy(filler=value) } }
                cycle(8,minOf(195,height-58),77,"+ sequence") { if(p.sequence.size<3) pattern { it.copy(sequence=it.sequence+lib.modules.keys.first()) } }
                cycle(88,minOf(195,height-58),80,"- sequence") { if(p.sequence.size>1) pattern { it.copy(sequence=it.sequence.dropLast(1)) } }
                select(174,188,140,p.partial,listOf(false,true),{ ui("partial_cycle",ui(if(it) "yes" else "no")) }) { value -> pattern { it.copy(partial=value) } }
            }
            3 -> {
                cycle(8,78,190,ui("plate_form",lib.forms[building.floorForm]?.name ?: building.floorForm)) {
                    SavedCopycatSelectionScreen.open(this,building.floorForm) { update(building.copy(floorForm=it,ceilingForm=it)) }
                }
                number(8,100,190,building.floorThickness,1..2,{ ui("plate_thickness",it) }) { update(building.copy(floorThickness=it,ceilingThickness=it)) }
                select(8,122,190,level,(0..building.floors).toList(),{ ui("cutout_layer",it) }) { level=it;rebuildWidgets() }
            }
            4 -> {
                select(8,78,165,building.roof,listOf("flat","gable"),{ ui("roof_type",ui(it)) }) { update(building.copy(roof=it)) }
                select(8,100,165,building.ridge,listOf(0,1),{ ui("bg_ridge",if(it==0) "Z" else "X") }) { update(building.copy(ridge=it)) }
                number(8,122,165,building.overhang,0..2,{ ui("overhang",it) }) { update(building.copy(overhang=it)) }
                number(8,144,165,building.roofThickness,1..2,{ ui("roof_thickness",it) }) { update(building.copy(roofThickness=it)) }
                choice(8,166,165,ui("trim_form",lib.forms[building.trimForm]?.name ?: ui("none")),listOf(
                    ui("none") to { update(building.copy(trimForm="")) },
                    ui("copycat_library") to { SavedCopycatSelectionScreen.open(this,building.trimForm) { update(building.copy(trimForm=it)) } }))
            }
            5 -> {
                if(role !in building.palette) role=building.palette.keys.first()
                choice(8,78,200,roleName(role),building.palette.keys.map { value -> roleName(value) to { role=value;rebuildWidgets() } }+
                    if(building.palette.size<16) listOf(ui("new_material_role") to { EditorValueInput.text(tr("role_identifier"),"",32,{ it.matches(Regex("[a-zA-Z0-9_-]{1,32}")) }) { value -> createRole(value);role=value;rebuildWidgets() } }) else emptyList(),building.palette.keys.indexOf(role))
                val material=building.palette.getValue(role)
                select(8,101,200,material,Economy.palette,{ Economy.item(it).description.string }) { update(building.copy(palette=building.palette+(role to it))) }
            }
            6 -> {
                val left=buildLeftWidth;val right=16+left;val rightWidth=width-right-8
                val cell=(left+4)/3
                val vals=listOf(origin.x,origin.y,origin.z,dock.x,dock.y,dock.z,computer)
                val spacing=buildCoordinateSpacing
                coordinateFields=vals.mapIndexed { i,v -> field(8+(i%3)*cell,89+(i/3)*spacing,cell-4,v.toString()).apply { setResponder { text -> text.toIntOrNull()?.let { value -> when(i) {
                    0->{ origin=origin.copy(x=value);placementChanged() };1->{ origin=origin.copy(y=value);placementChanged() };2->{ origin=origin.copy(z=value);placementChanged() }
                    3->dock=dock.copy(x=value);4->dock=dock.copy(y=value);5->dock=dock.copy(z=value);else->computer=value
                } } } } }
                select(8,114+2*spacing,left,rotation,(0..3).toList(),{ ui("rotation")+": ${it*90}°" }) { rotation=it;placementChanged();rebuildWidgets() }
                button(8,136+2*spacing,left,"create_job") { safe {
                    val v=coordinateFields.map { it.value.toInt() };origin=P(v[0],v[1],v[2]);dock=P(v[3],v[4],v[5]);computer=v[6]
                    placementChanged()
                    building=building.copy(name=name.value);ClientState.send(Command("createJob",Json.encode(building),origin=origin,dock=dock,turn=rotation,computer=computer))
                } }
                select(right,78,rightWidth,jobIndex,ClientState.snapshot.jobs.indices.toList(),{ index -> ClientState.snapshot.jobs.getOrNull(index)?.let { "${it.name}: ${it.index}/${it.total}" } ?: ui("no_job") }) { jobIndex=it;rebuildWidgets() }
                val columns=buildActionColumns;val actionWidth=(rightWidth-(columns-1)*3)/columns
                listOf("start","pause","resume","cancel").forEachIndexed { i,action ->
                    button(right+(i%columns)*(actionWidth+3),101+(i/columns)*22,actionWidth,action) { ClientState.snapshot.jobs.getOrNull(jobIndex)?.let { ClientState.send(Command(action,key=it.id)) } }.active=ClientState.snapshot.jobs.getOrNull(jobIndex)!=null
                }
                val toolsY=101+((4+columns-1)/columns)*22;val half=(rightWidth-3)/2
                button(right,toolsY,half,"refresh") { ClientState.send(Command()) }
                button(right+half+3,toolsY,half,"export") { minecraft?.keyboardHandler?.clipboard=Json.encode(building);ClientState.snapshot=ClientState.snapshot.copy(message=ui("building_copied")) }
                button(right,toolsY+22,half,if(keepWorldPreview) "bg_projection_keep_on" else "bg_projection_keep_off") { keepWorldPreview=!keepWorldPreview;updateWorldPreview();rebuildWidgets() }.active=boundTable!=null
                button(right+half+3,toolsY+22,half,"import") { safe { val text=minecraft?.keyboardHandler?.clipboard.orEmpty();require(text.length<=CommandTransport.MAX_JSON_CHARS);val imported=Json.decode<Building>(text);Generator.generate(imported,lib.forms,lib.modules);update(imported) } }
                if(ClientState.snapshot.creative) creativeBuildButton=button(right,toolsY+44,rightWidth,"creative_build") { safe {
                    val v=coordinateFields.take(3).map { it.value.toInt() };origin=P(v[0],v[1],v[2]);placementChanged()
                    building=building.copy(name=name.value)
                    ClientState.send(Command("creativeBuild",Json.encode(building),origin=origin,turn=rotation))
                } }.apply { active=boundTable!=null;setTooltip(Tooltip.create(tr("creative_build_hint"))) }
            }
        }
        if(tab!=1 && tab!=2 && tab!=6) {
            select(width-146,78,138,visibleFloor,(-1..building.floors).toList(),{ ui("preview_floor",if(it<0) ui("all_groups") else it) }) { visibleFloor=it;rebuildWidgets() }
            select(width-146,height-60,138,component,(0..3).toList(),{ listOf(ui("all_components"),ui("walls"),ui("plates"),ui("roof"))[it] }) { component=it;rebuildWidgets() }
        }
        children().filterIsInstance<AbstractWidget>().filter { it !in existing }.forEach { widget ->
            widget.x+=classicOffsetX;widget.y+=classicOffsetY
        }
    }
    override fun mouseDragged(x:Double,y:Double,button:Int,dx:Double,dy:Double):Boolean {
        if(focused===floorPlanCanvas && floorPlanCanvas!=null) return floorPlanCanvas!!.mouseDragged(x,y,button,dx,dy)
        if(focused===kilaBuildingGraph && kilaBuildingGraph!=null) return kilaBuildingGraph!!.mouseDragged(x,y,button,dx,dy)
        if(graphMode && buildingInspectorVisible && buildingGraphCanvas?.visible==true && focused===buildingGraphCanvas) return buildingGraphCanvas?.mouseDragged(x,y,button,dx,dy) ?: false
        return super.mouseDragged(x,y,button,dx,dy)
    }
    override fun mouseReleased(x:Double,y:Double,button:Int):Boolean {
        if(focused===floorPlanCanvas && floorPlanCanvas!=null) return floorPlanCanvas!!.mouseReleased(x,y,button)
        if(focused===kilaBuildingGraph && kilaBuildingGraph!=null) return kilaBuildingGraph!!.mouseReleased(x,y,button)
        if(graphMode && buildingInspectorVisible && buildingGraphCanvas?.visible==true && focused===buildingGraphCanvas) return buildingGraphCanvas?.mouseReleased(x,y,button) ?: false
        return super.mouseReleased(x,y,button)
    }
    override fun keyPressed(key:Int,scan:Int,mods:Int):Boolean {
        if(dropdownKey(key)) return true
        if(hasControlDown() && focused !is EditBox && key==90) { if(hasShiftDown()) redoEdit() else undoEdit();return true }
        if(hasControlDown() && focused !is EditBox && key==89) { redoEdit();return true }
        if(focused !is EditBox && floorPlanCanvas?.keyPressed(key,scan,mods)==true) return true
        if(focused===kilaBuildingGraph && kilaBuildingGraph?.keyPressed(key,scan,mods)==true) return true
        if(graphMode && focused !is EditBox && buildingGraphCanvas?.visible==true && buildingGraphCanvas?.keyPressed(key,scan,mods)==true) return true
        if(hasControlDown() && key==67 && focused !is EditBox) { minecraft?.keyboardHandler?.clipboard=if(tab==1) Json.encode(module.copy(name=name.value)) else Json.encode(building.copy(name=name.value));return true }
        if(hasControlDown() && key==86 && focused !is EditBox) { safe {
            val text=minecraft?.keyboardHandler?.clipboard.orEmpty();require(text.length<=CommandTransport.MAX_JSON_CHARS)
            if(tab==1) modUpdate(Json.decode<Module>(text).validate().copy(id=UUID.randomUUID().toString(),version=1))
            else update(Json.decode<Building>(text).copy(id=UUID.randomUUID().toString(),version=1))
        };return true }
        return super.keyPressed(key,scan,mods)
    }
    private fun initBuildingPanel() {
        val graph=buildingDisplayGraph()
        val section=buildingPanelSection
        val definitions=graph.nodes.filter { it.kind in section.kinds }
        if(definitions.none { it.id==buildingGraphView.selected }) {
            buildingGraphView.selected=(definitions.firstOrNull { it.id==panelSelections[section] } ?: definitions.firstOrNull())?.id.orEmpty()
            buildingGraphView.inspectorScroll=0.0
        }
        val selected=definitions.firstOrNull { it.id==buildingGraphView.selected }
        selected?.let { panelSelections[section]=it.id }
        val l=workspace
        val sectionWidth=l.innerWidth/BuildingPanelSection.entries.size
        BuildingPanelSection.entries.forEachIndexed { index,item ->
            button(l.x+index*sectionWidth,l.graphY,sectionWidth-2,item.key) { openBuildingSection(item) }.apply { (this as EditorButton).selected=item==section }
        }
        val listWidth=(l.innerWidth*0.25).toInt().coerceIn(102,196)
        val mainX=l.x+listWidth+6
        val mainWidth=l.innerWidth-listWidth-6
        val bodyY=l.graphY+24
        val bodyHeight=(l.graphHeight-24).coerceAtLeast(56)
        val storeyLayout=section==BuildingPanelSection.STOREYS && selected?.storeyMode=="rooms"
        val footerHeight=if(storeyLayout) 68 else 24
        buildingDefinitionList=addRenderableWidget(BuildingDefinitionList(l.x,bodyY,listWidth,(bodyHeight-footerHeight).coerceAtLeast(32),
            ::buildingDisplayGraph,section,{buildingGraphView.selected},::selectPanelDefinition,panelScroll[section] ?: 0.0,{ panelScroll[section]=it }))
        buildingDefinitionList!!.reveal(buildingGraphView.selected)
        val addKinds=section.kinds.filter { GraphModels.buildingInsertable(it,explicitAssembly) }
        choice(l.x,bodyY+bodyHeight-footerHeight+3,(listWidth-4)/2,ui("panel_add_definition"),addKinds.map { kind ->
            ui(GraphModels.building(kind).labelKey) to { buildingGraphCanvas?.addAt(kind,600.0,30.0+graph.nodes.size*12) }
        })
        button(l.x+(listWidth+4)/2,bodyY+bodyHeight-footerHeight+3,(listWidth-4)/2,"remove") { buildingGraphCanvas?.deleteSelected() }
            .active=selected!=null && selected.kind!=BuildingKind.BUILDING && selected.id!="empty_wall"
        val split=storeyLayout && l.innerWidth>=850
        val showLayout=storeyLayout && (split || !storeyProperties)
        val inspectorWidth=if(split) minOf(340,mainWidth/2) else mainWidth
        val inspectorX=if(split) l.x+l.innerWidth-inspectorWidth else mainX
        buildingGraphCanvas=BuildingGraphCanvas(inspectorX,bodyY,inspectorWidth,bodyHeight,{displayBuilding()},{lib},buildingGraphView,
            ::commitBuildingGraph,{ message -> ClientState.snapshot=ClientState.snapshot.copy(message=message) },true,onCreateRole=::validateRole,preview=::compiledPlan,
            definitionKinds=section.kinds.toSet(),onSelectDefinition=::selectPanelDefinition,onSelectMaterial=::openBuildingMaterialSelection)
        buildingGraphCanvas!!.visible=selected!=null && (!showLayout || split)
        if(selected!=null && (!showLayout || split)) addRenderableWidget(buildingGraphCanvas!!)
        if(storeyLayout) {
            floorPlanView.storey=selected!!.id
            if(showLayout) floorPlanCanvas=addRenderableWidget(FloorPlanCanvas(mainX,bodyY,if(split) inspectorX-mainX-6 else mainWidth,bodyHeight,
                ::buildingDisplayGraph,floorPlanView,buildingGraphView,::commitBuildingGraph,{ message -> ClientState.snapshot=ClientState.snapshot.copy(message=message) },
                placementMode=true,showInspector=false,roomFootprint=buildingNodePreviews::roomFootprint,roomShell=buildingNodePreviews::roomShell,editRoomLayout=::editRoomSegmentLayout))
            button(l.x,bodyY+bodyHeight-42,listWidth,"preview3d") {
                storeyProperties=true;buildingGraphView.inspectorSections.setExpanded("3d",true);rebuildWidgets();buildingGraphCanvas?.revealSection("3d")
            }
            button(l.x,bodyY+bodyHeight-20,listWidth,if(showLayout) "panel_storey_properties" else "panel_storey_layout") {
                storeyProperties=!storeyProperties
                if(storeyProperties) buildingGraphView.inspectorSections.setExpanded("properties",true)
                rebuildWidgets()
                if(storeyProperties) buildingGraphCanvas?.revealSection("properties")
            }.active=!split
        }
    }
    private val buildLeftWidth get()=if(classicWidth<500) 151 else 178
    private val buildCoordinateSpacing get()=if(classicHeight<260) 29 else 32
    private val buildActionColumns get()=if(classicWidth-24-buildLeftWidth>=224) 4 else 2
    private val buildSummaryY get()=101+((4+buildActionColumns-1)/buildActionColumns)*22+44+if(ClientState.snapshot.creative) 22 else 0
    private fun moduleGridLayout():IntArray {
        val width=classicWidth;val height=classicHeight
        val availableWidth=(width-194).coerceAtLeast(8)
        val availableHeight=(height-196).coerceAtLeast(8)
        val cellSize=minOf(32,availableWidth/module.width,availableHeight/module.height).coerceAtLeast(1)
        return intArrayOf(174+(availableWidth-module.width*cellSize)/2,126+(availableHeight-module.height*cellSize)/2,cellSize)
    }
    internal fun moduleCellPoint(cell:P):Pair<Double,Double> {
        val (gx,gy,s)=moduleGridLayout()
        return gx+(cell.x+0.5)*s+classicOffsetX to gy+(module.height-cell.y-0.5)*s+classicOffsetY
    }
    private fun plateGridLayout():IntArray {
        val width=classicWidth;val height=classicHeight
        val minX=building.wings.minOf { it.x };val minZ=building.wings.minOf { it.z }
        val spanX=building.wings.maxOf { it.x+it.width }-minX
        val spanZ=building.wings.maxOf { it.z+it.length }-minZ
        val availableWidth=(width-230).coerceAtLeast(1);val availableHeight=(height-142).coerceAtLeast(1)
        val cellSize=minOf(16,availableWidth/spanX,availableHeight/spanZ).coerceAtLeast(1)
        return intArrayOf(213+(availableWidth-spanX*cellSize)/2,86+(availableHeight-spanZ*cellSize)/2,cellSize,minX,minZ)
    }
    private fun grid(g:GuiGraphics) {
        val (gx,gy,s)=moduleGridLayout()
        for(y in 0 until module.height) for(x in 0 until module.width) {
            val p=P(x,y,level);val c=module.cells.firstOrNull { it.pos==p }
            val color=when { c==null->ArchitectTheme.blueprint;c.empty->0xff754a3d.toInt();c.flexible->0xff66864c.toInt();c.role=="glass"->0xff7caaa0.toInt();else->ArchitectTheme.brass }
            val cellX=gx+x*s;val cellY=gy+(module.height-1-y)*s
            g.fill(cellX,cellY,cellX+s,cellY+s,ArchitectTheme.shadow)
            g.fill(cellX+1,cellY+1,cellX+s-1,cellY+s-1,color)
            if(s>=12) {
                if(y==0) label(g,x.toString(),cellX+(s-font.width(x.toString()))/2,gy+module.height*s+4,ArchitectTheme.muted)
                if(x==0) label(g,y.toString(),gx-11,cellY+(s-8)/2,ArchitectTheme.muted)
            }
        }
    }
    override fun mouseClicked(x:Double,y:Double,button:Int):Boolean {
        if(dropdownClick(x,y,button)) return true
        if(floorPlanCanvas?.hasOpenMenu()==true) return floorPlanCanvas!!.mouseClicked(x,y,button)
        if(buildingGraphCanvas?.visible==true && buildingGraphCanvas?.hasOpenMenu()==true) return buildingGraphCanvas!!.mouseClicked(x,y,button)
        if(floorPlanCanvas?.mouseClicked(x,y,button)==true) { focused=floorPlanCanvas;return true }
        if(kilaBuildingGraph?.mouseClicked(x,y,button)==true) { focused=kilaBuildingGraph;return true }
        if(graphMode && buildingInspectorVisible && buildingGraphCanvas?.visible==true && buildingGraphCanvas?.mouseClicked(x,y,button)==true) { focused=buildingGraphCanvas;return true }
        if(!graphMode && tab==1 && !module3d && button==0) {
            val x=x-classicOffsetX;val y=y-classicOffsetY
            val (gx,gy,s)=moduleGridLayout()
            val u=((x-gx)/s).toInt();val v=module.height-1-((y-gy)/s).toInt()
            if(x>=gx && y>=gy && u in 0 until module.width && v in 0 until module.height) {
                val p=P(u,v,level);selectedCell=p
                val cells=module.cells.filter { it.pos!=p }.toMutableList()
                if(cellMode!=2) cells+=Cell(p,cellForm,if(cellMode==4) building.palette[role] ?: "minecraft:stone" else "",role,cellTurn,cellMode==1,cellMode==3)
                module=module.copy(cells=cells);return true
            }
        }
        if(!graphMode && tab==3 && button==0) {
            val x=x-classicOffsetX;val y=y-classicOffsetY
            val (gx,gy,size,minX,minZ)=plateGridLayout()
            val u=kotlin.math.floor((x-gx)/size).toInt()+minX;val z=kotlin.math.floor((y-gy)/size).toInt()+minZ
            val p=P(u,level*(building.roomHeight+building.floorThickness),z)
            if(x>=gx && y>=gy && building.wings.any { it.has(u,z) }) { update(building.copy(holes=if(p in building.holes) building.holes-p else building.holes+p));return true }
        }
        return super.mouseClicked(x,y,button)
    }
    override fun renderChrome(g:GuiGraphics) { workspace.render(g,WorldPeek.active) }
    override fun renderWorkspace(g:GuiGraphics) {
        if(WorldPeek.active || graphMode) return
        g.pose().pushPose();g.pose().translate(classicOffsetX.toFloat(),classicOffsetY.toFloat(),0f)
        try { renderClassicWorkspace(g) } finally { g.pose().popPose() }
    }
    private fun renderClassicWorkspace(g:GuiGraphics) {
        val width=classicWidth;val height=classicHeight
        when(tab) {
            1 -> {
                ArchitectTheme.panel(g,4,74,157,(height-110).coerceAtLeast(1))
                ArchitectTheme.viewport(g,163,99,width-171,(height-139).coerceAtLeast(65))
            }
            2 -> ArchitectTheme.panel(g,4,74,width-8,(height-110).coerceAtLeast(1))
            6 -> {
                ArchitectTheme.panel(g,4,74,buildLeftWidth+8,height-110)
                ArchitectTheme.panel(g,12+buildLeftWidth,74,width-buildLeftWidth-16,height-110)
            }
            else -> {
                val controls=if(tab==5) 212 else if(tab==3) 202 else 172
                ArchitectTheme.panel(g,4,74,controls-6,(height-115).coerceAtLeast(120))
                ArchitectTheme.viewport(g,controls+2,74,width-controls-10,(height-116).coerceAtLeast(75))
            }
        }
    }
    override fun renderContents(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        if(WorldPeek.active) return
        val l=workspace
        val result=plan()
        val cost=ui("total_cost",if(result==null) ui("preview_calculating") else result.getOrNull()?.raw ?: "—")
        label(g,cost,l.x,l.bottom+4,ArchitectTheme.brass)
        val hint=ui(if(tab==8 && graphMode) if(floorPlanCanvas?.segmentRoomSelected()==true) "semantic_floorplan_help" else "floorplan_grid_help" else if(graphMode && tab==0) "bg_help" else if(graphMode) "bg_inspector_hint" else "ui_ready")
        val message=ClientState.snapshot.message.ifBlank { hint }
        label(g,font.plainSubstrByWidth(message,(l.innerWidth-font.width(cost)-8).coerceAtLeast(0)),l.x+font.width(cost)+8,l.bottom+4,ArchitectTheme.muted)
        if(graphMode) {
            if(building.graph!=null) kilaBuildingGraph?.syncInspectorSelection(buildingGraphView.selected)
            if(fitBuildingGraph && kilaBuildingGraph!=null) { kilaBuildingGraph!!.fit();fitBuildingGraph=false }
            if(tab==7 && building.graph!=null && buildingDisplayGraph().nodes.none { it.id==buildingGraphView.selected && it.kind in buildingPanelSection.kinds }) {
                val left=l.x+(l.innerWidth*0.25).toInt().coerceIn(102,196)+6
                ArchitectTheme.panel(g,left,l.graphY+24,l.x+l.innerWidth-left,l.graphHeight-24,ui(buildingPanelSection.key))
                label(g,font.plainSubstrByWidth(ui("panel_create_hint"),l.x+l.innerWidth-left-20),left+10,l.graphY+59,ArchitectTheme.muted)
            }
            kilaBuildingGraph?.renderPopupOverlay(g,mx,my,delta)
            return
        }
        g.pose().pushPose();g.pose().translate(classicOffsetX.toFloat(),classicOffsetY.toFloat(),0f)
        try { renderClassicContents(g,mx-classicOffsetX,my-classicOffsetY,delta) } finally { g.pose().popPose() }
    }
    private fun renderClassicContents(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        val width=classicWidth;val height=classicHeight
        if(tab==1) {
            if(!module3d) { grid(g);label(g,font.plainSubstrByWidth(ui("module_origin_hint"),(width-190).coerceAtLeast(1)),170,height-51) }
            else {
                val q=org.joml.Quaternionf().rotationX(Math.toRadians(20.0).toFloat()).rotateY(Math.toRadians(yaw.toDouble()).toFloat())
                val corners=buildList {
                    for(x in listOf(-module.width/2f,module.width/2f)) for(y in listOf(-module.height/2f,module.height/2f)) for(z in listOf(-module.depth/2f,module.depth/2f))
                        add(q.transform(org.joml.Vector3f(x,y,z)))
                }
                val scale=minOf((width-190)/(corners.maxOf { it.x }-corners.minOf { it.x }),(height-168)/(corners.maxOf { it.y }-corners.minOf { it.y }))*0.86f
                g.flush();g.enableScissor(166+classicOffsetX,122+classicOffsetY,width-12+classicOffsetX,height-43+classicOffsetY)
                val pose=g.pose();pose.pushPose();pose.translate((width+155)/2.0,(height+80)/2.0,120.0)
                pose.scale(scale,-scale,scale);pose.mulPose(q);pose.translate(-module.width/2.0,-module.height/2.0,-module.depth/2.0)
                try {
                    val placements=module.cells.filter { !it.empty }.mapNotNull { c ->
                        val f=if(c.block.isEmpty()) lib.forms[c.form] ?: return@mapNotNull null else Form(parts=listOf(Part("copycats:copycat_block")))
                        Placement(c.pos,Geometry.rotate(f.parts,c.turn),if(c.block.isEmpty()) building.palette[c.role] ?: "minecraft:stone" else c.block,groups=f.groups,surfaces=f.groups.associate { it.id to (building.palette[if(it.role=="surface") c.role else it.role] ?: "minecraft:stone") })
                    }.associateBy { it.pos }
                    for(c in placements.values) {
                        pose.pushPose();pose.translate(c.pos.x.toDouble(),c.pos.y.toDouble(),c.pos.z.toDouble())
                        try {
                            val adjacent=PlanMeshNeighbors.at(c.pos,placements)
                            Meshes.draw(c.parts,c.material,pose,g.bufferSource(),15728880,groups=c.groups,surfaces=c.surfaces,neighbors=adjacent.parts,neighborGroups=adjacent.groups,neighborTransparent=adjacent.transparent,textures=CopycatTextureContext.plan(c.pos,placements))
                        } finally { pose.popPose() }
                    }
                    g.flush()
                } finally { pose.popPose();g.disableScissor() }
            };return
        }
        if(tab==2) {
            safe {
                val d=Patterns.distribute(11,building.patterns[wall],lib.modules)
                label(g,"W=11",322,80);label(g,"Rest ${d.remainder}",322,92)
                val scale=(width-16)/11f
                for((i,span) in d.spans.withIndex()) {
                    val left=8+(span.start*scale).toInt();val right=8+((span.start+span.width)*scale).toInt()
                    g.fill(left,height-49,right-1,height-39,if(span.key==building.patterns[wall].filler) 0xff547786.toInt() else if(i%2==0) 0xffad8b52.toInt() else 0xff635e48.toInt())
                    label(g,lib.modules[span.key]?.name?.take(3) ?: "?",left+2,height-48)
                }
            };return
        }
        if(tab==3) {
            val (gx,gy,size,minX,minZ)=plateGridLayout()
            for(r in building.wings) for(x in r.x until r.x+r.width) for(z in r.z until r.z+r.length) {
                val hole=P(x,level*(building.roomHeight+building.floorThickness),z) in building.holes
                val px=gx+(x-minX)*size;val py=gy+(z-minZ)*size
                g.fill(px,py,px+size,py+size,ArchitectTheme.grid)
                g.fill(px+1,py+1,px+size,py+size,if(hole) 0xffa4654f.toInt() else 0xff6e9064.toInt())
                if(mx in px until px+size && my in py until py+size) g.renderOutline(px,py,size,size,ArchitectTheme.brass)
            };return
        }
        val plan=plan() ?: run { label(g,ui("preview_calculating"),8,height-47,ArchitectTheme.muted);return }
        if(plan.isFailure) { label(g,plan.exceptionOrNull()?.message?.take(65) ?: "Invalid design",8,height-47,ArchitectTheme.danger);return }
        val p=plan.getOrThrow()
        if(tab==6) {
            label(g,ui("origin_xyz"),8,78);label(g,ui("dock_xyz"),8,78+buildCoordinateSpacing);label(g,ui("turtle_id_hint"),8,78+2*buildCoordinateSpacing)
            val right=16+buildLeftWidth;val summaryWidth=width-right-8;val summaryY=buildSummaryY
            if(summaryY+22>height-36) return
            val j=ClientState.snapshot.jobs.getOrNull(jobIndex)
            label(g,font.plainSubstrByWidth(j?.let { it.state+": "+it.message } ?: ui("no_job"),summaryWidth),right,summaryY,ArchitectTheme.muted)
            label(g,font.plainSubstrByWidth(ui("plan_cost",p.placements.size,p.raw),summaryWidth),right,summaryY+13,ArchitectTheme.brass)
            val materialRows=((height-36-summaryY-26)/10).coerceIn(0,3)
            p.materials.entries.take(materialRows).forEachIndexed { i,e ->
                label(g,font.plainSubstrByWidth("${Economy.item(e.key).description.string}: ${e.value}",summaryWidth),right,summaryY+26+i*10)
            }
            return
        }
        val previewLeft=if(tab==5) 216 else 178
        g.flush();g.enableScissor(previewLeft+classicOffsetX,76+classicOffsetY,width-10+classicOffsetX,height-44+classicOffsetY)
        val lo=P(p.placements.minOf { it.pos.x },p.placements.minOf { it.pos.y },p.placements.minOf { it.pos.z })
        val hi=P(p.placements.maxOf { it.pos.x }+1,p.placements.maxOf { it.pos.y }+1,p.placements.maxOf { it.pos.z }+1)
        val sx=(hi.x-lo.x).toFloat();val sy=(hi.y-lo.y).toFloat();val sz=(hi.z-lo.z).toFloat()
        val orientation=org.joml.Quaternionf().rotationX(Math.toRadians(25.0).toFloat()).rotateY(Math.toRadians(yaw.toDouble()).toFloat())
        val corners=buildList { for(x in listOf(-sx/2,sx/2)) for(y in listOf(-sy/2,sy/2)) for(z in listOf(-sz/2,sz/2)) add(orientation.transform(org.joml.Vector3f(x,y,z))) }
        val scale=minOf((width-previewLeft-20)/(corners.maxOf { it.x }-corners.minOf { it.x }),(height-126)/(corners.maxOf { it.y }-corners.minOf { it.y }))*0.92f
        val pose=g.pose();pose.pushPose();pose.translate((width+previewLeft-8)/2.0,(height+32)/2.0,120.0)
        pose.scale(scale,-scale,scale);pose.mulPose(orientation);pose.translate(-(lo.x+hi.x)/2.0,-(lo.y+hi.y)/2.0,-(lo.z+hi.z)/2.0)
        val textureCells=p.placements.associateBy { it.pos }
        for(cell in p.placements) {
            if(visibleFloor>=0 && cell.pos.y/(building.roomHeight+building.floorThickness)!=visibleFloor) continue
            if(component==1 && cell.component !in listOf("wall","corner","trim","gable")) continue
            if(component==2 && cell.component !in listOf("floor","ceiling")) continue
            if(component==3 && cell.component!="roof") continue
            pose.pushPose();pose.translate(cell.pos.x.toDouble(),cell.pos.y.toDouble(),cell.pos.z.toDouble())
            if(cell.block.isEmpty()) {
                val adjacent=PlanMeshNeighbors.at(cell.pos,textureCells)
                Meshes.draw(cell.parts,cell.material,pose,g.bufferSource(),15728880,groups=cell.groups,surfaces=cell.surfaces,neighbors=adjacent.parts,neighborGroups=adjacent.groups,neighborTransparent=adjacent.transparent,textures=CopycatTextureContext.plan(cell.pos,textureCells))
            }
            else minecraft!!.blockRenderer.renderSingleBlock(Economy.nativeState(cell.block),pose,g.bufferSource(),15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
            pose.popPose()
        };g.flush();pose.popPose();g.disableScissor()
    }
    override fun mouseScrolled(x:Double,y:Double,dx:Double,dy:Double):Boolean {
        if(buildingGraphCanvas?.hasOpenMenu()==true) { buildingGraphCanvas!!.mouseScrolled(x,y,dx,dy);return true }
        if(kilaBuildingGraph?.hasOpenMenu()==true) { kilaBuildingGraph!!.mouseScrolled(x,y,dx,dy);return true }
        return super.mouseScrolled(x,y,dx,dy)
    }
    override fun removed() { WorldPeek.active=false;BuildingWorldPreview.release(this);kilaBuildingGraph?.dispose();planJob.cancelPending();buildingThumbnailJobs.cancelPending();super.removed() }
    internal fun siteHistory()=buildingUndo.isNotEmpty() to buildingRedo.isNotEmpty()
    internal fun undoSiteEdit()=undoEdit()
    internal fun redoSiteEdit()=redoEdit()
    private fun undoEdit() { if(buildingUndo.isNotEmpty()) { buildingRedo.addLast(building);building=buildingUndo.removeLast();rebuildWidgets() } }
    private fun redoEdit() { if(buildingRedo.isNotEmpty()) { buildingUndo.addLast(building);building=buildingRedo.removeLast();rebuildWidgets() } }
}
