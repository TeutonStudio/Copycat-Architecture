package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.Json
import de.teutonstudio.copycatarchitect.server.Library
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen

/** Real native controls verify that folding previews is independent, persistent view state. */
internal object GraphNodePreviewSmoke {
    private var ticks=0
    private var failed=false
    private var previous:Screen?=null
    private var previousScale=1
    private lateinit var screen:PreviewScreen

    fun tick():Boolean {
        if(failed) return false
        val mc=Minecraft.getInstance()
        if(ticks==0 && (mc.screen==null || mc.overlay!=null)) return false
        ticks++
        var acted=true
        try {
            when(ticks) {
                1 -> {
                    previous=mc.screen;previousScale=mc.options.guiScale().get()
                    mc.options.guiScale().set(1);mc.resizeDisplay()
                    screen=PreviewScreen(false);mc.setScreen(screen)
                }
                10 -> screen.editor.fit()
                20 -> { screen.verifyEveryNode();screen.show(listOf("slab","angle","rotated","result"),expandPreviews=true) }
                30 -> screen.editor.fit()
                40 -> { screen.unchanged();screenshot(mc,"shape-normal");fold("slab",false) }
                50 -> { fold("angle",false);check(!screen.editor.isNodePreviewExpanded("slab")) }
                60 -> { fold("angle",true);screen.rebuild() }
                70 -> { check(!screen.editor.isNodePreviewExpanded("slab") && screen.editor.isNodePreviewExpanded("angle"));screen.editor.fit() }
                80 -> { screen.unchanged();screenshot(mc,"shape-folded-normal");mc.options.guiScale().set(2);mc.resizeDisplay() }
                90 -> screen.editor.fit()
                100 -> { check(!screen.editor.isNodePreviewExpanded("slab"));fold("slab",true) }
                110 -> { screen.unchanged();screenshot(mc,"shape-compact");mc.options.guiScale().set(1);mc.resizeDisplay();screen=PreviewScreen(true);mc.setScreen(screen) }
                120 -> screen.editor.fit()
                130 -> { screen.verifyEveryNode();screen.show(listOf("wall_copycat","area","layout","room","storey","building"),expandPreviews=true) }
                140 -> screen.editor.fit()
                150 -> { verifyRenderedGeometry(mc,listOf("wall_copycat","room","storey","building"));screenshot(mc,"building-normal");fold("wall_copycat",false) }
                160 -> { fold("area",false);check(!screen.editor.isNodePreviewExpanded("wall_copycat")) }
                170 -> { fold("area",true);screen.rebuild() }
                180 -> { check(!screen.editor.isNodePreviewExpanded("wall_copycat") && screen.editor.isNodePreviewExpanded("area"));screen.editor.fit() }
                190 -> { screen.unchanged();screenshot(mc,"building-folded-normal");screen.show(listOf("wall_copycat","area","room"));mc.options.guiScale().set(2);mc.resizeDisplay() }
                200 -> screen.editor.fit()
                210 -> { check(!screen.editor.isNodePreviewExpanded("wall_copycat"));fold("wall_copycat",true) }
                220 -> { screen.unchanged();verifyRenderedGeometry(mc,listOf("wall_copycat","room"));screenshot(mc,"building-compact") }
                230 -> { screen.unchanged();screen.show(listOf("garden","path"),expandPreviews=true);mc.options.guiScale().set(1);mc.resizeDisplay() }
                240 -> screen.editor.fit()
                250 -> { screen.unchanged();screenshot(mc,"landscape-normal");mc.options.guiScale().set(2);mc.resizeDisplay() }
                260 -> screen.editor.fit()
                270 -> { screen.unchanged();screenshot(mc,"landscape-compact") }
                280 -> {
                    screen.unchanged();mc.options.guiScale().set(previousScale);mc.resizeDisplay();mc.setScreen(previous)
                    println("ARCHITECT GRAPH NODE PREVIEW UI SMOKE PASSED: only result previews open by default, every shape/building node has a native 3D fold control, local geometry and pending errors, independent folding/unfolding, persistent preview overrides across graph rebuilds and GUI-scale changes, read-only controls, unchanged document JSON and zero edit callbacks, material pixels inside every transformed normal/compact plan viewport and screenshots verified")
                    mc.stop()
                }
                else -> acted=false
            }
        } catch(error:Throwable) {
            failed=true;screenshot(mc,"failure-$ticks")
            throw IllegalStateException("Graph node preview UI smoke failed at tick $ticks",error)
        }
        return acted
    }

    private fun fold(id:String,expanded:Boolean) {
        screen.unchanged()
        val point=checkNotNull(screen.editor.nodePreviewTogglePoint(id))
        check(screen.editor.isMouseOver(point.first,point.second)) { "The native fold button for $id must fit the graph viewport" }
        check(screen.mouseClicked(point.first,point.second,0)) { "The actual native fold button must receive its click" }
        screen.mouseReleased(point.first,point.second,0)
        check(screen.editor.isNodePreviewExpanded(id)==expanded) { "Folding must update only $id's native 3D area" }
        if(!expanded) check(screen.editor.nodePreviewPoint(id)==null) { "A collapsed preview must release its graph layout area" }
        screen.unchanged()
    }

