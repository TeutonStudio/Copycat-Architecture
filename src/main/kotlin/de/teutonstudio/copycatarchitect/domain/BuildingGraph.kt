package de.teutonstudio.copycatarchitect.domain

import java.util.concurrent.ConcurrentHashMap

enum class BuildingKind { AREA, COPYCAT, SEGMENT_LAYOUT, SEGMENT, PATTERN, ROOM_GEOMETRY, FLOOR, CEILING, ROOM, STOREY, ROOF_LEVEL, ROOF_LAYOUT, ROOF, STAIRWELL, ELEVATOR_SHAFT, SERVICE_SHAFT, BUILDING, GARDEN, PATH, SEGMENT_MIRROR, COPYCAT_MIRROR, SEGMENT_ROTATE, COPYCAT_ROTATE, ROOM_LAYOUT, SEGMENT_UNION, SEGMENT_INTERSECTION, SEGMENT_DIFFERENCE, SEGMENT_SYMMETRIC_DIFFERENCE, COPYCAT_UNION, COPYCAT_INTERSECTION, COPYCAT_DIFFERENCE, COPYCAT_SYMMETRIC_DIFFERENCE, ROTATION, MIRRORING, BAY_WINDOW, DORMER }
data class SegmentLayoutCell(val x:Int=0,val y:Int=0,val sourceIndex:Int=0,val turn:Int=0)
data class BuildingNode(
    val id:String="",val kind:BuildingKind=BuildingKind.SEGMENT,val name:String="",val x:Int=0,val y:Int=0,
    val inputs:List<String> = emptyList(),val module:String="window@1",val block:String="",val length:Int=3,val height:Int=4,
    val rectangles:List<Rect> = listOf(Rect(width=3,length=3)),val walls:Map<String,String> = emptyMap(),
    val floor:String="",val ceiling:String="",val offsets:List<P> = emptyList(),
    val form:String="full@1",val thickness:Int=1,val occupied:Boolean=false,val finish:String="",
    val levels:Map<Int,String> = emptyMap(),val roof:String="",val roofType:String="flat",val ridge:Int=0,val overhang:Int=0,
    val materialMode:MaterialMode=MaterialMode.SEPARATED,val role:String="wall",val preview:String="all",val storeyMode:String="rooms",val corner:String="",
    val patternAxis:String="horizontal",val layout:String="",val geometry:String="",val coordinates:List<P> = emptyList(),
    val layoutCells:List<SegmentLayoutCell> = emptyList(),val assembly:Boolean=false,
    val depth:Int=1,val regionCopies:Map<String,String> = emptyMap(),val transforms:List<SegmentTransform> = emptyList(),
    val rotationAxis:RotationAxis=RotationAxis.Y,val quarterTurns:Int=0,
    val fenced:Boolean=false,val fenceBlock:String="minecraft:oak_fence",val fenceHeight:Int=1,
    val pathWidth:Int=1,val groundDepth:Int=1,val groundBlock:String="minecraft:dirt",val clearance:Int=0,val elevation:Int=0,val landscapeOffset:P=P(),
    val buildingOffset:P=P(),val sitePaths:Map<Int,List<P>> = emptyMap(),
    val roofLevel:RoofLevelConfig?=null,val roofConfig:RoofConfig?=null,val levelIds:Map<Int,String> = emptyMap(),
    val stairwell:StairwellConfig?=null,val elevatorShaft:ElevatorShaftConfig?=null,val serviceShaft:ServiceShaftConfig?=null,
    val accessInstances:List<VerticalAccessInstance> = emptyList(),
    val mirror:CopycatMirror?=null,val mirrorPositionsOnly:Boolean?=null,
    val surfaces:Map<String,String>?=null,
    val verticalEdges:Map<String,String>?=null,
    // Missing values select joined end rows for explicit rooms; false retains old free-interior layouts.
    val joinWallEnds:Boolean?=null,
    val roomLayout:RoomLayoutConfig?=null,val roomConfig:RoomConfig?=null,val facade:FacadeConfig?=null,
    val features:SegmentFeatures?=null,val attachments:List<AttachmentSpec>?=null,
    val bayWindow:BayWindowConfig?=null,val dormer:DormerConfig?=null,
    val bayWindows:List<BayWindowInstance>?=null,val dormers:List<DormerInstance>?=null,
    val roofPeak:String?=null,
    val rotation:String?=null,val mirroring:String?=null,val rotations:List<SegmentRotation>?=null,val mirrors:List<CopycatMirror>?=null
)
/** Optional arrays stay absent in older JSON documents until the feature is used. */
val BuildingNode.bayWindowInstances get()=bayWindows.orEmpty()
val BuildingNode.dormerInstances get()=dormers.orEmpty()
/** Optional Copycat and outdoor sequences grow through spare ports; unused tails are not required inputs. */
internal fun BuildingNode.trimCopycatInputs():BuildingNode {
    if(kind !in setOf(BuildingKind.SEGMENT,BuildingKind.FLOOR,BuildingKind.CEILING,BuildingKind.ROOF,BuildingKind.ROOF_LAYOUT,BuildingKind.BUILDING,BuildingKind.ROOM_LAYOUT)) return this
    val trimmed=inputs.dropLastWhile(String::isEmpty)
    return if(trimmed.size==inputs.size) this else copy(inputs=trimmed,offsets=offsets.take(trimmed.size),sitePaths=sitePaths.filterKeys { it<trimmed.size })
}
data class BuildingGraph(val schema:Int=1,val nodes:List<BuildingNode> = emptyList(),val projection:BuildingProjection=BuildingProjection(),
                         val nodeGroups:List<GraphGroup> = emptyList()) {
    fun resultNode():BuildingNode=nodes.single { n -> n.kind==BuildingKind.BUILDING && nodeGroups.none { n.id in it.nodes } }
    fun replace(n:BuildingNode)=GraphGroups.synchronize(this,copy(nodes=nodes.map { if(it.id==n.id) n else it })).withCurrentSchema()
    fun remove(id:String):BuildingGraph=GraphGroups.synchronize(this,removeLocal(id))
    internal fun removeLocal(id:String):BuildingGraph {
        require(id!="empty_wall" && nodes.none { n -> n.id==id && n.kind==BuildingKind.BUILDING && nodeGroups.none { id in it.nodes } }) { "Keep the result and Empty wall source" }
        return copy(nodeGroups=GraphGroups.prune(nodeGroups,nodes.map { it.id }.toSet()-id),nodes=nodes.filter { it.id!=id }.map { original -> val n=BuildingSemantics.remapReferences(original) { _,ref->if(ref==id) "" else ref };n.copy(
            inputs=n.inputs.map { if(it==id) "" else it },walls=n.walls.filterValues { it!=id },verticalEdges=n.verticalEdges?.filterValues { it!=id },
            floor=if(n.floor==id) "" else n.floor,ceiling=if(n.ceiling==id) "" else n.ceiling,corner=if(n.corner==id) "" else n.corner,
            roof=if(n.roof==id) "" else n.roof,roofPeak=if(n.roofPeak==id) null else n.roofPeak,
            rotation=if(n.rotation==id) null else n.rotation,mirroring=if(n.mirroring==id) null else n.mirroring,
            layout=if(n.layout==id) "" else n.layout,geometry=if(n.geometry==id) "" else n.geometry,
            regionCopies=n.regionCopies.filterValues { it!=id },sitePaths=n.sitePaths.filterKeys { n.inputs.getOrNull(it)!=id },
            roofLevel=n.roofLevel?.let { it.copy(area=if(it.area==id) "" else it.area) },accessInstances=n.accessInstances.filter { it.source!=id },
            stairwell=n.stairwell?.let { it.copy(tread=it.tread.takeUnless { s->s==id }.orEmpty(),landing=it.landing.takeUnless { s->s==id }.orEmpty(),enclosure=it.enclosure.takeUnless { s->s==id }.orEmpty()) },
            elevatorShaft=n.elevatorShaft?.let { it.copy(enclosure=it.enclosure.takeUnless { s->s==id }.orEmpty()) },
            serviceShaft=n.serviceShaft?.let { it.copy(enclosure=it.enclosure.takeUnless { s->s==id }.orEmpty()) },
            roofConfig=n.roofConfig?.let { it.copy(wall=it.wall.takeUnless { s->s==id }.orEmpty(),variants=it.variants.filterValues { s->s!=id },families=it.families.map { family->family.copy(variants=family.variants.filterValues { s->s!=id }) }) }).withLevels(n.levels.filterValues { it!=id }).trimCopycatInputs() }).withCurrentSchema()
    }
    fun connect(target:String,key:String,source:String):BuildingGraph {
        val n=nodes.first { it.id==target }
        require(BuildingPorts.of(n,this,true).any { it.key==key }) { "Unknown building socket" }
        var changed=when {
            key=="rotation" -> n.copy(rotation=source.takeIf(String::isNotEmpty))
            key=="mirroring" -> n.copy(mirroring=source.takeIf(String::isNotEmpty))
            key=="layout" -> n.copy(layout=source,assembly=true,roomConfig=if(n.kind==BuildingKind.ROOM) (n.roomConfig ?: RoomConfig()).copy(mode=RoomMode.SEGMENTS) else n.roomConfig)
            key=="geometry" -> n.copy(geometry=source,assembly=if(n.kind==BuildingKind.ROOM) true else n.assembly)
            key.startsWith("region:") -> n.copy(regionCopies=resolvedNode(n).withRegionCopy(key,source).regionCopies)
            key=="tread" -> n.copy(stairwell=(n.stairwell ?: StairwellConfig()).copy(tread=source))
            key=="landing" -> n.copy(stairwell=(n.stairwell ?: StairwellConfig()).copy(landing=source))
            key=="enclosure" -> when(n.kind) {
                BuildingKind.STAIRWELL->n.copy(stairwell=(n.stairwell ?: StairwellConfig()).copy(enclosure=source))
                BuildingKind.ELEVATOR_SHAFT->n.copy(elevatorShaft=(n.elevatorShaft ?: ElevatorShaftConfig()).copy(enclosure=source))
                else->n.copy(serviceShaft=(n.serviceShaft ?: ServiceShaftConfig()).copy(enclosure=source))
            }
            key=="floor" -> n.copy(floor=source)
            key=="ceiling" -> n.copy(ceiling=source)
            key=="corner" -> n.copy(corner=source)
            key.startsWith("edge:") -> n.copy(verticalEdges=if(source.isEmpty()) n.verticalEdges.orEmpty()-key.removePrefix("edge:") else n.verticalEdges.orEmpty()+(key.removePrefix("edge:") to source))
            key=="roofPeak" -> n.copy(roofPeak=source.takeIf(String::isNotEmpty))
            key=="roof" -> n.copy(roof=source)
            key=="area" -> n.copy(roofLevel=(n.roofLevel ?: RoofLevelConfig()).copy(area=source))
            key.startsWith("access:") -> {
                val i=key.removePrefix("access:").toInt();val items=n.accessInstances.toMutableList()
                if(i==items.size && source.isNotEmpty()) items+=VerticalAccessInstance(id=freshAccessInstanceId(items),source=source)
                else if(i<items.size) { if(source.isEmpty()) items.removeAt(i) else items[i]=items[i].copy(source=source) }
                n.copy(accessInstances=items)
            }
            key.startsWith("wall:") -> {
                val edge=key.removePrefix("wall:")
                n.copy(walls=if(n.kind==BuildingKind.ROOF && source.isEmpty()) n.walls-edge else n.walls+(edge to source.ifEmpty { "empty_wall" }))
            }
            key.startsWith("level:") -> {
                val level=key.removePrefix("level:").toInt();require(level in -8..8)
                n.withLevels(if(source.isEmpty()) n.levels-level else n.levels+(level to source))
            }
            key.startsWith("in:") -> {
                val i=key.removePrefix("in:").toInt();require(i in 0 until 16 && i<=n.inputs.size)
                val inputs=n.inputs.toMutableList();if(i==inputs.size) inputs+="";inputs[i]=source
                n.copy(inputs=inputs,offsets=List(inputs.size) { n.offsets.getOrNull(it) ?: P() },sitePaths=if(n.inputs.getOrNull(i)!=source) n.sitePaths-i else n.sitePaths)
            }
            key.startsWith("semantic:") -> BuildingSemantics.remapReferences(n) { refKey,ref -> if(refKey==key.removePrefix("semantic:")) source else ref }
            else -> error("Unknown building socket")
        }
        if(key=="geometry" && n.kind==BuildingKind.ROOM && source.isNotEmpty()) {
            val connected=replace(changed).resolvedNode(changed)
            changed=RoomGeometry.reshape(resolvedNode(n),connected.rectangles).copy(geometry=source,assembly=true,rectangles=n.rectangles)
        }
        val graph=replace(changed.trimCopycatInputs());BuildingGraphCompiler.structure(graph)
        if(key=="geometry" && n.kind==BuildingKind.ROOM && n.geometry!=changed.geometry) graph.validateRoomPlacements(setOf(target))
        return graph.remapGeometryDependents(this)
    }
    companion object {
        fun compactLevels(levels:Map<Int,String>):Map<Int,String> = buildMap {
            levels[0]?.takeIf { it.isNotEmpty() }?.let { put(0,it) }
            levels.filter { it.key>0 && it.value.isNotEmpty() }.toSortedMap().values.forEachIndexed { i,s -> put(i+1,s) }
            levels.filter { it.key<0 && it.value.isNotEmpty() }.toSortedMap(reverseOrder()).values.forEachIndexed { i,s -> put(-i-1,s) }
        }
        fun starter()=BuildingGraph(nodes=listOf(
            BuildingNode("empty_wall",BuildingKind.SEGMENT,"Empty wall",20,20,block="minecraft:air",length=1),
            BuildingNode("wall",BuildingKind.SEGMENT,"Solid wall",20,175,module=""),
            BuildingNode("floor",BuildingKind.FLOOR,"Floor",20,350),
            BuildingNode("ceiling",BuildingKind.CEILING,"Ceiling",20,525),
            BuildingNode("room",BuildingKind.ROOM,"Room",300,60,walls=RoomGeometry.edges(listOf(Rect(width=3,length=3))).associate { it.id to "wall" },floor="floor",ceiling="ceiling"),
            BuildingNode("storey",BuildingKind.STOREY,"Storey",650,60,inputs=listOf("room"),offsets=listOf(P())),
            BuildingNode("roof",BuildingKind.ROOF,"Roof",650,460,role="roof"),
            BuildingNode("building",BuildingKind.BUILDING,"Building",1000,100,levels=mapOf(0 to "storey"),roof="roof")
        ))
        /** The current starter exposes every assembly step; saved direct-wall storeys retain their mode. */
        fun assembledStarter()=BuildingGraph(nodes=listOf(
            BuildingNode("empty_wall",BuildingKind.SEGMENT,"Empty wall",20,20,block="minecraft:air",length=1),
            BuildingNode("wall_copycat",BuildingKind.COPYCAT,"Wall Copycat",20,200),
            BuildingNode("roof_copycat",BuildingKind.COPYCAT,"Roof Copycat",20,550,role="roof"),
            BuildingNode("wall",BuildingKind.SEGMENT,"Wall segment",290,30,inputs=listOf("wall_copycat"),module=""),
            BuildingNode("floor",BuildingKind.FLOOR,"Floor",290,220,inputs=listOf("wall_copycat")),
            BuildingNode("ceiling",BuildingKind.CEILING,"Ceiling",290,440,inputs=listOf("wall_copycat")),
            BuildingNode("gable_wall",BuildingKind.SEGMENT,"Gable segment",290,660,inputs=listOf("wall_copycat"),module="",length=5,height=2),
            BuildingNode("room",BuildingKind.ROOM,"Room",600,30,walls=RoomGeometry.edges(listOf(Rect(width=3,length=3))).associate { it.id to "wall" },floor="floor",ceiling="ceiling"),
            BuildingNode("storey",BuildingKind.STOREY,"Storey",900,30,inputs=listOf("room"),offsets=listOf(P())),
            BuildingNode("roof",BuildingKind.ROOF,"Roof",900,560,inputs=listOf("roof_copycat"),walls=mapOf("0" to "gable_wall","2" to "gable_wall"),roofType="gable",role="roof"),
            BuildingNode("building",BuildingKind.BUILDING,"Building",1230,100,levels=mapOf(0 to "storey"),roof="roof")
        ))
        fun explicitStarter(joinWallEnds:Boolean=true)=BuildingAssembly.starter(joinWallEnds)
    }
}
data class WallRun(val id:String,val direction:Int,val cells:List<P>) { val length get()=cells.size }
object RoomGeometry {
    val directions=listOf(P(0,0,-1),P(1,0,0),P(0,0,1),P(-1,0,0))
    private data class Shape(val cells:Set<P>,val edges:List<WallRun>,val corners:Map<P,Pair<P,P>>)
    private val cache=ConcurrentHashMap<List<Rect>,Shape>()
    private fun shape(rectangles:List<Rect>):Shape=cache.getOrPut(rectangles.toList()) {
        require(rectangles.size in 1..64) { "A room needs 1–64 rectangles" }
        rectangles.forEach { require(it.width in 1..32 && it.length in 1..32 && it.x in -32..63 && it.z in -32..63 && it.x+it.width<=64 && it.z+it.length<=64) { "Room footprint exceeds grid bounds" } }
        val cells=buildSet { rectangles.forEach { r -> for(x in r.x until r.x+r.width) for(z in r.z until r.z+r.length) add(P(x,0,z)) } }
        require(cells.size<=4096) { "Room exceeds 4096 cells" }
        val edges=boundary(cells);val corners=linkedMapOf<P,Pair<P,P>>()
        for(q in 0..3) {
            val normal=directions[q];val next=directions[(q+1)%4]
            cells.filter { it+normal !in cells && it+next !in cells && it+normal+next !in cells }.forEach { p -> corners[p+normal+next]=(p+normal) to (p+next) }
        }
        require(edges.size<=64) { "Room has more than 64 wall runs" }
        if(cache.size>512) cache.clear();Shape(cells,edges,corners)
    }
    /** Wall runs lie immediately outside the supplied footprint, including irregular roofs. */
    fun boundary(cells:Set<P>):List<WallRun> {
        val edges=mutableListOf<WallRun>()
        for(q in 0..3) {
            val normal=directions[q];val along=P(-normal.z,0,normal.x)
            val boundary=cells.filter { it+normal !in cells }.toSet();val done=mutableSetOf<P>()
            for(p in boundary.sortedWith(compareBy({it.z},{it.x}))) {
                if(p in done) continue
                var start=p;while(start+P(-along.x,0,-along.z) in boundary) start+=P(-along.x,0,-along.z)
                val run=mutableListOf<P>();var cursor=start
                while(cursor in boundary) { run+=cursor+normal;done+=cursor;cursor+=along }
                edges+=WallRun("$q:${start.x}:${start.z}",q,run)
            }
        }
        return edges
    }
    fun cells(rects:List<Rect>)=shape(rects).cells
    fun edges(rects:List<Rect>)=shape(rects).edges
    fun corners(rects:List<Rect>)=shape(rects).corners
    fun shell(rects:List<Rect>)=cells(rects)+edges(rects).flatMap { it.cells }+corners(rects).keys
    /** Store a clicked block footprint in the existing rectangle schema without filling its gaps. */
    fun fromCells(cells:Set<P>):List<Rect> {
        require(cells.isNotEmpty()) { "A room needs at least one block" }
        require(cells.size<=4096 && cells.all { it.y==0 && it.x in -32..63 && it.z in -32..63 }) { "Room blocks exceed grid bounds" }
        val remaining=cells.toMutableSet();val rectangles=mutableListOf<Rect>()
        while(remaining.isNotEmpty()) {
            val start=remaining.minWith(compareBy({it.z},{it.x}))
            var width=1
            while(width<32 && start+P(width,0,0) in remaining) width++
            var length=1
            while(length<32 && (0 until width).all { start+P(it,0,length) in remaining }) length++
            rectangles+=Rect(start.x,start.z,width,length)
            for(x in 0 until width) for(z in 0 until length) remaining-=start+P(x,0,z)
        }
        shape(rectangles)
        return rectangles
    }
    fun toggleCell(node:BuildingNode,cell:P):BuildingNode {
        require(node.kind==BuildingKind.ROOM || node.kind==BuildingKind.STOREY && node.storeyMode=="walls") { "Only a room has an editable block footprint" }
        require(cell.y==0 && cell.x in -32..63 && cell.z in -32..63) { "Room footprint exceeds grid bounds" }
        val cells=cells(node.rectangles).toMutableSet()
        if(!cells.add(cell)) cells.remove(cell)
        return reshape(node,fromCells(cells))
    }
    fun reshape(node:BuildingNode,rects:List<Rect>):BuildingNode {
        val old=edges(node.rectangles);val next=edges(rects)
        // Reserve unchanged runs before considering new runs, so an inserted edge cannot steal their source.
        val exact=next.mapNotNull { edge -> old.firstOrNull { it.id==edge.id }?.let { edge.id to it } }.toMap()
        val used=exact.values.map { it.id }.toMutableSet()
        val walls=next.associate { edge ->
            val overlap=old.filter { it.direction==edge.direction }.map { it to it.cells.count(edge.cells.toSet()::contains) }.filter { it.second>0 }
                .maxWithOrNull(compareBy<Pair<WallRun,Int>>({ it.second },{ if(it.first.length==edge.length) 1 else 0 }))?.first
            val previous=exact[edge.id] ?: overlap ?: old.filter { it.direction==edge.direction && it.id !in used }.minWithOrNull(compareBy<WallRun>({ if(it.length==edge.length) 0 else 1 },{ kotlin.math.abs(it.cells.first().x-edge.cells.first().x)+kotlin.math.abs(it.cells.first().z-edge.cells.first().z) }))
            if(previous!=null) used+=previous.id
            edge.id to (previous?.let { node.walls[it.id] }.orEmpty().ifEmpty { "empty_wall" })
        }
        return node.copy(rectangles=rects,walls=walls,verticalEdges=RoomVerticalEdges.reshape(node,rects))
    }
}
data class BuildingPort(val key:String,val source:String,val accepts:Set<BuildingKind>,val number:Int=0,val length:Int=0,val height:Int=0)
object BuildingPorts {
    private fun port(n:BuildingNode,key:String,source:String,number:Int=0,length:Int=0,height:Int=0)=
        BuildingPort(key,source,GraphModels.buildingPort(n,key).accepts,number,length,height)

