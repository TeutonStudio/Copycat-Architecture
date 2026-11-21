@file:OptIn(ExperimentalJsExport::class)

package de.teutonstudio.copycatarchitect.web

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.client.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.Json
import kotlin.js.json

private val browserFormKinds=NodeKind.entries
private val browserBuildingKinds=BuildingKind.entries
private fun int(obj:dynamic,key:String,default:Int):Int {
    val v=obj[key] ?: return default
    require(jsTypeOf(v)=="number" && js("Number.isInteger(v) && v >= -2147483648 && v <= 2147483647").unsafeCast<Boolean>())
    return v.unsafeCast<Int>()
}
private fun string(obj:dynamic,key:String,default:String=""):String {
    val v=obj[key] ?: return default;require(jsTypeOf(v)=="string");return v.unsafeCast<String>()
}
private fun keys(value:dynamic):Array<String> = js("Object.keys(value)").unsafeCast<Array<String>>()
private fun <T> map(value:dynamic,read:(dynamic,String)->T):Map<String,T> = if(value==null) emptyMap() else keys(value).associateWith { read(value,it) }
private fun Map<String,String>.asJson():Json=json(*entries.map { it.key to it.value }.toTypedArray())
private fun P.jsPoint()=encodeP(this)
private fun part(v:dynamic)=if(v==null) Part() else decodePart(v)
private fun Part.jsPart()=encodePart(this)
private fun groups(v:dynamic)=if(v.groups==null) DEFAULT_GROUPS else v.groups.unsafeCast<Array<dynamic>>().map(::decodeMaterialGroup)
private fun formNode(v:dynamic)=decodeGraphNode(v)
private fun GraphNode.jsNode()=encodeGraphNode(this)
private fun formGraph(v:dynamic)=decodeNodeGraph(v)
private fun form(v:dynamic)=decodeForm(v)
private fun buildingNode(v:dynamic)=decodeBuildingNode(v)
private fun BuildingNode.jsNode()=encodeBuildingNode(this)
private fun buildingGraph(v:dynamic)=decodeBuildingGraph(v)
private fun building(v:dynamic)=decodeBuilding(v)
private fun module(v:dynamic)=decodeModule(v)

@JsExport
fun wireModels():Json=codecModels()

/** Export through native validation and schema selection, retaining every DTO field. */
@JsExport
fun exportDocument(isBuilding:Boolean,document:dynamic):Json = if(isBuilding) {
    val b=building(document);BuildingGraphCompiler.validateMetadata(b)
    val g=requireNotNull(b.graph).withCurrentSchema();BuildingGraphCompiler.structure(g)
    encodeBuilding(b.copy(schema=2,graph=g))
} else { val f=form(document);encodeForm(Geometry.validate(f.copy(graph=f.graph?.withTransformSchema()))) }

@JsExport
fun graphModels(): Json = json(
    "resultConstructors" to json(*GraphCards.types.map { type->type to GraphPrograms.constructors(type).map { it.name }.toTypedArray() }.toTypedArray()),
    "operators" to JSON.parse<dynamic>(graphCatalogJson).operators,"cardTypes" to GraphCards.types.toTypedArray(),"release" to graphRelease,"catalogSha256" to graphCatalogHash,
    "copycatSchemas" to GraphModels.copycat.graphSchemas.toTypedArray(),
    "buildingSchemas" to GraphModels.building.graphSchemas.toTypedArray(),
    "inputEditing" to graphInputEditingAvailable,"directAreas" to graphDirectAreasAvailable,
    "nodeProperties" to json("form" to json(*browserFormKinds.map { kind ->
        val spec=GraphModels.shape(kind);kind.name to json("commutativeAssociative" to spec.commutativeAssociative,"available" to spec.algebraAvailable)
    }.toTypedArray()),"building" to json(*browserBuildingKinds.map { kind ->
        val spec=GraphModels.building.nodes.getValue(kind.name);kind.name to json("commutativeAssociative" to spec.commutativeAssociative,"available" to spec.algebraAvailable)
    }.toTypedArray())),
    "form" to browserFormKinds.map { kind -> json("kind" to kind.name,
        "min" to GraphModels.inputs(kind).first, "max" to GraphModels.inputs(kind).last) }.toTypedArray(),
    "building" to browserBuildingKinds.map { it.name }.toTypedArray(),
    "buildingInputs" to json(*browserBuildingKinds.mapNotNull { kind ->
        val ports=GraphModels.building.nodes.getValue(kind.name).ports
        ports.firstOrNull { it.key=="in:*" && it.conditions==null && it.min!=null }?.let {
            kind.name to json("min" to it.min,"max" to (it.max ?: GraphModels.SOCKETS))
        }
    }.toTypedArray()),
    "shapes" to Adapters.all.values.map { json("id" to it.source, "name" to it.name, "max" to it.max, "tiltable" to it.tiltable) }.toTypedArray(),
    "baseShapes" to BaseShapes.all.map { shape ->
        val part=shape.part().let { if(it.source==RoofPartParameters.SOURCE) it.copy(roof=RoofPartParameters()) else it }
        json("id" to shape.id,"source" to shape.source,"discCut" to shape.discCut,"name" to shape.name,"part" to encodePart(part))
    }.toTypedArray(),
    "roofSlopes" to RoofSlope.presets.map { json("p" to it.p,"q" to it.q) }.toTypedArray(),
    "rotationAxes" to RotationAxis.entries.map { it.name }.toTypedArray(),
    "materialModes" to MaterialMode.entries.map { it.name }.toTypedArray(),
    "carpets" to BuildingGraphCompiler.CARPETS.toTypedArray(),
    "formNodes" to GraphModels.FORM_NODES, "formSources" to GraphModels.FORM_SOURCES,
    "buildingNodes" to GraphModels.BUILDING_NODES, "sockets" to GraphModels.SOCKETS,
    "positionMin" to GraphModels.coordinates.first, "positionMax" to GraphModels.coordinates.last
)

