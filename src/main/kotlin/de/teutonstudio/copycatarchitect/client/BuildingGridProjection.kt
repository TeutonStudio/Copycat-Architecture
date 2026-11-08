package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.P
import kotlin.math.floor

/** One transform for fitting, drawing and picking every cell in an editable raster. */
data class BuildingGridProjection(
    val x:Double,val y:Double,val minX:Int,val minZ:Int,val step:Double,
    val left:Int,val top:Int,val width:Int,val height:Int
) {
    fun px(value:Int)=x+(value-minX)*step
    fun py(value:Int)=y+(value-minZ)*step
    fun cell(screenX:Double,screenY:Double)=P(floor((screenX-x)/step).toInt()+minX,0,floor((screenY-y)/step).toInt()+minZ)
    fun contains(screenX:Double,screenY:Double)=screenX>=left && screenX<left+width && screenY>=top && screenY<top+height
    val columns:IntRange get()=cell(left.toDouble(),top.toDouble()).x..cell(left+width-0.000001,top.toDouble()).x
    val rows:IntRange get()=cell(left.toDouble(),top.toDouble()).z..cell(left.toDouble(),top+height-0.000001).z

    companion object {
        fun fit(left:Int,top:Int,width:Int,height:Int,cells:Set<P>,maxStep:Double=18.0):BuildingGridProjection {
            require(width>0 && height>0 && maxStep>0)
            val minX=(cells.minOfOrNull { it.x } ?: 0)-1
            val minZ=(cells.minOfOrNull { it.z } ?: 0)-1
            val spanX=(cells.maxOfOrNull { it.x } ?: 3)-minX+2
            val spanZ=(cells.maxOfOrNull { it.z } ?: 3)-minZ+2
            val step=minOf(width.toDouble()/spanX,height.toDouble()/spanZ,maxStep)
            return BuildingGridProjection(left+(width-spanX*step)/2,top+(height-spanZ*step)/2,minX,minZ,step,left,top,width,height)
        }
    }
}
