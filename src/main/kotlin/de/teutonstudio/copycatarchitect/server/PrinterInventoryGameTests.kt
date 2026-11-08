package de.teutonstudio.copycatarchitect.server

import com.mojang.authlib.GameProfile
import de.teutonstudio.copycatarchitect.Architect
import de.teutonstudio.copycatarchitect.client.PrinterInventoryMouse
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.MenuProvider
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.common.util.FakePlayer
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import java.util.OptionalInt
import java.util.UUID
import java.util.function.Consumer

@GameTestHolder(Architect.ID)
@PrefixGameTestTemplate(false)
object PrinterInventoryGameTests {
    /** FakePlayer suppresses opening menus; retain the real provider and menu for server interaction tests. */
    private class InventoryPlayer(level:ServerLevel,name:String):FakePlayer(level,GameProfile(UUID.randomUUID(),name)) {
        override fun openMenu(provider:MenuProvider?,extraDataWriter:Consumer<RegistryFriendlyByteBuf>?):OptionalInt {
            if(provider==null) return OptionalInt.empty()
            if(containerMenu!==inventoryMenu) closeContainer()
            containerMenu=provider.createMenu(1,inventory,this) ?: return OptionalInt.empty()
            return OptionalInt.of(containerMenu.containerId)
        }
    }
    private fun actor(h:GameTestHelper,name:String)=InventoryPlayer(h.level,name).also {
        it.gameMode.changeGameModeForPlayer(GameType.SURVIVAL)
        val pos=h.absolutePos(BlockPos(1,1,1));it.setPos(pos.x+4.0,pos.y.toDouble(),pos.z+4.0)
    }
    private fun cartridge(units:Int,name:String)=Economy.cartridge(units).also {
        it.set(DataComponents.CUSTOM_NAME,Component.literal(name))
        PrinterStack.update(it) { tag -> tag.putString("inventory_test",name) }
    }
    private fun playerSlot(index:Int)=7+index
    private fun inventoryCopies(player:InventoryPlayer,expected:ItemStack)=(0 until player.inventory.containerSize)
        .sumOf { if(ItemStack.isSameItemSameComponents(player.inventory.getItem(it),expected)) player.inventory.getItem(it).count else 0 }
    private fun menu(player:InventoryPlayer,cartridges:SimpleContainer=SimpleContainer(7))=
        PrinterStorageMenu(1,player.inventory,cartridges).also { player.containerMenu=it }
    private fun opened(h:GameTestHelper,player:InventoryPlayer):PrinterStorageMenu {
        PrinterService.openStorage(player)
        h.assertTrue(player.containerMenu is PrinterStorageMenu,"Storage must open the native printer menu on the server")
        return player.containerMenu as PrinterStorageMenu
    }
    private fun drag(menu:PrinterStorageMenu,player:InventoryPlayer,slots:List<Int>,type:Int=0) {
        menu.clicked(-999,AbstractContainerMenu.getQuickcraftMask(0,type),ClickType.QUICK_CRAFT,player)
        slots.forEach { menu.clicked(it,AbstractContainerMenu.getQuickcraftMask(1,type),ClickType.QUICK_CRAFT,player) }
        menu.clicked(-999,AbstractContainerMenu.getQuickcraftMask(2,type),ClickType.QUICK_CRAFT,player)
    }

