package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.Json
import de.teutonstudio.copycatarchitect.server.Library
import de.teutonstudio.copycatarchitect.server.Snapshot
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen

/** Independent real-client acceptance for the Copycat -> segment -> room -> storey -> house graph. */
internal object ExplicitAssemblySmoke {
    private var ticks=0
    private var failed=false
    private var previous:Screen?=null
    private var previousScale=1
    private lateinit var previousSnapshot:Snapshot
    private lateinit var screen:BuildingScreen
    private var original=""
    private var changed=""
    private var geometryId=""
    private var layoutId=""
    private var roomId=""
    private var annexId=""
    private var storeyId=""
    private var upperId=""
    private var roofId=""
    private var resultId=""
    private var copycatId=""
    private val areaId="acceptance_area"
    private data class RasterDrag(val id:String,val first:P,val last:P,val button:Int,val expected:Set<P>,
        val start:Pair<Double,Double>,val end:Pair<Double,Double>,val history:Pair<Boolean,Boolean>)
    private var rasterDrag:RasterDrag?=null

    fun tick():Boolean {
        if(failed) return false
        val mc=Minecraft.getInstance()
        if(ticks==0 && (mc.screen==null || mc.overlay!=null)) return false
        ticks++
        var acted=true
        try {
            when(ticks) {
                1 -> begin(mc)
                10 -> { editor().fit();hierarchy() }
                15 -> screenshot(mc,"graph-normal")
                20 -> rejectCopycats()
                25 -> sourceInspector(geometryId)
                30 -> { rasterFit(geometryId);screenshot(mc,"geometry-normal") }
                35 -> editCoordinates(mc)
                40 -> undoRedo()
                45 -> toggleGeometryCell()
                50 -> undoRedo()
                51 -> beginGeometryRectangle()
                52 -> { screenshot(mc,"geometry-rectangle-preview-normal");finishRasterRectangle() }
                53 -> undoRedo()
                54 -> beginGeometryRectangle(erase=true)
                55 -> finishRasterRectangle()
                56 -> undoRedo()
                57 -> cancelRasterRectangle(geometryId)
                58 -> inspector(layoutId)
                60 -> { rasterFit(layoutId);screenshot(mc,"segment-layout-normal") }
                65 -> editLength(mc)
                70 -> undoRedo()
                75 -> editLayout(mc)
                80 -> undoRedo()
                81 -> editDepth(mc)
                82 -> undoRedo()
                85 -> roofPlaceholder()
                90 -> screenshot(mc,"roof-placeholder-normal")
                91 -> inspector(areaId)
                92 -> beginAreaRectangle()
                93 -> { screenshot(mc,"area-rectangle-preview-normal");finishRasterRectangle() }
                94 -> undoRedo()
                95 -> cancelRasterRectangle(areaId)
                100 -> {
                    restored();mc.options.guiScale().set(2);mc.resizeDisplay();screen.openBuildingGraph(false)
                    check(screen.width in 400..499 && screen.height in 230..299) { "Compact acceptance requires the 427 x 240 GUI viewport" }
                }
                105 -> { editor().fit();hierarchy() }
                110 -> screenshot(mc,"graph-compact")
                115 -> rejectCopycats()
                120 -> sourceInspector(geometryId)
                125 -> { rasterFit(geometryId);screenshot(mc,"geometry-compact") }
                130 -> editCoordinates(mc)
                135 -> undoRedo()
                140 -> toggleGeometryCell()
                145 -> undoRedo()
                146 -> beginGeometryRectangle()
                147 -> { screenshot(mc,"geometry-rectangle-preview-compact");finishRasterRectangle() }
                148 -> undoRedo()
                149 -> beginGeometryRectangle(erase=true)
                150 -> finishRasterRectangle()
                151 -> undoRedo()
                152 -> cancelRasterRectangle(geometryId)
                153 -> inspector(layoutId)
                155 -> { rasterFit(layoutId);screenshot(mc,"segment-layout-compact") }
                160 -> editLength(mc)
                165 -> undoRedo()
                170 -> editLayout(mc)
                175 -> undoRedo()
                176 -> editDepth(mc)
                177 -> undoRedo()
                180 -> roofPlaceholder()
                185 -> screenshot(mc,"roof-placeholder-compact")
                186 -> inspector(areaId)
                187 -> beginAreaRectangle()
                188 -> { screenshot(mc,"area-rectangle-preview-compact");finishRasterRectangle() }
                189 -> undoRedo()
                190 -> cancelRasterRectangle(areaId)
                195 -> {
                    restored();ClientState.snapshot=previousSnapshot
                    mc.options.guiScale().set(previousScale);mc.resizeDisplay();mc.setScreen(previous)
                    println("ARCHITECT EXPLICIT ASSEMBLY UI SMOKE PASSED: native Copycat/layout/segment/geometry/room/storey/roof/building port types and left-to-right hierarchy, horizontal floor and ceiling segments, two shared-geometry rooms and two storeys, rejected Copycat cables without history changes, real source-node selection, coordinate-list and raster editing with shared dependents, empty-area and occupied-start rectangle drags, reverse erasing, deferred commits, frozen preview projection, Escape cancellation and unrelated-button release, integer validation, explicit source-index/rotation arrangement entry, complete JSON Undo/Redo and normal/compact roof placeholders verified")
                    mc.stop()
                }
                else -> acted=false
            }
        } catch(error:Throwable) {
            failed=true
            System.err.println("ARCHITECT EXPLICIT ASSEMBLY UI SMOKE FAILED at tick $ticks: ${error.message}")
            screenshot(mc,"failure-$ticks")
            throw IllegalStateException("Explicit assembly UI smoke failed at tick $ticks",error)
        }
        return acted
    }

