package de.teutonstudio.copycatarchitect.domain

import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor

/** Reduced vertical blocks : horizontal blocks. Geometry uses this ratio, never rounded degrees. */
data class RoofSlope(val p:Int=1,val q:Int=1) {
    fun reduced():RoofSlope {
        require(p in 1..16 && q in 1..16) { "Roof rise and run must be 1–16" }
        fun gcd(a:Int,b:Int):Int=if(b==0) a else gcd(b,a%b)
        val g=gcd(p,q)
        return RoofSlope(p/g,q/g)
    }
    val degrees get()=Math.toDegrees(atan2(p.toDouble(),q.toDouble()))
    val period get()=reduced().let { "${it.p}:${it.q}" }
    companion object { val presets=listOf(1 to 4,1 to 3,1 to 2,2 to 3,3 to 4,1 to 1,4 to 3,3 to 2,2 to 1,3 to 1,4 to 1).map { RoofSlope(it.first,it.second) } }
}
enum class RoofForm { FLAT,PENT,GABLE,HIP,MANSARD,CROSS }
enum class RoofMode { HEIGHT,ANGLE }
data class RoofLevelConfig(val storey:String="",val area:String="",val form:RoofForm=RoofForm.GABLE,val axis:Int=0,
    val mode:RoofMode=RoofMode.ANGLE,val slope:RoofSlope=RoofSlope(),val upperSlope:RoofSlope=RoofSlope(1,2),
    val height16:Int=64,val knee16:Int=0,val overhang16:Int=16,val breakRatio:RoofSlope=RoofSlope(1,3),val family:String="")
data class RoofTemplateCell(val slope:RoofSlope=RoofSlope(),val variant:String="slope",val phase:Int=0,val slice:Int=0,val form:String="")
data class RoofTemplateFamily(val id:String="builtin",val name:String="Roof",val variants:Map<String,String> = emptyMap(),
    val slopes:List<RoofSlope> = RoofSlope.presets,val thickness16:Int=2,val cells:List<RoofTemplateCell> = emptyList())
data class RoofConfig(val family:String="builtin",val families:List<RoofTemplateFamily> = emptyList(),val variants:Map<String,String> = emptyMap(),val wall:String="")
data class BuildingStoreyInstance(val id:String,val level:Int,val source:String)
fun BuildingGraph.storeyInstances():List<BuildingStoreyInstance> {
    val result=nodes.single { it.kind==BuildingKind.BUILDING }
    return result.levels.toSortedMap().map { (level,source)->BuildingStoreyInstance(result.levelIds[level] ?: "storey_$level",level,source) }
}
/** Preserve identity while logical floor numbers compact after a disconnection. */
fun BuildingNode.withLevels(next:Map<Int,String>):BuildingNode {
    val compact=BuildingGraph.compactLevels(next)
    val old=levels.keys.associateWith { levelIds[it] ?: "storey_$it" }
    val ids=linkedMapOf<Int,String>()
    val sourceKeys=next.filterValues(String::isNotEmpty).keys
    sourceKeys.filter { it<0 }.sortedDescending().forEachIndexed { i,k->ids[-i-1]=old[k] ?: freshStoreyId(old.values+ids.values,k) }
    sourceKeys.filter { it>0 }.sorted().forEachIndexed { i,k->ids[i+1]=old[k] ?: freshStoreyId(old.values+ids.values,k) }
    if(0 in sourceKeys) ids[0]=old[0] ?: freshStoreyId(old.values+ids.values,0)
    return copy(levels=compact,levelIds=ids)
}
private fun freshStoreyId(used:Collection<String>,level:Int):String { var value="storey_$level";var i=1;while(value in used) value="storey_${level}_${i++}";return value }

