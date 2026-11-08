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
import net.minecraft.world.phys.shapes.CollisionContext
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import java.util.UUID

/** Builds the complete reusable T example as paid, persistent blocks in a real server world. */
@GameTestHolder(Architect.ID)
@PrefixGameTestTemplate(false)
object TBuildingGameTests {
    @JvmStatic
    @GameTest(template="empty",timeoutTicks=400)
    fun threeStoreyTBuildingRetainsItsFootprintMaterialsPatternsAndWindows(h:GameTestHelper) {
        val example=TBuildingExample
        val compiled=BuildingGraphCompiler.compile(example.building,example.forms,example.modules)
        val plan=compiled.plan
        val level=h.level
        // Keep the 17 x 17 building clear of the small templates used by other concurrent tests.
        val origin=h.absolutePos(BlockPos(3,1,3)).offset(160,0,160)
        fun world(p:P)=origin.offset(p.x,p.y,p.z)
        val bounds=plan.placements.map { it.pos }+plan.clear
        val low=P(bounds.minOf { it.x },bounds.minOf { it.y },bounds.minOf { it.z })
        val high=P(bounds.maxOf { it.x },bounds.maxOf { it.y },bounds.maxOf { it.z })
        val worldLow=world(low);val worldHigh=world(high)
        for(x in (worldLow.x shr 4)..(worldHigh.x shr 4))
            for(z in (worldLow.z shr 4)..(worldHigh.z shr 4)) level.getChunk(x,z)
        for(x in low.x..high.x) for(y in low.y..high.y) for(z in low.z..high.z)
            level.setBlockAndUpdate(world(P(x,y,z)),Blocks.AIR.defaultBlockState())

        val expectedFootprint=buildSet {
            for(x in 0 until 15) for(z in 0 until 5) add(P(x,0,z))
            for(x in 5 until 10) for(z in 5 until 15) add(P(x,0,z))
        }
        h.assertTrue(expectedFootprint.size==125 && example.footprint==expectedFootprint,
            "The example must have the actual 15 x 5 crossbar and centered 5 x 10 stem of a T")
        h.assertTrue(compiled.storeys.map { it.level }==listOf(0,1,2) && compiled.storeys.map { it.base }==listOf(1,7,13),
            "The T house must contain three distinct five-block-high storeys above their floors")
        h.assertTrue(plan.warnings.isEmpty() && plan.placements.map { it.pos }.distinct().size==plan.placements.size,
            "The complete example must generate without warnings or overlapping world positions")

        val supplies=plan.materials.flatMap { (material,count) ->
            val sample=ItemStack(Economy.item(material))
            buildList {
                var remaining=count
                while(remaining>0) {
                    val stack=sample.copy();stack.count=minOf(remaining,stack.maxStackSize)
                    add(stack);remaining-=stack.count
                }
            }
        }
        val inventory=SimpleContainer(supplies.size+1)
        inventory.setItem(0,Economy.cartridge(plan.raw))
        supplies.forEachIndexed { index,stack -> inventory.setItem(index+1,stack) }
        val actor=FakePlayerFactory.get(level,GameProfile(UUID.randomUUID(),"TBuildingBuilder"))
        actor.gameMode.changeGameModeForPlayer(GameType.SURVIVAL)
        actor.setPos(origin.x-8.0,origin.y.toDouble(),origin.z-8.0)
        h.assertTrue(plan.placements.all { it.block.isEmpty() } && plan.raw>0,
            "The test house must use the real saved Copycat geometry and paid surface assignments")
        for(placement in plan.placements) h.assertTrue(
            PlacementService.place(level,world(placement.pos),placement,inventory,actor,"t-building-example"),
            "Every generated T-building block must place in Survival: ${placement.pos}")
        h.assertTrue(inventory.getItem(0).`is`(CartridgeRegistry.EMPTY.get()) && Economy.units(inventory.getItem(0))==0 &&
            (1 until inventory.containerSize).all { inventory.getItem(it).isEmpty },
            "The real build must consume exactly the generated zinc and every surface sample")

        fun entity(p:P)=level.getBlockEntity(world(p)) as? CompositeEntity
            ?: error("Missing real T-building block entity at $p")
        fun materials(p:P)=entity(p).let { Geometry.resolve(it.parts,it.groups).materialSolids(it.material,it.surfaces) }
        val checkedCollisionShapes=mutableSetOf<String>()
        var rawPaid=0
        val paidSurfaces=linkedMapOf<String,Int>()
        for(placement in plan.placements) {
            val pos=world(placement.pos);val be=entity(placement.pos)
            val actual=Geometry.resolve(be.parts,be.groups)
            val expected=Geometry.resolve(placement.parts,placement.groups)
            h.assertTrue(PlacementService.matches(level,pos,placement) && actual.solid.key()==expected.solid.key() &&
                actual.materialSolids(be.material,be.surfaces).mapValues { it.value.key() }==
                expected.materialSolids(placement.material,placement.surfaces).mapValues { it.value.key() },
                "Every placed voxel and its actual material must match the editable graph at ${placement.pos}")
            h.assertTrue(be.receipt.units==Extrusion.cost(placement.parts,placement.groups) && be.receipt.surfaces==placement.surfaceItems(),
                "Each actual block must retain exactly its paid Copycat volume and material samples")
            rawPaid+=be.receipt.units
            for((id,count) in be.receipt.surfaces) paidSurfaces[id]=(paidSurfaces[id] ?: 0)+count
            val loaded=CompositeEntity(pos,level.getBlockState(pos))
            loaded.loadWithComponents(be.saveWithoutMetadata(level.registryAccess()),level.registryAccess())
            h.assertTrue(Geometry.resolve(loaded.parts,loaded.groups).visualKey(loaded.material,loaded.surfaces)==placement.visualKey() &&
                loaded.receipt==be.receipt && loaded.owner==actor.uuid.toString(),
                "World saving must preserve every T-building shape, material, payment and owner")

            // A material variant shares collision; exhaust all 4096 voxels once for each distinct shape.
            if(checkedCollisionShapes.add(expected.solid.key())) {
                val state=level.getBlockState(pos)
                val ordinary=state.getCollisionShape(level,pos).toAabbs()
                val contextual=state.getCollisionShape(level,pos,CollisionContext.empty()).toAabbs()
                var exact=true
                for(x in 0..15) for(y in 0..15) for(z in 0..15) {
                    val vx=(x+0.5)/16;val vy=(y+0.5)/16;val vz=(z+0.5)/16
                    val occupied=expected.solid.has(x,y,z)
                    if(ordinary.any { it.contains(vx,vy,vz) }!=occupied || contextual.any { it.contains(vx,vy,vz) }!=occupied) exact=false
                }
                h.assertTrue(exact,"Both actual collision paths must retain the exact window, panel and ornament geometry at ${placement.pos}")
            }
        }
        h.assertTrue(rawPaid==plan.raw && paidSurfaces==plan.materials,
            "All actual world receipts must equal the complete generated material bill")
        h.assertTrue(paidSurfaces.keys==setOf("minecraft:stone_bricks","minecraft:bricks","minecraft:white_terracotta",
            "minecraft:quartz_block","minecraft:dark_oak_planks","minecraft:glass","minecraft:oak_planks","minecraft:deepslate_tiles"),
            "The real building must contain all eight distinct facade, ornament, frame, floor and roof materials")

        val occupied=plan.placements.associateBy { it.pos }
        var interiorCells=0
        var windows=0
        var reliefSquares=0
        for(storey in compiled.storeys) {
            for(cell in expectedFootprint) {
                h.assertTrue(!level.getBlockState(world(cell+P(0,storey.base-1,0))).isAir &&
                    !level.getBlockState(world(cell+P(0,storey.base+5,0))).isAir,
                    "Every usable T-shaped room cell needs a real floor and ceiling on storey ${storey.level}")
                for(y in 0 until 5) {
                    val p=cell+P(0,storey.base+y,0);interiorCells++
                    h.assertTrue(p in plan.clear && p !in occupied && level.getBlockState(world(p)).isAir,
                        "The full T-shaped interior must remain usable on every storey: $p")
                }
            }
            for(direction in 0..3) {
                val facade=RoomGeometry.edges(example.rectangles).filter { it.direction==direction }.flatMap { it.cells }.toSet()
                h.assertTrue(plan.placements.any { placement ->
                    placement.pos.y in storey.base until storey.base+5 &&
                        placement.pos.copy(y=0) in facade && materials(placement.pos).containsKey("minecraft:glass")
                },"Every side of every storey must have an actual glass window, direction $direction level ${storey.level}")
            }
            var storeyWindows=0
            for(edge in RoomGeometry.edges(example.rectangles)) for((index,cell) in edge.cells.withIndex()) {
                val frieze=materials(cell+P(0,storey.base+3,0))
                val body=if(index%5%2==0) "minecraft:bricks" else "minecraft:white_terracotta"
                val square=frieze.getValue("minecraft:quartz_block")
                h.assertTrue(frieze.keys==setOf(body,"minecraft:quartz_block") && square.count()>=8*8*3 && frieze.getValue(body).count()>=704,
                    "Each real facade bay must retain its red/white pattern and centered raised quartz square on level ${storey.level}")
                // At the two concave T joints, two decorated faces share one block entity.
                // Verify each complete face rather than treating the other legitimate face as excess.
                val normals=if(edge.direction==0 || edge.direction==3) 11..13 else 2..4
                var raisedSquare=true
                for(tangent in 4..11) for(y in 4..11) for(normal in normals)
                    if(!square.has(if(edge.direction%2==0) tangent else normal,y,if(edge.direction%2==0) normal else tangent)) raisedSquare=false
                h.assertTrue(raisedSquare,
                    "The quartz wall decoration must protrude beyond the 3/16 panel, not become a flat material label")
                reliefSquares++
                for(y in 1..2) {
                    val p=cell+P(0,storey.base+y,0)
                    if(index%5 !in 1..3 || p !in occupied) continue
                    val surface=materials(p)
                    val glass=surface.getValue("minecraft:glass")
                    h.assertTrue(glass.count()==12*12*2 && surface.getValue("minecraft:dark_oak_planks").count()==224 &&
                        Geometry.solid(entity(p).parts).count()==512,
                        "Each actual window must have a clear 12 x 12 pane, thin dark wooden frame and no opaque panel behind the glass")
                    val paneNormal=mutableSetOf<Int>()
                    var framed=true
                    glass.voxels { x,vy,z ->
                        paneNormal+=if(edge.direction%2==0) z else x
                        if(vy !in 2..13 || (if(edge.direction%2==0) x else z) !in 2..13) framed=false
                    }
                    h.assertTrue(framed && paneNormal==(if(edge.direction==0 || edge.direction==3) (14..15) else (0..1)).toSet(),
                        "The glass must occupy the room-facing 2/16 edge of the real wall in every direction")
                    storeyWindows++;windows++
                }
            }
            h.assertTrue(storeyWindows==if(storey.level==0) 70 else 72,
                "Each upper T-shaped storey needs 72 real windows; only the two ground-floor entrance cells are omitted")
        }
        h.assertTrue(windows==214 && reliefSquares==180,
            "The finished real building needs all 214 framed windows and 180 alternating raised wall decorations")
        h.assertTrue(interiorCells==1875 && plan.clear.all { level.getBlockState(world(it)).isAir },
            "All 1875 T-shaped room cells and every defined doorway must remain unobstructed")
        for(x in listOf(1,13)) for(y in low.y..high.y) h.assertTrue(level.getBlockState(world(P(x,y,10))).isAir,
            "Both rear notches must remain empty through floors, all storeys and roof so the result stays T-shaped")
        for(y in 1..3) h.assertTrue(level.getBlockState(world(P(7,y,15))).isAir,
            "The ground-floor entrance must remain a genuine three-block-high opening")

        val before=(0 until inventory.containerSize).map { inventory.getItem(it).copy() }
        for(placement in plan.placements) h.assertTrue(
            !PlacementService.place(level,world(placement.pos),placement,inventory,actor,"t-building-repeat"),
            "The complete three-storey example must remain idempotent at ${placement.pos}")
        h.assertTrue(before.indices.all { ItemStack.matches(before[it],inventory.getItem(it)) },
            "Checking an already-built T house must not charge any additional material")
        println("ARCHITECT T BUILDING: ${plan.placements.size} exact Survival placements, three complete T-shaped storeys, $interiorCells free room cells, $windows thin framed windows in every direction, $reliefSquares alternating raised quartz decorations, ${plan.materials.size} materials, all material voxels/NBT/receipts and ${checkedCollisionShapes.size} exhaustive world collision shapes verified")
        h.succeed()
    }
}