private fun inspectBuilding(n:BuildingNode,graph:BuildingGraph):Json=json("dynamicAxes" to runCatching { DynamicSegments.axes(graph,n.id).map { it.name } }.getOrDefault(emptyList()).toTypedArray(),"node" to n.jsNode(),"producedType" to buildingValueType(graph,n),"outputType" to (n.resultType ?: buildingValueType(graph,n)),
    "ports" to BuildingPorts.of(n,graph,true).map { port -> json("id" to port.key,"source" to port.source,"name" to (if(port.key=="defaultSource") "Standardwert" else if(port.key=="resultSource") "Ergebnis" else if(port.key.startsWith("arg:")) graph.graphDefinitions.orEmpty()[n.referenceKey]?.document?.graph?.let { g->GraphPrograms.parameters(g).firstOrNull { it.id==port.key.removePrefix("arg:") }?.name } else null),"accepts" to port.accepts.map { it.name }.toTypedArray(),
        "types" to nativePortTypes(port).toTypedArray(),"length" to port.length,"height" to port.height) }.toTypedArray())
private fun inspectShape(value:GraphNode,graph:NodeGraph):Json { val n=value.withValueSockets();return json("node" to value.jsNode(),"outputType" to shapeValueType(graph,n),
    "ports" to shapeInputSlots(n).map { index -> json("id" to index.toString(),"source" to n.inputs[index],"types" to arrayOf(shapeSocketType(graph,n,index) ?: "VALUE"),
        "accepts" to browserFormKinds.filter { it.name=="CONDITIONAL" || shapeSocketType(graph,n,index)==null || GraphModels.shape(it).output==shapeSocketType(graph,n,index) }.map { it.name }.toTypedArray()) }.toTypedArray()) }

@JsExport
fun describeNode(isBuilding:Boolean,document:dynamic,id:String):Json = if(isBuilding) {
    val graph=buildingGraph(document.graph);inspectBuilding(graph.nodes.first { it.id==id },graph)
} else { val graph=formGraph(document.graph);inspectShape(graph.nodes.first { it.id==id },graph) }

@JsExport
fun inspectGraph(isBuilding:Boolean,document:dynamic):Array<Json> {
    if(isBuilding) {
        BuildingGraphCompiler.validateMetadata(building(document))
        val graph=buildingGraph(document.graph);BuildingGraphCompiler.structure(graph)
        return graph.nodes.map { inspectBuilding(it,graph) }.toTypedArray()
    }
    Geometry.validateMetadata(form(document))
    val graph=formGraph(document.graph);GraphCompiler.structure(graph,groups(document))
    return graph.nodes.map { inspectShape(it,graph) }.toTypedArray()
}

@JsExport
fun applyDiagram(isBuilding: Boolean, document: dynamic, positions: Array<dynamic>, wires: Array<dynamic>): Array<Json> {
    val layout = positions.map { DiagramPosition(string(it, "id"), int(it, "x", 0), int(it, "y", 0)) }
    val connections = wires.map { DiagramConnection(string(it, "source"), string(it, "target"), string(it, "port")) }
    return if (isBuilding) DiagramEdits.building(buildingGraph(document.graph), layout, connections).nodes.map { it.jsNode() }.toTypedArray()
    else DiagramEdits.shape(formGraph(document.graph), groups(document), layout, connections).nodes.map { it.jsNode() }.toTypedArray()
}

/** A named terminal may fan out to several member sockets. Rewiring one target
 * updates the terminal and all its targets as a single native graph edit. */
private fun namedWires(groups:List<GraphGroup>,connections:List<DiagramConnection>,retained:Set<String>,previous:(String,String)->String):Pair<List<GraphGroup>,List<DiagramConnection>> {
    val wires=connections.associateBy { it.target to it.port }.toMutableMap()
    val updated=groups.map { group ->
        val incoming=group.inputConnections.filterValues { it in retained }.toMutableMap()
        group.graphInterface?.inputs.orEmpty().forEach { input ->
            val targets=input.targets.map { group.nodes[it.member] to it.port }.filter { it.first in retained }
            val changes=targets.mapNotNull { (id,port) ->
                val source=wires[id to port]?.source.orEmpty()
                source.takeIf { it!=previous(id,port) }
            }.distinct()
            require(changes.size<=1) { "A named group input accepts one source" }
            val source=changes.firstOrNull() ?: incoming[input.id].orEmpty()
            if(source.isEmpty()) incoming.remove(input.id) else incoming[input.id]=source
            targets.forEach { (id,port) ->
                if(source.isEmpty()) wires.remove(id to port) else wires[id to port]=DiagramConnection(source,id,port)
            }
        }
        group.copy(inputConnections=incoming)
    }
    return updated to wires.values.toList()
}

@JsExport
fun applyGraphDiagram(isBuilding:Boolean,document:dynamic,positions:Array<dynamic>,wires:Array<dynamic>):Json {
    val layout=positions.map { DiagramPosition(string(it,"id"),int(it,"x",0),int(it,"y",0)) }
    val connections=wires.map { DiagramConnection(string(it,"source"),string(it,"target"),string(it,"port")) }
    val retained=layout.map { it.id }.toSet()
    return if(isBuilding) {
        val original=buildingGraph(document.graph)
        val captured=GraphGroups.captureLayouts(original.nodeGroups,original.nodes.associate { it.id to GraphGroupPosition(it.x,it.y) })
        val ports=original.nodes.associate { n -> n.id to BuildingPorts.of(n,original,true,includeInactive=true).associate { it.key to it.source } }
        val dormant=original.nodes.flatMap { n ->
            val visible=BuildingPorts.of(n,original,true).map { it.key }.toSet()
            ports.getValue(n.id).filter { (key,source)->key !in visible && source in retained && n.id in retained && connections.none { it.target==n.id && it.port==key } }.map { (key,source)->DiagramConnection(source,n.id,key) }
        }
        val (groups,expanded)=namedWires(captured,connections+dormant,retained) { id,port -> ports[id]?.get(port).orEmpty() }
        encodeBuildingGraph(DiagramEdits.building(original.copy(nodeGroups=groups),layout,expanded))
    } else {
        val original=formGraph(document.graph)
        val captured=GraphGroups.captureLayouts(original.nodeGroups,original.nodes.associate { it.id to GraphGroupPosition(it.x,it.y) })
        val nodes=original.nodes.associateBy { it.id }
        val (groups,expanded)=namedWires(captured,connections,retained) { id,port -> nodes[id]?.inputs?.getOrNull(port.toInt()).orEmpty() }
        encodeNodeGraph(DiagramEdits.shape(original.copy(nodeGroups=groups),groups(document),layout,expanded))
    }
}

