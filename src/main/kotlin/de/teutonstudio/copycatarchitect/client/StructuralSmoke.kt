package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.Json
import de.teutonstudio.copycatarchitect.server.Library
import de.teutonstudio.copycatarchitect.server.Snapshot
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen

/** Native normal/compact acceptance for roof levels, prepared shafts and stairwell settings. */
internal object StructuralSmoke {
    private var ticks=0
    private var failed=false
    private var previous:Screen?=null
    private var previousScale=1
    private lateinit var previousSnapshot:Snapshot
    private lateinit var building:BuildingScreen
    private var original=""
    private var edited=""
    private var dragEnd=0.0 to 0.0

    internal fun fixture():Building {
        fun wall(id:String,length:Int)=BuildingNode(id,BuildingKind.SEGMENT,id,module="",inputs=listOf("wall_copycat"),length=length,height=4)
        fun room(id:String,width:Int)=BuildingNode(id,BuildingKind.ROOM,id,rectangles=listOf(Rect(width=width,length=18)),height=4,
            walls=RoomGeometry.edges(listOf(Rect(width=width,length=18))).associate { it.id to if(it.length==18) "wall18" else "wall$width" },floor="floor",ceiling="ceiling")
        val graph=BuildingGraph(nodes=listOf(
            BuildingNode("empty_wall",BuildingKind.SEGMENT,"Empty wall",module="",block="minecraft:air",length=1),
            BuildingNode("wall_copycat",BuildingKind.COPYCAT,"Mauerwerk"),
            BuildingNode("plate_copycat",BuildingKind.COPYCAT,"Eichenpodest",role="frame"),
            BuildingNode("stair_copycat",BuildingKind.COPYCAT,"Eichenstufe",form="stair@1",role="frame"),
            wall("wall20",20),wall("wall18",18),wall("wall12",12),
            BuildingNode("floor",BuildingKind.FLOOR,"Geschossboden",inputs=listOf("plate_copycat")),
            BuildingNode("ceiling",BuildingKind.CEILING,"Geschossdecke",inputs=listOf("plate_copycat")),
            room("ground_room",20),room("upper_room",12),
            BuildingNode("ground_storey",BuildingKind.STOREY,"Erdgeschoss",inputs=listOf("ground_room")),
            BuildingNode("upper_storey",BuildingKind.STOREY,"Obergeschoss",inputs=listOf("upper_room")),
            BuildingNode("roof_lower",BuildingKind.ROOF_LEVEL,"Seitendach",roofLevel=RoofLevelConfig(storey="ground",form=RoofForm.PENT,slope=RoofSlope(1,2),overhang16=8)),
            BuildingNode("roof_top",BuildingKind.ROOF_LEVEL,"Abschlussdach",roofLevel=RoofLevelConfig(storey="upper",form=RoofForm.GABLE,slope=RoofSlope(1,2),overhang16=8)),
            BuildingNode("roof_layout",BuildingKind.ROOF_LAYOUT,"Zwei Dachebenen",inputs=listOf("roof_lower","roof_top")),
            BuildingNode("roof",BuildingKind.ROOF,"Modulares Dach",assembly=true,layout="roof_layout",role="roof",roofConfig=RoofConfig()),
            BuildingNode("stairs",BuildingKind.STAIRWELL,"Treppenhaus",stairwell=StairwellConfig(tread="stair_copycat",landing="plate_copycat",enclosure="wall_copycat")),
            BuildingNode("elevator",BuildingKind.ELEVATOR_SHAFT,"Aufzugsvorbereitung",elevatorShaft=ElevatorShaftConfig(enclosure="wall_copycat")),
            BuildingNode("service",BuildingKind.SERVICE_SHAFT,"Versorgungsschacht",serviceShaft=ServiceShaftConfig(enclosure="wall_copycat")),
            BuildingNode("building",BuildingKind.BUILDING,"Strukturhaus",levels=mapOf(0 to "ground_storey",1 to "upper_storey"),levelIds=mapOf(0 to "ground",1 to "upper"),roof="roof",
                accessInstances=listOf(VerticalAccessInstance("stairs_instance","stairs",P(3,0,7)),VerticalAccessInstance("elevator_instance","elevator",P(9,0,5)),VerticalAccessInstance("service_instance","service",P(10,0,14))))
        ))
        return Building(schema=2,id="structural_smoke",name="Dachebenen und Erschließung",graph=graph.withCurrentSchema())
    }

