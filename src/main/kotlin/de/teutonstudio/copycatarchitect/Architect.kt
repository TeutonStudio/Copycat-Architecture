package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.server.*
import de.teutonstudio.copycatarchitect.integration.ArchitectUpgrade
import dan200.computercraft.api.turtle.ITurtleUpgrade
import dan200.computercraft.api.upgrades.UpgradeType
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.*
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension

@Mod(Architect.ID)
class Architect(bus:IEventBus) {
    init { BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); MENUS.register(bus); UPGRADES.register(bus);CartridgeRegistry.register(bus);de.teutonstudio.copycatarchitect.integration.CreativeCatalog.register(bus);bus.addListener(Network::register);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(Jobs::tick);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ExtrusionReload::register) }
    companion object {
        const val ID="copycat_architect"
        val BLOCKS=DeferredRegister.createBlocks(ID)
        val ITEMS=DeferredRegister.createItems(ID)
        val ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,ID)
        val MENUS=DeferredRegister.create(Registries.MENU,ID)
        val UPGRADES=DeferredRegister.create(ITurtleUpgrade.typeRegistry(),ID)
        val COMPOSITE=BLOCKS.register("composite") { -> CompositeBlock(BlockBehaviour.Properties.of().strength(1.5f).noOcclusion().isViewBlocking { _,_,_->false }.isSuffocating { _,_,_->false }) }
        val TABLE=BLOCKS.register("blueprint_table") { -> TableBlock(BlockBehaviour.Properties.of().strength(2f)) }
        val PRINTER=BLOCKS.register("block_printer") { -> PrinterBlock(BlockBehaviour.Properties.of().strength(2f).noOcclusion().isViewBlocking { _,_,_->false }.isSuffocating { _,_,_->false }) }
        val GUN=ITEMS.register("extrusion_gun") { -> PrinterItem(Item.Properties().stacksTo(1)) }
        val COMPOSITE_ITEM=ITEMS.register("composite") { -> CompositeFormItem(Item.Properties().stacksTo(64)) }
        // A procedural source has an icon item; its printed geometry is stored in a composite.
        val ROOF_TEMPLATE=ITEMS.register("copycat_roof") { -> Item(Item.Properties()) }
        val CARTRIDGE=ITEMS.register("cartridge") { -> CartridgeItem(Item.Properties().stacksTo(1)) }
        val TABLE_ITEM=ITEMS.register("blueprint_table") { -> BlockItem(TABLE.get(),Item.Properties()) }
        val TABLE_BE=ENTITIES.register("blueprint_table") { -> BlockEntityType.Builder.of(::TableEntity,TABLE.get()).build(null) }
        val PRINTER_BE=ENTITIES.register("block_printer") { -> BlockEntityType.Builder.of(::PrinterEntity,PRINTER.get()).build(null) }
        val BE=ENTITIES.register("composite") { -> BlockEntityType.Builder.of(::CompositeEntity,COMPOSITE.get()).build(null) }
        val UPGRADE=UPGRADES.register("extrusion_gun") { -> UpgradeType.simple(ArchitectUpgrade()) }
        val PRINTER_MENU=MENUS.register("printer_storage") { -> IMenuTypeExtension.create { id,inventory,buffer -> PrinterStorageMenu(id,inventory,lockedInventorySlot=buffer.readVarInt()) } }
    }
}
