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
object RoomMountingGameTests {
    @JvmStatic @GameTest(template="empty",timeoutTicks=300) fun mirroredWallLayersAndVerticalEdgeSegmentsKeepTheirGraphGeometry(h:GameTestHelper) {
        val initial=BuildingGraph.explicitStarter()
        val rectangles=listOf(Rect(width=5,length=5))
        val forms=mapOf("full@1" to Form(id="full",parts=listOf(Part("copycats:copycat_block"))),
            "panel@1" to Form(id="panel",parts=listOf(Part("create:copycat_panel",tilt=3))),
            "detail@1" to Form(id="detail",parts=listOf(Part("copycats:copycat_byte",offset=P(1,2,3)))))
        val skin=BuildingNode("skin",BuildingKind.SEGMENT,module="",form="panel@1",length=5,height=2)
        val back=BuildingAssembly.newNode(BuildingKind.SEGMENT_MIRROR,"back").copy(inputs=listOf(skin.id),rotationAxis=RotationAxis.Z,materialMode=MaterialMode.GROUPED,role="frame")
        val pair=BuildingAssembly.newNode(BuildingKind.PATTERN,"pair").copy(inputs=listOf(skin.id,back.id),patternAxis="depth")
        val edge=BuildingNode("edge",BuildingKind.SEGMENT,module="",form="detail@1",length=2,height=2,depth=2)
        val graph=initial.copy(nodes=initial.nodes+listOf(skin,back,pair,edge))
            .replace(initial.nodes.single { it.id=="room_geometry" }.copy(coordinates=RoomGeometry.cells(rectangles).toList(),height=2))
            .replace(initial.nodes.single { it.id=="room" }.copy(walls=RoomGeometry.edges(rectangles).associate { it.id to pair.id },verticalEdges=mapOf("0:0" to edge.id)))
            .replace(initial.nodes.single { it.id=="plate_layout" }.copy(length=5,height=5))
            .replace(initial.nodes.single { it.id=="building" }.copy(roof=""))
        val building=Building(graph=graph,palette=Building().palette+("wall" to "minecraft:stone")+("frame" to "minecraft:oak_planks"))
        val plan=Generator.generate(building,forms,emptyMap())
        h.assertTrue(plan.placements.all { it.pos.x in 0..4 && it.pos.z in 0..4 },"Wall ends and vertical edge segments must share the 5 × 5 outline without exterior pillars")
        val level=h.level;val origin=h.absolutePos(BlockPos(0,80,0))
        fun world(p:P)=origin.offset(p.x,p.y,p.z)
        val actor=FakePlayerFactory.get(level,GameProfile(UUID.randomUUID(),"RoomMountingBuilder"))
        actor.gameMode.changeGameModeForPlayer(GameType.CREATIVE);actor.setPos(origin.x-5.0,origin.y.toDouble(),origin.z-5.0)
        (plan.placements.map { it.pos }+plan.clear).forEach { level.setBlock(world(it),Blocks.AIR.defaultBlockState(),2) }
        for(p in plan.placements) h.assertTrue(PlacementService.place(level,world(p.pos),p,actor.inventory,actor,"room-mounting",creative=true),"Every fixture block must place")
        val occupied=mutableSetOf<Int>();val materials=linkedMapOf<String,MutableSet<Int>>()
        for(z in 0..1) {
            // The building origin is the structural floor; the first wall row is one block higher.
            val pos=world(P(2,1,z));val be=level.getBlockEntity(pos) as CompositeEntity
            val resolved=Geometry.resolve(be.parts,be.groups)
            resolved.solid.voxels { x,y,k -> if(x==8 && y==8) occupied+=z*16+k }
            resolved.materialSolids(be.material,be.surfaces).forEach { (material,solid) ->
                solid.voxels { x,y,k -> if(x==8 && y==8) materials.getOrPut(material) { mutableSetOf() }+=z*16+k }
            }
            val collision=level.getBlockState(pos).getCollisionShape(level,pos).toAabbs()
            for(k in 0..15) h.assertTrue(collision.any { it.contains(8.5/16,8.5/16,(k+0.5)/16) }==resolved.solid.has(8,8,k),"Actual collision must follow each layer")
        }
        h.assertTrue(occupied==(13..18).toSet(),"The actual wall must have six consecutive pixels without the former 26-pixel gap: $occupied")
        h.assertTrue(materials==mapOf("minecraft:stone" to (13..15).toSet(),"minecraft:oak_planks" to (16..18).toSet()),"Each side must retain its three pixels of coating")
        h.assertTrue(plan.placements.count { it.component=="trim" }==8,"The selected vertical edge must share the wall end strips with its entire 2 × 2 × 2 segment")
        for(p in plan.placements) {
            h.assertTrue(PlacementService.matches(level,world(p.pos),p),"The complete placed room must match its plan")
            val be=level.getBlockEntity(world(p.pos)) as CompositeEntity
            val loaded=CompositeEntity(world(p.pos),be.blockState)
            loaded.loadWithComponents(be.saveWithoutMetadata(level.registryAccess()),level.registryAccess())
            h.assertTrue(Geometry.resolve(loaded.parts,loaded.groups).sameVisual(loaded.material,loaded.surfaces,Geometry.resolve(p.parts,p.groups),p.material,p.surfaces),"NBT must preserve layers and vertical edges")
            h.assertTrue(!PlacementService.place(level,world(p.pos),p,actor.inventory,actor,"repeat",creative=true),"The rebuilt room must be idempotent")
        }
        h.assertTrue(plan.clear.all { level.getBlockState(world(it)).isAir },"Room clearances must remain free")
        h.succeed()
    }
}
