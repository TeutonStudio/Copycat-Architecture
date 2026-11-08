package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class RoofSeamTest {
    @Test fun `neighbor slopes crossing only at the shared edge midpoint are rejected`() {
        val leftArea=BuildingNode("left_area",BuildingKind.AREA,coordinates=listOf(P()))
        val rightArea=BuildingNode("right_area",BuildingKind.AREA,coordinates=listOf(P(1,0,0)))
        val left=BuildingNode("left",BuildingKind.ROOF_LEVEL,roofLevel=RoofLevelConfig(area=leftArea.id,form=RoofForm.PENT,axis=1,knee16=8,overhang16=0))
        val right=BuildingNode("right",BuildingKind.ROOF_LEVEL,roofLevel=RoofLevelConfig(area=rightArea.id,form=RoofForm.PENT,axis=1,slope=RoofSlope(2,1),overhang16=0))
        val layout=BuildingNode("layout",BuildingKind.ROOF_LAYOUT,inputs=listOf(left.id,right.id))
        val roof=BuildingNode("roof",BuildingKind.ROOF,assembly=true,layout=layout.id)
        val graph=BuildingGraph(nodes=listOf(leftArea,rightArea,left,right,layout,roof))
        val support=RoofSupport("storey_0",0,5,setOf(P(),P(1,0,0)))
        // At z=8 both finished profiles are 98 pixels high; their endpoints differ by 8 pixels.
        val failure=assertFailsWith<IllegalArgumentException> {
            Roofs.compile(graph,roof,listOf(support),Building().palette,{ error("Unexpected template") })
        }
        assertContains(failure.message.orEmpty(),"Roof levels end at different heights")
    }
    @Test fun `minimum roof height rounds up to an editable sixteenth of a block`() {
        val level=BuildingNode("level",BuildingKind.ROOF_LEVEL,roofLevel=RoofLevelConfig(mode=RoofMode.HEIGHT,height16=7,slope=RoofSlope(1,3),overhang16=0))
        val layout=BuildingNode("layout",BuildingKind.ROOF_LAYOUT,inputs=listOf(level.id))
        val roof=BuildingNode("roof",BuildingKind.ROOF,assembly=true,layout=layout.id)
        val graph=BuildingGraph(nodes=listOf(level,layout,roof))
        val footprint=buildSet { for(x in 0..1) for(z in 0..1) add(P(x,0,z)) }
        val failure=assertFailsWith<IllegalArgumentException> {
            Roofs.compile(graph,roof,listOf(RoofSupport("storey_0",0,5,footprint)),Building().palette,{ error("Unexpected template") })
        }
        assertContains(failure.message.orEmpty(),"at least 0.5 blocks")
    }

    @Test fun `pent roof axes match the four editor rise directions`() {
        for(axis in 0..3) {
            val level=BuildingNode("level",BuildingKind.ROOF_LEVEL,roofLevel=RoofLevelConfig(form=RoofForm.PENT,axis=axis,overhang16=0))
            val layout=BuildingNode("layout",BuildingKind.ROOF_LAYOUT,inputs=listOf(level.id))
            val roof=BuildingNode("roof",BuildingKind.ROOF,assembly=true,layout=layout.id)
            val graph=BuildingGraph(nodes=listOf(level,layout,roof))
            val plan=Roofs.compile(graph,roof,listOf(RoofSupport("storey_0",0,5,setOf(P()))),Building().palette,{ error("Unexpected template") })
            val vertices=plan.placements.flatMap { placement->Geometry.exact(placement.parts).cells.flatMap { it.vertices() }.map {
                Triple(placement.pos.x*16+it.x.toDouble(),placement.pos.y*16+it.y.toDouble(),placement.pos.z*16+it.z.toDouble())
            } }
            assertEquals(98.0,vertices.maxOf { it.second },1e-8,"Axis $axis")
            val top=vertices.filter { kotlin.math.abs(it.second-98.0)<1e-8 }
            assertTrue(top.isNotEmpty())
            val expected=if(axis<2) 16.0 else 0.0
            top.forEach { assertEquals(expected,if(axis%2==0) it.first else it.third,1e-8,"Axis $axis") }
        }
    }

}