    private fun begin(mc:Minecraft) {
        previous=mc.screen;previousScale=mc.options.guiScale().get();previousSnapshot=ClientState.snapshot
        ClientState.snapshot=Snapshot(library=Library().apply { seed() })
        mc.options.guiScale().set(1);mc.resizeDisplay()
        val initial=BuildingGraph.explicitStarter(joinWallEnds=false)
        val room=initial.nodes.first { it.kind==BuildingKind.ROOM }
        val storey=initial.nodes.first { it.kind==BuildingKind.STOREY }
        val result=initial.nodes.single { it.kind==BuildingKind.BUILDING }
        roomId=room.id;annexId="acceptance_annex";storeyId=storey.id;upperId="acceptance_upper";resultId=result.id
        geometryId=room.geometry
        val wall=initial.nodes.first { it.id in room.walls.values && it.id!="empty_wall" }
        layoutId=wall.layout;copycatId=wall.inputs.first()
        roofId=initial.nodes.single { it.kind==BuildingKind.ROOF && it.assembly }.id
        val assembled=storey.copy(inputs=listOf(roomId,annexId),offsets=listOf(P(),P(5,0,0)))
        val secondCopycat=initial.nodes.first { it.kind==BuildingKind.COPYCAT && it.id!=copycatId }
        val base=initial.replace(assembled).replace(result.copy(levels=mapOf(0 to storeyId,1 to upperId)))
            .replace(wall.copy(inputs=listOf(copycatId,secondCopycat.id)))
        val graph=base.copy(nodes=base.nodes+room.copy(id=annexId,name="Shared geometry annex",y=310)+
            assembled.copy(id=upperId,name="Upper storey",y=250)+
            BuildingNode(areaId,BuildingKind.AREA,"Rectangle area draft",20,1040))
        BuildingGraphCompiler.structure(graph)
        screen=BuildingScreen().apply {
            building=Building(schema=2,id="explicit_assembly_smoke",name="Copycat to house",graph=graph)
            graphMode=true;graphInspector=false;selectBuildingNode(geometryId)
        }
        original=document();mc.setScreen(screen);screen.openBuildingGraph(false)
        check(screen.width==854 && screen.height==480) { "Explicit assembly acceptance requires the configured 854 x 480 window" }
        canvas().result().getOrThrow()
    }

    private fun graph()=checkNotNull(screen.building.graph)
    private fun node(id:String)=graph().nodes.single { it.id==id }
    private fun canvas()=checkNotNull(screen.buildingGraphCanvas)
    private fun editor()=checkNotNull(screen.kilaBuildingGraph)
    private fun document()=Json.encode(screen.building)
    private fun restored()=check(document()==original) { "Every source edit must restore the complete building JSON" }
    private fun ports(id:String)=BuildingPorts.of(node(id),graph(),true)
    private fun slot(id:String,key:String)=ports(id).indexOfFirst { it.key==key }.also { check(it>=0) { "Missing explicit assembly input $id:$key" } }
    private fun control(key:String)=screen.children().filterIsInstance<Button>().first { it.visible && it.message.string==ui(key) }
    private fun history()=control("undo").active to control("redo").active
    private fun click(point:Pair<Double,Double>,button:Int=0) {
        check(point.first>=0 && point.first<screen.width && point.second>=0 && point.second<screen.height) {
            "Explicit assembly click must fit ${screen.width} x ${screen.height}: $point"
        }
        check(screen.mouseClicked(point.first,point.second,button)) { "The actual explicit assembly widget must receive its click" }
        screen.mouseReleased(point.first,point.second,button)
    }
    private fun action(key:String) {
        val control=control(key);check(control.active) { "Explicit assembly $key must be available" }
        click(control.x+control.width/2.0 to control.y+control.height/2.0)
    }
    private fun screenshot(mc:Minecraft,name:String) {
        Screenshot.grab(mc.gameDirectory,"architect-explicit-assembly-$name.png",mc.mainRenderTarget) { println(it.string) }
    }

