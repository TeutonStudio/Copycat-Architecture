package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import kotlin.test.*
import de.teutonstudio.copycatarchitect.server.Json

class RoofTemplateWorkflowTest {
    private fun fixture(size:Int=5,draw:Boolean=false,slope:RoofSlope=RoofSlope(),form:RoofForm=RoofForm.GABLE):BuildingGraph {
        val area=BuildingNode("area",BuildingKind.AREA,areaMode=if(draw) AreaMode.DRAW else AreaMode.SQUARE,length=size,height=size,
            coordinates=(0 until size).flatMap { x->(0 until size).map { z->P(x,0,z) } })
        val tile=BuildingNode("tile",BuildingKind.COPYCAT,inlinePart=Part(RoofPartParameters.SOURCE,roof=RoofPartParameters(slope.p,slope.q)))
        val ridge=BuildingNode("ridge",BuildingKind.COPYCAT,inlinePart=Part(RoofPartParameters.SOURCE,roof=RoofPartParameters(slope.p,slope.q,variant="ridge")))
        val solid=BuildingNode("solid",BuildingKind.COPYCAT,inlinePart=Part("copycats:copycat_block"))
        val template=GraphPrograms.newResult("ROOF_TEMPLATE").copy(id="result",geometry="area",inputs=List(RoofTemplates.variantCount) { if(it==0) "tile" else if(it==3) "ridge" else "solid" },roofSettings=RoofTemplateSettings(form=form,slope=slope,overhang16=0))
        return GraphCards.starter().copy(nodes=listOf(GraphCards.starter().nodes.first(),area,tile,ridge,solid,template)).withCurrentSchema()
    }
    private fun evaluate(g:BuildingGraph,id:String="result")=BuildingGraphCompiler.Evaluation(g,Building().palette,emptyMap(),emptyMap()).preview(id)
    @Test fun `odd and even square and drawn roofs fit complete profiles and preview the same consumer`() {
        for(size in listOf(5,6)) for(draw in listOf(false,true)) {
            val g=fixture(size,draw);val preview=evaluate(g)
            assertTrue(preview.placements.isNotEmpty())
            val template=g.resultNode().copy(id="template",kind=BuildingKind.ROOF_TEMPLATE,resultType=null,resultMode=null,producerKind=null)
            val roof=GraphPrograms.newResult("ROOF").copy(id="result",geometry="area",roofTemplate="template",role="roof")
            val consumer=g.copy(nodes=g.nodes.filter { it.id!="result" }+template+roof)
            assertEquals(preview,evaluate(consumer))
            assertEquals(15,g.schema)
        }
    }
    @Test fun `the original submitted graph also previews natively on a concave drawn area`() {
        val text=checkNotNull(javaClass.getResourceAsStream("/roof/testdach-building.json")).bufferedReader().use { it.readText() }
        val building=Json.decode<Building>(text)
        val original=building.graph!!
        val area=BuildingNode("test_area",BuildingKind.AREA,areaMode=AreaMode.DRAW,coordinates=(0..4).flatMap { x->(0..4).filter { z->x<3 || z<3 }.map { z->P(x,0,z) } })
        val template=original.resultNode().copy(geometry=area.id)
        val graph=original.copy(nodes=original.nodes.map { if(it.id==template.id) template else it }+area).withCurrentSchema()
        val definitions=original.graphDefinitions.orEmpty().values
        val plan=BuildingGraphCompiler.Evaluation(graph,building.palette,definitions.flatMap { it.forms.entries }.associate { it.toPair() },definitions.flatMap { it.modules.entries }.associate { it.toPair() }).preview(template.id)
        assertTrue(plan.placements.isNotEmpty())
    }
    @Test fun `completed templates use an optional five block sample without persisting geometry`() {
        val g=fixture();val implicit=g.replace(g.resultNode().copy(geometry=""))
        val plan=evaluate(implicit)
        assertTrue(plan.placements.isNotEmpty())
        assertTrue(plan.warnings.any { "5 × 5" in it })
        assertEquals("",implicit.resultNode().geometry)
    }
    @Test fun `new eave verge transition flat and gable sockets all contribute their own materials`() {
        val colors=(0 until RoofTemplates.variantCount).map { "minecraft:test_color_$it" }
        fun colored(form:RoofForm):Plan {
            val g=fixture(5,form=form)
            val parts=colors.mapIndexed { i,color->BuildingNode("color_$i",BuildingKind.COPYCAT,inlinePart=Part("copycats:copycat_block"),surfaces=mapOf("body" to color)) }
            val result=g.resultNode().copy(inputs=parts.map { it.id },roofSettings=RoofTemplateSettings(form=form,slope=RoofSlope(2,1),overhang16=16))
            return evaluate(g.copy(nodes=g.nodes.filter { it.id!="result" }+parts+result))
        }
        fun materials(plan:Plan)=plan.placements.flatMap { it.surfaces.values }.toSet()
        val gable=materials(colored(RoofForm.GABLE))
        for(i in listOf(8,9,12)) assertTrue(colors[i] in gable,RoofTemplates.labels[i])
        assertTrue(colors[10] in materials(colored(RoofForm.MANSARD)))
        assertTrue(colors[11] in materials(colored(RoofForm.FLAT)))
    }
    @Test fun `native sloped templates resolve every phase without changing the declared ratio`() {
        for(slope in RoofSlope.presets) {
            assertTrue(evaluate(fixture(3,slope=slope,form=RoofForm.PENT)).placements.isNotEmpty(),slope.period)
        }
    }
    @Test fun `edited finite profiles still reject an actual missing spill slice`() {
        val part=Part(RoofPartParameters.SOURCE,roof=RoofPartParameters())
        val clipped=Placement(parts=listOf(part.copy(exactClip=ExactGeometry.part(part))))
        val upper=ExactPlane(0,1,-1,2)
        val backing=ExactVolume.convex(listOf(upper.copy(d=1),upper.opposite().copy(d=0)))
        val envelope=ExactVolume.convex(listOf(upper,upper.opposite().copy(d=0)))
        val error=assertFailsWith<IllegalArgumentException> { RoofTemplatePlacement.fitManyOrNull(listOf(-16,0,16).map { clipped to P(0,it,0) },upper,RoofSlope(),0,0,backing,envelope,completeProfile=true) }
        assertContains(error.message.orEmpty(),"hole")
    }
    @Test fun `extended variant sockets preserve legacy IDs and remap the optional preview independently`() {
        val g=fixture();val template=g.resultNode()
        val ports=BuildingPorts.of(template,g)
        assertEquals((0 until RoofTemplates.variantCount).map { "in:$it" }+"geometry",ports.map { it.key })
        val legacy=template.copy(inputs=template.inputs.take(8),geometry="")
        val old=g.replace(legacy)
        val connected=old.connect("result","in:12","solid")
        assertEquals("solid",connected.resultNode().inputs[12])
        assertEquals("tile",RoofTemplates.input(legacy,"eave"))
        assertEquals("tile",RoofTemplates.input(legacy,"edge"))
    }
}
