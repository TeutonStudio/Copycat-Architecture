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

    fun fitManyOrNull(templateMembers:List<Pair<Placement,P>>,upper:ExactPlane,slope:RoofSlope,phase:Int,slice:Int,target:ExactVolume,envelope:ExactVolume=target,completeProfile:Boolean=false,alignJunction:Boolean=false):Placement? {
        require(templateMembers.isNotEmpty() && templateMembers.all { it.first.block.isEmpty() }) { "Roof period templates must be Copycats" }
        val s=slope.reduced()
        require(phase in 0 until s.q) { "Invalid custom roof horizontal phase" }
        val turn=orientation(upper,s)
        val canonical=(if(upper.x==0L && upper.z==0L) ExactPlane(0,1,0,-16L*slice) else ExactPlane(0,s.q.toLong(),-s.p.toLong(),16L*s.p*phase-16L*s.q*slice)).rotate(RotationAxis.Y,turn)
        val shift=ExactNumber.of(upper.d)/ExactNumber.of(upper.y)-ExactNumber.of(canonical.d)/ExactNumber.of(canonical.y)
        // An unedited procedural roof declares its whole profile. Its block-clipped display
        // cell alone loses the lower spill triangle after translation. Custom cuts stay finite.
        fun volume(part:Part):ExactVolume {
            if(!completeProfile || part.source!=RoofPartParameters.SOURCE || part.exact!=null || part.exactClip!=null || part.cutMask.isNotEmpty() || part.top || part.tilt!=0) return ExactGeometry.part(part)
            val parameters=part.roof ?: RoofPartParameters()
            require(parameters.rise.toLong()*s.q==parameters.run.toLong()*s.p) { "Dachprofil und Dachsteigung stimmen nicht überein" }
            val profile=ExactGeometry.roof(parameters)
            val full=ExactVolume(profile.cells.map { cell -> ExactCell(cell.planes.filterNot { it.x==0L && it.z==0L }+listOf(ExactPlane(0,1,0,8192),ExactPlane(0,-1,0,8192))) })
            val phaseShift=ExactNumber.of(16L*s.p*phase)/ExactNumber.of(s.q.toLong())-ExactNumber.of(16L*parameters.phase)/ExactNumber.of(parameters.run.toLong())+ExactNumber.of(16L*parameters.slice)
            return full.translatedY(phaseShift).rotate(RotationAxis.Y,part.spin).rotate(RotationAxis.Y,part.turn).translated(part.offset)
        }
        val models=templateMembers.map { (template,offset)->Triple(template,offset,template.parts.map(::volume)) }
        // A Boolean corner may taper to a closed seam instead of keeping one
        // pixel of backing everywhere. Validate its actual outer faces exactly;
        // demanding a uniform inner band incorrectly calls that seam a hole.
        val roofFaces by lazy { if(alignJunction) envelope.surfaces().filter { it.plane.y>0 && (it.plane.x!=0L || it.plane.z!=0L) } else emptyList() }
        val roofVertices by lazy { roofFaces.flatMap { it.vertices }.distinct() }
        val requiredVertices=if(alignJunction) target.cells.flatMap { it.vertices() }.distinct() else emptyList()
        fun coversRoofFaces(volume:ExactVolume):Boolean = roofFaces.isNotEmpty() && roofVertices.all(volume::containsPoint) && roofFaces.all { face->
            var remaining=listOf(face.vertices)
            for(cell in volume.cells) {
                remaining=remaining.flatMap { ExactGeometry.subtractPolygon(it,cell.planes) }
                if(remaining.isEmpty()) break
            }
            remaining.isEmpty()
        }
        val turns=if(alignJunction) listOf(turn)+(0..3).filter { it!=turn } else listOf(turn)
        val offsets=if(alignJunction) listOf(0,-8,8,-16,16).flatMap { x->listOf(0,-8,8,-16,16).map { z->P(x,0,z) } }.sortedBy { kotlin.math.abs(it.x)+kotlin.math.abs(it.z) } else listOf(P())
        for(q in turns) for(offset in offsets) {
            val plane=canonical.rotate(RotationAxis.Y,q-turn)
            val vertical=ExactNumber.of(upper.d)/ExactNumber.of(upper.y)-ExactNumber.of(plane.d)/ExactNumber.of(plane.y)-ExactNumber.of(upper.x*offset.x+upper.z*offset.z)/ExactNumber.of(upper.y)
            val positioned=models.map { (_,cell,volumes)->volumes.map { it.translated(cell).rotate(RotationAxis.Y,q).translated(offset).translatedY(if(alignJunction) vertical else shift) } }
            if(alignJunction) {
                // Reject an impossible orientation before expensive Boolean clipping
                // and material ownership resolution of all period members.
                val profile=ExactVolume(positioned.flatten().flatMap { it.cells })
                if(!requiredVertices.all(profile::containsPoint) && (roofVertices.isEmpty() || !roofVertices.all(profile::containsPoint))) continue
            }
            val fitted=models.mapIndexedNotNull { member,(template,_,_) ->
                val parts=template.parts.mapIndexedNotNull { index,part ->
                    val placed=positioned[member][index].intersect(envelope)
                    if(placed.isEmpty()) null else part.copy(top=false,turn=0,tilt=0,spin=0,offset=P(),cutMask="",exact=placed,exactClip=null)
                }
                if(parts.isEmpty()) null else template.copy(pos=P(),component="roof",parts=parts)
            }
            if(fitted.isEmpty()) continue
            val solid=Geometry.exact(fitted.flatMap { it.parts })
            if(solid.contains(target) || alignJunction && coversRoofFaces(solid)) return if(fitted.size==1) fitted.single() else ExactPlacementUnion.merge(fitted)
        }
        require(target.isEmpty()) { "Custom roof template leaves a hole in its declared roof profile or repeat seam; provide every vertical spill slice of the complete period" }
        return null
    }
}
