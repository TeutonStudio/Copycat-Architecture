package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.Architect
import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.Button
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.storage.LevelResource
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.math.atan2
import kotlin.math.sqrt

/** Creates a real editable demonstration world, then verifies it again after a fresh load. */
object TBuildingClientSmoke {
    private data class Fixture(val origin:P,val table:P,val buildingKey:String,val placements:Int,val windows:Int,val materials:Map<String,Int>)
    private var ticks=0
    private var waiting=0
    private var failed=false
    private var savedVersion=0
    private var pending:CompletableFuture<Unit>?=null
    private lateinit var fixture:Fixture
    private lateinit var building:Building
    private val plan by lazy { TBuildingExample.plan() }
    private val verifyOnly get()=java.lang.Boolean.getBoolean("architect.tBuildingVerify")

    private fun server(mc:Minecraft,action:()->Unit) {
        val next=CompletableFuture<Unit>();pending=next
        checkNotNull(mc.singleplayerServer).execute {
            try { action();next.complete(Unit) } catch(error:Throwable) { next.completeExceptionally(error) }
        }
    }
    private fun player(mc:Minecraft):ServerPlayer=checkNotNull(mc.singleplayerServer).playerList.players.single()
    private fun metadata(mc:Minecraft):Path=checkNotNull(mc.singleplayerServer).getWorldPath(LevelResource.ROOT).resolve("t-building-test.json")
    private fun world(local:P)=BlockPos(fixture.origin.x+local.x,fixture.origin.y+local.y,fixture.origin.z+local.z)
    private fun camera(mc:Minecraft,x:Double,y:Double,z:Double,targetX:Double=7.5,targetY:Double=9.0,targetZ:Double=7.5) {
        server(mc) {
            val p=player(mc);val dx=targetX-x;val dy=targetY-y;val dz=targetZ-z
            p.abilities.flying=true;p.onUpdateAbilities()
            p.teleportTo(p.serverLevel(),fixture.origin.x+x,fixture.origin.y+y,fixture.origin.z+z,
                Math.toDegrees(atan2(-dx,dz)).toFloat(),-Math.toDegrees(atan2(dy,sqrt(dx*dx+dz*dz))).toFloat())
        }
    }
    private fun screenshot(mc:Minecraft,name:String) {
        Screenshot.grab(mc.gameDirectory,"architect-t-building-$name.png",mc.mainRenderTarget) { println(it.string) }
    }
    private fun create(mc:Minecraft) {
        val p=player(mc);val level=p.serverLevel();val server=checkNotNull(mc.singleplayerServer)
        p.gameMode.changeGameModeForPlayer(GameType.CREATIVE)
        p.abilities.flying=true;p.onUpdateAbilities()
        level.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,server)
        level.gameRules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false,server)
        level.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server)
        level.dayTime=6000;level.setWeatherParameters(0,0,false,false)
        // This is a dedicated copy of the smoke-world template. Its source is never changed.
        for(cx in 2..6) for(cz in 2..6) level.getChunk(cx,cz)
        val ground=(44..84 step 4).flatMap { x -> (44..84 step 4).map { z -> level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z) } }.max()
        val origin=P(56,ground+1,56);val tableLocal=P(7,0,19)
        building=TBuildingExample.building.copy(graph=checkNotNull(TBuildingExample.building.graph).copy(projection=BuildingProjection(tableLocal)))
        val key="${building.id}@${building.version}"
        fixture=Fixture(origin,origin+tableLocal,key,plan.placements.size,
            plan.placements.count { it.surfaceItems().containsKey("minecraft:glass") },plan.materials)
        p.teleportTo(level,origin.x+7.5,origin.y+25.0,origin.z+34.0,180f,25f)
        val top=plan.placements.maxOf { it.pos.y }+7
        for(x in -8..23) for(z in -8..25) {
            for(y in 0..top) level.setBlock(world(P(x,y,z)),Blocks.AIR.defaultBlockState(),3)
            val border=x in listOf(-8,23) || z in listOf(-8,25)
            val path=z>=15 && x in 5..9
            val groundBlock=when { border->Blocks.STONE_BRICKS;path->Blocks.SMOOTH_STONE;else->Blocks.GRASS_BLOCK }
            level.setBlock(world(P(x,-1,z)),groundBlock.defaultBlockState(),3)
            level.setBlock(world(P(x,-2,z)),Blocks.DIRT.defaultBlockState(),3)
        }
        for(placement in plan.placements) check(PlacementService.place(level,world(placement.pos),placement,p.inventory,p,"t-building-world",creative=true))
        level.setBlock(BlockPos(fixture.table.x,fixture.table.y,fixture.table.z),Architect.TABLE.get().defaultBlockState(),3)
        level.setDefaultSpawnPos(world(P(7,0,22)),180f)
        p.setRespawnPosition(level.dimension(),world(P(7,0,22)),180f,true,false)
        p.inventory.setItem(0,ItemStack(Architect.TABLE_ITEM.get()))
        p.inventory.setItem(1,ItemStack(Architect.GUN.get()))
        val data=ArchitectData.get(level);val library=data.library(p.uuid)
        library.forms.putAll(TBuildingExample.forms);library.modules.putAll(TBuildingExample.modules);library.buildings[key]=building
        data.setDirty()
        Files.writeString(metadata(mc),Json.encode(fixture))
        verifyServer(mc)
        server.saveEverything(true,false,false)
        println("ARCHITECT T BUILDING CREATED: $key at $origin, ${plan.placements.size} actual world blocks, ${fixture.windows} glazed cells, ${plan.materials.size} materials; table=${fixture.table}")
    }
    private fun load(mc:Minecraft) {
        fixture=Json.decode<Fixture>(Files.readString(metadata(mc)))
        building=ArchitectData.get(player(mc).serverLevel()).library(player(mc).uuid).buildings.getValue(fixture.buildingKey)
        verifyServer(mc)
    }
    private fun verifyServer(mc:Minecraft) {
        val p=player(mc);val level=p.serverLevel()
        for(placement in plan.placements) {
            level.getChunk(world(placement.pos))
            check(PlacementService.matches(level,world(placement.pos),placement)) { "Actual saved T-house differs at ${placement.pos}" }
        }
        check(plan.clear.all { level.getBlockState(world(it)).isAir }) { "T-house interior or entrance is obstructed" }
        val library=ArchitectData.get(level).library(p.uuid)
        check(library.forms.keys.containsAll(TBuildingExample.forms.keys) && library.buildings.getValue(fixture.buildingKey)==building)
        check(level.getBlockState(BlockPos(fixture.table.x,fixture.table.y,fixture.table.z)).`is`(Architect.TABLE.get()))
        check(building.graph!!.projection.origin(fixture.table)==fixture.origin)
    }
    private fun verifyClient(mc:Minecraft):Boolean {
        val level=checkNotNull(mc.level)
        return plan.placements.all { placement ->
            val be=level.getBlockEntity(world(placement.pos)) as? CompositeEntity
            be!=null && Geometry.resolve(be.parts,be.groups).visualKey(be.material,be.surfaces)==placement.visualKey()
        }
    }
    fun tick():Boolean {
        if(failed) return false
        val mc=Minecraft.getInstance()
        if(mc.level==null || mc.player==null || mc.singleplayerServer==null || mc.overlay!=null) {
            check(++waiting<2400) { "T-building acceptance requires its dedicated quick-play world" };return false
        }
        try {
            pending?.let { if(!it.isDone) return false;it.join();pending=null }
            ticks++
            when(ticks) {
                1 -> {
                    mc.options.guiScale().set(2);mc.options.fov().set(50);mc.options.fovEffectScale().set(0.0)
                    mc.resizeDisplay();mc.options.hideGui=true
                    server(mc) { if(verifyOnly) load(mc) else create(mc) }
                }
                2 -> { mc.setScreen(null);camera(mc,33.0,29.0,37.0) }
                75 -> {
                    if(!verifyClient(mc)) { check(++waiting<2400) { "Client did not receive all T-house geometry" };ticks--;return false }
                    if(verifyOnly) {
                        screenshot(mc,"reloaded-world")
                        println("ARCHITECT T BUILDING RELOAD PASSED: all ${plan.placements.size} real blocks, exact materials/geometry, saved library, table binding and clear interiors survived a fresh client/server load")
                        mc.options.hideGui=false;mc.stop()
                    }
                }
                100 -> screenshot(mc,"world-front")
                110 -> camera(mc,-20.0,26.0,-25.0)
                165 -> screenshot(mc,"world-rear")
                175 -> camera(mc,7.5,46.0,7.6,7.5,0.0,7.5)
                230 -> screenshot(mc,"world-top")
                240 -> camera(mc,27.0,13.5,29.0,7.5,9.0,12.0)
                295 -> screenshot(mc,"world-facade")
                305 -> {
                    mc.options.hideGui=false
                    server(mc) {
                        verifyServer(mc)
                        val p=player(mc);p.teleportTo(p.serverLevel(),fixture.table.x+0.5,fixture.table.y+1.0,fixture.table.z+3.5,180f,0f)
                        Network.open(p,"building",BlockPos(fixture.table.x,fixture.table.y,fixture.table.z))
                    }
                }
                330 -> {
                    val editor=mc.screen as? BuildingScreen ?: error("The real placed architect table must open its saved T-building library")
                    check(editor.building.id==building.id && editor.building.graph!=null)
                    editor.openBuildingGraph();checkNotNull(editor.kilaBuildingGraph).fit()
                }
                335 -> {
                    val editor=mc.screen as BuildingScreen
                    savedVersion=ClientState.snapshot.library.buildings.values.filter { it.id==building.id }.maxOf { it.version }
                    val command=Json.encode(Command("saveBuilding",Json.encode(editor.building)))
                    check(command.length>CommandTransport.MAX_WIRE_CHARS && CommandTransport.encode(command).length<=CommandTransport.MAX_WIRE_CHARS)
                    val save=editor.children().filterIsInstance<Button>().single { it.visible && it.message.string==ui("save") }
                    check(editor.mouseClicked(save.x+save.width/2.0,save.y+save.height/2.0,0))
                    editor.mouseReleased(save.x+save.width/2.0,save.y+save.height/2.0,0)
                }
                345 -> {
                    val saved=ClientState.snapshot.library.buildings.values.filter { it.id==building.id }.maxBy { it.version }
                    if(saved.version<=savedVersion) {
                        check(++waiting<2400) { "The real architect table did not save its large graph: ${ClientState.snapshot.message}" }
                        ticks--;return false
                    }
                    building=saved;fixture=fixture.copy(buildingKey="${saved.id}@${saved.version}")
                    server(mc) {
                        verifyServer(mc);Files.writeString(metadata(mc),Json.encode(fixture))
                        checkNotNull(mc.singleplayerServer).saveEverything(true,false,false)
                    }
                    println("ARCHITECT T BUILDING NETWORK SAVE PASSED: real table Save sent a compressed command over 30000 JSON characters and received authoritative ${fixture.buildingKey}")
                }
                350 -> screenshot(mc,"table-graph")
                360 -> (mc.screen as BuildingScreen).openFloorPlan()
                385 -> screenshot(mc,"table-footprint")
                395 -> { mc.setScreen(null);mc.options.hideGui=true;camera(mc,7.5,2.6,23.0,7.5,7.0,12.0) }
                445 -> screenshot(mc,"entrance")
                455 -> {
                    mc.options.hideGui=false
                    server(mc) { verifyServer(mc);checkNotNull(mc.singleplayerServer).saveEverything(true,false,false) }
                }
                465 -> {
                    println("ARCHITECT T BUILDING CLIENT PASSED: actual three-storey T-shaped world building with eight materials, framed glass windows, raised facade patterns, real table library/graph and T floor plan; compressed network save, front/rear/top/facade/entrance screenshots, exact client block geometry and saved world verified")
                    mc.stop()
                }
                else -> return false
            }
        } catch(error:Throwable) {
            failed=true;System.err.println("ARCHITECT T BUILDING CLIENT FAILED at tick $ticks: ${error.message}")
            error.printStackTrace();screenshot(mc,"failure-$ticks");mc.stop()
            throw IllegalStateException("T-building client validation failed",error)
        }
        return true
    }
}
