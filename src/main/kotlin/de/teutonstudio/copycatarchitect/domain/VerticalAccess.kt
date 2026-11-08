package de.teutonstudio.copycatarchitect.domain

import kotlin.math.roundToInt

enum class StairLayout { STRAIGHT, L, U }
enum class VerticalAccessKind { STAIRWELL, ELEVATOR_SHAFT, SERVICE_SHAFT }

data class StairwellConfig(
    val layout:StairLayout=StairLayout.U,val width:Int=2,val landingDepth:Int=2,val headroom:Int=2,
    val clockwise:Boolean=true,val enclosed:Boolean=true,val tread:String="",val landing:String="",val enclosure:String=""
) {
    fun validate() {
        require(width in 1..8 && landingDepth in width..16 && headroom in 2..8) { "Stairs need a width of 1–8, sufficiently deep landings and at least two blocks of headroom" }
    }
}
data class ElevatorShaftConfig(val width:Int=2,val depth:Int=2,val headroom:Int=2,val enclosed:Boolean=true,val enclosure:String="") {
    fun validate() { require(width in 1..16 && depth in 1..16 && headroom in 2..8) { "Invalid elevator shaft dimensions" } }
}
data class ServiceShaftConfig(val width:Int=1,val depth:Int=1,val enclosed:Boolean=true,val enclosure:String="") {
    fun validate() { require(width in 1..16 && depth in 1..16) { "Invalid service shaft dimensions" } }
}
/** Storey references identify building slots, rather than reusable STOREY sources. */
data class VerticalAccessInstance(
    val id:String="",val source:String="",val position:P=P(),val quarterTurns:Int=0,
    val fromStorey:String="",val toStorey:String="",val stops:List<String> = emptyList()
)
data class AccessDefinition(
    val id:String,val kind:VerticalAccessKind,val stairwell:StairwellConfig=StairwellConfig(),
    val elevatorShaft:ElevatorShaftConfig=ElevatorShaftConfig(),val serviceShaft:ServiceShaftConfig=ServiceShaftConfig()
)
/** Plan coordinates include this instance's base, but not the building's site offset. */
data class AccessStorey(val instanceId:String,val level:Int,val base:Int,val plan:Plan,val footprint:Set<P>)

class AccessCompilation internal constructor(
    val plan:Plan,val footprints:Map<String,Set<P>>,private val plates:Set<P>,private val bodies:Set<P>
) {
    @Suppress("UNUSED_PARAMETER")
    fun shouldCarvePlate(storeyId:String,pos:P):Boolean = pos in plates
    @Suppress("UNUSED_PARAMETER")
    fun shouldSkipInteriorAir(storeyId:String,pos:P):Boolean = pos in bodies
    /** Preflight before a second, context-aware enclosure compilation. */
    @Suppress("UNUSED_PARAMETER")
    fun carve(storeyId:String,original:Plan):Plan {
        val r=Resolver()
        original.placements.forEach { p ->
            if(p.pos in plates) require(p.component in setOf("floor","ceiling")) { "Vertical access intersects ${p.component} at ${p.pos}" }
            else r.add(p,true)
        }
        original.clear.filter { it !in bodies }.forEach(r::clear)
        return r.plan(original.warnings)
    }
}

