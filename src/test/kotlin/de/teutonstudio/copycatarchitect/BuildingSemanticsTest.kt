package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class BuildingSemanticsTest {
    private val features=SegmentFeatures(
        openings=listOf(OpeningSpec("window",P(1,1,0),P(1,2,1),maskSource="mask",insetSource="frame")),
        extensions=listOf(ColumnExtensionSpec("column",1,3,roof=SurfaceRef("roof","storey:0/room:1","underside")))
    )
    private fun segment()=BuildingSemantics.annotate(FixedSegment(3,4,listOf(SegmentCell(Cell(P(1,2,0),form="full@1"))),depth=2),"wall",features)

    @Test fun `semantic identity and exact coordinates survive composition and negative normalization`() {
        val source=segment()
        val combined=SegmentGeometry.union(listOf(source to SegmentTransform(P(-3,2,0)),source to SegmentTransform(P(2,2,0),quarterTurns=1)))
        assertEquals(2,combined.metadata.instances.size)
        assertEquals(setOf("union:0/wall","union:1/wall"),combined.metadata.instances.map { it.path }.toSet())
        assertTrue(combined.metadata.instances.all { it.features==features && it.source=="wall" && it.dimensions==P(3,4,2) })
        assertEquals(P(1,2,0),combined.metadata.instances.first().transform.cell(P(1,2,0)))
        assertEquals(P(6,2,1),combined.metadata.instances.last().transform.cell(P(1,2,0)))
        assertEquals(source,SegmentGeometry.rotated(SegmentGeometry.rotated(source,RotationAxis.Y,1),RotationAxis.Y,3))
    }

    @Test fun `all axis rotations and reflections transform semantic cells exactly like geometry`() {
        for(axis in listOf(RotationAxis.X,RotationAxis.Y,RotationAxis.Z)) for(q in -4..4) {
            val source=segment();val transformed=SegmentGeometry.rotated(source,axis,q)
            assertEquals(transformed.cells.single().cell.pos,transformed.metadata.instances.single().transform.cell(source.cells.single().cell.pos))
            transformed.metadata.instances.single().transform.validate()
            val mirrored=SegmentGeometry.mirror(transformed,axis)
            assertEquals(mirrored.cells.single().cell.pos,mirrored.metadata.instances.single().transform.cell(source.cells.single().cell.pos))
        }
    }

    @Test fun `position-only mirrors retain asymmetric within-cell masks`() {
        val source=segment()
        val geometry=SegmentGeometry.mirror(source,RotationAxis.X)
        val positions=SegmentGeometry.mirror(source,RotationAxis.X,positionsOnly=true)
        val cell=P(1,2,0);val local=P(3,5,7)
        assertEquals(P(29,37,7),geometry.metadata.instances.single().transform.microCellPoint(cell,local))
        assertEquals(P(19,37,7),positions.metadata.instances.single().transform.microCellPoint(cell,local))
        assertEquals(source.metadata,SegmentGeometry.mirror(positions,RotationAxis.X,positionsOnly=true).metadata)
    }

    @Test fun `metadata reference registry separates evaluation sources and late surface bindings`() {
        val node=BuildingNode("wall",features=features,attachments=listOf(AttachmentSpec("canopy",1,SurfaceRef("room","room:1","east"))))
        assertEquals(setOf("mask","frame","roof","room"),BuildingSemantics.references(node).values.toSet())
        assertEquals(setOf("mask","frame"),BuildingSemantics.structuralReferences(node).values.toSet())
        val moved=BuildingSemantics.remapReferences(node) { _,ref -> if(ref.isEmpty()) ref else "copy_$ref" }
        assertEquals("copy_roof",moved.features!!.extensions.single().roof.node)
        assertEquals("copy_room",moved.attachments!!.single().target.node)
        assertEquals("storey:0/room:1",moved.features.extensions.single().roof.instance)
        assertEquals(node,Gson().fromJson(Gson().toJson(node),BuildingNode::class.java))
    }

    @Test fun `grouped parents override nested feature material roles without losing source identity`() {
        val inner=BuildingSemantics.annotate(segment(),"inner",null,"frame")
        val outer=BuildingSemantics.annotate(SegmentGeometry.rotated(inner,RotationAxis.Y,1),"pattern",null,"roof")
        val separated=BuildingSemantics.annotate(outer,"separated",null)
        assertEquals("wall",outer.metadata.instances.single().source)
        assertEquals("roof",outer.metadata.instances.single().materialRole)
        assertEquals(outer,separated)
    }

    @Test fun `group instances remap internal semantic sources and keep external targets local`() {
        val nodes=listOf(BuildingNode("mask",BuildingKind.COPYCAT),BuildingNode("wall",features=features),BuildingNode("frame"),BuildingNode("roof",BuildingKind.ROOF))
        val grouped=GraphGroups.create(BuildingGraph(schema=3,nodes=nodes),setOf("mask","wall","frame"),"Fassade")
        val instanced=GraphGroups.instantiate(grouped,grouped.nodeGroups.single().id)
        val copy=instanced.nodeGroups.last();val copied=instanced.nodes.first { it.id==copy.nodes[1] }
        assertEquals(copy.nodes[0],copied.features!!.openings.single().maskSource)
        assertEquals(copy.nodes[2],copied.features.openings.single().insetSource)
        assertEquals("roof",copied.features.extensions.single().roof.node)
        val edited=instanced.replace(instanced.nodes.first { it.id=="wall" }.copy(features=features.copy(openings=listOf(features.openings.single().copy(position=P(0,1,0))))))
        assertEquals(P(0,1,0),edited.nodes.first { it.id==copied.id }.features!!.openings.single().position)
        assertEquals(copy.nodes[0],edited.nodes.first { it.id==copied.id }.features!!.openings.single().maskSource)
    }
}
