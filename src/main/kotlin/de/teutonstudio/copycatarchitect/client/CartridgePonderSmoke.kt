package de.teutonstudio.copycatarchitect.client

import com.mojang.math.Axis
import com.simibubi.create.content.fluids.spout.SpoutBlockEntity
import com.simibubi.create.content.kinetics.belt.BeltBlock
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity
import com.simibubi.create.content.kinetics.belt.BeltHelper
import com.simibubi.create.content.kinetics.belt.BeltPart
import com.simibubi.create.content.kinetics.belt.BeltSlope
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack
import com.simibubi.create.content.logistics.depot.DepotBlockEntity
import com.simibubi.create.content.processing.basin.BasinBlockEntity
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour
import de.teutonstudio.copycatarchitect.Architect
import de.teutonstudio.copycatarchitect.server.CartridgeRegistry
import de.teutonstudio.copycatarchitect.server.CartridgeStackBlock
import de.teutonstudio.copycatarchitect.server.CartridgeStackEntity
import de.teutonstudio.copycatarchitect.server.Economy
import net.createmod.ponder.api.level.PonderLevel
import net.createmod.ponder.foundation.ui.PonderUI
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import net.minecraft.world.level.levelgen.structure.BoundingBox

/** Opt-in acceptance in a disposable real client world, exercising Ponder and Create's renderers. */
object CartridgePonderSmoke {
    private var ticks=0
    private var waiting=0
    private var failed=false
    private lateinit var ponder:PonderUI
    private lateinit var models:CartridgeModelsScreen
    private lateinit var placed:PlacedCartridgeModelsScreen
    private val depot=BlockPos(3,1,2)
    private val spout=BlockPos(3,3,2)
    private fun screenshot(mc:Minecraft,name:String) {
        Screenshot.grab(mc.gameDirectory,"architect-cartridges-$name.png",mc.mainRenderTarget) { println(it.string) }
    }
    private fun verifyModels(mc:Minecraft) {
        val variants=listOf(ItemStack(CartridgeRegistry.EMPTY.get()),Economy.cartridge(256),ItemStack(CartridgeRegistry.CREATIVE.get()))
        check(variants.map { it.hoverName.string }.toSet().size==3) { "Empty, filled and creative cartridges require distinct localized names" }
        for(stack in variants) {
            val model=mc.itemRenderer.getModel(stack,mc.level,mc.player,0)
            check(model.isGui3d && !model.isCustomRenderer) { "Cartridges require a volumetric baked model: ${stack.item}" }
            val quads=model.getQuads(null,null,RandomSource.create(42))
            check(quads.size>=24 && quads.none { it.sprite.contents().name()==ResourceLocation.withDefaultNamespace("missingno") }) {
                "All cartridge variants require complete textured geometry: ${stack.item}, quads=${quads.size}"
            }
            check(quads.map { it.direction }.toSet().size==6) { "Cartridges must have faces in all six directions" }
            val fixed=model.transforms.getTransform(ItemDisplayContext.FIXED)
            check(fixed.scale.x==1f && fixed.scale.y==1f && fixed.scale.z==1f && fixed.translation.x==0f && fixed.translation.z==0f)
            check(!BeltHelper.isItemUpright(stack)) { "Fluid-capable cartridge must retain its stable 3D orientation on Create belts and depots" }
        }
    }
    private fun open(mc:Minecraft,item:ItemStack,time:Int=0) {
        ponder=PonderUI.of(item)
        mc.setScreen(ponder)
        if(time>0) ponder.seekToTime(time)
        for(index in 1..5) check(ponder.activeScene.getString("text_$index").let { !it.contains(".ponder.") && it.isNotBlank() }) {
            "Ponder's generated text_$index translation must resolve for ${ponder.activeScene.id}"
        }
    }
    private fun block(pos:BlockPos,id:String) {
        val actual=BuiltInRegistries.BLOCK.getKey(ponder.activeScene.world.getBlockState(pos).block).toString()
        check(actual==id) { "Missing Ponder block at $pos: expected=$id, actual=$actual" }
    }
    fun tick():Boolean {
        if(failed) return false
        val mc=Minecraft.getInstance()
        if(mc.level==null || mc.player==null || mc.overlay!=null) {
            if(++waiting>2400) { failed=true;error("Cartridge acceptance requires a loaded disposable client world; use --quickPlaySingleplayer") }
            return false
        }
        ticks++
        try {
            when(ticks) {
                10 -> {
                    mc.options.guiScale().set(2);mc.resizeDisplay();verifyModels(mc)
                    open(mc,Economy.cartridge(256),45)
                }
                15 -> {
                    block(BlockPos(0,1,2),"create:basin");block(BlockPos(0,3,2),"create:mechanical_mixer");block(BlockPos(0,0,2),"create:blaze_burner")
                    check(ponder.activeScene.bounds.maxY()>=6)
                    screenshot(mc,"01-melting")
                }
                20 -> ponder.seekToTime(170)
                25 -> {
                    block(spout,"create:spout");block(depot,"create:depot");block(BlockPos(1,1,2),"create:mechanical_pump")
                    for(y in 1..3) block(BlockPos(2,y,2),"create:fluid_pipe")
                    val world=ponder.activeScene.world
                    check((world.getBlockEntity(depot) as DepotBlockEntity).heldItem.`is`(CartridgeRegistry.EMPTY.get()))
                    val tank=(world.getBlockEntity(spout) as SpoutBlockEntity).getBehaviour(SmartFluidTankBehaviour.TYPE)
                    check(tank.primaryHandler.fluid.amount==256 && BuiltInRegistries.FLUID.getKey(tank.primaryHandler.fluid.fluid).toString()=="molten_metals:molten_zinc")
                    screenshot(mc,"02-spout-and-empty")
                }
                30 -> ponder.seekToTime(240)
                33 -> {
                    val entity=ponder.activeScene.world.getBlockEntity(spout) as SpoutBlockEntity
                    check(entity.processingTicks in 6..14 && !entity.getBehaviour(SmartFluidTankBehaviour.TYPE).primaryTank.renderedFluid.isEmpty) {
                        "The actual spout renderer must show its fluid stream, processingTicks=${entity.processingTicks}"
                    }
                    screenshot(mc,"03-pouring")
                }
                40 -> ponder.seekToTime(280)
                45 -> {
                    val world=ponder.activeScene.world
                    check(Economy.units((world.getBlockEntity(depot) as DepotBlockEntity).heldItem)==256)
                    check((world.getBlockEntity(BlockPos(0,1,2)) as BasinBlockEntity).inputTank.primaryHandler.fluid.amount==32)
                    screenshot(mc,"04-filled")
                }
                50 -> ponder.seekToTime(370)
                55 -> {
                    check((ponder.activeScene.world.getBlockEntity(BlockPos(4,1,2)) as DepotBlockEntity).heldItem.`is`(CartridgeRegistry.CREATIVE.get()))
                    screenshot(mc,"05-creative")
                }
                60 -> ponder.seekToTime(470)
                65 -> {
                    block(BlockPos(2,1,0),"copycat_architect:cartridge_stack");block(BlockPos(0,1,0),"copycat_architect:block_printer")
                    screenshot(mc,"06-storage")
                }
                70 -> { models=CartridgeModelsScreen(mc);mc.setScreen(models) }
                80 -> { models.verify();screenshot(mc,"07-belt-depot-models") }
                85 -> models.turn=105f
                95 -> screenshot(mc,"08-models-other-side")
                96 -> { placed=PlacedCartridgeModelsScreen(mc);placed.verify();mc.setScreen(placed) }
                99 -> screenshot(mc,"11-placed-variants")
                100 -> open(mc,ItemStack(Architect.GUN.get()),45)
                105 -> { block(BlockPos(2,1,2),"copycat_architect:block_printer");screenshot(mc,"09-printer-scene") }
                110 -> open(mc,ItemStack(Architect.TABLE_ITEM.get()),385)
                115 -> { check(!ponder.activeScene.world.getBlockState(BlockPos(2,4,2)).isAir);screenshot(mc,"10-table-scene") }
                125 -> {
                    println("ARCHITECT CARTRIDGE CLIENT SMOKE PASSED: real Ponder world, heated mixer, pump and connected pipes, Molten Metals zinc, pouring spout, real empty/full/creative depot items, actual 3D belt and depot renderers, both viewing angles, all 120 placed variant combinations, refill/drain and client update tags, printer and table scenes")
                    mc.stop()
                }
                else -> return false
            }
        } catch(error:Throwable) {
            failed=true
            System.err.println("ARCHITECT CARTRIDGE CLIENT SMOKE FAILED at tick $ticks: ${error.message}")
            error.printStackTrace();screenshot(mc,"failure-tick$ticks")
            mc.stop()
            throw IllegalStateException("Cartridge client smoke failed at tick $ticks",error)
        }
        return true
    }

