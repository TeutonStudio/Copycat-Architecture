package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.Architect
import de.teutonstudio.copycatarchitect.server.CompositeEntity
import de.teutonstudio.copycatarchitect.server.Library
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.client.model.data.ModelData

/** Compare final native CT quads with copycat quads after the real texture atlas has loaded. */
internal object ConnectedTextureSmoke {
    private var ticks=0
    private var failed=false
    private val cube=listOf(Part("copycats:copycat_block"))
    private val glass="create:framed_glass"
    private val mc get()=Minecraft.getInstance()
    private val largePreview by lazy {
        val initial=BuildingGraph.explicitStarter(joinWallEnds=false)
        val layout=initial.nodes.single { it.id=="wall_layout" }.copy(length=24,height=24,layoutCells=emptyList())
        val graph=initial.replace(layout)
        val building=Building(graph=graph,palette=Building().palette+("wall" to glass))
        val library=Library(forms=linkedMapOf("full@1" to Form(id="full",parts=cube)))
        BuildingNodePreviews({building}) { library }.get("wall")
    }
    private fun cropped(x:Int,y:Int,z:Int,maxX:Int,maxY:Int,maxZ:Int,group:String="body"):Part {
        val solid=Solid()
        for(a in x until maxX) for(b in y until maxY) for(c in z until maxZ) solid.set(a,b,c)
        return Part("copycats:copycat_block",group=group,cutMask=solid.mask())
    }
    private fun placement(pos:P,parts:List<Part> = cube,material:String=glass)=Placement(pos,parts,material)
    private fun quads(context:CopycatTextureContext,cell:CopycatTextureContext.Cell,face:Face,reference:Map<BlockPos,net.minecraft.world.level.block.state.BlockState>?=null):List<Float> {
        val state=cell.state(face.group);val model=mc.blockRenderer.getBlockModel(state)
        val view=context.view(cell,face)
        val world=if(reference==null) view else object:BlockAndTintGetter by view {
            override fun getBlockState(pos:BlockPos)=reference[pos] ?: Blocks.AIR.defaultBlockState()
        }
        val data=model.getModelData(world,context.position,state,ModelData.EMPTY)
        return model.getQuads(state,face.direction,RandomSource.create(42),data,null).flatMap { quad ->
            val uv=MaterialQuadUv(quad)
            // Use the actual cropped geometry, not just corners of the original cube.
            face.vertices.flatMap { v -> uv.at(v).let { listOf(it.first,it.second) } }
        }
    }
    private fun verify() {
        var shifts=0
        for(direction in Direction.entries) {
            val placements=buildMap {
                for(x in -1..1) for(y in -1..1) for(z in -1..1) {
                    if(x*direction.stepX+y*direction.stepY+z*direction.stepZ==0) put(P(x,y,z),placement(P(x,y,z)))
                }
            }
            val cell=CopycatTextureContext.Cell(cube,material=glass)
            val face=cell.faces.first { it.direction==direction }
            val connected=CopycatTextureContext.plan(P(),placements)
            val actual=quads(connected,cell,face)
            val native=placements.keys.associate { BlockPos(it.x,it.y,it.z) to cell.state("body") }
            check(actual.isNotEmpty() && actual==quads(connected,cell,face,native)) { "Native CT mismatch on $direction" }
            val block=Architect.COMPOSITE.get().defaultBlockState()
            val entities=placements.keys.associate { p ->
                val pos=BlockPos(p.x,p.y,p.z)
                pos to CompositeEntity(pos,block).apply { parts=cube;material=glass }
            }
            val placedWorld=object:BlockAndTintGetter by connected.view(cell,face) {
                override fun getBlockState(pos:BlockPos)=if(pos in entities) block else Blocks.AIR.defaultBlockState()
                override fun getBlockEntity(pos:BlockPos)=entities[pos]
            }
            check(actual==quads(CopycatTextureContext.world(BlockPos.ZERO,placedWorld),cell,face)) { "Placed copycats and preview must have identical CT UVs on $direction" }
            val tangent=Direction.entries.first { it.axis!=direction.axis }
            check(block.getAppearance(placedWorld,BlockPos.ZERO,direction,cell.state("body"),BlockPos.ZERO.relative(tangent))==cell.state("body")) { "Native neighbors must recognize a copycat coating on $direction" }
            if(actual!=quads(CopycatTextureContext(),cell,face)) shifts++
            val removed=placements.mapValues { (_,p) -> p.copy(material="minecraft:stone") }+(P() to placement(P()))
            check(quads(CopycatTextureContext.plan(P(),removed),cell,face)==quads(CopycatTextureContext(),cell,face)) { "Recolor must disconnect $direction" }
        }
        check(shifts==6) { "All six native CT faces must change: $shifts" }
        val panel=listOf(Part("copycats:copycat_slab"))
        val cell=CopycatTextureContext.Cell(panel,material=glass)
        val top=cell.faces.first { it.direction==Direction.UP }
        val matching=mapOf(P() to placement(P(),panel),P(1,0,0) to placement(P(1,0,0),panel))
        val context=CopycatTextureContext.plan(P(),matching)
        check(context.view(cell,top).getBlockState(BlockPos(1,0,0))==cell.state("body")) { "Coplanar half blocks must connect" }
        val otherHeight=matching+(P(1,0,0) to placement(P(1,0,0),cube))
        check(CopycatTextureContext.plan(P(),otherHeight).view(cell,top).getBlockState(BlockPos(1,0,0)).isAir) { "Different surface heights must not connect" }
        val block=Architect.COMPOSITE.get().defaultBlockState()
        val half=CompositeEntity(BlockPos.ZERO,block).apply { parts=panel;material=glass }
        val halfWorld=object:BlockAndTintGetter by context.view(cell,top) {
            override fun getBlockEntity(pos:BlockPos)=if(pos==BlockPos.ZERO) half else null
        }
        check(block.getAppearance(halfWorld,BlockPos.ZERO,Direction.UP,cell.state("body"),BlockPos(1,0,0))==block) { "A native full block must not connect to a half-height top face" }
        val gap=matching+(P(1,0,0) to placement(P(1,0,0),listOf(cropped(2,0,0,14,8,16))))
        check(CopycatTextureContext.plan(P(),gap).view(cell,top).getBlockState(BlockPos(1,0,0)).isAir) { "A physical gap must disconnect" }
        val groups=listOf(MaterialGroup(),MaterialGroup("cap","Cap",priority=1))
        val split=listOf(cropped(0,0,0,16,8,16),cropped(0,8,0,16,16,16,"cap"))
        val neighbor=Placement(P(1,0,0),split,"minecraft:stone",groups=groups,surfaces=mapOf("cap" to glass))
        val base=CopycatTextureContext.Cell(cube,material=glass);val face=base.faces.first { it.direction==Direction.UP }
        check(CopycatTextureContext.plan(P(),mapOf(neighbor.pos to neighbor)).view(base,face).getBlockState(BlockPos(1,0,0))==base.state("body")) { "Connection must resolve the coating of the visible group" }
        val native=CopycatTextureContext { pos -> if(pos==BlockPos(1,0,0)) CopycatTextureContext.Cell(cube,native=base.state("body")) else null }
        check(native.view(base,face).getBlockState(BlockPos(1,0,0))==base.state("body")) { "Native material neighbors must connect too" }
        // CT corners use diagonals, not only the six face neighbors.
        val diagonal=placement(P(1,0,1))
        check(CopycatTextureContext.plan(P(),mapOf(diagonal.pos to diagonal)).view(base,face).getBlockState(BlockPos(1,0,1))==base.state("body"))
        val obstruction=placement(P(1,1,0))
        check(!CopycatTextureContext.plan(P(),mapOf(obstruction.pos to obstruction)).view(base,face).getBlockState(BlockPos(1,1,0)).isAir)
        val large=largePreview.plan.getOrThrow()
        check(large.placements.size==576) { "Node previews must retain every cell and CT neighbor above the former 512-cell cutoff: ${large.placements.size}" }
        val centre=large.placements.single { it.pos==P(12,12,0) }
        val largeCell=CopycatTextureContext.Cell(centre.parts,centre.groups,centre.material,centre.surfaces)
        val largeFace=largeCell.faces.first { it.direction==Direction.NORTH }
        val largeContext=CopycatTextureContext.plan(centre.pos,large.placements.associateBy { it.pos })
        val nativeGrid=buildMap {
            for(x in 11..13) for(y in 11..13) put(BlockPos(x,y,0),largeCell.state("body"))
        }
        check(quads(largeContext,largeCell,largeFace)==quads(largeContext,largeCell,largeFace,nativeGrid)) { "The actual node-preview evaluator must retain full native CT on a large wall" }
        println("ARCHITECT LARGE NODE PREVIEW CT PASSED: all 576 cells retained, native centre UVs and full neighbor topology")
        println("ARCHITECT CONNECTED TEXTURE MODEL CHECKS PASSED: native atlas UVs on all 6 faces, recolor, half blocks, heights, gaps, visible material groups, native neighbors, diagonals and obstructions")
    }
    fun tick():Boolean {
        if(failed || ticks==0 && (mc.screen==null || mc.overlay!=null)) return false
        ticks++
        try {
            when(ticks) {
                1 -> { verify();mc.options.guiScale().set(1);mc.resizeDisplay();mc.setScreen(TextureScreen()) }
                10 -> { Screenshot.grab(mc.gameDirectory,"architect-connected-large-normal.png",mc.mainRenderTarget) { println(it.string) };mc.setScreen(NativeComparisonScreen()) }
                20 -> { nativePixels();Screenshot.grab(mc.gameDirectory,"architect-connected-native-normal.png",mc.mainRenderTarget) { println(it.string) };mc.options.guiScale().set(2);mc.resizeDisplay() }
                30 -> { nativePixels();Screenshot.grab(mc.gameDirectory,"architect-connected-native-compact.png",mc.mainRenderTarget) { println(it.string) };mc.setScreen(TextureScreen()) }
                40 -> { Screenshot.grab(mc.gameDirectory,"architect-connected-large-compact.png",mc.mainRenderTarget) { println(it.string) };println("ARCHITECT CONNECTED TEXTURE SMOKE PASSED");mc.stop() }
            }
        } catch(t:Throwable) { failed=true;t.printStackTrace();println("ARCHITECT CONNECTED TEXTURE SMOKE FAILED: ${t.message}");mc.stop() }
        return true
    }
    /** Independent native BakedQuad rendering catches backfaces as well as UV errors. */
    private fun nativePixels() {
        val screen=mc.screen as NativeComparisonScreen
        Screenshot.takeScreenshot(mc.mainRenderTarget).use { pixels ->
            val sx=pixels.width.toDouble()/screen.width;val sy=pixels.height.toDouble()/screen.height
            val half=screen.width/2;var changed=0;var compared=0;var content=0
            for(y in (45*sy).toInt() until ((screen.height-13)*sy).toInt()) for(x in (8*sx).toInt() until ((half-8)*sx).toInt()) {
                val a=pixels.getPixelRGBA(x,y);val b=pixels.getPixelRGBA(x+(half*sx).toInt(),y)
                compared++
                if(a and 0xffffff!=0x2a2115 || b and 0xffffff!=0x2a2115) content++
                if((0..2).any { channel -> kotlin.math.abs((a ushr (8*channel) and 255)-(b ushr (8*channel) and 255))>8 }) changed++
            }
            check(content>200) { "Native comparison must contain actual rendered glass" }
            check(changed*100<compared*2) { "Copycat preview differs from native culled CT geometry: $changed / $compared pixels" }
            println("ARCHITECT NATIVE CT PIXELS PASSED ${screen.width}x${screen.height}: $changed / $compared differing pixels")
        }
    }
    private class NativeComparisonScreen:Screen(Component.literal("Native CT comparison")) {
        private val plan=Plan(buildList { for(x in 0..2) for(y in 0..2) add(placement(P(x,y,0))) },emptyList(),emptyList(),0,emptyMap())
        override fun render(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
            g.fill(0,0,width,height,0xff15212a.toInt())
            g.drawCenteredString(font,"Copycat-Vorschau",width/4,18,0xffffff)
            g.drawCenteredString(font,"Natives Blockmodell",width*3/4,18,0xffffff)
            BuildingScene.draw(g,plan,8,45,width/2-16,height-58,35f)
            native(g,width/2+8,45,width/2-16,height-58)
        }
        private fun native(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int) {
            val q=org.joml.Quaternionf().rotationX(Math.toRadians(25.0).toFloat()).rotateY(Math.toRadians(35.0).toFloat())
            val corners=buildList { for(a in listOf(-1.5f,1.5f)) for(b in listOf(-1.5f,1.5f)) for(c in listOf(-0.5f,0.5f)) add(q.transform(org.joml.Vector3f(a,b,c))) }
            val scale=minOf(w/(corners.maxOf { it.x }-corners.minOf { it.x }),h/(corners.maxOf { it.y }-corners.minOf { it.y }))*0.88f
            val state=CopycatTextureContext.materialState(glass);val model=mc.blockRenderer.getBlockModel(state)
            val cells=plan.placements.associate { BlockPos(it.pos.x,it.pos.y,it.pos.z) to state }
            val cell=CopycatTextureContext.Cell(cube,material=glass)
            val delegate=CopycatTextureContext().view(cell,cell.faces.first())
            val world=object:BlockAndTintGetter by delegate {
                override fun getBlockState(pos:BlockPos)=cells[pos] ?: Blocks.AIR.defaultBlockState()
            }
            g.flush();val pose=g.pose();pose.pushPose()
            try {
                pose.translate(x+w/2.0,y+h/2.0,160.0);pose.scale(scale,-scale,scale);pose.mulPose(q);pose.translate(-1.5,-1.5,-0.5)
                val consumer=g.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucentCull(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS))
                for(pos in cells.keys) {
                    val data=model.getModelData(world,pos,state,ModelData.EMPTY)
                    pose.pushPose();pose.translate(pos.x.toDouble(),pos.y.toDouble(),pos.z.toDouble())
                    for(d in Direction.entries) {
                        if(pos.relative(d) in cells) continue
                        val shade=when(d.axis) { Direction.Axis.Y->1f;Direction.Axis.X->0.8f;else->0.65f }
                        for(quad in model.getQuads(state,d,RandomSource.create(state.getSeed(pos)),data,null))
                            consumer.putBulkData(pose.last(),quad,shade,shade,shade,1f,15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                    }
                    pose.popPose()
                }
            } finally { g.flush();pose.popPose() }
        }
    }
    private class TextureScreen:Screen(Component.literal("Connected Textures")) {
        override fun render(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
            g.fill(0,0,width,height,0xff15212a.toInt())
            g.drawCenteredString(font,"Native Connected Textures · Copycat-Vorschau",width/2,12,0xffffff)
            val connected=largePreview
            val disconnected=connected.copy(plan=connected.plan.map { plan -> plan.copy(placements=plan.placements.map { p ->
                if(p.pos.x in 10..13 && p.pos.y in 10..13) p.copy(material="minecraft:stone",surfaces=emptyMap()) else p
            }) })
            g.drawCenteredString(font,"24 × 24 · vollständige Knotenvorschau",width/4,28,0xffffff)
            g.drawCenteredString(font,"mit Materialwechsel in der Mitte",width*3/4,28,0xffffff)
            BuildingNodePreviewRenderer.draw(g,8,45,width/2-16,height-58,connected)
            BuildingNodePreviewRenderer.draw(g,width/2+8,45,width/2-16,height-58,disconnected)
        }
    }
}
