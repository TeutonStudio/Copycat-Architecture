package de.teutonstudio.copycatarchitect.domain

/** The architect table's block coordinate in the unrotated building model. */
data class BuildingProjection(val table:P=P(-5,0,-5),val turn:Int=0) {
    fun validate():BuildingProjection {
        require(table.x in -512..512 && table.z in -512..512 && table.y in -256..256 && turn in 0..3) { "Invalid architect table reference" }
        return this
    }
    fun origin(tableWorld:P):P {
        validate()
        val offset=table.turn(turn)
        return tableWorld+P(-offset.x,-offset.y,-offset.z)
    }
    fun world(local:P,tableWorld:P):P=origin(tableWorld)+local.turn(turn)
    fun plan(plan:Plan,tableWorld:P):Plan {
        val origin=origin(tableWorld)
        return plan.copy(placements=plan.placements.map { p ->
            p.copy(pos=origin+p.pos.turn(turn),parts=if(p.block.isEmpty()) Geometry.rotate(p.parts,turn) else p.parts)
        },clear=plan.clear.map { origin+it.turn(turn) })
    }
}
