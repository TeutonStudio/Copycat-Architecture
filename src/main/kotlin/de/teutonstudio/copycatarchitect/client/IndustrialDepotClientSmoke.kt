package de.teutonstudio.copycatarchitect.client

import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
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

/** Opt-in acceptance: imports the supplied graph over the real connection and builds at a real table. */
@EventBusSubscriber(modid=Architect.ID,value=[Dist.CLIENT])
object IndustrialDepotClientSmoke {
    private data class Fixture(val origin:P,val table:P,val buildingKey:String,val placements:Int,
        val clearCells:Int,val materials:Map<String,Int>,val tableBuildingId:String="",val owner:String="")
    private val enabled get()=java.lang.Boolean.getBoolean("architect.industrialDepotSmoke")
    private val verifyOnly get()=java.lang.Boolean.getBoolean("architect.industrialDepotVerify")
    private val packagePath:Path get()=Path.of(System.getProperty("architect.industrialDepotPackage"))
    private var frames=0L
    private var lastFrame=-1L
    private var ticks=0
    private var waiting=0
    private var failed=false
    private var formIndex=0
    private var awaitingForm=""
    private var pending:CompletableFuture<Unit>?=null
    private lateinit var fixture:Fixture
    private lateinit var building:Building
    private lateinit var plan:Plan
    private lateinit var forms:LinkedHashMap<String,Form>

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
    private fun metadata(mc:Minecraft):Path=checkNotNull(mc.singleplayerServer).getWorldPath(LevelResource.ROOT).resolve("industrial-depot-test.json")
    private fun world(p:P)=BlockPos(fixture.origin.x+p.x,fixture.origin.y+p.y,fixture.origin.z+p.z)
    private fun tablePos()=BlockPos(fixture.table.x,fixture.table.y,fixture.table.z)
    private fun waitFor(ok:Boolean,message:String):Boolean {
        if(ok) { waiting=0;return false }
        check(++waiting<2400) { "$message; server: ${ClientState.snapshot.message}" };ticks--;return true
    }
    private fun click(mc:Minecraft,key:String) {
        val editor=mc.screen as? BuildingScreen ?: error("Real architect table editor missing")
        val button=editor.children().filterIsInstance<Button>().single { it.visible && it.active && it.message.string==ui(key) }
        val x=button.x+button.width/2.0;val y=button.y+button.height/2.0
        check(editor.mouseClicked(x,y,0)) { "Table button $key did not accept click" };editor.mouseReleased(x,y,0)
    }
    private fun screenshot(mc:Minecraft,name:String) {
        if(listOf("exterior","interior","world").any(name::contains)) check(mc.screen==null) { "Refusing to capture a world view with an open screen: ${mc.screen?.javaClass?.simpleName}" }
        Screenshot.grab(mc.gameDirectory,"architect-industrial-depot-$name.png",mc.mainRenderTarget) { println(it.string) }
    }
    private fun camera(mc:Minecraft,x:Double,y:Double,z:Double,tx:Double,ty:Double,tz:Double,fov:Int=55) {
        mc.setScreen(null);mc.options.hideGui=true
        mc.options.fov().set(fov)
        server(mc) {
            val p=player(mc);val dx=tx-x;val dy=ty-(y+p.eyeHeight);val dz=tz-z
            p.abilities.flying=true;p.onUpdateAbilities()
            p.teleportTo(p.serverLevel(),fixture.origin.x+x,fixture.origin.y+y,fixture.origin.z+z,
                Math.toDegrees(atan2(-dx,dz)).toFloat(),-Math.toDegrees(atan2(dy,sqrt(dx*dx+dz*dz))).toFloat())
        }
    }
    private fun readDesign() {
        val root=JsonParser.parseString(Files.readString(packagePath.resolve("library.json"))).asJsonObject
        forms=Json.gson.fromJson(root.get("forms"),object:TypeToken<LinkedHashMap<String,Form>>(){}.type)
        forms.replaceAll { _,f -> Geometry.validate(f) }
        val buildings:Map<String,Building> =Json.gson.fromJson(root.get("buildings"),object:TypeToken<Map<String,Building>>(){}.type)
        building=buildings.values.single()
        plan=Generator.generate(building,forms,emptyMap())
        check(forms.size==14 && building.graph!!.nodes.size==69 && plan.placements.size==6662)
        check(plan.warnings.isEmpty())
    }
    private fun prepare(mc:Minecraft) {
        readDesign()
        val p=player(mc);val level=p.serverLevel();val server=checkNotNull(mc.singleplayerServer)
        check(metadata(mc).parent.normalize().fileName.toString()=="Lichtwerk Industrie-Depot") { "Refusing to prepare any world except the dedicated depot copy" }
        check(!Files.exists(metadata(mc))) { "Dedicated depot world is already initialized; use industrialDepotVerify to inspect it" }
        p.gameMode.changeGameModeForPlayer(GameType.CREATIVE)
        level.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,server)
        level.gameRules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false,server)
        level.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server)
        level.dayTime=6000;level.setWeatherParameters(0,0,false,false)
        for(cx in 14..20) for(cz in 14..19) level.getChunk(cx,cz)
        val ground=(244..316 step 8).flatMap { x -> (240..300 step 8).map { z -> level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z) } }.max()
        val origin=P(256,ground+1,256);val tableLocal=P(54,0,12)
        building=building.copy(graph=building.graph!!.copy(projection=BuildingProjection(tableLocal)))
        fixture=Fixture(origin,origin+tableLocal,"${building.id}@1",plan.placements.size,plan.clear.size,plan.materials,owner=p.uuid.toString())
        p.teleportTo(level,fixture.table.x+0.5,fixture.table.y+1.0,fixture.table.z+3.5,180f,0f)
        p.abilities.flying=true;p.onUpdateAbilities()
        // Only the dedicated copy's new construction site is prepared. The house itself is built by the table command below.
        for(x in -12..61) for(z in -19..43) {
            for(y in 0..25) level.setBlock(world(P(x,y,z)),Blocks.AIR.defaultBlockState(),2)
            val path=z< -8 || x>49
            level.setBlock(world(P(x,-1,z)),(if(path) Blocks.SMOOTH_STONE else Blocks.GRASS_BLOCK).defaultBlockState(),2)
            level.setBlock(world(P(x,-2,z)),Blocks.DIRT.defaultBlockState(),2)
        }
        level.getEntities(null,AABB(origin.x-12.0,origin.y.toDouble(),origin.z-19.0,origin.x+62.0,origin.y+26.0,origin.z+44.0)).filter { it!==p }.forEach { it.discard() }
        level.setBlock(tablePos(),Architect.TABLE.get().defaultBlockState(),3)
        level.setDefaultSpawnPos(world(P(54,0,16)),180f);p.setRespawnPosition(level.dimension(),world(P(54,0,16)),180f,true,false)
        p.inventory.clearContent();p.inventory.setItem(0,ItemStack(Architect.TABLE_ITEM.get()));p.inventory.setItem(1,ItemStack(Architect.GUN.get()))
        // Start the cloned world's player library with its standard seed. All depot entries arrive through network saves.
        val data=ArchitectData.get(level);data.db.libraries[p.uuid.toString()]=Library().also { it.seed() };data.setDirty()
        println("ARCHITECT INDUSTRIAL DEPOT SITE READY: origin=$origin table=${fixture.table}; no depot blocks placed yet")
    }
    private fun verifyServer(mc:Minecraft) {
        val p=player(mc);val level=p.serverLevel();val data=ArchitectData.get(level);val library=data.library(p.uuid)
        val saved=library.buildings.getValue(fixture.buildingKey)
        check(saved==building && forms.all { (key,value) -> library.forms[key]==value }) { "Saved graph/form library differs" }
        check(Generator.generate(saved,library.forms,library.modules)==plan) { "Authoritative saved graph changed compiled plan" }
        for(placement in plan.placements) {
            level.getChunk(world(placement.pos))
            check(PlacementService.matches(level,world(placement.pos),placement)) { "Actual depot differs at ${placement.pos}" }
        }
        check(plan.clear.all { level.getBlockState(world(it)).isAir }) { "Depot interior, stairs or entrance is obstructed" }
        val table=level.getBlockEntity(tablePos()) as? TableEntity ?: error("Placed architect table missing")
        val record=TableBuildings.current(level,table) ?: error("Architect table is not bound to the built depot")
        check(fixture.tableBuildingId.isEmpty() || record.id==fixture.tableBuildingId) { "Reload changed the table's persisted building identity" }
        check(table.buildingId==record.id && record.id.isNotEmpty() && record.attached)
        check(record.owner==p.uuid.toString() && record.table==fixture.table && record.origin==fixture.origin && record.turn==0)
        check(record.building==building && record.blocks.size==plan.placements.size && record.blocks.map { it.pos }.toSet().size==plan.placements.size)
        check(record.blocks.all { it.token.isNotEmpty() && TableBuildings.owned(level,record,it) }) { "Table ownership ledger does not cover every real block" }
        check(building.graph!!.projection.origin(fixture.table)==fixture.origin)
        fixture=fixture.copy(tableBuildingId=record.id,owner=p.uuid.toString())
        println("ARCHITECT INDUSTRIAL DEPOT SERVER VERIFIED: ${plan.placements.size} actual blocks, ${plan.clear.size} air cells; table=${record.id}, ${record.blocks.size} owned tokens; library=${Json.encode(library).length}/180000 characters")
    }
    private fun verifyClient(mc:Minecraft):Boolean {
        val level=checkNotNull(mc.level)
        return plan.placements.all { placement ->
            val be=level.getBlockEntity(world(placement.pos)) as? CompositeEntity
            be!=null && Geometry.resolve(be.parts,be.groups).visualKey(be.material,be.surfaces)==placement.visualKey()
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
        if(mc.level==null || mc.player==null || mc.singleplayerServer==null || mc.overlay!=null) { check(++waiting<3600) { "Depot acceptance requires its dedicated QuickPlay world" };return false }
        try {
            pending?.let { if(!it.isDone) return false;it.join();pending=null }
            ticks++
            if(verifyOnly) {
                when(ticks) {
                    1 -> { configure(mc);server(mc) { load(mc) } }
                    2 -> { mc.setScreen(null);mc.options.hideGui=true;camera(mc,16.0,26.0,-14.0,21.0,0.0,9.0,80) }
                    80 -> { if(waitFor(verifyClient(mc),"Reloaded client did not receive depot geometry")) return false;screenshot(mc,"verified-exterior-front") }
                    90 -> camera(mc,35.0,26.0,33.0,29.0,4.0,17.0,85)
                    150 -> screenshot(mc,"verified-exterior-rear")
                    160 -> camera(mc,18.0,3.0,-22.0,18.0,8.0,5.0,65)
                    220 -> screenshot(mc,"verified-exterior-loading")
                    230 -> camera(mc,3.5,2.0,2.5,30.0,8.0,23.0,78)
                    290 -> screenshot(mc,"verified-interior-hall")
                    300 -> camera(mc,44.0,7.1,24.0,16.0,7.0,9.0,78)
                    360 -> screenshot(mc,"verified-interior-gallery")
                    370 -> { mc.options.hideGui=false;server(mc) { val p=player(mc);p.teleportTo(p.serverLevel(),fixture.table.x+0.5,fixture.table.y+1.0,fixture.table.z+3.5,180f,0f);Network.open(p,"building",tablePos()) } }
                    390 -> { if(waitFor(mc.screen is BuildingScreen,"Reloaded architect table did not open")) return false;check((mc.screen as BuildingScreen).building==building);(mc.screen as BuildingScreen).openBuildingGraph();(mc.screen as BuildingScreen).kilaBuildingGraph?.fit() }
                    400 -> screenshot(mc,"reloaded-table")
                    410 -> { println("ARCHITECT INDUSTRIAL DEPOT RELOAD PASSED: exact world geometry/materials, clear interiors, saved graph/form library and complete real architect-table ownership ledger survived fresh load");mc.stop() }
                    else -> return false
                }
                return true
            }
            when(ticks) {
                1 -> { configure(mc);server(mc) { prepare(mc) } }
                2 -> server(mc) { Network.open(player(mc),"building",tablePos()) }
                20 -> { if(waitFor(mc.screen is BuildingScreen,"Real placed architect table did not open")) return false;formIndex=0 }
                21 -> {
                    if(awaitingForm.isNotEmpty() && waitFor(ClientState.snapshot.library.forms.containsKey(awaitingForm),"Form $awaitingForm was not saved over the connection")) return false
                    if(formIndex<forms.size) { val entry=forms.entries.elementAt(formIndex++);awaitingForm=entry.key;ClientState.send(Command("saveForm",Json.encode(entry.value)));ticks--;return true }
                    println("ARCHITECT INDUSTRIAL DEPOT FORMS NETWORK SAVED: ${forms.size} authoritative forms")
                }
                30 -> { val editor=mc.screen as BuildingScreen;editor.building=building;editor.openBuildingGraph();editor.kilaBuildingGraph?.fit() }
                35 -> click(mc,"save")
                45 -> {
                    if(waitFor(ClientState.snapshot.library.buildings.containsKey(fixture.buildingKey),"Graph was not saved by the real table Save button")) return false
                    building=ClientState.snapshot.library.buildings.getValue(fixture.buildingKey)
                    println("ARCHITECT INDUSTRIAL DEPOT GRAPH NETWORK SAVED: ${fixture.buildingKey}, ${building.graph!!.nodes.size} nodes")
                }
                // Reopen from the authoritative saved library so the editor derives its origin from this graph's table projection.
                46 -> server(mc) { Network.open(player(mc),"building",tablePos()) }
                48 -> { if(waitFor(mc.screen is BuildingScreen && (mc.screen as BuildingScreen).building==building,"Saved depot did not reload in the real table")) return false;(mc.screen as BuildingScreen).openBuildingGraph();(mc.screen as BuildingScreen).kilaBuildingGraph?.fit() }
                50 -> screenshot(mc,"table-graph")
                55 -> click(mc,"build")
                80 -> screenshot(mc,"table-build")
                85 -> click(mc,"creative_build")
                95 -> { if(waitFor(ClientState.snapshot.message.startsWith("Kreativ gebaut:"),"The real table Creative Build did not finish")) return false;server(mc) { verifyServer(mc);Files.writeString(metadata(mc),Json.encode(fixture));checkNotNull(mc.singleplayerServer).saveEverything(true,false,false) } }
                110 -> { mc.setScreen(null);mc.options.hideGui=true;camera(mc,16.0,26.0,-14.0,21.0,0.0,9.0,80) }
                170 -> { if(waitFor(verifyClient(mc),"Client did not receive all real depot geometry")) return false;screenshot(mc,"exterior-front") }
                180 -> camera(mc,35.0,26.0,33.0,29.0,4.0,17.0,85)
                240 -> screenshot(mc,"exterior-rear")
                250 -> camera(mc,18.0,3.0,-22.0,18.0,8.0,5.0,65)
                310 -> screenshot(mc,"exterior-loading")
                320 -> camera(mc,3.5,2.0,2.5,30.0,8.0,23.0,78)
                380 -> screenshot(mc,"interior-hall")
                390 -> camera(mc,44.0,7.1,24.0,16.0,7.0,9.0,78)
                450 -> screenshot(mc,"interior-gallery")
                460 -> { mc.options.hideGui=false;server(mc) { val p=player(mc);p.teleportTo(p.serverLevel(),fixture.table.x+0.5,fixture.table.y+1.0,fixture.table.z+3.5,180f,0f);Network.open(p,"building",tablePos()) } }
                480 -> { if(waitFor(mc.screen is BuildingScreen,"Bound table did not reopen")) return false;val editor=mc.screen as BuildingScreen;check(editor.building==building);editor.openBuildingGraph();editor.kilaBuildingGraph?.fit() }
                490 -> screenshot(mc,"table-bound-graph")
                500 -> (mc.screen as BuildingScreen).openFloorPlan()
                520 -> screenshot(mc,"table-floorplan")
                530 -> { mc.setScreen(null);server(mc) { verifyServer(mc);Files.writeString(metadata(mc),Json.encode(fixture));checkNotNull(mc.singleplayerServer).saveEverything(true,false,false) } }
                540 -> { println("ARCHITECT INDUSTRIAL DEPOT CLIENT PASSED: genuine table/network form+graph saves, UI Creative Build, ${plan.placements.size} real world blocks, complete table ownership, five world views and table graph/build/floorplan screenshots");mc.stop() }
                else -> return false
            }
        } catch(error:Throwable) {
            failed=true;System.err.println("ARCHITECT INDUSTRIAL DEPOT CLIENT FAILED at tick $ticks: ${error.message}");error.printStackTrace();screenshot(mc,"failure-$ticks");mc.stop();throw IllegalStateException("Industrial depot client acceptance failed",error)
        }
        return true
    }
    private fun configure(mc:Minecraft) {
        mc.options.guiScale().set(2);mc.options.fov().set(55);mc.options.fovEffectScale().set(0.0);mc.options.gamma().set(1.0)
        mc.options.pauseOnLostFocus=false
        mc.options.renderDistance().set(12);mc.options.hideGui=false;mc.resizeDisplay()
    }
}
