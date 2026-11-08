package de.teutonstudio.copycatarchitect.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component

/** Raised machine keys share the same focus and selected states across every screen. */
class EditorButton(x:Int,y:Int,w:Int,label:Component,action:()->Unit):
    Button(x,y,w,18,label,{ action() },DEFAULT_NARRATION) {
    var selected=false
    var dropdown=false
    var primary=false
    override fun renderWidget(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        val font=Minecraft.getInstance().font
        val highlighted=active && isHoveredOrFocused
        ArchitectTheme.button(g,x,y,width,height,highlighted,selected,active)
        if(primary && active) {
            g.fill(x+2,y+2,x+width-2,y+height-3,if(highlighted) 0xff668454.toInt() else 0xff4c673f.toInt())
            g.fill(x+2,y+height-3,x+width-2,y+height-2,ArchitectTheme.green)
        }
        val color=if(active) ArchitectTheme.text else ArchitectTheme.disabled
        val available=(width-if(dropdown) 18 else if(selected) 14 else 8).coerceAtLeast(1)
        val key=(message.contents as? net.minecraft.network.chat.contents.TranslatableContents)?.key?.removePrefix("gui.copycat_architect.")
        val icon=key?.takeIf { it in setOf("save","load","close","fit_graph","remove") && font.width(message.string)>available }
        if(icon!=null) drawCompactIcon(g,if(dropdown) x+(width-12)/2-5 else x+width/2-5,y+4,icon,color)
        else {
            val text=if(font.width(message.string)<=available) message.string else font.plainSubstrByWidth(message.string,(available-font.width("…")).coerceAtLeast(0))+"…"
            g.drawString(font,text,if(dropdown) x+5 else x+(width-font.width(text))/2+(if(selected) 2 else 0),y+5,color,false)
        }
        if(dropdown) {
            g.fill(x+width-13,y+3,x+width-12,y+height-3,ArchitectTheme.shadow)
            for(row in 0..2) g.fill(x+width-10+row,y+7+row,x+width-5-row,y+8+row,ArchitectTheme.brass)
        }
    }
    private fun drawCompactIcon(g:GuiGraphics,px:Int,py:Int,key:String,color:Int) {
        when(key) {
            "save" -> {
                g.renderOutline(px,py,10,10,color);g.fill(px+2,py+1,px+7,py+4,color)
                g.renderOutline(px+2,py+6,6,4,color)
            }
            "load" -> {
                g.fill(px+4,py,px+6,py+6,color)
                for(i in 0..2) g.fill(px+2+i,py+3+i,px+8-i,py+4+i,color)
                g.fill(px,py+7,px+2,py+10,color);g.fill(px+8,py+7,px+10,py+10,color);g.fill(px,py+9,px+10,py+10,color)
            }
            "fit_graph" -> for(dx in listOf(0,6)) for(dy in listOf(0,6)) {
                g.fill(px+dx,py+dy+(if(dy==0) 0 else 3),px+dx+4,py+dy+(if(dy==0) 1 else 4),color)
                g.fill(px+dx+(if(dx==0) 0 else 3),py+dy,px+dx+(if(dx==0) 1 else 4),py+dy+4,color)
            }
            "remove" -> {
                g.fill(px+1,py+1,px+9,py+2,color);g.fill(px+3,py,px+7,py+1,color)
                g.renderOutline(px+2,py+3,6,7,color);g.fill(px+4,py+4,px+5,py+8,color)
            }
            "close" -> for(i in 0..7) {
                g.fill(px+1+i,py+1+i,px+3+i,py+2+i,color)
                g.fill(px+8-i,py+1+i,px+10-i,py+2+i,color)
            }
        }
    }
}

/** Native text editing inside an inset terminal display, including caret and selection rendering. */
class EditorTextField(font:net.minecraft.client.gui.Font,x:Int,y:Int,w:Int,h:Int,label:Component):
    net.minecraft.client.gui.components.EditBox(font,x,y,w,h,label) {
    init { setBordered(false);setTextColor(ArchitectTheme.green);setTextColorUneditable(ArchitectTheme.disabled) }
    override fun renderWidget(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        ArchitectTheme.inset(g,x,y,width,height)
        if(isFocused) g.renderOutline(x,y,width,height,ArchitectTheme.brass)
        val px=x;val py=y
        x+=5;y+=5
        try { super.renderWidget(g,mx,my,delta) } finally { x=px;y=py }
    }
    override fun getInnerWidth()=(width-10).coerceAtLeast(1)
    override fun onClick(mx:Double,my:Double) { super.onClick(mx-5,my) }
}

