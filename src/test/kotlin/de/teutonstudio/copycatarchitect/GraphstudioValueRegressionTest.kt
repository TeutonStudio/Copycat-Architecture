package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class GraphstudioValueRegressionTest {
    @Test fun `vector composition extraction and arithmetic feed position operators`() {
        val nodes=listOf(GraphNode("source",part=Part("copycats:copycat_byte")),
            GraphNode("a",NodeKind.INT,countValue=2),GraphNode("b",NodeKind.INT,countValue=3),
            GraphNode("sum",NodeKind.INT_ADD,listOf("a","b")),
            GraphNode("vector",NodeKind.POSITION,listOf("sum","a","b")),
            GraphNode("x",NodeKind.POSITION_X,listOf("vector")),
            GraphNode("move",NodeKind.TRANSLATE,listOf("source","vector")),GraphNode("result",NodeKind.OUTPUT,listOf("move")))
        val graph=NodeGraph(nodes=nodes).withTransformSchema()
        assertEquals(5,graph.schema)
        assertEquals(P(5,2,3),GraphValues.shape(nodes.associateBy { it.id }).vector("vector"))
        assertEquals(5,GraphValues.shape(nodes.associateBy { it.id }).integer("x"))
        assertEquals(512,GraphCompiler.compile(graph).let { Geometry.resolve(it,DEFAULT_GROUPS).solid.count() })
    }
    @Test fun `integer division overflow cycles and absent inputs are explicit diagnostics`() {
        fun evaluate(kind:NodeKind,a:Int,b:Int)=GraphValues.shape(listOf(GraphNode("a",NodeKind.INT,countValue=a),GraphNode("b",NodeKind.INT,countValue=b),GraphNode("op",kind,listOf("a","b"))).associateBy { it.id }).integer("op")
        assertEquals(-2,evaluate(NodeKind.INT_DIVIDE,-5,2))
        assertFailsWith<IllegalArgumentException> { evaluate(NodeKind.INT_DIVIDE,2,0) }
        assertFailsWith<IllegalArgumentException> { evaluate(NodeKind.INT_ADD,Int.MAX_VALUE,1) }
        assertFailsWith<GraphValues.IncompleteValue> { GraphValues.shape(mapOf("op" to GraphNode("op",NodeKind.INT_ADD,listOf("","")))).integer("op") }
    }
    @Test fun `new rotation and mirror nodes have valid world axes and typed sockets`() {
        assertEquals(RotationAxis.Y,GraphModels.newShape(NodeKind.ROTATE).rotationAxis)
        assertEquals(RotationAxis.Y,GraphModels.newShape(NodeKind.ROTATION).rotationAxis)
        assertEquals(2,GraphModels.newShape(NodeKind.MIRROR).inputs.size)
        assertEquals(3,GraphModels.newShape(NodeKind.POSITION).inputs.size)
        val legacy=GraphNode("mirror",NodeKind.MIRROR,listOf(""))
        assertEquals(2,legacy.withValueSockets().inputs.size)
    }
    @Test fun `legacy normal rotations keep geometry after adding a modern parameter`() {
        val nodes=listOf(GraphNode("source",part=Part("copycats:copycat_slice",turn=1)),GraphNode("rotate",NodeKind.ROTATE,listOf("source",""),quarterTurns=1),GraphNode("result",NodeKind.OUTPUT,listOf("rotate")))
        val old=NodeGraph(schema=2,nodes=nodes)
        val upgraded=old.copy(nodes=nodes+GraphNode("point",NodeKind.POSITION)).withTransformSchema()
        assertEquals(4,upgraded.schema)
        assertEquals(Geometry.resolve(GraphCompiler.compile(old),DEFAULT_GROUPS).solid.mask(),Geometry.resolve(GraphCompiler.compile(upgraded),DEFAULT_GROUPS).solid.mask())
    }
    @Test fun `new value functions require current schemas and never downgrade an existing document`() {
        val old=NodeGraph(nodes=listOf(GraphNode("a",NodeKind.INT,countValue=1),GraphNode("result",NodeKind.OUTPUT,listOf(""))))
        assertFailsWith<IllegalArgumentException> { GraphCompiler.structure(old,DEFAULT_GROUPS) }
        assertEquals(5,old.withTransformSchema().schema)
        val graph=BuildingGraph.explicitStarter().copy(nodes=BuildingGraph.explicitStarter().nodes+BuildingNode("sum",BuildingKind.INT_ADD,inputs=listOf("","")))
        assertFailsWith<IllegalArgumentException> { BuildingGraphCompiler.structure(graph) }
        assertEquals(6,graph.withCurrentSchema().schema)
        assertEquals(6,graph.withCurrentSchema().copy(nodes=graph.nodes.filter { it.id!="sum" }+BuildingNode("position",BuildingKind.POSITION)).withCurrentSchema().schema)
        val vectorNodes=mapOf("point" to GraphNode("point",NodeKind.POSITION,listOf("","",""),translation=P(-1,0,0)))
        assertFailsWith<IllegalArgumentException> { GraphValues.shape(vectorNodes).vector("point") }
        val cycle=mapOf("cycle" to GraphNode("cycle",NodeKind.INT_ADD,listOf("cycle","cycle")))
        assertFailsWith<IllegalArgumentException> { GraphValues.shape(cycle).integer("cycle") }
    }
    @Test fun `column modes distinguish overlapping rotations from adjacent blocks`() {
        val form=Form(id="block",parts=listOf(Part("copycats:copycat_block")))
        val graph=BuildingGraph.explicitStarter()
        val column=BuildingNode("column",BuildingKind.ROTATION_SEGMENT,inputs=listOf("wall_copycat"),length=3,columnMode=RotationSegmentMode.OVERLAY)
        val source=graph.nodes.first { it.id=="wall_copycat" }.copy(form="block@1")
        fun plan(mode:RotationSegmentMode)=BuildingGraphCompiler.Evaluation(graph.copy(nodes=graph.nodes.map { if(it.id==source.id) source else it }+column.copy(columnMode=mode)).withCurrentSchema(),Building().palette,mapOf("block@1" to form),emptyMap()).preview("column")
        assertEquals(3,plan(RotationSegmentMode.OVERLAY).placements.size)
        assertEquals(12,plan(RotationSegmentMode.ADJACENT).placements.size)
        assertEquals(2,plan(RotationSegmentMode.ADJACENT).placements.map { it.pos.x }.distinct().size)
    }
}
