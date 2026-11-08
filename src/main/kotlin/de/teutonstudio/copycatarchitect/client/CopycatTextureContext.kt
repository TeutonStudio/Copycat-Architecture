package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.CompositeEntity
import de.teutonstudio.copycatarchitect.server.CopycatMaterials
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.ColorResolver
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.lighting.LevelLightEngine
import kotlin.math.abs

/** Material-only world used by native baked models, shared by placed blocks and every preview. */
class CopycatTextureContext(val position:BlockPos=BlockPos.ZERO,private val lookup:(BlockPos)->Cell? = { null }) {
    data class Cell(val parts:List<Part>,val groups:List<MaterialGroup> = DEFAULT_GROUPS,val material:String="",val surfaces:Map<String,String> = emptyMap(),val native:BlockState?=null) {
        fun state(group:String)=native ?: materialState(surfaces[group] ?: material)
        val faces:List<Face> by lazy { Meshes.mesh(parts,groups=groups) }
    }
    private val cells=mutableMapOf<BlockPos,Cell?>()
    private fun cell(pos:BlockPos)=cells.getOrPut(pos.immutable()) { lookup(pos) }

    fun view(current:Cell,face:Face):BlockAndTintGetter {
        val state=current.state(face.group)
        // Treat touching groups with the same coating as one material surface.
        val surface=current.faces.filter { current.state(it.group)==state && SurfaceConnections.coplanar(face,it) }
        return object:BlockAndTintGetter {
            override fun getBlockState(pos:BlockPos):BlockState {
                if(pos==position) return state
                val delta=pos.subtract(position)
                if(abs(delta.x)>1 || abs(delta.y)>1 || abs(delta.z)>1) return Blocks.AIR.defaultBlockState()
                val other=cell(pos) ?: return Blocks.AIR.defaultBlockState()
                val offset=floatArrayOf(delta.x.toFloat(),delta.y.toFloat(),delta.z.toFloat())
                // Native CT behaviours also query blocks in front of a connection for obstruction.
                if((0..2).sumOf { (offset[it]*face.normal[it]).toDouble() }>0.00001) {
                    if(other.native!=null) return other.native
                    val geometry=Geometry.resolve(other.parts,other.groups)
                    if(geometry.solid.count()==4096) return other.state(geometry.owner(8,8,8) ?: "body")
                }
                val matches=other.faces.filter { SurfaceConnections.coplanar(face,it,offset) && surface.any { own -> SurfaceConnections.touch(own,it,offset) } }
                return matches.firstOrNull { other.state(it.group)==state }?.let { other.state(it.group) }
                    ?: matches.firstOrNull()?.let { other.state(it.group) } ?: Blocks.AIR.defaultBlockState()
            }
            override fun getBlockEntity(pos:BlockPos):BlockEntity?=null
            override fun getFluidState(pos:BlockPos)=getBlockState(pos).fluidState
            override fun getShade(direction:Direction,shade:Boolean)=Minecraft.getInstance().level?.getShade(direction,shade) ?: 1f
            override fun getBlockTint(pos:BlockPos,resolver:ColorResolver)=Minecraft.getInstance().level?.getBlockTint(pos,resolver) ?: 0xffffff
            override fun getLightEngine():LevelLightEngine=Minecraft.getInstance().level?.lightEngine ?: previewLight
            override fun getHeight()=Minecraft.getInstance().level?.height ?: 384
            override fun getMinBuildHeight()=Minecraft.getInstance().level?.minBuildHeight ?: -64
        }
    }
    companion object {
        private val previewLight by lazy {
            LevelLightEngine(object:net.minecraft.world.level.chunk.LightChunkGetter {
                override fun getChunkForLighting(x:Int,z:Int):net.minecraft.world.level.chunk.LightChunk?=null
                override fun getLevel():net.minecraft.world.level.BlockGetter=net.minecraft.world.level.EmptyBlockGetter.INSTANCE
            },false,false)
        }
        fun materialState(id:String):BlockState=runCatching { CopycatMaterials.state(id.ifEmpty { "create:zinc_block" }) }.getOrElse { Blocks.STONE.defaultBlockState() }
        fun plan(position:P,cells:Map<P,Placement>)=CopycatTextureContext(BlockPos(position.x,position.y,position.z)) { pos ->
            cells[P(pos.x,pos.y,pos.z)]?.let { p ->
                if(p.block.isEmpty()) Cell(p.parts,p.groups,p.material,p.surfaces)
                else Cell(listOf(Part("copycats:copycat_block")),native=LandscapeBlockPreview.state(p,cells))
            }
        }
        fun world(position:BlockPos,level:BlockAndTintGetter?=Minecraft.getInstance().level)=CopycatTextureContext(position) { pos ->
            val entity=level?.getBlockEntity(pos)
            if(entity is CompositeEntity) Cell(entity.parts,entity.groups,entity.material,entity.surfaces)
            else level?.getBlockState(pos)?.takeUnless { it.isAir || !it.isCollisionShapeFullBlock(level,pos) && it.block !is net.minecraft.world.level.block.TransparentBlock }
                ?.let { Cell(listOf(Part("copycats:copycat_block")),native=it) }
        }
    }
}
