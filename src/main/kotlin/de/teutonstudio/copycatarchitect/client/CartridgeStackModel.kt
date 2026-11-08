package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.Architect
import de.teutonstudio.copycatarchitect.server.CartridgeKind
import de.teutonstudio.copycatarchitect.server.CartridgeRegistry
import de.teutonstudio.copycatarchitect.server.CartridgeStackBlock
import de.teutonstudio.copycatarchitect.server.CartridgeStackEntity
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.block.BlockModelShaper
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.client.resources.model.ModelResourceLocation
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ModelEvent
import net.neoforged.neoforge.client.model.BakedModelWrapper
import net.neoforged.neoforge.client.model.data.ModelData

/** Keep the existing block geometry while choosing each stored cartridge's own cap texture. */
class CartridgeStackModel(full:BakedModel,empty:BakedModel,creative:BakedModel):BakedModelWrapper<BakedModel>(full) {
    private data class Face(val slot:Int,val variants:List<BakedQuad>)
    private val faces=(listOf<Direction?>(null)+Direction.entries).associateWith { side ->
        val variants=listOf(full,empty,creative).map { it.getQuads(null,side,RandomSource.create(42)) }
        variants[0].mapIndexed { index,quad ->
            val vertices=quad.vertices;val stride=vertices.size/4
            val x=(0..3).sumOf { Float.fromBits(vertices[it*stride]).toDouble() }/4
            val z=(0..3).sumOf { Float.fromBits(vertices[it*stride+2]).toDouble() }/4
            Face((if(x>0.5) 1 else 0)+(if(z>0.5) 2 else 0),variants.map { it[index] })
        }
    }
    override fun getQuads(state:BlockState?,side:Direction?,rand:RandomSource,data:ModelData,renderType:RenderType?):List<BakedQuad> {
        val kinds=data.get(CartridgeStackEntity.KINDS)
        return faces.getValue(side).map { face -> face.variants[(kinds?.getOrNull(face.slot) ?: CartridgeKind.FULL).ordinal] }
    }
}

@EventBusSubscriber(modid=Architect.ID,bus=EventBusSubscriber.Bus.MOD,value=[Dist.CLIENT])
object CartridgeStackModels {
    private fun variant(count:Int,kind:String)=ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(Architect.ID,"block/cartridge_stack_${count}_$kind"))
    @SubscribeEvent fun additional(e:ModelEvent.RegisterAdditional) {
        for(count in 1..4) for(kind in listOf("empty","creative")) e.register(variant(count,kind))
    }
    @SubscribeEvent fun bake(e:ModelEvent.ModifyBakingResult) {
        for(state in CartridgeRegistry.BLOCK.get().stateDefinition.possibleStates) {
            val count=state.getValue(CartridgeStackBlock.COUNT)
            val key=BlockModelShaper.stateToModelLocation(state)
            e.models[key]=CartridgeStackModel(checkNotNull(e.models[key]),checkNotNull(e.models[variant(count,"empty")]),checkNotNull(e.models[variant(count,"creative")]))
        }
    }
}
