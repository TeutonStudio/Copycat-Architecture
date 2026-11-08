package de.teutonstudio.copycatarchitect.domain

/** A vertical boundary vertex, with its nearest exterior block and local +X/+Z directed into the room. */
data class RoomVerticalEdge(val id:String,val vertex:P,val pos:P,val turn:Int,val concave:Boolean) {
    val normalX get()=P(-1,0,0).turn(turn)
    val normalZ get()=P(0,0,-1).turn(turn)
    val sideX get()=if(concave) pos else pos+P(-normalZ.x,0,-normalZ.z)
    val sideZ get()=if(concave) pos else pos+P(-normalX.x,0,-normalX.z)
}

object RoomVerticalEdges {
    private val cache=java.util.concurrent.ConcurrentHashMap<List<Rect>,List<RoomVerticalEdge>>()
    fun of(rectangles:List<Rect>):List<RoomVerticalEdge> {
        if(cache.size>512) cache.clear()
        return cache.getOrPut(rectangles.toList()) { compute(rectangles) }
    }
    private fun compute(rectangles:List<Rect>):List<RoomVerticalEdge> {
        val cells=RoomGeometry.cells(rectangles)
        val vertices=cells.flatMap { p -> listOf(p,p+P(1,0,0),p+P(0,0,1),p+P(1,0,1)) }.toSet()
        return vertices.sortedWith(compareBy({it.z},{it.x})).mapNotNull { vertex ->
            // SE, SW, NW, NE follow the same quarter turns as Copycat geometry.
            val quadrants=listOf(vertex,vertex+P(-1,0,0),vertex+P(-1,0,-1),vertex+P(0,0,-1))
            val inside=quadrants.indices.filter { quadrants[it] in cells }
            val turn=when(inside.size) { 1->inside.single();3->(quadrants.indices.single { it !in inside }+2)%4;else->return@mapNotNull null }
            RoomVerticalEdge("${vertex.x}:${vertex.z}",vertex,quadrants[(turn+2)%4],turn,inside.size==3)
        }
    }

    /** Preserve edges at unchanged boundary vertices; never move an assignment to a different corner. */
    fun reshape(node:BuildingNode,rectangles:List<Rect>):Map<String,String>? {
        val keys=of(rectangles).map { it.id }.toSet()
        return node.verticalEdges?.filterKeys(keys::contains)
    }
}
