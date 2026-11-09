@file:OptIn(ExperimentalJsExport::class)

package de.teutonstudio.copycatarchitect.web

import de.teutonstudio.copycatarchitect.domain.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.Json
import kotlin.js.json

// These are the lossless browser authoring capabilities; native-only fields must
// be rejected before a preview or export can silently discard them.
private val browserFormKinds=listOf(NodeKind.INPUT,NodeKind.UNION,NodeKind.INTERSECTION,NodeKind.DIFFERENCE,NodeKind.SYMMETRIC_DIFFERENCE,NodeKind.ROTATE,NodeKind.ROTATION,NodeKind.OUTPUT,NodeKind.SAVED,NodeKind.MIRROR)
private val browserBuildingKinds=listOf(
    BuildingKind.COPYCAT,BuildingKind.COPYCAT_ROTATE,BuildingKind.COPYCAT_MIRROR,
    BuildingKind.COPYCAT_UNION,BuildingKind.COPYCAT_INTERSECTION,BuildingKind.COPYCAT_DIFFERENCE,BuildingKind.COPYCAT_SYMMETRIC_DIFFERENCE,
    BuildingKind.SEGMENT,BuildingKind.SEGMENT_ROTATE,BuildingKind.SEGMENT_MIRROR,
    BuildingKind.SEGMENT_UNION,BuildingKind.SEGMENT_INTERSECTION,BuildingKind.SEGMENT_DIFFERENCE,BuildingKind.SEGMENT_SYMMETRIC_DIFFERENCE,
    BuildingKind.PATTERN,BuildingKind.FLOOR,BuildingKind.CEILING,BuildingKind.ROOM,BuildingKind.STOREY,BuildingKind.ROOF,BuildingKind.BUILDING
)
private val browserFormFields=setOf("id","kind","inputs","part","x","y","materialMode","materialGroup","previewGroup","inputGroups","rotationAxis","quarterTurns","savedCopycat","mirror")
private val browserBuildingFields=setOf("id","kind","name","x","y","inputs","module","block","length","height","rectangles","walls","floor","ceiling","offsets","form","thickness","occupied","finish","levels","roof","roofType","ridge","overhang","materialMode","role","preview","storeyMode","corner","surfaces","rotationAxis","quarterTurns","mirror","mirrorPositionsOnly","patternAxis","transforms")
private fun browserNode(value:dynamic,isBuilding:Boolean):dynamic {
    val kind=string(value,"kind",if(isBuilding) "SEGMENT" else "INPUT")
    require(if(isBuilding) browserBuildingKinds.any { it.name==kind } else browserFormKinds.any { it.name==kind }) { "$kind requires the native editor" }
    GraphModels.validateBrowserFields(value,isBuilding,if(isBuilding) browserBuildingFields else browserFormFields)
    return value
}
private fun browserGraph(value:dynamic,isBuilding:Boolean):dynamic {
    val schema=int(value,"schema",if(isBuilding) 1 else 2)
    require(if(isBuilding) schema==1 else schema in 1..2) { "Unsupported browser graph schema; use the native editor" }
    require(array(value,"nodeGroups").isNullOrEmpty() && value.projection==null) { "Graph groups and projections require the native editor" }
    return value
}

private fun int(obj: dynamic, key: String, default: Int): Int {
    val value = obj[key]
    if (value == null) return default
    require(jsTypeOf(value) == "number" && isInteger(value)) { "$key must be an integer" }
    return value.unsafeCast<Int>()
}
private fun string(obj: dynamic, key: String, default: String = ""): String {
    val value = obj[key] ?: return default
    require(jsTypeOf(value) == "string") { "$key must be a string" }
    return value.unsafeCast<String>()
}
private fun bool(obj: dynamic, key: String, default: Boolean = false): Boolean {
    val value = obj[key] ?: return default
    require(jsTypeOf(value) == "boolean") { "$key must be a boolean" }
    return value.unsafeCast<Boolean>()
}
private fun array(obj: dynamic, key: String): Array<dynamic>? {
    val value = obj[key] ?: return null
    require(isArray(value)) { "$key must be an array" }
    return value.unsafeCast<Array<dynamic>>()
}
private fun isInteger(value: dynamic): Boolean = js("Number.isInteger(value) && value >= -2147483648 && value <= 2147483647")
private fun isArray(value: dynamic): Boolean = js("Array.isArray(value)")
private fun keys(value: dynamic): Array<String> {
    require(jsTypeOf(value) == "object" && !isArray(value)) { "Expected a JSON object" }
    return js("Object.keys(value)").unsafeCast<Array<String>>()
}
private fun <T> map(value: dynamic, read: (dynamic, String) -> T): Map<String, T> =
    if (value == null) emptyMap() else keys(value).associateWith { key -> read(value, key) }
