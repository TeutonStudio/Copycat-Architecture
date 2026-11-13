package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class RoofEaveTest {
    private val forms=mapOf("full@1" to Form(id="full",parts=listOf(Part("copycats:copycat_block"))),
        "eave@1" to Form(id="eave",parts=listOf(Part("create:copycat_panel"))))
    private fun graph(width:Int=3,length:Int=4,ridge:Int=0,overhang:Int=0,thickness:Int=1):BuildingGraph {
        val g=BuildingGraph.starter()
        return g.copy(nodes=g.nodes+BuildingNode("eave",BuildingKind.COPYCAT,form="eave@1",role="frame"))
            .replace(g.nodes.first { it.id=="room" }.copy(rectangles=listOf(Rect(width=width,length=length)),walls=emptyMap()))
            .replace(g.nodes.first { it.id=="roof" }.copy(roofType="gable",ridge=ridge,overhang=overhang,thickness=thickness,roofEave="eave"))
    }
    private fun preview(g:BuildingGraph)=BuildingGraphCompiler.Evaluation(g,Building().palette,forms,emptyMap()).preview("roof")
    private fun withoutEave(g:BuildingGraph)=g.replace(g.nodes.first { it.id=="roof" }.copy(roofEave=null))

    @Test fun `eave source replaces only the two low rows parallel to either ridge axis`() {
        for(axis in 0..1) for(width in 3..4) for(overhang in 0..1) for(thickness in 1..2) {
            val g=graph(width=width,ridge=axis,overhang=overhang,thickness=thickness)
            val old=preview(withoutEave(g)).placements.associateBy { it.pos }
            val current=preview(g).placements.associateBy { it.pos }
            assertEquals(old.keys,current.keys)
            val changed=current.filter { (p,v)->old[p]!=v }.values
            val along=if(axis==0) 4 else width
            assertEquals(2*(along+2+2*overhang)*thickness,changed.size)
            val min=old.keys.minOf { if(axis==0) it.x else it.z };val max=old.keys.maxOf { if(axis==0) it.x else it.z }
            changed.forEach { p ->
                val side=if(axis==0) p.pos.x else p.pos.z
                assertTrue(side==min || side==max)
                val turn=if(axis==0) (if(side==min) 3 else 1) else (if(side==min) 0 else 2)
                assertEquals(Geometry.rotate(Geometry.validate(forms.getValue("eave@1")).parts,turn),p.parts)
                assertEquals(Building().palette.getValue("frame"),p.material)
            }
        }
    }
    @Test fun `eave and peak are independent and flat roofs retain dormant eave sources`() {
        val g=graph();val roof=g.nodes.first { it.id=="roof" }
        val both=g.replace(roof.copy(roofPeak="eave"))
        assertEquals(preview(g).placements.filter { it.pos.y==0 },preview(both).placements.filter { it.pos.y==0 })
        val flat=g.replace(roof.copy(roofType="flat"))
        assertEquals(preview(withoutEave(flat)),preview(flat))
        assertFalse(BuildingPorts.of(flat.nodes.first { it.id=="roof" }).any { it.key=="roofEave" })
        assertTrue(BuildingPorts.of(roof).any { it.key=="roofEave" && BuildingKind.COPYCAT in it.accepts })
        assertFails { BuildingGraphCompiler.structure(flat.replace(flat.nodes.first { it.id=="roof" }.copy(roofEave="wall"))) }
        val positions=flat.nodes.map { DiagramPosition(it.id,it.x+10,it.y) }
        val wires=flat.nodes.flatMap { n->BuildingPorts.of(n,flat).filter { it.source.isNotEmpty() }.map { DiagramConnection(it.source,n.id,it.key) } }
        assertEquals("eave",DiagramEdits.building(flat,positions,wires).nodes.first { it.id=="roof" }.roofEave)
        assertNull(DiagramEdits.building(flat,positions.filter { it.id!="eave" },wires.filter { it.source!="eave" }).nodes.first { it.id=="roof" }.roofEave)
    }
    @Test fun `eave references roundtrip connect disconnect validate and remap with grouped roofs`() {
        val g=graph();BuildingGraphCompiler.structure(g)
        assertEquals(g,Gson().fromJson(Gson().toJson(g),BuildingGraph::class.java))
        assertFails { g.connect("roof","roofEave","wall") }
        assertFails { g.connect("roof","roofEave","roof") }
        assertNull(g.connect("roof","roofEave","").nodes.first { it.id=="roof" }.roofEave)
        assertNull(g.remove("eave").nodes.first { it.id=="roof" }.roofEave)
        val grouped=GraphGroups.create(g,setOf("roof","eave"))
        val copy=GraphGroups.instantiate(grouped,grouped.nodeGroups.single().id)
        val duplicated=copy.nodes.first { it.kind==BuildingKind.ROOF && it.id!="roof" }
        assertNotEquals("eave",duplicated.roofEave)
        assertEquals(BuildingKind.COPYCAT,copy.nodes.first { it.id==duplicated.roofEave }.kind)
    }
}
