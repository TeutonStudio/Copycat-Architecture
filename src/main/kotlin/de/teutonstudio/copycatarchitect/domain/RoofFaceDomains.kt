package de.teutonstudio.copycatarchitect.domain

/** Exact X/Z domains of the upper envelope of roofs made of convex patches. */
internal object RoofFaceDomains {
    private val cube=ExactVolume.box(0,0,0,16,16,16)
    private fun below(a:ExactPlane,b:ExactPlane):ExactVolume {
        val x=b.x*a.y-a.x*b.y;val z=b.z*a.y-a.z*b.y;val d=b.d*a.y-a.d*b.y
        return if(x==0L && z==0L) { if(d>=0) cube else ExactVolume() }
        else ExactVolume.convex(listOf(ExactPlane(x,0,z,d))).intersect(cube)
    }
    fun visible(face:ExactPlane,own:List<ExactPlane>,others:List<List<ExactPlane>>):ExactVolume {
        var area=own.fold(cube) { v,p->v.intersect(below(face,p)) }
        for(planes in others) {
            val choices=planes.map { below(it,face) }
            val region=if(choices.any { it==cube }) cube else {
                val cells=choices.flatMap { it.cells }.distinct().filterNot { it.isEmpty() }
                ExactVolume(cells.filterIndexed { i,c->cells.indices.none { j->i!=j && ExactGeometry.cellContains(cells[j],c) && (j<i || !ExactGeometry.cellContains(c,cells[j])) } })
            }
            area=area.intersect(region)
            if(area.isEmpty()) break
        }
        return area
    }
}

/** Runtime placement workspace. No meshes or cached surface identities enter the document. */
data class RoofMapPoint(val x:Double,val z:Double)
data class RoofDormerSurface(val points:List<RoofMapPoint>,val direction:Int)
data class RoofDormerArea(val id:String,val name:String,val points:Set<P>,val instances:List<DormerInstance>)
data class RoofDormerCheck(val valid:Boolean,val message:String,val points:Set<P>)
class RoofDormerWorkspace(val areas:List<RoofDormerArea>,val definitions:List<BuildingNode>,
    val surfaces:(String)->List<RoofDormerSurface>,val check:(String,DormerInstance)->RoofDormerCheck)
