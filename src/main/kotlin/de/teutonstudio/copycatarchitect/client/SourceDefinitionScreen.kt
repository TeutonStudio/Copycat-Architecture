package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.Form
import de.teutonstudio.copycatarchitect.domain.NodeKind
import de.teutonstudio.copycatarchitect.domain.Part
import de.teutonstudio.copycatarchitect.domain.BaseShapes
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen

/** A detached view of a built-in source definition; it has no document edit or history callback. */
class SourceDefinitionScreen private constructor(private val parent:Screen?,source:Part,
                                                val definition:Form,private val surfaces:Map<String,String>):
    EditorScreen(tr("source_definition_title",ui(BaseShapes.find(source.source,source.discCut).name))) {
    val graphView=GraphView()
    val kilaGraphState=KilaGraphState()
    var inspectorMode=false;private set
    var graphCanvas:NodeGraphCanvas?=null;private set
    var kilaGraph:KilaGraphEditor?=null;private set
    private val previews=ShapeNodePreviews({ definition }) { checkNotNull(definition.graph) }

    fun selectNode(id:String) { graphView.selected=id;graphView.inspectorScroll=0 }
    fun showInspector(inspector:Boolean) { inspectorMode=inspector;rebuildWidgets() }
    override fun titleAvailableWidth()=width-16

    fun diagram():KilaGraphData {
        val graph=checkNotNull(definition.graph)
        return KilaGraphData(graph.nodes.mapIndexed { index,n ->
            KilaNode(n.id,"${index+1} · ${ui("node_"+n.kind.name.lowercase())}",n.x,n.y,
                n.inputs.indices.map { "${ui("input")} ${it+1}" },output=n.kind!=NodeKind.OUTPUT,deletable=false,
                source=if(n.kind==NodeKind.INPUT) n.part.source else "",discCut=n.part.discCut,
                part=if(n.kind==NodeKind.INPUT) n.part else null,previewExpandedByDefault=n.kind==NodeKind.OUTPUT)
        },graph.nodes.flatMap { n -> n.inputs.mapIndexedNotNull { slot,id ->
            id.takeIf { it.isNotEmpty() }?.let { KilaLink(it,n.id,slot) }
        } },graph.nodeGroups)
    }

    override fun init() {
        kilaGraph?.dispose();kilaGraph=null
        button(8,28,84,"fit_graph") { kilaGraph?.fit() }
        button(width-80,28,72,"close") { onClose() }
        val normal=width>=700
        if(!normal) {
            val tabWidth=((width-80-4-98-4)/2).coerceIn(24,82)
            button(98,28,tabWidth,"node_graph") { showInspector(false) }.apply { (this as EditorButton).selected=!inspectorMode }
            button(102+tabWidth,28,tabWidth,"control_panel") { showInspector(true) }.apply { (this as EditorButton).selected=inspectorMode }
        }
        val inspectorWidth=if(normal) minOf(360,width/3) else width-16
        val inspectorLeft=if(normal) width-inspectorWidth-8 else 8
        graphCanvas=NodeGraphCanvas(inspectorLeft,52,inspectorWidth,height-82,{definition},{surfaces},graphView,
            {},{},inspector=true,readOnly=true)
        if(normal || inspectorMode) addRenderableWidget(graphCanvas!!)
        if(normal || !inspectorMode) kilaGraph=addRenderableWidget(KilaGraphEditor(8,52,if(normal) inspectorLeft-14 else width-16,height-82,
            this,kilaGraphState,::diagram,::selectNode,{}, {},readOnly=true,nodePreview=previews::get,previewGroups={ definition.groups },previewSurfaces={ surfaces }))
    }

    override fun renderWorkspace(g:GuiGraphics) {
        ArchitectTheme.bevel(g,4,26,width-8,23,ArchitectTheme.panel)
        val available=width-194
        if(width>=700 && available>40) label(g,font.plainSubstrByWidth(ui("source_definition_read_only"),available),100,33,ArchitectTheme.brass)
    }

    override fun renderContents(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        kilaGraph?.selectNode(graphView.selected)
        label(g,font.plainSubstrByWidth(ui("source_definition_read_only")+" · "+ui("source_definition_hint"),width-16),8,height-26,ArchitectTheme.muted)
        kilaGraph?.renderPopupOverlay(g,mx,my,delta)
    }

    override fun mouseClicked(x:Double,y:Double,button:Int):Boolean {
        if((width>=700 || inspectorMode) && graphCanvas?.hasOpenMenu()==true) return graphCanvas!!.mouseClicked(x,y,button)
        if(kilaGraph?.mouseClicked(x,y,button)==true) { focused=kilaGraph;return true }
        if((width>=700 || inspectorMode) && graphCanvas?.mouseClicked(x,y,button)==true) { focused=graphCanvas;return true }
        return super.mouseClicked(x,y,button)
    }

    override fun mouseDragged(x:Double,y:Double,button:Int,dx:Double,dy:Double):Boolean {
        if(focused===kilaGraph) return kilaGraph?.mouseDragged(x,y,button,dx,dy) ?: false
        if(focused===graphCanvas) return graphCanvas?.mouseDragged(x,y,button,dx,dy) ?: false
        return super.mouseDragged(x,y,button,dx,dy)
    }

    override fun mouseReleased(x:Double,y:Double,button:Int):Boolean {
        if(focused===kilaGraph) return kilaGraph?.mouseReleased(x,y,button) ?: false
        if(focused===graphCanvas) return graphCanvas?.mouseReleased(x,y,button) ?: false
        return super.mouseReleased(x,y,button)
    }

    override fun mouseScrolled(x:Double,y:Double,dx:Double,dy:Double):Boolean {
        if((width>=700 || inspectorMode) && graphCanvas?.hasOpenMenu()==true) return graphCanvas!!.mouseScrolled(x,y,dx,dy)
        if(kilaGraph?.hasOpenMenu()==true) return kilaGraph!!.mouseScrolled(x,y,dx,dy)
        return super.mouseScrolled(x,y,dx,dy)
    }

    override fun keyPressed(key:Int,scan:Int,mods:Int):Boolean {
        if(key==256 && kilaGraph?.hasOpenMenu()==true && kilaGraph?.keyPressed(key,scan,mods)==true) return true
        if(hasControlDown() && key in listOf(90,89,67,86)) return true
        if(focused===kilaGraph && kilaGraph?.keyPressed(key,scan,mods)==true) return true
        if((width>=700 || inspectorMode) && graphCanvas?.keyPressed(key,scan,mods)==true) return true
        return super.keyPressed(key,scan,mods)
    }

    override fun onClose() { minecraft?.setScreen(parent) }
    override fun removed() { kilaGraph?.dispose();super.removed() }

    companion object {
        fun open(source:Part,definition:Form,surfaces:Map<String,String>) {
            val minecraft=Minecraft.getInstance()
            minecraft.setScreen(SourceDefinitionScreen(minecraft.screen,source,definition,surfaces.toMap()))
        }
    }
}
