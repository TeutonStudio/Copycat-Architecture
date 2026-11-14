package de.teutonstudio.copycatarchitect.domain

/** A reference is always exact. Zero is reserved for a separately editable draft. */
data class GraphVersion(val graphId:String="",val version:Int=1) {
    val key get()="$graphId@$version"
    fun validate() { require(graphId.matches(Regex("[a-zA-Z0-9_-]{1,64}")) && version>0) { "Invalid graph version $key" } }
}
enum class GraphMode { FORM, BUILDING }
enum class GraphPortDirection { INPUT, OUTPUT }
data class GraphSocket(val node:String="",val port:String="")
data class GraphOutput(val node:String="",val port:String="out")
data class GraphInterfacePort(val id:String="",val name:String="",val type:String="",val direction:GraphPortDirection=GraphPortDirection.INPUT)
data class GraphInputBinding(val port:String="",val targets:List<GraphSocket> = emptyList(),val defaultSource:GraphOutput?=null)
data class GraphOutputBinding(val port:String="",val source:GraphOutput=GraphOutput())
/** The interface snapshot keeps missing versions and obsolete wires repairable. */
data class GraphReferenceNode(val id:String="",val reference:GraphVersion=GraphVersion(),val ports:List<GraphInterfacePort> = emptyList(),val x:Int=0,val y:Int=0)
/** Native members and reference nodes are disjoint; reference members are never copied into this document. */
data class GraphDocument(
    val schema:Int=1,val graphId:String="",val version:Int=0,val name:String="Graph",val mode:GraphMode=GraphMode.FORM,
    val shape:NodeGraph?=null,val building:BuildingGraph?=null,
    val references:List<GraphReferenceNode> = emptyList(),val connections:List<DiagramConnection> = emptyList(),
    val ports:List<GraphInterfacePort> = emptyList(),val inputBindings:List<GraphInputBinding> = emptyList(),val outputBindings:List<GraphOutputBinding> = emptyList(),
    val materialGroups:List<MaterialGroup> = DEFAULT_GROUPS,val palette:Map<String,String> = Building().palette
) {
    val key get()="$graphId@$version"
}
data class GraphPortChange(val id:String,val before:GraphInterfacePort?=null,val after:GraphInterfacePort?=null) {
    val incompatible get()=before!=null && (after==null || before.type!=after.type || before.direction!=after.direction)
}
data class ResolvedGraph(val shape:NodeGraph?=null,val building:BuildingGraph?=null,val outputs:Map<String,String> = emptyMap(),val nodeOutputs:Map<String,Map<String,String>> = emptyMap())

