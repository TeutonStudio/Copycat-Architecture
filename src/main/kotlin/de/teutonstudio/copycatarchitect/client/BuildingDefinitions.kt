package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import kotlin.math.roundToInt

enum class BuildingPanelSection(val key:String,val kinds:List<BuildingKind>) {
    SEGMENTS("panel_segments",listOf(BuildingKind.INT,BuildingKind.ROTATION_SEGMENT,BuildingKind.SEGMENT,BuildingKind.PATTERN,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_ROTATE,BuildingKind.COPYCAT,BuildingKind.COPYCAT_MIRROR,BuildingKind.COPYCAT_ROTATE,BuildingKind.COPYCAT_UNION,BuildingKind.COPYCAT_INTERSECTION,BuildingKind.COPYCAT_DIFFERENCE,BuildingKind.COPYCAT_SYMMETRIC_DIFFERENCE,BuildingKind.SEGMENT_UNION,BuildingKind.SEGMENT_INTERSECTION,BuildingKind.SEGMENT_DIFFERENCE,BuildingKind.SEGMENT_SYMMETRIC_DIFFERENCE,BuildingKind.FLOOR,BuildingKind.CEILING,BuildingKind.SEGMENT_LAYOUT)),
    ROOMS("panel_rooms",listOf(BuildingKind.AREA,BuildingKind.ROOM_GEOMETRY,BuildingKind.ROOM_LAYOUT,BuildingKind.ROOM,BuildingKind.BAY_WINDOW)),
    STOREYS("panel_storeys",listOf(BuildingKind.STOREY)),
    BUILDING("panel_building",listOf(BuildingKind.BUILDING)),
    ROOF("panel_roof",listOf(BuildingKind.ROOF_LEVEL,BuildingKind.ROOF_LAYOUT,BuildingKind.ROOF,BuildingKind.DORMER)),
    ACCESS("panel_access",listOf(BuildingKind.STAIRWELL,BuildingKind.ELEVATOR_SHAFT,BuildingKind.SERVICE_SHAFT)),
    LANDSCAPE("panel_landscape",listOf(BuildingKind.GARDEN,BuildingKind.PATH));
    companion object {
        fun of(kind:BuildingKind)=entries.first { kind in it.kinds }
    }
}

/** A definition selection is presentation state; it never rewrites graph nodes or references. */
class BuildingDefinitionList(left:Int,top:Int,listWidth:Int,listHeight:Int,
    private val graph:()->BuildingGraph,val section:BuildingPanelSection,
    private val selected:()->String,private val select:(String)->Unit,
    initialScroll:Double=0.0,private val scrollChanged:(Double)->Unit={}):
    AbstractWidget(left,top,listWidth,listHeight,tr(section.key)) {
    private val font get()=Minecraft.getInstance().font
    private val nodes get()=graph().nodes.filter { it.kind in section.kinds }
    private val rowTop get()=y+27
    private val visibleHeight get()=(height-32).coerceAtLeast(1)
    private var scroll=initialScroll
    private fun maximum()=(nodes.size*29-visibleHeight).coerceAtLeast(0).toDouble()
    private fun setScroll(value:Double) { scroll=value.coerceIn(0.0,maximum());scrollChanged(scroll) }
    fun reveal(id:String) {
        val index=nodes.indexOfFirst { it.id==id };if(index<0) return
        setScroll(when { index*29<scroll->index*29.0;(index+1)*29>scroll+visibleHeight->(index+1)*29.0-visibleHeight;else->scroll })
    }
    fun definitionPoint(id:String):Pair<Double,Double> {
        reveal(id);return x+width/2.0 to rowTop+nodes.indexOfFirst { it.id==id }*29-scroll+12
    }
    private fun name(node:BuildingNode):String = when {
        node.id=="empty_wall"->ui("empty_wall")
        node.name.isBlank() || node.name in setOf("Room","Storey","Building","Roof","Floor","Ceiling","Wall segment","Wall Copycat","Roof Copycat","Gable segment")->ui(GraphModels.building(node.kind).labelKey)
        node.name=="Solid wall"->ui("bg_solid_wall")
        else->node.name
    }
    override fun renderWidget(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        setScroll(scroll)
        ArchitectTheme.panel(g,x,y,width,height,ui(section.key)+" · "+nodes.size)
        g.enableScissor(x+3,rowTop,x+width-3,y+height-4)
        nodes.forEachIndexed { index,node ->
            val top=(rowTop+index*29-scroll).roundToInt()
            if(top+27>=rowTop && top<y+height-4) {
                val hovered=mx>=x+5 && mx<x+width-8 && my>=maxOf(top,rowTop) && my<minOf(top+27,y+height-4)
                ArchitectTheme.button(g,x+5,top,width-14,27,hovered,node.id==selected())
                g.drawString(font,font.plainSubstrByWidth(name(node),width-26),x+10,top+4,if(node.id==selected()) ArchitectTheme.brass else ArchitectTheme.text,false)
                g.drawString(font,font.plainSubstrByWidth(ui(GraphModels.building(node.kind).labelKey),width-26),x+10,top+16,ArchitectTheme.muted,false)
            }
        }
        if(nodes.isEmpty()) g.drawString(font,font.plainSubstrByWidth(ui("panel_no_definitions"),width-14),x+7,rowTop+5,ArchitectTheme.muted,false)
        if(maximum()>0) {
            val thumb=(visibleHeight.toDouble()*visibleHeight/(visibleHeight+maximum())).roundToInt().coerceIn(8,visibleHeight.coerceAtLeast(8))
            val top=rowTop+((visibleHeight-thumb)*scroll/maximum()).roundToInt()
            g.fill(x+width-6,rowTop,x+width-4,rowTop+visibleHeight,ArchitectTheme.shadow)
            g.fill(x+width-6,top,x+width-4,top+thumb,ArchitectTheme.brass)
        }
        g.disableScissor()
    }
    override fun mouseClicked(mx:Double,my:Double,button:Int):Boolean {
        if(button!=0 || !isMouseOver(mx,my) || my<rowTop) return false
        nodes.getOrNull(((my-rowTop+scroll)/29).toInt())?.let { select(it.id) }
        return true
    }
    override fun mouseScrolled(mx:Double,my:Double,dx:Double,dy:Double):Boolean {
        if(!isMouseOver(mx,my)) return false
        setScroll(scroll-dy*29);return true
    }
    override fun keyPressed(key:Int,scan:Int,mods:Int):Boolean {
        val entries=nodes;if(entries.isEmpty()) return false
        val index=entries.indexOfFirst { it.id==selected() }.coerceAtLeast(0)
        val next=when(key) { 264->index+1;265->index-1;268->0;269->entries.lastIndex;else->return false }.coerceIn(0,entries.lastIndex)
        reveal(entries[next].id);select(entries[next].id);return true
    }
    override fun updateWidgetNarration(output:NarrationElementOutput) {
        output.add(NarratedElementType.TITLE,message)
        nodes.firstOrNull { it.id==selected() }?.let { output.add(NarratedElementType.POSITION,name(it)) }
    }
}
