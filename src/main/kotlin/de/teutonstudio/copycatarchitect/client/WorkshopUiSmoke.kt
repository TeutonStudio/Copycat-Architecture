package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.BaseShapes
import de.teutonstudio.copycatarchitect.domain.Building
import de.teutonstudio.copycatarchitect.domain.Module
import de.teutonstudio.copycatarchitect.server.Economy
import de.teutonstudio.copycatarchitect.server.Json
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen

/** Screenshots come from real rendered frames; actions stay local and never submit a server command. */
internal object WorkshopUiSmoke {
    private var ticks=0
    private var failed=false
    fun tick():Boolean {
        if(failed) return false
        val mc=Minecraft.getInstance()
        if(ticks==0 && (mc.screen==null || mc.overlay!=null)) return false
        ticks++
        try {
            if(ticks==1) {
                ClientState.snapshot=de.teutonstudio.copycatarchitect.server.Snapshot(library=de.teutonstudio.copycatarchitect.server.Library().apply { seed() })
                begin(mc)
            } else if(tick(mc,ticks-1)) {
                println("ARCHITECT WORKSHOP UI SMOKE PASSED")
                mc.stop()
            }
        } catch(error:Throwable) {
            failed=true
            System.err.println("ARCHITECT WORKSHOP UI SMOKE FAILED at tick $ticks: ${error.message}")
            screenshot(mc,"failure-$ticks")
            throw IllegalStateException("Workshop UI smoke failed at tick $ticks",error)
        }
        return true
    }
    private var previous:Screen?=null
    private var previousScale=1
    private lateinit var building:BuildingScreen
    private var buildingJson=""

    fun begin(mc:Minecraft) {
        previous=mc.screen;previousScale=mc.options.guiScale().get()
        mc.options.guiScale().set(1);mc.resizeDisplay()
        building=BuildingScreen().apply {
            building=Building(id="workshop_ui_smoke",name="Workshop UI")
            tab=1;graphMode=false
        }
        buildingJson=Json.encode(building.building)
        mc.setScreen(building)
    }

    private fun buttons(screen:Screen)=screen.children().filterIsInstance<Button>()
    private fun click(screen:Screen,control:AbstractWidget) {
        check(control.visible && control.active) { "Workshop action must be available: ${control.message.string}" }
        check(control.x>=0 && control.y>=0 && control.x+control.width<=screen.width && control.y+control.height<=screen.height) {
            "Workshop control must fit the current GUI size: ${control.message.string}"
        }
        val x=control.x+control.width/2.0;val y=control.y+control.height/2.0
        check(screen.mouseClicked(x,y,0)) { "Workshop screen must receive the actual widget click" }
        screen.mouseReleased(x,y,0)
    }
    private fun tab(mc:Minecraft,index:Int,key:String) {
        check(mc.screen===building)
        click(building,buttons(building).filterIsInstance<PrinterEdgeTab>().single { it.message.string==ui(key) })
        check(building.tab==index && !building.graphMode)
    }
    private fun layout() {
        building.children().filterIsInstance<AbstractWidget>().filter { it.visible }.forEach { control ->
            check(control.x>=0 && control.y>=0 && control.x+control.width<=building.width && control.y+control.height<=building.height) {
                "Visible workshop widget extends beyond ${building.width}x${building.height}: ${control.message.string}"
            }
        }
        if(building.tab==1 && !building.module3d) {
            val l=PrinterWorkspaceLayout(building.width,building.height,topTabs=true)
            for(cell in listOf(de.teutonstudio.copycatarchitect.domain.P(0,0,0),de.teutonstudio.copycatarchitect.domain.P(2,3,0))) {
                val point=building.moduleCellPoint(cell)
                check(point.first in (l.x+166.0)..(l.x+l.innerWidth-12.0) && point.second in (l.graphY+52.0)..(l.bottom-9.0)) {
                    "Module cells and their hit targets must fit the preview frame at both scales"
                }
            }
        }
        check(Json.encode(building.building)==buildingJson) { "Screenshot navigation must preserve the building document" }
    }
    private fun number(mc:Minecraft) {
        tab(mc,1,"modules")
        // The first width button is the module's real range-checked numeric control.
        val module=ClientState.snapshot.library.modules["window@1"] ?: Module()
        click(building,buttons(building).single { it.message.string==ui("width",module.width) })
        val dialog=checkNotNull(mc.screen)
        check(dialog!==building)
        check(dialog.children().filterIsInstance<EditBox>().single().value.toInt() in 1..8)
        check(buttons(dialog).single { it.message.string==ui("save") }.active)
    }
    private fun invalidNumber(mc:Minecraft) {
        val dialog=checkNotNull(mc.screen)
        dialog.children().filterIsInstance<EditBox>().single().value="9"
        check(!buttons(dialog).single { it.message.string==ui("save") }.active)
        check(dialog.keyPressed(257,0,0))
        check(mc.screen===dialog) { "Out-of-range values must keep the numeric dialog open" }
    }
    private fun catalog(mc:Minecraft) {
        check(mc.screen===building)
        CopycatSelectionScreen.open(BaseShapes.all.first()) { error("Catalog screenshot must never apply a selection") }
        check(mc.screen is CopycatSelectionScreen)
    }
    private fun filterCatalog(mc:Minecraft,category:Int,query:String) {
        val screen=mc.screen as CopycatSelectionScreen
        val point=checkNotNull(screen.categoryPoint(category))
        check(screen.mouseClicked(point.first,point.second,0));screen.mouseReleased(point.first,point.second,0)
        screen.children().filterIsInstance<EditBox>().single().value=query
        check(screen.visibleShapes().isNotEmpty()) { "Catalog search and category must retain matching components" }
        check(screen.visibleShapes().all { it.source.contains(query,true) })
    }
    private fun screenshot(mc:Minecraft,name:String) {
        if(mc.screen===building) layout()
        Screenshot.grab(mc.gameDirectory,"architect-workshop-$name.png",mc.mainRenderTarget) { println(it.string) }
    }

