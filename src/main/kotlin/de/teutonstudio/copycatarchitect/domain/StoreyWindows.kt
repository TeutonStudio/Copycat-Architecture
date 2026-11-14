package de.teutonstudio.copycatarchitect.domain

/** A reusable window definition is mounted independently on each storey wall. */
data class WindowInstance(val id:String="",val source:String="",val roomIndex:Int=0,val edge:String="",val offset:Int=0,val sill:Int=1,val depthOffset:Int=0,val autoOpening:Boolean=true)
data class WindowWall(val roomIndex:Int,val edge:String,val direction:Int,val cells:List<P>,val height:Int)

object StoreyWindows {
    private val segments=setOf(BuildingKind.ROTATION_SEGMENT,BuildingKind.SEGMENT,BuildingKind.PATTERN,BuildingKind.SEGMENT_ROTATE,BuildingKind.SEGMENT_MIRROR)+SegmentBooleans.kinds
    fun validate(n:BuildingNode,nodes:Map<String,BuildingNode>) {
        require(n.windows.isNullOrEmpty() || n.kind==BuildingKind.STOREY) { "Fensterinstanzen gehören zu einem Stockwerk" }
        val items=n.windows.orEmpty()
        require(items.size<=64 && items.map { it.id }.distinct().size==items.size && items.all { it.id.matches(Regex("[a-zA-Z0-9_-]{1,36}")) }) { "Fenster benötigen eindeutige Kennungen; höchstens 64 je Stockwerk" }
        for(i in items) {
            require(i.source.isEmpty() || nodes[i.source]?.kind==BuildingKind.WINDOW) { "Fensterinstanz benötigt einen Fensterknoten" }
            require(i.roomIndex in 0..15 && i.offset in 0..128 && i.sill in 0..128 && i.depthOffset in -128..128) { "Ungültige Fensterposition" }
        }
        if(n.kind==BuildingKind.WINDOW) {
            require(n.inputs.size==1 && (n.inputs[0].isEmpty() || nodes[n.inputs[0]]?.kind in segments)) { "Fenster benötigt genau eine Segmentquelle für Rahmen und Glas" }
            require(n.length in 1..32 && n.height in 1..16) { "Fensterbreite 1–32, Fensterhöhe 1–16 Blöcke" }
        }
    }
    /** Stable wall IDs and world coordinates also drive the two editors' floor-plan controls. */
    fun walls(graph:BuildingGraph,n:BuildingNode):List<WindowWall> {
        val hosts=if(n.storeyMode=="walls") listOf(0 to n) else n.inputs.mapIndexedNotNull { index,id->graph.nodes.firstOrNull { it.id==id && it.kind==BuildingKind.ROOM }?.let { index to it } }
        return hosts.flatMap { (index,host)->
            val resolved=graph.resolvedNode(host)
            val offset=if(n.storeyMode=="walls") P() else n.offsets.getOrNull(index) ?: P()
            val height=if(host.roomConfig?.mode==RoomMode.SEGMENTS) RoomTopology.outline(graph,host).height else resolved.height
            SpecialRooms.anchors(graph,resolved).map { a->WindowWall(index,a.edge,a.direction,a.cells.map { it+offset },height) }
        }
    }
    fun definition(n:BuildingNode,segment:(String)->FixedSegment,placement:(P,List<SegmentCell>,Int,String)->Placement,copycat:(String)->Placement):Plan {
        val source=n.inputs.singleOrNull().orEmpty()
        require(source.isNotEmpty()) { "Fenster ${n.name.ifBlank { n.id }}: Segment mit Fenstergeometrie anschließen" }
        val value=segment(source)
        require(!value.air && value.length>0 && value.height>0 && n.length%value.length==0 && n.height%value.height==0) { "Fenstermaße müssen ganze Vielfache der Segmentmaße sein; Rahmen werden nicht gestreckt oder abgeschnitten" }
        val period=SpecialRooms.wallPeriod(value,placement,copycat)
        val r=Resolver();val occupied=mutableSetOf<P>()
        for(x in 0 until n.length step value.length) for(y in 0 until n.height step value.height) for(p in period.values) {
            val pos=p.pos+P(x,y,0);occupied+=pos;r.add(p.copy(pos=pos))
        }
        for(x in 0 until n.length) for(y in 0 until n.height) for(z in 0 until value.depth) {
            val p=P(x,y,z);if(p !in occupied) r.clear(p)
        }
        return r.plan(emptyList())
    }
    fun attach(graph:BuildingGraph,n:BuildingNode,base:Plan,segment:(String)->FixedSegment,placement:(P,List<SegmentCell>,Int,String)->Placement,copycat:(String)->Placement):Plan {
        if(n.windows.isNullOrEmpty()) return base
        val walls=walls(graph,n)
        val cleared=base.clear.toSet()
        val cuts=linkedSetOf<P>();val inserts=mutableListOf<Placement>();val occupiedOpenings=mutableSetOf<P>()
        val nodes=graph.nodes.associateBy { it.id }
        for(i in n.windows) {
            val prefix="Fenster ${i.id}"
            val def=nodes[i.source]?.takeIf { it.kind==BuildingKind.WINDOW } ?: error("$prefix: Fensterquelle anschließen")
            val a=walls.firstOrNull { it.roomIndex==i.roomIndex && it.edge==i.edge } ?: error("$prefix: gültige Zielwand am Stockwerk wählen")
            require(i.offset+def.length<=a.cells.size) { "$prefix: Breite oder Position liegt außerhalb der Zielwand" }
            require(i.sill+def.height<=a.height) { "$prefix: Einbauhöhe und Fensterhöhe überschreiten die Stockwerkshöhe" }
            val normal=RoomGeometry.directions[a.direction]
            val tangent=P(1,0,0).turn(a.direction)
            val origin=a.cells[i.offset]+P(0,i.sill,0)
            fun dot(p:P,d:P)=p.x*d.x+p.y*d.y+p.z*d.z
            // Derive the complete layer span from geometry, including arbitrarily wide air gaps.
            val reach=base.placements.filter { it.component in setOf("wall","trim") }.maxOfOrNull { kotlin.math.abs(dot(it.pos-origin,normal)) } ?: 1
            fun within(p:P):Boolean {
                val local=p-origin
                return dot(local,tangent) in 0 until def.length && local.y in 0 until def.height && dot(local,normal) in -reach..reach
            }
            // Do not reach the opposite wall across an interior (even in narrow rooms).
            // Exterior air gaps are absent from base.clear and do not stop the wall cut.
            val intervals=mutableMapOf<Pair<Int,Int>,Pair<Int,Int>>()
            fun sameWall(p:P):Boolean {
                val local=p-origin;val x=dot(local,tangent);val y=local.y;val d=dot(local,normal)
                val (near,far)=intervals.getOrPut(x to y) {
                    fun air(distance:Int):Boolean = origin+P(tangent.x*x+normal.x*distance,y,tangent.z*x+normal.z*distance) in cleared
                    val inward=(1..reach).firstOrNull { air(-it) } ?: (reach+1)
                    // An opposing room owns the far side of a courtyard; do not cut its facade.
                    val opposing=walls.asSequence().filter { it.direction==(a.direction+2)%4 }.flatMap { it.cells.asSequence() }
                        .filter { dot(it-origin,tangent)==x && dot(it-origin,normal)>0 }.map { dot(it-origin,normal) }.minOrNull()
                    val outward=minOf((1..reach).firstOrNull { air(it) } ?: (reach+1),opposing?.let { (it+1)/2 } ?: (reach+1))
                    (1-inward) to (outward-1)
                }
                return d in near..far
            }
            val wallCells=base.placements.filter { it.component in setOf("wall","trim") && within(it.pos) && sameWall(it.pos) }.map { it.pos }.toSet()
            for(x in 0 until def.length) require(wallCells.any { dot(it-origin,tangent)==x }) { "$prefix: An dieser Position fehlt eine geschlossene Wand" }
            require(wallCells.none { it in occupiedOpenings }) { "$prefix: Fensteröffnungen überschneiden sich" }
            require(i.depthOffset in wallCells.minOf { dot(it-origin,normal) }..wallCells.maxOf { dot(it-origin,normal) }) { "$prefix: Tiefenversatz muss innerhalb der Wandlagen liegen" }
            occupiedOpenings+=wallCells
            if(i.autoOpening) cuts+=wallCells
            val profile=definition(def,segment,placement,copycat)
            for(p in profile.placements) {
                val target=origin+p.pos.turn(a.direction)+P(normal.x*i.depthOffset,0,normal.z*i.depthOffset)
                require(within(target)) { "$prefix: Tiefenversatz liegt außerhalb der Wandlagen" }
                inserts+=SpecialRooms.mounted(p,target,a.direction).copy(component="window")
            }
        }
        val original=base.placements.filterNot { it.pos in cuts && it.component in setOf("wall","trim") }
        val originalPositions=original.map { it.pos }.toSet()
        val r=Resolver();original.forEach { r.add(it) }
        for(p in inserts) {
            require(p.pos !in originalPositions) { "Fenster kollidiert mit der Wand; automatische Wandöffnung aktivieren oder Tiefenversatz ändern" }
            r.add(p,true)
        }
        val occupied=(original.map { it.pos }+inserts.map { it.pos }).toSet()
        (base.clear+cuts).filterNot(occupied::contains).distinct().forEach(r::clear)
        return r.plan(base.warnings)
    }
}
