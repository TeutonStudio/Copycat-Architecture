package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class SemanticOpeningTest {
    private fun point(x:Long,y:Long,z:Long)=ExactPoint(ExactNumber.of(x),ExactNumber.of(y),ExactNumber.of(z))
    @Test fun `free silhouette is scaled exactly and extruded through wall depth`() {
        val triangular=ExactVolume(listOf(ExactCell(ExactVolume.box(0,0,0,16,16,16).cells.single().planes+ExactPlane(x=1,y=1,d=16))))
        val opening=ResolvedRoomOpening("a","window",SemanticTransform(),OpeningSpec("a",P(),P(2,2,1),maskSource="triangle"))
        val cut=SemanticStoreys.openingVolume(opening,{Placement(parts=listOf(Part("copycats:copycat_block",exact=triangular)))},-64,96)
        assertTrue(cut.containsPoint(point(8,8,80)))
        assertFalse(cut.containsPoint(point(28,28,8)))
        assertTrue(cut.containsPoint(point(8,8,-48)))
    }
    @Test fun `position only mirror moves mask cells while retaining the asymmetric shape`() {
        val triangular=ExactVolume(listOf(ExactCell(ExactVolume.box(0,0,0,16,16,16).cells.single().planes+ExactPlane(x=1,y=1,d=16))))
        val frame=SemanticTransform.reflection(RotationAxis.X,P(3,1,1),positionsOnly=true)
        val opening=ResolvedRoomOpening("a","window",frame,OpeningSpec("a",P(),P(1,1,1),maskSource="triangle"))
        val cut=SemanticStoreys.openingVolume(opening,{Placement(parts=listOf(Part("copycats:copycat_block",exact=triangular)))},-16,32)
        assertTrue(cut.containsPoint(point(34,12,8)))
        assertFalse(cut.containsPoint(point(46,12,8)))
    }
    @Test fun `partial Copycat cut keeps a frame in an open voxel and retains receipts`() {
        val value=Placement(parts=listOf(Part("copycats:copycat_block",recipeSource="window_frame")))
        val cut=ExactVolume.box(4,4,-16,12,12,32)
        val result=assertNotNull(SemanticStoreys.cut(value,cut))
        assertEquals("window_frame",result.parts.single().recipeSource)
        assertTrue(Geometry.exact(result.parts).intersect(cut).isEmpty())
        assertFalse(Geometry.exact(result.parts).isEmpty())
    }
    @Test fun `partial Minecraft block cut has a useful error while full cut is legal`() {
        val value=Placement(block="minecraft:stone")
        val error=assertFailsWith<IllegalArgumentException> {SemanticStoreys.cut(value,ExactVolume.box(4,4,0,12,12,16))}
        assertTrue(error.message.orEmpty().contains("Copycat"))
        assertNull(SemanticStoreys.cut(value,ExactVolume.box(0,0,0,16,16,16)))
    }
}
