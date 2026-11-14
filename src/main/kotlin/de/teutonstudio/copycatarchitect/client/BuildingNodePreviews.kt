package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.Library
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.RenderType
import org.joml.Quaternionf
import org.joml.Vector3f

data class BuildingNodePreview(val node:BuildingNode,val plan:Result<Plan>,val boxes:List<Box> = emptyList())

/** Graph thumbnails share one evaluator, while each node keeps its own intermediate result. */
class BuildingNodePreviews(private val definition:()->Building,private val library:()->Library) {
    private var oldBuilding:Building?=null
    private var oldLibrary:Library?=null
    private var oldRecipes=0
    private var evaluation:Result<BuildingGraphCompiler.Evaluation>?=null
    private val values=mutableMapOf<String,BuildingNodePreview>()
    fun get(id:String):BuildingNodePreview {
        val b=definition();val lib=library();val graph=b.graph ?: BuildingGraph.starter()
        if(oldBuilding!=b || oldLibrary!=lib || oldRecipes!=Extrusion.outputs.hashCode()) {
            oldBuilding=b;oldLibrary=lib;oldRecipes=Extrusion.outputs.hashCode();values.clear();evaluation=null
        }
        return values.getOrPut(id) {
            val n=graph.nodes.firstOrNull { it.id==id } ?: BuildingNode(id=id)
            val boxes=runCatching { BuildingNodePreviewGeometry.boxes(graph,n) }
            val plan=runCatching {
                when(n.kind) {
                    BuildingKind.AREA,BuildingKind.ROOM_GEOMETRY,BuildingKind.SEGMENT_LAYOUT -> {
                        boxes.getOrThrow();emptyPlan()
                    }
                    else -> {
                        if(evaluation==null) evaluation=runCatching { BuildingGraphCompiler.Evaluation(graph,b.palette,lib.forms,lib.modules) }
                        val ready=evaluation!!.getOrThrow()
                        ready.preview(id)
                    }
                }
            }
            // CT and face culling need the complete topology even in small graph viewports.
            BuildingNodePreview(n,plan,boxes.getOrDefault(emptyList()))
        }
    }

    fun roomFootprint(node:BuildingNode):Set<P> {
        if(node.roomConfig?.mode!=RoomMode.SEGMENTS && node.bayWindowInstances.isEmpty()) return RoomGeometry.cells(node.rectangles)
        get(node.id)
        return runCatching { evaluation?.getOrThrow()?.room(node.id)?.footprint.orEmpty() }.getOrDefault(emptySet())
    }
    fun roomShell(node:BuildingNode):Set<P> {
        if(node.roomConfig?.mode!=RoomMode.SEGMENTS && node.bayWindowInstances.isEmpty()) return RoomWallJoints.shell(node)
        get(node.id)
        return runCatching { evaluation?.getOrThrow()?.room(node.id)?.shell.orEmpty() }.getOrDefault(emptySet())
    }

    private fun emptyPlan()=Plan(emptyList(),emptyList(),emptyList(),0,emptyMap())
}

object BuildingNodePreviewRenderer {
    internal fun drawThumbnail(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int,preview:BuildingNodePreview) {
        val plan=preview.plan.getOrNull()
        if(plan==null || plan.placements.isEmpty() && preview.boxes.isEmpty()) {
            draw(g,x,y,w,h,preview)
            return
        }
        GraphPreviewThumbnails.draw(g,x,y,w,h,preview) {
            if(preview.boxes.isNotEmpty()) {
                { target ->
                    draw(target,0,0,w,h,preview)
                    true
                }
            } else {
                var renderScene:((GuiGraphics)->Boolean)?=null
                val viewport=BuildingPreviewViewport(0,0,w,h)
                val render:(GuiGraphics)->Boolean={ target ->
                    if(renderScene==null) {
                        BuildingPreviewRenderer.draw(target,viewport,preview.node,preview.plan,"3d",35f,clip=false) {
                            renderScene=BuildingScene.thumbnail(plan,it)
                        }
                    }
                    checkNotNull(renderScene)(target)
                }
                render
            }
        }
    }

    fun draw(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int,preview:BuildingNodePreview) {
        if(preview.boxes.isEmpty() || preview.plan.isFailure) {
            BuildingPreviewRenderer.draw(g,BuildingPreviewViewport(x,y,w,h),preview.node,preview.plan,"3d",35f,clip=false)
            return
        }
        ArchitectTheme.inset(g,x,y,w,h,ArchitectTheme.blueprint)
        val boxes=preview.boxes
        val lo=Vector3f(boxes.minOf { it.x }.toFloat(),boxes.minOf { it.y }.toFloat(),boxes.minOf { it.z }.toFloat())
        val hi=Vector3f(boxes.maxOf { it.maxX }.toFloat(),boxes.maxOf { it.maxY }.toFloat(),boxes.maxOf { it.maxZ }.toFloat())
        val centre=Vector3f(lo).add(hi).mul(0.5f)
        val q=Quaternionf().rotationX(Math.toRadians(25.0).toFloat()).rotateY(Math.toRadians(35.0).toFloat())
        val corners=buildList { for(a in listOf(lo.x,hi.x)) for(b in listOf(lo.y,hi.y)) for(c in listOf(lo.z,hi.z)) add(q.transform(Vector3f(a,b,c).sub(centre))) }
        val scale=minOf((w-10)/(corners.maxOf { it.x }-corners.minOf { it.x }).coerceAtLeast(1f),(h-10)/(corners.maxOf { it.y }-corners.minOf { it.y }).coerceAtLeast(1f))
        g.flush();val pose=g.pose();pose.pushPose()
        try {
            pose.translate(x+w/2.0,y+h/2.0,180.0);pose.scale(scale,-scale,scale);pose.mulPose(q);pose.translate(-centre.x.toDouble(),-centre.y.toDouble(),-centre.z.toDouble())
            val lines=g.bufferSource().getBuffer(RenderType.lines());val matrix=pose.last()
            for(box in boxes) for(axis in 0..2) for(a in 0..1) for(b in 0..1) {
                val min=floatArrayOf(box.x.toFloat(),box.y.toFloat(),box.z.toFloat());val max=floatArrayOf(box.maxX.toFloat(),box.maxY.toFloat(),box.maxZ.toFloat())
                val start=min.copyOf();start[(axis+1)%3]=if(a==0) min[(axis+1)%3] else max[(axis+1)%3];start[(axis+2)%3]=if(b==0) min[(axis+2)%3] else max[(axis+2)%3]
                val end=start.copyOf();end[axis]=max[axis]
                for(v in listOf(start,end)) lines.addVertex(matrix,v[0],v[1],v[2]).setColor(ArchitectTheme.green).setNormal(matrix,if(axis==0) 1f else 0f,if(axis==1) 1f else 0f,if(axis==2) 1f else 0f)
            }
        } finally { g.flush();pose.popPose() }
    }
}
