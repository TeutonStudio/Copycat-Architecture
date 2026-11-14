package de.teutonstudio.copycatarchitect.domain

/** Dimensions describe clear interiors. Sources are repeated at their native size, never stretched. */
data class BayWindowConfig(val width:Int=3,val depth:Int=2,val wall:String="",val front:String="",val floor:String="",val ceiling:String="")
data class BayWindowInstance(val id:String="",val source:String="",val edge:String="",val offset:Int=0,val openingWidth:Int=0,val openingHeight:Int=0)
data class DormerConfig(val width:Int=3,val depth:Int=3,val wallHeight:Int=2,val slope:RoofSlope=RoofSlope(),val wall:String="",val front:String="",val roof:String="")
data class DormerInstance(val id:String="",val source:String="",val position:P=P(),val direction:Int=0,val openCeiling:Boolean=false)
data class BayGeometry(val plan:Plan,val opening:Set<P>,val interior:Set<P>,val shell:Set<P>)
data class BayAnchor(val edge:String,val direction:Int,val cells:List<P>)
internal data class BayFrame(val origin:P,val direction:Int,val wallDepth:Int)

object SpecialRooms {
    private val segmentKinds=(setOf(BuildingKind.ROTATION_SEGMENT,BuildingKind.SEGMENT,BuildingKind.PATTERN,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_ROTATE)+SegmentBooleans.kinds)
    fun validate(n:BuildingNode,nodes:Map<String,BuildingNode>) {
        require(n.bayWindow==null || n.kind==BuildingKind.BAY_WINDOW) { "Erkereinstellungen benötigen einen Erkerknoten" }
        require(n.dormer==null || n.kind==BuildingKind.DORMER) { "Gaubeneinstellungen benötigen einen Gaubenknoten" }
        require(n.bayWindowInstances.isEmpty() || n.kind==BuildingKind.ROOM) { "Erkerinstanzen gehören zu einem Raum" }
        require(n.dormerInstances.isEmpty() || n.kind==BuildingKind.ROOF_LEVEL) { "Gaubeninstanzen gehören zu einer Dachebene" }
        fun source(id:String,kinds:Set<BuildingKind>) { require(id.isEmpty() || nodes[id]?.kind in kinds) { "Unpassende Quelle für Erker oder Gaube: $id" } }
        n.bayWindow?.let { c ->
            require(c.width in 1..32 && c.depth in 1..32) { "Erkermaße müssen zwischen 1 und 32 Blöcken liegen" }
            source(c.wall,segmentKinds);source(c.front,segmentKinds);source(c.floor,segmentKinds+BuildingKind.FLOOR);source(c.ceiling,segmentKinds+BuildingKind.CEILING)
        }
        n.dormer?.let { c ->
            require(c.width in 1..32 && c.depth in 1..32 && c.wallHeight in 1..16 && c.slope.reduced() in RoofSlope.presets) { "Ungültige Gaubenmaße oder Dachneigung" }
            source(c.wall,segmentKinds);source(c.front,segmentKinds);source(c.roof,CopycatBooleans.sourceKinds)
        }
        fun ids(values:List<String>) { require(values.size<=16 && values.distinct().size==values.size && values.all { it.matches(Regex("[a-zA-Z0-9_-]{1,36}")) }) { "Aufbauten benötigen eindeutige Instanz-IDs; höchstens 16 je Raum oder Dachebene" } }
        ids(n.bayWindowInstances.map { it.id });ids(n.dormerInstances.map { it.id })
        n.bayWindowInstances.forEach { i -> source(i.source,setOf(BuildingKind.BAY_WINDOW));require(i.offset in 0..128 && i.openingWidth in 0..32 && i.openingHeight in 0..128) { "Ungültiger Erkerdurchgang" } }
        n.dormerInstances.forEach { i -> source(i.source,setOf(BuildingKind.DORMER));require(i.direction in 0..3 && i.position.y==0 && i.position.x in -128..128 && i.position.z in -128..128) { "Ungültige Gaubenposition" } }
    }

