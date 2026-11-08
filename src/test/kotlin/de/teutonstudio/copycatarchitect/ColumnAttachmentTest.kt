package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class ColumnAttachmentTest {
    private val cube=ExactVolume.box(0,0,0,16,16,16)
    private val parts=listOf(Part("copycats:copycat_block"))
    private fun p(pos:P,material:String="minecraft:stone",volume:ExactVolume=cube,component:String="wall")=Placement(pos,parts.map { it.copy(exact=volume) },material,component=component)
    private fun plan(placements:List<Placement>)=Resolver().apply { placements.forEach { add(it) } }.plan(emptyList())
    private fun source(height:Int=3)=FixedSegment(1,height,(0 until height).map { SegmentCell(Cell(P(0,it,0))) })
    private val place:(SegmentCell)->Placement={ p(it.cell.pos) }
    private fun roof(height16:Int,slope:Int=0)=RoofSurfaceModel(listOf(RoofContact("roof","storey_0",setOf(P()),height16,height16+16*slope) { block ->
        ExactVolume.convex(listOf(ExactPlane(-slope.toLong(),1,0,(height16-block.y*16).toLong())))
    }))
    private fun request(spec:ColumnExtensionSpec=ColumnExtensionSpec("column",1,2,roof=SurfaceRef("roof","storey_0")))=
        PlacedColumn(SegmentFeatureInstance("room:0/column","column",P(1,3,1)),spec)

    @Test fun `shaft repeats to exact pitched roof with base and capital and keeps unrelated wall geometry`() {
        val column=source()
        val initial=plan((0..2).map { p(P(0,it,0)) }+p(P(1,0,0),"minecraft:bricks"))
        val result=SurfaceAttachments.resolve(initial,listOf(request()),emptyList(),roof(88,1),emptyList(),{ column },place)
        assertEquals("minecraft:bricks",result.placements.single { it.pos==P(1,0,0) }.material)
        assertEquals((0..6).toList(),result.placements.filter { it.pos.x==0 }.map { it.pos.y })
        for(piece in result.placements.filter { it.pos.x==0 }) {
            assertTrue(Geometry.exact(piece.parts).intersect(roof(88,1).contacts.single().below(piece.pos)).equivalent(Geometry.exact(piece.parts)))
        }
        val top=result.placements.single { it.pos==P(0,6,0) }
        assertFalse(Geometry.exact(top.parts).equivalent(cube))
    }

    @Test fun `fractional roof gap does not leave a gap at every block boundary`() {
        val initial=plan((0..2).map { p(P(0,it,0)) })
        val result=SurfaceAttachments.resolve(initial,listOf(request(request().extension.copy(gap=3))),emptyList(),roof(96),emptyList(),{ source() },place)
        assertEquals((0..5).toList(),result.placements.map { it.pos.y })
        assertTrue(Geometry.exact(result.placements.first { it.pos.y==3 }.parts).equivalent(cube))
        assertTrue(Geometry.exact(result.placements.first { it.pos.y==5 }.parts).equivalent(ExactVolume.box(0,0,0,16,13,16)))
    }

    @Test fun `base and capital keep their materials while only the marked shaft repeats`() {
        val materials=listOf("minecraft:andesite","minecraft:granite","minecraft:diorite")
        val source=FixedSegment(1,3,(0..2).map { SegmentCell(Cell(P(0,it,0),role=materials[it])) })
        val callback:(SegmentCell)->Placement={ p(it.cell.pos,it.cell.role) }
        val initial=plan(source.cells.map(callback))
        val result=SurfaceAttachments.resolve(initial,listOf(request()),emptyList(),roof(96),emptyList(),{ source },callback)
        assertEquals(materials[0],result.placements.single { it.pos.y==0 }.material)
        assertTrue(result.placements.filter { it.pos.y in 1..4 }.all { it.material==materials[1] })
        assertEquals(materials[2],result.placements.single { it.pos.y==5 }.material)
        assertEquals(1,result.placements.count { it.material==materials[2] })
    }

    @Test fun `a wider base outside roof coverage is preserved while the narrower shaft fits the roof`() {
        val source=FixedSegment(3,3,(0..2).map { SegmentCell(Cell(P(it,0,0))) }+listOf(SegmentCell(Cell(P(1,1,0))),SegmentCell(Cell(P(1,2,0)))))
        val contact=RoofContact("roof","storey_0",setOf(P(1,0,0)),96,96) { block -> ExactVolume.convex(listOf(ExactPlane(0,1,0,(96-block.y*16).toLong()))) }
        val instance=SegmentFeatureInstance("room:0/column","column",P(3,3,1))
        val result=SurfaceAttachments.resolve(plan(source.cells.map(place)),listOf(PlacedColumn(instance,request().extension)),emptyList(),RoofSurfaceModel(listOf(contact)),emptyList(),{ source },place)
        assertTrue(result.placements.any { it.pos==P(0,0,0) })
        assertTrue(result.placements.any { it.pos==P(2,0,0) })
        assertTrue(result.placements.any { it.pos==P(1,5,0) })
        assertTrue(result.placements.none { it.pos.x!=1 && it.pos.y>0 })
    }

    @Test fun `capital placement follows a thin actual profile instead of the empty part of its block`() {
        val source=FixedSegment(1,3,(0..2).map { SegmentCell(Cell(P(0,it,0),role=if(it==2) "minecraft:diorite" else "minecraft:granite")) })
        val callback:(SegmentCell)->Placement={ p(it.cell.pos,it.cell.role,ExactVolume.box(0,0,0,2,16,16)) }
        val result=SurfaceAttachments.resolve(plan(source.cells.map(callback)),listOf(request()),emptyList(),roof(80,4),emptyList(),{ source },callback)
        val capital=result.placements.filter { it.material=="minecraft:diorite" }
        assertTrue(capital.isNotEmpty())
        assertTrue(capital.all { it.pos.y==5 })
        assertTrue(result.placements.all { it.pos.y<=5 })
    }

    @Test fun `material overrides apply to repeated shafts and relocated capitals`() {
        val desired="minecraft:oak_planks"
        val column=request().copy(instance=request().instance.copy(materialRole="frame"),materialOverride=desired)
        val callback:(SegmentCell)->Placement={ entry -> p(entry.cell.pos,if(entry.overrideRole=="frame") "minecraft:gold_block" else "minecraft:stone") }
        val result=SurfaceAttachments.resolve(plan((0..2).map { p(P(0,it,0),desired) }),listOf(column),emptyList(),roof(96),emptyList(),{ source() },callback)
        assertTrue(result.placements.all { it.material==desired && it.surfaces.values.all { material -> material==desired } })
        assertEquals(setOf(desired),result.materials.keys)
    }

    @Test fun `authoritative window masks survive column regeneration and retain existing glass`() {
        val opening=ExactVolume.box(4,0,0,12,16,16)
        val glass=ExactVolume.box(4,0,7,12,16,9)
        val middle=ExactPlacementUnion.merge(listOf(p(P(0,1,0),volume=cube.minus(opening)),p(P(0,1,0),"minecraft:glass",glass)))
        val initial=plan(listOf(p(P()),middle,p(P(0,2,0))))
        val result=SurfaceAttachments.resolve(initial,listOf(request()),emptyList(),roof(96),emptyList(),{ source() },place,
            openingMask={ _,block -> if(block==P(0,1,0)) opening else ExactVolume() })
        val actual=result.placements.single { it.pos==P(0,1,0) }
        val materials=Geometry.resolve(actual.parts,actual.groups).exactMaterials(actual.material,actual.surfaces)
        assertTrue(materials.getValue("minecraft:stone").intersect(opening).isEmpty())
        assertTrue(materials.getValue("minecraft:glass").equivalent(glass))
        assertTrue(Geometry.exact(actual.parts).intersect(opening).equivalent(glass))
    }

    @Test fun `real roof contacts remain exact after room rotation and world translation`() {
        val roofNode=BuildingNode("roof",BuildingKind.ROOF,assembly=true,layout="roof_layout",role="roof")
        val graph=BuildingGraph(nodes=listOf(roofNode,BuildingNode("roof_layout",BuildingKind.ROOF_LAYOUT,inputs=listOf("roof_plane")),
            BuildingNode("roof_plane",BuildingKind.ROOF_LEVEL,roofLevel=RoofLevelConfig(form=RoofForm.PENT,slope=RoofSlope(1,2),overhang16=0))))
        val footprint=(0..2).flatMap { x -> (0..2).map { z -> P(x,0,z) } }.toSet()
        val contacts=mutableListOf<RoofContact>()
        val actualRoof=Roofs.compile(graph,roofNode,listOf(RoofSupport("storey_0",0,5,footprint)),Building().palette,
            { error("No custom template expected") },contacts=contacts)
        val offset=P(12,7,-4);val moved=contacts.single().moved(offset)
        assertEquals(contacts.single().minimum+112,moved.minimum)
        val transform=SemanticTransform.rotation(RotationAxis.Y,1,P(1,3,1)).moved(offset+P(1,0,1))
        val instance=SegmentFeatureInstance("room:0/column","column",P(1,3,1),transform=transform)
        val spec=ColumnExtensionSpec("column",1,2,roof=SurfaceRef("roof_plane","storey_0"))
        val initial=plan((0..2).map { p(transform.cell(P(0,it,0))) })
        val result=SurfaceAttachments.resolve(initial,listOf(PlacedColumn(instance,spec)),emptyList(),RoofSurfaceModel(listOf(moved)),emptyList(),{ source() },place)
        val physicalRoof=actualRoof.placements.associate { (it.pos+offset) to Geometry.exact(it.parts) }
        assertTrue(result.placements.any { it.pos.y>offset.y+4 })
        for(piece in result.placements) {
            val geometry=Geometry.exact(piece.parts)
            assertTrue(geometry.intersect(moved.below(piece.pos)).equivalent(geometry))
            assertTrue(geometry.intersect(physicalRoof[piece.pos] ?: ExactVolume()).isEmpty(),"Column penetrates the physical roof at ${piece.pos}")
        }
    }

    @Test fun `ambiguous roof instances and exact cuts through native blocks are rejected`() {
        val model=roof(88)
        assertFailsWith<IllegalArgumentException> {
            SurfaceAttachments.resolve(plan((0..2).map { p(P(0,it,0)) }),listOf(request()),emptyList(),RoofSurfaceModel(model.contacts+model.contacts),emptyList(),{ source() },place)
        }
        assertFailsWith<IllegalArgumentException> {
            SurfaceAttachments.resolve(plan((0..2).map { Placement(P(0,it,0),block="minecraft:stone") }),listOf(request()),emptyList(),model,emptyList(),{ source() },{ Placement(it.cell.pos,block="minecraft:stone") })
        }
    }

    @Test fun `plate meets actual partial wall face without changing its original footprint`() {
        val original=p(P(),component="ceiling")
        val wall=p(P(3,0,0),volume=ExactVolume.box(5,0,0,16,16,16))
        val request=PlacedPlateAttachment("ceiling","porch:0",AttachmentSpec("to_wall",1,SurfaceRef("hall","hall:0","west")),listOf(original))
        val result=SurfaceAttachments.resolve(plan(listOf(original,wall)),emptyList(),listOf(request),RoofSurfaceModel(emptyList()),listOf(WallAttachmentSurface(request.attachment.target,listOf(wall))),{ source() },place)
        assertEquals(setOf(P(),P(1,0,0),P(2,0,0),P(3,0,0)),result.placements.map { it.pos }.toSet())
        val joined=result.placements.single { it.pos==P(3,0,0) }
        assertTrue(Geometry.exact(joined.parts).equivalent(cube))
        assertEquals(original,result.placements.single { it.pos==P() })
        val aggregate=request.copy(attachment=request.attachment.copy(target=request.attachment.target.copy(face="")))
        val combined=SurfaceAttachments.resolve(plan(listOf(original,wall)),emptyList(),listOf(aggregate),RoofSurfaceModel(emptyList()),
            listOf(WallAttachmentSurface(aggregate.attachment.target,listOf(wall)),WallAttachmentSurface(request.attachment.target,listOf(wall))),{ source() },place)
        assertEquals(result,combined)
        val masked=SurfaceAttachments.resolve(plan(listOf(original,wall)),emptyList(),listOf(request),RoofSurfaceModel(emptyList()),listOf(WallAttachmentSurface(request.attachment.target,listOf(wall))),{ source() },place,
            openingMask={ _,block -> if(block==P(2,0,0)) ExactVolume.box(0,0,0,16,8,16) else ExactVolume() })
        assertTrue(Geometry.exact(masked.placements.single { it.pos==P(2,0,0) }.parts).equivalent(ExactVolume.box(0,8,0,16,16,16)))
    }

    @Test fun `wall holes at plate height are reported rather than leaving a floating extension`() {
        val original=p(P(),component="ceiling")
        val wall=p(P(3,0,0),volume=ExactVolume.box(5,0,0,16,8,16))
        val request=PlacedPlateAttachment("ceiling","porch:0",AttachmentSpec("to_wall",1,SurfaceRef("hall")),listOf(original))
        assertFailsWith<IllegalArgumentException> {
            SurfaceAttachments.resolve(plan(listOf(original,wall)),emptyList(),listOf(request),RoofSurfaceModel(emptyList()),listOf(WallAttachmentSurface(request.attachment.target,listOf(wall))),{ source() },place)
        }
    }
}
