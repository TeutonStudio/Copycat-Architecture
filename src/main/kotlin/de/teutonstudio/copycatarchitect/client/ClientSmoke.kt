package de.teutonstudio.copycatarchitect.client

import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RenderFrameEvent

/** Opt-in UI acceptance advances only after a real frame has settled native widget layouts. */
object ClientSmoke {
    private var frames=0L
    private var lastFrame=-1L
    fun frame(event:RenderFrameEvent.Post) {
        frames++
        if(CommerceQuarterGraphProfile.enabled) CommerceQuarterGraphProfile.frame()
    }
    fun tick(event:ClientTickEvent.Post) {
        if(CommerceQuarterGraphProfile.enabled) {
            if(frames>lastFrame) { lastFrame=frames;CommerceQuarterGraphProfile.tick() }
            return
        }
        if(java.lang.Boolean.getBoolean("architect.graphSelectionSmoke")) {
            if(frames>lastFrame && GraphSelectionSmoke.tick()) lastFrame=frames+1
            return
        }
        if(java.lang.Boolean.getBoolean("architect.roomMountingSmoke")) {
            if(frames>lastFrame && RoomMountingSmoke.tick()) lastFrame=frames+1
            return
        }
        if(java.lang.Boolean.getBoolean("architect.inspectorSectionsSmoke")) {
            if(frames>lastFrame && InspectorSectionsSmoke.tick()) lastFrame=frames+1
            return
        }
        if(java.lang.Boolean.getBoolean("architect.countArrangementSmoke")) {
            if(frames>lastFrame && CountArrangementSmoke.tick()) lastFrame=frames+1
            return
        }
        if(java.lang.Boolean.getBoolean("architect.segmentArrangementSmoke")) {
            if(frames>lastFrame && SegmentArrangementSmoke.tick()) lastFrame=frames+1
            return
        }
        if(java.lang.Boolean.getBoolean("architect.inspectorWorkflowSmoke")) {
            if(frames>lastFrame && InspectorWorkflowSmoke.tick()) lastFrame=frames+1
            return
        }
        if(java.lang.Boolean.getBoolean("architect.connectedTextureSmoke")) {
            if(frames>lastFrame && ConnectedTextureSmoke.tick()) lastFrame=frames+1
            return
        }
        if(java.lang.Boolean.getBoolean("architect.graphGroupSmoke")) {
            if(frames>lastFrame && GraphGroupSmoke.tick()) lastFrame=frames+1
            return
        }
        if(java.lang.Boolean.getBoolean("architect.commerceQuarterSmoke")) {
            if(frames>lastFrame && CommerceQuarterClientSmoke.tick()) lastFrame=frames+1
            return
        }
        if(java.lang.Boolean.getBoolean("architect.materialSelectionSmoke")) {
            if(frames>lastFrame && MaterialSelectionSmoke.tick()) lastFrame=frames+1
            return
        }
        if(!java.lang.Boolean.getBoolean("architect.structuralSmoke") && !java.lang.Boolean.getBoolean("architect.tBuildingSmoke") && !java.lang.Boolean.getBoolean("architect.graphNodePreviewSmoke") && !java.lang.Boolean.getBoolean("architect.savedCopycatSmoke") && !java.lang.Boolean.getBoolean("architect.cartridgeSmoke") && !java.lang.Boolean.getBoolean("architect.workshopSmoke") && !java.lang.Boolean.getBoolean("architect.floorPlanSmoke") && !java.lang.Boolean.getBoolean("architect.buildingPanelSmoke") && !java.lang.Boolean.getBoolean("architect.buildingWorkspaceSmoke") && !java.lang.Boolean.getBoolean("architect.smoke") && !java.lang.Boolean.getBoolean("architect.kilaGraphSmoke") && !java.lang.Boolean.getBoolean("architect.printerSmoke") && !java.lang.Boolean.getBoolean("architect.panelBuildingSmoke") && !java.lang.Boolean.getBoolean("architect.stairsSmoke") && !java.lang.Boolean.getBoolean("architect.explicitAssemblySmoke") && !java.lang.Boolean.getBoolean("architect.buildingPreviewSmoke") && !java.lang.Boolean.getBoolean("architect.copycatMirrorSmoke")) return
        // Minecraft may execute several catch-up ticks before drawing another frame.
        if(frames<=lastFrame) return
        // A popup can request a bounded position after its first layout; allow that second layout too.
        val acted=when {
            java.lang.Boolean.getBoolean("architect.structuralSmoke") -> StructuralSmoke.tick()
            java.lang.Boolean.getBoolean("architect.tBuildingSmoke") -> TBuildingClientSmoke.tick()
            java.lang.Boolean.getBoolean("architect.graphNodePreviewSmoke") -> GraphNodePreviewSmoke.tick()
            java.lang.Boolean.getBoolean("architect.savedCopycatSmoke") -> SavedCopycatSelectionSmoke.tick()
            java.lang.Boolean.getBoolean("architect.cartridgeSmoke") -> CartridgePonderSmoke.tick()
            java.lang.Boolean.getBoolean("architect.workshopSmoke") -> WorkshopUiSmoke.tick()
            java.lang.Boolean.getBoolean("architect.floorPlanSmoke") -> FloorPlanClientSmoke.tick()
            java.lang.Boolean.getBoolean("architect.buildingPanelSmoke") -> BuildingPanelSmoke.tick()
            java.lang.Boolean.getBoolean("architect.buildingWorkspaceSmoke") -> BuildingWorkspaceSmoke.tick()
            java.lang.Boolean.getBoolean("architect.panelBuildingSmoke") -> PanelBuildingClientSmoke.tick()
            java.lang.Boolean.getBoolean("architect.printerSmoke") -> PrinterClientSmoke.tick()
            java.lang.Boolean.getBoolean("architect.stairsSmoke") -> StairsDefinitionSmoke.tick()
            java.lang.Boolean.getBoolean("architect.explicitAssemblySmoke") -> ExplicitAssemblySmoke.tick()
            java.lang.Boolean.getBoolean("architect.buildingPreviewSmoke") -> BuildingPreviewSmoke.tick()
            java.lang.Boolean.getBoolean("architect.copycatMirrorSmoke") -> CopycatMirrorSmoke.tick()
            else -> KilaGraphSmoke.tick()
        }
        if(acted) lastFrame=frames+1
    }
}
