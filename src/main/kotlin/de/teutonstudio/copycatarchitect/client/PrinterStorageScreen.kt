package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.*
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Renderable
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.MenuAccess
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Physical cartridge management and persistent print storage, including a 427 × 240 layout. */
class PrinterStorageScreen(private val parent:Screen?=null,private val storage:PrinterStorageMenu?=null):EditorScreen(tr("printer_storage")),MenuAccess<PrinterStorageMenu> {
    /** The real menu stays open while the existing workspace and all its cards remain intact. */
    var workspace:ShapeScreen?=(parent as? ShapeScreen)?.takeIf { it.isPrinterWorkspace } ?: (parent as? PrinterStorageScreen)?.workspace
        private set
    private data class Entry(val key:String,val form:Form,val surfaces:Map<String,String>,val turn:Int,val time:Long=0)
    private data class Rect(val x:Int,val y:Int,val w:Int,val h:Int) {
        fun contains(mx:Double,my:Double)=mx>=x && my>=y && mx<x+w && my<y+h
    }
    private var saved=false
    private var selected=""
    private var first=0
    private val inventoryMouse=storage?.let { menu -> PrinterInventoryMouse(menu) { slot,button,type ->
        val mc=minecraft ?: return@PrinterInventoryMouse
        mc.player?.let { mc.gameMode?.handleInventoryMouseClick(menu.containerId,slot,button,type,it) }
    } }
    private var hint=""
    private var observed:Snapshot?=null
    private var previewForm:Form?=null
    private var preview:Result<Form>?=null
    private var hardwareForm:Form?=null
    private var hardwarePreview:Result<Form>?=null
    private val compact get()=width<640 || height<320
    private val leftWidth get()=if(compact) 188 else minOf(288,(width*0.36).roundToInt())
    private val hardware get()=Rect(8,30,leftWidth,height-50)
    private val archive get()=Rect(hardware.x+hardware.w+8,30,width-hardware.w-32,height-50)
    private val previewWidth get()=if(compact) 88 else minOf(160,archive.w/3)
    private val controlY get()=archive.y+archive.h-47
    private val list get()=Rect(archive.x+10,archive.y+54,(archive.w-previewWidth-26).coerceAtLeast(72),(controlY-archive.y-62).coerceAtLeast(28))
    private val previewArea get()=Rect(list.x+list.w+10,list.y,archive.x+archive.w-10-(list.x+list.w+10),
        if(compact) list.h else minOf(216,(list.h-56).coerceAtLeast(60)))
    private val rowHeight get()=if(compact) 24 else 30
    private val rows get()=(list.h/rowHeight).coerceAtLeast(1)
    private val view get()=ClientState.snapshot.printer
    private fun entries():List<Entry> = if(saved) ClientState.snapshot.library.let { library -> library.forms.entries.map {
        val settings=library.printSettings[it.key] ?: PrintSettings();Entry(it.key,it.value,settings.surfaces,settings.turn)
    } }
        else ClientState.snapshot.library.history.map { Entry(it.id,it.form,it.surfaces,it.turn,it.time) }
    private fun selection():Entry?=entries().firstOrNull { it.key==selected }
    private fun chosen():Entry?=selection() ?: view?.let { Entry("",it.form,it.surfaces,it.turn) }
    private fun cartridgeRect(slot:Int):Rect {
        val gap=if(compact) 24 else minOf(34,(hardware.w-16)/7)
        val size=if(compact) 22 else gap-4
        return Rect(hardware.x+(hardware.w-6*gap-size)/2+slot*gap,hardware.y+if(compact) 46 else 52,size,size)
    }
    private fun inventoryRect(slot:Int):Rect {
        val pitch=if(compact) 18 else 24
        val column=if(slot<9) slot else (slot-9)%9
        val row=if(slot<9) 3 else (slot-9)/9
        val startY=hardware.y+hardware.h-4*pitch-if(compact) 8 else 16
        return Rect(hardware.x+(hardware.w-9*pitch)/2+column*pitch,startY+row*pitch+if(row==3) 4 else 0,pitch,pitch)
    }
    override fun getMenu()=checkNotNull(storage)
    override fun init() {
        workspace?.let { screen ->
            storage?.let(screen::attachPrinterInventory)
            screen.init(net.minecraft.client.Minecraft.getInstance(),width,height)
            return
        }
        inventoryMouse?.reset();refresh()
    }
    /** Called by the snapshot receiver; also checked in tick for server-driven inventory changes. */
    fun refresh(fromSnapshot:Boolean=false) {
        if(minecraft==null) return
        // Sneak-opening an already placed printer enters the same Print tab after its
        // authoritative snapshot arrives. Held-printer menus keep their locked-slot UI.
        if(fromSnapshot && storage?.lockedInventorySlot==-1 && view?.placed==true && workspace?.printerCommand("printerPreview")?.key!=view?.session) {
            val screen=ShapeScreen(printerSession=checkNotNull(view));workspace=screen
            screen.attachPrinterInventory(checkNotNull(storage));screen.init(checkNotNull(minecraft),width,height);screen.showPrinterPrint()
            return
        }
        workspace?.let { it.refreshPrinterLibrary();return }
        (parent as? ShapeScreen)?.loadPrinterDraft(ClientState.snapshot.printer)
        observed=ClientState.snapshot
        val values=entries()
        if(values.none { it.key==selected }) selected=values.firstOrNull()?.key.orEmpty()
        first=first.coerceIn(0,(values.size-rows).coerceAtLeast(0))
        clearWidgets();resetDropdown()
        button(width-76,5,68,"close") { onClose() }
        if(view?.placed==true) button(width-157,5,77,"ps_graph") { ClientState.send(Command("printerGraph")) }
        val a=archive
        val half=(a.w-24)/2
        (button(a.x+10,a.y+28,half,"ps_history") { if(saved) { saved=false;selected="";first=0;refresh() } } as EditorButton).selected=!saved
        (button(a.x+14+half,a.y+28,half,"ps_saved") { if(!saved) { saved=true;selected="";first=0;refresh() } } as EditorButton).selected=saved
        button(a.x+10,controlY,a.w-20,"ps_load") {
            selection()?.let { ClientState.send(Command("printerLoad",key=it.key));hint=ui("ps_loaded") }
        }.active=selection()!=null && view!=null
        button(a.x+10,controlY+23,a.w-20,if(saved) "copycat_library" else "ps_save_named") {
            if(saved) SavedCopycatSelectionScreen.open(this,selected) { key ->
                selected=key;first=entries().indexOfFirst { it.key==key }.coerceAtLeast(0);refresh()
            }
            else selection()?.let { entry -> EditorValueInput.text(tr("ps_save_named"),entry.form.name,maxLength=64) { name ->
                ClientState.send(Command("historySave",key=entry.key,payload=name))
            } }
        }.active=saved || selection()!=null
    }
    override fun tick() {
        workspace?.let { screen ->
            screen.tick()
            minecraft?.player?.let { if(!it.isAlive || it.isRemoved) it.closeContainer() }
            return
        }
        super.tick()
        if(observed!==ClientState.snapshot) refresh()
        if(storage!=null) minecraft?.player?.let { if(!it.isAlive || it.isRemoved) it.closeContainer() }
    }
    override fun onClose() {
        workspace?.let { it.onClose();return }
        inventoryMouse?.reset()
        if(storage!=null) minecraft?.player?.closeContainer()
        minecraft?.setScreen(parent)
    }
    private fun cartridgeStack(slot:Int)=storage?.getSlot(slot)?.item ?: stack(view?.slots?.getOrNull(slot)?.item.orEmpty())
    private fun inventoryStack(slot:Int)=storage?.getSlot(7+slot)?.item ?: minecraft?.player?.inventory?.getItem(slot) ?: ItemStack.EMPTY
    private fun slotAt(mx:Double,my:Double):Int {
        for(i in 0..6) if(cartridgeRect(i).contains(mx,my)) return i
        for(i in 0..35) if(inventoryRect(i).contains(mx,my)) return 7+i
        return -1
    }

