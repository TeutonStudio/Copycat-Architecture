package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.Architect
import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.phys.AABB
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RenderFrameEvent
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.math.atan2
import kotlin.math.sqrt

/** Explicitly opted-in work on a copy of the player's Vorlagen world, through its existing table. */
@EventBusSubscriber(modid=Architect.ID,value=[Dist.CLIENT])
object VorlagenDepotClientSmoke {
    private const val RECORD="dffc2b0a-6e0c-49e8-8cff-2dbccf45ee42"
    private const val BUILDING="7ffd9d7d-d471-42b8-abe2-2b11589e3a77"
    private val table=P(-219,71,138)
    private val origin=P(-214,73,143)
    private val mode get()=System.getProperty("architect.vorlagenDepotMode","")
    private val enabled get()=mode in setOf("inspect","apply","verify")
    private val output:Path get()=Path.of(System.getProperty("architect.vorlagenDepotOutput"))
    private data class Fixture(val recordId:String,val owner:String,val baselineLibrary:String,val formerOwned:List<P>,val building:Building)
    private data class PlayerView(val x:Double,val y:Double,val z:Double,val yaw:Float,val pitch:Float,val flying:Boolean)
    private var frames=0L
    private var lastFrame=-1L
    private var ticks=0
    private var waiting=0
    private var failed=false
    private var pending:CompletableFuture<Unit>?=null
    private lateinit var building:Building
    private lateinit var plan:Plan
    private lateinit var fixture:Fixture
    private lateinit var previousView:PlayerView
    private var previousDay=0L

