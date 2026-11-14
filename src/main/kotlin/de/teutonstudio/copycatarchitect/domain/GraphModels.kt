package de.teutonstudio.copycatarchitect.domain

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** The editor catalog is packaged in the mod and imported directly by the web editor. */
internal object GraphModels {
    private val gson = Gson()
    private val catalog by lazy {
        val stream = checkNotNull(javaClass.getResourceAsStream("/assets/copycat_architect/graph-models.json"))
        stream.bufferedReader(Charsets.UTF_8).use { gson.fromJson(it, Catalog::class.java) }.also {
            check(it.version == 1) { "Unsupported graph model catalog version" }
            check(it.copycat.nodes.keys == NodeKind.entries.map { kind -> kind.name }.toSet())
            check(it.building.nodes.keys == BuildingKind.entries.map { kind -> kind.name }.toSet())
        }
    }

    val copycat get() = catalog.copycat
    val building get() = catalog.building

    fun shape(kind: NodeKind) = copycat.nodes.getValue(kind.name)
    fun building(kind: BuildingKind) = building.nodes.getValue(kind.name)
    fun buildingInsertable(kind: BuildingKind, explicit: Boolean): Boolean =
        building(kind).let { it.output != null && (!explicit || !it.legacyOnly) }

    val shapeOperations get() = NodeKind.entries.filter { it != NodeKind.INPUT && it != NodeKind.SAVED && shape(it).output != null }

    fun newShape(kind: NodeKind): GraphNode {
        val model = shape(kind)
        return gson.fromJson(defaults(copycat, kind.name, model.defaults), GraphNode::class.java)
            .copy(inputs = List(checkNotNull(model.inputs).min) { "" })
    }

    private val assemblyTemplates by lazy { buildingTemplates(true) }
    private val legacyTemplates by lazy { buildingTemplates(false) }

    fun newBuilding(kind: BuildingKind, explicit: Boolean): BuildingNode =
        (if (explicit || building(kind).alwaysExplicit) assemblyTemplates else legacyTemplates).getValue(kind)

    private fun buildingTemplates(explicit: Boolean): Map<BuildingKind, BuildingNode> =
        BuildingKind.entries.associateWith { kind ->
            val model = building(kind)
            val overrides = if (explicit) model.defaults else model.legacyDefaults
            gson.fromJson(defaults(building, kind.name, overrides), BuildingNode::class.java)
        }

    private fun defaults(model: Model, kind: String, overrides: JsonObject?): JsonObject =
        model.nodeDefaults.deepCopy().apply {
            overrides?.entrySet()?.forEach { (name, value) -> add(name, value) }
            addProperty("kind", kind)
        }

    private val buildingRules by lazy {
        BuildingKind.entries.associateWith { kind -> building(kind).ports.orEmpty().map(::PortRule) }
    }
    private val semanticRules by lazy { building.semanticPorts.orEmpty().map(::PortRule) }

    fun buildingPort(node: BuildingNode, key: String): BuildingPortModel {
        val rules = if (key.startsWith("semantic:")) semanticRules else buildingRules.getValue(node.kind)
        return checkNotNull(rules.firstOrNull { it.matches(node, key) }) { "Missing ${node.kind} port model for $key" }.model
    }

    private class PortRule(val model: BuildingPortModel) {
        private val exactKey = model.key.takeUnless { '*' in it }
        private val keyPattern = if (exactKey == null) Regex(model.key.split('*').joinToString(".*", transform = Regex::escape)) else null
        private val conditions = model.`when`.orEmpty().map { (field, value) -> Condition(field, value) }

        fun matches(node: BuildingNode, key: String): Boolean =
            (exactKey == key || keyPattern?.matches(key) == true) && conditions.all { it.matches(node) }
    }

    private class Condition(private val field: String, value: JsonElement) {
        private val inverted = value.isJsonObject
        private val expected = if (inverted) value.asJsonObject.get("not").asString else value.asString

        fun matches(node: BuildingNode): Boolean {
            val actual = when (field) {
                "assembly" -> node.assembly.toString()
                "id" -> node.id
                "roomConfig.mode" -> node.roomConfig?.mode?.name
                "storeyMode" -> node.storeyMode
                "roofType" -> node.roofType
                else -> error("Unknown graph port condition $field")
            }
            return (actual == expected) != inverted
        }
    }

    private class Catalog(val version: Int, val copycat: Model, val building: Model)

    class Model(
        val graphSchemas: Set<Int>,
        val defaultSchema: Int,
        val documentSchemas: Set<Int>,
        val maxNodes: Int,
        val maxSources: Int,
        val maxInputs: Int,
        val nodeDefaults: JsonObject,
        val baseShapes: List<BaseShape>?,
        val semanticPorts: List<BuildingPortModel>?,
        val nodes: Map<String, NodeModel>
    )

    class NodeModel(
        val labelKey: String,
        val output: String?,
        val inputs: ShapeInputs?,
        val defaults: JsonObject,
        val legacyDefaults: JsonObject?,
        val legacyOnly: Boolean,
        val alwaysExplicit: Boolean,
        val ports: List<BuildingPortModel>?
    )

    class ShapeInputs(val min: Int, val max: Int, private val types: List<String>) {
        fun type(slot: Int): String = types.getOrElse(slot) { types.last() }
    }

    class BuildingPortModel(
        val key: String,
        val accepts: Set<BuildingKind>,
        val min: Int?,
        val max: Int?,
        val expand: Boolean?,
        val `when`: Map<String, JsonElement>?
    )
}
