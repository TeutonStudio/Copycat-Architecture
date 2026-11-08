package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.Architect
import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.storage.LevelResource
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

/** Native architect-table acceptance; changes are guarded to a disposable copy of Vorlagen. */
@EventBusSubscriber(modid=Architect.ID,value=[Dist.CLIENT])
object SemanticBuildingClientSmoke {
    private const val KEY="semantic_building_acceptance@1"
    private val origin=P(-310,100,100)
    private val table=origin+P(15,0,3)
    private val enabled get()=java.lang.Boolean.getBoolean("architect.semanticBuildingSmoke")
    private val verifyOnly get()=java.lang.Boolean.getBoolean("architect.semanticBuildingVerify")
    private val output:Path get()=Path.of(System.getProperty("architect.semanticBuildingOutput","build/semantic-building-acceptance"))
    private data class Fixture(val building:Building,val origin:P,val table:P,val record:String="",val owner:String="",val baselineLibraryCharacters:Int=0)
    private var frames=0L;private var lastFrame=-1L;private var ticks=0;private var waiting=0;private var failed=false
    private var pending:CompletableFuture<Unit>?=null
    private lateinit var fixture:Fixture
    private lateinit var building:Building
    private lateinit var plan:Plan

    /** Small public fixture for an ordinary compiler test before launching Minecraft. */
    fun createBuilding():Building {
        val square=listOf(P(),P(6,0,0),P(6,0,6),P(0,0,6))
        val outside=listOf(P(),P(4,0,0),P(4,0,4),P(0,0,4))
        fun node(id:String,kind:BuildingKind,name:String,x:Int,y:Int)=BuildingNode(id,kind,name,x,y,module="")
        fun instance(id:String,index:Int,pos:P,turn:Int=0,policy:WallPolicy=WallPolicy.EXPLICIT,contribution:RoomContribution=RoomContribution.BOUNDARY)=
            RoomSegmentInstance(id,index,pos,turn,policy,contribution)
        val nodes=listOf(
            node("empty_wall",BuildingKind.SEGMENT,"Leere Wand",20,-180).copy(block="minecraft:air",length=1),
            node("plain",BuildingKind.SEGMENT,"Ziegelwand",20,30).copy(length=6,height=4,role="wall"),
            node("glass",BuildingKind.COPYCAT,"Fensterglas",20,260).copy(role="glass"),
            node("opening_mask",BuildingKind.COPYCAT,"Freie Fensterform",20,720).copy(form="stair@1",quarterTurns=1),
            node("window",BuildingKind.SEGMENT,"Fenster und Eingang",260,30).copy(length=6,height=4,role="wall",features=SegmentFeatures(openings=listOf(
                OpeningSpec("window",position=P(1,1,0),size=P(2,2,1),maskSource="opening_mask",insetSource="glass",retainSource=false),
                OpeningSpec("door",position=P(4,0,0),size=P(1,3,1),retainSource=false)
            ))),
            node("facade",BuildingKind.SEGMENT,"Fassadenbaustein",20,490).copy(length=1,height=1,role="wall"),
            node("column",BuildingKind.SEGMENT,"Säule bis Dach",260,270).copy(length=1,height=4,role="frame",features=SegmentFeatures(extensions=listOf(
                ColumnExtensionSpec("shaft",shaftMinY=0,shaftMaxY=4,roof=SurfaceRef("roof_level","storey_0","underside"))
            ))),
            node("main_layout",BuildingKind.ROOM_LAYOUT,"Halle aus Segmenten",520,30).copy(inputs=listOf("window","plain","column"),roomLayout=RoomLayoutConfig(
                instances=listOf(instance("north",0,P(),policy=WallPolicy.AUTO),instance("east",1,P(5,0,0),1),instance("south",1,P(0,0,5),2),instance("west",1,P(),3),
                    instance("column",2,P(3,0,3),contribution=RoomContribution.STRUCTURE)),boundary=square)),
            node("floor",BuildingKind.FLOOR,"Steinboden",520,310).copy(thickness=1,role="floor"),
            node("main_room",BuildingKind.ROOM,"Halle",810,30).copy(layout="main_layout",height=4,floor="floor",roomConfig=RoomConfig(RoomMode.SEGMENTS)),
            node("canopy_support",BuildingKind.SEGMENT,"Vordachstütze",260,500).copy(length=1,height=3,role="frame"),
            node("canopy_layout",BuildingKind.ROOM_LAYOUT,"Offener Außenbereich",520,540).copy(inputs=listOf("canopy_support"),roomLayout=RoomLayoutConfig(
                instances=listOf(instance("outer_north",0,P(3,0,0),contribution=RoomContribution.STRUCTURE),instance("outer_south",0,P(3,0,3),contribution=RoomContribution.STRUCTURE)),
                boundary=outside,boundaryRules=outside.indices.map { RoomBoundaryRule(outside[it],outside[(it+1)%outside.size],WallPolicy.OPEN) })),
            node("canopy_ceiling",BuildingKind.CEILING,"Vordach bis Hallenwand",810,340).copy(thickness=1,role="roof",attachments=listOf(
                AttachmentSpec("wall_contact",edge=3,target=SurfaceRef("main_room","storey_0/room:0","east"))
            )),
            node("canopy_room",BuildingKind.ROOM,"Überdachter Außenraum",810,610).copy(layout="canopy_layout",height=3,floor="floor",ceiling="canopy_ceiling",roomConfig=RoomConfig(RoomMode.SEGMENTS,heightOverride=3)),
            node("storey",BuildingKind.STOREY,"Stockwerk mit Fassade",1100,40).copy(inputs=listOf("main_room","canopy_room"),offsets=listOf(P(),P(8,0,1)),facade=FacadeConfig("facade","facade")),
            node("roof_area",BuildingKind.AREA,"Dachfläche der Halle",1080,340).copy(coordinates=(0 until 6).flatMap { x -> (0 until 6).map { z -> P(x,0,z) } }),
            node("roof_level",BuildingKind.ROOF_LEVEL,"Satteldach 1:4",1350,320).copy(role="roof",roofLevel=RoofLevelConfig(storey="storey_0",area="roof_area",slope=RoofSlope(1,4),knee16=16,overhang16=0)),
            node("roof_layout",BuildingKind.ROOF_LAYOUT,"Dachanordnung",1590,320).copy(inputs=listOf("roof_level")),
            node("roof",BuildingKind.ROOF,"Hallendach",1830,320).copy(assembly=true,layout="roof_layout",role="roof"),
            node("building",BuildingKind.BUILDING,"Segmenträume – Abnahme",1350,30).copy(levels=mapOf(0 to "storey"),levelIds=mapOf(0 to "storey_0"),roof="roof")
        )
        return Building(schema=2,id="semantic_building_acceptance",name="Segmenträume – Abnahme",floors=1,wings=emptyList(),
            palette=mapOf("wall" to "minecraft:bricks","frame" to "minecraft:polished_andesite","glass" to "minecraft:glass","roof" to "minecraft:deepslate_tiles","floor" to "minecraft:smooth_stone","gable" to "minecraft:bricks","trim" to "minecraft:polished_andesite"),
            graph=BuildingGraph(schema=3,nodes=nodes,projection=BuildingProjection(P(15,0,3))))
    }

