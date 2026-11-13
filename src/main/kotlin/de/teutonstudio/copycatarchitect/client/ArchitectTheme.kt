package de.teutonstudio.copycatarchitect.client

import dan200.computercraft.client.gui.GuiSprites
import dan200.computercraft.shared.computer.core.ComputerFamily
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics

/** CC's native advanced-computer casing around neutral controls and dark terminal surfaces. */
object ArchitectTheme {
    val backdrop=0xb0101010.toInt()
    val casing=0xff999999.toInt()
    val panel=0xff262626.toInt()
    val raised=0xff4c4c4c.toInt()
    val highlight=0xffb2b2b2.toInt()
    val shadow=0xff111111.toInt()
    val well=0xff191919.toInt()
    val blueprint=0xff111111.toInt()
    val grid=0xff222222.toInt()
    val gridAccent=0xff333333.toInt()
    val brass=0xfff2d96b.toInt()
    val brassDark=0xff7f763c.toInt()
    val text=0xfff0f0f0.toInt()
    val muted=0xffb2b2b2.toInt()
    val disabled=0xff777777.toInt()
    val green=0xff8ed17a.toInt()
    val danger=0xfff28b82.toInt()
    val hovered=0xff3c3c3c.toInt()
    val selected=0xff494632.toInt()
    val alternate=0xff222222.toInt()
    private val font get()=Minecraft.getInstance().font

    /** Shared with the printer and table so resource packs also skin every secondary window. */
    fun computer(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int) {
        if(w<24 || h<24) return
        g.blitSprite(GuiSprites.getComputerTextures(ComputerFamily.ADVANCED).border(),x,y,w,h)
        g.fill(x+12,y+12,x+w-12,y+h-12,blueprint)
    }
    fun bevel(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int,fill:Int=panel,pressed:Boolean=false,edge:Int=shadow) {
        if(w<3 || h<3) return
        g.fill(x,y,x+w,y+h,edge)
        g.fill(x+1,y+1,x+w-1,y+h-1,fill)
        g.fill(x+1,y+1,x+w-1,y+2,if(pressed) shadow else casing)
        g.fill(x+1,y+2,x+2,y+h-1,if(pressed) shadow else casing)
        g.fill(x+1,y+h-2,x+w-1,y+h-1,if(pressed) raised else shadow)
        g.fill(x+w-2,y+2,x+w-1,y+h-2,if(pressed) raised else shadow)
    }
    fun panel(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int,title:String="") {
        if(w<3 || h<3) return
        g.fill(x,y,x+w,y+h,panel)
        g.renderOutline(x,y,w,h,raised)
        heading(g,x,y,w,title)
    }
    /** Keep the established content/hit bounds while adding the same native 12px CC border. */
    fun dialog(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int,title:String) {
        computer(g,x-4,y-4,w+8,h+8)
        heading(g,x,y,w,title)
    }
    private fun heading(g:GuiGraphics,x:Int,y:Int,w:Int,title:String) {
        if(title.isNotEmpty()) {
            g.drawString(font,font.plainSubstrByWidth(title,(w-24).coerceAtLeast(1)),x+12,y+9,brass,false)
            g.fill(x+10,y+22,x+w-10,y+23,raised)
        }
    }
    fun inset(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int,fill:Int=well) = bevel(g,x,y,w,h,fill,true)
    fun button(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int,hovered:Boolean=false,selected:Boolean=false,enabled:Boolean=true) {
        bevel(g,x,y,w,h,when { !enabled->panel;selected->this.selected;hovered->0xff666666.toInt();else->raised },selected)
        if(selected && enabled) g.fill(x+3,y+4,x+5,y+h-4,brass)
        if(hovered && enabled) g.renderOutline(x,y,w,h,text)
    }
    fun slot(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int,hovered:Boolean=false,selected:Boolean=false,enabled:Boolean=true) {
        inset(g,x,y,w,h,if(enabled) raised else panel)
        if(selected) g.renderOutline(x,y,w,h,brass)
        if(hovered && enabled) g.fill(x+2,y+2,x+w-2,y+h-2,0x40ffffff)
    }
    fun grid(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int,step:Int=16) {
        if(w<1 || h<1) return
        g.fill(x,y,x+w,y+h,blueprint)
        for(px in x until x+w step step) g.fill(px,y,px+1,y+h,grid)
        for(py in y until y+h step step) g.fill(x,py,x+w,py+1,grid)
        for(px in x until x+w step step*4) for(py in y until y+h step step*4) {
            g.fill(px,py,px+3,py+1,gridAccent);g.fill(px,py,px+1,py+3,gridAccent)
        }
    }
    fun viewport(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int) {
        inset(g,x,y,w,h,blueprint)
        grid(g,x+2,y+2,w-4,h-4)
    }
    fun background(g:GuiGraphics,w:Int,h:Int,peek:Boolean=false) {
        g.fill(0,0,w,h,if(peek) 0x30101010 else backdrop)
        // Full-screen tools retain their 8px content margins; modal dialogs use the full border.
        if(!peek) computer(g,-8,-8,w+16,h+16)
    }
    fun header(g:GuiGraphics,w:Int,title:String,available:Int) {
        g.fill(8,4,w-8,24,blueprint)
        g.drawString(font,font.plainSubstrByWidth(title,(available-12).coerceAtLeast(1)),12,8,text,false)
        g.fill(8,23,w-8,24,raised)
    }
    fun footer(g:GuiGraphics,w:Int,h:Int,message:String) {
        g.fill(8,h-16,w-8,h-4,blueprint)
        g.drawString(font,font.plainSubstrByWidth(message,(w-24).coerceAtLeast(1)),12,h-12,muted,false)
    }
}
