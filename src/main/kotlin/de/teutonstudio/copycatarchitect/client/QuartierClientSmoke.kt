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

/** Builds the complete supplied graph through the real table's Save and Creative Build buttons. */
@EventBusSubscriber(modid=Architect.ID,bus=EventBusSubscriber.Bus.GAME,value=[Dist.CLIENT])
object QuartierClientSmoke {
    private data class Fixture(val origin:P,val table:P,val buildingKey:String,val placements:Int,val copycatBlocks:Int,val nativeBlocks:Int,
        val clearCells:Int,val materials:Map<String,Int>,val forms:Int,val modules:Int,val graphNodes:Int,
        val sourceSha256:String,val tableBuildingId:String="",val owner:String="")
    private val enabled get()=java.lang.Boolean.getBoolean("architect.quartierSmoke")
    private val verifyOnly get()=java.lang.Boolean.getBoolean("architect.quartierVerify")
    private val packagePath:Path get()=Path.of(checkNotNull(System.getProperty("architect.quartierSource")))
    private var frames=0L
    private var lastFrame=-1L
    private var ticks=0
    private var waiting=0
    private var failed=false
    private var draftReopens=0
    private var draftReopenWaiting=0
    private var draftReopening=false
    private var draftEditor:BuildingScreen?=null
    private var creativeMessageBefore=""
    private var pending:CompletableFuture<Unit>?=null
    private lateinit var fixture:Fixture
    private lateinit var building:Building
    private lateinit var plan:Plan
    private lateinit var sourceLibrary:Library
    private lateinit var sourceSha256:String

