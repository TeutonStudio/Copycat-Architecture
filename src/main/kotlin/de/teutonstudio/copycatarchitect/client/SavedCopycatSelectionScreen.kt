package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/** Both machines browse the same library. Choosing a form never changes its name, key or print settings. */
class SavedCopycatSelectionScreen private constructor(
    private val parent:Screen?,private val currentKey:String,private val allowAir:Boolean,private val apply:(String)->Unit,
    private val versionGroup:FormLibraryTree.Entry?=null
):Screen(tr(if(versionGroup==null) "saved_copycat_title" else "saved_copycat_version_title")) {
    private val library=ClientState.snapshot.library
    private val tree=FormLibraryTree(library.forms)
    private var path=tree.folderOf(currentKey)
    private var query=""
    private var entries=emptyList<FormLibraryTree.Entry>()
    private var index=0
    private var first=0
    private var selectedKey=currentKey
    private var finished=false
    private var yaw=35f
    private var draggingPreview=false
    private var draggingScroll=false
    private var lastClickKey=""
    private var lastClickTime=0L
    private val rows=mutableListOf<RowButton>()
    private val previewCache=mutableMapOf<String,Result<Form>>()
    private lateinit var search:EditorTextField
    private lateinit var back:EditorButton
    private var choose:EditorButton?=null
    private val panelWidth get()=minOf(570,(width-16).coerceAtLeast(220))
    private val panelHeight get()=minOf(350,(height-48).coerceAtLeast(164))
    private val left get()=(width-panelWidth)/2
    private val top get()=(height-panelHeight)/2
    private val listX get()=left+10
    private val listY get()=top+77
    private val listWidth get()=(panelWidth*0.57f).toInt()
    private val listHeight get()=panelHeight-112
    private val previewX get()=listX+listWidth+7
    private val previewWidth get()=left+panelWidth-10-previewX
    private val visibleRows get()=((listHeight-6)/ROW_HEIGHT).coerceAtLeast(1)
    private val maximumFirst get()=(entries.size-visibleRows).coerceAtLeast(0)
    private val airEntry get()=FormLibraryTree.Entry(listOf(ui("bg_air_source")),ui("bg_air_source"),AIR_KEY,Form(id="air",name=ui("bg_air_source"),parts=emptyList()))

    override fun init() {
        rows.clear();choose=null
        search=addRenderableWidget(EditorTextField(font,left+10,top+28,panelWidth-20,22,tr(if(versionGroup==null) "saved_copycat_search" else "saved_copycat_version_search")).apply {
            setMaxLength(100);value=query;setHint(tr(if(versionGroup==null) "saved_copycat_search" else "saved_copycat_version_search"))
        })
        back=addRenderableWidget(EditorButton(left+10,top+54,26,Component.literal("←")) { up() }.apply {
            setTooltip(Tooltip.create(tr(if(versionGroup==null) "saved_copycat_up" else "saved_copycat_back")))
        })
        val buttonWidth=(panelWidth-25)/2
        addRenderableWidget(EditorButton(left+10,top+panelHeight-27,buttonWidth,tr("cancel")) { onClose() })
        choose=addRenderableWidget(EditorButton(left+15+buttonWidth,top+panelHeight-27,buttonWidth,tr("saved_copycat_choose")) { chooseSelected() }.apply { primary=true })
        refreshEntries(keepSelection=true)
        search.setResponder { query=it;refreshEntries(keepSelection=true) }
    }

    private fun refreshEntries(keepSelection:Boolean=false) {
        entries=(versionGroup?.versions?.map { version ->
            FormLibraryTree.Entry(versionGroup.path,ui("ps_version",version.form.version),version.key,version.form,versions=listOf(version))
        }?.filter { query.isBlank() || it.key.contains(query.trim(),true) || ui("ps_version",it.form!!.version).contains(query.trim(),true) }
            ?: tree.entries(path,query)).let { found ->
            if(versionGroup==null && allowAir && (query.isBlank() && path.isEmpty() || query.isNotBlank() && ui("bg_air_source").contains(query.trim(),true))) listOf(airEntry)+found else found
        }
        val preferred=if(keepSelection) entries.indexOfFirst { !it.folder && it.contains(selectedKey) } else -1
        index=if(preferred>=0) preferred else index.coerceIn(0,entries.lastIndex.coerceAtLeast(0))
        first=first.coerceIn(0,maximumFirst)
        revealIndex();refreshRows()
        back.active=versionGroup!=null || path.isNotEmpty() || query.isNotEmpty()
    }

    private fun refreshRows() {
        rows.forEach(::removeWidget);rows.clear()
        first=first.coerceIn(0,maximumFirst)
        entries.drop(first).take(visibleRows).forEachIndexed { position,entry ->
            rows+=addRenderableWidget(RowButton(listX+3,listY+3+position*ROW_HEIGHT,listWidth-14,entry,first+position))
        }
        refreshChoose()
    }

    private fun refreshChoose() {
        choose?.active=selectedKey.isNotEmpty() && (selectedKey in library.forms || allowAir && selectedKey==AIR_KEY)
        choose?.message=tr(if(versionGroup==null && tree.groupOf(selectedKey)?.versions.orEmpty().size>1) "saved_copycat_versions_choose" else "saved_copycat_choose")
    }

    private fun revealIndex() {
        if(index<first) first=index
        if(index>=first+visibleRows) first=index-visibleRows+1
        first=first.coerceIn(0,maximumFirst)
    }

    private fun moveSelection(next:Int) {
        if(entries.isEmpty()) return
        index=next.coerceIn(0,entries.lastIndex)
        val entry=entries[index]
        if(!entry.folder) selectedKey=selectionFor(entry)
        revealIndex();refreshRows()
        rows.firstOrNull { it.entryIndex==index }?.let { setFocused(it) }
    }

    private fun selectionFor(entry:FormLibraryTree.Entry):String {
        if(versionGroup!=null) return entry.key
        if(query.isNotBlank() && entry.key.contains(query.trim(),true)) return entry.key
        return if(entry.contains(selectedKey)) selectedKey else entry.key
    }

    private fun activate(entry:FormLibraryTree.Entry,position:Int) {
        if(entry.folder) {
            path=entry.path;first=0;index=0;lastClickKey="";refreshEntries();return
        }
        index=position;selectedKey=selectionFor(entry);refreshChoose()
        val now=System.currentTimeMillis()
        if(lastClickKey==entry.key && now-lastClickTime<350) chooseSelected()
        else { lastClickKey=entry.key;lastClickTime=now }
    }

    private fun up() {
        if(versionGroup!=null) { onClose();return }
        if(query.isNotEmpty()) { search.value="";return }
        if(path.isEmpty()) return
        val previous=path
        path=path.dropLast(1);first=0;index=0;refreshEntries()
        entries.indexOfFirst { it.folder && it.path==previous }.takeIf { it>=0 }?.let(::moveSelection)
    }

    private fun chooseSelected() {
        if(finished || selectedKey !in library.forms && !(allowAir && selectedKey==AIR_KEY)) return
        val group=if(versionGroup==null) tree.groupOf(selectedKey) else null
        if(group!=null && group.versions.size>1) {
            lastClickKey="";lastClickTime=0L
            minecraft?.setScreen(SavedCopycatSelectionScreen(this,selectedKey,false,{ key -> selectedKey=key;finishSelection() },group))
            return
        }
        finishSelection()
    }

    private fun finishSelection() {
        if(finished) return
        finished=true
        // The parent rebuilds its graph widgets before receiving the document change.
        minecraft?.setScreen(parent)
        apply(selectedKey)
    }

    override fun setInitialFocus() { rows.firstOrNull { it.entryIndex==index }?.let { setInitialFocus(it) } ?: setInitialFocus(search) }

    override fun onClose() {
        if(finished) return
        finished=true;minecraft?.setScreen(parent)
    }

    override fun keyPressed(key:Int,scan:Int,mods:Int):Boolean {
        if(key==256) { onClose();return true }
        if(key==70 && hasControlDown()) { setFocused(search);return true }
        if(search.isFocused) {
            if(key==264 && entries.isNotEmpty()) { moveSelection(index);return true }
            if(key==257 || key==335) {
                entries.getOrNull(index)?.let { if(it.folder) activate(it,index) else { selectedKey=selectionFor(it);chooseSelected() } }
                return true
            }
            return super.keyPressed(key,scan,mods)
        }
        val focusedRow=rows.firstOrNull { it.isFocused }
        if(focusedRow!=null) index=focusedRow.entryIndex
        else if((key==257 || key==335) && focused is Button) return super.keyPressed(key,scan,mods)
        when(key) {
            265 -> moveSelection(index-1)
            264 -> moveSelection(index+1)
            268 -> moveSelection(0)
            269 -> moveSelection(entries.lastIndex)
            266 -> moveSelection(index-visibleRows)
            267 -> moveSelection(index+visibleRows)
            263,259 -> up()
            262 -> entries.getOrNull(index)?.let { if(it.folder) activate(it,index) else if(it.versions.size>1) { selectedKey=selectionFor(it);chooseSelected() } }
            257,335 -> {
                val entry=rows.firstOrNull { it.isFocused }?.entry ?: entries.getOrNull(index)
                if(entry?.folder==true) activate(entry,index) else if(entry!=null) { selectedKey=selectionFor(entry);chooseSelected() }
                else return super.keyPressed(key,scan,mods)
            }
            else -> return super.keyPressed(key,scan,mods)
        }
        return true
    }

    override fun mouseClicked(mx:Double,my:Double,button:Int):Boolean {
        if(button==1 && inPreview(mx,my)) { draggingPreview=true;return true }
        if(button==0 && maximumFirst>0 && mx>=listX+listWidth-10 && mx<listX+listWidth-2 && my>=listY+3 && my<listY+listHeight-3) {
            draggingScroll=true;scrollTo(my);return true
        }
        return super.mouseClicked(mx,my,button)
    }

    override fun mouseDragged(mx:Double,my:Double,button:Int,dx:Double,dy:Double):Boolean {
        if(draggingPreview && button==1) { yaw+=dx.toFloat()*1.5f;return true }
        if(draggingScroll && button==0) { scrollTo(my);return true }
        return super.mouseDragged(mx,my,button,dx,dy)
    }

    override fun mouseReleased(mx:Double,my:Double,button:Int):Boolean {
        val consumed=draggingPreview || draggingScroll
        draggingPreview=false;draggingScroll=false
        return super.mouseReleased(mx,my,button) || consumed
    }

    private fun scrollTo(my:Double) {
        val ratio=((my-listY-3)/(listHeight-6).coerceAtLeast(1)).coerceIn(0.0,1.0)
        first=(ratio*maximumFirst+0.5).toInt();refreshRows()
    }

    override fun mouseScrolled(mx:Double,my:Double,dx:Double,dy:Double):Boolean {
        if(mx>=listX && mx<listX+listWidth && my>=listY && my<listY+listHeight) {
            if(dy!=0.0) { first=(first-if(dy>0) 1 else -1).coerceIn(0,maximumFirst);refreshRows() }
            return true
        }
        return super.mouseScrolled(mx,my,dx,dy)
    }

    private fun inPreview(mx:Double,my:Double)=mx>=previewX && mx<previewX+previewWidth && my>=listY && my<listY+listHeight

    override fun render(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        ArchitectTheme.background(g,width,height)
        ArchitectTheme.header(g,width,title.string,width-16)
        ArchitectTheme.panel(g,left,top,panelWidth,panelHeight,if(versionGroup==null) ui("saved_copycat_shared") else ui("saved_copycat_versions_count",versionGroup.versions.size))
        val breadcrumb=if(versionGroup!=null && query.isBlank()) versionGroup.fullName else if(query.isNotBlank()) ui("saved_copycat_results",entries.size) else (listOf(ui("saved_copycat_root"))+path).joinToString(" / ")
        g.drawString(font,font.plainSubstrByWidth(breadcrumb,panelWidth-60),left+43,top+59,ArchitectTheme.brass,false)
        ArchitectTheme.inset(g,listX,listY,listWidth,listHeight)
        ArchitectTheme.viewport(g,previewX,listY,previewWidth,listHeight)
        renderPreview(g)
        if(entries.isEmpty()) font.split(tr("saved_copycat_empty"),listWidth-16).take(3).forEachIndexed { i,line ->
            g.drawString(font,line,listX+8,listY+9+i*11,ArchitectTheme.muted,false)
        }
        if(maximumFirst>0) {
            val track=listHeight-6
            val thumb=maxOf(10,track*visibleRows/entries.size)
            val y=listY+3+(track-thumb)*first/maximumFirst
            g.fill(listX+listWidth-9,listY+3,listX+listWidth-4,listY+listHeight-3,ArchitectTheme.shadow)
            g.fill(listX+listWidth-9,y,listX+listWidth-4,y+thumb,ArchitectTheme.brassDark)
        }
        ArchitectTheme.footer(g,width,height,ui(if(versionGroup==null) "saved_copycat_hint" else "saved_copycat_version_hint"))
        super.render(g,mx,my,delta)
    }

    private fun renderPreview(g:GuiGraphics) {
        val form=library.forms[selectedKey]
        if(form==null) {
            if(allowAir && selectedKey==AIR_KEY) g.drawCenteredString(font,tr("bg_air_source"),previewX+previewWidth/2,listY+listHeight/2-4,ArchitectTheme.muted)
            return
        }
        val ready=previewCache.getOrPut(selectedKey) { runCatching { Geometry.validate(form) } }
        val detailHeight=if(listHeight>=90) 38 else 25
        val sceneHeight=(listHeight-detailHeight).coerceAtLeast(20)
        g.enableScissor(previewX+2,listY+2,previewX+previewWidth-2,listY+sceneHeight)
        try {
            val definition=ready.getOrNull()
            if(definition!=null) {
                val settings=library.printSettings[selectedKey]
                val surfaces=definition.groups.associate { it.id to (settings?.surfaces?.get(it.id) ?: "minecraft:stone") }
                Meshes.preview(g,previewX+previewWidth/2,listY+sceneHeight/2,minOf(previewWidth*0.36f,sceneHeight*0.41f),yaw,
                    Geometry.rotate(definition.parts,settings?.turn ?: 0),groups=definition.groups,surfaces=surfaces)
            } else font.split(Component.literal(ready.exceptionOrNull()?.message.orEmpty()),(previewWidth-12).coerceAtLeast(1)).take(3).forEachIndexed { i,line ->
                g.drawString(font,line,previewX+6,listY+6+i*10,ArchitectTheme.danger,false)
            }
        } finally { g.flush();g.disableScissor() }
        val label=form.name.substringAfterLast('/').trim().ifEmpty { form.name }
        g.drawString(font,font.plainSubstrByWidth(label,previewWidth-12),previewX+6,listY+listHeight-detailHeight+3,ArchitectTheme.text,false)
        g.drawString(font,font.plainSubstrByWidth(ui("ps_version",form.version),previewWidth-12),previewX+6,listY+listHeight-detailHeight+14,ArchitectTheme.muted,false)
        if(listHeight>=90) g.drawString(font,font.plainSubstrByWidth(ui("node_preview_hint"),previewWidth-12),previewX+6,listY+listHeight-12,ArchitectTheme.muted,false)
    }

    override fun renderBackground(g:GuiGraphics,mx:Int,my:Int,delta:Float) {}
    override fun isPauseScreen()=false

    internal fun rowPoint(key:String):Pair<Double,Double>?=rows.firstOrNull { it.entry.contains(key) && !it.entry.folder }?.let { it.x+it.width/2.0 to it.y+it.height/2.0 }
    internal fun folderPoint(path:String):Pair<Double,Double>?=rows.firstOrNull { it.entry.folder && it.entry.path.joinToString("/")==path }?.let { it.x+it.width/2.0 to it.y+it.height/2.0 }
    internal fun searchPoint():Pair<Double,Double> = search.x+search.width/2.0 to search.y+search.height/2.0
    internal fun visibleKeys()=rows.mapNotNull { it.entry.key.takeIf(String::isNotEmpty) }
    internal fun isVersionDialog()=versionGroup!=null
    internal fun currentFolder()=path.joinToString("/")
    internal fun selectedKey()=selectedKey

    private fun rowLabel(entry:FormLibraryTree.Entry,full:Boolean=false):String {
        val name=if(full) entry.fullName else entry.label
        return when {
            entry.folder->"$name (${entry.count})"
            entry.key==AIR_KEY->name
            versionGroup!=null->"${ui("ps_version",entry.form!!.version)} · ${entry.key}"
            else->if(entry.versions.size>1) "$name · ${ui("saved_copycat_versions_count",entry.versions.size)}" else name
        }
    }

    private inner class RowButton(x:Int,y:Int,w:Int,val entry:FormLibraryTree.Entry,val entryIndex:Int):
        Button(x,y,w,ROW_HEIGHT-1,Component.literal(rowLabel(entry,true)),{ activate(entry,entryIndex) },DEFAULT_NARRATION) {
        init { setTooltip(Tooltip.create(message)) }
        override fun renderWidget(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
            ArchitectTheme.button(g,x,y,width,height,isHoveredOrFocused,if(entry.folder) entryIndex==index else entry.contains(selectedKey))
            if(entry.folder) {
                g.fill(x+6,y+5,x+12,y+7,ArchitectTheme.brass)
                g.renderOutline(x+5,y+7,11,8,ArchitectTheme.brass)
            } else {
                g.renderOutline(x+6,y+6,9,9,if(entry.contains(currentKey)) ArchitectTheme.green else ArchitectTheme.muted)
                g.fill(x+10,y+7,x+11,y+14,ArchitectTheme.brassDark)
            }
            val text=rowLabel(entry,query.isNotBlank())
            g.drawString(font,font.plainSubstrByWidth(text,(width-28).coerceAtLeast(1)),x+21,y+7,ArchitectTheme.text,false)
            if(entry.contains(currentKey)) g.fill(x+width-5,y+3,x+width-2,y+6,ArchitectTheme.green)
        }
    }

    companion object {
        const val AIR_KEY="minecraft:air"
        private const val ROW_HEIGHT=22
        fun open(parent:Screen?,currentKey:String,allowAir:Boolean=false,apply:(String)->Unit) {
            Minecraft.getInstance().setScreen(SavedCopycatSelectionScreen(parent,currentKey,allowAir,apply))
        }
        fun open(currentKey:String,allowAir:Boolean=false,apply:(String)->Unit) = open(Minecraft.getInstance().screen,currentKey,allowAir,apply)
    }
}