@JsExport
fun replaceGraphNodes(isBuilding:Boolean,document:dynamic,nodes:Array<dynamic>):Json = if(isBuilding) {
    val original=buildingGraph(document.graph)
    val captured=GraphGroups.captureLayouts(original.nodeGroups,original.nodes.associate { it.id to GraphGroupPosition(it.x,it.y) })
    encodeBuildingGraph(numericBuildingEdits(original,GraphGroups.synchronize(original,original.copy(nodes=nodes.map(::decodeBuildingNode),nodeGroups=captured)).withCurrentSchema()))
} else {
    val original=formGraph(document.graph)
    val captured=GraphGroups.captureLayouts(original.nodeGroups,original.nodes.associate { it.id to GraphGroupPosition(it.x,it.y) })
    encodeNodeGraph(numericShapeEdits(original,GraphGroups.synchronize(original,original.copy(nodes=nodes.map(::decodeGraphNode),nodeGroups=captured)).withTransformSchema()))
}

@JsExport
fun promoteNumberGraph(isBuilding:Boolean,document:dynamic):Json = if(isBuilding) encodeBuildingGraph(promoteBuildingNumbers(buildingGraph(document.graph))) else encodeNodeGraph(promoteShapeNumbers(formGraph(document.graph)))

@JsExport
fun newNode(isBuilding:Boolean,kind:String,id:String,x:Int,y:Int,explicit:Boolean=false):Json =
    if(isBuilding) GraphModels.newBuilding(BuildingKind.valueOf(kind),explicit).let { if(explicit) it else it.copy(module="") }.copy(id=id,x=x,y=y).jsNode()
    else GraphModels.newShape(NodeKind.valueOf(kind)).copy(id=id,x=x,y=y,savedCopycat=if(kind=="SAVED") SavedCopycat(parts=listOf(Part())) else null).jsNode()

@JsExport
fun adaptRectangles(previous: dynamic, next: dynamic): Json {
    val old = buildingNode(previous)
    val changed = buildingNode(next)
    return RoomGeometry.reshape(old.copy(walls = changed.walls), changed.rectangles).jsNode()
}

@JsExport
fun prepareForm(document: dynamic): Array<Json> {
    val value = form(document)
    return NodeGraph.fromParts(Geometry.validate(value).parts).nodes.map { it.jsNode() }.toTypedArray()
}

@JsExport
fun compileForm(document: dynamic): Array<Json> = Geometry.validate(form(document)).parts.map { it.jsPart() }.toTypedArray()

private data class MeshKey(val parts: List<Part>, val groups: List<MaterialGroup>, val material: String,
    val surfaces: Map<String, String>, val block: String, val component: String)

private fun boxes(solid: Solid) = solid.boxes().map { box ->
    intArrayOf(box.x, box.y, box.z, box.maxX, box.maxY, box.maxZ)
}.toTypedArray()
private fun solidMesh(material: String, solid: Solid, positions: List<P>, component: String) = json(
    "material" to material, "boxes" to boxes(solid), "positions" to positions.map { intArrayOf(it.x, it.y, it.z) }.toTypedArray(),
    "component" to component
)
private fun exactMesh(material:String,faces:List<ExactSurface>,positions:List<P>,component:String)=json(
    "material" to material,"boxes" to emptyArray<IntArray>(),
    "faces" to faces.map { face -> json(
        "normal" to doubleArrayOf(face.plane.x.toDouble(),face.plane.y.toDouble(),face.plane.z.toDouble()),
        "vertices" to face.vertices.map { p -> doubleArrayOf(p.x.toDouble(),p.y.toDouble(),p.z.toDouble()) }.toTypedArray()
    ) }.toTypedArray(),
    "positions" to positions.map { intArrayOf(it.x,it.y,it.z) }.toTypedArray(),"component" to component
)
private fun geometryMeshes(resolved:ResolvedGeometry,positions:List<P>,component:String,filter:String="",material:(String)->String):List<Json> =
    if(resolved.exactSolid==null) resolved.owned.filterKeys { filter.isEmpty() || it==filter }.entries.groupBy { material(it.key) }.map { (id,regions) ->
        solidMesh(id,regions.fold(Solid()) { solid,region -> solid.union(region.value) },positions,component)
    }
    else (if(filter.isEmpty()) resolved.visibleSurfaces() else resolved.exactOwned[filter]?.surfaces().orEmpty().map { OwnedExactSurface(it,filter) })
        .groupBy { material(it.group) }.map { (id,faces) ->
        exactMesh(id,faces.map { it.surface },positions,component)
    }
private fun fullBlock(key: MeshKey, positions: List<P>) = json(
    "material" to key.block, "boxes" to arrayOf(intArrayOf(0, 0, 0, 16, 16, 16)),
    "positions" to positions.map { intArrayOf(it.x, it.y, it.z) }.toTypedArray(), "component" to key.component
)

