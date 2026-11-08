package de.teutonstudio.copycatarchitect.client

import com.lowdragmc.kilagraph.graph.util.KGGraphView
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI
import com.lowdragmc.lowdraglib2.gui.ui.Style
import com.lowdragmc.lowdraglib2.gui.ui.UI
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.data.Tooltips
import com.lowdragmc.lowdraglib2.gui.ui.event.CommandEvents
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent
import com.lowdragmc.lowdraglib2.gui.ui.elements.Menu
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button as KilaButton
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.PortConnectorUI
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandleHelpers
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.command.GraphCommands
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.command.IGraphCommand
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.command.LayoutCommands
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.command.WireCommands
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.node.NodeElement
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.node.PortElement
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.ModelElement
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.Capabilities
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ICustomNodeModel
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.wire.WireModel
import dev.vfyjxf.taffy.style.TaffyPosition
import dev.vfyjxf.taffy.style.FlexDirection
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Tuple
import de.teutonstudio.copycatarchitect.domain.BaseShapes
import de.teutonstudio.copycatarchitect.domain.Part
import de.teutonstudio.copycatarchitect.domain.RotationAxis
import de.teutonstudio.copycatarchitect.domain.GraphGroup
import java.util.UUID
import de.teutonstudio.copycatarchitect.domain.MaterialGroup
import de.teutonstudio.copycatarchitect.domain.DEFAULT_GROUPS
import de.teutonstudio.copycatarchitect.server.Economy
import org.joml.Vector2f
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

/** A view projection only. The editor's domain objects remain the sole saved document. */
data class KilaNode(val id:String,val title:String,val x:Int,val y:Int,val inputs:List<String>,
                    val output:Boolean=true,val deletable:Boolean=true,
                    val inputTypes:List<String> = emptyList(),val outputType:String="shape",val source:String="",val discCut:Int=0,val part:Part?=null,
                    val rotatable:Boolean=false,val rotationAxis:RotationAxis=RotationAxis.NORMAL,val accent:Int?=null,
                    val previewExpandedByDefault:Boolean=false,val outputs:List<KilaOutput> = emptyList(),val sourceLabel:String="")
data class KilaOutput(val label:String,val type:String)
fun KilaNode.outputPorts():List<KilaOutput> = outputs.ifEmpty { if(output) listOf(KilaOutput(" ",outputType)) else emptyList() }
private fun outputPortId(slot:Int)=if(slot==0) "out" else "out$slot"
private fun outputPortSlot(id:String)=if(id=="out") 0 else id.removePrefix("out").toIntOrNull()
data class KilaLink(val source:String,val target:String,val slot:Int,val sourceSlot:Int=0)
data class KilaGraphData(val nodes:List<KilaNode>,val links:List<KilaLink>,val nodeGroups:List<GraphGroup> = emptyList())
data class KilaInsertion(val label:String,val category:String="",val icon:String="",val insert:(Int,Int)->Unit)

/** Native LDLib surfaces use the same integer-pixel casing as the surrounding Minecraft widgets. */
private class KilaMachineTexture(private val kind:Int=0,private val accent:Int=ArchitectTheme.brass):IGuiTexture {
    override fun copy():IGuiTexture=KilaMachineTexture(kind,accent)
    override fun draw(g:GuiGraphics,mx:Float,my:Float,x:Float,y:Float,w:Float,h:Float,partialTick:Float) {
        val px=x.roundToInt();val py=y.roundToInt();val width=w.roundToInt();val height=h.roundToInt()
        when(kind) {
            0 -> ArchitectTheme.panel(g,px,py,width,height)
            1 -> { ArchitectTheme.bevel(g,px,py,width,height,ArchitectTheme.raised);g.fill(px+4,py+4,px+7,py+height-4,accent) }
            2 -> ArchitectTheme.button(g,px,py,width,height)
            3 -> ArchitectTheme.button(g,px,py,width,height,hovered=true)
            4 -> ArchitectTheme.button(g,px,py,width,height,selected=true)
            5 -> ArchitectTheme.inset(g,px,py,width,height)
        }
    }
}

/** Typed sockets remain native drag targets, drawn as square machine terminals. */
private class KilaSocketTexture(private val filled:Boolean,private val tint:Int=ArchitectTheme.green):IGuiTexture {
    override fun copy():IGuiTexture=KilaSocketTexture(filled,tint)
    override fun setColor(color:Int):IGuiTexture=KilaSocketTexture(filled,color)
    override fun draw(g:GuiGraphics,mx:Float,my:Float,x:Float,y:Float,w:Float,h:Float,partialTick:Float) {
        val px=x.roundToInt();val py=y.roundToInt();val width=w.roundToInt();val height=h.roundToInt()
        ArchitectTheme.inset(g,px,py,width,height)
        if(filled) g.fill(px+2,py+2,px+width-2,py+height-2,tint)
        else g.renderOutline(px+2,py+2,(width-4).coerceAtLeast(1),(height-4).coerceAtLeast(1),tint)
    }
}

private val machineSocket=PortConnectorUI(KilaSocketTexture(false),KilaSocketTexture(true))

/** Semantic port types also supply the native quick-info label. */
private object KilaPortTypes {
    private val handles=mutableMapOf<String,TypeHandle>()
    fun of(key:String):TypeHandle=handles.getOrPut(key) {
        val label=key.split('|').joinToString(" / ") { type -> when(type) {
            "shape","COPYCAT" -> ui("port_copycat")
            "rotation" -> ui("port_rotation")
            "WALL" -> ui("bg_segment")
            else -> ui("bg_"+type.lowercase())
        } }
        TypeHandleHelpers.customType(Any::class.java,"copycat_architect:$key",label).also {
            TypeHandleHelpers.setCustomColor(it,when(key) {
                "rotation" -> ArchitectTheme.brass
                "WALL","FLOOR","CEILING","SEGMENT_LAYOUT" -> 0xffff8a27.toInt()
                "ROOM_GEOMETRY" -> 0xff74b4cf.toInt()
                "ROOF_LAYOUT","ROOF" -> 0xffb77865.toInt()
                "ROOM" -> 0xff21b15b.toInt()
                "STOREY" -> 0xffeeeeee.toInt()
                else -> ArchitectTheme.green
            })
        }
    }
}

/** Navigation belongs to the view and never enters document undo, validation or exports. */
class KilaGraphState {
    var selected=""
    var selection:Set<String> = emptySet()
    internal var inspectorSelection:String?=null
    var offsetX=0f
    var offsetY=0f
    var zoom=1f
    var fitted=false
    /** View preferences survive graph edits, screen rebuilds and switching to the inspector. */
    val previewVisibility=NodePreviewVisibility()
    val expandedGroups=mutableSetOf<String>()
}