    private fun screenshot(mc:Minecraft,name:String) {
        Screenshot.grab(mc.gameDirectory,"architect-graph-node-preview-$name.png",mc.mainRenderTarget) { println(it.string) }
    }

    private fun verifyRenderedGeometry(mc:Minecraft,ids:List<String>) {
        Screenshot.takeScreenshot(mc.mainRenderTarget).use { pixels ->
            val scaleX=pixels.width.toDouble()/screen.width;val scaleY=pixels.height.toDouble()/screen.height
            for(id in ids) {
                val viewport=checkNotNull(screen.editor.nodePreviewViewport(id))
                var materialPixels=0
                // The fixture uses neutral stone textures. Blueprint, raster, brass corners and
                // machine borders are coloured; sampling only the interior also excludes casing.
                val left=((viewport.x+viewport.width*0.15)*scaleX).toInt().coerceIn(0,pixels.width-1)
                val right=((viewport.x+viewport.width*0.85)*scaleX).toInt().coerceIn(left+1,pixels.width)
                val top=((viewport.y+viewport.height*0.15)*scaleY).toInt().coerceIn(0,pixels.height-1)
                val bottom=((viewport.y+viewport.height*0.85)*scaleY).toInt().coerceIn(top+1,pixels.height)
                for(y in top until bottom) for(x in left until right) {
                    val color=pixels.getPixelRGBA(x,y);val red=color and 255;val green=color ushr 8 and 255;val blue=color ushr 16 and 255
                    if(minOf(red,green,blue)>=35 && maxOf(red,green,blue)-minOf(red,green,blue)<=12) materialPixels++
                }
                check(materialPixels>=12) { "The transformed $id graph viewport must contain actual material geometry, not an empty raster (${screen.width} x ${screen.height}, $viewport, $materialPixels pixels)" }
            }
        }
    }