@JsExport
fun previewGraph(isBuilding: Boolean, document: dynamic, library: dynamic, selected: String, surfaces:dynamic=null): Json {
    if (!isBuilding) {
        val original = form(document)
        Geometry.validateMetadata(original)
        val graph = requireNotNull(original.graph)
        val n = if(selected.isEmpty()) graph.nodes.single { it.kind==NodeKind.OUTPUT }
            else selectedShapeNode(graph,selected)
        if(n.kind in CopycatTransforms.valueKinds) {
            GraphCompiler.structure(graph,original.groups)
            return json("count" to 0, "raw" to 0, "meshes" to emptyArray<Json>(),
            "value" to when { n.kind.name in UnifiedOperators.valueKinds->UnifiedOperators.value(UnifiedOperators.valueNodes(graph.nodes),n.id).toString();n.kind.name in setOf("BOOLEAN","INT_COMPARE","BOOLEAN_LOGIC")->"Boolean: ${if(shapeBooleanValue(graph,n.id)) "Wahr" else "Falsch"}";n.kind.name in GraphValues.vectorKinds->"Position: ${numericShape(graph).vector(n.id)} hB";n.kind.name in GraphValues.integerKinds->"${if(shapeValueType(graph,graph.nodes.first { it.id==selected })=="COUNT") "Anzahl" else "Ganzzahl"}: ${numericShape(graph).integer(n.id)}";n.kind.name=="TRANSFORMATION"->shapeTransformationDescription(n,graph);n.kind==NodeKind.MIRRORING->"Spiegelung: ${(n.mirror ?: CopycatMirror()).plane}";else->"${n.rotationAxis.name}: ${n.quarterTurns*90}°" }, "warnings" to arrayOf("Parameterwert, keine Geometrie"))
        }
        val parts = if(selected.isEmpty()) GraphCompiler.compile(graph,original.groups) else GraphCompiler.intermediate(graph,n.id,original.groups)
        val filter = if(n.kind==NodeKind.OUTPUT) n.previewGroup else ""
        val resolved = Geometry.resolve(parts,original.groups)
        val meshes = geometryMeshes(resolved,listOf(P()),"form",filter) { it }
        return json("count" to resolved.owned.values.sumOf { it.count() }, "raw" to if(parts.isEmpty()) 0 else Extrusion.cost(parts,original.groups),
            "materials" to MaterialRequirements.form(parts,original.groups,map(surfaces) { obj,key -> obj[key] as String }).mapValues { it.value.toString() }.asJson(), "meshes" to meshes.toTypedArray())
    }
    val b = building(document)
    BuildingGraphCompiler.validateMetadata(b)
    val forms = map(library.forms) { obj, key -> form(obj[key]) }
    val modules = map(library.modules) { obj, key -> module(obj[key]) }
    val rawGraph = requireNotNull(b.graph)
    val rawResult=rawGraph.resultNode()
    val prepared=GraphPrograms.expand(rawGraph,b.palette,forms,modules)
    val graph=prepared.graph
    val result=graph.resultNode()
    val cardTarget=if((selected.isEmpty() || selected==result.id) && result.resultType!=null) GraphPrograms.resultSource(result).also { require(it.isNotEmpty()) { "Ergebnis verbinden" } } else selected
    GraphPrograms.diagnostic(graph,result)?.let { error(it) }
    val selectedNode=cardTarget.takeIf(String::isNotEmpty)?.let { selectedBuildingNode(graph,it) }
    if(selectedNode?.kind?.name in UnifiedOperators.valueKinds) {
        val value=UnifiedOperators.value(UnifiedOperators.buildingValueNodes(graph.nodes),selectedNode!!.id)
        return json("count" to 0,"raw" to 0,"meshes" to emptyArray<Json>(),"value" to value.toString())
    }
    if(selectedNode?.kind?.name=="ROOF_TEMPLATE") {
        BuildingGraphCompiler.structure(graph)
        return json("count" to 0,"raw" to 0,"meshes" to emptyArray<Json>(),"value" to "Dachvorlage · ${selectedNode.inputs.count(String::isNotEmpty)} / 8 Bauteile")
    }
    if(selectedNode?.kind?.name in GraphValues.kinds) {
        BuildingGraphCompiler.structure(graph)
        return json("count" to 0,"raw" to 0,"meshes" to emptyArray<Json>(),"value" to if(selectedNode!!.kind.name in setOf("BOOLEAN","INT_COMPARE","BOOLEAN_LOGIC")) "Boolean: ${if(buildingBooleanValue(graph,selectedNode.id)) "Wahr" else "Falsch"}" else if(selectedNode.kind.name in GraphValues.integerKinds) "${if(buildingValueType(graph,graph.nodes.first { it.id==cardTarget })=="COUNT") "Anzahl" else "Ganzzahl"}: ${numericBuilding(graph).integer(selectedNode.id)}" else "Position: ${numericBuilding(graph).vector(selectedNode.id)} hB")
    }
    if(selectedNode?.kind?.name in setOf("ROTATION","MIRRORING","POSITION","TRANSFORMATION")) {
        BuildingGraphCompiler.structure(graph)
        val value=if(selectedNode!!.kind.name=="TRANSFORMATION") buildingTransformationDescription(selectedNode,graph) else if(selectedNode.kind==BuildingKind.POSITION) "Versatz: ${selectedNode.translation}" else if(selectedNode.kind==BuildingKind.ROTATION) selectedNode.rotations.orEmpty().joinToString(" → ") { "${it.axis}: ${it.quarterTurns*90}°" } else selectedNode.mirrors.orEmpty().joinToString(" → ") { it.plane.name }
        return json("count" to 0,"raw" to 0,"meshes" to emptyArray<Json>(),"value" to value.ifEmpty { "Identität (leere Liste)" })
    }
    val plan = if(cardTarget.isEmpty()) BuildingGraphCompiler.compile(b,forms,modules).plan
        else BuildingGraphCompiler.Evaluation(graph,prepared.palette,prepared.forms,prepared.modules).let { evaluation ->
            if(graph.nodes.first { it.id==cardTarget }.kind in setOf(BuildingKind.FLOOR,BuildingKind.CEILING)) evaluation.contextualPlatePreview(cardTarget) else evaluation.preview(cardTarget)
        }
    val filter = graph.nodes.firstOrNull { it.id == selected }?.preview
        ?: graph.resultNode().preview
    val grouped = plan.placements.filter { filter == "all" || it.component == filter ||
        filter == "walls" && it.component in setOf("wall", "trim") || filter == "roof" && it.component == "gable" }
        .groupBy({ MeshKey(it.parts, it.groups, it.material, it.surfaces, it.block, it.component) }, { it.pos })
    val meshes = grouped.flatMap { (key, positions) ->
        if (key.block.isNotEmpty()) listOf(fullBlock(key, positions))
        else geometryMeshes(Geometry.resolve(key.parts,key.groups),positions,key.component) { id -> key.surfaces[id] ?: key.material }
    }
    return json("count" to plan.placements.size, "raw" to plan.raw, "materials" to MaterialRequirements.aggregate(plan.materials).mapValues { it.value.toString() }.asJson(),
        "warnings" to plan.warnings.toTypedArray(), "meshes" to meshes.toTypedArray())
}

@JsExport
fun starterBuilding():Json=encodeBuilding(Building(schema=2,name="Neues Gebäude",graph=authoringStarter()))

@JsExport
fun authoringBuildingNode(value:dynamic):Json=encodeBuildingNode(authoringNode(decodeBuildingNode(value)))