    private class PlacedCartridgeModelsScreen(mc:Minecraft):Screen(Component.literal("Aufgestellte Kartuschen · leer / normal / kreativ")) {
        private val world=PonderLevel(BlockPos.ZERO,checkNotNull(mc.level))
        private val stacks=listOf(Economy.cartridge(0),Economy.cartridge(256),ItemStack(CartridgeRegistry.CREATIVE.get()))
        init {
            world.setBounds(BoundingBox(0,0,0,3,1,0))
            repeat(4) { index ->
                world.setBlock(BlockPos(index,0,0),CartridgeRegistry.BLOCK.get().defaultBlockState().setValue(CartridgeStackBlock.COUNT,index+1),3)
                val entity=world.getBlockEntity(BlockPos(index,0,0)) as CartridgeStackEntity
                repeat(index+1) { slot -> entity.cartridges[slot]=stacks[when(index) { 0->2;1->slot+1;else->slot.coerceAtMost(2) }].copy() }
            }
        }
        private fun verifyCaps(entity:CartridgeStackEntity,expected:List<Int>) {
            val model=Minecraft.getInstance().blockRenderer.getBlockModel(entity.blockState)
            check(model is CartridgeStackModel) { "Placed cartridges must use their variant-aware block model" }
            val quads=model.getQuads(entity.blockState,null,RandomSource.create(42),entity.modelData,null)
            val tops=quads.filter { it.direction==Direction.UP && Float.fromBits(it.vertices[1])>0.55f }
            check(tops.size==expected.size)
            for(quad in tops) {
                val stride=quad.vertices.size/4
                val x=(0..3).sumOf { Float.fromBits(quad.vertices[it*stride]).toDouble() }/4
                val z=(0..3).sumOf { Float.fromBits(quad.vertices[it*stride+2]).toDouble() }/4
                val slot=(if(x>0.5) 1 else 0)+(if(z>0.5) 2 else 0)
                val texture=listOf("iron_block","gold_block","amethyst_block")[expected[slot]]
                check(quad.sprite.contents().name()==ResourceLocation.withDefaultNamespace("block/$texture")) { "Wrong placed cap texture in slot $slot: ${quad.sprite.contents().name()}" }
            }
        }
        fun verify() {
            val pos=BlockPos.ZERO
            for(count in 1..4) {
                val state=CartridgeRegistry.BLOCK.get().defaultBlockState().setValue(CartridgeStackBlock.COUNT,count)
                repeat(Math.pow(3.0,count.toDouble()).toInt()) { combination ->
                    val entity=CartridgeStackEntity(pos,state)
                    val kinds=List(count) { slot -> combination/Math.pow(3.0,slot.toDouble()).toInt()%3 }
                    kinds.forEachIndexed { slot,kind -> entity.cartridges[slot]=stacks[kind].copy() }
                    verifyCaps(entity,kinds)
                    val client=CartridgeStackEntity(pos,state);client.level=world
                    client.handleUpdateTag(entity.getUpdateTag(world.registryAccess()),world.registryAccess())
                    verifyCaps(client,kinds)
                }
            }
            val state=CartridgeRegistry.BLOCK.get().defaultBlockState().setValue(CartridgeStackBlock.COUNT,4)
            val entity=CartridgeStackEntity(pos,state)
            listOf(0,1,1,2).forEachIndexed { slot,kind -> entity.cartridges[slot]=stacks[kind].copy() }
            check(entity.fluidHandler.fill(FluidStack(CartridgeRegistry.ZINC.get(),100),FluidAction.EXECUTE)==100)
            verifyCaps(entity,listOf(1,1,1,2))
            check(entity.fluidHandler.drain(100,FluidAction.EXECUTE).amount==100)
            verifyCaps(entity,listOf(0,1,1,2))
        }
        override fun render(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
            val mc=Minecraft.getInstance()
            g.fill(0,0,width,height,0xff172322.toInt())
            g.drawCenteredString(font,title,width/2,24,0xfff0d49a.toInt())
            val panelWidth=(width-40)/4
            repeat(4) { index ->
                val left=8+index*(panelWidth+8)
                g.fill(left,60,left+panelWidth,height-50,0xff203031.toInt())
                g.drawCenteredString(font,"${index+1} Kartusche${if(index==0) "" else "n"}",left+panelWidth/2,76,0xffe4e8d5.toInt())
                val pose=g.pose();pose.pushPose();pose.translate(left+panelWidth/2.0,height/2.0+35,200.0)
                val scale=minOf(panelWidth/1.5f,(height-180)/1.6f)
                pose.scale(scale,-scale,scale);pose.mulPose(Axis.XP.rotationDegrees(25f));pose.mulPose(Axis.YP.rotationDegrees(35f));pose.translate(-0.5,-0.25,-0.5)
                val entity=world.getBlockEntity(BlockPos(index,0,0)) as CartridgeStackEntity
                mc.blockRenderer.renderSingleBlock(entity.blockState,pose,g.bufferSource(),15728880,OverlayTexture.NO_OVERLAY,entity.modelData,null)
                g.flush();pose.popPose()
            }
            g.drawCenteredString(font,"Silber: leer    Gold: normal    Amethyst: kreativ",width/2,height-30,0xffe4e8d5.toInt())
        }
    }