private fun Map<String, String>.asJson(): Json = json(*entries.map { it.key to it.value }.toTypedArray())

private fun point(v: dynamic): P = if (v == null) P() else P(int(v, "x", 0), int(v, "y", 0), int(v, "z", 0))
private fun P.jsPoint() = json("x" to x, "y" to y, "z" to z)
private fun rect(v: dynamic): Rect = Rect(int(v, "x", 0), int(v, "z", 0), int(v, "width", 11), int(v, "length", 9))
private fun Rect.jsRect() = json("x" to x, "z" to z, "width" to width, "length" to length)

private fun part(v: dynamic): Part {
    if(v==null) return Part()
    require(v.exact==null && v.exactClip==null && v.roof==null) { "Exact and roof geometry require the native editor" }
    return Part(
    string(v, "source", Part().source), int(v, "turn", 0), bool(v, "top"), int(v, "size", 1),
    point(v.offset), int(v, "tilt", 0), string(v, "group", "body"), string(v, "cutMask"), string(v, "recipeSource"),
    int(v, "spin", 0), int(v, "discCut", 0)
)
}
private fun Part.jsPart() = json(
    "source" to source, "turn" to turn, "top" to top, "size" to size, "offset" to offset.jsPoint(),
    "tilt" to tilt, "group" to group, "cutMask" to cutMask, "recipeSource" to recipeSource,
    "spin" to spin, "discCut" to discCut
)
private fun groups(v: dynamic): List<MaterialGroup> = array(v, "groups")?.map {
    MaterialGroup(string(it, "id", "body"), string(it, "name", "Body"), string(it, "role", "surface"), int(it, "priority", 0))
} ?: DEFAULT_GROUPS
private fun strings(v: dynamic, key: String): List<String> = array(v, key)?.map {
    require(jsTypeOf(it) == "string") { "$key must contain strings" }
    it.unsafeCast<String>()
}.orEmpty()
private fun mirror(v:dynamic):CopycatMirror? {
    if(v==null) return null
    val plane=CopycatMirrorPlane.valueOf(string(v,"plane","X"))
    require(plane in setOf(CopycatMirrorPlane.X,CopycatMirrorPlane.Y,CopycatMirrorPlane.Z)) { "This mirror plane requires the native editor" }
    return CopycatMirror(plane)
}
private fun saved(v:dynamic):SavedCopycat? = if(v==null) null else SavedCopycat(string(v,"id"),int(v,"version",1),string(v,"name","Copycat"),array(v,"parts")?.map(::part).orEmpty())
private fun formNode(raw: dynamic): GraphNode {
    val v=browserNode(raw,false)
    return GraphNode(
    string(v, "id"), NodeKind.valueOf(string(v, "kind", "INPUT")), strings(v, "inputs"), part(v.part),
    int(v, "x", 0), int(v, "y", 0), MaterialMode.valueOf(string(v, "materialMode", "SEPARATED")),
    string(v, "materialGroup"), string(v, "previewGroup"), strings(v, "inputGroups"),
    RotationAxis.valueOf(string(v, "rotationAxis", "NORMAL")), int(v, "quarterTurns", 0), mirror(v.mirror), saved(v.savedCopycat)
)
}
private fun GraphNode.jsNode() = json(
    "id" to id, "kind" to kind.name, "inputs" to inputs.toTypedArray(), "part" to part.jsPart(),
    "x" to x, "y" to y, "materialMode" to materialMode.name, "materialGroup" to materialGroup,
    "previewGroup" to previewGroup, "inputGroups" to inputGroups.toTypedArray(),
    "rotationAxis" to rotationAxis.name, "quarterTurns" to quarterTurns, "mirror" to mirror?.let { json("plane" to it.plane.name) },
    "savedCopycat" to savedCopycat?.let { json("id" to it.id,"version" to it.version,"name" to it.name,"parts" to it.parts.map { p -> p.jsPart() }.toTypedArray()) }
)
private fun formGraph(raw: dynamic): NodeGraph { val v=browserGraph(raw,false);return NodeGraph(int(v,"schema",2),array(v,"nodes")?.map(::formNode).orEmpty()) }
private fun form(v: dynamic): Form = Form(
    int(v, "schema", 3).also { require(it in 1..3) { "Invalid form schema for browser; use the native editor" } }, string(v, "id"), int(v, "version", 1), string(v, "name", "Form"),
    array(v, "parts")?.map(::part) ?: listOf(Part()), groups(v), if (v.graph == null) null else formGraph(v.graph)
)
private fun buildingNode(raw: dynamic): BuildingNode {
    val v=browserNode(raw,true)
    return BuildingNode(
    id = string(v, "id"), kind = BuildingKind.valueOf(string(v, "kind", "SEGMENT")), name = string(v, "name"),
    x = int(v, "x", 0), y = int(v, "y", 0), inputs = strings(v, "inputs"), module = string(v, "module", "window@1"),
    block = string(v, "block"), length = int(v, "length", 3), height = int(v, "height", 4),
    rectangles = array(v, "rectangles")?.map(::rect) ?: BuildingNode().rectangles,
    walls = map(v.walls) { obj, key -> string(obj, key) }, floor = string(v, "floor"), ceiling = string(v, "ceiling"),
    offsets = array(v, "offsets")?.map(::point).orEmpty(), form = string(v, "form", "full@1"), thickness = int(v, "thickness", 1),
    occupied = bool(v, "occupied"), finish = string(v, "finish"),
    levels = map(v.levels) { obj, key -> string(obj, key) }.mapKeys { it.key.toInt() },
    roof = string(v, "roof"), roofType = string(v, "roofType", "flat"), ridge = int(v, "ridge", 0),
    overhang = int(v, "overhang", 0), materialMode = MaterialMode.valueOf(string(v, "materialMode", "SEPARATED")),
    role = string(v, "role", "wall"), preview = string(v, "preview", "all"), storeyMode = string(v, "storeyMode", "rooms"),
    corner = string(v, "corner"), surfaces = if(v.surfaces==null) null else map(v.surfaces) { obj,key -> string(obj,key) },
    rotationAxis=RotationAxis.valueOf(string(v,"rotationAxis","Y")),quarterTurns=int(v,"quarterTurns",0),mirror=mirror(v.mirror),
    mirrorPositionsOnly=if(v.mirrorPositionsOnly==null) null else bool(v,"mirrorPositionsOnly"),
    patternAxis=string(v,"patternAxis","horizontal"),transforms=array(v,"transforms")?.map { t ->
        SegmentTransform(if(t.offset==null) P() else point(t.offset),RotationAxis.valueOf(string(t,"axis","Y")),int(t,"quarterTurns",0))
    }.orEmpty()
)
}
private fun BuildingNode.jsNode() = json(
    "id" to id, "kind" to kind.name, "name" to name, "x" to x, "y" to y, "inputs" to inputs.toTypedArray(),
    "module" to module, "block" to block, "length" to length, "height" to height,
    "rectangles" to rectangles.map { it.jsRect() }.toTypedArray(), "walls" to walls.asJson(), "floor" to floor,
    "ceiling" to ceiling, "offsets" to offsets.map { it.jsPoint() }.toTypedArray(), "form" to form,
    "thickness" to thickness, "occupied" to occupied, "finish" to finish,
    "levels" to levels.mapKeys { it.key.toString() }.asJson(), "roof" to roof, "roofType" to roofType,
    "ridge" to ridge, "overhang" to overhang, "materialMode" to materialMode.name, "role" to role, "preview" to preview,
    "storeyMode" to storeyMode, "corner" to corner, "surfaces" to surfaces?.asJson(),
    "rotationAxis" to rotationAxis.name,"quarterTurns" to quarterTurns,"mirror" to mirror?.let { json("plane" to it.plane.name) },
    "mirrorPositionsOnly" to mirrorPositionsOnly,"patternAxis" to patternAxis,"transforms" to transforms.map {
        json("offset" to it.offset.jsPoint(),"axis" to it.axis.name,"quarterTurns" to it.quarterTurns)
    }.toTypedArray()
)
private fun buildingGraph(raw:dynamic):BuildingGraph { val v=browserGraph(raw,true);return BuildingGraph(int(v,"schema",1),array(v,"nodes")?.map(::buildingNode).orEmpty()) }
private fun building(v: dynamic) = Building(
    schema = int(v, "schema", 2), id = string(v, "id"), version = int(v, "version", 1), name = string(v, "name", "L House"),
    palette = if (v.palette == null) Building().palette else map(v.palette) { obj, key -> string(obj, key) },
    graph = buildingGraph(v.graph)
)
private fun module(v: dynamic): Module {
    val width = int(v, "width", 3)
    return Module(int(v, "schema", 1), string(v, "id"), int(v, "version", 1), string(v, "name", "Module"), width,
        int(v, "height", 4), int(v, "depth", 1), array(v, "cells")?.map {
            Cell(point(it.pos), string(it, "form", "full@1"), string(it, "block"), string(it, "role", "wall"),
                int(it, "turn", 0), bool(it, "empty"), bool(it, "flexible"))
        }.orEmpty(), int(v, "minWidth", width), int(v, "maxWidth", width))
}

