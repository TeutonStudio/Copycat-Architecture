package de.teutonstudio.copycatarchitect.domain

/** Place immutable period cells by quarter turn and exact translation; never deform their geometry. */
object RoofTemplatePlacement {
    fun orientation(upper:ExactPlane,slope:RoofSlope):Int {
        if(upper.x==0L && upper.z==0L && upper.y>0) return 0
        val s=slope.reduced()
        val canonical=ExactPlane(0,s.q.toLong(),-s.p.toLong(),0)
        return (0..3).firstOrNull { q ->
            val n=canonical.rotate(RotationAxis.Y,q)
            n.x*upper.y==upper.x*n.y && n.z*upper.y==upper.z*n.y && upper.y>0
        } ?: error("Custom roof template ratio does not match the target roof plane")
    }
    fun fit(template:Placement,upper:ExactPlane,slope:RoofSlope,phase:Int,slice:Int,target:ExactVolume,envelope:ExactVolume=target):Placement =
        fitMany(listOf(template to P()),upper,slope,phase,slice,target,envelope)

    /** Offsets are canonical pixel offsets; phase is the horizontal column within the q-block period. */
    fun fitMany(templateMembers:List<Pair<Placement,P>>,upper:ExactPlane,slope:RoofSlope,phase:Int,slice:Int,target:ExactVolume,envelope:ExactVolume=target):Placement =
        fitManyOrNull(templateMembers,upper,slope,phase,slice,target,envelope) ?: error("Custom roof period has no geometry at this phase and vertical slice")

    fun fitManyOrNull(templateMembers:List<Pair<Placement,P>>,upper:ExactPlane,slope:RoofSlope,phase:Int,slice:Int,target:ExactVolume,envelope:ExactVolume=target):Placement? {
        require(templateMembers.isNotEmpty() && templateMembers.all { it.first.block.isEmpty() }) { "Roof period templates must be Copycats" }
        val s=slope.reduced()
        require(phase in 0 until s.q) { "Invalid custom roof horizontal phase" }
        val turn=orientation(upper,s)
        val canonical=(if(upper.x==0L && upper.z==0L) ExactPlane(0,1,0,-16L*slice) else ExactPlane(0,s.q.toLong(),-s.p.toLong(),16L*s.p*phase-16L*s.q*slice)).rotate(RotationAxis.Y,turn)
        val shift=ExactNumber.of(upper.d)/ExactNumber.of(upper.y)-ExactNumber.of(canonical.d)/ExactNumber.of(canonical.y)
        val fitted=templateMembers.mapNotNull { (template,offset) ->
            val parts=template.parts.mapNotNull { part ->
                val volume=ExactGeometry.part(part).translated(offset).rotate(RotationAxis.Y,turn).translatedY(shift).intersect(envelope)
                if(volume.isEmpty()) null else part.copy(top=false,turn=0,tilt=0,spin=0,offset=P(),cutMask="",exact=volume,exactClip=null)
            }
            if(parts.isEmpty()) null else template.copy(pos=P(),component="roof",parts=parts)
        }
        if(fitted.isEmpty()) {
            require(target.isEmpty()) { "Custom roof template leaves a hole in its declared roof profile or repeat seam" }
            return null
        }
        val merged=if(fitted.size==1) fitted.single() else ExactPlacementUnion.merge(fitted)
        require(Geometry.exact(merged.parts).contains(target)) { "Custom roof template leaves a hole in its declared roof profile or repeat seam" }
        return merged
    }
}