/** Static building geometry only. An elevator shaft creates no moving mechanism. */
object VerticalAccess {
    private data class LocalCore(val placements:List<Placement>,val walks:List<Box>,val air:Set<P>,val footprint:Set<P>)
    private data class Bounds(val minX:Int,val minZ:Int,val maxX:Int,val maxZ:Int) {
        fun cells()=buildSet { for(x in minX..maxX) for(z in minZ..maxZ) add(P(x,0,z)) }
    }
    fun validateInstance(instance:VerticalAccessInstance) {
        require(instance.id.matches(Regex("[a-zA-Z0-9_-]{1,36}"))) { "Vertical access instances need unique IDs" }
        require(instance.position.y==0 && instance.position.x in -128..128 && instance.position.z in -128..128 && instance.quarterTurns in 0..3) { "Invalid vertical access placement" }
        require(instance.stops.size<=17 && instance.stops.distinct().size==instance.stops.size) { "Invalid elevator stops" }
    }
    fun previewFootprint(definition:AccessDefinition,rise:Int=5):Set<P> {
        val c=definition.stairwell
        val b=when(definition.kind) {
            VerticalAccessKind.STAIRWELL -> stairBounds(c,rise.coerceAtLeast(if(c.layout==StairLayout.STRAIGHT) 1 else 2))
            VerticalAccessKind.ELEVATOR_SHAFT -> Bounds(0,0,definition.elevatorShaft.width-1,definition.elevatorShaft.depth-1)
            VerticalAccessKind.SERVICE_SHAFT -> Bounds(0,0,definition.serviceShaft.width-1,definition.serviceShaft.depth-1)
        }
        val enclosed=when(definition.kind) { VerticalAccessKind.STAIRWELL->c.enclosed;VerticalAccessKind.ELEVATOR_SHAFT->definition.elevatorShaft.enclosed;VerticalAccessKind.SERVICE_SHAFT->definition.serviceShaft.enclosed }
        return (if(enclosed) b.copy(minX=b.minX-1,minZ=b.minZ-1,maxX=b.maxX+1,maxZ=b.maxZ+1) else b).cells().map {
            if(definition.kind==VerticalAccessKind.STAIRWELL && !c.clockwise) it.copy(x=c.width-1-it.x) else it
        }.toSet()
    }
    /** Definition-only preview uses the same physical generator as a building, without its rooms. */
    fun preview(definition:AccessDefinition,copycat:(String,String)->Placement,palette:Map<String,String>):Plan {
        val footprint=previewFootprint(definition)
        val stories=listOf(0,5).mapIndexed { index,base ->
            val r=Resolver()
            r.add(Placement(P(0,base-1,0),listOf(Part("copycats:copycat_block")),component="floor"))
            r.add(Placement(P(0,base+4,0),listOf(Part("copycats:copycat_block")),component="ceiling"))
            AccessStorey("preview_$index",index,base,r.plan(emptyList()),footprint)
        }
        val compiled=compile(listOf(VerticalAccessInstance("preview",definition.id)),stories,{ definition },copycat,palette)
        stories.forEach { compiled.carve(it.instanceId,it.plan) }
        return compiled.plan
    }
    fun compile(
        instances:List<VerticalAccessInstance>,storeys:List<AccessStorey>,definition:(String)->AccessDefinition,
        copycat:(String,String)->Placement,palette:Map<String,String>
    ):AccessCompilation {
        require(instances.size<=16) { "A building supports at most sixteen vertical access instances" }
        val connected=instances.filter { it.source.isNotEmpty() }
        require(connected.map { it.id }.distinct().size==connected.size) { "Vertical access instance IDs must be unique" }
        val ordered=storeys.sortedBy { it.level }
        require(ordered.map { it.instanceId }.distinct().size==ordered.size) { "Storey instances need stable unique IDs" }
        val r=Resolver();val plates=linkedSetOf<P>();val bodies=linkedSetOf<P>();val air=linkedSetOf<P>();val footprints=linkedMapOf<String,Set<P>>()
        val claimed=mutableSetOf<P>()
        for(instance in connected) {
            validateInstance(instance);require(ordered.isNotEmpty()) { "Connect storeys before placing vertical access" }
            fun index(id:String,fallback:Int)=if(id.isEmpty()) fallback else ordered.indexOfFirst { it.instanceId==id }.also { require(it>=0) { "Vertical access refers to a missing storey" } }
            val first=index(instance.fromStorey,0);val last=index(instance.toStorey,ordered.lastIndex)
            require(first<=last) { "Vertical access storey range is reversed" }
            val selected=ordered.subList(first,last+1);val d=definition(instance.source)
            val floors=selected.map { walkingLevel(it,instance.position) }
            require(floors.zipWithNext().all { (a,b)->b>a }) { "Vertical access requires ascending floor surfaces" }
            val stops=if(instance.stops.isEmpty()) selected.map { it.instanceId }.toSet() else instance.stops.toSet()
            require(stops.all { id->selected.any { it.instanceId==id } }) { "Elevator stops must belong to the selected storey range" }
            val local=when(d.kind) {
                VerticalAccessKind.STAIRWELL -> {
                    d.stairwell.validate();require(selected.size>=2) { "A stairwell must connect at least two storeys" }
                    stairs(d.stairwell,floors,copycat,palette)
                }
                VerticalAccessKind.ELEVATOR_SHAFT -> {
                    val c=d.elevatorShaft;c.validate()
                    shaft(c.width,c.depth,c.enclosed,c.enclosure,floors.first(),ceilingLevel(selected.last(),instance.position),floors.filterIndexed { i,_->selected[i].instanceId in stops },c.headroom,copycat,palette,true)
                }
                VerticalAccessKind.SERVICE_SHAFT -> {
                    val c=d.serviceShaft;c.validate()
                    shaft(c.width,c.depth,c.enclosed,c.enclosure,floors.first(),ceilingLevel(selected.last(),instance.position),emptyList(),0,copycat,palette,false)
                }
            }
            fun cell(p:P)=p.turn(instance.quarterTurns)+instance.position
            val footprint=local.footprint.map(::cell).toSet();footprints[instance.id]=footprint
            for(story in selected) require(footprint.all { it in story.footprint }) { "Vertical access must fit inside every selected storey" }
            val placed=local.placements.map { p->p.copy(pos=cell(p.pos),parts=Geometry.rotate(p.parts,instance.quarterTurns)) }
            val requiredAir=local.air.map(::cell).toSet();val ownBodies=placed.map { it.pos }.toSet()
            val ownReservation=ownBodies+requiredAir
            require(ownReservation.none { it in claimed }) { "Vertical access instances overlap" };claimed+=ownReservation
            for(story in ordered) for(p in story.plan.placements) if(p.pos in ownReservation)
                require(p.component in setOf("floor","ceiling")) { "Vertical access intersects ${p.component} at ${p.pos}" }
            placed.forEach { r.add(it,true) };bodies+=ownBodies;air+=requiredAir;plates+=ownReservation
        }
        require(air.none { it in bodies }) { "Vertical access headroom is obstructed" }
        air.forEach(r::clear)
        return AccessCompilation(r.plan(emptyList()),footprints,plates,bodies)
    }

