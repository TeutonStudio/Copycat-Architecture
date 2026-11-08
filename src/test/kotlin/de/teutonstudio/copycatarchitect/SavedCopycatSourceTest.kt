package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class SavedCopycatSourceTest {
    private val full=Part("copycats:copycat_block")
    private val slab=Part("copycats:copycat_slab")
    private val blank=Form(id="new_form",name="New form",graph=NodeGraph())
    private val groups=listOf(MaterialGroup(),MaterialGroup("cap","Cap","frame",1))
    private fun split()=Form(id="split",version=7,name="Trim / Split",groups=groups,graph=NodeGraph(nodes=listOf(
        GraphNode("source",part=full),GraphNode("mask",part=slab),
        GraphNode("lower",NodeKind.INTERSECTION,listOf("source","mask"),materialMode=MaterialMode.GROUPED,materialGroup="cap"),
        GraphNode("upper",NodeKind.DIFFERENCE,listOf("source","mask")),
        GraphNode("join",NodeKind.UNION,listOf("upper","lower")),GraphNode("result",NodeKind.OUTPUT,listOf("join")))))
    private fun connected(insert:SavedCopycats.Insertion):Form = insert.form.let { f ->
        f.copy(graph=f.graph!!.replace(f.graph!!.nodes.single { it.kind==NodeKind.OUTPUT }.copy(inputs=listOf(insert.nodeId))))
    }
    private fun assertSame(source:Form,target:Form,mapping:Map<String,String>) {
        val before=Geometry.validate(source);val after=Geometry.validate(target)
        assertTrue(Geometry.exact(before.parts).equivalent(Geometry.exact(after.parts)))
        assertEquals(Extrusion.cost(before.parts,before.groups),Extrusion.cost(after.parts,after.groups))
        val a=Geometry.resolve(before.parts,before.groups);val b=Geometry.resolve(after.parts,after.groups)
        mapping.forEach { (old,new) -> assertEquals(a.owned[old]?.key(),b.owned[new]?.key()) }
    }

    @Test fun `saved multi material shape is a single independent source with exact version and receipts`() {
        val original=split();val json=Gson().toJson(original)
        val inserted=SavedCopycats.insert(blank,original,140,90)
        assertEquals(2,inserted.form.graph!!.nodes.size)
        val node=inserted.form.graph!!.nodes.single { it.kind==NodeKind.SAVED }
        assertEquals("split",node.savedCopycat!!.id);assertEquals(7,node.savedCopycat!!.version)
        assertEquals("Trim / Split",node.savedCopycat!!.name)
        assertEquals(140,node.x);assertEquals(90,node.y)
        assertSame(original,connected(inserted),inserted.groups)
        val parts=Geometry.validate(connected(inserted)).parts
        assertEquals(1,parts.map { it.recipeSource }.distinct().size)
        assertTrue(parts.all { it.recipeSource.isNotEmpty() })
        assertEquals(json,Gson().toJson(original))
    }

    @Test fun `saved difference never restores the removed cutter when read as a new source`() {
        val source=Form(id="cut",graph=NodeGraph(nodes=listOf(GraphNode("full",part=full),GraphNode("mask",part=slab),
            GraphNode("cut",NodeKind.DIFFERENCE,listOf("full","mask")),GraphNode("result",NodeKind.OUTPUT,listOf("cut")))))
        val inserted=SavedCopycats.insert(blank,source)
        assertSame(source,connected(inserted),inserted.groups)
        assertEquals(2048,Geometry.solid(Geometry.validate(connected(inserted)).parts).count())
    }

    @Test fun `nested saved copies flatten and survive JSON without their library`() {
        var source=split()
        repeat(6) { index ->
            val inserted=SavedCopycats.insert(blank,source)
            source=Geometry.validate(connected(inserted).copy(id="nested_$index"))
            assertEquals(2,source.graph!!.nodes.size)
            assertEquals(2,source.graph!!.nodes.single { it.kind==NodeKind.SAVED }.savedCopycat!!.parts.size)
        }
        val json=Gson().toJson(source)
        val restored=Geometry.validate(Gson().fromJson(json,Form::class.java))
        assertEquals(source,restored)
        assertEquals(Extrusion.cost(Geometry.validate(split()).parts,groups),Extrusion.cost(restored.parts,restored.groups))
        assertTrue(json.length<6000)
    }

    @Test fun `saved input supports further difference rotation mirror and material inheritance`() {
        val inserted=SavedCopycats.insert(blank,split())
        val f=connected(inserted)
        val graph=f.graph!!.copy(nodes=f.graph!!.nodes.filter { it.kind!=NodeKind.OUTPUT }+listOf(
            GraphNode("mask",part=Part("copycats:copycat_byte")),
            GraphNode("cut",NodeKind.DIFFERENCE,listOf(inserted.nodeId,"mask")),
            GraphNode("rotate",NodeKind.ROTATE,listOf("cut",""),rotationAxis=RotationAxis.X,quarterTurns=1),
            GraphNode("mirror",NodeKind.MIRROR,listOf("rotate"),mirror=CopycatMirror(CopycatMirrorPlane.Z)),
            GraphNode("result",NodeKind.OUTPUT,listOf("mirror"))))
        val before=GraphCompiler.intermediate(graph,"cut",f.groups)
        val after=GraphCompiler.compile(graph,f.groups)
        val expected=Geometry.exact(before).rotate(RotationAxis.X,1).reflected(CopycatMirrorPlane.Z)
        assertTrue(expected.equivalent(Geometry.exact(after)))
        assertEquals(3584,Geometry.solid(after).count())
        assertEquals(inserted.groups.values.toSet(),GraphCompiler.inputMaterialGroups(graph,graph.nodes.last(),0,f.groups))
        assertEquals(Extrusion.cost(before,f.groups),Extrusion.cost(after,f.groups))
    }

    @Test fun `rational exact cuts remain exact through saved source and another mirror`() {
        val source=Form(id="exact",graph=NodeGraph(nodes=listOf(GraphNode("full",part=full),
            GraphNode("wedge",part=Part(RoofPartParameters.SOURCE,exact=ExactVolume.convex(listOf(ExactPlane(0,3,-2,1))))),
            GraphNode("cut",NodeKind.DIFFERENCE,listOf("full","wedge")),GraphNode("result",NodeKind.OUTPUT,listOf("cut")))))
        val inserted=SavedCopycats.insert(blank,source)
        val f=connected(inserted)
        assertSame(source,f,inserted.groups)
        val node=f.graph!!.nodes.single { it.kind==NodeKind.SAVED }
        val rotated=f.copy(graph=f.graph!!.replace(node.copy(rotationAxis=RotationAxis.Z,quarterTurns=1)))
        val after=Geometry.validate(rotated)
        assertEquals(4,after.schema);assertEquals(3,after.graph!!.schema)
        assertTrue(Geometry.exact(Geometry.validate(source).parts).rotate(RotationAxis.Z,1).equivalent(Geometry.exact(after.parts)))
        assertEquals(after,Geometry.validate(Gson().fromJson(Gson().toJson(after),Form::class.java)))
    }

    @Test fun `assigning a common material merges saved receipts without losing geometry`() {
        val inserted=SavedCopycats.insert(blank,split());val f=connected(inserted)
        val graph=GraphSelectionEdits.assignGroup(f.graph!!,setOf(inserted.nodeId),"body")
        val after=Geometry.validate(f.copy(graph=graph))
        assertEquals(4096,Geometry.solid(after.parts).count())
        assertEquals(setOf("body"),after.parts.map { it.group }.toSet())
        assertEquals(Extrusion.cost(listOf(full)),Extrusion.cost(after.parts,after.groups))
    }

    @Test fun `saved placement turn is baked once and each instance namespaces its receipts`() {
        val source=Form(id="byte",parts=listOf(Part("copycats:copycat_byte")))
        val a=SavedCopycats.insert(blank,source,turn=1)
        assertTrue(Geometry.solid(Geometry.validate(connected(a)).parts).key()==Solid.part(source.parts.single()).rotate(1).key())
        val b=SavedCopycats.insert(a.form,split())
        val c=SavedCopycats.insert(b.form,split())
        val bParts=GraphCompiler.intermediate(c.form.graph!!,b.nodeId,c.form.groups)
        val cParts=GraphCompiler.intermediate(c.form.graph!!,c.nodeId,c.form.groups)
        assertTrue(bParts.map { it.recipeSource }.intersect(cParts.map { it.recipeSource }.toSet()).isEmpty())
    }

    @Test fun `replacing a saved source keeps its links position and rotation without leaking groups`() {
        val inserted=SavedCopycats.insert(blank,split(),100,200);var f=connected(inserted)
        val node=f.graph!!.nodes.single { it.kind==NodeKind.SAVED }
        f=f.copy(graph=f.graph!!.replace(node.copy(rotationAxis=RotationAxis.X,quarterTurns=1)))
        repeat(10) {
            val replaced=SavedCopycats.insert(f,split(),replaceId=inserted.nodeId)
            f=replaced.form
            assertEquals(3,f.groups.size)
            assertEquals(inserted.nodeId,f.graph!!.nodes.single { it.kind==NodeKind.OUTPUT }.inputs.single())
            val after=f.graph!!.nodes.single { it.kind==NodeKind.SAVED }
            assertEquals(100,after.x);assertEquals(200,after.y);assertEquals(RotationAxis.X,after.rotationAxis);assertEquals(1,after.quarterTurns)
        }
    }

    @Test fun `existing default material order and priority ownership survive insertion`() {
        val target=blank.copy(groups=listOf(MaterialGroup(priority=32),MaterialGroup("low","Low",priority=-32)),graph=NodeGraph(nodes=listOf(
            GraphNode("full",part=full),GraphNode("slab",part=slab.copy(group="low")),
            GraphNode("join",NodeKind.UNION,listOf("full","slab"),materialMode=MaterialMode.GROUPED),GraphNode("result",NodeKind.OUTPUT,listOf("join")))))
        val inserted=SavedCopycats.insert(target,split())
        assertEquals("body",inserted.form.groups.first().id)
        assertEquals("body",GraphCompiler.compile(inserted.form.graph!!,inserted.form.groups).single().group)
        assertEquals(Geometry.solid(Geometry.validate(target).parts).key(),Geometry.solid(Geometry.validate(inserted.form).parts).key())
    }

    @Test fun `two saved shapes combine and remain editable in shared node group instances`() {
        val lower=Form(id="lower",parts=listOf(slab))
        val upper=Form(id="upper",parts=listOf(slab.copy(top=true)))
        val a=SavedCopycats.insert(blank,lower)
        val b=SavedCopycats.insert(a.form,upper)
        var graph=b.form.graph!!.copy(nodes=b.form.graph!!.nodes.filter { it.kind!=NodeKind.OUTPUT }+listOf(
            GraphNode("join",NodeKind.UNION,listOf(a.nodeId,b.nodeId)),GraphNode("result",NodeKind.OUTPUT,listOf("join"))))
        val combined=Geometry.validate(b.form.copy(graph=graph))
        assertEquals(4096,Geometry.solid(combined.parts).count())
        assertEquals(256,Extrusion.cost(combined.parts,combined.groups))
        assertEquals(setOf(a.groups.getValue("body"),b.groups.getValue("body")),combined.parts.map { it.group }.toSet())
        graph=GraphGroups.create(graph,setOf(a.nodeId,b.nodeId,"join"),"Saved assembly")
        val group=graph.nodeGroups.single()
        graph=GraphGroups.instantiate(graph,group.id)
        GraphCompiler.structure(graph,b.form.groups)
        val source=graph.nodes.single { it.id==a.nodeId }
        graph=graph.replace(source.copy(rotationAxis=RotationAxis.Y,quarterTurns=1))
        val copy=graph.nodeGroups.last().nodes.first()
        assertEquals(1,graph.nodes.single { it.id==copy }.quarterTurns)
        assertEquals(4096,Geometry.solid(GraphCompiler.compile(graph,b.form.groups)).count())
        assertEquals(graph,Gson().fromJson(Gson().toJson(graph),NodeGraph::class.java))
    }

    @Test fun `a saved form with eight material groups can fill an empty draft`() {
        val materialGroups=(0..7).map { MaterialGroup("g$it","Group $it",priority=it) }
        val source=Form(id="eight",groups=materialGroups,parts=(0..7).map { Part("copycats:copycat_byte",offset=P((it%2)*8,((it/2)%2)*8,(it/4)*8),group="g$it") })
        val inserted=SavedCopycats.insert(blank,source)
        assertTrue(inserted.form.groups.size<=8)
        assertSame(source,connected(inserted),inserted.groups)
    }

    @Test fun `invalid snapshots and group or source limits reject atomically`() {
        val noData=blank.copy(graph=NodeGraph(nodes=listOf(GraphNode("saved",NodeKind.SAVED),GraphNode("result",NodeKind.OUTPUT,listOf("saved")))))
        assertFailsWith<IllegalArgumentException> { Geometry.validate(noData) }
        val invalid=GraphNode("saved",NodeKind.SAVED,savedCopycat=SavedCopycat(parts=listOf(full.copy(group="missing"))))
        assertFailsWith<IllegalArgumentException> { GraphCompiler.structure(NodeGraph(nodes=listOf(invalid,GraphNode("result",NodeKind.OUTPUT,listOf("saved")))),DEFAULT_GROUPS) }
        val crowded=blank.copy(groups=(0..7).map { MaterialGroup(if(it==0) "body" else "g$it","Group $it",priority=it) })
        assertFailsWith<IllegalArgumentException> { SavedCopycats.insert(crowded,split()) }
        val sources=blank.copy(graph=NodeGraph(nodes=(0..31).map { GraphNode("n$it",part=full) }+GraphNode("result",NodeKind.OUTPUT,listOf("n0"))))
        assertFailsWith<IllegalArgumentException> { SavedCopycats.insert(sources,split()) }
        assertEquals(1,blank.graph!!.nodes.size);assertEquals(DEFAULT_GROUPS,blank.groups)
    }
}
