package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.*
import kotlin.test.Test

class SpecialRoomPortParityTest {
    @Test fun `every source offered by a special-room socket is accepted by its validator`() {
        for(kind in listOf(BuildingKind.BAY_WINDOW,BuildingKind.DORMER)) {
            val node=BuildingAssembly.newNode(kind,"special")
            for(port in BuildingPorts.of(node,true)) for(source in port.accepts) {
                val changed=GraphGroups.remap(node) { key,ref -> if(key==port.key) "source" else ref }
                SpecialRooms.validate(changed,mapOf(changed.id to changed,"source" to BuildingNode("source",source)))
            }
        }
    }
}
