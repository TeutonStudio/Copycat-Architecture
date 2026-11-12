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
    BuildingKind.INT,BuildingKind.INT_ADD,BuildingKind.INT_SUBTRACT,BuildingKind.INT_MULTIPLY,BuildingKind.INT_DIVIDE,BuildingKind.INT_MIN,BuildingKind.INT_MAX,BuildingKind.ROTATION,BuildingKind.MIRRORING,BuildingKind.COPYCAT,BuildingKind.COPYCAT_ROTATE,BuildingKind.COPYCAT_MIRROR,
    BuildingKind.COPYCAT_UNION,BuildingKind.COPYCAT_INTERSECTION,BuildingKind.COPYCAT_DIFFERENCE,BuildingKind.COPYCAT_SYMMETRIC_DIFFERENCE,
    BuildingKind.SEGMENT,BuildingKind.SEGMENT_ROTATE,BuildingKind.SEGMENT_MIRROR,
    BuildingKind.SEGMENT_UNION,BuildingKind.SEGMENT_INTERSECTION,BuildingKind.SEGMENT_DIFFERENCE,BuildingKind.SEGMENT_SYMMETRIC_DIFFERENCE,
    BuildingKind.AREA,BuildingKind.SEGMENT_LAYOUT,BuildingKind.PATTERN,BuildingKind.FLOOR,BuildingKind.CEILING,BuildingKind.ROOM,BuildingKind.STOREY,BuildingKind.ROOF,BuildingKind.BUILDING
)
private val browserFormFields=setOf("id","kind","inputs","part","x","y","materialMode","materialGroup","previewGroup","inputGroups","rotationAxis","quarterTurns","savedCopycat","mirror")
private val browserBuildingFields=setOf("id","kind","name","x","y","inputs","module","block","length","height","rectangles","walls","floor","ceiling","offsets","form","thickness","occupied","finish","levels","roof","roofType","ridge","overhang","materialMode","role","preview","storeyMode","corner","surfaces","rotationAxis","quarterTurns","mirror","mirrorPositionsOnly","patternAxis","transforms","copycatArrangement","depth","countValue","dimensions","assembly","layout","geometry","coordinates","layoutCells","regionCopies","rotation","mirroring","rotations","mirrors")
private fun browserNode(value:dynamic,isBuilding:Boolean):dynamic {
    val kind=string(value,"kind",if(isBuilding) "SEGMENT" else "INPUT")
    require(if(isBuilding) browserBuildingKinds.any { it.name==kind } else browserFormKinds.any { it.name==kind }) { "$kind requires the native editor" }
    GraphModels.validateBrowserFields(value,isBuilding,if(isBuilding) browserBuildingFields else browserFormFields)
    return value
}
private fun browserGraph(value:dynamic,isBuilding:Boolean):dynamic {
    val schema=int(value,"schema",if(isBuilding) 1 else 2)
    require(if(isBuilding) schema in 1..3 else schema in 1..2) { "Unsupported browser graph schema; use the native editor" }
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
private fun graphGroups(v:dynamic)=array(v,"nodeGroups")?.map { GraphGroup(string(it,"id"),string(it,"name","Gruppe"),strings(it,"nodes"),string(it,"definitionId")) }.orEmpty()
private fun formGraph(raw: dynamic): NodeGraph { val v=browserGraph(raw,false);return NodeGraph(int(v,"schema",2),array(v,"nodes")?.map(::formNode).orEmpty(),graphGroups(v)) }
private fun form(v: dynamic): Form = Form(
    int(v, "schema", 3).also { require(it in 1..3) { "Invalid form schema for browser; use the native editor" } }, string(v, "id"), int(v, "version", 1), string(v, "name", "Form"),
    array(v, "parts")?.map(::part) ?: listOf(Part()), groups(v), if (v.graph == null || v.versionedGraph!=null) null else formGraph(v.graph),if(v.versionedGraph==null) null else graphBundle(v.versionedGraph)
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
    rotation=string(v,"rotation").takeIf(String::isNotEmpty),mirroring=string(v,"mirroring").takeIf(String::isNotEmpty),
    rotations=array(v,"rotations")?.map { SegmentRotation(RotationAxis.valueOf(string(it,"axis","Y")),int(it,"quarterTurns",0)) },
    mirrors=array(v,"mirrors")?.map { requireNotNull(mirror(it)) },
    mirrorPositionsOnly=if(v.mirrorPositionsOnly==null) null else bool(v,"mirrorPositionsOnly"),
    assembly=bool(v,"assembly"),layout=string(v,"layout"),geometry=string(v,"geometry"),
    coordinates=array(v,"coordinates")?.map(::point).orEmpty(),regionCopies=map(v.regionCopies) { obj,key -> string(obj,key) },
    layoutCells=array(v,"layoutCells")?.map { SegmentLayoutCell(int(it,"x",0),int(it,"y",0),int(it,"sourceIndex",0),int(it,"turn",0)) }.orEmpty(),
    countValue=if(v.countValue==null) null else int(v,"countValue",0),dimensions=if(v.dimensions==null) null else map(v.dimensions) { obj,key -> string(obj,key) },
    depth=int(v,"depth",1),copycatArrangement=if(v.copycatArrangement==null) null else bool(v,"copycatArrangement"),
    patternAxis=string(v,"patternAxis","horizontal"),transforms=array(v,"transforms")?.map { t ->
        SegmentTransform(if(t.offset==null) P() else point(t.offset),RotationAxis.valueOf(string(t,"axis","Y")),int(t,"quarterTurns",0))
    }.orEmpty()
)
}
private fun BuildingNode.jsNode() = json(
    "assembly" to assembly,"layout" to layout,"geometry" to geometry,"coordinates" to coordinates.map { it.jsPoint() }.toTypedArray(),
    "regionCopies" to regionCopies.asJson(),"layoutCells" to layoutCells.map { json("x" to it.x,"y" to it.y,"sourceIndex" to it.sourceIndex,"turn" to it.turn) }.toTypedArray(),
    "countValue" to countValue,"dimensions" to dimensions?.asJson(),
    "id" to id, "kind" to kind.name, "name" to name, "x" to x, "y" to y, "inputs" to inputs.toTypedArray(),
    "module" to module, "block" to block, "length" to length, "height" to height,"depth" to depth,"copycatArrangement" to copycatArrangement,
    "rectangles" to rectangles.map { it.jsRect() }.toTypedArray(), "walls" to walls.asJson(), "floor" to floor,
    "ceiling" to ceiling, "offsets" to offsets.map { it.jsPoint() }.toTypedArray(), "form" to form,
    "thickness" to thickness, "occupied" to occupied, "finish" to finish,
    "levels" to levels.mapKeys { it.key.toString() }.asJson(), "roof" to roof, "roofType" to roofType,
    "ridge" to ridge, "overhang" to overhang, "materialMode" to materialMode.name, "role" to role, "preview" to preview,
    "storeyMode" to storeyMode, "corner" to corner, "surfaces" to surfaces?.asJson(),
    "rotationAxis" to rotationAxis.name,"quarterTurns" to quarterTurns,"mirror" to mirror?.let { json("plane" to it.plane.name) },
    "rotation" to rotation.orEmpty(),"mirroring" to mirroring.orEmpty(),
    "rotations" to rotations?.map { json("axis" to it.axis.name,"quarterTurns" to it.quarterTurns) }?.toTypedArray(),
    "mirrors" to mirrors?.map { json("plane" to it.plane.name) }?.toTypedArray(),
    "mirrorPositionsOnly" to mirrorPositionsOnly,"patternAxis" to patternAxis,"transforms" to transforms.map {
        json("offset" to it.offset.jsPoint(),"axis" to it.axis.name,"quarterTurns" to it.quarterTurns)
    }.toTypedArray()
)
private fun buildingGraph(raw:dynamic):BuildingGraph { val v=browserGraph(raw,true);return BuildingGraph(int(v,"schema",1),array(v,"nodes")?.map(::buildingNode).orEmpty(),projection=if(v.projection==null) BuildingProjection() else BuildingProjection(if(v.projection.table==null) P(-5,0,-5) else point(v.projection.table),int(v.projection,"turn",0)),nodeGroups=graphGroups(v)) }
private fun building(v: dynamic) = Building(
    schema = int(v, "schema", 2), id = string(v, "id"), version = int(v, "version", 1), name = string(v, "name", "L House"),
    palette = if (v.palette == null) Building().palette else map(v.palette) { obj, key -> string(obj, key) },
    graph = if(v.graph==null || v.versionedGraph!=null) null else buildingGraph(v.graph),versionedGraph=if(v.versionedGraph==null) null else graphBundle(v.versionedGraph),
    graphProjection=if(v.graphProjection==null) null else BuildingProjection(point(v.graphProjection.table),int(v.graphProjection,"turn",0))
)
private fun module(v: dynamic): Module {
    val width = int(v, "width", 3)
    return Module(int(v, "schema", 1), string(v, "id"), int(v, "version", 1), string(v, "name", "Module"), width,
        int(v, "height", 4), int(v, "depth", 1), array(v, "cells")?.map {
            Cell(point(it.pos), string(it, "form", "full@1"), string(it, "block"), string(it, "role", "wall"),
                int(it, "turn", 0), bool(it, "empty"), bool(it, "flexible"),
                array(it,"rotations")?.map { r -> SegmentRotation(RotationAxis.valueOf(string(r,"axis","Y")),int(r,"quarterTurns",0)) }.orEmpty(),
                if(it.surfaces==null) null else map(it.surfaces) { obj,key -> string(obj,key) })
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
            json("node" to n.jsNode(),"dimensionError" to runCatching { BuildingCounts.resolve(graph,n,strict=true) }.exceptionOrNull()?.message,"axes" to BuildingCounts.axisUsage(graph)[n.id].orEmpty().toTypedArray(),"effectiveDimensions" to graph.resolvedNode(n).let { json("length" to it.length,"height" to it.height,"depth" to it.depth) }, "ports" to BuildingPorts.of(n, graph, true).map {
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
    require(document.versionedGraph==null) { "Edit this source in Versioned subgraphs" }
    val layout = positions.map { DiagramPosition(string(it, "id"), int(it, "x", 0), int(it, "y", 0)) }
    val connections = wires.map { DiagramConnection(string(it, "source"), string(it, "target"), string(it, "port"),string(it,"sourcePort","out")) }
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
            rotations=if(type==BuildingKind.ROTATION) listOf(SegmentRotation(RotationAxis.Y,1)) else null,
            mirrors=if(type==BuildingKind.MIRRORING) listOf(CopycatMirror()) else null,
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
        val parsed = form(document)
        val original=parsed.versionedGraph?.let { source ->
            GraphPackages.validate(source)
            parsed.copy(graph=VersionedGraphs.preview(source.root,source.graphs).shape,versionedGraph=null)
        } ?: parsed
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
    val parsedBuilding=building(document)
    val b=parsedBuilding.versionedGraph?.let { source ->
        GraphPackages.validate(source)
        parsedBuilding.copy(graph=VersionedGraphs.preview(source.root,source.graphs).building!!.let { g -> parsedBuilding.graphProjection?.let { g.copy(projection=it) } ?: g },versionedGraph=null)
    } ?: parsedBuilding
    BuildingGraphCompiler.validateMetadata(b)
    val forms = map(library.forms) { obj, key -> form(obj[key]) }+parsedBuilding.versionedGraph?.forms.orEmpty()
    val modules = map(library.modules) { obj, key -> module(obj[key]) }+parsedBuilding.versionedGraph?.modules.orEmpty()
    val graph = requireNotNull(b.graph)
    val selectedNode=graph.nodes.firstOrNull { it.id==selected }
    if(selectedNode?.kind in setOf(BuildingKind.AREA,BuildingKind.SEGMENT_LAYOUT)) {
        BuildingGraphCompiler.structure(graph)
        val boxes=BuildingNodePreviewGeometry.boxes(graph,selectedNode!!)
        val n=graph.resolvedNode(selectedNode)
        return json("count" to 0,"raw" to 0,"value" to "${n.length} × ${n.height} × ${n.depth}","meshes" to arrayOf(json(
            "material" to "wall","boxes" to boxes.map { intArrayOf(it.x*16,it.y*16,it.z*16,it.maxX*16,it.maxY*16,it.maxZ*16) }.toTypedArray(),
            "positions" to arrayOf(intArrayOf(0,0,0)),"component" to "layout")))
    }
    if(selectedNode?.kind in BuildingCounts.kinds) {
        BuildingGraphCompiler.structure(graph)
        return json("count" to 0,"raw" to 0,"meshes" to emptyArray<Json>(),"value" to BuildingCounts.Evaluation(graph).value(selected).toString())
    }
    if(selectedNode?.kind in setOf(BuildingKind.ROTATION,BuildingKind.MIRRORING)) {
        BuildingGraphCompiler.structure(graph)
        val value=if(selectedNode!!.kind==BuildingKind.ROTATION) selectedNode.rotations.orEmpty().joinToString(" → ") { "${it.axis}: ${it.quarterTurns*90}°" } else selectedNode.mirrors.orEmpty().joinToString(" → ") { it.plane.name }
        return json("count" to 0,"raw" to 0,"meshes" to emptyArray<Json>(),"value" to value.ifEmpty { "Identität (leere Liste)" })
    }
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
    if(node.kind==BuildingKind.PATTERN || node.kind==BuildingKind.SEGMENT) {
        val draft=SegmentArrangement(if(node.kind==BuildingKind.SEGMENT) evaluation.copycatInputSegments(node) else node.inputs.map(evaluation::segment),graph.resolvedNode(node))
        return json("transforms" to draft.transforms.map { json("offset" to it.offset.jsPoint(),"axis" to it.axis.name,"quarterTurns" to it.quarterTurns) }.toTypedArray(),
            "items" to node.inputs.mapIndexed { i,id -> json("id" to id,"label" to graph.nodes.first { it.id==id }.let { it.name.ifBlank { id } },
                "cells" to draft.projected(i,plane).map { arrayOf(it.x,it.z) }.toTypedArray()) }.toTypedArray())
    }
    if(node.kind==BuildingKind.SEGMENT_UNION) return json("items" to node.inputs.mapIndexed { i,id ->
        val offset=node.offsets.getOrNull(i) ?: P()
        json("id" to id,"label" to graph.nodes.first { it.id==id }.let { it.name.ifBlank { id } },
            "cells" to evaluation.segment(id).cells.filter { !it.cell.empty && it.cell.block!="minecraft:air" }.map { plane.project(it.cell.pos+offset) }.toSet().map { arrayOf(it.x,it.z) }.toTypedArray())
    }.toTypedArray())
    require(node.kind==BuildingKind.STOREY && node.storeyMode=="rooms") { "Select a segment pattern or storey made from rooms" }
    return json("items" to node.inputs.mapIndexed { i,id ->
        val plan=evaluation.preview(id)
        val offset=node.offsets.getOrNull(i) ?: P()
        json("id" to id,"label" to graph.nodes.first { it.id==id }.let { it.name.ifBlank { id } },
            "cells" to plan.placements.map { plane.project(it.pos+offset) }.toSet().map { arrayOf(it.x,it.z) }.toTypedArray())
    }.toTypedArray())
}

@JsExport
fun evaluateCount(document:dynamic,id:String):Int=BuildingCounts.Evaluation(buildingGraph(document.graph)).value(id)

private fun graphPort(v:dynamic)=GraphInterfacePort(string(v,"id"),string(v,"name"),string(v,"type"),GraphPortDirection.valueOf(string(v,"direction","INPUT")))
private fun GraphInterfacePort.jsPort()=json("id" to id,"name" to name,"type" to type,"direction" to direction.name)
private fun graphOutput(v:dynamic)=GraphOutput(string(v,"node"),string(v,"port","out"))
private fun GraphOutput.jsOutput()=json("node" to node,"port" to port)
private fun graphDocument(v:dynamic):GraphDocument=GraphDocument(
    schema=int(v,"schema",1),graphId=string(v,"graphId"),version=int(v,"version",0),name=string(v,"name","Graph"),mode=GraphMode.valueOf(string(v,"mode","FORM")),
    shape=if(v.shape==null) null else formGraph(v.shape),building=if(v.building==null) null else buildingGraph(v.building),
    references=array(v,"references")?.map { ref -> GraphReferenceNode(string(ref,"id"),GraphVersion(string(ref.reference,"graphId"),int(ref.reference,"version",1)),
        array(ref,"ports")?.map(::graphPort).orEmpty(),int(ref,"x",0),int(ref,"y",0)) }.orEmpty(),
    connections=array(v,"connections")?.map { DiagramConnection(string(it,"source"),string(it,"target"),string(it,"port"),string(it,"sourcePort","out")) }.orEmpty(),
    ports=array(v,"ports")?.map(::graphPort).orEmpty(),
    inputBindings=array(v,"inputBindings")?.map { GraphInputBinding(string(it,"port"),array(it,"targets")?.map { t -> GraphSocket(string(t,"node"),string(t,"port")) }.orEmpty(),if(it.defaultSource==null) null else graphOutput(it.defaultSource)) }.orEmpty(),
    outputBindings=array(v,"outputBindings")?.map { GraphOutputBinding(string(it,"port"),graphOutput(it.source)) }.orEmpty(),
    materialGroups=if(v.materialGroups==null) DEFAULT_GROUPS else v.materialGroups.unsafeCast<Array<dynamic>>().map { MaterialGroup(string(it,"id","body"),string(it,"name","Body"),string(it,"role","surface"),int(it,"priority",0)) },palette=if(v.palette==null) Building().palette else map(v.palette) { obj,key -> string(obj,key) })
private fun NodeGraph.jsGraph()=json("schema" to schema,"nodes" to nodes.map { it.jsNode() }.toTypedArray(),"nodeGroups" to nodeGroups.map { json("id" to it.id,"name" to it.name,"nodes" to it.nodes.toTypedArray(),"definitionId" to it.definitionId) }.toTypedArray())
private fun BuildingGraph.jsGraph()=json("schema" to schema,"nodes" to nodes.map { it.jsNode() }.toTypedArray(),"projection" to json("table" to projection.table.jsPoint(),"turn" to projection.turn),
    "nodeGroups" to nodeGroups.map { json("id" to it.id,"name" to it.name,"nodes" to it.nodes.toTypedArray(),"definitionId" to it.definitionId) }.toTypedArray())
private fun GraphDocument.jsDocument():Json=json("schema" to schema,"graphId" to graphId,"version" to version,"name" to name,"mode" to mode.name,
    "shape" to shape?.jsGraph(),"building" to building?.jsGraph(),
    "references" to references.map { ref -> json("id" to ref.id,"reference" to json("graphId" to ref.reference.graphId,"version" to ref.reference.version),"ports" to ref.ports.map { it.jsPort() }.toTypedArray(),"x" to ref.x,"y" to ref.y) }.toTypedArray(),
    "connections" to connections.map { json("source" to it.source,"target" to it.target,"port" to it.port,"sourcePort" to it.sourcePort) }.toTypedArray(),
    "ports" to ports.map { it.jsPort() }.toTypedArray(),
    "inputBindings" to inputBindings.map { b -> json("port" to b.port,"targets" to b.targets.map { json("node" to it.node,"port" to it.port) }.toTypedArray(),"defaultSource" to b.defaultSource?.jsOutput()) }.toTypedArray(),
    "outputBindings" to outputBindings.map { json("port" to it.port,"source" to it.source.jsOutput()) }.toTypedArray(),
    "palette" to palette.asJson(),"materialGroups" to materialGroups.map { json("id" to it.id,"name" to it.name,"role" to it.role,"priority" to it.priority) }.toTypedArray())
private fun graphDefinitions(v:dynamic)=map(v) { obj,key -> graphDocument(obj[key]) }
private fun graphBundle(v:dynamic)=GraphPackage(int(v,"schema",1),graphDocument(v.root),graphDefinitions(v.graphs),map(v.forms) { obj,key -> form(obj[key]) },map(v.modules) { obj,key -> module(obj[key]) })

@JsExport
fun createVersionedGraph(isBuilding:Boolean,document:dynamic,id:String):Json =
    if(document.versionedGraph!=null) graphBundle(document.versionedGraph).root.let { root -> if(isBuilding && document.palette!=null) VersionedGraphs.withPalette(root,map(document.palette) { obj,key -> string(obj,key) }) else root }.jsDocument() else (if(isBuilding) VersionedGraphs.fromBuilding(id,string(document,"name","Graph"),buildingGraph(document.graph),if(document.palette==null) Building().palette else map(document.palette) { obj,key -> string(obj,key) }) else VersionedGraphs.fromShape(id,string(document,"name","Graph"),formGraph(document.graph),groups(document))).jsDocument()

@JsExport
fun inspectVersionedGraph(document:dynamic):Array<Json> {
    val d=graphDocument(document);VersionedGraphs.validate(d,allowBrokenConnections=true)
    val ids=d.shape?.nodes?.map { it.id } ?: d.building?.nodes?.map { it.id }.orEmpty()
    return (ids+d.references.map { it.id }).map { id ->
        val shape=d.shape?.nodes?.firstOrNull { it.id==id };val building=d.building?.nodes?.firstOrNull { it.id==id };val ref=d.references.firstOrNull { it.id==id }
        json("id" to id,"title" to (ref?.let { "${it.reference.graphId} @${it.reference.version}" } ?: shape?.kind?.name ?: building?.name?.takeIf(String::isNotBlank) ?: building?.kind?.name),
            "x" to (ref?.x ?: shape?.x ?: building?.x ?: 0),"y" to (ref?.y ?: shape?.y ?: building?.y ?: 0),
            "inputs" to VersionedGraphs.displayInputs(d,id).map { it.jsPort() }.toTypedArray(),"outputs" to VersionedGraphs.displayOutputs(d,id).map { it.jsPort() }.toTypedArray())
    }.toTypedArray()
}

@JsExport
fun extractVersionedGraph(document:dynamic,selection:Array<String>,graphId:String,name:String,nodeId:String):Json {
    val extracted=VersionedGraphs.extract(graphDocument(document),selection.toSet(),graphId,name,nodeId)
    return json("parent" to extracted.parent.jsDocument(),"definition" to extracted.definition.jsDocument())
}

@JsExport
fun compareGraphVersions(before:dynamic,after:dynamic):Array<Json> = VersionedGraphs.compare(graphDocument(before).ports,graphDocument(after).ports).map {
    json("id" to it.id,"before" to it.before?.jsPort(),"after" to it.after?.jsPort(),"incompatible" to it.incompatible)
}.toTypedArray()

@JsExport
fun switchGraphVersion(document:dynamic,nodeId:String,next:dynamic):Json = VersionedGraphs.switchVersion(graphDocument(document),nodeId,graphDocument(next)).jsDocument()

@JsExport
fun resolveVersionedGraph(document:dynamic,definitions:dynamic):Json {
    val d=graphDocument(document);val resolved=VersionedGraphs.preview(d,graphDefinitions(definitions))
    resolved.shape?.let { GraphCompiler.structure(it,d.materialGroups) };resolved.building?.let(BuildingGraphCompiler::structure)
    return json("schema" to if(d.mode==GraphMode.FORM) 3 else 2,"name" to d.name,"palette" to d.palette.asJson(),"graph" to (resolved.shape?.jsGraph() ?: resolved.building!!.jsGraph()),
        "groups" to d.materialGroups.map { json("id" to it.id,"name" to it.name,"role" to it.role,"priority" to it.priority) }.toTypedArray(),
        "nodeOutputs" to json(*resolved.nodeOutputs.map { (key,value) -> key to value.asJson() }.toTypedArray()))
}

@JsExport
fun validateGraphBundle(bundle:dynamic) { GraphPackages.validate(graphBundle(bundle)) }

@JsExport
fun exportGraphBundle(document:dynamic,definitions:dynamic,library:dynamic):Json {
    val d=graphDocument(document)
    val bundle=GraphPackages.export(d,graphDefinitions(definitions),map(library.forms) { obj,key -> form(obj[key]) },map(library.modules) { obj,key -> module(obj[key]) })
    return json("schema" to 1,"root" to d.jsDocument(),"graphs" to json(*bundle.graphs.map { (key,value) -> key to value.jsDocument() }.toTypedArray()),
        "forms" to json(*bundle.forms.keys.map { it to library.forms[it] }.toTypedArray()),"modules" to json(*bundle.modules.keys.map { it to library.modules[it] }.toTypedArray()))
}

@JsExport
fun validateVersionedGraph(document:dynamic) { VersionedGraphs.validate(graphDocument(document)) }

@JsExport
fun nextGraphVersion(document:dynamic,definitions:dynamic):Json = VersionedGraphs.nextVersion(graphDocument(document),graphDefinitions(definitions)).jsDocument()

@JsExport
fun checkGraphBundleConflicts(bundle:dynamic,definitions:dynamic,library:dynamic) {
    GraphPackages.checkConflicts(graphBundle(bundle),graphDefinitions(definitions),map(library.forms) { obj,key -> form(obj[key]) },map(library.modules) { obj,key -> module(obj[key]) })
}
