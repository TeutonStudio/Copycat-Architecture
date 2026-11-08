package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.PrintRecord
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class PrinterHistoryState { var selected="";var first=0 }

/** History is a workspace tab. Loading opens a separate card with the original print settings. */
class PrinterHistoryPanel(x:Int,y:Int,w:Int,h:Int,private val state:PrinterHistoryState,
                          private val entries:()->List<PrintRecord>,private val changed:()->Unit)
    :AbstractWidget(x,y,w,h,tr("printer_history")) {
    private val font get()=Minecraft.getInstance().font
    private val listWidth get()=(width*0.58).roundToInt()
    private val rowHeight=26
    private val rows get()=((height-35)/rowHeight).coerceAtLeast(1)
    private var previewKey:Form?=null
    private var preview:Result<Form>?=null
    fun selection()=entries().firstOrNull { it.id==state.selected }
    fun rowPoint(row:Int)=x+listWidth/2.0 to y+30+row*rowHeight+rowHeight/2.0
    override fun renderWidget(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        val values=entries();state.first=state.first.coerceIn(0,(values.size-rows).coerceAtLeast(0))
        ArchitectTheme.inset(g,x,y,width,height)
        g.drawString(font,tr("printer_history"),x+10,y+9,ArchitectTheme.brass,false)
        val count=values.size.toString();g.drawString(font,count,x+listWidth-font.width(count)-8,y+9,ArchitectTheme.muted,false)
        val top=y+30;val available=height-35
        g.enableScissor(x+4,top,x+listWidth,y+height-4)
        try {
            if(values.isEmpty()) g.drawWordWrap(font,tr("ps_history_empty"),x+10,top+6,listWidth-20,ArchitectTheme.muted)
            repeat(rows) { row ->
                val entry=values.getOrNull(state.first+row) ?: return@repeat
                val yy=top+row*rowHeight;val selected=entry.id==state.selected
                ArchitectTheme.button(g,x+6,yy,listWidth-12,rowHeight-2,mx in x+6 until x+listWidth-6 && my in yy until yy+rowHeight,selected)
                g.drawString(font,font.plainSubstrByWidth(entry.form.name,listWidth-26),x+12,yy+3,ArchitectTheme.text,false)
                val date=DateTimeFormatter.ofPattern("dd.MM. HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(entry.time))
                g.drawString(font,date,x+12,yy+14,ArchitectTheme.muted,false)
            }
            if(values.size>rows) {
                val thumb=(available*rows/values.size).coerceAtLeast(8);val yy=top+(available-thumb)*state.first/(values.size-rows)
                g.fill(x+listWidth-4,yy,x+listWidth-2,yy+thumb,ArchitectTheme.brass)
            }
        } finally { g.disableScissor() }
        val px=x+listWidth+4;val pw=width-listWidth-10
        val selected=selection() ?: return
        if(previewKey!=selected.form) { previewKey=selected.form;preview=runCatching { Geometry.validate(selected.form) } }
        g.enableScissor(px,top,x+width-6,y+height-6)
        try { preview?.getOrNull()?.let { form ->
            ShapeNodePreviewRenderer.draw(g,px,top,pw,height-36,ShapeNodePreview(parts=Geometry.rotate(form.parts,selected.turn)),form.groups,selected.surfaces)
        } } finally { g.flush();g.disableScissor() }
    }
    override fun mouseClicked(mx:Double,my:Double,button:Int):Boolean {
        if(button!=0 || mx<x+6 || mx>=x+listWidth-6 || my<y+30 || my>=minOf(y+height-4,y+30+rows*rowHeight)) return false
        entries().getOrNull(state.first+((my-y-30)/rowHeight).toInt())?.let { state.selected=it.id;changed() }
        return true
    }
    override fun mouseScrolled(mx:Double,my:Double,dx:Double,dy:Double):Boolean {
        if(!isMouseOver(mx,my)) return false
        state.first=(state.first-dy.toInt()).coerceIn(0,(entries().size-rows).coerceAtLeast(0));return true
    }
    override fun updateWidgetNarration(output:NarrationElementOutput) { defaultButtonNarrationText(output) }
}
