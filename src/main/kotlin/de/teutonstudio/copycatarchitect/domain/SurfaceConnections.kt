package de.teutonstudio.copycatarchitect.domain

import kotlin.math.abs

/** A visible polygon in block-local coordinates, independent of the game renderer. */
interface TextureSurface {
    val vertices:List<FloatArray>
    val normal:FloatArray
}

/** Geometric seam checks also work on the triangles of exact, sloping surfaces. */
object SurfaceConnections {
    private const val EPS=0.00001f
    fun coplanar(a:TextureSurface,b:TextureSurface,offset:FloatArray=floatArrayOf(0f,0f,0f)):Boolean {
        if((0..2).any { abs(a.normal[it]-b.normal[it])>EPS }) return false
        return abs((0..2).sumOf { (a.normal[it]*(b.vertices[0][it]+offset[it]-a.vertices[0][it])).toDouble() })<EPS
    }
    fun touch(a:TextureSurface,b:TextureSurface,offset:FloatArray):Boolean {
        val axes=(0..2).filter { offset[it]!=0f }
        if(axes.isEmpty()) return false
        val av=a.vertices.filter { v -> axes.all { abs(v[it]-(if(offset[it]>0) 1f else 0f))<EPS } }
        val bv=b.vertices.map { v -> FloatArray(3) { v[it]+offset[it] } }.filter { v -> axes.all { abs(v[it]-(if(offset[it]>0) 1f else 0f))<EPS } }
        if(av.isEmpty() || bv.isEmpty()) return false
        if(axes.size>1) return av.any { x -> bv.any { y -> (0..2).all { abs(x[it]-y[it])<EPS } } }
        // A shared point alone is a corner, not a connected edge.
        val tangent=(0..2).maxBy { i -> (av.maxOf { it[i] }-av.minOf { it[i] }) }
        return minOf(av.maxOf { it[tangent] },bv.maxOf { it[tangent] })-maxOf(av.minOf { it[tangent] },bv.minOf { it[tangent] })>EPS
    }
}
