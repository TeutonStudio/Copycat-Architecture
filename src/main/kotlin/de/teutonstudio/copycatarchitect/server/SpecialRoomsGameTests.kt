package de.teutonstudio.copycatarchitect.server

import com.mojang.authlib.GameProfile
import de.teutonstudio.copycatarchitect.Architect
import de.teutonstudio.copycatarchitect.domain.*
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import java.util.UUID

@GameTestHolder(Architect.ID)
@PrefixGameTestTemplate(false)
object SpecialRoomsGameTests {
    @JvmStatic @GameTest(template="empty",timeoutTicks=400) fun bayAndDormerPlaceWithExactGeometryAndFreeOpenings(h:GameTestHelper) {
        val library=Library().apply { seed() };val building=SpecialRoomsExample.building(true)
        val graph=checkNotNull(building.graph)
        val evaluation=BuildingGraphCompiler.Evaluation(graph,building.palette,library.forms,library.modules)
        val compiled=evaluation.building();val plan=compiled.plan
        val closed=Generator.generate(SpecialRoomsExample.building(false),library.forms,library.modules)
        h.assertTrue(plan.placements.count { it.component=="ceiling" }<closed.placements.count { it.component=="ceiling" },"The ceiling option must cut the actual storey ceiling")
        h.assertTrue(plan.raw==plan.placements.filter { it.block.isEmpty() }.sumOf { Extrusion.cost(it.parts,it.groups) },"Costs must use the carved exact geometry")
        val level=h.level;val origin=h.absolutePos(BlockPos(0,70,0))
        fun world(p:P)=origin.offset(p.x,p.y,p.z)
        val actor=FakePlayerFactory.get(level,GameProfile(UUID.randomUUID(),"SpecialRoomBuilder"))
        actor.gameMode.changeGameModeForPlayer(GameType.CREATIVE);actor.setPos(origin.x-5.0,origin.y.toDouble(),origin.z-5.0)
        (plan.placements.map { it.pos }+plan.clear).forEach { level.setBlock(world(it),Blocks.AIR.defaultBlockState(),2) }
        for(p in plan.placements) h.assertTrue(PlacementService.place(level,world(p.pos),p,actor.inventory,actor,"special-rooms",creative=true),"Erker und Gaube müssen platzierbar sein: ${p.pos}")
        for(p in plan.placements) {
            h.assertTrue(PlacementService.matches(level,world(p.pos),p),"Placed geometry and materials must match the exact plan")
            if(p.parts.any(ExactGeometry::usesExact)) {
                val entity=level.getBlockEntity(world(p.pos)) as? CompositeEntity
                h.assertTrue(entity!=null && Geometry.exact(entity.parts).equivalent(Geometry.exact(p.parts)),"The world must retain the exact sloped seam")
            }
        }
        h.assertTrue(plan.clear.all { level.getBlockState(world(it)).isAir },"Room, passage and dormer clearances must remain free")
        val opening=evaluation.storey("storey").bayOpenings.map { it+P(0,compiled.storeys.single().base,0) }
        h.assertTrue(opening.isNotEmpty() && opening.all { level.getBlockState(world(it)).isAir },"The actual bay passage must be free")
        h.succeed()
    }
}
