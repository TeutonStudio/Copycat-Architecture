package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class RoofPeakTest {
    private val forms=mapOf("full@1" to Form(id="full",parts=listOf(Part("copycats:copycat_block"))),
        "peak@1" to Form(id="peak",parts=listOf(Part("create:copycat_panel"))))
    private fun graph(width:Int=3,length:Int=3,ridge:Int=0,overhang:Int=0,thickness:Int=1):BuildingGraph {
        val g=BuildingGraph.starter()
        return g.copy(nodes=g.nodes+BuildingNode("peak",BuildingKind.COPYCAT,form="peak@1",role="frame"))
            .replace(g.nodes.first { it.id=="room" }.copy(rectangles=listOf(Rect(width=width,length=length)),walls=emptyMap()))
            .replace(g.nodes.first { it.id=="roof" }.copy(roofType="gable",ridge=ridge,overhang=overhang,thickness=thickness,roofPeak="peak"))
    }
    private fun preview(g:BuildingGraph)=BuildingGraphCompiler.Evaluation(g,Building().palette,forms,emptyMap()).preview("roof")
    private fun withoutPeak(g:BuildingGraph)=g.replace(g.nodes.first { it.id=="roof" }.copy(roofPeak=null))

    @Test fun `odd spans use peak geometry only in the middle and orient it along both ridge axes`() {
        for(axis in 0..1) for(overhang in 0..1) for(thickness in 1..2) {
            val g=graph(ridge=axis,overhang=overhang,thickness=thickness)
            val old=preview(withoutPeak(g)).placements.associateBy { it.pos }
            val current=preview(g).placements.associateBy { it.pos }
            assertEquals(old.keys,current.keys)
            val changed=current.filter { (p,v)->old[p]!=v }.values
            assertEquals((5+2*overhang)*thickness,changed.size)
            assertTrue(changed.all { if(axis==0) it.pos.x==1 else it.pos.z==1 })
            val expected=Geometry.rotate(Geometry.validate(forms.getValue("peak@1")).parts,axis)
            assertTrue(changed.all { it.parts==expected && it.material==Building().palette.getValue("frame") })
            val compiled=BuildingGraphCompiler.Evaluation(g,Building().palette,forms,emptyMap()).building()
            val base=compiled.storeys.last().maximum
            assertEquals(current.values.filter { it.component=="roof" },compiled.plan.placements.filter { it.component=="roof" }.map { it.copy(pos=it.pos+P(0,-base,0)) })
        }
    }
    @Test fun `even spans and flat roofs keep their existing geometry`() {
        for(axis in 0..1) {
            val even=graph(width=if(axis==0) 4 else 3,length=if(axis==1) 4 else 3,ridge=axis)
            assertEquals(preview(withoutPeak(even)),preview(even))
            val g=graph(ridge=axis);val flat=g.replace(g.nodes.first { it.id=="roof" }.copy(roofType="flat"))
            assertEquals(preview(withoutPeak(flat)),preview(flat))
        }
    }
    @Test fun `peak connections are typed serializable removable and remapped on duplication`() {
        val g=graph();BuildingGraphCompiler.structure(g)
        assertEquals(g,Gson().fromJson(Gson().toJson(g),BuildingGraph::class.java))
        assertFails { g.connect("roof","roofPeak","wall") }
        assertFails { BuildingGraphCompiler.structure(g.replace(g.nodes.first { it.id=="roof" }.copy(roofPeak="missing"))) }
        assertNull(g.connect("roof","roofPeak","").nodes.first { it.id=="roof" }.roofPeak)
        assertNull(g.remove("peak").nodes.first { it.id=="roof" }.roofPeak)
        val grouped=GraphGroups.create(g,setOf("roof","peak"))
        val copy=GraphGroups.instantiate(grouped,grouped.nodeGroups.single().id)
        val duplicated=copy.nodes.first { it.kind==BuildingKind.ROOF && it.id!="roof" }
        assertNotEquals("peak",duplicated.roofPeak)
        assertEquals(BuildingKind.COPYCAT,copy.nodes.first { it.id==duplicated.roofPeak }.kind)
    }
}
