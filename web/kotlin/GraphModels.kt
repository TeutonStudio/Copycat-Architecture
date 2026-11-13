package de.teutonstudio.copycatarchitect.domain

import de.teutonstudio.copycatarchitect.web.decodeBuildingNode
import de.teutonstudio.copycatarchitect.web.decodeGraphNode

/** Read the same packaged contract as the native editor without a JVM JSON runtime. */
internal object GraphModels {
    private val catalog:dynamic=JSON.parse(graphCatalogJson)
    private fun names(value:dynamic):Array<String> = js("Object.keys(value)")
    private fun list(value:dynamic):List<dynamic> = if(value==null) emptyList() else value.unsafeCast<Array<dynamic>>().toList()
    private fun ints(value:dynamic)=list(value).map { it.unsafeCast<Int>() }.toSet()
    val copycat=Model(catalog.copycat)
    val building=Model(catalog.building)
    val coordinates=-8192..8192
    const val FORM_NODES=64
    const val FORM_SOURCES=32
    const val BUILDING_NODES=96
    const val SOCKETS=16
    const val LEVELS=8
    fun shape(kind:NodeKind)=copycat.nodes.getValue(kind.name)
    fun inputs(kind:NodeKind)=checkNotNull(shape(kind).inputs).let { it.min..it.max }
    fun accepts(target:NodeKind,slot:Int,source:NodeKind)=shape(source).output?.let { it==shape(target).inputs?.type(slot) } ?: false
    fun requiredBuildingInputs(kind:BuildingKind,mode:String)=when(kind) {
        BuildingKind.PATTERN,BuildingKind.SEGMENT_UNION,BuildingKind.SEGMENT_INTERSECTION,BuildingKind.SEGMENT_DIFFERENCE,BuildingKind.SEGMENT_SYMMETRIC_DIFFERENCE,
        BuildingKind.COPYCAT_UNION,BuildingKind.COPYCAT_INTERSECTION,BuildingKind.COPYCAT_DIFFERENCE,BuildingKind.COPYCAT_SYMMETRIC_DIFFERENCE->2
        BuildingKind.COPYCAT_MIRROR,BuildingKind.COPYCAT_ROTATE,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_ROTATE->1
        BuildingKind.STOREY->if(mode=="rooms") 1 else 0
        else->0
    }
    private fun defaults(model:dynamic,kind:String,overrides:dynamic):dynamic {
        val value:dynamic=JSON.parse(JSON.stringify(model.nodeDefaults))
        if(overrides!=null) for(key in names(overrides)) value[key]=overrides[key]
        value.kind=kind
        return value
    }
    fun newShape(kind:NodeKind):GraphNode = decodeGraphNode(defaults(catalog.copycat,kind.name,catalog.copycat.nodes[kind.name].defaults))
        .copy(inputs=List(inputs(kind).first) { "" })
    fun newBuilding(kind:BuildingKind,explicit:Boolean):BuildingNode {
        val model=catalog.building.nodes[kind.name]
        return decodeBuildingNode(defaults(catalog.building,kind.name,if(explicit || model.alwaysExplicit==true) model.defaults else model.legacyDefaults))
    }
    fun buildingPort(node:BuildingNode,key:String):BuildingPortModel {
        val rules=if(key.startsWith("semantic:")) building.semanticPorts else building.nodes.getValue(node.kind.name).ports
        return checkNotNull(rules.firstOrNull { rule ->
            val matches=Regex(rule.key.split('*').joinToString(".*",transform=Regex::escape)).matches(key)
            matches && (rule.conditions==null || names(rule.conditions).all { field ->
                val actual=when(field) {
                    "assembly"->node.assembly.toString();"id"->node.id
                    "roomConfig.mode"->node.roomConfig?.mode?.name;"storeyMode"->node.storeyMode;"roofType"->node.roofType
                    else->error("Unknown graph port condition $field")
                }
                val value=rule.conditions[field]
                if(jsTypeOf(value)=="object") actual!=value.not.unsafeCast<String>() else actual==value.toString()
            })
        }) { "Missing ${node.kind} port model for $key" }
    }
    class Model(value:dynamic) {
        val graphSchemas=ints(value.graphSchemas)
        val defaultSchema=value.defaultSchema.unsafeCast<Int>()
        val documentSchemas=ints(value.documentSchemas)
        val maxNodes=value.maxNodes.unsafeCast<Int>()
        val maxSources=value.maxSources.unsafeCast<Int>()
        val maxInputs=value.maxInputs.unsafeCast<Int>()
        val baseShapes=list(value.baseShapes).map { BaseShape(it.source.unsafeCast<String>(),(it.discCut ?: 0).unsafeCast<Int>()) }
        val semanticPorts=list(value.semanticPorts).map(::BuildingPortModel)
        val nodes=names(value.nodes).associateWith { NodeModel(value.nodes[it]) }
    }
    class NodeModel(value:dynamic) {
        val output=value.output?.unsafeCast<String>()
        val inputs=if(value.inputs==null) null else ShapeInputs(value.inputs)
        val ports=list(value.ports).map(::BuildingPortModel)
    }
    class ShapeInputs(value:dynamic) {
        val min=value.min.unsafeCast<Int>();val max=value.max.unsafeCast<Int>()
        private val types=list(value.types).map { it.unsafeCast<String>() }
        fun type(slot:Int)=types.getOrElse(slot) { types.last() }
    }
    class BuildingPortModel(value:dynamic) {
        val key=value.key.unsafeCast<String>()
        val accepts=list(value.accepts).map { BuildingKind.valueOf(it.unsafeCast<String>()) }.toSet()
        val min=value.min?.unsafeCast<Int>();val max=value.max?.unsafeCast<Int>()
        val expand=value.expand?.unsafeCast<Boolean>()
        val conditions:dynamic=value["when"]
    }
}
