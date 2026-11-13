package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

data class BuildingPreviewViewport(val x:Int,val y:Int,val width:Int,val height:Int) {
    fun contains(px:Double,py:Double)=px>=x && px<x+width && py>=y && py<y+height
}

/** A view of the selected definition, independent of the final building and its visibility filter. */
object BuildingPreviewRenderer {
    private data class Style(val node:BuildingNode,val mode:String,val yaw:Float,val sampleRoof:Boolean,val footprint:Set<P>)

    /** Reuse the finished inspector image; render changed scenes in bounded slices. */
    internal fun drawCached(g:GuiGraphics,viewport:BuildingPreviewViewport,node:BuildingNode,ready:Result<Plan>,mode:String,yaw:Float,sampleRoof:Boolean=false,roomFootprint:Set<P> = emptySet()) {
        val plan=ready.getOrNull()
        if(plan==null || plan.placements.isEmpty()) {
            draw(g,viewport,node,ready,mode,yaw,sampleRoof,roomFootprint)
            return
        }
        val (x,y,w,h)=viewport
        GraphPreviewThumbnails.draw(g,x,y,w,h,plan,Style(node,mode,if(mode=="2d") 0f else yaw,sampleRoof,roomFootprint)) {
            var renderScene:((GuiGraphics)->Boolean)?=null
            var sceneViewport:BuildingPreviewViewport?=null
            val local=BuildingPreviewViewport(0,0,w,h)
            val roomTop=mode=="2d" && node.kind==BuildingKind.ROOM
            val shown=if(roomTop) plan.copy(placements=plan.placements.filter { it.component!="ceiling" }) else plan
            val projection=projection(node,mode)
            val render:(GuiGraphics)->Boolean={ target ->
                if(renderScene==null) {
                    draw(target,local,node,ready,mode,yaw,sampleRoof,roomFootprint,clip=false) {
                        sceneViewport=it
                        renderScene=BuildingScene.thumbnail(shown,it,yaw,projection)
                    }
                }
                val complete=renderScene?.invoke(target) ?: true
                // The room outline must be drawn after the last geometry batch.
                if(complete && roomTop && shown.placements.isNotEmpty()) {
                    sceneViewport?.let { roomOutline(target,it,shown,roomFootprint) }
                }
                complete
            }
            render
        }
    }

    private fun projection(node:BuildingNode,mode:String)=if(mode!="2d") "3d" else if(node.kind in setOf(BuildingKind.COPYCAT_MIRROR,BuildingKind.COPYCAT_ROTATE,BuildingKind.SEGMENT,BuildingKind.PATTERN,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_ROTATE)) "front" else "top"

