package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.client.BuildingGridProjection
import de.teutonstudio.copycatarchitect.domain.P
import org.junit.jupiter.api.Test
import kotlin.test.*

class BuildingGridProjectionTest {
    @Test fun `small and offset raster bounds stay centered in wide and tall viewports`() {
        for((width,height) in listOf(190 to 116,280 to 152,190 to 36)) {
            for(origin in listOf(P(),P(-17,0,12),P(31,0,-9))) {
                val cells=(0..2).flatMap { x -> (0..3).map { z -> origin+P(x,0,z) } }.toSet()
                val grid=BuildingGridProjection.fit(12,30,width,height,cells)
                assertEquals(12+width/2.0,(grid.px(origin.x)+grid.px(origin.x+3))/2,0.00001)
                assertEquals(30+height/2.0,(grid.py(origin.z)+grid.py(origin.z+4))/2,0.00001)
                assertTrue(grid.px(origin.x)>grid.left && grid.px(origin.x+3)<grid.left+width)
                assertTrue(grid.py(origin.z)>grid.top && grid.py(origin.z+4)<grid.top+height)
            }
        }
    }

    @Test fun `grid cells cover the complete viewport including empty margins`() {
        val grid=BuildingGridProjection.fit(10,20,280,152,setOf(P(),P(2,0,3)))
        for(x in 10 until 290) for(y in 20 until 172) {
            val picked=grid.cell(x+0.5,y+0.5)
            assertTrue(picked.x in grid.columns && picked.z in grid.rows)
            assertTrue(grid.px(picked.x)<=x+0.5 && grid.px(picked.x+1)>x+0.5)
            assertTrue(grid.py(picked.z)<=y+0.5 && grid.py(picked.z+1)>y+0.5)
        }
        assertTrue(grid.columns.first<grid.minX)
        assertTrue(grid.columns.last>3)
        assertTrue(grid.contains(10.0,20.0))
        assertFalse(grid.contains(290.0,20.0))
        assertFalse(grid.contains(10.0,172.0))
    }

    @Test fun `large sparse and negative coordinates use the same fit and click transform`() {
        val cells=setOf(P(-32,0,-32),P(63,0,63))
        val grid=BuildingGridProjection.fit(200,100,190,80,cells)
        assertTrue(grid.step<1)
        for(cell in cells) assertEquals(cell,grid.cell(grid.px(cell.x)+grid.step/2,grid.py(cell.z)+grid.step/2))
        assertEquals(295.0,(grid.px(-32)+grid.px(64))/2,0.00001)
        assertEquals(140.0,(grid.py(-32)+grid.py(64))/2,0.00001)
    }
}
