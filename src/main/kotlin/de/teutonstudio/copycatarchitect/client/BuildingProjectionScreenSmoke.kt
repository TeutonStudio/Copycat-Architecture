package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.EditBox

/** Actual build-coordinate responders and sync, usable by the opt-in client acceptance suite. */
internal object BuildingProjectionScreenSmoke {
    fun verify(mc:Minecraft) {
        val previous=mc.screen;val snapshot=ClientState.snapshot
        try {
            val table=P(13,64,-9)
            val projection=BuildingProjection(P(-7,-2,-6),1)
            ClientState.snapshot=snapshot.copy(table=table)
            val screen=BuildingScreen().apply {
                building=Building(schema=2,id="projection_smoke",name="Projection draft",graph=BuildingGraph.assembledStarter().copy(projection=projection))
                tab=6
            }
            mc.setScreen(screen)
            fun fields()=screen.children().filterIsInstance<EditBox>()
            fun origin()=fields().drop(1).take(3).map { it.value.toInt() }.let { P(it[0],it[1],it[2]) }
            check(origin()==projection.origin(table)) { "Build fields must start at the projected origin of the actual table" }
            fields().first().value="Unsaved projection draft"
            val edited=origin()+P(3,1,0)
            fields()[1].value=edited.x.toString();fields()[2].value=edited.y.toString()
            val graph=checkNotNull(screen.building.graph)
            check(graph.projection.origin(table)==edited && graph.projection.turn==1) { "Actual coordinate responders must persist the table offset without losing rotation" }
            fields()[1].value="999999"
            check(screen.building.graph==graph) { "A partial or distant origin must not store an invalid projection in the document" }
            fields()[1].value=edited.x.toString()
            val before=screen.building
            ClientState.snapshot=ClientState.snapshot.copy(table=table+P(2,0,-3),message="Updated")
            screen.refreshProjectionBinding()
            check(screen.building==before && fields().first().value=="Unsaved projection draft") { "A server sync must preserve unsaved graph and name input" }
            check(origin()==graph.projection.origin(checkNotNull(ClientState.snapshot.table))) { "A changed table binding must refresh build coordinates even when projection data is unchanged" }
            ClientState.snapshot=ClientState.snapshot.copy(table=null)
            screen.refreshProjectionBinding()
            check(screen.children().filterIsInstance<PrinterEyeButton>().single().active.not()) { "Losing the nearby table must disable world peek" }
            println("ARCHITECT BUILDING PROJECTION SCREEN SMOKE PASSED: table-relative rotated origin, real manual coordinate responders, safe partial input, and lossless table rebind/sync verified")
        } finally {
            ClientState.snapshot=snapshot
            mc.setScreen(previous)
        }
    }
}
