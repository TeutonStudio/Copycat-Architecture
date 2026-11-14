package de.teutonstudio.copycatarchitect.client

import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.vertex.VertexSorting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.GameRenderer
import net.neoforged.neoforge.client.ClientHooks
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30
import java.lang.ref.WeakReference

/** Small, bounded GPU thumbnails. Building work advances one slice per rendered frame. */
internal object GraphPreviewThumbnails {
    private const val MAX_THUMBNAILS=48

    private class Key(preview:Any,val width:Int,val height:Int,private val style:Any) {
        private val source=WeakReference(preview)
        private val hash=31*(31*(31*System.identityHashCode(preview)+width)+height)+style.hashCode()
        override fun hashCode()=hash
        override fun equals(other:Any?):Boolean {
            if(this===other) return true
            if(other !is Key || hash!=other.hash || width!=other.width || height!=other.height || style!=other.style) return false
            val value=source.get()
            return value!=null && value===other.source.get()
        }
    }

    private class Entry(val target:TextureTarget,var step:((GuiGraphics)->Boolean)?,var lastStep:Long,var lastVisible:Long=lastStep)
    private val cache=LinkedHashMap<Key,Entry>(16,0.75f,true)
    private val requests=linkedMapOf<Key,()->(GuiGraphics)->Boolean>()
    private var frame=0L
    private var visible=0
    @Volatile private var resourcesChanged=false
    private var level=WeakReference<Any>(null)
    private var reset=false
    internal var renderSteps=0L
        private set

    internal fun hasPendingVisible():Boolean = reset || requests.isNotEmpty() || cache.values.any { it.step!=null && it.lastVisible>=frame-1 }

    fun invalidateResources() { resourcesChanged=true }

    fun beginFrame() {
        frame++
        visible=0
        requests.clear()
        val currentLevel=Minecraft.getInstance().level
        if(resourcesChanged || currentLevel!==level.get()) {
            resourcesChanged=false
            level=WeakReference(currentLevel)
            reset=true
        }
    }

    fun draw(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int,preview:Any,style:Any=Unit,create:()->(GuiGraphics)->Boolean) {
        val key=Key(preview,w,h,style)
        val withinBudget=visible++<MAX_THUMBNAILS
        val entry=if(reset) null else cache[key]
        if(entry!=null) entry.lastVisible=frame
        if(entry==null || entry.step!=null) {
            if(withinBudget) requests.putIfAbsent(key,create)
            ArchitectTheme.inset(g,x,y,w,h,ArchitectTheme.blueprint)
            g.drawCenteredString(Minecraft.getInstance().font,tr("preview_calculating"),x+w/2,y+(h-8)/2,ArchitectTheme.muted)
            return
        }
        blit(g,entry.target,x,y,w,h)
    }

    fun endFrame(g:GuiGraphics) {
        val stale=cache.entries.filter { (_,entry) -> entry.step!=null && frame-entry.lastVisible>3 }.map { it.key }
        if(requests.isEmpty() && !reset && stale.isEmpty()) return
        val next=requests.minByOrNull { (key,_) -> if(reset) Long.MIN_VALUE else cache[key]?.lastStep ?: Long.MIN_VALUE }
        requests.clear()
        g.flush()
        withRenderState {
            if(reset) {
                cache.values.forEach { it.target.destroyBuffers() }
                cache.clear()
                reset=false
            }
            stale.forEach { cache.remove(it)?.target?.destroyBuffers() }
            if(next!=null) {
                val (key,create)=next
                val entry=cache[key] ?: run {
                    if(cache.size==MAX_THUMBNAILS) {
                        val oldest=cache.entries.iterator()
                        oldest.next().value.target.destroyBuffers()
                        oldest.remove()
                    }
                    val step=create()
                    val target=TextureTarget((key.width*2).coerceAtMost(512),(key.height*2).coerceAtMost(512),true,Minecraft.ON_OSX)
                    target.setFilterMode(GL11.GL_LINEAR)
                    Entry(target,step,frame).also { cache[key]=it }
                }
                entry.lastStep=frame
                entry.target.bindWrite(true)
                val far=ClientHooks.getGuiFarPlane()
                RenderSystem.setProjectionMatrix(Matrix4f().setOrtho(0f,key.width.toFloat(),key.height.toFloat(),0f,1000f,far),VertexSorting.ORTHOGRAPHIC_Z)
                RenderSystem.getModelViewStack().translation(0f,0f,10000f-far)
                RenderSystem.applyModelViewMatrix()
                val thumbnail=GuiGraphics(Minecraft.getInstance(),g.bufferSource())
                try {
                    renderSteps++
                    if(entry.step?.invoke(thumbnail)==true) entry.step=null
                } finally {
                    thumbnail.flush()
                }
            }
        }
    }

