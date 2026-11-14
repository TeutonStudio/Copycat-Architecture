package de.teutonstudio.copycatarchitect

import com.google.gson.Gson
import de.teutonstudio.copycatarchitect.domain.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.*

/** Catalog-driven coverage: newly selectable base shapes automatically join the matrix. */
class SymmetricDifferenceMatrixTest {
    private val groups=listOf(MaterialGroup(),MaterialGroup("second","Second","surface",1))
    private fun graph(parts:List<Part>,mode:MaterialMode=MaterialMode.SEPARATED)=NodeGraph(nodes=
        parts.mapIndexed { i,p -> GraphNode("source$i",part=p) }+
            GraphNode("xor",NodeKind.SYMMETRIC_DIFFERENCE,parts.indices.map { "source$it" },
                materialMode=mode,materialGroup="second",inputGroups=parts.indices.map { if(it%2==0) "body" else "second" })+
            GraphNode("result",NodeKind.OUTPUT,listOf("xor")))

    private fun variant(shape:BaseShape,index:Int):Part {
        val adapter=Adapters.get(shape.source)
        return when(index) {
            0->shape.part()
            1->shape.part().copy(turn=1,top=true,size=minOf(3,adapter.max),spin=1)
            2->shape.part().copy(turn=3,tilt=if(adapter.tiltable) 1 else 0,size=adapter.max,spin=2)
            3->{
                val p=shape.part().copy(size=minOf(5,adapter.max),
                    roof=if(shape.source==RoofPartParameters.SOURCE) RoofPartParameters(rise=2,run=3,phase=1) else null)
                val boxes=Solid.part(p).boxes()
                // Shift toward free space while keeping the complete source inside the block.
                fun shift(low:Int,high:Int)=if(low>0) -minOf(2,low) else minOf(2,16-high)
                p.copy(offset=P(shift(boxes.minOf { it.x },boxes.maxOf { it.maxX }),
                    shift(boxes.minOf { it.y },boxes.maxOf { it.maxY }),shift(boxes.minOf { it.z },boxes.maxOf { it.maxZ })))
            }
            else->error("Unknown fixture")
        }
    }

    private fun checkPair(a:Part,b:Part) {
        val exact=ExactGeometry.usesExact(a) || ExactGeometry.usesExact(b)
        val av=if(exact) ExactGeometry.part(a) else null
        val bv=if(exact) ExactGeometry.part(b) else null
        // Independent set identity, rather than copying the compiler's XOR fold.
        val expectedExact=if(exact) av!!.union(bv!!).minus(av.intersect(bv)) else null
        val sa=Solid.part(a);val sb=Solid.part(b)
        val expected=if(exact) expectedExact!!.solid() else Solid().also { s ->
            for(y in 0..15) for(z in 0..15) for(x in 0..15)
                if(sa.has(x,y,z) xor sb.has(x,y,z)) s.set(x,y,z)
        }
        for(mode in MaterialMode.entries) {
            val g=graph(listOf(a,b),mode)
            val parts=GraphCompiler.intermediate(g,"xor",groups)
            assertEquals(expected.key(),Geometry.solid(parts).key(),"Collision in $mode")
            if(exact) assertTrue(Geometry.exact(parts).equivalent(expectedExact!!),"Exact boundary in $mode")
            if(expected.count()==0) {
                assertTrue(parts.isEmpty())
                val failure=assertFailsWith<IllegalArgumentException> { GraphCompiler.compile(g,groups) }
                assertContains(failure.message.orEmpty(),"Result is empty")
                continue
            }
            assertEquals(parts,GraphCompiler.compile(g,groups))
            val resolved=Geometry.resolve(parts,groups)
            if(mode==MaterialMode.GROUPED) assertEquals(setOf("second"),resolved.owned.keys)
            else if(exact) {
                assertTrue((resolved.exactOwned["body"] ?: ExactVolume()).equivalent(av!!.minus(bv!!)),"First input material")
                assertTrue((resolved.exactOwned["second"] ?: ExactVolume()).equivalent(bv!!.minus(av!!)),"Second input material")
            } else {
                for(y in 0..15) for(z in 0..15) for(x in 0..15) {
                    val owner=when {
                        sa.has(x,y,z) && !sb.has(x,y,z)->"body"
                        sb.has(x,y,z) && !sa.has(x,y,z)->"second"
                        else->null
                    }
                    assertEquals(owner,resolved.owner(x,y,z),"Material at $x/$y/$z")
                }
            }
        }
    }

