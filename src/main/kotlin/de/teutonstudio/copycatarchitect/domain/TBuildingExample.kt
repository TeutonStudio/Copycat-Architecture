package de.teutonstudio.copycatarchitect.domain

/** A printable three-storey T house that exercises the complete room and facade assembly. */
object TBuildingExample {
    const val WIDTH=15
    const val DEPTH=15
    const val CROSSBAR_DEPTH=5
    const val STEM_WIDTH=5
    const val STEM_START=5
    const val STOREYS=3
    const val LEVELS=STOREYS
    const val HEIGHT=5
    const val BAY_WIDTH=5

    val rectangles=listOf(Rect(0,0,WIDTH,CROSSBAR_DEPTH),Rect(STEM_START,CROSSBAR_DEPTH,STEM_WIDTH,DEPTH-CROSSBAR_DEPTH))
    val footprint:Set<P> by lazy { RoomGeometry.cells(rectangles) }

    private val reliefGroups=listOf(MaterialGroup(),MaterialGroup("ornament","Quarzrelief","trim",1))
    private val windowGroups=listOf(MaterialGroup("glass","Glas","glass",0),MaterialGroup("frame","Fensterrahmen","frame",1))
    private fun form(id:String,name:String,parts:List<Part>,groups:List<MaterialGroup> = DEFAULT_GROUPS)=
        Geometry.validate(Form(id=id,name="T-Haus/$name",groups=groups,graph=NodeGraph.fromParts(parts)))
    private fun panel(group:String="body")=Part("create:copycat_panel",tilt=3,group=group)
    private fun rail(top:Boolean=false)=Part("copycats:copycat_slice",top=top,offset=P(0,0,-2),group="ornament")

    /** Names intentionally use the same slash hierarchy as the shared Copycat chooser. */
    val forms:Map<String,Form> by lazy {
        listOf(
            form("t_full","Bauteile/Vollblock",listOf(Part("copycats:copycat_block"))),
            form("t_panel","Fassade/Wandpanel",listOf(panel())),
            form("t_base","Fassade/Sockel mit Quarzleiste",listOf(panel(),rail()),reliefGroups),
            form("t_cornice","Fassade/Gesims mit Quarzleiste",listOf(panel(),rail(top=true)),reliefGroups),
            form("t_pier","Fassade/Senkrechter Pilaster",listOf(panel(),
                Part("copycats:copycat_vertical_slice",size=2,offset=P(-6,0,-2),group="ornament")),reliefGroups),
            form("t_inlay","Fassade/Erhabenes Quarzquadrat",listOf(panel(),
                Part("copycats:copycat_byte_panel",tilt=3,offset=P(4,4,-2),group="ornament")),reliefGroups),
            form("t_window","Fenster/Glas mit Holzrahmen",listOf(
                Part("copycats:copycat_layer",tilt=3,group="glass"),
                Part("copycats:copycat_slice",group="frame"),Part("copycats:copycat_slice",top=true,group="frame"),
                Part("copycats:copycat_vertical_slice",group="frame"),Part("copycats:copycat_vertical_slice",turn=1,group="frame")
            ),windowGroups),
            form("t_corner","Fassade/Senkrechte Quarzecke",listOf(Part("copycats:copycat_vertical_slice",size=2)))
        ).associateBy { "${it.id}@${it.version}" }
    }

    /** This example exposes its arrangements in the building graph rather than hiding them in modules. */
    val modules:Map<String,Module> = emptyMap()

    private val facadeInputs=listOf("base_copycat","pier_copycat","window_copycat","brick_copycat","plaster_copycat","cornice_copycat","air_copycat")
    private fun facadeCells(entrance:Boolean)=buildList {
        for(y in 0 until HEIGHT) for(x in 0 until BAY_WIDTH) {
            val source=when {
                entrance && x==BAY_WIDTH/2 && y<3 -> 6
                y==0 -> 0
                y==HEIGHT-1 -> 5
                y==HEIGHT-2 -> if(x%2==0) 3 else 4
                x==0 || x==BAY_WIDTH-1 -> 1
                else -> 2
            }
            add(SegmentLayoutCell(x,y,source))
        }
    }

