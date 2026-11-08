package de.teutonstudio.copycatarchitect.server

import com.mojang.authlib.GameProfile
import de.teutonstudio.copycatarchitect.Architect
import de.teutonstudio.copycatarchitect.domain.*
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.FenceBlock
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import java.util.UUID

@GameTestHolder(Architect.ID)
@PrefixGameTestTemplate(false)
object LandscapeGameTests {
    @JvmStatic @GameTest(template="empty",timeoutTicks=100) fun landscapeUsesExactSurvivalItemsAndConnectedFences(h:GameTestHelper) {
        val garden=BuildingAssembly.newNode(BuildingKind.GARDEN,"garden").copy(
            coordinates=(0..2).flatMap { x -> (0..2).map { z -> P(x,0,z) } },fenced=true,groundDepth=2,groundBlock="minecraft:stone")
        val path=BuildingAssembly.newNode(BuildingKind.PATH,"path").copy(
            coordinates=listOf(P(5,0,0),P(5,2,2)),groundDepth=2,groundBlock="minecraft:stone",clearance=2)
        val graph=BuildingGraph(nodes=listOf(BuildingNode("empty_wall",BuildingKind.SEGMENT,block="minecraft:air"),
            BuildingNode("building",BuildingKind.BUILDING),garden,path))
        val evaluation=BuildingGraphCompiler.Evaluation(graph,emptyMap(),emptyMap(),emptyMap())
        val resolver=Resolver()
        for(node in listOf(garden,path)) {
            val plan=evaluation.preview(node.id)
            plan.placements.forEach { resolver.add(it) };plan.clear.forEach { resolver.clear(it) }
        }
        val plan=resolver.plan(emptyList());val origin=h.absolutePos(BlockPos(2,3,2));val level=h.level
        fun world(p:P)=origin.offset(p.x,p.y,p.z)
        for(x in 0..5) for(y in -1..5) for(z in 0..4) level.setBlockAndUpdate(world(P(x,y,z)),Blocks.AIR.defaultBlockState())
        val actor=FakePlayerFactory.get(level,GameProfile(UUID.randomUUID(),"GardenBuilder"))
        actor.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);actor.setPos(origin.x-8.0,origin.y.toDouble(),origin.z-8.0)
        val inventory=SimpleContainer(9)
        plan.materials.entries.forEachIndexed { index,(id,count) -> inventory.setItem(index,ItemStack(Economy.item(id),count)) }
        h.assertTrue(plan.raw==0,"Native landscape blocks must not consume zinc")
        for(p in plan.placements) h.assertTrue(PlacementService.place(level,world(p.pos),p,inventory,actor,"garden-test"),"Landscape placement must succeed: ${p.pos}")
        h.assertTrue((0 until inventory.containerSize).all { inventory.getItem(it).isEmpty },"The exact material bill must be consumed once")
        val fence=level.getBlockState(world(P(1,1,0)))
        h.assertTrue(fence.getValue(FenceBlock.EAST) && fence.getValue(FenceBlock.WEST),"A garden fence must connect to its two neighbors")
        for(p in plan.placements) {
            h.assertTrue(PlacementService.matches(level,world(p.pos),p),"Connected landscape blocks must match the plan")
            h.assertTrue(!PlacementService.place(level,world(p.pos),p,inventory,actor,"garden-repeat"),"Repeating a completed landscape must not charge again")
        }
        h.assertTrue(plan.clear.all { level.getBlockState(world(it)).isAir },"Path clearance must remain free")
        LandscapeGeometry.nativeBlocks.forEach { Economy.nativeState(it) }
        h.succeed()
    }
}