@JsExport
fun sourceMounting(value:dynamic):String = directionName(PartRotations.normal(part(value)))

@JsExport
fun mountSource(value:dynamic,normal:String):Json {
    val source=part(value)
    val target=when(normal) {
        "up"->P(0,1,0);"down"->P(0,-1,0);"east"->P(1,0,0);"west"->P(-1,0,0)
        "south"->P(0,0,1);"north"->P(0,0,-1);else->error("Invalid mounting")
    }
    if(PartRotations.normal(source)==target) return source.jsPart()
    for(q in listOf(1,3,2)) for(axis in listOf(RotationAxis.X,RotationAxis.Z,RotationAxis.Y)) {
        val result=PartRotations.rotate(source.copy(offset=P()),axis,q)?.copy(offset=source.offset) ?: continue
        if(PartRotations.normal(result)==target) return result.jsPart()
    }
    error("This source has no supported native orientation for the selected mounting")
}

@JsExport
fun insertSavedCopycat(document:dynamic,source:dynamic,nodeId:String):Json {
    val target=form(document)
    val result=SavedCopycats.insert(target,form(source),replaceId=nodeId)
    val inserted=result.form
    return json("groups" to inserted.groups.map { json("id" to it.id,"name" to it.name,"role" to it.role,"priority" to it.priority) }.toTypedArray(),
        "groupMapping" to result.groups.asJson(),"nodeId" to result.nodeId,
        "nodes" to inserted.graph!!.nodes.map { it.jsNode() }.toTypedArray())
}

/** Orthographic authoring uses the same placement and rotation rules as Minecraft. */
@JsExport
fun arrangementGraph(document:dynamic,library:dynamic,selected:String,view:String):Json {
    val b=building(document)
    val graph=requireNotNull(b.graph)
    BuildingGraphCompiler.structure(graph)
    val node=graph.nodes.first { it.id==selected }
    val forms=map(library.forms) { obj,key -> form(obj[key]) }
    val modules=map(library.modules) { obj,key -> module(obj[key]) }
    val evaluation=BuildingGraphCompiler.Evaluation(graph,b.palette,forms,modules)
    val plane=SegmentView.valueOf(view)
    if(node.kind==BuildingKind.PATTERN) {
        val draft=SegmentArrangement(node.inputs.map(evaluation::segment),node)
        return json("transforms" to draft.transforms.map { json("offset" to it.offset.jsPoint(),"axis" to it.axis.name,"quarterTurns" to it.quarterTurns) }.toTypedArray(),
            "items" to node.inputs.mapIndexed { i,id -> json("id" to id,"label" to graph.nodes.first { it.id==id }.let { it.name.ifBlank { id } },
                "cells" to draft.projected(i,plane).map { arrayOf(it.x,it.z) }.toTypedArray()) }.toTypedArray())
    }
    if(node.kind==BuildingKind.SEGMENT_OPERATOR || node.kind in SegmentBooleans.kinds || node.kind in setOf(BuildingKind.SEGMENT_ROTATE,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_TRANSFORM)) {
        val editable=SegmentBooleans.arrangeable(node)
        val ids=if(editable) node.inputs else node.inputs.take(1)+node.id
        val items=ids.mapIndexed { i,id ->
            val offset=if(editable) operandOffset(node,i) else P()
            var problem:String?=null
            val cells=try {
                require(id.isNotEmpty()) { "Eingang ${i+1} verbinden" }
                evaluation.segment(id).cells.filter { !it.cell.empty && it.cell.block!="minecraft:air" }
                    .map { plane.project(it.cell.pos+offset) }.toSet().map { arrayOf(it.x,it.z) }.toTypedArray()
            } catch(cause:Throwable) { problem=cause.message ?: "Quelle noch nicht bereit";emptyArray<Array<Int>>() }
            json("id" to id,"slot" to i,"label" to (if(!editable && i==1) "Ergebnis" else graph.nodes.firstOrNull { it.id==id }?.let { it.name.ifBlank { id } } ?: "Eingang ${i+1}"),
                "offset" to offset.jsPoint(),"error" to problem,"cells" to cells)
        }
        return json("items" to items.toTypedArray(),"editable" to editable)
    }
    require(node.kind==BuildingKind.STOREY && node.storeyMode=="rooms") { "Select a segment pattern or storey made from rooms" }
    val areas=node.inputs.mapIndexed { i,id -> RoomArrangementArea.of(i,graph.nodes.first { it.id==id },evaluation.room(id),node.offsets.getOrNull(i) ?: P()) }
    var conflicts=RoomArrangement.conflicts(areas)
    var pending:String?=null
    if(conflicts.isEmpty()) try { evaluation.storey(node.id) }
    catch(error:InvalidRoomArrangement) { conflicts=error.conflicts }
    catch(error:Throwable) { pending=error.message ?: "Quellen vervollständigen" }
    fun cells(points:Collection<P>)=points.map { plane.project(it) }.toSet().map { arrayOf(it.x,it.z) }.toTypedArray()
    return json("items" to areas.map { area -> json("id" to node.inputs[area.index],"label" to area.label,
        "cells" to cells(area.interior),"walls" to cells(area.walls)) }.toTypedArray(),
        "pending" to pending,"conflicts" to conflicts.map { c -> json("kind" to c.kind.name,
            "first" to c.first.index,"second" to c.second.index,"firstLabel" to c.first.label,"secondLabel" to c.second.label,
            "cells" to cells(c.cells)) }.toTypedArray())
}

@JsExport
fun editNativeGroup(isBuilding:Boolean,document:dynamic,action:String,id:String,name:String,selection:Array<String>):Json = if(isBuilding) {
    val graph=buildingGraph(document.graph)
    val changed=when(action) {
        "create"->GraphGroups.create(graph,selection.toSet(),name)
        "initialize"->graph.copy(nodeGroups=GroupGraphInterfaces.initializeInstances(diagram(graph),id).nodeGroups)
        "instantiate"->GraphGroups.instantiate(graph,id)
        "rename"->GraphGroups.rename(graph,id,name)
        "ungroup"->GraphGroups.ungroup(graph,id)
        else->error("Unknown group action")
    }.withCurrentSchema()
    BuildingGraphCompiler.structure(changed);encodeBuildingGraph(changed)
} else {
    val graph=formGraph(document.graph)
    val changed=when(action) {
        "create"->GraphGroups.create(graph,selection.toSet(),name)
        "initialize"->graph.copy(nodeGroups=GroupGraphInterfaces.initializeInstances(diagram(graph),id).nodeGroups)
        "instantiate"->GraphGroups.instantiate(graph,id)
        "rename"->GraphGroups.rename(graph,id,name)
        "ungroup"->GraphGroups.ungroup(graph,id)
        else->error("Unknown group action")
    }
    GraphCompiler.structure(changed,groups(document));encodeNodeGraph(changed)
}

