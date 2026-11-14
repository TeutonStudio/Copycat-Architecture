package de.teutonstudio.copycatarchitect.server

import com.mojang.authlib.GameProfile
import de.teutonstudio.copycatarchitect.Architect
import de.teutonstudio.copycatarchitect.domain.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import java.util.UUID
import java.util.function.Consumer

@GameTestHolder(Architect.ID)
@PrefixGameTestTemplate(false)
object TableBuildingGameTests {
    private data class Fixture(val player:ServerPlayer,val table:TableEntity,val library:Library,val building:Building,val origin:P,val plan:Plan)
    private fun fixture(h:GameTestHelper):Fixture {
        val level=h.level;val tablePos=h.absolutePos(BlockPos(1,2,1))
        val player=FakePlayerFactory.get(level,GameProfile(UUID.randomUUID(),"TableBuilder"))
        player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);player.setPos(tablePos.x+1.5,tablePos.y.toDouble(),tablePos.z+0.5)
        val library=Library().apply { seed() };val building=Building(schema=2,id="table_test",graph=BuildingGraph.explicitStarter(joinWallEnds=false))
        val origin=PlacementService.local(h.absolutePos(BlockPos(5,3,5)))
        val plan=BuildingProjection(table=P()).plan(Generator.generate(building,library.forms,library.modules),origin)
        (plan.placements.map { it.pos }+plan.clear).forEach { level.setBlock(PlacementService.world(it),Blocks.AIR.defaultBlockState(),2) }
        level.setBlockAndUpdate(tablePos,Architect.TABLE.get().defaultBlockState())
        return Fixture(player,level.getBlockEntity(tablePos) as TableEntity,library,building,origin,plan)
    }
    private fun build(f:Fixture,origin:P=f.origin,building:Building=f.building)=TableBuildings.creativeBuild(f.player,f.table,building,f.library,origin,0)
    private fun record(h:GameTestHelper,f:Fixture)=TableBuildings.current(h.level,f.table)!!

    @JvmStatic @GameTest(template="empty",timeoutTicks=100) fun creativeRebuildPreservesForeignAndReplacedCopycatsWithoutAdoptingThem(h:GameTestHelper) {
        val f=fixture(h);val level=h.level;val foreign=f.plan.placements.first { it.block.isEmpty() }
        PlacementService.place(level,PlacementService.world(foreign.pos),foreign,f.player.inventory,f.player,"foreign",creative=true)
        build(f)
        val first=record(h,f)
        h.assertTrue(first.blocks.none { it.pos==foreign.pos },"Even an identical foreign Copycat must never be adopted")
        val replaced=first.blocks.first();val pos=PlacementService.world(replaced.pos)
        level.setBlock(pos,Blocks.AIR.defaultBlockState(),2)
        val placement=f.plan.placements.single { it.pos==replaced.pos }
        PlacementService.place(level,pos,placement,f.player.inventory,f.player,"foreign-replacement",creative=true)
        val moved=f.origin+P(7,0,0)
        (f.plan.placements.map { it.pos+P(7,0,0) }+f.plan.clear.map { it+P(7,0,0) }).forEach { level.setBlock(PlacementService.world(it),Blocks.AIR.defaultBlockState(),2) }
        build(f,moved)
        h.assertTrue((level.getBlockEntity(pos) as? CompositeEntity)?.buildingId=="","A later foreign replacement at a tracked position must remain")
        h.assertTrue((level.getBlockEntity(PlacementService.world(foreign.pos)) as? CompositeEntity)?.buildingId=="","The initial unrelated Copycat must remain inside the old building region")
        h.assertTrue(first.blocks.filter { it.pos!=replaced.pos }.all { level.getBlockState(PlacementService.world(it.pos)).isAir },"Obsolete owned Copycats must be removed even outside the new footprint")
        val after=Json.encode(record(h,f));h.assertTrue(build(f,moved)==0 && Json.encode(record(h,f))==after,"An unchanged rebuild must be idempotent, including ownership tokens")
        h.succeed()
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=100) fun foreignObstructionAndSurvivalCommandsCannotDeleteOwnedBuilding(h:GameTestHelper) {
        val f=fixture(h);build(f);val before=Json.encode(record(h,f))
        val changed=f.building.copy(palette=f.building.palette.mapValues { "minecraft:oak_planks" })
        val foreign=f.plan.placements.last();val pos=PlacementService.world(foreign.pos)
        h.level.setBlock(pos,Blocks.AIR.defaultBlockState(),2)
        PlacementService.place(h.level,pos,foreign,f.player.inventory,f.player,"foreign",creative=true)
        h.assertTrue(runCatching { build(f,building=changed) }.isFailure,"A foreign Copycat obstructing changed geometry must reject the whole rebuild")
        h.assertTrue(Json.encode(record(h,f))==before && f.plan.placements.all { PlacementService.matches(h.level,PlacementService.world(it.pos),it) },"Rejected preflight must not change another owned block or the ledger")
        Network.open(f.player,"building",f.table.blockPos);f.player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL)
        Network.handle(f.player,Json.encode(Command("creativeBuild",Json.encode(changed),origin=f.origin)))
        h.assertTrue(Json.encode(record(h,f))==before && f.plan.placements.all { PlacementService.matches(h.level,PlacementService.world(it.pos),it) },"A forged survival request must have no effect")
        h.assertTrue(runCatching { build(f,building=changed) }.isFailure,"The service itself must also enforce Creative mode")
        h.succeed()
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=100) fun protectionFailureRestoresAllGeometryReceiptsAndOwnership(h:GameTestHelper) {
        val f=fixture(h);build(f);val old=record(h,f);val first=old.blocks.first()
        val firstEntity=h.level.getBlockEntity(PlacementService.world(first.pos)) as CompositeEntity
        firstEntity.receipt=Receipt(64,"minecraft:stone",true,"paid-survival")
        val before=old.blocks.associate { entry -> val pos=PlacementService.world(entry.pos);entry.pos to (h.level.getBlockEntity(pos) as CompositeEntity).saveWithoutMetadata(h.level.registryAccess()).toString() }
        val ledger=Json.encode(old);val last=f.plan.placements.last().pos
        val protection=Consumer<BlockEvent.EntityPlaceEvent> { event -> if(event.level===h.level && event.pos==PlacementService.world(last) && event.entity?.uuid==f.player.uuid) event.isCanceled=true }
        NeoForge.EVENT_BUS.addListener(protection)
        try {
            h.assertTrue(runCatching { build(f,building=f.building.copy(palette=f.building.palette.mapValues { "minecraft:oak_planks" })) }.isFailure,"A denied later placement must fail the rebuild")
        } finally { NeoForge.EVENT_BUS.unregister(protection) }
        h.assertTrue(Json.encode(record(h,f))==ledger,"A failed transaction must leave the ledger unchanged")
        h.assertTrue(before.all { (p,nbt) -> (h.level.getBlockEntity(PlacementService.world(p)) as? CompositeEntity)?.saveWithoutMetadata(h.level.registryAccess()).toString()==nbt },"Rollback must restore every Copycat, paid receipt, and ownership token")
        h.assertTrue(h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(f.table.blockPos).inflate(20.0)).isEmpty(),"Rollback must not duplicate survival refunds")
        h.succeed()
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=100) fun removalProtectionIsCheckedBeforeAnyBlockChanges(h:GameTestHelper) {
        val f=fixture(h);build(f);val old=record(h,f);val before=Json.encode(old);val target=PlacementService.world(old.blocks.last().pos)
        val protection=Consumer<BlockEvent.BreakEvent> { event -> if(event.level===h.level && event.pos==target && event.player.uuid==f.player.uuid) event.isCanceled=true }
        NeoForge.EVENT_BUS.addListener(protection)
        try { h.assertTrue(runCatching { build(f,building=f.building.copy(palette=f.building.palette.mapValues { "minecraft:glass" })) }.isFailure,"Removal protection must reject rebuilding") }
        finally { NeoForge.EVENT_BUS.unregister(protection) }
        h.assertTrue(Json.encode(record(h,f))==before && f.plan.placements.all { PlacementService.matches(h.level,PlacementService.world(it.pos),it) },"Every old Copycat must survive a denied deletion")
        h.succeed()
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=100) fun tableItemAndFreshCreativeTableRecoverBuildingWithoutMovingIt(h:GameTestHelper) {
        val f=fixture(h);build(f);val record=record(h,f);val id=record.id
        val reloaded=TableEntity(f.table.blockPos,f.table.blockState)
        reloaded.loadWithComponents(f.table.saveWithoutMetadata(h.level.registryAccess()),h.level.registryAccess())
        h.assertTrue(reloaded.buildingId==id,"Table association must survive NBT reload")
        val entry=record.blocks.first();val be=h.level.getBlockEntity(PlacementService.world(entry.pos)) as CompositeEntity
        val copy=CompositeEntity(be.blockPos,be.blockState);copy.loadWithComponents(be.saveWithoutMetadata(h.level.registryAccess()),h.level.registryAccess())
        h.assertTrue(copy.buildingId==id && copy.buildingToken==entry.token,"Exact block provenance must survive NBT reload")
        val drops=Block.getDrops(f.table.blockState,h.level,f.table.blockPos,f.table,f.player,ItemStack.EMPTY)
        h.assertTrue(drops.size==1,"The table must have one normal drop")
        h.level.setBlock(f.table.blockPos,Blocks.AIR.defaultBlockState(),3)
        val next=f.table.blockPos.offset(2,0,0);h.level.setBlockAndUpdate(next.below(),Blocks.STONE.defaultBlockState())
        val item=drops.single();f.player.setItemInHand(InteractionHand.MAIN_HAND,item)
        val hit=BlockHitResult(Vec3(next.x+0.5,next.y.toDouble(),next.z+0.5),Direction.UP,next.below(),false)
        f.player.gameMode.useItemOn(f.player,h.level,item,InteractionHand.MAIN_HAND,hit)
        val moved=h.level.getBlockEntity(next) as? TableEntity
        h.assertTrue(moved?.buildingId==id && record.origin==f.origin,"A recovered table item must reconnect without moving the building")
        h.assertTrue(record.relativeBuilding().graph!!.projection.origin(PlacementService.local(next))==f.origin,"Moving the table must recompute its local offset")
        h.level.setBlock(next,Blocks.AIR.defaultBlockState(),3)
        val freshPos=next.offset(1,0,0);h.level.setBlockAndUpdate(freshPos,Architect.TABLE.get().defaultBlockState())
        val fresh=h.level.getBlockEntity(freshPos) as TableEntity
        h.assertTrue(TableBuildings.bind(h.level,fresh,f.player)?.id==id,"A fresh Creative table near a detached owned building must recognize it")
        h.assertTrue(f.plan.placements.all { PlacementService.matches(h.level,PlacementService.world(it.pos),it) },"Table relocation must leave all existing building blocks in place")
        h.succeed()
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=100) fun liveTableAndDifferentOwnerCannotHaveTheirBuildingClaimed(h:GameTestHelper) {
        val f=fixture(h);build(f);val id=record(h,f).id
        val next=f.table.blockPos.offset(2,0,0);h.level.setBlockAndUpdate(next,Architect.TABLE.get().defaultBlockState())
        val other=h.level.getBlockEntity(next) as TableEntity
        h.assertTrue(TableBuildings.bind(h.level,other,f.player,id)==null,"Copying a table item must not claim a building whose original table is still present")
        h.level.setBlock(f.table.blockPos,Blocks.AIR.defaultBlockState(),3)
        val stranger=FakePlayerFactory.get(h.level,GameProfile(UUID.randomUUID(),"OtherArchitect"));stranger.gameMode.changeGameModeForPlayer(GameType.CREATIVE)
        h.assertTrue(TableBuildings.bind(h.level,other,stranger,id)==null,"A detached building must not be adopted by a different player")
        h.succeed()
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=100) fun oldTurtleJobIsRecoveredOnlyFromExactPlacementReceipts(h:GameTestHelper) {
        val f=fixture(h);val data=ArchitectData.get(h.level)
        data.db.libraries[f.player.uuid.toString()]=f.library
        f.library.buildings["table_test@1"]=f.building
        val job=Job(owner=f.player.uuid.toString(),dimension=h.level.dimension().location().toString(),name=f.building.name,
            origin=f.origin,plan=f.plan,state="complete")
        data.db.jobs+=job
        val indexed=f.plan.placements.withIndex().filter { it.value.block.isEmpty() }
        for((index,p) in indexed) PlacementService.place(h.level,PlacementService.world(p.pos),p,f.player.inventory,f.player,
            if(index==indexed.last().index) "unrelated" else "${job.id}:$index",creative=true)
        val recovered=TableBuildings.bind(h.level,f.table,f.player)
        h.assertTrue(recovered!=null && job.buildingId==recovered.id,"A legacy job with an exact saved plan and actual receipts must be recovered")
        h.assertTrue(recovered!!.blocks.size==indexed.size-1 && recovered.blocks.none { it.pos==indexed.last().value.pos },"Appearance and proximity must not adopt a foreign block in a legacy building")
        h.assertTrue(recovered.blocks.all { TableBuildings.owned(h.level,recovered,it) },"Migrated receipts must produce persistent per-block ownership")
        h.succeed()
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=100) fun nativeLandscapeIsPlacedButNeverTreatedAsAnOwnedCopycat(h:GameTestHelper) {
        val f=fixture(h);val graph=f.building.graph!!
        val garden=BuildingAssembly.newNode(BuildingKind.GARDEN,"garden").copy(coordinates=listOf(P(8,0,0)),block="minecraft:stone",groundBlock="minecraft:stone",groundDepth=1)
        val building=f.building.copy(graph=graph.copy(nodes=graph.nodes.map { if(it.kind==BuildingKind.BUILDING) it.copy(inputs=listOf("garden")) else it }+garden))
        val ground=PlacementService.world(f.origin+P(8,0,0));h.level.setBlockAndUpdate(ground,Blocks.AIR.defaultBlockState())
        build(f,building=building)
        h.assertTrue(h.level.getBlockState(ground).`is`(Blocks.STONE),"Creative building must include native garden soil without material costs")
        h.assertTrue(record(h,f).blocks.none { it.pos==PlacementService.local(ground) },"Native ground must not enter the Copycat deletion ledger")
        build(f)
        h.assertTrue(h.level.getBlockState(ground).`is`(Blocks.STONE),"Removing a garden from the draft must not excavate native terrain")
        h.succeed()
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=100) fun removedTableRemainsRecoverableAfterItsOldChunkIsUnloaded(h:GameTestHelper) {
        val f=fixture(h);build(f);val record=record(h,f);val id=record.id
        h.level.setBlock(f.table.blockPos,Blocks.AIR.defaultBlockState(),3)
        h.assertTrue(!record.attached,"Removing the table must persist that the building is detached")
        // Model an old table chunk that has since left the server's loaded area.
        record.table=record.table+P(16000,0,0)
        h.assertTrue(!h.level.hasChunkAt(PlacementService.world(record.table)),"The previous table location must be unloaded for this regression")
        val pos=f.table.blockPos.offset(2,0,0);h.level.setBlockAndUpdate(pos,Architect.TABLE.get().defaultBlockState())
        val replacement=h.level.getBlockEntity(pos) as TableEntity
        h.assertTrue(TableBuildings.bind(h.level,replacement,f.player,id)?.id==id && record.attached,"The carried identity must recover a known detached building without loading its old table chunk")
        h.assertTrue(record.origin==f.origin,"Restoring the table must not relocate the existing building")
        h.assertTrue(runCatching { TableBuildings.creativeBuild(f.player,replacement,f.building,f.library,f.origin+P(0,257,0),0) }.isFailure,"An invalid vertical table reference must be rejected before storing an unusable document")
        h.succeed()
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=100) fun versionedBuildingSourceRetainsReferencesAndTableOwnership(h:GameTestHelper) {
        val f=fixture(h);val graph=f.building.graph!!
        val members=graph.nodes.filter { it.kind!=BuildingKind.BUILDING && it.id!="empty_wall" }.map { it.id }.toSet()
        val extracted=VersionedGraphs.extract(VersionedGraphs.fromBuilding("buildingRoot","Pinned building",graph,f.building.palette),members,"buildingPart","Building part","instance")
        val bundle=GraphPackages.export(extracted.parent,mapOf(extracted.definition.key to extracted.definition),f.library.forms,f.library.modules)
        val source=f.building.copy(graph=null,versionedGraph=bundle)
        h.assertTrue(BuildingProjection(table=P()).plan(Generator.generate(source,emptyMap(),emptyMap()),f.origin)==f.plan,"Offline package must retain the actual build plan")
        f.library.graphs[extracted.definition.key]=extracted.definition.copy(name="Conflicting version")
        h.assertTrue(runCatching { build(f,building=source) }.isFailure,"A conflicting immutable version must be refused before building")
        h.assertTrue(f.plan.placements.none { h.level.getBlockEntity(PlacementService.world(it.pos)) is CompositeEntity },"Version conflicts must leave the actual build area untouched")
        f.library.graphs.clear()
        build(f,building=source)
        val record=record(h,f)
        h.assertTrue(record.building.graph==null && record.building.versionedGraph==bundle,"The persistent building must retain its parent reference document")
        h.assertTrue(record.blocks.all { TableBuildings.owned(h.level,record,it) },"Every actual Copycat must retain its table ownership token")
        val restored=Json.decode<TableBuilding>(Json.encode(record))
        h.assertTrue(restored.relativeBuilding().versionedGraph==bundle,"Restoring a table must retain the exact source package and versions")
        h.succeed()
    }

}
