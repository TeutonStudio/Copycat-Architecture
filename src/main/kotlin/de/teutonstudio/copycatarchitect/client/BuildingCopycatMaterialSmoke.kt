package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen

/** Regression for the table's COPYCAT node material row, using its actual native inspector click. */
internal object BuildingCopycatMaterialSmoke {
    private var ticks=0
    private lateinit var screen:BuildingScreen
    private lateinit var original:Building
    private lateinit var edited:Building
    private var libraryJson=""
    private val source="wall_copycat"
    private val form=Form(id="building_material_fixture",groups=listOf(MaterialGroup(),MaterialGroup("cap","Cap","trim",1),MaterialGroup("alias","Other wall area","surface",2)),
        parts=listOf(Part("copycats:copycat_block"),Part("copycats:copycat_slab",top=true,group="cap"),Part("copycats:copycat_byte",group="alias")))
    private val mc get()=Minecraft.getInstance()
    private fun picker()=mc.screen as? MaterialSelectionScreen ?: error("The Copycat node material row must open the 3D material picker directly")
    private fun click(target:Screen,point:Pair<Double,Double>) {
        check(point.first>=0 && point.first<target.width && point.second>=0 && point.second<target.height)
        check(target.mouseClicked(point.first,point.second,0));target.mouseReleased(point.first,point.second,0)
    }
    private fun action(target:Screen,key:String) {
        val button=target.children().filterIsInstance<Button>().single { it.visible && it.message.string==ui(key) }
        check(button.active);click(target,button.x+button.width/2.0 to button.y+button.height/2.0)
    }
    private fun history()=listOf("buildingUndo","buildingRedo").map { field ->
        (BuildingScreen::class.java.getDeclaredField(field).apply { isAccessible=true }.get(screen) as ArrayDeque<*>).size
    }
    private fun open(groupIndex:Int=0) {
        check(mc.screen===screen)
        val inspector=checkNotNull(screen.buildingGraphCanvas)
        check(inspector.visible)
        check(screen.buildingGraphView.selected==source)
        var point=inspector.propertyPoint(source,44+groupIndex*22)
        val lower=inspector.y+44.0;val upper=inspector.y+inspector.height-12.0
        if(point.second !in lower..upper) {
            val target=point.second.coerceIn(lower,upper)
            check(screen.mouseScrolled(inspector.x+inspector.width/2.0,inspector.y+inspector.height-8.0,0.0,(target-point.second)/25.0))
            point=inspector.propertyPoint(source,44+groupIndex*22)
        }
        check(inspector.isMouseOver(point.first,point.second) && point.second>=inspector.y+35) { "The real source material row must be visible below the fixed inspector header" }
        click(screen,point);picker()
        check(!inspector.hasOpenMenu()) { "The old material dropdown must not open" }
        bounds()
    }
    private fun choose(id:String) {
        val dialog=picker();dialog.children().filterIsInstance<EditBox>().single().value=id
        click(dialog,checkNotNull(dialog.slotPoint(id)))
    }
    private fun group(index:Int) {
        val dialog=picker();val button=dialog.children().filterIsInstance<EditorButton>().single { it.dropdown }
        click(dialog,button.x+button.width/2.0 to button.y+button.height/2.0)
        check(dialog.keyPressed(268,0,0));repeat(index) { check(dialog.keyPressed(264,0,0)) };check(dialog.keyPressed(257,0,0))
    }
    private fun bounds() {
        val dialog=picker()
        dialog.children().filterIsInstance<AbstractWidget>().filter { it.visible }.forEach { widget ->
            check(widget.x>=0 && widget.y>=0 && widget.x+widget.width<=dialog.width && widget.y+widget.height<=dialog.height)
        }
    }
    private fun verifyPixels(name:String) {
        val dialog=picker();bounds()
        Screenshot.takeScreenshot(mc.mainRenderTarget).use { pixels ->
            val sx=pixels.width.toDouble()/dialog.width;val sy=pixels.height.toDouble()/dialog.height
            for(viewport in listOf(dialog.blockViewport(),dialog.applicationViewport())) {
                val (x,y,w,h)=viewport;var copper=0
                for(py in ((y+2)*sy).toInt() until ((y+h-2)*sy).toInt()) for(px in ((x+2)*sx).toInt() until ((x+w-2)*sx).toInt()) {
                    val pixel=pixels.getPixelRGBA(px,py);val r=pixel and 255;val g=pixel ushr 8 and 255;val b=pixel ushr 16 and 255
                    if(r>75 && r>g*1.25 && g>b*1.1) copper++
                }
                check(copper>=15) { "The node's block and application preview must show the selected copper: $viewport, $copper" }
            }
        }
        Screenshot.grab(mc.gameDirectory,"architect-copycat-node-material-$name.png",mc.mainRenderTarget) { println(it.string) }
    }
    private fun surfaces()=mapOf("body" to "minecraft:copper_block","cap" to "minecraft:glass","alias" to "minecraft:gold_block")
    private fun verifyBuilding() {
        check(screen.building.palette==original.palette)
        check(screen.building.graph!!.nodes.single { it.id==source }.surfaces==surfaces())
        val evaluation=BuildingGraphCompiler.Evaluation(screen.building.graph!!,screen.building.palette,ClientState.snapshot.library.forms,ClientState.snapshot.library.modules)
        check(evaluation.preview(source).placements.single().surfaces==surfaces())
        check(evaluation.preview("shared_wall_copycat").placements.single().surfaces==mapOf("body" to original.palette.getValue("wall"),"cap" to original.palette.getValue("trim"),"alias" to original.palette.getValue("wall")))
        check(evaluation.preview("wall").placements.all { it.surfaces==surfaces() })
        check(Json.decode<Building>(Json.encode(screen.building))==screen.building)
        check(Json.encode(ClientState.snapshot.library)==libraryJson) { "Changing building materials must preserve the source library" }
    }
    fun tick():Boolean {
        ticks++
        when(ticks) {
            1 -> {
                mc.options.guiScale().set(1);mc.resizeDisplay()
                ClientState.snapshot.library.forms["${form.id}@1"]=form;libraryJson=Json.encode(ClientState.snapshot.library)
                val graph=BuildingGraph.assembledStarter().let { initial -> initial.copy(nodes=initial.nodes.map { node ->
                    if(node.id==source) node.copy(form="${form.id}@1",rotationAxis=RotationAxis.X,quarterTurns=1) else node
                }+BuildingNode("shared_wall_copycat",BuildingKind.COPYCAT,form="${form.id}@1")) }
                original=Building(id="building_material_smoke",schema=2,graph=graph)
                screen=BuildingScreen().apply { building=original;selectBuildingNode(source) }
                mc.setScreen(screen);screen.openBuildingGraph();action(screen,"printer_inspector")
                check(screen.tab==0 && screen.graphInspector)
            }
            7 -> Screenshot.grab(mc.gameDirectory,"architect-copycat-node-groups-normal.png",mc.mainRenderTarget) { println(it.string) }
            8 -> {
                open();choose("minecraft:copper_block")
                check(picker().draftSurfaces()["alias"]==original.palette.getValue("wall")) { "Groups with the same inherited role must remain individually editable" }
                group(2);choose("minecraft:gold_block")
                group(1);choose("minecraft:glass");group(0)
                action(picker(),"material_whole_copycat")
                val expected=Geometry.previewParts(Geometry.validate(form).parts.map { PartRotations.rotate(it,RotationAxis.X,1)!! },form.groups,"body")
                check(Geometry.solid(picker().previewParts().getOrThrow()).key()==Geometry.solid(expected).key())
                check(screen.building==original && history()==listOf(0,0))
            }
            15 -> {
                verifyPixels("normal");check(picker().keyPressed(256,0,0))
                check(mc.screen===screen && screen.building==original && history()==listOf(0,0))
            }
            20 -> {
                open();choose("minecraft:copper_block");group(1);choose("minecraft:glass");group(2);choose("minecraft:gold_block");action(picker(),"apply")
                check(mc.screen===screen && history()==listOf(1,0));verifyBuilding();edited=screen.building
                action(screen,"undo");check(screen.building==original)
                action(screen,"redo");check(screen.building==edited)
                open();action(picker(),"apply");check(history()==listOf(1,0))
                mc.options.guiScale().set(2);mc.resizeDisplay()
            }
            27 -> Screenshot.grab(mc.gameDirectory,"architect-copycat-node-groups-compact.png",mc.mainRenderTarget) { println(it.string) }
            28 -> {
                open(2);check(picker().draftSurfaces()["alias"]=="minecraft:gold_block");choose("minecraft:diamond_block")
                check(picker().draftSurfaces()["body"]=="minecraft:copper_block")
                choose("minecraft:gold_block");group(0);action(picker(),"material_whole_copycat")
                check(screen.building==edited && history()==listOf(1,0))
            }
            35 -> {
                verifyPixels("compact");action(picker(),"cancel");check(screen.building==edited && history()==listOf(1,0))
                action(screen,"control_panel");check(screen.tab==7)
            }
            38 -> {
                open(1);choose("minecraft:bricks");action(picker(),"apply")
                check(screen.building.palette==edited.palette)
                check(screen.building.graph!!.nodes.single { it.id==source }.surfaces==surfaces()+("cap" to "minecraft:bricks"))
                action(screen,"undo");check(screen.building==edited)
                action(screen,"redo");check(screen.building.graph!!.nodes.single { it.id==source }.surfaces.orEmpty()["cap"]=="minecraft:bricks")
            }
            42 -> {
                println("ARCHITECT COPYCAT NODE MATERIAL UI SMOKE PASSED: actual table graph inspector and definition-panel material row open the 3D picker directly; normal/compact block and oriented group pixels, independent nodes and groups with shared roles, downstream wall placements, cancellation, atomic multi-group Apply, no-op Apply, Undo/Redo, JSON persistence and unchanged source library verified")
                mc.stop()
            }
            else -> return false
        }
        return true
    }
}