data class RoofSegmentTemplate(val width:Int,val height:Int,val depth:Int=1,val cells:List<Pair<P,Placement>> = emptyList()) {
    internal val validatedPeriods=mutableSetOf<String>()
}
data class RoofSupport(val instanceId:String,val level:Int,val ceiling:Int,val footprint:Set<P>,val ceiling16:Int=ceiling*16,val ceilingByCell:Map<P,Int> = emptyMap())
/** Runtime-only contact geometry; it is never persisted in a building document. */
data class RoofContact(val id:String,val instance:String,val coverage:Set<P>,val minimum:Int,val maximum:Int,val below:(P)->ExactVolume) {
    fun moved(offset:P)=copy(coverage=coverage.map { it+offset.copy(y=0) }.toSet(),minimum=minimum+offset.y*16,maximum=maximum+offset.y*16,
        below={ block->below(block-P(offset.x,offset.y,offset.z)) })
}
data class RoofSurfaceModel(val contacts:List<RoofContact> = emptyList())
object Roofs {
    fun validate(config:RoofLevelConfig) {
        require(config.axis in 0..3 && config.height16 in 0..2048 && config.knee16 in 0..2048 && config.overhang16 in 0..128) { "Invalid roof dimensions" }
        require(config.slope.reduced() in RoofSlope.presets && config.upperSlope.reduced() in RoofSlope.presets) { "Choose a supported exact roof ratio" }
        val b=config.breakRatio.reduced();require(b.p<b.q) { "Mansard break must be inside its run" }
    }
    private data class Ratio(val n:Long,val d:Long=1):Comparable<Ratio> {
        operator fun plus(v:Ratio)=Ratio(n*v.d+v.n*d,d*v.d).reduce()
        operator fun minus(v:Ratio)=Ratio(n*v.d-v.n*d,d*v.d).reduce()
        operator fun times(v:Ratio)=Ratio(n*v.n,d*v.d).reduce()
        operator fun div(v:Ratio)=Ratio(n*v.d,d*v.n).reduce()
        override fun compareTo(other:Ratio)=(n*other.d).compareTo(other.n*d)
        fun value()=n.toDouble()/d
        fun reduce():Ratio {
            fun gcd(a:Long,b:Long):Long=if(b==0L) kotlin.math.abs(a) else gcd(b,a%b)
            require(d!=0L)
            val g=gcd(n,d).coerceAtLeast(1)*(if(d<0) -1 else 1)
            return Ratio(n/g,d/g)
        }
        override fun toString()=if(d==1L) "$n" else "$n/$d"
    }
    private data class Plane(val x:Long,val z:Long,val d:Long,val q:Long) {
        fun height(x:Double,z:Double)=(d-this.x*x-this.z*z)/q
        fun exactHeight(x:Int,z:Int)=Ratio(d-this.x*x-this.z*z,q).reduce()
        fun local(block:P,raise:Ratio,shift:Int)=ExactPlane(x*raise.d,q*raise.d,z*raise.d,
            (d-q*shift-x*block.x*16-q*block.y*16-z*block.z*16)*raise.d+q*raise.n)
    }
    private data class Patch(val planes:List<Plane>) { fun height(x:Double,z:Double)=planes.minOf { it.height(x,z) };fun exactHeight(x:Int,z:Int)=planes.minOf { it.exactHeight(x,z) } }
    private data class Level(val node:BuildingNode,val c:RoofLevelConfig,val support:RoofSupport,val mask:Set<P>,val coverage:Set<P>,val patches:List<Patch>,val raise:Ratio,val family:RoofTemplateFamily,val blocked:Set<P>,val rectangles:List<Rect>,val expectedPeak:Ratio) {
        fun height(x:Double,z:Double)=raise.value()+patches.maxOf { it.height(x,z) }
        fun body(block:P,shift:Int=0):ExactVolume {
            if(block.copy(y=0) !in coverage) return ExactVolume()
            val local=ExactVolume.box(0,0,0,16,16,16)
            return patches.fold(ExactVolume()) { all,p->all.union(ExactVolume.convex(p.planes.map { it.local(block,raise,shift) }).intersect(local)) }
        }
    }
    fun compile(graph:BuildingGraph,roof:BuildingNode,supports:List<RoofSupport>,palette:Map<String,String>,source:(String)->Placement,formSource:(String)->Placement=source,segmentSource:(String)->RoofSegmentTemplate?={ null },contacts:MutableList<RoofContact>?=null):Plan {
        require(roof.layout.isNotEmpty()) { "Connect a roof arrangement source" }
        val layout=graph.nodes.first { it.id==roof.layout && it.kind==BuildingKind.ROOF_LAYOUT }
        require(layout.inputs.isNotEmpty() && layout.inputs.all(String::isNotEmpty)) { "Roof arrangement is pending definition; connect roof levels" }
        require(roof.inputs.drop(6).all(String::isEmpty)) { "An explicit roof accepts six ordered variants: slope, eave, edge, ridge, hip, valley" }
        val shared=roof.roofConfig ?: RoofConfig()
        require(shared.families.size<=16 && shared.families.map { it.id }.distinct().size==shared.families.size && shared.families.none { it.id=="builtin" }) { "Custom roof families need unique IDs" }
        val validatedPeriods=mutableSetOf<String>()
        val segmentCache=mutableMapOf<String,RoofSegmentTemplate>()
        val variantOrder=listOf("slope","eave","edge","ridge","hip","valley")
        fun segmentOverride(variant:String):RoofSegmentTemplate? {
            val index=variantOrder.indexOf(variant);if(index<0) return null
            val id=roof.inputs.getOrNull(index)?.takeIf(String::isNotEmpty) ?: return null
            return segmentCache.getOrPut(id) { segmentSource(id) ?: error("Missing roof segment template $id") }
        }
        val levels=layout.inputs.map { id ->
            val n=graph.nodes.first { it.id==id && it.kind==BuildingKind.ROOF_LEVEL };val c=n.roofLevel ?: RoofLevelConfig();validate(c)
            var support=(if(c.storey.isEmpty()) supports.maxByOrNull { it.level } else supports.firstOrNull { it.instanceId==c.storey }) ?: error("Roof level targets a missing storey instance")
            var blocked=supports.filter { it.level>support.level }.flatMap { it.footprint }.toSet()
            val exposed=support.footprint-blocked
            val mask=if(c.area.isEmpty()) exposed else graph.nodes.first { it.id==c.area && it.kind==BuildingKind.AREA }.coordinates.toSet().also { require(it.all(exposed::contains)) { "Roof area must lie on the exposed selected storey" } }
            require(mask.isNotEmpty() && mask.size<=4096) { "Roof level needs an exposed area" }
            require(mask.all { it.y==0 } && mask.maxOf { it.x }-mask.minOf { it.x }<128 && mask.maxOf { it.z }-mask.minOf { it.z }<128) { "Roof footprint exceeds 128 blocks" }
            val ceilingHeights=mask.map { support.ceilingByCell[it] ?: support.ceiling16 }.distinct()
            require(ceilingHeights.size==1) { "Roof area crosses different structural ceiling heights; select separate roof levels" }
            support=support.copy(ceiling16=ceilingHeights.single(),ceiling=ceil(ceilingHeights.single()/16.0).toInt())
            blocked=blocked+support.ceilingByCell.filterValues { it>support.ceiling16 }.keys
            val familyId=c.family.ifEmpty { shared.family }
            val family=if(familyId=="builtin") RoofTemplateFamily() else shared.families.firstOrNull { it.id==familyId } ?: error("Missing roof template family $familyId")
            require(family.cells.size<=512 && family.cells.map { "${it.slope.reduced().period}:${it.variant}:${it.phase}:${it.slice}" }.distinct().size==family.cells.size) { "Roof period cells must be unique and bounded" }
            family.cells.forEach { cell->val pitch=cell.slope.reduced();require(cell.phase in 0 until pitch.q && cell.slice in -1..pitch.p && cell.form.substringAfterLast('@',"").toIntOrNull()?.let { it>0 }==true) { "Roof period cells need bounded phases, slices and immutable form versions" } }
            for((key,members) in family.cells.groupBy { it.slope.reduced().period+":"+it.variant }) {
                val pitch=members.first().slope.reduced()
                require((0 until pitch.q).all { phase->members.any { it.phase==phase } }) { "Roof period is missing a horizontal phase for $key" }
                if(members.first().variant=="slope" && validatedPeriods.add(family.id+":"+key)) validatePeriod(members,pitch,family.thickness16,formSource)
            }
            require(c.slope.reduced() in family.slopes.map(RoofSlope::reduced) && (c.form!=RoofForm.MANSARD || c.upperSlope.reduced() in family.slopes.map(RoofSlope::reduced)) && family.thickness16 in 1..16) { "Roof family does not support this ratio" }
            val patches=patches(mask,c)
            val structuralRise=mask.flatMap { p->listOf(p.x*16 to p.z*16,(p.x+1)*16 to p.z*16,p.x*16 to (p.z+1)*16,(p.x+1)*16 to (p.z+1)*16,p.x*16+8 to p.z*16+8) }.maxOf { (x,z)->patches.maxOf { it.exactHeight(x,z) } }
            val rise=if(c.form==RoofForm.PENT) structuralRise+Ratio(c.slope.p.toLong()*c.overhang16,c.slope.q.toLong()) else structuralRise
            val finished=rise+Ratio(family.thickness16.toLong())
            val knee=if(c.mode==RoofMode.HEIGHT) Ratio(c.height16.toLong())-finished else Ratio(c.knee16.toLong())
            require(knee.n>=0) { "Roof height needs at least ${ceil(finished.value())/16} blocks for this ratio and span" }
            require((knee+finished)<=Ratio(2048)) { "Roof exceeds 128 blocks above its storey" }
            val e=ceil(c.overhang16/16.0).toInt()
            val coverage=buildSet { for(p in mask) for(dx in -e..e) for(dz in -e..e) if(p+P(dx,0,dz) !in blocked) add(p+P(dx,0,dz)) }
            require(coverage.size<=8192) { "Roof exceeds 8192 cells" }
            Level(n,c,support,mask,coverage,patches,Ratio(support.ceiling16.toLong())+knee+Ratio(family.thickness16.toLong()),family,blocked,maximalRectangles(mask),Ratio(support.ceiling16.toLong())+knee+finished)
        }
        // An internal end of one complete roof must meet its neighbor without a vertical step.
        for(a in levels) for(b in levels) if(a!==b && a.support.ceiling16==b.support.ceiling16) for(p in a.mask) for(d in RoomGeometry.directions) {
            val next=p+d
            if(next !in a.mask && next in b.mask) {
                val x=(p.x+if(d.x>0) 1 else 0)*16
                val z=(p.z+if(d.z>0) 1 else 0)*16
                require(continuousEdge(a,b,x,z,if(d.x==0) 16 else 0,if(d.z==0) 16 else 0)) { "Roof levels end at different heights; align their height or knee wall" }
            }
        }
        val wallNode=shared.wall.takeIf(String::isNotEmpty)?.let { id->graph.nodes.firstOrNull { it.id==id } ?: error("Missing roof wall template $id") }
        val wallCopycat=wallNode?.takeIf { it.kind in setOf(BuildingKind.COPYCAT,BuildingKind.COPYCAT_MIRROR,BuildingKind.COPYCAT_ROTATE) }?.let { source(it.id) }
        val wallPeriod=wallNode?.takeIf { it.kind in setOf(BuildingKind.SEGMENT,BuildingKind.PATTERN,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_ROTATE) }?.let { node->
            segmentSource(node.id)?.also { require(it.width in 1..128 && it.height in 1..128 && it.depth==1) { "Roof wall periods need positive dimensions and depth 1" } } ?: error("Missing roof wall segment ${node.id}")
        }
        val result=Resolver()
        val wallAir=mutableSetOf<P>()
        val contributions=linkedMapOf<P,MutableList<Placement>>()
        val supportOccupancy=mutableMapOf<P,MutableList<Pair<Int,ExactVolume>>>()
        val actualPeaks=mutableMapOf<Int,Double>()
        val contactBlocking=mutableMapOf<P,ExactVolume>()
        val byBase=levels.groupBy { it.support.ceiling16 }
        for(group in byBase.values) {
            val coverage=group.flatMap { it.coverage }.toSet()
            val wallRuns=RoomGeometry.boundary(group.flatMap { it.mask }.toSet()).flatMap { run->
                val normal=RoomGeometry.directions[run.direction]
                run.cells.mapIndexed { index,p->(p+P(-normal.x,0,-normal.z)) to (run.direction to index) }
            }.groupBy({ it.first },{ it.second })
            for(p in coverage) {
                val active=group.filter { p in it.coverage }
                val boundary=active.any { l->p in l.mask && RoomGeometry.directions.any { p+it !in l.mask && p+it !in l.blocked && group.none { other->p+it in other.mask } } }
                val heights=active.flatMap { l->(0..2).flatMap { x->(0..2).map { z->l.height(p.x*16+x*8.0,p.z*16+z*8.0) } } }
                val low=if(boundary) minOf(active.minOf { it.support.ceiling },floor(heights.min()/16).toInt()-1) else floor(heights.min()/16).toInt()-1
                val high=ceil(heights.max()/16).toInt()+1
                for(y in low..high) {
                val block=p.copy(y=y)
                val pieces=mutableListOf<Placement>()
                val body=active.fold(ExactVolume()) { v,l->v.union(clippedBody(l,block)) }
                if(body.isEmpty()) continue
                val down=active.fold(ExactVolume()) { v,l->v.union(clippedBody(l,block,l.family.thickness16)) }
                val skin=body.minus(down)
                val joints=jointBands(active,block)
                val jointBand=joints.values.fold(ExactVolume()) { v,b->v.union(b) }
                val capSkin=skin.intersect(jointBand)
                val visibleSkin=skin.minus(capSkin)
                val backingUpper=active.fold(ExactVolume()) { v,l->v.union(clippedBody(l,block,l.family.thickness16-1)) }
                val backing=backingUpper.minus(down).intersect(visibleSkin)
                val winner=active.maxBy { it.height((p.x+.5)*16,(p.z+.5)*16) }
                if(!visibleSkin.isEmpty()) {
                    val variant=if(p !in winner.mask) "eave" else if(RoomGeometry.directions.any { p+it !in winner.mask }) "edge" else "slope"
                    templatePlacement(winner,block,visibleSkin,variant,shared,roof,palette,source,formSource,::segmentOverride,required=backing)?.let(pieces::add)

                }
                // A separate structural band under the outer rim forms fascia and a visible underside frame.
                val domain=active.fold(ExactVolume()) { v,l->v.union(coverageVolume(l,block)) }
                val inset=listOf(P(2,0,0),P(-2,0,0),P(0,0,2),P(0,0,-2)).fold(domain) { v,offset->v.intersect(domain.translated(offset)) }
                val rim=domain.minus(inset)
                val lower=active.fold(ExactVolume()) { v,l->v.union(clippedBody(l,block,l.family.thickness16+2)) }
                val aboveSupport=ExactVolume.convex(listOf(ExactPlane(0,-1,0,y*16L-winner.support.ceiling16)))
                val reinforcement=down.minus(lower).intersect(jointBand).intersect(aboveSupport)
                val cap=capSkin.union(reinforcement)
                for((kind,band) in joints) {
                    val piece=cap.intersect(band)
                    if(!piece.isEmpty()) templatePlacement(winner,block,piece,kind,shared,roof,palette,source,formSource,::segmentOverride,cap=true)?.let(pieces::add)
                }
                val frame=down.minus(lower).intersect(rim).minus(cap).let { if(active.any { p in it.mask }) it.intersect(aboveSupport) else it }
                if(!frame.isEmpty()) pieces+=semanticPlacement(block,listOf(Triple("roof_frame",frame,"frame")),Part("copycats:copycat_block"),palette,palette["frame"] ?: palette.getValue("wall"))
                if(contacts!=null && pieces.isNotEmpty()) {
                    val solid=pieces.fold(ExactVolume()) { volume,piece -> volume.union(Geometry.exact(piece.parts)) }
                    contactBlocking[block]=(contactBlocking[block] ?: ExactVolume()).union(solid)
                }
                if(boundary && (y+1)*16>winner.support.ceiling16) {
                    val wall=down.minus(skin).minus(frame).minus(cap).intersect(aboveSupport)
                    if(!wall.isEmpty()) {
                        if(wallNode==null) pieces+=semanticPlacement(block,listOf(Triple("roof_wall",wall,"gable")),Part("copycats:copycat_block"),palette,palette["gable"] ?: palette.getValue("wall"),"gable")
                        else {
                            var added=false
                            for((direction,index) in wallRuns[p].orEmpty().filter { (direction,_)->p+RoomGeometry.directions[direction] !in winner.blocked }) {
                                val row=wallPeriod?.let { Math.floorMod(y-floor(winner.support.ceiling16/16.0).toInt(),it.height) }
                                val templates=if(wallPeriod==null) listOfNotNull(wallCopycat) else wallPeriod.cells.filter { it.first.x==Math.floorMod(index,wallPeriod.width) && it.first.y==row }.map { it.second }
                                for(template in templates) {
                                    require(template.block.isEmpty()) { "Roof wall templates must contain Copycat geometry" }
                                    val parts=template.parts.mapNotNull { part->
                                        val exact=ExactGeometry.part(part).rotate(RotationAxis.Y,direction).intersect(wall)
                                        if(exact.isEmpty()) null else part.copy(top=false,turn=0,spin=0,tilt=0,offset=P(),cutMask="",exact=exact,exactClip=null)
                                    }
                                    if(parts.isNotEmpty()) { pieces+=template.copy(pos=block,component="gable",parts=parts);added=true }
                                }
                            }
                            if(!added) wallAir+=block
                        }
                    }
                }
                if(pieces.isNotEmpty()) {
                    val merged=if(pieces.size==1) pieces.single() else ExactPlacementUnion.merge(pieces)
                    val volume=Geometry.exact(merged.parts)
                    val peak=block.y*16+volume.cells.flatMap { it.vertices() }.maxOf { it.y.toDouble() }
                    actualPeaks[winner.support.ceiling16]=maxOf(actualPeaks[winner.support.ceiling16] ?: Double.NEGATIVE_INFINITY,peak)
                    val occupied=supportOccupancy.getOrPut(block) { mutableListOf() }
                    occupied.filter { it.first!=winner.support.ceiling16 }.forEach { (_,other)->require(volume.intersect(other).isEmpty()) { "Roof levels on different support heights intersect at $block" } }
                    occupied+=winner.support.ceiling16 to volume
                    contributions.getOrPut(block) { mutableListOf() }+=merged
                }
            }
            }
        }
        for((base,group) in byBase) {
            val highest=group.maxBy { it.expectedPeak }
            if(highest.c.mode==RoofMode.HEIGHT) require(kotlin.math.abs((actualPeaks[base] ?: Double.NEGATIVE_INFINITY)-highest.expectedPeak.value())<1e-7) { "Custom roof family does not reach its declared finished ridge height; adjust its cap or thickness" }
        }
        contributions.values.forEach { parts->result.add(if(parts.size==1) parts.single() else ExactPlacementUnion.merge(parts)) }
        wallAir.filter { it !in contributions }.forEach(result::clear)
        if(contacts!=null) for(level in levels) contacts+=RoofContact(level.node.id,level.support.instanceId,level.coverage,
            level.support.ceiling16,ceil(level.expectedPeak.value()).toInt()+16,
            { block->clippedBody(level,block).minus(contactBlocking[block] ?: ExactVolume()) })
        return result.plan(emptyList())
    }
    private data class EdgeLine(val offset:Ratio,val slope:Ratio) { fun at(t:Ratio)=offset+slope*t }
    private data class EdgeSegment(val from:Ratio,val to:Ratio,val line:EdgeLine)
    /** Exact min/max envelopes retain every hip, ridge and mansard kink along a cell edge. */
    private fun edgeProfile(level:Level,x:Int,z:Int,dx:Int,dz:Int):List<EdgeSegment> {
        fun line(p:Plane)=EdgeLine(level.raise+Ratio(p.d-p.x*x-p.z*z,p.q).reduce(),Ratio(-p.x*dx-p.z*dz,p.q).reduce())
        fun profile(p:Plane)=listOf(EdgeSegment(Ratio(0),Ratio(1),line(p)))
        return level.patches.map { patch->patch.planes.map(::profile).reduce { a,b->mergeEdgeProfiles(a,b,false) } }
            .reduce { a,b->mergeEdgeProfiles(a,b,true) }
    }
    private fun mergeEdgeProfiles(a:List<EdgeSegment>,b:List<EdgeSegment>,upper:Boolean):List<EdgeSegment> {
        val result=mutableListOf<EdgeSegment>();var i=0;var j=0
        fun append(from:Ratio,to:Ratio,line:EdgeLine) {
            if(result.lastOrNull()?.let { it.to==from && it.line==line }==true) result[result.lastIndex]=result.last().copy(to=to)
            else result+=EdgeSegment(from,to,line)
        }
        while(i<a.size && j<b.size) {
            val l=a[i];val r=b[j];val from=maxOf(l.from,r.from);val to=minOf(l.to,r.to)
            val slope=l.line.slope-r.line.slope
            val cross=if(slope.n==0L) null else ((r.line.offset-l.line.offset)/slope).takeIf { it>from && it<to }
            val points=listOfNotNull(from,cross,to)
            for(k in 0 until points.lastIndex) {
                val lo=points[k];val hi=points[k+1];val mid=(lo+hi)*Ratio(1,2)
                val comparison=l.line.at(mid).compareTo(r.line.at(mid))
                append(lo,hi,if(if(upper) comparison>=0 else comparison<=0) l.line else r.line)
            }
            if(l.to<=r.to) i++
            if(r.to<=l.to) j++
        }
        return result
    }
    private fun continuousEdge(a:Level,b:Level,x:Int,z:Int,dx:Int,dz:Int):Boolean {
        val left=edgeProfile(a,x,z,dx,dz);val right=edgeProfile(b,x,z,dx,dz)
        var i=0;var j=0
        while(i<left.size && j<right.size) {
            val l=left[i];val r=right[j];val from=maxOf(l.from,r.from);val to=minOf(l.to,r.to)
            // The difference is linear here, so its extrema lie at the retained profile endpoints.
            if(listOf(from,(from+to)*Ratio(1,2),to).any { l.line.at(it)>r.line.at(it) }) return false
            if(l.to<=r.to) i++
            if(r.to<=l.to) j++
        }
        return true
    }
    private fun validatePeriod(cells:List<RoofTemplateCell>,pitch:RoofSlope,thickness:Int,forms:(String)->Placement) {
        for(phase in 0 until pitch.q) {
            val members=cells.filter { it.phase==phase }.map { forms(it.form) to P(0,it.slice*16,0) }
            for(row in -1..pitch.p) {
                val upper=ExactPlane(0,pitch.q.toLong(),-pitch.p.toLong(),16L*pitch.p*phase-16L*pitch.q*row)
                val target=ExactVolume.convex(listOf(upper,upper.opposite().copy(d=-upper.d+thickness*pitch.q)))
                if(!target.isEmpty()) {
                    val backingTop=upper.copy(d=upper.d-(thickness-1)*pitch.q)
                    val backing=ExactVolume.convex(listOf(backingTop,upper.opposite().copy(d=-upper.d+thickness*pitch.q)))
                    RoofTemplatePlacement.fitManyOrNull(members,upper,pitch,phase,0,backing,envelope=target)
                }
            }
        }
        fun volume(phase:Int,shift:Int):ExactVolume {
            val source=cells.filter { it.phase==phase }.fold(ExactVolume()) { v,c->v.union(Geometry.exact(forms(c.form).parts).translated(P(0,c.slice*16,0))) }
            val upper=ExactPlane(0,pitch.q.toLong(),-pitch.p.toLong(),16L*pitch.p*phase)
            val envelope=ExactVolume(listOf(ExactCell(listOf(upper,upper.opposite().copy(d=-upper.d+thickness*pitch.q),ExactPlane(1,0,0,16),ExactPlane(-1,0,0,0),ExactPlane(0,0,1,16),ExactPlane(0,0,-1,0)))))
            return source.intersect(envelope).translated(P(0,shift,0))
        }
        require(sectionAtRun(volume(pitch.q-1,0),16).equivalent(sectionAtRun(volume(0,pitch.p*16),0))) { "Custom roof period has incompatible first and last seams" }
    }
    private fun sectionAtRun(volume:ExactVolume,z:Int):ExactVolume {
        return ExactVolume(volume.cells.mapNotNull { cell->
            val planes=mutableListOf<ExactPlane>()
            for(p in cell.planes) {
                val d=p.d-p.z*z
                if(p.x==0L && p.y==0L) { if(d<0) return@mapNotNull null }
                else planes+=ExactPlane(p.x,p.y,0,d)
            }
            ExactCell(planes+listOf(ExactPlane(0,0,1,1),ExactPlane(0,0,-1,0)))
        })
    }
    private data class SelectedFace(val upper:ExactPlane,val slope:RoofSlope,val phase:Int)
    private fun selectFace(l:Level,block:P):SelectedFace {
        val x=(block.x+.5)*16;val z=(block.z+.5)*16
        val patch=l.patches.withIndex().maxBy { it.value.height(x,z) }
        val plane=patch.value.planes.minBy { it.height(x,z) }
        val rise=kotlin.math.abs(plane.x)+kotlin.math.abs(plane.z)
        fun gcd(a:Long,b:Long):Long=if(b==0L) a else gcd(b,a%b)
        val g=gcd(rise,plane.q).coerceAtLeast(1)
        val slope=if(rise==0L) l.c.slope.reduced() else RoofSlope((rise/g).toInt(),(plane.q/g).toInt())
        val r=if(l.c.form in setOf(RoofForm.HIP,RoofForm.MANSARD)) l.rectangles[patch.index] else Rect(l.mask.minOf { it.x },l.mask.minOf { it.z },l.mask.maxOf { it.x }-l.mask.minOf { it.x }+1,l.mask.maxOf { it.z }-l.mask.minOf { it.z }+1)
        val column=when { plane.x<0->block.x-r.x;plane.x>0->r.x+r.width-block.x-1;plane.z<0->block.z-r.z;else->r.z+r.length-block.z-1 }
        return SelectedFace(plane.local(block,l.raise,0),slope,Math.floorMod(column,slope.q))
    }
    /** Stable semantic IDs are limited to nonempty actual regions; caller templates keep their own groups. */
    private fun semanticPlacement(block:P,regions:List<Triple<String,ExactVolume,String>>,prototype:Part,palette:Map<String,String>,fallback:String,component:String="roof"):Placement {
        val present=regions.filterNot { it.second.isEmpty() }
        require(present.isNotEmpty()) { "Roof semantic region must contain geometry" }
        val groups=present.mapIndexed { index,(id,_,role)->MaterialGroup(id,id.removePrefix("roof_").replaceFirstChar { it.uppercase() },if(id in palette) id else role,index) }
        val materials=present.associate { (id,_,role)->id to (palette[id] ?: palette[role] ?: fallback) }
        return Placement(block,present.map { (id,volume,_)->prototype.copy(group=id,exact=volume) },materials.getValue(present.first().first),component=component,groups=groups,surfaces=materials)
    }
    private fun templatePlacement(l:Level,block:P,target:ExactVolume,variant:String,shared:RoofConfig,roof:BuildingNode,palette:Map<String,String>,source:(String)->Placement,formSource:(String)->Placement,segmentOverride:(String)->RoofSegmentTemplate?,cap:Boolean=false,required:ExactVolume=target):Placement? {
        val face=selectFace(l,block)
        val period=if(shared.variants[variant].isNullOrEmpty()) l.family.cells.filter { it.slope.reduced()==face.slope && it.variant==variant } else emptyList()
        val override=shared.variants[variant] ?: l.family.variants[variant]
        val segment=if(period.isEmpty() && override.isNullOrEmpty()) segmentOverride(variant) else null
        if(period.isEmpty() && override.isNullOrEmpty() && segment==null) {
            val prototype=Part(RoofPartParameters.SOURCE,roof=RoofPartParameters(face.slope.p,face.slope.q,Math.floorMod(face.slope.p*face.phase,face.slope.q),variant=variant,thickness=l.family.thickness16))
            val key=if(cap) when(variant) { "ridge"->"roof_ridge";"hip"->"roof_hip";"valley"->"roof_valley";else->"roof_transition" }
                else when(variant) { "eave"->"roof_eave";"edge"->"roof_verge";else->"roof_skin" }
            val regions=if(cap) listOf(Triple(key,target,"frame")) else listOf(Triple("roof_underside",target.intersect(required),roof.role),Triple(key,target.minus(required),roof.role))
            return semanticPlacement(block,regions,prototype,palette,if(cap) palette["frame"] ?: palette.getValue("roof") else palette[roof.role] ?: palette.getValue("roof"))
        }
        if(period.isNotEmpty()) require((0 until face.slope.q).all { phase->period.any { it.phase==phase } }) { "Roof period is missing a horizontal phase for ${face.slope.period} ($variant)" }
        val members=when {
            segment!=null -> {
                require(segment.width==face.slope.q && segment.height in listOf(face.slope.p,face.slope.p+2) && segment.depth==1) { "Roof $variant segment needs ${face.slope.q} columns and ${face.slope.p} rows (or ${face.slope.p+2} with spill rows), depth 1, for ratio ${face.slope.period}" }
                val origin=if(segment.height==face.slope.p+2) -1 else 0
                synchronized(segment.validatedPeriods) {
                    val key=variant+":"+face.slope.period+":"+l.family.thickness16
                    if(key !in segment.validatedPeriods) {
                        val refs=segment.cells.associate { (cell,p)->"period_${cell.x}_${cell.y}@1" to p }
                        val cells=segment.cells.map { (cell,_)->RoofTemplateCell(face.slope,phase=cell.x,slice=cell.y+origin,form="period_${cell.x}_${cell.y}@1") }
                        require((0 until face.slope.q).all { phase->cells.any { it.phase==phase } }) { "Roof segment is missing a horizontal phase" }
                        if(variant=="slope") validatePeriod(cells,face.slope,l.family.thickness16) { refs.getValue(it) }
                        segment.validatedPeriods+=key
                    }
                }
                segment.cells.filter { it.first.x==face.phase }.map { (cell,p)->p to P(0,(cell.y+origin)*16,0) }
            }
            period.isNotEmpty() -> period.filter { it.phase==face.phase }.map { formSource(it.form) to P(0,it.slice*16,0) }
            else -> listOf(source(checkNotNull(override)) to P())
        }
        val fitted=RoofTemplatePlacement.fitManyOrNull(members,face.upper,face.slope,face.phase,0,required,envelope=target) ?: return null
        return fitted.copy(pos=block,component="roof")
    }
    /** Flush joint caps replace the outer skin and add a real two-pixel reinforcement below it. */
    private fun jointBands(levels:List<Level>,block:P):Map<String,ExactVolume> {
        data class Face(val level:Int,val patch:Int,val plane:ExactPlane)
        val faces=levels.flatMapIndexed { li,l->l.patches.flatMapIndexed { pi,p->p.planes.map { Face(li,pi,it.local(block,l.raise,0)) } } }
        val result=linkedMapOf<String,ExactVolume>()
        for(i in faces.indices) for(j in i+1 until faces.size) {
            val a=faces[i];val b=faces[j];val x=-a.plane.x*b.plane.y+b.plane.x*a.plane.y;val z=-a.plane.z*b.plane.y+b.plane.z*a.plane.y
            if(x==0L && z==0L) continue
            val constant=a.plane.d*b.plane.y-b.plane.d*a.plane.y;val denominator=a.plane.y*b.plane.y
            val dot=a.plane.x*b.plane.x+a.plane.z*b.plane.z
            val mansardKink=a.level==b.level && levels[a.level].c.form==RoofForm.MANSARD && dot>0
            val kind=if(mansardKink) "edge" else if(a.level!=b.level || a.patch!=b.patch) "valley" else if(dot<0) "ridge" else if(dot==0L) "hip" else "edge"
            val band=ExactVolume.convex(listOf(ExactPlane(x,0,z,2*denominator-constant),ExactPlane(-x,0,-z,2*denominator+constant),
                a.plane.copy(d=a.plane.d+a.plane.y*2),a.plane.opposite().copy(d=-a.plane.d+a.plane.y*6)))
            if(!band.isEmpty()) result[kind]=(result[kind] ?: ExactVolume()).union(band)
        }
        return result
    }
    private fun clippedBody(l:Level,block:P,shift:Int=0):ExactVolume {
        var body=l.body(block,shift)
        // Fractional eaves use the original wall line, not a rounded block dilation.
        val e=l.c.overhang16
        val coverage=coverageVolume(l,block)
        body=body.intersect(coverage)
        return body
    }
    private fun coverageVolume(l:Level,block:P):ExactVolume {
        val e=l.c.overhang16
        return l.rectangles.filter { r->r.x*16-e<(block.x+1)*16+2 && (r.x+r.width)*16+e>block.x*16-2 && r.z*16-e<(block.z+1)*16+2 && (r.z+r.length)*16+e>block.z*16-2 }.fold(ExactVolume()) { v,r->
            val x=r.x*16-block.x*16;val z=r.z*16-block.z*16
            v.union(ExactVolume.box(x-e,0,z-e,x+r.width*16+e,16,z+r.length*16+e))
        }
    }

