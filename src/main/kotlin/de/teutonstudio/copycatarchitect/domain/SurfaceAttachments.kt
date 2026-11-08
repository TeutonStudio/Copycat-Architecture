package de.teutonstudio.copycatarchitect.domain

import kotlin.math.ceil

data class PlacedColumn(val instance:SegmentFeatureInstance,val extension:ColumnExtensionSpec,val materialOverride:String?=null)
data class PlacedPlateAttachment(val source:String,val instance:String,val attachment:AttachmentSpec,val placements:List<Placement>)
data class WallAttachmentSurface(val reference:SurfaceRef,val placements:List<Placement>)

/** Late geometric constraints. Neither these results nor their dimensions feed structural heights. */
object SurfaceAttachments {
    private val cube=ExactVolume.box(0,0,0,16,16,16)

    fun resolve(plan:Plan,columns:List<PlacedColumn>,plates:List<PlacedPlateAttachment>,roofs:RoofSurfaceModel,
                walls:List<WallAttachmentSurface>,source:(String)->FixedSegment,placement:(SegmentCell)->Placement,
                openingMask:(String,P)->ExactVolume={ _,_->ExactVolume() }):Plan {
        if(columns.isEmpty() && plates.isEmpty()) return plan
        val retained=plan.placements.associateBy { it.pos }.toMutableMap();val additions=mutableListOf<Placement>();val removed=linkedSetOf<P>()
        for(column in columns) {
            val instance=column.instance;val spec=column.extension;val segment=source(instance.source)
            fun sourcePlacement(entry:SegmentCell):Placement {
                val value=placement(if(instance.materialRole.isEmpty()) entry else entry.copy(overrideRole=instance.materialRole))
                val material=column.materialOverride
                return if(material==null || value.block.isNotEmpty()) value else value.copy(material=material,surfaces=value.groups.associate { it.id to material })
            }
            require(spec.roof.node.isNotEmpty() && spec.roof.face in setOf("","underside")) { "Column ${spec.id}: choose a roof underside target" }
            require(instance.transform.yAxis==P(0,1,0)) { "Column ${spec.id}: roof extensions require an upright shaft" }
            require(spec.shaftMinY in 0 until spec.shaftMaxY && spec.shaftMaxY<=segment.height) { "Column ${spec.id}: shaft interval lies outside its source" }
            val shaft=if(spec.profileSource.isEmpty()) segment else source(spec.profileSource)
            val shaftMin=if(spec.profileSource.isEmpty()) spec.shaftMinY else 0
            val shaftHeight=if(spec.profileSource.isEmpty()) spec.shaftMaxY-spec.shaftMinY else shaft.height
            val shaftCells=shaft.cells.filter { it.cell.pos.y in shaftMin until shaftMin+shaftHeight }
            require(shaftCells.any { !it.cell.empty && it.cell.block!="minecraft:air" }) { "Column ${spec.id}: its shaft has no geometry" }
            require(shaft.length==segment.length && shaft.depth==segment.depth) { "Column ${spec.id}: its shaft profile must keep the source width and depth" }
            val contacts=roofs.contacts.filter { (spec.roof.node.isEmpty() || it.id==spec.roof.node) && (spec.roof.instance.isEmpty() || it.instance==spec.roof.instance) }
            require(contacts.size==1) { "Column ${spec.id}: select one concrete roof level and storey instance" }
            val contact=contacts.single()
            val horizontal=(shaftCells+segment.cells.filter { it.cell.pos.y>=spec.shaftMaxY }).filter { !it.cell.empty && it.cell.block!="minecraft:air" }
                .map { orient(sourcePlacement(it),it.cell.pos,instance.transform) }.groupBy { it.pos.copy(y=0) }
                .mapValues { (_,values) -> values.fold(ExactVolume()) { all,p -> all.union(projectVertical(volume(p))) } }
            require(horizontal.isNotEmpty() && horizontal.keys.all { it in contact.coverage }) { "Column ${spec.id}: its full shaft and capital must lie below the selected roof" }
            var top=Int.MIN_VALUE
            for((p,profile) in horizontal) {
                var found=false
                for(y in Math.floorDiv(contact.maximum-spec.gap,16)+1 downTo Math.floorDiv(contact.minimum-spec.gap,16)-1) {
                    val b=p.copy(y=y);val volume=below(contact,b,spec.gap).intersect(profile)
                    if(!volume.isEmpty()) {
                        val high=volume.cells.flatMap { it.vertices() }.maxOf { it.y.toDouble() }+y*16
                        top=maxOf(top,ceil(high).toInt());found=true;break
                    }
                }
                require(found) { "Column ${spec.id}: the selected roof has no underside above its profile" }
            }
            val originY=instance.transform.origin.y
            val topRow=Math.floorDiv(top+15,16)-originY
            val capHeight=segment.height-spec.shaftMaxY
            val capStart=topRow-capHeight
            require(capStart>=spec.shaftMinY && topRow in 1..256) { "Column ${spec.id}: the roof is too low for its base and capital" }

            // The annotated source is explicitly owned by this column. Remove exactly that volume,
            // retaining neighboring wall geometry in the same block and its material ownership.
            for(entry in segment.cells.filter { !it.cell.empty && it.cell.block!="minecraft:air" }) {
                val old=orient(sourcePlacement(entry),entry.cell.pos,instance.transform)
                val previous=retained[old.pos] ?: continue
                val cut=volume(old).minus(openingMask(instance.path,old.pos))
                val clipped=clip(previous,volume(previous).minus(cut),"Column ${spec.id}")
                if(clipped==null) { retained.remove(old.pos);removed+=old.pos } else retained[old.pos]=clipped
            }
            fun add(entry:SegmentCell,targetY:Int,roofClip:Boolean=true) {
                if(entry.cell.empty || entry.cell.block=="minecraft:air") return
                val moved=entry.copy(cell=entry.cell.copy(pos=entry.cell.pos.copy(y=targetY)))
                val p=orient(sourcePlacement(moved),moved.cell.pos,instance.transform)
                val allowed=(if(roofClip) below(contact,p.pos,spec.gap) else cube).minus(openingMask(instance.path,p.pos))
                clip(p,allowed,"Column ${spec.id}")?.let(additions::add)
            }
            segment.cells.filter { it.cell.pos.y<spec.shaftMinY }.forEach { add(it,it.cell.pos.y,false) }
            for(y in spec.shaftMinY until capStart) shaftCells.filter { Math.floorMod(y-spec.shaftMinY,shaftHeight)==it.cell.pos.y-shaftMin }.forEach { add(it,y) }
            segment.cells.filter { it.cell.pos.y>=spec.shaftMaxY }.forEach { add(it,it.cell.pos.y+topRow-segment.height) }
        }
        for(plate in plates) additions+=extendPlate(plate,walls).mapNotNull { p -> clip(p,cube.minus(openingMask("",p.pos)),"Plate ${plate.attachment.id}") }
        val grouped=(retained.values+additions).groupBy { it.pos }
        val resolver=Resolver()
        for((_,items) in grouped) {
            val native=items.filter { it.block.isNotEmpty() }
            val merged=if(native.isEmpty()) ExactPlacementUnion.merge(items) else {
                require(items.all { it.block==native.first().block }) { "An attachment intersects a normal Minecraft block at ${items.first().pos}" }
                native.first()
            }
            resolver.add(merged)
        }
        val occupied=grouped.keys
        (plan.clear+removed).filter { it !in occupied }.distinct().forEach(resolver::clear)
        return resolver.plan(plan.warnings)
    }