    @JvmStatic @GameTest(template="empty") fun printerMousePickupSwapAndDepositPreserveCompleteCartridges(h:GameTestHelper) {
        val player=actor(h,"PrinterMousePickup");val cartridges=SimpleContainer(7)
        val first=cartridge(173,"Partially filled named cartridge");val second=cartridge(49,"Second named cartridge")
        cartridges.setItem(0,first.copy());cartridges.setItem(1,second.copy());val menu=menu(player,cartridges)
        menu.clicked(0,0,ClickType.PICKUP,player)
        h.assertTrue(cartridges.getItem(0).isEmpty && ItemStack.matches(menu.carried,first),"Left-click must pick up the complete cartridge on the cursor, including its fill level, name and unrelated data")
        menu.clicked(1,0,ClickType.PICKUP,player)
        h.assertTrue(ItemStack.matches(cartridges.getItem(1),first) && ItemStack.matches(menu.carried,second),"Clicking another cartridge must swap it with the cursor stack")
        menu.clicked(playerSlot(10),0,ClickType.PICKUP,player)
        h.assertTrue(menu.carried.isEmpty && ItemStack.matches(player.inventory.getItem(10),second),"A carried cartridge must be deposited into the exact clicked player slot")
        menu.clicked(playerSlot(10),0,ClickType.PICKUP,player);menu.clicked(6,0,ClickType.PICKUP,player)
        h.assertTrue(player.inventory.getItem(10).isEmpty && menu.carried.isEmpty && ItemStack.matches(cartridges.getItem(6),second),"An inventory cartridge must also travel on the cursor into the selected printer slot")
        player.closeContainer();h.succeed()
    }

    @JvmStatic @GameTest(template="empty") fun printerMouseGesturesCarryOnPressAndSupportDragReleaseAndDistribution(h:GameTestHelper) {
        val player=actor(h,"PrinterMouseGestures");val cartridges=SimpleContainer(7);val menu=menu(player,cartridges)
        val mouse=PrinterInventoryMouse(menu) { slot,button,type -> menu.clicked(slot,button,type,player) }
        val sample=cartridge(159,"Mouse gesture cartridge");player.inventory.setItem(10,sample.copy())
        mouse.press(playerSlot(10),0,false)
        h.assertTrue(player.inventory.getItem(10).isEmpty && ItemStack.matches(menu.carried,sample),"Pressing a source slot must immediately put its real stack on the mouse cursor")
        mouse.release(playerSlot(10),0)
        h.assertTrue(ItemStack.matches(menu.carried,sample),"Releasing the same slot must keep the picked-up cartridge on the cursor")
        mouse.press(2,0,false);mouse.release(2,0)
        h.assertTrue(menu.carried.isEmpty && ItemStack.matches(cartridges.getItem(2),sample),"A later target click must deposit the cursor cartridge into that printer slot")
        mouse.press(2,0,false);mouse.drag(playerSlot(11));mouse.release(playerSlot(11),0)
        h.assertTrue(menu.carried.isEmpty && cartridges.isEmpty && ItemStack.matches(player.inventory.getItem(11),sample),"Holding the source press and releasing over another slot must move the cartridge in one drag gesture")
        player.inventory.setItem(12,ItemStack(Items.STONE,8));mouse.press(playerSlot(12),0,false);mouse.release(playerSlot(12),0)
        mouse.press(playerSlot(13),0,false);mouse.drag(0);mouse.drag(playerSlot(14));mouse.release(playerSlot(14),0)
        h.assertTrue(menu.carried.isEmpty && cartridges.isEmpty && (13..14).all { player.inventory.getItem(it).`is`(Items.STONE) && player.inventory.getItem(it).count==4 },"Dragging an already carried stack must distribute it across valid player slots while rejecting printer storage")
        player.closeContainer();h.succeed()
    }

    @JvmStatic @GameTest(template="empty") fun printerInventoryRightClickSplitsAndPlacesOneWhileStorageRejectsOtherItems(h:GameTestHelper) {
        val player=actor(h,"PrinterMouseSplit");val cartridges=SimpleContainer(7);val menu=menu(player,cartridges)
        val stones=ItemStack(Items.STONE,9).also { it.set(DataComponents.CUSTOM_NAME,Component.literal("Named building materials")) }
        player.inventory.setItem(9,stones.copy())
        menu.clicked(playerSlot(9),1,ClickType.PICKUP,player)
        h.assertTrue(player.inventory.getItem(9).count==4 && ItemStack.matches(menu.carried,stones.copyWithCount(5)),"Right-click pickup must take the rounded-up half of a player stack and preserve its components")
        menu.clicked(0,0,ClickType.PICKUP,player);menu.clicked(1,1,ClickType.PICKUP,player)
        h.assertTrue(cartridges.isEmpty && menu.carried.count==5,"Neither mouse button may insert a non-cartridge into printer storage")
        menu.clicked(playerSlot(10),1,ClickType.PICKUP,player)
        h.assertTrue(ItemStack.matches(player.inventory.getItem(10),stones.copyWithCount(1)) && menu.carried.count==4,"Right-click must place exactly one item into a player inventory slot")
        menu.clicked(playerSlot(11),0,ClickType.PICKUP,player)
        h.assertTrue(menu.carried.isEmpty && ItemStack.matches(player.inventory.getItem(11),stones.copyWithCount(4)) && inventoryCopies(player,stones)==9,"Left-click must deposit the remaining split stack without changing the item total")
        player.closeContainer();h.succeed()
    }

