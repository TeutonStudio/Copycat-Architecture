package de.teutonstudio.copycatarchitect.domain

enum class RoomConflictKind { INTERIORS, WALL_IN_INTERIOR }
data class RoomArrangementArea(val index:Int,val label:String,val interior:Set<P>,val walls:Set<P>,val minimum:Int,val maximum:Int) {
    val shell get()=interior+walls.map { it.copy(y=0) }
    companion object {
        fun of(index:Int,node:BuildingNode,room:CompiledRoom,offset:P=P()):RoomArrangementArea {
            val height=room.maximum-room.topThickness
            val walls=room.plan.placements.filter { it.component=="wall" || it.component=="trim" && it.pos.copy(y=0) !in room.footprint }
                .map { it.pos+offset }.toSet()
            return RoomArrangementArea(index,node.name.ifBlank { node.id },room.footprint.map { (it+offset).copy(y=0) }.toSet(),walls,offset.y,offset.y+height)
        }
    }
}
/** Shell/shell overlap is intentional. Walls may never consume another room's usable interior. */
data class RoomArrangementConflict(val kind:RoomConflictKind,val first:RoomArrangementArea,val second:RoomArrangementArea,val cells:Set<P>) {
    fun description():String {
        val p=cells.minWith(compareBy({ it.z },{ it.x }))
        val a="${first.index+1}. ${first.label}";val b="${second.index+1}. ${second.label}"
        return when(kind) {
            RoomConflictKind.INTERIORS->"Room interiors overlap: $a / $b at X ${p.x}, Z ${p.z}"
            RoomConflictKind.WALL_IN_INTERIOR->"Wall of $a intrudes into the interior of $b at X ${p.x}, Z ${p.z}"
        }
    }
}
class InvalidRoomArrangement(val conflicts:List<RoomArrangementConflict>):IllegalArgumentException(conflicts.first().description())
object RoomArrangement {
    fun conflicts(rooms:List<RoomArrangementArea>):List<RoomArrangementConflict> = buildList {
        for(i in rooms.indices) for(j in i+1 until rooms.size) {
            val a=rooms[i];val b=rooms[j]
            if(maxOf(a.minimum,b.minimum)>=minOf(a.maximum,b.maximum)) continue
            val overlap=a.interior.intersect(b.interior)
            if(overlap.isNotEmpty()) add(RoomArrangementConflict(RoomConflictKind.INTERIORS,a,b,overlap))
            for((wall,inside) in listOf(a to b,b to a)) {
                val cells=wall.walls.filter { it.y in inside.minimum until inside.maximum && it.copy(y=0) in inside.interior }.map { it.copy(y=0) }.toSet()
                if(cells.isNotEmpty()) add(RoomArrangementConflict(RoomConflictKind.WALL_IN_INTERIOR,wall,inside,cells))
            }
        }
    }
    fun requireValid(rooms:List<RoomArrangementArea>) { val conflicts=conflicts(rooms);if(conflicts.isNotEmpty()) throw InvalidRoomArrangement(conflicts) }
}
