package de.teutonstudio.copycatarchitect.domain

/** Exact depth masks retain source profiles, material groups and extrusion receipts. */
internal object WallLayers {
    private fun inward(direction:Int)=when(direction) { 0->P(0,0,1);1->P(-1,0,0);2->P(0,0,-1);else->P(1,0,0) }
    fun projection(p:P,direction:Int):Int { val n=inward(direction);return p.x*n.x+p.z*n.z }
    fun innerFace(p:P,direction:Int)=projection(p,direction)*16+if(direction==0 || direction==3) 16 else 0

    fun clip(value:Placement,direction:Int,near:Int,far:Int):Placement? {
        if(near>=far) return null
        val n=inward(direction);val offset=projection(value.pos,direction)*16
        val whole=ExactVolume.box(0,0,0,16,16,16)
        val slab=ExactVolume(listOf(ExactCell(whole.cells.single().planes+listOf(
            ExactPlane(x=n.x.toLong(),z=n.z.toLong(),d=(far-offset).toLong()),
            ExactPlane(x=-n.x.toLong(),z=-n.z.toLong(),d=(offset-near).toLong())
        ))))
        if(slab.contains(whole)) return value
        if(slab.intersect(whole).isEmpty()) return null
        require(value.block.isEmpty()) { "Partial wall thickness requires a Copycat source; ${value.block} cannot be clipped" }
        val parts=value.parts.mapNotNull { part -> ExactGeometry.part(part).intersect(slab).takeUnless { it.isEmpty() }?.let { part.copy(exactClip=it) } }
        return parts.takeIf { it.isNotEmpty() }?.let { value.copy(parts=it) }
    }
    fun inside(value:Placement,direction:Int,face:Int,thickness:Int,depth:Int):Placement? {
        require(thickness in 0..depth*16) { "Wall thickness exceeds the source depth of $depth blocks; choose a deeper segment" }
        return clip(value,direction,face-thickness,face)
    }
    fun outside(value:Placement,direction:Int,face:Int,thickness:Int,depth:Int):Placement? {
        require(thickness in 0..depth*16) { "Facade thickness exceeds the source depth of $depth blocks; choose a deeper segment" }
        return clip(value,direction,face,face+thickness)
    }
}
