package de.teutonstudio.copycatarchitect.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.math.BigDecimal

/** A scrollable, transactional property sheet shared by structural definitions. */
class StructurePropertyScreen private constructor(
    private val parent:Screen?,title:Component,
    private val properties:StructurePropertyScreen.()->Unit,
    private val apply:()->Unit
):EditorScreen(title) {
    private var first=0
    private var count=0
    private var status=""
    private val left get()=maxOf(8,(width-620)/2)
    private val fieldWidth get()=minOf(620,width-16)
    private val visibleRows get()=((height-108)/24).coerceAtLeast(1)
    override fun titleAvailableWidth()=width-16
    override fun init() {
        resetDropdown();count=0
        properties()
        if(first>(count-visibleRows).coerceAtLeast(0)) { first=(count-visibleRows).coerceAtLeast(0);rebuildWidgets();return }
        button(left,height-55,(fieldWidth-8)/2,"cancel") { onClose() }
        button(left+(fieldWidth+8)/2,height-55,(fieldWidth-8)/2,"apply") {
            runCatching(apply).onSuccess { onClose() }.onFailure { status=it.message.orEmpty() }
        }
    }
    private fun row(draw:(Int)->Unit) {
        val index=count++
        if(index in first until first+visibleRows) draw(36+(index-first)*24)
    }
    fun action(text:String,run:()->Unit) = row { y -> button(left,y,fieldWidth,Component.literal(text)) { run();rebuildWidgets() } }
    fun hint(text:String)=row { y -> button(left,y,fieldWidth,Component.literal(text)) {}.active=false }
    fun int(text:String,value:Int,range:IntRange,change:(Int)->Unit)=row { y ->
        number(left,y,fieldWidth,value,range,{ "$text: $it" }) { change(it);rebuildWidgets() }
    }
    fun blocks(text:String,value16:Int,range:IntRange,change:(Int)->Unit)=row { y ->
        val label="$text: ${blockText(value16)} ${ui("structure_blocks")}"
        button(left,y,fieldWidth,Component.literal(label)) {
            EditorValueInput.text(Component.literal(text),blockText(value16),24,{ runCatching { parseBlocks(it,range) }.isSuccess }) {
                change(parseBlocks(it,range));rebuildWidgets()
            }
        }
    }
    fun text(text:String,value:String,maxLength:Int=128,valid:(String)->Boolean={ true },change:(String)->Unit)=row { y ->
        button(left,y,fieldWidth,Component.literal("$text: $value")) {
            EditorValueInput.text(Component.literal(text),value,maxLength,valid) { change(it);rebuildWidgets() }
        }
    }
    fun <T> choice(text:String,value:T,values:List<T>,display:(T)->String,change:(T)->Unit)=row { y ->
        select(left,y,fieldWidth,value,values,{ "$text: ${display(it)}" }) { change(it);rebuildWidgets() }
    }
    fun toggle(text:String,value:Boolean,change:(Boolean)->Unit)=choice(text,value,listOf(false,true),{ ui(if(it) "structure_yes" else "structure_no") },change)
    override fun mouseClicked(x:Double,y:Double,button:Int)=dropdownClick(x,y,button) || super.mouseClicked(x,y,button)
    override fun keyPressed(key:Int,scan:Int,mods:Int)=dropdownKey(key) || super.keyPressed(key,scan,mods)
    override fun mouseScrolled(x:Double,y:Double,dx:Double,dy:Double):Boolean {
        if(super.mouseScrolled(x,y,dx,dy)) return true
        first=(first-dy.toInt()).coerceIn(0,(count-visibleRows).coerceAtLeast(0));rebuildWidgets();return true
    }
    override fun renderContents(g:GuiGraphics,mx:Int,my:Int,delta:Float) {
        val message=status.ifEmpty { if(count>visibleRows) ui("structure_scroll",first+1,minOf(first+visibleRows,count),count) else ui("structure_draft_hint") }
        label(g,font.plainSubstrByWidth(message,width-16),8,height-28,if(status.isEmpty()) ArchitectTheme.muted else ArchitectTheme.danger)
    }
    override fun onClose() { minecraft?.setScreen(parent) }
    companion object {
        fun open(title:Component,properties:StructurePropertyScreen.()->Unit,apply:()->Unit) {
            val mc=Minecraft.getInstance();mc.setScreen(StructurePropertyScreen(mc.screen,title,properties,apply))
        }
        fun blockText(value16:Int):String=BigDecimal(value16).divide(BigDecimal(16)).stripTrailingZeros().toPlainString()
        fun parseBlocks(text:String,range:IntRange):Int {
            val value=BigDecimal(text.trim().replace(',','.')).multiply(BigDecimal(16)).intValueExact()
            require(value in range) { "${blockText(range.first)} … ${blockText(range.last)}; 1/16" }
            return value
        }
    }
}
