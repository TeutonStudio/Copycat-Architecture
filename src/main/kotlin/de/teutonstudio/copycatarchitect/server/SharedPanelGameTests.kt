package de.teutonstudio.copycatarchitect.server

import com.mojang.authlib.GameProfile
import de.teutonstudio.copycatarchitect.Architect
import de.teutonstudio.copycatarchitect.domain.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import java.util.UUID

@GameTestHolder(Architect.ID)
@PrefixGameTestTemplate(false)
object SharedPanelGameTests {
    private fun plan():Plan {
        val initial=BuildingGraph.starter()
        val rectangles=listOf(Rect(width=2,length=2))
        val wall=initial.nodes.single { it.id=="wall" }.copy(form="panel@1",module="",length=2,height=2)
        val room=initial.nodes.single { it.id=="room" }.copy(rectangles=rectangles,height=2,
            walls=RoomGeometry.edges(rectangles).associate { it.id to "wall" })
        val graph=initial.copy(nodes=initial.nodes.map { node -> when(node.id) {
            "wall"->wall
            "room"->room
            "storey"->node.copy(inputs=listOf("room","room2"),offsets=listOf(P(),P(3,0,0)))
            else->node
        } }+wall.copy(id="wall2",role="frame")+room.copy(id="room2",walls=room.walls.mapValues { "wall2" }))
        val forms=mapOf(
            "full@1" to Form(id="full",parts=listOf(Part("copycats:copycat_block"))),
            "panel@1" to Form(id="panel",parts=listOf(Part("create:copycat_panel",tilt=1)))
        )
        return Generator.generate(Building(schema=2,graph=graph),forms,emptyMap())
    }