@JsExport
fun graphModels(): Json = json(
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
    "rotationAxes" to RotationAxis.entries.map { it.name }.toTypedArray(),
    "materialModes" to MaterialMode.entries.map { it.name }.toTypedArray(),
    "carpets" to BuildingGraphCompiler.CARPETS.toTypedArray(),
    "formNodes" to GraphModels.FORM_NODES, "formSources" to GraphModels.FORM_SOURCES,
    "buildingNodes" to GraphModels.BUILDING_NODES, "sockets" to GraphModels.SOCKETS,
    "positionMin" to GraphModels.coordinates.first, "positionMax" to GraphModels.coordinates.last
)

@JsExport
fun inspectGraph(isBuilding: Boolean, document: dynamic): Array<Json> {
    if (isBuilding) {
        BuildingGraphCompiler.validateMetadata(building(document))
        val graph = buildingGraph(document.graph)
        BuildingGraphCompiler.structure(graph)
        return graph.nodes.map { n ->
            json("node" to n.jsNode(), "ports" to BuildingPorts.of(n, true).map {
                json("id" to it.key, "source" to it.source, "accepts" to it.accepts.map { kind -> kind.name }.toTypedArray(),
                    "length" to it.length, "height" to it.height)
            }.toTypedArray())
        }.toTypedArray()
    }
    Geometry.validateMetadata(form(document))
    val graph = formGraph(document.graph)
    GraphCompiler.structure(graph, groups(document))
    return graph.nodes.map { n ->
        json("node" to n.jsNode(), "ports" to n.inputs.mapIndexed { index, source ->
            json("id" to index.toString(), "source" to source, "accepts" to browserFormKinds
                .filter { GraphModels.accepts(n.kind, index, it) }.map { it.name }.toTypedArray())
        }.toTypedArray())
    }.toTypedArray()
}

