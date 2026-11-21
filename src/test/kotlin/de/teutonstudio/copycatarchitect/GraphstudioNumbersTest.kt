package de.teutonstudio.copycatarchitect
import de.teutonstudio.copycatarchitect.domain.*
import kotlin.test.*

class GraphstudioNumbersTest {
    private fun graph(vararg values:BuildingNode)=GraphCards.starter("INT").let { g->g.copy(schema=13,nodes=g.nodes.filter {it.id!="source"}.map {if(it.resultType!=null)it.copy(inputs=listOf(values.last().id)) else it}+values) }
    private fun value(k:BuildingKind,id:String,n:Int)=BuildingNode(id,k,countValue=n)
    private fun evaluate(g:BuildingGraph,id:String)=GraphValues.building(GraphNumbers.buildingContext(g)).integer(id)
    @Test fun `operator types follow operand types and never the current signs`() {
        for(a in listOf(BuildingKind.COUNT,BuildingKind.INT)) for(b in listOf(BuildingKind.COUNT,BuildingKind.INT)) for(op in listOf("ADD","SUBTRACT","MULTIPLY","DIVIDE","REMAINDER","MIN","MAX")) {
            val g=graph(value(a,"a",6),value(b,"b",3),BuildingNode("op",BuildingKind.valueOf("INT_$op"),inputs=listOf("a","b")))
            BuildingGraphCompiler.structure(g)
            assertEquals(if(a==BuildingKind.COUNT && b==BuildingKind.COUNT && op!="SUBTRACT") "COUNT" else "INT",GraphTypes.building(g,"op"))
        }
        val open=graph(value(BuildingKind.COUNT,"a",6),BuildingNode("op",BuildingKind.INT_ADD,inputs=listOf("a","")))
        assertNull(GraphTypes.building(open,"op"));BuildingGraphCompiler.structure(open)
        assertFails { evaluate(open,"op") }
    }
    @Test fun `absolute value and explicit count conversion preserve magnitude and reject overflow and negatives`() {
        for(n in listOf(-8,0,8,Int.MAX_VALUE,Int.MIN_VALUE)) {
            val g=graph(value(BuildingKind.INT,"a",n),BuildingNode("op",BuildingKind.INT_ABS,inputs=listOf("a")))
            assertEquals("COUNT",GraphTypes.building(g,"op"))
            if(n==Int.MIN_VALUE) assertFails { evaluate(g,"op") } else assertEquals(kotlin.math.abs(n),evaluate(g,"op"))
            val conversion=g.replace(g.nodes.last().copy(kind=BuildingKind.INT_TO_COUNT))
            if(n<0) assertFails { evaluate(conversion,"op") } else assertEquals(n,evaluate(conversion,"op"))
        }
    }
    @Test fun `integer division and remainder truncate toward zero for all signs`() {
        for(a in listOf(-7,7)) for(b in listOf(-3,3)) for(op in listOf(BuildingKind.INT_DIVIDE,BuildingKind.INT_REMAINDER)) {
            val g=graph(value(BuildingKind.INT,"a",a),value(BuildingKind.INT,"b",b),BuildingNode("op",op,inputs=listOf("a","b")))
            assertEquals(if(op==BuildingKind.INT_DIVIDE) a/b else a-(a/b)*b,evaluate(g,"op"))
        }
        for(op in listOf(BuildingKind.INT_DIVIDE,BuildingKind.INT_REMAINDER)) for(pair in listOf(7 to 0,Int.MIN_VALUE to -1)) {
            assertFails { evaluate(graph(value(BuildingKind.INT,"a",pair.first),value(BuildingKind.INT,"b",pair.second),BuildingNode("op",op,inputs=listOf("a","b"))),"op") }
        }
    }
    @Test fun `negative position components become repetition counts through absolute value`() {
        val pos=BuildingNode("position",BuildingKind.POSITION,translation=P(-3,0,-2))
        val x=BuildingNode("x",BuildingKind.POSITION_X,inputs=listOf(pos.id))
        val abs=BuildingNode("magnitude",BuildingKind.INT_ABS,inputs=listOf(x.id))
        val source=BuildingNode("copy",BuildingKind.COPYCAT,inlinePart=Part("copycats:copycat_block"))
        val column=BuildingNode("column",BuildingKind.ROTATION_SEGMENT,inputs=listOf(source.id),dimensions=mapOf("length" to abs.id))
        val g=graph(pos,x,abs,source,column).replace(graph(pos,x,abs,source,column).resultNode().copy(resultType="WALL",inputs=listOf(column.id)))
        BuildingGraphCompiler.structure(g)
        assertEquals(-3,evaluate(g,"x"));assertEquals(3,evaluate(g,"magnitude"))
        assertEquals(3,BuildingGraphCompiler.Evaluation(g,Building().palette,emptyMap(),emptyMap()).segment(column.id).height)
        assertFails { g.connect(column.id,"length",x.id) }
    }
    @Test fun `conditional numeric joins comparisons and widening accept both types`() {
        val g=graph(value(BuildingKind.INT,"a",-2),value(BuildingKind.COUNT,"b",5),BuildingNode("flag",BuildingKind.BOOLEAN),BuildingNode("op",BuildingKind.CONDITIONAL,inputs=listOf("a","flag","b")))
        BuildingGraphCompiler.structure(g);assertEquals("INT",GraphTypes.building(g,"op"));assertEquals(5,evaluate(g,"op"))
        val cmp=g.copy(nodes=g.nodes+BuildingNode("compare",BuildingKind.INT_COMPARE,inputs=listOf("a","b"),operation="LESS"))
        assertTrue(GraphValues.building(GraphNumbers.buildingContext(cmp)).boolean("compare"))
    }
    @Test fun `migration keeps signed sources and adds explicit adapters without changing old geometry`() {
        val source=BuildingNode("copy",BuildingKind.COPYCAT,inlinePart=Part("copycats:copycat_block"))
        val number=value(BuildingKind.INT,"number",3)
        val column=BuildingNode("column",BuildingKind.ROTATION_SEGMENT,inputs=listOf(source.id),dimensions=mapOf("length" to number.id))
        val old=graph(source,number,column).copy(schema=8).let { it.copy(nodes=it.nodes.map { n->if(n.resultType!=null)n.copy(resultType="WALL") else n }) }
        fun plan(g:BuildingGraph)=BuildingGraphCompiler.Evaluation(g,Building().palette,emptyMap(),emptyMap()).preview(column.id)
        val before=plan(old)
        val upgraded=old.copy(nodes=old.nodes+value(BuildingKind.COUNT,"new",0)).withCurrentSchema()
        assertEquals(13,upgraded.schema);assertEquals(BuildingKind.INT,upgraded.nodes.first {it.id==number.id}.kind)
        assertEquals(number.id,upgraded.nodes.single {it.kind==BuildingKind.INT_TO_COUNT}.inputs.single())
        assertEquals(before,plan(upgraded));assertEquals(upgraded,upgraded.withCurrentSchema())
        val changed=upgraded.replace(upgraded.nodes.single {it.id=="new"}.copy(countValue=4))
        assertEquals(before,plan(changed))
    }
    @Test fun `concrete type changes release only incompatible count connections`() {
        val count=value(BuildingKind.COUNT,"count",4)
        val area=BuildingNode("area",BuildingKind.AREA,areaMode=AreaMode.SQUARE,dimensions=mapOf("length" to count.id))
        val pos=BuildingNode("pos",BuildingKind.POSITION,inputs=listOf(count.id,"",""))
        val g=graph(count,area,pos).let {it.copy(nodes=it.nodes.map {n->if(n.resultType!=null)n.copy(resultType="POSITION") else n})}
        val changed=g.replace(count.copy(kind=BuildingKind.INT))
        assertEquals("",changed.nodes.first {it.id==area.id}.dimensions!!["length"])
        assertEquals(count.id,changed.nodes.first {it.id==pos.id}.inputs[0])
        BuildingGraphCompiler.structure(changed)
    }

    @Test fun `invalid Count dimensions never become fallback geometry and are repairable`() {
        val count=value(BuildingKind.COUNT,"n",0)
        val area=BuildingNode("area",BuildingKind.AREA,areaMode=AreaMode.SQUARE,dimensions=mapOf("length" to count.id))
        val g=graph(count,area).let { it.copy(nodes=it.nodes.map { n->if(n.resultType!=null)n.copy(resultType="AREA") else n}) }
        BuildingGraphCompiler.structure(g)
        assertFails { g.areaOf(area,true) }
        assertFails { BuildingCounts.resolve(g,area,true) }
        assertEquals(4,g.replace(count.copy(countValue=2)).areaOf(area,true)!!.points.size)
    }
}
