package de.teutonstudio.copycatarchitect.client

import com.mojang.math.Axis
import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.CopycatMaterials
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import kotlin.math.ceil

/** All groups are edited in a local draft; only Apply changes the host's material assignments. */
class MaterialSelectionScreen private constructor(
    private val parent:Screen?,
    private val groups:List<MaterialGroup>,
    private val geometry:Result<List<Part>>,
    surfaces:Map<String,String>,
    initialGroup:String,
    private val allowUncoated:Boolean,
    private val materialRoles:Map<String,String>,
    private val apply:(Map<String,String>)->Unit
):EditorScreen(tr("select_material")) {
    private data class Entry(val id:String,val stack:ItemStack) {
        val name get()=if(id.isEmpty()) ui("printer_uncoated") else stack.hoverName.string
    }
    private val entries=buildList {
        if(allowUncoated) add(Entry("",ItemStack.EMPTY))
        BuiltInRegistries.ITEM.forEach { item ->
            if(item is BlockItem) {
                val id=BuiltInRegistries.ITEM.getKey(item).toString()
                if(runCatching { CopycatMaterials.state(id) }.isSuccess) add(Entry(id,ItemStack(item)))
            }
        }
    }.sortedWith(compareBy<Entry> { it.id.isNotEmpty() }.thenBy { it.name.lowercase() }.thenBy { it.id })
    private val draft=groups.associate { it.id to surfaces[it.id].orEmpty() }.toMutableMap()
    private var group=groups.firstOrNull { it.id==initialGroup } ?: groups.first()
    private var query=""
    private var firstRow=0
    private var isolated=false
    private var finished=false
    private var initialViewport=true
    private val slots=mutableListOf<MaterialSlot>()
    private var filtered=entries.indices.toList()
    private lateinit var search:EditorTextField
    private val areaParts=geometry.map { parts -> groups.associate { it.id to Geometry.previewParts(parts,groups,it.id) } }
    private val panelWidth get()=minOf(720,width-16)
    private val panelHeight get()=minOf(360,height-52)
    private val left get()=(width-panelWidth)/2
    private val top get()=(height-panelHeight)/2
    private val gridX get()=left+10
    private val gridY get()=top+75
    private val columns get()=((panelWidth*0.46f-16)/SLOT).toInt().coerceAtLeast(1)
    private val gridWidth get()=columns*SLOT+8
    private val rows get()=((panelHeight-114)/SLOT).coerceAtLeast(1)
    private val gridHeight get()=rows*SLOT+8
    private val detailX get()=gridX+gridWidth+12
    private val detailWidth get()=left+panelWidth-10-detailX
    private val previewWidth get()=(detailWidth-6)/2
    private val previewY get()=top+64
    private val previewHeight get()=panelHeight-126
    private val selected get()=draft.getValue(group.id)
    private fun roleLabel(role:String):String {
        val key="role_$role"
        return if(net.minecraft.client.resources.language.I18n.exists("gui.copycat_architect.$key")) ui(key) else role
    }
    private fun groupLabel(value:MaterialGroup)=materialRoles[value.id]?.let { "${value.name} · ${roleLabel(it)}" } ?: value.name

    override fun titleAvailableWidth()=width-16
    override fun init() {
        resetDropdown();slots.clear()
        choice(gridX,top+27,gridWidth,groupLabel(group),groups.map { value -> groupLabel(value) to {
            group=value;revealSelection();rebuildWidgets()
        } },groups.indexOf(group)).setTooltip(Tooltip.create(materialRoles[group.id]?.let { tr("role",roleLabel(it)) } ?: tr("material_group")))
        button(detailX,top+27,detailWidth,if(isolated) "material_area_only" else "material_whole_copycat") {
            isolated=!isolated;rebuildWidgets()
        }
        search=addRenderableWidget(EditorTextField(font,gridX,top+50,gridWidth,20,tr("catalog_search")).apply {
            setMaxLength(80);value=query;setHint(tr("catalog_search"))
        })
        if(initialViewport) { revealSelection();initialViewport=false }
        refreshSlots()
        search.setResponder { query=it;firstRow=0;refreshSlots() }
        val half=(panelWidth-24)/2
        button(gridX,top+panelHeight-27,half,"cancel") { onClose() }
        button(gridX+half+4,top+panelHeight-27,panelWidth-24-half,"apply") { confirm() }
    }

    private fun refreshSlots() {
        val hadFocus=slots.any { it.isFocused }
        slots.forEach { removeWidget(it) };slots.clear()
        val needle=query.trim()
        filtered=entries.indices.filter { needle.isEmpty() || entries[it].name.contains(needle,true) || entries[it].id.contains(needle,true) }
        firstRow=firstRow.coerceIn(0,maxRow())
        filtered.drop(firstRow*columns).take(rows*columns).forEachIndexed { position,index ->
            slots+=addRenderableWidget(MaterialSlot(gridX+4+position%columns*SLOT,gridY+4+position/columns*SLOT,index))
        }
        if(hadFocus) slots.firstOrNull { entries[it.index].id==selected }?.let { setFocused(it) }
    }
    private fun maxRow()=(ceil(filtered.size.toDouble()/columns).toInt()-rows).coerceAtLeast(0)
    private fun revealSelection() {
        val position=filtered.indexOfFirst { entries[it].id==selected }
        if(position>=0) {
            val row=position/columns
            if(row<firstRow) firstRow=row
            if(row>=firstRow+rows) firstRow=row-rows+1
        }
    }
    private fun select(index:Int) {
        draft[group.id]=entries[index].id
        val previousRow=firstRow
        revealSelection()
        if(previousRow!=firstRow) refreshSlots()
        slots.firstOrNull { it.index==index }?.let { setFocused(it) }
    }
    private fun confirm() {
        if(finished) return
        finished=true
        minecraft?.setScreen(parent)
        apply(draft.toMap())
    }
    override fun onClose() {
        if(finished) return
        finished=true;minecraft?.setScreen(parent)
    }
    override fun setInitialFocus() { setInitialFocus(search) }
    override fun keyPressed(key:Int,scan:Int,mods:Int):Boolean {
        if(dropdownKey(key)) return true
        if(key==70 && hasControlDown()) { setFocused(search);return true }
        if(key==256) { onClose();return true }
        if(search.isFocused && key!=264 && key!=257 && key!=335) return super.keyPressed(key,scan,mods)
        if(key==257 || key==335) { confirm();return true }
        if(filtered.isNotEmpty()) {
            val position=filtered.indexOfFirst { entries[it].id==selected }.coerceAtLeast(0)
            val next=when(key) {
                263->position-1;262->position+1;265->position-columns;264->if(search.isFocused) firstRow*columns else position+columns
                268->0;269->filtered.lastIndex;266->position-rows*columns;267->position+rows*columns
                else->null
            }
            if(next!=null) { select(filtered[next.coerceIn(0,filtered.lastIndex)]);return true }
        }
        return super.keyPressed(key,scan,mods)
    }
    override fun mouseScrolled(x:Double,y:Double,dx:Double,dy:Double):Boolean {
        if(super.mouseScrolled(x,y,dx,dy)) return true
        if(x>=gridX && x<gridX+gridWidth && y>=gridY && y<gridY+gridHeight) {
            firstRow=(firstRow-if(dy>0) 1 else if(dy<0) -1 else 0).coerceIn(0,maxRow());refreshSlots();return true
        }
        return false
    }
    override fun mouseClicked(x:Double,y:Double,button:Int):Boolean {
        if(dropdownClick(x,y,button)) return true
        return super.mouseClicked(x,y,button)
    }
    override fun mouseDragged(x:Double,y:Double,button:Int,dx:Double,dy:Double):Boolean {
        if(button==1 && x>=detailX && x<detailX+detailWidth && y>=previewY && y<previewY+previewHeight) {
            yaw+=dx.toFloat();return true
        }
        return false
    }
    override fun renderWorkspace(g:GuiGraphics) {
        ArchitectTheme.dialog(g,left,top,panelWidth,panelHeight,ui("material_group")+": "+group.name)
        ArchitectTheme.viewport(g,gridX,gridY,gridWidth,gridHeight)
        if(filtered.isEmpty()) g.drawWordWrap(font,tr("catalog_empty"),gridX+6,gridY+8,gridWidth-12,ArchitectTheme.muted)
        val count="${filtered.size} / ${entries.size}"
        label(g,count,left+panelWidth-10-font.width(count),top+10,ArchitectTheme.muted)
        if(maxRow()>0) label(g,"${firstRow+1}/${maxRow()+1} ↕",gridX,gridY+gridHeight+2,ArchitectTheme.muted)
        label(g,ui("material_block_preview"),detailX,top+52,ArchitectTheme.muted)
        label(g,font.plainSubstrByWidth(ui("material_application_preview"),previewWidth),detailX+previewWidth+6,top+52,ArchitectTheme.brass)
        viewport(g,detailX) { drawBlock(g) }
        viewport(g,detailX+previewWidth+6) {
            val parts=if(isolated) areaParts.map { it.getValue(group.id) } else geometry
            parts.fold({ value ->
                Meshes.preview(g,detailX+previewWidth+6+previewWidth/2,previewY+previewHeight/2,
                    minOf(previewWidth*0.42f,previewHeight*0.42f),yaw,value,groups=groups,surfaces=draft)
                if(areaParts.getOrNull()?.getValue(group.id)?.isEmpty()==true)
                    g.drawCenteredString(font,tr("material_empty_area"),detailX+previewWidth+6+previewWidth/2,previewY+previewHeight-12,ArchitectTheme.muted)
            },{ error -> g.drawWordWrap(font,Component.literal(error.message ?: ui("disconnected")),detailX+previewWidth+11,previewY+6,previewWidth-10,ArchitectTheme.danger) })
        }
        val entry=entries.firstOrNull { it.id==selected }
        label(g,font.plainSubstrByWidth(entry?.name ?: selected,detailWidth),detailX,previewY+previewHeight+5,ArchitectTheme.text)
        label(g,font.plainSubstrByWidth(selected,detailWidth),detailX,previewY+previewHeight+16,ArchitectTheme.muted)
    }
    private fun viewport(g:GuiGraphics,x:Int,draw:()->Unit) {
        ArchitectTheme.inset(g,x,previewY,previewWidth,previewHeight,ArchitectTheme.blueprint)
        g.flush();g.enableScissor(x+1,previewY+1,x+previewWidth-1,previewY+previewHeight-1)
        try { draw() } finally { g.flush();g.disableScissor() }
    }
    private fun drawBlock(g:GuiGraphics) {
        if(selected.isEmpty()) {
            g.drawCenteredString(font,tr("printer_uncoated"),detailX+previewWidth/2,previewY+previewHeight/2,ArchitectTheme.muted);return
        }
        val state=CopycatMaterials.state(selected)
        val scale=minOf(previewWidth*0.42f,previewHeight*0.42f)
        val pose=g.pose();pose.pushPose()
        try {
            pose.translate(detailX+previewWidth/2.0,previewY+previewHeight/2.0,180.0);pose.scale(scale,-scale,scale)
            pose.mulPose(Axis.XP.rotationDegrees(25f));pose.mulPose(Axis.YP.rotationDegrees(yaw));pose.translate(-0.5,-0.5,-0.5)
            Minecraft.getInstance().blockRenderer.renderSingleBlock(state,pose,g.bufferSource(),15728880,OverlayTexture.NO_OVERLAY)
        } finally { g.flush();pose.popPose() }
    }
    override fun renderContents(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        slots.firstOrNull { it.isMouseOver(mx.toDouble(),my.toDouble()) }?.let { slot ->
            val entry=entries[slot.index]
            g.renderComponentTooltip(font,listOf(Component.literal(entry.name),Component.literal(entry.id)),mx,my)
        }
    }
    override fun renderChrome(g:GuiGraphics) {
        ArchitectTheme.background(g,width,height);ArchitectTheme.header(g,width,title.string,width-16)
        ArchitectTheme.footer(g,width,height,ui("material_picker_hint"))
    }
    private inner class MaterialSlot(x:Int,y:Int,val index:Int):Button(x,y,SLOT,SLOT,Component.literal(entries[index].name),{ select(index) },DEFAULT_NARRATION) {
        override fun renderWidget(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
            val entry=entries[index]
            ArchitectTheme.slot(g,x,y,width,height,isHoveredOrFocused,entry.id==selected)
            if(entry.id.isEmpty()) g.drawCenteredString(font,Component.literal("∅"),x+width/2,y+7,ArchitectTheme.muted)
            else g.renderItem(entry.stack,x+3,y+3)
            if(isFocused) g.renderOutline(x+1,y+1,width-2,height-2,ArchitectTheme.brass)
        }
    }
    internal fun slotPoint(id:String)=slots.firstOrNull { entries[it.index].id==id }?.let { it.x+SLOT/2.0 to it.y+SLOT/2.0 }
    internal fun draftSurfaces()=draft.toMap()
    internal fun visibleMaterials()=slots.map { entries[it.index].id }
    internal fun previewParts()=if(isolated) areaParts.map { it.getValue(group.id) } else geometry
    internal fun blockViewport()=listOf(detailX,previewY,previewWidth,previewHeight)
    internal fun applicationViewport()=listOf(detailX+previewWidth+6,previewY,previewWidth,previewHeight)

    companion object {
        private const val SLOT=22
        fun open(groups:List<MaterialGroup>,geometry:Result<List<Part>>,surfaces:Map<String,String>,group:String,allowUncoated:Boolean=false,materialRoles:Map<String,String> = emptyMap(),apply:(Map<String,String>)->Unit) {
            val mc=Minecraft.getInstance()
            mc.setScreen(MaterialSelectionScreen(mc.screen,groups,geometry,surfaces,group,allowUncoated,materialRoles,apply))
        }
    }
}
