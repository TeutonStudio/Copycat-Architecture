package de.teutonstudio.copycatarchitect.domain

/** Mirror planes through the cube center, selected by their normal direction. */
enum class CopycatMirrorPlane {
    X,Y,Z,XY,XY_NEGATIVE,XZ,XZ_NEGATIVE,YZ,YZ_NEGATIVE,BODY_PPP,BODY_PPN,BODY_PNP,BODY_PNN;

    val requiresExact get()=this in setOf(BODY_PPP,BODY_PPN,BODY_PNP,BODY_PNN)
    val normal:P get()=when(this) {
        X->P(1,0,0);Y->P(0,1,0);Z->P(0,0,1)
        XY->P(1,1,0);XY_NEGATIVE->P(1,-1,0)
        XZ->P(1,0,1);XZ_NEGATIVE->P(1,0,-1)
        YZ->P(0,1,1);YZ_NEGATIVE->P(0,1,-1)
        BODY_PPP->P(1,1,1);BODY_PPN->P(1,1,-1);BODY_PNP->P(1,-1,1);BODY_PNN->P(1,-1,-1)
    }

    companion object {
        fun fromAxis(axis:RotationAxis):CopycatMirrorPlane = when(axis) {
            RotationAxis.X->X
            RotationAxis.Y->Y
            RotationAxis.Z->Z
            RotationAxis.NORMAL->error("A Copycat reflection needs a world axis or an explicit plane")
        }
    }
}

data class CopycatMirror(val plane:CopycatMirrorPlane=CopycatMirrorPlane.X)

/** Voxel indices use edge 15; displacement vectors use edge zero. */
internal fun reflectPoint(point:P,plane:CopycatMirrorPlane,vector:Boolean=false):P {
    val edge=if(vector) 0 else 15
    val (x,y,z)=point
    return when(plane) {
        CopycatMirrorPlane.X->P(edge-x,y,z)
        CopycatMirrorPlane.Y->P(x,edge-y,z)
        CopycatMirrorPlane.Z->P(x,y,edge-z)
        CopycatMirrorPlane.XY->P(edge-y,edge-x,z)
        CopycatMirrorPlane.XY_NEGATIVE->P(y,x,z)
        CopycatMirrorPlane.XZ->P(edge-z,y,edge-x)
        CopycatMirrorPlane.XZ_NEGATIVE->P(z,y,x)
        CopycatMirrorPlane.YZ->P(x,edge-z,edge-y)
        CopycatMirrorPlane.YZ_NEGATIVE->P(x,z,y)
        else->error("Body-diagonal reflections require exact rational geometry")
    }
}

/** Householder reflection in exact coordinates, centered at (8,8,8). */
internal fun reflectPoint(point:ExactPoint,plane:CopycatMirrorPlane):ExactPoint {
    val n=plane.normal;val center=ExactNumber.of(8)
    val dot=(point.x-center)*n.x.toLong()+(point.y-center)*n.y.toLong()+(point.z-center)*n.z.toLong()
    val amount=dot*2L/ExactNumber.of((n.x*n.x+n.y*n.y+n.z*n.z).toLong())
    return ExactPoint(point.x-amount*n.x.toLong(),point.y-amount*n.y.toLong(),point.z-amount*n.z.toLong())
}

internal fun ExactVolume.withinCopycatBlock():Boolean {
    val minimum=ExactNumber.ZERO;val maximum=ExactNumber.of(16)
    return cells.all { cell -> cell.vertices().all { p -> listOf(p.x,p.y,p.z).all { it>=minimum && it<=maximum } } }
}

internal fun requireCopycatMirrorBounds(volume:ExactVolume,plane:CopycatMirrorPlane) {
    require(volume.withinCopycatBlock()) { "Copycat reflection ${plane.name} leaves block bounds (0–16); use a smaller centered shape" }
}