    @JvmStatic @GameTest(template="empty",timeoutTicks=400) fun adjacentRoomPanelsKeepBothPaidFacesInOneWorldBlock(h:GameTestHelper) {
        val level=h.level;val plan=plan();val origin=h.absolutePos(BlockPos(3,1,3))
        fun world(p:P)=origin.offset(p.x,p.y,p.z)
        val actor=FakePlayerFactory.get(level,GameProfile(UUID.randomUUID(),"SharedPanelBuilder"))
        actor.gameMode.changeGameModeForPlayer(GameType.SURVIVAL)
        actor.setPos(origin.x-5.0,origin.y.toDouble(),origin.z-5.0)
        val bounds=plan.placements.map { it.pos }+plan.clear
        for(x in bounds.minOf { it.x }..bounds.maxOf { it.x })
            for(y in bounds.minOf { it.y }..bounds.maxOf { it.y })
                for(z in bounds.minOf { it.z }..bounds.maxOf { it.z }) level.setBlockAndUpdate(world(P(x,y,z)),Blocks.AIR.defaultBlockState())

        val inventory=SimpleContainer(27);inventory.setItem(0,Economy.cartridge(plan.raw));var slot=1
        for((material,count) in plan.materials) {
            var remaining=count
            while(remaining>0) {
                val stack=ItemStack(Economy.item(material));stack.count=minOf(remaining,stack.maxStackSize)
                h.assertTrue(slot<inventory.containerSize,"The two-room fixture must fit its exact supplies")
                inventory.setItem(slot++,stack);remaining-=stack.count
            }
        }
        for(placement in plan.placements) h.assertTrue(PlacementService.place(level,world(placement.pos),placement,inventory,actor,"shared-room-panels"),"Each generated shared-room position must place exactly once")
        h.assertTrue(inventory.getItem(0).`is`(CartridgeRegistry.EMPTY.get()) && (1 until inventory.containerSize).all { inventory.getItem(it).isEmpty },"The exact generated zinc and surface budget must be fully consumed")
        h.assertTrue(plan.clear.all { level.getBlockState(world(it)).isAir },"Both rooms must retain every buildable interior cell")
        h.assertTrue(plan.placements.size==plan.placements.map { it.pos }.distinct().size,"Adjacent room panels must compile to unique world blocks")

        val shared=plan.placements.single { it.pos==P(2,1,0) };val pos=world(shared.pos)
        val entity=level.getBlockEntity(pos) as? CompositeEntity ?: error("Shared wall must use one actual composite block entity")
        val state=level.getBlockState(pos);val geometry=Geometry.resolve(entity.parts,entity.groups)
        val materials=geometry.materialSolids(entity.material,entity.surfaces)
        val stone=materials.getValue("minecraft:stone_bricks");val wood=materials.getValue("minecraft:oak_planks")
        h.assertTrue(geometry.solid.count()==1536 && stone.count()==768 && wood.count()==768,"Both 3/16 panel faces must retain their separate material volumes inside one block")
        val collision=state.getCollisionShape(level,pos).toAabbs()
        val contextual=state.getCollisionShape(level,pos,CollisionContext.empty()).toAabbs()
        for(x in 0..15) for(y in 0..15) for(z in 0..15) {
            val occupied=x in 0..2 || x in 13..15
            h.assertTrue(stone.has(x,y,z)==(x in 13..15) && wood.has(x,y,z)==(x in 0..2),"Shared panel material ownership must remain on its room-facing side at $x/$y/$z")
            h.assertTrue(collision.any { it.contains((x+0.5)/16,(y+0.5)/16,(z+0.5)/16) }==occupied && contextual.any { it.contains((x+0.5)/16,(y+0.5)/16,(z+0.5)/16) }==occupied,"Both actual collision paths must retain the two 3/16 faces and the 10/16 middle gap")
        }
        for((direction,material) in listOf(Direction.WEST to "minecraft:oak_planks",Direction.EAST to "minecraft:stone_bricks")) {
            val hit=BlockHitResult(Vec3(pos.x+(if(direction==Direction.WEST) 0.0 else 1.0),pos.y+0.5,pos.z+0.5),direction,pos,false)
            val group=entity.hitGroup(hit) ?: error("Both exposed shared-wall faces need a selectable material group")
            h.assertTrue(entity.coating(group.id)==material,"The actual block hit must select the material facing its room")
        }
        val loaded=CompositeEntity(pos,state);loaded.loadWithComponents(entity.saveWithoutMetadata(level.registryAccess()),level.registryAccess())
        h.assertTrue(Geometry.resolve(loaded.parts,loaded.groups).visualKey(loaded.material,loaded.surfaces)==shared.visualKey() && loaded.receipt==entity.receipt,"NBT must preserve both namespaced material groups, their geometry and payment receipt")
        h.assertTrue(entity.receipt.units==96 && entity.receipt.surfaces==mapOf("minecraft:stone_bricks" to 1,"minecraft:oak_planks" to 1),"The two 3/16 panel faces occupy 1536 voxels and cost 96 mB plus two surface items")
        val receipts=plan.placements.sumOf { placement ->
            val be=level.getBlockEntity(world(placement.pos)) as CompositeEntity
            h.assertTrue(be.receipt.units==(Geometry.solid(placement.parts).count()+15)/16 && be.receipt.surfaces==placement.surfaceItems(),"Every generated position must record its occupied voxel volume in mB and paid surface items")
            be.receipt.units
        }
        h.assertTrue(receipts==plan.raw,"Generated shared-wall costs must equal the actual total world receipts")
        val before=(0 until inventory.containerSize).map { inventory.getItem(it).copy() }
        for(placement in plan.placements) h.assertTrue(!PlacementService.place(level,world(placement.pos),placement,inventory,actor,"shared-room-repeat"),"Repeating either room must be idempotent at shared and independent cells")
        h.assertTrue(before.indices.all { ItemStack.matches(before[it],inventory.getItem(it)) },"Repeated placement must not charge any extra zinc or surface materials")

        h.assertTrue(level.destroyBlock(pos,true,actor),"The shared wall must break as one actual block")
        val drops=level.getEntitiesOfClass(ItemEntity::class.java,AABB(pos).inflate(0.75))
        h.assertTrue(drops.sumOf { if(Economy.isCartridge(it.item)) Economy.units(it.item) else 0 }==96,"Breaking the shared wall must refund exactly its paid 96 mB")
        for(material in listOf("minecraft:stone_bricks","minecraft:oak_planks")) h.assertTrue(drops.filter { it.item.`is`(Economy.item(material)) }.sumOf { it.item.count }==1,"Breaking a shared block must refund its $material surface exactly once")
        h.assertTrue(drops.sumOf { it.item.count }==3,"The refund must contain only the paid cartridge and two material samples")
        println("ARCHITECT SHARED PANELS: ${plan.placements.size} exact Survival placements, one shared world block with two 3/16 material faces, 10/16 gap, all interior cells free, collision/hits/NBT, exact receipts, idempotence and real break refunds verified")
        h.succeed()
    }
}