    fun tick():Boolean {
        if(failed) return false
        val mc=Minecraft.getInstance()
        if(ticks==0 && (mc.screen==null || mc.overlay!=null)) return false
        ticks++
        var acted=true
        try {
            when(ticks) {
                1 -> begin(mc)
                8 -> { select(BuildingPanelSection.ROOF,"roof_top");openProperties("roof_top") }
                13 -> { assertLayout();screenshot(mc,"roof-level-settings-normal") }
                18 -> editRoofSlope()
                23 -> { apply();check(document()!=original);edited=document();historyRoundTrip() }
                28 -> { select(BuildingPanelSection.ROOF,"roof");mode("3d") }
                33 -> { assertPreview();screenshot(mc,"roof-3d-normal") }
                38 -> { select(BuildingPanelSection.ACCESS,"stairs");openProperties("stairs") }
                43 -> { assertLayout();screenshot(mc,"stairs-settings-normal") }
                48 -> editStairLayout()
                53 -> { apply();check(node("stairs").stairwell!!.layout==StairLayout.STRAIGHT);edited=document();historyRoundTrip() }
                58 -> openPlacement()
                60 -> startCoreDrag()
                63 -> { assertLayout();screenshot(mc,"core-placement-normal") }
                66 -> finishCoreDrag()
                68 -> closeSubscreen()
                73 -> { unchanged();mc.options.guiScale().set(2);mc.resizeDisplay();check(building.width in 400..499 && building.height in 230..299) }
                78 -> { select(BuildingPanelSection.ROOF,"roof_top");openProperties("roof_top") }
                83 -> { assertLayout();screenshot(mc,"roof-level-settings-compact") }
                88 -> closeSubscreen()
                93 -> { select(BuildingPanelSection.ROOF,"roof");mode("3d") }
                98 -> { assertPreview();screenshot(mc,"roof-3d-compact") }
                103 -> { select(BuildingPanelSection.ACCESS,"stairs");openProperties("stairs") }
                108 -> { assertLayout();screenshot(mc,"stairs-settings-compact") }
                113 -> { closeSubscreen();select(BuildingPanelSection.ACCESS,"elevator");openProperties("elevator") }
                118 -> {
                    val sheet=active()
                    check(sheet.mouseScrolled(sheet.width/2.0,sheet.height/2.0,0.0,-2.0))
                    assertLayout();check(buttons().any { it.message.string.contains(ui("elevator_preparation_hint")) })
                }
                120 -> screenshot(mc,"elevator-settings-compact")
                123 -> { closeSubscreen();openPlacement() }
                125 -> startCoreDrag()
                128 -> { assertLayout();screenshot(mc,"core-placement-compact") }
                131 -> finishCoreDrag()
                133 -> closeSubscreen()
                138 -> {
                    unchanged();ClientState.snapshot=previousSnapshot;mc.options.guiScale().set(previousScale);mc.resizeDisplay();mc.setScreen(previous)
                    println("ARCHITECT STRUCTURAL UI SMOKE PASSED: real roof-level and stair layout edits, transactional Apply/Undo/Redo, exact multi-level roof previews, three independently positioned cores, resolved translations and bounded normal/compact property and placement screens verified")
                    mc.stop()
                }
                else -> acted=false
            }
        } catch(error:Throwable) {
            failed=true;System.err.println("ARCHITECT STRUCTURAL UI SMOKE FAILED at tick $ticks: ${error.message}")
            screenshot(mc,"failure-$ticks")
            throw IllegalStateException("Structural UI smoke failed at tick $ticks",error)
        }
        return acted
    }
    private fun begin(mc:Minecraft) {
        previous=mc.screen;previousScale=mc.options.guiScale().get();previousSnapshot=ClientState.snapshot
        ClientState.snapshot=Snapshot(library=Library().apply { seed() })
        mc.options.guiScale().set(1);mc.resizeDisplay()
        val fixture=fixture();val lib=ClientState.snapshot.library
        BuildingGraphCompiler.structure(fixture.graph!!)
        val compiled=Generator.generate(fixture,lib.forms,lib.modules)
        check(compiled.placements.any { it.component=="roof" } && compiled.placements.any { it.component=="stairs" })
        check(compiled.placements.none { it.pos in compiled.clear })
        building=BuildingScreen().apply { building=fixture;buildingGraphView.selected="roof_top" }
        mc.setScreen(building);building.openBuildingSection(BuildingPanelSection.ROOF)
        check(building.width==854 && building.height==480)
        original=document();assertTranslations()
    }
    private fun graph()=checkNotNull(building.building.graph)
    private fun node(id:String)=graph().nodes.single { it.id==id }
    private fun document()=Json.encode(building.building)
    private fun unchanged()=check(document()==original) { "Presentation and cancelled drafts must preserve the complete structural document" }
    private fun active()=checkNotNull(Minecraft.getInstance().screen)
    private fun buttons()=active().children().filterIsInstance<Button>()
    private fun control(key:String)=buttons().filter { it.message.string==ui(key) }.minByOrNull { it.y }
        ?: error("Missing native control $key on ${active().javaClass.simpleName}")
    private fun click(point:Pair<Double,Double>) {
        val s=active();check(point.first>=0 && point.first<s.width && point.second>=0 && point.second<s.height)
        s.mouseMoved(point.first,point.second)
        check(s.mouseClicked(point.first,point.second,0)) { "Actual native structural screen must receive the click" }
        s.mouseReleased(point.first,point.second,0)
    }
    private fun action(key:String) { val b=control(key);check(b.active && b.visible);click(b.x+b.width/2.0 to b.y+b.height/2.0) }
    private fun select(section:BuildingPanelSection,id:String) {
        check(active()===building);unchanged();action(section.key)
        click(checkNotNull(building.buildingDefinitionList).definitionPoint(id));check(building.buildingGraphView.selected==id)
        assertLayout();unchanged()
    }
    private fun inspector()=checkNotNull(building.buildingGraphCanvas)
    private fun properties() {
        val c=inspector()
        c.showSection("properties")
    }
    private fun property(id:String,row:Int):Pair<Double,Double> {
        val c=inspector();val point=c.propertyPoint(id,row)
        val target=point.second.coerceIn(c.y+70.0,c.y+c.height-18.0)
        if(target!=point.second) check(building.mouseScrolled(c.x+c.width/2.0,c.y+c.height-8.0,0.0,(target-point.second)/25.0))
        return c.propertyPoint(id,row)
    }
    private fun openProperties(id:String) {
        properties();click(property(id,0));check(active() is StructurePropertyScreen);unchanged()
    }
    private fun mode(mode:String) { inspector().showSection(mode);check(inspector().sectionExpanded(mode));unchanged() }
    private fun assertPreview() {
        check(inspector().previewPlan().getOrThrow().placements.any { it.component=="roof" })
        checkNotNull(inspector().previewViewport());assertLayout();unchanged()
    }
    private fun editRoofSlope() {
        val b=buttons().first { it.message.string.startsWith(ui("roof_slope")+":") }
        click(b.x+b.width/2.0 to b.y+b.height/2.0)
        check(active().keyPressed(264,0,0));check(active().keyPressed(257,0,0));unchanged()
    }
    private fun editStairLayout() {
        val b=buttons().first { it.message.string.startsWith(ui("stairs_layout")+":") }
        click(b.x+b.width/2.0 to b.y+b.height/2.0)
        check(active().keyPressed(268,0,0));check(active().keyPressed(257,0,0));unchanged()
    }
    private fun apply() { action("apply");check(active()===building) { "Applying a valid structure draft must return to the shared building document" } }
    private fun historyRoundTrip() {
        val editedLibrary=ClientState.snapshot.library;Generator.generate(building.building,editedLibrary.forms,editedLibrary.modules)
        action("undo");unchanged();action("redo");check(document()==edited);action("undo");unchanged()
        val lib=ClientState.snapshot.library;Generator.generate(building.building,lib.forms,lib.modules)
    }
    private fun openPlacement() {
        select(BuildingPanelSection.BUILDING,"building");click(property("building",0))
        check(active() is BuildingSiteScreen) { "The building core-placement control must open its groundplan editor" }
        (active() as BuildingSiteScreen).selectPart("access:0");unchanged()
    }
    private fun startCoreDrag() {
        val site=active() as BuildingSiteScreen;site.selectPart("access:0")
        val start=site.partPoint("access:0")
        val unit=site.gridPoint(1.0,0.0).first-site.gridPoint(0.0,0.0).first
        dragEnd=start.first to start.second+unit
        check(site.mouseClicked(start.first,start.second,0))
        check(site.selectedPart=="access:0")
        check(site.mouseDragged(dragEnd.first,dragEnd.second,0,0.0,unit));unchanged()
    }
    private fun finishCoreDrag() {
        val site=active() as BuildingSiteScreen
        check(site.mouseReleased(dragEnd.first,dragEnd.second,0))
        check(node("building").accessInstances.first().position==P(3,0,8)) { "Core dragging must change only its independent instance position" }
        val moved=document();check(moved!=original)
        val originalGraph=Json.decode<Building>(original).graph!!
        check(graph().nodes.filter { it.id!="building" }==originalGraph.nodes.filter { it.id!="building" })
        val lib=ClientState.snapshot.library;Generator.generate(building.building,lib.forms,lib.modules)
        check(site.keyPressed(90,0,2));unchanged()
        check(site.keyPressed(89,0,2));check(document()==moved)
        check(site.keyPressed(90,0,2));unchanged()
    }
    private fun closeSubscreen() { check(active()!==building);active().onClose();check(active()===building);unchanged() }
    private fun assertTranslations() {
        for(key in listOf("panel_access","roof_level_settings","roof_slope","roof_templates","structure_settings","structure_instances","stairs_layout","elevator_preparation_hint"))
            check(!ui(key).startsWith("gui.copycat_architect.")) { "Structural translation is missing: $key" }
    }
    private fun assertLayout() {
        val s=active();assertTranslations()
        s.children().filterIsInstance<AbstractWidget>().filter { it.visible }.forEach { w->
            check(w.x>=0 && w.y>=0 && w.x+w.width<=s.width && w.y+w.height<=s.height) { "Structural widget extends beyond ${s.width}x${s.height}: ${w.message.string}" }
            check(!w.message.string.contains("gui.copycat_architect.")) { "Structural control exposes an untranslated key" }
        }
    }
    private fun screenshot(mc:Minecraft,name:String) {
        Screenshot.grab(mc.gameDirectory,"architect-structural-$name.png",mc.mainRenderTarget) { println(it.string) }
    }
}
