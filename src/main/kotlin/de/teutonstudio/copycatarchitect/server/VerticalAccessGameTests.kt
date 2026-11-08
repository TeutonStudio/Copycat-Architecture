package de.teutonstudio.copycatarchitect.server

import com.mojang.authlib.GameProfile
import de.teutonstudio.copycatarchitect.Architect
import de.teutonstudio.copycatarchitect.domain.*
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.MoverType
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import java.util.UUID
import kotlin.math.sqrt

@GameTestHolder(Architect.ID)
@PrefixGameTestTemplate(false)
object VerticalAccessGameTests {
    @JvmStatic @GameTest(template="empty",timeoutTicks=400)
    fun generatedStairsAreWalkableWithoutJumpingForEveryLayoutAndOrientation(h:GameTestHelper) {
        val level=h.level
        // The shared empty structure is only 4 x 4 x 4. Keep the larger walking arena
        // well above concurrent tests, including their structures and status markers.
        val origin=h.absolutePos(BlockPos(12,80,12))
        for(x in (origin.x-12 shr 4)..(origin.x+12 shr 4))
            for(z in (origin.z-12 shr 4)..(origin.z+12 shr 4)) level.getChunk(x,z)
        for(x in -12..12) for(y in -2..12) for(z in -12..12)
            level.setBlockAndUpdate(origin.offset(x,y,z),Blocks.AIR.defaultBlockState())
        val actor=FakePlayerFactory.get(level,GameProfile(UUID.randomUUID(),"StairWalker"))
        actor.gameMode.changeGameModeForPlayer(GameType.SURVIVAL)
        val footprint=buildSet { for(x in -20..20) for(z in -20..20) add(P(x,0,z)) }
        val stories=listOf(1,6).mapIndexed { i,base ->
            val r=Resolver()
            r.add(Placement(P(0,base-1,0),listOf(Part("copycats:copycat_block")),component="floor"))
            r.add(Placement(P(0,base+4,0),listOf(Part("copycats:copycat_block")),component="ceiling"))
            AccessStorey("level_$i",i,base,r.plan(emptyList()),footprint)
        }
        var previous=emptySet<P>()
        for(layout in StairLayout.entries) for(clockwise in listOf(true,false)) for(turn in 0..3) {
            actor.setPos(origin.x-30.0,origin.y.toDouble(),origin.z-30.0)
            previous.forEach { p->level.setBlockAndUpdate(origin.offset(p.x,p.y,p.z),Blocks.AIR.defaultBlockState()) }
            val definition=AccessDefinition("stairs",VerticalAccessKind.STAIRWELL,stairwell=StairwellConfig(layout=layout,clockwise=clockwise))
            val core=VerticalAccess.compile(listOf(VerticalAccessInstance("core","stairs",quarterTurns=turn)),stories,{ definition },{ _,_->error("No custom templates") },Building().palette)
            val positions=(core.plan.placements.map { it.pos }+core.plan.clear).toSet()
            positions.forEach { p->level.setBlockAndUpdate(origin.offset(p.x,p.y,p.z),Blocks.AIR.defaultBlockState()) }
            for(p in core.plan.placements) PlacementService.place(level,origin.offset(p.pos.x,p.pos.y,p.pos.z),p,SimpleContainer(1),actor,"stair-walk",creative=true)
            h.assertTrue(core.plan.placements.all { PlacementService.matches(level,origin.offset(it.pos.x,it.pos.y,it.pos.z),it) },
                "The isolated walking arena must contain every generated stair, landing and enclosure")
            previous=positions
            fun target(x:Double,z:Double):Vec3 {
                var dx=if(clockwise) x else 2.0-x;var dz=z
                repeat(turn) { val old=dx;dx=1.0-dz;dz=old }
                return Vec3(origin.x+dx,origin.y+1.0,origin.z+dz)
            }
            val start=target(0.5,-0.5)
            actor.setPos(start.x,start.y,start.z);actor.setOnGround(true)
            fun walk(point:Vec3) {
                repeat(250) {
                    val dx=point.x-actor.x;val dz=point.z-actor.z;val distance=sqrt(dx*dx+dz*dz)
                    if(distance<0.025) return
                    val step=minOf(0.1,distance)
                    actor.move(MoverType.SELF,Vec3(dx/distance*step,-0.08,dz/distance*step))
                }
                val feet=BlockPos.containing(actor.position())
                val expected=core.plan.placements.associateBy { origin.offset(it.pos.x,it.pos.y,it.pos.z) }
                val neighbors=buildList {
                    for(x in -1..1) for(y in -1..2) for(z in -1..1) {
                        val at=feet.offset(x,y,z);val state=level.getBlockState(at)
                        if(!state.isAir) add("$at: $state, expected=${expected[at]?.component ?: "foreign"}, collision=${state.getCollisionShape(level,at,CollisionContext.of(actor)).toAabbs()}")
                    }
                }.joinToString(" | ")
                val diagnostic="actor=${actor.position()}, target=$point, grounded=${actor.onGround()}, step=${actor.maxUpStep()}, horizontalCollision=${actor.horizontalCollision}, verticalCollision=${actor.verticalCollision}, bounds=${actor.boundingBox}; $neighbors"
                System.err.println("ARCHITECT STAIR WALK DIAGNOSTIC: $diagnostic")
                h.fail("Player could not walk $layout / clockwise=$clockwise / turn=$turn to the next landing: $diagnostic")
            }
            when(layout) {
                StairLayout.STRAIGHT -> walk(target(0.5,5.5))
                StairLayout.L -> { walk(target(0.5,3.5));walk(target(4.5,3.5)) }
                StairLayout.U -> { walk(target(0.5,3.5));walk(target(2.5,3.5));walk(target(2.5,-0.5)) }
            }
            h.assertTrue(kotlin.math.abs(actor.y-(origin.y+6.0))<0.01,"Walking without jumps must reach the upper floor for $layout / clockwise=$clockwise / turn=$turn: actualY=${actor.y}, expectedY=${origin.y+6.0}")
        }
        actor.setPos(origin.x-30.0,origin.y.toDouble(),origin.z-30.0)
        h.succeed()
    }
}
