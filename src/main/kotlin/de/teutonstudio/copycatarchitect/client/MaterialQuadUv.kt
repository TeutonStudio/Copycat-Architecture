package de.teutonstudio.copycatarchitect.client

import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.core.Direction

/** Interpolate the model's final atlas UVs, including CT tile shifts, rotation and mirroring. */
internal class MaterialQuadUv(quad:BakedQuad) {
    private val data=quad.vertices
    private val stride=data.size/4
    private val axes=when(quad.direction.axis) { Direction.Axis.X->2 to 1;Direction.Axis.Y->0 to 2;else->0 to 1 }
    private fun value(vertex:Int,component:Int)=Float.fromBits(data[vertex*stride+component])
    private val x0=value(0,axes.first);private val y0=value(0,axes.second)
    private val dx1=value(1,axes.first)-x0;private val dy1=value(1,axes.second)-y0
    private val dx2=value(2,axes.first)-x0;private val dy2=value(2,axes.second)-y0
    private val determinant=dx1*dy2-dx2*dy1
    fun at(vertex:FloatArray):Pair<Float,Float> {
        val x=vertex[axes.first]-x0;val y=vertex[axes.second]-y0
        val a=if(kotlin.math.abs(determinant)>0.000001f) (x*dy2-dx2*y)/determinant else 0f
        val b=if(kotlin.math.abs(determinant)>0.000001f) (dx1*y-x*dy1)/determinant else 0f
        fun uv(component:Int)=value(0,component)+a*(value(1,component)-value(0,component))+b*(value(2,component)-value(0,component))
        return uv(4) to uv(5)
    }
}
