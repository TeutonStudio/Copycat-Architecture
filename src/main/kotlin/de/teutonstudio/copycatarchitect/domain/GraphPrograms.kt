package de.teutonstudio.copycatarchitect.domain

enum class GraphResultMode { CONSTRUCT, VALUE }
data class GraphDefinition(val document:Building=Building(),val forms:Map<String,Form> = emptyMap(),val modules:Map<String,Module> = emptyMap())
data class GraphParameter(val id:String="",val name:String="Eingang",val type:String="COUNT",val defaultSource:String="")

/** Authored terminals are lowered to ordinary producers. References keep their exact typed values. */
object GraphPrograms {
    val kinds=setOf(BuildingKind.RESULT,BuildingKind.GROUP_INPUT,BuildingKind.GRAPH_REFERENCE)
    fun parameters(graph:BuildingGraph):List<GraphParameter> {
        val inputs=graph.nodes.filter { it.kind==BuildingKind.GROUP_INPUT }
        val order=graph.groupInputOrder.orEmpty()+inputs.map { it.id }
        return order.distinct().mapNotNull { id -> inputs.firstOrNull { it.id==id }?.let { GraphParameter(it.id,it.name.ifBlank { "Eingang" },it.valueType ?: "COUNT",it.defaultSource.orEmpty()) } }
    }
    fun resultSource(n:BuildingNode):String=when {
        n.resultMode==GraphResultMode.CONSTRUCT -> n.id
        n.kind==BuildingKind.RESULT && n.resultMode!=GraphResultMode.VALUE -> n.id
        n.kind==BuildingKind.RESULT -> n.resultSource.orEmpty()
        n.resultType!=null -> n.inputs.firstOrNull().orEmpty()
        else -> n.id
    }
    fun producer(n:BuildingNode):BuildingNode {
        if(n.kind!=BuildingKind.RESULT) return n
        if(n.resultMode==GraphResultMode.VALUE) return BuildingNode(n.id,BuildingKind.RESULT,n.name,n.x,n.y,resultType=n.resultType,resultMode=n.resultMode,resultSource=n.resultSource)
        var kind=n.producerKind ?: BuildingKind.COPYCAT
        require(kind !in setOf(BuildingKind.RESULT,BuildingKind.GROUP_INPUT)) { "Diese Knotenart kann kein Ergebnis erzeugen" }
        if(kind in setOf(BuildingKind.POSITION_X,BuildingKind.POSITION_Y,BuildingKind.POSITION_Z)) kind=when(n.producerOutput ?: kind.name.takeLast(1)) { "Y"->BuildingKind.POSITION_Y;"Z"->BuildingKind.POSITION_Z;else->BuildingKind.POSITION_X }
        return n.copy(kind=kind)
    }
    fun restore(original:BuildingNode,changed:BuildingNode)=if(original.kind==BuildingKind.RESULT) changed.copy(kind=BuildingKind.RESULT) else changed
    fun source(n:BuildingNode):String?=when(n.kind) {
        BuildingKind.RESULT->if(n.resultMode==GraphResultMode.VALUE) n.resultSource.orEmpty() else null
        BuildingKind.GROUP_INPUT->n.boundSource?.takeIf(String::isNotEmpty) ?: n.defaultSource.orEmpty()
        BuildingKind.GRAPH_REFERENCE->n.boundSource.orEmpty()
        else->null
    }
    fun type(n:BuildingNode):String?=when(n.kind) {
        BuildingKind.RESULT->n.resultType
        BuildingKind.GROUP_INPUT,BuildingKind.GRAPH_REFERENCE->n.valueType
        else->null
    }
    fun constructors(type:String):List<BuildingKind> = BuildingKind.entries.filter { kind ->
        kind !in setOf(BuildingKind.RESULT,BuildingKind.GROUP_INPUT) && (kind==BuildingKind.CONDITIONAL || kind==BuildingKind.GRAPH_REFERENCE || GraphNumbers.producer(kind.name,type) || buildingGraphType(kind)==type)
    }
    fun authoring(node:BuildingNode):BuildingNode=if(node.kind==BuildingKind.ROOF) node.copy(roofInputMode=RoofInputMode.TEMPLATE,inputs=List(8){""}) else node
    fun newResult(type:String="COPYCAT",kind:BuildingKind=when(type) { "WALL"->BuildingKind.SEGMENT;"COUNT"->BuildingKind.COUNT;"INT"->BuildingKind.INT;else->BuildingKind.entries.first { buildingGraphType(it)==type } }):BuildingNode {
        val n=authoring(GraphModels.newBuilding(kind,true))
        return n.copy(kind=BuildingKind.RESULT,name="Ergebnis",resultType=type,resultMode=GraphResultMode.CONSTRUCT,producerKind=kind,
            inlinePart=if(kind==BuildingKind.COPYCAT) Part("copycats:copycat_block") else n.inlinePart)
    }
    fun definition(graph:BuildingGraph,n:BuildingNode)=graph.graphDefinitions.orEmpty()[n.referenceKey] ?: error("Fehlende Graphreferenz ${n.referenceKey}")
    fun validate(graph:BuildingGraph) {
        val ids=graph.nodes.filter { it.kind==BuildingKind.GROUP_INPUT }.map { it.id }
        require(graph.groupInputOrder==null || graph.groupInputOrder.distinct().size==graph.groupInputOrder.size && graph.groupInputOrder.all { it in ids }) { "Ungültige Reihenfolge der Gruppeneingänge" }
        for(raw in graph.nodes) {
            val n=producer(raw)
            if(n.kind==BuildingKind.GROUP_INPUT) require(n.valueType in GraphCards.types && n.name.isNotBlank()) { "Gruppeneingang benötigt Namen und Datentyp" }
            if(n.kind==BuildingKind.GRAPH_REFERENCE && n.boundSource==null && !n.referenceKey.isNullOrEmpty()) {
                val d=definition(graph,n);val g=d.document.graph ?: error("Graphreferenz ohne Graph")
                require(n.valueType==g.resultNode().resultType || g.resultNode().resultType==null && n.valueType=="BUILDING") { "Graphreferenz hat einen anderen Ergebnistyp" }
                require(n.arguments.orEmpty().keys.all { key -> parameters(g).any { it.id==key } }) { "Unbekannter Referenzeingang" }
            }
        }
    }
    fun configure(graph:BuildingGraph,id:String,field:String,value:String):BuildingGraph {
        val before=graph.nodes.first { it.id==id }
        var next=when(field) {
            "resultMode"->before.copy(resultMode=GraphResultMode.valueOf(value))
            "resultType"->before.copy(resultType=value)
            "valueType"->before.copy(valueType=value)
            "operation"->restore(before,UnifiedOperators.switch(producer(before),value,GraphNumbers.buildingContext(graph)))
            "producerOutput"->before.copy(producerOutput=value)
            "producerKind"->{
                val kind=BuildingKind.valueOf(value)
                require(kind in constructors(before.resultType ?: "COPYCAT")) { "Unpassende Erzeugungsart" }
                val factory=authoring(GraphModels.newBuilding(kind,true))
                factory.copy(id=before.id,name=before.name,x=before.x,y=before.y,kind=BuildingKind.RESULT,
                    resultType=before.resultType,resultMode=GraphResultMode.CONSTRUCT,resultSource=before.resultSource,producerKind=kind,
                    inlinePart=if(kind==BuildingKind.COPYCAT) Part("copycats:copycat_block") else factory.inlinePart)
            }
            else->error("Unbekannte Ergebniseinstellung")
        }
        if(field=="resultType" && next.kind==BuildingKind.RESULT && next.resultMode!=GraphResultMode.VALUE && producer(next).kind !in constructors(value)) {
            next=newResult(value).copy(id=before.id,name=before.name,x=before.x,y=before.y,resultSource=before.resultSource)
        }
        // Match native socket identities first, then remaining compatible connected operands.
        if(field=="producerKind" || field=="resultType" && next.producerKind!=before.producerKind) {
            val oldPorts=BuildingPorts.of(before,graph,true).filter { it.source.isNotEmpty() }.toMutableList()
            var temporary=graph.copy(nodes=graph.nodes.map { if(it.id==id) next else it })
            for(port in BuildingPorts.of(next,temporary,true)) {
                val match=oldPorts.firstOrNull { it.key==port.key && GraphTypes.accepts(temporary,port,graph.nodes.first { n->n.id==it.source }) }
                    ?: oldPorts.firstOrNull { GraphTypes.accepts(temporary,port,graph.nodes.first { n->n.id==it.source }) }
                if(match!=null) { temporary=temporary.connect(id,port.key,match.source);oldPorts.remove(match) }
            }
            next=temporary.nodes.first { it.id==id }
        }
        return graph.replace(next)
    }
    fun importInterface(graph:BuildingGraph,face:GraphGroupInterface,members:List<String>):BuildingGraph {
        if(face.inputs.isEmpty()) return graph
        require(face.inputs.none { input->graph.nodes.any { it.id==input.id && it.kind!=BuildingKind.GROUP_INPUT } }) { "Gruppeneingangskennung kollidiert mit einem internen Knoten" }
        fun logical(type:String)=when(type) { "shape"->"COPYCAT";"segment"->"WALL";else->type.uppercase() }
        val parameters=face.inputs.filter { input->graph.nodes.none { it.id==input.id } }.mapIndexed { i,input->
            val target=input.targets.firstOrNull()
            val source=target?.let { t->graph.nodes.firstOrNull { it.id==members.getOrNull(t.member) }?.let { n->BuildingPorts.of(n,graph,true).firstOrNull { it.key==t.port }?.source } }
            BuildingNode(input.id,BuildingKind.GROUP_INPUT,input.name,0,i*180,valueType=logical(input.type),defaultSource=source?.takeIf(String::isNotEmpty))
        }
        var next=graph.copy(nodes=graph.nodes+parameters,groupInputOrder=(graph.groupInputOrder.orEmpty()+graph.nodes.filter { it.kind==BuildingKind.GROUP_INPUT }.map { it.id }+parameters.map { it.id }).distinct()).withCurrentSchema()
        val replacements=mutableMapOf<String,MutableMap<String,String>>()
        for(input in face.inputs) for(target in input.targets) {
            val targetId=members[target.member]
            val port=BuildingPorts.of(next.nodes.first { it.id==targetId },next,true).first { it.key==target.port }
            var source=input.id
            if(logical(input.type)=="INT" && port.valueType=="COUNT") {
                val conversion="gc_${next.nodes.size}"
                next=next.copy(nodes=next.nodes+BuildingNode(conversion,BuildingKind.INT_TO_COUNT,inputs=listOf(input.id)))
                source=conversion
            }
            replacements.getOrPut(targetId){mutableMapOf()}[target.port]=source
        }
        val groups=next.nodeGroups.map { group->group.copy(inputConnections=group.inputConnections+group.graphInterface?.inputs.orEmpty().mapNotNull { input->
            input.targets.firstNotNullOfOrNull { target->replacements[group.nodes[target.member]]?.get(target.port) }?.let { input.id to it }
        }.toMap()) }
        next=next.copy(nodes=next.nodes.map { n->GraphGroups.remap(n) { key,source->replacements[n.id]?.get(key) ?: source } },nodeGroups=groups).withCurrentSchema()
        BuildingGraphCompiler.structure(next)
        return next
    }
    fun diagnostic(graph:BuildingGraph,n:BuildingNode):String? {
        if(n.kind!=BuildingKind.RESULT || n.resultMode==GraphResultMode.VALUE) return null
        val actual=GraphTypes.building(graph,n.id)
        return if(actual!=null && !GraphNumbers.compatible(actual,n.resultType)) "Erzeugungsart liefert $actual, erwartet wird ${n.resultType}" else null
    }
    data class Prepared(val graph:BuildingGraph,val palette:Map<String,String>,val forms:Map<String,Form>,val modules:Map<String,Module>)
    fun expand(graph:BuildingGraph,palette:Map<String,String> = emptyMap(),forms:Map<String,Form> = emptyMap(),modules:Map<String,Module> = emptyMap()):Prepared {
        if(graph.nodes.none { producer(it).kind==BuildingKind.GRAPH_REFERENCE && producer(it).boundSource==null && !producer(it).referenceKey.isNullOrEmpty() }) return Prepared(graph,palette,forms,modules)
        val result=mutableListOf<BuildingNode>();val outPalette=palette.toMutableMap();val outForms=forms.toMutableMap();val outModules=modules.toMutableMap()
        val groups=mutableListOf<GraphGroup>();val allDefinitions=graph.graphDefinitions.orEmpty().toMutableMap();val active=mutableSetOf<String>();var serial=0
        fun instantiate(source:BuildingGraph,prefix:String,bindings:Map<String,String>,definitions:Map<String,GraphDefinition>):String {
            require(active.size<32) { "Graphreferenzen sind zu tief verschachtelt" }
            val mapping=source.nodes.associate { it.id to if(prefix.isEmpty()) it.id else "${prefix}_${serial++}" }
            val registry=definitions+source.graphDefinitions.orEmpty()
            for((key,definition) in registry) { require(allDefinitions[key]==null || allDefinitions[key]==definition) { "Widersprüchliche gespeicherte Graphversion $key" };allDefinitions[key]=definition }
            for(original in source.nodes) {
                var n=GraphGroups.remap(original) { _,ref -> mapping[ref] ?: ref }.copy(id=mapping.getValue(original.id))
                if(prefix.isNotEmpty()) n=n.copy(resultType=null)
                else if(original.id==source.resultNode().id && original.resultType==null) n=n.copy(resultType="BUILDING",resultMode=GraphResultMode.CONSTRUCT)
                if(original.kind==BuildingKind.RESULT && prefix.isNotEmpty()) n=if(original.resultMode==GraphResultMode.VALUE) BuildingNode(n.id,BuildingKind.GROUP_INPUT,"Ergebnis",valueType=original.resultType,defaultSource=n.resultSource) else producer(n).copy(resultType=null)
                if(original.kind==BuildingKind.BUILDING && original.resultType!=null && prefix.isNotEmpty()) n=BuildingNode(n.id,BuildingKind.GROUP_INPUT,"Ergebnis",valueType=original.resultType,defaultSource=n.inputs.firstOrNull())
                if(original.kind==BuildingKind.GROUP_INPUT) n=n.copy(boundSource=bindings[original.id])
                val recipe=producer(n)
                if(recipe.kind==BuildingKind.GRAPH_REFERENCE && recipe.boundSource==null && !recipe.referenceKey.isNullOrEmpty()) {
                    val key=recipe.referenceKey!!;require(active.add(key)) { "Rekursive Graphreferenz $key" }
                    val d=registry[key] ?: error("Fehlende Graphreferenz $key");val child=d.document.graph ?: error("Graphreferenz ohne Graph")
                    validate(source.copy(graphDefinitions=registry));validate(child.copy(graphDefinitions=registry+child.graphDefinitions.orEmpty()))
                    val scope="g${serial++}";val roles=d.document.palette.keys.associateWith { "${scope}_$it" };val formIds=d.forms.keys.associateWith { "${scope}_$it" };val moduleIds=d.modules.keys.associateWith { "${scope}_$it" }
                    outPalette+=d.document.palette.mapKeys { roles.getValue(it.key) };outForms+=d.forms.map { (id,f)->formIds.getValue(id) to f.copy(groups=f.groups.map { group->group.copy(role=roles[group.role] ?: group.role) }) }
                    outModules+=d.modules.map { (id,m)->moduleIds.getValue(id) to m.copy(cells=m.cells.map { c->c.copy(form=formIds[c.form] ?: c.form,role=roles[c.role] ?: c.role) }) }
                    val scoped=child.copy(nodes=child.nodes.map { c -> c.copy(role=roles[c.role] ?: c.role,form=formIds[c.form] ?: c.form,module=moduleIds[c.module] ?: c.module) })
                    val target=instantiate(scoped,scope,recipe.arguments.orEmpty(),registry)
                    n=n.copy(boundSource=target)
                    active.remove(key)
                }
                result+=n
                require(result.size<=3072) { "Graphreferenzen überschreiten 3072 ausgewertete Knoten" }
            }
            groups+=source.nodeGroups.map { group -> group.copy(id=if(prefix.isEmpty()) group.id else "${prefix}_group_${serial++}",nodes=group.nodes.map(mapping::getValue),definitionId="",inputConnections=group.inputConnections.mapValues { mapping[it.value] ?: it.value }) }
            return mapping.getValue(source.resultNode().id)
        }
        instantiate(graph,"",emptyMap(),graph.graphDefinitions.orEmpty())
        return Prepared(graph.copy(nodes=result,nodeGroups=groups,graphDefinitions=allDefinitions),outPalette,outForms,outModules)
    }
}