    /** Use physical wall columns so joined outlines and thick exterior walls share the same placement rule. */
    fun anchors(graph:BuildingGraph,host:BuildingNode,room:SemanticRoom?=null):List<BayAnchor> {
        if(host.roomConfig?.mode!=RoomMode.SEGMENTS) return RoomWallJoints.runs(graph.resolvedNode(host)).map { BayAnchor(it.id,it.direction,it.cells) }
        val topology=room?.topology ?: RoomTopology.outline(graph,host)
        val groups=mutableListOf<MutableList<RoomBoundarySpan>>()
        for(span in topology.spans) {
            if(span.policy==WallPolicy.OPEN) continue
            val previous=groups.lastOrNull()?.lastOrNull()
            if(previous!=null && previous.direction==span.direction && previous.end==span.start) groups.last()+=span
            else groups+=mutableListOf(span)
        }
        return groups.map { spans ->
            val first=spans.first()
            BayAnchor(first.id,first.direction,spans.flatMap { span -> RoomTopology.units(span.start,span.end).map { RoomTopology.interiorColumn(it.first,span.direction) } })
        }
    }

    internal fun bayFrame(graph:BuildingGraph,host:BuildingNode,room:SemanticRoom,instance:BayWindowInstance,c:BayWindowConfig):BayFrame {
        val a=anchors(graph,host,room).firstOrNull { it.edge==instance.edge } ?: error("Erker ${instance.id}: gültige Außenwand wählen")
        require(instance.offset+c.width<=a.cells.size) { "Erker ${instance.id}: die Zielwand ist zu kurz" }
        val normal=RoomGeometry.directions[a.direction]
        val selected=a.cells.drop(instance.offset).take(c.width)
        val hostWalls=room.compiled.plan.placements.filter { it.component in setOf("wall","trim") }
        val columns=hostWalls.map { it.pos.copy(y=0) }.toSet()
        // Find the outer surface of every selected column, including multi-block wall depths.
        val outer=selected.map { start ->
            val first=(0..16).map { start+normal.times(it) }.firstOrNull { it in columns }
                ?: (1..16).map { start-normal.times(it) }.firstOrNull { it in columns }
                ?: error("Erker ${instance.id}: Anschluss benötigt eine geschlossene Außenwand")
            var p=first
            while(p+normal in columns && dot(p-start,normal)<32) p+=normal
            require((1..16).any { p-normal.times(it) in room.compiled.footprint }) { "Erker ${instance.id}: Anschluss benötigt eine geschlossene Außenwand" }
            p
        }
        require(outer.all { dot(it,normal)==dot(outer.first(),normal) }) { "Erker ${instance.id}: Anschlusswand ist nicht eben" }
        val hostDepth=outer.maxOf { column -> (1..32).firstOrNull { column-normal.times(it) in room.compiled.footprint }
            ?: error("Erkeranschluss überschreitet die Wandtiefe") }
        val origin=outer.first()-P(1,0,c.depth+1).turn(a.direction)
        return BayFrame(origin,a.direction,hostDepth)
    }