/** Canonical DTO representation for comparing changes while retaining opaque JSON metadata. */
@JsExport
fun normalizeDocument(isBuilding:Boolean,document:dynamic):Json =
    if(isBuilding) encodeBuilding(building(document)) else encodeForm(form(document))

/** Reusable library selections use native reference traversal, including hidden
 * roof/room/attachment fields which are not all represented by visible wires. */
@JsExport
fun remapBuildingReferences(value:dynamic,ids:dynamic):Json = encodeBuildingNode(
    GraphGroups.remap(buildingNode(value)) { _,ref -> if(ref=="empty_wall") ref else ids[ref]?.unsafeCast<String>().orEmpty() }
)

/** Native port and inspector contract; React does not keep its own node whitelist. */
@JsExport
fun editorContract():Json=JSON.parse<Json>(graphCatalogJson)

@JsExport
fun inspectorContext(document:dynamic,id:String):Json {
    val b=building(document);val graph=requireNotNull(b.graph)
    val node=GraphPrograms.producer(graph.nodes.first { it.id==id })
    val ports=BuildingPorts.of(graph.nodes.first { it.id==id },graph,true)
    val encoded=encodeBuildingNode(node).asDynamic()
    val references=json()
    fun visit(value:dynamic,path:List<String>) {
        if(value==null) return
        if(jsTypeOf(value)=="string") {
            val probe=JSON.parse<dynamic>(JSON.stringify(encoded))
            var parent:dynamic=probe
            path.dropLast(1).forEach { parent=parent[it] }
            parent[path.last()]="__inspector_probe__"
            var reference:String?=null
            runCatching { GraphGroups.remap(decodeBuildingNode(probe)) { key,source -> if(source=="__inspector_probe__") reference=key;source } }
            reference?.let { key ->
                val kinds=ports.firstOrNull { it.key==key }?.accepts ?: when {
                    key=="template:wall"->setOf(BuildingKind.SEGMENT,BuildingKind.PATTERN,BuildingKind.SEGMENT_ROTATE,BuildingKind.SEGMENT_MIRROR)+BuildingKind.entries.filter { it.name=="SEGMENT_TRANSFORM" }+SegmentBooleans.kinds
                    key.startsWith("template:") || key.startsWith("family:")->CopycatBooleans.sourceKinds
                    else->emptySet()
                }
                if(kinds.isNotEmpty()) references[path.joinToString(".")]=graph.nodes.filter { it.id!=id && it.kind in kinds }.map { json("value" to it.id,"label" to it.name.ifBlank { it.id },"kind" to it.kind.name) }.toTypedArray()
            }
        } else if(jsTypeOf(value)=="object") keys(value).forEach { key -> visit(value[key],path+key) }
    }
    visit(encoded,emptyList())
    val derived=json()
    fun source(id:String,vararg fields:String) { if(id.isNotEmpty()) fields.forEach { derived[it]=id } }
    when(node.kind) {
        BuildingKind.ROTATION_SEGMENT->source(node.dimensions.orEmpty()["length"].orEmpty(),"length")
        BuildingKind.AREA->{source(node.dimensions.orEmpty()["length"].orEmpty(),"length");source(node.dimensions.orEmpty()["height"].orEmpty(),"height")}
        BuildingKind.SEGMENT->if(node.dynamicAxes.isNullOrEmpty()) {
            if(graph.nodes.firstOrNull { it.id==node.layout }?.kind==BuildingKind.AREA) source(node.layout,"length","height")
            else source(node.layout,"length","height","depth")
        }
        BuildingKind.SEGMENT_LAYOUT->source(node.geometry,"length","height")
        BuildingKind.ROOM_GEOMETRY->source(node.geometry,"coordinates")
        BuildingKind.ROOM->if(node.assembly) {
            if(graph.nodes.firstOrNull { it.id==node.geometry }?.kind==BuildingKind.AREA) source(node.geometry,"rectangles")
            else source(node.geometry,"height","rectangles")
        }
        else->Unit
    }
    val wallSections=if(node.kind!=BuildingKind.ROOM) emptyList() else if(node.roomConfig?.mode==RoomMode.SEGMENTS) {
        val config=graph.nodes.firstOrNull { it.id==node.layout }?.roomLayout
        config?.instances.orEmpty().filter { it.contribution in setOf(RoomContribution.BOUNDARY,RoomContribution.OPENING) }.map { json("key" to "wall:${it.id}","label" to it.id) }+
            config?.boundary.orEmpty().indices.map { json("key" to "boundary:$it","label" to "Grenzabschnitt ${it+1}") }
    } else RoomWallJoints.runs(graph.resolvedNode(node)).map { json("key" to "wall:${it.id}","label" to "${listOf("Norden","Osten","Süden","Westen")[it.direction]} · ${it.id}") }
    return json("wallSections" to wallSections.toTypedArray(),"palette" to b.palette.asJson(),"references" to references,"derived" to derived,"resolved" to encodeBuildingNode(graph.resolvedNode(node)),
        "storeys" to graph.storeyInstances().map { json("value" to it.id,"label" to "${it.level}: ${graph.nodes.first { n->n.id==it.source }.name.ifBlank { it.source }}") }.toTypedArray(),
        "slopes" to RoofSlope.presets.map { json("p" to it.p,"q" to it.q) }.toTypedArray(),
        "surfaceBlocks" to LandscapeGeometry.surfaceBlocks.toTypedArray(),"groundBlocks" to LandscapeGeometry.groundBlocks.toTypedArray(),"fenceBlocks" to LandscapeGeometry.fenceBlocks.toTypedArray())
}

