package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import kotlin.test.*

class GraphstudioAuthoringTest {
    private fun graph(vararg nodes:BuildingNode,type:String="COPYCAT",output:String="pick"):BuildingGraph {
        val base=GraphCards.starter(type)
        return base.copy(nodes=base.nodes.filter { it.id!="source" }.map { if(it.resultType!=null) it.copy(inputs=listOf(output)) else it }+nodes).withCurrentSchema()
    }
    @Test fun `conditional infers every actual type and requires equal branches`() {
        for(kind in BuildingKind.entries.filter { it!=BuildingKind.CONDITIONAL }) {
            val value=GraphModels.newBuilding(kind,true).copy(id="a").let { if(kind.name in GraphNumbers.derived) it.copy(inputs=listOf("number","number")) else it }
            val pick=BuildingNode("pick",BuildingKind.CONDITIONAL,inputs=listOf("a","flag","a"))
            val g=graph(value,pick,BuildingNode("number",BuildingKind.INT,countValue=1),BuildingNode("flag",BuildingKind.BOOLEAN),type=buildingGraphType(kind))
            assertEquals(buildingGraphType(kind),GraphTypes.building(g,"pick"),kind.name)
            assertTrue(GraphTypes.accepts(g,BuildingPorts.of(g.resultNode(),g).single(),pick),kind.name+" → "+BuildingPorts.of(g.resultNode(),g).single().accepts)
        }
        val g=graph(BuildingNode("a",BuildingKind.INT),BuildingNode("b",BuildingKind.POSITION),BuildingNode("pick",BuildingKind.CONDITIONAL,inputs=listOf("a","flag","b")),BuildingNode("flag",BuildingKind.BOOLEAN),type="INT")
        assertFailsWith<IllegalArgumentException> { BuildingGraphCompiler.structure(g) }
    }
    @Test fun `Boolean logic and comparison share lazy conditional arithmetic`() {
        val nodes=listOf(BuildingNode("a",BuildingKind.INT,countValue=7),BuildingNode("b",BuildingKind.INT,countValue=0),
            BuildingNode("bad",BuildingKind.INT_DIVIDE,inputs=listOf("a","b")),BuildingNode("flag",BuildingKind.BOOLEAN,booleanValue=true),
            BuildingNode("pick",BuildingKind.CONDITIONAL,inputs=listOf("a","flag","bad")))
        assertEquals(7,GraphValues.building(nodes.associateBy { it.id }).integer("pick"))
        for(a in listOf(false,true)) for(b in listOf(false,true)) for(op in listOf("AND","OR","XOR")) {
            val g=listOf(BuildingNode("a",BuildingKind.BOOLEAN,booleanValue=a),BuildingNode("b",BuildingKind.BOOLEAN,booleanValue=b),BuildingNode("op",BuildingKind.BOOLEAN_LOGIC,inputs=listOf("a","b"),operation=op))
            assertEquals(when(op){"AND"->a&&b;"OR"->a||b;else->a xor b},GraphValues.building(g.associateBy { it.id }).boolean("op"))
        }
        for(op in listOf("EQUAL","NOT_EQUAL","LESS","LESS_EQUAL","GREATER","GREATER_EQUAL")) {
            val g=nodes.take(2)+BuildingNode("cmp",BuildingKind.INT_COMPARE,inputs=listOf("a","b"),operation=op)
            assertEquals(op in listOf("NOT_EQUAL","GREATER","GREATER_EQUAL"),GraphValues.building(g.associateBy { it.id }).boolean("cmp"))
        }
    }
    @Test fun `selected Copycat geometry ignores the invalid unchosen recipe`() {
        val g=graph(BuildingNode("a",BuildingKind.COPYCAT,inlinePart=Part("copycats:copycat_block")),
            BuildingNode("bad",BuildingKind.COPYCAT_DIFFERENCE,inputs=listOf("","")),BuildingNode("flag",BuildingKind.BOOLEAN,booleanValue=true),
            BuildingNode("pick",BuildingKind.CONDITIONAL,inputs=listOf("a","flag","bad")))
        assertEquals(1,BuildingGraphCompiler.Evaluation(g,Building().palette,emptyMap(),emptyMap()).preview("pick").placements.size)
    }
    @Test fun `roof modes share exact geometry and validate only required slots`() {
        val area=BuildingNode("area",BuildingKind.AREA,areaMode=AreaMode.RECTANGLE,length=5,height=5)
        val copy=BuildingNode("copy",BuildingKind.COPYCAT,inlinePart=Part("copycats:copycat_block"))
        val settings=RoofTemplateSettings(overhang16=0)
        val template=BuildingNode("template",BuildingKind.ROOF_TEMPLATE,inputs=List(8){"copy"},roofSettings=settings)
        val roof=BuildingNode("roof",BuildingKind.ROOF,geometry="area",roofTemplate="template",roofInputMode=RoofInputMode.TEMPLATE,role="roof")
        val g=graph(area,copy,template,roof,type="ROOF",output="roof")
        fun eval(graph:BuildingGraph)=BuildingGraphCompiler.Evaluation(graph,Building().palette,emptyMap(),emptyMap()).preview("roof")
        val expected=eval(g)
        assertTrue(expected.placements.isNotEmpty())
        val direct=g.replace(roof.copy(roofInputMode=RoofInputMode.SEGMENTS,inputs=template.inputs,roofSettings=settings))
        assertEquals(expected,eval(direct))
        assertFails { eval(g.replace(template.copy(inputs=List(8){""}))) }
        assertEquals(12,g.schema)
    }
    @Test fun `junction classification distinguishes straight curve T X and a point`() {
        assertEquals("ridge",RoofTemplates.junction(setOf(0,2)))
        assertEquals("ridge_curve",RoofTemplates.junction(setOf(0,1)))
        assertEquals("ridge_t",RoofTemplates.junction(setOf(0,1,2)))
        assertEquals("ridge_x",RoofTemplates.junction(setOf(0,1,2,3)))
        assertEquals("peak",RoofTemplates.junction(emptySet(),true))
    }
    @Test fun `shaft height is independently resolved and rotations retain explicit openings`() {
        val n=BuildingNode("shaft",BuildingKind.ELEVATOR_SHAFT_SEGMENT)
        val a=ShaftSegments.generate(n,5,listOf(0)){error("default")}
        val b=ShaftSegments.generate(n,9,listOf(0,5)){error("default")}
        assertEquals(5,a.height);assertEquals(9,b.height)
        assertTrue(b.cells.any { it.cell.pos==P(0,5,-1) && it.cell.empty })
        val rotated=SegmentGeometry.rotated(b,RotationAxis.Y,1)
        assertEquals(b.cells.count { it.cell.empty },rotated.cells.count { it.cell.empty })
        val op=BuildingNode("xor",BuildingKind.SEGMENT_SYMMETRIC_DIFFERENCE,inputs=listOf("shaft","shaft"))
        assertTrue(SegmentBooleans.evaluate(op,listOf(a,a),mutableMapOf(),Building().palette).cells.isEmpty())
    }