    fun bay(graph:BuildingGraph,host:BuildingNode,room:SemanticRoom,instance:BayWindowInstance,
            segment:(String)->FixedSegment,placement:(P,List<SegmentCell>,Int,String)->Placement,plates:(BuildingNode)->CompiledRoom,
            copycat:(String)->Placement={ error("Fenstereinsatz benötigt eine Copycat-Quelle: $it") }):BayGeometry {
        val definition=graph.nodes.firstOrNull { it.id==instance.source && it.kind==BuildingKind.BAY_WINDOW } ?: error("Erker ${instance.id}: Definitionsquelle anschließen")
        val c=definition.bayWindow ?: BayWindowConfig()
        require(listOf(c.wall,c.front,c.floor,c.ceiling).all(String::isNotEmpty)) { "Erker ${instance.id}: Wand, Fensterfront, Boden und Decke anschließen" }
        val frame=bayFrame(graph,host,room,instance,c)
        val origin=frame.origin;val hostDepth=frame.wallDepth
        val hostWalls=room.compiled.plan.placements.filter { it.component in setOf("wall","trim") }
        fun world(p:P)=origin+p.turn(frame.direction)
        val height=host.roomConfig?.heightOverride ?: if(host.roomConfig?.mode==RoomMode.SEGMENTS) room.topology.height else graph.resolvedNode(host).height
        val floorNode=graph.nodes.first { it.id==c.floor };val ceilingNode=graph.nodes.first { it.id==c.ceiling }
        val bottom=if(graph.nodes.firstOrNull { it.id==host.floor }?.occupied==true) 1 else 0
        val top=height-if(graph.nodes.firstOrNull { it.id==host.ceiling }?.occupied==true) 1 else 0
        val width=instance.openingWidth.takeUnless { it==0 } ?: c.width
        val passageHeight=instance.openingHeight.takeUnless { it==0 } ?: (top-bottom)
        require(width<=c.width && passageHeight<=top-bottom) { "Erker ${instance.id}: Durchgang überschreitet den Innenraum" }
        require(floorNode.occupied==(bottom==1) && ceilingNode.occupied==(top<height)) { "Erkerboden und -decke müssen bündig zum Hauptraum liegen" }
        val local=BuildingNode("bay_preview_room",BuildingKind.ROOM,height=height,rectangles=listOf(Rect(1,1,c.width,c.depth)),floor=c.floor,ceiling=c.ceiling)
        val slab=plates(local)
        require(slab.minimum==room.compiled.minimum && slab.maximum==room.compiled.maximum) { "Erkerboden und -decke müssen die Schichtdicke des Hauptraums verwenden" }
        val added=mutableListOf<Placement>()
        slab.plan.placements.filter { it.component in setOf("floor","ceiling") }.forEach { p -> added+=p.copy(pos=world(p.pos),parts=p.parts.map { PartRotations.rotate(it,RotationAxis.Y,frame.direction) ?: error("Erkerquelle kann nicht gedreht werden") }) }
        val periods=mutableMapOf<String,Map<P,Placement>>()
        fun wall(source:String,length:Int,start:P,direction:Int) {
            val value=segment(source)
            require(!value.air && value.length>0 && value.height>0) { "Erkerwände benötigen eine feste Segmentquelle" }
            val period=periods.getOrPut(source) { wallPeriod(value,placement,copycat) }
            for(i in 0 until length) for(y in 0 until height) for(z in 0 until value.depth) {
                val template=period[P(i%value.length,y%value.height,z)] ?: continue
                val p=start+P(i,y,-(value.depth-1-z)).turn(direction)
                added+=mounted(template,world(p),(direction+frame.direction)%4)
            }
        }
        wall(c.front,c.width+2,P(),0)
        wall(c.wall,c.depth,P(c.width+1,0,1),1)
        wall(c.wall,c.depth,P(0,0,c.depth),3)
        val plateColumns=added.filter { it.component in setOf("floor","ceiling") }.map { it.pos.copy(y=0) }.toSet()
        val wallColumns=added.filter { it.component=="wall" }.map { it.pos.copy(y=0) }.toSet()
        val slabPlacements=added.filter { it.component in setOf("floor","ceiling") }
        for(column in wallColumns-plateColumns) {
            val nearest=plateColumns.minByOrNull { kotlin.math.abs(it.x-column.x)+kotlin.math.abs(it.z-column.z) } ?: continue
            slabPlacements.filter { it.pos.copy(y=0)==nearest && it.pos.y !in 0 until height }.forEach { added+=it.copy(pos=column.copy(y=it.pos.y)) }
        }
        val interior=buildSet { for(x in 1..c.width) for(z in 1..c.depth) add(world(P(x,0,z))) }
        require(interior.intersect(room.compiled.shell).isEmpty()) { "Erker ${instance.id} überschneidet den Hauptraum" }
        val shell=added.map { it.pos.copy(y=0) }.toSet()+interior
        val openingStart=1+(c.width-width)/2
        val opening=hostWalls.map { it.pos }.filter { p ->
            val localP=(p-origin).turn(-frame.direction)
            localP.x in openingStart until openingStart+width && localP.y in bottom until bottom+passageHeight && localP.z in c.depth+1 until c.depth+1+hostDepth
        }.toSet()
        require(opening.isNotEmpty()) { "Erker ${instance.id}: die Anschlusswand enthält keine Öffnungsfläche" }
        val r=Resolver();added.groupBy { it.pos }.values.forEach { r.add(RoomTopology.merge(it)) }
        for(p in interior) for(y in bottom until top) r.clear(p+P(0,y,0))
        return BayGeometry(r.plan(emptyList()),opening,interior,shell)
    }

