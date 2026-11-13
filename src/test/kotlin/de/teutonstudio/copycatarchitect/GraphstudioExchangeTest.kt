package de.teutonstudio.copycatarchitect

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import de.teutonstudio.copycatarchitect.domain.*
import java.io.File
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.*

/** Both runtimes evaluate the same native exports; the browser exports are then
 * read back by Gson and the actual mod validators in a second CI test step. */
class GraphstudioExchangeTest {
    private val gson=GsonBuilder().serializeNulls().create()
    private val directory=File("build/graphstudio-exchange")
    private fun library():Pair<Map<String,Form>,Map<String,Module>> {
        val json=gson.fromJson(File("examples/library.json").readText(),JsonObject::class.java)
        return json.getAsJsonObject("forms").entrySet().associate { it.key to gson.fromJson(it.value,Form::class.java) } to
            json.getAsJsonObject("modules").entrySet().associate { it.key to gson.fromJson(it.value,Module::class.java) }
    }
    @Test fun nativeCorpus() {
        val (forms,modules)=library()
        val cases=mutableListOf<Map<String,Any>>()
        fun shape(name:String,form:Form) {
            val valid=Geometry.validate(form)
            cases+=mapOf("name" to name,"building" to false,"document" to valid,"library" to emptyMap<String,Any>(),
                "count" to Geometry.resolve(valid.parts,valid.groups).solid.count(),"raw" to if(valid.parts.isEmpty()) 0 else Extrusion.cost(valid.parts,valid.groups))
        }
        shape("exact-diagonal",Form(name="Diagonal",graph=NodeGraph(nodes=listOf(
            GraphNode("source",part=Part("copycats:copycat_byte",offset=P(4,4,4))),
            GraphNode("mirror",NodeKind.MIRROR,listOf("source"),mirror=CopycatMirror(CopycatMirrorPlane.BODY_PPN)),
            GraphNode("result",NodeKind.OUTPUT,listOf("mirror"))
        ))))
        shape("roof-template",Form(name="Dachvorlage",graph=NodeGraph.fromParts(listOf(Part(source=RoofPartParameters.SOURCE,roof=RoofPartParameters())))))
        shape("empty-saved",Form(name="Leer",graph=NodeGraph(nodes=listOf(
            GraphNode("air",NodeKind.SAVED,savedCopycat=SavedCopycat(parts=emptyList())),GraphNode("result",NodeKind.OUTPUT,listOf("air"))
        ))))
        val named=GraphGroup("group","Benannt",listOf("cut"),graphInterface=GraphGroupInterface(
            inputs=listOf(GraphGroupInput("in","Quelle","shape",listOf(GraphGroupTarget(0,"0"),GraphGroupTarget(0,"1")))),
            outputs=listOf(GraphGroupOutput("out","Ergebnis","shape",0)),positions=listOf(GraphGroupPosition(40,80))),inputConnections=mapOf("in" to "source"))
        // One named input fans out to two sockets on the same operator.
        shape("named-group",Form(name="Gruppe",graph=NodeGraph(nodes=listOf(GraphNode("source",part=Part("copycats:copycat_byte")),
            GraphNode("cut",NodeKind.UNION,listOf("source","source")),GraphNode("result",NodeKind.OUTPUT,listOf("cut"))),nodeGroups=listOf(named))))
        fun building(name:String,value:Building,sourceForms:Map<String,Form> = forms,sourceModules:Map<String,Module> = modules) {
            val plan=BuildingGraphCompiler.compile(value,sourceForms,sourceModules).plan
            cases+=mapOf("name" to name,"building" to true,"document" to value,"library" to mapOf("forms" to sourceForms,"modules" to sourceModules),
                "count" to plan.placements.size,"raw" to plan.raw)
        }
        building("explicit-projection",Building(schema=2,name="Explizit",graph=BuildingGraph.explicitStarter().copy(projection=BuildingProjection(P(3,2,-7),3))))
        building("special-rooms",SpecialRoomsExample.building())
        building("semantic-T-house",TBuildingExample.building,TBuildingExample.forms,TBuildingExample.modules)
        val formNodes=NodeKind.entries.map { GraphModels.newShape(it).copy(id=it.name) }
        val buildingNodes=BuildingKind.entries.flatMap { kind -> listOf(false,true).map { explicit -> GraphModels.newBuilding(kind,explicit).copy(id=kind.name+if(explicit) "_explicit" else "_legacy") } }
        val buildingSpecs=buildingNodes.map { node ->
            val graph=BuildingGraph(nodes=listOf(BuildingNode("empty_wall",BuildingKind.SEGMENT,block="minecraft:air",length=1))+
                (if(node.kind==BuildingKind.BUILDING) emptyList() else listOf(BuildingNode("building",BuildingKind.BUILDING)))+node)
            mapOf("document" to Building(schema=2,graph=graph),"id" to node.id,"outputType" to buildingGraphType(node.kind),
                "ports" to BuildingPorts.of(node,graph,true).map { port -> mapOf("id" to port.key,"source" to port.source,"accepts" to port.accepts.map { it.name },
                    "types" to port.accepts.map(::buildingGraphType).distinct(),"length" to port.length,"height" to port.height) })
        }
        val corpus=mapOf("cases" to cases,"formNodes" to formNodes,"buildingNodes" to buildingNodes,"buildingSpecs" to buildingSpecs)
        directory.mkdirs();File(directory,"native.json").writeText(gson.toJson(corpus)+"\n")
    }
    @Test fun browserExports() {
        val file=File(directory,"browser.json")
        assumeTrue(file.exists(),"Run the browser exchange tests before the second JVM pass")
        val output=gson.fromJson(file.readText(),JsonObject::class.java)
        val native=gson.fromJson(File(directory,"native.json").readText(),JsonObject::class.java)
        val cases=native.getAsJsonArray("cases").associateBy { it.asJsonObject["name"].asString }
        for(entry in output.getAsJsonArray("cases")) {
            val item=entry.asJsonObject;val original=cases.getValue(item["name"].asString).asJsonObject
            if(item["building"].asBoolean) {
                val b=gson.fromJson(item["document"],Building::class.java)
                val lib=original.getAsJsonObject("library")
                val forms=lib.getAsJsonObject("forms").entrySet().associate { it.key to gson.fromJson(it.value,Form::class.java) }
                val modules=lib.getAsJsonObject("modules").entrySet().associate { it.key to gson.fromJson(it.value,Module::class.java) }
                val plan=BuildingGraphCompiler.compile(b,forms,modules).plan
                assertEquals(original["count"].asInt,plan.placements.size,item["name"].asString)
                assertEquals(original["raw"].asInt,plan.raw,item["name"].asString)
                assertEquals(original["document"].asJsonObject["graph"],gson.toJsonTree(b.graph),"All building fields survive")
            } else {
                val f=Geometry.validate(gson.fromJson(item["document"],Form::class.java))
                assertEquals(original["count"].asInt,Geometry.resolve(f.parts,f.groups).solid.count())
                assertEquals(original["raw"].asInt,if(f.parts.isEmpty()) 0 else Extrusion.cost(f.parts,f.groups))
                assertEquals(original["document"],gson.toJsonTree(f),"All form fields survive")
            }
        }
        assertEquals(cases.size,output.getAsJsonArray("cases").size())
    }
}