    private fun inputs(n:BuildingNode,expanding:Boolean):List<BuildingPort> {
        val model=GraphModels.buildingPort(n,"in:0")
        val spare=expanding && model.expand==true && n.inputs.size<checkNotNull(model.max) && n.inputs.none(String::isEmpty)
        val sources=if(spare) n.inputs+"" else n.inputs
        return sources.mapIndexed { i,s -> BuildingPort("in:$i",s,model.accepts,i) }
    }

    private fun enclosure(n:BuildingNode)=RoomWallJoints.runs(n).mapIndexed { i,e -> port(n,"wall:${e.id}",n.walls[e.id].orEmpty().ifEmpty { "empty_wall" },i,e.length,n.height) }+
        listOf(port(n,"floor",n.floor),port(n,"ceiling",n.ceiling),port(n,"corner",n.corner))+
        if(n.kind==BuildingKind.ROOM) RoomVerticalEdges.of(n.rectangles).mapIndexed { i,e ->
            port(n,"edge:${e.id}",n.verticalEdges.orEmpty()[e.id].orEmpty(),i,1,n.height)
        } else emptyList()

    fun of(n:BuildingNode,graph:BuildingGraph,expanding:Boolean=false)=of(graph.resolvedNode(n),expanding)
    fun of(n:BuildingNode,expanding:Boolean=false):List<BuildingPort> = (when(n.kind) {
        BuildingKind.SEGMENT -> if(n.id=="empty_wall") emptyList() else {
            val assigned=n.effectiveRegionCopies()
            (if(n.assembly) listOf(port(n,"layout",n.layout)) else emptyList())+inputs(n,expanding)+
                (if(n.assembly) SegmentRegions.of(n.length,n.height,n.depth).mapIndexed { i,region -> port(n,region.key,assigned[region.key].orEmpty(),i) } else emptyList())
        }
        BuildingKind.COPYCAT_MIRROR,BuildingKind.COPYCAT_ROTATE,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_ROTATE -> listOf(port(n,"in:0",n.inputs.firstOrNull().orEmpty())) +
            if(n.kind in setOf(BuildingKind.COPYCAT_ROTATE,BuildingKind.SEGMENT_ROTATE)) listOf(port(n,"rotation",n.rotation.orEmpty())) else listOf(port(n,"mirroring",n.mirroring.orEmpty()))
        BuildingKind.GARDEN -> listOf(port(n,"geometry",n.geometry),port(n,"floor",n.floor))
        BuildingKind.PATH -> listOf(port(n,"floor",n.floor))
        BuildingKind.SEGMENT_LAYOUT,BuildingKind.ROOM_GEOMETRY -> listOf(port(n,"geometry",n.geometry))
        BuildingKind.FLOOR,BuildingKind.CEILING,BuildingKind.PATTERN,BuildingKind.SEGMENT_UNION,BuildingKind.SEGMENT_INTERSECTION,BuildingKind.SEGMENT_DIFFERENCE,BuildingKind.SEGMENT_SYMMETRIC_DIFFERENCE,BuildingKind.COPYCAT_UNION,BuildingKind.COPYCAT_INTERSECTION,BuildingKind.COPYCAT_DIFFERENCE,BuildingKind.COPYCAT_SYMMETRIC_DIFFERENCE,BuildingKind.ROOM_LAYOUT,BuildingKind.ROOF_LAYOUT -> inputs(n,expanding)
        BuildingKind.ROOM -> if(n.roomConfig?.mode==RoomMode.SEGMENTS) listOf(port(n,"layout",n.layout),port(n,"floor",n.floor),port(n,"ceiling",n.ceiling))
            else (if(n.assembly) listOf(port(n,"geometry",n.geometry)) else emptyList())+enclosure(n)
        BuildingKind.STOREY -> if(n.storeyMode=="walls") enclosure(n) else inputs(n,expanding)
        BuildingKind.ROOF_LEVEL -> listOf(port(n,"area",n.roofLevel?.area.orEmpty()))
        BuildingKind.STAIRWELL -> (n.stairwell ?: StairwellConfig()).let { c->listOf(port(n,"tread",c.tread),port(n,"landing",c.landing),port(n,"enclosure",c.enclosure)) }
        BuildingKind.ELEVATOR_SHAFT -> listOf(port(n,"enclosure",n.elevatorShaft?.enclosure.orEmpty()))
        BuildingKind.SERVICE_SHAFT -> listOf(port(n,"enclosure",n.serviceShaft?.enclosure.orEmpty()))
        BuildingKind.ROOF -> if(n.assembly) listOf(port(n,"layout",n.layout))+inputs(n,expanding)
            else inputs(n,expanding)+port(n,"roofPeak",n.roofPeak.orEmpty())+(0..3).map { port(n,"wall:$it",n.walls[it.toString()].orEmpty(),it) }
        BuildingKind.BUILDING -> {
            val levels=n.levels.keys+0+(if(expanding) setOf((n.levels.keys.maxOrNull() ?: 0)+1,(n.levels.keys.minOrNull() ?: 0)-1) else emptySet())
            val levelModel=GraphModels.buildingPort(n,"level:0")
            val accessModel=GraphModels.buildingPort(n,"access:0")
            val access=n.accessInstances.mapIndexed { i,a -> port(n,"access:$i",a.source) }
            val spareAccess=if(expanding && access.size<checkNotNull(accessModel.max)) listOf(port(n,"access:${access.size}","")) else emptyList()
            listOf(port(n,"roof",n.roof))+access+spareAccess+
                levels.filter { it in checkNotNull(levelModel.min)..checkNotNull(levelModel.max) }.sortedDescending().map { i -> port(n,"level:$i",n.levels[i].orEmpty(),i) }+inputs(n,expanding)
        }
        else -> emptyList()
    })+semanticPorts(n)