    @JvmStatic @GameTest(template="empty") fun printerShiftClickTransfersOnlyCartridgesAndRetainsComponents(h:GameTestHelper) {
        val player=actor(h,"PrinterShiftClick");val cartridges=SimpleContainer(7);val menu=menu(player,cartridges)
        val sample=cartridge(117,"Shift-click cartridge");player.inventory.setItem(10,sample.copy())
        menu.clicked(playerSlot(10),0,ClickType.QUICK_MOVE,player)
        h.assertTrue(player.inventory.getItem(10).isEmpty && ItemStack.matches(cartridges.getItem(0),sample) && menu.carried.isEmpty,"Shift-click from player inventory must transfer a complete cartridge into storage")
        menu.clicked(0,0,ClickType.QUICK_MOVE,player)
        h.assertTrue(cartridges.isEmpty && inventoryCopies(player,sample)==1 && menu.carried.isEmpty,"Shift-click from storage must return exactly one unchanged cartridge to the player")
        player.inventory.setItem(12,ItemStack(Items.STONE,32));menu.clicked(playerSlot(12),0,ClickType.QUICK_MOVE,player)
        h.assertTrue(cartridges.isEmpty && (0 until player.inventory.containerSize).sumOf { val stack=player.inventory.getItem(it);if(stack.`is`(Items.STONE)) stack.count else 0 }==32,"Shift-clicking building material must never put it in cartridge storage or lose items")
        player.closeContainer();h.succeed()
    }

    @JvmStatic @GameTest(template="empty") fun printerInventorySupportsLeftAndRightMouseDragDistribution(h:GameTestHelper) {
        val player=actor(h,"PrinterMouseDrag");val cartridges=SimpleContainer(7);val menu=menu(player,cartridges)
        player.inventory.setItem(10,ItemStack(Items.STONE,9));menu.clicked(playerSlot(10),0,ClickType.PICKUP,player)
        drag(menu,player,listOf(playerSlot(11),0,playerSlot(12),playerSlot(13)))
        h.assertTrue(menu.carried.isEmpty && (11..13).all { player.inventory.getItem(it).`is`(Items.STONE) && player.inventory.getItem(it).count==3 } && cartridges.isEmpty,"Left-button drag must divide a carried stack evenly across accepted player slots and skip cartridge-only storage")
        player.inventory.setItem(14,ItemStack(Items.GLASS,5));menu.clicked(playerSlot(14),0,ClickType.PICKUP,player)
        drag(menu,player,listOf(playerSlot(15),1,playerSlot(16)),1)
        h.assertTrue(menu.carried.count==3 && (15..16).all { player.inventory.getItem(it).`is`(Items.GLASS) && player.inventory.getItem(it).count==1 } && cartridges.isEmpty,"Right-button drag must place one item per accepted player slot and retain the cursor remainder")
        menu.clicked(playerSlot(17),0,ClickType.PICKUP,player);player.closeContainer();h.succeed()
    }

