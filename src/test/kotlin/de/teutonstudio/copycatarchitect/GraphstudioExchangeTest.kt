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
        val transformed=NodeGraph(schema=4,nodes=listOf(
            GraphNode("source",part=Part("copycats:copycat_byte"),copycatTransforms=listOf(
                CopycatTransform(CopycatTransformKind.POSITION,offset=P(2,0,0)),
                CopycatTransform(axis=RotationAxis.Y,pivot=P(8,8,8)),
                CopycatTransform(CopycatTransformKind.MIRROR,plane=CopycatMirrorPlane.Z),
                CopycatTransform(CopycatTransformKind.POSITION,false,offset=P(-16,0,0)))),
            GraphNode("result",NodeKind.OUTPUT,listOf("source"))))
        shape("ordered-transforms",Form(name="Transformiert",graph=transformed))
        shape("duplicate-transforms",Form(name="Duplikat",graph=GraphDuplicates.shape(transformed,DEFAULT_GROUPS,listOf("source"),listOf("duplicate"))))
        shape("typed-position",Form(name="Position",graph=NodeGraph(schema=4,nodes=listOf(
            GraphNode("source",part=Part("copycats:copycat_byte")),GraphNode("value",NodeKind.POSITION,translation=P(1,2,0)),
            GraphNode("move",NodeKind.TRANSLATE,listOf("source","value")),GraphNode("result",NodeKind.OUTPUT,listOf("move"))))))
        val vectorNodes=listOf(GraphNode("source",part=Part("copycats:copycat_byte")),
            GraphNode("one",NodeKind.INT,countValue=1),GraphNode("two",NodeKind.INT,countValue=2),
            GraphNode("sum",NodeKind.INT_ADD,listOf("one","two")),GraphNode("vector",NodeKind.POSITION,listOf("sum","one","two")),
            GraphNode("x",NodeKind.POSITION_X,listOf("vector")),GraphNode("y",NodeKind.POSITION_Y,listOf("vector")),GraphNode("z",NodeKind.POSITION_Z,listOf("vector")),
            GraphNode("move",NodeKind.TRANSLATE,listOf("source","vector")),GraphNode("result",NodeKind.OUTPUT,listOf("move")))
        val vectorGroup=GraphGroup("split","Position zerlegen",listOf("x","y","z"),graphInterface=GraphGroupInterface(
            inputs=listOf(GraphGroupInput("vector","Vektor3","position",(0..2).map { GraphGroupTarget(it,"0") })),
            outputs=listOf("X","Y","Z").mapIndexed { index,axis -> GraphGroupOutput(axis,axis,"INT",index) }),inputConnections=mapOf("vector" to "vector"),
            libraryReference=GraphLibraryReference("vector_definition",2,"Vektor","nodeGroups"))
        shape("integer-vector-reference",Form(name="Vektorrechnung",graph=NodeGraph(schema=5,nodes=vectorNodes,nodeGroups=listOf(vectorGroup))))
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
        val groupedCopySource=Form(name="Gruppenduplikat",graph=NodeGraph(nodes=listOf(GraphNode("source",part=Part("copycats:copycat_byte")),
            GraphNode("cut",NodeKind.UNION,listOf("source","source")),GraphNode("result",NodeKind.OUTPUT,listOf("cut"))),nodeGroups=listOf(named)))
        val duplicatedGroup=GraphDuplicates.shape(groupedCopySource.graph!!,groupedCopySource.groups,listOf("source","cut"),listOf("source_copy","cut_copy")).let { copied -> copied.replace(copied.nodes.first { it.id=="cut_copy" }.copy(copycatTransforms=listOf(CopycatTransform(CopycatTransformKind.POSITION,offset=P(1,0,0))))) }
        shape("duplicate-bound-group",groupedCopySource.copy(graph=duplicatedGroup.copy(nodeGroups=duplicatedGroup.nodeGroups.map {if(it.id=="group") it else it.copy(id="group_copy")})))
        fun building(name:String,value:Building,sourceForms:Map<String,Form> = forms,sourceModules:Map<String,Module> = modules) {
            val graph=value.graph!!;val terminal=graph.resultNode()
            val plan=if(terminal.resultType!=null && terminal.resultType!="BUILDING") BuildingGraphCompiler.Evaluation(graph,value.palette,sourceForms,sourceModules).preview(terminal.id) else BuildingGraphCompiler.compile(value,sourceForms,sourceModules).plan
            cases+=mapOf("name" to name,"building" to true,"document" to value,"library" to mapOf("forms" to sourceForms,"modules" to sourceModules),
                "count" to plan.placements.size,"raw" to plan.raw)
        }
        val transformSource=Form(id="transform_byte",version=3,name="Byte v3",parts=listOf(Part("copycats:copycat_byte")))
        val assembly=BuildingGraph.explicitStarter()
        val transformedBuilding=assembly.copy(schema=5,nodes=assembly.nodes.map { node ->
            when(node.id) {
                "wall_copycat"->node.copy(form="transform_byte@3",copycatTransforms=listOf(CopycatTransform(axis=RotationAxis.Y),CopycatTransform(CopycatTransformKind.MIRROR,plane=CopycatMirrorPlane.X)))
                "wall"->node.copy(inputs=listOf("positioned"))
                else->node
            }
        }+BuildingNode("position_value",BuildingKind.POSITION,translation=P(1,0,0))+
          BuildingNode("positioned",BuildingKind.COPYCAT_POSITION,inputs=listOf("wall_copycat"),positionValue="position_value"))
        building("common-transformed-building",Building(schema=2,name="Transformationshaus",graph=transformedBuilding),forms+mapOf("transform_byte@3" to transformSource,"transform_byte@4" to transformSource.copy(version=4,parts=listOf(Part("copycats:copycat_block")))),modules)
        building("explicit-projection",Building(schema=2,name="Explizit",graph=BuildingGraph.explicitStarter().copy(projection=BuildingProjection(P(3,2,-7),3))))
        building("special-rooms",SpecialRoomsExample.building())
        building("roof-peak",gson.fromJson(File("examples/building-roof-peak.json").readText(),Building::class.java))
        building("rotation-segment",gson.fromJson(File("examples/building-rotation-segment.json").readText(),Building::class.java))
        val adjacent=gson.fromJson(File("examples/building-rotation-segment.json").readText(),Building::class.java)
        val adjacentNodes=adjacent.graph!!.nodes.map { if(it.id=="column") it.copy(columnMode=RotationSegmentMode.ADJACENT,dimensions=mapOf("length" to "sum")) else it }+
            BuildingNode("one",BuildingKind.INT,countValue=1)+BuildingNode("sum",BuildingKind.INT_ADD,inputs=listOf("count","one"))+
            BuildingNode("vector",BuildingKind.POSITION,inputs=listOf("one","sum","one"))+BuildingNode("extracted",BuildingKind.POSITION_Y,inputs=listOf("vector"))
        building("adjacent-column-arithmetic",adjacent.copy(graph=adjacent.graph!!.copy(nodes=adjacentNodes).withCurrentSchema()))
        building("roof-eave",gson.fromJson(File("examples/building-roof-eave.json").readText(),Building::class.java))
        building("semantic-T-house",TBuildingExample.building,TBuildingExample.forms,TBuildingExample.modules)
        val areaCount=BuildingNode("area_count",BuildingKind.INT,countValue=4)
        val area=BuildingNode("area",BuildingKind.AREA,areaMode=AreaMode.SQUARE,length=3,height=3,dimensions=mapOf("length" to areaCount.id))
        val dynamic=BuildingGraph.explicitStarter().let { base -> base.copy(nodes=base.nodes.map { n -> when(n.id) {
            "room_geometry" -> n.copy(geometry=area.id)
            "wall","floor","ceiling" -> n.copy(dynamicAxes=setOf(RotationAxis.X,RotationAxis.Y))
            else -> n
        } }+area+areaCount).withCurrentSchema() }
        building("square-dynamic-room",Building(schema=2,name="Dynamische Flächen",graph=dynamic))
        val rectangle=dynamic.replace(area.copy(areaMode=AreaMode.RECTANGLE,height=2))
        building("rectangle-dynamic-room",Building(schema=2,name="Rechteckfläche",graph=rectangle))
        val roofLevel=BuildingNode("roof_level",BuildingKind.ROOF_LEVEL,roofLevel=RoofLevelConfig(form=RoofForm.HIP,overhang16=0))
        val roofSource=BuildingNode("roof_source",BuildingKind.SEGMENT,assembly=true,module="",inputs=listOf("plate_copycat"),dynamicAxes=setOf(RotationAxis.X,RotationAxis.Y))
        val automatic=dynamic.copy(nodes=dynamic.nodes.map { n -> when(n.id) {
            "area_count" -> n.copy(countValue=2)
            "roof_layout" -> n.copy(inputs=listOf(roofLevel.id))
            "roof" -> n.copy(adaptiveRoof=true,inputs=List(5) { roofSource.id })
            "building" -> n.copy(roof="roof")
            else -> n
        } }+roofLevel+roofSource).withCurrentSchema()
        building("adaptive-roof-dynamic-segments",Building(schema=2,name="Automatische Dachsegmente",graph=automatic))
        val mixed=GraphCards.starter().let { g -> g.copy(nodes=g.nodes+BuildingNode("number",BuildingKind.INT,countValue=4)+BuildingNode("vector",BuildingKind.POSITION,inputs=listOf("number","number","number"))) }
        building("unified-copycat-card",Building(schema=2,name="Copycatkarte",graph=mixed))
        building("unified-count-card",Building(schema=2,name="Anzahlkarte",graph=mixed.replace(mixed.resultNode().copy(resultType="INT",inputs=listOf("number")))))
        val cardResult=BuildingNode("result",BuildingKind.BUILDING,name="Ergebnis",resultType="BUILDING",inputs=listOf("building"))
        building("unified-building-card",Building(schema=2,name="Gebäudekarte",graph=automatic.copy(nodes=automatic.nodes+cardResult).withCurrentSchema()))
        for(kind in SegmentBooleans.kinds) {
            val a=BuildingNode("operand_a",BuildingKind.SEGMENT,module="",form="full@1",length=3,height=2)
            val b=a.copy(id="operand_b",length=2)
            val op=BuildingNode("spatial_op",kind,inputs=listOf(a.id,b.id),operandOffsets=listOf(P(-3,-2,1),P(-1,-2,1)))
            val base=BuildingGraph.starter()
            val g=base.copy(nodes=base.nodes.map { if(it.id=="building") it.copy(resultType="WALL",levels=emptyMap(),roof="",inputs=listOf(op.id)) else it }+listOf(a,b,op)).withCurrentSchema()
            building("relative-${kind.name.lowercase()}",Building(schema=2,graph=g))
        }
        for(operation in SegmentBooleans.operations) {
            val a=BuildingNode("operand_a",BuildingKind.SEGMENT,module="",form="full@1",length=3,height=2)
            val b=a.copy(id="operand_b",length=2)
            val op=BuildingNode("spatial_op",BuildingKind.SEGMENT_OPERATOR,operation=operation,inputs=listOf(a.id,b.id),operandOffsets=listOf(P(-3,-2,1),P(-1,-2,1)))
            val base=BuildingGraph.starter()
            val g=base.copy(nodes=base.nodes.map { if(it.id=="building") it.copy(resultType="WALL",levels=emptyMap(),roof="",inputs=listOf(op.id)) else it }+listOf(a,b,op)).withCurrentSchema()
            building("operator-family-relative-${operation.lowercase()}",Building(schema=2,graph=g))
        }
        val direct=BuildingAssembly.directStarter().let { g -> g.copy(nodes=g.nodes.map { if(it.id=="building") it.copy(roof="roof") else it }) }
        building("direct-area-roof-building",Building(schema=2,graph=direct))
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
                val graph=b.graph!!;val terminal=graph.resultNode()
                val plan=if(terminal.resultType!=null && terminal.resultType!="BUILDING") BuildingGraphCompiler.Evaluation(graph,b.palette,forms,modules).preview(terminal.id) else BuildingGraphCompiler.compile(b,forms,modules).plan
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
    @Test fun localBrowserExports() {
        val file=File(directory,"local-browser.json")
        assumeTrue(file.exists(),"Run the local JVM and browser bridge first")
        val output=gson.fromJson(file.readText(),JsonObject::class.java)
        val corpus=gson.fromJson(File(directory,"native.json").readText(),JsonObject::class.java).getAsJsonArray("cases").associateBy {it.asJsonObject["name"].asString}
        for(value in output.getAsJsonArray("cases")) {
            val item=value.asJsonObject;val original=corpus.getValue(item["name"].asString).asJsonObject
            if(item["building"].asBoolean) {
                val lib=original.getAsJsonObject("library")
                val forms=lib.getAsJsonObject("forms").entrySet().associate {it.key to gson.fromJson(it.value,Form::class.java)}
                val modules=lib.getAsJsonObject("modules").entrySet().associate {it.key to gson.fromJson(it.value,Module::class.java)}
                val plan=BuildingGraphCompiler.compile(gson.fromJson(item["document"],Building::class.java),forms,modules).plan
                assertEquals(original["count"].asInt,plan.placements.size);assertEquals(original["raw"].asInt,plan.raw)
            } else {
                val form=Geometry.validate(gson.fromJson(item["document"],Form::class.java))
                assertEquals(original["count"].asInt,Geometry.resolve(form.parts,form.groups).solid.count());assertEquals(original["raw"].asInt,if(form.parts.isEmpty()) 0 else Extrusion.cost(form.parts,form.groups))
            }
        }
    }

}
