package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class UnifiedOperatorsTest {
    private val full=Part("copycats:copycat_block")
    private val slab=Part("copycats:copycat_slab")
    private val forms=mapOf("full@1" to Form(id="full",parts=listOf(full)),"slab@1" to Form(id="slab",parts=listOf(slab)))
    private fun shape(operation:String,a:Part=full,b:Part=slab):NodeGraph {
        val arity=UnifiedOperators.spec("COPYCAT_OPERATOR",operation).min
        return NodeGraph(schema=6,nodes=listOf(GraphNode("a",part=a),GraphNode("b",part=b),
            GraphNode("op",NodeKind.COPYCAT_OPERATOR,List(arity) { if(it==0) "a" else "b" },operation=operation),GraphNode("result",NodeKind.OUTPUT,listOf("op"))))
    }
    private fun segment(id:String,form:String="full@1",length:Int=1,height:Int=1)=BuildingNode(id,BuildingKind.SEGMENT,module="",form=form,length=length,height=height)
    private fun operator(id:String,operation:String,vararg inputs:String)=GraphModels.newBuilding(BuildingKind.SEGMENT_OPERATOR,true).copy(id=id,operation=operation,inputs=inputs.toList())
    private fun graph(vararg nodes:BuildingNode)=BuildingGraph.starter().let { it.copy(schema=9,nodes=it.nodes+nodes) }
    private fun evaluation(graph:BuildingGraph)=BuildingGraphCompiler.Evaluation(graph,Building().palette,forms,emptyMap())

    @Test fun `canonical Copycat set operations equal their legacy operators and preserve invert complement`() {
        for(operation in listOf("UNION","INTERSECTION","DIFFERENCE","SYMMETRIC_DIFFERENCE")) {
            val current=shape(operation)
            val legacy=current.copy(schema=2,nodes=current.nodes.map { if(it.id=="op") it.copy(kind=NodeKind.valueOf(operation),operation=null) else it })
            assertTrue(Geometry.exact(GraphCompiler.intermediate(current,"op")).equivalent(Geometry.exact(GraphCompiler.intermediate(legacy,"op"))),operation)
        }
        val inverse=GraphCompiler.intermediate(shape("INVERT",slab),"op")
        assertTrue(Geometry.exact(inverse).equivalent(ExactGeometry.part(full).minus(ExactGeometry.part(slab))))
        assertTrue(GraphCompiler.intermediate(shape("INVERT",full),"op").isEmpty())
    }

    @Test fun `segment boolean results equal pairwise Copycat results including absent cells and exact cuts`() {
        for(operation in listOf("UNION","INTERSECTION","DIFFERENCE","SYMMETRIC_DIFFERENCE")) {
            val a=segment("a",length=2);val b=segment("b","slab@1")
            val op=operator("op",operation,"a","b")
            val e=evaluation(graph(a,b,op))
            val actual=e.preview("op").placements.associateBy { it.pos }
            val expected=Geometry.exact(GraphCompiler.intermediate(shape(operation),"op"))
            if(expected.isEmpty()) assertNull(actual[P()]) else assertTrue(expected.equivalent(Geometry.exact(actual.getValue(P()).parts)),operation)
            assertEquals(operation!="INTERSECTION",P(1,0,0) in actual,operation)
        }
        // Rational sloped geometry must remain exact, rather than falling back to voxel occupancy.
        val slope=Form(id="slope",parts=listOf(full.copy(exact=ExactVolume.convex(listOf(ExactPlane(0,3,-2,1))))))
        val a=segment("a");val b=segment("b","slope@1");val op=operator("op","DIFFERENCE","a","b")
        val e=BuildingGraphCompiler.Evaluation(graph(a,b,op),Building().palette,forms+("slope@1" to slope),emptyMap())
        val expected=ExactGeometry.part(full).minus(Geometry.exact(slope.parts))
        assertTrue(expected.equivalent(Geometry.exact(e.preview("op").placements.single().parts)))
    }

    @Test fun `segment translations retain the common frame through chained set operations`() {
        val a=segment("a",length=2);val b=segment("b","slab@1")
        val move=operator("move","TRANSLATE","a","").copy(translation=P(-1,0,0))
        val op=operator("op","DIFFERENCE","move","b")
        val e=evaluation(graph(a,b,move,op))
        assertEquals(P(-1,0,0),e.segment("move").origin)
        val actual=e.preview("op").placements.associateBy { it.pos }
        assertEquals(setOf(P(-1,0,0),P()),actual.keys)
        assertTrue(Geometry.exact(actual.getValue(P(-1,0,0)).parts).equivalent(ExactGeometry.part(full)))
        assertTrue(Geometry.exact(actual.getValue(P()).parts).equivalent(ExactGeometry.part(full).minus(ExactGeometry.part(slab))))
    }

    @Test fun `native blocks bypass every segment operator and win collisions`() {
        val a=segment("a");val native=segment("native").copy(block="minecraft:glass")
        for(operation in listOf("UNION","INTERSECTION","DIFFERENCE","SYMMETRIC_DIFFERENCE")) {
            val e=evaluation(graph(a,native,operator("op",operation,"a","native")))
            assertEquals(listOf("minecraft:glass"),e.preview("op").placements.map { it.block },operation)
            assertEquals(P(),e.preview("op").placements.single().pos)
        }
        for(operation in listOf("ROTATE","MIRROR","TRANSLATE")) {
            val op=operator("op",operation,"native","").copy(quarterTurns=1,translation=P(5,-2,1))
            val output=evaluation(graph(native,op)).preview("op").placements.single()
            assertEquals(P(),output.pos,operation);assertEquals("minecraft:glass",output.block)
        }
        val other=native.copy(id="other",block="minecraft:bricks")
        assertFails { evaluation(graph(native,other,operator("op","UNION","native","other"))).preview("op") }
    }

    @Test fun `building Copycat operators use the same exact shape engine and dynamic typed ports`() {
        val a=BuildingNode("a",BuildingKind.COPYCAT,form="full@1")
        val b=a.copy(id="b",form="slab@1")
        for(operation in listOf("UNION","INTERSECTION","DIFFERENCE","SYMMETRIC_DIFFERENCE","INVERT")) {
            val op=GraphModels.newBuilding(BuildingKind.COPYCAT_OPERATOR,true).copy(id="op",operation=operation,inputs=if(operation=="INVERT") listOf("b") else listOf("a","b"))
            val result=evaluation(graph(a,b,op)).preview("op").placements
            val expected=Geometry.exact(GraphCompiler.intermediate(shape(operation,if(operation=="INVERT") slab else full),"op"))
            assertTrue(expected.equivalent(result.fold(ExactVolume()) { total,p -> total.union(Geometry.exact(p.parts)) }),operation)
        }
    }

    @Test fun `all new catalog operations instantiate with their declared input counts and round trip`() {
        val gson=Gson()
        for(kind in NodeKind.entries.filter { it.name in UnifiedOperators.kinds }) {
            val initial=GraphModels.newShape(kind)
            assertEquals(UnifiedOperators.spec(kind.name,initial.operation).min,initial.inputs.size)
            assertEquals(initial,gson.fromJson(gson.toJson(initial),GraphNode::class.java))
            for((operation,spec) in UnifiedOperators.family(kind.name).operations) {
                val node=initial.copy(id="op",operation=operation,inputs=List(spec.min) { "" })
                val g=NodeGraph(schema=6,nodes=listOf(node,GraphNode("result",NodeKind.OUTPUT,listOf(""))))
                GraphCompiler.structure(g,DEFAULT_GROUPS)
            }
        }
        for(kind in BuildingKind.entries.filter { it.name in UnifiedOperators.kinds }) {
            val initial=GraphModels.newBuilding(kind,true).copy(id="op")
            for((operation,spec) in UnifiedOperators.family(kind.name).operations) BuildingGraphCompiler.structure(graph(initial.copy(operation=operation,inputs=List(spec.min) { "" })))
        }
    }

    @Test fun `relations support local comparisons and divisibility and statements have proper truth tables`() {
        val a=GraphNode("a",NodeKind.NUMBER_OPERATOR,operation="VALUE",numberValue=12)
        val relation=GraphNode("relation",NodeKind.RELATION_OPERATOR,listOf("a",""),operation="DIVISIBLE",comparisonValue=3)
        fun evaluate(vararg nodes:GraphNode)=UnifiedOperators.value(UnifiedOperators.valueNodes(nodes.toList()),nodes.last().id)
        assertEquals(true,evaluate(a,relation));assertEquals(false,evaluate(a,relation.copy(comparisonValue=5)))
        assertFails { evaluate(a,relation.copy(comparisonValue=0)) }
        for(x in listOf(false,true)) for(y in listOf(false,true)) {
            val left=GraphNode("left",NodeKind.STATEMENT_OPERATOR,operation="VALUE",booleanValue=x)
            val right=left.copy(id="right",booleanValue=y)
            for((op,expected) in mapOf("AND" to (x && y),"OR" to (x || y),"XOR" to (x xor y),"IMPLIES" to (!x || y),"EQUIVALENT" to (x==y)))
                assertEquals(expected,evaluate(left,right,GraphNode("op",NodeKind.STATEMENT_OPERATOR,listOf("left","right"),operation=op)),"$op $x $y")
        }
    }

    @Test fun `value arithmetic checks overflow and uses truncating signed division`() {
        fun calculate(op:String,a:Int,b:Int):Any {
            val left=GraphNode("a",NodeKind.NUMBER_OPERATOR,operation="VALUE",numberValue=a);val right=left.copy(id="b",numberValue=b)
            return UnifiedOperators.value(UnifiedOperators.valueNodes(listOf(left,right,GraphNode("op",NodeKind.NUMBER_OPERATOR,listOf("a","b"),operation=op))),"op")
        }
        assertEquals(-2,calculate("DIVIDE",-7,3));assertEquals(-1,calculate("REMAINDER",-7,3))
        assertFails { calculate("ADD",Int.MAX_VALUE,1) };assertFails { calculate("DIVIDE",Int.MIN_VALUE,-1) };assertFails { calculate("DIVIDE",1,0) }
    }
}
