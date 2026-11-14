package de.teutonstudio.copycatarchitect.domain

/** Small complete fixture shared by domain, native editor and physical placement checks. */
object SpecialRoomsExample {
    fun building(openCeiling:Boolean=false):Building {
        val rectangles=listOf(Rect(width=9,length=13))
        val room=BuildingNode("room",BuildingKind.ROOM,"Hauptraum",600,40,rectangles=rectangles,
            walls=RoomGeometry.edges(rectangles).associate { it.id to if(it.direction%2==0) "wall_x" else "wall_z" },floor="floor",ceiling="ceiling")
        val bayEdge=RoomWallJoints.runs(room).first { it.direction==2 }.id
        val nodes=listOf(
            BuildingNode("empty_wall",BuildingKind.SEGMENT,"Empty wall",block="minecraft:air",length=1),
            BuildingNode("full",BuildingKind.COPYCAT,"Stein"),
            BuildingNode("glass",BuildingKind.COPYCAT,"Fensterglas",role="glass",materialMode=MaterialMode.GROUPED),
            BuildingNode("roof_source",BuildingKind.COPYCAT,"Dachdeckung",role="roof",materialMode=MaterialMode.GROUPED),
            BuildingNode("wall_x",BuildingKind.SEGMENT,"Wand X",module="",length=9,height=4,inputs=listOf("full")),
            BuildingNode("wall_z",BuildingKind.SEGMENT,"Wand Z",module="",length=13,height=4,inputs=listOf("full")),
            BuildingNode("side",BuildingKind.SEGMENT,"Wandperiode",module="",length=1,height=1,inputs=listOf("full")),
            BuildingNode("window",BuildingKind.SEGMENT,"Fensterperiode",module="",length=1,height=1,inputs=listOf("glass")),
            BuildingNode("floor",BuildingKind.FLOOR,"Boden",inputs=listOf("full")),
            BuildingNode("ceiling",BuildingKind.CEILING,"Decke",inputs=listOf("full")),
            BuildingNode("bay",BuildingKind.BAY_WINDOW,"Erker",300,350,bayWindow=BayWindowConfig(wall="side",front="window",floor="floor",ceiling="ceiling")),
            room.copy(bayWindows=listOf(BayWindowInstance("bay_0","bay",bayEdge,2,openingWidth=2,openingHeight=3))),
            BuildingNode("storey",BuildingKind.STOREY,"Erdgeschoss",900,40,inputs=listOf("room"),offsets=listOf(P())),
            BuildingNode("dormer",BuildingKind.DORMER,"Satteldachgaube",300,650,dormer=DormerConfig(wall="side",front="window",roof="roof_source")),
            BuildingNode("roof_level",BuildingKind.ROOF_LEVEL,"Satteldach",600,650,roofLevel=RoofLevelConfig(axis=1,overhang16=0),
                dormers=listOf(DormerInstance("dormer_0","dormer",P(1,0,-1),openCeiling=openCeiling))),
            BuildingNode("roof_layout",BuildingKind.ROOF_LAYOUT,"Dachanordnung",900,650,inputs=listOf("roof_level")),
            BuildingNode("roof",BuildingKind.ROOF,"Dach",1200,650,assembly=true,layout="roof_layout",role="roof"),
            BuildingNode("building",BuildingKind.BUILDING,"Haus mit Erker und Gaube",1250,40,levels=mapOf(0 to "storey"),roof="roof")
        )
        return Building(schema=2,id="special_rooms",name="Haus mit Erker und Gaube",graph=BuildingGraph(nodes=nodes).withCurrentSchema())
    }
}