    @Test fun `actual hip roof contours choose point curve T and X templates`() {
        fun rectangle(x:Int,z:Int,w:Int,d:Int)=buildSet { for(a in x until x+w) for(b in z until z+d) add(P(a,0,b)) }
        val cases=listOf(
            rectangle(0,0,5,5) to 7,
            (rectangle(0,0,7,3)+rectangle(0,0,3,7)) to 4,
            (rectangle(0,0,7,3)+rectangle(2,0,3,7)) to 5,
            (rectangle(0,2,7,3)+rectangle(2,0,3,7)) to 6)
        val colors=listOf("white","orange","magenta","light_blue","yellow","lime","pink","gray").map { "minecraft:${it}_concrete" }
        for((points,required) in cases) {
            val parts=colors.mapIndexed { i,color->BuildingNode("p$i",BuildingKind.COPYCAT,inlinePart=Part("copycats:copycat_block"),surfaces=mapOf("body" to color)) }
            val area=BuildingNode("area",BuildingKind.AREA,areaMode=AreaMode.DRAW,coordinates=points.toList())
            val roof=BuildingNode("roof",BuildingKind.ROOF,geometry="area",roofInputMode=RoofInputMode.SEGMENTS,inputs=parts.map { it.id },roofSettings=RoofTemplateSettings(form=RoofForm.HIP,overhang16=0),role="roof")
            val g=graph(*(parts+area+roof).toTypedArray(),type="ROOF",output="roof")
            val plan=BuildingGraphCompiler.Evaluation(g,Building().palette,emptyMap(),emptyMap()).preview("roof")
            val materials=plan.placements.flatMap { it.surfaces.values }.toSet()
            assertTrue(colors[required] in materials,"${RoofTemplates.labels[required]} was not selected: $materials")
        }
    }
    @Test fun `shared conditional definitions retain independent external Boolean inputs`() {
        val a=BuildingNode("a",BuildingKind.COPYCAT,inlinePart=Part("copycats:copycat_block"),surfaces=mapOf("body" to "minecraft:gold_block"))
        val b=a.copy(id="b",surfaces=mapOf("body" to "minecraft:iron_block"))
        val p=BuildingNode("pick",BuildingKind.CONDITIONAL,inputs=listOf("a","flag","b"))
        val peer=p.copy(id="peer",inputs=listOf("a","other","b"))
        val flags=listOf(BuildingNode("flag",BuildingKind.BOOLEAN,booleanValue=true),BuildingNode("other",BuildingKind.BOOLEAN,booleanValue=false))
        val face=GraphGroupInterface(inputs=listOf(GraphGroupInput("a","A","COPYCAT",listOf(GraphGroupTarget(0,"in:0"))),GraphGroupInput("condition","Bedingung","BOOLEAN",listOf(GraphGroupTarget(0,"in:1"))),GraphGroupInput("b","B","COPYCAT",listOf(GraphGroupTarget(0,"in:2")))),outputs=listOf(GraphGroupOutput("out","Wert","COPYCAT",0)))
        val g=graph(*(listOf(a,b,p,peer)+flags).toTypedArray()).copy(nodeGroups=listOf(
            GraphGroup("one","Eins",listOf("pick"),definitionId="shared",graphInterface=face,inputConnections=mapOf("a" to "a","condition" to "flag","b" to "b")),
            GraphGroup("two","Zwei",listOf("peer"),definitionId="shared",graphInterface=face,inputConnections=mapOf("a" to "a","condition" to "other","b" to "b"))))
        val e=BuildingGraphCompiler.Evaluation(g,Building().palette,emptyMap(),emptyMap())
        assertEquals("minecraft:gold_block",e.preview("pick").placements.single().surfaces["body"])
        assertEquals("minecraft:iron_block",e.preview("peer").placements.single().surfaces["body"])
    }
    @Test fun `ordinary elevator segments use building floors for doors and cuts`() {
        val building=de.teutonstudio.copycatarchitect.client.StructuralSmoke.fixture()
        val old=building.graph!!.nodes.first { it.kind==BuildingKind.ELEVATOR_SHAFT }
        val changed=building.copy(graph=building.graph!!.replace(old.copy(kind=BuildingKind.ELEVATOR_SHAFT_SEGMENT)))
        val library=de.teutonstudio.copycatarchitect.server.Library().apply { seed() }
        val result=BuildingGraphCompiler.compile(changed,library.forms,library.modules)
        assertEquals(2,result.storeys.size)
        val instance=changed.graph!!.nodes.first { it.accessInstances.any { a->a.source==old.id } }.accessInstances.first { it.source==old.id }
        assertTrue(result.accessFootprints.getValue(instance.id).isNotEmpty())
        assertTrue(result.plan.clear.any { it.copy(y=0) in result.accessFootprints.getValue(instance.id) })
    }