    private fun semanticPorts(n:BuildingNode):List<BuildingPort> {
        val refs=linkedMapOf<String,String>()
        BuildingSemantics.remapReferences(n) { key,ref ->
            if(!key.startsWith("attachment:") && !(key.startsWith("column:") && key.endsWith(":roof"))) refs[key]=ref
            ref
        }
        return refs.map { (key,ref) -> port(n,"semantic:$key",ref) }
    }
}
data class SegmentCell(val cell:Cell,val overrideRole:String="")
data class FixedSegment(val length:Int,val height:Int,val cells:List<SegmentCell>,val air:Boolean=false,val depth:Int=1,val metadata:SegmentMetadata=SegmentMetadata())
data class CompiledRoom(val plan:Plan,val footprint:Set<P>,val shell:Set<P>,val minimum:Int,val maximum:Int,val bottomThickness:Int,val topThickness:Int,val usableHeight:Int) {
    /** Dimensions include wall segments, their corner joints and structural plate layers. */
    val outerWidth get()=if(shell.isEmpty()) 0 else shell.maxOf { it.x }-shell.minOf { it.x }+1
    val outerDepth get()=if(shell.isEmpty()) 0 else shell.maxOf { it.z }-shell.minOf { it.z }+1
    val totalHeight get()=maximum-minimum
}
data class CompiledStorey(val plan:Plan,val footprint:Set<P>,val minimum:Int,val maximum:Int,val bottomThickness:Int,val topThickness:Int,val roofFootprint:Set<P> = footprint,val bayOpenings:Set<P> = emptySet())
data class StoreyPosition(val level:Int,val base:Int,val minimum:Int,val maximum:Int)
data class CompiledBuilding(val plan:Plan,val storeys:List<StoreyPosition>,val accessFootprints:Map<String,Set<P>> = emptyMap())
object BuildingGraphCompiler {
    fun structure(graph:BuildingGraph) {
        graph.projection.validate()
        require(graph.schema in GraphModels.building.graphSchemas && graph.nodes.size in 1..GraphModels.building.maxNodes) { "Building graph needs 1–96 nodes" }
        val nodes=graph.nodes.associateBy { it.id }
        require(nodes.size==graph.nodes.size && nodes.keys.all { it.matches(Regex("[a-zA-Z0-9_-]{1,36}")) }) { "Building node IDs must be unique" }
        GraphGroups.validate(graph)
        require(graph.nodes.count { n -> n.kind==BuildingKind.BUILDING && graph.nodeGroups.none { n.id in it.nodes } }==1) { "Keep exactly one building result outside group graphs" }
        require(nodes["empty_wall"]?.let { it.kind==BuildingKind.SEGMENT && it.block=="minecraft:air" && it.inputs.isEmpty() }==true) { "Keep the standard Empty wall source" }
        for(n in graph.nodes) {
            BuildingTransformValues.validate(n)
            BuildingSemantics.validate(n,nodes)
            SpecialRooms.validate(n,nodes)
            require(n.roomLayout==null || n.kind==BuildingKind.ROOM_LAYOUT) { "Room arrangements belong to a room layout" }
            require(n.roomConfig==null || n.kind==BuildingKind.ROOM) { "Room settings belong to a room" }
            require(n.facade==null || n.kind==BuildingKind.STOREY) { "Facade settings belong to a storey" }
            require(n.features==null || n.kind in setOf(BuildingKind.SEGMENT,BuildingKind.PATTERN,BuildingKind.SEGMENT_ROTATE,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_UNION,BuildingKind.SEGMENT_INTERSECTION,BuildingKind.SEGMENT_DIFFERENCE,BuildingKind.SEGMENT_SYMMETRIC_DIFFERENCE)) { "Bauteilregeln benötigen ein Segment" }
            require(n.attachments.isNullOrEmpty() || n.kind in setOf(BuildingKind.CEILING,BuildingKind.SEGMENT)) { "Wandanschlüsse benötigen eine Deckenplatte oder ein Segment" }
            require(n.preview in listOf("all","walls","floor","ceiling","roof")) { "Invalid building preview filter" }
            require(n.name.length<=64 && n.x in -8192..8192 && n.y in -8192..8192 && n.materialMode in MaterialMode.entries) { "Invalid building node" }
            require(n.role.matches(Regex("[a-zA-Z0-9_-]{1,32}"))) { "Invalid node material role" }
            require(n.rotationAxis in listOf(RotationAxis.X,RotationAxis.Y,RotationAxis.Z) && n.quarterTurns in 0..3) { "Invalid Copycat rotation" }
            val maximumHeight=if(n.kind in setOf(BuildingKind.SEGMENT_LAYOUT,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_ROTATE) || n.assembly && n.kind in setOf(BuildingKind.SEGMENT,BuildingKind.PATTERN)) 128 else 16
            require(n.depth in 1..128 && n.length.toLong()*n.height*n.depth<=262144 && n.length in 1..128 && n.height in 1..maximumHeight && n.thickness in 1..4 && n.offsets.size<=GraphModels.building.maxInputs && n.inputs.size<=GraphModels.building.maxInputs) { "Invalid fixed dimensions" }
            require(n.regionCopies.isEmpty() || n.kind==BuildingKind.SEGMENT && n.regionCopies.size<=27 && n.regionCopies.keys.all { it.matches(Regex("region:x[01ib]_y[01ib]_z[01ib]")) }) { "Invalid segment region" }
            require(n.transforms.size<=n.inputs.size && n.transforms.all { it.quarterTurns in 0..3 && it.axis in listOf(RotationAxis.X,RotationAxis.Y,RotationAxis.Z) && listOf(it.offset.x,it.offset.y,it.offset.z).all { c -> c in -128..128 } }) { "Invalid segment transformation" }
            require(n.transforms.isEmpty() || n.kind==BuildingKind.PATTERN) { "Only segment composition accepts transformations" }
            val offsetBound=if(n.kind==BuildingKind.BUILDING) 128 else 64
            require(n.offsets.all { (it.y==0 || n.kind==BuildingKind.SEGMENT_UNION && it.y in -64..64) && it.x in -offsetBound..offsetBound && it.z in -offsetBound..offsetBound }) { "Placement offset exceeds bounds" }
            if(n.kind==BuildingKind.BUILDING) BuildingSite.validate(graph,n)
            else require(n.sitePaths.isEmpty()) { "Path placement belongs to the building node" }
            require(n.layout.isEmpty() || n.assembly && n.kind in setOf(BuildingKind.SEGMENT,BuildingKind.ROOF,BuildingKind.ROOM) || n.kind==BuildingKind.ROOM && n.roomConfig?.mode==RoomMode.SEGMENTS) { "Only an explicit segment, room or roof accepts an arrangement source" }
            require(n.geometry.isEmpty() || n.kind in setOf(BuildingKind.SEGMENT_LAYOUT,BuildingKind.ROOM_GEOMETRY,BuildingKind.GARDEN) || n.assembly && n.kind==BuildingKind.ROOM) { "Only an explicit room accepts a room geometry source" }
            require(n.coordinates.isEmpty() || n.kind in setOf(BuildingKind.AREA,BuildingKind.ROOM_GEOMETRY,BuildingKind.GARDEN,BuildingKind.PATH)) { "Only geometry sources and landscape nodes store coordinates" }
            require(n.layoutCells.isEmpty() || n.kind==BuildingKind.SEGMENT_LAYOUT) { "Only a segment arrangement stores cell assignments" }
            if(n.kind in LandscapeGeometry.kinds) LandscapeGeometry.validate(n)
            if(n.kind==BuildingKind.AREA && n.coordinates.isNotEmpty()) Area(n.coordinates)
            if(n.kind==BuildingKind.ROOM_GEOMETRY) {
                require(n.coordinates.size<=4096 && n.coordinates.distinct().size==n.coordinates.size) { "Room geometry coordinates must be unique" }
                if(n.coordinates.isNotEmpty()) { RoomFootprintEditor.validate(n.coordinates.toSet());RoomGeometry.fromCells(n.coordinates.toSet()) }
            }
            if(n.kind==BuildingKind.ROOM_GEOMETRY && n.geometry.isNotEmpty()) graph.areaOf(n)?.let { RoomVolume(it,n.height) }
            if(n.kind==BuildingKind.SEGMENT_LAYOUT) {
                if(n.geometry.isNotEmpty()) graph.areaOf(n)?.let { SegmentVolume(it,n.depth) }
                val resolved=graph.resolvedNode(n)
                require(n.layoutCells.size<=16384 && n.layoutCells.map { it.x to it.y }.distinct().size==n.layoutCells.size) { "Segment arrangement cells must be unique" }
                require(n.layoutCells.all { it.x in 0 until resolved.length && it.y in 0 until resolved.height && it.sourceIndex in 0..15 && it.turn in 0..3 }) { "Invalid segment arrangement cell" }
            }
            if(n.kind in SegmentBooleans.kinds) require(n.inputs.size in 2..if(n.kind==BuildingKind.SEGMENT_DIFFERENCE) 2 else 16) { "A segment operation needs 2–16 inputs (difference exactly two)" }
            if(n.kind in CopycatBooleans.kinds) require(n.inputs.size in 2..if(n.kind==BuildingKind.COPYCAT_DIFFERENCE) 2 else 16) { "A Copycat operation needs 2–16 inputs (difference exactly two)" }
            if(n.kind==BuildingKind.PATTERN) {
                require(n.inputs.size in 2..16) { "A pattern needs 2–16 ordered segment inputs" }
                require(n.patternAxis in listOf("horizontal","vertical","depth","transform")) { "Invalid wall pattern axis" }
            }
            require(n.mirror==null || n.kind==BuildingKind.COPYCAT_MIRROR) { "Copycat mirror settings belong to a Copycat mirror" }
            require(n.mirrorPositionsOnly==null || n.kind==BuildingKind.SEGMENT_MIRROR) { "Segment mirror mode belongs to a segment mirror" }
            require(n.verticalEdges.isNullOrEmpty() || n.kind==BuildingKind.ROOM && n.verticalEdges.size<=128 &&
                n.verticalEdges.keys.all { key -> RoomVerticalEdges.of(graph.resolvedNode(n).rectangles).any { it.id==key } }) { "Invalid vertical room edge assignment" }
            n.mirror?.let { require(it.plane in CopycatMirrorPlane.entries) { "Invalid Copycat mirror plane" } }
            require(n.surfaces.isNullOrEmpty() || n.kind==BuildingKind.COPYCAT && n.surfaces.size<=8 && n.surfaces.all { (group,material) ->
                group.isNotBlank() && group.length<=64 && material.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")) && material!="minecraft:air"
            }) { "Invalid Copycat material assignment" }
            if(n.kind in setOf(BuildingKind.COPYCAT_MIRROR,BuildingKind.COPYCAT_ROTATE)) require(n.inputs.size==1) { "A Copycat transform needs exactly one Copycat input" }
            if(n.kind in setOf(BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_ROTATE)) require(n.inputs.size==1) { "A segment transform needs exactly one segment input" }
            if(n.kind==BuildingKind.STOREY) {
                require(n.storeyMode in listOf("rooms","walls")) { "Invalid storey assembly mode" }
                if(n.storeyMode=="rooms") require(n.inputs.size in 1..16) { "A storey needs 1–16 room inputs" }
            }
            if(n.kind==BuildingKind.ROOM || n.kind==BuildingKind.STOREY && n.storeyMode=="walls") { RoomGeometry.cells(graph.resolvedNode(n).rectangles);require(n.walls.size<=64) }
            if(n.kind==BuildingKind.BUILDING) require(n.levels.size<=17 && n.levels.keys.all { it in -8..8 }) { "At most eight upper and eight lower storeys" }
            if(n.kind==BuildingKind.STAIRWELL) (n.stairwell ?: StairwellConfig()).validate()
            if(n.kind==BuildingKind.ELEVATOR_SHAFT) (n.elevatorShaft ?: ElevatorShaftConfig()).validate()
            if(n.kind==BuildingKind.SERVICE_SHAFT) (n.serviceShaft ?: ServiceShaftConfig()).validate()
            if(n.kind==BuildingKind.BUILDING) { require(n.accessInstances.size<=16);n.accessInstances.forEach(VerticalAccess::validateInstance) }
            require(n.roofLevel==null || n.kind==BuildingKind.ROOF_LEVEL) { "Roof-level settings belong to a roof level" }
            require(n.roofPeak.isNullOrEmpty() || n.kind==BuildingKind.ROOF && !n.assembly) { "A roof peak Copycat belongs to a simple roof; explicit roofs use their ridge template" }
            require(n.roofConfig==null || n.kind==BuildingKind.ROOF) { "Roof templates belong to a roof" }
            if(n.roofConfig!=null) {
                val refs=n.roofConfig.variants.values+n.roofConfig.families.flatMap { it.variants.values }
                refs.filter(String::isNotEmpty).forEach { require(nodes[it]?.kind in CopycatBooleans.sourceKinds) { "Roof templates require a connected Copycat source" } }
                if(n.roofConfig.wall.isNotEmpty()) require(nodes[n.roofConfig.wall]?.kind in (CopycatBooleans.sourceKinds+setOf(BuildingKind.SEGMENT,BuildingKind.PATTERN,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_ROTATE,BuildingKind.SEGMENT_UNION,BuildingKind.SEGMENT_INTERSECTION,BuildingKind.SEGMENT_DIFFERENCE,BuildingKind.SEGMENT_SYMMETRIC_DIFFERENCE))) { "Roof walls require a Copycat or wall segment source" }
            }
            if(n.kind==BuildingKind.ROOF_LEVEL) Roofs.validate(n.roofLevel ?: RoofLevelConfig())
            require(n.levelIds.isEmpty() || n.kind==BuildingKind.BUILDING && n.levelIds.keys.all { it in n.levels } && n.levelIds.values.distinct().size==n.levelIds.size) { "Invalid storey instance identities" }
            if(n.kind==BuildingKind.ROOF) require(n.roofType in listOf("flat","gable") && n.ridge in 0..1 && n.overhang in 0..2 && n.walls.keys.all { it in setOf("0","1","2","3") })
            require(n.finish.isEmpty() || n.kind==BuildingKind.FLOOR && n.finish in CARPETS && n.thickness>=2) { "Carpet floors need a structural layer below the carpet" }
            for(p in BuildingPorts.of(n,graph)) if(p.source.isNotEmpty()) {
                val source=nodes[p.source] ?: error("Missing source for ${n.name.ifEmpty { n.kind.name }}")
                require(source.kind in p.accepts) { "Incompatible ${source.kind} → ${n.kind} socket ${p.key}" }
            }
        }
        val done=mutableSetOf<String>();val active=mutableSetOf<String>()
        fun visit(id:String) { if(id in done) return;require(active.add(id)) { "Cycle in building graph" };BuildingPorts.of(nodes.getValue(id),graph).filter { it.source.isNotEmpty() }.forEach { visit(it.source) };active.remove(id);done+=id }
        nodes.keys.forEach { visit(it) }
    }
    val CARPETS=listOf("white","orange","magenta","light_blue","yellow","lime","pink","gray","light_gray","cyan","purple","blue","brown","green","red","black").map { "minecraft:${it}_carpet" }
    private fun override(plan:Plan,n:BuildingNode,palette:Map<String,String>):Plan {
        if(n.materialMode!=MaterialMode.GROUPED) return plan
        val material=palette[n.role] ?: error("Missing material role ${n.role}")
        val resolver=Resolver();plan.placements.forEach { p -> resolver.add(if(p.block.isEmpty()) p.copy(material=material,surfaces=p.groups.associate { it.id to material }) else p) };plan.clear.forEach { resolver.clear(it) };return resolver.plan(plan.warnings)
    }
    /** Shared shell blocks retain each wall's resolved finish and independently paid source recipes. */
    private fun wallUnion(contributions:List<Placement>):Placement {
        if(contributions.any { it.parts.any(ExactGeometry::usesExact) }) return ExactPlacementUnion.merge(contributions)
        val sources=contributions.distinctBy { it.visualKey() };val first=sources.first()
        if(sources.size==1) return first
        data class Finish(val group:MaterialGroup,val material:String)
        data class Region(val source:Int,val finish:Finish,val solid:Solid)
        var earlier=Solid()
        val regions=sources.flatMapIndexed { index,p ->
            val geometry=Geometry.resolve(p.parts,p.groups);val available=geometry.solid.minus(earlier);earlier=earlier.union(geometry.solid)
            p.groups.mapNotNull { group -> geometry.owned[group.id]?.intersect(available)?.takeIf { it.count()>0 }?.let {
                Region(index,Finish(group,p.surfaces[group.id] ?: p.material),it)
            } }
        }
        // Resolve each source's own priorities first, then give earlier rooms every shared voxel.
        // Clip before grouping: a later matching finish must not paint over an intervening room.
        val finishes=regions.map { it.finish }.distinct()
        val receipts=sources.map { p -> p.parts.map { it.recipeSource }.filter(String::isNotEmpty).distinct().withIndex().associate { it.value to it.index } }
        val groups=mutableListOf<MaterialGroup>();val surfaces=linkedMapOf<String,String>();val parts=mutableListOf<Part>()
        val reserved=finishes.map { it.group.id }.toSet();val collisions=finishes.groupingBy { it.group.id }.eachCount()
        for((index,finish) in finishes.withIndex()) {
            val matching=regions.filter { it.finish==finish }
            var groupId=finish.group.id
            if(collisions.getValue(groupId)>1) {
                groupId="shared_$index";var suffix=0
                while(groupId in reserved || groupId in surfaces) groupId="shared_${index}_${suffix++}"
            }
            // Only surviving groups count toward the per-block material-group limit.
            groups+=finish.group.copy(id=groupId,priority=groups.size);surfaces[groupId]=finish.material
            for(region in matching) for(part in sources[region.source].parts.filter { it.group==finish.group.id }) {
                val original=Solid.part(part);val clipped=original.intersect(region.solid)
                if(clipped.count()==0) continue
                parts+=part.copy(group=groupId,cutMask=if(clipped.contains(original)) part.cutMask else clipped.mask(),
                    recipeSource=if(part.recipeSource.isEmpty()) "" else "wall_${region.source}_${receipts[region.source].getValue(part.recipeSource)}")
            }
        }
        // Ownership has already been clipped, so these distinct priorities need no extra overlap rule.
        require(groups.size<=8) { "Shared walls exceed eight material groups in one block" }
        require(parts.size<=32) { "Shared walls exceed 32 source parts in one block" }
        return first.copy(parts=Geometry.normalized(parts,groups),groups=groups,surfaces=surfaces)
    }
    class Evaluation(val graph:BuildingGraph,val palette:Map<String,String>,forms:Map<String,Form>,val modules:Map<String,Module>,private val resultId:String=graph.resultNode().id) {
        val forms:MutableMap<String,Form> = forms.toMutableMap()
        private val nodes=graph.nodes.associateBy { it.id }
        private var activeAccess:AccessCompilation?=null
        private var activeStoreyId=""
        private var activeStoreyBase=0
        private var activeRoomOffset=P()
        private fun reservedPlate(p:P)=activeAccess?.shouldCarvePlate(activeStoreyId,p+activeRoomOffset+P(0,activeStoreyBase,0))==true
        private fun reservedBody(p:P)=activeAccess?.shouldSkipInteriorAir(activeStoreyId,p+activeRoomOffset+P(0,activeStoreyBase,0))==true

