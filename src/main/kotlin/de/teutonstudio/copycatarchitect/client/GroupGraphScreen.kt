package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.network.chat.Component
import java.util.UUID

/** A separate navigation context; document edits still use the parent's validation and history. */
internal class GroupGraphScreen(
    private val parent:EditorScreen,private val groupId:String,
    private val data:()->KilaGraphData,private val commit:(KilaGraphData)->Unit,
    private val insertions:List<KilaInsertion>,private val adopt:(List<String>)->Unit,
    private val types:Map<String,String>,
    private val inspector:(Int,Int,Int,Int,(String)->Unit)->AbstractWidget,
    private val selectMember:(String)->Unit,
    private val removeMember:(String)->Unit,
    private val shapePreview:((String)->ShapeNodePreview?)?=null,
    private val buildingPreview:((String)->BuildingNodePreview?)?=null,
    private val groups:()->List<MaterialGroup> ={ DEFAULT_GROUPS },
    private val surfaces:()->Map<String,String> ={ emptyMap() },
    private val undo:()->Unit={},private val redo:()->Unit={}
):EditorScreen(tr("group_graph")) {
    private val state=KilaGraphState()
    private var graphInspector=true
    private var showInspector=true
    private var terminal=""
    private var nodeInspector:AbstractWidget?=null
    internal lateinit var editor:KilaGraphEditor;private set
    private val group get()=data().nodeGroups.first { it.id==groupId }
    private val face get()=checkNotNull(group.graphInterface)
    private val memberPositions get()=face.positions ?: checkNotNull(GraphGroups.captureLayouts(data().nodeGroups,
        data().nodes.associate { it.id to GraphGroupPosition(it.x,it.y) }).first { it.id==groupId }.graphInterface?.positions)
    private var inspectorX=0
    private var knownNodes=data().nodes.mapTo(hashSetOf()) { it.id }
    private val workspace get()=PrinterWorkspaceLayout(width,height,topTabs=true)
    override fun titleAvailableWidth()=width-200

    override fun init() {
        if(data().nodeGroups.none { it.id==groupId && it.graphInterface!=null }) { onClose();return }
        val added=data().nodes.filter { it.id !in knownNodes && data().nodeGroups.none { group -> it.id in group.nodes } }.map { it.id }
        if(added.isNotEmpty()) adopt(added)
        knownNodes=data().nodes.mapTo(hashSetOf()) { it.id }
        if(::editor.isInitialized) editor.dispose()
        clearWidgets()
        val l=workspace
        inspectorX=l.panelX
        addRenderableWidget(ArchitectTopTab(l.left+12,l.top-17,Component.literal(group.name),"G",true) {})
        addRenderableWidget(PrinterEdgeTab(l.left-17,l.top+12,tr("group_graph_back"),"‹",selected={false}) { onClose() })
        addRenderableWidget(PrinterEdgeTab(l.left+l.width,l.top+12,tr("printer_inspector"),"I",true,{showInspector}) { showInspector=!showInspector;rebuild() })
        button(l.x,l.y,38,"undo") { undo();rebuild() }
        button(l.x+42,l.y,38,"redo") { redo();rebuild() }
        button(l.x+84,l.y,54,"fit_graph") { editor.fit() }
        button(l.x+142,l.y,54,"remove") { safe { if(state.selected in group.nodes) { removeMember(state.selected);graphInspector=true;state.selected="";rebuild() } } }
        if(showInspector) button(inspectorX+4,l.graphY+2,l.panelWidth-8,"group_graph_interface") { graphInspector=true;rebuild() }
        val wrapped=insertions.map { item -> item.copy(insert={ x,y ->
            val before=data().nodes.map { it.id }.toSet()
            val anchor=data().nodes.first { it.id==group.nodes.first() }
            val local=memberPositions.first()
            item.insert((x+anchor.x-local.x).coerceIn(-8192,8192),(y+anchor.y-local.y).coerceIn(-8192,8192))
            val added=data().nodes.filter { it.id !in before && data().nodeGroups.none { group -> it.id in group.nodes } }.map { it.id }
            if(added.isNotEmpty()) adopt(added)
            knownNodes=data().nodes.mapTo(hashSetOf()) { it.id }
        }) }
        editor=addRenderableWidget(KilaGraphEditor(l.x,l.graphY,l.graphWidth(showInspector),l.graphHeight,this,state,data,::select,commit,::failure,
            insertions=wrapped,nodePreview=shapePreview,buildingNodePreview=buildingPreview,previewGroups=groups,previewSurfaces=surfaces,groupScope=groupId,
            selectSource={ (nodeInspector as? NodeGraphCanvas)?.openSourceSelection(it) },
            rotateSource={ id,axis,turns -> (nodeInspector as? NodeGraphCanvas)?.rotateSource(id,axis,turns) }))
        nodeInspector=addRenderableWidget(inspector(inspectorX,l.graphY+24,l.panelWidth,l.graphHeight-24) { id -> select(id);editor.selectNode(id,true) }).apply { visible=showInspector && !graphInspector }
        if(showInspector && graphInspector) interfaceWidgets()
    }
    private fun rebuild() { init() }
    private fun failure(message:String) { ClientState.snapshot=ClientState.snapshot.copy(message=message) }
    private fun select(id:String) {
        if(id.startsWith("@input:") || id.startsWith("@output:")) {
            terminal=id.substringAfter(':');graphInspector=true;rebuild()
        } else if(group.nodes.contains(id)) { selectMember(id);if(graphInspector) { graphInspector=false;rebuild() } }
    }
    private fun edit(next:GraphGroupInterface) {
        safe {
        val original=data()
        val edited=original.nodeGroups.first { it.id==groupId }.copy(graphInterface=next.copy(positions=next.positions ?: memberPositions))
        commit(GroupGraphInterfaces.withInterface(original,edited))
        rebuild()
        }
    }
    private fun interfaceWidgets() {
        val x=inspectorX+8;val w=workspace.panelWidth-16;val top=workspace.graphY+24
        val entries=face.inputs.map { it.id to "→ ${it.name}" }+face.outputs.map { it.id to "← ${it.name}" }
        if(entries.none { it.first==terminal }) terminal=entries.firstOrNull()?.first.orEmpty()
        choice(x,top+20,w,entries.firstOrNull { it.first==terminal }?.second ?: ui("group_no_ports"),entries.map { (id,name) -> name to { terminal=id;rebuild() } })
        val input=face.inputs.firstOrNull { it.id==terminal }
        val output=face.outputs.firstOrNull { it.id==terminal }
        val type=input?.type ?: output?.type
        if(type!=null) {
            choice(x,top+40,w,types[type] ?: type,types.map { (key,label) -> label to {
                if(input!=null) edit(face.copy(inputs=face.inputs.map { if(it.id==terminal) it.copy(type=key,targets=emptyList()) else it }))
                else edit(face.copy(outputs=face.outputs.map { if(it.id==terminal) it.copy(type=key,member=-1) else it }))
            } })
            button(x,top+60,(w-4)/2,"group_rename_port") {
                EditorValueInput.text(tr("group_port_name"),input?.name ?: output!!.name,64) { name ->
                    if(name.isNotBlank()) edit(face.copy(inputs=face.inputs.map { if(it.id==terminal) it.copy(name=name) else it },outputs=face.outputs.map { if(it.id==terminal) it.copy(name=name) else it }))
                }
            }
            button(x+(w-4)/2+4,top+60,(w-4)/2,"remove") {
                edit(face.copy(inputs=face.inputs.filter { it.id!=terminal },outputs=face.outputs.filter { it.id!=terminal }))
            }
            if(output!=null) {
                val nodes=data().nodes.associateBy { it.id }
                val choices=group.nodes.withIndex().filter { nodes[it.value]?.outputPorts()?.firstOrNull()?.type==type }
                choice(x,top+80,w,group.nodes.getOrNull(output.member)?.let { nodes[it]?.title } ?: ui("disconnected"),
                    listOf(ui("disconnected") to { edit(face.copy(outputs=face.outputs.map { if(it.id==terminal) it.copy(member=-1) else it })) })+
                    choices.map { (index,id) -> nodes.getValue(id).title to { edit(face.copy(outputs=face.outputs.map { if(it.id==terminal) it.copy(member=index) else it })) } })
            }
        }
        button(x,top,(w-4)/2,"group_add_input") {
            EditorValueInput.text(tr("group_port_name"),ui("group_input"),64) { name ->
                if(name.isNotBlank()) {
                    val id=UUID.randomUUID().toString();terminal=id
                    val positions=memberPositions
                    edit(face.copy(inputs=face.inputs+GraphGroupInput(id,name,types.keys.first(),x=(positions.minOf { it.x }-240).coerceIn(-8192,8192),y=(positions.minOf { it.y }+face.inputs.size*100).coerceIn(-8192,8192))))
                }
            }
        }
        button(x+(w-4)/2+4,top,(w-4)/2,"group_add_output") {
            EditorValueInput.text(tr("group_port_name"),ui("group_output"),64) { name ->
                if(name.isNotBlank()) {
                    val id=UUID.randomUUID().toString();terminal=id
                    val positions=memberPositions
                    edit(face.copy(outputs=face.outputs+GraphGroupOutput(id,name,types.keys.first(),x=(positions.maxOf { it.x }+250).coerceIn(-8192,8192),y=(positions.minOf { it.y }+face.outputs.size*100).coerceIn(-8192,8192))))
                }
            }
        }
    }
    override fun renderContents(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        val l=workspace
        label(g,font.plainSubstrByWidth(group.name,l.innerWidth-204),l.x+202,l.y+5,ArchitectTheme.brass)
        label(g,font.plainSubstrByWidth(ClientState.snapshot.message.ifBlank { ui("ui_ready") },l.innerWidth),l.x,l.bottom+4,ArchitectTheme.muted)
        if(showInspector && graphInspector && height>280) {
            val text=font.split(tr("group_interface_hint"),l.panelWidth-24)
            text.forEachIndexed { i,line -> g.drawString(font,line,inspectorX+12,l.graphY+132+i*11,ArchitectTheme.muted,false) }
        }
        editor.renderPopupOverlay(g,mx,my,delta)
    }
    override fun renderChrome(g:GuiGraphics) { workspace.render(g,false) }
    override fun renderWorkspace(g:GuiGraphics) {
        val l=workspace
        if(showInspector && graphInspector) ArchitectTheme.panel(g,l.panelX,l.graphY,l.panelWidth,l.graphHeight)
    }
    override fun render(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        if(data().nodeGroups.none { it.id==groupId && it.graphInterface!=null }) { onClose();return }
        super.render(g,mx,my,delta)
    }
    override fun keyPressed(key:Int,scan:Int,mod:Int):Boolean {
        if(dropdownKey(key)) return true
        if(mod and 2!=0 && key in setOf(89,90)) {
            if(key==89 || mod and 1!=0) redo() else undo()
            rebuild();return true
        }
        if(key==256 && !editor.hasOpenMenu()) { onClose();return true }
        return super.keyPressed(key,scan,mod)
    }
    override fun mouseClicked(x:Double,y:Double,button:Int):Boolean {
        if(dropdownClick(x,y,button)) return true
        return super.mouseClicked(x,y,button)
    }
    override fun onClose() { Minecraft.getInstance().setScreen(parent) }
    override fun removed() { if(::editor.isInitialized) editor.dispose();super.removed() }
}