    private fun slot(g:GuiGraphics,r:Rect,stack:ItemStack,active:Boolean,enabled:Boolean,mx:Int,my:Int) {
        ArchitectTheme.slot(g,r.x,r.y,r.w,r.h,r.contains(mx.toDouble(),my.toDouble()),active,enabled)
        if(!stack.isEmpty) {
            val x=r.x+(r.w-16)/2;val y=r.y+(r.h-16)/2
            g.renderItem(stack,x,y);g.renderItemDecorations(font,stack,x,y)
        }
    }
    private fun stack(item:String)=ResourceLocation.tryParse(item)?.let { id -> if(BuiltInRegistries.ITEM.containsKey(id)) ItemStack(BuiltInRegistries.ITEM.get(id)) else ItemStack.EMPTY } ?: ItemStack.EMPTY
    private fun shortUnits(units:Int)=when { units>=1_000_000->"${units/1_000_000}M";units>=10_000->"${units/1000}k";else->units.toString() }
    private fun availableUnits():String {
        if(storage!=null) {
            val slots=(0..6).map(::cartridgeStack)
            return if(slots.any(Economy::isCreativeCartridge)) "∞" else slots.sumOf { Economy.units(it).toLong() }.toString()
        }
        return view?.let { printer -> if(printer.slots.any { it.creative }) "∞" else printer.slots.sumOf { it.units.toLong() }.toString() } ?: "0"
    }
    private fun instructions()=ui(if(storage?.carried?.isEmpty==false) (if(compact) "ps_choose_target_compact" else "ps_choose_target") else (if(compact) "ps_choose_inventory_compact" else "ps_choose_inventory"))
    private fun drawHardware(g:GuiGraphics,mx:Int,my:Int) {
        val a=hardware
        ArchitectTheme.panel(g,a.x,a.y,a.w,a.h,ui("ps_cartridges"))
        // A permanently visible terminal readout keeps supply information available at every scale.
        ArchitectTheme.inset(g,a.x+10,a.y+26,a.w-20,14)
        g.fill(a.x+15,a.y+31,a.x+18,a.y+34,if(availableUnits()=="0") ArchitectTheme.brassDark else ArchitectTheme.green)
        label(g,font.plainSubstrByWidth(ui("ps_available",availableUnits()),a.w-34),a.x+23,a.y+29,ArchitectTheme.green)
        for(i in 0..6) {
            val r=cartridgeRect(i);val value=cartridgeStack(i);val contents=view?.slots?.getOrNull(i)
            val creative=if(storage!=null) Economy.isCreativeCartridge(value) else contents?.creative==true
            val enabled=storage?.carried?.let { it.isEmpty || Economy.isCartridge(it) } ?: true
            slot(g,r,value,false,enabled,mx,my)
            val lamp=when { !enabled->ArchitectTheme.danger;creative->ArchitectTheme.brass;!value.isEmpty->ArchitectTheme.green;else->ArchitectTheme.shadow }
            g.fill(r.x+r.w-5,r.y+3,r.x+r.w-3,r.y+5,lamp)
            val amount=when { value.isEmpty->"·";creative->"∞";else->shortUnits(if(storage!=null) Economy.units(value) else contents?.units ?: 0) }
            label(g,amount,r.x+(r.w-font.width(amount))/2,r.y+r.h+3,if(value.isEmpty) ArchitectTheme.disabled else if(creative) ArchitectTheme.brass else ArchitectTheme.text)
        }
        val invY=inventoryRect(9).y-12
        val separatorY=cartridgeRect(0).y+cartridgeRect(0).h+18
        g.fill(a.x+10,separatorY,a.x+a.w-10,separatorY+1,ArchitectTheme.shadow)
        if(!compact) view?.let { printer ->
            val x=a.x+12;val y=separatorY+10;val w=a.w-24;val h=invY-y-12
            if(h>=84) {
                ArchitectTheme.viewport(g,x,y,w,h)
                ArchitectTheme.inset(g,x+5,y+5,w-10,27)
                label(g,font.plainSubstrByWidth(ui("ps_active_print"),w-24),x+12,y+9,ArchitectTheme.muted)
                label(g,font.plainSubstrByWidth(printer.form.name,w-24),x+12,y+20,ArchitectTheme.text)
                if(hardwareForm!=printer.form) { hardwareForm=printer.form;hardwarePreview=runCatching { Geometry.validate(printer.form) } }
                hardwarePreview?.getOrNull()?.let { form ->
                    g.enableScissor(x+3,y+35,x+w-3,y+h-3)
                    Meshes.preview(g,x+w/2,y+35+(h-38)/2,minOf(w,h-38)*0.32f,yaw,Geometry.rotate(form.parts,printer.turn),material="",groups=form.groups,surfaces=printer.surfaces)
                    g.disableScissor()
                }
            }
        }
        label(g,font.plainSubstrByWidth(ui("ps_inventory"),a.w-24),a.x+12,invY,ArchitectTheme.brass)
        val labelEnd=a.x+16+font.width(ui("ps_inventory"))
        if(labelEnd<a.x+a.w-12) g.fill(labelEnd,invY+4,a.x+a.w-12,invY+5,ArchitectTheme.shadow)
        for(i in 0..35) {
            val enabled=storage?.let { menu -> minecraft?.player?.let { menu.getSlot(7+i).mayPickup(it) } } ?: true
            slot(g,inventoryRect(i),inventoryStack(i),false,enabled,mx,my)
        }
    }
    private fun drawArchive(g:GuiGraphics,mx:Int,my:Int) {
        val a=archive;val area=list;val values=entries()
        ArchitectTheme.panel(g,a.x,a.y,a.w,a.h,ui("ps_library"))
        val count=values.size.toString()
        ArchitectTheme.inset(g,a.x+a.w-35,a.y+6,23,14)
        label(g,count,a.x+a.w-24-font.width(count)/2,a.y+9,ArchitectTheme.green)
        ArchitectTheme.inset(g,area.x-2,area.y-2,area.w+4,area.h+4)
        g.enableScissor(area.x,area.y,area.x+area.w,area.y+area.h)
        if(values.isEmpty()) g.drawWordWrap(font,tr(if(saved) "ps_saved_empty" else "ps_history_empty"),area.x+6,area.y+8,area.w-12,ArchitectTheme.muted)
        for(row in 0 until rows) {
            val entry=values.getOrNull(first+row) ?: break;val top=area.y+row*rowHeight
            val hovered=mx in area.x until area.x+area.w && my in top until top+rowHeight
            val isSelected=entry.key==selected
            g.fill(area.x,top,area.x+area.w,top+rowHeight-1,when { isSelected->ArchitectTheme.selected;hovered->ArchitectTheme.hovered;row%2==0->ArchitectTheme.alternate;else->ArchitectTheme.well })
            if(isSelected) { g.fill(area.x,top,area.x+2,top+rowHeight-1,ArchitectTheme.brass);g.fill(area.x+area.w-4,top+4,area.x+area.w-2,top+7,ArchitectTheme.green) }
            val textX=area.x+if(compact) 7 else 32
            if(!compact) label(g,(first+row+1).toString().padStart(2,'0'),area.x+8,top+9,if(isSelected) ArchitectTheme.brass else ArchitectTheme.disabled)
            label(g,font.plainSubstrByWidth(entry.form.name,area.x+area.w-textX-8),textX,top+if(compact) 3 else 5,ArchitectTheme.text)
            val detail=if(entry.time>0) DateTimeFormatter.ofPattern("dd.MM. HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(entry.time)) else ui("ps_version",entry.form.version)
            label(g,font.plainSubstrByWidth(detail,area.x+area.w-textX-8),textX,top+if(compact) 14 else 17,ArchitectTheme.muted)
        }
        g.disableScissor()
        if(values.size>rows) {
            val thumb=(area.h.toDouble()*rows/values.size).roundToInt().coerceAtLeast(8)
            val top=area.y+((area.h-thumb).toDouble()*first/(values.size-rows)).roundToInt()
            g.fill(area.x+area.w-2,area.y,area.x+area.w,area.y+area.h,ArchitectTheme.shadow)
            g.fill(area.x+area.w-2,top,area.x+area.w,top+thumb,ArchitectTheme.brass)
        }
        val pane=previewArea
        ArchitectTheme.viewport(g,pane.x,pane.y,pane.w,pane.h)
        ArchitectTheme.inset(g,pane.x+4,pane.y+4,pane.w-8,14)
        label(g,font.plainSubstrByWidth(ui("preview3d"),pane.w-18),pane.x+9,pane.y+7,ArchitectTheme.brass)
        val choice=chosen() ?: return
        if(previewForm!=choice.form) { previewForm=choice.form;preview=runCatching { Geometry.validate(choice.form) } }
        preview?.getOrNull()?.let { checked ->
            g.enableScissor(pane.x+3,pane.y+21,pane.x+pane.w-3,pane.y+pane.h-3)
            Meshes.preview(g,pane.x+pane.w/2,pane.y+21+(pane.h-24)/2,minOf(pane.w-6,pane.h-24)*0.34f,yaw,Geometry.rotate(checked.parts,choice.turn),material="",groups=checked.groups,surfaces=choice.surfaces)
            g.disableScissor()
            if(!compact) {
                val infoY=pane.y+pane.h+7
                label(g,font.plainSubstrByWidth(choice.form.name,pane.w),pane.x,infoY,ArchitectTheme.text)
                label(g,font.plainSubstrByWidth(ui("ps_raw_units",Extrusion.cost(checked.parts,checked.groups)),pane.w),pane.x,infoY+14,ArchitectTheme.brass)
                label(g,font.plainSubstrByWidth(ui("ps_groups",checked.groups.size),pane.w),pane.x,infoY+26,ArchitectTheme.muted)
            }
        }
    }
    override fun render(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        workspace?.let { it.render(g,mx,my,delta);return }
        ArchitectTheme.background(g,width,height)
        ArchitectTheme.header(g,width,title.string,(width-185).coerceAtLeast(60))
        drawHardware(g,mx,my);drawArchive(g,mx,my)
        children().filterIsInstance<Renderable>().forEach { it.render(g,mx,my,delta) }
        val message=hint.ifEmpty { ClientState.snapshot.message.ifEmpty { instructions() } }
        ArchitectTheme.footer(g,width,height,message)
        val carried=storage?.carried ?: ItemStack.EMPTY
        if(!carried.isEmpty) {
            g.pose().pushPose();g.pose().translate(0f,0f,300f)
            g.renderItem(carried,mx-8,my-8);g.renderItemDecorations(font,carried,mx-8,my-8)
            g.pose().popPose()
            return
        }
        var tooltip:List<Component>?=null
        for(i in 0..6) if(cartridgeRect(i).contains(mx.toDouble(),my.toDouble())) {
            val value=cartridgeStack(i);val entry=view?.slots?.getOrNull(i)
            val creative=if(storage!=null) Economy.isCreativeCartridge(value) else entry?.creative==true
            tooltip=listOf(tr("ps_slot",i+1),if(value.isEmpty) tr("ps_slot_empty") else value.hoverName,
                if(creative) Component.translatable("item.copycat_architect.cartridge.infinite") else tr("ps_raw_units",if(storage!=null) Economy.units(value) else entry?.units ?: 0),tr("ps_take_hint"))
        }
        for(i in 0..35) if(inventoryRect(i).contains(mx.toDouble(),my.toDouble())) inventoryStack(i).takeIf { !it.isEmpty }?.let { value ->
            tooltip=getTooltipFromItem(checkNotNull(minecraft),value)+tr("ps_select_cartridge")
        }
        tooltip?.let { g.renderComponentTooltip(font,it,mx,my) }
    }
    override fun mouseClicked(mx:Double,my:Double,button:Int):Boolean {
        workspace?.let { return it.mouseClicked(mx,my,button) }
        if(button==0 || button==1) {
            val slot=slotAt(mx,my)
            if(slot>=0) {
                inventoryMouse?.press(slot,button,hasShiftDown())
                hint=if(slot<7 && storage?.carried?.let { !it.isEmpty && !Economy.isCartridge(it) }==true) ui("ps_only_cartridges") else ""
                return true
            }
            if(button==0 && list.contains(mx,my)) {
                entries().getOrNull(first+((my-list.y)/rowHeight).toInt())?.let { selected=it.key;refresh() }
                return true
            }
        }
        return super.mouseClicked(mx,my,button)
    }
    override fun mouseDragged(mx:Double,my:Double,button:Int,dx:Double,dy:Double):Boolean {
        workspace?.let { return it.mouseDragged(mx,my,button,dx,dy) }
        if(inventoryMouse?.active==true) { inventoryMouse.drag(slotAt(mx,my));return true }
        return super.mouseDragged(mx,my,button,dx,dy)
    }
    override fun mouseReleased(mx:Double,my:Double,button:Int):Boolean {
        workspace?.let { return it.mouseReleased(mx,my,button) }
        inventoryMouse?.release(slotAt(mx,my),button)
        return super.mouseReleased(mx,my,button)
    }
    override fun keyPressed(key:Int,scan:Int,mods:Int):Boolean {
        workspace?.let { return it.keyPressed(key,scan,mods) }
        if(storage!=null && minecraft?.options?.keyInventory?.isActiveAndMatches(com.mojang.blaze3d.platform.InputConstants.getKey(key,scan))==true) {
            onClose();return true
        }
        return super.keyPressed(key,scan,mods)
    }
    override fun mouseScrolled(mx:Double,my:Double,dx:Double,dy:Double):Boolean {
        workspace?.let { return it.mouseScrolled(mx,my,dx,dy) }
        if(archive.contains(mx,my)) { first=(first-if(dy>0) 1 else -1).coerceIn(0,(entries().size-rows).coerceAtLeast(0));return true }
        return super.mouseScrolled(mx,my,dx,dy)
    }
    override fun mouseMoved(mx:Double,my:Double) { workspace?.let { it.mouseMoved(mx,my);return };super.mouseMoved(mx,my) }
    override fun keyReleased(key:Int,scan:Int,mods:Int):Boolean=workspace?.keyReleased(key,scan,mods) ?: super.keyReleased(key,scan,mods)
    override fun charTyped(character:Char,mods:Int):Boolean=workspace?.charTyped(character,mods) ?: super.charTyped(character,mods)
    override fun removed() { workspace?.removed();super.removed() }
}
