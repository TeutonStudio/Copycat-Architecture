import { createHash } from 'node:crypto'
import { existsSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'

// One contract, one release: the mod ships this catalog and the web compiler
// embeds it alongside the same domain sources. Fingerprints are public evidence.
const root = new URL('../', import.meta.url)
const catalogue = readFileSync(new URL('src/main/resources/assets/copycat_architect/graph-models.json', root), 'utf8')
const model = JSON.parse(catalogue)
const release = JSON.parse(readFileSync(new URL('package.json', import.meta.url))).version
const hash = text => createHash('sha256').update(text).digest('hex')
const domain = new URL('src/main/kotlin/de/teutonstudio/copycatarchitect/domain/', root)
const inputEditing = existsSync(new URL('GraphInputEdits.kt', domain))
const sourceHash = hash(readdirSync(domain).filter(name => name.endsWith('.kt')).sort().map(name => name + '\n' + readFileSync(new URL(name, domain), 'utf8')).join('\n') + ['GraphDiagramModels', 'KilaGroupProjection', 'GroupGraphInterfaces'].map(name => name + '\n' + readFileSync(new URL(`src/main/kotlin/de/teutonstudio/copycatarchitect/client/${name}.kt`, root), 'utf8')).join('\n'))
const literal = JSON.stringify(catalogue).replaceAll('$', '\\$')
const output = new URL('src/generated/Catalog.kt', import.meta.url)
const source = `package de.teutonstudio.copycatarchitect.domain\ninternal val graphCatalogJson = ${literal}\ninternal const val graphRelease = "${release}"\ninternal const val graphCatalogHash = "${hash(catalogue)}"\n`
if (!existsSync(output) || readFileSync(output, 'utf8') !== source) writeFileSync(output, source)
const manifest = {
  release, catalogSha256: hash(catalogue), domainSha256: sourceHash, inputEditing,
  copycat: { nodes: Object.keys(model.copycat.nodes), graphSchemas: model.copycat.graphSchemas, documentSchemas: model.copycat.documentSchemas },
  building: { nodes: Object.keys(model.building.nodes), graphSchemas: model.building.graphSchemas, documentSchemas: model.building.documentSchemas },
  features: ['universalResults','parameterizedGraphReferences','graphInputDefaults','graphDefinitions','dropdownIcons','unifiedOperators','exactSegmentOperators','logicalRelations','projection', 'nodeGroups', 'namedGroupInputs', 'sharedGroupDefinitions', 'exactGeometry', 'roofTemplates', 'specialRooms', 'copycatTransforms', 'typedCopycatTransforms', 'graphDuplication', 'semanticPorts', 'immutableLibraryVersions', 'folderCopycatPicker', 'areaRaster', 'dynamicSegments', 'adaptiveRoofSegments', ...(existsSync(new URL('DirectAreaInputs.kt', domain)) ? ['directAreaInputs','segmentOperandOffsets'] : []), ...(existsSync(new URL('GraphConditions.kt', domain)) ? ['conditionalValues','booleanValues','roofTemplateValues','shaftSegments','inlineNodeConfiguration'] : []), ...(existsSync(new URL('GraphNumbers.kt', domain)) ? ['signedIntegers','countValues','numericTypeInference','numericMigration','socketInputReordering','storeyDualPreview'] : []), 'typedGraphCards', 'groupedLibraryVersions', 'wallLayers', 'roomArrangementValidation'],
}
writeFileSync(new URL('public/compatibility.json', import.meta.url), JSON.stringify(manifest, null, 2) + '\n')


// Older public pins do not have the new native command. Compile a capability gate
// instead of making every public build depend on an unpublished domain class.
const inputCapabilities = `package de.teutonstudio.copycatarchitect.web
import de.teutonstudio.copycatarchitect.domain.*
internal const val graphInputEditingAvailable = ${inputEditing}
internal fun updateShapeInputs(graph:NodeGraph,id:String,order:List<Int>):NodeGraph = ${inputEditing ? 'GraphInputEdits.shape(graph,id,order)' : 'error("This core does not support dynamic input edits")'}
internal fun updateBuildingInputs(graph:BuildingGraph,id:String,order:List<Int>):BuildingGraph = ${inputEditing ? 'GraphInputEdits.building(graph,id,order)' : 'error("This core does not support dynamic input edits")'}
`
const inputOutput = new URL('src/generated/InputCapabilities.kt', import.meta.url)
if (!existsSync(inputOutput) || readFileSync(inputOutput,'utf8') !== inputCapabilities) writeFileSync(inputOutput,inputCapabilities)

const transformations = existsSync(new URL('TransformationValues.kt', domain))
const transformationCapabilities = `package de.teutonstudio.copycatarchitect.web
import de.teutonstudio.copycatarchitect.domain.*
internal fun shapeInputSlots(node:GraphNode):List<Int> = ${transformations ? 'if(node.kind.name=="TRANSFORMATION") (node.transformationOrder ?: TransformationOrder.SRP).slots else node.inputs.indices.toList()' : 'node.inputs.indices.toList()'}
internal fun shapeTransformationDescription(node:GraphNode,graph:NodeGraph):String = ${transformations ? 'TransformationValues.describe(TransformationValues.shape(node,graph.nodes.associateBy { it.id }),node.transformationOrder)' : 'error("This core does not support transformation values")'}
internal fun buildingTransformationDescription(node:BuildingNode,graph:BuildingGraph):String = ${transformations ? 'TransformationValues.describe(TransformationValues.building(node,graph.nodes.associateBy { it.id }),node.transformationOrder)' : 'error("This core does not support transformation values")'}
`
const transformationsOutput = new URL('src/generated/TransformationCapabilities.kt', import.meta.url)
if (!existsSync(transformationsOutput) || readFileSync(transformationsOutput,'utf8') !== transformationCapabilities) writeFileSync(transformationsOutput,transformationCapabilities)

const numbers = existsSync(new URL('GraphNumbers.kt', domain))
const conditions = existsSync(new URL('GraphConditions.kt', domain))
const directAreas = existsSync(new URL('DirectAreaInputs.kt', domain))
const areaCapabilities = `package de.teutonstudio.copycatarchitect.web
import de.teutonstudio.copycatarchitect.domain.*
internal const val graphDirectAreasAvailable = ${directAreas}
internal fun operandOffset(node:BuildingNode,index:Int):P = ${directAreas ? 'SegmentBooleans.offset(node,index)' : 'if(node.kind==BuildingKind.SEGMENT_UNION) node.offsets.getOrNull(index) ?: P() else P()'}
internal fun authoringNode(node:BuildingNode):BuildingNode = ${conditions ? 'if(node.kind==BuildingKind.ROOF) node.copy(roofInputMode=RoofInputMode.TEMPLATE,inputs=List(8) { "" }) else node' : directAreas ? 'if(node.kind==BuildingKind.ROOF) node.copy(roofSections=listOf(RoofSection())) else node' : 'node'}
internal fun authoringStarter():BuildingGraph = ${directAreas ? 'BuildingAssembly.directStarter()' : 'BuildingGraph.explicitStarter()'}
`
const areaOutput = new URL('src/generated/AreaCapabilities.kt', import.meta.url)
if (!existsSync(areaOutput) || readFileSync(areaOutput,'utf8') !== areaCapabilities) writeFileSync(areaOutput,areaCapabilities)

const conditionalCapabilities = `package de.teutonstudio.copycatarchitect.web
import de.teutonstudio.copycatarchitect.domain.*
internal fun nativePortTypes(port:BuildingPort):List<String> = ${numbers ? 'GraphNumbers.portTypes(port)' : 'port.accepts.filter { it.name!="CONDITIONAL" }.map(::buildingGraphType).filter { it!="VALUE" }.distinct()'}
internal fun numericShape(graph:NodeGraph)=GraphValues.shape(${numbers ? 'GraphNumbers.shapeContext(graph)' : 'graph.nodes.associateBy { it.id }'})
internal fun numericBuilding(graph:BuildingGraph)=GraphValues.building(${numbers ? 'GraphNumbers.buildingContext(graph)' : 'graph.nodes.associateBy { it.id }'})
internal fun promoteShapeNumbers(graph:NodeGraph):NodeGraph = ${numbers ? 'graph.copy(schema=maxOf(9,graph.schema))' : 'graph'}
internal fun promoteBuildingNumbers(graph:BuildingGraph):BuildingGraph = ${numbers ? 'GraphNumbers.upgrade(graph.copy(schema=maxOf(13,graph.schema)),graph.schema)' : 'graph'}
internal fun numericShapeEdits(before:NodeGraph,after:NodeGraph):NodeGraph = ${numbers ? 'GraphNumbers.edit(before,after)' : 'after'}
internal fun numericBuildingEdits(before:BuildingGraph,after:BuildingGraph):BuildingGraph = ${numbers ? 'GraphNumbers.edit(before,after)' : 'after'}
internal fun shapeValueType(graph:NodeGraph,node:GraphNode):String? = ${conditions ? numbers ? 'GraphTypes.shape(GraphNumbers.shapeContext(graph),node.id)' : 'GraphTypes.shape(graph.nodes.associateBy { it.id },node.id)' : 'GraphModels.shape(node.kind).output'}
internal fun buildingValueType(graph:BuildingGraph,node:BuildingNode):String? = ${conditions ? 'GraphTypes.building(graph,node.id)' : 'buildingGraphType(node.kind)'}
internal fun shapeSocketType(graph:NodeGraph,node:GraphNode,index:Int):String? = ${conditions ? numbers ? 'GraphTypes.expectedShape(GraphNumbers.shapeContext(graph),node,index)' : 'GraphTypes.expectedShape(graph.nodes.associateBy { it.id },node,index)' : 'GraphModels.shape(node.kind).inputs?.type(index)'}
internal fun selectedShapeNode(graph:NodeGraph,id:String):GraphNode = ${conditions ? numbers ? 'GraphConditions.shapeNodes(GraphNumbers.shapeContext(graph)).getValue(id)' : 'GraphConditions.shapeNodes(graph.nodes.associateBy { it.id }).getValue(id)' : 'graph.nodes.first { it.id==id }'}
internal fun selectedBuildingNode(graph:BuildingGraph,id:String):BuildingNode = ${conditions ? 'GraphConditions.buildingNode(graph,id)' : 'graph.nodes.first { it.id==id }'}
internal fun shapeBooleanValue(graph:NodeGraph,id:String):Boolean = ${conditions ? numbers ? 'GraphValues.shape(GraphNumbers.shapeContext(graph)).boolean(id)' : 'GraphValues.shape(graph.nodes.associateBy { it.id }).boolean(id)' : 'error("Boolean values are not available")'}
internal fun buildingBooleanValue(graph:BuildingGraph,id:String):Boolean = ${conditions ? numbers ? 'GraphValues.building(GraphNumbers.buildingContext(graph)).boolean(id)' : 'GraphValues.building(graph.nodes.associateBy { it.id }).boolean(id)' : 'error("Boolean values are not available")'}
`
writeFileSync(new URL('src/generated/ConditionalCapabilities.kt',import.meta.url),conditionalCapabilities)
