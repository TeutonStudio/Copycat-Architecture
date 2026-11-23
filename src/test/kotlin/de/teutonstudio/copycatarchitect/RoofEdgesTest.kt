package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.Json
import kotlin.test.*

class RoofEdgesTest {
    private fun graph(height:Int=2,modes:Set<Int> = setOf(0,2),points:List<P>?=null,template:Boolean=false,slope:RoofSlope=RoofSlope()):BuildingGraph {
        val area=BuildingNode("area",BuildingKind.AREA,areaMode=if(points==null) AreaMode.RECTANGLE else AreaMode.DRAW,length=8,height=10,coordinates=points.orEmpty())
        val copy=BuildingNode("copy",BuildingKind.COPYCAT,inlinePart=Part("copycats:copycat_block"),surfaces=mapOf("body" to "minecraft:gray_concrete"))
        val facade=copy.copy(id="facade_copy",surfaces=mapOf("body" to "minecraft:red_concrete"))
        val wallArea=BuildingNode("wall_area",BuildingKind.AREA,areaMode=AreaMode.RECTANGLE,length=2,height=height)
        val wall=BuildingNode("facade",BuildingKind.SEGMENT,assembly=true,module="",layout=wallArea.id,inputs=listOf(facade.id),length=2,height=height)
        val mask=points?.toSet() ?: (0 until 8).flatMap { x->(0 until 10).map { z->P(x,0,z) } }.toSet()
        val edges=RoofEdges.runs(mask).map { RoofEdgeSetting(it.id,if(it.direction in modes) RoofEdgeMode.FACADE else RoofEdgeMode.ROOF,if(it.direction in modes) wall.id else "") }
        val settings=RoofTemplateSettings(overhang16=0,slope=slope)
        val definition=BuildingNode("template",BuildingKind.ROOF_TEMPLATE,inputs=List(13) {copy.id},roofSettings=settings)
        val result=GraphPrograms.newResult("ROOF").copy(id="result",geometry=area.id,inputs=List(13) {copy.id},roofInputMode=if(template) RoofInputMode.TEMPLATE else RoofInputMode.SEGMENTS,roofTemplate=if(template) definition.id else null,roofSettings=if(template) null else settings,roofEdges=edges)
        return GraphCards.starter().copy(nodes=listOf(GraphCards.starter().nodes.first(),area,copy,facade,wallArea,wall,definition,result)).withCurrentSchema()
    }
    private fun plan(g:BuildingGraph)=BuildingGraphCompiler.Evaluation(g,Building().palette,emptyMap(),emptyMap()).preview("result")
    private fun topAt(p:Plan,x:Double,z:Double):Double = p.placements.filter { it.pos.x==kotlin.math.floor(x).toInt() && it.pos.z==kotlin.math.floor(z).toInt() }.flatMap { placement->
        val localX=(x-placement.pos.x)*16;val localZ=(z-placement.pos.z)*16
        Geometry.exact(placement.parts).cells.mapNotNull { cell->
            var low=Double.NEGATIVE_INFINITY;var high=Double.POSITIVE_INFINITY
            for(a in cell.planes) {
                val bound=a.d-a.x*localX-a.z*localZ
                if(a.y==0L) { if(bound < -1e-7) return@mapNotNull null }
                else if(a.y>0) high=minOf(high,bound/a.y) else low=maxOf(low,bound/a.y)
            }
            if(high>=low-1e-7) placement.pos.y*16+high else null
        }
    }.maxOrNull() ?: error("No geometry at $x / $z")
    @Test fun `facade segment height makes a partial hip above the gable`() {
        for(template in listOf(false,true)) {
            val g=graph(template=template);assertEquals(18,g.schema)
            val p=plan(g)
            assertEquals(34.0,topAt(p,4.0,0.0),1e-7)
            assertEquals(66.0,topAt(p,4.0,2.0),1e-7)
            val facade=p.placements.filter { "minecraft:red_concrete" in it.surfaces.values }
            assertTrue(facade.isNotEmpty())
            assertTrue(facade.all { placement->placement.pos.y*16+Geometry.exact(placement.parts.filter { part->placement.surfaces[part.group]=="minecraft:red_concrete" }).cells.flatMap { it.vertices() }.maxOf { it.y.toDouble() }<=32.000001 })
            assertEquals(g.nodes.first { it.id=="template" },Json.decode<Building>(Json.encode(Building(graph=g))).graph!!.nodes.first { it.id=="template" })
        }
    }
    @Test fun `taller facades reach a complete gable and one roof edge produces a pent`() {
        assertEquals(66.0,topAt(plan(graph(height=6)),4.0,0.0),1e-7)
        val p=plan(graph(height=8,modes=setOf(0,1,2)))
        assertEquals(18.0,topAt(p,1.0,5.0),1e-7)
        assertEquals(114.0,topAt(p,7.0,5.0),1e-7)
    }
    @Test fun `every actual edge gets an independent stable facade socket`() {
        val g=graph();val n=g.resultNode();val keys=BuildingPorts.of(n,g).filter { it.key.startsWith("semantic:roofEdge:") }
        assertEquals(2,keys.size);assertTrue(keys.all { it.valueType=="WALL" && BuildingKind.SEGMENT in it.accepts && BuildingKind.COPYCAT !in it.accepts })
        val first=n.roofEdges!!.first { it.mode==RoofEdgeMode.FACADE }
        val switched=g.replace(n.copy(roofEdges=n.roofEdges!!.map { if(it.id==first.id) it.copy(mode=RoofEdgeMode.ROOF) else it }))
        assertEquals(1,BuildingPorts.of(switched.resultNode(),switched).count { it.key.startsWith("semantic:roofEdge:") })
        assertEquals(2,BuildingPorts.of(switched.resultNode(),switched,includeInactive=true).count { it.key.startsWith("semantic:roofEdge:") && it.source.isNotEmpty() })
        assertTrue(g.remove("facade").resultNode().roofEdges!!.all { it.source.isEmpty() })
    }
    @Test fun `holes and reentrant edges remain open with exact rational slopes`() {
        val cells=(0 until 7).flatMap { x->(0 until 7).filter { z->x !in 2..4 || z !in 2..4 }.map { z->P(x-3,0,z-3) } }
        val g=graph(modes=emptySet(),points=cells,slope=RoofSlope(2,3));assertEquals(8,RoofEdges.runs(cells.toSet()).size)
        val p=plan(g);assertTrue(p.placements.isNotEmpty());assertTrue(p.placements.all { it.pos.copy(y=0) in cells })
        assertEquals(plan(g),plan(Json.decode<Building>(Json.encode(Building(graph=g))).graph!!))
    }
    @Test fun `missing facade source and dynamic height give edge-specific diagnostics`() {
        val g=graph();val n=g.resultNode()
        val missing=g.replace(n.copy(roofEdges=n.roofEdges!!.map { if(it.mode==RoofEdgeMode.FACADE) it.copy(source="") else it }))
        assertTrue(assertFails { plan(missing) }.message.orEmpty().contains("Fassadenkante"))
        val dynamic=g.replace(g.nodes.first { it.id=="facade" }.copy(dynamicAxes=setOf(RotationAxis.Y)))
        assertTrue(assertFails { plan(dynamic) }.message.orEmpty().contains("Segmenthöhe muss fest"))
    }
    @Test fun `read-only model measures the real segment rather than preview dimensions`() {
        var g=graph().replace(graph().nodes.first { it.id=="wall_area" }.copy(height=3))
        g=g.replace(g.nodes.first { it.id=="facade" }.copy(height=9))
        val ev=BuildingGraphCompiler.Evaluation(g,Building().palette,emptyMap(),emptyMap())
        val model=ev.roofEdgeModel("result")
        assertEquals(listOf(8,10,8,10),model.edges.map { it.length })
        assertTrue(model.edges.filter { it.mode==RoofEdgeMode.FACADE }.all { it.height==3 && it.message.isEmpty() })
        assertEquals(50.0,topAt(ev.preview("result"),4.0,0.0),1e-7)
        assertTrue(model.edges.all { e->kotlin.math.abs(e.end.x-e.start.x)+kotlin.math.abs(e.end.z-e.start.z)==e.length })
    }
    @Test fun `all twelve cross-outline runs retain the footprint instead of filling the notches`() {
        val cells=(0..6).flatMap { z->(if(z in 2..4) -1..3 else 0..2).map { x->P(x,0,z) } }
        val g=graph(modes=emptySet(),points=cells)
        assertEquals(12,RoofEdges.runs(cells.toSet()).size)
        val p=plan(g)
        assertTrue(p.placements.all { it.pos.copy(y=0) in cells })
        assertTrue(cells.all { q->p.placements.any { it.pos.copy(y=0)==q } })
        assertEquals(26.0,topAt(p,1.5,3.5),1e-7)
    }