    private fun patches(mask:Set<P>,c:RoofLevelConfig):List<Patch> {
        val minX=mask.minOf { it.x }*16;val maxX=(mask.maxOf { it.x }+1)*16;val minZ=mask.minOf { it.z }*16;val maxZ=(mask.maxOf { it.z }+1)*16
        val s=c.slope.reduced()
        fun edge(x:Int,z:Int,d:Int,slope:RoofSlope=s)=Plane(x*slope.p.toLong(),z*slope.p.toLong(),d*slope.p.toLong(),slope.q.toLong())
        val bounds=listOf(edge(-1,0,-minX),edge(1,0,maxX),edge(0,-1,-minZ),edge(0,1,maxZ))
        return when(c.form) {
            RoofForm.FLAT->listOf(Patch(listOf(Plane(0,0,0,1))))
            RoofForm.PENT->listOf(Patch(listOf(bounds[listOf(0,2,1,3)[c.axis]])))
            RoofForm.GABLE,RoofForm.CROSS->listOf(Patch(if(c.axis%2==0) bounds.take(2) else bounds.drop(2)))
            RoofForm.HIP,RoofForm.MANSARD->maximalRectangles(mask).map { r->
                val b=listOf(Triple(-1,0,-r.x*16),Triple(1,0,(r.x+r.width)*16),Triple(0,-1,-r.z*16),Triple(0,1,(r.z+r.length)*16))
                val planes=b.map { (x,z,d)->edge(x,z,d) }.toMutableList()
                if(c.form==RoofForm.MANSARD) {
                    val u=c.upperSlope.reduced();val f=c.breakRatio.reduced();val run=minOf(r.width,r.length)*8L
                    for((x,z,d) in b) {
                        val common=s.q.toLong()*u.q*f.q
                        val offset=(s.p*u.q-u.p*s.q).toLong()*run*f.p
                        planes+=Plane(x*u.p.toLong()*s.q*f.q,z*u.p.toLong()*s.q*f.q,d*u.p.toLong()*s.q*f.q+offset,common)
                    }
                };Patch(planes)
            }
        }
    }
    /** All maximal rectangles make the square-inset field exact also at concave corners. */
    internal fun maximalRectangles(mask:Set<P>):List<Rect> {
        val rectangles=mutableListOf<Rect>();val xs=mask.minOf { it.x }..mask.maxOf { it.x };val zs=mask.minOf { it.z }..mask.maxOf { it.z }
        for(left in xs) { var rows=zs.toSet();for(right in left..xs.last) {
            rows=rows.filterTo(mutableSetOf()) { P(right,0,it) in mask };if(rows.isEmpty()) break
            var start:Int?=null
            for(z in zs.first..zs.last+1) {
                if(z in rows && start==null) start=z
                if(z !in rows && start!=null) { val a=start;val width=right-left+1;val length=z-a
                    if((a until z).any { P(left-1,0,it) !in mask } && (a until z).any { P(right+1,0,it) !in mask }) rectangles+=Rect(left,a,width,length)
                    start=null
                }
            }
        } }
        require(rectangles.size<=128) { "Roof contour exceeds 128 structural patches" };return rectangles
    }
}

fun BuildingGraph.withCurrentSchema():BuildingGraph {
    val upgraded=nodes.any { it.kind in setOf(BuildingKind.ROOF_LEVEL,BuildingKind.STAIRWELL,BuildingKind.ELEVATOR_SHAFT,BuildingKind.SERVICE_SHAFT) || it.roofLevel!=null || it.roofConfig!=null || it.accessInstances.isNotEmpty() || it.levelIds.isNotEmpty() }
    val semantic=nodes.any { it.kind==BuildingKind.ROOM_LAYOUT || it.roomConfig!=null || it.roomLayout!=null || it.facade!=null || it.features!=null || !it.attachments.isNullOrEmpty() }
    return if(semantic && schema<3) copy(schema=3) else if(upgraded && schema<2) copy(schema=2) else this
}

internal fun freshAccessInstanceId(instances:List<VerticalAccessInstance>):String {
    val used=instances.map { it.id }.toSet()
    var index=0
    while("access_$index" in used) index++
    return "access_$index"
}
