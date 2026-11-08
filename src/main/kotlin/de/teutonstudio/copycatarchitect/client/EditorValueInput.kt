package de.teutonstudio.copycatarchitect.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/** A draft never mutates the edited document until a valid value is accepted. */
class EditorValueDraft<T>(initial:String,private val parse:(String)->T?) {
    var value=initial
    private var finished=false
    val valid get()=!finished && parse(value)!=null
    fun submit(apply:(T)->Unit):Boolean {
        if(finished) return false
        val parsed=parse(value) ?: return false
        apply(parsed)
        finished=true
        return true
    }
    fun cancel() { finished=true }
}

/** Direct entry for numbers and names; closed sets continue to use a choice control. */
object EditorValueInput {
    fun integer(title:Component,current:Int,range:IntRange,apply:(Int)->Unit) {
        require(!range.isEmpty())
        open(title,current.toString(),12,Component.literal("${range.first} … ${range.last}"),
            { it.toIntOrNull()?.takeIf { number -> number in range } },apply)
    }
    fun text(title:Component,current:String,maxLength:Int=32,validate:(String)->Boolean={ it.isNotBlank() },apply:(String)->Unit) {
        open(title,current,maxLength,null,{ it.trim().takeIf(validate) },apply)
    }
    private fun <T> open(title:Component,current:String,maxLength:Int,hint:Component?,parse:(String)->T?,apply:(T)->Unit) {
        val minecraft=Minecraft.getInstance()
        minecraft.setScreen(ValueScreen(minecraft.screen,title,current,maxLength,hint,parse,apply))
    }
    private class ValueScreen<T>(private val parent:Screen?,title:Component,current:String,private val maxLength:Int,
        private val hint:Component?,parse:(String)->T?,private val apply:(T)->Unit):Screen(title) {
        private val draft=EditorValueDraft(current,parse)
        private var error=""
        private val panelWidth get()=minOf(344,(width-32).coerceAtLeast(128))
        private val panelHeight get()=164
        private val left get()=(width-panelWidth)/2
        private val top get()=(height-panelHeight)/2
        override fun init() {
            val input=addRenderableWidget(EditorTextField(font,left+12,top+48,panelWidth-24,22,title).apply {
                setMaxLength(maxLength);value=draft.value
            })
            val buttonWidth=(panelWidth-30)/2
            addRenderableWidget(EditorButton(left+12,top+panelHeight-30,buttonWidth,tr("cancel")) { onClose() })
            val save=addRenderableWidget(EditorButton(left+18+buttonWidth,top+panelHeight-30,buttonWidth,tr("save")) { accept() }.apply { primary=true })
            save.active=draft.valid
            input.setResponder { draft.value=it;error="";save.active=draft.valid }
            setInitialFocus(input)
            input.setHighlightPos(0)
        }
        private fun accept() {
            try {
                if(draft.submit(apply)) minecraft?.setScreen(parent)
                else error=ui("value_invalid")
            } catch(e:Exception) { error=e.message ?: title.string }
        }
        override fun onClose() { draft.cancel();minecraft?.setScreen(parent) }
        override fun keyPressed(key:Int,scan:Int,mods:Int):Boolean {
            if(key==257 || key==335) { accept();return true }
            return super.keyPressed(key,scan,mods)
        }
        override fun render(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
            ArchitectTheme.background(g,width,height)
            ArchitectTheme.header(g,width,title.string,width-16)
            ArchitectTheme.panel(g,left,top,panelWidth,panelHeight,title.string)
            g.drawString(font,tr("value_field"),left+12,top+34,ArchitectTheme.muted,false)
            val count="${draft.value.length} / $maxLength"
            g.drawString(font,count,left+panelWidth-12-font.width(count),top+34,ArchitectTheme.muted,false)
            hint?.let { g.drawString(font,font.plainSubstrByWidth(it.string,panelWidth-24),left+12,top+79,ArchitectTheme.muted,false) }
            val valid=draft.valid && error.isEmpty()
            ArchitectTheme.inset(g,left+12,top+99,panelWidth-24,24)
            g.fill(left+19,top+108,left+23,top+112,if(valid) ArchitectTheme.green else ArchitectTheme.danger)
            val status=error.ifEmpty { ui(if(valid) "value_ready" else "value_invalid") }
            g.drawString(font,font.plainSubstrByWidth(status,panelWidth-48),left+29,top+107,
                if(valid) ArchitectTheme.green else ArchitectTheme.danger,false)
            ArchitectTheme.footer(g,width,height,"Enter · ${ui("save")}    Esc · ${ui("cancel")}")
            super.render(g,mx,my,delta)
        }
        override fun renderBackground(g:GuiGraphics,mx:Int,my:Int,delta:Float) {}
        override fun isPauseScreen()=false
    }
}
