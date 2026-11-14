package de.teutonstudio.copycatarchitect.domain

/** Self-contained dependency closure. Import never resolves a missing item from the receiver's library. */
data class GraphPackage(val schema:Int=1,val root:GraphDocument=GraphDocument(),val graphs:Map<String,GraphDocument> = emptyMap(),
    val forms:Map<String,Form> = emptyMap(),val modules:Map<String,Module> = emptyMap())

object GraphPackages {
    private fun dependencies(documents:List<GraphDocument>):Pair<Set<String>,Set<String>> {
        val forms=linkedSetOf<String>();val modules=linkedSetOf<String>()
        documents.forEach { d -> d.building?.nodes.orEmpty().forEach { n ->
            if(n.kind==BuildingKind.COPYCAT && n.block.isEmpty() && n.form.isNotEmpty()) forms+=n.form
            if(n.kind==BuildingKind.SEGMENT && !n.assembly && n.inputs.isEmpty() && n.block.isEmpty()) {
                if(n.module.isNotEmpty()) modules+=n.module else if(n.form.isNotEmpty()) forms+=n.form
            }
            if(n.kind in setOf(BuildingKind.FLOOR,BuildingKind.CEILING,BuildingKind.ROOF) && n.inputs.isEmpty() && n.block.isEmpty() && !n.assembly && n.form.isNotEmpty()) forms+=n.form
            if(n.kind==BuildingKind.ROOF && !n.assembly && n.roofType=="gable") forms+="full@1"
        } }
        return forms to modules
    }
    fun export(root:GraphDocument,graphs:Map<String,GraphDocument>,forms:Map<String,Form>,modules:Map<String,Module>):GraphPackage {
        val closure=VersionedGraphs.dependencies(root,graphs,allowBrokenRoot=root.version==0)
        val (formKeys,moduleKeys)=dependencies(closure+root)
        val selectedModules=moduleKeys.associateWith { modules[it] ?: error("Missing module $it") }
        val allForms=formKeys+selectedModules.values.flatMap { m -> m.cells.filter { !it.empty && it.block.isEmpty() }.map { it.form } }
        return GraphPackage(root=root,graphs=closure.associateBy { it.key },forms=allForms.associateWith { forms[it] ?: error("Missing form $it") },modules=selectedModules).also(::validate)
    }
    fun validate(bundle:GraphPackage) {
        require(bundle.schema==1) { "Unsupported graph package schema ${bundle.schema}" }
        require(bundle.graphs.size<=VersionedGraphs.MAX_DEFINITIONS && bundle.forms.size<=128 && bundle.modules.size<=128) { "Graph package dependency limit exceeded" }
        bundle.graphs.forEach { (key,d) -> require(key==d.key && d.version>0) { "Invalid packaged graph $key" } }
        val closure=VersionedGraphs.dependencies(bundle.root,bundle.graphs,allowBrokenRoot=bundle.root.version==0)
        require(closure.map { it.key }.toSet()==bundle.graphs.keys) { "Graph package contains unrelated definitions" }
        val (formKeys,moduleKeys)=dependencies(closure+bundle.root)
        moduleKeys.forEach { require(it in bundle.modules) { "Missing packaged module $it" } }
        val allForms=formKeys+bundle.modules.values.flatMap { m -> m.cells.filter { !it.empty && it.block.isEmpty() }.map { it.form } }
        require(allForms==bundle.forms.keys && moduleKeys==bundle.modules.keys) { "Incomplete or unrelated form/module dependencies" }
        // Check membership before validating embedded forms: FORM programs cannot contain nested form packages.
        bundle.forms.forEach { (key,f) ->
            require(key=="${f.id}@${f.version}" && f.version>0) { "Invalid packaged form $key" };Geometry.validate(f)
        }
        bundle.modules.forEach { (key,m) -> require(key==m.key && m.version>0) { "Invalid packaged module $key" };m.validate() }
        // Resolve before accepting a package: aliases, reference cycles and expanded node budgets matter too.
        if(bundle.root.version==0 && runCatching { VersionedGraphs.validate(bundle.root) }.isFailure) return
        val resolved=VersionedGraphs.resolve(bundle.root,bundle.graphs)
        resolved.shape?.let { graph ->
            val checked=if(graph.nodes.none { it.kind==NodeKind.OUTPUT }) graph.copy(nodes=graph.nodes+GraphNode("bundle_result",NodeKind.OUTPUT,listOf(""))) else graph
            GraphCompiler.structure(checked,bundle.root.materialGroups)
        }
        resolved.building?.let { graph ->
            var nodes=graph.nodes
            if(nodes.none { it.id=="empty_wall" }) nodes=nodes+BuildingNode("empty_wall",BuildingKind.SEGMENT,block="minecraft:air",length=1)
            if(nodes.none { it.kind==BuildingKind.BUILDING }) nodes=nodes+BuildingNode("bundle_result",BuildingKind.BUILDING)
            BuildingGraphCompiler.structure(graph.copy(nodes=nodes))
        }
    }
    /** Preflight all conflicts before callers mutate any destination map. */
    fun checkConflicts(bundle:GraphPackage,graphs:Map<String,GraphDocument>,forms:Map<String,Form>,modules:Map<String,Module>) {
        validate(bundle)
        fun <T> check(incoming:Map<String,T>,existing:Map<String,T>) { incoming.forEach { (key,value) -> require(key !in existing || existing[key]==value) { "Version conflict: $key" } } }
        check(bundle.graphs,graphs);if(bundle.root.version>0) check(mapOf(bundle.root.key to bundle.root),graphs)
        check(bundle.forms,forms);check(bundle.modules,modules)
        val combinedGraphs=graphs+bundle.graphs+if(bundle.root.version>0) mapOf(bundle.root.key to bundle.root) else emptyMap()
        bundle.forms.values.forEach { form -> form.versionedGraph?.let { checkConflicts(it,combinedGraphs,forms+bundle.forms,modules+bundle.modules) } }
    }
}
