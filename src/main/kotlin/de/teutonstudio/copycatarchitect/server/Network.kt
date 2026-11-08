package de.teutonstudio.copycatarchitect.server

import de.teutonstudio.copycatarchitect.Architect
import de.teutonstudio.copycatarchitect.domain.*
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.component.CustomData
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import java.util.UUID

data class Request(val json:String):CustomPacketPayload {
    override fun type()=TYPE
    companion object {
        val TYPE=CustomPacketPayload.Type<Request>(ResourceLocation.fromNamespaceAndPath(Architect.ID,"request"))
        val CODEC=object:StreamCodec<RegistryFriendlyByteBuf,Request> {
            override fun encode(b:RegistryFriendlyByteBuf,p:Request) { b.writeUtf(CommandTransport.encode(p.json),CommandTransport.MAX_WIRE_CHARS) }
            override fun decode(b:RegistryFriendlyByteBuf)=Request(CommandTransport.decode(b.readUtf(CommandTransport.MAX_WIRE_CHARS)))
        }
    }
}
data class Response(val json:String):CustomPacketPayload {
    override fun type()=TYPE
    companion object {
        val TYPE=CustomPacketPayload.Type<Response>(ResourceLocation.fromNamespaceAndPath(Architect.ID,"response"))
        val CODEC=object:StreamCodec<RegistryFriendlyByteBuf,Response> {
            override fun encode(b:RegistryFriendlyByteBuf,p:Response) { b.writeUtf(CommandTransport.encode(p.json,Snapshot.MAX_SNAPSHOT_JSON_CHARS,250000),250000) }
            override fun decode(b:RegistryFriendlyByteBuf)=Response(CommandTransport.decode(b.readUtf(250000),Snapshot.MAX_SNAPSHOT_JSON_CHARS,250000))
        }
    }
}
data class Command(val action:String="sync",val payload:String="",val key:String="",val material:String="minecraft:stone",val turn:Int=0,val origin:P=P(),val dock:P=P(),val computer:Int=-1,val surfaces:Map<String,String> = emptyMap(),val slot:Int=-1,val inventorySlot:Int=-1)
data class JobView(val id:String,val name:String,val state:String,val index:Int,val total:Int,val message:String)
data class Snapshot(val editor:String="",val library:Library=Library(),val jobs:List<JobView> = emptyList(),val message:String="",val position:P=P(),val extrusionOutputs:Map<String,Int> = Extrusion.outputs.toMap(),val printer:PrinterView?=null,val table:P?=null,
    val creative:Boolean=false,val tableBuilding:Building?=null,val tableOrigin:P?=null,val tableTurn:Int=0) {
    companion object { const val MAX_SNAPSHOT_JSON_CHARS=1000000 }
}
object Network {
    var clientReceiver:(String)->Unit = {}
    private val last=mutableMapOf<UUID,Long>()
    private val previews=mutableMapOf<UUID,Long>()
    private data class TableBinding(val dimension:String,val pos:BlockPos)
    private val tables=mutableMapOf<UUID,TableBinding>()
    fun register(event:RegisterPayloadHandlersEvent) {
        val registrar=event.registrar("1")
        registrar.playToServer(Request.TYPE,Request.CODEC) { p,ctx -> ctx.enqueueWork { val player=ctx.player() as? ServerPlayer ?: return@enqueueWork;handle(player,p.json) } }
        registrar.playToClient(Response.TYPE,Response.CODEC) { p,ctx -> ctx.enqueueWork { clientReceiver(p.json) } }
    }
    fun open(player:ServerPlayer,type:String,table:BlockPos?=null) {
        if(type=="building") {
            tables.remove(player.uuid)
            if(table!=null && player.level().getBlockState(table).`is`(Architect.TABLE.get())) {
                tables[player.uuid]=TableBinding(player.level().dimension().location().toString(),table.immutable())
                (player.level().getBlockEntity(table) as? TableEntity)?.let { TableBuildings.bind(player.serverLevel(),it,player) }
            }
        }
        if(type=="printerStorage") PrinterService.openStorage(player)
        sync(player,type)
    }
    private fun table(player:ServerPlayer):P? {
        val binding=tables[player.uuid] ?: return null
        val delta=binding.pos.subtract(player.blockPosition())
        if(binding.dimension!=player.level().dimension().location().toString() || kotlin.math.abs(delta.x)>6 || kotlin.math.abs(delta.y)>3 || kotlin.math.abs(delta.z)>6 || !player.level().getBlockState(binding.pos).`is`(Architect.TABLE.get())) {
            tables.remove(player.uuid);return null
        }
        return PlacementService.local(binding.pos)
    }
    fun sync(player:ServerPlayer,editor:String="",message:String="") {
        val data=ArchitectData.get(player.serverLevel())
        val jobs=data.db.jobs.filter { it.owner==player.uuid.toString() }.map { JobView(it.id,it.name,it.state,it.index,it.plan.placements.size,it.message) }
        val table=table(player)
        val record=table?.let { player.level().getBlockEntity(PlacementService.world(it)) as? TableEntity }?.let { TableBuildings.current(player.serverLevel(),it) }
        val json=Json.encode(Snapshot(editor,data.library(player.uuid),jobs,message,PlacementService.local(player.blockPosition()),printer=PrinterService.view(player),table=table,
            creative=player.isCreative,tableBuilding=if(editor=="building") record?.relativeBuilding() else null,tableOrigin=record?.origin,tableTurn=record?.turn ?: 0))
        if(json.length<=Snapshot.MAX_SNAPSHOT_JSON_CHARS) PacketDistributor.sendToPlayer(player,Response(json))
    }
    private fun authorized(p:ServerPlayer):Boolean {
        if(PrinterService.active(p)!=null) return true
        if(p.mainHandItem.`is`(Architect.GUN.get()) || p.offhandItem.`is`(Architect.GUN.get())) return true
        return BlockPos.betweenClosed(p.blockPosition().offset(-6,-3,-6),p.blockPosition().offset(6,3,6)).any { p.level().getBlockState(it).`is`(Architect.TABLE.get()) }
    }
    fun handle(player:ServerPlayer,json:String) {
        try {
            require(json.length<=CommandTransport.MAX_JSON_CHARS && authorized(player)) { "Use your block printer or a nearby architect table" }
            val c=Json.decode<Command>(json)
            val now=System.currentTimeMillis()
            if(c.action=="printerPreview") { if(now-(previews[player.uuid] ?: 0)<80) return;previews[player.uuid]=now }
            if(c.action!="printerPreview") { require(now-(last[player.uuid] ?: 0)>=40) { "Please wait before the next change" };last[player.uuid]=now }
            val data=ArchitectData.get(player.serverLevel());val lib=Json.decode<Library>(Json.encode(data.library(player.uuid)))
            fun id(s:String)=s.ifEmpty { UUID.randomUUID().toString() }.also { require(it.matches(Regex("[a-zA-Z0-9_-]{1,64}"))) }
            var message=""
            var editor=""
            when(c.action) {
                "printerPreview" -> { PrinterService.update(player,c);return }
                "printerCancel" -> { PrinterService.cancel(player,c.key);if(player.containerMenu is PrinterStorageMenu) player.closeContainer();editor="printerDone" }
                "printerPrint" -> { PrinterService.print(player,c,lib);if(player.containerMenu is PrinterStorageMenu) player.closeContainer();editor="printerDone";message="Print complete" }
                "printerStorage" -> { if(c.key.isNotEmpty()) PrinterService.update(player,c);PrinterService.openStorage(player);editor="printerStorage" }
                "printerGraph" -> { require(PrinterService.active(player)!=null);if(player.containerMenu is PrinterStorageMenu) player.closeContainer();editor="printer" }
                "printerCartridge" -> { PrinterService.cartridge(player,c.slot,c.inventorySlot);editor="printerStorage" }
                "printerLoad" -> {
                    val record=lib.history.firstOrNull { it.id==c.key }
                    val form=record?.form ?: lib.forms[c.key] ?: error("Print definition not found")
                    val settings=(record?.let { PrintSettings(it.surfaces,it.turn) } ?: lib.printSettings[c.key] ?: PrintSettings()).validate(form)
                    PrinterService.load(player,form,settings.surfaces,settings.turn);editor="printerStorage"
                }
                "historySave" -> {
                    lib.savePrintHistory(c.key,c.payload);editor="printerStorage";message="Print saved"
                }
                "sync" -> {}
                "saveForm" -> {
                    val f=Geometry.validate(Json.decode<Form>(c.payload));val fid=id(f.id)
                    f.parts.forEach { part ->
                        val source=ResourceLocation.parse(part.source)
                        if(part.source==RoofPartParameters.SOURCE) {
                            require(net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(source) && net.minecraft.core.registries.BuiltInRegistries.ITEM.get(source)!==net.minecraft.world.item.Items.AIR) { "Procedural roof source item is missing" }
                        } else de.teutonstudio.copycatarchitect.integration.SourceStates.apply(net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(source).defaultBlockState(),part.sourceProperties())
                    }
                    val v=(lib.forms.values.filter { it.id==fid }.maxOfOrNull { it.version } ?: 0)+1
                    val saved=f.copy(id=fid,version=v);lib.forms["$fid@$v"]=saved
                    if(c.key.isNotEmpty() || c.surfaces.isNotEmpty() || c.turn!=0) {
                        if(c.key.isNotEmpty()) require(PrinterService.active(player)?.session==c.key) { "Printer session expired" }
                        val settings=PrintSettings(c.surfaces.toMap(),c.turn).validate(saved)
                        settings.surfaces.values.filter(String::isNotEmpty).forEach { Economy.state(it) }
                        lib.printSettings["$fid@$v"]=settings
                    }
                    message="Saved ${saved.name} @$v (${f.parts.size} normalized parts)"
                }
                "saveModule" -> {
                    val m=Json.decode<Module>(c.payload).validate();val mid=id(m.id)
                    m.cells.filter { !it.empty }.forEach { if(it.block.isEmpty()) require(it.form in lib.forms) { "Missing form ${it.form}" } else if(it.block!="minecraft:air") Economy.nativeState(it.block) }
                    val v=(lib.modules.values.filter { it.id==mid }.maxOfOrNull { it.version } ?: 0)+1
                    lib.modules["$mid@$v"]=m.copy(id=mid,version=v);message="Module saved @$v"
                }
                "saveBuilding" -> {
                    val decoded=Json.decode<Building>(c.payload);val b=decoded.copy(graph=decoded.graph?.withCurrentSchema());val plan=Generator.generate(b,lib.forms,lib.modules);plan.placements.filter { it.block.isNotEmpty() }.forEach { Economy.nativeState(it.block) };b.palette.values.forEach { Economy.state(it) }
                    val bid=id(b.id);val v=(lib.buildings.values.filter { it.id==bid }.maxOfOrNull { it.version } ?: 0)+1
                    lib.buildings["$bid@$v"]=b.copy(schema=if(b.graph==null) 1 else 2,id=bid,version=v);message="Building saved @$v"
                }
                "select" -> {
                    require(c.key in lib.forms && c.turn in 0..3);Economy.state(c.material)
                    val form=lib.forms.getValue(c.key)
                    require(c.surfaces.size<=8 && c.surfaces.keys.all { id -> form.groups.any { it.id==id } }) { "Unknown material group" }
                    c.surfaces.values.forEach { Economy.state(it) }
                    val stack=if(player.mainHandItem.`is`(Architect.GUN.get())) player.mainHandItem else player.offhandItem
                    require(stack.`is`(Architect.GUN.get())) { "Hold the gun to select a form" }
                    stack.set(DataComponents.CUSTOM_DATA,CustomData.of(PrinterStack.tag(stack).apply { putString("form",c.key);putString("material",c.material);putString("surfaces",Json.encode(c.surfaces));putInt("turn",c.turn) }));message="Gun selection updated"
                    PrinterStack.draft(stack,form,c.surfaces,c.turn)
                }
                "printerRotate" -> {
                    require(c.turn in 0..3) { "Printer rotation must be 0–3" }
                    val stack=PrinterService.held(player);require(!stack.isEmpty) { "Hold your block printer to rotate it" }
                    PrinterStack.update(stack) { it.putInt("turn",c.turn) };message="Printer rotation updated"
                }
                "creativeBuild" -> {
                    require(player.isCreative) { "Creative mode is required" }
                    val table=table(player)?.let { player.level().getBlockEntity(PlacementService.world(it)) as? TableEntity } ?: error("Open a nearby architect table")
                    require(c.payload.length<=180000) { "Building document is too large" }
                    val count=TableBuildings.creativeBuild(player,table,Json.decode(c.payload),lib,c.origin,c.turn)
                    sync(player,message="Kreativ gebaut: $count Blöcke aktualisiert");return
                }
                "createJob" -> {
                    val b=Json.decode<Building>(c.payload);val plan=Generator.generate(b,lib.forms,lib.modules)
                    plan.placements.filter { it.block.isNotEmpty() }.forEach { Economy.nativeState(it.block) }
                    require(c.turn in 0..3 && c.computer>=-1 && c.computer<=1_000_000)
                    require(PlacementService.world(c.origin).distManhattan(player.blockPosition())<96) { "Build origin is too far away" }
                    val frozen=BuildingProjection(table=P(),turn=c.turn).plan(plan,c.origin)
                    require(data.db.jobs.count { it.owner==player.uuid.toString() && it.state!="cancelled" }<16) { "Job limit reached" }
                    val table=table(player)?.let { player.level().getBlockEntity(PlacementService.world(it)) as? TableEntity }
                    val record=table?.let { TableBuildings.prepare(player.serverLevel(),it,player,b,c.origin,c.turn) }
                    val job=Job(owner=player.uuid.toString(),dimension=player.level().dimension().location().toString(),name=b.name,origin=c.origin,rotation=c.turn,dock=c.dock,plan=frozen,computer=c.computer,buildingId=record?.id ?: "")
                    Jobs.validateDock(job)
                    if(table!=null && record!=null) TableBuildings.remember(player.serverLevel(),table,record)
                    data.db.jobs+=job;message="Job created; start checks the work space"
                }
                "start","resume","pause","cancel" -> {
                    val j=data.db.jobs.firstOrNull { it.id==c.key && it.owner==player.uuid.toString() } ?: error("Job not found")
                    when(c.action) {
                        "pause" -> { j.state="paused";j.message="Paused by player" }
                        "cancel" -> { j.state="cancelled";j.message="Cancelled; placed blocks retained" }
                        else -> { require(j.state !in listOf("cancelled","complete"));Jobs.beginCheck(player.serverLevel(),j) }
                    }
                }
                else -> error("Unknown editor command")
            }
            require(lib.forms.size<=128 && lib.modules.size<=64 && lib.buildings.size<=32) { "Library limit reached" }
            lib.validateStorageSize()
            data.db.libraries[player.uuid.toString()]=lib
            data.setDirty();sync(player,editor,message)
        } catch(e:Exception) { sync(player,message=e.message?.take(250) ?: "Invalid input") }
    }
}
