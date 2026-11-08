package de.teutonstudio.copycatarchitect.client

import com.mojang.blaze3d.vertex.VertexConsumer
import de.teutonstudio.copycatarchitect.Architect
import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.PlacementService
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.phys.AABB
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent

/** One local draft, bound to the exact world in which its architect table was opened. */
object BuildingWorldPreview {
    private data class Draft(val owner:BuildingScreen,val level:ClientLevel,val source:Plan,val origin:P,val turn:Int,val table:P?,val keep:Boolean)
    private class Projected(val plan:Plan) {
        val cells=plan.placements.associateBy(Placement::pos)
        val neighbors=mutableMapOf<P,PlanMeshNeighbors>()
        val nativeStates=mutableMapOf<P,net.minecraft.world.level.block.state.BlockState>()
    }
    private var draft:Draft?=null
    private val projectionJob=PreviewJobs<Unit,Projected>()

    private fun clear() {
        draft=null
        projectionJob.clear()
    }
    val persistent get()=draft?.keep==true

    fun update(owner:BuildingScreen,plan:Plan?,origin:P,turn:Int,table:P?,keep:Boolean) {
        val level=Minecraft.getInstance().level ?: run { clear();return }
        if(plan==null) { if(draft?.owner===owner) clear();return }
        val previous=draft
        if(previous!=null && previous.owner===owner && previous.level===level && previous.source===plan && previous.origin==origin && previous.turn==turn && previous.table==table) {
            if(previous.keep!=keep) draft=previous.copy(keep=keep)
            return
        }
        projectionJob.clear()
        draft=Draft(owner,level,plan,origin,turn,table,keep)
        projectionJob.request(Unit) { Projected(BuildingProjection(table=P(),turn=turn).plan(plan,origin)) }
    }

    fun release(owner:BuildingScreen) { if(draft?.owner===owner && !persistent) clear() }

    fun tick(event:ClientTickEvent.Post) {
        val current=draft ?: return
        val mc=Minecraft.getInstance()
        if(mc.level!==current.level || mc.player==null) { clear();return }
        current.table?.let { table ->
            val pos=PlacementService.world(table)
            if(current.level.hasChunkAt(pos) && !current.level.getBlockState(pos).`is`(Architect.TABLE.get())) clear()
        }
    }

    fun render(event:RenderLevelStageEvent) {
        if(event.stage!=RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) return
        val current=draft ?: return
        val mc=Minecraft.getInstance()
        if(mc.level!==current.level) { clear();return }
        if(mc.screen===current.owner) {
            if(!WorldPeek.active) return
        } else if(!current.keep) return
        val projected=projectionJob.request(Unit) {
            Projected(BuildingProjection(table=P(),turn=current.turn).plan(current.source,current.origin))
        }?.getOrNull() ?: return
        val cells=projected.cells
        val camera=event.camera.position
        val pose=event.poseStack;val buffers=mc.renderBuffers().bufferSource()
        val ghost=MultiBufferSource { GhostVertexConsumer(buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS))) }
        for(cell in projected.plan.placements) {
            if(camera.distanceToSqr(cell.pos.x+0.5,cell.pos.y+0.5,cell.pos.z+0.5)>192.0*192.0) continue
            if(!event.frustum.isVisible(AABB(cell.pos.x.toDouble(),cell.pos.y.toDouble(),cell.pos.z.toDouble(),cell.pos.x+1.0,cell.pos.y+1.0,cell.pos.z+1.0))) continue
            pose.pushPose()
            pose.translate(cell.pos.x-camera.x,cell.pos.y-camera.y,cell.pos.z-camera.z)
            try {
                if(cell.block.isEmpty()) {
                    val adjacent=projected.neighbors.getOrPut(cell.pos) { PlanMeshNeighbors.at(cell.pos,cells) }
                    Meshes.draw(cell.parts,cell.material,pose,buffers,15728880,0.42f,neighbors=adjacent.parts,groups=cell.groups,surfaces=cell.surfaces,neighborGroups=adjacent.groups,neighborTransparent=adjacent.transparent,textures=CopycatTextureContext.plan(cell.pos,cells))
                }
                else mc.blockRenderer.renderSingleBlock(projected.nativeStates.getOrPut(cell.pos) { LandscapeBlockPreview.state(cell,cells) },pose,ghost,15728880,OverlayTexture.NO_OVERLAY)
            } finally { pose.popPose() }
        }
        current.table?.let { table ->
            pose.pushPose();pose.translate(table.x-camera.x,table.y-camera.y,table.z-camera.z)
            LevelRenderer.renderLineBox(pose,buffers.getBuffer(RenderType.lines()),-0.005,-0.005,-0.005,1.005,1.005,1.005,0.95f,0.7f,0.2f,1f)
            pose.popPose()
        }
        buffers.endBatch()
    }

    /** Preserve native models (including carpets), applying the same translucent tint as copycats. */
    private class GhostVertexConsumer(private val target:VertexConsumer):VertexConsumer {
        override fun addVertex(x:Float,y:Float,z:Float):VertexConsumer { target.addVertex(x,y,z);return this }
        override fun setColor(r:Int,g:Int,b:Int,a:Int):VertexConsumer { target.setColor(r,g,b,(a*0.42f).toInt());return this }
        override fun setUv(u:Float,v:Float):VertexConsumer { target.setUv(u,v);return this }
        override fun setUv1(u:Int,v:Int):VertexConsumer { target.setUv1(u,v);return this }
        override fun setUv2(u:Int,v:Int):VertexConsumer { target.setUv2(u,v);return this }
        override fun setNormal(x:Float,y:Float,z:Float):VertexConsumer { target.setNormal(x,y,z);return this }
    }
}
