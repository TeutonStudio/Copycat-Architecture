package de.teutonstudio.copycatarchitect.server

import de.teutonstudio.copycatarchitect.Architect
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.Fluid
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

/** Cartridge item IDs remain stable; the required Molten Metals dependency owns all zinc fluids. */
object CartridgeRegistry {
    private val blocks=DeferredRegister.createBlocks(Architect.ID)
    private val items=DeferredRegister.createItems(Architect.ID)
    private val entities=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,Architect.ID)
    val BLOCK=blocks.register("cartridge_stack") { -> CartridgeStackBlock(BlockBehaviour.Properties.of().strength(0.5f).sound(SoundType.METAL).noOcclusion()) }
    val ENTITY=entities.register("cartridge_stack") { -> BlockEntityType.Builder.of(::CartridgeStackEntity,BLOCK.get()).build(null) }
    val EMPTY=items.register("empty_cartridge") { -> CartridgeItem(Item.Properties().stacksTo(1),CartridgeKind.EMPTY) }
    val CREATIVE=items.register("creative_cartridge") { -> CartridgeItem(Item.Properties().stacksTo(1),CartridgeKind.CREATIVE) }
    val ZINC_ID=ResourceLocation.fromNamespaceAndPath("molten_metals","molten_zinc")
    /** Resolve after registration; Molten Metals is a required dependency and owns the fluid. */
    val ZINC:Supplier<Fluid> = Supplier {
        check(BuiltInRegistries.FLUID.containsKey(ZINC_ID)) { "Required Molten Metals zinc fluid is missing" }
        BuiltInRegistries.FLUID.get(ZINC_ID)
    }

    fun register(bus:IEventBus) {
        blocks.register(bus);items.register(bus);entities.register(bus)
        bus.addListener(::capabilities)
    }
    private fun capabilities(event:RegisterCapabilitiesEvent) {
        event.registerItem(Capabilities.FluidHandler.ITEM,{ stack,_ -> CartridgeFluidHandler(stack) },Architect.CARTRIDGE.get(),EMPTY.get())
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,ENTITY.get()) { entity,_ -> entity.fluidHandler }
    }
}
