package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen

/** Real catalog/host events and rendered material pixels at both supported GUI scales. */
internal object MaterialSelectionSmoke {
    private var ticks=0
    private var failed=false
    private lateinit var host:ShapeScreen
    private val fixture=Form(id="material_ui_smoke",name="Two material areas",groups=listOf(
        MaterialGroup(),MaterialGroup("cap","Cap","frame",1),MaterialGroup("empty","Empty","surface",2)),
        parts=listOf(Part("copycats:copycat_slab"),Part("copycats:copycat_slab",top=true,group="cap")))
    private var original=""
    private val coating=mapOf("body" to "minecraft:copper_block","cap" to "minecraft:glass","empty" to "minecraft:stone")
    private val mc get()=Minecraft.getInstance()
    private fun picker()=mc.screen as? MaterialSelectionScreen ?: error("Material control must open the shared picker")
    private fun history()=listOf("undo","redo").map { key ->
        (ShapeScreen::class.java.getDeclaredField(key).apply { isAccessible=true }.get(host) as ArrayDeque<*>).size
    }
    private fun click(screen:Screen,point:Pair<Double,Double>) {
        check(point.first>=0 && point.first<screen.width && point.second>=0 && point.second<screen.height)
        check(screen.mouseClicked(point.first,point.second,0));screen.mouseReleased(point.first,point.second,0)
    }
    private fun button(screen:Screen,key:String)=screen.children().filterIsInstance<Button>().single { it.visible && it.message.string==ui(key) }
    private fun action(screen:Screen,key:String) { val b=button(screen,key);check(b.active);click(screen,b.x+b.width/2.0 to b.y+b.height/2.0) }
    private fun search(id:String) {
        val dialog=picker();dialog.children().filterIsInstance<EditBox>().single().value=id
    }
    private fun choose(id:String) {
        search(id);click(picker(),checkNotNull(picker().slotPoint(id)))
    }
    private fun group(index:Int) {
        val dialog=picker()
        val b=dialog.children().filterIsInstance<EditorButton>().single { it.dropdown }
        click(dialog,b.x+b.width/2.0 to b.y+b.height/2.0)
        check(dialog.keyPressed(268,0,0));repeat(index) { check(dialog.keyPressed(264,0,0)) };check(dialog.keyPressed(257,0,0))
    }
    private fun bounds() {
        val dialog=picker()
        dialog.children().filterIsInstance<AbstractWidget>().filter { it.visible }.forEach { widget ->
            check(widget.x>=0 && widget.y>=0 && widget.x+widget.width<=dialog.width && widget.y+widget.height<=dialog.height) {
                "Material control outside ${dialog.width}x${dialog.height}: ${widget.message.string}"
            }
        }
        listOf(dialog.blockViewport(),dialog.applicationViewport()).forEach { (x,y,w,h) ->
            check(x>=0 && y>=0 && w>0 && h>0 && x+w<=dialog.width && y+h<=dialog.height)
        }
    }
    private fun copperPixels() {
        val dialog=picker()
        Screenshot.takeScreenshot(mc.mainRenderTarget).use { pixels ->
            val sx=pixels.width.toDouble()/dialog.width;val sy=pixels.height.toDouble()/dialog.height
            for(viewport in listOf(dialog.blockViewport(),dialog.applicationViewport())) {
                val (x,y,w,h)=viewport;var copper=0
                for(py in ((y+2)*sy).toInt() until ((y+h-2)*sy).toInt()) for(px in ((x+2)*sx).toInt() until ((x+w-2)*sx).toInt()) {
                    val pixel=pixels.getPixelRGBA(px,py);val r=pixel and 255;val g=pixel ushr 8 and 255;val b=pixel ushr 16 and 255
                    if(r>75 && r>g*1.25 && g>b*1.1) copper++
                }
                check(copper>=15) { "Both actual 3D viewports must show copper pixels: $viewport, $copper" }
            }
        }
    }
    private fun screenshot(name:String) {
        bounds();Screenshot.grab(mc.gameDirectory,"architect-material-$name.png",mc.mainRenderTarget) { println(it.string) }
    }
    fun tick():Boolean {
        if(failed) return false
        if(ticks==0 && (mc.screen==null || mc.overlay!=null)) return false
        ticks++
        try {
            when(ticks) {
                1 -> {
                    ClientState.snapshot=Snapshot(library=Library().apply { seed() })
                    mc.options.guiScale().set(1);mc.resizeDisplay()
                    host=ShapeScreen(parent=BuildingScreen()).apply { form=fixture }
                    original=Json.encode(host.form);mc.setScreen(host)
                }
                8 -> {
                    action(host,"select_material");bounds()
                    val first=picker().visibleMaterials();val (x,y,_,_)=picker().blockViewport()
                    check(picker().mouseScrolled(x-20.0,y+40.0,0.0,-1.0))
                    check(picker().visibleMaterials()!=first)
                    search("minecraft:diamond_sword");check(picker().visibleMaterials().isEmpty())
                    search("minecraft:chest");check(picker().visibleMaterials().isEmpty())
                    choose("minecraft:copper_block")
                    check(host.printerMaterials.isEmpty() && history()==listOf(0,0))
                    action(picker(),"material_whole_copycat")
                    check(Geometry.solid(picker().previewParts().getOrThrow()).count()==2048)
                }
                15 -> { copperPixels();screenshot("normal");check(picker().keyPressed(256,0,0));check(mc.screen===host && host.printerMaterials.isEmpty() && history()==listOf(0,0)) }
                20 -> {
                    action(host,"select_material");choose("minecraft:copper_block");group(1);choose("minecraft:glass")
                    group(2);action(picker(),"material_whole_copycat");check(picker().previewParts().getOrThrow().isEmpty())
                    check(host.printerMaterials.isEmpty());action(picker(),"apply")
                    check(mc.screen===host && host.printerMaterials==coating && Json.encode(host.form)==original && history()==listOf(1,0))
                    action(host,"undo");check(host.printerMaterials.isEmpty())
                    action(host,"redo");check(host.printerMaterials==coating)
                    action(host,"select_material");action(picker(),"apply");check(history()==listOf(1,0))
                    mc.options.guiScale().set(2);mc.resizeDisplay()
                }
                28 -> {
                    action(host,"select_material");bounds()
                    search("minecraft:copper_block");action(picker(),"material_whole_copycat")
                    val before=picker().draftSurfaces();val parts=picker().previewParts().getOrThrow()
                    picker().resize(mc,mc.window.guiScaledWidth,mc.window.guiScaledHeight)
                    check(picker().draftSurfaces()==before && picker().previewParts().getOrThrow()==parts)
                    check(picker().visibleMaterials().contains("minecraft:copper_block"))
                    val (x,y,w,h)=picker().applicationViewport()
                    check(picker().mouseDragged(x+w/2.0,y+h/2.0,1,30.0,0.0));check(host.printerMaterials==coating)
                }
                35 -> {
                    copperPixels();screenshot("compact")
                    choose("minecraft:gold_block");check(host.printerMaterials==coating)
                    check(picker().keyPressed(256,0,0));check(host.printerMaterials==coating && history()==listOf(1,0))
                    host=ShapeScreen(printerSession=PrinterView(session="material-smoke",form=fixture,surfaces=coating))
                    mc.setScreen(host);host.openMaterialSelection("body")
                    search("no_such_material");check(picker().visibleMaterials().isEmpty());search("")
                    check(picker().keyPressed(264,0,0)) // Leave text editing before navigating the block grid.
                    check(picker().keyPressed(269,0,0));check(picker().slotPoint(picker().draftSurfaces().getValue("body"))!=null)
                    check(picker().keyPressed(268,0,0));check(picker().draftSurfaces().getValue("body").isEmpty())
                    group(1);choose("minecraft:stone");check(picker().keyPressed(257,0,0))
                    check(mc.screen===host && host.printerCommand("printerPrint").surfaces["body"]=="" && host.printerCommand("printerPrint").surfaces["cap"]=="minecraft:stone")
                    action(host,"undo");check(host.printerCommand("printerPrint").surfaces==coating)
                }
                38 -> {
                    val saved=fixture.copy(version=7)
                    val key="${saved.id}@7"
                    ClientState.snapshot.library.forms[key]=saved
                    ClientState.snapshot.library.printSettings[key]=PrintSettings(coating,1)
                    host=ShapeScreen(parent=BuildingScreen()).apply { form=fixture };mc.setScreen(host)
                    action(host,"load")
                    val library=mc.screen as? SavedCopycatSelectionScreen ?: error("Load must open the shared library")
                    library.children().filterIsInstance<EditBox>().single().value=key
                    click(library,checkNotNull(library.rowPoint(key)));check(library.keyPressed(257,0,0))
                    check(mc.screen===host && host.form==saved && host.printerMaterials==coating && host.printerCommand("saveForm").turn==1)
                    action(host,"undo");check(host.form==fixture && host.printerMaterials.isEmpty() && host.printerCommand("saveForm").turn==0)
                    action(host,"redo");check(host.form==saved && host.printerCommand("saveForm").surfaces==coating)
                }
                45 -> {
                    println("ARCHITECT MATERIAL SELECTION UI SMOKE PASSED: table material tab, valid block catalog, search/scroll/keyboard/resize, actual block and group copper pixels, isolated ownership, empty group, multi-group atomic Apply, unchanged geometry, cancellation, no-op Apply, Undo/Redo, compact controls, printer coatings/uncoated state and saved library appearance/rotation with atomic load Undo/Redo verified")
                }
                in 46..Int.MAX_VALUE -> return BuildingCopycatMaterialSmoke.tick()
                else -> return false
            }
        } catch(error:Throwable) {
            failed=true
            Screenshot.grab(mc.gameDirectory,"architect-material-failure-$ticks.png",mc.mainRenderTarget) { println(it.string) }
            throw IllegalStateException("Material selection UI smoke failed at tick $ticks",error)
        }
        return true
    }
}