    fun attach(base:Plan,bays:List<BayGeometry>):Plan {
        val cuts=bays.flatMap { it.opening }.toSet()
        val original=base.placements.filterNot { it.pos in cuts && it.component in setOf("wall","trim") }
        val byPosition=original.associateBy { it.pos }
        val r=Resolver();original.forEach { r.add(it) }
        for(bay in bays) for(p in bay.plan.placements) {
            val old=byPosition[p.pos]
            if(old!=null && p.component in setOf("floor","ceiling") && old.component==p.component) continue
            require(old==null || old.sameVisual(p)) { "Erker überschneidet bestehende Geometrie bei ${p.pos}" }
            if(old==null) r.add(p)
        }
        val occupied=(original.map { it.pos }+bays.flatMap { it.plan.placements.map { p -> p.pos } }).toSet()
        (base.clear+bays.flatMap { it.plan.clear }+cuts).filterNot { it in occupied }.forEach(r::clear)
        return r.plan(base.warnings+bays.flatMap { it.plan.warnings })
    }
    private fun dot(a:P,b:P)=a.x*b.x+a.y*b.y+a.z*b.z
    private fun P.times(v:Int)=P(x*v,y*v,z*v)

    /** Resolve opening masks and inserts before repeating a segment's native cell period. */
    internal fun wallPeriod(value:FixedSegment,placement:(P,List<SegmentCell>,Int,String)->Placement,
        copycat:(String)->Placement={ error("Fenstereinsatz benötigt eine Copycat-Quelle: $it") }):Map<P,Placement> {
        if(value.metadata.instances.none { it.features.openings.isNotEmpty() }) return value.cells.groupBy { it.cell.pos }.mapNotNull { (p,entries) ->
            if(entries.first().cell.empty || entries.first().cell.block=="minecraft:air") null else p to placement(p,entries,0,"wall")
        }.toMap()
        val layout=BuildingNode("period_layout",BuildingKind.ROOM_LAYOUT,inputs=listOf("period"),roomLayout=RoomLayoutConfig(instances=listOf(RoomSegmentInstance("wall",policy=WallPolicy.EXPLICIT))))
        val room=BuildingNode("period_room",BuildingKind.ROOM,roomConfig=RoomConfig(mode=RoomMode.SEGMENTS),layout=layout.id)
        val semantic=RoomTopology.compile(room,layout,{ value },placement,{ _,h -> RoomPlateResult(maximum=h,usableHeight=h) })
        val result=SemanticStoreys.compile(BuildingNode("period_storey",BuildingKind.STOREY),listOf(SemanticRoomInstance(semantic)),{ value },placement,copycat).plan
        require(result.placements.all { it.pos.x in 0 until value.length && it.pos.y in 0 until value.height && it.pos.z in 0 until value.depth }) { "Fenstereinsätze müssen innerhalb der Segmentperiode liegen" }
        return result.placements.associateBy { it.pos }
    }
    internal fun mounted(value:Placement,position:P,turn:Int):Placement=value.copy(pos=position,
        parts=value.parts.map { PartRotations.rotate(it,RotationAxis.Y,turn) ?: error("Aufbauquelle kann nicht gedreht werden") })

    internal fun carveCeilings(plan:Plan,cuts:Map<P,ExactVolume>,offset:P=P()):Plan {
        if(cuts.isEmpty()) return plan
        val r=Resolver();val occupied=mutableSetOf<P>();val clear=plan.clear.toMutableSet()
        for(p in plan.placements) {
            val mask=cuts[p.pos-offset]
            val value=if(mask==null || p.component!="ceiling") p else clipped(p,cube.minus(mask))
            if(value==null) clear+=p.pos else { r.add(value);occupied+=value.pos }
        }
        clear.filterNot(occupied::contains).forEach(r::clear)
        return r.plan(plan.warnings)
    }
    internal val cube=ExactVolume.box(0,0,0,16,16,16)
    internal fun clipped(p:Placement,allowed:ExactVolume):Placement? {
        if(allowed.isEmpty()) return null
        if(p.block.isNotEmpty()) {
            require(allowed.equivalent(cube)) { "Ein schräger Dachanschluss benötigt Copycat-Geometrie statt ${p.block}" }
            return p
        }
        val parts=p.parts.mapNotNull { part -> ExactGeometry.part(part).intersect(allowed).takeUnless { it.isEmpty() }?.let { part.copy(exactClip=it) } }
        return if(parts.isEmpty()) null else p.copy(parts=parts)
    }
}