        private val segments=mutableMapOf<String,FixedSegment>();private val rooms=mutableMapOf<String,CompiledRoom>();private val storeys=mutableMapOf<String,CompiledStorey>()
        private val semanticRooms=mutableMapOf<String,SemanticRoom>()
        private val roofContacts=mutableListOf<RoofContact>()
        private val previews=mutableMapOf<String,Plan>()
        private val copycatResults=mutableMapOf<String,SegmentCell>()
        private data class RoofPreviewArea(val footprint:Set<P>,val sample:Boolean)
        private val roofPreviewAreas=mutableMapOf<String,RoofPreviewArea>()
        private val dormerCeilingCuts=mutableMapOf<P,ExactVolume>()
        init { structure(graph) }
        fun node(id:String,kind:BuildingKind?=null):BuildingNode { val n=nodes[id] ?: error("Connect the missing input");require(kind==null || n.kind==kind);return n }
        fun copycat(id:String)=node(id).also { require(it.kind in CopycatBooleans.sourceKinds) { "Connect a Copycat source" } }
        private fun copycats(n:BuildingNode):List<BuildingNode> {
            require(n.inputs.all { it.isNotEmpty() }) { "Connect every Copycat input" }
            return n.inputs.map { copycat(it) }
        }
        /** Local Copycat orientation precedes layout turns and mounting on walls or roofs. */
        private fun sourceCell(pos:P,source:BuildingNode,turn:Int=0):Cell {
            if(source.kind in CopycatBooleans.kinds) {
                val cell=copycatResult(source).cell
                return cell.copy(pos=pos,rotations=cell.rotations+if(turn==0) emptyList() else listOf(SegmentRotation(RotationAxis.Y,turn)))
            }
            if(source.kind==BuildingKind.COPYCAT_ROTATE) {
                require(source.inputs.single().isNotEmpty()) { "Connect the Copycat to rotate" }
                val cell=sourceCell(pos,copycat(source.inputs.single()))
                require(cell.block.isEmpty() || cell.empty) { "Copycat rotations require Copycat geometry" }
                val operations=cell.rotations+BuildingTransformValues.rotations(source,graph)+listOfNotNull(
                    SegmentRotation(RotationAxis.Y,turn).takeIf { turn!=0 })
                return cell.copy(role=if(source.materialMode==MaterialMode.GROUPED) source.role else cell.role,rotations=operations)
            }
            if(source.kind==BuildingKind.COPYCAT_MIRROR) {
                require(source.inputs.single().isNotEmpty()) { "Connect the Copycat to mirror" }
                val cell=sourceCell(pos,copycat(source.inputs.single()))
                require(cell.block.isEmpty() || cell.empty) { "Copycat mirrors require Copycat geometry; native blocks cannot be mirrored" }
                val operations=cell.rotations+BuildingTransformValues.mirrors(source,graph).map { SegmentRotation(mirror=it) }+
                    if(turn==0) emptyList() else listOf(SegmentRotation(RotationAxis.Y,turn))
                return cell.copy(role=if(source.materialMode==MaterialMode.GROUPED) source.role else cell.role,rotations=operations)
            }
            val rotations=buildList {
                if(source.quarterTurns!=0) add(SegmentRotation(source.rotationAxis,source.quarterTurns))
                if(turn!=0) add(SegmentRotation(RotationAxis.Y,turn))
            }
            // Keep legacy Y-only cells stable, including their serialized previews.
            return Cell(pos,form=source.form,role=source.role,block=source.block,empty=source.block=="minecraft:air" || source.block.isEmpty() && Geometry.validate(forms[source.form] ?: error("Missing form ${source.form}")).parts.isEmpty(),
                turn=if(source.quarterTurns==0 || source.rotationAxis==RotationAxis.Y) Math.floorMod(source.quarterTurns+turn,4) else 0,
                rotations=if(source.quarterTurns==0 || source.rotationAxis==RotationAxis.Y) emptyList() else rotations,surfaces=source.surfaces)
        }
        private fun copycatResult(source:BuildingNode):SegmentCell=copycatResults.getOrPut(source.id) {
            require(source.inputs.all(String::isNotEmpty)) { "Connect every Copycat operation input" }
            CopycatBooleans.evaluate(source,source.inputs.map { id ->
                val input=copycat(id);SegmentCell(sourceCell(P(),input),sourceOverride(input))
            },forms,palette)
        }
        private fun sourceOverride(source:BuildingNode):String = when {
            source.kind in CopycatBooleans.kinds -> copycatResult(source).overrideRole
            source.kind !in setOf(BuildingKind.COPYCAT_MIRROR,BuildingKind.COPYCAT_ROTATE) -> ""
            source.materialMode==MaterialMode.GROUPED -> source.role
            else -> sourceOverride(copycat(source.inputs.single()))
        }
        private fun sourceIsAir(source:BuildingNode)=sourceCell(P(),source).empty
        fun segment(id:String):FixedSegment=segments.getOrPut(id) { val n=node(id);BuildingSemantics.annotate(segmentRecipe(id),id,n.features,if(n.materialMode==MaterialMode.GROUPED) n.role else "") }
        private fun segmentRecipe(id:String):FixedSegment=run {
            val n=node(id);require(n.kind in setOf(BuildingKind.SEGMENT,BuildingKind.PATTERN,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_ROTATE,BuildingKind.SEGMENT_UNION,BuildingKind.SEGMENT_INTERSECTION,BuildingKind.SEGMENT_DIFFERENCE,BuildingKind.SEGMENT_SYMMETRIC_DIFFERENCE))
            if(n.kind in SegmentBooleans.kinds) {
                require(n.inputs.all(String::isNotEmpty)) { "Connect every segment operation input" }
                return@run SegmentBooleans.evaluate(n,n.inputs.map(::segment),forms,palette)
            }
            if(n.kind==BuildingKind.SEGMENT_ROTATE) {
                require(n.inputs.single().isNotEmpty()) { "Connect the segment to rotate" }
                return@run BuildingTransformValues.rotations(n,graph).fold(segment(n.inputs.single())) { value,step -> SegmentGeometry.rotated(value,step.axis,step.quarterTurns) }.let { value ->
                    if(n.materialMode==MaterialMode.GROUPED) value.copy(cells=value.cells.map { it.copy(overrideRole=n.role) }) else value
                }
            }
            if(n.kind==BuildingKind.SEGMENT_MIRROR) {
                require(n.inputs.single().isNotEmpty()) { "Connect the segment to mirror" }
                return@run BuildingTransformValues.mirrors(n,graph).fold(segment(n.inputs.single())) { value,step -> SegmentGeometry.mirror(value,RotationAxis.valueOf(step.plane.name),positionsOnly=n.mirrorPositionsOnly==true) }.let { value ->
                    if(n.materialMode==MaterialMode.GROUPED) value.copy(cells=value.cells.map { it.copy(overrideRole=n.role) }) else value
                }
            }
            if(n.kind==BuildingKind.SEGMENT) {
                if(n.assembly) {
                    require(n.layout.isNotEmpty()) { "Connect a segment arrangement source" }
                    val layout=graph.resolvedNode(node(n.layout,BuildingKind.SEGMENT_LAYOUT))
                    require(layout.geometry.isEmpty() || graph.areaOf(layout)!=null) { "Segment geometry needs occupied area coordinates" }
                    val copies=copycats(n)
                    val regions=graph.resolvedNode(n).effectiveRegionCopies()
                    require(copies.isNotEmpty() || regions.isNotEmpty()) { "Connect at least one Copycat input of the segment" }
                    require(layout.layoutCells.all { it.sourceIndex in copies.indices }) { "Segment arrangement refers to a missing Copycat input" }
                    val assignments=layout.layoutCells.associateBy { it.x to it.y }
                    val regionAt=SegmentRegions.of(layout.length,layout.height,layout.depth).flatMap { region -> region.cells.map { it to region.key } }.toMap()
                    val surface=graph.areaOf(layout)
                    val area=surface?.segmentCells()?.map { it.x to it.y }?.toSet()
                    val cells=buildList {
                        for(z in 0 until layout.depth) for(y in 0 until layout.height) for(x in 0 until layout.length) {
                            val pos=P(x,y,z);val assignment=assignments[x to y]
                            val source=regions[regionAt[pos]]?.let { copycat(it) }
                                ?: copies.getOrNull(assignment?.sourceIndex ?: ((y*layout.length+x)%copies.size.coerceAtLeast(1)))
                            if(area!=null && (x to y) !in area ||
                                layout.layoutCells.isNotEmpty() && assignment==null && regions[regionAt[pos]]==null || source==null)
                                add(SegmentCell(Cell(pos,block="minecraft:air",empty=true)))
                            else add(SegmentCell(sourceCell(pos,source,assignment?.turn ?: 0),if(n.materialMode==MaterialMode.GROUPED) n.role else sourceOverride(source)))
                        }
                    }
                    FixedSegment(layout.length,layout.height,cells,depth=layout.depth)
                }
                else if(n.inputs.isNotEmpty()) {
                    val copies=copycats(n)
                    FixedSegment(n.length,n.height,(0 until n.depth).flatMap { z -> (0 until n.length).flatMap { x -> (0 until n.height).map { y ->
                        val source=copies[(y*n.length+x)%copies.size]
                        SegmentCell(sourceCell(P(x,y,z),source),if(n.materialMode==MaterialMode.GROUPED) n.role else sourceOverride(source))
                    } } },depth=n.depth)
                }
                else if(n.block.isNotEmpty()) FixedSegment(n.length,n.height,if(n.block=="minecraft:air") emptyList() else (0 until n.depth).flatMap { z -> (0 until n.length).flatMap { x -> (0 until n.height).map { y -> SegmentCell(Cell(P(x,y,z),block=n.block,role=n.role)) } } },n.block=="minecraft:air",n.depth)
                else if(n.module.isEmpty()) FixedSegment(n.length,n.height,(0 until n.depth).flatMap { z -> (0 until n.length).flatMap { x -> (0 until n.height).map { y -> SegmentCell(Cell(P(x,y,z),form=n.form,role=n.role)) } } },depth=n.depth)
                else { val m=modules[n.module]?.validate() ?: error("Missing wall segment ${n.module}");FixedSegment(m.width,m.height,m.cells.map { SegmentCell(it) },depth=m.depth) }
            } else {
                require(n.inputs.all { it.isNotEmpty() }) { "Connect every wall pattern input" }
                val values=n.inputs.map { segment(it) };val vertical=n.patternAxis=="vertical"
                if(n.patternAxis=="transform" || n.patternAxis=="depth") {
                    var cursor=0
                    val items=values.mapIndexed { index,value ->
                        val transform=if(n.patternAxis=="transform") n.transforms.getOrNull(index) ?: SegmentTransform() else SegmentTransform(offset=P(0,0,cursor))
                        cursor+=value.depth
                        value to transform
                    }
                    return@run SegmentGeometry.union(items).let { value -> if(n.materialMode==MaterialMode.GROUPED) value.copy(cells=value.cells.map { it.copy(overrideRole=n.role) }) else value }
                }
                val depths=values.filter { !it.air }.map { it.depth }.distinct()
                require(depths.size<=1) { "Pattern segments must have the same depth" }
                val depth=depths.firstOrNull() ?: values.maxOf { it.depth }
                val across=values.filter { !it.air }.map { if(vertical) it.length else it.height }.distinct()
                require(across.size<=1) { if(vertical) "Pattern segments must have the same fixed length" else "Pattern segments must have the same fixed height" }
                val transverse=across.firstOrNull() ?: if(vertical) n.length else n.height
                val total=values.sumOf { if(vertical) it.height else it.length }
                val length=if(vertical) transverse else total;val height=if(vertical) total else transverse
                require(length<=128) { "Wall pattern exceeds 128 blocks" }
                require(height<=if(n.assembly) 128 else 16) { if(n.assembly) "Segment pattern exceeds 128 blocks" else "Wall pattern exceeds 16 blocks in height" }
                require(length.toLong()*height*depth<=262144) { "Segment pattern exceeds 262144 cells" }
                var cursor=0;val cells=mutableListOf<SegmentCell>();var metadata=SegmentMetadata()
                for((index,v) in values.withIndex()) {
                    val offset=if(vertical) P(0,cursor,0) else P(cursor,0,0)
                    if(v.air) for(z in 0 until depth) for(x in 0 until if(vertical) length else v.length) for(y in 0 until if(vertical) v.height else height)
                        cells+=SegmentCell(Cell(P(x,y,z)+offset,block="minecraft:air",empty=true))
                    else cells+=v.cells.map { it.copy(cell=it.cell.copy(pos=it.cell.pos+offset)) }
                    metadata+=v.metadata.moved(offset,"pattern:$index")
                    cursor+=if(vertical) v.height else v.length
                }
                FixedSegment(length,height,cells.map { if(n.materialMode==MaterialMode.GROUPED) it.copy(overrideRole=n.role) else it },depth=depth,metadata=metadata)
            }
        }
        private fun placement(pos:P,form:String,role:String,type:String,turn:Int=0,forced:String="",surfaces:Map<String,String> = emptyMap()):Placement {
            val f=Geometry.validate(forms[form] ?: error("Missing form $form"));val material=palette[forced.ifEmpty { role }] ?: error("Missing material role $role")
            return Placement(pos,Geometry.rotate(f.parts,turn),material,component=type,groups=f.groups,surfaces=f.groups.associate { it.id to if(forced.isNotEmpty()) material else surfaces[it.id] ?: palette[if(it.role=="surface") role else it.role].orEmpty().ifEmpty { error("Missing material role ${it.role}") } })
        }
        private fun segmentPlacement(pos:P,cell:Cell,type:String,forced:String="",worldTurn:Int=0):Placement {
            val p=placement(pos,cell.form,cell.role,type,forced=forced,surfaces=cell.surfaces.orEmpty())
            return p.copy(parts=SegmentGeometry.parts(cell,p.parts,worldTurn))
        }
        private fun sourcePlacement(pos:P,source:BuildingNode,type:String,turn:Int=0,forced:String=""):Placement {
            val cell=sourceCell(pos,source)
            return if(cell.block.isNotEmpty()) Placement(pos,block=cell.block,material="",component=type)
                else segmentPlacement(pos,cell,type,forced.ifEmpty { sourceOverride(source) },turn)
        }
        private fun validateCarpet(layer:BuildingNode,copies:List<BuildingNode>) {
            if(layer.kind!=BuildingKind.FLOOR || layer.finish.isEmpty()) return
            for(source in copies.ifEmpty { listOf(layer) }) {
                require(!sourceIsAir(source)) { "Carpet finish needs a floor with a complete top face" }
                val cell=sourceCell(P(),source)
                val support=Geometry.solid(SegmentGeometry.parts(cell,Geometry.validate(forms[cell.form] ?: error("Missing floor form ${cell.form}")).parts))
                require((0..15).all { x -> (0..15).all { z -> support.has(x,15,z) } }) { "Carpet finish needs a floor with a complete top face" }
            }
        }
        /** Used by complete enclosures and isolated floor/ceiling previews with identical source ordering. */
        private fun addPlateLayers(r:Resolver,layer:BuildingNode,copies:List<BuildingNode>,isFloor:Boolean,first:Int,
                                   footprint:Set<P>,shell:Set<P>,roomHeight:Int) {
            val sources=copies.ifEmpty { listOf(layer) }
            for(t in 0 until layer.thickness) {
                val y=first+t;val area=if(y in 0 until roomHeight) footprint else shell
                for((index,p) in area.sortedWith(compareBy({it.z},{it.x})).withIndex()) {
                    val target=p+P(0,y,0)
                    if(reservedPlate(target)) continue
                    if(isFloor && layer.finish.isNotEmpty() && t==layer.thickness-1) r.add(Placement(target,block=layer.finish,material="",component="floor"))
                    else {
                        val source=sources[(t*area.size+index)%sources.size]
                        if(sourceIsAir(source)) r.clear(target) else r.add(sourcePlacement(target,source,if(isFloor) "floor" else "ceiling",forced=if(layer.materialMode==MaterialMode.GROUPED) layer.role else ""))
                    }
                }
            }
        }
        private fun segmentUnionPlacement(pos:P,entries:List<SegmentCell>,type:String,worldTurn:Int=0,forced:String=""):Placement =
            wallUnion(entries.map { segmentPlacement(pos,it.cell,type,forced.ifEmpty { it.overrideRole },worldTurn) })
        /** Geometry owned by this node only; incomplete or unrelated building branches are never evaluated. */
        fun preview(id:String):Plan=previews.getOrPut(id) {
            val n=node(id)
            when(n.kind) {
                BuildingKind.COPYCAT,BuildingKind.COPYCAT_MIRROR,BuildingKind.COPYCAT_ROTATE,BuildingKind.COPYCAT_UNION,BuildingKind.COPYCAT_INTERSECTION,BuildingKind.COPYCAT_DIFFERENCE,BuildingKind.COPYCAT_SYMMETRIC_DIFFERENCE -> Resolver().apply { if(sourceIsAir(n)) clear(P()) else add(sourcePlacement(P(),n,"wall")) }.plan(emptyList())
                BuildingKind.FLOOR,BuildingKind.CEILING -> {
                    val copies=copycats(n);validateCarpet(n,copies)
                    val sample=(0..2).flatMap { x -> (0..2).map { z -> P(x,0,z) } }.toSet()
                    val r=Resolver();addPlateLayers(r,n,copies,n.kind==BuildingKind.FLOOR,0,sample,sample,n.thickness)
                    r.plan(emptyList())
                }
                BuildingKind.SEGMENT,BuildingKind.PATTERN,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_ROTATE,BuildingKind.SEGMENT_UNION,BuildingKind.SEGMENT_INTERSECTION,BuildingKind.SEGMENT_DIFFERENCE,BuildingKind.SEGMENT_SYMMETRIC_DIFFERENCE -> {
                    val value=segment(id);val r=Resolver()
                    if(value.air) for(x in 0 until value.length) for(y in 0 until value.height) for(z in 0 until value.depth) r.clear(P(x,y,z))
                    else for(entries in value.cells.groupBy { it.cell.pos }.values) {
                        val entry=entries.first();val cell=entry.cell
                        if(cell.empty || cell.block=="minecraft:air") r.clear(cell.pos)
                        else r.add(if(cell.block.isEmpty()) segmentUnionPlacement(cell.pos,entries,"wall")
                            else Placement(cell.pos,block=cell.block,material="",component="wall"),true)
                    }
                    r.plan(emptyList())
                }
                BuildingKind.GARDEN,BuildingKind.PATH -> landscape(n)
                BuildingKind.BUILDING -> if(n.id==resultId) building().plan else Evaluation(graph,palette,forms,modules,n.id).building().plan
                BuildingKind.ROOM_LAYOUT -> {
                    val resolved=RoomTopology.resolve(n,::segment);val r=Resolver()
                    for(item in resolved.instances) if(item.definition.policy!=WallPolicy.OPEN) for(entries in item.value.cells.groupBy { it.cell.pos }.values) {
                        val c=entries.first().cell
                        if(!c.empty && c.block!="minecraft:air") r.add(if(c.block.isEmpty()) segmentUnionPlacement(c.pos+item.position,entries,"wall")
                            else Placement(c.pos+item.position,block=c.block,material="",component="wall"),true)
                    }
                    r.plan(resolved.warnings)
                }
                BuildingKind.BAY_WINDOW -> {
                    val c=n.bayWindow ?: BayWindowConfig()
                    val host=BuildingNode("bay_sample",BuildingKind.ROOM,height=4,rectangles=listOf(Rect(width=c.width,length=1)),floor=c.floor,ceiling=c.ceiling,
                        walls=RoomGeometry.edges(listOf(Rect(width=c.width,length=1))).associate { it.id to c.wall })
                    // A one-cell repeated wall is sufficient for the sample enclosure.
                    val base=sampleBayRoom(host,c)
                    val sampleGraph=graph.copy(nodes=graph.nodes+host)
                    val semantic=RoomTopology.legacy(host,base)
                    val anchor=SpecialRooms.anchors(sampleGraph,host,semantic).first()
                    val bay=SpecialRooms.bay(sampleGraph,host,semantic,BayWindowInstance("sample",id,anchor.edge,0),::segment,
                        { p,entries,turn,type -> val cell=entries.first().cell;if(cell.block.isEmpty()) segmentUnionPlacement(p,entries,type,turn) else Placement(p,block=cell.block,material="",component=type) },::specialRoomPlates,{ source->sourcePlacement(P(),copycat(source),"wall") })
                    SpecialRooms.attach(base.plan,listOf(bay)).copy(warnings=listOf("Beispielvorschau · Erker an einem Raum mit Höhe 4"))
                }
                BuildingKind.DORMER -> dormerSample(n)
                BuildingKind.ROOM -> room(id).plan
                BuildingKind.STOREY -> storey(id).plan
                BuildingKind.ROOF -> {
                    requireDefinedRoof(n)
                    if(n.assembly) explicitRoof(n,preview=true) else roofPlan(n,roofPreviewArea(id).footprint,0)
                }
                BuildingKind.ROOF_LEVEL,BuildingKind.ROOF_LAYOUT -> {
                    val layout=if(n.kind==BuildingKind.ROOF_LAYOUT) n else graph.nodes.firstOrNull { it.kind==BuildingKind.ROOF_LAYOUT && id in it.inputs }
                        ?: BuildingNode("roof_preview_layout",BuildingKind.ROOF_LAYOUT,inputs=listOf(id))
                    val localLayout=if(n.kind==BuildingKind.ROOF_LEVEL) layout.copy(inputs=listOf(id)) else layout
                    val roof=graph.nodes.firstOrNull { it.kind==BuildingKind.ROOF && it.layout==layout.id }
                        ?: BuildingNode("roof_preview",BuildingKind.ROOF,assembly=true,layout=layout.id,role="roof")
                    val context=if(graph.nodes.any { it.id==layout.id }) graph.replace(localLayout) else graph.copy(nodes=graph.nodes+localLayout)
                    explicitRoof(roof,context,preview=true)
                }
                BuildingKind.STAIRWELL,BuildingKind.ELEVATOR_SHAFT,BuildingKind.SERVICE_SHAFT -> {
                    val kind=when(n.kind) { BuildingKind.STAIRWELL->VerticalAccessKind.STAIRWELL;BuildingKind.ELEVATOR_SHAFT->VerticalAccessKind.ELEVATOR_SHAFT;else->VerticalAccessKind.SERVICE_SHAFT }
                    VerticalAccess.preview(AccessDefinition(n.id,kind,n.stairwell ?: StairwellConfig(),n.elevatorShaft ?: ElevatorShaftConfig(),n.serviceShaft ?: ServiceShaftConfig()),{ source,type->sourcePlacement(P(),copycat(source),type) },palette)
                }
                else -> error("This node has no local geometry preview")
            }
        }
        /** Browser plate previews retain the connected footprint without evaluating other room branches. */
        fun contextualPlatePreview(id:String):Plan {
            val layer=node(id);require(layer.kind==BuildingKind.FLOOR || layer.kind==BuildingKind.CEILING)
            val floor=layer.kind==BuildingKind.FLOOR
            val context=graph.nodes.firstOrNull { (it.kind==BuildingKind.ROOM || it.kind==BuildingKind.STOREY && it.storeyMode=="walls") && (if(floor) it.floor==id else it.ceiling==id) }
                ?: error("Connect this plate to a room or wall-based storey to provide its footprint")
            val copies=copycats(layer);validateCarpet(layer,copies)
            val start=if(floor) (if(layer.occupied) 1 else 0)-layer.thickness else context.height-(if(layer.occupied) 1 else 0)
            val result=Resolver();addPlateLayers(result,layer,copies,floor,start,RoomGeometry.cells(graph.resolvedNode(context).rectangles),RoomGeometry.shell(graph.resolvedNode(context).rectangles),context.height)
            return result.plan(listOf("Grundriss: "+context.name.ifEmpty { context.id }))
        }
        fun validateDormerPlacement(id:String,full:Boolean=false) {
            node(id,BuildingKind.ROOF_LEVEL)
            val parentLayout=graph.nodes.firstOrNull { it.kind==BuildingKind.ROOF_LAYOUT && id in it.inputs }
            val layout=(parentLayout ?: BuildingNode(kind=BuildingKind.ROOF_LAYOUT,inputs=listOf(id))).copy(id=java.util.UUID.randomUUID().toString())
            val parent=graph.nodes.firstOrNull { it.kind==BuildingKind.ROOF && it.layout==parentLayout?.id }
                ?: BuildingNode("dormer_validation_roof",BuildingKind.ROOF,assembly=true,role="roof")
            val context=graph.copy(nodes=graph.nodes+layout)
            explicitRoof(parent.copy(layout=layout.id),context,preview=true,validateDormersOnly=!full)
        }
        private fun specialRoomPlates(n:BuildingNode):CompiledRoom {
            val interior=RoomGeometry.cells(n.rectangles);val shell=RoomGeometry.shell(n.rectangles)
            val topology=ResolvedRoomLayout(emptyList(),emptyList(),emptyList(),shell,interior,shell-interior,shell,
                shell.maxOf { it.x }-shell.minOf { it.x }+1,shell.maxOf { it.z }-shell.minOf { it.z }+1,n.height,true,emptyList())
            val result=semanticPlates(n,topology,n.height)
            return CompiledRoom(result.plan,interior,shell,result.minimum,result.maximum,result.bottomThickness,result.topThickness,result.usableHeight)
        }
        private fun sampleBayRoom(host:BuildingNode,c:BayWindowConfig):CompiledRoom {
            require(c.wall.isNotEmpty()) { "Erkerwand anschließen" }
            val base=specialRoomPlates(host.copy(walls=emptyMap()));val r=Resolver()
            base.plan.placements.filter { it.component in setOf("floor","ceiling") }.forEach { r.add(it) }
            val value=segment(c.wall)
            require(!value.air) { "Erkerwand benötigt feste Geometrie" }
            for(edge in RoomGeometry.edges(host.rectangles)) for(i in edge.cells.indices) for(y in 0 until host.height) for(z in 0 until value.depth) {
                val entries=value.cells.filter { it.cell.pos==P(i%value.length,y%value.height,z) }
                if(entries.isEmpty() || entries.first().cell.empty || entries.first().cell.block=="minecraft:air") continue
                val p=edge.cells[i]+P(0,y,-(value.depth-1-z)).turn(edge.direction)
                val cell=entries.first().cell
                r.add(if(cell.block.isEmpty()) segmentUnionPlacement(p,entries,"wall",edge.direction) else Placement(p,block=cell.block,material="",component="wall"),true)
            }
            base.plan.clear.forEach(r::clear)
            return base.copy(plan=r.plan(emptyList()))
        }
        private fun dormerSample(n:BuildingNode):Plan {
            val c=n.dormer ?: DormerConfig();val length=2*(c.depth+3)
            val minimumSlope=(c.wallHeight+(c.width+2)*0.5*c.slope.p/c.slope.q-0.125)/(c.depth+2)
            val sampleSlope=RoofSlope.presets.firstOrNull { it.p.toDouble()/it.q>=minimumSlope } ?: RoofSlope(4,1)
            val level=BuildingNode("dormer_sample_level",BuildingKind.ROOF_LEVEL,roofLevel=RoofLevelConfig(form=RoofForm.GABLE,axis=1,slope=sampleSlope,overhang16=0),
                dormers=listOf(DormerInstance("sample",n.id)))
            val layout=BuildingNode("dormer_sample_layout",BuildingKind.ROOF_LAYOUT,inputs=listOf(level.id))
            val roof=BuildingNode("dormer_sample_roof",BuildingKind.ROOF,assembly=true,layout=layout.id,role="roof")
            val context=graph.copy(nodes=graph.nodes+listOf(level,layout,roof))
            val support=RoofSupport("sample",0,0,buildSet { for(x in 0 until c.width+2) for(z in 0 until length) add(P(x,0,z)) })
            return Roofs.compile(context,roof,listOf(support),palette,{ id->sourcePlacement(P(),copycat(id),"roof") },wallSegment=::segment,
                wallPlacement={ p,entries,turn -> val cell=entries.first().cell;if(cell.block.isEmpty()) segmentUnionPlacement(p,entries,"wall",turn) else Placement(p,block=cell.block,material="",component="wall") })
                .copy(warnings=listOf("Beispielvorschau · Gaube auf einer Dachschräge"))
        }
        /** True when a legacy roof is shown on a labelled 5 × 5 example instead of a connected top storey. */
        fun roofPreviewUsesSample(id:String):Boolean = !node(id,BuildingKind.ROOF).assembly && roofPreviewArea(id).sample
        private fun roofPreviewArea(id:String):RoofPreviewArea=roofPreviewAreas.getOrPut(id) {
            val result=nodes.getValue(resultId)
            val top=if(result.roof==id) result.levels.filterValues(String::isNotEmpty).entries.maxByOrNull { it.key }?.value else null
            val connected=top?.let { runCatching { storeyPreviewFootprint(it) }.getOrNull() }?.takeIf { it.isNotEmpty() }
            if(connected!=null) RoofPreviewArea(connected,false)
            else RoofPreviewArea(RoomGeometry.shell(listOf(Rect(width=3,length=3))),true)
        }
        /** Roof shape depends on room outlines and placement, not on their wall or plate recipes. */
        private fun storeyPreviewFootprint(id:String):Set<P> {
            val storey=node(id,BuildingKind.STOREY)
            if(storey.storeyMode=="walls") return RoomGeometry.shell(storey.rectangles)
            require(storey.inputs.isNotEmpty() && storey.inputs.all(String::isNotEmpty)) { "Connect every room input of the storey" }
            return buildSet {
                storey.inputs.forEachIndexed { index,source ->
                    val room=node(source,BuildingKind.ROOM)
                    if(room.roomConfig?.mode==RoomMode.SEGMENTS) {
                        addAll(RoomTopology.resolve(node(room.layout,BuildingKind.ROOM_LAYOUT),::segment).shell.map { it+(storey.offsets.getOrNull(index) ?: P()) })
                        return@forEachIndexed
                    }
                    if(room.assembly) {
                        require(room.geometry.isNotEmpty()) { "Connect a room geometry source" }
                        require(graph.resolvedNode(node(room.geometry,BuildingKind.ROOM_GEOMETRY)).coordinates.isNotEmpty()) { "Room geometry needs occupied coordinates" }
                    }
                    val offset=storey.offsets.getOrNull(index) ?: P()
                    addAll(RoomWallJoints.shell(graph.resolvedNode(room)).map { it+offset })
                }
            }
        }
        private fun baseRoom(id:String):CompiledRoom=rooms.getOrPut(id) {
            val n=node(id,BuildingKind.ROOM)
            if(n.roomConfig?.mode==RoomMode.SEGMENTS) semanticRoom(id).compiled else enclosure(n)
        }
        private fun bayGeometry(host:BuildingNode):List<BayGeometry> = host.bayWindowInstances.map { instance ->
            SpecialRooms.bay(graph,host,semanticRoom(host.id),instance,::segment,
                { p,entries,turn,type -> val c=entries.first().cell;if(c.block.isEmpty()) segmentUnionPlacement(p,entries,type,turn) else Placement(p,block=c.block,material="",component=type) },::specialRoomPlates,{ id->sourcePlacement(P(),copycat(id),"wall") }).let { it.copy(plan=override(it.plan,host,palette)) }
        }
        fun room(id:String):CompiledRoom {
            val base=baseRoom(id);val bays=bayGeometry(node(id))
            return if(bays.isEmpty()) base else base.copy(plan=SpecialRooms.attach(base.plan,bays),footprint=base.footprint+bays.flatMap { it.interior },shell=base.shell+bays.flatMap { it.shell })
        }
        fun semanticRoom(id:String):SemanticRoom=semanticRooms.getOrPut(id) {
            val n=node(id,BuildingKind.ROOM)
            if(n.roomConfig?.mode!=RoomMode.SEGMENTS) RoomTopology.legacy(n,enclosure(n))
            else RoomTopology.compile(n,node(n.layout,BuildingKind.ROOM_LAYOUT),::segment,
                { p,entries,turn,type -> val c=entries.first().cell;if(c.block.isEmpty()) segmentUnionPlacement(p,entries,type,turn) else Placement(p,block=c.block,material="",component=type) },
                { topology,height -> semanticPlates(n,topology,height) },::reservedBody).let { result -> result.copy(compiled=result.compiled.copy(plan=override(result.compiled.plan,n,palette))) }
        }
        private fun semanticPlates(n:BuildingNode,topology:ResolvedRoomLayout,height:Int):RoomPlateResult {
            val r=Resolver();var minimum=0;var maximum=height;var bottom=0;var top=0;var occupiedLayers=0
            val shell=topology.shell;val footprint=topology.interior
            fun plate(id:String,isFloor:Boolean) {
                if(id.isEmpty()) return
                val layer=node(id);val isSegment=layer.kind in setOf(BuildingKind.SEGMENT,BuildingKind.PATTERN,BuildingKind.SEGMENT_ROTATE,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_UNION,BuildingKind.SEGMENT_INTERSECTION,BuildingKind.SEGMENT_DIFFERENCE,BuildingKind.SEGMENT_SYMMETRIC_DIFFERENCE)
                val value=if(isSegment) segment(id) else null
                val thickness=value?.depth ?: layer.thickness
                val occupied=!isSegment && layer.occupied
                if(occupied) occupiedLayers++
                val start=if(isFloor) (if(occupied) 1 else 0)-thickness else height-(if(occupied) 1 else 0)
                if(isFloor) { minimum=start;bottom=(thickness-if(occupied) 1 else 0).coerceAtLeast(0) }
                else { maximum=start+thickness;top=(thickness-if(occupied) 1 else 0).coerceAtLeast(0) }
                if(value==null) {
                    require(layer.kind==(if(isFloor) BuildingKind.FLOOR else BuildingKind.CEILING)) { "Connect a floor or ceiling source" }
                    val sources=copycats(layer);if(isFloor) validateCarpet(layer,sources)
                    addPlateLayers(r,layer,sources,isFloor,start,footprint,shell,height)
                } else {
                    if(shell.isEmpty()) return
                    val minX=shell.minOf { it.x };val minZ=shell.minOf { it.z }
                    val width=shell.maxOf { it.x }-minX+1;val depth=shell.maxOf { it.z }-minZ+1
                    require(value.air || value.length>=width && value.height>=depth) { "Horizontal segment needs at least $width × $depth" }
                    if(value.air) shell.forEach { p->for(y in 0 until value.depth) if(!reservedPlate(p+P(0,start+y,0))) r.clear(p+P(0,start+y,0)) }
                    else for(entries in value.cells.groupBy { it.cell.pos }.values) {
                        val c=entries.first().cell
                        val horizontal=P(minX+c.pos.x-(value.length-width)/2,0,minZ+(if(isFloor) value.height-1-c.pos.y else c.pos.y)-(value.height-depth)/2)
                        if(horizontal !in shell) continue
                        val target=horizontal+P(0,start+if(isFloor) c.pos.z else value.depth-1-c.pos.z,0)
                        if(reservedPlate(target)) continue
                        if(c.empty || c.block=="minecraft:air") r.clear(target)
                        else r.add(if(c.block.isNotEmpty()) Placement(target,block=c.block,material="",component=if(isFloor) "floor" else "ceiling")
                            else segmentUnionPlacement(target,entries,if(isFloor) "floor" else "ceiling").let { p -> p.copy(parts=p.parts.map { PartRotations.rotate(it,RotationAxis.X,if(isFloor) 3 else 1) ?: error("Unsupported horizontal segment") }) },true)
                    }
                }
            }
            plate(n.floor,true);plate(n.ceiling,false)
            require(height>occupiedLayers) { "Room has no buildable height after floor/ceiling layers" }
            return RoomPlateResult(r.plan(emptyList()),minimum,maximum,bottom,top,height-occupiedLayers)
        }
        private data class JoinedRoomWalls(val plan:Plan,val columns:Set<P>)
        private fun joinedRoomWalls(n:BuildingNode):JoinedRoomWalls {
            val legacy=RoomGeometry.edges(n.rectangles)
            val runs=RoomWallJoints.runs(n)
            val segments=runs.associateWith { segment(n.walls[it.id].orEmpty().ifEmpty { "empty_wall" }) }
            val columns=linkedSetOf<P>();val contributions=linkedMapOf<P,MutableList<Placement>>();val clears=linkedSetOf<P>()
            for((run,value) in segments) {
                require(value.air || value.length==run.length && value.height==n.height) {
                    "Wall ${run.id}: needs ${run.length} × ${n.height}, got ${value.length} × ${value.height}"
                }
                val normal=RoomGeometry.directions[run.direction]
                for(base in run.cells) for(z in 0 until value.depth) {
                    val p=base-P(normal.x*z,0,normal.z*z);columns+=p
                    for(y in 0 until n.height) clears+=p+P(0,y,0)
                }
                if(value.air) continue
                for(entries in value.cells.groupBy { it.cell.pos }.values) {
                    val c=entries.first().cell
                    val target=run.cells[c.pos.x]-P(normal.x*c.pos.z,-c.pos.y,normal.z*c.pos.z)
                    if(c.empty || c.block=="minecraft:air") continue
                    val placed=if(c.block.isEmpty()) segmentUnionPlacement(target,entries,"wall",run.direction)
                        else Placement(target,block=c.block,material="",component="wall")
                    contributions.getOrPut(target) { mutableListOf() }+=placed
                }
            }
            val directionalDepths=legacy.flatMap { run -> run.cells.map { (it to run.direction) to segments.getValue(runs.single { r -> r.id==run.id }).depth } }.toMap()
            for(edge in RoomVerticalEdges.of(n.rectangles)) {
                val source=n.verticalEdges.orEmpty()[edge.id]?.takeIf(String::isNotEmpty)
                val width=directionalDepths[edge.sideX to RoomGeometry.directions.indexOf(edge.normalX)] ?: 1
                val depth=directionalDepths[edge.sideZ to RoomGeometry.directions.indexOf(edge.normalZ)] ?: 1
                val origin=edge.pos-edge.normalX-edge.normalZ
                if(source!=null) {
                    val value=segment(source)
                    require(value.air || value.length==width && value.height==n.height && value.depth==depth) {
                        "Vertical edge ${edge.id}: needs $width × ${n.height} × $depth, got ${value.length} × ${value.height} × ${value.depth}"
                    }
                    // Edge inputs occupy the shared end rows; they never add an exterior pillar.
                    for(x in 0 until width) for(z in 0 until depth) for(y in 0 until n.height) {
                        val p=origin+P(x,y,z).turn(edge.turn);columns+=p.copy(y=0);clears+=p
                        if(value.air) contributions.remove(p)
                    }
                    if(!value.air) for(entries in value.cells.groupBy { it.cell.pos }.values) {
                        val c=entries.first().cell;val p=origin+c.pos.turn(edge.turn)
                        if(c.empty || c.block=="minecraft:air") contributions.remove(p)
                        else {
                            val placed=if(c.block.isEmpty()) segmentUnionPlacement(p,entries,"trim",edge.turn)
                                else Placement(p,block=c.block,material="",component="trim")
                            contributions.getOrPut(p) { mutableListOf() }.add(0,placed)
                        }
                    }
                } else if(n.corner.isNotEmpty()) {
                    val copycat=copycat(n.corner)
                    for(x in 0 until width) for(z in 0 until depth) for(y in 0 until n.height) {
                        val p=origin+P(x,y,z).turn(edge.turn)
                        if(p !in contributions) continue
                        if(sourceIsAir(copycat)) contributions.remove(p)
                        else contributions[p]=mutableListOf(sourcePlacement(p,copycat,"trim",edge.turn))
                    }
                }
            }
            val resolver=Resolver()
            for((pos,parts) in contributions) {
                val blocks=parts.filter { it.block.isNotEmpty() }
                // Vanilla blocks cannot carry independently clipped Copycat pieces.
                require(blocks.isEmpty() || parts.all { it.block==blocks.first().block }) { "Joined wall blocks conflict at $pos" }
                resolver.add(if(blocks.isEmpty()) wallUnion(parts) else blocks.first())
            }
            clears.filterNot(contributions::containsKey).forEach { resolver.clear(it) }
            return JoinedRoomWalls(resolver.plan(emptyList()),columns)
        }
        private fun enclosure(original:BuildingNode):CompiledRoom {
            if(original.assembly) {
                require(original.geometry.isNotEmpty()) { "Connect a room geometry source" }
                require(graph.resolvedNode(node(original.geometry,BuildingKind.ROOM_GEOMETRY)).coordinates.isNotEmpty()) { "Room geometry needs occupied coordinates" }
            }
            val n=graph.resolvedNode(original)
            val floor=node(n.floor);val ceiling=node(n.ceiling)
            val floorSegment=floor.kind in setOf(BuildingKind.SEGMENT,BuildingKind.PATTERN,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_ROTATE,BuildingKind.SEGMENT_UNION,BuildingKind.SEGMENT_INTERSECTION,BuildingKind.SEGMENT_DIFFERENCE,BuildingKind.SEGMENT_SYMMETRIC_DIFFERENCE)
            val ceilingSegment=ceiling.kind in setOf(BuildingKind.SEGMENT,BuildingKind.PATTERN,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_ROTATE,BuildingKind.SEGMENT_UNION,BuildingKind.SEGMENT_INTERSECTION,BuildingKind.SEGMENT_DIFFERENCE,BuildingKind.SEGMENT_SYMMETRIC_DIFFERENCE)
            require(floor.kind==BuildingKind.FLOOR || n.assembly && floorSegment) { "Connect a floor definition or horizontal segment" }
            require(ceiling.kind==BuildingKind.CEILING || n.assembly && ceilingSegment) { "Connect a ceiling definition or horizontal segment" }
            val floorCopies=if(floorSegment) emptyList() else copycats(floor);val ceilingCopies=if(ceilingSegment) emptyList() else copycats(ceiling)
            val floorOccupied=!floorSegment && floor.occupied;val ceilingOccupied=!ceilingSegment && ceiling.occupied
            val floorThickness=if(floorSegment) segment(floor.id).depth else floor.thickness;val ceilingThickness=if(ceilingSegment) segment(ceiling.id).depth else ceiling.thickness
            val usable=n.height-(if(floorOccupied) 1 else 0)-(if(ceilingOccupied) 1 else 0);require(usable>0) { "Room has no buildable height after floor/ceiling layers" }
            if(!floorSegment) validateCarpet(floor,floorCopies)
            val joined=n.usesJoinedWallEnds
            val footprint=RoomGeometry.cells(n.rectangles).toMutableSet();val shell=(if(joined) footprint else RoomGeometry.shell(n.rectangles)).toMutableSet();val r=Resolver();val wallPlacements=linkedMapOf<P,Placement>();val wallDepths=linkedMapOf<P,Int>()
            if(joined) {
                val walls=joinedRoomWalls(n)
                walls.plan.placements.forEach { r.add(it) };walls.plan.clear.forEach { r.clear(it) }
                require(walls.columns.all { it in footprint }) { "Wall depth or vertical edge exceeds the joined room outline" }
                shell+=walls.columns;footprint.removeAll(walls.columns)
            } else {
                val wallSegments=RoomGeometry.edges(n.rectangles).associateWith { edge -> segment(n.walls[edge.id].orEmpty().ifEmpty { "empty_wall" }) }
                val directionalDepths=wallSegments.flatMap { (edge,s) -> edge.cells.map { (it to edge.direction) to s.depth } }.toMap()
                val edgeColumns=linkedMapOf<P,String>()
                for(edge in RoomVerticalEdges.of(n.rectangles)) {
                    val source=n.verticalEdges.orEmpty()[edge.id]?.takeIf(String::isNotEmpty) ?: continue
                    val value=segment(source)
                    val width=directionalDepths[edge.sideX to RoomGeometry.directions.indexOf(edge.normalX)] ?: 1
                    val depth=directionalDepths[edge.sideZ to RoomGeometry.directions.indexOf(edge.normalZ)] ?: 1
                    require(value.air || value.length==width && value.height==n.height && value.depth==depth) {
                        "Vertical edge ${edge.id}: needs $width × ${n.height} × $depth, got ${value.length} × ${value.height} × ${value.depth}"
                    }
                    val origin=edge.pos+P(edge.normalX.x*(width-1)+edge.normalZ.x*(depth-1),0,edge.normalX.z*(width-1)+edge.normalZ.z*(depth-1))
                    for(x in 0 until width) for(z in 0 until depth) {
                        val column=origin+P(x,0,z).turn(edge.turn)
                        require(edgeColumns.put(column,edge.id)==null) { "Vertical edge segments overlap at $column" }
                        shell+=column
                    }
                    if(value.air) edgeColumns.filterValues { it==edge.id }.keys.forEach { column -> for(y in 0 until n.height) r.clear(column+P(0,y,0)) }
                    else for(entries in value.cells.groupBy { it.cell.pos }.values) {
                        val c=entries.first().cell;val target=origin+c.pos.turn(edge.turn)
                        if(c.empty || c.block=="minecraft:air") r.clear(target)
                        else r.add(if(c.block.isEmpty()) segmentUnionPlacement(target,entries,"trim",edge.turn)
                            else Placement(target,block=c.block,material="",component="trim"),true)
                    }
                }
                for(edge in RoomGeometry.edges(n.rectangles)) {
                    val s=wallSegments.getValue(edge)
                    require(s.air || s.length==edge.length && s.height==n.height) { "Wall ${edge.id}: needs ${edge.length} × ${n.height}, got ${s.length} × ${s.height}" }
                    edge.cells.forEach { wallDepths[it]=s.depth }
                    val normal=RoomGeometry.directions[edge.direction]
                    edge.cells.forEach { p -> for(z in 0 until s.depth) shell+=p+P(normal.x*z,0,normal.z*z) }
                    if(s.air) for(p in edge.cells) for(y in 0 until n.height) for(z in 0 until s.depth) {
                        val target=p+P(normal.x*z,y,normal.z*z)
                        if(target.copy(y=0) !in edgeColumns) r.clear(target)
                    }
                    else for(entries in s.cells.groupBy { it.cell.pos }.values) {
                        val entry=entries.first();val c=entry.cell;val normal=RoomGeometry.directions[edge.direction]
                        // Local +Z faces into the room, exactly like the Copycats after the mounting turn.
                        val depth=s.depth-1-c.pos.z
                        val p=edge.cells[c.pos.x]+P(normal.x*depth,c.pos.y,normal.z*depth)
                        if(p.copy(y=0) in edgeColumns) continue
                        if(c.empty || c.block=="minecraft:air") r.clear(p)
                        else {
                            val wall=if(c.block.isEmpty()) segmentUnionPlacement(p,entries,"wall",edge.direction) else Placement(p,block=c.block,material="",component="wall")
                            r.add(wall,true);wallPlacements[p]=wall
                        }
                    }
                }
                // Close convex joints only where an incident wall actually contributes geometry.
                val cornerSource=n.corner.takeIf(String::isNotEmpty)?.let { copycat(it) }
                for((corner,adjacent) in RoomGeometry.corners(n.rectangles)) {
                    val outwardFirst=P(corner.x-adjacent.first.x,0,corner.z-adjacent.first.z)
                    val outwardSecond=P(corner.x-adjacent.second.x,0,corner.z-adjacent.second.z)
                    val firstDepth=wallDepths[adjacent.first] ?: 1;val secondDepth=wallDepths[adjacent.second] ?: 1
                    for(a in 0 until firstDepth) for(b in 0 until secondDepth) {
                        val column=corner+P(outwardFirst.x*b+outwardSecond.x*a,0,outwardFirst.z*b+outwardSecond.z*a)
                        shell+=column
                        if(column in edgeColumns) continue
                        for(y in 0 until n.height) {
                            val first=wallPlacements[adjacent.first+P(outwardSecond.x*a,y,outwardSecond.z*a)]
                            val second=wallPlacements[adjacent.second+P(outwardFirst.x*b,y,outwardFirst.z*b)]
                            val template=first ?: second;val target=column+P(0,y,0)
                            if(template==null || cornerSource?.let(::sourceIsAir)==true) r.clear(target)
                            else if(cornerSource!=null) {
                                val dx=adjacent.first.x+adjacent.second.x-2*corner.x;val dz=adjacent.first.z+adjacent.second.z-2*corner.z
                                val turn=when { dx>0 && dz>0->0;dx<0 && dz>0->1;dx<0 && dz<0->2;else->3 }
                                r.add(sourcePlacement(target,cornerSource,"trim",turn),true)
                            } else {
                                val faces=listOfNotNull(first,second).map { it.copy(pos=target) }
                                r.add(if(faces.all { it.block.isEmpty() }) wallUnion(faces) else template.copy(pos=target),true)
                            }
                        }
                    }
                }
            }
            val minimum=(if(floorOccupied) 1 else 0)-floorThickness
            val ceilingStart=n.height-(if(ceilingOccupied) 1 else 0);val maximum=ceilingStart+ceilingThickness
            fun layer(layer:BuildingNode,isFloor:Boolean) {
                if(if(isFloor) floorSegment else ceilingSegment) {
                    val value=segment(layer.id)
                    val minX=shell.minOf { it.x };val minZ=shell.minOf { it.z }
                    val width=shell.maxOf { it.x }-minX+1;val depth=shell.maxOf { it.z }-minZ+1
                    require(value.air || if(joined) value.length>=width && value.height>=depth else value.length==width && value.height==depth) { "Horizontal ${if(isFloor) "floor" else "ceiling"} segment needs $width × $depth, got ${value.length} × ${value.height}" }
                    val first=if(isFloor) minimum else ceilingStart
                    if(value.air) shell.forEach { p -> for(z in 0 until value.depth) { val target=p+P(0,first+z,0);if(!reservedBody(target)) r.clear(target) } }
                    else for(entries in value.cells.groupBy { it.cell.pos }.values) {
                        val entry=entries.first();val c=entry.cell
                        // Oversized horizontal sources are centred and masked to the assembled shell.
                        // Existing floors therefore follow the smaller outline after end rows are joined.
                        val cropX=if(joined) (value.length-width)/2 else 0;val cropZ=if(joined) (value.height-depth)/2 else 0
                        val horizontal=P(minX+c.pos.x-cropX,0,minZ+(if(isFloor) value.height-1-c.pos.y else c.pos.y)-cropZ)
                        if(horizontal !in shell) continue
                        val target=horizontal+P(0,first+if(isFloor) c.pos.z else value.depth-1-c.pos.z,0)
                        if(reservedPlate(target)) continue
                        if(c.empty || c.block=="minecraft:air") r.clear(target)
                        else r.add(if(c.block.isEmpty()) {
                            val placed=segmentUnionPlacement(target,entries,if(isFloor) "floor" else "ceiling")
                            // The canonical north-wall panel faces the room at local +Z.
                            // Mount it at the floor's upper face and the ceiling's lower face.
                            placed.copy(parts=placed.parts.map { part -> PartRotations.rotate(part,RotationAxis.X,if(isFloor) 3 else 1)
                                ?: error("Copycat ${part.source} cannot be mounted as a horizontal segment") })
                        } else Placement(target,block=c.block,material="",component=if(isFloor) "floor" else "ceiling"))
                    }
                    return
                }
                addPlateLayers(r,layer,if(isFloor) floorCopies else ceilingCopies,isFloor,if(isFloor) minimum else ceilingStart,footprint,shell,n.height)
            }
            layer(floor,true);layer(ceiling,false)
            for(p in footprint) for(y in (if(floorOccupied) 1 else 0) until n.height-(if(ceilingOccupied) 1 else 0)) if(!reservedBody(p+P(0,y,0))) r.clear(p+P(0,y,0))
            return CompiledRoom(override(r.plan(emptyList()),n,palette),footprint,shell,minimum,maximum,(floorThickness-if(floorOccupied || !floorSegment && floor.finish.isNotEmpty()) 1 else 0).coerceAtLeast(0),(ceilingThickness-if(ceilingOccupied) 1 else 0).coerceAtLeast(0),usable)
        }
        private fun baseStorey(id:String):CompiledStorey {
            val n=node(id,BuildingKind.STOREY)
            if(n.storeyMode=="walls") {
                val enclosed=enclosure(n)
                return CompiledStorey(enclosed.plan,enclosed.shell,enclosed.minimum,enclosed.maximum,enclosed.bottomThickness,enclosed.topThickness)
            }
            require(n.inputs.isNotEmpty() && n.inputs.all { it.isNotEmpty() }) { "Connect every room input of the storey" }
            if(n.facade!=null || n.inputs.any { node(it).roomConfig?.mode==RoomMode.SEGMENTS }) {
                val instances=n.inputs.mapIndexed { index,source ->
                    activeRoomOffset=n.offsets.getOrNull(index) ?: P()
                    if(activeAccess!=null) { semanticRooms.remove(source);rooms.remove(source) }
                    SemanticRoomInstance(semanticRoom(source),activeRoomOffset,"room:$index")
                }
                return SemanticStoreys.compile(n,instances,::segment,
                    { p,entries,turn,type->val c=entries.first().cell;if(c.block.isEmpty()) segmentUnionPlacement(p,entries,type,turn) else Placement(p,block=c.block,material="",component=type) },
                    { source->sourcePlacement(P(),copycat(source),"wall") },{ p->activeAccess?.shouldSkipInteriorAir(activeStoreyId,p+P(0,activeStoreyBase,0))==true }).let { result -> result.copy(plan=override(result.plan,n,palette)) }
            }
            val r=Resolver();val placements=linkedMapOf<P,MutableList<Placement>>();val footprint=linkedSetOf<P>();val shell=linkedSetOf<P>();var minimum=0;var maximum=0;var bottom=4;var top=4
            n.inputs.forEachIndexed { i,source ->
                val offset=n.offsets.getOrNull(i) ?: P()
                activeRoomOffset=offset
                if(activeAccess!=null) rooms.remove(source)
                val room=baseRoom(source)
                val translated=room.footprint.map { it+offset }.toSet()
                require(translated.none { it in footprint }) { "Room interiors overlap; drag them apart in the storey preview" };footprint+=translated;shell+=room.shell.map { it+offset }
                room.plan.placements.forEach { p -> val translated=p.copy(pos=p.pos+offset);placements.getOrPut(translated.pos) { mutableListOf() }+=translated };room.plan.clear.forEach { r.clear(it+offset) }
                minimum=minOf(minimum,room.minimum);maximum=maxOf(maximum,room.maximum);bottom=minOf(bottom,room.bottomThickness);top=minOf(top,room.topThickness)
            }
            for(contributions in placements.values) {
                if(contributions.size>1 && contributions.all { it.block.isEmpty() && it.component in setOf("wall","trim") }) r.add(wallUnion(contributions))
                else contributions.forEach { r.add(it,true) }
            }
            return CompiledStorey(override(r.plan(emptyList()),n,palette),shell,minimum,maximum,bottom,top)
        }
        fun storey(id:String):CompiledStorey=storeys.getOrPut(id) {
            val n=node(id,BuildingKind.STOREY);val base=baseStorey(id)
            if(n.storeyMode=="walls") return@getOrPut base
            val bays=mutableListOf<BayGeometry>()
            val hosts=n.inputs.mapIndexed { i,source -> node(source) to (n.offsets.getOrNull(i) ?: P()) }
            for((host,offset) in hosts) for(bay in bayGeometry(host)) {
                val moved=bay.copy(plan=bay.plan.copy(placements=bay.plan.placements.map { it.copy(pos=it.pos+offset) },clear=bay.plan.clear.map { it+offset }),
                    opening=bay.opening.map { it+offset }.toSet(),interior=bay.interior.map { it+offset }.toSet(),shell=bay.shell.map { it+offset }.toSet())
                val other=hosts.filter { it.first!==host || it.second!=offset }.flatMap { (room,p)->baseRoom(room.id).shell.map { it+p } }.toSet()
                require(moved.shell.intersect(other).isEmpty()) { "Erker an ${host.name.ifBlank { host.id }}: Anschluss liegt an einer Innenwand oder überschneidet einen anderen Raum" }
                require(bays.none { it.shell.intersect(moved.shell).isNotEmpty() }) { "Erker überschneiden sich" }
                bays+=moved
            }
            if(bays.isEmpty()) base else base.copy(plan=override(SpecialRooms.attach(base.plan,bays),n,palette),footprint=base.footprint+bays.flatMap { it.shell },roofFootprint=base.footprint,bayOpenings=bays.flatMap { it.opening }.toSet())
        }
        fun building():CompiledBuilding {
            roofContacts.clear();dormerCeilingCuts.clear()
            val n=nodes.getValue(resultId)
            val attachments=BuildingAttachments(graph,::segment,palette,n) { id -> sourcePlacement(P(),copycat(id),"wall") }
            require(n.levels[0]?.isNotEmpty()==true || n.levels.isEmpty() && n.inputs.any { nodes[it]?.kind==BuildingKind.BUILDING }) { "Connect the ground floor or a building graph" }
            require(n.levels.values.all { it.isNotEmpty() })
            var r=Resolver();val positions=mutableListOf<StoreyPosition>();var previous:CompiledStorey?=null;var previousBase=0
            data class InstancePlan(val instance:BuildingStoreyInstance,val story:CompiledStorey,val base:Int)
            val preliminary=graph.storeyInstances(resultId).map { instance->
                val story=storey(instance.source)
                val base=previous?.let { previousBase+it.maximum-story.minimum-minOf(it.topThickness,story.bottomThickness) } ?: -story.minimum
                previous=story;previousBase=base
                InstancePlan(instance,story,base)
            }
            val access=if(n.accessInstances.isEmpty()) null else VerticalAccess.compile(n.accessInstances,preliminary.map { entry->
                val plan=entry.story.plan.copy(placements=entry.story.plan.placements.map { it.copy(pos=it.pos+P(0,entry.base,0)) },clear=entry.story.plan.clear.map { it+P(0,entry.base,0) })
                AccessStorey(entry.instance.id,entry.instance.level,entry.base,plan,entry.story.footprint)
            },{ id->
                val definition=node(id)
                val kind=when(definition.kind) { BuildingKind.STAIRWELL->VerticalAccessKind.STAIRWELL;BuildingKind.ELEVATOR_SHAFT->VerticalAccessKind.ELEVATOR_SHAFT;BuildingKind.SERVICE_SHAFT->VerticalAccessKind.SERVICE_SHAFT;else->error("Connect a vertical access definition") }
                AccessDefinition(id,kind,definition.stairwell ?: StairwellConfig(),definition.elevatorShaft ?: ElevatorShaftConfig(),definition.serviceShaft ?: ServiceShaftConfig())
            },{ id,type->sourcePlacement(P(),copycat(id),type) },palette)
            var last:CompiledStorey?=null;var roofBase=0
            try {
            for(entry in preliminary) {
                activeAccess=access;activeStoreyId=entry.instance.id;activeStoreyBase=entry.base;activeRoomOffset=P()
                // The first pass determines elevations; reservations apply while emitting the final enclosure.
                val story=if(access==null) entry.story else { rooms.clear();semanticRooms.clear();storeys.clear();storey(entry.instance.source) }
                attachments.collect(node(entry.instance.source),story,P(0,entry.base,0)+n.buildingOffset,entry.instance.id,::semanticRoom)
                story.plan.placements.forEach { r.add(it.copy(pos=it.pos+P(0,entry.base,0)+n.buildingOffset),true) }
                story.plan.clear.forEach { r.clear(it+P(0,entry.base,0)+n.buildingOffset) }
                positions+=StoreyPosition(entry.instance.level,entry.base,entry.base+story.minimum,entry.base+story.maximum)
                last=story;roofBase=entry.base+story.maximum
            }

            } finally {
                activeAccess=null;activeStoreyId="";activeStoreyBase=0;activeRoomOffset=P()
                rooms.clear();semanticRooms.clear();storeys.clear()
            }
            access?.plan?.let { plan->plan.placements.forEach { r.add(it.copy(pos=it.pos+n.buildingOffset),true) };plan.clear.forEach { r.clear(it+n.buildingOffset) } }
            // The top storey's ceiling already closes a building without a separate roof definition.
            if(n.roof.isNotEmpty()) {
                val roofNode=node(n.roof,BuildingKind.ROOF)
                val roof=roofPlan(roofNode,last!!.roofFootprint,if(roofNode.assembly) 0 else roofBase)
                if(dormerCeilingCuts.isNotEmpty()) {
                    val carved=SpecialRooms.carveCeilings(r.plan(emptyList()),dormerCeilingCuts,n.buildingOffset)
                    r=Resolver();carved.placements.forEach { r.add(it) };val roofPositions=roof.placements.map { it.pos+n.buildingOffset }.toSet();carved.clear.filterNot(roofPositions::contains).forEach(r::clear)
                }
                roof.placements.forEach { r.add(it.copy(pos=it.pos+n.buildingOffset),true) };val occupied=r.plan(emptyList()).placements.map { it.pos }.toSet();roof.clear.map { it+n.buildingOffset }.filterNot(occupied::contains).forEach(r::clear)
            }
            n.inputs.forEachIndexed { index,source ->
                if(source.isEmpty()) return@forEachIndexed
                val sourceNode=node(source)
                val exterior=n.sitePaths[index]?.let { landscape(sourceNode.copy(coordinates=it)) } ?: preview(source)
                val offset=n.offsets.getOrNull(index) ?: P()
                exterior.placements.forEach { r.add(it.copy(pos=it.pos+offset),true) };exterior.clear.forEach { r.clear(it+offset) }
            }
            val structural=r.plan(emptyList())
            val attached=SurfaceAttachments.resolve(structural,attachments.columns,attachments.plates,RoofSurfaceModel(roofContacts.map { it.moved(n.buildingOffset) }),attachments.walls,::segment,
                { entry -> if(entry.cell.block.isNotEmpty()) Placement(entry.cell.pos,block=entry.cell.block,material="",component="trim") else segmentPlacement(entry.cell.pos,entry.cell,"trim",entry.overrideRole) },openingMask=attachments::openingMask)
            if(access!=null && (attachments.columns.isNotEmpty() || attachments.plates.isNotEmpty())) {
                val before=structural.placements.associateBy { it.pos };val after=attached.placements.associateBy { it.pos }
                for(p in before.keys+after.keys) {
                    val local=p-n.buildingOffset
                    if(access.shouldCarvePlate("",local) || access.shouldSkipInteriorAir("",local))
                        require(before[p]?.sameVisual(after[p] ?: Placement(block="minecraft:air"))==true || before[p]==null && after[p]==null) { "A roof or wall attachment intersects reserved vertical access at $p" }
                }
            }
            var finalPlan=SpecialRooms.carveCeilings(attached,dormerCeilingCuts,n.buildingOffset)
            val bayCuts=preliminary.flatMap { entry -> entry.story.bayOpenings.map { it+P(0,entry.base,0)+n.buildingOffset } }.toSet()
            if(bayCuts.isNotEmpty()) {
                val kept=finalPlan.placements.filterNot { it.pos in bayCuts && it.component in setOf("wall","trim") }
                val occupied=kept.map { it.pos }.toSet();val carved=Resolver()
                kept.forEach { carved.add(it) };(finalPlan.clear+bayCuts).filterNot(occupied::contains).forEach(carved::clear)
                finalPlan=carved.plan(finalPlan.warnings)
            }
            return CompiledBuilding(override(finalPlan,n,palette),positions,access?.footprints?.mapValues { (_,points)->points.map { it+n.buildingOffset }.toSet() } ?: emptyMap())
        }
        /** Ground layers extend to a fixed depth; clearance becomes explicit air requirements. */
        private fun landscape(n:BuildingNode):Plan {
            val surface=if(n.kind==BuildingKind.GARDEN) LandscapeGeometry.gardenCells(graph,n).map { it+P(0,n.elevation,0) }.toSet() else LandscapeGeometry.pathCells(n)
            val r=Resolver();val type=if(n.kind==BuildingKind.GARDEN) "garden" else "path"
            val source=n.floor.takeIf { it.isNotEmpty() }?.let { copycat(it) }
            require(source?.let(::sourceIsAir)!=true) { "Landscape surfaces need a solid source" }
            val fence=if(n.kind==BuildingKind.GARDEN && n.fenced) LandscapeGeometry.fenceCells(surface.map { it.copy(y=0) }.toSet()).map { it+P(0,n.elevation,0) }.toSet() else emptySet()
            for(p in surface) {
                for(d in 1 until n.groundDepth) r.add(Placement(p+P(0,-d,0),block=n.groundBlock,material="",component=type))
                r.add(if(source==null) Placement(p,block=n.block,material="",component=type)
                    else sourcePlacement(p,source,type,forced=if(n.materialMode==MaterialMode.GROUPED) n.role else ""))
                val fenceHeight=if(p in fence) n.fenceHeight else 0
                for(y in 1..fenceHeight) r.add(Placement(p+P(0,y,0),block=n.fenceBlock,material="",component="fence"))
                for(y in fenceHeight+1..n.clearance) r.clear(p+P(0,y,0))
            }
            val plan=override(r.plan(emptyList()),n,palette)
            return plan.copy(placements=plan.placements.map { it.copy(pos=it.pos+n.landscapeOffset) },clear=plan.clear.map { it+n.landscapeOffset })
        }
        private fun requireDefinedRoof(roof:BuildingNode) {
            if(roof.assembly) {
                require(roof.layout.isNotEmpty()) { "Connect a roof arrangement source" }
                node(roof.layout,BuildingKind.ROOF_LAYOUT)
                require(node(roof.layout,BuildingKind.ROOF_LAYOUT).inputs.any(String::isNotEmpty)) { "Roof arrangement is pending definition; connect roof levels" }
            }
        }
        private fun explicitRoof(roof:BuildingNode,context:BuildingGraph=graph,preview:Boolean=false,validateDormersOnly:Boolean=false):Plan {
            var previous:CompiledStorey?=null;var previousBase=0
            val supports=graph.storeyInstances(resultId).map { instance->
                val value=if(!preview) storey(instance.source) else runCatching { storey(instance.source) }.getOrElse {
                    val source=node(instance.source,BuildingKind.STOREY)
                    val roomHeight=if(source.storeyMode=="walls") source.height else source.inputs.mapNotNull { id->nodes[id]?.takeIf { it.kind==BuildingKind.ROOM }?.let(graph::resolvedNode)?.height }.maxOrNull() ?: 4
                    CompiledStorey(Plan(emptyList(),emptyList(),emptyList(),0,emptyMap()),storeyPreviewFootprint(instance.source),-1,roomHeight+1,1,1)
                }
                val base=previous?.let { previousBase+it.maximum-value.minimum-minOf(it.topThickness,value.bottomThickness) } ?: -value.minimum
                previous=value;previousBase=base
                val tops=value.plan.placements.filter { it.component=="ceiling" }.groupBy { it.pos.copy(y=0) }.mapValues { (_,parts)->parts.maxOf { p->
                    (base+p.pos.y)*16+if(p.block.isNotEmpty()) 16 else kotlin.math.ceil(Geometry.exact(p.parts).cells.flatMap { it.vertices() }.maxOfOrNull { it.y.toDouble() } ?: 16.0).toInt()
                } }
                RoofSupport(instance.id,instance.level,base+value.maximum,value.roofFootprint,ceilingByCell=tops)
            }
            return Roofs.compile(context,roof,if(supports.isEmpty() && preview) listOf(RoofSupport("sample",0,0,RoomGeometry.shell(listOf(Rect(width=3,length=3))))) else supports,palette,{ id->sourcePlacement(P(),copycat(id),"roof") },{ reference->placement(P(),reference,roof.role,"roof") },{ id->
                val value=segment(id)
                RoofSegmentTemplate(value.length,value.height,value.depth,value.cells.groupBy { it.cell.pos }.mapNotNull { (pos,entries)->
                    val cell=entries.first().cell
                    if(cell.empty || cell.block=="minecraft:air") null else pos to (if(cell.block.isEmpty()) segmentUnionPlacement(P(),entries,"roof") else Placement(block=cell.block,material="",component="roof"))
                })
            },contacts=if(preview) null else roofContacts,
                wallSegment={ id -> segment(id) },wallPlacement={ p,entries,turn -> val c=entries.first().cell;if(c.block.isEmpty()) segmentUnionPlacement(p,entries,"wall",turn) else Placement(p,block=c.block,material="",component="wall") },
                ceilingCuts=if(preview) null else dormerCeilingCuts,validateDormersOnly=validateDormersOnly)
        }
        /** Shared by compilation and isolated previews, including the exact legacy gable rules. */
        private fun roofPlan(roof:BuildingNode,footprint:Set<P>,roofBase:Int):Plan {
            requireDefinedRoof(roof)
            if(roof.assembly) return explicitRoof(roof)
            val r=Resolver()
            val coverage=buildSet { for(p in footprint) for(dx in -roof.overhang..roof.overhang) for(dz in -roof.overhang..roof.overhang) add(p+P(dx,0,dz)) }
            require(coverage.size<=8192) { "Roof exceeds 8192 cells" }
            val copies=copycats(roof)
            val ordered=coverage.sortedWith(compareBy({it.z},{it.x}))
            val heights=linkedMapOf<P,Pair<Int,Int>>()
            if(roof.roofType=="flat") for((index,p) in ordered.withIndex()) for(t in 0 until roof.thickness) {
                val source=copies.getOrNull((t*ordered.size+index)%copies.size.coerceAtLeast(1)) ?: roof
                if(sourceIsAir(source)) r.clear(p+P(0,roofBase+t,0)) else r.add(sourcePlacement(p+P(0,roofBase+t,0),source,"roof",forced=if(roof.materialMode==MaterialMode.GROUPED) roof.role else ""))
            }
            else {
                for(p in coverage) {
                    val d=if(roof.ridge==0) P(1,0,0) else P(0,0,1);var a=p;var b=p
                    while(a+P(-d.x,0,-d.z) in coverage) a+=P(-d.x,0,-d.z)
                    while(b+d in coverage) b+=d
                    val left=if(roof.ridge==0) p.x-a.x else p.z-a.z;val right=if(roof.ridge==0) b.x-p.x else b.z-p.z
                    heights[p]=minOf(left,right) to if(left==right) -1 else if(roof.ridge==0) if(left<right) 3 else 1 else if(left<right) 0 else 2
                }
                for((index,p) in ordered.withIndex()) {
                    val profile=heights.getValue(p)
                    val (h,turn)=profile
                    val part=Part(if(turn<0) "copycats:copycat_slab" else "copycats:copycat_stairs",turn.coerceAtLeast(0))
                    for(t in 0 until roof.thickness) {
                        val peak=if(turn<0) roof.roofPeak?.takeIf(String::isNotEmpty)?.let(::copycat) else null
                        val source=peak ?: copies.getOrNull((t*ordered.size+index)%copies.size.coerceAtLeast(1))
                        if(source?.let(::sourceIsAir)==true) { r.clear(p+P(0,roofBase+h+t,0));continue }
                        r.add(if(source==null) Placement(p+P(0,roofBase+h+t,0),listOf(part),palette[roof.role] ?: error("Missing roof material"),component="roof")
                            else sourcePlacement(p+P(0,roofBase+h+t,0),source,"roof",if(peak!=null) roof.ridge else turn.coerceAtLeast(0),if(roof.materialMode==MaterialMode.GROUPED) roof.role else ""))
                    }
                    val lowest=RoomGeometry.directions.minOf { heights[p+it]?.first ?: -1 }
                    val explicitWall=RoomGeometry.directions.indices.any { q -> p+RoomGeometry.directions[q] !in coverage && roof.walls[q.toString()].orEmpty().isNotEmpty() }
                    if(!explicitWall) for(y in (lowest+1).coerceAtLeast(0) until h) r.add(placement(p+P(0,roofBase+y,0),"full@1",if(lowest<0) "gable" else roof.role,if(lowest<0) "gable" else "roof"))
                }
            }
            for(edge in RoomGeometry.boundary(coverage)) {
                val source=roof.walls[edge.direction.toString()].orEmpty();if(source.isEmpty()) continue
                val s=segment(source);val normal=RoomGeometry.directions[edge.direction]
                val cells=edge.cells.map { it+P(-normal.x,0,-normal.z) }
                require(s.air || s.length==edge.length) { "Roof wall ${edge.direction}: needs length ${edge.length}, got ${s.length}" }
                val needed=if(roof.roofType=="flat") s.height else cells.maxOf { heights.getValue(it).first }
                require(s.air || s.height>=needed) { "Roof wall ${edge.direction}: needs height $needed, got ${s.height}" }
                val base=roofBase+if(roof.roofType=="flat") roof.thickness else 0
                if(s.air) for(p in cells) for(y in 0 until if(roof.roofType=="flat") needed else heights.getValue(p).first) for(z in 0 until s.depth) r.clear(p+P(normal.x*z,base+y,normal.z*z))
                else for(entries in s.cells.groupBy { it.cell.pos }.values) {
                    val entry=entries.first();val c=entry.cell;val p=cells[c.pos.x]
                    if(roof.roofType!="flat" && c.pos.y>=heights.getValue(p).first) continue
                    val target=p+P(normal.x*c.pos.z,base+c.pos.y,normal.z*c.pos.z)
                    if(c.empty || c.block=="minecraft:air") r.clear(target)
                    else r.add(if(c.block.isEmpty()) segmentUnionPlacement(target,entries,if(roof.roofType=="flat") "roof" else "gable",edge.direction,
                        if(roof.materialMode==MaterialMode.GROUPED) roof.role else "") else Placement(target,block=c.block,material="",component=if(roof.roofType=="flat") "roof" else "gable"),true)
                }
            }
            return r.plan(emptyList())
        }
    }
    fun validateMetadata(b:Building) {
        require(b.name.length in 1..64 && b.palette.size<=16 && b.schema in GraphModels.building.documentSchemas) { "Invalid building schema/name/palette" }
    }
    fun compile(b:Building,forms:Map<String,Form>,modules:Map<String,Module>):CompiledBuilding {
        validateMetadata(b)
        return Evaluation(b.graph ?: error("Missing building graph"),b.palette,forms,modules).building()
    }
}