private fun diagram(graph:BuildingGraph):KilaGraphData {
    val ports=graph.nodes.associate { it.id to BuildingPorts.of(it,graph,true) }
    return KilaGraphData(graph.nodes.map { n -> KilaNode(n.id,n.name.ifBlank { n.kind.name },n.x,n.y,
        ports.getValue(n.id).map { it.key },output=n.id!=graph.resultNode().id,
        outputType=buildingValueType(graph,n).orEmpty(),inputTypes=ports.getValue(n.id).map { p -> nativePortTypes(p).joinToString("|") },
        inputKeys=ports.getValue(n.id).map { it.key }) },
        graph.nodes.flatMap { n -> ports.getValue(n.id).mapIndexedNotNull { slot,port -> port.source.takeIf(String::isNotEmpty)?.let { KilaLink(it,n.id,slot) } } },graph.nodeGroups)
}
private fun diagram(graph:NodeGraph):KilaGraphData = KilaGraphData(graph.nodes.map { n ->
    KilaNode(n.id,n.id,n.x,n.y,n.inputs.indices.map { it.toString() },output=n.kind!=NodeKind.OUTPUT,
        outputType=shapeValueType(graph,n).orEmpty(),inputTypes=n.inputs.indices.map { shapeSocketType(graph,n,it).orEmpty() },inputKeys=n.inputs.indices.map { it.toString() })
},graph.nodes.flatMap { n -> n.inputs.mapIndexedNotNull { slot,source -> source.takeIf(String::isNotEmpty)?.let { KilaLink(it,n.id,slot) } } },graph.nodeGroups)

@JsExport
fun groupInterfaceContext(isBuilding:Boolean,document:dynamic,id:String):Json {
    val data=if(isBuilding) diagram(buildingGraph(document.graph)) else diagram(formGraph(document.graph))
    val group=data.nodeGroups.first { it.id==id }
    val members=group.nodes.map { member -> data.nodes.first { it.id==member } }
    return json("graphTypes" to (if(isBuilding) BuildingKind.entries.map(::buildingGraphType).distinct() else listOf("shape","rotation","mirroring","position","transformation","INT","COUNT","BOOLEAN")).toTypedArray(),
        "members" to members.mapIndexed { i,n -> json("index" to i,"label" to n.title,"outputType" to n.outputType,
            "ports" to n.inputKeys.mapIndexed { slot,key -> json("key" to key,"types" to n.inputTypes[slot].split('|').toTypedArray()) }.toTypedArray()) }.toTypedArray())
}

/** The same named-terminal adapter as the architect table rewires all instances. */
@JsExport
fun editGroupInterface(isBuilding:Boolean,document:dynamic,id:String,face:dynamic):Json {
    val next=decodeGraphGroupInterface(face)
    if(isBuilding) {
        val graph=buildingGraph(document.graph);val original=diagram(graph)
        val edited=GroupGraphInterfaces.withInterface(original,original.nodeGroups.first { it.id==id }.copy(graphInterface=next))
        val reserved=GraphGroups.reserveInputs(graph.copy(nodeGroups=edited.nodeGroups))
        val positions=edited.nodes.map { DiagramPosition(it.id,it.x,it.y) }
        val wires=edited.links.map { link -> DiagramConnection(link.source,link.target,edited.nodes.first { it.id==link.target }.inputKeys[link.slot]) }
        return encodeBuildingGraph(DiagramEdits.building(reserved,positions,wires))
    }
    val graph=formGraph(document.graph);val original=diagram(graph)
    val edited=GroupGraphInterfaces.withInterface(original,original.nodeGroups.first { it.id==id }.copy(graphInterface=next))
    return encodeNodeGraph(DiagramEdits.shape(graph.copy(nodeGroups=edited.nodeGroups),groups(document),edited.nodes.map { DiagramPosition(it.id,it.x,it.y) },
        edited.links.map { DiagramConnection(it.source,it.target,it.slot.toString()) }))
}

@JsExport
fun editInspectorNode(isBuilding:Boolean,document:dynamic,value:dynamic):Json {
    if(!isBuilding) {
        val graph=formGraph(document.graph);var node=decodeGraphNode(value)
        val before=graph.nodes.first { it.id==node.id }
        if(node.part.roof!=before.part.roof && node.part.source==RoofPartParameters.SOURCE) {
            node=node.copy(part=node.part.copy(exact=null,exactClip=null))
            require(!Geometry.exact(node.part).isEmpty()) { "Roof slice must contain geometry" }
        }
        val changed=graph.replace(node);GraphCompiler.structure(changed,groups(document));return encodeNodeGraph(changed)
    }
    val original=buildingGraph(document.graph);var node=decodeBuildingNode(value)
    val before=original.nodes.first { it.id==node.id }
    node.inlinePart?.let { part ->
        if(part.roof!=before.inlinePart?.roof && part.source==RoofPartParameters.SOURCE) {
            val edited=part.copy(exact=null,exactClip=null)
            require(!Geometry.exact(edited).isEmpty()) { "Roof slice must contain geometry" }
            node=node.copy(inlinePart=edited)
        }
    }
    var changed=original
    if(before.coordinates!=node.coordinates) {
        changed=when(before.kind) {
            BuildingKind.AREA,BuildingKind.ROOM_GEOMETRY->changed.editRoomGeometry(node.id,node.coordinates)
            BuildingKind.GARDEN,BuildingKind.PATH->changed.editLandscapeCoordinates(node.id,node.coordinates)
            else->changed
        }
    }
    if(before.rectangles!=node.rectangles && (node.kind==BuildingKind.ROOM || node.kind==BuildingKind.STOREY && node.storeyMode=="walls")) changed=changed.reshapeRoom(node.id,node.rectangles)
    val geometryEdited=changed!=original
    val current=changed.nodes.first { it.id==node.id }
    val next=if(geometryEdited) node.copy(coordinates=current.coordinates,rectangles=current.rectangles,walls=current.walls,verticalEdges=current.verticalEdges) else node
    changed=changed.replace(next).remapGeometryDependents(original).withCurrentSchema()
    BuildingGraphCompiler.structure(changed)
    return encodeBuildingGraph(changed)
}