    fun draw(g:GuiGraphics,viewport:BuildingPreviewViewport,node:BuildingNode,ready:Result<Plan>,mode:String,yaw:Float,sampleRoof:Boolean=false,roomFootprint:Set<P> = emptySet(),clip:Boolean=true,sceneRenderer:((BuildingPreviewViewport)->Unit)?=null) {
        val (x,y,w,h)=viewport
        val font=Minecraft.getInstance().font
        ArchitectTheme.viewport(g,x,y,w,h)
        // Graph thumbnails already have a GUIContext scissor transformed by native pan/zoom.
        // GuiGraphics scissors use screen coordinates, so only standalone inspectors add one here.
        if(clip) g.enableScissor(x+2,y+2,x+w-2,y+h-2)
        try {
            val pending=false
            if(pending || ready.isFailure) {
                val message=if(pending) tr("bg_preview_roof_pending") else Component.literal(ready.exceptionOrNull()?.message.orEmpty())
                val lines=font.split(message,(w-20).coerceAtLeast(1))
                lines.take(((h-12)/11).coerceAtLeast(1)).forEachIndexed { i,line -> g.drawString(font,line,x+10,y+8+i*11,if(pending) ArchitectTheme.brass else ArchitectTheme.danger,false) }
                return
            }
            val plan=ready.getOrThrow()
            val roomTop=mode=="2d" && node.kind==BuildingKind.ROOM
            val shown=if(roomTop) plan.copy(placements=plan.placements.filter { it.component!="ceiling" }) else plan
            val projection=projection(node,mode)
            val showHint=h>=100 || roomTop
            val pendingConnections=node.kind!=BuildingKind.BUILDING && (node.features?.extensions.orEmpty().isNotEmpty() || !node.attachments.isNullOrEmpty())
            val footer=(if(showHint) 14 else 0)+(if(sampleRoof) 13 else 0)+(if(pendingConnections) 13 else 0)
            if(shown.placements.isNotEmpty()) {
                val scene=BuildingPreviewViewport(x+5,y+5,(w-10).coerceAtLeast(1),(h-footer-9).coerceAtLeast(1))
                if(sceneRenderer!=null) sceneRenderer(scene)
                else BuildingScene.draw(g,shown,scene.x,scene.y,scene.width,scene.height,yaw,projection=projection)
                if(roomTop) roomOutline(g,scene,shown,roomFootprint)
            }
            else {
                val label=ui(if(plan.clear.isNotEmpty()) "bg_preview_air" else "node_empty_result")
                g.drawString(font,font.plainSubstrByWidth(label,(w-16).coerceAtLeast(1)),x+8,y+8,ArchitectTheme.muted,false)
            }
            val hint=ui(when { mode=="3d"->"node_preview_hint";roomTop->"bg_preview_room_top";projection=="front"->"bg_preview_front";else->"bg_preview_top" })
            if(showHint) g.drawString(font,font.plainSubstrByWidth(hint,(w-16).coerceAtLeast(1)),x+8,y+h-footer,ArchitectTheme.muted,false)
            if(pendingConnections) g.drawString(font,font.plainSubstrByWidth(ui("semantic_pending_connections"),(w-16).coerceAtLeast(1)),x+8,y+h-(if(sampleRoof) 26 else 13),ArchitectTheme.brass,false)
            if(sampleRoof) g.drawString(font,font.plainSubstrByWidth(ui(if(node.kind in setOf(BuildingKind.BAY_WINDOW,BuildingKind.DORMER)) "special_sample_hint" else "bg_preview_roof_sample"),(w-16).coerceAtLeast(1)),x+8,y+h-13,ArchitectTheme.brass,false)
        } finally {
            g.flush()
            if(clip) g.disableScissor()
        }
    }
    private fun roomOutline(g:GuiGraphics,viewport:BuildingPreviewViewport,plan:Plan,footprint:Set<P>) {
        val loX=plan.placements.minOf { it.pos.x };val hiX=plan.placements.maxOf { it.pos.x }+1
        val loZ=plan.placements.minOf { it.pos.z };val hiZ=plan.placements.maxOf { it.pos.z }+1
        val scale=minOf(viewport.width.toDouble()/(hiX-loX),viewport.height.toDouble()/(hiZ-loZ))*0.88
        fun px(value:Int)=(viewport.x+viewport.width/2.0+(value-(loX+hiX)/2.0)*scale).toInt()
        fun pz(value:Int)=(viewport.y+viewport.height/2.0+(value-(loZ+hiZ)/2.0)*scale).toInt()
        // The occupied interior remains legible when walls and floor use the same material.
        g.pose().pushPose()
        try {
            g.pose().translate(0.0,0.0,400.0)
            for(p in footprint) {
                val left=px(p.x);val right=px(p.x+1);val top=pz(p.z);val bottom=pz(p.z+1)
                if(p+P(0,0,-1) !in footprint) g.fill(left,top,right,top+1,ArchitectTheme.brass)
                if(p+P(0,0,1) !in footprint) g.fill(left,bottom-1,right,bottom,ArchitectTheme.brass)
                if(p+P(-1,0,0) !in footprint) g.fill(left,top,left+1,bottom,ArchitectTheme.brass)
                if(p+P(1,0,0) !in footprint) g.fill(right-1,top,right,bottom,ArchitectTheme.brass)
            }
        } finally { g.flush();g.pose().popPose() }
    }
}
