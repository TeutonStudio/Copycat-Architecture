package de.teutonstudio.copycatarchitect.client

import com.google.gson.JsonParser
import com.mojang.math.Axis
import de.teutonstudio.copycatarchitect.Architect
import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.integration.CreativeCatalog
import de.teutonstudio.copycatarchitect.server.*
import net.minecraft.client.Minecraft
import net.minecraft.client.MouseHandler
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.client.event.RenderFrameEvent
import net.neoforged.neoforge.common.NeoForge
import net.createmod.ponder.foundation.PonderIndex
import net.createmod.ponder.foundation.PonderScene
import net.createmod.ponder.foundation.registration.PonderLocalization
import net.createmod.ponder.foundation.registration.PonderSceneRegistry
import net.minecraft.resources.ResourceLocation
import org.lwjgl.glfw.GLFW
import com.lowdragmc.lowdraglib2.gui.ui.utils.KeyState

/** Opt-in acceptance that needs loaded client resources, but no world or network connection. */
object PrinterClientSmoke {
    private var ticks=0
    private var failed=false
    private lateinit var shape:ShapeScreen
    private lateinit var view:PrinterView
    private lateinit var model:PrinterModelScreen
    private lateinit var storage:PrinterStorageMenu
    private var original=""
    private var peekCapture=0L
    private val peekPixelChecks=mutableSetOf<String>()
    private var peekPixelFailure:Throwable?=null
    private const val peekBackdrop=0xff315d31.toInt()
    private fun peekScene()=when(ticks) {
        in 40..55->"graph-inspector"
        in 112..120->"print-inventory"
        in 142..150->"history"
        else->null
    }
    private fun beforeFrame(event:RenderFrameEvent.Pre) {
        if(peekScene()==null) return
        val mc=Minecraft.getInstance()
        // Feed held test input before GameRenderer samples its pointer for this frame.
        // Tick-only positioning can be overwritten by a native callback between frames.
        if(ticks in 40..48 || ticks in 112..116 || ticks in 142..146) {
            val eye=shape.children().filterIsInstance<PrinterEyeButton>().single()
            pointer(mc,eye.x+12.0,eye.y+9.0)
        } else pointer(mc,shape.width-30.0,shape.height-12.0)
    }
    private fun beforeScreen(event:ScreenEvent.Render.Pre) {
        if(peekScene()!=null && event.screen===Minecraft.getInstance().screen)
            event.guiGraphics.fill(0,0,shape.width,shape.height,peekBackdrop)
    }
    private fun afterScreen(event:ScreenEvent.Render.Post) {
        val scene=peekScene() ?: return
        if(!WorldPeek.active || scene in peekPixelChecks || event.screen!==Minecraft.getInstance().screen) return
        try {
            event.guiGraphics.flush()
            val eye=shape.children().filterIsInstance<PrinterEyeButton>().single()
            Screenshot.takeScreenshot(Minecraft.getInstance().mainRenderTarget).use { pixels ->
                val scaleX=pixels.width.toDouble()/shape.width;val scaleY=pixels.height.toDouble()/shape.height
                var eyePixels=0
                for(y in 0 until pixels.height) for(x in 0 until pixels.width) {
                    val color=pixels.getPixelRGBA(x,y) and 0xffffff
                    val overEye=x>=(eye.x-1)*scaleX && x<(eye.x+eye.width+1)*scaleX &&
                        y>=(eye.y-1)*scaleY && y<(eye.y+eye.height+1)*scaleY
                    if(overEye) { if(color!=(peekBackdrop and 0xffffff)) eyePixels++ }
                    else check(color==(peekBackdrop and 0xffffff)) {
                        "World peek must leave every background pixel visible outside the eye ($scene at $x,$y: ${color.toString(16)})"
                    }
                }
                check(eyePixels>0) { "The eye must remain visible while revealing $scene" }
            }
            peekPixelChecks+=scene
        } catch(e:Throwable) { peekPixelFailure=e }
    }
    private var contextPosition=0 to 0
    private var contextJson=""
    private val mouseMove by lazy {
        MouseHandler::class.java.getDeclaredMethod("onMove",java.lang.Long.TYPE,java.lang.Double.TYPE,java.lang.Double.TYPE).apply { isAccessible=true }
    }
    private fun fixture()=Geometry.validate(Form(id="printer_client",name="Zinc and gold print",
        groups=listOf(MaterialGroup(),MaterialGroup("cap","Gold","frame",1)),
        graph=NodeGraph(nodes=listOf(
            GraphNode("slab",part=Part("copycats:copycat_slab"),x=20,y=20),
            GraphNode("angle",NodeKind.ROTATION,x=20,y=180,rotationAxis=RotationAxis.Z,quarterTurns=1),
            GraphNode("rotate",NodeKind.ROTATE,listOf("slab","angle"),x=250,y=150),
            GraphNode("xor",NodeKind.SYMMETRIC_DIFFERENCE,listOf("slab","rotate"),inputGroups=listOf("body","cap"),x=480,y=20),
            GraphNode("result",NodeKind.OUTPUT,listOf("xor"),x=710,y=20)))))
    private fun screenshot(mc:Minecraft,name:String) {
        Screenshot.grab(mc.gameDirectory,"architect-printer-$name.png",mc.mainRenderTarget) { println(it.string) }
    }
    private fun button(screen:Screen,key:String)=screen.children().filterIsInstance<Button>().single { it !is PrinterEdgeTab && it.message.string==ui(key) }
    private fun edge(key:String)=shape.children().filterIsInstance<PrinterEdgeTab>().single { it.message.string==ui(key) }
    private fun nativeClick(id:String,shift:Boolean=false) {
        val point=checkNotNull(shape.kilaGraph?.nodeHeaderPoint(id))
        val held=KeyState.Source { it==GLFW.GLFW_KEY_LEFT_SHIFT && shift }
        KeyState.setSource(held)
        try { shape.mouseClicked(point.first,point.second,0);shape.mouseReleased(point.first,point.second,0) }
        finally { KeyState.clearSource(held) }
    }
    private fun emptyClick() {
        val editor=checkNotNull(shape.kilaGraph)
        shape.mouseClicked(editor.x+editor.width-4.0,editor.y+editor.height-4.0,0)
        shape.mouseReleased(editor.x+editor.width-4.0,editor.y+editor.height-4.0,0)
    }
    private fun click(screen:Screen,button:Button) {
        check(button.x>=0 && button.y>=0 && button.x+button.width<=screen.width && button.y+button.height<=screen.height)
        check(screen.mouseClicked(button.x+button.width/2.0,button.y+button.height/2.0,0))
        screen.mouseReleased(button.x+button.width/2.0,button.y+button.height/2.0,0)
    }
    private fun pointer(mc:Minecraft,x:Double,y:Double) {
        val screen=checkNotNull(mc.screen)
        screen.mouseMoved(x,y)
        val rawX=x*mc.window.screenWidth/screen.width;val rawY=y*mc.window.screenHeight/screen.height
        GLFW.glfwSetCursorPos(mc.window.window,rawX,rawY)
        // GLFW may omit cursor callbacks for background windows. Exercise Minecraft's actual
        // callback as well: GameRenderer reads MouseHandler coordinates for render-time hover.
        mouseMove.invoke(mc.mouseHandler,mc.window.window,rawX,rawY)
        check(kotlin.math.abs(mc.mouseHandler.xpos()-rawX)<0.001 && kotlin.math.abs(mc.mouseHandler.ypos()-rawY)<0.001) {
            "The real mouse callback must update render-time pointer coordinates"
        }
    }
    private fun verifyGraph() {
        check(shape.graphZincText()==ui("total_cost",128) && shape.graphZincText().contains("mB")) {
            "The graph must permanently show the final occupied-volume zinc cost in mB, including compact layouts"
        }
        val editor=checkNotNull(shape.kilaGraph) { "The printer must use the real native graph editor" }
        val geometry=checkNotNull(editor.outputPortType("slab"));val rotation=checkNotNull(editor.outputPortType("angle"))
        check(geometry.friendlyName==ui("port_copycat") && rotation.friendlyName==ui("port_rotation"))
        check(geometry==editor.inputPortType("rotate",0) && rotation==editor.inputPortType("rotate",1) && geometry!=rotation)
        check(editor.rotationButtonPoint("slab",1)==null) { "A Copycat source must not expose embedded rotation controls" }
        checkNotNull(shape.form.graph).nodes.forEach { node ->
            check(editor.hasNodePreview(node.id)) { "Every native node needs its own 3D preview control: ${node.id}" }
            val result=node.kind==NodeKind.OUTPUT
            check(editor.isNodePreviewExpanded(node.id)==result) { "Only the result preview should expand by default: ${node.id}" }
            check((editor.nodePreviewPoint(node.id)!=null)==result) { "Only expanded previews should occupy graph layout space: ${node.id}" }
        }
        val slab=checkNotNull(editor.nodePreviewValue("slab"));val rotated=checkNotNull(editor.nodePreviewValue("rotate"));val result=checkNotNull(editor.nodePreviewValue("result"))
        check(slab.error.isEmpty() && rotated.error.isEmpty() && result.error.isEmpty())
        check(Geometry.solid(slab.parts).key()!=Geometry.solid(rotated.parts).key() && Geometry.solid(rotated.parts).key()!=Geometry.solid(result.parts).key()) {
            "Native previews must contain each node's intermediate geometry rather than the final result"
        }
        check(editor.nodePreviewValue("angle")?.rotation==RotationAxis.Z && editor.nodePreviewValue("angle")?.turns==1)
        check(shape.printerPrintTabVisible==shape.printerSelection.isEmpty())
        check(shape.children().filterIsInstance<Button>().none { it.message.string==ui("insert_node") }) { "The printer inserts nodes through its context menu" }
        check(Json.encode(shape.form)==original && view.slots.size==7)
    }
    private fun navigation() {
        val before=Json.encode(shape.form)
        val history=button(shape,"undo").active to button(shape,"redo").active
        repeat(3) { shape.openGraph(true);shape.openGraph(false) }
        check(Json.encode(shape.form)==before && history==(button(shape,"undo").active to button(shape,"redo").active)) {
            "Changing printer presentations must preserve the graph and document history"
        }
    }
    private fun draftHistory() {
        val before=shape.printerCommand("printerPrint")
        val changed=view.copy(form=view.form.copy(name="Restored printer settings"),
            surfaces=mapOf("body" to "minecraft:oak_planks","cap" to "minecraft:glass"),turn=3)
        shape.loadPrinterDraft(changed)
        val after=shape.printerCommand("printerPrint")
        check(Json.decode<Form>(after.payload).name==changed.form.name && after.surfaces==changed.surfaces && after.turn==3)
        click(shape,button(shape,"undo"))
        check(shape.printerCommand("printerPrint")==before) { "Undo must restore the complete printer draft, coatings and placement turn" }
        click(shape,button(shape,"redo"))
        check(shape.printerCommand("printerPrint")==after) { "Redo must restore the complete imported printer draft" }
        click(shape,button(shape,"undo"))
        check(shape.printerCommand("printerPrint")==before && Json.encode(shape.form)==original)
    }
    private fun cards() {
        val before=shape.printerCommand("printerPrint")
        val history=button(shape,"undo").active to button(shape,"redo").active
        val selection=shape.printerSelection
        val navigation=Triple(shape.kilaGraphState.offsetX,shape.kilaGraphState.offsetY,shape.kilaGraphState.zoom)
        val firstName=shape.form.name
        val second=shape.form.copy(id="second_card",name="Second card")
        shape.openPrinterCard(second,PrintSettings(before.surfaces,2))
        check(shape.printerCardCount==2 && shape.printerSelection.isEmpty())
        val graph=checkNotNull(shape.form.graph);val node=graph.nodes.first()
        shape.commitShapeGraph(graph.replace(node.copy(x=node.x+17)))
        check(button(shape,"undo").active)
        click(shape,shape.children().filterIsInstance<PrinterEdgeTab>().single { it.message.string==firstName })
        check(shape.printerCommand("printerPrint")==before && history==(button(shape,"undo").active to button(shape,"redo").active))
        check(shape.printerSelection==selection && navigation==Triple(shape.kilaGraphState.offsetX,shape.kilaGraphState.offsetY,shape.kilaGraphState.zoom))
        click(shape,shape.children().filterIsInstance<PrinterEdgeTab>().single { it.message.string==second.name })
        click(shape,button(shape,"undo"));check(shape.form==second && shape.printerCommand("printerPrint").turn==2)
        shape.selectShapeNode("");shape.openGraph(true);click(shape,button(shape,"printer_close_card"))
        check(shape.printerCardCount==1 && shape.printerCommand("printerPrint")==before)
        shape.openGraph(false)
    }
    private fun catalogAndPonder(mc:Minecraft) {
        val tab=CreativeCatalog.TAB.get()
        check(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab)==CreativeCatalog.ID)
        val sections=CreativeCatalog.sections()
        check(sections.keys.toList()==CreativeCatalogSection.entries)
        val procedural=sections.getValue(CreativeCatalogSection.PROCEDURAL)
        check(procedural.size==5 && procedural[0].`is`(Architect.GUN.get()) && procedural[4].`is`(Architect.TABLE_ITEM.get()))
        val native=sections.getValue(CreativeCatalogSection.BASIC)
        check(Adapters.all.keys.all { id -> native.any { BuiltInRegistries.ITEM.getKey(it.item).toString()==id } }) {
            "The grouped creative tab must retain every supported native Copycat shape"
        }
        val special=sections.getValue(CreativeCatalogSection.SPECIAL).map { BuiltInRegistries.ITEM.getKey(it.item) }.toSet()
        for(path in listOf("copycat_fluid_pipe","copycat_shaft","copycat_cogwheel","copycat_large_cogwheel",
            "copycat_door","copycat_iron_door","copycat_sliding_door","copycat_folding_door","copycat_trapdoor","copycat_iron_trapdoor",
            "copycat_fence_gate","copycat_stone_button","copycat_wooden_button","copycat_stone_pressure_plate","copycat_wooden_pressure_plate",
            "copycat_light_weighted_pressure_plate","copycat_heavy_weighted_pressure_plate","copycat_ladder","copycat_ghost_block")) {
            val id=ResourceLocation.fromNamespaceAndPath("copycats",path)
            check(BuiltInRegistries.ITEM.containsKey(id) && id in special) { "The functional Copycat $id must be registered in Special" }
        }
        val optionalFunctional=listOf("copycats:copycat_glass_fluid_pipe","aeroworks:copycat_control_desk","aeroworks:copycat_control_stand",
            "cc_aeroworks:computer_copycat_control_desk","cc_aeroworks:advanced_computer_copycat_control_desk",
            "cc_aeroworks:computer_copycat_control_stand","cc_aeroworks:advanced_computer_copycat_control_stand")
        for(id in optionalFunctional.map(ResourceLocation::parse).filter(BuiltInRegistries.ITEM::containsKey))
            check(id in special) { "The installed functional Copycat $id must appear in Special" }
        check((native+sections.getValue(CreativeCatalogSection.FREQUENT)).none { BuiltInRegistries.ITEM.getKey(it.item) in special }) {
            "Special Copycats must not also appear in Basic or Frequent"
        }
        for((language,label) in mapOf("de_de" to "Speziell","en_us" to "Special")) {
            val resource=mc.resourceManager.getResource(ResourceLocation.fromNamespaceAndPath(Architect.ID,"lang/$language.json")).orElseThrow()
            val translated=resource.openAsReader().use { JsonParser.parseReader(it).asJsonObject.get("creative_section.copycat_architect.special").asString }
            check(translated==label) { "The Special creative section must be translated as $label in $language" }
        }
        val saved=sections.getValue(CreativeCatalogSection.SAVED)
        check(saved.isNotEmpty() && saved.all { it.`is`(Architect.COMPOSITE_ITEM.get()) && de.teutonstudio.copycatarchitect.server.JsonNbt.get(PrinterStack.tag(it),"form_definition").isNotEmpty() })
        val sample=saved.first()
        check(mc.itemRenderer.getModel(sample,null,null,0).isCustomRenderer && IClientItemExtensions.of(sample).customRenderer is CompositeFormItemRenderer) {
            "Saved creative and recipe-viewer entries must use their dynamic form renderer"
        }
        CreativeCatalogClient.arrange(tab)
        val arranged=tab.displayItems.toList()
        check(arranged.size%9==0 && arranged.take(9).all { it.isEmpty } && arranged[9].`is`(Architect.GUN.get()))
        check(arranged.chunked(9).count { row -> row.all { it.isEmpty } }==CreativeCatalogSection.entries.size) {
            "Each creative section must have its own non-collectible empty header row"
        }
        check(saved.all { item -> arranged.any { ItemStack.isSameItemSameComponents(it,item) } })
        check(PonderIndex.streamPlugins().anyMatch { it is ArchitectPonderPlugin }) { "The Architect Ponder plugin must be registered" }
        val scenes=PonderIndex.getSceneAccess()
        for(item in listOf(Architect.GUN.get(),CartridgeRegistry.EMPTY.get(),Architect.CARTRIDGE.get(),CartridgeRegistry.CREATIVE.get(),Architect.TABLE_ITEM.get()))
            check(scenes.doScenesExistForId(BuiltInRegistries.ITEM.getKey(item))) { "Missing registered Ponder scene for ${BuiltInRegistries.ITEM.getKey(item)}" }
        val tutorials=listOf(Architect.GUN.get() to "printer",Architect.CARTRIDGE.get() to "cartridges",Architect.TABLE_ITEM.get() to "table")
        for((item,name) in tutorials) {
            val id=BuiltInRegistries.ITEM.getKey(item)
            val entry=scenes.registeredEntries.first { it.key==id && it.value.namespace==Architect.ID }.value
            val schematic=entry.schematicLocation
            check(mc.resourceManager.getResource(ResourceLocation.fromNamespaceAndPath(schematic.namespace,"ponder/${schematic.path}.nbt")).isPresent) { "Ponder schematic is missing for $name" }
            // Ponder explicitly supports composition with a null world for scene and text validation.
            // Rendering and executing its world instructions require an actual client level.
            val composed=PonderSceneRegistry.compileScene(PonderLocalization(),entry,null)
            // In pinned Ponder 1.0.82 the schedule has no public accessor. Duration is computed
            // only by begin()/onScheduled, so it is still zero after worldless composition.
            val schedule=PonderScene::class.java.getDeclaredField("schedule").apply { isAccessible=true }.get(composed) as List<*>
            val expected=ResourceLocation.fromNamespaceAndPath(Architect.ID,name)
            val diagnostic="id=${composed.id}, expected=$expected, instructions=${schedule.size}, durationBeforeBegin=${composed.totalTime}, title=${composed.title}"
            check(composed.id==expected && schedule.size>=16 && composed.title.isNotBlank()) {
                "The complete $name storyboard must compose its instruction plan: $diagnostic"
            }
            println("ARCHITECT PONDER COMPOSED $name: $diagnostic")
        }
        println("ARCHITECT CATALOG CLIENT: grouped tab, complete basic shapes, functional Copycats in Special, ${CreativeCatalogSection.entries.size} header rows, saved-form icons, Ponder mappings and all three actual storyboard compositions verified; Ponder world rendering is outside this worldless run")
    }
    fun tick():Boolean {
        if(failed) return false
        val mc=Minecraft.getInstance()
        if(ticks==0 && (mc.screen==null || mc.overlay!=null)) return false
        ticks++;var acted=true
        try {
            peekPixelFailure?.let { throw it }
            // Keep the held hover stable across native cursor callbacks between frames.
            if(ticks in 40..48 || ticks in 112..116 || ticks in 142..146) {
                val eye=shape.children().filterIsInstance<PrinterEyeButton>().single()
                pointer(mc,eye.x+12.0,eye.y+9.0)
            }
            when(ticks) {
                10 -> {
                    NeoForge.EVENT_BUS.addListener(::beforeScreen)
                    NeoForge.EVENT_BUS.addListener(::afterScreen)
                    NeoForge.EVENT_BUS.addListener(::beforeFrame)
                    check(mc.player==null && mc.level==null) { "Printer UI acceptance must start without a world" }
                    mc.options.guiScale().set(1);mc.resizeDisplay()
                    val form=fixture();val surfaces=mapOf("body" to "","cap" to "minecraft:gold_block")
                    val slots=listOf(CartridgeView("copycat_architect:cartridge",192),CartridgeView("copycat_architect:cartridge",256),
                        CartridgeView("copycat_architect:empty_cartridge",0),CartridgeView(),CartridgeView("copycat_architect:creative_cartridge",Int.MAX_VALUE,true),
                        CartridgeView("copycat_architect:cartridge",128),CartridgeView("copycat_architect:empty_cartridge",0))
                    view=PrinterView(P(1,1,1),"printer-client-session",form,surfaces,0,slots,true)
                    val library=Library().apply { seed();history+=PrintRecord(id="printer-preview",form=form,surfaces=surfaces,time=1_700_000_000_000) }
                    ClientState.snapshot=Snapshot(library=library,printer=view)
                    shape=ShapeScreen(printerSession=view).apply { graphMode=true;graphInspector=false;selectShapeNode("xor") }
                    original=Json.encode(shape.form);mc.setScreen(shape);pointer(mc,shape.width-30.0,shape.height-12.0)
                }
                25 -> { navigation();draftHistory();checkNotNull(shape.kilaGraph).fit();verifyGraph();catalogAndPonder(mc) }
                27 -> {
                    val command=shape.printerCommand("printerPrint")
                    check(command.surfaces["cap"]=="minecraft:gold_block") { "An imported non-preset coating must remain assigned" }
                    click(shape,edge("printer_open_card"));click(shape,button(shape,"groups"))
                    check(shape.graphMode && shape.graphInspector && shape.kilaGraph!=null && shape.printerCommand("printerPrint")==command)
                    check(shape.children().filterIsInstance<Button>().none { it.message.string==ui("equip") })
                }
                32 -> screenshot(mc,"groups-normal")
                33 -> { click(shape,edge("printer_inspector"));check(shape.graphMode && !shape.graphInspector);verifyGraph() }
                35 -> {
                    check(PrinterDisplayCapture.isEditorActive(view.session) && PrinterDisplayCapture.width in 1..512 && PrinterDisplayCapture.height in 1..512) {
                        "The actual printer editor must have a bounded GPU display capture"
                    }
                    check(!PrinterDisplayCapture.isEditorActive("another-printer-session"))
                    screenshot(mc,"graph-normal")
                }
                36 -> { nativeClick("slab");check(shape.printerSelection==setOf("slab") && !shape.graphInspector && !shape.printerPrintTabVisible) }
                37 -> { nativeClick("angle",true);check(shape.printerSelection==setOf("slab","angle") && !shape.graphInspector) { "Shift selection: ${shape.printerSelection}, inspector=${shape.graphInspector}" } }
                38 -> { click(shape,edge("printer_inspector"));check(shape.kilaGraph!=null && button(shape,"duplicate").active) }
                39 -> screenshot(mc,"multi-selection-normal")
                40 -> { val eye=shape.children().filterIsInstance<PrinterEyeButton>().single();pointer(mc,eye.x+12.0,eye.y+9.0) }
                45 -> {
                    check("graph-inspector" in peekPixelChecks) { "The hovered graph and inspector must reveal the background" }
                    check(WorldPeek.active) {
                        "Rendering the real hovered eye must reveal the world: pointer=${mc.mouseHandler.xpos()},${mc.mouseHandler.ypos()}, screen=${shape.width}x${shape.height}"
                    }
                    check(Json.encode(shape.form)==original);peekCapture=PrinterDisplayCapture.serial;screenshot(mc,"eye-world-peek")
                }
                48 -> check(PrinterDisplayCapture.serial==peekCapture) { "The hovered eye must freeze the display image before revealing the world" }
                50 -> pointer(mc,shape.width-30.0,shape.height-12.0)
                55 -> {
                    check(!WorldPeek.active && PrinterDisplayCapture.serial>peekCapture) { "Moving away from the eye must restore the editor and its GPU mirror" }
                }
                56 -> cards()
                57 -> {
                    shape.openGraph(true);check(shape.printerSelection.size==2)
                    click(shape,button(shape,"duplicate"))
                    check(shape.printerSelection.size==2 && shape.form.graph!!.nodes.size==view.form.graph!!.nodes.size+2)
                }
                58 -> { click(shape,button(shape,"undo"));check(Json.encode(shape.form)==original);shape.openGraph(false) }
                60 -> { mc.options.guiScale().set(2);mc.resizeDisplay();shape.openGraph(false);pointer(mc,shape.width-20.0,shape.height-12.0) }
                70 -> { checkNotNull(shape.kilaGraph).fit();verifyGraph() }
                71 -> { emptyClick();check(shape.printerSelection.isEmpty() && shape.printerPrintTabVisible);click(shape,edge("printer_print"));check(!button(shape,"printer_print").active) }
                72 -> screenshot(mc,"print-compact")
                73 -> {
                    shape.openGraph(false)
                    val editor=checkNotNull(shape.kilaGraph);val x=editor.x+editor.width-8.0;val y=editor.y+editor.height-8.0
                    contextPosition=editor.graphPoint(x,y);contextJson=Json.encode(shape.form)
                    shape.mouseClicked(x,y,1);shape.mouseReleased(x,y,1);check(editor.hasOpenMenu())
                }
                75 -> { val editor=checkNotNull(shape.kilaGraph);val p=checkNotNull(editor.menuItemPoint(ui("add_node")));pointer(mc,p.first,p.second) }
                77 -> {
                    val editor=checkNotNull(shape.kilaGraph)
                    val p=checkNotNull(editor.menuItemPoint(ui("copycat_group_basic"))) { "Hovering Add through the screen must open its node categories" }
                    pointer(mc,p.first,p.second)
                }
                78 -> screenshot(mc,"add-node-submenu-compact")
                79 -> {
                    val editor=checkNotNull(shape.kilaGraph);val first=BaseShapes.all.first();val p=checkNotNull(editor.menuItemPoint(ui(first.name)))
                    shape.mouseClicked(p.first,p.second,0);shape.mouseReleased(p.first,p.second,0)
                    val added=checkNotNull(shape.form.graph).nodes.single { n -> Json.decode<Form>(contextJson).graph!!.nodes.none { it.id==n.id } }
                    check((added.x to added.y)==contextPosition && !shape.graphInspector)
                }
                80 -> screenshot(mc,"graph-compact")
                82 -> { click(shape,button(shape,"undo"));check(Json.encode(shape.form)==contextJson) }
                84 -> {
                    nativeClick("slab");shape.openGraph(true)
                    val preview=checkNotNull(shape.graphCanvas).previewParts()
                    check(Geometry.solid(preview).key()==Geometry.solid(shape.shapeNodePreview("slab").parts).key())
                    check(Geometry.solid(preview).key()!=Geometry.solid(shape.shapeNodePreview("result").parts).key()) { "The selected-node inspector must show its own intermediate model" }
                }
                86 -> { val canvas=checkNotNull(shape.graphCanvas);shape.mouseScrolled(canvas.x+30.0,canvas.y+60.0,0.0,-20.0) }
                88 -> screenshot(mc,"node-preview-inspector-compact")
                89 -> emptyClick()
                90 -> shape.openGraph(true)
                100 -> { check(Json.encode(shape.form)==original);screenshot(mc,"inspector-compact") }
                102 -> {
                    check(!button(shape,"close").visible)
                    shape.mouseScrolled(shape.width-70.0,80.0,0.0,-2.0)
                    check(button(shape,"close").visible) { "Compact card actions must remain reachable by scrolling" }
                    shape.mouseScrolled(shape.width-70.0,80.0,0.0,2.0)
                }
                110 -> {
                    storage=PrinterStorageMenu(1,net.minecraft.world.entity.player.Inventory(null))
                    view.slots.forEachIndexed { index,entry ->
                        if(entry.item.isNotEmpty()) storage.getSlot(index).set(ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.item))).also {
                            if(!entry.creative && entry.units>0) PrinterStack.update(it) { tag -> tag.putInt("units",entry.units) }
                        })
                    }
                    storage.getSlot(7+10).set(Economy.cartridge(97).also { it.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,Component.literal("Mouse cartridge")) })
                    storage.getSlot(7+11).set(ItemStack(net.minecraft.world.item.Items.STONE,32))
                    shape.openGraph(false);click(shape,edge("printer_print"))
                    mc.setScreen(PrinterStorageScreen(shape,storage));WorldPeek.active=false
                    check((mc.screen as PrinterStorageScreen).menu===storage && (mc.screen as PrinterStorageScreen).workspace===shape && storage.slots.size==43)
                    check(shape.children().none { it is PrinterMemoryButton }) { "The old combined storage button must be removed" }
                }
                120 -> {
                    check("print-inventory" in peekPixelChecks && !WorldPeek.active)
                    check(ClientState.snapshot.printer?.slots?.size==7)
                    shape.mouseScrolled(shape.width-70.0,80.0,0.0,-4.0)
                    val inventory=checkNotNull(shape.printerInventoryPanel)
                    for(i in 0..42) { val p=inventory.slotPoint(i);check(inventory.slotAt(p.first,p.second)==i) { "Every compact cartridge/player slot must be visible and hittable: $i at $p" } }
                    check(inventory.slotAt(inventory.x+5.0,30.0)==-1) { "Clipped inventory rows must not consume toolbar clicks" }
                    check(PrinterDisplayCapture.isEditorActive(view.session)) { "The live menu host must retain the same printer display capture" }
                }
                130 -> screenshot(mc,"cartridges-print-compact")
                118,148 -> pointer(mc,shape.width-30.0,shape.height-12.0)
                135 -> {
                    val history=edge("printer_history");val open=edge("printer_open_card")
                    check(history.x==open.x && history.y==open.y+24) { "History must sit directly under Open card on the left rail" }
                    click(checkNotNull(mc.screen),history)
                    check(shape.printerHistoryVisible && shape.kilaGraph==null && shape.printerInventoryPanel==null && Json.encode(shape.form)==original)
                }
                136 -> {
                    storage.carried=storage.getSlot(7+10).remove(1)
                    pointer(mc,110.0,114.0)
                    check(storage.getSlot(7+10).item.isEmpty && storage.carried.hoverName.string=="Mouse cartridge")
                }
                138 -> screenshot(mc,"history-cursor-compact")
                139 -> { storage.getSlot(7+10).set(storage.carried);storage.carried=ItemStack.EMPTY }
                140 -> { mc.options.guiScale().set(1);mc.resizeDisplay() }
                150 -> {
                    check("history" in peekPixelChecks && !WorldPeek.active)
                    screenshot(mc,"history-normal")
                }
                152 -> {
                    val before=shape.printerCommand("printerPrint");val count=shape.printerCardCount
                    click(checkNotNull(mc.screen),button(shape,"printer_history_open"))
                    check(!shape.printerHistoryVisible && shape.printerCardCount==count+1 && shape.printerCommand("printerPrint")==before)
                    shape.openGraph(true);click(checkNotNull(mc.screen),button(shape,"printer_close_card"))
                    check(shape.printerCardCount==count && shape.printerCommand("printerPrint")==before)
                    click(checkNotNull(mc.screen),edge("printer_history"))
                    val previous=ClientState.snapshot
                    ClientState.snapshot=previous.copy(library=previous.library.copy(history=mutableListOf()))
                    shape.refreshPrinterLibrary();check(!button(shape,"printer_history_open").active && !button(shape,"ps_save_named").active)
                    ClientState.snapshot=previous;shape.refreshPrinterLibrary()
                    val record=previous.library.history.first()
                    ClientState.snapshot=previous.copy(library=previous.library.copy(history=(0..23).map { record.copy(id="history-$it",form=record.form.copy(name="Print $it")) }.toMutableList()))
                    shape.refreshPrinterLibrary()
                    val history=checkNotNull(shape.printerHistoryPanel);val selected=history.selection()?.id
                    checkNotNull(mc.screen).mouseClicked(history.x+20.0,history.y+history.height-6.0,0)
                    checkNotNull(mc.screen).mouseReleased(history.x+20.0,history.y+history.height-6.0,0)
                    check(shape.printerHistoryPanel?.selection()?.id==selected) { "Blank list space must not select an invisible history row" }
                    shape.mouseScrolled(history.x+20.0,history.y+40.0,0.0,-100.0)
                    val row=history.rowPoint(0)
                    checkNotNull(mc.screen).mouseClicked(row.first,row.second,0);checkNotNull(mc.screen).mouseReleased(row.first,row.second,0)
                    check(shape.printerHistoryPanel?.selection()?.id!=selected) { "Scrolling must reach older prints" }
                    ClientState.snapshot=previous;shape.refreshPrinterLibrary()
                }
                154 -> {
                    click(checkNotNull(mc.screen),edge("printer_open_card"));click(checkNotNull(mc.screen),edge("printer_print"))
                    check(shape.printerInventoryPanel!=null && !shape.printerHistoryVisible && Json.encode(shape.form)==original)
                }
                156 -> screenshot(mc,"cartridges-print-normal")
                157 -> shape.openGraph(false)
                158 -> {
                    val p=checkNotNull(shape.kilaGraph?.nodeHeaderPoint("slab"));val host=checkNotNull(mc.screen)
                    host.mouseClicked(p.first,p.second,0);host.mouseReleased(p.first,p.second,0)
                    check(shape.printerSelection==setOf("slab") && PrinterDisplayCapture.isEditorActive(view.session))
                }
                159 -> {
                    val direct=PrinterStorageScreen(null,storage);mc.setScreen(direct);direct.refresh(fromSnapshot=true)
                    shape=checkNotNull(direct.workspace)
                    check(shape.printerInventoryPanel!=null && shape.form==view.form) { "Direct access to a placed printer must enter the same Print tab" }
                }
                160 -> {
                    model=PrinterModelScreen(view);mc.setScreen(model)
                    check(!WorldPeek.active)
                    check(!PrinterDisplayCapture.isEditorActive(view.session) && PrinterDisplayCapture.hasCapture(view.session)) {
                        "Closed editors must use the world graph fallback; the visual fixture retains the last captured UI"
                    }
                }
                175 -> screenshot(mc,"frame-and-ui-display")
                185 -> model.front=true
                200 -> screenshot(mc,"frame-interior-preview")
                210 -> {
                    check(Json.encode(shape.form)==original)
                    println("ARCHITECT PRINTER CLIENT SMOKE PASSED: history tab below Open card, separate history/card loading, empty history, live menu-host preservation, 43 clipped inventory slots in Print, per-node 3D previews, multi-selection, independent histories, context insertion and GPU capture verified")
                    mc.stop()
                }
                else -> acted=false
            }
        } catch(e:Throwable) {
            failed=true;System.err.println("ARCHITECT PRINTER CLIENT SMOKE FAILED at tick $ticks: ${e.message}");e.printStackTrace()
            screenshot(mc,"failure-tick$ticks");throw IllegalStateException("Printer client smoke failed at tick $ticks",e)
        }
        return acted
    }

    /** Uses the registered block model and block entity renderer in a world-independent scene. */
    private class PrinterModelScreen(private val view:PrinterView):Screen(Component.literal("Block printer")) {
        var front=false
        private val entity=PrinterEntity(BlockPos.ZERO,Architect.PRINTER.get().defaultBlockState()).apply {
            preview=view.form;surfaces=view.surfaces;turn=view.turn;session=view.session
        }
        override fun render(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
            val mc=Minecraft.getInstance();val state=entity.blockState
            g.fill(0,0,width,height,0xff172322.toInt());g.fill(0,0,width,28,0xff354a49.toInt())
            g.drawCenteredString(font,Component.translatable("block.copycat_architect.block_printer"),width/2,10,0xfff0d49a.toInt())
            val renderer=checkNotNull(mc.blockEntityRenderDispatcher.getRenderer(entity)) { "The world printer renderer must be registered" }
            check(renderer is PrinterRenderer)
            val model=mc.blockRenderer.getBlockModel(state)
            check(model.getQuads(state,null,RandomSource.create(42)).size>24) { "Printer resources must contain the actual frame and display model" }
            val panelWidth=(width-48)/2;val panelHeight=height-82;val top=44
            for(index in 0..1) {
                val left=16+index*(panelWidth+16)
                g.fill(left,top,left+panelWidth,top+panelHeight,0xff203031.toInt());g.renderOutline(left,top,panelWidth,panelHeight,0xff526e69.toInt())
                g.enableScissor(left+1,top+1,left+panelWidth-1,top+panelHeight-1)
                g.flush();val pose=g.pose();pose.pushPose()
                pose.translate(left+panelWidth/2.0,top+panelHeight/2.0,200.0)
                val scale=minOf(panelWidth,panelHeight)*if(index==1) 0.76f else 0.58f
                pose.scale(scale,-scale,scale)
                pose.mulPose(Axis.XP.rotationDegrees(if(index==1) 90f else if(front) 8f else 28f))
                pose.mulPose(Axis.YP.rotationDegrees(if(index==1) 0f else if(front) 15f else 35f))
                pose.translate(-0.5,-0.5,-0.5)
                mc.blockRenderer.renderSingleBlock(state,pose,g.bufferSource(),15728880,OverlayTexture.NO_OVERLAY)
                renderer.render(entity,delta,pose,g.bufferSource(),15728880,OverlayTexture.NO_OVERLAY)
                check(PrinterDisplayCapture.drawRetained(view.session,pose,g.bufferSource())) {
                    "The isolated display model must show pixels captured from the actual printer editor"
                }
                g.flush();pose.popPose();g.disableScissor()
            }
            g.drawCenteredString(font,view.form.name,width/2,height-24,0xffd8d4b9.toInt())
            g.renderItem(CompositeFormItem.stack(view.form,view.surfaces,0),16,height-26)
            g.renderItem(CompositeFormItem.stack(view.form,emptyMap(),1),38,height-26)
        }
    }
}
