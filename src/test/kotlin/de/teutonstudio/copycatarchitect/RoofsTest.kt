package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class RoofsTest {
    private val palette=Building().palette
    private fun cells(width:Int=2,length:Int=2)=buildSet { for(x in 0 until width) for(z in 0 until length) add(P(x,0,z)) }
    private fun graph(config:RoofLevelConfig,roofConfig:RoofConfig?=null):Pair<BuildingGraph,BuildingNode> {
        val roof=BuildingNode("roof",BuildingKind.ROOF,assembly=true,layout="layout",role="roof",roofConfig=roofConfig)
        val graph=BuildingGraph(nodes=listOf(roof,BuildingNode("layout",BuildingKind.ROOF_LAYOUT,inputs=listOf("plane")),BuildingNode("plane",BuildingKind.ROOF_LEVEL,roofLevel=config)))
        return graph to roof
    }
    private fun compile(config:RoofLevelConfig,mask:Set<P> = cells()):Plan {
        val (g,r)=graph(config)
        return Roofs.compile(g,r,listOf(RoofSupport("storey_0",0,5,mask)),palette,{ error("Unexpected template") })
    }
    private fun top(plan:Plan)=plan.placements.maxOf { p->p.pos.y*16+Geometry.exact(p.parts).cells.flatMap { it.vertices() }.maxOf { it.y.toDouble() } }

    @Test fun `ratios reveal their complete reduced block period`() {
        assertEquals(11,RoofSlope.presets.size)
        assertEquals(RoofSlope(1,3),RoofSlope(2,6).reduced())
        assertEquals("1:3",RoofSlope(1,3).period)
        assertEquals(45.0,RoofSlope().degrees,1e-10)
    }
    @Test fun `all forms compile exact roof surfaces`() {
        for(form in RoofForm.entries) {
            val plan=compile(RoofLevelConfig(form=form,overhang16=0))
            assertTrue(plan.placements.any { it.component=="roof" },form.name)
            assertTrue(plan.placements.flatMap { it.parts }.any { it.exact!=null },form.name)
        }
    }
    @Test fun `height mode keeps the exact finished ridge for a denominator of three`() {
        val plan=compile(RoofLevelConfig(slope=RoofSlope(1,3),mode=RoofMode.HEIGHT,height16=48,overhang16=0))
        assertEquals(5*16+48.0,top(plan),1e-8)
        assertFailsWith<IllegalArgumentException> { compile(RoofLevelConfig(mode=RoofMode.HEIGHT,height16=1,overhang16=0)) }
    }
    @Test fun `all pitches preserve the analytic ridge for even and odd spans on either ridge axis`() {
        for(slope in RoofSlope.presets) for(span in listOf(2,3)) for(axis in 0..1) {
            val plan=compile(RoofLevelConfig(slope=slope,axis=axis,overhang16=0),cells(span,span))
            assertEquals(82.0+span*8.0*slope.p/slope.q,top(plan),1e-8,"${slope.period}, span=$span, axis=$axis")
            assertTrue(plan.placements.isNotEmpty())
        }
    }
    @Test fun `fractional overhang preserves the original finished ridge`() {
        val zero=compile(RoofLevelConfig(overhang16=0))
        val fraction=compile(RoofLevelConfig(overhang16=3))
        assertEquals(top(zero),top(fraction),1e-8)
        assertTrue(fraction.placements.size>zero.placements.size)
    }
    @Test fun `fractional roof extension grows only beyond gable ends and keeps wall positions`() {
        for(form in listOf(RoofForm.GABLE,RoofForm.PENT)) for(axis in 0..3) {
            val config=RoofLevelConfig(form=form,axis=axis,slope=RoofSlope(1,3),knee16=16,overhang16=0)
            val base=compile(config,cells(3,2))
            val extended=compile(config.copy(overhang16=7),cells(3,2))
            fun bounds(plan:Plan,axisX:Boolean):Pair<Double,Double> {
                val points=plan.placements.flatMap { placement->Geometry.exact(placement.parts).cells.flatMap { it.vertices() }.map {
                    if(axisX) placement.pos.x*16+it.x.toDouble() else placement.pos.z*16+it.z.toDouble()
                } }
                return points.min() to points.max()
            }
            val alongX=axis%2==1
            assertEquals(bounds(base,!alongX),bounds(extended,!alongX),"$form/$axis: width")
            val (lo,hi)=bounds(base,alongX)
            assertEquals(lo-7,bounds(extended,alongX).first,1e-8,"$form/$axis: first gable")
            assertEquals(hi+7,bounds(extended,alongX).second,1e-8,"$form/$axis: second gable")
            assertEquals(top(base),top(extended),1e-8,"$form/$axis: ridge")
            val walls=extended.placements.filter { p->p.parts.any { it.group=="roof_wall" } }
            assertTrue(walls.isNotEmpty())
            assertTrue(walls.all { it.pos.copy(y=0) in cells(3,2) },"$form/$axis: walls must stay on the support")
        }
    }
    @Test fun `explicit eave templates follow low edges parallel to the ridge including gable extensions`() {
        for(form in listOf(RoofForm.GABLE,RoofForm.PENT)) for(axis in 0..3) {
            val plan=compile(RoofLevelConfig(form=form,axis=axis,slope=RoofSlope(1,3),knee16=16,overhang16=5),cells(3,4))
            val eaves=plan.placements.filter { p->p.parts.any { it.group=="roof_eave" } }
            assertTrue(eaves.isNotEmpty(),"$form/$axis")
            val alongX=axis%2==1
            val sides=if(form==RoofForm.GABLE) (if(alongX) setOf(0,3) else setOf(0,2))
                else setOf(if(axis<2) 0 else if(alongX) 3 else 2)
            assertEquals(sides,eaves.map { if(alongX) it.pos.z else it.pos.x }.toSet(),"$form/$axis: low sides")
            val along=eaves.map { if(alongX) it.pos.x else it.pos.z }.toSet()
            assertEquals(-1,along.min());assertEquals(if(alongX) 3 else 4,along.max())
        }
    }
    @Test fun `same support upper envelope removes a completely hidden lower roof`() {
        val base=RoofLevelConfig(form=RoofForm.FLAT,knee16=16,overhang16=0)
        val (g,r)=graph(base)
        val upper=g.nodes.first { it.id=="plane" }.copy(id="upper",roofLevel=base.copy(knee16=48))
        val both=g.copy(nodes=g.nodes.map { if(it.id=="layout") it.copy(inputs=listOf("plane","upper")) else it }+upper)
        val plan=Roofs.compile(both,r,listOf(RoofSupport("storey_0",0,5,cells())),palette,{ error("Unexpected template") })
        val roof=plan.placements.filter { it.component=="roof" }
        assertTrue(roof.isNotEmpty())
        assertTrue(roof.all { it.pos.y>=7 })
    }
    @Test fun `concave hip contours use maximal inscribed rectangles`() {
        val mask=cells(4,2)+cells(2,4)
        val rectangles=Roofs.maximalRectangles(mask)
        assertEquals(2,rectangles.size)
        assertEquals(mask,RoomGeometry.cells(rectangles))
        assertTrue(compile(RoofLevelConfig(form=RoofForm.HIP,overhang16=0),mask).placements.isNotEmpty())
    }
    @Test fun `storey identities survive removal and repeated source definitions`() {
        val g=BuildingGraph(nodes=listOf(BuildingNode("building",BuildingKind.BUILDING,levels=mapOf(0 to "repeat",1 to "repeat",2 to "repeat"))))
        val before=g.storeyInstances()
        val changed=g.nodes.single().withLevels(mapOf(0 to "repeat",2 to "repeat"))
        val after=BuildingGraph(nodes=listOf(changed)).storeyInstances()
        assertEquals(listOf(before[0].id,before[2].id),after.map { it.id })
        assertEquals(listOf(0,1),after.map { it.level })
    }
    @Test fun `roof and static shaft compile together with final enclosure reservations`() {
        var g=BuildingGraph.assembledStarter()
        val level=BuildingNode("roof_level",BuildingKind.ROOF_LEVEL,roofLevel=RoofLevelConfig(form=RoofForm.GABLE,overhang16=0))
        val layout=BuildingNode("roof_layout",BuildingKind.ROOF_LAYOUT,inputs=listOf(level.id))
        val shaft=BuildingNode("shaft",BuildingKind.SERVICE_SHAFT,serviceShaft=ServiceShaftConfig(enclosed=false))
        g=g.copy(nodes=g.nodes+listOf(level,layout,shaft))
        g=g.replace(g.nodes.first { it.id=="roof" }.copy(assembly=true,layout=layout.id,inputs=emptyList()))
        val result=g.nodes.first { it.kind==BuildingKind.BUILDING }.withLevels(mapOf(0 to "storey",1 to "storey")).copy(accessInstances=listOf(VerticalAccessInstance("service","shaft",P(1,0,1))))
        g=g.replace(result)
        BuildingGraphCompiler.structure(g)
        val forms=mapOf("full@1" to Form(id="full",parts=listOf(Part("copycats:copycat_block"))))
        val compiled=BuildingGraphCompiler.Evaluation(g,palette,forms,emptyMap()).building()
        assertEquals(2,compiled.storeys.size)
        assertEquals(setOf(P(1,0,1)),compiled.accessFootprints.getValue("service"))
        assertTrue(compiled.plan.placements.any { it.component=="roof" })
    }
    @Test fun `different physical ceiling heights require explicit roof areas`() {
        val support=RoofSupport("storey_0",0,6,cells(2,1),ceilingByCell=mapOf(P() to 80,P(1,0,0) to 96))
        val (g,r)=graph(RoofLevelConfig(form=RoofForm.FLAT,overhang16=0))
        assertContains(assertFails { Roofs.compile(g,r,listOf(support),palette,{ error("Unexpected template") }) }.message.orEmpty(),"different structural ceiling heights")
        val area=BuildingNode("low_area",BuildingKind.AREA,coordinates=listOf(P()))
        val selected=g.copy(nodes=g.nodes.map { if(it.kind==BuildingKind.ROOF_LEVEL) it.copy(roofLevel=it.roofLevel!!.copy(area=area.id)) else it }+area)
        val plan=Roofs.compile(selected,r,listOf(support),palette,{ error("Unexpected template") })
        assertEquals(82.0,top(plan),1e-8)
    }
    @Test fun `immutable custom family requires every used horizontal phase and vertical slice`() {
        val pitch=RoofSlope(1,3)
        val family=RoofTemplateFamily(id="custom",slopes=listOf(pitch),cells=(0..2).flatMap { phase->(-1..0).map { slice->RoofTemplateCell(pitch,phase=phase,slice=slice,form="full@1") } })
        val (g,r)=graph(RoofLevelConfig(form=RoofForm.PENT,slope=pitch,family="custom",overhang16=0),RoofConfig(families=listOf(family)))
        val template=Placement(parts=listOf(Part("copycats:copycat_block")),material=palette.getValue("frame"))
        val supports=listOf(RoofSupport("storey_0",0,5,cells(4,3)))
        assertTrue(Roofs.compile(g,r,supports,palette,{ template },{ template }).placements.isNotEmpty())
        val incomplete=r.copy(roofConfig=RoofConfig(families=listOf(family.copy(cells=family.cells.filter { it.phase!=2 }))))
        assertContains(assertFails { Roofs.compile(g.replace(incomplete),incomplete,supports,palette,{ template },{ template }) }.message.orEmpty(),"missing")
    }

    @Test fun `custom ribs preserve voids above sealed backing inside the finished envelope`() {
        val pitch=RoofSlope()
        val thickness=4
        fun model(slice:Int):Placement {
            val upper=ExactPlane(0,1,-1,-16L*slice)
            val full=ExactVolume.convex(listOf(upper,upper.opposite().copy(d=-upper.d+thickness)))
            val backing=ExactVolume.convex(listOf(upper.copy(d=upper.d-thickness+1),upper.opposite().copy(d=-upper.d+thickness)))
            val ribs=full.intersect(ExactVolume.box(0,0,0,4,16,16).union(ExactVolume.box(12,0,0,16,16,16)))
            return Placement(parts=listOf(Part(RoofPartParameters.SOURCE,exact=backing.union(ribs),roof=RoofPartParameters(1,1,slice=slice,thickness=thickness))),material=palette.getValue("roof"))
        }
        val family=RoofTemplateFamily("ribs",thickness16=thickness,cells=listOf(RoofTemplateCell(pitch,slice=-1,form="rib_low@1"),RoofTemplateCell(pitch,slice=0,form="rib_main@1")))
        val config=RoofLevelConfig(form=RoofForm.PENT,mode=RoofMode.HEIGHT,height16=80,overhang16=0,family="ribs")
        val (g,r)=graph(config,RoofConfig(families=listOf(family)))
        val support=listOf(RoofSupport("storey_0",0,5,cells(3,3)))
        val custom=Roofs.compile(g,r,support,palette,{ error("Unexpected node source") },{ ref->model(if(ref=="rib_low@1") -1 else 0) })
        val plainRoof=r.copy(roofConfig=RoofConfig(families=listOf(family.copy(cells=emptyList()))))
        val plain=Roofs.compile(g.replace(plainRoof),plainRoof,support,palette,{ error("Unexpected node source") })
        assertEquals(160.0,top(custom),1e-8)
        assertTrue(custom.placements.any { p->p.groups.any { it.id=="body" } })
        assertTrue(custom.placements.sumOf { Geometry.solid(it.parts).count() }<plain.placements.sumOf { Geometry.solid(it.parts).count() })
    }

    @Test fun `ordered roof segment inputs supply real fallback variant periods`() {
        val (base,r0)=graph(RoofLevelConfig(form=RoofForm.PENT,overhang16=0))
        val roof=r0.copy(inputs=listOf("period"))
        val g=base.replace(roof)
        val template=RoofSegmentTemplate(1,3,cells=(0..2).map { row->P(0,row,0) to Placement(parts=listOf(Part("copycats:copycat_block")),material="minecraft:gold_block") })
        val supports=listOf(RoofSupport("storey_0",0,5,cells(3,3)))
        val plan=Roofs.compile(g,roof,supports,palette,{ error("Unexpected node source") },segmentSource={ template })
        assertTrue(plan.placements.any { p->p.material=="minecraft:gold_block" || "minecraft:gold_block" in p.surfaces.values })
        assertContains(assertFails { Roofs.compile(g,roof,supports,palette,{ error("Unexpected node source") },segmentSource={ template.copy(width=2) }) }.message.orEmpty(),"Roof slope segment needs")
    }

    @Test fun `failed building assembly clears access reservation context before isolated previews`() {
        var g=BuildingGraph.assembledStarter()
        val wood=BuildingNode("wood_copycat",BuildingKind.COPYCAT,role="frame")
        val shaft=BuildingNode("shaft",BuildingKind.SERVICE_SHAFT,serviceShaft=ServiceShaftConfig(enclosed=false))
        g=g.copy(nodes=g.nodes+listOf(wood,shaft))
        g=g.replace(g.nodes.first { it.id=="floor" }.copy(inputs=listOf(wood.id)))
        g=g.replace(g.nodes.first { it.kind==BuildingKind.BUILDING }.withLevels(mapOf(0 to "storey",1 to "storey")).copy(roof="",accessInstances=listOf(VerticalAccessInstance("service",shaft.id,P(1,0,1)))))
        val forms=mapOf("full@1" to Form(id="full",parts=listOf(Part("copycats:copycat_block"))))
        fun evaluation()=BuildingGraphCompiler.Evaluation(g,palette,forms,emptyMap())
        val failed=evaluation()
        assertContains(assertFails { failed.building() }.message.orEmpty(),"Incompatible")
        assertEquals(evaluation().preview("room"),failed.preview("room"))
    }

    @Test fun `builtin semantic groups partition the unchanged exact skin and preserve fallback finishes`() {
        val plan=compile(RoofLevelConfig(form=RoofForm.PENT,overhang16=0),cells(3,3))
        val ids=plan.placements.flatMap { it.groups }.map { it.id }.toSet()
        assertTrue(setOf("roof_skin","roof_underside","roof_verge","roof_frame","roof_wall").all { it in ids })
        assertTrue(plan.placements.all { it.groups.size<=8 })
        plan.placements.forEach { p->p.groups.filter { it.id.startsWith("roof_") }.forEach { group->assertEquals(palette.getValue(group.role),p.surfaces.getValue(group.id)) } }
        val skinIds=setOf("roof_skin","roof_underside","roof_verge","roof_eave")
        for(x in 0..2) for(z in 0..2) for(y in 4..9) {
            val parts=plan.placements.firstOrNull { it.pos==P(x,y,z) }?.parts.orEmpty().filter { it.group in skinIds }
            val upper=ExactPlane(-1,1,0,82L+16*x-16*y)
            val expected=ExactVolume.convex(listOf(upper,upper.opposite().copy(d=-upper.d+2)))
            assertTrue(Geometry.exact(parts).equivalent(expected),"Skin geometry at $x/$y/$z")
        }
    }
    @Test fun `mansard same-flank slope changes receive transition groups`() {
        val plan=compile(RoofLevelConfig(form=RoofForm.MANSARD,slope=RoofSlope(2,1),upperSlope=RoofSlope(1,2),overhang16=0),cells(4,4))
        val ids=plan.placements.flatMap { it.groups }.map { it.id }.toSet()
        assertTrue("roof_transition" in ids)
        assertFalse("roof_valley" in ids)
        assertTrue(plan.placements.all { it.groups.size<=8 })
    }
    @Test fun `wall segment periods selected through roof config compile and clip under the roof`() {
        var g=BuildingGraph.assembledStarter()
        val level=BuildingNode("roof_level",BuildingKind.ROOF_LEVEL,roofLevel=RoofLevelConfig(mode=RoofMode.HEIGHT,height16=96,overhang16=0))
        val layout=BuildingNode("roof_layout",BuildingKind.ROOF_LAYOUT,inputs=listOf(level.id))
        val gold=BuildingNode("gold",BuildingKind.COPYCAT,role="gold")
        val copper=BuildingNode("copper",BuildingKind.COPYCAT,role="copper")
        val wall=BuildingNode("wall_period",BuildingKind.SEGMENT,module="",length=2,height=2,inputs=listOf(gold.id,copper.id))
        g=g.copy(nodes=g.nodes+listOf(level,layout,gold,copper,wall))
        val roof=g.nodes.first { it.kind==BuildingKind.ROOF }.copy(assembly=true,layout=layout.id,inputs=emptyList())
        g=g.replace(roof)
        val forms=mapOf("full@1" to Form(id="full",parts=listOf(Part("copycats:copycat_block"))))
        val finishes=palette+mapOf("gold" to "minecraft:gold_block","copper" to "minecraft:copper_block")
        fun evaluation(graph:BuildingGraph)=BuildingGraphCompiler.Evaluation(graph,finishes,forms,emptyMap())
        val before=evaluation(g).building().plan
        val selected=g.replace(roof.copy(roofConfig=RoofConfig(wall=wall.id)))
        BuildingGraphCompiler.structure(selected)
        val after=evaluation(selected).building().plan
        assertEquals(before.placements.map { it.pos },after.placements.map { it.pos })
        val original=before.placements.associateBy { it.pos }
        after.placements.forEach { p->assertTrue(Geometry.exact(p.parts).equivalent(Geometry.exact(original.getValue(p.pos).parts)),"Wall clip at ${p.pos}") }
        assertTrue("minecraft:gold_block" in after.materials)
        assertTrue("minecraft:copper_block" in after.materials)
    }

}