    @SubscribeEvent fun frame(event:RenderFrameEvent.Post) { if(enabled) frames++ }
    @SubscribeEvent fun onTick(event:ClientTickEvent.Post) {
        if(enabled && frames>lastFrame && tick()) lastFrame=frames+1
    }
    private fun server(mc:Minecraft,action:()->Unit) {
        val next=CompletableFuture<Unit>();pending=next
        checkNotNull(mc.singleplayerServer).execute {
            try { action();next.complete(Unit) } catch(error:Throwable) { next.completeExceptionally(error) }
        }
    }
    private fun player(mc:Minecraft):ServerPlayer=checkNotNull(mc.singleplayerServer).playerList.players.single()
    private fun metadata(mc:Minecraft):Path=checkNotNull(mc.singleplayerServer).getWorldPath(LevelResource.ROOT).resolve("quartier-test.json")
    private fun world(p:P)=BlockPos(fixture.origin.x+p.x,fixture.origin.y+p.y,fixture.origin.z+p.z)
    private fun tablePos()=BlockPos(fixture.table.x,fixture.table.y,fixture.table.z)
    private fun waitFor(ok:Boolean,message:String):Boolean {
        if(ok) { waiting=0;return false }
        check(++waiting<2400) { "$message; server: ${ClientState.snapshot.message}" };ticks--;return true
    }
    private fun click(mc:Minecraft,key:String) {
        val editor=mc.screen as? BuildingScreen ?: error("Real architect table editor missing")
        val button=editor.children().filterIsInstance<Button>().single { it.visible && it.active && it.message.string==ui(key) }
        if(key=="build") check(button is PrinterEdgeTab) { "Build navigation must use the real table edge tab" }
        val x=button.x+button.width/2.0;val y=button.y+button.height/2.0
        check(editor.mouseClicked(x,y,0)) { "Table button $key did not accept click" };editor.mouseReleased(x,y,0)
        if(key=="build") check(editor.tab==6 && !editor.graphMode)
    }
    /** A closed or replaced table screen must recover the unsaved source draft before Save. */
    private fun draftReady(mc:Minecraft):Boolean {
        val editor=mc.screen as? BuildingScreen
        if(editor==null || ClientState.snapshot.table!=fixture.table) {
            if(!draftReopening) {
                check(++draftReopens<=3) { "The real table screen closed more than three times before saving the Quartier draft" }
                draftReopening=true;draftReopenWaiting=0
                println("ARCHITECT QUARTIER DRAFT TABLE REOPEN: attempt $draftReopens at stage $ticks")
                server(mc) {
                    val p=player(mc)
                    p.teleportTo(p.serverLevel(),fixture.table.x+0.5,fixture.table.y+1.0,fixture.table.z+3.5,180f,0f)
                    Network.open(p,"building",tablePos())
                }
            }
            check(++draftReopenWaiting<2400) { "The real table did not reopen for the unsaved Quartier draft; server: ${ClientState.snapshot.message}" }
            ticks--;return false
        }
        draftReopening=false;draftReopenWaiting=0
        if(editor!==draftEditor || editor.building!=building) {
            check(ClientState.snapshot.library.buildings.values.none { it.id==building.id }) { "Quartier must still be unsaved before its first real Save click" }
            editor.building=building;editor.openBuildingGraph();editor.kilaBuildingGraph?.fit()
            check(Json.encode(editor.building).length<=180000)
            draftEditor=editor
            // Keep this stage pending until native widgets have actually rendered after the draft assignment.
            ticks--;return false
        }
        return true
    }
    private fun screenshot(mc:Minecraft,name:String) {
        Screenshot.grab(mc.gameDirectory,"architect-quartier-$name.png",mc.mainRenderTarget) { println(it.string) }
    }
    private fun camera(mc:Minecraft,x:Double,y:Double,z:Double,tx:Double,ty:Double,tz:Double,fov:Int=55) {
        mc.options.fov().set(fov)
        server(mc) {
            val p=player(mc);val dx=tx-x;val dy=ty-(y+p.eyeHeight);val dz=tz-z
            p.abilities.flying=true;p.onUpdateAbilities()
            p.teleportTo(p.serverLevel(),fixture.origin.x+x,fixture.origin.y+y,fixture.origin.z+z,
                Math.toDegrees(atan2(-dx,dz)).toFloat(),-Math.toDegrees(atan2(dy,sqrt(dx*dx+dz*dz))).toFloat())
        }
    }
    private fun readDesign() {
        val bytes=Files.readAllBytes(packagePath.resolve("library.json"))
        sourceSha256=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        sourceLibrary=Json.decode<Library>(String(bytes,Charsets.UTF_8))
        sourceLibrary.forms.replaceAll { _,form -> Geometry.validate(form) }
        sourceLibrary.modules.values.forEach { it.validate() }
        building=sourceLibrary.buildings.values.single()
        plan=Generator.generate(building,sourceLibrary.forms,sourceLibrary.modules)
        check(sourceLibrary.forms.size==26 && sourceLibrary.modules.size==3 && building.graph!!.nodes.size==89)
        check(plan.placements.size==7733 && plan.clear.size==6619) { "Expected the complete Quartier graph, including all four roofs" }
        check(plan.warnings.isEmpty())
        check(plan.placements.count { it.block.isEmpty() }==7565 && plan.placements.count { it.block.isNotEmpty() }==168)
        check(plan.placements.filter { it.block.isNotEmpty() }.groupingBy { it.block }.eachCount()==mapOf("minecraft:stone" to 93,"minecraft:stone_bricks" to 75))
        println("ARCHITECT QUARTIER FULL SOURCE LOADED: ${building.graph!!.nodes.size} nodes, ${sourceLibrary.forms.size} forms, ${sourceLibrary.modules.size} modules, ${plan.placements.size} positions (7565 Copycats + 168 native paving); source SHA-256=$sourceSha256")
    }
    private fun prepare(mc:Minecraft) {
        readDesign()
        val p=player(mc);val level=p.serverLevel();val server=checkNotNull(mc.singleplayerServer)
        check(server.getWorldPath(LevelResource.ROOT).normalize().fileName.toString()=="Quartier de l Horloge") { "Quartier setup requires its dedicated copied world" }
        check(!Files.exists(metadata(mc))) { "Dedicated Quartier world is already initialized; use -PquartierVerify to inspect it" }
        p.gameMode.changeGameModeForPlayer(GameType.CREATIVE)
        level.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,server)
        level.gameRules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false,server)
        level.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server)
        level.gameRules.getRule(GameRules.RULE_DOBLOCKDROPS).set(false,server)
        level.gameRules.getRule(GameRules.RULE_DOENTITYDROPS).set(false,server)
        level.dayTime=6000;level.setWeatherParameters(0,0,false,false)
        for(cx in 14..20) for(cz in 14..20) level.getChunk(cx,cz)
        val ground=(240..304 step 8).flatMap { x -> (236..312 step 8).map { z -> level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z) } }.max()
        val origin=P(256,ground+1,256);val tableLocal=P(12,0,-7)
        building=building.copy(graph=building.graph!!.copy(projection=BuildingProjection(tableLocal)))
        fixture=Fixture(origin,origin+tableLocal,"${building.id}@1",plan.placements.size,
            plan.placements.count { it.block.isEmpty() },plan.placements.count { it.block.isNotEmpty() },plan.clear.size,plan.materials,
            sourceLibrary.forms.size,sourceLibrary.modules.size,building.graph!!.nodes.size,sourceSha256,owner=p.uuid.toString())
        p.teleportTo(level,fixture.table.x+0.5,fixture.table.y+1.0,fixture.table.z+3.5,180f,0f)
        p.abilities.flying=true;p.onUpdateAbilities()
        // Only the dedicated copy's new construction site is prepared. The house itself is built by the table command below.
        for(x in -16..46) for(z in -20..56) {
            for(y in 0..48) level.setBlock(world(P(x,y,z)),Blocks.AIR.defaultBlockState(),2)
            val path=z< -8 || x>31
            level.setBlock(world(P(x,-1,z)),(if(path) Blocks.SMOOTH_STONE else Blocks.GRASS_BLOCK).defaultBlockState(),2)
            level.setBlock(world(P(x,-2,z)),Blocks.DIRT.defaultBlockState(),2)
        }
        level.getEntities(null,AABB(origin.x-16.0,origin.y.toDouble(),origin.z-20.0,origin.x+47.0,origin.y+49.0,origin.z+57.0)).filter { it!==p }.forEach { it.discard() }
        level.setBlock(tablePos(),Architect.TABLE.get().defaultBlockState(),3)
        level.setDefaultSpawnPos(world(P(12,0,-10)),0f);p.setRespawnPosition(level.dimension(),world(P(12,0,-10)),0f,true,false)
        p.inventory.clearContent();p.inventory.setItem(0,ItemStack(Architect.TABLE_ITEM.get()));p.inventory.setItem(1,ItemStack(Architect.GUN.get()))
        // Only forms and modules are preloaded. The building itself must arrive through the actual Save button.
        val library=Library().apply { seed();forms.putAll(sourceLibrary.forms);modules.putAll(sourceLibrary.modules) }
        check(library.buildings.values.none { it.id==building.id })
        library.copy(buildings=(library.buildings+(fixture.buildingKey to building)).toMutableMap()).validateStorageSize()
        check(Json.encode(building).length<=180000) { "Complete building exceeds the Creative Build document limit" }
        CommandTransport.encode(Json.encode(Command("saveBuilding",Json.encode(building))))
        val data=ArchitectData.get(level);data.db.libraries[p.uuid.toString()]=library;data.setDirty()
        println("ARCHITECT QUARTIER SITE READY: origin=$origin table=${fixture.table}; no Quartier blocks placed and no Quartier building saved yet")
    }
    private fun verifyServer(mc:Minecraft) {
        val p=player(mc);val level=p.serverLevel();val data=ArchitectData.get(level);val library=data.library(p.uuid)
        val saved=library.buildings.getValue(fixture.buildingKey)
        check(saved==building && sourceLibrary.forms.all { (key,value) -> library.forms[key]==value } &&
            sourceLibrary.modules.all { (key,value) -> library.modules[key]==value }) { "Saved graph/form/module library differs" }
        check(Generator.generate(saved,library.forms,library.modules)==plan) { "Authoritative saved graph changed compiled plan" }
        for(placement in plan.placements) {
            level.getChunk(world(placement.pos))
            check(PlacementService.matches(level,world(placement.pos),placement)) { "Actual quartier differs at ${placement.pos}" }
        }
        check(plan.clear.all { level.getBlockState(world(it)).isAir }) { "Quartier interior, stairs or entrance is obstructed" }
        val table=level.getBlockEntity(tablePos()) as? TableEntity ?: error("Placed architect table missing")
        val record=TableBuildings.current(level,table) ?: error("Architect table is not bound to the built quartier")
        check(fixture.tableBuildingId.isEmpty() || record.id==fixture.tableBuildingId) { "Reload changed the table's persisted building identity" }
        check(table.buildingId==record.id && record.id.isNotEmpty() && record.attached)
        check(record.owner==p.uuid.toString() && record.table==fixture.table && record.origin==fixture.origin && record.turn==0)
        val copycats=plan.placements.filter { it.block.isEmpty() }
        check(record.building==building && record.blocks.size==copycats.size)
        check(record.blocks.map { it.pos }.toSet()==copycats.map { fixture.origin+it.pos }.toSet()) { "Table ledger positions differ from the compiled Copycat positions" }
        check(record.blocks.all { it.token.isNotEmpty() && TableBuildings.owned(level,record,it) }) { "Table ownership ledger does not cover every real block" }
        check(building.graph!!.projection.origin(fixture.table)==fixture.origin)
        fixture=fixture.copy(tableBuildingId=record.id,owner=p.uuid.toString())
        check(fixture.placements==plan.placements.size && fixture.copycatBlocks==copycats.size && fixture.nativeBlocks==plan.placements.size-copycats.size)
        check(fixture.clearCells==plan.clear.size && fixture.materials==plan.materials && fixture.sourceSha256==sourceSha256)
        check(fixture.forms==sourceLibrary.forms.size && fixture.modules==sourceLibrary.modules.size && fixture.graphNodes==building.graph!!.nodes.size)
        println("ARCHITECT QUARTIER SERVER VERIFIED: ${plan.placements.size} actual blocks, ${plan.clear.size} air cells; table=${record.id}, ${record.blocks.size} owned tokens; library=${Json.encode(library).length}/${Library.MAX_JSON_CHARS} characters")
    }
    private fun verifyClient(mc:Minecraft):Boolean {
        val level=checkNotNull(mc.level)
        return plan.placements.all { placement ->
            val pos=world(placement.pos)
            if(placement.block.isNotEmpty()) {
                val actual=level.getBlockState(pos);val expected=Economy.nativeState(placement.block)
                if(placement.block in LandscapeGeometry.fenceBlocks) actual.`is`(expected.block) else actual==expected
            } else {
                val be=level.getBlockEntity(pos) as? CompositeEntity
                be!=null && Geometry.resolve(be.parts,be.groups).visualKey(be.material,be.surfaces)==placement.visualKey()
            }
        }
    }
    private fun load(mc:Minecraft) {
        readDesign();fixture=Json.decode<Fixture>(Files.readString(metadata(mc)))
        building=ArchitectData.get(player(mc).serverLevel()).library(player(mc).uuid).buildings.getValue(fixture.buildingKey)
        verifyServer(mc)
    }
    private fun tick():Boolean {
        if(failed) return false
        val mc=Minecraft.getInstance()
        if(mc.level==null || mc.player==null || mc.singleplayerServer==null || mc.overlay!=null) { check(++waiting<3600) { "Quartier acceptance requires its dedicated QuickPlay world" };return false }
        try {
            pending?.let { if(!it.isDone) return false;it.join();pending=null }
            ticks++
            if(verifyOnly) {
                when(ticks) {
                    1 -> { configure(mc);server(mc) { load(mc) } }
                    2 -> { mc.setScreen(null);mc.options.hideGui=true;camera(mc,-37.0,24.0,-44.0,12.0,16.0,15.0) }
                    80 -> { if(waitFor(verifyClient(mc),"Reloaded client did not receive quartier geometry")) return false;screenshot(mc,"reloaded-world") }
                    90 -> { mc.options.hideGui=false;server(mc) { val p=player(mc);p.teleportTo(p.serverLevel(),fixture.table.x+0.5,fixture.table.y+1.0,fixture.table.z+3.5,180f,0f);Network.open(p,"building",tablePos()) } }
                    110 -> { if(waitFor(mc.screen is BuildingScreen,"Reloaded architect table did not open")) return false;check((mc.screen as BuildingScreen).building==building);(mc.screen as BuildingScreen).openBuildingGraph();(mc.screen as BuildingScreen).kilaBuildingGraph?.fit() }
                    120 -> screenshot(mc,"reloaded-table")
                    130 -> { println("ARCHITECT QUARTIER RELOAD PASSED: exact world geometry/materials, clear interiors, saved graph/form library and complete real architect-table ownership ledger survived fresh load");mc.stop() }
                    else -> return false
                }
                return true
            }
            when(ticks) {
                1 -> { configure(mc);server(mc) { prepare(mc) } }
                2 -> server(mc) { Network.open(player(mc),"building",tablePos()) }
                20 -> {
                    if(waitFor(mc.screen is BuildingScreen && ClientState.snapshot.table==fixture.table,"Real placed architect table did not open")) return false
                    check(ClientState.snapshot.creative && ClientState.snapshot.library.buildings.values.none { it.id==building.id })
                    check(ClientState.snapshot.library.forms.keys.containsAll(sourceLibrary.forms.keys) && ClientState.snapshot.library.modules.keys.containsAll(sourceLibrary.modules.keys))
                }
                30 -> {
                    if(!draftReady(mc)) return true
                }
                35 -> { if(!draftReady(mc)) return true;click(mc,"save") }
                45 -> {
                    if(waitFor(ClientState.snapshot.library.buildings.containsKey(fixture.buildingKey),"Graph was not saved by the real table Save button")) return false
                    building=ClientState.snapshot.library.buildings.getValue(fixture.buildingKey)
                    check(building.version==1 && building.graph!!.nodes.size==89)
                    println("ARCHITECT QUARTIER GRAPH NETWORK SAVED: ${fixture.buildingKey}, ${building.graph!!.nodes.size} nodes")
                }
                // Reopen from the authoritative saved library so the editor derives its origin from this graph's table projection.
                46 -> server(mc) { Network.open(player(mc),"building",tablePos()) }
                48 -> { if(waitFor(mc.screen is BuildingScreen && (mc.screen as BuildingScreen).building==building,"Saved quartier did not reload in the real table")) return false;(mc.screen as BuildingScreen).openBuildingGraph();(mc.screen as BuildingScreen).kilaBuildingGraph?.fit() }
                50 -> screenshot(mc,"table-graph")
                55 -> click(mc,"build")
                80 -> screenshot(mc,"table-build")
                83 -> server(mc) {
                    val p=player(mc);val origin=fixture.origin
                    val entities=p.serverLevel().getEntities(null,AABB(origin.x-16.0,origin.y.toDouble(),origin.z-20.0,
                        origin.x+47.0,origin.y+49.0,origin.z+57.0)).filter { it!==p }
                    entities.forEach { it.discard() }
                    println("ARCHITECT QUARTIER PRE-BUILD SITE CLEAR: removed ${entities.size} entities from the dedicated construction area; player retained")
                }
                85 -> { creativeMessageBefore=ClientState.snapshot.message;click(mc,"creative_build") }
                95 -> {
                    val reply=ClientState.snapshot.message
                    check(reply==creativeMessageBefore || reply.startsWith("Kreativ gebaut:")) { "The real table Creative Build was rejected: $reply" }
                    if(waitFor(reply.startsWith("Kreativ gebaut:"),"The real table Creative Build did not finish")) return false
                    server(mc) { verifyServer(mc);Files.writeString(metadata(mc),Json.encode(fixture));checkNotNull(mc.singleplayerServer).saveEverything(true,false,false) }
                }
                110 -> { mc.setScreen(null);mc.options.hideGui=true;camera(mc,-37.0,24.0,-44.0,12.0,16.0,15.0) }
                170 -> { if(waitFor(verifyClient(mc),"Client did not receive all real quartier geometry")) return false;screenshot(mc,"exterior-front") }
                180 -> camera(mc,57.0,28.0,62.0,13.0,13.0,19.0)
                240 -> screenshot(mc,"exterior-rear")
                250 -> camera(mc,13.0,2.1,44.0,13.0,8.5,24.5,76)
                310 -> screenshot(mc,"courtyard")
                320 -> camera(mc,7.5,1.5,2.4,7.5,6.4,19.0,78)
                380 -> screenshot(mc,"interior-hall")
                390 -> camera(mc,7.5,7.2,21.0,7.5,4.0,5.5,78)
                450 -> screenshot(mc,"interior-gallery")
                460 -> camera(mc,24.0,13.1,0.8,20.0,16.0,4.6,78)
                520 -> screenshot(mc,"interior-tower-stairs")
                530 -> { mc.options.hideGui=false;server(mc) { val p=player(mc);p.teleportTo(p.serverLevel(),fixture.table.x+0.5,fixture.table.y+1.0,fixture.table.z+3.5,180f,0f);Network.open(p,"building",tablePos()) } }
                550 -> { if(waitFor(mc.screen is BuildingScreen,"Bound table did not reopen")) return false;val editor=mc.screen as BuildingScreen;check(editor.building==building);editor.openBuildingGraph();editor.kilaBuildingGraph?.fit() }
                570 -> screenshot(mc,"table-bound-graph")
                580 -> { val editor=mc.screen as BuildingScreen;editor.selectBuildingNode("ground");editor.openFloorPlan() }
                610 -> screenshot(mc,"table-floorplan-ground")
                620 -> { val editor=mc.screen as BuildingScreen;editor.selectBuildingNode("upper");editor.openFloorPlan() }
                650 -> screenshot(mc,"table-floorplan-gallery")
                660 -> { mc.setScreen(null);server(mc) { verifyServer(mc);Files.writeString(metadata(mc),Json.encode(fixture));checkNotNull(mc.singleplayerServer).saveEverything(true,false,false) } }
                670 -> { println("ARCHITECT QUARTIER CLIENT PASSED: complete 89-node graph saved as @1 by the real table Save button, actual UI Creative Build, ${plan.placements.size} world blocks, ${plan.clear.size} clear cells, complete table ownership, six world views and real table graph/build/two-floorplan screenshots");mc.stop() }
                else -> return false
            }
        } catch(error:Throwable) {
            failed=true;System.err.println("ARCHITECT QUARTIER CLIENT FAILED at tick $ticks: ${error.message}");error.printStackTrace();screenshot(mc,"failure-$ticks");mc.stop();throw IllegalStateException("Quartier client acceptance failed",error)
        }
        return true
    }
    private fun configure(mc:Minecraft) {
        mc.options.guiScale().set(2);mc.options.fov().set(55);mc.options.fovEffectScale().set(0.0);mc.options.gamma().set(1.0)
        mc.options.renderDistance().set(12);mc.options.hideGui=false;mc.resizeDisplay()
    }
}
