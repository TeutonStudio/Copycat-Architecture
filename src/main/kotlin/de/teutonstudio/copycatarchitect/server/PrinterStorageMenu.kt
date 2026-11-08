package de.teutonstudio.copycatarchitect.server

import de.teutonstudio.copycatarchitect.Architect
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

/** The standard Minecraft transaction and cursor handling behind the printer's custom screen. */
class PrinterStorageMenu(
    id:Int,
    playerInventory:Inventory,
    val cartridges:Container=SimpleContainer(7),
    private val validity:(Player)->Boolean={ true },
    val lockedInventorySlot:Int=-1,
    val storageIdentity:String=""
):AbstractContainerMenu(Architect.PRINTER_MENU.get(),id) {
    init {
        checkContainerSize(cartridges,7)
        repeat(7) { index ->
            addSlot(object:Slot(cartridges,index,0,0) {
                override fun mayPlace(stack:ItemStack)=Economy.isCartridge(stack)
                override fun getMaxStackSize()=1
            })
        }
        repeat(36) { index ->
            addSlot(object:Slot(playerInventory,index,0,0) {
                override fun mayPickup(player:Player)=index!=lockedInventorySlot && super.mayPickup(player)
                override fun mayPlace(stack:ItemStack)=index!=lockedInventorySlot && super.mayPlace(stack)
            })
        }
    }

    override fun stillValid(player:Player)=validity(player)

    override fun clicked(slotId:Int,button:Int,clickType:ClickType,player:Player) {
        if(!stillValid(player)) return
        // Number keys and the offhand key can otherwise move the printer without clicking its slot.
        if(lockedInventorySlot>=0 && (slotId==7+lockedInventorySlot || clickType==ClickType.SWAP && button==lockedInventorySlot)) return
        super.clicked(slotId,button,clickType,player)
    }

    override fun quickMoveStack(player:Player,index:Int):ItemStack {
        if(!stillValid(player) || index !in slots.indices) return ItemStack.EMPTY
        val source=slots[index]
        if(!source.hasItem() || !source.mayPickup(player)) return ItemStack.EMPTY
        val stack=source.item
        val original=stack.copy()
        val moved=when {
            index<7 -> moveItemStackTo(stack,7,43,true)
            Economy.isCartridge(stack) -> moveItemStackTo(stack,0,7,false)
            index<16 -> moveItemStackTo(stack,16,43,false)
            else -> moveItemStackTo(stack,7,16,false)
        }
        if(!moved) return ItemStack.EMPTY
        if(stack.isEmpty) source.setByPlayer(ItemStack.EMPTY) else source.setChanged()
        source.onTake(player,stack)
        return original
    }
}