@JsExport
fun applyDiagram(isBuilding: Boolean, document: dynamic, positions: Array<dynamic>, wires: Array<dynamic>): Array<Json> {
    val layout = positions.map { DiagramPosition(string(it, "id"), int(it, "x", 0), int(it, "y", 0)) }
    val connections = wires.map { DiagramConnection(string(it, "source"), string(it, "target"), string(it, "port")) }
    return if (isBuilding) DiagramEdits.building(buildingGraph(document.graph), layout, connections).nodes.map { it.jsNode() }.toTypedArray()
    else DiagramEdits.shape(formGraph(document.graph), groups(document), layout, connections).nodes.map { it.jsNode() }.toTypedArray()
}

@JsExport
fun newNode(isBuilding: Boolean, kind: String, id: String, x: Int, y: Int): Json {
    if (isBuilding) {
        val type = BuildingKind.valueOf(kind)
        require(type in browserBuildingKinds) { "Use the native editor for $kind" }
        val inputs = List(GraphModels.requiredBuildingInputs(type, "rooms")) { "" }
        val node = BuildingNode(id = id, kind = type, x = x, y = y, inputs = inputs,
            module = "", role = if (type == BuildingKind.ROOF) "roof" else "wall",
            rotationAxis=if(type==BuildingKind.SEGMENT_MIRROR) RotationAxis.X else RotationAxis.Y,
            quarterTurns=if(type in setOf(BuildingKind.COPYCAT_ROTATE,BuildingKind.SEGMENT_ROTATE)) 1 else 0,
            mirror=if(type==BuildingKind.COPYCAT_MIRROR) CopycatMirror() else null)
        return node.jsNode()
    }
    val type = NodeKind.valueOf(kind)
    require(type in browserFormKinds) { "Use the native editor for $kind" }
    return GraphNode(id, type, List(GraphModels.inputs(type).first) { "" }, x = x, y = y,
        savedCopycat=if(type==NodeKind.SAVED) SavedCopycat(parts=listOf(Part())) else null).jsNode()
}

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
private fun fullBlock(key: MeshKey, positions: List<P>) = json(
    "material" to key.block, "boxes" to arrayOf(intArrayOf(0, 0, 0, 16, 16, 16)),
    "positions" to positions.map { intArrayOf(it.x, it.y, it.z) }.toTypedArray(), "component" to key.component
)

