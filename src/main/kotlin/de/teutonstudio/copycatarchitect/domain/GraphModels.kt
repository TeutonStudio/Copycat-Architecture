package de.teutonstudio.copycatarchitect.domain

enum class NodeKind { INPUT, UNION, INTERSECTION, DIFFERENCE, SYMMETRIC_DIFFERENCE, ROTATE, ROTATION, OUTPUT }
enum class MaterialMode { SEPARATED, GROUPED }
enum class BuildingKind { COPYCAT, SEGMENT, PATTERN, FLOOR, CEILING, ROOM, STOREY, ROOF, BUILDING }

/** Shared graph contract for the Minecraft and browser editors. */
object GraphModels {
    const val FORM_NODES = 64
    const val FORM_SOURCES = 32
    const val BUILDING_NODES = 96
    const val SOCKETS = 16
    const val LEVELS = 8
    val formSchemas = 1..2
    val buildingSchemas = 1..1
    val coordinates = -8192..8192

    fun inputs(kind: NodeKind): IntRange = when (kind) {
        NodeKind.INPUT, NodeKind.ROTATION -> 0..0
        NodeKind.OUTPUT -> 1..1
        NodeKind.DIFFERENCE, NodeKind.ROTATE -> 2..2
        NodeKind.UNION, NodeKind.INTERSECTION, NodeKind.SYMMETRIC_DIFFERENCE -> 2..SOCKETS
    }

    fun accepts(target: NodeKind, slot: Int, source: NodeKind): Boolean =
        source != NodeKind.OUTPUT && (source == NodeKind.ROTATION) == (target == NodeKind.ROTATE && slot == 1)

    fun requiredBuildingInputs(kind: BuildingKind, storeyMode: String): Int = when (kind) {
        BuildingKind.PATTERN -> 2
        BuildingKind.STOREY -> if (storeyMode == "rooms") 1 else 0
        else -> 0
    }

    val expandableBuildingInputs = setOf(BuildingKind.SEGMENT, BuildingKind.FLOOR, BuildingKind.CEILING, BuildingKind.ROOF)
}

data class BuildingPort(val key:String,val source:String,val accepts:Set<BuildingKind>,val number:Int=0,val length:Int=0,val height:Int=0)
object BuildingPorts {
    private val segments=setOf(BuildingKind.SEGMENT,BuildingKind.PATTERN)
    private fun copies(n:BuildingNode,expanding:Boolean)=inputs(n,setOf(BuildingKind.COPYCAT),expanding)
    private fun inputs(n:BuildingNode,kinds:Set<BuildingKind>,expanding:Boolean)=
        (n.inputs+if(expanding && n.inputs.size<GraphModels.SOCKETS && n.inputs.none { it.isEmpty() }) listOf("") else emptyList()).mapIndexed { i,s -> BuildingPort("in:$i",s,kinds,i) }
    private fun enclosure(n:BuildingNode)=RoomGeometry.edges(n.rectangles).mapIndexed { i,e -> BuildingPort("wall:${e.id}",n.walls[e.id].orEmpty().ifEmpty { "empty_wall" },segments,i,e.length,n.height) }+
        listOf(BuildingPort("floor",n.floor,setOf(BuildingKind.FLOOR)),BuildingPort("ceiling",n.ceiling,setOf(BuildingKind.CEILING)),BuildingPort("corner",n.corner,setOf(BuildingKind.COPYCAT)))
    fun of(n:BuildingNode,expanding:Boolean=false):List<BuildingPort> = when(n.kind) {
        BuildingKind.SEGMENT -> if(n.id=="empty_wall") emptyList() else copies(n,expanding)
        BuildingKind.FLOOR,BuildingKind.CEILING -> copies(n,expanding)
        BuildingKind.PATTERN -> n.inputs.mapIndexed { i,s -> BuildingPort("in:$i",s,segments,i) }
        BuildingKind.ROOM -> enclosure(n)
        BuildingKind.STOREY -> if(n.storeyMode=="walls") enclosure(n) else inputs(n,setOf(BuildingKind.ROOM),expanding)
        BuildingKind.ROOF -> copies(n,expanding)+(0..3).map { BuildingPort("wall:$it",n.walls[it.toString()].orEmpty(),segments,it) }
        BuildingKind.BUILDING -> {
            val levels=n.levels.keys+0+(if(expanding) setOf((n.levels.keys.maxOrNull() ?: 0)+1,(n.levels.keys.minOrNull() ?: 0)-1) else emptySet())
            listOf(BuildingPort("roof",n.roof,setOf(BuildingKind.ROOF)))+levels.filter { it in -GraphModels.LEVELS..GraphModels.LEVELS }.sortedDescending().map { i -> BuildingPort("level:$i",n.levels[i].orEmpty(),setOf(BuildingKind.STOREY),i) }
        }
        else -> emptyList()
    }
}