    @SubscribeEvent fun frame(event:RenderFrameEvent.Post) { if(enabled) frames++ }
    @SubscribeEvent fun tick(event:ClientTickEvent.Post) { if(enabled && frames>lastFrame && advance()) lastFrame=frames+1 }
    private fun player(mc:Minecraft):ServerPlayer=checkNotNull(mc.singleplayerServer).playerList.players.single()
    private fun server(mc:Minecraft,action:()->Unit) {
        pending=CompletableFuture<Unit>().also { future -> checkNotNull(mc.singleplayerServer).execute {
            try { action();future.complete(Unit) } catch(error:Throwable) { future.completeExceptionally(error) }
        } }
    }
    private fun metadata(mc:Minecraft)=checkNotNull(mc.singleplayerServer).getWorldPath(LevelResource.ROOT).resolve("vorlagen-depot-fixture.json")
    private fun pos(p:P)=PlacementService.world(p)
    private fun record(mc:Minecraft):TableBuilding {
        val p=player(mc);val level=p.serverLevel()
        val entity=level.getBlockEntity(pos(table)) as? TableEntity ?: error("Original architect table is missing")
        val value=TableBuildings.current(level,entity) ?: error("Original table binding is missing")
        check(value.id==RECORD && value.owner==p.uuid.toString() && value.table==table && value.origin==origin && value.turn==0 && value.attached) { "Unexpected table, owner or placement; refusing to change this world" }
        check(value.building.id==BUILDING && value.building.name=="Lagerhalle") { "Original Lagerhalle identity changed" }
        return value
    }
    private fun prepare(mc:Minecraft) {
        val world=metadata(mc).parent.toAbsolutePath().normalize()
        val runtime=mc.gameDirectory.toPath().toAbsolutePath().normalize()
        check(world.fileName.toString()=="Vorlagen" && runtime.fileName.toString()=="vorlagen-depot-client") { "Only the isolated Vorlagen copy may be inspected or changed: world=$world runtime=$runtime" }
        Files.createDirectories(output)
        val p=player(mc);val level=p.serverLevel()
        check(level.dimension().location().toString()=="minecraft:overworld")
        previousView=PlayerView(p.x,p.y,p.z,p.yRot,p.xRot,p.abilities.flying);previousDay=level.dayTime
        val originalContext=output.resolve("before-world-context.json")
        if(Files.exists(originalContext)) {
            val context=com.google.gson.JsonParser.parseString(Files.readString(originalContext)).asJsonObject
            previousDay=context["dayTime"].asLong
            val position=context.getAsJsonArray("position");val rotation=context.getAsJsonArray("rotation")
            previousView=PlayerView(position[0].asDouble,position[1].asDouble,position[2].asDouble,rotation[0].asFloat,rotation[1].asFloat,context["flying"].asInt!=0)
        }
        // No terrain, inventory, gamerule, entity or library reset. Daylight and camera are restored before closing.
        level.dayTime=6000
        for(cx in -18..-12) for(cz in 5..11) level.getChunk(cx,cz)
        val current=record(mc);val library=ArchitectData.get(level).library(p.uuid)
        if(mode=="inspect") {
            building=current.relativeBuilding();plan=Generator.generate(building,library.forms,library.modules)
            val expected=BuildingProjection(table=P(),turn=current.turn).plan(plan,current.origin)
            val missing=expected.placements.filter { level.getBlockState(pos(it.pos)).isAir }
            val different=expected.placements.filter { !level.getBlockState(pos(it.pos)).isAir && !PlacementService.matches(level,pos(it.pos),it) }
            Files.writeString(output.resolve("before-building.json"),Json.encode(building))
            Files.writeString(output.resolve("baseline-library.json"),Json.encode(library))
            Files.writeString(output.resolve("inspect-report.json"),Json.encode(mapOf("record" to current.id,"owner" to current.owner,"table" to table,"origin" to origin,"ownedBlocks" to current.blocks.size,"validOwnedBlocks" to current.blocks.count { TableBuildings.owned(level,current,it) },"placements" to plan.placements.size,"clearCells" to plan.clear.size,"matchingWorldCells" to expected.placements.size-missing.size-different.size,"missingWorldCells" to missing.size,"differentWorldCells" to different.size,"missingSample" to missing.take(12).map { it.pos },"differentSample" to different.take(12).map { it.pos },"libraryCharacters" to Json.encode(library).length,"libraryLimit" to Library.MAX_JSON_CHARS,"warnings" to plan.warnings)))
            println("VORLAGEN DEPOT INSPECT READY: table=${current.id}, owner=${current.owner}, ${current.blocks.size} owned blocks, library=${Json.encode(library).length}/${Library.MAX_JSON_CHARS}")
        } else {
            building=Json.decode<Building>(Files.readString(output.resolve("after-building.json")))
            check(building.id==BUILDING && building.name=="Lagerhalle")
            check(building.graph!!.projection.origin(table)==origin && building.graph!!.projection.turn==0) { "Optimization must retain the original table projection" }
            check(Json.encode(building).length<=180000)
            plan=Generator.generate(building,library.forms,library.modules)
            check(plan.warnings.isEmpty()) { "Optimized graph has warnings: ${plan.warnings}" }
            if(mode=="apply") {
                check(!Files.exists(metadata(mc))) { "Optimization was already applied; use verification mode" }
                check(p.isCreative) { "The existing player must be in Creative mode" }
                fixture=Fixture(current.id,current.owner,Json.encode(library),current.blocks.filter { TableBuildings.owned(level,current,it) }.map { it.pos },building)
                // Clear only ordinary short grass directly underneath the planned new foundation legs.
                val grass=BuildingProjection(table=P(),turn=0).plan(plan,origin).placements
                    .filter { it.component=="garden" && level.getBlockState(pos(it.pos)).`is`(Blocks.SHORT_GRASS) }
                    .map { it.pos }
                check(grass.size<=16) { "Unexpected vegetation footprint; refusing site preparation" }
                grass.forEach { level.setBlock(pos(it),Blocks.AIR.defaultBlockState(),2) }
                if(grass.isNotEmpty()) Files.writeString(output.resolve("site-preparation.json"),Json.encode(mapOf("removedShortGrass" to grass)))
                preflight(mc,current)
            } else {
                fixture=Json.decode(Files.readString(metadata(mc)))
                check(fixture.building==building) { "Exported graph differs from the graph used for the saved optimization" }
                verifyServer(mc)
            }
        }
    }
    /** Mirror the production command's obstruction checks before any UI build click. */
    private fun preflight(mc:Minecraft,current:TableBuilding) {
        val p=player(mc);val level=p.serverLevel()
        val projected=BuildingProjection(table=P(),turn=0).plan(plan,origin)
        check(projected.placements.size+projected.clear.size<=65536)
        val targets=projected.placements.associateBy { it.pos }
        val owned=current.blocks.filter { TableBuildings.owned(level,current,it) }.associateBy { it.pos }
        val removed=owned.keys.filter { target -> targets[target]?.let { !PlacementService.matches(level,pos(target),it) } ?: true }.toSet()
        (targets.keys+projected.clear+current.blocks.map { it.pos }).forEach { check(level.hasChunkAt(pos(it)) && level.isInWorldBounds(pos(it))) { "Unloaded build cell $it" } }
        projected.placements.forEach { placement -> check(placement.pos in removed || level.getBlockState(pos(placement.pos)).isAir || PlacementService.matches(level,pos(placement.pos),placement)) { "Foreign block obstructs optimization at ${placement.pos}" } }
        projected.clear.forEach { check(it in removed || level.getBlockState(pos(it)).isAir) { "Foreign block obstructs optimized interior at $it" } }
        val additions=projected.placements.filter { it.pos in removed || !PlacementService.matches(level,pos(it.pos),it) }
        // Creatures standing on the old flat roof are moved onto nearby open ground before rebuilding.
        val occupants=(removed+additions.map { it.pos }).flatMap { target ->
            level.getEntities(p,AABB(pos(target))).filter { it.isAlive && !it.isSpectator }
        }.distinctBy { it.uuid }
        check(occupants.size<=32) { "Unexpected construction-site occupancy" }
        val relocations=occupants.mapIndexed { i,entity ->
            val before=mapOf("x" to entity.x,"y" to entity.y,"z" to entity.z)
            val x=-260-(i%4)*3;val z=124+(i/4)*3
            val y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)
            entity.teleportTo(x+0.5,y+0.1,z+0.5)
            mapOf("entity" to entity.uuid.toString(),"name" to entity.name.string,"from" to before,"to" to mapOf("x" to entity.x,"y" to entity.y,"z" to entity.z))
        }
        if(relocations.isNotEmpty()) Files.writeString(output.resolve("site-relocations.json"),Json.encode(relocations))
        (removed+additions.map { it.pos }).forEach { target -> check(level.getEntities(p,AABB(pos(target))).none { it.isAlive && !it.isSpectator }) { "An entity occupies changed cell $target; nothing was removed" } }
        println("VORLAGEN DEPOT PREFLIGHT PASSED: ${removed.size} old owned cells change, ${additions.size} additions; foreign blocks and entities preserved")
    }
    private fun verifyServer(mc:Minecraft) {
        val p=player(mc);val level=p.serverLevel();val current=record(mc)
        val library=ArchitectData.get(level).library(p.uuid)
        check(current.id==fixture.recordId && current.owner==fixture.owner)
        check(Json.encode(library)==fixture.baselineLibrary) { "A historical library entry or form changed" }
        check(current.relativeBuilding()==building) { "The table did not retain the optimized editable graph" }
        val projected=BuildingProjection(table=P(),turn=0).plan(plan,origin)
        projected.placements.forEach { check(PlacementService.matches(level,pos(it.pos),it)) { "Actual world differs at ${it.pos}" } }
        check(projected.clear.all { level.getBlockState(pos(it)).isAir }) { "Optimized interior contains an obstruction" }
        val targetSet=projected.placements.map { it.pos }.toSet()
        check(fixture.formerOwned.filter { it !in targetSet }.all { level.getBlockState(pos(it)).isAir }) { "A former owned block was left behind" }
        check(current.blocks.map { it.pos }.toSet().size==current.blocks.size && current.blocks.all { it.pos in targetSet && TableBuildings.owned(level,current,it) }) { "Optimized ownership ledger is inconsistent" }
        Files.writeString(output.resolve(if(mode=="verify") "reload-report.json" else "apply-report.json"),Json.encode(mapOf("passed" to true,"mode" to mode,"record" to current.id,"table" to table,"origin" to origin,"placements" to plan.placements.size,"clearCells" to plan.clear.size,"ownedBlocks" to current.blocks.size,"libraryUnchanged" to true,"building" to building)))
        println("VORLAGEN DEPOT SERVER VERIFIED: ${plan.placements.size} actual placements, ${plan.clear.size} clear cells, ${current.blocks.size} owned tokens, original table identity and complete library unchanged")
    }
    private fun waitFor(ok:Boolean,message:String):Boolean {
        if(ok) { waiting=0;return false }
        check(++waiting<1600) { "$message; server: ${ClientState.snapshot.message}" };ticks--;return true
    }
    private fun click(mc:Minecraft,key:String) {
        val screen=mc.screen as? BuildingScreen ?: error("Real architect table editor is missing")
        val button=screen.children().filterIsInstance<Button>().single { it.visible && it.active && it.message.string==ui(key) }
        val x=button.x+button.width/2.0;val y=button.y+button.height/2.0
        check(screen.mouseClicked(x,y,0));screen.mouseReleased(x,y,0)
    }
    private fun screenshot(mc:Minecraft,name:String) {
        if(name.contains("exterior") || name.contains("interior")) check(mc.screen==null)
        Screenshot.grab(mc.gameDirectory,"vorlagen-depot-$mode-$name.png",mc.mainRenderTarget) { println(it.string) }
    }
    private fun camera(mc:Minecraft,x:Double,y:Double,z:Double,tx:Double,ty:Double,tz:Double,fov:Int=80) {
        mc.setScreen(null);mc.options.hideGui=true;mc.options.fov().set(fov)
        server(mc) { val p=player(mc);val dx=tx-x;val dy=ty-y-p.eyeHeight;val dz=tz-z
            p.abilities.flying=true;p.onUpdateAbilities()
            p.teleportTo(p.serverLevel(),x,y,z,Math.toDegrees(atan2(-dx,dz)).toFloat(),-Math.toDegrees(atan2(dy,sqrt(dx*dx+dz*dz))).toFloat())
        }
    }
    private fun openTable(mc:Minecraft) {
        mc.setScreen(null);mc.options.hideGui=false
        server(mc) { val p=player(mc);p.teleportTo(p.serverLevel(),table.x+0.5,table.y+1.0,table.z+3.5,180f,0f);Network.open(p,"building",pos(table)) }
    }
    private fun finish(mc:Minecraft) {
        mc.setScreen(null);mc.options.hideGui=false
        server(mc) {
            val p=player(mc);p.serverLevel().dayTime=previousDay
            p.teleportTo(p.serverLevel(),previousView.x,previousView.y,previousView.z,previousView.yaw,previousView.pitch)
            p.abilities.flying=previousView.flying;p.onUpdateAbilities()
            if(mode!="inspect") verifyServer(mc)
            checkNotNull(mc.singleplayerServer).saveEverything(true,false,false)
        }
    }
    private fun advance():Boolean {
        if(failed) return false
        val mc=Minecraft.getInstance()
        if(mc.level==null || mc.player==null || mc.singleplayerServer==null || mc.overlay!=null) return false
        try {
            pending?.let { if(!it.isDone) return false;it.join();pending=null }
            if(mc.screen is PauseScreen) mc.setScreen(null)
            ticks++
            if(mode=="inspect") {
                when(ticks) {
                    1 -> { configure(mc);server(mc) { prepare(mc) } }
                    2 -> camera(mc,-220.0,95.0,108.0,-238.0,80.0,132.0,80)
                    70 -> screenshot(mc,"exterior")
                    80 -> camera(mc,-244.0,75.0,119.0,-231.0,82.0,147.0,88)
                    150 -> screenshot(mc,"interior")
                    160 -> openTable(mc)
                    180 -> { if(waitFor(mc.screen is BuildingScreen,"Original table failed to open")) return false;(mc.screen as BuildingScreen).openBuildingGraph();(mc.screen as BuildingScreen).kilaBuildingGraph?.fit() }
                    190 -> screenshot(mc,"table-graph")
                    200 -> finish(mc)
                    210 -> { println("VORLAGEN DEPOT INSPECTION PASSED; no building, library, entity or terrain changes");mc.stop() }
                    else -> return false
                }
                return true
            }
            if(mode=="apply" && ticks<=100) {
                when(ticks) {
                    1 -> { configure(mc);server(mc) { prepare(mc) } }
                    2 -> openTable(mc)
                    20 -> { if(waitFor(mc.screen is BuildingScreen,"Original table failed to open")) return false;val editor=mc.screen as BuildingScreen;check(editor.building.id==BUILDING);editor.building=building;editor.openBuildingGraph();editor.kilaBuildingGraph?.fit() }
                    30 -> screenshot(mc,"table-optimized-graph")
                    35 -> click(mc,"build")
                    45 -> screenshot(mc,"table-build")
                    50 -> click(mc,"creative_build")
                    65 -> { if(waitFor(ClientState.snapshot.message.startsWith("Kreativ gebaut:"),"Real table Creative Build did not succeed")) return false;server(mc) { verifyServer(mc);Files.writeString(metadata(mc),Json.encode(fixture));checkNotNull(mc.singleplayerServer).saveEverything(true,false,false) } }
                    80 -> openTable(mc)
                    95 -> { if(waitFor(mc.screen is BuildingScreen,"Updated table failed to reopen")) return false;check((mc.screen as BuildingScreen).building==building);(mc.screen as BuildingScreen).openBuildingGraph();(mc.screen as BuildingScreen).kilaBuildingGraph?.fit() }
                    100 -> screenshot(mc,"table-persisted-graph")
                    else -> return false
                }
                return true
            }
            val viewTick=if(mode=="verify") ticks else ticks-100
            when(viewTick) {
                1 -> if(mode=="verify") { configure(mc);server(mc) { prepare(mc) } }
                2 -> camera(mc,-220.0,95.0,108.0,-238.0,80.0,132.0,80)
                80 -> screenshot(mc,"exterior-front")
                90 -> camera(mc,-254.0,93.0,161.0,-238.0,80.0,132.0,80)
                160 -> screenshot(mc,"exterior-rear")
                170 -> camera(mc,-212.0,77.0,124.0,-229.0,78.0,130.0,85)
                240 -> screenshot(mc,"exterior-loading")
                250 -> camera(mc,-216.0,75.0,150.0,-227.0,76.0,143.0,75)
                320 -> screenshot(mc,"exterior-entrance")
                330 -> camera(mc,-244.0,75.0,119.0,-231.0,82.0,147.0,88)
                400 -> screenshot(mc,"interior-hall")
                410 -> camera(mc,-228.0,78.0,120.0,-245.0,78.0,144.0,82)
                480 -> screenshot(mc,"interior-reverse")
                490 -> openTable(mc)
                510 -> { if(waitFor(mc.screen is BuildingScreen,"Persisted table failed to open")) return false;check((mc.screen as BuildingScreen).building==building);(mc.screen as BuildingScreen).openBuildingGraph();(mc.screen as BuildingScreen).kilaBuildingGraph?.fit() }
                520 -> screenshot(mc,"table-reopened")
                530 -> (mc.screen as BuildingScreen).openFloorPlan()
                550 -> screenshot(mc,"table-floorplan")
                560 -> finish(mc)
                570 -> { println("VORLAGEN DEPOT ${mode.uppercase()} PASSED: actual architect-table rebuild, persisted original table identity, editable optimized graph, unchanged historical library and native Minecraft views");mc.stop() }
                else -> return false
            }
        } catch(error:Throwable) {
            failed=true;System.err.println("VORLAGEN DEPOT ${mode.uppercase()} FAILED at tick $ticks: ${error.message}");error.printStackTrace()
            screenshot(mc,"failure-$ticks");mc.stop();throw IllegalStateException("Vorlagen depot $mode failed",error)
        }
        return true
    }
    private fun configure(mc:Minecraft) {
        mc.options.guiScale().set(2);mc.options.fov().set(80);mc.options.fovEffectScale().set(0.0);mc.options.gamma().set(1.0)
        mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(12);mc.options.hideGui=false;mc.resizeDisplay()
    }
}
