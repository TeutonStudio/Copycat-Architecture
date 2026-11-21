package de.teutonstudio.copycatarchitect
import de.teutonstudio.copycatarchitect.domain.*
import com.google.gson.Gson
import kotlin.test.*

class GraphProgramsTest {
    private fun parameter(id:String="argument",default:String?=null)=BuildingNode(id,BuildingKind.GROUP_INPUT,"Anzahl",valueType="COUNT",defaultSource=default)
    private fun number(id:String,n:Int)=BuildingNode(id,BuildingKind.COUNT,countValue=n)
    private fun valueGraph(vararg nodes:BuildingNode,source:String)=GraphCards.starter("COUNT").let { it.copy(nodes=it.nodes.map { n->if(n.kind==BuildingKind.RESULT)n.copy(resultMode=GraphResultMode.VALUE,resultSource=source) else n }+nodes) }
    private fun number(g:BuildingGraph,id:String)=GraphValues.building(GraphNumbers.buildingContext(g)).integer(id)
    private fun definition(g:BuildingGraph)=GraphDefinition(Building(schema=2,graph=g))
    @Test fun `new graph creates the same exact Copycat directly and retains value mode`() {
        val g=GraphCards.starter();BuildingGraphCompiler.structure(g)
        val direct=BuildingGraphCompiler.Evaluation(g,Building().palette,emptyMap(),emptyMap()).preview("result")
        val old=GraphCards.legacyStarter()
        val indirect=BuildingGraphCompiler.Evaluation(old,Building().palette,emptyMap(),emptyMap()).preview("source")
        assertEquals(indirect.placements.map { it.parts },direct.placements.map { it.parts })
        val toggled=GraphPrograms.configure(GraphPrograms.configure(g,"result","resultMode","VALUE"),"result","resultMode","CONSTRUCT")
        assertEquals(g,toggled)
        assertEquals(14,g.schema)
    }
    @Test fun `all result constructors share their normal native ports`() {
        for(type in GraphCards.types) for(kind in GraphPrograms.constructors(type)) {
            val n=GraphPrograms.newResult(type,kind).copy(id="result")
            val g=GraphCards.starter().let {it.copy(nodes=it.nodes.map {old->if(old.id=="result")n else old})}
            assertEquals(BuildingPorts.of(GraphPrograms.producer(n).copy(resultType=null),g),BuildingPorts.of(n,g),"$type $kind")
        }
        val g=GraphCards.starter("ROOF_TEMPLATE");assertEquals(8,BuildingPorts.of(g.resultNode(),g).size)
    }
    @Test fun `value-mode diagram edits retain inactive producer settings and wires`() {
        val source=BuildingNode("tile",BuildingKind.COPYCAT,inlinePart=Part("copycats:copycat_block"))
        val direct=GraphCards.starter("ROOF_TEMPLATE").let { g->g.copy(nodes=g.nodes.map { if(it.id=="result")it.copy(inputs=List(8){"tile"},roofSettings=RoofTemplateSettings(knee16=5)) else it }+source) }
        val value=GraphPrograms.configure(direct,"result","resultMode","VALUE")
        val moved=DiagramEdits.building(value,value.nodes.map { DiagramPosition(it.id,it.x+10,it.y) },emptyList())
        val restored=GraphPrograms.configure(moved,"result","resultMode","CONSTRUCT").resultNode()
        assertEquals(List(8){"tile"},restored.inputs)
        assertEquals(5,restored.roofSettings!!.knee16)
    }
    @Test fun `all logical parameter contracts remain saveable with open defaults`() {
        for(type in GraphCards.types) {
            val param=parameter().copy(valueType=type)
            val g=GraphCards.starter(type).let { base->base.copy(nodes=base.nodes.map { if(it.kind==BuildingKind.RESULT)it.copy(resultMode=GraphResultMode.VALUE,resultSource=param.id) else it }+param) }
            BuildingGraphCompiler.structure(g)
        }
    }
    @Test fun `instances override defaults lazily with independent arguments and cache`() {
        val bad=BuildingNode("bad",BuildingKind.INT_DIVIDE,inputs=listOf("zero","zero"))
        val child=valueGraph(number("zero",0),bad,parameter(default="bad"),source="argument")
        val a=BuildingNode("a",BuildingKind.GRAPH_REFERENCE,valueType="COUNT",referenceKey="definition@1",arguments=mapOf("argument" to "five"))
        val b=a.copy(id="b",arguments=mapOf("argument" to "nine"))
        val outer=valueGraph(number("five",5),number("nine",9),a,b,source="a").copy(graphDefinitions=mapOf("definition@1" to definition(child)))
        BuildingGraphCompiler.structure(outer)
        assertEquals(5,number(outer,"a"));assertEquals(9,number(outer,"b"))
        val noOverride=outer.replace(a.copy(arguments=emptyMap()))
        assertFails { number(noOverride,"a") }
        assertEquals(outer,Gson().fromJson(Gson().toJson(outer),BuildingGraph::class.java))
    }
    @Test fun `open parameters can be saved and defaults are typed and checked for cycles`() {
        val g=valueGraph(parameter(),source="argument");BuildingGraphCompiler.structure(g)
        assertFails { number(g,"argument") }
        assertFails { BuildingGraphCompiler.structure(g.replace(parameter(default="argument"))) }
        val default=valueGraph(number("five",5),parameter(default="five"),source="argument")
        assertEquals(5,number(default,"argument"))
        assertEquals(listOf("argument"),GraphPrograms.parameters(default).map { it.id })
    }
    @Test fun `typing a parameter prunes incompatible default and argument consumers atomically`() {
        val g=valueGraph(number("five",5),parameter(default="five"),source="argument")
        val changed=GraphPrograms.configure(g,"argument","valueType","COPYCAT")
        assertTrue(changed.nodes.first { it.id=="argument" }.defaultSource.isNullOrEmpty())
        assertTrue(changed.resultNode().resultSource.isNullOrEmpty())
        BuildingGraphCompiler.structure(changed)
        assertEquals("five",g.nodes.first { it.id=="argument" }.defaultSource)
    }
    @Test fun `nested references keep their exact typed roof template`() {
        val child=GraphCards.starter("ROOF_TEMPLATE")
        val ref=BuildingNode("ref",BuildingKind.GRAPH_REFERENCE,valueType="ROOF_TEMPLATE",referenceKey="roof@1")
        val g=GraphCards.starter("ROOF_TEMPLATE").let {it.copy(nodes=it.nodes.map { n->if(n.kind==BuildingKind.RESULT)n.copy(resultMode=GraphResultMode.VALUE,resultSource="ref") else n }+ref,graphDefinitions=mapOf("roof@1" to definition(child)))}
        BuildingGraphCompiler.structure(g)
        assertEquals(BuildingKind.ROOF_TEMPLATE,GraphConditions.buildingNode(g,"ref").kind)
        val nested=g.copy(graphDefinitions=g.graphDefinitions!!+("nested@1" to definition(g)),nodes=g.nodes.map {if(it.id=="ref")ref.copy(referenceKey="nested@1") else it})
        BuildingGraphCompiler.structure(nested)
        assertEquals(BuildingKind.ROOF_TEMPLATE,GraphConditions.buildingNode(nested,"ref").kind)
        val recursive=g.copy(graphDefinitions=mapOf("roof@1" to definition(g)))
        assertFails { BuildingGraphCompiler.structure(recursive) }
    }
}
