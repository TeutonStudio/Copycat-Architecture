package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.Architect
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.narration.NarratedElementType
import java.util.UUID
import kotlin.math.*

class FloorPlanView {
    var selected=""
    var storey=""
    var panX=0.0
    var panY=0.0
    var step=24.0
    var fitted=false
    var fittedContext=""
    var inspectorScroll=0.0
    var inspectorRoom=""
    val colors=mutableMapOf<String,Int>()
}

class FloorPlanCanvas(left:Int,top:Int,canvasWidth:Int,canvasHeight:Int,private val graph:()->BuildingGraph,
    private val view:FloorPlanView,private val graphView:BuildingGraphView,
    private val commit:(BuildingGraph)->Unit,private val failure:(String)->Unit,
    private val placementMode:Boolean=false,private val showInspector:Boolean=true,
    private val roomFootprint:((BuildingNode)->Set<P>)?=null,private val roomShell:((BuildingNode)->Set<P>)?=null,
    private val editRoomLayout:((String)->Unit)?=null):AbstractWidget(left,top,canvasWidth,canvasHeight,tr("footprint")) {
    private val font get()=Minecraft.getInstance().font
    private val sidebarWidth get()=(width*0.30).roundToInt().coerceIn(136,244)
    private val mapX get()=x+if(showInspector) sidebarWidth+6 else 0
    private val mapY get()=y+27
    private val mapWidth get()=(width-if(showInspector) sidebarWidth+6 else 0).coerceAtLeast(1)
    private val mapHeight get()=(height-51).coerceAtLeast(1)
    private fun roomCells(room:BuildingNode)=roomFootprint?.invoke(room) ?: RoomGeometry.cells(room.rectangles)
    private fun shell(room:BuildingNode)=roomShell?.invoke(room) ?: RoomWallJoints.shell(room)
    private val rooms get()=graph().let { current -> current.nodes.filter { it.kind==BuildingKind.ROOM }.map(current::resolvedNode) }
    private val selected get()=rooms.firstOrNull { it.id==view.selected }
    fun segmentRoomSelected()=selected?.roomConfig?.mode==RoomMode.SEGMENTS
    private val storey get()=graph().nodes.firstOrNull { it.id==view.storey && it.kind==BuildingKind.STOREY }
    private val inspector=RoomPlanInspector(x,y,sidebarWidth,canvasHeight,graph,{view.selected},::selectRoom,::roomColor,::commitGraph,failure,roomFootprint)
    private data class Instance(val room:BuildingNode,val offset:P,val index:Int)
    private data class Stroke(val room:BuildingNode,val offset:P,val first:P,var last:P,val erase:Boolean,var preview:Result<BuildingGraph>?=null)
    private var stroke:Stroke?=null
    private data class PlacementDrag(val storey:String,val instance:Instance,val startX:Double,val startY:Double,
        var offset:P=instance.offset,var preview:Result<BuildingGraph>?=null)
    private var placementDrag:PlacementDrag?=null
    private data class TableDrag(val original:BuildingProjection,val startX:Double,val startY:Double,var next:BuildingProjection=original)
    private var tableDrag:TableDrag?=null
    private val tableReference get()=tableDrag?.next ?: graph().projection
    private var panning=false

    init {
        if(rooms.none { it.id==view.selected }) view.selected=rooms.firstOrNull { it.id==graphView.selected }?.id ?: rooms.firstOrNull()?.id.orEmpty()
        selectContext()
        if(placementMode) {
            val available=instances()
            val index=graphView.room[view.storey] ?: available.firstOrNull { it.room.id==view.selected }?.index ?: available.firstOrNull()?.index
            (available.firstOrNull { it.index==index } ?: available.firstOrNull { it.room.id==view.selected } ?: available.firstOrNull())?.let(::selectInstance)
            storey?.let { graphView.selected=it.id }
        }
        if(view.fittedContext!=fitContext()) view.fitted=false
        rooms.forEach { roomColor(it.id) }
        inspector.scrollOffset=view.inspectorScroll
        if(view.inspectorRoom!=view.selected) { inspector.revealRoom(view.selected);view.inspectorRoom=view.selected }
        view.inspectorScroll=inspector.scrollOffset
    }
    private fun commitGraph(changed:BuildingGraph) {
        view.inspectorScroll=inspector.scrollOffset
        if(changed==graph()) return
        validateChangedGraph(changed)
        commit(changed)
        if(graph()==changed) failure(ui("floorplan_updated"))
    }
    private fun selectContext() {
        if(placementMode) {
            val next=storey ?: graph().nodes.firstOrNull { it.id==graphView.selected && it.kind==BuildingKind.STOREY }
                ?: graph().nodes.firstOrNull { it.kind==BuildingKind.STOREY && it.storeyMode=="rooms" }
            if(view.storey!=next?.id.orEmpty()) { view.storey=next?.id.orEmpty();view.fitted=false }
            return
        }
        val choices=graph().nodes.filter { it.kind==BuildingKind.STOREY && it.storeyMode=="rooms" && view.selected in it.inputs }
        val next=choices.firstOrNull { it.id==view.storey } ?: choices.firstOrNull { it.id==graphView.selected } ?: choices.firstOrNull()
        if(view.storey!=next?.id.orEmpty()) { view.storey=next?.id.orEmpty();view.fitted=false }
    }
    private fun selectRoom(id:String) {
        stroke=null;placementDrag=null;view.selected=id;selectContext()
        if(placementMode) {
            instances().firstOrNull { it.room.id==id }?.let(::selectInstance)
        } else { graphView.selected=id;view.fitted=false }
        inspector.revealRoom(id)
        view.inspectorRoom=id;view.inspectorScroll=inspector.scrollOffset
    }
    private fun instances():List<Instance> {
        val level=storey
        if(placementMode && level?.storeyMode!="rooms") return emptyList()
        return level?.inputs?.mapIndexedNotNull { index,id -> rooms.firstOrNull { it.id==id }?.let { Instance(it,level.offsets.getOrNull(index) ?: P(),index) } }
            ?: if(placementMode) emptyList() else selected?.let { listOf(Instance(it,P(),-1)) }.orEmpty()
    }
    private fun selectInstance(instance:Instance) {
        view.selected=instance.room.id
        graphView.room[view.storey]=instance.index
        graphView.selected=view.storey
    }
    fun selectedInstanceIndex():Int {
        val available=instances()
        return available.firstOrNull { it.index==graphView.room[view.storey] }?.index ?: available.firstOrNull()?.index ?: -1
    }
    private fun visibleInstances()=instances().map { instance ->
        placementDrag?.takeIf { it.storey==view.storey && it.instance.index==instance.index }?.let { instance.copy(offset=it.offset) } ?: instance
    }
    fun roomColor(id:String):Int=view.colors.getOrPut(id) {
        val index=view.colors.size
        val palette=listOf(0xff65b897.toInt(),0xffdcaa62.toInt(),0xff7eace0.toInt(),0xffc88ba9.toInt(),0xffb4bf6c.toInt(),0xffb29bd8.toInt(),0xffd88a6b.toInt(),0xff6abec4.toInt())
        if(index<palette.size) palette[index] else {
            val hue=(index*0.61803398875%1.0)*6
            val chroma=140;val secondary=(chroma*(1-abs(hue%2-1))).toInt();val base=85
            val channels=when(hue.toInt()) { 0->listOf(chroma,secondary,0);1->listOf(secondary,chroma,0);2->listOf(0,chroma,secondary);3->listOf(0,secondary,chroma);4->listOf(secondary,0,chroma);else->listOf(chroma,0,secondary) }
            (0xff000000.toInt() or ((channels[0]+base) shl 16) or ((channels[1]+base) shl 8) or (channels[2]+base))
        }
    }
    private fun fitContext()="$placementMode:$showInspector:$width:$height:${view.storey}"
    fun fit() {
        val cells=instances().flatMap { instance -> (if(placementMode) shell(instance.room) else roomCells(instance.room)).map { it+instance.offset } }
            .let { if(placementMode) it+tableReference.table.copy(y=0) else it }
        val minX=(cells.minOfOrNull { it.x } ?: 0)-2
        val minZ=(cells.minOfOrNull { it.z } ?: 0)-2
        val spanX=(cells.maxOfOrNull { it.x } ?: 3)-minX+3
        val spanZ=(cells.maxOfOrNull { it.z } ?: 3)-minZ+3
        view.step=minOf(mapWidth.toDouble()/spanX,mapHeight.toDouble()/spanZ,32.0).coerceIn(1.0,40.0)
        view.panX=(mapWidth-spanX*view.step)/2-minX*view.step
        view.panY=(mapHeight-spanZ*view.step)/2-minZ*view.step
        view.fitted=true;view.fittedContext=fitContext()
    }
    fun gridPoint(cellX:Double,cellZ:Double)=mapX+view.panX+cellX*view.step to mapY+view.panY+cellZ*view.step
    fun tablePoint()=gridPoint(tableReference.table.x+0.5,tableReference.table.z+0.5)
    fun instancePoint(index:Int):Pair<Double,Double> {
        val instance=visibleInstances().first { it.index==index }
        val point=(roomCells(instance.room).minWithOrNull(compareBy({ it.z },{ it.x })) ?: P())+instance.offset
        return gridPoint(point.x+0.5,point.z+0.5)
    }
    fun roomPoint(id:String)=inspector.roomPoint(id)
    fun wallPoint(key:String)=inspector.wallPoint(key)
    fun sourcePoint(id:String)=inspector.sourcePoint(id)
    fun hasOpenMenu()=showInspector && inspector.hasOpenMenu()
    fun revealRoom(id:String) { inspector.revealRoom(id);view.inspectorScroll=inspector.scrollOffset }
    fun revealWall(key:String) { inspector.revealWall(key);view.inspectorScroll=inspector.scrollOffset }
    private fun inMap(mx:Double,my:Double)=mx>=mapX && mx<mapX+mapWidth && my>=mapY && my<mapY+mapHeight
    private fun cell(mx:Double,my:Double)=P(floor((mx-mapX-view.panX)/view.step).toInt(),0,floor((my-mapY-view.panY)/view.step).toInt())
    private fun candidate(drag:Stroke):BuildingNode=if(drag.first==drag.last && !drag.erase) RoomFootprintEditor.toggleCell(drag.room,drag.first)
        else RoomFootprintEditor.rectangle(drag.room,drag.first,drag.last,drag.erase)
    private fun strokePreview(drag:Stroke):Result<BuildingGraph> {
        if(drag.preview==null) drag.preview=runCatching { changedGraph(candidate(drag)) }
        return drag.preview!!
    }
    private fun placementGraph(drag:PlacementDrag):BuildingGraph {
        require(drag.offset.y==0 && drag.offset.x in -64..64 && drag.offset.z in -64..64) { ui("floorplan_bounds") }
        val changed=DiagramEdits.moveStoreyRoom(graph(),drag.storey,drag.instance.index,drag.offset)
        validateStoreyOverlap(changed,changed.nodes.first { it.id==drag.storey })
        return changed
    }
    private fun placementPreview(drag:PlacementDrag):Result<BuildingGraph> {
        if(drag.preview==null) drag.preview=runCatching { placementGraph(drag) }
        return drag.preview!!
    }
    private fun updatePlacementDrag(drag:PlacementDrag,mx:Double,my:Double) {
        val offset=drag.instance.offset
        val next=P((offset.x+((mx-drag.startX)/view.step).roundToInt()).coerceIn(-64,64),0,
            (offset.z+((my-drag.startY)/view.step).roundToInt()).coerceIn(-64,64))
        if(next!=drag.offset) { drag.offset=next;drag.preview=null }
    }
    private fun updateTableDrag(drag:TableDrag,mx:Double,my:Double) {
        val table=drag.original.table
        drag.next=drag.original.copy(table=table.copy(x=(table.x+((mx-drag.startX)/view.step).roundToInt()).coerceIn(-128,128),
            z=(table.z+((my-drag.startY)/view.step).roundToInt()).coerceIn(-128,128)))
    }
    private fun validateStoreyOverlap(changed:BuildingGraph,level:BuildingNode) {
        val occupied=mutableSetOf<P>()
        for((index,id) in level.inputs.withIndex()) {
            val source=changed.nodes.firstOrNull { it.id==id && it.kind==BuildingKind.ROOM } ?: continue
            val offset=level.offsets.getOrNull(index) ?: P()
            val cells=RoomGeometry.cells(changed.resolvedNode(source).rectangles).map { it+offset }
            require(cells.none { it in occupied }) { ui("floorplan_overlap") }
            occupied+=cells
        }
    }
    private fun editMessage(error:Throwable?):String=when(error?.message) {
        "A room needs at least one block"->ui("floorplan_keep_cell")
        "Room footprint must stay connected"->ui("floorplan_connected")
        "Room footprint must not enclose holes"->ui("floorplan_no_holes")
        "Room blocks exceed grid bounds"->ui("floorplan_bounds")
        else->error?.message.orEmpty()
    }
    private fun validateChangedGraph(changed:BuildingGraph) {
        val original=graph();val previous=original.nodes.associateBy { it.id }
        val affected=changed.nodes.filter { it.kind==BuildingKind.ROOM }.filter { room ->
            val before=previous[room.id]
            before==null || before.kind!=BuildingKind.ROOM || original.resolvedNode(before).rectangles!=changed.resolvedNode(room).rectangles
        }.mapTo(mutableSetOf()) { it.id }
        for(storey in changed.nodes.filter { it.kind==BuildingKind.STOREY && it.storeyMode=="rooms" && it.inputs.any(affected::contains) }) {
            validateStoreyOverlap(changed,storey)
        }
        BuildingGraphCompiler.structure(changed)
    }
    private fun changedGraph(room:BuildingNode):BuildingGraph {
        val changed=graph().reshapeRoom(room.id,room.rectangles)
        validateChangedGraph(changed)
        return changed
    }
    fun addRoom() {
        try {
            val original=graph()
            val template=selected
            val storey=original.nodes.firstOrNull { it.id==view.storey && it.kind==BuildingKind.STOREY && it.storeyMode=="rooms" }
                ?: if(placementMode) null else original.nodes.firstOrNull { it.kind==BuildingKind.STOREY && it.storeyMode=="rooms" }
            require(!placementMode || storey!=null) { ui("floorplan_select_storey") }
            val assembly=template?.assembly ?: original.nodes.any { it.assembly || it.kind==BuildingKind.ROOM_GEOMETRY }
            require(original.nodes.size+(if(assembly) 2 else 1)<=96) { ui("bg_node_limit") }
            val rectangles=listOf(Rect(width=1,length=1));val roomY=30+rooms.size*40
            val geometry=if(assembly) BuildingAssembly.newNode(BuildingKind.ROOM_GEOMETRY,UUID.randomUUID().toString(),ui("bg_room_geometry"),300,roomY)
                .copy(coordinates=listOf(P())) else null
            val initial=if(assembly) BuildingAssembly.newNode(BuildingKind.ROOM,UUID.randomUUID().toString(),ui("bg_room_index",rooms.size+1),600,roomY)
                else BuildingNode(id=UUID.randomUUID().toString(),kind=BuildingKind.ROOM,name=ui("bg_room_index",rooms.size+1),x=600,y=roomY)
            val room=initial.copy(height=template?.height ?: 4,rectangles=rectangles,geometry=geometry?.id.orEmpty(),
                floor=template?.floor ?: original.nodes.firstOrNull { it.kind==BuildingKind.FLOOR }?.id.orEmpty(),
                ceiling=template?.ceiling ?: original.nodes.firstOrNull { it.kind==BuildingKind.CEILING }?.id.orEmpty(),
                walls=if(assembly && template!=null) RoomGeometry.reshape(template,rectangles).walls else emptyMap(),
                corner=if(assembly) template?.corner.orEmpty() else "")
            var changed=original.copy(nodes=original.nodes+listOfNotNull(geometry,room))
            if(storey!=null) {
                val index=storey.inputs.indexOfFirst { it.isEmpty() }.takeIf { it>=0 } ?: storey.inputs.size
                require(index<16) { ui("floorplan_room_limit") }
                val occupied=storey.inputs.flatMapIndexed { position,id -> rooms.firstOrNull { it.id==id }?.let { source -> shell(source).map { it+(storey.offsets.getOrNull(position) ?: P()) } }.orEmpty() }.toSet()
                val shell=shell(room)
                val offset=sequence {
                    yield(P((occupied.maxOfOrNull { it.x } ?: -2)+2,0,0))
                    for(row in -64..64) for(column in -64..64) yield(P(column,0,row))
                }.firstOrNull { candidate -> candidate.x in -64..64 && shell.none { it+candidate in occupied } } ?: error(ui("floorplan_no_space"))
                val inputs=storey.inputs.toMutableList();if(index==inputs.size) inputs+=room.id else inputs[index]=room.id
                changed=changed.replace(storey.copy(inputs=inputs,offsets=List(inputs.size) { if(it==index) offset else storey.offsets.getOrNull(it) ?: P() }))
                view.storey=storey.id
            }
            BuildingGraphCompiler.structure(changed)
            view.selected=room.id
            if(placementMode && storey!=null) {
                graphView.selected=storey.id
                graphView.room[storey.id]=changed.nodes.first { it.id==storey.id }.inputs.indexOfLast { it==room.id }
            } else graphView.selected=room.id
            view.fitted=false;commitGraph(changed)
        } catch(error:Exception) { failure(error.message.orEmpty()) }
    }
    fun deleteSelected() {
        val room=selected ?: return
        val original=graph()
        var changed=original.remove(room.id)
        for(storey in original.nodes.filter { it.kind==BuildingKind.STOREY && it.storeyMode=="rooms" && room.id in it.inputs }) {
            val remaining=storey.inputs.indices.filter { storey.inputs[it]!=room.id }
            changed=changed.replace(storey.copy(inputs=remaining.map { storey.inputs[it] }.ifEmpty { listOf("") },
                offsets=remaining.map { storey.offsets.getOrNull(it) ?: P() }.ifEmpty { listOf(P()) }))
        }
        BuildingGraphCompiler.structure(changed)
        view.selected=changed.nodes.firstOrNull { it.kind==BuildingKind.ROOM }?.id.orEmpty();view.fitted=false
        graphView.selected=view.selected.ifEmpty { "building" };commitGraph(changed)
    }
    private fun fillCell(graphics:GuiGraphics,cell:P,color:Int) {
        val first=gridPoint(cell.x.toDouble(),cell.z.toDouble());val last=gridPoint(cell.x+1.0,cell.z+1.0)
        val gap=if(view.step>=5) 1 else 0
        graphics.fill(first.first.floorToInt()+gap,first.second.floorToInt()+gap,last.first.floorToInt(),last.second.floorToInt(),color)
    }
    private fun Double.floorToInt()=floor(this).toInt()
    private fun drawTableMarker(graphics:GuiGraphics) {
        val table=tableReference.table
        val first=gridPoint(table.x.toDouble(),table.z.toDouble());val last=gridPoint(table.x+1.0,table.z+1.0)
        val left=first.first.floorToInt();val top=first.second.floorToInt();val size=(last.first-first.first).roundToInt().coerceAtLeast(1)
        graphics.fill(left,top,left+size,top+size,ArchitectTheme.brassDark)
        graphics.renderOutline(left,top,size,size,ArchitectTheme.brass)
        val pose=graphics.pose();pose.pushPose();pose.translate(left.toDouble(),top.toDouble(),10.0);pose.scale(size/16f,size/16f,1f)
        graphics.renderItem(net.minecraft.world.item.ItemStack(Architect.TABLE_ITEM.get()),0,0);graphics.flush();pose.popPose()
    }
    override fun renderWidget(graphics:GuiGraphics,mx:Int,my:Int,delta:Float) {
        if(!view.fitted) fit()
        val selectedInstance=if(placementMode) visibleInstances().firstOrNull { it.index==selectedInstanceIndex() } else null
        val title=ui("footprint")+if(placementMode) storey?.let { " · "+it.name }.orEmpty() else selected?.let { " · "+it.name }.orEmpty()
        ArchitectTheme.panel(graphics,mapX,y,mapWidth,height,font.plainSubstrByWidth(title,(mapWidth-12).coerceAtLeast(1)))
        ArchitectTheme.inset(graphics,mapX,mapY,mapWidth,mapHeight,ArchitectTheme.blueprint)
        graphics.enableScissor(mapX+2,mapY+2,mapX+mapWidth-2,mapY+mapHeight-2)
        val minimum=cell(mapX.toDouble(),mapY.toDouble());val maximum=cell((mapX+mapWidth).toDouble(),(mapY+mapHeight).toDouble())
        for(column in minimum.x..maximum.x+1) {
            val screen=gridPoint(column.toDouble(),0.0).first.floorToInt()
            graphics.fill(screen,mapY,screen+1,mapY+mapHeight,if(column==0) ArchitectTheme.brassDark else ArchitectTheme.grid)
        }
        for(row in minimum.z..maximum.z+1) {
            val screen=gridPoint(0.0,row.toDouble()).second.floorToInt()
            graphics.fill(mapX,screen,mapX+mapWidth,screen+1,if(row==0) ArchitectTheme.brassDark else ArchitectTheme.grid)
        }
        val preview=stroke?.let(::strokePreview)
        val placementPreview=placementDrag?.let(::placementPreview)
        val instances=visibleInstances()
        if(placementMode) {
            val interiors=instances.flatMap { instance -> roomCells(instance.room).map { it+instance.offset } }.toSet()
            val shells=instances.flatMap { instance -> shell(instance.room).map { it+instance.offset } }.toSet()
            for(cell in shells-interiors) fillCell(graphics,cell,ArchitectTheme.brassDark)
        }
        for(instance in instances.sortedBy { if(placementMode) it.index==selectedInstanceIndex() else it.room.id==view.selected }) {
            val previewGraph=preview?.getOrNull()
            val room=if(previewGraph!=null) previewGraph.nodes.firstOrNull { it.id==instance.room.id }?.let(previewGraph::resolvedNode) ?: instance.room else instance.room
            val cells=roomCells(room)
            val active=if(placementMode) instance.index==selectedInstanceIndex() else room.id==view.selected
            val invalid=placementPreview?.isFailure==true && placementDrag?.instance?.index==instance.index
            val color=if(invalid) ArchitectTheme.danger else roomColor(room.id)
            for(local in cells) fillCell(graphics,local+instance.offset,color)
            if(active || placementMode) for(local in cells) {
                val cell=local+instance.offset;val first=gridPoint(cell.x.toDouble(),cell.z.toDouble());val last=gridPoint(cell.x+1.0,cell.z+1.0)
                val left=first.first.floorToInt();val top=first.second.floorToInt();val right=last.first.floorToInt();val bottom=last.second.floorToInt()
                val outline=if(active) ArchitectTheme.text else ArchitectTheme.shadow
                if(local+P(0,0,-1) !in cells) graphics.fill(left,top,right,top+1,outline)
                if(local+P(1,0,0) !in cells) graphics.fill(right-1,top,right,bottom,outline)
                if(local+P(0,0,1) !in cells) graphics.fill(left,bottom-1,right,bottom,outline)
                if(local+P(-1,0,0) !in cells) graphics.fill(left,top,left+1,bottom,outline)
            }
            if(placementMode && view.step>=10 && cells.isNotEmpty()) {
                val local=cells.minWith(compareBy({ it.z },{ it.x }))+instance.offset
                val point=gridPoint(local.x.toDouble(),local.z.toDouble())
                val label="${instance.index+1}. ${room.name}"
                val available=(cells.filter { it.z+instance.offset.z==local.z }.maxOf { it.x+instance.offset.x+1 }-local.x)*view.step-5
                graphics.drawString(font,font.plainSubstrByWidth(label,available.toInt().coerceAtLeast(1)),point.first.toInt()+3,point.second.toInt()+3,ArchitectTheme.shadow,false)
            }
        }
        if(placementMode) drawTableMarker(graphics)
        stroke?.let { drag ->
            val first=gridPoint((minOf(drag.first.x,drag.last.x)+drag.offset.x).toDouble(),(minOf(drag.first.z,drag.last.z)+drag.offset.z).toDouble())
            val last=gridPoint(maxOf(drag.first.x,drag.last.x)+drag.offset.x+1.0,maxOf(drag.first.z,drag.last.z)+drag.offset.z+1.0)
            val color=if(preview?.isFailure==true) ArchitectTheme.danger else ArchitectTheme.brass
            graphics.fill(first.first.toInt(),first.second.toInt(),last.first.toInt(),last.second.toInt(),(color and 0x00ffffff) or 0x40000000)
            graphics.renderOutline(first.first.toInt(),first.second.toInt(),(last.first-first.first).toInt().coerceAtLeast(1),(last.second-first.second).toInt().coerceAtLeast(1),color)
        }
        if(!placementMode && stroke==null && inMap(mx.toDouble(),my.toDouble()) && selected!=null) {
            val hovered=cell(mx.toDouble(),my.toDouble());fillCell(graphics,hovered,0x408bd082)
        }
        graphics.flush();graphics.disableScissor()
        val invalid=preview?.isFailure==true || placementPreview?.isFailure==true
        val hint=when {
            placementPreview?.isFailure==true->editMessage(placementPreview.exceptionOrNull())
            preview?.isFailure==true->editMessage(preview.exceptionOrNull())
            placementMode && instances.isEmpty()->ui("floorplan_empty_storey")
            placementMode->ui("floorplan_storey_help")
            selected==null->ui("floorplan_empty_rooms")
            segmentRoomSelected()->ui("semantic_floorplan_help")
            else->ui("floorplan_grid_help")
        }
        if(placementMode && selectedInstance!=null) {
            val label="${selectedInstance.index+1}. ${selectedInstance.room.name} · X ${selectedInstance.offset.x} · Z ${selectedInstance.offset.z}"
            graphics.drawString(font,font.plainSubstrByWidth(label,(mapWidth-12).coerceAtLeast(1)),mapX+6,mapY+5,ArchitectTheme.text,false)
        }
        graphics.drawString(font,font.plainSubstrByWidth(hint,(mapWidth-12).coerceAtLeast(1)),mapX+6,y+height-16,if(invalid) ArchitectTheme.danger else ArchitectTheme.muted,false)
        if(showInspector) inspector.render(graphics,mx,my,delta)
    }
    override fun mouseClicked(mx:Double,my:Double,button:Int):Boolean {
        if(showInspector && (inspector.hasOpenMenu() || inspector.isMouseOver(mx,my))) return inspector.mouseClicked(mx,my,button)
        if(!inMap(mx,my)) return false
        if(stroke!=null || placementDrag!=null || tableDrag!=null || panning) return true
        if(button==2) { panning=true;return true }
        if(placementMode) {
            if(button!=0) return true
            val world=cell(mx,my)
            if(world==tableReference.table.copy(y=0)) {
                placementDrag=null;tableDrag=TableDrag(graph().projection,mx,my);return true
            }
            val available=instances().asReversed()
            val instance=available.firstOrNull { world+P(-it.offset.x,0,-it.offset.z) in roomCells(it.room) }
                ?: available.firstOrNull { world+P(-it.offset.x,0,-it.offset.z) in shell(it.room) }
            if(instance!=null) {
                selectInstance(instance)
                tableDrag=null;placementDrag=PlacementDrag(view.storey,instance,mx,my)
            }
            return true
        }
        val room=selected ?: return true
        if(room.roomConfig?.mode==RoomMode.SEGMENTS) {
            val world=cell(mx,my)
            val overRoom=instances().filter { it.room.id==room.id }.any { instance -> world-instance.offset in roomCells(room)+shell(room) }
            if(button==0 && overRoom && editRoomLayout!=null) editRoomLayout.invoke(room.layout) else failure(ui("semantic_floorplan_help"))
            return true
        }
        if(button in 0..1) {
            val world=cell(mx,my)
            val footprint=roomCells(room)
            val offset=instances().filter { it.room.id==room.id }.minByOrNull { instance ->
                footprint.minOf { local -> abs(world.x-local.x-instance.offset.x)+abs(world.z-local.z-instance.offset.z) }
            }?.offset ?: P()
            val local=world+P(-offset.x,0,-offset.z)
            stroke=Stroke(room,offset,local,local,button==1)
        }
        return true
    }
    override fun mouseDragged(mx:Double,my:Double,button:Int,dx:Double,dy:Double):Boolean {
        if(panning) { view.panX+=dx;view.panY+=dy;return true }
        tableDrag?.let { if(button==0) updateTableDrag(it,mx,my);return true }
        placementDrag?.let { if(button==0) updatePlacementDrag(it,mx,my);return true }
        val drag=stroke ?: return false
        val point=cell(mx.coerceIn(mapX+2.0,mapX+mapWidth-3.0),my.coerceIn(mapY+2.0,mapY+mapHeight-3.0))
        val next=(point+P(-drag.offset.x,0,-drag.offset.z)).let { P(it.x.coerceIn(-32,63),0,it.z.coerceIn(-32,63)) }
        if(next!=drag.last) { drag.last=next;drag.preview=null }
        return true
    }
    override fun mouseReleased(mx:Double,my:Double,button:Int):Boolean {
        if(panning) { if(button==2) panning=false;return true }
        tableDrag?.let { drag ->
            if(button!=0) return true
            updateTableDrag(drag,mx,my);tableDrag=null
            try { commitGraph(graph().copy(projection=drag.next.validate())) } catch(error:Exception) { failure(editMessage(error)) }
            return true
        }
        placementDrag?.let { drag ->
            if(button!=0) return true
            updatePlacementDrag(drag,mx,my);placementDrag=null
            if(drag.offset==drag.instance.offset) return true
            try { commitGraph(placementGraph(drag)) } catch(error:Exception) { failure(editMessage(error)) }
            return true
        }
        val drag=stroke ?: return false
        if(button!=if(drag.erase) 1 else 0) return true
        stroke=null
        try { commitGraph(strokePreview(drag).getOrThrow()) } catch(error:Exception) { failure(editMessage(error)) }
        return true
    }
    override fun mouseScrolled(mx:Double,my:Double,dx:Double,dy:Double):Boolean {
        if(showInspector && (inspector.hasOpenMenu() || inspector.isMouseOver(mx,my))) {
            val handled=inspector.mouseScrolled(mx,my,dx,dy)
            view.inspectorScroll=inspector.scrollOffset
            return handled
        }
        if(!inMap(mx,my) || stroke!=null || placementDrag!=null || tableDrag!=null) return false
        val localX=(mx-mapX-view.panX)/view.step;val localZ=(my-mapY-view.panY)/view.step
        view.step=(view.step*1.12.pow(dy)).coerceIn(1.0,48.0)
        view.panX=mx-mapX-localX*view.step;view.panY=my-mapY-localZ*view.step
        return true
    }
    override fun keyPressed(key:Int,scan:Int,mods:Int):Boolean {
        if(key==256 && (stroke!=null || placementDrag!=null || tableDrag!=null || panning)) {
            stroke=null;placementDrag=null;tableDrag=null;panning=false;return true
        }
        if(showInspector && inspector.hasOpenMenu()) return inspector.keyPressed(key,scan,mods)
        return false
    }
    override fun updateWidgetNarration(output:NarrationElementOutput) {
        output.add(NarratedElementType.TITLE,tr("footprint"))
        output.add(NarratedElementType.USAGE,tr(if(placementMode) "floorplan_storey_help" else if(segmentRoomSelected()) "semantic_floorplan_help" else "floorplan_grid_help"))
    }
}
