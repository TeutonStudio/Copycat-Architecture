package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import kotlin.math.roundToInt
import kotlin.math.sign

internal class RoomPlanInspector(left:Int,top:Int,canvasWidth:Int,canvasHeight:Int,private val graph:()->BuildingGraph,
    private val selected:()->String,private val select:(String)->Unit,private val roomColor:(String)->Int,
    private val commit:(BuildingGraph)->Unit,private val failure:(String)->Unit,
    private val roomFootprint:((BuildingNode)->Set<P>)?=null):AbstractWidget(left,top,canvasWidth,canvasHeight,tr("floorplan_rooms",0)) {
    private val font get()=Minecraft.getInstance().font
    private val contentTop get()=y+28
    private val contentHeight get()=(height-34).coerceAtLeast(1)
    private var scroll=0.0
    var scrollOffset:Double
        get() { clampScroll(rows());return scroll }
        set(value) { scroll=value.coerceIn(0.0,maxScroll(rows())) }
    private data class Row(val room:BuildingNode,val key:String,val label:String,val top:Int,val height:Int,val source:String="")
    private data class Menu(val room:String,val key:String,val label:String,var first:Int=0,var focus:Int=0)
    private data class MenuBox(val left:Int,val top:Int,val width:Int,val rows:Int)
    private var menu:Menu?=null
    private val rooms get()=graph().let { current -> current.nodes.filter { it.kind==BuildingKind.ROOM }.map(current::resolvedNode) }
    private fun roomName(room:BuildingNode)=room.name.takeUnless { it.isEmpty() || it=="Room" } ?: ui("bg_room")
    private fun sourceName(source:String):String {
        if(source.isEmpty()) return ui("bg_no_connection")
        if(source=="empty_wall") return ui("empty_wall")
        val node=graph().nodes.firstOrNull { it.id==source } ?: return ui("bg_no_connection")
        return if(node.name.isNotEmpty() && node.name !in setOf("Floor","Ceiling","Room","Solid wall")) node.name
        else if(node.name=="Solid wall") ui("bg_solid_wall") else ui("bg_"+node.kind.name.lowercase())
    }
    private fun rows():List<Row> {
        var top=0
        return buildList {
            for((index,room) in rooms.withIndex()) {
                add(Row(room,"","${index+1}. ${roomName(room)}",top,25));top+=25
                if(room.id!=selected()) continue
                BuildingPorts.of(room,graph()).firstOrNull { it.key=="geometry" }?.let { port ->
                    add(Row(room,"geometry",ui("bg_room_geometry"),top,32,port.source));top+=32
                }
                if(room.roomConfig?.mode==RoomMode.SEGMENTS) {
                    BuildingPorts.of(room,graph()).firstOrNull { it.key=="layout" }?.let { port ->
                        add(Row(room,"layout",ui("bg_room_layout"),top,32,port.source));top+=32
                    }
                    val cells=roomFootprint?.invoke(room).orEmpty()
                    val instances=graph().nodes.firstOrNull { it.id==room.layout }?.roomLayout?.instances.orEmpty().size
                    add(Row(room,"summary",ui("semantic_plan_summary",cells.size,instances),top,19));top+=19
                    for((key,label) in listOf("floor" to "bg_floor","ceiling" to "bg_ceiling")) {
                        BuildingPorts.of(room,graph()).firstOrNull { it.key==key }?.let { port ->
                            add(Row(room,key,ui(label),top,32,port.source));top+=32
                        }
                    }
                    top+=6
                    continue
                }
                val edges=RoomWallJoints.runs(room)
                add(Row(room,"summary",ui("floorplan_room_summary",RoomGeometry.cells(room.rectangles).size,edges.size),top,19));top+=19
                add(Row(room,"walls",ui("floorplan_walls"),top,16));top+=16
                for(edge in edges) {
                    val direction=ui(listOf("north","east","south","west")[edge.direction])
                    add(Row(room,"wall:${edge.id}",ui("floorplan_wall",direction,edge.length),top,32,room.walls[edge.id].orEmpty().ifEmpty { "empty_wall" }));top+=32
                }
                top+=5
                for(edge in RoomVerticalEdges.of(room.rectangles)) {
                    add(Row(room,"edge:${edge.id}",ui("bg_vertical_edge_port",edge.vertex.x,edge.vertex.z,room.height),top,32,room.verticalEdges.orEmpty()[edge.id].orEmpty()));top+=32
                }
                for((key,label) in listOf("floor" to "bg_floor","ceiling" to "bg_ceiling","corner" to "bg_corner_copycat")) {
                    BuildingPorts.of(room,graph()).firstOrNull { it.key==key }?.let { port ->
                        add(Row(room,key,ui(label),top,32,port.source));top+=32
                    }
                }
                top+=6
            }
        }
    }
    private fun maxScroll(entries:List<Row>)=((entries.lastOrNull()?.let { it.top+it.height } ?: 0)-contentHeight).coerceAtLeast(0).toDouble()
    private fun clampScroll(entries:List<Row>) { scroll=scroll.coerceIn(0.0,maxScroll(entries)) }
    fun hasOpenMenu()=menu!=null
    private fun reveal(row:Row,entries:List<Row>) {
        if(row.top<scroll) scroll=row.top.toDouble()
        if(row.top+row.height>scroll+contentHeight) scroll=(row.top+row.height-contentHeight).toDouble()
        clampScroll(entries)
    }
    fun revealRoom(id:String) {
        val entries=rows();val row=entries.firstOrNull { it.room.id==id && it.key.isEmpty() } ?: return
        reveal(row,entries)
    }
    fun revealWall(key:String) {
        val entries=rows();val row=entries.firstOrNull { it.room.id==selected() && it.key==key } ?: return
        reveal(row,entries)
    }
    fun roomPoint(id:String):Pair<Double,Double> {
        val entries=rows();clampScroll(entries)
        val row=entries.first { it.room.id==id && it.key.isEmpty() }
        return x+width/2.0 to contentTop+row.top+row.height/2.0-scroll
    }
    fun wallPoint(key:String):Pair<Double,Double> {
        val entries=rows();clampScroll(entries)
        val row=entries.first { it.room.id==selected() && it.key==key }
        return x+width/2.0 to contentTop+row.top+row.height/2.0-scroll
    }
    fun sourcePoint(id:String):Pair<Double,Double> {
        val opened=checkNotNull(menu);val choices=options(opened);val box=menuBox(choices)
        val index=choices.indexOfFirst { it.first==id };check(index>=0)
        return box.left+box.width/2.0 to box.top+(index-opened.first)*21+10.5
    }
    private fun text(graphics:GuiGraphics,label:String,left:Int,top:Int,color:Int=ArchitectTheme.text,available:Int=width-28) {
        graphics.drawString(font,font.plainSubstrByWidth(label,available.coerceAtLeast(1)),left,top,color,false)
    }
    override fun renderWidget(graphics:GuiGraphics,mx:Int,my:Int,delta:Float) {
        val entries=rows();clampScroll(entries)
        ArchitectTheme.panel(graphics,x,y,width,height,ui("floorplan_rooms",rooms.size))
        graphics.enableScissor(x+4,contentTop,x+width-4,y+height-5)
        if(entries.isEmpty()) text(graphics,ui("floorplan_empty_rooms"),x+10,contentTop+6,ArchitectTheme.muted)
        for(row in entries) {
            val top=(contentTop+row.top-scroll).roundToInt()
            if(top+row.height<contentTop || top>=y+height-5) continue
            val left=x+7;val rowWidth=(width-19).coerceAtLeast(1)
            val hovered=menu==null && mx>=left && mx<left+rowWidth && my>=top && my<top+row.height && my>=contentTop && my<y+height-5
            when(row.key) {
                "" -> {
                    ArchitectTheme.button(graphics,left,top,rowWidth,23,hovered,row.room.id==selected())
                    val color=roomColor(row.room.id)
                    graphics.fill(left+7,top+6,left+16,top+16,ArchitectTheme.shadow)
                    graphics.fill(left+8,top+7,left+15,top+15,color)
                    text(graphics,row.label,left+21,top+8,if(row.room.id==selected()) ArchitectTheme.brass else ArchitectTheme.text,rowWidth-29)
                }
                "summary" -> text(graphics,row.label,left+5,top+5,ArchitectTheme.muted,rowWidth-10)
                "walls" -> {
                    text(graphics,row.label,left+5,top+4,ArchitectTheme.brass,rowWidth-10)
                    graphics.fill(left+5,top+14,left+rowWidth-5,top+15,ArchitectTheme.shadow)
                }
                else -> {
                    ArchitectTheme.button(graphics,left+4,top,rowWidth-4,30,hovered)
                    graphics.fill(left+7,top+5,left+9,top+24,roomColor(row.room.id))
                    text(graphics,row.label,left+13,top+4,ArchitectTheme.text,rowWidth-27)
                    text(graphics,sourceName(row.source),left+13,top+17,ArchitectTheme.muted,rowWidth-27)
                    text(graphics,"▾",left+rowWidth-12,top+11,ArchitectTheme.brass,9)
                }
            }
        }
        if(maxScroll(entries)>0) {
            val total=contentHeight+maxScroll(entries);val thumb=(contentHeight*contentHeight/total).roundToInt().coerceAtLeast(10).coerceAtMost(contentHeight)
            val top=contentTop+((contentHeight-thumb)*scroll/maxScroll(entries)).roundToInt()
            graphics.fill(x+width-8,contentTop,x+width-6,contentTop+contentHeight,ArchitectTheme.shadow)
            graphics.fill(x+width-8,top,x+width-6,top+thumb,ArchitectTheme.brass)
        }
        graphics.disableScissor()
        renderMenu(graphics,mx,my)
    }
    private fun options(opened:Menu):List<Pair<String,String>> {
        val current=graph();val room=current.nodes.firstOrNull { it.id==opened.room && it.kind==BuildingKind.ROOM } ?: return emptyList()
        val port=BuildingPorts.of(room,current).firstOrNull { it.key==opened.key } ?: return emptyList()
        return listOf("" to ui("bg_no_connection"))+current.nodes.filter { candidate ->
            candidate.id!=room.id && candidate.kind in port.accepts && runCatching { current.connect(room.id,opened.key,candidate.id) }.isSuccess
        }.map { it.id to sourceName(it.id) }
    }
    private fun menuBox(choices:List<Pair<String,String>>):MenuBox {
        val count=minOf(choices.size,((height-54)/21).coerceAtLeast(1))
        return MenuBox(x+8,y+49,(width-16).coerceAtLeast(1),count)
    }
    private fun currentSource(opened:Menu)=graph().let { current -> current.nodes.firstOrNull { it.id==opened.room }?.let { room -> BuildingPorts.of(room,current).firstOrNull { it.key==opened.key }?.source }.orEmpty() }
    private fun openMenu(row:Row) {
        val opened=Menu(row.room.id,row.key,row.label);val choices=options(opened);if(choices.isEmpty()) return
        val box=menuBox(choices);opened.focus=choices.indexOfFirst { it.first==currentSource(opened) }.coerceAtLeast(0)
        opened.first=(opened.focus-box.rows/2).coerceIn(0,(choices.size-box.rows).coerceAtLeast(0));menu=opened
    }
    private fun renderMenu(graphics:GuiGraphics,mx:Int,my:Int) {
        val opened=menu ?: return;val choices=options(opened)
        if(choices.isEmpty()) { menu=null;return }
        val box=menuBox(choices);opened.focus=opened.focus.coerceIn(0,choices.lastIndex);opened.first=opened.first.coerceIn(0,(choices.size-box.rows).coerceAtLeast(0))
        val current=currentSource(opened)
        graphics.flush();graphics.pose().pushPose();graphics.pose().translate(0f,0f,500f)
        graphics.enableScissor(x+4,contentTop,x+width-4,y+height-5)
        ArchitectTheme.bevel(graphics,x+5,contentTop,width-10,(box.top-contentTop)+box.rows*21+3,ArchitectTheme.panel)
        text(graphics,ui("floorplan_sources"),x+11,contentTop+5,ArchitectTheme.brass,width-23)
        for(index in 0 until box.rows) {
            val choice=choices[index+opened.first];val top=box.top+index*21
            val hovered=mx>=box.left && mx<box.left+box.width && my>=top && my<top+21
            ArchitectTheme.button(graphics,box.left,top,box.width,21,hovered || index+opened.first==opened.focus,choice.first==current)
            text(graphics,choice.second,box.left+6,top+7,if(choice.first==current) ArchitectTheme.brass else ArchitectTheme.text,box.width-17)
        }
        if(choices.size>box.rows) {
            val track=box.rows*21;val thumb=(track*box.rows/choices.size).coerceAtLeast(8)
            val top=box.top+(track-thumb)*opened.first/(choices.size-box.rows)
            graphics.fill(box.left+box.width-4,top,box.left+box.width-2,top+thumb,ArchitectTheme.brass)
        }
        graphics.flush();graphics.disableScissor();graphics.pose().popPose()
    }
    private fun connect(opened:Menu,source:String) {
        menu=null
        try { commit(graph().connect(opened.room,opened.key,source)) }
        catch(error:Exception) { failure(error.message ?: ui("value_invalid")) }
    }
    override fun mouseClicked(mx:Double,my:Double,button:Int):Boolean {
        menu?.let { opened ->
            val choices=options(opened);val box=menuBox(choices)
            if(button==0 && mx>=box.left && mx<box.left+box.width && my>=box.top && my<box.top+box.rows*21) {
                val index=((my-box.top)/21).toInt()+opened.first;choices.getOrNull(index)?.let { connect(opened,it.first) }
            } else menu=null
            return true
        }
        if(!isMouseOver(mx,my) || button!=0 || my<contentTop || my>=y+height-5) return false
        val entries=rows();clampScroll(entries)
        val row=entries.firstOrNull { my>=contentTop+it.top-scroll && my<contentTop+it.top+it.height-scroll } ?: return true
        if(row.key.isEmpty()) { select(row.room.id);revealRoom(row.room.id) }
        else if(row.key !in setOf("summary","walls")) openMenu(row)
        return true
    }
    override fun mouseScrolled(mx:Double,my:Double,dx:Double,dy:Double):Boolean {
        menu?.let { opened ->
            val choices=options(opened);val box=menuBox(choices)
            opened.first=(opened.first-dy.sign.toInt()).coerceIn(0,(choices.size-box.rows).coerceAtLeast(0));return true
        }
        if(!isMouseOver(mx,my)) return false
        scroll=(scroll-dy*25).coerceIn(0.0,maxScroll(rows()));return true
    }
    override fun keyPressed(key:Int,scan:Int,mods:Int):Boolean {
        val opened=menu ?: return false
        if(key==256) { menu=null;return true }
        val choices=options(opened);if(choices.isEmpty()) { menu=null;return true }
        val box=menuBox(choices)
        if(key==257 || key==335) { connect(opened,choices[opened.focus.coerceIn(0,choices.lastIndex)].first);return true }
        val next=when(key) { 264->opened.focus+1;265->opened.focus-1;268->0;269->choices.lastIndex;266->opened.focus-box.rows;267->opened.focus+box.rows;else->return true }
        opened.focus=next.coerceIn(0,choices.lastIndex)
        opened.first=opened.first.coerceIn((opened.focus-box.rows+1).coerceAtLeast(0),opened.focus).coerceAtMost((choices.size-box.rows).coerceAtLeast(0))
        return true
    }
    override fun updateWidgetNarration(output:NarrationElementOutput) {
        output.add(NarratedElementType.TITLE,tr("floorplan_rooms",rooms.size))
        rooms.firstOrNull { it.id==selected() }?.let { output.add(NarratedElementType.POSITION,roomName(it)) }
        output.add(NarratedElementType.USAGE,tr("floorplan_inspector_help"))
    }
}
