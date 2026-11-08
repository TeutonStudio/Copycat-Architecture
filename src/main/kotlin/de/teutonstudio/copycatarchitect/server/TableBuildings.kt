package de.teutonstudio.copycatarchitect.server

import de.teutonstudio.copycatarchitect.Architect
import de.teutonstudio.copycatarchitect.domain.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.util.BlockSnapshot
import net.neoforged.neoforge.event.level.BlockEvent
import java.util.UUID

class TableEntity(pos:BlockPos,state:BlockState):BlockEntity(Architect.TABLE_BE.get(),pos,state) {
    var buildingId=""
    override fun saveAdditional(tag:CompoundTag,registries:HolderLookup.Provider) {
        super.saveAdditional(tag,registries);tag.putString("buildingId",buildingId)
    }
    override fun loadAdditional(tag:CompoundTag,registries:HolderLookup.Provider) {
        super.loadAdditional(tag,registries);buildingId=tag.getString("buildingId")
    }
}

/** A position alone is never proof of ownership: a replacement has a different block token. */
data class OwnedCopycat(val pos:P=P(),val token:String="")
data class TableBuilding(val id:String=UUID.randomUUID().toString(),val owner:String="",val dimension:String="",
    var table:P=P(),var building:Building=Building(),var origin:P=P(),var turn:Int=0,var attached:Boolean=true,
    var blocks:MutableList<OwnedCopycat> = mutableListOf()) {
    fun relativeBuilding():Building = building.graph?.let { graph ->
        building.copy(graph=graph.copy(projection=BuildingProjection(P(table.x-origin.x,table.y-origin.y,table.z-origin.z).turn(-turn),turn)))
    } ?: building
}

