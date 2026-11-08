package de.teutonstudio.copycatarchitect.domain

/** Built-in compositions retain their saved source ID while using ordinary Copycat parts internally. */
object SourceDefinitions {
    const val STAIRS_SOURCE="copycats:copycat_stairs"
    private const val BYTE_SOURCE="copycats:copycat_byte"
    private val stairBytes=(0..3).map { Part(BYTE_SOURCE,turn=it) }+
        (2..3).map { Part(BYTE_SOURCE,turn=it,top=true) }
    private val stairBoxes by lazy { stairBytes.flatMap { Solid.part(it).boxes() } }

    /** The adapter and the inspection graph share this definition, rather than separate stair cuboids. */
    internal fun boxes(source:String):List<Box> = when(source) {
        STAIRS_SOURCE->stairBoxes
        else->error("No composed source definition: $source")
    }

    /** Expand after applying the saved orientation, material ownership and any Boolean clipping. */
    fun expand(part:Part):List<Part> {
        if(part.source!=STAIRS_SOURCE) return listOf(part)
        if(ExactGeometry.usesExact(part)) {
            val visible=ExactGeometry.part(part)
            return expand(part.copy(exact=null,exactClip=null)).mapNotNull { byte ->
                val source=ExactGeometry.part(byte);val clipped=source.intersect(visible)
                if(clipped.isEmpty()) null else byte.copy(exactClip=if(clipped.contains(source)) null else clipped)
            }
        }
        val visible=Solid.part(part)
        return stairBytes.mapNotNull { byte ->
            // A byte is a cube, so its transformed octant completely describes its orientation.
            val center=part.oriented(byte.oriented(P(4,4,4)))
            val turn=when {
                center.z<8 && center.x<8->0
                center.z<8->1
                center.x>=8->2
                else->3
            }
            val expanded=Part(BYTE_SOURCE,turn=turn,top=center.y>=8,offset=part.offset,
                group=part.group,recipeSource=part.recipeSource)
            val source=Solid.part(expanded)
            val clipped=source.intersect(visible)
            if(clipped.count()==0) null else expanded.copy(cutMask=if(clipped.contains(source)) "" else clipped.mask())
        }
    }

    /** View only: expose the six editable-looking byte sources without replacing the user's source node. */
    fun definition(part:Part):Form? {
        if(part.source!=STAIRS_SOURCE) return null
        val parts=expand(part.copy(cutMask="",exactClip=null,recipeSource=""))
        val groups=if(part.group=="body") DEFAULT_GROUPS else listOf(MaterialGroup(part.group,part.group.take(32)))
        val graph=NodeGraph(nodes=parts.mapIndexed { index,byte ->
            GraphNode("source$index",part=byte,x=20+(index/3)*210,y=20+(index%3)*152)
        }+GraphNode("union0",NodeKind.UNION,parts.indices.map { "source$it" },x=460,y=100)+
            GraphNode("result",NodeKind.OUTPUT,listOf("union0"),x=690,y=100))
        return Geometry.validate(Form(id="stairs_definition",name="Stairs / Bytes",parts=parts,
            groups=groups,graph=graph))
    }
}