    @JvmStatic @GameTest(template="empty") fun closingPrinterStorageReturnsCursorOnceWithoutLosingOrDuplicatingItems(h:GameTestHelper) {
        val player=actor(h,"PrinterCursorClose");val cartridges=SimpleContainer(7)
        val sample=cartridge(213,"Cursor return cartridge");cartridges.setItem(2,sample.copy());val menu=menu(player,cartridges)
        menu.clicked(2,0,ClickType.PICKUP,player);player.closeContainer()
        h.assertTrue(menu.carried.isEmpty && cartridges.isEmpty && inventoryCopies(player,sample)==1,"Closing storage with a cartridge on the cursor must return it once and leave the printer slot empty")
        menu.removed(player)
        h.assertTrue(inventoryCopies(player,sample)==1 && h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(player.blockPosition()).inflate(2.0)).isEmpty(),"Repeated close cleanup must not duplicate the returned cartridge or also drop an item")
        h.succeed()
    }

    @JvmStatic @GameTest(template="empty") fun closingInvalidPrinterStorageWithFullInventoryDropsCursorExactlyOnce(h:GameTestHelper) {
        val player=actor(h,"PrinterFullCursor");repeat(36) { player.inventory.setItem(it,ItemStack(Items.STONE,64)) }
        val cartridges=SimpleContainer(7);val sample=cartridge(223,"Full inventory cursor cartridge");cartridges.setItem(0,sample.copy())
        var valid=true
        val menu=PrinterStorageMenu(1,player.inventory,cartridges,{ valid });player.containerMenu=menu
        menu.clicked(0,0,ClickType.PICKUP,player);valid=false
        menu.clicked(1,0,ClickType.PICKUP,player);menu.clicked(playerSlot(10),0,ClickType.PICKUP,player)
        menu.clicked(playerSlot(11),0,ClickType.QUICK_MOVE,player)
        h.assertTrue(!menu.stillValid(player) && cartridges.isEmpty && ItemStack.matches(menu.carried,sample),"An invalidated menu must reject further cursor and shift-click transfers while retaining its carried cartridge")
        player.closeContainer()
        fun drops()=h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(player.blockPosition()).inflate(2.0))
            .filter { ItemStack.isSameItemSameComponents(it.item,sample) }
        h.assertTrue(menu.carried.isEmpty && cartridges.isEmpty && inventoryCopies(player,sample)==0 && drops().size==1 && ItemStack.matches(drops().single().item,sample),"Closing an invalidated printer with a full inventory must drop exactly the complete cursor cartridge once")
        menu.removed(player)
        h.assertTrue(drops().size==1 && ItemStack.matches(drops().single().item,sample) && (0 until 36).all { player.inventory.getItem(it).`is`(Items.STONE) && player.inventory.getItem(it).count==64 },"Repeated cleanup must neither duplicate the dropped cursor stack nor replace any item in the full inventory")
        h.succeed()
    }

    @JvmStatic @GameTest(template="empty") fun heldPrinterStorageWritesBackAndLocksDeviceAgainstPickupShiftSwapAndThrow(h:GameTestHelper) {
        val player=actor(h,"HeldPrinterMouse");val device=ItemStack(Architect.GUN.get());player.setItemInHand(InteractionHand.MAIN_HAND,device)
        val sample=cartridge(187,"Persistent held cartridge");player.inventory.setItem(5,sample.copy());val menu=opened(h,player)
        menu.clicked(playerSlot(5),0,ClickType.PICKUP,player);menu.clicked(6,0,ClickType.PICKUP,player)
        h.assertTrue(ItemStack.matches(PrinterStack.contents(device,h.level.registryAccess())[6],sample),"A mouse deposit into held storage must write the complete cartridge back to the actual printer item")
        val expected=device.copy()
        menu.clicked(playerSlot(0),0,ClickType.PICKUP,player)
        menu.clicked(playerSlot(0),0,ClickType.QUICK_MOVE,player)
        menu.clicked(playerSlot(0),5,ClickType.SWAP,player)
        menu.clicked(playerSlot(5),0,ClickType.SWAP,player)
        menu.clicked(6,0,ClickType.SWAP,player)
        menu.clicked(playerSlot(0),1,ClickType.THROW,player)
        h.assertTrue(ItemStack.matches(player.mainHandItem,expected) && menu.carried.isEmpty && player.inventory.getItem(5).isEmpty && ItemStack.matches(menu.getSlot(6).item,sample),"The printer backing its open inventory must remain locked as both clicked slot and hotbar swap source, without moving its stored cartridge")
        player.closeContainer();val reopened=opened(h,player)
        h.assertTrue(ItemStack.matches(reopened.getSlot(6).item,sample),"Closing and reopening held storage must preserve the deposited cartridge")
        reopened.clicked(6,0,ClickType.PICKUP,player);player.closeContainer()
        h.assertTrue(PrinterStack.contents(device,h.level.registryAccess()).all { it.isEmpty } && inventoryCopies(player,sample)==1,"Closing while carrying a held printer's cartridge must persist its removal and return the cursor item once")
        val offhand=actor(h,"OffhandPrinterMouse");val offhandDevice=ItemStack(Architect.GUN.get());offhand.setItemInHand(InteractionHand.OFF_HAND,offhandDevice)
        offhand.inventory.setItem(5,sample.copy());val offhandMenu=opened(h,offhand);val offhandExpected=offhandDevice.copy()
        offhandMenu.clicked(playerSlot(5),40,ClickType.SWAP,offhand)
        h.assertTrue(ItemStack.matches(offhand.offhandItem,offhandExpected) && ItemStack.matches(offhand.inventory.getItem(5),sample),"An offhand printer must also be protected when the offhand swap key targets a player slot")
        offhand.closeContainer();h.succeed()
    }

    @JvmStatic @GameTest(template="empty") fun placedPrinterMouseInventoryPersistsInBlockEntityAndRecoveredItem(h:GameTestHelper) {
        val player=actor(h,"PlacedPrinterMouse");val pos=h.absolutePos(BlockPos(1,1,1));val device=ItemStack(Architect.GUN.get())
        val initial=cartridge(61,"Existing placed cartridge");val replacements=SimpleContainer(7);replacements.setItem(1,initial.copy())
        PrinterStack.contents(device,replacements,h.level.registryAccess());player.setItemInHand(InteractionHand.MAIN_HAND,device)
        h.level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());h.level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
        val hit=BlockHitResult(Vec3(pos.x+0.5,pos.y.toDouble(),pos.z+0.5),Direction.UP,pos.below(),false)
        player.gameMode.useItemOn(player,h.level,device,InteractionHand.MAIN_HAND,hit)
        val placed=h.level.getBlockEntity(pos) as? PrinterEntity
        h.assertTrue(placed!=null && device.isEmpty,"The test must place and activate the real printer item")
        val menu=opened(h,player);val sample=cartridge(209,"Persistent placed cartridge");player.inventory.setItem(10,sample.copy())
        menu.clicked(playerSlot(10),0,ClickType.PICKUP,player);menu.clicked(1,0,ClickType.PICKUP,player)
        menu.clicked(playerSlot(11),0,ClickType.PICKUP,player);player.closeContainer()
        h.assertTrue(ItemStack.matches(placed!!.cartridges.getItem(1),sample) && ItemStack.matches(player.inventory.getItem(11),initial),"Mouse swapping must mutate the placed block entity and deliver the previous cartridge to the exact chosen player slot")
        val loaded=PrinterEntity(pos,placed.blockState);loaded.loadWithComponents(placed.saveWithoutMetadata(h.level.registryAccess()),h.level.registryAccess())
        h.assertTrue(ItemStack.matches(loaded.cartridges.getItem(1),sample),"A placed printer's mouse inventory changes must survive block entity serialization")
        val recovered=PrinterService.cancelDirect(h.level,placed,player)
        h.assertTrue(ItemStack.matches(PrinterStack.contents(recovered,h.level.registryAccess())[1],sample) && inventoryCopies(player,initial)==1 && inventoryCopies(player,sample)==0,"Picking up the placed printer must retain the replacement exactly once inside the recovered device")
        h.succeed()
    }
}
