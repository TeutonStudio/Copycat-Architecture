package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.CopycatMaterials
import net.minecraft.core.Direction
import net.minecraft.world.level.block.TransparentBlock

/** Preview neighbors carry the same exact coverage and glass ownership as placed composites. */
data class PlanMeshNeighbors(val parts:Map<Direction,List<Part>>,val groups:Map<Direction,List<MaterialGroup>>,val transparent:Map<Direction,Set<String>>) {
    companion object {
        fun at(position:P,cells:Map<P,Placement>):PlanMeshNeighbors {
            val parts=linkedMapOf<Direction,List<Part>>();val groups=linkedMapOf<Direction,List<MaterialGroup>>();val transparent=linkedMapOf<Direction,Set<String>>()
            for(direction in Direction.entries) {
                val neighbor=cells[position+P(direction.stepX,direction.stepY,direction.stepZ)] ?: continue
                if(neighbor.block.isNotEmpty()) {
                    if(neighbor.block in BuildingGraphCompiler.CARPETS) continue
                    parts[direction]=listOf(Part("copycats:copycat_block"));groups[direction]=DEFAULT_GROUPS
                    transparent[direction]=emptySet()
                } else {
                    parts[direction]=neighbor.parts;groups[direction]=neighbor.groups
                    transparent[direction]=neighbor.groups.filter { group -> runCatching { CopycatMaterials.state(neighbor.surfaces[group.id] ?: neighbor.material).block is TransparentBlock }.getOrDefault(false) }.map { it.id }.toSet()
                }
            }
            return PlanMeshNeighbors(parts,groups,transparent)
        }
    }
}
