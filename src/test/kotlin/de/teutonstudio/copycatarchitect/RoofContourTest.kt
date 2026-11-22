package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.Json
import kotlin.test.*

class RoofContourTest {
    private val cross=(0..6).flatMap { z->(if(z in 2..4) -1..3 else 0..2).map { x->P(x,0,z) } }.toSet()
    private val colors=(0 until RoofTemplates.variantCount).map { "minecraft:contour_$it" }
    private fun graph(mode:RoofContourMode?=null,axis:Int=0):BuildingGraph {
        val area=BuildingNode("area",BuildingKind.AREA,areaMode=AreaMode.DRAW,coordinates=cross.map { if(axis==0) it else P(-it.z,0,it.x) })
        val sources=colors.mapIndexed { i,color->BuildingNode("part_$i",BuildingKind.COPYCAT,inlinePart=Part("copycats:copycat_block"),surfaces=mapOf("body" to color)) }
        val result=GraphPrograms.newResult("ROOF_TEMPLATE").copy(id="result",inputs=sources.map { it.id },geometry=area.id,
            roofSettings=RoofTemplateSettings(axis=axis,overhang16=0,contourMode=mode ?: RoofContourMode.BOUNDS))
        return GraphCards.starter().copy(nodes=listOf(GraphCards.starter().nodes.first(),area)+sources+result).withCurrentSchema()
    }
    private fun plan(g:BuildingGraph)=BuildingGraphCompiler.Evaluation(g,Building().palette,emptyMap(),emptyMap()).preview("result")
    private fun top(plan:Plan)=plan.placements.maxOf { p->p.pos.y*16+Geometry.exact(p.parts).cells.flatMap { it.vertices() }.maxOf { it.y.toDouble() } }
    @Test fun `existing total-span roofs retain their previous shape by default`() {
        assertEquals(plan(graph()),plan(graph(RoofContourMode.BOUNDS)))
        assertEquals(42.0,top(plan(graph())),1e-8)
        assertEquals(RoofContourMode.BOUNDS,Json.decode<Building>("""{"schema":2,"graph":{"schema":15,"nodes":[{"id":"r","kind":"ROOF_TEMPLATE","roofSettings":{}}]}}""").graph!!.nodes.single().roofSettings!!.contourMode)
    }
    @Test fun `contour mode builds all four inner and outer corners of the submitted 27-cell shape`() {
        val legacy=plan(graph())
        assertTrue(legacy.placements.none { colors[1] in it.surfaces.values || colors[2] in it.surfaces.values })
        for(axis in 0..1) {
            val g=graph(RoofContourMode.CONTOUR,axis);val actual=plan(g)
            assertEquals(16,g.schema)
            assertEquals(26.0,top(actual),1e-8,"The narrow gables keep their own rise")
            for(variant in listOf(1,2)) {
                val corners=actual.placements.filter { colors[variant] in it.surfaces.values }.map { p->
                    val x=if(axis==0) p.pos.x*16+8 else p.pos.z*16+8
                    val z=if(axis==0) p.pos.z*16+8 else -p.pos.x*16-8
                    (x<24) to (z<56)
                }.toSet()
                assertEquals(setOf(true to true,true to false,false to true,false to false),corners,RoofTemplates.labels[variant]+" axis "+axis)
            }
        }
    }
    @Test fun `consumer contour overrides preserve the frozen template and survive JSON`() {
        val g=graph();val template=g.resultNode().copy(id="template",kind=BuildingKind.ROOF_TEMPLATE,resultType=null,resultMode=null,producerKind=null)
        val consumer=GraphPrograms.newResult("ROOF").copy(id="result",geometry="area",roofTemplate="template",roofContourMode=RoofContourMode.CONTOUR)
        val next=g.copy(nodes=g.nodes.filter { it.id!="result" }+template+consumer).withCurrentSchema()
        val restored=Json.decode<Building>(Json.encode(Building(graph=next))).graph!!
        val expanded=RoofTemplates.expand(restored,GraphPrograms.producer(restored.resultNode()))
        assertEquals(RoofContourMode.CONTOUR,expanded.roofSections!!.single().config.contourMode)
        assertEquals(RoofContourMode.BOUNDS,restored.nodes.first { it.id=="template" }.roofSettings!!.contourMode)
        assertEquals(plan(graph(RoofContourMode.CONTOUR)),plan(restored))
    }
    @Test fun `submitted Boolean corner pieces also fit the contour roof`() {
        val text=checkNotNull(javaClass.getResourceAsStream("/roof/testdach-building.json")).bufferedReader().use { it.readText() }
        val building=Json.decode<Building>(text);val original=building.graph!!
        val area=BuildingNode("area",BuildingKind.AREA,areaMode=AreaMode.DRAW,coordinates=cross.toList())
        val result=original.resultNode().copy(geometry=area.id,roofSettings=original.resultNode().roofSettings!!.copy(contourMode=RoofContourMode.CONTOUR))
        val g=original.copy(nodes=original.nodes.map { if(it.id==result.id) result else it }+area).withCurrentSchema()
        val definitions=g.graphDefinitions.orEmpty().values
        val evaluation=BuildingGraphCompiler.Evaluation(g,building.palette,definitions.flatMap { it.forms.entries }.associate { it.toPair() },definitions.flatMap { it.modules.entries }.associate { it.toPair() })
        assertTrue(evaluation.preview(result.id).placements.isNotEmpty())
    }

    @Test fun `a closed Boolean valley seam need not have a uniform backing thickness`() {
        val a=ExactPlane(0,1,-1,0);val b=ExactPlane(-1,1,0,0)
        val full=ExactGeometry.roof(RoofPartParameters(variant="valley"))
        val taper=ExactVolume.convex(listOf(a,a.opposite().copy(d=2),b.opposite())).union(ExactVolume.convex(listOf(b,b.opposite().copy(d=2),a.opposite())))
        assertFalse(taper.contains(full))
        val template=Placement(parts=listOf(Part("copycats:copycat_block",exact=taper)))
        val fitted=RoofTemplatePlacement.fitManyOrNull(listOf(template to P()),a,RoofSlope(),0,0,full,full,alignJunction=true)
        assertNotNull(fitted)
        assertTrue(Geometry.exact(fitted.parts).equivalent(taper))
    }
    @Test fun `a real opening in a Boolean corner surface is still rejected`() {
        val a=ExactPlane(0,1,-1,0);val full=ExactGeometry.roof(RoofPartParameters(variant="valley"))
        val cut=full.minus(ExactVolume.box(7,0,0,9,16,16))
        val template=Placement(parts=listOf(Part("copycats:copycat_block",exact=cut)))
        val failure=assertFailsWith<IllegalArgumentException> { RoofTemplatePlacement.fitManyOrNull(listOf(template to P()),a,RoofSlope(),0,0,full,full,alignJunction=true) }
        assertContains(failure.message.orEmpty(),"hole")
    }

}
