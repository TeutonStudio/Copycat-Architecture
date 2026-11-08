package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class RoofTemplatePlacementTest {
    private val slope=RoofSlope(1,2)
    private val upper=ExactPlane(-1,2,0,11)
    private val target=ExactVolume.convex(listOf(upper,upper.opposite().copy(d= -upper.d+4)))
    private fun member(slice:Int):Placement {
        val plane=ExactPlane(0,2,-1,-32L*slice)
        val volume=ExactVolume.convex(listOf(plane,plane.opposite().copy(d= -plane.d+4)))
        return Placement(parts=listOf(Part(RoofPartParameters.SOURCE,exact=volume)),material="minecraft:stone")
    }

    @Test fun `period spill members rotate and translate by a rational height without stretching`() {
        val templates=(-1..0).map { slice -> member(slice) to P(0,slice*16,0) }
        val fitted=RoofTemplatePlacement.fitMany(templates,upper,slope,0,0,target)
        assertEquals(3,RoofTemplatePlacement.orientation(upper,slope))
        assertTrue(Geometry.exact(fitted.parts).equivalent(target))
        assertTrue(fitted.parts.all { it.turn==0 && it.offset==P() && it.exact!=null })
        val json=Gson().toJson(fitted)
        val loaded=Gson().fromJson(json,Placement::class.java)
        assertTrue(Geometry.exact(loaded.parts).equivalent(target))
        assertTrue(Geometry.exact(member(0).parts).surfaces().any { it.plane==ExactPlane(0,2,-1,0) })
    }

    @Test fun `a missing custom spill member is rejected instead of filling or stretching it`() {
        assertContains(assertFails { RoofTemplatePlacement.fit(member(0),upper,slope,0,0,target) }.message.orEmpty(),"hole")
        assertFails { RoofTemplatePlacement.fit(member(0),ExactPlane(-1,3,0,11),slope,0,0,target) }
    }
}