    private fun hierarchy() {
        restored()
        val native=editor();val g=graph()
        val vocabulary=listOf(BuildingKind.COPYCAT,BuildingKind.SEGMENT_LAYOUT,BuildingKind.SEGMENT,BuildingKind.ROOM_GEOMETRY,
            BuildingKind.ROOM,BuildingKind.STOREY,BuildingKind.ROOF_LAYOUT,BuildingKind.ROOF)
        val types=vocabulary.associateWith { kind -> checkNotNull(native.outputPortType(g.nodes.first { it.kind==kind && it.id!="empty_wall" }.id)) }
        check(types.values.distinct().size==vocabulary.size) { "Every explicit assembly source must expose its own native semantic type" }
        for(target in g.nodes) for((index,port) in BuildingPorts.of(target,g,true).withIndex()) {
            val input=checkNotNull(native.inputPortType(target.id,index)) { "The native graph must expose every graph-aware socket" }
            val expected=port.accepts.map { kind -> when(kind) {
                BuildingKind.COPYCAT,BuildingKind.COPYCAT_MIRROR,BuildingKind.COPYCAT_ROTATE -> ui("port_copycat")
                BuildingKind.SEGMENT,BuildingKind.PATTERN,BuildingKind.SEGMENT_MIRROR,BuildingKind.SEGMENT_ROTATE -> ui("bg_segment")
                else -> ui("bg_"+kind.name.lowercase())
            } }.toSet()
            check(input.friendlyName.split(" / ").toSet()==expected) { "The native ${target.id}:${port.key} socket must expose all accepted semantic types" }
            if(port.source.isNotEmpty()) {
                val source=node(port.source)
                check(source.kind in port.accepts) { "Assembly connection has the wrong source kind" }
                check(checkNotNull(native.outputPortType(source.id)).friendlyName in expected) { "Native cable/socket types disagree for ${target.id}:${port.key}" }
                check(source.x<target.x) { "The assembly graph must progress from Copycat and arrangement sources towards the house" }
            }
        }
        val room=node(roomId);val resolved=g.resolvedNode(room)
        check(room.assembly && room.geometry==geometryId && node(annexId).geometry==geometryId)
        check(RoomGeometry.cells(resolved.rectangles)==node(geometryId).coordinates.toSet())
        check(listOf(room.floor,room.ceiling).all { node(it).kind==BuildingKind.SEGMENT && node(it).assembly }) {
            "Floor and ceiling must consume horizontal segments in the explicit room vocabulary"
        }
        check(g.nodes.any { it.kind==BuildingKind.SEGMENT && it.layout==layoutId && it.inputs.size==2 }) {
            "Several Copycats and one arrangement must compose the same segment"
        }
        check(node(storeyId).inputs==listOf(roomId,annexId) && node(upperId).inputs==listOf(roomId,annexId))
        check(node(resultId).levels==mapOf(0 to storeyId,1 to upperId))
        check(node(resultId).roof.isEmpty() && node(roofId).layout.isNotEmpty()) {
            "The undefined roof arrangement must remain an explicit draft outside the build result"
        }
        check(native.inputPortType(roomId,slot(roomId,"geometry"))==types.getValue(BuildingKind.ROOM_GEOMETRY))
        check(native.inputPortType(roofId,slot(roofId,"layout"))==types.getValue(BuildingKind.ROOF_LAYOUT))
        check(native.inputPortType(resultId,slot(resultId,"roof"))==types.getValue(BuildingKind.ROOF))
        check(native.inputPortType(resultId,slot(resultId,"level:1"))==types.getValue(BuildingKind.STOREY))
        canvas().result().getOrThrow()
    }

