package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.Json
import kotlin.test.*

class RoofDormerWorkflowTest {
    private fun graph(form:RoofForm=RoofForm.GABLE,contour:RoofContourMode=RoofContourMode.BOUNDS,points:List<P>?=null,template:Boolean=false):BuildingGraph {
        val area=BuildingNode("area",BuildingKind.AREA,areaMode=if(points==null) AreaMode.RECTANGLE else AreaMode.DRAW,length=14,height=18,coordinates=points.orEmpty())
        val copy=BuildingNode("copy",BuildingKind.COPYCAT,inlinePart=Part("copycats:copycat_block"))
        val wallArea=BuildingNode("wall_area",BuildingKind.AREA,areaMode=AreaMode.SQUARE,length=1,height=1)
        val wall=BuildingNode("wall",BuildingKind.SEGMENT,inputs=listOf("copy"),assembly=true,layout="wall_area",length=1,height=1,depth=1,module="")
        val dormer=BuildingNode("dormer",BuildingKind.DORMER,dormer=DormerConfig(wall="wall",front="wall",roof="copy"))
        val settings=RoofTemplateSettings(form=form,axis=1,overhang16=0,contourMode=contour)
        val definition=BuildingNode("template",BuildingKind.ROOF_TEMPLATE,inputs=List(13) { "copy" },roofSettings=settings)
        val roof=GraphPrograms.newResult("ROOF").copy(id="result",geometry="area",inputs=List(13) { "copy" },roofInputMode=if(template) RoofInputMode.TEMPLATE else RoofInputMode.SEGMENTS,roofTemplate=if(template) "template" else null,roofSettings=if(template) null else settings)
        return GraphCards.starter().copy(nodes=listOf(GraphCards.starter().nodes.first(),area,copy,wallArea,wall,dormer,definition,roof)).withCurrentSchema()
    }
    private fun evaluation(g:BuildingGraph)=BuildingGraphCompiler.Evaluation(g,Building().palette,emptyMap(),emptyMap())
    private fun item(id:String="first",p:P=P(3,0,0),q:Int=0)=DormerInstance(id,"dormer",p,q)
    private fun workspace(g:BuildingGraph)=evaluation(g).roofDormerWorkspace("result")
    private fun attach(g:BuildingGraph,vararg values:DormerInstance)=g.replace(g.resultNode().copy(dormers=values.toList()))
    @Test fun `modern template and segment roofs compile dormers without changing their template`() {
        for(template in listOf(false,true))for(mode in RoofContourMode.entries) {
            val before=graph(contour=mode,template=template);val g=attach(before,item());val ev=evaluation(g)
            assertEquals(17,g.schema)
            assertTrue(workspace(g).let { it.check(it.areas.single().id,item()).valid })
            assertTrue(ev.preview("result").placements.any { it.component=="wall" })
            assertEquals(before.nodes.first { it.id=="template" },g.nodes.first { it.id=="template" })
            val expanded=RoofTemplates.expand(g,GraphPrograms.producer(g.resultNode()))
            assertEquals(listOf(item()),expanded.roofSections!!.single().dormers)
            val restored=Json.decode<Building>(Json.encode(Building(graph=g))).graph!!
            assertEquals(ev.preview("result"),evaluation(restored).preview("result"))
        }
    }
    @Test fun `ports use instance identities when earlier instances are removed`() {
        val g=attach(graph(),item("a"),item("b",P(9,0,0)))
        val ports=BuildingPorts.of(g.resultNode(),g).filter { it.key.startsWith("semantic:dormer:") }
        assertEquals(setOf("semantic:dormer:a:source","semantic:dormer:b:source"),ports.map { it.key }.toSet())
        val after=attach(g,item("b",P(9,0,0)))
        assertTrue(BuildingPorts.of(after.resultNode(),after).any { it.key=="semantic:dormer:b:source" })
        assertEquals(BuildingKind.DORMER,ports.first().accepts.first { it==BuildingKind.DORMER })
    }
    @Test fun `same cheap placement rules reject uphill ridge crossings holes and collisions`() {
        val g=attach(graph(),item());val w=workspace(g);val id=w.areas.single().id
        assertFalse(w.check(id,item(q=2)).valid)
        assertFalse(w.check(id,item(p=P(3,0,7))).valid)
        assertFalse(w.check(id,item("second")).valid)
        assertFalse(w.check(id,item(p=P(-1,0,0))).valid)
        assertFalse(w.check(id,item().copy(source="")).valid)
        val hole=(0 until 14).flatMap { x -> (0 until 18).map { z->P(x,0,z) } }.filterNot { it==P(5,0,2) }
        val h=workspace(graph(points=hole));assertFalse(h.check(h.areas.single().id,item()).valid)
    }
    @Test fun `composite cross has valid slopes on four wings and rejects its central junction`() {
        val cross=(0 until 30).flatMap { x->(0 until 36).filter { z->x in 10..19 || z in 13..22 }.map { z->P(x,0,z) } }
        val w=workspace(graph(RoofForm.CROSS,RoofContourMode.CONTOUR,cross));val id=w.areas.single().id
        val valid=mutableListOf<DormerInstance>()
        for(q in 0..3) {
            val found=cross.asSequence().map { item("q$q",it,q) }.firstOrNull { w.check(id,it).valid }
            assertNotNull(found,"No valid direction $q");valid+=found
        }
        assertEquals(4,valid.size)
        assertFalse(w.check(id,item(p=P(13,0,15))).valid)
        assertTrue(w.surfaces(id).isNotEmpty())
    }
    @Test fun `small submitted cross rejects oversized dormers without altering roof geometry`() {
        val cross=(0..6).flatMap { z->(if(z in 2..4) -1..3 else 0..2).map { x->P(x,0,z) } }
        val g=graph(RoofForm.CROSS,RoofContourMode.CONTOUR,cross);val w=workspace(g)
        assertFalse(w.check(w.areas.single().id,item(p=P())).valid)
        assertTrue(evaluation(g).preview("result").placements.isNotEmpty())
    }
    @Test fun `native placement workspace supports legacy roof levels and embedded areas`() {
        val building=SpecialRoomsExample.building();val ev=BuildingGraphCompiler.Evaluation(building.graph!!,building.palette,emptyMap(),emptyMap())
        val w=ev.roofDormerWorkspace("roof_level")
        assertTrue(w.areas.any { it.id=="roof_level" })
        val modern=graph();val result=modern.resultNode().copy(roofInputMode=null,geometry="",assembly=true,adaptiveRoof=false,inputs=List(6) { "copy" },roofSections=listOf(RoofSection(config=RoofLevelConfig(area="area",axis=1,overhang16=0))))
        val g=modern.replace(result);val embedded=workspace(g)
        assertEquals("result:section_1",embedded.areas.single().id)
        assertTrue(embedded.check(embedded.areas.single().id,item()).valid)
    }
    @Test fun `rational roof slopes accept all four downhill orientations in both modes`() {
        val positions=listOf(P(3,0,0),P(13,0,3),P(10,0,17),P(0,0,14))
        for(form in listOf(RoofForm.GABLE,RoofForm.PENT))for(mode in RoofContourMode.entries)for(q in 0..3) {
            val g=graph(form,mode);val result=g.resultNode();val settings=result.roofSettings!!.copy(axis=if(form==RoofForm.PENT) (q+1)%4 else if(q%2==0) 1 else 0,slope=RoofSlope(1,2))
            val changed=g.copy(nodes=g.nodes.map { when(it.id) { "result"->result.copy(roofSettings=settings);"dormer"->it.copy(dormer=it.dormer!!.copy(wallHeight=1,slope=RoofSlope(1,2)));else->it } }).withCurrentSchema()
            val w=workspace(changed);val check=w.check(w.areas.single().id,item(p=positions[q],q=q))
            assertTrue(check.valid,"$form $mode $q: ${check.message}")
        }
    }
    @Test fun `coplanar visible patches can jointly support a dormer`() {
        val points=(0 until 22).flatMap { x->(0 until if(x<10) 16 else 24).map { z->P(x,0,z) } }
        val w=workspace(graph(contour=RoofContourMode.CONTOUR,points=points))
        assertTrue(w.check(w.areas.single().id,item(p=P(8,0,0))).valid)
    }
    @Test fun `cheap validation detects unsuitable solid wall and roof profiles`() {
        val g=graph();val wall=g.nodes.first { it.id=="wall" }
        val solid=g.replace(wall.copy(assembly=false,layout="",inputs=emptyList(),block="minecraft:stone_bricks"))
        val w=workspace(solid);assertFalse(w.check(w.areas.single().id,item()).valid)
    }
    @Test fun `modern building roof cuts its selected ceiling and preserves the exterior and offsets`() {
        fun building(open:Boolean):Building {
            val b=SpecialRoomsExample.building(open);val g=b.graph!!;val old=g.nodes.first { it.id=="roof" };val level=g.nodes.first { it.id=="roof_level" }
            val area=BuildingNode("modern_area",BuildingKind.AREA,areaMode=AreaMode.DRAW,coordinates=RoomGeometry.cells(listOf(Rect(-1,-1,11,15))).toList())
            val roof=old.copy(roofInputMode=RoofInputMode.SEGMENTS,geometry=area.id,layout="",inputs=List(13) { "roof_source" },roofSettings=RoofTemplateSettings(axis=1,overhang16=0),dormers=level.dormers)
            return b.copy(graph=g.copy(nodes=g.nodes.map { if(it.id==old.id) roof else it }+area).withCurrentSchema())
        }
        val forms=mapOf("full@1" to Form(id="full",parts=listOf(Part("copycats:copycat_block"))))
        val closed=building(false);val opened=building(true)
        val a=Generator.generate(closed,forms,emptyMap());val b=Generator.generate(opened,forms,emptyMap())
        assertEquals(a.placements.filter { it.component!="ceiling" },b.placements.filter { it.component!="ceiling" })
        assertEquals(9,a.placements.count { it.component=="ceiling" }-b.placements.count { it.component=="ceiling" })
        val g=opened.graph!!;val offset=P(20,0,-12);val moved=Generator.generate(opened.copy(graph=g.replace(g.resultNode().copy(buildingOffset=offset))),forms,emptyMap())
        assertEquals(b.placements.map { it.copy(pos=it.pos+offset) }.toSet(),moved.placements.toSet())
    }