    fun tick(mc:Minecraft,elapsed:Int):Boolean {
        when(elapsed) {
            5 -> screenshot(mc,"modules")
            10 -> tab(mc,5,"palette")
            15 -> screenshot(mc,"materials")
            20 -> click(building,buttons(building).single { it.message.string==Economy.item(building.building.palette.getValue("wall")).description.string })
            25 -> screenshot(mc,"material-menu")
            30 -> { building.keyPressed(256,0,0);tab(mc,6,"build") }
            35 -> screenshot(mc,"build")
            40 -> number(mc)
            45 -> screenshot(mc,"number-valid")
            50 -> invalidNumber(mc)
            55 -> screenshot(mc,"number-invalid")
            60 -> { checkNotNull(mc.screen).onClose();check(mc.screen===building);mc.options.guiScale().set(2);mc.resizeDisplay() }
            65 -> screenshot(mc,"compact-modules")
            70 -> tab(mc,5,"palette")
            75 -> screenshot(mc,"compact-materials")
            80 -> tab(mc,6,"build")
            85 -> screenshot(mc,"compact-build")
            90 -> number(mc)
            95 -> screenshot(mc,"compact-number-valid")
            100 -> invalidNumber(mc)
            105 -> screenshot(mc,"compact-number-invalid")
            110 -> { checkNotNull(mc.screen).onClose();catalog(mc) }
            115 -> screenshot(mc,"compact-catalog")
            120 -> filterCatalog(mc,3,"half_layer")
            125 -> screenshot(mc,"compact-catalog-filtered")
            130 -> { checkNotNull(mc.screen).onClose();mc.options.guiScale().set(1);mc.resizeDisplay();catalog(mc) }
            135 -> screenshot(mc,"catalog")
            140 -> filterCatalog(mc,1,"stairs")
            145 -> screenshot(mc,"catalog-filtered")
            150 -> {
                checkNotNull(mc.screen).onClose();check(mc.screen===building)
                tab(mc,1,"modules")
                check(!building.module3d)
                click(building,buttons(building).single { it.message.string==ui("edit_grid") })
                check(building.module3d) { "Actual module preview toggle must open its 3D view" }
            }
            155 -> screenshot(mc,"module-3d")
            160 -> tab(mc,3,"plates")
            165 -> screenshot(mc,"plates-cutouts")
            170 -> {
                mc.options.guiScale().set(2);mc.resizeDisplay();tab(mc,1,"modules")
                check(building.module3d) { "Module preview presentation must survive resize and tab navigation" }
            }
            175 -> screenshot(mc,"compact-module-3d")
            180 -> tab(mc,3,"plates")
            185 -> screenshot(mc,"compact-plates-cutouts")
            190 -> {
                check(mc.screen===building);layout()
                mc.options.guiScale().set(previousScale);mc.resizeDisplay();mc.setScreen(previous)
                println("ARCHITECT WORKSHOP UI: normal/compact module grids and 3D views, plate/cutout, material and build pages, actual numeric controls with range validation, material menu, catalog search/category and widget bounds verified")
                return true
            }
        }
        return false
    }
}
