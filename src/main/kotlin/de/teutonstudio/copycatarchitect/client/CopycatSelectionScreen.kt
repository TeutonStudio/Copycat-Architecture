package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.BaseShape
import de.teutonstudio.copycatarchitect.domain.BaseShapes
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

/** A machine-parts catalog; choosing an icon never moves a player's items. */
class CopycatSelectionScreen private constructor(
    private val parent:Screen?,
    private val current:BaseShape,
    private val apply:(BaseShape)->Unit
):Screen(tr("select_copycat")) {
    private val entries=BaseShapes.all
    private val stacks=entries.map { ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(it.source))) }
    private val slots=mutableListOf<SlotButton>()
    private val tabs=mutableListOf<EditorButton>()
    private val categoryKeys=listOf("all_components","copycat_group_basic","catalog_trim","copycat_group_half_layers")
    private var visibleIndices=entries.indices.toList()
    private var category=0
    private var query=""
    private var index=entries.indexOfFirst { it.id==current.id }.coerceAtLeast(0)
    private var keyboardSelection=false
    private var finished=false
    private lateinit var search:EditorTextField
    private val panelWidth get()=minOf(440,(width-16).coerceAtLeast(220))
    private val panelHeight get()=minOf(270,(height-52).coerceAtLeast(180))
    private val left get()=(width-panelWidth)/2
    private val top get()=(height-panelHeight)/2
    private val columns get()=minOf(COLUMNS,((panelWidth-84)/SLOT_SIZE).coerceAtLeast(1))
    private val gridLeft get()=left+10
    private val gridTop get()=top+77
    private val gridWidth get()=columns*SLOT_SIZE+8
    private val gridHeight get()=panelHeight-113
    private val infoLeft get()=gridLeft+gridWidth+8
    private val infoWidth get()=left+panelWidth-10-infoLeft

    override fun init() {
        slots.clear();tabs.clear()
        search=addRenderableWidget(EditorTextField(font,left+10,top+28,panelWidth-20,22,tr("catalog_search")).apply {
            setMaxLength(80);value=query;setHint(tr("catalog_search"))
        })
        val available=panelWidth-20
        categoryKeys.forEachIndexed { i,key ->
            val x=left+10+i*available/categoryKeys.size
            val next=left+10+(i+1)*available/categoryKeys.size
            tabs+=addRenderableWidget(EditorButton(x,top+53,next-x-3,tr(key)) {
                category=i;refreshEntries()
            }.apply { selected=category==i;setTooltip(Tooltip.create(tr(key))) })
        }
        refreshEntries()
        search.setResponder { query=it;refreshEntries() }
        addRenderableWidget(EditorButton(left+10,top+panelHeight-28,panelWidth-20,tr("cancel")) { onClose() })
    }

    private fun categoryOf(shape:BaseShape)=when {
        shape.source.contains("half_layer")->3
        shape.name in setOf("full","slab","stairs","roof_template")->1
        else->2
    }

    private fun refreshEntries() {
        slots.forEach { removeWidget(it) };slots.clear()
        val needle=query.trim()
        visibleIndices=entries.indices.filter { i ->
            (category==0 || categoryOf(entries[i])==category) &&
                (needle.isEmpty() || listOf(ui(entries[i].name),stacks[i].hoverName.string,entries[i].source).any { it.contains(needle,true) })
        }
        if(index !in visibleIndices) index=visibleIndices.firstOrNull() ?: -1
        tabs.forEachIndexed { i,tab -> tab.selected=category==i }
        visibleIndices.forEachIndexed { position,i ->
            slots+=addRenderableWidget(SlotButton(gridLeft+4+position%columns*SLOT_SIZE,
                gridTop+4+position/columns*SLOT_SIZE,i,entries[i],stacks[i]))
        }
    }

    override fun setInitialFocus() { slots.firstOrNull { it.entryIndex==index }?.let { setInitialFocus(it) } }

    override fun repositionElements() {
        slots.firstOrNull { it.isFocused }?.let { index=it.entryIndex }
        super.repositionElements()
    }

    /** Stable screen-space targets for the native client smoke. */
    internal fun slotPoint(source:String,discCut:Int=0):Pair<Double,Double>?=slotPoint(BaseShapes.find(source,discCut))
    internal fun slotPoint(shape:BaseShape):Pair<Double,Double>? =
        slots.firstOrNull { entries[it.entryIndex].id==shape.id }?.let { it.x+it.width/2.0 to it.y+it.height/2.0 }
    internal fun searchPoint():Pair<Double,Double> = search.x+search.width/2.0 to search.y+search.height/2.0
    internal fun categoryPoint(categoryIndex:Int):Pair<Double,Double>? =
        tabs.getOrNull(categoryIndex)?.let { it.x+it.width/2.0 to it.y+it.height/2.0 }
    internal fun visibleShapes():List<BaseShape> = visibleIndices.map { entries[it] }

    private fun choose(i:Int) {
        if(finished) return
        val shape=entries.getOrNull(i) ?: return
        finished=true
        // The editor's graph widget is disposed while another screen is open.
        // Restore its widgets before applying a document edit.
        minecraft?.setScreen(parent)
        apply(shape)
    }

    override fun onClose() {
        if(finished) return
        finished=true
        minecraft?.setScreen(parent)
    }

    override fun keyPressed(key:Int,scan:Int,mods:Int):Boolean {
        if(key==70 && hasControlDown()) { setFocused(search);return true }
        if(search.isFocused) {
            if(key==264 && slots.isNotEmpty()) {
                index=visibleIndices.first();keyboardSelection=true;setFocused(slots.first());return true
            }
            return super.keyPressed(key,scan,mods)
        }
        val focused=slots.firstOrNull { it.isFocused }
        if(focused!=null) index=focused.entryIndex
        val position=visibleIndices.indexOf(index).coerceAtLeast(0)
        val next=when(key) {
            263->position-1
            262->position+1
            265->verticalIndex(position,-1)
            264->verticalIndex(position,1)
            268->0
            269->visibleIndices.lastIndex
            else->null
        }
        if(next!=null && visibleIndices.isNotEmpty()) {
            index=visibleIndices[next.coerceIn(0,visibleIndices.lastIndex)]
            keyboardSelection=true;setFocused(slots.first { it.entryIndex==index });return true
        }
        if((key==257 || key==335) && focused!=null) { choose(index);return true }
        return super.keyPressed(key,scan,mods)
    }

    private fun verticalIndex(position:Int,direction:Int):Int {
        val rows=((visibleIndices.size+columns-1)/columns).coerceAtLeast(1)
        val row=(position/columns+direction).coerceIn(0,rows-1)
        return (row*columns+position%columns).coerceAtMost(visibleIndices.lastIndex)
    }

    override fun mouseMoved(mx:Double,my:Double) {
        keyboardSelection=false
        super.mouseMoved(mx,my)
    }

    override fun render(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        ArchitectTheme.background(g,width,height)
        ArchitectTheme.header(g,width,title.string,width-16)
        ArchitectTheme.panel(g,left,top,panelWidth,panelHeight,ui("catalog_title"))
        val count="${visibleIndices.size} / ${entries.size}"
        g.drawString(font,count,left+panelWidth-12-font.width(count),top+10,ArchitectTheme.muted,false)
        ArchitectTheme.inset(g,left+10,top+28,panelWidth-20,22)
        ArchitectTheme.viewport(g,gridLeft,gridTop,gridWidth,gridHeight)
        ArchitectTheme.inset(g,infoLeft,gridTop,infoWidth,gridHeight)
        val hovered=slots.firstOrNull { it.isMouseOver(mx.toDouble(),my.toDouble()) }
        val focused=slots.firstOrNull { it.isFocused }
        val detailIndex=hovered?.entryIndex ?: focused?.entryIndex ?: index
        renderDetail(g,detailIndex)
        if(visibleIndices.isEmpty()) {
            font.split(tr("catalog_empty"),gridWidth-20).take(3).forEachIndexed { i,line ->
                g.drawString(font,line,gridLeft+10,gridTop+12+i*11,ArchitectTheme.muted,false)
            }
        }
        ArchitectTheme.footer(g,width,height,ui("copycat_choose_hint").replace('\n',' '))
        super.render(g,mx,my,delta)
        val tooltipSlot=hovered ?: if(keyboardSelection) focused else null
        if(tooltipSlot!=null) {
            val i=tooltipSlot.entryIndex
            val adapter=entries[i]
            val lines=mutableListOf<Component>(tr(adapter.name),stacks[i].hoverName,
                Component.literal(adapter.source).withStyle(ChatFormatting.DARK_GRAY))
            if(adapter.id==current.id) lines+=tr("copycat_selected").copy().withStyle(ChatFormatting.GOLD)
            val point=slotPoint(adapter)!!
            g.renderComponentTooltip(font,lines,if(hovered!=null) mx else point.first.toInt(),if(hovered!=null) my else point.second.toInt())
        }
    }

    private fun renderDetail(g:GuiGraphics,i:Int) {
        val shape=entries.getOrNull(i) ?: return
        val selected=shape.id==current.id
        val label=ui(if(selected) "copycat_current" else "selected_part")
        g.drawString(font,font.plainSubstrByWidth(label,infoWidth-10),infoLeft+5,gridTop+7,ArchitectTheme.muted,false)
        val scale=if(gridHeight>=100) 2f else 1.5f
        val itemSize=(16*scale).toInt()
        g.pose().pushPose()
        g.pose().translate((infoLeft+(infoWidth-itemSize)/2).toFloat(),(gridTop+22).toFloat(),0f)
        g.pose().scale(scale,scale,1f);g.renderItem(stacks[i],0,0);g.pose().popPose()
        val nameY=gridTop+27+itemSize
        font.split(tr(shape.name),(infoWidth-12).coerceAtLeast(1)).take(if(gridHeight>=100) 2 else 1).forEachIndexed { lineIndex,line ->
            g.drawString(font,line,infoLeft+6,nameY+lineIndex*10,if(selected) ArchitectTheme.brass else ArchitectTheme.text,false)
        }
        if(gridHeight>=115) {
            val source=shape.source.substringBefore(':')
            g.drawString(font,font.plainSubstrByWidth(source,infoWidth-12),infoLeft+6,gridTop+gridHeight-19,ArchitectTheme.muted,false)
            g.fill(infoLeft+6,gridTop+gridHeight-8,infoLeft+infoWidth-6,gridTop+gridHeight-7,ArchitectTheme.brassDark)
            g.fill(infoLeft+6,gridTop+gridHeight-8,infoLeft+10,gridTop+gridHeight-7,ArchitectTheme.green)
        }
    }

    override fun renderBackground(g:GuiGraphics,mx:Int,my:Int,delta:Float) {}
    override fun isPauseScreen()=false

    private inner class SlotButton(x:Int,y:Int,val entryIndex:Int,private val adapter:BaseShape,private val stack:ItemStack):
        Button(x,y,SLOT_SIZE,SLOT_SIZE,tr(adapter.name).copy().append(" · ").append(stack.hoverName).append(" · ").append(adapter.source),
            { choose(entryIndex) },DEFAULT_NARRATION) {
        private val selected=adapter.id==current.id
        override fun renderWidget(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
            ArchitectTheme.slot(g,x,y,width,height,isHoveredOrFocused,selected)
            if(isFocused) g.renderOutline(x+1,y+1,width-2,height-2,ArchitectTheme.text)
            g.renderItem(stack,x+(width-16)/2,y+(height-16)/2)
            if(selected) g.fill(x+width-5,y+2,x+width-2,y+5,ArchitectTheme.green)
            if(adapter.discCut>0) {
                val fraction=if(adapter.discCut==1) "½" else "¼"
                val badgeWidth=font.width(fraction)+2
                g.fill(x+width-badgeWidth-1,y+height-10,x+width-1,y+height-1,ArchitectTheme.well)
                g.drawString(font,fraction,x+width-badgeWidth,y+height-10,ArchitectTheme.brass,false)
            }
        }
    }

    companion object {
        private const val COLUMNS=9
        private const val SLOT_SIZE=22
        fun open(current:BaseShape,apply:(BaseShape)->Unit) {
            val minecraft=Minecraft.getInstance()
            minecraft.setScreen(CopycatSelectionScreen(minecraft.screen,current,apply))
        }
    }
}