    @TestFactory fun `every ordered base shape pair in default mounted maximum size and offset fixtures`()=
        BaseShapes.all.flatMap { a -> BaseShapes.all.flatMap { b -> (0..3).map { fixture ->
            DynamicTest.dynamicTest("${a.id} XOR ${b.id}, fixture $fixture") {
                checkPair(variant(a,if(fixture==0) 0 else fixture-1),variant(b,fixture))
            }
        } } }

    @TestFactory fun `every base shape cancels at even fan in and survives at odd fan in through sixteen`()=
        BaseShapes.all.flatMap { shape -> (2..16).map { count ->
            DynamicTest.dynamicTest("${shape.id}, $count inputs") {
                val p=variant(shape,1)
                val g=graph(List(count) { p })
                val parts=GraphCompiler.intermediate(g,"xor",groups)
                if(count%2==0) {
                    assertTrue(parts.isEmpty())
                    assertFailsWith<IllegalArgumentException> { GraphCompiler.compile(g,groups) }
                } else {
                    assertEquals(Solid.part(p).key(),Geometry.solid(GraphCompiler.compile(g,groups)).key())
                    if(ExactGeometry.usesExact(p)) assertTrue(Geometry.exact(parts).equivalent(ExactGeometry.part(p)))
                }
            }
        } }

    @TestFactory fun `mixed base shapes have odd coverage at every supported fan in`()=
        BaseShapes.all.indices.flatMap { start -> (2..16).map { count ->
            DynamicTest.dynamicTest("Mixed inputs starting at ${BaseShapes.all[start].id}, $count inputs") {
                val sources=List(count) { i -> variant(BaseShapes.all[(start+i)%BaseShapes.all.size],i%4) }
                val g=graph(sources)
                val parts=GraphCompiler.intermediate(g,"xor",groups)
                if(sources.any(ExactGeometry::usesExact)) {
                    val expected=sources.map(ExactGeometry::part).fold(ExactVolume()) { a,b -> a.union(b).minus(a.intersect(b)) }
                    assertTrue(Geometry.exact(parts).equivalent(expected),"Exact mixed-input parity")
                    assertEquals(expected.solid().key(),Geometry.solid(parts).key())
                } else {
                    val solids=sources.map(Solid::part);val actual=Geometry.solid(parts)
                    for(y in 0..15) for(z in 0..15) for(x in 0..15)
                        assertEquals(solids.count { it.has(x,y,z) }%2==1,actual.has(x,y,z),"Parity at $x/$y/$z")
                }
            }
        } }

    @TestFactory fun `every base shape survives xor serialization and downstream rotations`()=
        BaseShapes.all.map { shape -> DynamicTest.dynamicTest(shape.id) {
            val p=variant(shape,1)
            // Full XOR shape always leaves a nonempty complement for partial sources.
            val cutter=if(shape.source=="copycats:copycat_block") Part("copycats:copycat_slab") else p
            val base=graph(listOf(Part("copycats:copycat_block"),cutter))
            val form=Geometry.validate(Form(graph=base,groups=groups))
            val restored=Geometry.validate(Gson().fromJson(Gson().toJson(form),Form::class.java))
            assertEquals(form,restored)
            val original=Geometry.solid(form.parts)
            for(axis in listOf(RotationAxis.X,RotationAxis.Y,RotationAxis.Z)) for(q in 1..3) {
                val rotated=base.copy(nodes=base.nodes.dropLast(1)+
                    GraphNode("rotate",NodeKind.ROTATE,listOf("xor",""),rotationAxis=axis,quarterTurns=q)+
                    base.nodes.last().copy(inputs=listOf("rotate")))
                val parts=GraphCompiler.compile(rotated,groups)
                assertEquals(original.rotate(axis,q).key(),Geometry.solid(parts).key())
                assertEquals(Extrusion.cost(form.parts,groups),Extrusion.cost(parts,groups))
                if(form.parts.any(ExactGeometry::usesExact))
                    assertTrue(Geometry.exact(parts).equivalent(Geometry.exact(form.parts).rotate(axis,q)))
            }
        } }
}
