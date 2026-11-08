package de.teutonstudio.copycatarchitect.client

import dan200.computercraft.client.gui.GuiSprites
import dan200.computercraft.shared.computer.core.ComputerFamily
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component
import org.lwjgl.opengl.GL11

/** Shared printer/table frame: the advanced computer's native border and two exterior rails. */
data class PrinterWorkspaceLayout(val screenWidth:Int,val screenHeight:Int,val topTabs:Boolean=false) {
    val left=24;val top=if(topTabs) 24 else 12;val width=screenWidth-48;val height=screenHeight-top-12
    val x=left+12;val y=top+12;val innerWidth=width-24
    val graphY=y+24;val graphHeight=height-24-38
    val bottom=graphY+graphHeight
    val panelWidth=minOf(300,maxOf(184,(innerWidth*0.42).toInt()))
    val panelX=x+innerWidth-panelWidth
    fun graphWidth(panel:Boolean)=if(panel) innerWidth-panelWidth-4 else innerWidth
    fun render(g:GuiGraphics,peek:Boolean) {
        if(!peek) {
            g.fill(0,0,screenWidth,screenHeight,0xb0101010.toInt())
            g.blitSprite(GuiSprites.getComputerTextures(ComputerFamily.ADVANCED).border(),left,top,width,height)
            g.fill(x,y,x+innerWidth,y+height-24,0xff101820.toInt())
        }
    }
}

/** Mirrors TeutonLib's 17×22 CC sidebar tabs for the right edge, retaining upright symbols. */
class PrinterEdgeTab(x:Int,y:Int,label:Component,private val symbol:String,private val right:Boolean=false,
                     private val selected:()->Boolean,action:()->Unit)
    :Button(x,y,17,22,label,{ action() },DEFAULT_NARRATION) {
    init { setTooltip(Tooltip.create(label)) }
    override fun renderWidget(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        renderComputerTab(g,x,y,width,height,symbol,right,false,isHoveredOrFocused,selected())
    }
}

/** The same native CC sidebar sprite on every rail; only its orientation changes. */
internal fun renderComputerTab(g:GuiGraphics,x:Int,y:Int,width:Int,height:Int,symbol:String,right:Boolean,top:Boolean,hovered:Boolean,selected:Boolean) {
    val sprite=GuiSprites.getComputerTextures(ComputerFamily.ADVANCED).sidebar()
    if(sprite!=null) {
        if(right || top) {
            g.flush()
            val cull=GL11.glIsEnabled(GL11.GL_CULL_FACE)
            RenderSystem.disableCull()
            g.pose().pushPose()
            if(top) {
                g.pose().translate((x+width).toFloat(),y.toFloat(),0f)
                g.pose().mulPose(Axis.ZP.rotationDegrees(90f))
                g.blitSprite(sprite,0,0,height,width)
            } else {
                g.pose().translate((2*x+width).toFloat(),0f,0f);g.pose().scale(-1f,1f,1f)
                g.blitSprite(sprite,x,y,width,height)
            }
            g.flush();g.pose().popPose()
            if(cull) RenderSystem.enableCull()
        } else g.blitSprite(sprite,x,y,width,height)
    }
    val color=if(hovered || selected) 0xffffffff.toInt() else 0xffdcdcdc.toInt()
    val font=Minecraft.getInstance().font
    val px=x+(width-11)/2;val py=y+(height-12)/2
    when(symbol) {
        "I" -> {
            for(row in 0..2) { val yy=py+2+row*4;g.fill(px,yy,px+11,yy+1,color);g.fill(px+2+row*3,yy-1,px+4+row*3,yy+2,color) }
        }
        "P" -> {
            g.renderOutline(px,py+4,11,6,color);g.renderOutline(px+2,py,7,5,color)
            g.fill(px+2,py+7,px+9,py+12,color);g.fill(px+3,py+8,px+8,py+9,0xff6c632e.toInt())
        }
        "H" -> {
            g.renderOutline(px+1,py+1,10,10,color)
            g.fill(px+5,py+3,px+6,py+7,color);g.fill(px+5,py+6,px+9,py+7,color)
            g.fill(px,py+2,px+2,py+6,color)
        }
        else -> g.drawString(font,symbol,x+(width-font.width(symbol))/2,y+(height-8)/2,color,false)
    }
    if(selected) {
        if(top) g.fill(x+5,y+height-2,x+width-5,y+height,0xffffcc55.toInt())
        else {
            val edge=if(right) x else x+width-2
            g.fill(edge,y+5,edge+2,y+height-5,0xffffcc55.toInt())
        }
    }
}