    private class PreviewScreen(private val buildingMode:Boolean):EditorScreen(tr(if(buildingMode) "building_graph" else "node_graph")) {
        private val library=Library().apply { seed() }
        private val graph=BuildingGraph.assembledStarter().let { initial -> initial.copy(nodes=initial.nodes+listOf(
            BuildingNode("area",BuildingKind.AREA,coordinates=listOf(P(-2,0,-1),P(-1,0,-1),P(-2,0,0))),
            BuildingNode("layout",BuildingKind.SEGMENT_LAYOUT,length=3,height=4,depth=2,layoutCells=listOf(SegmentLayoutCell(),SegmentLayoutCell(2,2))),
            BuildingNode("geometry",BuildingKind.ROOM_GEOMETRY,geometry="area",height=4),
            BuildingNode("pattern",BuildingKind.PATTERN,inputs=listOf("wall","empty_wall")),
            BuildingAssembly.newNode(BuildingKind.SEGMENT_MIRROR,"mirror").copy(inputs=listOf("wall")),
            BuildingAssembly.newNode(BuildingKind.COPYCAT_MIRROR,"copycat_mirror").copy(inputs=listOf("wall_copycat")),
            BuildingAssembly.newNode(BuildingKind.COPYCAT_ROTATE,"copycat_rotation").copy(inputs=listOf("wall_copycat")),
            BuildingAssembly.newNode(BuildingKind.SEGMENT_ROTATE,"segment_rotation").copy(inputs=listOf("wall")),
            BuildingNode("bay",BuildingKind.BAY_WINDOW,bayWindow=BayWindowConfig(wall="wall",front="wall",floor="floor",ceiling="ceiling")),
            BuildingNode("dormer",BuildingKind.DORMER,dormer=DormerConfig(wall="wall",front="wall",roof="roof_copycat")),
            BuildingNode("roof_layout",BuildingKind.ROOF_LAYOUT),
            BuildingAssembly.newNode(BuildingKind.ROOF_LEVEL,"roof_level"),
            BuildingAssembly.newNode(BuildingKind.STAIRWELL,"stairs"),
            BuildingAssembly.newNode(BuildingKind.ELEVATOR_SHAFT,"elevator"),
            BuildingAssembly.newNode(BuildingKind.SERVICE_SHAFT,"services"),
            BuildingAssembly.newNode(BuildingKind.GARDEN,"garden").copy(fenced=true),
            BuildingAssembly.newNode(BuildingKind.PATH,"path"))) }
        private val building=Building(id="node_preview_smoke",graph=graph)
        private val form=Geometry.validate(Form(id="node_preview_smoke",graph=NodeGraph(nodes=listOf(
            GraphNode("full",part=Part("copycats:copycat_block")),GraphNode("slab",part=Part("copycats:copycat_slab")),
            GraphNode("angle",NodeKind.ROTATION,rotationAxis=RotationAxis.Z,quarterTurns=1),
            GraphNode("rotated",NodeKind.ROTATE,listOf("slab","angle")),
            GraphNode("union",NodeKind.UNION,listOf("full","rotated")),
            GraphNode("intersection",NodeKind.INTERSECTION,listOf("full","slab")),
            GraphNode("difference",NodeKind.DIFFERENCE,listOf("full","slab")),
            GraphNode("symmetric",NodeKind.SYMMETRIC_DIFFERENCE,listOf("full","slab")),
            GraphNode("mirror",NodeKind.MIRROR,listOf("slab")),
            GraphNode("saved",NodeKind.SAVED,savedCopycat=SavedCopycat("fixture",1,"Saved slab",listOf(Part("copycats:copycat_slab")))),
            GraphNode("result",NodeKind.OUTPUT,listOf("union"))))))
        private val shapePreviews=ShapeNodePreviews({form}) { checkNotNull(form.graph) }
        private val buildingPreviews=BuildingNodePreviews({building}) { library }
        private val view=KilaGraphState()
        private var visibleIds:List<String>?=null
        private val original=Json.encode(building) to Json.encode(form)
        private var commits=0
        lateinit var editor:KilaGraphEditor;private set
        override fun titleAvailableWidth()=width-16
        override fun init() {
            if(::editor.isInitialized) editor.dispose()
            editor=addRenderableWidget(KilaGraphEditor(8,29,width-16,height-53,this,view,::diagram,{}, {commits++}, { error(it) },
                readOnly=true,nodePreview=if(buildingMode) null else shapePreviews::get,
                buildingNodePreview=if(buildingMode) buildingPreviews::get else null))
        }
        private fun error(message:String):Nothing=throw IllegalStateException(message)
        private fun diagram():KilaGraphData {
            val columns=if(visibleIds!=null && buildingMode && width>=700) 3 else if(visibleIds!=null) 2 else 4
            val nodes=if(buildingMode) graph.nodes.map { KilaNode(it.id,ui("bg_"+it.kind.name.lowercase()),0,0,emptyList(),output=false,previewExpandedByDefault=it.kind==BuildingKind.BUILDING) }
                else checkNotNull(form.graph).nodes.map { KilaNode(it.id,ui("node_"+it.kind.name.lowercase()),0,0,emptyList(),output=false,previewExpandedByDefault=it.kind==NodeKind.OUTPUT) }
            return KilaGraphData(nodes.filter { visibleIds==null || it.id in visibleIds!! }.mapIndexed { i,n -> n.copy(x=i%columns*195,y=i/columns*145) },emptyList())
        }
        fun show(ids:List<String>,expandPreviews:Boolean=false) {
            unchanged()
            if(expandPreviews) ids.forEach { view.previewVisibility.setExpanded(it,true) }
            visibleIds=ids;rebuild()
        }
        fun rebuild() { unchanged();rebuildWidgets() }
        fun verifyEveryNode() {
            unchanged()
            val ids=if(buildingMode) graph.nodes.map { it.id } else checkNotNull(form.graph).nodes.map { it.id }
            for(id in ids) {
                check(editor.hasNodePreview(id) && editor.nodePreviewTogglePoint(id)!=null) { "Every native node needs a visible 3D fold control: $id" }
                val result=if(buildingMode) graph.nodes.single { it.id==id }.kind==BuildingKind.BUILDING
                    else checkNotNull(form.graph).nodes.single { it.id==id }.kind==NodeKind.OUTPUT
                check(editor.isNodePreviewExpanded(id)==result) { "Only result nodes should expand their 3D preview by default: $id" }
                if(!result) check(editor.nodePreviewPoint(id)==null) { "Collapsed intermediate previews must not occupy graph layout space: $id" }
                if(buildingMode) {
                    val value=checkNotNull(editor.buildingNodePreviewValue(id))
                    if(value.node.kind==BuildingKind.ROOF_LAYOUT) check(value.plan.isFailure) { "Undefined roof arrangements must expose their pending state" }
                    else check(value.plan.isSuccess) { "Local preview failed for ${value.node.kind}: ${value.plan.exceptionOrNull()}" }
                } else check(checkNotNull(editor.nodePreviewValue(id)).error.isEmpty()) { "Every valid shape node must retain its own intermediate geometry" }
            }
            if(buildingMode) check(graph.nodes.map { it.kind }.toSet()==BuildingKind.entries.toSet())
            else check(checkNotNull(form.graph).nodes.map { it.kind }.toSet()==NodeKind.entries.toSet())
        }
        fun unchanged() { check(commits==0 && original==(Json.encode(building) to Json.encode(form))) { "Preview folding must preserve documents and never invoke an edit callback" } }
        override fun renderContents(g:GuiGraphics,mx:Int,my:Int,delta:Float) { editor.renderPopupOverlay(g,mx,my,delta) }
        override fun mouseClicked(x:Double,y:Double,button:Int):Boolean {
            if(editor.mouseClicked(x,y,button)) { focused=editor;return true };return super.mouseClicked(x,y,button)
        }
        override fun mouseReleased(x:Double,y:Double,button:Int)=editor.mouseReleased(x,y,button)
        override fun removed() { editor.dispose();super.removed() }
    }
}