/**
 * Embeds KilaGraph's real canvas in the existing Minecraft screen. Only supported domain
 * operations are offered; unsupported LDLib variables, subgraphs and resource saving are absent.
 * Every completed command is projected back to the screen's existing validation/undo path.
 */
class KilaGraphEditor(x:Int,y:Int,w:Int,h:Int,screen:Screen,private val state:KilaGraphState,
                      private val data:()->KilaGraphData,private val selected:(String)->Unit,
                      private val commit:(KilaGraphData)->Unit,private val failure:(String)->Unit,
                      private val readOnly:Boolean=false,private val insertions:List<KilaInsertion> = emptyList(),
                      private val selectSource:(String)->Unit={},
                      private val rotateSource:(String,RotationAxis,Int)->Unit={_,_,_->},
                      private val selectionChanged:((Set<String>)->Unit)?=null,
                      private val nodePreview:((String)->ShapeNodePreview?)?=null,
                      private val previewGroups:()->List<MaterialGroup> ={ DEFAULT_GROUPS },
                      private val previewSurfaces:()->Map<String,String> ={ emptyMap() },
                      private val buildingNodePreview:((String)->BuildingNodePreview?)?=null,
                      private val instantiateGroup:(String)->Unit={},private val previewMaterials:()->Boolean={true},
                      private val dataRevision:(()->Any?)?=null,private val previewAvailable:(String)->Boolean={true})
    :AbstractWidget(x,y,w,h,Component.literal("KilaGraph")) {
    private var loadedRevision=dataRevision?.invoke()
    private var loaded=data()
    private lateinit var loadedNodes:Map<String,KilaNode>
    private var pending:KilaGraphData?=null
    private var pendingInsertion:(()->Unit)?=null
    private var pendingSource:String?=null
    private var pendingRotation:Pair<String,Int>?=null
    private var pendingPreview:String?=null
    private var pendingGroupAction:(()->Unit)?=null
    private var groupToggleAnchor:Pair<String,Pair<Double,Double>>?=null
    private lateinit var projection:KilaGroupProjection
    private var forceReload=false
    private var refreshAfterCommand=false
    private val models=linkedMapOf<String,NodeModel>()
    private val ids=hashMapOf<NodeModel,String>()
    private val sourceButtons=hashMapOf<String,UIElement>()
    private val rotationButtons=hashMapOf<Pair<String,Int>,UIElement>()
    private val previewElements=hashMapOf<String,UIElement>()
    private val previewButtons=hashMapOf<String,KilaButton>()
    private val groupButtons=hashMapOf<String,KilaButton>()
    private var lastTick=Long.MIN_VALUE
    private var disposed=false
    private var restoreNavigation=false
    private val canvas=object:KGGraphView() {
        fun applyModelChanges()=updateGraphModelChanges()
        private var additiveClick=false
        override fun onGraphElementMouseDown(element:ModelElement,event:UIEvent) {
            // Grouping uses the same additive Shift selection in every graph editor.
            additiveClick=event.isShiftDown && element.model!=null && element.allowGraphMouseDown(event)
            if(additiveClick) element.model?.let { addSelected(it) }
            super.onGraphElementMouseDown(element,event)
        }
        override fun onDragEnd(event:UIEvent) {
            // Native mouse-up turns a stationary drag back into an exclusive click.
            // Keep the additive Shift selection through that second half of the gesture.
            val additive=additiveClick;additiveClick=false
            if(additive &&
                event.dragHandler.draggingObject is com.lowdragmc.lowdraglib2.nodegraphtookit.gui.GraphView.DragMove &&
                (event.x-event.dragStartX)*(event.x-event.dragStartX)+(event.y-event.dragStartY)*(event.y-event.dragStartY)<1f) return
            super.onDragEnd(event)
        }
        override fun initHeaders() {}
        override fun initPanels() {}
        override fun fitGraphChildren(padding:Float) { fitNative(padding) }
        override fun claimsCommand(command:String)=command==CommandEvents.SELECT_ALL
        override fun createMenu(mouseX:Float,mouseY:Float):TreeBuilder.Menu {
            val menu=TreeBuilder.Menu.start()
            if(!readOnly && insertions.isNotEmpty()) {
                val position=getContentViewContainer().worldToLocalLayoutOffset(Vector2f(mouseX,mouseY))
                fun leaves(target:TreeBuilder.Menu,items:List<KilaInsertion>) {
                    items.forEach { item -> target.leaf(if(item.icon.isEmpty()) IGuiTexture.EMPTY else ItemStackTexture(Economy.item(item.icon)),Component.literal(item.label)) {
                        pendingInsertion={ item.insert(position.x.roundToInt().coerceIn(-8000,8000),position.y.roundToInt().coerceIn(-8000,8000)) }
                    } }
                }
                menu.branch(tr("add_node")) { branch ->
                    insertions.groupBy { it.category }.forEach { (category,items) ->
                        if(category.isEmpty()) leaves(branch,items)
                        else branch.branch(Component.literal(category)) { leaves(it,items) }
                    }
                }
            }
            appendGroupMenu(menu)
            menu.leaf(tr("fit_graph")) { fitGraphChildren() }
            if(!readOnly && getSelected().any { it is WireModel || it is NodeModel && it.isDeletable }) {
                menu.leaf(Component.translatable("selectWorld.delete")) { deleteSelectedElements() }
            }
            return menu
        }
    }
    private val root=UIElement()
    private val ui:ModularUI

    init {
        canvas.layout { it.positionType(TaffyPosition.ABSOLUTE).left(x.toFloat()).top(y.toFloat()).width(w.toFloat()).height(h.toFloat()) }
        // Our bounded documents remain editable at overview zoom; keep their titles and sockets visible.
        canvas.graphView.graphViewStyle { it.lodEnabled(false).gridSize(24f).gridLineWidth(1f).gridLineColor(ArchitectTheme.grid).gridAccentColor(0xff43563d.toInt()) }
        Style.importantPipeline(canvas.style) { it.backgroundTexture(ColorRectTexture(ArchitectTheme.blueprint)) }
        Style.importantPipeline(canvas.graphView.style) { it.backgroundTexture(ColorRectTexture(ArchitectTheme.blueprint)) }
        canvas.header.setDisplay(false)
        canvas.blackboard.setDisplay(false)
        canvas.inspector.setDisplay(false)
        canvas.preview.setDisplay(false)
        root.layout { it.widthPercent(100f).heightPercent(100f) }
        root.addChild(canvas)
        ui=ModularUI(UI.of(root,StylesheetManager.MODERN),Minecraft.getInstance().player)
        canvas.addCommandListener { _,_,_ -> pending=snapshot() }
        canvas.setReadOnly(readOnly)
        load(loaded)
        ui.setScreenAndInit(screen)
    }

    /** Changing the inspector width keeps native selection, focus and drag state alive. */
    fun resizeViewport(w:Int,h:Int) {
        if(width==w && height==h) return
        width=w
        height=h
        canvas.layout { it.width(w.toFloat()).height(h.toFloat()) }
    }

    private class ViewNode(var projection:KilaNode=KilaNode("","",0,0,emptyList())):Node() {
        override fun getDisplayName()=Component.literal(projection.title)
        override fun getNodeWidth()=168f
        override fun onDefinePorts(context:IPortDefinitionContext) {
            projection.inputs.forEachIndexed { index,label ->
                context.addInputPort("in$index",KilaPortTypes.of(projection.inputTypes.getOrNull(index) ?: "shape"))
                    .withDisplayName(Component.literal(label)).withTooltips(Tooltips.of(Component.literal(label))).withConnectorUI(machineSocket)
                    .withoutConfigurator().withoutSerialization().build()
            }
            projection.outputPorts().forEachIndexed { index,port ->
                context.addOutputPort(outputPortId(index),KilaPortTypes.of(port.type))
                    .withDisplayName(Component.literal(port.label)).withConnectorUI(machineSocket).build()
            }
        }
    }

    private inner class ViewGraph:Graph() {
        override fun createGraphModel():CustomGraphModelImpl = object:CustomGraphModelImpl(this) {
            override fun canAssignTo(destination:PortModel,source:PortModel):Boolean {
                if(!super.canAssignTo(destination,source)) return false
                val from=((source.nodeModel as? ICustomNodeModel)?.node as? ViewNode)?.projection ?: return false
                val to=((destination.nodeModel as? ICustomNodeModel)?.node as? ViewNode)?.projection ?: return false
                val slot=destination.portId.removePrefix("in").toIntOrNull() ?: return false
                val sourceSlot=outputPortSlot(source.portId) ?: return false
                return from.outputPorts().getOrNull(sourceSlot)?.type in (to.inputTypes.getOrNull(slot) ?: "shape").split('|')
            }
        }
        override fun getSupportNodes():List<Class<out Node>> = emptyList()
        override fun getSupportTypes():List<TypeHandle> = (projection.display.nodes.flatMap { it.inputTypes+it.outputPorts().map(KilaOutput::type) }+"shape").distinct().map(KilaPortTypes::of)
        override fun getLibrarySupportTypes():List<TypeHandle> = emptyList()
        override fun canExecuteCommand(command:IGraphCommand):Boolean = !readOnly && when(command) {
            is GraphCommands.MoveElementsCommand,is LayoutCommands.AutoLayoutCommand -> true
            is WireCommands.CreateWireCommand -> true
            is GraphCommands.DeleteElementsCommand -> command.elementsToDelete.all {
                it is WireModel || it is NodeModel && ids[it]?.let { id -> projection.display.nodes.find { n -> n.id==id }?.deletable }==true
            }
            else -> false
        }
    }

    private fun load(value:KilaGraphData) {
        val preserve=state.fitted
        models.clear();ids.clear();sourceButtons.clear();rotationButtons.clear();previewElements.clear();previewButtons.clear();groupButtons.clear()
        loaded=value
        loadedNodes=value.nodes.associateBy(KilaNode::id)
        projection=KilaGroupProjection(value,state.expandedGroups)
        val visible=projection.display
        val graph=ViewGraph()
        visible.nodes.forEach { n ->
            val model=graph.graphModel.createNodeModel(ViewNode(n),Vector2f(n.x.toFloat(),n.y.toFloat()))
            model.clearCapabilities()
            model.setCapability(Capabilities.SELECTABLE,true)
            model.setCapability(Capabilities.MOVABLE,!readOnly)
            model.setCapability(Capabilities.DELETABLE,n.deletable && !readOnly)
            model.setColor(n.accent ?: if(n.outputType=="rotation" || projection.groupForView(n.id)!=null) ArchitectTheme.brass else ArchitectTheme.green)
            models[n.id]=model;ids[model]=n.id
        }
        visible.links.forEach { link ->
            val from=models[link.source]?.outputsById?.get(outputPortId(link.sourceSlot))
            val to=models[link.target]?.inputsById?.get("in${link.slot}")
            if(from!=null && to!=null) graph.graphModel.createWire(to,from)
        }
        canvas.loadGraph(graph)
        canvas.blackboard.setDisplay(false);canvas.inspector.setDisplay(false);canvas.preview.setDisplay(false)
        visible.nodes.forEach { n ->
            val element=models[n.id]?.let { canvas.getModelElement(it) } as? NodeElement
            if(element!=null) {
                val accent=n.accent ?: if(n.outputType=="rotation" || projection.groupForView(n.id)!=null) ArchitectTheme.brass else ArchitectTheme.green
                Style.importantPipeline(element.style) { it.backgroundTexture(KilaMachineTexture()).overlayTexture(IGuiTexture.EMPTY) }
                element.nodeStyle.focusOverlay(ColorBorderTexture(2,ArchitectTheme.brass))
                element.nodeTittle?.let { title ->
                    Style.importantPipeline(title.style) { it.backgroundTexture(KilaMachineTexture(1,accent)).tooltips(Component.literal(n.title)) }
                    Style.importantPipeline(title.layout) { it.paddingLeft(11f).paddingRight(5f).paddingVertical(5f) }
                    title.colorLine.setDisplay(false)
                }
                styleNodeChildren(element)
            }
            if(element!=null && projection.groupForView(n.id)!=null) {
                val group=checkNotNull(projection.groupForView(n.id))
                val toggle=KilaButton().setText(ui(if(group.id in state.expandedGroups) "collapse_node_group" else "expand_node_group"))
                toggle.layout { it.height(20f).marginHorizontal(5f).marginVertical(4f) }
                machineButton(toggle,true)
                toggle.style { it.tooltips(Component.literal("${group.name} · ${ui("group_definition")}")) }
                toggle.setOnClick { event -> pendingGroupAction={ toggleGroup(group.id) };event.stopPropagation() }
                element.addChild(toggle);groupButtons[group.id]=toggle
            }
            if(element!=null && (n.source.isNotEmpty() || n.sourceLabel.isNotEmpty())) {
                val button=KilaButton().setText(n.sourceLabel.ifEmpty { ui(BaseShapes.find(n.source,n.discCut).name) })
                button.layout { it.height(if(nodePreview!=null) 18f else 24f).marginHorizontal(5f).marginVertical(if(nodePreview!=null) 3f else 5f) }
                if(n.source.isNotEmpty()) button.addPreIcon(ItemStackTexture(Economy.item(n.source)))
                machineButton(button)
                button.style { it.tooltips(tr(if(n.sourceLabel.isEmpty()) "choose_base_copycat" else "node_saved")) }
                button.setActive(!readOnly)
                button.setOnClick { event -> if(!readOnly) pendingSource=n.id;event.stopPropagation() }
                element.addChild(button)
                sourceButtons[n.id]=button
            }
            if(element!=null && n.rotatable) {
                val rotations=UIElement().layout { it.height(18f).marginHorizontal(5f).marginBottom(5f).flexDirection(FlexDirection.ROW) }
                listOf(-1,2,1).forEach { turns ->
                    val label=when(turns) { -1->"−90°";2->"180°";else->"+90°" }
                    val axis=if(n.rotationAxis==RotationAxis.NORMAL) ui("rotation_normal_generic") else n.rotationAxis.name
                    val rotation=KilaButton().setText(label)
                    rotation.layout { it.width(49f).height(18f).marginRight(3f) }
                    machineButton(rotation)
                    rotation.style { it.tooltips(tr("rotate_about_axis",turns*90,axis)) }
                    rotation.setActive(!readOnly)
                    rotation.setOnClick { event -> if(!readOnly) pendingRotation=n.id to turns;event.stopPropagation() }
                    rotations.addChild(rotation)
                    rotationButtons[n.id to turns]=rotation
                }
                element.addChild(rotations)
            }
            if(element!=null && projection.groupForView(n.id)==null && (nodePreview!=null || buildingNodePreview!=null) && previewAvailable(n.id)) {
                val toggle=KilaButton().setText(previewLabel(n.id))
                toggle.layout { it.height(18f).marginHorizontal(5f).marginBottom(3f) }
                machineButton(toggle,true)
                toggle.style { it.tooltips(tr("preview3d")) }
                // Folding is navigation, so it also remains available on read-only graphs.
                toggle.setOnClick { event -> pendingPreview=n.id;event.stopPropagation() }
                element.addChild(toggle);previewButtons[n.id]=toggle
                if(previewExpanded(n.id)) addPreview(n.id,element)
            }
        }
        restoreNavigation=preserve
        val selection=if(projection.groupForView(state.selected)!=null) setOf(state.selected) else state.selection.ifEmpty { setOf(state.selected) }
        selection.map { projection.viewIdForNode(it) }.distinct().forEach { id -> models[id]?.let { canvas.addSelected(it) } }
    }

    private fun addPreview(id:String,element:UIElement) {
        val preview=object:UIElement() {
            override fun drawBackgroundAdditional(context:GUIContext) {
                val w=sizeWidth.roundToInt()
                val h=sizeHeight.roundToInt()
                if(w<2 || h<2) return
                val lo=localToWorld(Vector2f(positionX,positionY))
                val hi=localToWorld(Vector2f(positionX+sizeWidth,positionY+sizeHeight))
                val view=this@KilaGraphEditor
                if(hi.x<=view.x || lo.x>=view.x+view.width || hi.y<=view.y || lo.y>=view.y+view.height) return
                val px=positionX.roundToInt()
                val py=positionY.roundToInt()
                if(hi.y-lo.y<12f) {
                    ArchitectTheme.inset(context.graphics,px,py,w,h,ArchitectTheme.blueprint)
                    return
                }
                context.enableScissor(positionX,positionY,sizeWidth,sizeHeight)
                try {
                    val shape=nodePreview?.invoke(id)
                    val building=if(nodePreview==null) buildingNodePreview?.invoke(id) else null
                    when {
                        shape!=null -> ShapeNodePreviewRenderer.drawThumbnail(context.graphics,px,py,w,h,shape,previewGroups(),previewSurfaces(),showMaterials=previewMaterials())
                        building!=null -> BuildingNodePreviewRenderer.drawThumbnail(context.graphics,px,py,w,h,building)
                        else -> {
                            ArchitectTheme.inset(context.graphics,px,py,w,h,ArchitectTheme.blueprint)
                            context.graphics.drawCenteredString(Minecraft.getInstance().font,tr("preview_calculating"),px+w/2,py+h/2-4,ArchitectTheme.muted)
                        }
                    }
                } finally {
                    context.graphics.flush()
                    context.disableScissor()
                }
            }
        }
        preview.layout { it.height(64f).marginHorizontal(5f).marginBottom(5f) }
        element.addChild(preview)
        previewElements[id]=preview
    }

    private fun updatePositions(value:KilaGraphData):Boolean {
        if(readOnly || value.links!=loaded.links || value.nodeGroups!=loaded.nodeGroups || value.nodes.size!=loaded.nodes.size) return false
        if(value.nodes.indices.any { i ->
            val previous=loaded.nodes[i]
            val next=value.nodes[i]
            previous!==next && previous.copy(x=next.x,y=next.y)!=next
        }) return false

        // Group proxies are derived positions, so update them along with their visible members.
        projection=KilaGroupProjection(value,state.expandedGroups)
        loaded=value
        loadedNodes=value.nodes.associateBy(KilaNode::id)
        for(node in projection.display.nodes) {
            val model=models.getValue(node.id)
            val position=model.position
            if(position.x!=node.x.toFloat() || position.y!=node.y.toFloat()) {
                model.position=Vector2f(node.x.toFloat(),node.y.toFloat())
            }
        }
        canvas.applyModelChanges()
        return true
    }

    private fun currentSelection()=projection.actualSelection(canvas.getSelected().mapNotNull { ids[it] }.toSet())
    private fun selectedGroups():List<GraphGroup> {
        val selection=currentSelection()
        return loaded.nodeGroups.filter { group -> group.nodes.any { it in selection } }
    }
    private fun groupableSelection():List<String> {
        val selection=currentSelection()
        return loaded.nodes.filter { node -> node.id in selection && node.deletable && loaded.nodeGroups.none { node.id in it.nodes } }.map { it.id }
    }

    private fun appendGroupMenu(menu:TreeBuilder.Menu) {
        if(!readOnly && groupableSelection().isNotEmpty()) {
            menu.leaf(tr("create_node_group")) { pendingGroupAction={ createGroup() } }
        }
        selectedGroups().forEach { group ->
            menu.branch(Component.literal(group.name)) { branch ->
                branch.leaf(tr(if(group.id in state.expandedGroups) "collapse_node_group" else "expand_node_group")) {
                    pendingGroupAction={ toggleGroup(group.id) }
                }
                if(!readOnly) {
                    branch.leaf(tr("rename_node_group")) { pendingGroupAction={ renameGroup(group) } }
                    branch.leaf(tr("ungroup_nodes")) { pendingGroupAction={ commitGroups(loaded.nodeGroups.filter { it.id!=group.id }) } }
                }
            }
        }
        if(!readOnly && loaded.nodeGroups.isNotEmpty()) {
            menu.branch(tr("instantiate_node_group")) { branch ->
                loaded.nodeGroups.distinctBy { it.definitionId.ifEmpty { it.id } }.forEach { group ->
                    branch.leaf(Component.literal(group.name)) { pendingGroupAction={ instantiateGroup(group.id) } }
                }
            }
        }
    }

    private fun commitGroups(groups:List<GraphGroup>) {
        if(readOnly) return
        commit(loaded.copy(nodeGroups=groups));forceReload=true
    }
    private fun createGroup() {
        val members=groupableSelection()
        if(readOnly || members.isEmpty()) return
        EditorValueInput.text(tr("node_group_name"),ui("node_group"),48) { name ->
            commitGroups(loaded.nodeGroups+GraphGroup(UUID.randomUUID().toString(),name,members))
        }
    }
    private fun renameGroup(group:GraphGroup) {
        EditorValueInput.text(tr("node_group_name"),group.name,48) { name ->
            val definition=group.definitionId.ifEmpty { group.id }
            commitGroups(loaded.nodeGroups.map { if(it.definitionId.ifEmpty { it.id }==definition) it.copy(name=name) else it })
        }
    }
    private fun toggleGroup(id:String) {
        groupToggleAnchor=groupTogglePoint(id)?.let { id to it }
        if(!state.expandedGroups.add(id)) state.expandedGroups.remove(id)
        val group=loaded.nodeGroups.firstOrNull { it.id==id }
        if(group!=null) {
            state.selected=projection.groupViewId(id);state.selection=group.nodes.toSet()
            canvas.clearAllSelected();models[state.selected]?.let { canvas.addSelected(it) }
            if(selectionChanged!=null) selectionChanged.invoke(state.selection) else selected(state.selected)
        }
        forceReload=true
    }

    private fun previewExpanded(id:String)=state.previewVisibility.isExpanded(id,loadedNodes[id]?.previewExpandedByDefault==true)
    private fun previewLabel(id:String)="${if(previewExpanded(id)) "▾" else "▸"} ${ui("preview3d")}"

    private fun machineButton(button:KilaButton,active:Boolean=!readOnly) {
        Style.importantPipeline(button.buttonStyle) { it.baseTexture(KilaMachineTexture(2)).hoverTexture(KilaMachineTexture(3)).pressedTexture(KilaMachineTexture(4)) }
        Style.importantPipeline(button.text.textStyle) { it.font(ResourceLocation.withDefaultNamespace("default")).fontSize(9f).textColor(if(active) ArchitectTheme.text else ArchitectTheme.disabled).textShadow(false) }
        Style.importantPipeline(button.style) { it.backgroundTexture(IGuiTexture.EMPTY) }
    }

    private fun styleNodeChildren(element:UIElement) {
        if(element is TextElement) Style.importantPipeline(element.textStyle) { it.font(ResourceLocation.withDefaultNamespace("default")).fontSize(9f).textColor(ArchitectTheme.text).textShadow(false) }
        if(element.hasClass("__node-port-container__") || element.hasClass("__in-out-port-container_input__") || element.hasClass("__in-out-port-container_output__")) {
            Style.importantPipeline(element.style) { it.backgroundTexture(ColorRectTexture(ArchitectTheme.well)) }
        }
        element.children.forEach(::styleNodeChildren)
    }

    private val styledMenus=mutableSetOf<UIElement>()
    private fun styleMenus() {
        fun style(element:UIElement) {
            if(element is Menu<*,*> && styledMenus.add(element)) {
                Style.importantPipeline(element.style) { it.backgroundTexture(KilaMachineTexture()) }
                Style.importantPipeline(element.menuStyle) {
                    it.nodeTexture(KilaMachineTexture(2)).leafTexture(KilaMachineTexture(2))
                        .nodeHoverTexture(KilaMachineTexture(3)).leafHoverTexture(KilaMachineTexture(3))
                }
                element.setTextureProvider { KilaMachineTexture(2) }
                element.setHoverTextureProvider { KilaMachineTexture(3) }
                element.nodeUIs.values.forEach { row ->
                    Style.importantPipeline(row.layout) { it.minHeight(19f).paddingHorizontal(2f) }
                }
                styleNodeChildren(element)
            }
            element.children.toList().forEach(::style)
        }
        root.children.filterIsInstance<Menu<*,*>>().forEach(::style)
        if(!hasOpenMenu()) styledMenus.clear()
    }

    private fun snapshot():KilaGraphData {
        val active=canvas.graph?.graphModel ?: return loaded
        // LDLib retains null tombstones until its next graph update after a delete command.
        val remaining=active.nodeModels.filterNotNull().toSet()
        val nodes=projection.display.nodes.mapNotNull { old -> models[old.id]?.takeIf { it in remaining }?.let { model ->
            old.copy(x=model.position.x.roundToInt(),y=model.position.y.roundToInt())
        } }
        val links=active.wireModels.filterNotNull().mapNotNull { wire ->
            val source=ids[wire.fromPort?.nodeModel] ?: return@mapNotNull null
            val target=ids[wire.toPort?.nodeModel] ?: return@mapNotNull null
            val slot=wire.toPort?.portId?.removePrefix("in")?.toIntOrNull() ?: return@mapNotNull null
            val sourceSlot=wire.fromPort?.portId?.let(::outputPortSlot) ?: return@mapNotNull null
            KilaLink(source,target,slot,sourceSlot)
        }
        return projection.restore(KilaGraphData(nodes,links))
    }

    private fun rememberNavigation() {
        if(restoreNavigation) return
        state.offsetX=canvas.graphView.offsetX;state.offsetY=canvas.graphView.offsetY
        state.zoom=canvas.graphView.scale;state.fitted=true
    }

    private fun afterInput() {
        rememberNavigation()
        val viewSelection=canvas.getSelected().mapNotNull { ids[it] }.toSet()
        val selection=projection.actualSelection(viewSelection)
        val inspectorId=projection.inspectorSelection(viewSelection)
        val changed=selection!=state.selection || inspectorId!=state.selected
        state.selection=selection
        val previous=state.selected
        state.selected=inspectorId
        if(selectionChanged==null && inspectorId!=previous && inspectorId.isNotEmpty()) {
            state.inspectorSelection=inspectorId;selected(inspectorId)
        }
        if(changed) selectionChanged?.invoke(selection)
        val next=pending
        pending=null
        if(next!=null && next!=loaded && !readOnly) {
            try { commit(next) } catch(e:Exception) { failure(e.message.orEmpty()) }
            refreshAfterCommand=true
        }
        val insertion=pendingInsertion
        pendingInsertion=null
        try { if(!readOnly) insertion?.invoke() } catch(e:Exception) { failure(e.message.orEmpty()) }
        val rotation=pendingRotation
        pendingRotation=null
        if(rotation!=null && !readOnly) rotateSource(rotation.first,loadedNodes[rotation.first]?.rotationAxis ?: RotationAxis.NORMAL,rotation.second)
        val source=pendingSource
        pendingSource=null
        // Open the modal only after KilaGraph has finished dispatching the click.
        if(source!=null && !readOnly) selectSource(source)
        pendingPreview?.let { id ->
            val expanded=state.previewVisibility.toggle(id,loadedNodes[id]?.previewExpandedByDefault==true)
            val preview=previewElements[id]
            if(preview!=null) preview.setDisplay(expanded)
            else if(expanded) models[id]?.let { canvas.getModelElement(it) }?.let { addPreview(id,it) }
            previewButtons[id]?.setText(previewLabel(id))
        }
        pendingPreview=null
        val groupAction=pendingGroupAction;pendingGroupAction=null
        try { groupAction?.invoke() } catch(e:Exception) { failure(e.message.orEmpty()) }
        styleMenus()
    }

    /** Only a changed inspector choice may replace the canvas's native multi/wire selection. */
    fun syncInspectorSelection(id:String) {
        // A rebuilt widget already restored the native selection from the shared view state.
        if(state.inspectorSelection==null && canvas.getSelected().isNotEmpty()) state.inspectorSelection=id
        if(id!=state.inspectorSelection) selectNode(id)
    }

    fun selectNode(id:String,reveal:Boolean=false) {
        state.inspectorSelection=id
        // Selecting a member from the inspector reveals it instead of selecting its folded proxy.
        if(reveal && !id.startsWith("@group:")) projection.groupForView(projection.viewIdForNode(id))?.let { group ->
            state.expandedGroups.add(group.id);load(loaded)
        }
        val viewId=projection.viewIdForNode(id)
        if(state.selected==id && models[viewId]?.let { canvas.isSelected(it) }==true) return
        state.selected=id
        state.selection=projection.actualSelection(setOf(id))
        canvas.clearAllSelected();models[viewId]?.let { canvas.addSelected(it) }
    }

    /** Use node-local model positions so embedding the canvas cannot bias the fit bounds. */
    private fun fitNative(padding:Float) {
        if(models.isEmpty()) return
        val bounds=models.values.map { model ->
            val element=canvas.getModelElement(model)
            floatArrayOf(model.position.x,model.position.y,
                model.position.x+maxOf(168f,element?.sizeWidth ?: 0f),
                model.position.y+maxOf(32f,element?.sizeHeight ?: 0f))
        }
        canvas.graphView.fit(bounds.minOf { it[0] }-padding,bounds.minOf { it[1] }-padding,
            bounds.maxOf { it[2] }+padding,bounds.maxOf { it[3] }+padding,0.1f)
    }

    fun fit() { restoreNavigation=false;fitNative(15f);rememberNavigation() }

    /** The shared Remove button follows the native selection, including cables and multiple nodes. */
    fun deleteSelection() {
        if(readOnly || disposed) return
        canvas.deleteSelectedElements();afterInput()
    }

    fun hasOpenMenu()=canvas.isMenuOpen

    private fun clampRootMenus() {
        fun clamp(element:UIElement) {
            if(element is Menu<*,*> && element.sizeWidth>0f && element.sizeHeight>0f) {
                val dx=element.positionX.coerceIn(0f,(ui.screenWidth-element.sizeWidth).coerceAtLeast(0f))-element.positionX
                val dy=element.positionY.coerceIn(0f,(ui.screenHeight-element.sizeHeight).coerceAtLeast(0f))-element.positionY
                if(dx!=0f || dy!=0f) {
                    // Root anchors use !important; nested native menus also need screen bounds
                    // after an ancestor moved. Let that layout settle before clamping children.
                    Style.importantPipeline(element.layout) { it.left(element.layoutX+dx).top(element.layoutY+dy) }
                    return
                }
            }
            element.children.toList().forEach(::clamp)
        }
        root.children.filterIsInstance<Menu<*,*>>().forEach(::clamp)
    }

    /** Native popups may cover adjacent inspectors; draw their existing trees above sibling widgets. */
    fun renderPopupOverlay(graphics:GuiGraphics,mouseX:Int,mouseY:Int,partialTick:Float) {
        if(disposed || !hasOpenMenu()) return
        val context=GUIContext.of(ui,graphics,mouseX,mouseY,partialTick)
        root.children.filterIsInstance<Menu<*,*>>().forEach { it.drawInBackground(context) }
        context.callPostRendering()
    }

    /** Actual native menu row hit point, used by the real-client acceptance scenario. */
    fun menuItemPoint(label:String):Pair<Double,Double>? {
        fun find(element:UIElement):UIElement? {
            if(element is Menu<*,*>) {
                element.nodeUIs.entries.firstOrNull { entry ->
                    ((entry.key.key as? Tuple<*,*>)?.b as? Component)?.string==label
                }?.let { return it.value }
            }
            return element.children.firstNotNullOfOrNull(::find)
        }
        val row=root.children.filterIsInstance<Menu<*,*>>().firstNotNullOfOrNull(::find) ?: return null
        if(row.sizeWidth<=0f || row.sizeHeight<=0f) return null
        val point=row.localToWorld(Vector2f(row.positionX+row.sizeWidth*0.5f,row.positionY+row.sizeHeight*0.5f))
        return point.x.toDouble() to point.y.toDouble()
    }

    /** Canonical graph coordinates at the visible viewport centre, for the shared insert toolbar. */
    fun insertionPoint():Pair<Int,Int> {
        return graphPoint(x+width*0.5,y+height*0.5)
    }
    fun graphPoint(screenX:Double,screenY:Double):Pair<Int,Int> {
        val point=canvas.getContentViewContainer().worldToLocalLayoutOffset(Vector2f(screenX.toFloat(),screenY.toFloat()))
        return point.x.roundToInt().coerceIn(-8000,8000) to point.y.roundToInt().coerceIn(-8000,8000)
    }

    /** Screen-space hit point from KilaGraph's actual laid-out title, including pan and zoom. */
    fun nodeHeaderPoint(id:String):Pair<Double,Double>? {
        val model=models[id] ?: return null
        val element=canvas.getModelElement(model) as? NodeElement ?: return null
        val title=element.nodeTittle ?: return null
        val point=title.localToWorld(Vector2f(title.positionX+title.sizeWidth*0.5f,title.positionY+title.sizeHeight*0.5f))
        return point.x.toDouble() to point.y.toDouble()
    }

    fun groupHeaderPoint(id:String)=nodeHeaderPoint(projection.groupViewId(id))
    fun groupTogglePoint(id:String):Pair<Double,Double>? {
        val button=groupButtons[id] ?: return null
        val point=button.localToWorld(Vector2f(button.positionX+button.sizeWidth/2,button.positionY+button.sizeHeight/2))
        return point.x.toDouble() to point.y.toDouble()
    }
    fun isGroupExpanded(id:String)=id in state.expandedGroups

    fun outputPortType(id:String):TypeHandle?=models[id]?.outputsById?.get("out")?.dataTypeHandle
    fun nodeAccent(id:String):Int?=models[id]?.elementColor
    fun hasNodePreview(id:String)=id in previewButtons
    fun isNodePreviewExpanded(id:String)=hasNodePreview(id) && previewExpanded(id)
    fun nodePreviewValue(id:String):ShapeNodePreview?=nodePreview?.invoke(id)
    fun buildingNodePreviewValue(id:String):BuildingNodePreview?=buildingNodePreview?.invoke(id)
    fun nodePreviewTogglePoint(id:String):Pair<Double,Double>? {
        val button=previewButtons[id] ?: return null
        val point=button.localToWorld(Vector2f(button.positionX+button.sizeWidth/2,button.positionY+button.sizeHeight/2))
        return point.x.toDouble() to point.y.toDouble()
    }
    fun nodePreviewPoint(id:String):Pair<Double,Double>? {
        if(!isNodePreviewExpanded(id)) return null
        val preview=previewElements[id] ?: return null
        if(preview.sizeWidth<=0f || preview.sizeHeight<=0f) return null
        val point=preview.localToWorld(Vector2f(preview.positionX+preview.sizeWidth/2,preview.positionY+preview.sizeHeight/2))
        return point.x.toDouble() to point.y.toDouble()
    }
    /** Screen-space viewport after native canvas pan and zoom, used for rendered acceptance. */
    fun nodePreviewViewport(id:String):BuildingPreviewViewport? {
        if(!isNodePreviewExpanded(id)) return null
        val preview=previewElements[id] ?: return null
        val lo=preview.localToWorld(Vector2f(preview.positionX,preview.positionY))
        val hi=preview.localToWorld(Vector2f(preview.positionX+preview.sizeWidth,preview.positionY+preview.sizeHeight))
        return BuildingPreviewViewport(lo.x.roundToInt(),lo.y.roundToInt(),(hi.x-lo.x).roundToInt(),(hi.y-lo.y).roundToInt())
    }
    fun inputPortType(id:String,slot:Int):TypeHandle?=models[id]?.inputsById?.get("in$slot")?.dataTypeHandle
    fun inputPortPoint(id:String,slot:Int):Pair<Double,Double>?=portPoint(models[id]?.inputsById?.get("in$slot"))
    fun outputPortPoint(id:String):Pair<Double,Double>?=portPoint(models[id]?.outputsById?.get("out"))
    fun sourceButtonPoint(id:String):Pair<Double,Double>? {
        val button=sourceButtons[id] ?: return null
        val point=button.localToWorld(Vector2f(button.positionX+button.sizeWidth*0.5f,button.positionY+button.sizeHeight*0.5f))
        return point.x.toDouble() to point.y.toDouble()
    }
    fun rotationButtonPoint(id:String,quarterTurns:Int):Pair<Double,Double>? {
        val button=rotationButtons[id to quarterTurns] ?: return null
        val point=button.localToWorld(Vector2f(button.positionX+button.sizeWidth*0.5f,button.positionY+button.sizeHeight*0.5f))
        return point.x.toDouble() to point.y.toDouble()
    }
    private fun portPoint(model:PortModel?):Pair<Double,Double>? {
        if(model==null) return null
        val element=canvas.getModelElement(model) as? PortElement ?: return null
        val icon=element.connector.connectorIcon
        val point=icon.localToWorld(Vector2f(icon.positionX+icon.sizeWidth*0.5f,icon.positionY+icon.sizeHeight*0.5f))
        return point.x.toDouble() to point.y.toDouble()
    }

    /** Called when the enclosing screen rebuilds or closes. */
    fun dispose() {
        if(disposed) return
        rememberNavigation();disposed=true;ui.onRemoved()
    }

    private fun refreshGraph() {
        val revision=dataRevision?.invoke()
        val readData=dataRevision==null || refreshAfterCommand || revision!=loadedRevision
        if(!readData && !forceReload) return
        val current=if(readData) data() else loaded
        val changed=current!=loaded
        if(forceReload || (if(changed) !updatePositions(current) else refreshAfterCommand)) {
            rememberNavigation()
            load(current)
        }
        loadedRevision=revision
        forceReload=false
        refreshAfterCommand=false
    }

    override fun renderWidget(graphics:GuiGraphics,mouseX:Int,mouseY:Int,partialTick:Float) {
        if(disposed) return
        refreshGraph()
        val tick=Minecraft.getInstance().level?.gameTime ?: (System.nanoTime()/50_000_000L)
        if(tick!=lastTick) { ui.tick();lastTick=tick }
        if(restoreNavigation) {
            canvas.graphView.offsetX=state.offsetX;canvas.graphView.offsetY=state.offsetY
            canvas.graphView.scale=state.zoom
            // Offset setters do not refresh LDLib's transform when the zoom is unchanged.
            val scale=canvas.graphView.scale
            canvas.graphView.contentRoot.transform { it.translate(-state.offsetX*scale,-state.offsetY*scale).scale(scale) }
            restoreNavigation=false
        }
        styleMenus()
        GraphPreviewThumbnails.beginFrame()
        ui.widget.render(graphics,mouseX,mouseY,partialTick)
        GraphPreviewThumbnails.endFrame(graphics)
        groupToggleAnchor?.let { (id,anchor) ->
            groupTogglePoint(id)?.let { point ->
                // Keep the navigation button under the pointer when the expanded header
                // moves above its members; the current zoom remains unchanged.
                val scale=canvas.graphView.scale
                canvas.graphView.offsetX+=((point.first-anchor.first)/scale).toFloat()
                canvas.graphView.offsetY+=((point.second-anchor.second)/scale).toFloat()
                canvas.graphView.contentRoot.transform { it.translate(-canvas.graphView.offsetX*scale,-canvas.graphView.offsetY*scale).scale(scale) }
                groupToggleAnchor=null
            }
        }
        clampRootMenus()
        graphics.enableScissor(x,y,x+width,y+height)
        drawGroupFrames(graphics)
        drawOverviewLabels(graphics)
        graphics.disableScissor()
        graphics.renderOutline(x,y,width,height,ArchitectTheme.shadow)
        graphics.fill(x+1,y+1,x+width-1,y+2,ArchitectTheme.brassDark)
        rememberNavigation()
    }

    private fun drawGroupFrames(graphics:GuiGraphics) {
        loaded.nodeGroups.filter { it.id in state.expandedGroups }.forEach { group ->
            val elements=(group.nodes+projection.groupViewId(group.id)).mapNotNull { id -> models[id]?.let { canvas.getModelElement(it) } }
            if(elements.isEmpty()) return@forEach
            val corners=elements.map { element ->
                val lo=element.localToWorld(Vector2f(element.positionX,element.positionY))
                val hi=element.localToWorld(Vector2f(element.positionX+element.sizeWidth,element.positionY+element.sizeHeight))
                floatArrayOf(lo.x,lo.y,hi.x,hi.y)
            }
            val left=corners.minOf { it[0] }.roundToInt()-5;val top=corners.minOf { it[1] }.roundToInt()-5
            val right=corners.maxOf { it[2] }.roundToInt()+5;val bottom=corners.maxOf { it[3] }.roundToInt()+5
            graphics.renderOutline(left,top,right-left,bottom-top,ArchitectTheme.brassDark)
        }
    }

    /** Keep an overview readable without changing fitted geometry or the native hit regions. */
    private fun drawOverviewLabels(graphics:GuiGraphics) {
        if(canvas.graphView.scale>=0.65f || hasOpenMenu()) return
        val font=Minecraft.getInstance().font
        val captions=projection.display.nodes.mapNotNull { node ->
            val element=models[node.id]?.let { canvas.getModelElement(it) } as? NodeElement ?: return@mapNotNull null
            val title=element.nodeTittle ?: return@mapNotNull null
            val point=title.localToWorld(Vector2f(title.positionX,title.positionY))
            val right=point.x+title.sizeWidth*canvas.graphView.scale
            val bottom=point.y+title.sizeHeight*canvas.graphView.scale
            if(right<x || point.x>x+width || bottom<y || point.y>y+height) return@mapNotNull null
            val px=point.x.roundToInt().coerceAtLeast(x+2)
            val py=(point.y.roundToInt()-12).coerceIn(y+2,maxOf(y+2,y+height-13))
            Triple(node,px,py)
        }
        // For each row keep the closest title already visited to the right. Titles in the
        // same column are processed together so they never count as one another's neighbour.
        val ordered=captions.indices.sortedByDescending { captions[it].second }
        val rightByRow=IntArray(height.coerceAtLeast(0)+3) { Int.MAX_VALUE }
        val neighbours=IntArray(captions.size) { Int.MAX_VALUE }
        var first=0
        while(first<ordered.size) {
            val column=captions[ordered[first]].second
            var end=first+1
            while(end<ordered.size && captions[ordered[end]].second==column) end++
            for(index in first until end) {
                val i=ordered[index]
                val row=captions[i].third-y
                for(r in (row-12).coerceAtLeast(0)..(row+12).coerceAtMost(rightByRow.lastIndex)) {
                    neighbours[i]=minOf(neighbours[i],rightByRow[r])
                }
            }
            for(index in first until end) rightByRow[captions[ordered[index]].third-y]=column
            first=end
        }
        for((index,caption) in captions.withIndex()) {
            val (node,px,py)=caption
            val labelWidth=minOf(112,x+width-px-5,neighbours[index]-px-6)
            if(labelWidth<font.width("1…")) continue
            val text=if(font.width(node.title)<=labelWidth) node.title else font.plainSubstrByWidth(node.title,(labelWidth-font.width("…")).coerceAtLeast(1))+"…"
            val textWidth=font.width(text)
            ArchitectTheme.inset(graphics,px-3,py-2,textWidth+8,14)
            graphics.fill(px-2,py,px-1,py+9,if(node.id==state.selected) ArchitectTheme.brass else node.accent ?: ArchitectTheme.green)
            graphics.drawString(font,text,px+1,py+1,if(node.id==state.selected) ArchitectTheme.brass else ArchitectTheme.text,false)
        }
    }

    override fun mouseClicked(mouseX:Double,mouseY:Double,button:Int):Boolean {
        val menuOpen=hasOpenMenu()
        if(!isMouseOver(mouseX,mouseY) && !menuOpen) return false
        ui.refreshHoveredElementAtScreen(mouseX.toFloat(),mouseY.toFloat())
        val result=ui.widget.mouseClicked(mouseX,mouseY,button)
        afterInput();return menuOpen || result || isMouseOver(mouseX,mouseY)
    }
    override fun mouseReleased(mouseX:Double,mouseY:Double,button:Int):Boolean {
        ui.refreshHoveredElementAtScreen(mouseX.toFloat(),mouseY.toFloat())
        val result=ui.widget.mouseReleased(mouseX,mouseY,button);afterInput();return result
    }
    override fun mouseDragged(mouseX:Double,mouseY:Double,button:Int,dragX:Double,dragY:Double):Boolean {
        val result=ui.widget.mouseDragged(mouseX,mouseY,button,dragX,dragY);afterInput();return result
    }
    override fun mouseMoved(mouseX:Double,mouseY:Double) {
        ui.refreshHoveredElementAtScreen(mouseX.toFloat(),mouseY.toFloat())
        ui.widget.mouseMoved(mouseX,mouseY)
    }
    override fun mouseScrolled(mouseX:Double,mouseY:Double,scrollX:Double,scrollY:Double):Boolean {
        val menuOpen=hasOpenMenu()
        if(!isMouseOver(mouseX,mouseY) && !menuOpen) return false
        ui.refreshHoveredElementAtScreen(mouseX.toFloat(),mouseY.toFloat())
        val result=ui.widget.mouseScrolled(mouseX,mouseY,scrollX,scrollY);afterInput();return menuOpen || result
    }
    override fun keyPressed(keyCode:Int,scanCode:Int,modifiers:Int):Boolean {
        if(keyCode==GLFW.GLFW_KEY_ESCAPE && hasOpenMenu()) {
            // LDLib's Menu handles focus loss but does not consume Escape itself.
            // Its close callback also clears GraphView's native isMenuOpen state.
            root.children.filterIsInstance<Menu<*,*>>().toList().forEach { it.close() }
            styledMenus.clear()
            return true
        }
        if(!hasOpenMenu() && keyCode==GLFW.GLFW_KEY_G && modifiers and GLFW.GLFW_MOD_CONTROL!=0 && !readOnly) {
            if(modifiers and GLFW.GLFW_MOD_ALT!=0) {
                val groups=selectedGroups().map { it.id }.toSet()
                if(groups.isNotEmpty()) commitGroups(loaded.nodeGroups.filter { it.id !in groups })
            } else createGroup()
            return true
        }
        if(!hasOpenMenu() && keyCode==GLFW.GLFW_KEY_TAB) {
            selectedGroups().firstOrNull()?.let { toggleGroup(it.id);return true }
        }
        // The shared screen owns document undo, redo and JSON clipboard shortcuts.
        if(modifiers and GLFW.GLFW_MOD_CONTROL!=0 && keyCode in listOf(GLFW.GLFW_KEY_Z,GLFW.GLFW_KEY_Y,GLFW.GLFW_KEY_C,GLFW.GLFW_KEY_V)) return false
        val result=ui.widget.keyPressed(keyCode,scanCode,modifiers);afterInput();return result
    }
    override fun keyReleased(keyCode:Int,scanCode:Int,modifiers:Int)=ui.widget.keyReleased(keyCode,scanCode,modifiers)
    override fun charTyped(codePoint:Char,modifiers:Int)=ui.widget.charTyped(codePoint,modifiers)
    override fun setFocused(focused:Boolean) { super.setFocused(focused);ui.widget.setFocused(focused) }
    override fun updateWidgetNarration(output:NarrationElementOutput) { defaultButtonNarrationText(output) }
}