    @Test fun `rational slopes retain the facade cap and outward roof extension`() {
        val g=graph(height=1,slope=RoofSlope(3,2));val p=plan(g)
        assertEquals(18.0,topAt(p,4.0,0.0),1e-7)
        assertEquals(42.0,topAt(p,4.0,1.0),1e-7)
        val original=graph();val roof=original.resultNode()
        val extended=plan(original.replace(roof.copy(roofSettings=roof.roofSettings!!.copy(overhang16=16))))
        assertEquals(-6.0,topAt(extended,-0.5,5.0),1e-7)
        assertEquals(26.0,topAt(extended,4.0,-0.5),1e-7)
    }
    @Test fun `frozen facade reference and group copies remap sources without changing edge identities`() {
        val g=graph();val n=g.resultNode()
        val definition=g.copy(nodes=g.nodes.filterNot { it.id==n.id }+GraphPrograms.newResult("WALL").copy(id="result",resultMode=GraphResultMode.VALUE,resultSource="facade")).withCurrentSchema()
        val ref=BuildingNode("facade_ref",BuildingKind.GRAPH_REFERENCE,valueType="WALL",referenceKey="wall@1")
        val referenced=g.copy(nodes=g.nodes+ref,graphDefinitions=mapOf("wall@1" to GraphDefinition(Building(schema=2,graph=definition)))).replace(n.copy(roofEdges=n.roofEdges!!.map { if(it.mode==RoofEdgeMode.FACADE) it.copy(source=ref.id) else it }))
        assertEquals(plan(g),plan(referenced))
        val roof=GraphPrograms.producer(n).copy(id="roof",resultType=null,resultMode=null,producerKind=null)
        val grouped=GraphGroups.create(g.copy(nodes=g.nodes+roof),setOf("roof","facade"),"Kantendach")
        val copied=GraphGroups.instantiate(grouped,grouped.nodeGroups.single().id)
        val members=copied.nodeGroups.last().nodes.map { id->copied.nodes.first { it.id==id } }
        val copiedRoof=members.single { it.kind==BuildingKind.ROOF };val copiedFacade=members.single { it.kind==BuildingKind.SEGMENT }
        assertEquals(n.roofEdges!!.map { it.id },copiedRoof.roofEdges!!.map { it.id })
        assertTrue(copiedRoof.roofEdges!!.filter { it.mode==RoofEdgeMode.FACADE }.all { it.source==copiedFacade.id })
        val removed=copied.remove(copiedFacade.id);BuildingGraphCompiler.structure(removed)
        assertTrue(removed.nodes.first { it.id==copiedRoof.id }.roofEdges!!.all { it.source.isEmpty() })
    }

    @Test fun `translated facade sources align their period at the roof base`() {
        val g=graph();val n=g.resultNode()
        val offset=BuildingNode("shift",BuildingKind.POSITION,translation=P(-3,5,-2))
        val moved=BuildingNode("moved",BuildingKind.SEGMENT_OPERATOR,operation="TRANSLATE",inputs=listOf("facade",offset.id))
        val shifted=g.copy(nodes=g.nodes+offset+moved).replace(n.copy(roofEdges=n.roofEdges!!.map { if(it.mode==RoofEdgeMode.FACADE) it.copy(source=moved.id) else it }))
        val a=plan(g);val b=plan(shifted)
        assertEquals(a.raw,b.raw)
        val actual=b.placements.associateBy { it.pos }
        for(p in a.placements) assertTrue(p.sameVisual(actual.getValue(p.pos)),"Translated source changed facade geometry at ${p.pos}")
    }

}
