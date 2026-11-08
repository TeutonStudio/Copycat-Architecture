package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox

/** Real widgets at both supported sizes, with server-provided permissions and a relocated table. */
internal object CreativeBuildScreenSmoke {
    fun verify(mc:Minecraft) {
        val previous=mc.screen;val snapshot=ClientState.snapshot;val scale=mc.options.guiScale().get()
        try {
            for(size in listOf(1,2)) {
                mc.options.guiScale().set(size);mc.resizeDisplay()
                val record=TableBuilding(table=P(12,64,-9),origin=P(17,65,-3),turn=1,
                    building=Building(schema=2,id="relocated_table",name="Versetzter Tisch",graph=BuildingGraph.explicitStarter(joinWallEnds=false)))
                ClientState.snapshot=Snapshot(library=Library().apply { seed() },table=record.table,creative=true,
                    tableBuilding=record.relativeBuilding(),tableOrigin=record.origin,tableTurn=record.turn)
                val screen=BuildingScreen().apply { tab=6 };mc.setScreen(screen)
                check(screen.building==record.relativeBuilding()) { "Opening a relocated table must load its own saved document" }
                val fields=screen.children().filterIsInstance<EditBox>()
                check(fields.drop(1).take(3).map { it.value.toInt() }==listOf(record.origin.x,record.origin.y,record.origin.z)) { "Relocation must keep the real building origin" }
                fun creative()=screen.children().filterIsInstance<Button>().single { it.message.string==ui("creative_build") }
                val button=creative();check(button.active && button.visible)
                val workspace=PrinterWorkspaceLayout(screen.width,screen.height,topTabs=true)
                val bottom=workspace.graphY+workspace.graphHeight
                check(button.x>=0 && button.y>=workspace.graphY && button.right<=screen.width && button.bottom<=bottom) { "Creative action must fit the build panel at ${screen.width}x${screen.height}: ${button.y}..${button.bottom}, bottom=$bottom" }
                val overlaps=screen.children().filterIsInstance<AbstractWidget>().filter { it!==button && it.visible && it.x<button.right && it.right>button.x && it.y<button.bottom && it.bottom>button.y }
                check(overlaps.isEmpty()) { "Creative action must not overlap another input: ${overlaps.map { it.message.string }}" }
                ClientState.snapshot=ClientState.snapshot.copy(table=null)
                screen.refreshProjectionBinding()
                check(!creative().active) { "A Creative player without a bound table must not be offered an active build" }
                ClientState.snapshot=ClientState.snapshot.copy(table=record.table,creative=false)
                screen.refreshProjectionBinding()
                check(screen.children().filterIsInstance<Button>().none { it.message.string==ui("creative_build") }) { "Survival must not display the Creative action" }
            }
            println("ARCHITECT CREATIVE BUILD SCREEN SMOKE PASSED: normal/compact creative-only action, table requirement, collision-free layout, restored table draft and unchanged relocated world origin")
        } finally { ClientState.snapshot=snapshot;mc.options.guiScale().set(scale);mc.resizeDisplay();mc.setScreen(previous) }
    }
}