/** A modal screen-space list: the viewport clips neither labels nor pointer targets. */
class EditorDropdown(val anchor:Button,val options:List<Pair<String,()->Unit>>,selected:Int) {
    var index=selected.coerceIn(0,options.lastIndex.coerceAtLeast(0))
    var first=selected.coerceAtLeast(0)
    private fun box(sw:Int,sh:Int):IntArray {
        val font=Minecraft.getInstance().font
        val w=maxOf(anchor.width,options.maxOfOrNull { font.width(it.first)+22 } ?: 80).coerceAtMost((sw-16).coerceAtLeast(1))
        val rows=minOf(options.size,((sh-20)/20).coerceAtLeast(1),12)
        first=first.coerceIn(0,(options.size-rows).coerceAtLeast(0))
        val below=anchor.y+anchor.height+2
        val top=if(below+rows*20<=sh-8) below else (anchor.y-rows*20-2).coerceAtLeast(8)
        return intArrayOf(anchor.x.coerceIn(8,maxOf(8,sw-w-8)),top,w,rows)
    }
    fun render(g:GuiGraphics,mx:Int,my:Int,sw:Int,sh:Int) {
        val (x,y,w,rows)=box(sw,sh);val font=Minecraft.getInstance().font
        first=first.coerceIn(0,(options.size-rows).coerceAtLeast(0))
        g.flush();g.pose().pushPose();g.pose().translate(0f,0f,600f)
        ArchitectTheme.panel(g,x-3,y-3,w+6,rows*20+6)
        for(i in 0 until rows) {
            val chosen=i+first;val hovered=mx in x until x+w && my in y+i*20 until y+(i+1)*20
            g.fill(x,y+i*20,x+w,y+(i+1)*20,if(hovered) ArchitectTheme.raised else if(chosen==index) 0xff515d3f.toInt() else ArchitectTheme.well)
            if(chosen==index) g.fill(x,y+i*20,x+2,y+(i+1)*20,ArchitectTheme.brass)
            g.drawString(font,font.plainSubstrByWidth(options[chosen].first,w-16),x+6,y+i*20+6,ArchitectTheme.text,false)
        }
        if(rows<options.size) g.drawString(font,"↕",x+w-9,y+6,ArchitectTheme.brass,false)
        g.flush();g.pose().popPose()
    }
    fun click(mx:Double,my:Double,button:Int,sw:Int,sh:Int):(() -> Unit)? {
        val (x,y,w,rows)=box(sw,sh)
        return if(button==0 && mx>=x && mx<x+w && my>=y && my<y+rows*20) options.getOrNull(first+((my-y)/20).toInt())?.second else null
    }
    fun scroll(dy:Double,sw:Int,sh:Int) { val rows=box(sw,sh)[3];first=(first-if(dy>0) 1 else -1).coerceIn(0,(options.size-rows).coerceAtLeast(0)) }
    fun key(key:Int,sw:Int,sh:Int):(() -> Unit)? {
        val rows=box(sw,sh)[3]
        when(key) { 265->index=(index-1).coerceAtLeast(0);264->index=(index+1).coerceAtMost(options.lastIndex);268->index=0;269->index=options.lastIndex;257,335->return options.getOrNull(index)?.second }
        if(index<first) first=index
        if(index>=first+rows) first=index-rows+1
        return null
    }
}

/** Holding the pointer over the eye reveals the machine without changing the graph or its history. */
object WorldPeek { var active=false }
class PrinterMemoryButton(x:Int,y:Int,action:()->Unit):Button(x,y,32,18,tr("printer_storage"),{ action() },DEFAULT_NARRATION) {
    init { setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr("printer_storage"))) }
    override fun renderWidget(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        val hover=isHoveredOrFocused
        ArchitectTheme.button(g,x,y,width,height,hover)
        val color=if(hover) ArchitectTheme.brass else ArchitectTheme.text
        g.renderOutline(x+9,y+4,14,11,color)
        for(row in 0..2) { g.fill(x+12,y+6+row*3,x+14,y+7+row*3,color);g.fill(x+16,y+6+row*3,x+20,y+7+row*3,color) }
    }
}
class PrinterEyeButton(x:Int,y:Int):net.minecraft.client.gui.components.AbstractWidget(x,y,28,18,tr("printer_eye")) {
    init { setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr("printer_eye"))) }
    fun renderWorldPeek(g:GuiGraphics,mx:Int,my:Int,delta:Float)=renderWidget(g,mx,my,delta)
    override fun renderWidget(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        val hover=isMouseOver(mx.toDouble(),my.toDouble())
        ArchitectTheme.button(g,x,y,width,height,hover,hover)
        val color=if(hover) ArchitectTheme.brass else ArchitectTheme.text
        g.fill(x+6,y+7,x+8,y+11,color);g.fill(x+8,y+5,x+20,y+7,color);g.fill(x+8,y+11,x+20,y+13,color);g.fill(x+20,y+7,x+22,y+11,color);g.fill(x+12,y+7,x+16,y+11,color)
    }
    override fun updateWidgetNarration(out:net.minecraft.client.gui.narration.NarrationElementOutput) { out.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,message) }
}
