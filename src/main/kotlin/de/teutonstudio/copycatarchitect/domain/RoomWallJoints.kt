package de.teutonstudio.copycatarchitect.domain

internal operator fun P.minus(other:P)=P(x-other.x,y-other.y,z-other.z)

/** Explicit rooms share the terminal columns of their unchanged wall segments. */
val BuildingNode.usesJoinedWallEnds get()=kind==BuildingKind.ROOM && assembly && joinWallEnds!=false

object RoomWallJoints {
    fun shell(node:BuildingNode)=if(node.usesJoinedWallEnds) RoomGeometry.cells(node.rectangles) else RoomGeometry.shell(node.rectangles)
    fun runs(node:BuildingNode):List<WallRun> {
        val old=RoomGeometry.edges(node.rectangles)
        if(!node.usesJoinedWallEnds) return old
        val footprint=RoomGeometry.cells(node.rectangles)
        return old.map { run ->
            val normal=RoomGeometry.directions[run.direction]
            val tangent=P(-normal.z,0,normal.x)
            val cells=run.cells.map { it-normal }.toMutableList()
            // Re-entrant boundaries meet at the preceding interior cell as well.
            val before=cells.first()-tangent;val after=cells.last()+tangent
            if(before in footprint) cells.add(0,before)
            if(after in footprint) cells.add(after)
            run.copy(cells=cells)
        }
    }
}