/** Storage-independent contracts, version checks and bounded resolution shared by JVM and browser. */
object VersionedGraphs {
    const val MAX_DEPTH=16
    const val MAX_DEFINITIONS=128
    private val idPattern=Regex("[a-zA-Z0-9_-]{1,36}")
    private fun nativeIds(d:GraphDocument)=d.shape?.nodes?.map { it.id } ?: d.building?.nodes?.map { it.id }.orEmpty()
    private fun nativeOutput(d:GraphDocument,id:String):String? = when(d.mode) {
        GraphMode.FORM -> d.shape?.nodes?.firstOrNull { it.id==id }?.let { GraphModels.shape(it.kind).output ?: if(it.kind==NodeKind.OUTPUT) "shape" else null }
        GraphMode.BUILDING -> d.building?.nodes?.firstOrNull { it.id==id }?.let { GraphModels.building(it.kind).output ?: if(it.kind==BuildingKind.BUILDING) "building" else null }
    }
    fun socketTypes(d:GraphDocument,s:GraphSocket):Set<String> {
        d.references.firstOrNull { it.id==s.node }?.let { ref ->
            return ref.ports.singleOrNull { it.id==s.port && it.direction==GraphPortDirection.INPUT }?.let { setOf(it.type) }
                ?: error("Missing input ${s.node}:${s.port}")
        }
        return when(d.mode) {
            GraphMode.FORM -> {
                val n=d.shape?.nodes?.firstOrNull { it.id==s.node } ?: error("Missing node ${s.node}")
                val slot=s.port.toIntOrNull() ?: error("Unknown shape input ${s.port}")
                require(slot in n.inputs.indices) { "Unknown shape input ${s.port}" }
                setOf(checkNotNull(GraphModels.shape(n.kind).inputs).type(slot))
            }
            GraphMode.BUILDING -> {
                val n=d.building?.nodes?.firstOrNull { it.id==s.node } ?: error("Missing node ${s.node}")
                if(s.port.startsWith("template:") || s.port.startsWith("family:")) return setOf("SEGMENT")
                val p=BuildingPorts.of(n,true).firstOrNull { it.key==s.port } ?: error("Unknown building input ${s.port}")
                p.accepts.mapNotNull { GraphModels.building(it).output }.toSet()
            }
        }
    }
    fun outputType(d:GraphDocument,e:GraphOutput):String {
        d.references.firstOrNull { it.id==e.node }?.let { ref ->
            return ref.ports.singleOrNull { it.id==e.port && it.direction==GraphPortDirection.OUTPUT }?.type
                ?: error("Missing output ${e.node}:${e.port}")
        }
        require(e.port=="out") { "Unknown output ${e.node}:${e.port}" }
        return nativeOutput(d,e.node) ?: error("Missing source ${e.node}")
    }
    private fun interfaceValid(ports:List<GraphInterfacePort>) {
        require(ports.size<=64 && ports.map { it.id }.distinct().size==ports.size) { "Interface port IDs must be unique" }
        ports.forEach { require(it.id.matches(idPattern) && it.name.trim().length in 1..64 && it.type.matches(Regex("[a-zA-Z][a-zA-Z0-9_-]{0,63}"))) { "Invalid graph interface port ${it.id}" } }
    }
    /** Structural checks allow an unconnected draft, but never silently repair an invalid connection. */
    fun validate(d:GraphDocument,allowBrokenConnections:Boolean=false) {
        require(d.schema==1) { "Unsupported graph document schema ${d.schema}" }
        require(d.graphId.matches(Regex("[a-zA-Z0-9_-]{1,64}")) && d.version>=0 && d.name.trim().length in 1..64) { "Invalid graph document identity" }
        require((d.shape!=null)==(d.mode==GraphMode.FORM) && (d.building!=null)==(d.mode==GraphMode.BUILDING)) { "Graph mode and body disagree" }
        val ids=nativeIds(d)+d.references.map { it.id }
        val max=if(d.mode==GraphMode.FORM) GraphModels.copycat.maxNodes else GraphModels.building.maxNodes
        require(ids.size in 1..max && ids.distinct().size==ids.size && ids.all { it.matches(idPattern) }) { "Graph node IDs must be unique and within the node limit" }
        require(d.shape==null || d.shape.schema in GraphModels.copycat.graphSchemas) { "Unsupported form graph schema" }
        require(d.building==null || d.building.schema in GraphModels.building.graphSchemas) { "Unsupported building graph schema" }
        d.shape?.let(GraphGroups::validate);d.building?.let(GraphGroups::validate)
        require(d.palette.size<=16 && d.palette.all { it.key.length in 1..64 && it.value.length<=256 }) { "Invalid graph palette" }
        interfaceValid(d.ports)
        require(d.version==0 || d.ports.any { it.direction==GraphPortDirection.OUTPUT }) { "A published graph needs an output" }
        d.references.forEach { it.reference.validate();interfaceValid(it.ports);require(it.x in -8192..8192 && it.y in -8192..8192) { "Reference node position exceeds graph bounds" } }
        require(d.connections.size<=4096) { "Too many graph connections" }
        val targets=mutableSetOf<GraphSocket>()
        d.connections.forEach { c ->
            val target=GraphSocket(c.target,c.port)
            require(targets.add(target)) { "An input accepts one connection: ${c.target}:${c.port}" }
            require(c.source in ids && c.target in ids) { "Unknown connection node" }
            if(!allowBrokenConnections) require(outputType(d,GraphOutput(c.source,c.sourcePort)) in socketTypes(d,target)) { "Incompatible connection at ${c.target}:${c.port}" }
        }
        val inputs=d.ports.filter { it.direction==GraphPortDirection.INPUT }.associateBy { it.id }
        val outputs=d.ports.filter { it.direction==GraphPortDirection.OUTPUT }.associateBy { it.id }
        require(d.inputBindings.map { it.port }.toSet()==inputs.keys && d.inputBindings.map { it.port }.distinct().size==d.inputBindings.size) { "Every input needs one interface binding" }
        require(d.outputBindings.map { it.port }.toSet()==outputs.keys && d.outputBindings.map { it.port }.distinct().size==d.outputBindings.size) { "Every output needs one interface binding" }
        d.inputBindings.forEach { b ->
            require(b.targets.isNotEmpty()) { "Input ${b.port} has no targets" }
            b.targets.forEach { target ->
                require(targets.add(target)) { "Input binding conflicts at ${target.node}:${target.port}" }
                if(!allowBrokenConnections) require(inputs.getValue(b.port).type in socketTypes(d,target)) { "Incompatible interface input ${b.port}" }
            }
            b.defaultSource?.let { if(!allowBrokenConnections) require(outputType(d,it)==inputs.getValue(b.port).type) { "Incompatible default for ${b.port}" } }
        }
        d.outputBindings.forEach { if(!allowBrokenConnections) require(outputType(d,it.source)==outputs.getValue(it.port).type) { "Incompatible interface output ${it.port}" } }
        // Ordinary references are canonicalized as connections, never stored twice.
        require(nativeConnections(d).isEmpty()) { "Versioned graph edges belong in connections" }
    }
    /** A building's local palette override becomes a draft, leaving published definitions unchanged. */
    fun withPalette(document:GraphDocument,palette:Map<String,String>):GraphDocument {
        require(document.mode==GraphMode.BUILDING) { "Only building graphs have a material palette" }
        return if(document.palette==palette) document else document.copy(version=0,palette=palette.toMap()).also { validate(it,allowBrokenConnections=true) }
    }
    fun nextVersion(draft:GraphDocument,library:Map<String,GraphDocument>):GraphDocument {
        val previous=library.values.filter { it.graphId==draft.graphId }
        require(previous.all { it.mode==draft.mode }) { "A graph identity cannot change modes" }
        val highest=previous.maxOfOrNull { it.version } ?: 0
        require(highest<Int.MAX_VALUE) { "Graph version overflow" }
        var next=draft.copy(version=highest+1)
        if(next.ports.isEmpty()) {
            val result=next.shape?.nodes?.singleOrNull { it.kind==NodeKind.OUTPUT }?.id ?: next.building?.nodes?.singleOrNull { it.kind==BuildingKind.BUILDING }?.id
            if(result!=null) next=next.copy(ports=listOf(GraphInterfacePort("result","Result",outputType(next,GraphOutput(result)),GraphPortDirection.OUTPUT)),outputBindings=listOf(GraphOutputBinding("result",GraphOutput(result))))
        }
        validate(next)
        return next
    }
    fun compare(before:List<GraphInterfacePort>,after:List<GraphInterfacePort>):List<GraphPortChange> =
        (before.map { it.id }+after.map { it.id }).distinct().mapNotNull { id ->
            val a=before.firstOrNull { it.id==id };val b=after.firstOrNull { it.id==id }
            if(a==b) null else GraphPortChange(id,a,b)
        }
    /** Caller displays compare() first. Wires are intentionally untouched, including obsolete ports. */
    fun switchVersion(d:GraphDocument,nodeId:String,next:GraphDocument):GraphDocument {
        require(next.version>0 && next.mode==d.mode) { "Choose a published graph of the same mode" };validate(next)
        val node=d.references.single { it.id==nodeId }
        require(node.reference.graphId==next.graphId) { "Version change must retain graph identity" }
        return d.copy(references=d.references.map { if(it.id==nodeId) it.copy(reference=GraphVersion(next.graphId,next.version),ports=next.ports.toList()) else it })
    }
    fun inputs(d:GraphDocument,id:String):List<GraphInterfacePort> {
        d.references.firstOrNull { it.id==id }?.let { return it.ports.filter { p -> p.direction==GraphPortDirection.INPUT } }
        d.shape?.nodes?.firstOrNull { it.id==id }?.let { n ->
            return n.inputs.indices.map { slot -> GraphInterfacePort(slot.toString(),"Input ${slot+1}",checkNotNull(GraphModels.shape(n.kind).inputs).type(slot),GraphPortDirection.INPUT) }
        }
        val node=d.building?.nodes?.firstOrNull { it.id==id } ?: error("Missing node $id")
        return BuildingPorts.of(node,true).map { p -> GraphInterfacePort(p.key,p.key,p.accepts.mapNotNull { GraphModels.building(it).output }.distinct().joinToString("|"),GraphPortDirection.INPUT) }
    }
    fun outputs(d:GraphDocument,id:String):List<GraphInterfacePort> {
        d.references.firstOrNull { it.id==id }?.let { return it.ports.filter { p -> p.direction==GraphPortDirection.OUTPUT } }
        return nativeOutput(d,id)?.let { listOf(GraphInterfacePort("out","Output",it,GraphPortDirection.OUTPUT)) }.orEmpty()
    }
    fun displayInputs(d:GraphDocument,id:String):List<GraphInterfacePort> {
        val ports=inputs(d,id)
        return ports+d.connections.filter { it.target==id && ports.none { p -> p.id==it.port } }.distinctBy { it.port }.map {
            GraphInterfacePort(it.port,"? ${it.port}",runCatching { outputType(d,GraphOutput(it.source,it.sourcePort)) }.getOrDefault("unknown"),GraphPortDirection.INPUT)
        }
    }
    fun displayOutputs(d:GraphDocument,id:String):List<GraphInterfacePort> {
        val ports=outputs(d,id)
        return ports+d.connections.filter { it.source==id && ports.none { p -> p.id==it.sourcePort } }.distinctBy { it.sourcePort }.map {
            GraphInterfacePort(it.sourcePort,"? ${it.sourcePort}",runCatching { socketTypes(d,GraphSocket(it.target,it.port)).joinToString("|") }.getOrDefault("unknown"),GraphPortDirection.OUTPUT)
        }
    }
    fun nativeConnections(d:GraphDocument):List<DiagramConnection> = when(d.mode) {
        GraphMode.FORM -> d.shape?.nodes.orEmpty().flatMap { n -> n.inputs.mapIndexedNotNull { i,id -> id.takeIf(String::isNotEmpty)?.let { DiagramConnection(it,n.id,i.toString()) } } }
        GraphMode.BUILDING -> buildList {
            d.building?.nodes.orEmpty().forEach { n -> GraphGroups.remap(n) { port,ref -> if(ref.isNotEmpty()) add(DiagramConnection(ref,n.id,port));ref } }
        }
    }
    fun fromShape(id:String,name:String,graph:NodeGraph,groups:List<MaterialGroup> = DEFAULT_GROUPS):GraphDocument {
        GraphCompiler.structure(graph,groups)
        val original=GraphDocument(graphId=id,name=name,shape=graph,materialGroups=groups)
        return original.copy(shape=graph.copy(nodes=graph.nodes.map { it.copy(inputs=it.inputs.map { "" }) }),connections=nativeConnections(original))
    }
    fun fromBuilding(id:String,name:String,graph:BuildingGraph,palette:Map<String,String> = Building().palette):GraphDocument {
        BuildingGraphCompiler.structure(graph)
        val original=GraphDocument(graphId=id,name=name,mode=GraphMode.BUILDING,building=graph,palette=palette)
        return original.copy(building=graph.copy(nodes=graph.nodes.map { GraphGroups.remap(it) { _,_->"" } }),connections=nativeConnections(original))
    }
    /** Validate the entire closure, including unused references, before evaluation or import. */
    fun dependencies(root:GraphDocument,library:Map<String,GraphDocument>,allowBrokenRoot:Boolean=false):List<GraphDocument> {
        val active=mutableSetOf<String>();val done=linkedMapOf<String,GraphDocument>();val heights=mutableMapOf<String,Int>()
        fun visit(d:GraphDocument,depth:Int) {
            require(depth<=MAX_DEPTH) { "Subgraph nesting exceeds $MAX_DEPTH levels" };validate(d,allowBrokenRoot && depth==0)
            require(active.add(d.key)) { "Recursive subgraph ${d.key}" }
            try {
                d.references.forEach { ref ->
                    val child=library[ref.reference.key] ?: error("Missing graph ${ref.reference.key}")
                    require(child.key==ref.reference.key && child.version>0 && child.mode==d.mode) { "Incompatible graph ${ref.reference.key}" }
                    require(child.ports==ref.ports) { "Interface changed for ${ref.reference.key}; repair the reference" }
                    require(child.materialGroups==d.materialGroups || d.mode!=GraphMode.FORM) { "Subgraph material groups differ at ${ref.id}" }
                    require(child.key !in active) { "Recursive subgraph ${child.key}" }
                    if(child.key !in done) visit(child,depth+1)
                    require(depth+1+heights.getValue(child.key)<=MAX_DEPTH) { "Subgraph nesting exceeds $MAX_DEPTH levels" }
                }
                heights[d.key]=d.references.maxOfOrNull { 1+heights.getValue(it.reference.key) } ?: 0
                done[d.key]=d
                require(done.size<=MAX_DEFINITIONS) { "Too many graph dependencies" }
            } finally { active.remove(d.key) }
        }
        visit(root,0)
        return done.values.filter { it.key!=root.key }
    }
    /** Evaluation-only expansion. IDs are generated locally and never saved back into documents. */
    fun resolve(root:GraphDocument,library:Map<String,GraphDocument>):ResolvedGraph {
        dependencies(root,library)
        val shapeNodes=mutableListOf<GraphNode>();val buildingNodes=mutableListOf<BuildingNode>();var serial=0
        val nodeOutputs=linkedMapOf<String,Map<String,String>>()
        val instances=mutableMapOf<Pair<GraphVersion,Map<String,String>>,Map<String,String>>()
        val max=if(root.mode==GraphMode.FORM) GraphModels.copycat.maxNodes else GraphModels.building.maxNodes
        fun fresh():String { require(++serial<=max) { "Resolved graph exceeds $max nodes" };return "v$serial" }
        fun expand(d:GraphDocument,incoming:Map<String,String>,depth:Int):Map<String,String> {
            require(depth<=MAX_DEPTH) { "Subgraph nesting exceeds $MAX_DEPTH levels" }
            val passthrough=if(depth>0) d.shape?.nodes.orEmpty().filter { it.kind==NodeKind.OUTPUT }.map { it.id }.toSet() else emptySet()
            val local=nativeIds(d).filter { it !in passthrough }.associateWith { if(it=="empty_wall" && d.mode==GraphMode.BUILDING) "empty_wall" else fresh() }
            val referenceOutputs=mutableMapOf<String,Map<String,String>>();val visiting=mutableSetOf<String>()
            val wires=d.connections.associateBy { GraphSocket(it.target,it.port) }
            val bindings=d.inputBindings.flatMap { b -> b.targets.map { it to b } }.toMap()
            lateinit var endpoint:(GraphOutput)->String
            fun input(target:GraphSocket):String {
                wires[target]?.let { return endpoint(GraphOutput(it.source,it.sourcePort)) }
                bindings[target]?.let { b -> return incoming[b.port] ?: b.defaultSource?.let { endpoint(it) } ?: "" }
                return ""
            }
            endpoint={ e ->
                if(e.node in passthrough) { require(e.port=="out");input(GraphSocket(e.node,"0")) } else local[e.node]?.also { require(e.port=="out") { "Unknown native output ${e.port}" } } ?: run {
                    val ref=d.references.firstOrNull { it.id==e.node } ?: error("Missing reference ${e.node}")
                    val outputs=referenceOutputs[e.node] ?: run {
                        require(visiting.add(e.node)) { "Cycle through reference ${e.node}" }
                        try {
                            val child=library.getValue(ref.reference.key)
                            val args=ref.ports.filter { it.direction==GraphPortDirection.INPUT }.associate { it.id to input(GraphSocket(ref.id,it.id)) }.filterValues(String::isNotEmpty)
                            val cacheKey=ref.reference to args
                            (instances[cacheKey] ?: expand(child,args,depth+1).also { instances[cacheKey]=it }).also { referenceOutputs[e.node]=it }
                        } finally { visiting.remove(e.node) }
                    }
                    outputs[e.port] ?: error("Missing output ${e.node}:${e.port}")
                }
            }
            // Expand every instance, so disconnected invalid instances cannot hide cycles or limits.
            d.references.forEach { ref -> ref.ports.filter { it.direction==GraphPortDirection.OUTPUT }.firstOrNull()?.let { endpoint(GraphOutput(ref.id,it.id)) } }
            d.shape?.nodes?.filter { it.id !in passthrough }?.forEach { n -> shapeNodes+=n.copy(id=local.getValue(n.id),inputs=n.inputs.indices.map { input(GraphSocket(n.id,it.toString())) }) }
            d.building?.nodes?.forEach { n ->
                if(n.id!="empty_wall" || buildingNodes.none { it.id=="empty_wall" }) {
                    buildingNodes+=GraphGroups.remap(n) { port,_ -> input(GraphSocket(n.id,port)) }.copy(id=local.getValue(n.id))
                }
            }
            if(depth==0) {
                local.forEach { (id,resolved) -> nodeOutputs[id]=mapOf("out" to resolved) }
                nodeOutputs.putAll(referenceOutputs)
            }
            return d.outputBindings.associate { it.port to endpoint(it.source) }
        }
        val outputs=expand(root,emptyMap(),0)
        return when(root.mode) {
            GraphMode.FORM -> ResolvedGraph(shape=root.shape!!.copy(nodes=shapeNodes,nodeGroups=emptyList()),outputs=outputs,nodeOutputs=nodeOutputs)
            GraphMode.BUILDING -> ResolvedGraph(building=root.building!!.copy(nodes=buildingNodes,nodeGroups=emptyList()),outputs=outputs,nodeOutputs=nodeOutputs)
        }
    }
    data class Extraction(val parent:GraphDocument,val definition:GraphDocument,val reference:GraphReferenceNode)
    /** Explicit migration/extraction. Boundary port IDs depend on stable selection order, not labels. */
    fun extract(parent:GraphDocument,selection:Set<String>,graphId:String,name:String,nodeId:String):Extraction {
        validate(parent)
        val known=(nativeIds(parent)+parent.references.map { it.id }).toSet()
        require(selection.isNotEmpty() && selection.all { it in known } && nodeId !in known && nodeId.matches(idPattern)) { "Choose existing members and a fresh reference ID" }
        require(parent.shape?.nodes.orEmpty().none { it.id in selection && it.kind==NodeKind.OUTPUT } &&
            parent.building?.nodes.orEmpty().none { it.id in selection && (it.kind==BuildingKind.BUILDING || it.id=="empty_wall") }) { "Keep the result and Empty wall in the parent graph" }
        require(parent.inputBindings.none { b -> b.targets.any { it.node in selection } }) { "Extract before exposing these members as interface inputs" }
        require(parent.outputBindings.none { it.source.node in selection }) { "Extract before exposing these members as interface outputs" }
        val incoming=parent.connections.filter { it.target in selection && it.source !in selection }.groupBy { GraphOutput(it.source,it.sourcePort) }
        val outgoing=parent.connections.filter { it.source in selection && it.target !in selection }.groupBy { GraphOutput(it.source,it.sourcePort) }
        // Even an unconnected selection can be published with its available outputs.
        val exits=outgoing.keys.toMutableList()
        if(exits.isEmpty()) selection.forEach { id ->
            val ref=parent.references.firstOrNull { it.id==id }
            if(ref!=null) ref.ports.filter { it.direction==GraphPortDirection.OUTPUT }.forEach { exits+=GraphOutput(id,it.id) }
            else if(nativeOutput(parent,id)!=null) exits+=GraphOutput(id)
        }
        require(exits.isNotEmpty()) { "Selected graph has no output" }
        val inPorts=incoming.keys.mapIndexed { i,e -> e to GraphInterfacePort("in${i+1}","Input ${i+1}",outputType(parent,e),GraphPortDirection.INPUT) }.toMap()
        val outPorts=exits.mapIndexed { i,e -> e to GraphInterfacePort("out${i+1}","Output ${i+1}",outputType(parent,e),GraphPortDirection.OUTPUT) }.toMap()
        val ports=inPorts.values+outPorts.values
        fun shapeMembers(selected:Boolean)=parent.shape?.let { g -> g.copy(nodes=g.nodes.filter { (it.id in selection)==selected },nodeGroups=GraphGroups.prune(g.nodeGroups,g.nodes.filter { (it.id in selection)==selected }.map { it.id }.toSet())) }
        fun buildingMembers(selected:Boolean)=parent.building?.let { g -> g.copy(nodes=g.nodes.filter { (it.id in selection)==selected },nodeGroups=GraphGroups.prune(g.nodeGroups,g.nodes.filter { (it.id in selection)==selected }.map { it.id }.toSet())) }
        val definition=GraphDocument(graphId=graphId,version=1,name=name,mode=parent.mode,shape=shapeMembers(true),building=buildingMembers(true),
            references=parent.references.filter { it.id in selection },connections=parent.connections.filter { it.source in selection && it.target in selection },ports=ports,
            inputBindings=inPorts.map { (e,p) -> GraphInputBinding(p.id,incoming.getValue(e).map { GraphSocket(it.target,it.port) }) },
            outputBindings=outPorts.map { (e,p) -> GraphOutputBinding(p.id,e) },materialGroups=parent.materialGroups,palette=parent.palette)
        validate(definition)
        val ref=GraphReferenceNode(nodeId,GraphVersion(graphId,1),ports)
        val connections=parent.connections.filter { it.target !in selection && it.source !in selection }+
            inPorts.map { (e,p) -> DiagramConnection(e.node,nodeId,p.id,e.port) }+
            outgoing.flatMap { (e,wires) -> wires.map { it.copy(source=nodeId,sourcePort=outPorts.getValue(e).id) } }
        val next=parent.copy(shape=shapeMembers(false),building=buildingMembers(false),references=parent.references.filter { it.id !in selection }+ref,connections=connections)
        validate(next)
        return Extraction(next,definition,ref)
    }
    /** Fragments receive an evaluation-only result socket so their own nodes can be previewed. */
    fun preview(root:GraphDocument,library:Map<String,GraphDocument>):ResolvedGraph {
        val resolved=resolve(root,library)
        resolved.shape?.let { graph ->
            val output=resolved.outputs.entries.firstOrNull { entry -> root.ports.any { it.id==entry.key && it.type=="shape" } }?.value.orEmpty()
            val nodes=if(graph.nodes.none { it.kind==NodeKind.OUTPUT }) graph.nodes+GraphNode("preview_result",NodeKind.OUTPUT,listOf(output)) else graph.nodes
            return resolved.copy(shape=graph.copy(nodes=nodes))
        }
        val graph=resolved.building!!
        var nodes=graph.nodes
        if(nodes.none { it.id=="empty_wall" }) nodes=nodes+BuildingNode("empty_wall",BuildingKind.SEGMENT,block="minecraft:air",length=1)
        if(nodes.none { it.kind==BuildingKind.BUILDING }) nodes=nodes+BuildingNode("preview_result",BuildingKind.BUILDING)
        return resolved.copy(building=graph.copy(nodes=nodes))
    }
    fun evaluateShapeOutput(root:GraphDocument,library:Map<String,GraphDocument>,portId:String):List<Part> {
        require(root.mode==GraphMode.FORM && root.ports.any { it.id==portId && it.direction==GraphPortDirection.OUTPUT && it.type=="shape" }) { "Choose a shape output" }
        val resolved=preview(root,library)
        return GraphCompiler.intermediate(resolved.shape!!,resolved.outputs.getValue(portId),root.materialGroups)
    }
    fun compileShape(root:GraphDocument,library:Map<String,GraphDocument>):List<Part> =
        GraphCompiler.compile(requireNotNull(preview(root,library).shape),root.materialGroups)
}