@JsExport
fun duplicateGraph(isBuilding:Boolean,document:dynamic,ids:Array<String>,copies:Array<String>):Json = if(isBuilding) {
    encodeBuilding(building(document).let { it.copy(graph=GraphDuplicates.building(requireNotNull(it.graph),ids.toList(),copies.toList())) })
} else encodeForm(form(document).let {it.copy(graph=GraphDuplicates.shape(requireNotNull(it.graph),it.groups,ids.toList(),copies.toList()))})

/** Repairable node diagnostics do not mutate a document or discard its last valid preview. */
@JsExport
fun transformDiagnostics(isBuilding:Boolean,document:dynamic,library:dynamic):Array<Json> = if(!isBuilding) {
    val f=form(document);val graph=requireNotNull(f.graph)
    graph.nodes.filter { it.kind.name in GraphValues.kinds || !it.copycatTransforms.isNullOrEmpty() || it.kind in CopycatTransforms.operatorKinds }.mapNotNull { n ->
        runCatching { if(n.kind.name in GraphValues.integerKinds) numericShape(graph).integer(n.id) else if(n.kind.name in GraphValues.vectorKinds) numericShape(graph).vector(n.id) else if(n.kind.name in setOf("BOOLEAN","INT_COMPARE","BOOLEAN_LOGIC")) shapeBooleanValue(graph,n.id) else GraphCompiler.intermediate(graph,n.id,f.groups) }.exceptionOrNull()?.let { json("nodeId" to n.id,"message" to (it.message ?: "Invalid Copycat transformation")) }
    }.toTypedArray()
} else {
    val b=building(document);val graph=requireNotNull(b.graph)
    val evaluation=BuildingGraphCompiler.Evaluation(graph,b.palette,map(library.forms) {obj,key->form(obj[key])},map(library.modules) {obj,key->module(obj[key])})
    graph.nodes.filter { it.kind==BuildingKind.RESULT || it.dimensions.orEmpty().isNotEmpty() || it.kind.name in GraphValues.kinds || !it.copycatTransforms.isNullOrEmpty() || it.kind in BuildingCopycatTransforms.operatorKinds }.mapNotNull { n ->
        runCatching { GraphPrograms.diagnostic(graph,n)?.let { error(it) };if(n.kind==BuildingKind.RESULT) evaluation.preview(n.id) else if(n.dimensions.orEmpty().isNotEmpty()) BuildingCounts.resolve(graph,n,true) else if(n.kind.name in GraphValues.integerKinds) numericBuilding(graph).integer(n.id) else if(n.kind.name in GraphValues.vectorKinds) numericBuilding(graph).vector(n.id) else if(n.kind.name in setOf("BOOLEAN","INT_COMPARE","BOOLEAN_LOGIC")) buildingBooleanValue(graph,n.id) else evaluation.preview(n.id) }.exceptionOrNull()?.let { json("nodeId" to n.id,"message" to (it.message ?: "Invalid Copycat transformation")) }
    }.toTypedArray()
}

/** Area preview uses the same resolved cells as every geometry consumer. */
@JsExport
fun areaGraph(document:dynamic,id:String):Json {
    val graph=buildingGraph(document.graph);val node=GraphPrograms.producer(graph.nodes.first { it.id==id });require(node.kind==BuildingKind.AREA)
    val resolved=BuildingCounts.resolve(graph,node,true);val area=graph.areaOf(resolved)
    return json("points" to area?.points.orEmpty().map { it.jsPoint() }.toTypedArray(),"width" to (area?.width ?: 0),"length" to (area?.length ?: 0))
}

@JsExport
fun starterCard():Json=encodeBuilding(Building(schema=2,name="Neuer Graph",graph=GraphCards.starter()))

@JsExport
fun cardForm(document:dynamic,library:dynamic):Json {
    val b=building(document);val graph=requireNotNull(b.graph);val result=graph.resultNode()
    require(result.resultType=="COPYCAT") { "Select a Copycat card" }
    val forms=map(library.forms) { obj,key->form(obj[key]) };val modules=map(library.modules) { obj,key->module(obj[key]) }
    val plan=BuildingGraphCompiler.Evaluation(graph,b.palette,forms,modules).preview(result.id)
    require(plan.placements.size<=1) { "A Copycat card must fit one block" }
    val placement=plan.placements.firstOrNull()
    val value=Geometry.validate(Form(name=b.name,parts=placement?.parts.orEmpty(),groups=placement?.groups ?: DEFAULT_GROUPS))
    return json("form" to encodeForm(value),"materials" to placement?.surfaces.orEmpty().asJson())
}


/** Socket order is native graph data, never a cosmetic wire reorder. */
@JsExport
fun editGraphInputs(isBuilding:Boolean,document:dynamic,id:String,order:Array<Int>):Json = if(isBuilding) {
    val changed=updateBuildingInputs(buildingGraph(document.graph),id,order.toList())
    BuildingGraphCompiler.structure(changed);encodeBuildingGraph(changed)
} else {
    val changed=updateShapeInputs(formGraph(document.graph),id,order.toList())
    GraphCompiler.structure(changed,groups(document));encodeNodeGraph(changed)
}

@JsExport
fun configureGraphProgram(document:dynamic,id:String,field:String,value:String):Json {
    val graph=GraphPrograms.configure(buildingGraph(document.graph),id,field,value)
    BuildingGraphCompiler.structure(graph)
    return encodeBuildingGraph(graph)
}
@JsExport
fun graphParameters(document:dynamic):Array<Json> = GraphPrograms.parameters(buildingGraph(document.graph)).map { json("id" to it.id,"name" to it.name,"type" to it.type,"defaultSource" to it.defaultSource) }.toTypedArray()

@JsExport
fun legacyStarterCard():Json=encodeBuilding(Building(schema=2,name="Neuer Graph",graph=GraphCards.legacyStarter()))

@JsExport
fun orderGraphParameters(document:dynamic,order:Array<String>):Json {
    val g=buildingGraph(document.graph)
    require(order.toSet()==GraphPrograms.parameters(g).map { it.id }.toSet() && order.distinct().size==order.size)
    return encodeBuildingGraph(g.copy(groupInputOrder=order.toList()).withCurrentSchema())
}

@JsExport
fun importGraphInterface(document:dynamic,face:dynamic,members:Array<String>):Json = encodeBuildingGraph(GraphPrograms.importInterface(buildingGraph(document.graph),decodeGraphGroupInterface(face),members.toList()))