    val building:Building by lazy {
        val shell=RoomGeometry.shell(rectangles)
        val plateWidth=shell.maxOf { it.x }-shell.minOf { it.x }+1
        val plateDepth=shell.maxOf { it.z }-shell.minOf { it.z }+1
        val walls=RoomGeometry.edges(rectangles).associate { it.id to "facade_${it.length}" }
        val entranceEdge=RoomGeometry.edges(rectangles).single { it.direction==2 && it.cells.all { p -> p.z==DEPTH } }
        fun room(id:String,name:String,entrance:Boolean)=BuildingNode(id,BuildingKind.ROOM,name,1210,if(entrance) 70 else 420,
            assembly=true,joinWallEnds=false,geometry="t_geometry",height=HEIGHT,
            walls=if(entrance) walls+(entranceEdge.id to "entrance") else walls,
            floor="plate",ceiling="plate",corner="corner_copycat")
        Building(schema=2,id="t_house",name="T-Haus mit drei Stockwerken und Relief-Fassade",
            wings=rectangles,floors=STOREYS,roomHeight=HEIGHT,roof="flat",overhang=0,
            floorForm="t_full@1",ceilingForm="t_full@1",trimForm="t_corner@1",
            palette=mapOf(
                "wall" to "minecraft:stone_bricks","brick" to "minecraft:bricks","plaster" to "minecraft:white_terracotta",
                "trim" to "minecraft:quartz_block","frame" to "minecraft:dark_oak_planks","glass" to "minecraft:glass",
                "floor" to "minecraft:oak_planks","roof" to "minecraft:deepslate_tiles"
            ),
            graph=BuildingGraph(nodes=listOf(
                BuildingNode("empty_wall",BuildingKind.SEGMENT,"Leere Wand",20,20,block="minecraft:air",length=1),
                BuildingNode("base_copycat",BuildingKind.COPYCAT,"Steinsockel und Quarzband",20,190,form="t_base@1",role="wall"),
                BuildingNode("pier_copycat",BuildingKind.COPYCAT,"Stein und Quarzpilaster",20,360,form="t_pier@1",role="wall"),
                BuildingNode("window_copycat",BuildingKind.COPYCAT,"Glas mit dunklem Holzrahmen",20,530,form="t_window@1",role="glass"),
                BuildingNode("brick_copycat",BuildingKind.COPYCAT,"Ziegel und erhabenes Quarzquadrat",20,700,form="t_inlay@1",role="brick"),
                BuildingNode("plaster_copycat",BuildingKind.COPYCAT,"Putz und erhabenes Quarzquadrat",20,870,form="t_inlay@1",role="plaster"),
                BuildingNode("cornice_copycat",BuildingKind.COPYCAT,"Putzgesims und Quarzband",20,1040,form="t_cornice@1",role="plaster"),
                BuildingNode("air_copycat",BuildingKind.COPYCAT,"Freier Eingang",20,1210,block="minecraft:air",form="t_full@1"),
                BuildingNode("corner_copycat",BuildingKind.COPYCAT,"Quarzecke",20,1380,form="t_corner@1",role="trim"),
                BuildingNode("floor_copycat",BuildingKind.COPYCAT,"Durchgehender Eichenboden",20,1550,form="t_full@1",role="floor"),
                BuildingNode("roof_copycat",BuildingKind.COPYCAT,"Dunkles Flachdach",20,1720,form="t_full@1",role="roof"),
                BuildingNode("facade_layout",BuildingKind.SEGMENT_LAYOUT,"Fassadenraster 5 x 5",360,70,length=BAY_WIDTH,height=HEIGHT,layoutCells=facadeCells(false)),
                BuildingNode("entrance_layout",BuildingKind.SEGMENT_LAYOUT,"Eingangsraster mit freier Tuer",360,330,length=BAY_WIDTH,height=HEIGHT,layoutCells=facadeCells(true)),
                BuildingNode("plate_layout",BuildingKind.SEGMENT_LAYOUT,"Geschossplatte 17 x 17",360,590,length=plateWidth,height=plateDepth),
                BuildingNode("t_area",BuildingKind.AREA,"T-Grundriss 125 Bloecke",360,850,
                    coordinates=footprint.sortedWith(compareBy({ it.z },{ it.x }))),
                BuildingNode("t_geometry",BuildingKind.ROOM_GEOMETRY,"T-Lebensraum mit Hoehe 5",760,850,geometry="t_area",height=HEIGHT),
                BuildingNode("facade_5",BuildingKind.SEGMENT,"Fensterbucht mit Relief und Gesims",760,70,
                    assembly=true,module="",layout="facade_layout",inputs=facadeInputs),
                BuildingNode("entrance",BuildingKind.SEGMENT,"Eingang mit seitlichen Fenstern",760,330,
                    assembly=true,module="",layout="entrance_layout",inputs=facadeInputs),
                BuildingNode("plate",BuildingKind.SEGMENT,"Gemeinsamer Boden und Decke",760,590,
                    assembly=true,module="",layout="plate_layout",inputs=listOf("floor_copycat")),
                BuildingNode("facade_10",BuildingKind.PATTERN,"Zwei Fensterbuchten",1010,1070,
                    assembly=true,inputs=listOf("facade_5","facade_5")),
                BuildingNode("facade_15",BuildingKind.PATTERN,"Drei Fensterbuchten",1010,1300,
                    assembly=true,inputs=listOf("facade_5","facade_5","facade_5")),
                room("ground_room","T-Lebensraum Erdgeschoss mit Eingang",true),
                room("upper_room","T-Lebensraum Obergeschoss",false),
                BuildingNode("ground_storey",BuildingKind.STOREY,"Erdgeschoss",1570,70,inputs=listOf("ground_room"),offsets=listOf(P())),
                BuildingNode("upper_storey",BuildingKind.STOREY,"Erstes Obergeschoss",1570,370,inputs=listOf("upper_room"),offsets=listOf(P())),
                BuildingNode("top_storey",BuildingKind.STOREY,"Zweites Obergeschoss",1570,670,inputs=listOf("upper_room"),offsets=listOf(P())),
                BuildingNode("roof",BuildingKind.ROOF,"T-Flachdach aus Tiefenschiefer",1570,1000,
                    inputs=listOf("roof_copycat"),role="roof",roofType="flat",overhang=0),
                BuildingNode("building",BuildingKind.BUILDING,"Dreistoeckiges T-Haus",1960,110,
                    levels=mapOf(0 to "ground_storey",1 to "upper_storey",2 to "top_storey"),roof="roof")
            )))
    }

    fun plan():Plan=Generator.generate(building,forms,modules)
}
