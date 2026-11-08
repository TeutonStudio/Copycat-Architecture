package de.teutonstudio.copycatarchitect.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics

/** Shared pixel-built machine casing. All dimensions are GUI pixels, with no filtered artwork. */
object ArchitectTheme {
    val backdrop=0xff272923.toInt()
    val casing=0xff858575.toInt()
    val panel=0xff454c40.toInt()
    val raised=0xff666c59.toInt()
    val highlight=0xffc5c3aa.toInt()
    val shadow=0xff1b211a.toInt()
    val well=0xff17241c.toInt()
    val blueprint=0xff1c3028.toInt()
    val grid=0xff2a4133.toInt()
    val brass=0xffd5b56c.toInt()
    val brassDark=0xff85663c.toInt()
    val text=0xfff0ead2.toInt()
    val muted=0xffb0b79b.toInt()
    val disabled=0xff838b76.toInt()
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
        bevel(g,x,y,w,h,when { !enabled->panel;selected->0xff625c3e.toInt();hovered->0xff808166.toInt();else->raised },selected)
        if(selected || hovered && enabled) {
            g.fill(x+2,y+h-3,x+w-2,y+h-2,brass)
            if(selected) g.fill(x+3,y+4,x+5,y+h-4,green)
        }
        if(hovered && enabled) g.renderOutline(x,y,w,h,brass)
    }
    fun slot(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int,hovered:Boolean=false,selected:Boolean=false,enabled:Boolean=true) {
        inset(g,x,y,w,h,if(enabled) 0xff626957.toInt() else 0xff3e473b.toInt())
        if(selected) g.renderOutline(x,y,w,h,brass)
        if(hovered && enabled) g.fill(x+2,y+2,x+w-2,y+h-2,0x408bd082)
    }
    fun grid(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int,step:Int=16) {
        if(w<1 || h<1) return
        g.fill(x,y,x+w,y+h,blueprint)
        for(px in x until x+w step step) g.fill(px,y,px+1,y+h,grid)
        for(py in y until y+h step step) g.fill(x,py,x+w,py+1,grid)
        for(px in x until x+w step step*4) for(py in y until y+h step step*4) {
            g.fill(px,py,px+3,py+1,0xff43563d.toInt());g.fill(px,py,px+1,py+3,0xff43563d.toInt())
        }
    }
    fun viewport(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int) {
        inset(g,x,y,w,h,blueprint)
        grid(g,x+2,y+2,w-4,h-4)
        for(px in listOf(x+5,x+w-12)) for(py in listOf(y+5,y+h-12)) {
            g.fill(px,py,px+7,py+1,brassDark);g.fill(px,py,px+1,py+7,brassDark)
        }
    }
    fun background(g:GuiGraphics,w:Int,h:Int,peek:Boolean=false) {
        g.fill(0,0,w,h,if(peek) 0x30272923 else backdrop)
        if(!peek) {
            for(y in 24 until h-16 step 32) g.fill(0,y,w,y+1,0xff2d3028.toInt())
            g.fill(0,24,3,h-16,casing);g.fill(w-3,24,w,h-16,shadow)
        }
    }
    fun header(g:GuiGraphics,w:Int,title:String,available:Int) {
        bevel(g,0,0,w,24,0xff555b49.toInt())
        g.fill(0,22,w,24,brassDark)
        rivet(g,5,5)
        // A small copper maker's mark leaves the title legible at the smallest supported size.
        g.fill(12,6,22,16,shadow);g.renderOutline(13,7,8,8,brass);g.fill(16,10,18,12,green)
        g.drawString(font,font.plainSubstrByWidth(title,(available-20).coerceAtLeast(1)),28,8,text,false)
    }
    fun footer(g:GuiGraphics,w:Int,h:Int,message:String) {
        inset(g,4,h-16,w-8,14)
        g.fill(9,h-11,12,h-8,if(message.isBlank()) brassDark else green)
        g.drawString(font,font.plainSubstrByWidth(message,(w-28).coerceAtLeast(1)),17,h-12,muted,false)
    }
}