@JsExport
fun previewGraph(isBuilding: Boolean, document: dynamic, library: dynamic, selected: String): Json {
    if (!isBuilding) {
        val original = form(document)
        Geometry.validateMetadata(original)
        val graph = requireNotNull(original.graph) { "Missing form graph" }
        val n = if(selected.isEmpty()) graph.nodes.single { it.kind==NodeKind.OUTPUT }
            else graph.nodes.firstOrNull { it.id==selected } ?: error("Select an existing node")
        if(n.kind==NodeKind.ROTATION) {
            GraphCompiler.structure(graph,original.groups)
            return json("count" to 0, "raw" to 0, "meshes" to emptyArray<Json>(),
            "value" to "${n.rotationAxis.name}: ${n.quarterTurns*90}°", "warnings" to arrayOf("Rotationswert, keine Geometrie"))
        }
        val parts = if(selected.isEmpty()) GraphCompiler.compile(graph,original.groups) else GraphCompiler.intermediate(graph,n.id,original.groups)
        val filter = if(n.kind==NodeKind.OUTPUT) n.previewGroup else ""
        val owned = if(parts.isEmpty()) emptyMap() else Geometry.resolve(parts,original.groups).owned
        val meshes = owned.filterKeys { filter.isEmpty() || it==filter }.map { (id,solid) -> solidMesh(id,solid,listOf(P()),"form") }
        return json("count" to owned.values.sumOf { it.count() }, "raw" to if(parts.isEmpty()) 0 else Extrusion.cost(parts,original.groups),
            "meshes" to meshes.toTypedArray())
    }
    val b = building(document)
    BuildingGraphCompiler.validateMetadata(b)
    val forms = map(library.forms) { obj, key -> form(obj[key]) }
    val modules = map(library.modules) { obj, key -> module(obj[key]) }
    val graph = requireNotNull(b.graph)
    val plan = if(selected.isEmpty()) BuildingGraphCompiler.compile(b,forms,modules).plan
        else BuildingGraphCompiler.Evaluation(graph,b.palette,forms,modules).let { evaluation ->
            if(graph.nodes.first { it.id==selected }.kind in setOf(BuildingKind.FLOOR,BuildingKind.CEILING)) evaluation.contextualPlatePreview(selected) else evaluation.preview(selected)
        }
    val filter = graph.nodes.firstOrNull { it.id == selected }?.preview
        ?: graph.nodes.single { it.kind == BuildingKind.BUILDING }.preview
    val grouped = plan.placements.filter { filter == "all" || it.component == filter ||
        filter == "walls" && it.component in setOf("wall", "trim") || filter == "roof" && it.component == "gable" }
        .groupBy({ MeshKey(it.parts, it.groups, it.material, it.surfaces, it.block, it.component) }, { it.pos })
    val meshes = grouped.flatMap { (key, positions) ->
        if (key.block.isNotEmpty()) listOf(fullBlock(key, positions))
        else Geometry.resolve(key.parts, key.groups).materialSolids(key.material, key.surfaces)
            .map { (material, solid) -> solidMesh(material, solid, positions, key.component) }
    }
    return json("count" to plan.placements.size, "raw" to plan.raw, "materials" to plan.materials.mapValues { it.value.toString() }.asJson(),
        "warnings" to plan.warnings.toTypedArray(), "meshes" to meshes.toTypedArray())
}

@JsExport
fun starterBuilding(): Json = json("name" to "Neues Gebäude", "schema" to 2, "palette" to Building().palette.asJson(),
    "graph" to json("schema" to 1, "nodes" to BuildingGraph.assembledStarter().nodes.map { it.jsNode() }.toTypedArray()))

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
    require(node.kind==BuildingKind.STOREY && node.storeyMode=="rooms") { "Select a segment pattern or storey made from rooms" }
    return json("items" to node.inputs.mapIndexed { i,id ->
        val plan=evaluation.preview(id)
        val offset=node.offsets.getOrNull(i) ?: P()
        json("id" to id,"label" to graph.nodes.first { it.id==id }.let { it.name.ifBlank { id } },
            "cells" to plan.placements.map { plane.project(it.pos+offset) }.toSet().map { arrayOf(it.x,it.z) }.toTypedArray())
    }.toTypedArray())
}
