package de.teutonstudio.copycatarchitect.client

import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ClickType

/** Mouse gestures share vanilla's authoritative slot transactions and carried stack. */
internal class PrinterInventoryMouse(private val menu:AbstractContainerMenu,private val click:(Int,Int,ClickType)->Unit) {
    private var origin=-1
    private var button=-1
    private var pickedUp=false
    private var shifted=false
    private val visited=linkedSetOf<Int>()
    val active get()=button>=0

    fun press(slot:Int,mouseButton:Int,shift:Boolean) {
        reset()
        origin=slot;button=mouseButton;shifted=shift
        if(shift) click(slot,mouseButton,ClickType.QUICK_MOVE)
        else if(menu.carried.isEmpty) {
            click(slot,mouseButton,ClickType.PICKUP)
            pickedUp=!menu.carried.isEmpty
        } else visited+=slot
    }
    fun drag(slot:Int) {
        if(button<0 || shifted || slot<0 || menu.carried.isEmpty) return
        if(pickedUp) return
        val target=menu.slots[slot]
        if(target.mayPlace(menu.carried) && menu.canDragTo(target) && AbstractContainerMenu.canItemQuickReplace(target,menu.carried,true)) visited+=slot
    }
    fun release(slot:Int,mouseButton:Int) {
        if(button!=mouseButton) return
        if(!shifted && !menu.carried.isEmpty) {
            if(pickedUp) {
                // A normal click keeps the picked-up stack; a drag drops it at release.
                if(slot>=0 && slot!=origin) click(slot,button,ClickType.PICKUP)
            } else if(visited.size>1 && menu.carried.count>1) {
                val kind=if(button==1) 1 else 0
                click(-999,AbstractContainerMenu.getQuickcraftMask(0,kind),ClickType.QUICK_CRAFT)
                visited.forEach { click(it,AbstractContainerMenu.getQuickcraftMask(1,kind),ClickType.QUICK_CRAFT) }
                click(-999,AbstractContainerMenu.getQuickcraftMask(2,kind),ClickType.QUICK_CRAFT)
            } else if(slot>=0) click(slot,button,ClickType.PICKUP)
        }
        reset()
    }
    fun reset() { origin=-1;button=-1;pickedUp=false;shifted=false;visited.clear() }
}