    @SubscribeEvent fun frame(event:RenderFrameEvent.Post) { if(enabled) frames++ }
    @SubscribeEvent fun tick(event:ClientTickEvent.Post) { if(enabled && frames>lastFrame && advance()) lastFrame=frames+1 }
    private fun player(mc:Minecraft):ServerPlayer=checkNotNull(mc.singleplayerServer).playerList.players.single()
    private fun server(mc:Minecraft,action:()->Unit) {
        pending=CompletableFuture<Unit>().also { future -> checkNotNull(mc.singleplayerServer).execute {
            try { action();future.complete(Unit) } catch(error:Throwable) { future.completeExceptionally(error) }
        } }
    }
    private fun metadata(mc:Minecraft)=checkNotNull(mc.singleplayerServer).getWorldPath(LevelResource.ROOT).resolve("semantic-building-fixture.json")
    private fun world(p:P)=PlacementService.world(origin+p)
    private fun tablePos()=PlacementService.world(table)
    private fun guard(mc:Minecraft) {
        val runtime=mc.gameDirectory.toPath().toAbsolutePath().normalize();val save=metadata(mc).parent.toAbsolutePath().normalize()
        check(runtime.fileName.toString()=="semantic-building-client" && save.fileName.toString()=="Vorlagen") { "Semantic acceptance is restricted to the disposable Vorlagen copy: $runtime / $save" }
    }
    private fun prepare(mc:Minecraft) {
        guard(mc);Files.createDirectories(output)
        val p=player(mc);val level=p.serverLevel();val data=ArchitectData.get(level)
        check(!Files.exists(metadata(mc))) { "Use semanticBuildingVerify after the fixture has been built" }
        val baseline=Json.encode(data.library(p.uuid))
        Files.writeString(output.resolve("copied-world-baseline-library.json"),baseline)
        // The original world's complete library is exported above. Only this guarded disposable
        // test copy receives a seeded fixture library; the user's world is never opened here.
        val lib=Library().also { it.seed() };data.db.libraries[p.uuid.toString()]=lib;data.setDirty()
        building=createBuilding();plan=Generator.generate(building,lib.forms,lib.modules)
        check(plan.warnings.isEmpty()) { "Fixture warnings: ${plan.warnings}" }
        fixture=Fixture(building,origin,table,owner=p.uuid.toString(),baselineLibraryCharacters=baseline.length)
        Files.writeString(output.resolve("building.json"),Json.encode(building))
        p.gameMode.changeGameModeForPlayer(GameType.CREATIVE);p.abilities.flying=true;p.onUpdateAbilities()
        level.dayTime=6000;level.setWeatherParameters(0,0,false,false)
        for(cx in -21..-18) for(cz in 5..8) level.getChunk(cx,cz)
        // A new elevated pad far from the user's table; no original build coordinates are touched.
        for(x in -3..18) for(z in -3..10) {
            for(y in -1..12) check(level.getBlockState(world(P(x,y,z))).isAir) { "Fixture site is not empty at ${origin+P(x,y,z)}" }
        }
        for(x in -3..18) for(z in -3..10) level.setBlock(world(P(x,-2,z)),Blocks.SMOOTH_STONE.defaultBlockState(),2)
        level.setBlock(tablePos(),Architect.TABLE.get().defaultBlockState(),3)
        println("SEMANTIC BUILDING SITE READY: ${plan.placements.size} planned blocks, ${building.graph!!.nodes.size} nodes; original copied library exported (${baseline.length} characters)")
    }
    private fun record(mc:Minecraft):TableBuilding {
        val p=player(mc);val level=p.serverLevel()
        val be=level.getBlockEntity(tablePos()) as? TableEntity ?: error("Real fixture architect table missing")
        return TableBuildings.current(level,be) ?: error("Fixture table has no building binding")
    }
    private fun verifyServer(mc:Minecraft) {
        guard(mc)
        val p=player(mc);val level=p.serverLevel();val lib=ArchitectData.get(level).library(p.uuid)
        val saved=lib.buildings.getValue(KEY)
        check(saved==building) { "Authoritative saved fixture graph differs" }
        check(Generator.generate(saved,lib.forms,lib.modules)==plan) { "Persisted semantic graph compiles differently" }
        val current=record(mc)
        check(fixture.record.isEmpty() || current.id==fixture.record)
        check(current.owner==p.uuid.toString() && current.origin==origin && current.table==table && current.turn==0 && current.attached)
        check(current.building==building && current.blocks.size==plan.placements.size)
        for(placement in plan.placements) check(PlacementService.matches(level,world(placement.pos),placement)) { "Real fixture differs at ${placement.pos}" }
        check(plan.clear.all { level.getBlockState(world(it)).isAir }) { "Fixture room, doorway or canopy contains an obstruction" }
        check(current.blocks.all { it.token.isNotEmpty() && TableBuildings.owned(level,current,it) }) { "Incomplete architect-table ownership" }
        fixture=fixture.copy(record=current.id)
        Files.writeString(output.resolve(if(verifyOnly) "reload-report.json" else "create-report.json"),Json.encode(mapOf("passed" to true,"placements" to plan.placements.size,"clearCells" to plan.clear.size,"ownership" to current.blocks.size,"fixture" to fixture)))
        println("SEMANTIC BUILDING SERVER VERIFIED: ${plan.placements.size} actual blocks, ${plan.clear.size} clear cells, ${current.blocks.size} ownership tokens")
    }
    private fun load(mc:Minecraft) {
        guard(mc);fixture=Json.decode(Files.readString(metadata(mc)));building=fixture.building
        val lib=ArchitectData.get(player(mc).serverLevel()).library(player(mc).uuid)
        plan=Generator.generate(building,lib.forms,lib.modules);verifyServer(mc)
    }
    private fun waitFor(ok:Boolean,message:String):Boolean {
        if(ok) { waiting=0;return false }
        check(++waiting<1800) { "$message; server: ${ClientState.snapshot.message}" };ticks--;return true
    }
    private fun click(mc:Minecraft,key:String) {
        val screen=checkNotNull(mc.screen)
        val b=screen.children().filterIsInstance<Button>().single { it.visible && it.active && it.message.string==ui(key) }
        check(screen.mouseClicked(b.x+b.width/2.0,b.y+b.height/2.0,0));screen.mouseReleased(b.x+b.width/2.0,b.y+b.height/2.0,0)
    }
    private fun screenshot(mc:Minecraft,name:String) {
        if(listOf("exterior","interior","contact").any(name::contains)) check(mc.screen==null)
        Screenshot.grab(mc.gameDirectory,"semantic-building-${if(verifyOnly) "reload" else "create"}-$name.png",mc.mainRenderTarget) { println(it.string) }
    }
    private fun openTable(mc:Minecraft) {
        mc.setScreen(null);mc.options.hideGui=false
        server(mc) { val p=player(mc);p.teleportTo(p.serverLevel(),table.x+0.5,table.y+1.0,table.z+3.5,180f,0f);Network.open(p,"building",tablePos()) }
    }
    private fun camera(mc:Minecraft,x:Double,y:Double,z:Double,tx:Double,ty:Double,tz:Double,fov:Int=70) {
        mc.setScreen(null);mc.options.hideGui=true;mc.options.fov().set(fov)
        server(mc) { val p=player(mc);val dx=tx-x;val dy=ty-y-p.eyeHeight;val dz=tz-z
            p.abilities.flying=true;p.onUpdateAbilities()
            p.teleportTo(p.serverLevel(),origin.x+x,origin.y+y,origin.z+z,Math.toDegrees(atan2(-dx,dz)).toFloat(),-Math.toDegrees(atan2(dy,sqrt(dx*dx+dz*dz))).toFloat())
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
            if(!verifyOnly && ticks<=120) {
                when(ticks) {
                    1->{ configure(mc);server(mc) { prepare(mc) } }
                    2->openTable(mc)
                    20->{ if(waitFor(mc.screen is BuildingScreen,"Native architect table failed to open")) return false;val editor=mc.screen as BuildingScreen;editor.building=building;editor.openBuildingGraph();editor.kilaBuildingGraph?.fit() }
                    30->click(mc,"save")
                    40->{ if(waitFor(ClientState.snapshot.library.buildings[KEY]==building,"Real Save button did not persist the fixture")) return false }
                    45->openTable(mc)
                    55->{ if(waitFor(mc.screen is BuildingScreen && (mc.screen as BuildingScreen).building==building,"Saved graph did not reload in the table")) return false;(mc.screen as BuildingScreen).openBuildingGraph();(mc.screen as BuildingScreen).kilaBuildingGraph?.fit() }
                    65->screenshot(mc,"table-graph")
                    70->click(mc,"build")
                    80->click(mc,"creative_build")
                    95->{ if(waitFor(ClientState.snapshot.message.startsWith("Kreativ gebaut:"),"Native Creative Build did not finish")) return false;server(mc) { verifyServer(mc);Files.writeString(metadata(mc),Json.encode(fixture));checkNotNull(mc.singleplayerServer).saveEverything(true,false,false) } }
                    120->Unit
                    else->return false
                }
                return true
            }
            val t=if(verifyOnly) ticks else ticks-120
            when(t) {
                1->if(verifyOnly) { configure(mc);server(mc) { load(mc) } }
                2->camera(mc,14.0,12.0,-9.0,5.0,2.0,3.0,72)
                65->screenshot(mc,"exterior-overview")
                75->camera(mc,2.2,1.0,2.0,4.0,5.2,3.4,82)
                135->screenshot(mc,"interior-column")
                145->camera(mc,12.5,3.5,-2.0,5.5,3.2,2.5,76)
                205->screenshot(mc,"contact-canopy")
                215->openTable(mc)
                235->{ if(waitFor(mc.screen is BuildingScreen,"Saved table did not reopen")) return false;check((mc.screen as BuildingScreen).building==building)
                    val graph=building.graph!!;val ev=BuildingGraphCompiler.Evaluation(graph,building.palette,ClientState.snapshot.library.forms,ClientState.snapshot.library.modules)
                    RoomSegmentLayoutScreen.open(graph.nodes.first { it.id=="main_layout" },graph,ev) { error("Acceptance view is read-only") }
                }
                238->{
                    val screen=mc.screen as RoomSegmentLayoutScreen
                    val start=screen.instancePoint("column");val end=screen.gridPoint(4.5,3.5)
                    check(screen.mouseClicked(start.first,start.second,0));check(screen.mouseDragged(end.first,end.second,0,end.first-start.first,end.second-start.second));screen.mouseReleased(end.first,end.second,0)
                    check(screen.draft.roomLayout!!.instances.single { it.id=="column" }.position==P(4,0,3)) { "Native room-raster drag failed" }
                    check(screen.keyPressed(82,0,0));check(screen.draft.roomLayout!!.instances.single { it.id=="column" }.quarterTurns==1) { "Native room-raster rotation failed" }
                    check(screen.keyPressed(261,0,0));check(screen.draft.roomLayout!!.instances.none { it.id=="column" }) { "Native room-raster removal failed" }
                    click(mc,"semantic_boundary_draw")
                    for(p in listOf(P(),P(6,0,0),P(6,0,6),P(0,0,6))) { val point=screen.gridPoint(p.x.toDouble(),p.z.toDouble());check(screen.mouseClicked(point.first,point.second,0));screen.mouseReleased(point.first,point.second,0) }
                    click(mc,"semantic_boundary_accept");check(screen.draft.roomLayout!!.boundary.size==4) { "Native boundary drawing failed" }
                    screen.onClose()
                    val graph=building.graph!!;val ev=BuildingGraphCompiler.Evaluation(graph,building.palette,ClientState.snapshot.library.forms,ClientState.snapshot.library.modules)
                    RoomSegmentLayoutScreen.open(graph.nodes.first { it.id=="main_layout" },graph,ev) { error("Acceptance view is read-only") }
                    println("SEMANTIC BUILDING NATIVE ROOM EDITOR VERIFIED: block drag, 90-degree rotation, deletion, explicit four-corner boundary and canceled draft")
                }
                245->screenshot(mc,"room-raster")
                255->{ mc.screen?.onClose();val graph=building.graph!!;SemanticPropertyEditors.features(graph.nodes.first { it.id=="window" },graph) { error("Acceptance view is read-only") } }
                265->screenshot(mc,"window-properties")
                275->{ mc.screen?.onClose();(mc.screen as BuildingScreen).openFloorPlan() }
                290->screenshot(mc,"table-floorplan")
                300->{ mc.setScreen(null);server(mc) { verifyServer(mc);Files.writeString(metadata(mc),Json.encode(fixture));checkNotNull(mc.singleplayerServer).saveEverything(true,false,false) } }
                310->{ println("SEMANTIC BUILDING ${if(verifyOnly) "RELOAD" else "CREATE"} PASSED: real Save and Creative Build, persistent editable graph, actual blocks and ownership, native room raster and world views");mc.stop() }
                else->return false
            }
        } catch(error:Throwable) {
            failed=true;System.err.println("SEMANTIC BUILDING FAILED at tick $ticks: ${error.message}");error.printStackTrace();screenshot(mc,"failure-$ticks");mc.stop();throw IllegalStateException("Semantic building acceptance failed",error)
        }
        return true
    }
    private fun configure(mc:Minecraft) {
        mc.options.guiScale().set(2);mc.options.fov().set(70);mc.options.fovEffectScale().set(0.0);mc.options.gamma().set(1.0)
        mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(12);mc.options.hideGui=false;mc.resizeDisplay()
    }
}
