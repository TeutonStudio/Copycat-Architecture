package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.GraphGroup
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.narration.NarratedElementType
import kotlin.math.roundToInt

/** Group headers have their own inspector, independent of the first member's node kind. */
internal class GraphGroupInspector(x:Int,y:Int,w:Int,h:Int,
    private val group:()->GraphGroup?,private val members:()->List<Pair<String,String>>,
    private val select:(String)->Unit):AbstractWidget(x,y,w,h,tr("node_group")) {
    private var scroll=0
    private var previous=""
    val available get()=group()!=null
    fun memberPoint(id:String):Pair<Double,Double>? {
        val index=entries().indexOfFirst { it.first==id }.takeIf { it>=0 } ?: return null
        return x+width/2.0 to y+52+index*23-scroll+10.5
    }
    private val contentHeight get()=(height-54).coerceAtLeast(1)
    private fun entries():List<Pair<String,String>> {
        val id=group()?.id.orEmpty()
        if(previous!=id) { previous=id;scroll=0 }
        val entries=members()
        scroll=scroll.coerceIn(0,(entries.size*23-contentHeight).coerceAtLeast(0))
        return entries
    }
    override fun renderWidget(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        val selected=group() ?: return
        val entries=entries();val font=Minecraft.getInstance().font
        ArchitectTheme.panel(g,x,y,width,height,ui("node_group"))
        g.drawString(font,font.plainSubstrByWidth(selected.name,(width-20).coerceAtLeast(1)),x+10,y+27,ArchitectTheme.brass,false)
        g.drawString(font,font.plainSubstrByWidth(ui("group_members",entries.size),(width-20).coerceAtLeast(1)),x+10,y+40,ArchitectTheme.muted,false)
        g.enableScissor(x+5,y+52,x+width-5,y+height-3)
        for((index,entry) in entries.withIndex()) {
            val top=y+52+index*23-scroll
            if(top+21<=y+52 || top>=y+height-3) continue
            ArchitectTheme.button(g,x+9,top,width-18,21,isMouseOver(mx.toDouble(),my.toDouble()) && my>=top && my<top+21)
            g.drawString(font,font.plainSubstrByWidth(entry.second,(width-32).coerceAtLeast(1)),x+15,top+6,ArchitectTheme.text,false)
        }
        g.disableScissor()
        if(entries.size*23>contentHeight) {
            val thumb=(contentHeight.toDouble()*contentHeight/(entries.size*23)).roundToInt().coerceAtLeast(6).coerceAtMost(contentHeight)
            val top=y+52+(contentHeight-thumb)*scroll/(entries.size*23-contentHeight)
            g.fill(x+width-6,top,x+width-4,top+thumb,ArchitectTheme.brass)
        }
    }
    override fun mouseClicked(mx:Double,my:Double,button:Int):Boolean {
        if(!isMouseOver(mx,my)) return false
        val entries=entries()
        if(button==0 && mx>=x+9 && mx<x+width-9 && my>=y+52 && my<y+height-3) {
            val row=(my-y-52+scroll).toInt()
            if(row%23<21) entries.getOrNull(row/23)?.let { select(it.first) }
        }
        return true
    }
    override fun mouseScrolled(mx:Double,my:Double,dx:Double,dy:Double):Boolean {
        if(!isMouseOver(mx,my)) return false
        val entries=entries()
        scroll=(scroll-(dy*23).roundToInt()).coerceIn(0,(entries.size*23-contentHeight).coerceAtLeast(0))
        return true
    }
    override fun updateWidgetNarration(output:NarrationElementOutput) {
        output.add(NarratedElementType.TITLE,group()?.name ?: ui("node_group"))
    }
}
