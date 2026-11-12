package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.*
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.UUID

/** Persistent references use the same native CC chrome and KilaGraph ports as the surrounding editors. */
class VersionedGraphScreen(private val parent:Screen,initial:GraphDocument,selection:Set<String> = emptySet(),
    initialDefinitions:Map<String,GraphDocument> = ClientState.snapshot.library.graphs,private val onApply:((GraphPackage)->Unit)?=null,initialResources:GraphPackage?=null):EditorScreen(tr("versioned_graphs")) {
    private var localForms=initialResources?.forms.orEmpty()
    private var localModules=initialResources?.modules.orEmpty()
    private fun resources():Library=ClientState.snapshot.library.let { it.copy(forms=(it.forms+localForms).toMutableMap(),modules=(it.modules+localModules).toMutableMap()) }
    private var document=initial
    private var definitions=initialDefinitions.toMap()
    private val state=KilaGraphState().apply { this.selection=selection }
    private var canvas:KilaGraphEditor?=null
    private var resolvedDocument:GraphDocument?=null
    private var resolvedDefinitions:Map<String,GraphDocument>?=null
    private var resolved:Result<ResolvedGraph>?=null
    private val shapePreviews=mutableMapOf<String,ShapeNodePreview?>()
    private val buildingPreviews=mutableMapOf<String,BuildingNodePreview?>()
    private fun resolution():ResolvedGraph {
        if(resolvedDocument!==document || resolvedDefinitions!==definitions) {
            resolvedDocument=document;resolvedDefinitions=definitions
            resolved=runCatching { VersionedGraphs.preview(document,definitions) }
            shapePreviews.clear();buildingPreviews.clear()
        }
        return resolved!!.getOrThrow()
    }
    private data class Edit(val document:GraphDocument,val definitions:Map<String,GraphDocument>)
    private val undo=ArrayDeque<Edit>();private val redo=ArrayDeque<Edit>()
    private var editingName=false
    private fun checkpoint() { undo.addLast(Edit(document,definitions));if(undo.size>100) undo.removeFirst();redo.clear() }
    private fun commit(next:GraphDocument,validate:Boolean=true) {
        if(validate) VersionedGraphs.validate(next)
        checkpoint();document=next.copy(version=0);rebuildWidgets()
    }
    private fun ids()=document.shape?.nodes?.map { it.id } ?: document.building!!.nodes.map { it.id }
    private fun inputs(id:String)=VersionedGraphs.displayInputs(document,id)
    private fun outputs(id:String)=VersionedGraphs.displayOutputs(document,id)
    private fun diagram():KilaGraphData {
        val native=ids().map { id ->
            val shape=document.shape?.nodes?.firstOrNull { it.id==id };val building=document.building?.nodes?.firstOrNull { it.id==id }
            val name=shape?.let { ui(GraphModels.shape(it.kind).labelKey) } ?: building?.name?.takeIf(String::isNotBlank) ?: building?.let { ui(GraphModels.building(it.kind).labelKey) }.orEmpty()
            KilaNode(id,name,shape?.x ?: building!!.x,shape?.y ?: building!!.y,inputs(id).map { it.name },
                deletable=shape?.kind!=NodeKind.OUTPUT && building?.kind!=BuildingKind.BUILDING && id!="empty_wall",inputTypes=inputs(id).map { it.type },outputs=outputs(id).map { KilaOutput(it.name,it.type) })
        }
        val refs=document.references.map { ref -> KilaNode(ref.id,definitions[ref.reference.key]?.name ?: ref.reference.graphId,ref.x,ref.y,inputs(ref.id).map { it.name },
            inputTypes=inputs(ref.id).map { it.type },outputs=outputs(ref.id).map { KilaOutput(it.name,it.type) },sourceLabel=ref.reference.key) }
        val links=document.connections.mapNotNull { c ->
            val slot=inputs(c.target).indexOfFirst { it.id==c.port };val source=outputs(c.source).indexOfFirst { it.id==c.sourcePort }
            if(slot<0 || source<0) null else KilaLink(c.source,c.target,slot,source)
        }
        return KilaGraphData(native+refs,links)
    }
    private fun editDiagram(data:KilaGraphData) {
        val known=data.nodes.associateBy { it.id }
        val removed=(ids()+document.references.map { it.id }).filter { it !in known }.toSet()
        require(document.shape?.nodes.orEmpty().none { it.id in removed && it.kind==NodeKind.OUTPUT } && document.building?.nodes.orEmpty().none { it.id in removed && (it.kind==BuildingKind.BUILDING || it.id=="empty_wall") }) { "Keep the result and Empty wall" }
        // Obsolete named ports remain in the document, even though KilaGraph cannot draw them.
        val oldUnresolved=document.connections.filter { c -> c.target !in removed && c.source !in removed && (inputs(c.target).none { it.id==c.port } || outputs(c.source).none { it.id==c.sourcePort }) }
        val wires=data.links.map { link -> DiagramConnection(link.source,link.target,inputs(link.target)[link.slot].id,outputs(link.source)[link.sourceSlot].id) }+oldUnresolved
        val next=document.copy(shape=document.shape?.let { g -> g.copy(nodes=g.nodes.filter { it.id in known }.map { it.copy(x=known.getValue(it.id).x,y=known.getValue(it.id).y) },nodeGroups=GraphGroups.prune(g.nodeGroups,known.keys)) },
            building=document.building?.let { g -> g.copy(nodes=g.nodes.filter { it.id in known }.map { it.copy(x=known.getValue(it.id).x,y=known.getValue(it.id).y) },nodeGroups=GraphGroups.prune(g.nodeGroups,known.keys)) },
            references=document.references.filter { it.id in known }.map { it.copy(x=known.getValue(it.id).x,y=known.getValue(it.id).y) },connections=wires)
        commit(next,oldUnresolved.isEmpty() && runCatching { VersionedGraphs.validate(document) }.isSuccess)
    }
    private data class Upload(val payload:String,val manifest:GraphTransferManifest,val chunks:List<String>,var waiting:Snapshot?=null,var active:Boolean=false,var sentAt:Long=0)
    private var upload:Upload?=null
    private fun save() {
        val lib=resources()
        val bundle=GraphPackages.export(document,definitions,lib.forms,lib.modules)
        val payload=Json.encode(bundle);val command=Command("saveGraphPackage",payload)
        if(runCatching { CommandTransport.encode(Json.encode(command)) }.isSuccess) { ClientState.send(command);return }
        require(payload.length<=GraphPackageTransfers.MAX_CHARACTERS) { "Graph package exceeds the library storage limit" }
        val pending=upload?.takeIf { it.payload==payload } ?: run {
            val chunks=GraphPackageTransfers.split(payload)
            Upload(payload,GraphTransferManifest(UUID.randomUUID().toString(),payload.length,chunks.size,GraphPackageTransfers.digest(payload)),chunks)
        }
        upload=pending;pending.waiting=ClientState.snapshot;pending.active=true;pending.sentAt=System.currentTimeMillis()
        ClientState.send(Command("graphImportBegin",Json.encode(pending.manifest)))
    }
    private fun uploadTick() {
        val pending=upload?.takeIf { it.active } ?: return
        val now=System.currentTimeMillis()
        if(now-pending.sentAt>10000) { pending.active=false;return }
        if(ClientState.snapshot===pending.waiting || now-pending.sentAt<70) return
        val progress=ClientState.snapshot.graphTransfer
        if(progress?.id!=pending.manifest.id) return
        pending.waiting=ClientState.snapshot;pending.sentAt=now
        val next=progress.missing.firstOrNull()
        if(next==null) {
            pending.active=false
            ClientState.send(Command("graphImportFinish",key=pending.manifest.id,turn=1))
        } else ClientState.send(Command("graphImportChunk",pending.chunks[next],key=pending.manifest.id,slot=next))
    }
    private fun publish() {
        val next=VersionedGraphs.nextVersion(document,definitions)
        val lib=resources()
        GraphPackages.export(next,definitions,lib.forms,lib.modules)
        checkpoint();document=next;definitions=definitions+(next.key to next);save();rebuildWidgets()
    }
    private fun extract() {
        val next=VersionedGraphs.extract(document,state.selection,UUID.randomUUID().toString(),document.name.take(48)+" · "+ui("subgraph"),"ref_"+UUID.randomUUID().toString().take(12))
        checkpoint();definitions=definitions+(next.definition.key to next.definition);document=next.parent.copy(version=0);state.selection=setOf(next.reference.id);state.selected=next.reference.id;rebuildWidgets()
    }
    private fun shapePreview(id:String):ShapeNodePreview? {
        if(document.mode!=GraphMode.FORM) return null
        return runCatching {
            val resolved=resolution()
            val port=outputs(id).firstOrNull { it.type=="shape" } ?: return null
            val source=resolved.nodeOutputs[id]?.get(port.id) ?: return null
            ShapeNodePreview(parts=GraphCompiler.intermediate(resolved.shape!!,source,document.materialGroups))
        }.getOrElse { ShapeNodePreview(error=it.message.orEmpty()) }
    }
    private fun buildingPreview(id:String):BuildingNodePreview? {
        if(document.mode!=GraphMode.BUILDING) return null
        return runCatching {
            val resolved=resolution();val graph=resolved.building!!
            val source=resolved.nodeOutputs[id]?.values?.firstOrNull() ?: return null
            val n=graph.nodes.first { it.id==source };val lib=resources()
            BuildingNodePreview(n,runCatching { BuildingGraphCompiler.Evaluation(graph,document.palette,lib.forms,lib.modules).preview(source) })
        }.getOrNull()
    }
    private fun patchShape(node:GraphNode) { commit(document.copy(shape=document.shape!!.copy(nodes=document.shape!!.nodes.map { if(it.id==node.id) node else it }))) }
    private fun patchBuilding(node:BuildingNode) { commit(document.copy(building=document.building!!.copy(nodes=document.building!!.nodes.map { if(it.id==node.id) node else it }))) }
    internal fun documentSnapshot()=document
    internal fun definitionsSnapshot()=definitions
    internal fun requestVersionChange(id:String,next:GraphDocument) {
        val ref=document.references.single { it.id==id }
        val changes=VersionedGraphs.compare(ref.ports,next.ports)
        val text=changes.joinToString("; ") { "${it.id}: ${it.before?.type ?: "+"} → ${it.after?.type ?: "−"}" }.ifBlank { ui("graph_ports_unchanged") }
        minecraft?.setScreen(GraphVersionConfirmScreen({ accepted ->
            if(accepted) safe { commit(VersionedGraphs.switchVersion(document,id,next),false) }
            minecraft?.setScreen(this)
        },text))
    }
    private fun editOptions():List<Pair<String,()->Unit>> {
        val id=state.selected
        val shape=document.shape?.nodes?.firstOrNull { it.id==id };val building=document.building?.nodes?.firstOrNull { it.id==id }
        val options=mutableListOf<Pair<String,()->Unit>>()
        fun text(label:String,value:String,apply:(String)->Unit) { options+=label to { EditorValueInput.text(Component.literal(label),value,128,apply={ v -> apply(v) }) } }
        fun number(label:String,value:Int,range:IntRange,apply:(Int)->Unit) { options+=label to { EditorValueInput.integer(Component.literal(label),value,range,apply) } }
        if(shape?.kind==NodeKind.INPUT) {
            text(ui("choose_base_copycat"),shape.part.source) { value -> patchShape(shape.copy(part=shape.part.copy(source=value))) }
            number(ui("size",shape.part.size),shape.part.size,1..8) { value -> patchShape(shape.copy(part=shape.part.copy(size=value))) }
        }
        if(shape?.kind in setOf(NodeKind.INPUT,NodeKind.ROTATION,NodeKind.ROTATE)) {
            val n=shape!!
            number(ui("rotation_angle",n.quarterTurns*90),n.quarterTurns,0..3) { value -> patchShape(n.copy(quarterTurns=value)) }
        }
        if(building!=null) {
            text(ui("graph_name"),building.name) { value -> patchBuilding(building.copy(name=value)) }
            if(building.kind==BuildingKind.INT) number(ui("bg_int"),building.countValue ?: 0,Int.MIN_VALUE..Int.MAX_VALUE) { value -> patchBuilding(building.copy(countValue=value)) }
            if(building.kind in setOf(BuildingKind.SEGMENT,BuildingKind.SEGMENT_LAYOUT)) {
                number(ui("bg_dimension_length"),building.length,1..128) { value -> patchBuilding(building.copy(length=value)) }
                number(ui("bg_dimension_height"),building.height,1..128) { value -> patchBuilding(building.copy(height=value)) }
                number(ui("bg_dimension_depth"),building.depth,1..128) { value -> patchBuilding(building.copy(depth=value)) }
            }
        }
        document.ports.forEach { port -> options+=(ui("graph_port_name")+": "+port.name) to {
            EditorValueInput.text(tr("graph_port_name"),port.name,64,apply={ value -> commit(document.copy(ports=document.ports.map { if(it.id==port.id) it.copy(name=value) else it })) })
        } }
        if(id in ids()+document.references.map { it.id }) {
            VersionedGraphs.outputs(document,id).forEach { port -> options+=(ui("graph_expose_output")+": "+port.name) to {
                val portId="out_"+UUID.randomUUID().toString().take(12)
                commit(document.copy(ports=document.ports+GraphInterfacePort(portId,port.name,port.type,GraphPortDirection.OUTPUT),outputBindings=document.outputBindings+GraphOutputBinding(portId,GraphOutput(id,port.id))))
            } }
            VersionedGraphs.inputs(document,id).filter { p -> document.inputBindings.none { b -> GraphSocket(id,p.id) in b.targets } }.forEach { port ->
                val wire=document.connections.firstOrNull { it.target==id && it.port==port.id }
                val source=wire?.let { GraphOutput(it.source,it.sourcePort) }
                val types=if(source!=null) listOf(VersionedGraphs.outputType(document,source)) else VersionedGraphs.socketTypes(document,GraphSocket(id,port.id)).sorted()
                types.forEach { type -> options+=(ui("graph_expose_input")+": "+port.name+" · "+type) to {
                    val portId="in_"+UUID.randomUUID().toString().take(12)
                    commit(document.copy(connections=document.connections.filter { it.target!=id || it.port!=port.id },ports=document.ports+GraphInterfacePort(portId,port.name,type,GraphPortDirection.INPUT),
                        inputBindings=document.inputBindings+GraphInputBinding(portId,listOf(GraphSocket(id,port.id)),source)))
                } }
            }
        }
        return options
    }
    override fun init() {
        canvas?.dispose();resetDropdown();editingName=false
        definitions=definitions+ClientState.snapshot.library.graphs
        val compact=width<700;val buttonWidth=if(compact) 69 else 92
        field(8,28,(width-24-buttonWidth*2).coerceAtLeast(70),document.name).setResponder { if(!editingName) { checkpoint();editingName=true };document=document.copy(name=it,version=0) }
        button(width-12-buttonWidth*2,28,buttonWidth,"save") { safe { save() } }
        button(width-8-buttonWidth,28,buttonWidth,"close") { onClose() }
        val applyOption=onApply?.let { action -> listOf(ui("apply") to { safe { val lib=resources();val bundle=GraphPackages.export(document,definitions,lib.forms,lib.modules);action(bundle);minecraft?.setScreen(parent) } }) }.orEmpty()
        choice(8,51,buttonWidth,ui("document"),applyOption+listOf(
            ui("graph_publish") to { safe { publish() } },
            ui("export") to { safe { val lib=resources();minecraft?.keyboardHandler?.clipboard=Json.encode(GraphPackages.export(document,definitions,lib.forms,lib.modules)) } },
            ui("import") to { safe {
                val text=minecraft?.keyboardHandler?.clipboard.orEmpty();require(text.length<=GraphPackageTransfers.MAX_CHARACTERS)
                val bundle=Json.decode<GraphPackage>(text);val lib=resources()
                GraphPackages.checkConflicts(bundle,definitions,lib.forms,lib.modules)
                checkpoint();localForms=localForms+bundle.forms;localModules=localModules+bundle.modules;document=bundle.root;definitions=definitions+bundle.graphs+if(bundle.root.version>0) mapOf(bundle.root.key to bundle.root) else emptyMap()
                ClientState.send(Command("importGraphPackage",text));rebuildWidgets()
            } })+editOptions())
        button(12+buttonWidth,51,buttonWidth,"graph_extract") { safe { extract() } }.active=state.selection.isNotEmpty()
        val saved=definitions.values.filter { it.mode==document.mode }
        choice(16+buttonWidth*2,51,buttonWidth,ui("graph_reference"),saved.map { d -> "${d.name} @${d.version}" to {
            safe { val ref=GraphReferenceNode("ref_"+UUID.randomUUID().toString().take(12),GraphVersion(d.graphId,d.version),d.ports,200,120);commit(document.copy(references=document.references+ref)) }
        } })
        choice(20+buttonWidth*3,51,buttonWidth,ui("load"),
            ClientState.snapshot.library.graphDrafts.values.filter { it.mode==document.mode }.map { d -> "${d.name} · ${ui("graph_draft")}" to { safe { commit(d,false);state.fitted=false } } }+
            saved.map { d -> "${d.name} @${d.version}" to { minecraft?.setScreen(VersionedGraphScreen(this,d,initialDefinitions=definitions)) } })
        button(8,75,buttonWidth,"undo") { if(undo.isNotEmpty()) { redo.addLast(Edit(document,definitions));val old=undo.removeLast();document=old.document;definitions=old.definitions;rebuildWidgets() } }.active=undo.isNotEmpty()
        button(12+buttonWidth,75,buttonWidth,"redo") { if(redo.isNotEmpty()) { undo.addLast(Edit(document,definitions));val next=redo.removeLast();document=next.document;definitions=next.definitions;rebuildWidgets() } }.active=redo.isNotEmpty()
        val ref=document.references.firstOrNull { it.id==state.selected }
        if(ref!=null) {
            choice(16+buttonWidth*2,75,buttonWidth,ui("graph_version"),saved.filter { it.graphId==ref.reference.graphId }.map { next -> "@${next.version}" to {
                requestVersionChange(ref.id,next)
            } })
            button(20+buttonWidth*3,75,buttonWidth,"graph_definition") { safe {
                val definition=definitions[ref.reference.key] ?: error("Missing graph ${ref.reference.key}")
                minecraft?.setScreen(VersionedGraphScreen(this,definition,initialDefinitions=definitions))
            } }
        }
        canvas=addRenderableWidget(KilaGraphEditor(8,100,width-16,(height-127).coerceAtLeast(70),this,state,::diagram,{ id -> state.selected=id;rebuildWidgets() },
            { next -> safe { editDiagram(next) } },{ message -> ClientState.snapshot=ClientState.snapshot.copy(message=message) },
            selectionChanged={ selected -> state.selection=selected },nodePreview=::shapePreview,previewGroups={ document.materialGroups },buildingNodePreview=::buildingPreview,dataRevision={ document },
            previewAvailable={ id -> outputs(id).any { it.type !in setOf("rotation","ROTATION","INT","MIRRORING") } }))
    }
    override fun renderContents(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        val problem=runCatching { VersionedGraphs.dependencies(document,definitions) }.exceptionOrNull()?.message
        graphSummary(g,"${document.name} · ${if(document.version==0) ui("graph_draft") else "@${document.version}"}",problem ?: document.key)
    }
    override fun tick() {
        super.tick();uploadTick()
        val incoming=ClientState.snapshot.library.graphs
        if(incoming.any { (key,value) -> definitions[key]!=value }) { definitions=definitions+incoming;rebuildWidgets() }
    }
    override fun mouseClicked(x:Double,y:Double,button:Int):Boolean=if(dropdownClick(x,y,button)) true else super.mouseClicked(x,y,button)
    override fun keyPressed(key:Int,scan:Int,modifiers:Int):Boolean=if(dropdownKey(key)) true else super.keyPressed(key,scan,modifiers)
    override fun removed() { canvas?.dispose();super.removed() }
    override fun onClose() { minecraft?.setScreen(parent) }
}

/** Version approval is a regular CC-styled subdialog, including at compact GUI scale. */
private class GraphVersionConfirmScreen(private val answer:(Boolean)->Unit,private val description:String):EditorScreen(tr("graph_version")) {
    override fun init() {
        button(width/2-104,height-47,100,"apply") { answer(true) }
        button(width/2+4,height-47,100,"cancel") { answer(false) }
    }
    override fun renderContents(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        var y=40
        for(line in font.split(Component.literal(description),width-32)) { g.drawString(font,line,16,y,ArchitectTheme.text,false);y+=font.lineHeight+3 }
    }
    override fun onClose() { answer(false) }
}