    private fun blit(g:GuiGraphics,target:TextureTarget,x:Int,y:Int,w:Int,h:Int) {
        g.flush()
        val oldShader=RenderSystem.getShader()
        val oldTexture=RenderSystem.getShaderTexture(0)
        RenderSystem.disableDepthTest()
        try {
            RenderSystem.setShaderTexture(0,target.colorTextureId)
            RenderSystem.setShader(GameRenderer::getPositionTexShader)
            val pose=g.pose().last().pose()
            val quad=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX)
            quad.addVertex(pose,x.toFloat(),y.toFloat(),0f).setUv(0f,1f)
            quad.addVertex(pose,x.toFloat(),(y+h).toFloat(),0f).setUv(0f,0f)
            quad.addVertex(pose,(x+w).toFloat(),(y+h).toFloat(),0f).setUv(1f,0f)
            quad.addVertex(pose,(x+w).toFloat(),y.toFloat(),0f).setUv(1f,1f)
            BufferUploader.drawWithShader(quad.buildOrThrow())
        } finally {
            RenderSystem.setShader { oldShader }
            RenderSystem.setShaderTexture(0,oldTexture)
            RenderSystem.enableDepthTest()
        }
    }

    private inline fun withRenderState(action:()->Unit) {
        val read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
        val draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        val textureUnit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
        val texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        val viewport=IntArray(4).also { GL11.glGetIntegerv(GL11.GL_VIEWPORT,it) }
        val scissor=IntArray(4).also { GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,it) }
        val scissorEnabled=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
        val depthEnabled=GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        val depthFunction=GL11.glGetInteger(GL11.GL_DEPTH_FUNC)
        val depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
        val cull=GL11.glIsEnabled(GL11.GL_CULL_FACE)
        val blend=GL11.glIsEnabled(GL11.GL_BLEND)
        val srcRgb=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
        val dstRgb=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        val srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
        val dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
        val clear=FloatArray(4).also { GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE,it) }
        val projection=RenderSystem.getProjectionMatrix()
        val sorting=RenderSystem.getVertexSorting()
        val shader=RenderSystem.getShader()
        val shaderTextures=IntArray(3,RenderSystem::getShaderTexture)
        val color=RenderSystem.getShaderColor().copyOf()
        val modelView=RenderSystem.getModelViewStack()
        modelView.pushMatrix()
        RenderSystem.disableScissor()
        RenderSystem.depthMask(true)
        RenderSystem.setShaderColor(1f,1f,1f,1f)
        try {
            action()
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read)
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw)
            RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3])
            if(scissorEnabled) RenderSystem.enableScissor(scissor[0],scissor[1],scissor[2],scissor[3])
            else RenderSystem.disableScissor()
            if(depthEnabled) RenderSystem.enableDepthTest() else RenderSystem.disableDepthTest()
            RenderSystem.depthFunc(depthFunction)
            RenderSystem.depthMask(depthMask)
            if(cull) RenderSystem.enableCull() else RenderSystem.disableCull()
            if(blend) RenderSystem.enableBlend() else RenderSystem.disableBlend()
            RenderSystem.blendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha)
            RenderSystem.clearColor(clear[0],clear[1],clear[2],clear[3])
            RenderSystem.activeTexture(textureUnit)
            RenderSystem.bindTexture(texture)
            shaderTextures.forEachIndexed(RenderSystem::setShaderTexture)
            RenderSystem.setShader { shader }
            RenderSystem.setShaderColor(color[0],color[1],color[2],color[3])
            RenderSystem.setProjectionMatrix(projection,sorting)
            modelView.popMatrix()
            RenderSystem.applyModelViewMatrix()
        }
    }
}
