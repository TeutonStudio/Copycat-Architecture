package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.server.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack

/** Physical slots inside the print inspector, clipped and hit-tested against the same viewport. */
class PrinterCartridgePanel(x:Int,y:Int,w:Int,private val clipTop:Int,private val clipBottom:Int,
                            private val menu:()->PrinterStorageMenu?,private val view:()->PrinterView?,
                            private val transaction:(Int,Int,ClickType)->Unit):AbstractWidget(x,y,w,if(w>=250) 151 else 123,tr("ps_cartridges")) {
    private val pitch=if(w>=250) 24 else 18
    private val size=if(w>=250) 22 else 18
    private val inventoryY=12+size+17
    private var bound:PrinterStorageMenu?=null
    private var mouse:PrinterInventoryMouse?=null
    val dragging get()=mouse?.active==true
    private fun controller():PrinterInventoryMouse? {
        val current=menu()
        if(bound!==current) { mouse?.reset();bound=current;mouse=current?.let { PrinterInventoryMouse(it,transaction) } }
        return mouse
    }
    private fun rect(slot:Int):IntArray {
        if(slot<7) return intArrayOf(x+(width-7*pitch)/2+slot*pitch,y+12,size,size)
        val i=slot-7;val column=if(i<9) i else (i-9)%9;val row=if(i<9) 3 else (i-9)/9
        return intArrayOf(x+(width-9*pitch)/2+column*pitch,y+inventoryY+row*pitch+if(row==3) 4 else 0,size,size)
    }
    fun slotPoint(slot:Int):Pair<Double,Double> {
        val r=rect(slot);return r[0]+r[2]/2.0 to r[1]+r[3]/2.0
    }
    fun slotAt(mx:Double,my:Double):Int {
        if(my<clipTop || my>=clipBottom || mx<x || mx>=x+width) return -1
        return (0..42).firstOrNull { i -> val r=rect(i);mx>=r[0] && mx<r[0]+r[2] && my>=r[1] && my<r[1]+r[3] } ?: -1
    }
    private fun stack(slot:Int):ItemStack {
        menu()?.let { return it.getSlot(slot).item }
        if(slot>=7) return Minecraft.getInstance().player?.inventory?.getItem(slot-7) ?: ItemStack.EMPTY
        val entry=view()?.slots?.getOrNull(slot) ?: return ItemStack.EMPTY
        val id=ResourceLocation.tryParse(entry.item) ?: return ItemStack.EMPTY
        return if(BuiltInRegistries.ITEM.containsKey(id)) ItemStack(BuiltInRegistries.ITEM.get(id)) else ItemStack.EMPTY
    }
    override fun renderWidget(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        controller()
        val font=Minecraft.getInstance().font
        g.enableScissor(x,clipTop,x+width,clipBottom)
        try {
            val current=menu();val snapshot=view()?.slots.orEmpty()
            val infinite=if(current!=null) (0..6).any { Economy.isCreativeCartridge(current.getSlot(it).item) } else snapshot.any { it.creative }
            val units=if(current!=null) (0..6).sumOf { Economy.units(current.getSlot(it).item).toLong() } else snapshot.sumOf { it.units.toLong() }
            val amount="${if(infinite) "∞" else units} mB"
            g.drawString(font,font.plainSubstrByWidth(ui("ps_cartridges"),(width-font.width(amount)-8).coerceAtLeast(1)),x+2,y,ArchitectTheme.brass,false)
            g.drawString(font,amount,x+width-font.width(amount)-2,y,ArchitectTheme.green,false)
            g.drawString(font,tr("ps_inventory"),x+2,y+inventoryY-11,ArchitectTheme.muted,false)
            for(i in 0..42) {
                val r=rect(i);if(r[1]+r[3]<=clipTop || r[1]>=clipBottom) continue
                val value=stack(i)
                val enabled=menu()?.let { m -> if(i<7) m.carried.isEmpty || Economy.isCartridge(m.carried) else i!=7+m.lockedInventorySlot } ?: false
                ArchitectTheme.slot(g,r[0],r[1],r[2],r[3],slotAt(mx.toDouble(),my.toDouble())==i,false,enabled)
                if(!value.isEmpty) {
                    val px=r[0]+(r[2]-16)/2;val py=r[1]+(r[3]-16)/2
                    g.renderItem(value,px,py);g.renderItemDecorations(font,value,px,py)
                }
                if(i<7) {
                    val entry=view()?.slots?.getOrNull(i)
                    val creative=if(menu()!=null) Economy.isCreativeCartridge(value) else entry?.creative==true
                    g.fill(r[0]+r[2]-4,r[1]+2,r[0]+r[2]-2,r[1]+4,if(creative) ArchitectTheme.brass else if(value.isEmpty) ArchitectTheme.shadow else ArchitectTheme.green)
                }
            }
        } finally { g.flush();g.disableScissor() }
    }
    fun renderTooltip(g:GuiGraphics,mx:Int,my:Int) {
        if(menu()?.carried?.isEmpty==false) return
        val index=slotAt(mx.toDouble(),my.toDouble());if(index<0) return
        val value=stack(index);val font=Minecraft.getInstance().font
        if(index<7) {
            val entry=view()?.slots?.getOrNull(index)
            val units=if(menu()!=null) Economy.units(value) else entry?.units ?: 0
            val creative=if(menu()!=null) Economy.isCreativeCartridge(value) else entry?.creative==true
            g.renderComponentTooltip(font,listOf(tr("ps_slot",index+1),if(value.isEmpty) tr("ps_slot_empty") else value.hoverName,
                if(creative) net.minecraft.network.chat.Component.translatable("item.copycat_architect.cartridge.infinite") else tr("ps_raw_units",units),tr("ps_take_hint")),mx,my)
        } else if(!value.isEmpty) g.renderTooltip(font,value,mx,my)
    }
    override fun mouseClicked(mx:Double,my:Double,button:Int):Boolean {
        val slot=slotAt(mx,my)
        if(slot<0 || button !in 0..1) return false
        controller()?.press(slot,button,net.minecraft.client.gui.screens.Screen.hasShiftDown())
        return true
    }
    override fun mouseDragged(mx:Double,my:Double,button:Int,dx:Double,dy:Double):Boolean {
        if(!dragging) return false
        mouse?.drag(slotAt(mx,my));return true
    }
    override fun mouseReleased(mx:Double,my:Double,button:Int):Boolean {
        if(!dragging) return false
        mouse?.release(slotAt(mx,my),button);return true
    }
    override fun updateWidgetNarration(output:NarrationElementOutput) { defaultButtonNarrationText(output) }
}