    private fun top(p:Placement):Int = when {
        p.block in BuildingGraphCompiler.CARPETS -> p.pos.y*16+1
        p.block.isNotEmpty() -> (p.pos.y+1)*16
        else -> p.pos.y*16+Geometry.solid(p.parts).boxes().maxOf { it.maxY }
    }
    private fun walkingLevel(story:AccessStorey,anchor:P):Int {
        val at=story.plan.placements.filter { it.component=="floor" && it.pos.x==anchor.x && it.pos.z==anchor.z }
        require(at.isNotEmpty()) { "Vertical access needs a structural floor at its anchor" }
        // Entrance lips remain no greater than half a block, including carpet floors.
        return (at.maxOf(::top)/16.0).roundToInt()
    }
    private fun ceilingLevel(story:AccessStorey,anchor:P):Int {
        val at=story.plan.placements.filter { it.component=="ceiling" && it.pos.x==anchor.x && it.pos.z==anchor.z }
        return at.minOfOrNull { p->p.pos.y+(if(p.block.isEmpty()) Geometry.solid(p.parts).boxes().minOf { it.y }/16 else 0) }
            ?: (story.plan.clear.filter { it.x==anchor.x && it.z==anchor.z }.maxOfOrNull { it.y }?.plus(1) ?: story.base+4)
    }
    private fun template(id:String,component:String,source:String,copycat:(String,String)->Placement,palette:Map<String,String>):Placement {
        val p=if(id.isEmpty()) Placement(parts=listOf(Part(source)),material=palette["wall"] ?: "minecraft:stone",component=component) else copycat(id,component)
        require(p.block.isEmpty() && p.parts.isNotEmpty()) { "Vertical access templates must contain Copycat geometry" }
        Geometry.normalized(p.parts,p.groups)
        return p.copy(pos=P(),component=component)
    }
    private fun validateStair(p:Placement) {
        val s=Geometry.solid(p.parts)
        for(x in 3..12) for(z in 0..15) {
            val h=if(z<8) 8 else 16
            require(s.has(x,h-1,z) && (h..15).none { y->s.has(x,y,z) }) { "The tread template must retain the walking profile of a straight Copycat stair" }
        }
    }
    private fun validateLanding(p:Placement) {
        val s=Geometry.solid(p.parts)
        require((0..15).all { x->(0..15).all { z->s.has(x,15,z) } }) { "The landing template needs a continuous upper walking surface" }
    }
    private fun stairBounds(c:StairwellConfig,rise:Int):Bounds {
        c.validate();require(rise>=if(c.layout==StairLayout.STRAIGHT) 1 else 2) { "The selected stair layout needs more height" }
        val a=(rise+1)/2;val b=rise/2;val w=c.width;val d=c.landingDepth
        return when(c.layout) {
            StairLayout.STRAIGHT -> Bounds(0,-d,w-1,rise+d-1)
            StairLayout.L -> Bounds(0,-d,w+b+d-1,a+maxOf(w,d)-1)
            StairLayout.U -> Bounds(0,-d,2*w-1,a+d-1)
        }
    }
    private fun stairs(c:StairwellConfig,floors:List<Int>,copycat:(String,String)->Placement,palette:Map<String,String>):LocalCore {
        val tread=template(c.tread,"stairs","copycats:copycat_stairs",copycat,palette);validateStair(tread)
        val landing=template(c.landing,"landing","copycats:copycat_block",copycat,palette);validateLanding(landing)
        val enclosure=if(c.enclosed) template(c.enclosure,"access_wall","copycats:copycat_block",copycat,palette) else null
        val placements=mutableListOf<Placement>();val walks=mutableListOf<Box>();val air=linkedSetOf<P>()
        val footprint=linkedSetOf<P>();val w=c.width;val depth=c.landingDepth
        fun addLanding(x0:Int,z0:Int,width:Int,length:Int,y:Int) {
            for(x in x0 until x0+width) for(z in z0 until z0+length) {
                placements+=landing.copy(pos=P(x,y-1,z));walks+=Box(x*16,y*16,z*16,(x+1)*16,(y+c.headroom)*16,(z+1)*16)
                for(a in 0 until c.headroom) air+=P(x,y+a,z)
            }
        }
        fun flight(x0:Int,z0:Int,count:Int,y:Int,turn:Int) {
            for(i in 0 until count) for(a in 0 until w) {
                val p=when(turn) { 0->P(x0+a,y+i,z0+i);2->P(x0+a,y+i,z0-i);else->P(x0+i,y+i,z0+a) }
                placements+=tread.copy(pos=p,parts=Geometry.rotate(tread.parts,turn))
                val low=Box(p.x*16,p.y*16+8,p.z*16,(p.x+1)*16,(p.y+c.headroom)*16+8,p.z*16+8)
                val high=Box(p.x*16,(p.y+1)*16,p.z*16+8,(p.x+1)*16,(p.y+c.headroom+1)*16,(p.z+1)*16)
                walks+=orientCellBox(low,p,turn);walks+=orientCellBox(high,p,turn)
                for(h in 1..c.headroom) air+=p+P(0,h,0)
            }
        }
        for((lower,upper) in floors.zipWithNext()) {
            val rise=upper-lower;val bounds=stairBounds(c,rise);val a=(rise+1)/2;val b=rise/2
            footprint+=bounds.cells();addLanding(0,-depth,w,depth,lower)
            when(c.layout) {
                StairLayout.STRAIGHT -> { flight(0,0,rise,lower,0);addLanding(0,rise,w,depth,upper) }
                StairLayout.L -> {
                    flight(0,0,a,lower,0);addLanding(0,a,w,depth,lower+a)
                    flight(w,a,b,lower+a,3);addLanding(w+b,a,depth,w,upper)
                }
                StairLayout.U -> {
                    flight(0,0,a,lower,0);addLanding(0,a,2*w,depth,lower+a)
                    flight(w,a-1,b,lower+a,2);addLanding(w,-depth,w,depth+a-b,upper)
                    addLanding(0,-depth,w,depth,upper)
                }
            }
        }
        val bounds=Bounds(footprint.minOf { it.x },footprint.minOf { it.z },footprint.maxOf { it.x },footprint.maxOf { it.z })
        if(enclosure!=null) {
            val expanded=bounds.copy(minX=bounds.minX-1,minZ=bounds.minZ-1,maxX=bounds.maxX+1,maxZ=bounds.maxZ+1)
            val walls=expanded.cells()-bounds.cells();footprint+=walls
            for(p in walls) for(y in floors.first() until floors.last()+c.headroom) {
                val atDoor=floors.any { y in it until it+c.headroom } && when(c.layout) {
                    StairLayout.STRAIGHT -> p.x in 0 until w && p.z in setOf(expanded.minZ,expanded.maxZ)
                    StairLayout.L -> p.z==expanded.minZ && p.x in 0 until w || p.x==expanded.maxX && p.z in bounds.maxZ-w+1..bounds.maxZ
                    StairLayout.U -> p.z==expanded.minZ && p.x in 0 until 2*w
                }
                if(atDoor) air+=p.copy(y=y) else placements+=enclosure.copy(pos=p.copy(y=y))
            }
        }
        val r=Resolver();placements.forEach { r.add(it,true) };val merged=r.plan(emptyList()).placements
        checkHeadroom(merged,walks)
        val local=LocalCore(merged,walks,air-merged.map { it.pos }.toSet(),footprint)
        return if(c.clockwise) local else mirror(local,w)
    }
    private fun orientCellBox(box:Box,p:P,q:Int):Box {
        var b=box.copy(x=box.x-p.x*16,y=box.y-p.y*16,z=box.z-p.z*16,maxX=box.maxX-p.x*16,maxY=box.maxY-p.y*16,maxZ=box.maxZ-p.z*16)
        repeat(q) { b=Box(16-b.maxZ,b.y,b.x,16-b.z,b.maxY,b.maxX) }
        return Box(b.x+p.x*16,b.y+p.y*16,b.z+p.z*16,b.maxX+p.x*16,b.maxY+p.y*16,b.maxZ+p.z*16)
    }
    private fun checkHeadroom(placements:List<Placement>,walks:List<Box>) {
        val byCell=placements.associateBy { it.pos }
        for(b in walks) {
            for(x in Math.floorDiv(b.x,16)..Math.floorDiv(b.maxX-1,16)) for(y in Math.floorDiv(b.y,16)..Math.floorDiv(b.maxY-1,16)) for(z in Math.floorDiv(b.z,16)..Math.floorDiv(b.maxZ-1,16)) {
                val p=byCell[P(x,y,z)] ?: continue
                val collides=Geometry.solid(p.parts).boxes().any { s->s.x+x*16<b.maxX && s.maxX+x*16>b.x && s.y+y*16<b.maxY && s.maxY+y*16>b.y && s.z+z*16<b.maxZ && s.maxZ+z*16>b.z }
                require(!collides) { "Stair headroom is obstructed; increase the storey height or adjust the layout" }
            }
        }
    }
    private fun mirror(local:LocalCore,width:Int):LocalCore {
        fun cell(p:P)=p.copy(x=width-1-p.x)
        fun part(part:Part):Part {
            val source=Solid.part(part);val mirrored=Solid();source.voxels { x,y,z->mirrored.set(15-x,y,z) }
            val rotated=PartRotations.rotate(part,RotationAxis.Y,if(part.turn%2==0) 0 else 2) ?: error("Template cannot be mirrored")
            val base=Solid.part(rotated.copy(cutMask=""))
            require(base.contains(mirrored)) { "Asymmetric template cannot retain its source when mirrored" }
            return rotated.copy(cutMask=if(base.count()==mirrored.count()) "" else mirrored.mask())
        }
        return local.copy(placements=local.placements.map { p->p.copy(pos=cell(p.pos),parts=p.parts.map(::part)) },air=local.air.map(::cell).toSet(),footprint=local.footprint.map(::cell).toSet())
    }
    private fun shaft(width:Int,depth:Int,enclosed:Boolean,enclosureId:String,bottom:Int,top:Int,stops:List<Int>,headroom:Int,
                      copycat:(String,String)->Placement,palette:Map<String,String>,elevator:Boolean):LocalCore {
        require(top>bottom && stops.all { it+headroom<=top }) { "Shaft stops need sufficient headroom beneath the ceiling" }
        val air=linkedSetOf<P>();val placements=mutableListOf<Placement>()
        var footprint=Bounds(0,0,width-1,depth-1).cells()
        for(p in footprint) for(y in bottom until top) air+=p.copy(y=y)
        if(enclosed) {
            val outer=Bounds(-1,-1,width,depth);val walls=outer.cells()-footprint;footprint=outer.cells()
            val source=template(enclosureId,"access_wall","copycats:copycat_block",copycat,palette)
            for(p in walls) for(y in bottom until top) {
                val stop=elevator && p.z == -1 && p.x in 0 until width && stops.any { y in it until it+headroom }
                if(stop) air+=p.copy(y=y) else placements+=source.copy(pos=p.copy(y=y))
            }
        }
        return LocalCore(placements,emptyList(),air,footprint)
    }
}