object TableBuildings {
    private fun dimension(level:ServerLevel)=level.dimension().location().toString()
    private fun records(level:ServerLevel)=ArchitectData.get(level).db.tableBuildings
    private fun live(level:ServerLevel,record:TableBuilding):Boolean {
        if(!record.attached) return false
        val pos=PlacementService.world(record.table)
        // Never steal an association from an unloaded table.
        return !level.hasChunkAt(pos) || (level.getBlockEntity(pos) as? TableEntity)?.buildingId==record.id
    }
    fun owned(level:ServerLevel,record:TableBuilding,entry:OwnedCopycat):Boolean {
        val be=level.getBlockEntity(PlacementService.world(entry.pos)) as? CompositeEntity ?: return false
        return entry.token.isNotEmpty() && be.buildingId==record.id && be.buildingToken==entry.token
    }
    fun current(level:ServerLevel,table:TableEntity):TableBuilding? = records(level)[table.buildingId]?.takeIf {
        it.dimension==dimension(level) && it.table==PlacementService.local(table.blockPos)
    }
    /** Old Turtle jobs already contain per-block receipts. Proximity or appearance alone is insufficient. */
    private fun legacy(level:ServerLevel,player:ServerPlayer,pos:P):TableBuilding? {
        val data=ArchitectData.get(level)
        val candidates=data.db.jobs.filter { job -> job.buildingId.isEmpty() && job.owner==player.uuid.toString() && job.dimension==dimension(level) }
            .mapNotNull { job ->
                val placements=job.plan.placements.withIndex().filter { it.value.block.isEmpty() }
                if(placements.isEmpty() || placements.any { !level.hasChunkAt(PlacementService.world(it.value.pos)) }) return@mapNotNull null
                val proven=placements.filter { (index,p) ->
                    val be=level.getBlockEntity(PlacementService.world(p.pos)) as? CompositeEntity
                    be!=null && be.buildingId.isEmpty() && be.owner==job.owner && be.receipt.operation=="${job.id}:$index"
                }
                if(proven.none { (_,p) -> kotlin.math.abs(p.pos.x-pos.x)<=96 && kotlin.math.abs(p.pos.y-pos.y)<=64 && kotlin.math.abs(p.pos.z-pos.z)<=96 }) return@mapNotNull null
                val building=data.library(player.uuid).buildings.values.lastOrNull { b ->
                    b.name==job.name && runCatching {
                        val plan=BuildingProjection(table=P(),turn=job.rotation).plan(Generator.generate(b,data.library(player.uuid).forms,data.library(player.uuid).modules),job.origin)
                        plan.placements==job.plan.placements && plan.clear==job.plan.clear
                    }.getOrDefault(false)
                } ?: return@mapNotNull null
                Triple(job,building,proven.map { it.value.pos })
            }
        val (job,building,positions)=candidates.singleOrNull() ?: return null
        val record=TableBuilding(owner=job.owner,dimension=job.dimension,table=pos,building=building,origin=job.origin,turn=job.rotation)
        records(level)[record.id]=record;job.buildingId=record.id
        positions.forEach { track(level,record.id,PlacementService.world(it)) }
        return record
    }
    /** A carried table retains its ID; a fresh table can recover a nearby detached building. */
    fun bind(level:ServerLevel,table:TableEntity,player:ServerPlayer,hint:String=""):TableBuilding? {
        current(level,table)?.let { return it }
        val owner=player.uuid.toString();val pos=PlacementService.local(table.blockPos)
        fun available(r:TableBuilding)=r.owner==owner && r.dimension==dimension(level) && !live(level,r)
        val requested=records(level)[hint.ifEmpty { table.buildingId }]
        if(requested!=null && !available(requested)) { table.buildingId="";table.setChanged();return null }
        val hinted=requested
        val candidates=if(hinted!=null) listOf(hinted) else records(level).values.filter { r ->
            available(r) && r.blocks.any { entry ->
                val delta=P(entry.pos.x-pos.x,entry.pos.y-pos.y,entry.pos.z-pos.z)
                kotlin.math.abs(delta.x)<=96 && kotlin.math.abs(delta.y)<=64 && kotlin.math.abs(delta.z)<=96 &&
                    level.hasChunkAt(PlacementService.world(entry.pos)) && owned(level,r,entry)
            }
        }
        // Ambiguous neighbors need their carried table item; proximity must not select the wrong house.
        val record=candidates.singleOrNull() ?: (if(candidates.isEmpty()) legacy(level,player,pos) else null)
            ?: run { table.buildingId="";table.setChanged();return null }
        if(record.building.graph!=null && runCatching {
            BuildingProjection(P(pos.x-record.origin.x,pos.y-record.origin.y,pos.z-record.origin.z).turn(-record.turn),record.turn).validate()
        }.isFailure) { table.buildingId="";table.setChanged();return null }
        record.table=pos;record.attached=true;table.buildingId=record.id;table.setChanged();ArchitectData.get(level).setDirty()
        return record
    }
    fun prepare(level:ServerLevel,table:TableEntity,player:ServerPlayer,building:Building,origin:P,turn:Int):TableBuilding {
        require(level.getBlockEntity(table.blockPos)===table) { "Open a nearby architect table" }
        require(level.mayInteract(player,table.blockPos) && player.mayUseItemAt(table.blockPos,Direction.UP,ItemStack(Architect.TABLE_ITEM.get()))) { "Table access denied" }
        if(building.graph!=null) {
            val pos=PlacementService.local(table.blockPos)
            BuildingProjection(P(pos.x-origin.x,pos.y-origin.y,pos.z-origin.z).turn(-turn),turn).validate()
        }
        val record=bind(level,table,player)
        require(record==null || record.owner==player.uuid.toString()) { "This building belongs to another player" }
        require(record==null || ArchitectData.get(level).db.jobs.none { it.buildingId==record.id && it.state !in listOf("complete","cancelled") }) { "Finish or cancel this table's building job first" }
        return (record ?: TableBuilding(owner=player.uuid.toString(),dimension=dimension(level),table=PlacementService.local(table.blockPos)))
            .copy(building=building,origin=origin,turn=turn,blocks=record?.blocks?.toMutableList() ?: mutableListOf())
    }
    fun remember(level:ServerLevel,table:TableEntity,record:TableBuilding) {
        record.attached=true;records(level)[record.id]=record;table.buildingId=record.id;table.setChanged();ArchitectData.get(level).setDirty()
    }
    fun detach(level:ServerLevel,table:TableEntity) {
        current(level,table)?.let { it.attached=false;ArchitectData.get(level).setDirty() }
    }
    /** Called only after this building actually placed a block, never for a matching foreign block. */
    fun track(level:ServerLevel,id:String,pos:BlockPos) {
        val record=records(level)[id] ?: return
        val be=level.getBlockEntity(pos) as? CompositeEntity ?: return
        val token=UUID.randomUUID().toString();be.buildingId=id;be.buildingToken=token;be.setChanged()
        val local=PlacementService.local(pos);record.blocks.removeAll { it.pos==local };record.blocks+=OwnedCopycat(local,token)
        ArchitectData.get(level).setDirty()
    }
    fun creativeBuild(player:ServerPlayer,table:TableEntity,building:Building,library:Library,origin:P,turn:Int):Int {
        require(player.isCreative) { "Creative mode is required" }
        require(turn in 0..3) { "Invalid building rotation" }
        val level=player.serverLevel();val delta=table.blockPos.subtract(player.blockPosition())
        require(kotlin.math.abs(delta.x)<=6 && kotlin.math.abs(delta.y)<=3 && kotlin.math.abs(delta.z)<=6) { "Open a nearby architect table" }
        require(PlacementService.world(origin).distManhattan(table.blockPos)<384) { "Building origin is too far from the table" }
        val generated=Generator.generate(building,library.forms,library.modules)
        val plan=BuildingProjection(table=P(),turn=turn).plan(generated,origin)
        require(plan.placements.size+plan.clear.size<=65536) { "Building is too large" }
        val record=prepare(level,table,player,building,origin,turn)
        val targets=plan.placements.associateBy { it.pos }
        val all=(targets.keys+plan.clear+record.blocks.map { it.pos }).toSet()
        all.forEach { p -> val pos=PlacementService.world(p);require(level.hasChunkAt(pos) && level.isInWorldBounds(pos)) { "Building chunk is not loaded at $p" } }
        val old=record.blocks.filter { owned(level,record,it) }.associateBy { it.pos }
        val removed=old.keys.filter { p -> targets[p]?.let { !PlacementService.matches(level,PlacementService.world(p),it) } ?: true }.toSet()
        // Matching foreign blocks may be reused, but do not become part of the deletion ledger.
        for(p in plan.placements) {
            val pos=PlacementService.world(p.pos)
            require(p.pos in removed || level.getBlockState(pos).isAir || PlacementService.matches(level,pos,p)) { "Foreign block obstructs the building at ${p.pos}" }
            if(p.block.isEmpty()) { Geometry.normalized(p.parts,p.groups);p.surfaceItems().keys.forEach { Economy.state(it) } } else Economy.nativeState(p.block)
        }
        plan.clear.forEach { p -> require(p in removed || level.getBlockState(PlacementService.world(p)).isAir) { "Foreign block obstructs the clearance at $p" } }
        val additions=plan.placements.filter { it.pos in removed || !PlacementService.matches(level,PlacementService.world(it.pos),it) }
        val changed=removed+additions.map { it.pos }
        changed.forEach { p ->
            val pos=PlacementService.world(p)
            require(level.mayInteract(player,pos) && player.mayUseItemAt(pos,Direction.UP,ItemStack(Architect.GUN.get()))) { "Building change denied at $p" }
            require(level.getEntities(player,AABB(pos)).none { it.isAlive && !it.isSpectator }) { "Building occupied at $p" }
        }
        removed.forEach { p ->
            val pos=PlacementService.world(p);val event=BlockEvent.BreakEvent(level,pos,level.getBlockState(pos),player)
            NeoForge.EVENT_BUS.post(event);require(!event.isCanceled) { "Building removal denied by protection at $p" }
        }
        val snapshots=changed.associateWith { BlockSnapshot.create(level.dimension(),level,PlacementService.world(it)) }
        val newEntries=mutableListOf<OwnedCopycat>()
        try {
            removed.forEach { p ->
                val pos=PlacementService.world(p)
                // A Creative rebuild does not refund survival receipts or manufacture item drops.
                (level.getBlockEntity(pos) as CompositeEntity).receipt=Receipt()
                require(level.setBlock(pos,Blocks.AIR.defaultBlockState(),2)) { "World rejected removal at $p" }
            }
            additions.forEach { p ->
                val pos=PlacementService.world(p.pos)
                if(PlacementService.place(level,pos,p,player.inventory,player,"creative:${record.id}",creative=true,updateFlags=2) && p.block.isEmpty()) {
                    val be=level.getBlockEntity(pos) as CompositeEntity
                    val token=UUID.randomUUID().toString();be.buildingId=record.id;be.buildingToken=token;be.setChanged()
                    newEntries+=OwnedCopycat(p.pos,token)
                }
            }
        } catch(e:Exception) {
            val restoring=level.restoringBlockSnapshots;level.restoringBlockSnapshots=true
            try { snapshots.values.toList().asReversed().forEach { snapshot ->
                // setBlock returns false for the same BlockState; its entity still needs its old NBT.
                if(!snapshot.restore(2)) snapshot.restoreBlockEntity(level,snapshot.pos)
            } }
            finally { level.restoringBlockSnapshots=restoring }
            changed.forEach { p -> val pos=PlacementService.world(p);level.sendBlockUpdated(pos,level.getBlockState(pos),level.getBlockState(pos),3) }
            throw e
        }
        record.blocks=(old.values.filter { it.pos !in removed }+newEntries).toMutableList()
        remember(level,table,record)
        changed.forEach { p -> val pos=PlacementService.world(p);level.updateNeighborsAt(pos,level.getBlockState(pos).block) }
        return additions.size
    }
}
