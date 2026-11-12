package de.teutonstudio.copycatarchitect.domain

/** Exact signed 32-bit values. Division truncates toward zero on both platforms. */
object BuildingCounts {
    val kinds=setOf(BuildingKind.INT,BuildingKind.INT_ADD,BuildingKind.INT_SUBTRACT,BuildingKind.INT_MULTIPLY,BuildingKind.INT_DIVIDE,BuildingKind.INT_MIN,BuildingKind.INT_MAX)
    val axes=listOf("length","height","depth")
    fun hasDimensions(n:BuildingNode)=n.id!="empty_wall" && n.kind in setOf(BuildingKind.SEGMENT,BuildingKind.SEGMENT_LAYOUT)
    fun validate(n:BuildingNode) {
        require(n.dimensions.orEmpty().isEmpty() || hasDimensions(n) && n.dimensions.orEmpty().keys.all { it in axes }) { "Only segments and layouts accept dimensions" }
        require(n.kind !in kinds || n.inputs.size==(if(n.kind==BuildingKind.INT) 0 else 2)) { "Anzahl operations need two inputs" }
        require(n.dimensions.orEmpty().isEmpty() || n.layout.isEmpty()) { "Connect dimensions on the segment layout, not its consumer" }
    }
    class IncompleteCount(message:String):IllegalArgumentException(message)
    class Evaluation(graph:BuildingGraph) {
        private val nodes=graph.nodes.associateBy { it.id }
        private val cache=mutableMapOf<String,Int>()
        private val active=mutableSetOf<String>()
        fun value(id:String):Int {
            cache[id]?.let { return it }
            require(active.add(id)) { "Cycle in Anzahl graph at $id" }
            try {
                val n=nodes[id] ?: error("Missing Anzahl source $id")
                require(n.kind in kinds) { "Anzahl input requires an integer source" }
                if(n.kind==BuildingKind.INT) return (n.countValue ?: 0).also { cache[id]=it }
                if(n.inputs.size!=2 || n.inputs.any(String::isEmpty)) throw IncompleteCount("Connect both Anzahl inputs")
                val a=value(n.inputs[0]).toLong();val b=value(n.inputs[1]).toLong()
                val result=when(n.kind) {
                    BuildingKind.INT_ADD->a+b
                    BuildingKind.INT_SUBTRACT->a-b
                    BuildingKind.INT_MULTIPLY->a*b
                    BuildingKind.INT_DIVIDE->{ require(b!=0L) { "Anzahl division by zero" };a/b }
                    BuildingKind.INT_MIN->minOf(a,b)
                    BuildingKind.INT_MAX->maxOf(a,b)
                    else->error("Unknown Anzahl operation")
                }
                require(result in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "Anzahl integer overflow" }
                return result.toInt().also { cache[id]=it }
            } finally { active.remove(id) }
        }
    }
    fun resolve(graph:BuildingGraph,n:BuildingNode,strict:Boolean=false):BuildingNode {
        validate(n)
        if(n.dimensions.orEmpty().isEmpty()) return n
        val evaluation=Evaluation(graph)
        fun dimension(key:String,fallback:Int)=n.dimensions.orEmpty()[key]?.takeIf(String::isNotEmpty)?.let { evaluation.value(it).also { v ->
            require(v in 1..128) { "$key must be between 1 and 128" }
        } } ?: fallback
        val resolved=try { n.copy(length=dimension("length",n.length),height=dimension("height",n.height),depth=dimension("depth",n.depth)) }
        catch(error:IncompleteCount) { if(strict) throw error;return n }
        require(resolved.length.toLong()*resolved.height*resolved.depth<=262144) { "Connected dimensions exceed 262144 segment cells" }
        return resolved
    }
    fun checkDerived(original:BuildingNode,resolved:BuildingNode,length:Int,height:Int) {
        require(original.dimensions.orEmpty()["length"].isNullOrEmpty() || resolved.length==length) { "Connected length conflicts with layout geometry" }
        require(original.dimensions.orEmpty()["height"].isNullOrEmpty() || resolved.height==height) { "Connected height conflicts with layout geometry" }
    }
    /** Display-only dependencies are recomputed after every edit; multi-axis paths remain neutral. */
    fun axisUsage(graph:BuildingGraph):Map<String,Set<String>> {
        val nodes=graph.nodes.associateBy { it.id };val usage=mutableMapOf<String,MutableSet<String>>()
        fun visit(id:String,axis:String) {
            val n=nodes[id] ?: return
            if(n.kind !in kinds || !usage.getOrPut(id) { mutableSetOf() }.add(axis)) return
            n.inputs.filter(String::isNotEmpty).forEach { visit(it,axis) }
        }
        graph.nodes.forEach { n -> n.dimensions.orEmpty().forEach { (axis,id) -> visit(id,axis) } }
        return usage
    }
}