    private fun nativeWire(source:String,target:String,key:String) {
        val native=editor();val from=checkNotNull(native.outputPortPoint(source));val to=checkNotNull(native.inputPortPoint(target,slot(target,key)))
        native.mouseMoved(from.first,from.second);native.mouseClicked(from.first,from.second,0)
        native.mouseDragged(to.first,to.second,0,to.first-from.first,to.second-from.second)
        native.mouseMoved(to.first,to.second);native.mouseReleased(to.first,to.second,0)
    }
    private fun rejectCopycats() {
        restored();val before=history()
        for((target,key) in listOf(roomId to "geometry",storeyId to "in:0",resultId to "level:0",roofId to "layout")) {
            nativeWire(copycatId,target,key)
            check(document()==original && history()==before) { "A Copycat cable must be rejected at $target:$key without changing the document or history" }
        }
    }

    private fun sourceInspector(id:String) {
        restored()
        val native=editor();click(checkNotNull(native.nodeHeaderPoint(id)))
        check(screen.buildingGraphView.selected==id) { "Clicking the actual source node must select that source's inspector" }
        screen.openBuildingGraph(true)
        check(screen.buildingGraphView.selected==id && canvas().visible)
    }
    private fun inspector(id:String) {
        restored();screen.selectBuildingNode(id);screen.openBuildingGraph(true)
        check(canvas().visible)
    }
    private fun rasterFit(id:String) {
        val canvas=canvas();val source=graph().resolvedNode(node(id))
        val layout=source.kind==BuildingKind.SEGMENT_LAYOUT
        val cells=if(layout) setOf(P(),P(source.length-1,0,source.height-1)) else source.coordinates.toSet()
        val centerX=(cells.minOf { it.x }+cells.maxOf { it.x }+1)/2.0
        val centerZ=(cells.minOf { it.z }+cells.maxOf { it.z }+1)/2.0
        val actual=canvas.gridPoint(id,centerX,centerZ)
        val mapTop=if(layout) 92 else 24
        val fullHeight=if(layout) 116 else 152
        val origin=canvas.propertyPoint(id,mapTop-2,canvas.nodeWidth(source)/2)
        val scale=canvas.propertyPoint(id,1).second-canvas.propertyPoint(id,0).second
        val mapHeight=if(canvas.height<220) minOf(fullHeight,((canvas.height-49)/scale).toInt().coerceAtLeast(24)) else fullHeight
        check(kotlin.math.abs(actual.first-origin.first)<0.001 && kotlin.math.abs(actual.second-origin.second-mapHeight*scale/2)<0.001) {
            "The occupied integer raster must stay centered horizontally and vertically in its normal or compact viewport"
        }
        for(cell in cells) {
            val point=canvas.gridPoint(id,cell.x+0.5,cell.z+0.5)
            check(point.first>canvas.x && point.first<canvas.x+canvas.width && point.second>canvas.y+35 && point.second<canvas.y+canvas.height) {
                "Every fitted raster cell must remain visible below the sticky selector in the normal and compact inspector"
            }
        }
    }
    private fun reveal(point:()->Pair<Double,Double>):Pair<Double,Double> {
        val inspector=canvas();val p=point()
        val target=p.second.coerceIn(inspector.y+55.0,inspector.y+inspector.height-18.0)
        if(target!=p.second) check(inspector.mouseScrolled(inspector.x+inspector.width/2.0,inspector.y+inspector.height-8.0,0.0,(target-p.second)/25.0))
        return point().also { check(it.second>=inspector.y+35 && it.second<inspector.y+inspector.height) { "The scrolled source control must be visible below the sticky selector" } }
    }
    private fun property(id:String,row:Int,column:Int=16)=reveal { canvas().propertyPoint(id,row,column) }
    private fun dialog(mc:Minecraft):Pair<Screen,EditBox> {
        val dialog=checkNotNull(mc.screen);check(dialog!==screen) { "An actual value-entry screen must open" }
        return dialog to dialog.children().filterIsInstance<EditBox>().single()
    }
    private fun rejectDraft(dialog:Screen,input:EditBox,value:String) {
        val before=document();val history=history();input.value=value;check(dialog.keyPressed(257,0,0))
        check(Minecraft.getInstance().screen===dialog && document()==before && history()==history) {
            "Invalid source text must remain a draft without modifying the building or history: $value"
        }
    }
    private fun acceptDraft(mc:Minecraft,dialog:Screen,input:EditBox,value:String) {
        input.value=value;check(dialog.keyPressed(257,0,0));check(mc.screen===screen) { "Valid source text must return to the table" }
        changed=document();check(changed!=original)
    }
    private fun editCoordinates(mc:Minecraft) {
        restored();click(property(geometryId,0))
        val (dialog,input)=dialog(mc)
        rejectDraft(dialog,input,"0.5,0; 1,0")
        rejectDraft(dialog,input,"0,0; 0,0")
        val corner=node(geometryId).coordinates.maxWith(compareBy({it.z},{it.x}))
        val expected=node(geometryId).coordinates.filter { it!=corner }
        check(expected.isNotEmpty() && expected.size<node(geometryId).coordinates.size)
        acceptDraft(mc,dialog,input,expected.joinToString("; ") { "${it.x},${it.z}" })
        check(node(geometryId).coordinates.toSet()==expected.toSet())
        for(id in listOf(roomId,annexId)) {
            check(RoomGeometry.cells(graph().resolvedNode(node(id)).rectangles)==expected.toSet()) { "Editing the single coordinate source must update every dependent room" }
            check(BuildingPorts.of(node(id),graph()).count { it.key.startsWith("wall:") }==RoomGeometry.boundary(expected.toSet()).size)
        }
    }
    private fun toggleGeometryCell() {
        restored()
        val before=node(geometryId).coordinates.toSet();val cell=before.maxWith(compareBy({it.z},{it.x}))
        click(reveal { canvas().gridPoint(geometryId,cell.x+0.5,cell.z+0.5) })
        check(node(geometryId).coordinates.toSet()==before-cell) { "Clicking the geometry source raster must remove exactly one occupied integer cell" }
        for(id in listOf(roomId,annexId)) check(RoomGeometry.cells(graph().resolvedNode(node(id)).rectangles)==before-cell)
        changed=document();check(changed!=original)
    }
    private fun rectangle(first:P,last:P)=buildSet {
        for(z in minOf(first.z,last.z)..maxOf(first.z,last.z)) for(x in minOf(first.x,last.x)..maxOf(first.x,last.x)) add(P(x,0,z))
    }
    private fun beginGeometryRectangle(erase:Boolean=false) {
        val cells=node(geometryId).coordinates.toSet()
        val first=P(if(erase) cells.maxOf { it.x } else cells.minOf { it.x },0,cells.maxOf { it.z })
        val last=P(if(erase) cells.minOf { it.x } else cells.maxOf { it.x },0,first.z+if(erase) 0 else 1)
        check(first in cells) { "A rectangle drag must also work when its first cell is occupied" }
        beginRasterRectangle(geometryId,first,last,if(erase) 1 else 0,if(erase) cells-rectangle(first,last) else cells+rectangle(first,last))
    }
    private fun beginAreaRectangle() {
        check(node(areaId).coordinates.isEmpty()) { "Rectangle creation must work in an empty area draft" }
        val first=P(2,0,1);val last=P()
        beginRasterRectangle(areaId,first,last,0,rectangle(first,last))
    }
    private fun beginRasterRectangle(id:String,first:P,last:P,button:Int,expected:Set<P>) {
        restored();check(rasterDrag==null)
        val inspector=canvas()
        val start=inspector.gridPoint(id,first.x+0.5,first.z+0.5)
        val end=inspector.gridPoint(id,last.x+0.5,last.z+0.5)
        for(point in listOf(start,end)) check(inspector.isMouseOver(point.first,point.second) && point.second>=inspector.y+35) {
            "Both rectangle corners must fit the actual normal or compact source viewport: $point"
        }
        val before=history()
        check(screen.mouseClicked(start.first,start.second,button)) { "The real source inspector must start the rectangle" }
        check(document()==original && history()==before) { "Pressing a rectangle corner must only begin a local draft" }
        check(screen.mouseDragged(end.first,end.second,button,end.first-start.first,end.second-start.second))
        check(document()==original && history()==before) { "Dragging must preview the whole rectangle without editing the document or history" }
        check(screen.mouseReleased(end.first,end.second,if(button==0) 1 else 0))
        check(document()==original && history()==before) { "Releasing an unrelated mouse button must preserve the active rectangle draft" }
        check(screen.mouseScrolled(start.first,start.second,0.0,-2.0))
        check(inspector.gridPoint(id,first.x+0.5,first.z+0.5)==start) { "Scrolling during a rectangle draft must preserve its coordinate transform" }
        rasterDrag=RasterDrag(id,first,last,button,expected,start,end,before)
    }
    private fun finishRasterRectangle() {
        val drag=checkNotNull(rasterDrag);rasterDrag=null
        check(document()==original && history()==drag.history) { "A rendered rectangle preview must leave the document and history untouched" }
        check(canvas().gridPoint(drag.id,drag.first.x+0.5,drag.first.z+0.5)==drag.start &&
            canvas().gridPoint(drag.id,drag.last.x+0.5,drag.last.z+0.5)==drag.end) {
            "Rendering an expanded rectangle preview must keep the raster projection fixed"
        }
        check(screen.mouseReleased(drag.end.first,drag.end.second,drag.button))
        check(node(drag.id).coordinates.toSet()==drag.expected) { "Releasing the actual rectangle drag must apply every included cell exactly once" }
        if(drag.id==geometryId) for(id in listOf(roomId,annexId)) {
            check(RoomGeometry.cells(graph().resolvedNode(node(id)).rectangles)==drag.expected) { "A rectangle edit must update every shared geometry consumer atomically" }
        }
        changed=document();check(changed!=original)
    }
    private fun cancelRasterRectangle(id:String) {
        val first=P();val last=P(1,0,1)
        beginRasterRectangle(id,first,last,0,emptySet())
        val drag=checkNotNull(rasterDrag);rasterDrag=null
        check(screen.keyPressed(256,0,0) && Minecraft.getInstance().screen===screen) { "Escape must cancel a rectangle before closing the table" }
        check(document()==original && history()==drag.history) { "Cancelling a rectangle must preserve complete JSON and both history stacks" }
        screen.mouseReleased(drag.end.first,drag.end.second,drag.button)
        check(document()==original && history()==drag.history) { "Releasing after Escape must not apply the cancelled rectangle" }
    }
    private fun editLength(mc:Minecraft) {
        restored();val before=node(layoutId);click(property(layoutId,0,16))
        val (dialog,input)=dialog(mc)
        rejectDraft(dialog,input,"2.5");rejectDraft(dialog,input,"129")
        acceptDraft(mc,dialog,input,(before.length+1).toString())
        check(node(layoutId)==before.copy(length=before.length+1)) { "The arrangement length must change only its integer dimension" }
        for(segment in graph().nodes.filter { it.kind==BuildingKind.SEGMENT && it.layout==layoutId }) check(graph().resolvedNode(segment).length==before.length+1)
    }
    private fun editLayout(mc:Minecraft) {
        restored();val before=node(layoutId);click(property(layoutId,66))
        val (dialog,input)=dialog(mc)
        rejectDraft(dialog,input,"0.5,0,1,0")
        rejectDraft(dialog,input,"0,0,0,0")
        rejectDraft(dialog,input,"0,0,1,45")
        acceptDraft(mc,dialog,input,"0,0,1,90; 1,0,2,180")
        check(node(layoutId)==before.copy(layoutCells=listOf(SegmentLayoutCell(0,0,0,1),SegmentLayoutCell(1,0,1,2)))) {
            "The arrangement editor must save integer positions, one-based Copycat numbers and quarter-turn rotations"
        }
    }
    private fun editDepth(mc:Minecraft) {
        restored();val before=node(layoutId);click(property(layoutId,22))
        val (dialog,input)=dialog(mc)
        rejectDraft(dialog,input,"0");rejectDraft(dialog,input,"129")
        acceptDraft(mc,dialog,input,"3")
        check(node(layoutId)==before.copy(depth=3)) { "The depth editor must change only the geometry's third dimension" }
        for(segment in graph().nodes.filter { it.kind==BuildingKind.SEGMENT && it.layout==layoutId }) {
            check(graph().resolvedNode(segment).depth==3)
            val regions=BuildingPorts.of(segment,graph()).filter { it.key.startsWith("region:") }
            check(regions.size==27 && regions.map { it.key }.distinct().size==27) { "Changing geometry depth must expand the segment's region ports without duplicate connections" }
        }
    }
    private fun undoRedo() {
        check(document()==changed)
        action("undo");restored()
        action("redo");check(document()==changed) { "Redo must restore the complete edited source and all remapped dependents" }
        action("undo");restored()
    }
    private fun roofPlaceholder() {
        inspector(node(roofId).layout)
        check(node(screen.buildingGraphView.selected).kind==BuildingKind.ROOF_LAYOUT)
        check(BuildingPorts.of(node(roofId),graph()).single { it.key=="layout" }.accepts==setOf(BuildingKind.ROOF_LAYOUT))
        check(node(resultId).roof.isEmpty())
    }
}