    /** Shifting clipped, block-local contact volumes requires both adjoining source blocks. */
    private fun below(contact:RoofContact,block:P,gap:Int):ExactVolume {
        val y16=block.y*16+gap;val y=Math.floorDiv(y16,16);val offset=Math.floorMod(y16,16)
        val first=contact.below(block.copy(y=y)).translated(P(0,-offset,0))
        val second=if(offset==0) ExactVolume() else contact.below(block.copy(y=y+1)).translated(P(0,16-offset,0))
        return first.union(second).intersect(cube)
    }

    private fun orient(p:Placement,localCell:P,transform:SemanticTransform):Placement {
        val target=transform.cell(localCell)
        if(p.block.isNotEmpty()) return p.copy(pos=target)
        val x=transform.shapeXAxis ?: transform.xAxis;val y=transform.shapeYAxis ?: transform.yAxis;val z=transform.shapeZAxis ?: transform.zAxis
        val offset=P(-16*(minOf(0,x.x)+minOf(0,y.x)+minOf(0,z.x)),-16*(minOf(0,x.y)+minOf(0,y.y)+minOf(0,z.y)),-16*(minOf(0,x.z)+minOf(0,y.z)+minOf(0,z.z)))
        fun transformed(volume:ExactVolume)=ExactVolume(volume.cells.map { cell -> ExactCell(cell.planes.map { plane ->
            val nx=x.x*plane.x+y.x*plane.y+z.x*plane.z;val ny=x.y*plane.x+y.y*plane.y+z.y*plane.z;val nz=x.z*plane.x+y.z*plane.y+z.z*plane.z
            ExactPlane(nx,ny,nz,plane.d+nx*offset.x+ny*offset.y+nz*offset.z).normalized()
        }) })
        return p.copy(pos=target,parts=p.parts.map { part -> exactPart(part,transformed(ExactGeometry.part(part))) })
    }
    private fun volume(p:Placement)=if(p.block.isNotEmpty()) cube else Geometry.exact(p.parts)
    /** Exact Fourier–Motzkin projection preserves thin and slanted shaft cross sections. */
    private fun projectVertical(volume:ExactVolume)=ExactVolume(volume.cells.map { cell ->
        val up=cell.planes.filter { it.y>0 };val down=cell.planes.filter { it.y<0 }
        val eliminated=up.flatMap { a -> down.mapNotNull { b ->
            val x=a.x*(-b.y)+b.x*a.y;val z=a.z*(-b.y)+b.z*a.y
            if(x==0L && z==0L) null else {
                val d=a.d*(-b.y)+b.d*a.y
                fun gcd(v:Long,w:Long):Long=if(w==0L) kotlin.math.abs(v) else gcd(w,v%w)
                val divisor=gcd(gcd(x,z),d).coerceAtLeast(1)
                ExactPlane(x/divisor,0,z/divisor,d/divisor).normalized()
            }
        } }
        ExactCell((cell.planes.filter { it.y==0L }+eliminated+cube.cells.single().planes).distinct())
    })
    private fun exactPart(part:Part,v:ExactVolume)=part.copy(turn=0,top=false,spin=0,tilt=0,offset=P(),cutMask="",exact=v,exactClip=null)
    private fun clip(p:Placement,allowed:ExactVolume,label:String):Placement? {
        val v=volume(p).intersect(allowed)
        if(v.isEmpty()) return null
        if(p.block.isNotEmpty()) {
            require(v.equivalent(cube)) { "$label: an exact cut requires Copycat geometry instead of a normal Minecraft block" }
            return p
        }
        val parts=p.parts.mapNotNull { part -> ExactGeometry.part(part).intersect(v).takeUnless { it.isEmpty() }?.let { exactPart(part,it) } }
        return p.copy(parts=parts).takeIf { parts.isNotEmpty() }
    }

