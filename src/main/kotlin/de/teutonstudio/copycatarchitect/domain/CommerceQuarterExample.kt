package de.teutonstudio.copycatarchitect.domain

/** Two inhabited levels around an open lane; the third technical level contains only chimney shafts. */
object CommerceQuarterExample {
    const val HEIGHT=5
    val palette=mapOf("wall" to "minecraft:polished_andesite","brick" to "minecraft:bricks",
        "plaster" to "minecraft:light_gray_terracotta","trim" to "minecraft:polished_andesite",
        "frame" to "minecraft:dark_oak_planks","glass" to "minecraft:glass",
        "floor" to "minecraft:spruce_planks","roof" to "minecraft:deepslate_tiles",
        "iron" to "minecraft:iron_block","chimney" to "minecraft:deepslate_bricks","garden" to "minecraft:moss_block")
    private val facadeGroups=listOf(MaterialGroup(),MaterialGroup("trim","Gesims","trim",1))
    private val windowGroups=listOf(MaterialGroup("glass","Glas","glass"),MaterialGroup("frame","Fensterrahmen","frame",1))
    private fun form(id:String,name:String,parts:List<Part>,groups:List<MaterialGroup> = DEFAULT_GROUPS)=
        Geometry.validate(Form(id=id,name="Handelshof/$name",groups=groups,graph=NodeGraph.fromParts(parts)))
    private fun panel(group:String="body")=Part("create:copycat_panel",tilt=3,group=group)
    private fun box(x:Int,y:Int,z:Int,X:Int,Y:Int,Z:Int,group:String="body")=
        Part(RoofPartParameters.SOURCE,exact=ExactVolume.box(x,y,z,X,Y,Z),group=group)
    private fun arch(left:Boolean):Part {
        var volume=ExactVolume()
        for(i in 0 until 8) {
            val a=i*2;val b=a+2
            val ya=kotlin.math.sqrt((256-(16-a)*(16-a)).toDouble()).toInt()
            val yb=kotlin.math.sqrt((256-(16-b)*(16-b)).toDouble()).toInt()
            val wedge=ExactVolume.box(a,0,13,b,16,16).intersect(ExactVolume.convex(listOf(ExactPlane((yb-ya).toLong(),-2,0,((yb-ya)*a-2*ya).toLong()))))
            volume=volume.union(wedge)
        }
        if(!left) volume=ExactVolume(volume.cells.map { cell -> ExactCell(cell.planes.map { p -> ExactPlane(-p.x,p.y,p.z,p.d-16*p.x) }) })
        return Part(RoofPartParameters.SOURCE,exact=volume)
    }
    val forms:Map<String,Form> by lazy {
        listOf(
            form("cq_full","Konstruktion/Vollblock",listOf(Part("copycats:copycat_block"))),
            form("cq_panel","Fassade/Schmales Wandpanel",listOf(panel())),
            form("cq_base","Fassade/Andesitsockel",listOf(panel(),box(0,0,11,16,4,16,"trim")),facadeGroups),
            form("cq_cornice","Fassade/Feines Gesims",listOf(panel(),box(0,12,10,16,16,16,"trim")),facadeGroups),
            form("cq_pier","Fassade/Dunkler Pilaster",listOf(panel(),box(6,0,10,10,16,16,"trim")),facadeGroups.map { if(it.id=="trim") it.copy(role="frame") else it }),
            form("cq_window","Fenster/Echte Scheibe und Holzrahmen",listOf(
                Part("copycats:copycat_layer",tilt=3,group="glass"),
                box(0,0,12,16,2,16,"frame"),box(0,14,12,16,16,16,"frame"),
                box(0,2,12,2,14,16,"frame"),box(14,2,12,16,14,16,"frame")),windowGroups),
            form("cq_arch_left","Portal/Facettierter Bogen links",listOf(arch(true))),
            form("cq_arch_right","Portal/Facettierter Bogen rechts",listOf(arch(false))),
            form("cq_table","Einrichtung/Tisch",listOf(box(0,12,0,16,15,16),box(6,0,6,10,12,10))),
            form("cq_stool","Einrichtung/Hocker",listOf(box(2,7,2,14,10,14),box(5,0,5,11,7,11))),
            form("cq_shelf","Einrichtung/Regal",listOf(box(1,0,1,3,16,15),box(13,0,1,15,16,15),
                box(1,0,1,15,2,15),box(1,7,1,15,9,15),box(1,14,1,15,16,15))),
            form("cq_bush","Hof/Facettierter Strauch",listOf(box(2,0,2,14,11,14),box(4,11,4,12,15,12))),
            form("cq_bed","Einrichtung/Liegebank",listOf(box(0,3,0,16,9,16),box(2,0,2,5,3,5),box(11,0,11,14,3,14)))
        ).associateBy { "${it.id}@${it.version}" }
    }
    private data class Space(val id:String,val name:String,val level:Int,val rect:Rect,val furniture:Map<P,String>,val entrance:Boolean=false,val height:Int=HEIGHT)
    private val spaces=listOf(
        Space("inn","Gasthaus und Laden",0,Rect(1,1,11,13),mapOf(P(4,0,5) to "table",P(2,0,5) to "stool",P(6,0,5) to "stool",P(4,0,10) to "table",P(2,0,10) to "stool",P(6,0,10) to "stool",P(10,0,3) to "shelf"),true),
        Space("shop","Handel und Werkstatt",0,Rect(19,1,13,22),mapOf(P(22,0,6) to "table",P(26,0,6) to "table",P(22,0,9) to "stool",P(30,0,5) to "shelf"),true),
        Space("home","Wohnen und Atelier",1,Rect(19,1,13,22),mapOf(P(22,0,6) to "table",P(26,0,6) to "stool",P(30,0,5) to "shelf",P(22,0,5) to "table",P(23,0,5) to "table",P(23,0,6) to "table",P(21,0,9) to "bed",P(22,0,9) to "bed",P(21,0,10) to "bed",P(22,0,10) to "bed",P(21,0,11) to "bed",P(22,0,11) to "bed")),
        Space("chimney_a","Unbewohnter Kaminschacht A",2,Rect(24,5,1,1),emptyMap(),height=7),
        Space("chimney_b","Unbewohnter Kaminschacht B",2,Rect(29,19,1,1),emptyMap(),height=7)
    )
    private fun inside(s:Space)=RoomGeometry.cells(listOf(s.rect))-s.furniture.keys
    private fun furnitureFor(s:Space,e:WallRun):String?=e.cells.mapNotNull { s.furniture[it] }.distinct().singleOrNull()
    private fun wallKey(s:Space,e:WallRun)=if(s.level==2) "chimney_wall" else furnitureFor(s,e)?.let { "furniture_${it}_${e.length}" }
        ?: "${s.id}_wall_${e.direction}_${e.length}"
    val modules:Map<String,Module> by lazy {
        val result=linkedMapOf<String,Module>()
        for(s in spaces) for(e in RoomGeometry.edges(RoomGeometry.fromCells(inside(s)))) {
            val key=wallKey(s,e)
            if(result.containsKey("$key@1")) continue
            val furniture=furnitureFor(s,e)
            val cells=buildList {
                for(y in 0 until s.height) for(x in 0 until e.length) {
                    val doorway=s.entrance && y<3 && ((e.direction==0 && x in (e.length/2-1)..(e.length/2)) ||
                        (s.id=="inn" && e.direction==1 && x in 8..9) || (s.id=="shop" && e.direction==3 && x in 8..9))
                    when {
                        s.level==2 -> add(Cell(P(x,y,0),form="cq_full@1",role="chimney"))
                        furniture!=null -> add(if(y==0 || furniture=="shelf" && y==1) Cell(P(x,y,0),form="cq_$furniture@1",role="frame") else Cell(P(x,y,0),block="minecraft:air",empty=true))
                        doorway -> {
                            if(y<2) add(Cell(P(x,y,0),block="minecraft:air",empty=true))
                            else {
                                val left=if(e.direction==0) x==e.length/2-1 else x==8
                                add(Cell(P(x,y,0),form=if(left) "cq_arch_left@1" else "cq_arch_right@1",role="brick"))
                            }
                        }
                        else -> {
                            val pier=x%4==0 || x==e.length-1
                            val f=when { y==0 -> "base";y==HEIGHT-1 -> "cornice";pier -> "pier";else -> "window" }
                            add(Cell(P(x,y,0),form="cq_$f@1",role=if(s.level==1) "plaster" else "brick"))
                        }
                    }
                }
            }
            result["$key@1"]=Module(id=key,name="Handelshof/$key",width=e.length,height=s.height,cells=cells)
        }
        result
    }
    val building:Building by lazy {
        val nodes=mutableListOf(
            BuildingNode("empty_wall",BuildingKind.SEGMENT,"Leere Wand",20,20,block="minecraft:air",length=1),
            BuildingNode("floor",BuildingKind.FLOOR,"Gemeinsamer Holzboden",20,200,form="cq_full@1",role="floor"),
            BuildingNode("ceiling",BuildingKind.CEILING,"Durchgehende Holzdecke",20,380,form="cq_full@1",role="floor"),
            BuildingNode("chimney_ceiling",BuildingKind.CEILING,"Kaminkappe",20,560,form="cq_full@1",role="chimney")
        )
        val wallIds=mutableSetOf<String>()
        for((i,s) in spaces.withIndex()) {
            val cells=inside(s);val rects=RoomGeometry.fromCells(cells);val edges=RoomGeometry.edges(rects)
            for(e in edges) {
                val key=wallKey(s,e)
                if(wallIds.add(key)) nodes+=BuildingNode(key,BuildingKind.SEGMENT,"Wand / $key",350+(wallIds.size/8)*220,(wallIds.size%8)*170,module="$key@1",length=e.length,height=s.height)
            }
            nodes+=BuildingNode("${s.id}_area",BuildingKind.AREA,s.name,1100,i*190,coordinates=cells.sortedWith(compareBy({it.z},{it.x})))
                        nodes+=BuildingNode(s.id,BuildingKind.ROOM,s.name,1700,i*230,rectangles=rects,height=s.height,
                walls=edges.associate { it.id to wallKey(s,it) },floor="floor",ceiling=if(s.level==2) "chimney_ceiling" else "ceiling")
        }
        nodes+=BuildingNode("ground",BuildingKind.STOREY,"EG: Gasthaus, Handel, offene Gasse",2100,50,inputs=listOf("inn","shop"))
        nodes+=BuildingNode("upper",BuildingKind.STOREY,"OG: Wohnen und Atelier",2100,320,inputs=listOf("home"))
        nodes+=BuildingNode("chimneys",BuildingKind.STOREY,"Technikebene: nur zwei Kaminschaechte",2100,590,inputs=listOf("chimney_a","chimney_b"))
        nodes+=BuildingNode("stairs",BuildingKind.STAIRWELL,"Begehbare U-Treppe",2100,860,stairwell=StairwellConfig(layout=StairLayout.U,width=2,landingDepth=2,enclosed=false))
        nodes+=BuildingNode("inn_roof",BuildingKind.ROOF_LEVEL,"Niedriges Gasthaus-Satteldach",2450,50,
            roofLevel=RoofLevelConfig(storey="storey_0",form=RoofForm.GABLE,axis=0,slope=RoofSlope(2,3),overhang16=6))
        nodes+=BuildingNode("home_roof",BuildingKind.ROOF_LEVEL,"Glattes hohes Handelshaus-Satteldach",2450,310,
            roofLevel=RoofLevelConfig(storey="storey_1",form=RoofForm.GABLE,axis=0,slope=RoofSlope(2,3),overhang16=6))
        nodes+=BuildingNode("caps",BuildingKind.ROOF_LEVEL,"Flache Kaminkappen",2450,570,
            roofLevel=RoofLevelConfig(storey="storey_2",form=RoofForm.FLAT,overhang16=0))
        nodes+=BuildingNode("roof_layout",BuildingKind.ROOF_LAYOUT,"Daecher aus drei tragenden Hoehen",2800,50,inputs=listOf("inn_roof","home_roof","caps"))
        nodes+=BuildingNode("roof",BuildingKind.ROOF,"Exakte durchgehende Dachflaechen",3100,50,assembly=true,layout="roof_layout",role="roof",roofConfig=RoofConfig(wall="gable_copycat"))
        nodes+=BuildingNode("gable_copycat",BuildingKind.COPYCAT,"Verputzte Giebel",2800,500,form="cq_full@1",role="plaster")
        val pavement=buildSet { for(x in 0..32) for(z in -3..25) if((x !in 0..12 || z !in 0..14) && (x !in 18..32 || z !in 0..23)) add(P(x,0,z)) }
        val gardens=buildSet { for(x in 1..4) for(z in 17..22) add(P(x,0,z));for(x in 9..11) for(z in 19..23) add(P(x,0,z)) }
        nodes+=BuildingNode("pavement",BuildingKind.GARDEN,"Andesitplatz und offener Hof",2450,1000,coordinates=(pavement-gardens).toList(),block="minecraft:stone_bricks")
        nodes+=BuildingNode("garden",BuildingKind.GARDEN,"Gruener Innenhof",2800,1000,coordinates=gardens.toList(),block="minecraft:moss_block")
        nodes+=BuildingNode("bush_copycat",BuildingKind.COPYCAT,"Gruene Hofstraeucher",3100,1000,form="cq_bush@1",role="garden")
        nodes+=BuildingNode("bushes",BuildingKind.GARDEN,"Niedrige Hofbepflanzung",3100,1200,coordinates=listOf(P(2,0,18),P(3,0,21),P(10,0,20),P(10,0,23)),block="minecraft:moss_block",floor="bush_copycat",elevation=1)
        nodes+=BuildingNode("building",BuildingKind.BUILDING,"Handelshof an der gruenen Gasse",3450,100,
            levels=mapOf(0 to "ground",1 to "upper",2 to "chimneys"),roof="roof",inputs=listOf("pavement","garden","bushes"),
            accessInstances=listOf(VerticalAccessInstance("main_stair","stairs",P(25,0,15),fromStorey="storey_0",toStorey="storey_1")))
        Building(schema=2,id="commerce_quarter",name="Handelshof an der gruenen Gasse",palette=palette,floors=2,roomHeight=HEIGHT,graph=BuildingGraph(nodes=nodes))
    }
    fun plan():Plan=Generator.generate(building,forms,modules)
}
