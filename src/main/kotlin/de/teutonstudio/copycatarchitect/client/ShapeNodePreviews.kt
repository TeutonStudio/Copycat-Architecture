package de.teutonstudio.copycatarchitect.client

import com.mojang.math.Axis
import de.teutonstudio.copycatarchitect.domain.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.RenderType

data class ShapeNodePreview(val parts:List<Part> = emptyList(),val rotation:RotationAxis?=null,val turns:Int=0,val error:String="")

/** Cached by graph and groups, independently of the final output's validity and visibility filter. */
class ShapeNodePreviews(private val definition:()->Form,private val disconnected:()->String={ui("disconnected")},private val graph:()->NodeGraph) {
    private var key:Triple<NodeGraph,List<MaterialGroup>,List<Part>?>?=null
    private val values=mutableMapOf<String,ShapeNodePreview>()
    fun get(id:String):ShapeNodePreview {
        val f=definition();val g=graph();val next=Triple(g,f.groups,f.parts.takeIf { f.graph==null })
        if(key!=next) { key=next;values.clear() }
        return values.getOrPut(id) {
            val n=g.nodes.firstOrNull { it.id==id }
            when {
                n==null -> ShapeNodePreview(error=disconnected())
                n.kind==NodeKind.ROTATION -> ShapeNodePreview(rotation=n.rotationAxis,turns=n.quarterTurns)
                else -> runCatching {
                    // Legacy clipped source parts remain viewable without converting the document.
                    val parts=if(f.graph==null && n.kind==NodeKind.OUTPUT) f.parts
                        else if(n.kind==NodeKind.INPUT && (n.part.cutMask.isNotEmpty() || n.part.recipeSource.isNotEmpty())) listOf(
                            PartRotations.rotate(n.part,n.rotationAxis,n.quarterTurns)
                                ?: error("Copycat ${n.part.source} cannot rotate around ${n.rotationAxis}"))
                        else GraphCompiler.intermediate(g,id,f.groups)
                    ShapeNodePreview(parts=if(n.kind==NodeKind.OUTPUT) Geometry.previewParts(parts,f.groups,n.previewGroup) else parts)
                }.getOrElse { ShapeNodePreview(error=it.message ?: disconnected()) }
            }
        }
    }
}

object ShapeNodePreviewRenderer {
    private data class ThumbnailStyle(val groups:List<MaterialGroup>,val surfaces:Map<String,String>,val showMaterials:Boolean)

    internal fun drawThumbnail(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int,preview:ShapeNodePreview,groups:List<MaterialGroup>,surfaces:Map<String,String>,showMaterials:Boolean=true) {
        if(preview.error.isNotEmpty()) {
            draw(g,x,y,w,h,preview,groups,surfaces,showMaterials=showMaterials)
            return
        }
        GraphPreviewThumbnails.draw(g,x,y,w,h,preview,ThumbnailStyle(groups,surfaces,showMaterials)) {
            { target ->
                draw(target,0,0,w,h,preview,groups,surfaces,showMaterials=showMaterials)
                true
            }
        }
    }

    fun draw(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int,preview:ShapeNodePreview,groups:List<MaterialGroup>,surfaces:Map<String,String>,yaw:Float=35f,showMaterials:Boolean=true) {
        ArchitectTheme.inset(g,x,y,w,h,ArchitectTheme.blueprint)
        val font=Minecraft.getInstance().font
        when {
            preview.error.isNotEmpty() -> {
                val lines=font.split(net.minecraft.network.chat.Component.literal(preview.error),(w-10).coerceAtLeast(1))
                lines.take(((h-8)/11).coerceAtLeast(1)).forEachIndexed { i,line -> g.drawString(font,line,x+5,y+5+i*11,ArchitectTheme.danger,false) }
            }
            preview.rotation!=null -> {
                rotation(g,x+w/2,y+h/2-3,minOf(w*0.35f,h*0.45f),yaw,preview.rotation,preview.turns)
                val label="${if(preview.rotation==RotationAxis.NORMAL) "N" else preview.rotation.name} · ${preview.turns*90}°"
                g.drawString(font,label,x+(w-font.width(label))/2,y+h-12,ArchitectTheme.brass,false)
            }
            else -> {
                Meshes.preview(g,x+w/2,y+h/2,minOf(w*0.36f,h*0.43f),yaw,preview.parts,groups=groups,surfaces=surfaces,showMaterials=showMaterials)
                if(preview.parts.isEmpty()) g.drawCenteredString(font,tr("node_empty_result"),x+w/2,y+h-12,ArchitectTheme.muted)
            }
        }
    }
    private fun rotation(g:GuiGraphics,x:Int,y:Int,scale:Float,yaw:Float,axis:RotationAxis,turns:Int) {
        g.flush();val pose=g.pose();pose.pushPose()
        pose.translate(x.toDouble(),y.toDouble(),180.0);pose.scale(scale,-scale,scale)
        pose.mulPose(Axis.XP.rotationDegrees(25f));pose.mulPose(Axis.YP.rotationDegrees(yaw))
        pose.mulPose(when(axis) { RotationAxis.X->Axis.XP.rotationDegrees(turns*90f);RotationAxis.Z->Axis.ZP.rotationDegrees(turns*90f);else->Axis.YP.rotationDegrees(-turns*90f) })
        pose.translate(-0.35,-0.35,-0.35)
        val lines=g.bufferSource().getBuffer(RenderType.lines());val matrix=pose.last()
        for(a in 0..2) {
            val color=listOf(0xffef7979.toInt(),0xffa4cf78.toInt(),0xff79bcef.toInt())[a]
            fun line(start:FloatArray,end:FloatArray) {
                for(v in listOf(start,end)) lines.addVertex(matrix,v[0],v[1],v[2]).setColor(color).setNormal(matrix,if(a==0) 1f else 0f,if(a==1) 1f else 0f,if(a==2) 1f else 0f)
            }
            val end=FloatArray(3).apply { this[a]=1f };line(FloatArray(3),end)
            for(sign in listOf(-1,1)) line(end,FloatArray(3).apply { this[a]=0.78f;this[(a+1)%3]=0.12f*sign })
        }
        g.flush();pose.popPose()
    }
}