    private class CartridgeModelsScreen(mc:Minecraft):Screen(Component.literal("Kartuschen auf Förderband und Ablage")) {
        private val world=PonderLevel(BlockPos.ZERO,checkNotNull(mc.level))
        private val stacks=listOf(ItemStack(CartridgeRegistry.EMPTY.get()),Economy.cartridge(256),ItemStack(CartridgeRegistry.CREATIVE.get()))
        var turn=35f
        init {
            world.setBounds(BoundingBox(0,0,0,2,2,2))
            val belt=BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:belt")).defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.EAST).setValue(BeltBlock.SLOPE,BeltSlope.HORIZONTAL)
            val depot=BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:depot")).defaultBlockState()
            repeat(3) { index ->
                val pos=BlockPos(index,0,0)
                world.setBlock(pos,belt.setValue(BeltBlock.PART,when(index) { 0->BeltPart.START;2->BeltPart.END;else->BeltPart.MIDDLE }),3)
                (world.getBlockEntity(pos) as BeltBlockEntity).apply { markVirtual();setController(BlockPos.ZERO);beltLength=3;this.index=index;initialize() }
                val display=BlockPos(index,0,2)
                world.setBlock(display,depot,3)
                (world.getBlockEntity(display) as DepotBlockEntity).apply { markVirtual();heldItem=stacks[index].copy() }
            }
            val controller=world.getBlockEntity(BlockPos.ZERO) as BeltBlockEntity
            stacks.forEachIndexed { index,stack -> controller.inventory.transportedItems.add(TransportedItemStack(stack.copy()).apply {
                beltPosition=index+0.5f;prevBeltPosition=beltPosition;sideOffset=0f;prevSideOffset=0f;angle=0;insertedAt=index;insertedFrom=Direction.UP
            }) }
        }
        fun verify() {
            check((world.getBlockEntity(BlockPos.ZERO) as BeltBlockEntity).inventory.transportedItems.map { it.stack.item }==stacks.map { it.item })
            repeat(3) { check(ItemStack.isSameItemSameComponents((world.getBlockEntity(BlockPos(it,0,2)) as DepotBlockEntity).heldItem,stacks[it])) }
        }
        override fun render(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
            val mc=Minecraft.getInstance()
            g.fill(0,0,width,height,0xff172322.toInt())
            g.drawCenteredString(font,title,width/2,18,0xfff0d49a.toInt())
            val panelWidth=(width-48)/2;val top=52;val panelHeight=height-114
            repeat(2) { panel ->
                val left=16+panel*(panelWidth+16)
                g.fill(left,top,left+panelWidth,top+panelHeight,0xff203031.toInt())
                g.renderOutline(left,top,panelWidth,panelHeight,0xff526e69.toInt())
                g.drawCenteredString(font,if(panel==0) "Create Förderband · FIXED" else "Create Ablagen · FIXED",left+panelWidth/2,top+12,0xffd8d4b9.toInt())
                g.enableScissor(left+1,top+28,left+panelWidth-1,top+panelHeight-1)
                g.flush();val pose=g.pose();pose.pushPose()
                pose.translate(left+panelWidth/2.0,top+panelHeight*0.6,200.0)
                val scale=minOf(panelWidth/3.9f,panelHeight/2.7f)
                pose.scale(scale,-scale,scale);pose.mulPose(Axis.XP.rotationDegrees(25f));pose.mulPose(Axis.YP.rotationDegrees(turn))
                pose.translate(-1.5,-0.35,-0.5)
                repeat(3) { index ->
                    val pos=BlockPos(index,0,panel*2);val state=world.getBlockState(pos)
                    val entity=checkNotNull(world.getBlockEntity(pos)) as SmartBlockEntity
                    val renderer=checkNotNull(mc.blockEntityRenderDispatcher.getRenderer(entity))
                    pose.pushPose();pose.translate(index.toDouble(),0.0,0.0)
                    mc.blockRenderer.renderSingleBlock(state,pose,g.bufferSource(),15728880,OverlayTexture.NO_OVERLAY)
                    renderer.render(entity,0f,pose,g.bufferSource(),15728880,OverlayTexture.NO_OVERLAY)
                    pose.popPose()
                }
                g.flush();pose.popPose();g.disableScissor()
            }
            stacks.forEachIndexed { index,stack ->
                val x=width/2-132+index*132
                g.renderItem(stack,x,height-42);g.drawString(font,stack.hoverName,x+22,height-38,0xffe4e8d5.toInt())
            }
        }
    }
}