    @Test fun `modern roof group copies remap dormer definitions and deletion preserves instance identity`() {
        val g=attach(graph(),item());val roof=GraphPrograms.producer(g.resultNode()).copy(id="roof",resultType=null,resultMode=null,producerKind=null)
        val grouped=GraphGroups.create(g.copy(nodes=g.nodes+roof),setOf("roof","dormer"),"Gauben")
        val copied=GraphGroups.instantiate(grouped,grouped.nodeGroups.single().id)
        val members=copied.nodeGroups.last().nodes.map { id->copied.nodes.first { it.id==id } }
        val copiedRoof=members.single { it.kind==BuildingKind.ROOF };val copiedDefinition=members.single { it.kind==BuildingKind.DORMER }
        assertEquals(copiedDefinition.id,copiedRoof.dormerInstances.single().source)
        assertEquals("first",copiedRoof.dormerInstances.single().id)
        val removed=copied.remove(copiedDefinition.id);BuildingGraphCompiler.structure(removed)
        val retained=removed.nodes.first { it.id==copiedRoof.id }.dormerInstances.single()
        assertEquals("first",retained.id);assertEquals("",retained.source)
        assertEquals("first",removed.resultNode().dormerInstances.single().id)
        assertEquals("",removed.resultNode().dormerInstances.single().source) // Linked group definitions share member deletion.
    }
    @Test fun `dormer cuts leave every roof placement outside their footprint unchanged`() {
        val g=graph();val original=evaluation(g).preview("result");val changed=evaluation(attach(g,item())).preview("result")
        val occupied=(3..7).flatMap { x->(0..4).map { z->P(x,0,z) } }.toSet()
        val actual=changed.placements.associateBy { it.pos }
        for(p in original.placements.filter { it.pos.copy(y=0) !in occupied }) assertTrue(p.sameVisual(actual.getValue(p.pos)),"Roof changed outside dormer at ${p.pos}")
    }

    @Test fun `dormer placement uses the visible slopes of an edge-controlled roof`() {
        val g=graph();val n=g.resultNode()
        val mask=(0 until 14).flatMap { x->(0 until 18).map { z->P(x,0,z) } }.toSet()
        val edges=RoofEdges.runs(mask).map { run->RoofEdgeSetting(run.id,if(run.direction%2==1) RoofEdgeMode.FACADE else RoofEdgeMode.ROOF,if(run.direction%2==1) "wall" else "") }
        val controlled=g.replace(n.copy(roofEdges=edges));val w=workspace(controlled);val area=w.areas.single().id
        w.check(area,item(p=P(4,0,0))).let { assertTrue(it.valid,it.message) }
        assertFalse(w.check(area,item(p=P(0,0,0))).valid)
    }

}
