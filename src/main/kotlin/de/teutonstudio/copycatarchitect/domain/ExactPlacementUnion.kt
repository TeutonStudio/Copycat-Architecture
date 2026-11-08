package de.teutonstudio.copycatarchitect.domain

/** Earlier placements own overlap, including partial voxels; coatings and receipts keep their provenance. */
object ExactPlacementUnion {
    fun merge(contributions:List<Placement>):Placement {
        require(contributions.isNotEmpty() && contributions.all { it.block.isEmpty() }) { "Exact union needs composite placements" }
        val sources=mutableListOf<Placement>()
        for(p in contributions) if(sources.none { old -> Geometry.resolve(old.parts,old.groups).sameVisual(old.material,old.surfaces,Geometry.resolve(p.parts,p.groups),p.material,p.surfaces) }) sources+=p
        val first=sources.first();if(sources.size==1) return first
        data class Finish(val group:MaterialGroup,val material:String)
        data class Region(val source:Int,val finish:Finish,val volume:ExactVolume)
        var earlier=ExactVolume()
        val regions=sources.flatMapIndexed { index,p ->
            val geometry=Geometry.resolve(p.parts,p.groups)
            val whole=geometry.exactSolid ?: ExactVolume.fromSolid(geometry.solid)
            val available=whole.minus(earlier);earlier=earlier.union(whole)
            p.groups.mapNotNull { group ->
                val owned=geometry.exactOwned[group.id] ?: geometry.owned[group.id]?.let(ExactVolume::fromSolid)
                owned?.intersect(available)?.takeUnless { it.isEmpty() }?.let { Region(index,Finish(group,p.surfaces[group.id] ?: p.material),it) }
            }
        }
        val finishes=regions.map { it.finish }.distinct()
        val receipts=sources.map { p -> p.parts.map { it.recipeSource }.filter(String::isNotEmpty).distinct().withIndex().associate { it.value to it.index } }
        val groups=mutableListOf<MaterialGroup>();val surfaces=linkedMapOf<String,String>();val parts=mutableListOf<Part>()
        val reserved=finishes.map { it.group.id }.toSet();val collisions=finishes.groupingBy { it.group.id }.eachCount()
        for((index,finish) in finishes.withIndex()) {
            val matching=regions.filter { it.finish==finish };var id=finish.group.id
            if(collisions.getValue(id)>1) {
                id="shared_$index";var suffix=0
                while(id in reserved || id in surfaces) id="shared_${index}_${suffix++}"
            }
            groups+=finish.group.copy(id=id,priority=groups.size);surfaces[id]=finish.material
            for(region in matching) for(part in sources[region.source].parts.filter { it.group==finish.group.id }) {
                val original=ExactGeometry.part(part)
                val clipped=if(sources[region.source].parts.count { it.group==finish.group.id }==1) region.volume else original.intersect(region.volume)
                if(clipped.isEmpty()) continue
                parts+=part.copy(group=id,exactClip=clipped,
                    recipeSource=if(part.recipeSource.isEmpty()) "" else "wall_${region.source}_${receipts[region.source].getValue(part.recipeSource)}")
            }
        }
        require(groups.size<=8 && parts.size<=32) { "Exact shared block exceeds eight groups or 32 source parts" }
        return first.copy(parts=Geometry.normalized(parts,groups),groups=groups,surfaces=surfaces)
    }
}
