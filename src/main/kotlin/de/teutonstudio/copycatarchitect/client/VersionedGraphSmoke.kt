package de.teutonstudio.copycatarchitect.client

import de.teutonstudio.copycatarchitect.domain.*
import de.teutonstudio.copycatarchitect.server.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen

/** Drives actual KilaGraph commands and CC dialogs at both supported GUI scales. */
internal object VersionedGraphSmoke {
    private var ticks=0
    private lateinit var screen:VersionedGraphScreen
    private lateinit var initial:GraphDocument
    private lateinit var snapshot:Snapshot
    private var scale=1
    private var reference=""
    private var applied:GraphPackage?=null
    private lateinit var reopened:ShapeScreen
    fun tick():Boolean {
        val mc=Minecraft.getInstance()
        if(ticks==0 && (mc.screen==null || mc.overlay!=null)) return false
        ticks++
        try {
            when(ticks) {
                1 -> {
                    snapshot=ClientState.snapshot;scale=mc.options.guiScale().get()
                    ClientState.snapshot=Snapshot(library=Library().apply { seed() })
                    mc.options.guiScale().set(1);mc.resizeDisplay()
                    val graph=NodeGraph(nodes=listOf(
                        GraphNode("full",part=Part("copycats:copycat_block"),x=10,y=20),
                        GraphNode("slab",part=Part("copycats:copycat_slab"),x=10,y=250),
                        GraphNode("difference",NodeKind.DIFFERENCE,listOf("full","slab"),x=270,y=20),
                        GraphNode("union",NodeKind.UNION,listOf("full","difference"),x=530,y=20),
                        GraphNode("result",NodeKind.OUTPUT,listOf("union"),x=790,y=20)))
                    initial=VersionedGraphs.fromShape("smoke","Pinned ports",graph)
                    screen=VersionedGraphScreen(ShapeScreen(),initial,setOf("full","difference"),onApply={ applied=it });mc.setScreen(screen)
                }
                5 -> {
                    click(screen,"graph_extract")
                    val d=screen.documentSnapshot();reference=d.references.single().id
                    check(d.shape!!.nodes.size==3)
                    check(d.connections.filter { it.source==reference }.map { it.sourcePort }.toSet()==setOf("out1","out2"))
                    check(VersionedGraphs.compileShape(initial,emptyMap())==VersionedGraphs.compileShape(d,screen.definitionsSnapshot()))
                    shot("normal")
                }
                8 -> {
                    click(screen,"undo");check(screen.documentSnapshot()==initial)
                    click(screen,"redo");check(screen.documentSnapshot().references.single().id==reference)
                    mc.options.guiScale().set(2);mc.resizeDisplay()
                }
                12 -> {
                    check(screen.width==427 && screen.height==240)
                    check(screen.children().filterIsInstance<Button>().all { it.x>=0 && it.y>=0 && it.x+it.width<=screen.width && it.y+it.height<=screen.height })
                    shot("compact")
                    val v1=screen.definitionsSnapshot().values.single()
                    val v2=v1.copy(version=2,ports=v1.ports.filter { it.id!="out1" },outputBindings=v1.outputBindings.filter { it.port!="out1" })
                    ClientState.snapshot=ClientState.snapshot.copy(library=ClientState.snapshot.library.copy(graphs=(screen.definitionsSnapshot()+(v2.key to v2)).toMutableMap()))
                    screen.requestVersionChange(reference,v2)
                }
                15 -> {
                    val dialog=mc.screen!!;check(dialog!==screen)
                    check(dialog.children().filterIsInstance<Button>().all { it.x>=0 && it.y>=0 && it.x+it.width<=dialog.width && it.y+it.height<=dialog.height })
                    shot("version-compact");click(dialog,"apply")
                    check(mc.screen===screen)
                    val d=screen.documentSnapshot();check(d.references.single().reference.version==2)
                    check(d.connections.any { it.source==reference && it.sourcePort=="out1" })
                    check(VersionedGraphs.displayOutputs(d,reference).any { it.id=="out1" && it.name.startsWith("?") })
                    GraphPackages.validate(GraphPackages.export(d,screen.definitionsSnapshot(),emptyMap(),emptyMap()))
                }
                18 -> {
                    click(screen,"undo")
                    check(screen.documentSnapshot().references.single().reference.version==1)
                    check(VersionedGraphs.compileShape(initial,emptyMap())==VersionedGraphs.compileShape(screen.documentSnapshot(),screen.definitionsSnapshot()))
                }
                21 -> {
                    click(screen,"document");check(screen.keyPressed(257,0,0))
                    val source=checkNotNull(applied)
                    check(source.root.references.single().reference.version==1)
                    val executable=Geometry.validate(Form(id="native_smoke",name="Native source",groups=source.root.materialGroups,versionedGraph=source))
                    check(executable.graph==null && executable.versionedGraph==source)
                    check(executable.parts==VersionedGraphs.compileShape(initial,emptyMap()))
                    reopened=ShapeScreen(BuildingScreen()).apply { form=executable };mc.setScreen(reopened)
                }
                25 -> {
                    click(reopened,"versioned_graphs")
                    val restored=mc.screen as VersionedGraphScreen
                    check(restored.documentSnapshot()==applied!!.root)
                    check(restored.documentSnapshot().references.single().reference.version==1)
                    shot("source-reopened")
                    mc.options.guiScale().set(scale);mc.resizeDisplay();ClientState.snapshot=snapshot
                    println("ARCHITECT VERSIONED GRAPH UI SMOKE PASSED: extraction, two named outputs, pinned evaluation, atomic Undo/Redo, normal/compact CC surfaces, explicit version comparison, retained incompatible wire and repairable package")
                    mc.stop()
                }
                else -> return false
            }
        } catch(error:Throwable) { shot("failure-$ticks");throw IllegalStateException("Versioned graph smoke failed at tick $ticks",error) }
        return true
    }
    private fun click(screen:Screen,key:String) {
        val b=screen.children().filterIsInstance<Button>().first { it.visible && it.message.string==ui(key) }
        check(b.active);check(screen.mouseClicked(b.x+b.width/2.0,b.y+b.height/2.0,0));screen.mouseReleased(b.x+b.width/2.0,b.y+b.height/2.0,0)
    }
    private fun shot(name:String) { val mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"architect-versioned-$name.png",mc.mainRenderTarget) { println(it.string) } }
}
