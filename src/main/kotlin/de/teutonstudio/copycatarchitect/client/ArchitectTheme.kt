package de.teutonstudio.copycatarchitect.client

import dan200.computercraft.client.gui.GuiSprites
import dan200.computercraft.shared.computer.core.ComputerFamily
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics

/** Shared CC-derived surfaces for printer/table dialogs, inspectors, and controls. */
object ArchitectTheme {
    val backdrop=0xff101820.toInt()
    val casing=0xff616c75.toInt()
    val panel=0xff26313a.toInt()
    val raised=0xff35434f.toInt()
    val highlight=0xff8a929b.toInt()
    val shadow=0xff080c10.toInt()
    val well=0xff111820.toInt()
    val blueprint=0xff17232d.toInt()
    val grid=0xff253642.toInt()
    val brass=0xffd4b055.toInt()
    val brassDark=0xff88733c.toInt()
    val text=0xffece9df.toInt()
    val muted=0xffaeb8c1.toInt()
    val disabled=0xff6d7882.toInt()
    val green=0xffa4cf78.toInt()
    val danger=0xffed9a76.toInt()
    private val font get()=Minecraft.getInstance().font

    fun bevel(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int,fill:Int=panel,pressed:Boolean=false,edge:Int=shadow) {
        if(w<3 || h<3) return
        g.fill(x,y,x+w,y+h,edge)
        g.fill(x+1,y+1,x+w-1,y+h-1,fill)
        g.fill(x+1,y+1,x+w-1,y+2,if(pressed) shadow else highlight)
        g.fill(x+1,y+2,x+2,y+h-1,if(pressed) shadow else casing)
        g.fill(x+1,y+h-2,x+w-1,y+h-1,if(pressed) casing else shadow)
        g.fill(x+w-2,y+2,x+w-1,y+h-2,if(pressed) highlight else shadow)
    }
    fun rivet(g:GuiGraphics,x:Int,y:Int) {
        g.fill(x,y,x+3,y+3,shadow);g.fill(x,y,x+2,y+2,highlight);g.fill(x+1,y+1,x+2,y+2,brassDark)
    }
    fun panel(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int,title:String="") {
        bevel(g,x,y,w,h)
        for(px in listOf(x+4,x+w-7)) for(py in listOf(y+4,y+h-7)) rivet(g,px,py)
        if(title.isNotEmpty()) {
            g.drawString(font,font.plainSubstrByWidth(title,(w-24).coerceAtLeast(1)),x+12,y+9,brass,false)
            g.fill(x+10,y+22,x+w-10,y+23,shadow)
        }
    }
    fun inset(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int,fill:Int=well) = bevel(g,x,y,w,h,fill,true)
    fun button(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int,hovered:Boolean=false,selected:Boolean=false,enabled:Boolean=true) {
        bevel(g,x,y,w,h,when { !enabled->panel;selected->0xff534d35.toInt();hovered->0xff50606d.toInt();else->raised },selected)
        if(selected || hovered && enabled) {
            g.fill(x+2,y+h-3,x+w-2,y+h-2,brass)
            if(selected) g.fill(x+3,y+4,x+5,y+h-4,green)
        }
        if(hovered && enabled) g.renderOutline(x,y,w,h,brass)
    }
    fun slot(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int,hovered:Boolean=false,selected:Boolean=false,enabled:Boolean=true) {
        inset(g,x,y,w,h,if(enabled) 0xff3f4b55.toInt() else 0xff222d36.toInt())
        if(selected) g.renderOutline(x,y,w,h,brass)
        if(hovered && enabled) g.fill(x+2,y+2,x+w-2,y+h-2,0x408bd082)
    }
    fun grid(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int,step:Int=16) {
        if(w<1 || h<1) return
        g.fill(x,y,x+w,y+h,blueprint)
        for(px in x until x+w step step) g.fill(px,y,px+1,y+h,grid)
        for(py in y until y+h step step) g.fill(x,py,x+w,py+1,grid)
        for(px in x until x+w step step*4) for(py in y until y+h step step*4) {
            g.fill(px,py,px+3,py+1,0xff3d5060.toInt());g.fill(px,py,px+1,py+3,0xff3d5060.toInt())
        }
    }
    fun viewport(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int) {
        inset(g,x,y,w,h,blueprint)
        grid(g,x+2,y+2,w-4,h-4)
        for(px in listOf(x+5,x+w-12)) for(py in listOf(y+5,y+h-12)) {
            g.fill(px,py,px+7,py+1,brassDark);g.fill(px,py,px+1,py+7,brassDark)
        }
    }
    /** Compact native CC casing leaves the existing eight-pixel dialog content margin intact. */
    fun background(g:GuiGraphics,w:Int,h:Int,peek:Boolean=false) {
        if(peek) { g.fill(0,0,w,h,0x30101820);return }
        g.fill(0,0,w,h,backdrop)
        g.pose().pushPose()
        g.pose().scale(0.5f,0.5f,1f)
        g.blitSprite(GuiSprites.getComputerTextures(ComputerFamily.ADVANCED).border(),0,0,w*2,h*2)
        g.pose().popPose()
        g.fill(6,6,w-6,h-6,backdrop)
    }
    fun header(g:GuiGraphics,w:Int,title:String,available:Int) {
        g.fill(8,7,w-8,24,well)
        g.fill(10,23,w-10,24,brassDark)
        g.drawString(font,font.plainSubstrByWidth(title,(available-16).coerceAtLeast(1)),14,11,text,false)
    }
    fun footer(g:GuiGraphics,w:Int,h:Int,message:String) {
        g.fill(8,h-12,w-8,h-4,well)
        g.fill(10,h-10,12,h-7,if(message.isBlank()) brassDark else green)
        g.drawString(font,font.plainSubstrByWidth(message,(w-32).coerceAtLeast(1)),17,h-12,muted,false)
    }
}