    private fun extendPlate(plate:PlacedPlateAttachment,walls:List<WallAttachmentSurface>):List<Placement> {
        val spec=plate.attachment;val normal=RoomGeometry.directions.getOrNull(spec.edge) ?: error("Select one plate edge")
        val targets=walls.filter { wall -> wall.reference.node==spec.target.node && (spec.target.instance.isEmpty() || wall.reference.instance==spec.target.instance) && wall.reference.face==spec.target.face }
        require(targets.size==1) { "Plate ${spec.id}: select one concrete wall surface" }
        val wall=targets.single().placements
        require(wall.isNotEmpty() && plate.placements.isNotEmpty()) { "Plate ${spec.id}: the plate and target wall need geometry" }
        fun outward(p:P)=p.x*normal.x+p.z*normal.z
        fun lane(p:P)=if(normal.x==0) p.x else p.z
        val edge=plate.placements.groupBy { lane(it.pos) to it.pos.y }.values.map { row -> row.maxBy { outward(it.pos) } }
        val result=mutableListOf<Placement>()
        for(template in edge) {
            val candidates=wall.filter { lane(it.pos)==lane(template.pos) && it.pos.y==template.pos.y && outward(it.pos)>=outward(template.pos) }
            require(candidates.isNotEmpty()) { "Plate ${spec.id}: the chosen wall does not meet the entire edge at plate height" }
            val end=candidates.maxOf { outward(it.pos) }+1
            require(end-outward(template.pos)<=128) { "Plate ${spec.id}: wall contact exceeds 128 blocks" }
            val shadow=ExactVolume(candidates.flatMap { p -> volume(p).cells.map { cell ->
                // Extrusion away from the plate removes only far-facing planes. The retained
                // near faces follow actual profiles and define the exact first wall contact.
                val shift=P(p.pos.x*16-normal.x*spec.gap,p.pos.y*16,p.pos.z*16-normal.z*spec.gap)
                ExactCell(cell.planes.filter { it.x*normal.x+it.z*normal.z<=0 }.map { it.translated(shift) }+
                    ExactVolume.box(-65536,-65536,-65536,65536,65536,65536).cells.single().planes)
            } })
            var contact=false;var complete=false
            for(distance in 1..(end-outward(template.pos))) {
                val pos=template.pos+P(normal.x*distance,0,normal.z*distance)
                val obstruction=shadow.translated(P(-pos.x*16,-pos.y*16,-pos.z*16)).intersect(cube)
                val candidate=template.copy(pos=pos)
                if(!volume(candidate).intersect(obstruction).isEmpty()) contact=true
                clip(candidate,cube.minus(obstruction),"Plate ${spec.id}")?.let(result::add)
                if(obstruction.contains(volume(candidate))) { complete=true;break }
            }
            require(contact && complete) { "Plate ${spec.id}: target wall has an opening or no surface across the complete plate edge" }
        }
        return result
    }
}
