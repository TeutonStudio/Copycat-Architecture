package de.teutonstudio.copycatarchitect.client

import com.mojang.blaze3d.vertex.*
import com.mojang.math.Axis
import de.teutonstudio.copycatarchitect.Architect
import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.*
import net.minecraft.client.renderer.blockentity.*
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.core.Direction
import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.client.event.ModelEvent
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraft.core.component.DataComponents
import net.minecraft.world.phys.BlockHitResult

@EventBusSubscriber(modid=Architect.ID,bus=EventBusSubscriber.Bus.MOD,value=[Dist.CLIENT])
object ClientSetup {
    @SubscribeEvent fun setup(e:FMLClientSetupEvent) { e.enqueueWork {
        println("ARCHITECT CLIENT SETUP")
        Network.clientReceiver={ json ->
            val snapshot=Json.decode<Snapshot>(json);ClientState.snapshot=snapshot;Extrusion.outputs=snapshot.extrusionOutputs
            val mc=Minecraft.getInstance()
            CreativeCatalogClient.refresh()
            if(mc.screen is PrinterStorageScreen) (mc.screen as PrinterStorageScreen).refresh(fromSnapshot=true)
            if(snapshot.editor=="printer") mc.setScreen(ShapeScreen(printerSession=snapshot.printer))
            if(snapshot.editor=="printerDone") { WorldPeek.active=false;mc.setScreen(null) }
            if(snapshot.editor=="shape") mc.setScreen(ShapeScreen())
            if(snapshot.editor=="building") mc.setScreen(BuildingScreen())
            else (mc.screen as? BuildingScreen)?.refreshProjectionBinding()
        }
        CreativeCatalogClient.setup()
        net.createmod.ponder.foundation.PonderIndex.addPlugin(ArchitectPonderPlugin())
        NeoForge.EVENT_BUS.addListener(ClientSmoke::tick)
        NeoForge.EVENT_BUS.addListener(ClientSmoke::frame)
        NeoForge.EVENT_BUS.addListener(PrinterDisplayCapture::afterScreen)
        NeoForge.EVENT_BUS.addListener(GunControls::tick)
        NeoForge.EVENT_BUS.addListener(ClientSetup::preview)
        NeoForge.EVENT_BUS.addListener(BuildingWorldPreview::render)
        NeoForge.EVENT_BUS.addListener(BuildingWorldPreview::tick)
    } }
    @SubscribeEvent fun menus(e:net.neoforged.neoforge.client.event.RegisterMenuScreensEvent) {
        e.register(Architect.PRINTER_MENU.get()) { menu,_,_ -> PrinterStorageScreen(Minecraft.getInstance().screen,menu) }
    }
    @SubscribeEvent fun upgradeModels(e:dan200.computercraft.api.client.turtle.RegisterTurtleModellersEvent) { e.register(Architect.UPGRADE.get(),dan200.computercraft.api.client.turtle.TurtleUpgradeModeller.flatItem()) }
    @SubscribeEvent fun keys(e:net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent) { e.register(GunControls.rotate) }
    @SubscribeEvent fun fluidExtensions(e:net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent) { e.registerItem(CompositeFormItemRenderer.extensions,Architect.COMPOSITE_ITEM.get()) }
    @SubscribeEvent fun renderers(e:EntityRenderersEvent.RegisterRenderers) {
        e.registerBlockEntityRenderer(Architect.BE.get()) { CompositeRenderer(it) }
        e.registerBlockEntityRenderer(Architect.PRINTER_BE.get()) { PrinterRenderer(it) }
    }
    @SubscribeEvent fun thumbnailResources(e:ModelEvent.BakingCompleted) { GraphPreviewThumbnails.invalidateResources() }
    fun preview(e:RenderLevelStageEvent) {
        if(e.stage!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return
        val mc=Minecraft.getInstance();val p=mc.player ?: return;val level=mc.level ?: return
        if(!p.mainHandItem.`is`(Architect.GUN.get())) return
        val hit=mc.hitResult as? BlockHitResult ?: return
        val tag=p.mainHandItem.get(DataComponents.CUSTOM_DATA)?.copyTag()
        val form=ClientState.snapshot.library.forms[tag?.getString("form").orEmpty().ifEmpty { "inner@1" }] ?: return
        val pos=hit.blockPos.relative(hit.direction);val camera=e.camera.position
        val pose=e.poseStack;pose.pushPose();pose.translate(pos.x-camera.x,pos.y-camera.y,pos.z-camera.z)
        Meshes.draw(Geometry.rotate(form.parts,tag?.getInt("turn") ?: 0),tag?.getString("material").orEmpty().ifEmpty { "minecraft:stone" },pose,mc.renderBuffers().bufferSource(),15728880,0.45f,groups=form.groups,surfaces=Json.materials(tag?.getString("surfaces").orEmpty().ifEmpty { "{}" }),textures=CopycatTextureContext.world(pos))
        mc.renderBuffers().bufferSource().endBatch();pose.popPose()
    }
}
object ClientState {
    var snapshot=Snapshot()
    fun send(c:Command) {
        if(Minecraft.getInstance().connection==null) return
        val json=Json.encode(c)
        if(runCatching { CommandTransport.encode(json) }.isFailure) { snapshot=snapshot.copy(message=ui("request_too_large"));return }
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(Request(json))
    }
}
class CompositeRenderer(ctx:BlockEntityRendererProvider.Context):BlockEntityRenderer<CompositeEntity> {
    override fun render(be:CompositeEntity,partial:Float,pose:PoseStack,buffers:MultiBufferSource,light:Int,overlay:Int) {
        val neighborGroups=linkedMapOf<Direction,List<MaterialGroup>>()
        val neighborTransparent=linkedMapOf<Direction,Set<String>>()
        val neighbors=buildMap<Direction,List<Part>> {
            for(d in Direction.entries) {
                val pos=be.blockPos.relative(d);val other=be.level?.getBlockEntity(pos) as? CompositeEntity
                if(other!=null) {
                    put(d,other.parts);neighborGroups[d]=other.groups
                    neighborTransparent[d]=other.groups.filter { group -> runCatching { CopycatMaterials.state(other.surfaces[group.id] ?: other.material).block is net.minecraft.world.level.block.TransparentBlock }.getOrDefault(false) }.map { it.id }.toSet()
                } else if(be.level?.getBlockState(pos)==runCatching { Economy.state(be.material) }.getOrNull()) {
                    put(d,listOf(Part("copycats:copycat_block")))
                    if(be.level?.getBlockState(pos)?.block is net.minecraft.world.level.block.TransparentBlock) neighborTransparent[d]=setOf("body")
                }
            }
        }
        Meshes.draw(be.parts,be.material,pose,buffers,light,neighbors=neighbors,groups=be.groups,surfaces=be.surfaces,neighborGroups=neighborGroups,neighborTransparent=neighborTransparent,textures=CopycatTextureContext.world(be.blockPos))
    }
}
data class Face(val direction:Direction,override val vertices:List<FloatArray>,val group:String,
                override val normal:FloatArray=floatArrayOf(direction.stepX.toFloat(),direction.stepY.toFloat(),direction.stepZ.toFloat())):TextureSurface
object Meshes {
    private data class MeshKey(val parts:List<Part>,val neighbors:Map<Direction,List<Part>>,val groups:List<MaterialGroup>,val transparent:Set<String>,val neighborGroups:Map<Direction,List<MaterialGroup>>,val neighborTransparent:Map<Direction,Set<String>>)
    private val cache=object:LinkedHashMap<MeshKey,List<Face>>(64,0.75f,true) { override fun removeEldestEntry(e:MutableMap.MutableEntry<MeshKey,List<Face>>?)=size>1024 }
    fun mesh(parts:List<Part>,neighbors:Map<Direction,List<Part>> = emptyMap(),groups:List<MaterialGroup> = DEFAULT_GROUPS,transparent:Set<String> = emptySet(),neighborGroups:Map<Direction,List<MaterialGroup>> = emptyMap(),neighborTransparent:Map<Direction,Set<String>> = emptyMap()):List<Face> {
        return cache.getOrPut(MeshKey(parts,neighbors,groups,transparent,neighborGroups,neighborTransparent)) {
            val resolved=Geometry.resolve(parts,groups)
            val solid=resolved.solid
            if(resolved.exactSolid!=null || neighbors.values.any { ps -> ps.any(ExactGeometry::usesExact) } || neighborTransparent.values.any { it.isNotEmpty() }) return@getOrPut exactMesh(resolved,neighbors,transparent,neighborGroups,neighborTransparent)
            val adjacent=neighbors.mapValues { Geometry.solid(it.value) }
            val result=mutableListOf<Face>()
            for(d in Direction.entries) for(w in 0..15) {
                val grid=Array(16) { arrayOfNulls<String>(16) }
                fun xyz(u:Int,v:Int)=when(d.axis) { Direction.Axis.X->intArrayOf(w,v,u);Direction.Axis.Y->intArrayOf(u,w,v);else->intArrayOf(u,v,w) }
                for(v in 0..15) for(u in 0..15) { val a=xyz(u,v);val x=a[0]+d.stepX;val y=a[1]+d.stepY;val z=a[2]+d.stepZ
                    val outside=x !in 0..15 || y !in 0..15 || z !in 0..15
                    val covered=if(outside) adjacent[d]?.has(Math.floorMod(x,16),Math.floorMod(y,16),Math.floorMod(z,16)) ?: false else solid.has(x,y,z)
                    val owner=resolved.owner(a[0],a[1],a[2]);val neighbor=resolved.owner(x,y,z)
                    // Only the opaque side of a glass/material interface is emitted.
                    // The transparent group never contributes a coincident back face.
                    val interfaceVisible=!outside && owner!=neighbor && neighbor in transparent && owner !in transparent
                    grid[v][u]=if(!covered || interfaceVisible) owner else null }
                for(v in 0..15) for(u in 0..15) if(grid[v][u]!=null) {
                    val owner=grid[v][u]!!
                    var U=u+1;while(U<16 && grid[v][U]==owner) U++
                    var V=v+1;while(V<16 && (u until U).all { grid[V][it]==owner }) V++
                    for(a in v until V) for(b in u until U) grid[a][b]=null
                    val plane=(w+if(d.axisDirection==Direction.AxisDirection.POSITIVE) 1 else 0)/16f
                    fun vertex(a:Int,b:Int):FloatArray=when(d.axis) { Direction.Axis.X->floatArrayOf(plane,b/16f,a/16f);Direction.Axis.Y->floatArrayOf(a/16f,plane,b/16f);else->floatArrayOf(a/16f,b/16f,plane) }
                    val points=listOf(vertex(u,v),vertex(U,v),vertex(U,V),vertex(u,V))
                    val reverse=when(d) { Direction.NORTH,Direction.EAST,Direction.UP->true;else->false }
                    result+=Face(d,if(reverse) points.reversed() else points,owner)
                }
            };result
        }
    }
    private fun exactMesh(resolved:ResolvedGeometry,neighbors:Map<Direction,List<Part>>,transparent:Set<String>,neighborGroups:Map<Direction,List<MaterialGroup>>,neighborTransparent:Map<Direction,Set<String>>):List<Face> {
        val adjacent=neighbors.map { (d,ps) ->
            val geometry=Geometry.resolve(ps,neighborGroups[d] ?: ps.map { it.group }.distinct().mapIndexed { i,id -> MaterialGroup(id,id.take(32),priority=i) })
            val offset=P(d.stepX*16,d.stepY*16,d.stepZ*16)
            val regions=(if(geometry.exactSolid!=null) geometry.exactOwned else geometry.owned.mapValues { ExactVolume.fromSolid(it.value) }).mapValues { it.value.translated(offset) }
            ResolvedGeometry(Solid(),emptyMap(),regions.values.fold(ExactVolume()) { a,b -> a.union(b) },regions) to neighborTransparent[d].orEmpty()
        }
        val result=mutableListOf<Face>()
        for((surface,group) in resolved.visibleSurfaces(transparent,adjacent)) {
                val plane=surface.plane
                val length=kotlin.math.sqrt((plane.x.toDouble()*plane.x+plane.y.toDouble()*plane.y+plane.z.toDouble()*plane.z)).toFloat()
                val normal=floatArrayOf(plane.x/length,plane.y/length,plane.z/length)
                val direction=when {
                    plane.y>0->Direction.UP;plane.y<0->Direction.DOWN
                    kotlin.math.abs(plane.x)>=kotlin.math.abs(plane.z)->if(plane.x>0) Direction.EAST else Direction.WEST
                    else->if(plane.z>0) Direction.SOUTH else Direction.NORTH
                }
                val polygon=surface.vertices
                for(i in 1 until polygon.size-1) {
                    fun vertex(p:ExactPoint)=floatArrayOf((p.x.toDouble()/16).toFloat(),(p.y.toDouble()/16).toFloat(),(p.z.toDouble()/16).toFloat())
                    val a=vertex(polygon[0]);val b=vertex(polygon[i]);val c=vertex(polygon[i+1])
                    result+=Face(direction,listOf(a,b,c,c),group,normal)
                }
        }
        return result
    }
    fun draw(parts:List<Part>,material:String,pose:PoseStack,buffers:MultiBufferSource,light:Int,alpha:Float=1f,neighbors:Map<Direction,List<Part>> = emptyMap(),groups:List<MaterialGroup> = DEFAULT_GROUPS,surfaces:Map<String,String> = emptyMap(),neighborGroups:Map<Direction,List<MaterialGroup>> = emptyMap(),neighborTransparent:Map<Direction,Set<String>> = emptyMap(),textures:CopycatTextureContext=CopycatTextureContext()) {
        val mc=Minecraft.getInstance()
        val models=(surfaces.values+material).distinct().associateWith { id ->
            val state=CopycatTextureContext.materialState(id);state to mc.blockRenderer.getBlockModel(state)
        }
        val cell=CopycatTextureContext.Cell(parts,groups,material,surfaces)
        val consumer=buffers.getBuffer(RenderType.entityTranslucentCull(InventoryMenu.BLOCK_ATLAS))
        val matrix=pose.last()
        val transparent=groups.filter { models.getValue(surfaces[it.id] ?: material).first.block is net.minecraft.world.level.block.TransparentBlock }.map { it.id }.toSet()
        data class TextureKey(val material:String,val direction:Direction,val normal:List<Float>,val plane:Float)
        val mappings=mutableMapOf<TextureKey,MaterialQuadUv?>()
        for(face in mesh(parts,neighbors,groups,transparent,neighborGroups,neighborTransparent)) {
            val id=surfaces[face.group] ?: material
            val (state,model)=models.getValue(id)
            val key=TextureKey(id,face.direction,face.normal.toList(),(0..2).sumOf { (face.normal[it]*face.vertices[0][it]).toDouble() }.toFloat())
            val uv=mappings.getOrPut(key) {
                val data=model.getModelData(textures.view(cell,face),textures.position,state,net.neoforged.neoforge.client.model.data.ModelData.EMPTY)
                val quads=model.getQuads(state,face.direction,RandomSource.create(state.getSeed(textures.position)),data,null)
                val quad=quads.firstOrNull() ?: model.getQuads(state,null,RandomSource.create(state.getSeed(textures.position)),data,null).firstOrNull { it.direction==face.direction }
                quad?.let(::MaterialQuadUv)
            }
            val shade=face.normal[1]*face.normal[1]+0.8f*face.normal[0]*face.normal[0]+0.65f*face.normal[2]*face.normal[2]
            for(v in face.vertices) {
                val (u,t)=uv?.at(v) ?: run {
                    val (a,b)=when(face.direction.axis) { Direction.Axis.X->v[2] to (1-v[1]);Direction.Axis.Y->v[0] to v[2];else->v[0] to (1-v[1]) }
                    model.particleIcon.getU(a) to model.particleIcon.getV(b)
                }
                consumer.addVertex(matrix,v[0],v[1],v[2]).setColor(shade,shade,shade,alpha).setUv(u,t)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(matrix,face.normal[0],face.normal[1],face.normal[2])
            }
        }
    }
    fun preview(g:GuiGraphics,x:Int,y:Int,scale:Float,yaw:Float,parts:List<Part>,material:String="minecraft:stone",pitch:Float=25f,groups:List<MaterialGroup> = DEFAULT_GROUPS,surfaces:Map<String,String> = emptyMap(),showMaterials:Boolean=true) {
        g.flush();val p=g.pose();p.pushPose();p.translate(x.toDouble(),y.toDouble(),180.0);p.scale(scale,-scale,scale)
        p.mulPose(Axis.XP.rotationDegrees(pitch));p.mulPose(Axis.YP.rotationDegrees(yaw));p.translate(-0.5,-0.5,-0.5)
        if(showMaterials) draw(parts,material,p,g.bufferSource(),15728880,groups=groups,surfaces=surfaces)
        else {
            val consumer=g.bufferSource().getBuffer(RenderType.debugQuads());val matrix=p.last()
            for(face in mesh(parts,groups=groups)) {
                val shade=0.82f*(face.normal[1]*face.normal[1]+0.8f*face.normal[0]*face.normal[0]+0.65f*face.normal[2]*face.normal[2])
                for(v in face.vertices) consumer.addVertex(matrix,v[0],v[1],v[2]).setColor(shade,shade,shade,1f)
            }
        }
        val lines=g.bufferSource().getBuffer(RenderType.lines());val matrix=p.last()
        for(axis in 0..2) for(a in 0..1) for(b in 0..1) {
            val start=when(axis) { 0->floatArrayOf(0f,a.toFloat(),b.toFloat());1->floatArrayOf(a.toFloat(),0f,b.toFloat());else->floatArrayOf(a.toFloat(),b.toFloat(),0f) }
            val end=start.copyOf();end[axis]=1f
            for(v in listOf(start,end)) lines.addVertex(matrix,v[0],v[1],v[2]).setColor(0.35f,0.65f,0.75f,0.6f).setNormal(matrix,if(axis==0) 1f else 0f,if(axis==1) 1f else 0f,if(axis==2) 1f else 0f)
        }
        g.flush();p.popPose()
    }
}
