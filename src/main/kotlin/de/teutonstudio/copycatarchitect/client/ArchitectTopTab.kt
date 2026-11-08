package de.teutonstudio.copycatarchitect.client

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component

/** Horizontal table navigation is separate from the graph workspace's side tools. */
class ArchitectTopTab(x:Int,y:Int,label:Component,private val symbol:String,val selected:Boolean,action:()->Unit):
    Button(x,y,22,17,label,{ action() },DEFAULT_NARRATION) {
    init { setTooltip(Tooltip.create(label)) }
    override fun renderWidget(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        renderComputerTab(g,x,y,width,height,symbol,false,true,isHoveredOrFocused,selected)
    }
}