    @Test fun `conditional structured transform values and segment sizing resolve only the chosen value`() {
        val flag=BuildingNode("flag",BuildingKind.BOOLEAN,booleanValue=true)
        val rotation=BuildingNode("r",BuildingKind.ROTATION,rotations=listOf(SegmentRotation(RotationAxis.X,1)))
        val pick=BuildingNode("pick_r",BuildingKind.CONDITIONAL,inputs=listOf("r","flag","r"))
        val transform=BuildingNode("t",BuildingKind.TRANSFORMATION,rotation=pick.id)
        val selected=BuildingNode("pick_t",BuildingKind.CONDITIONAL,inputs=listOf("t","flag","t"))
        val shaft=BuildingNode("shaft",BuildingKind.SERVICE_SHAFT_SEGMENT)
        val broken=BuildingNode("broken",BuildingKind.SEGMENT_ROTATE,inputs=listOf(""))
        val branch=BuildingNode("pick_s",BuildingKind.CONDITIONAL,inputs=listOf("shaft","flag","broken"))
        val op=BuildingNode("move",BuildingKind.SEGMENT_TRANSFORM,inputs=listOf(branch.id),transformationValue=selected.id)
        val g=graph(flag,rotation,pick,transform,selected,shaft,broken,branch,op,type="WALL",output=op.id)
        BuildingGraphCompiler.structure(g)
        assertEquals(setOf(RotationAxis.Z),DynamicSegments.axes(g,op.id))
        assertEquals(CopycatTransformKind.ROTATE,TransformationValues.building(selected,g.nodes.associateBy { it.id }).single().kind)
        assertTrue(BuildingGraphCompiler.Evaluation(g,Building().palette,emptyMap(),emptyMap()).preview(op.id).placements.isNotEmpty())
        val cycle=g.replace(broken.copy(inputs=listOf(branch.id)))
        assertFails { BuildingGraphCompiler.structure(cycle) }
    }
}
