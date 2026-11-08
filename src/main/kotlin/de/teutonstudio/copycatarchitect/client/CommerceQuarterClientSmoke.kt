package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.Architect
import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.Button
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.phys.AABB
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.math.atan2
import kotlin.math.sqrt

/** Builds through the real table's Save and Creative Build buttons, then checks a fresh world load. */
object CommerceQuarterClientSmoke {
    private data class Fixture(val origin:P,val table:P,val buildingKey:String,val owner:String,val positions:Int)
    private var ticks=0
    private var waiting=0
    private var failed=false
    private var pending:CompletableFuture<Unit>?=null
    private lateinit var fixture:Fixture
    private lateinit var building:Building
    private val plan by lazy { CommerceQuarterExample.plan() }
    private val verifyOnly get()=java.lang.Boolean.getBoolean("architect.commerceQuarterVerify")

    private fun server(mc:Minecraft,action:()->Unit) {
        val future=CompletableFuture<Unit>();pending=future
        checkNotNull(mc.singleplayerServer).execute {
            try { action();future.complete(Unit) } catch(error:Throwable) { future.completeExceptionally(error) }
        }
    }
    private fun player(mc:Minecraft):ServerPlayer=checkNotNull(mc.singleplayerServer).playerList.players.single()
    private fun metadata(mc:Minecraft):Path=checkNotNull(mc.singleplayerServer).getWorldPath(LevelResource.ROOT).resolve("commerce-quarter-test.json")
    private fun world(p:P)=BlockPos(fixture.origin.x+p.x,fixture.origin.y+p.y,fixture.origin.z+p.z)
    private fun tablePos()=BlockPos(fixture.table.x,fixture.table.y,fixture.table.z)
    private fun screenshot(mc:Minecraft,name:String) {
        Screenshot.grab(mc.gameDirectory,"architect-commerce-quarter-$name.png",mc.mainRenderTarget) { println(it.string) }
    }
    private fun camera(mc:Minecraft,x:Double,y:Double,z:Double,targetX:Double,targetY:Double,targetZ:Double) {
        server(mc) {
            val p=player(mc);p.abilities.flying=true;p.onUpdateAbilities()
            val dx=targetX-x;val dz=targetZ-z;val dy=targetY-(y+p.eyeHeight)
            p.teleportTo(p.serverLevel(),fixture.origin.x+x,fixture.origin.y+y,fixture.origin.z+z,
                Math.toDegrees(atan2(-dx,dz)).toFloat(),-Math.toDegrees(atan2(dy,sqrt(dx*dx+dz*dz))).toFloat())
        }
    }
    private fun openTable(mc:Minecraft) {
        server(mc) {
            val p=player(mc)
            p.teleportTo(p.serverLevel(),fixture.table.x+0.5,fixture.table.y+1.0,fixture.table.z-2.5,0f,15f)
            Network.open(p,"building",tablePos())
        }
    }
    private fun click(mc:Minecraft,key:String) {
        val editor=mc.screen as? BuildingScreen ?: error("The real architect table is not open")
        val button=editor.children().filterIsInstance<Button>().single { it.visible && it.message.string==ui(key) }
        check(button.active) { "Table action $key is disabled: ${ClientState.snapshot.message}" }
        check(editor.mouseClicked(button.x+button.width/2.0,button.y+button.height/2.0,0))
        editor.mouseReleased(button.x+button.width/2.0,button.y+button.height/2.0,0)
    }
    private fun create(mc:Minecraft) {
        val p=player(mc);val level=p.serverLevel();val srv=checkNotNull(mc.singleplayerServer)
        p.gameMode.changeGameModeForPlayer(GameType.CREATIVE)
        p.abilities.flying=true;p.onUpdateAbilities()
        level.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,srv)
        level.gameRules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false,srv)
        level.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,srv)
        level.dayTime=6000;level.setWeatherParameters(0,0,false,false)
        for(cx in 2..7) for(cz in 2..7) level.getChunk(cx,cz)
        val ground=(44..100 step 4).flatMap { x->(40..96 step 4).map { z->level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z) } }.max()
        val origin=P(56,ground+1,56);val tableLocal=P(15,0,-6)
        fixture=Fixture(origin,origin+tableLocal,"commerce_quarter@1",p.uuid.toString(),plan.placements.size)
        building=CommerceQuarterExample.building.copy(graph=checkNotNull(CommerceQuarterExample.building.graph).withCurrentSchema().copy(projection=BuildingProjection(tableLocal)))
        // Only this dedicated copy is prepared. The architecture itself is placed solely by the real table action below.
        val region=AABB(net.minecraft.world.phys.Vec3.atLowerCornerOf(world(P(-12,-1,-18))),net.minecraft.world.phys.Vec3.atLowerCornerOf(world(P(46,30,39))))
        level.getEntitiesOfClass(LivingEntity::class.java,region).filter { it!==p }.forEach { it.discard() }
        for(x in -12..45) for(z in -18..38) {
            for(y in 0..28) if(!level.getBlockState(world(P(x,y,z))).isAir) level.setBlock(world(P(x,y,z)),Blocks.AIR.defaultBlockState(),2)
            level.setBlock(world(P(x,-1,z)),Blocks.GRASS_BLOCK.defaultBlockState(),2)
            level.setBlock(world(P(x,-2,z)),Blocks.DIRT.defaultBlockState(),2)
        }
        check(plan.placements.all { level.getBlockState(world(it.pos)).isAir }) { "The initial table must start with an empty building site" }
        level.setBlock(tablePos(),Architect.TABLE.get().defaultBlockState(),3)
        val data=ArchitectData.get(level)
        val library=Library().also { lib->
            lib.seed();lib.buildings.clear()
            lib.forms.putAll(CommerceQuarterExample.forms);lib.modules.putAll(CommerceQuarterExample.modules)
            lib.buildings[fixture.buildingKey]=building
        }
        data.db.libraries[p.uuid.toString()]=library;data.setDirty()
        p.inventory.setItem(0,ItemStack(Architect.TABLE_ITEM.get()))
        p.inventory.setItem(1,ItemStack(Architect.GUN.get()))
        level.setDefaultSpawnPos(world(P(15,0,-10)),0f)
        p.setRespawnPosition(level.dimension(),world(P(15,0,-10)),0f,true,false)
        p.teleportTo(level,fixture.table.x+0.5,fixture.table.y+1.0,fixture.table.z-2.5,0f,15f)
        Files.writeString(metadata(mc),Json.encode(fixture))
        Network.open(p,"building",tablePos())
        println("ARCHITECT COMMERCE SITE READY: empty site at $origin, real table at ${fixture.table}; waiting for native UI Save and Creative Build")
    }
    private fun verifyServer(mc:Minecraft) {
        val p=player(mc);val level=p.serverLevel()
        check(p.uuid.toString()==fixture.owner) { "The demonstration must use its saved local player profile" }
        for(placement in plan.placements) {
            level.getChunk(world(placement.pos))
            check(PlacementService.matches(level,world(placement.pos),placement)) { "Saved Commerce geometry differs at ${placement.pos}" }
        }
        check(plan.clear.all { level.getBlockState(world(it)).isAir }) { "A room, door or stair clearance is obstructed" }
        val table=level.getBlockEntity(tablePos()) as? TableEntity ?: error("The placed architect table is missing")
        val record=TableBuildings.current(level,table) ?: error("The table was not bound by its native Creative Build action")
        check(record.owner==fixture.owner && record.origin==fixture.origin && record.turn==0)
        check(record.building.id==building.id && record.building.version==building.version)
        check(record.relativeBuilding().graph!!.projection.origin(fixture.table)==fixture.origin)
        check(record.blocks.size==plan.placements.count { it.block.isEmpty() })
        check(record.blocks.all { TableBuildings.owned(level,record,it) }) { "The table's saved block ownership ledger is incomplete" }
        val library=ArchitectData.get(level).library(p.uuid)
        check(library.forms.keys.containsAll(CommerceQuarterExample.forms.keys) && library.modules.keys.containsAll(CommerceQuarterExample.modules.keys))
        check(library.buildings.getValue(fixture.buildingKey)==building)
        println("ARCHITECT COMMERCE WORLD VERIFIED: ${plan.placements.size} blocks, ${record.blocks.size} owned Copycats, ${plan.placements.count { it.block.isNotEmpty() }} native blocks, table ${fixture.buildingKey}, clear rooms and stair, exact materials")
    }
    private fun verifyClient(mc:Minecraft):Boolean {
        val level=checkNotNull(mc.level)
        return plan.placements.all { p->
            if(p.block.isNotEmpty()) level.getBlockState(world(p.pos))==Economy.nativeState(p.block)
            else (level.getBlockEntity(world(p.pos)) as? CompositeEntity)?.let { be->
                Geometry.resolve(be.parts,be.groups).visualKey(be.material,be.surfaces)==p.visualKey()
            }==true
        }
    }
    private fun load(mc:Minecraft) {
        fixture=Json.decode<Fixture>(Files.readString(metadata(mc)))
        building=ArchitectData.get(player(mc).serverLevel()).library(player(mc).uuid).buildings.getValue(fixture.buildingKey)
        verifyServer(mc)
    }
    fun tick():Boolean {
        if(failed) return false
        val mc=Minecraft.getInstance()
        if(mc.level==null || mc.player==null || mc.singleplayerServer==null || mc.overlay!=null) {
            check(++waiting<4800) { "Commerce acceptance needs its dedicated quick-play world" };return false
        }
        try {
            pending?.let { if(!it.isDone) return false;it.join();pending=null }
            ticks++
            if(verifyOnly) {
                when(ticks) {
                    1->{ mc.options.hideGui=true;server(mc) { load(mc) } }
                    2->camera(mc,37.0,22.0,-20.0,16.0,7.0,9.0)
                    85->{
                        if(!verifyClient(mc)) { check(++waiting<4800) { "Reloaded client did not receive all Commerce blocks" };ticks--;return false }
                        screenshot(mc,"reloaded-world");mc.options.hideGui=false;openTable(mc)
                    }
                    110->{
                        val editor=mc.screen as? BuildingScreen ?: error("The saved real table did not reopen")
                        check(editor.building.id==building.id && editor.building.version==building.version)
                        check(ClientState.snapshot.tableOrigin==fixture.origin)
                        editor.openBuildingGraph();editor.kilaBuildingGraph?.fit()
                    }
                    135->{ screenshot(mc,"reloaded-table");println("ARCHITECT COMMERCE RELOAD PASSED: exact world, native table binding, library and editor survived a fresh client/server process") }
                    145->{ mc.setScreen(null);openTable(mc) }
                    165->{ mc.setScreen(null);mc.options.hideGui=false;server(mc) { checkNotNull(mc.singleplayerServer).saveEverything(true,false,false) } }
                    175->mc.stop()
                    else->return false
                }
                return true
            }
            when(ticks) {
                1->{
                    mc.options.guiScale().set(2);mc.options.fov().set(55);mc.options.fovEffectScale().set(0.0)
                    mc.options.gamma().set(1.0);mc.options.renderDistance().set(10);mc.options.simulationDistance().set(6)
                    mc.options.entityDistanceScaling().set(1.5);mc.resizeDisplay();mc.options.hideGui=false
                    server(mc) { create(mc) }
                }
                20->{ val e=mc.screen as? BuildingScreen ?: error("Real architect table editor did not open");check(e.building.id==building.id);e.openBuildingGraph();e.kilaBuildingGraph?.fit() }
                45->click(mc,"save")
                65->{
                    val saved=ClientState.snapshot.library.buildings.values.filter { it.id==building.id }.maxBy { it.version }
                    if(saved.version<2) { check(++waiting<2400) { "Real table Save failed: ${ClientState.snapshot.message}" };ticks--;return false }
                    building=saved;fixture=fixture.copy(buildingKey="${saved.id}@${saved.version}")
                    openTable(mc)
                    println("ARCHITECT COMMERCE TABLE SAVE PASSED: authoritative ${fixture.buildingKey} received through real UI Save")
                }
                85->{ val e=mc.screen as? BuildingScreen ?: error("Saved table editor did not reopen");check(e.building.version==building.version);e.tab=6;e.graphMode=false;e.graphInspector=false;mc.setScreen(e) }
                100->screenshot(mc,"table-before-build")
                110->click(mc,"creative_build")
                130->{
                    if(!ClientState.snapshot.message.startsWith("Kreativ gebaut:")) { check(++waiting<2400) { "Native table build failed: ${ClientState.snapshot.message}" };ticks--;return false }
                    server(mc) { verifyServer(mc);Files.writeString(metadata(mc),Json.encode(fixture));checkNotNull(mc.singleplayerServer).saveEverything(true,false,false) }
                    println("ARCHITECT COMMERCE NATIVE BUILD PASSED: ${ClientState.snapshot.message}")
                }
                140->{ mc.setScreen(null);mc.options.hideGui=true;camera(mc,37.0,22.0,-20.0,16.0,7.0,9.0) }
                215->{
                    if(!verifyClient(mc)) { check(++waiting<2400) { "Client did not receive all actual Commerce geometry" };ticks--;return false }
                    screenshot(mc,"exterior-street")
                }
                225->camera(mc,-10.0,23.0,37.0,16.0,6.0,11.0)
                290->screenshot(mc,"exterior-courtyard")
                300->camera(mc,15.5,1.1,-1.5,15.5,3.0,21.0)
                355->screenshot(mc,"lane")
                365->camera(mc,2.2,1.1,11.5,9.0,2.5,4.0)
                420->screenshot(mc,"interior-inn")
                430->camera(mc,20.5,7.1,3.0,28.0,8.5,17.0)
                485->screenshot(mc,"interior-atelier")
                495->{ mc.options.hideGui=false;openTable(mc) }
                520->{ val e=mc.screen as? BuildingScreen ?: error("Built table did not reopen");check(e.building.version==building.version);check(ClientState.snapshot.tableOrigin==fixture.origin);e.openBuildingGraph();e.kilaBuildingGraph?.fit() }
                550->screenshot(mc,"table-graph")
                560->(mc.screen as BuildingScreen).openFloorPlan()
                590->screenshot(mc,"table-floorplan")
                600->{ mc.setScreen(null);server(mc) { verifyServer(mc);checkNotNull(mc.singleplayerServer).saveEverything(true,false,false) } }
                615->{ println("ARCHITECT COMMERCE CLIENT PASSED: real table Save and Creative Build, full editable world, five native world views and table graph/floorplan verified");mc.stop() }
                else->return false
            }
        } catch(error:Throwable) {
            failed=true;System.err.println("ARCHITECT COMMERCE CLIENT FAILED at tick $ticks: ${error.message}")
            error.printStackTrace();screenshot(mc,"failure-$ticks");mc.stop()
            throw IllegalStateException("Commerce table acceptance failed",error)
        }
        return true
    }
}
