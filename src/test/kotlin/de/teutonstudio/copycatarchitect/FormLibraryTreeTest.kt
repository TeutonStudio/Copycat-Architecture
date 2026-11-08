package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.Form
import de.teutonstudio.copycatarchitect.domain.FormLibraryTree
import org.junit.jupiter.api.Test
import kotlin.test.*

class FormLibraryTreeTest {
    private val forms=linkedMapOf(
        "roof@2" to Form(id="roof",version=2,name="Roof / Edges / Copper"),
        "wall@1" to Form(id="wall",name="Walls / Stone"),
        "roof@1" to Form(id="roof",name="Roof / Edges / Copper"),
        "cap@1" to Form(id="cap",name="Roof / Cap"),
        "simple@1" to Form(id="simple",name="Simple"))

    @Test fun `slash folders count grouped names and keep every version accessible`() {
        val tree=FormLibraryTree(forms)
        val root=tree.entries()
        assertEquals(listOf("Roof","Walls","Simple"),root.map { it.label })
        assertEquals(listOf(2,1,0),root.map { it.count })
        assertTrue(root.take(2).all { it.folder })
        assertEquals(listOf("Edges","Cap"),tree.entries(listOf("Roof")).map { it.label })
        val copper=tree.entries(listOf("Roof","Edges")).single()
        assertEquals("roof@2",copper.key)
        assertEquals(listOf("roof@2","roof@1"),copper.versions.map { it.key })
        assertTrue(copper.contains("roof@1"));assertFalse(copper.contains("missing@1"))
        assertEquals(listOf("Roof","Edges"),tree.folderOf("roof@1"))
        assertEquals(listOf("Roof","Edges"),tree.folderOf("roof@2"))
        assertNull(tree.groupOf("missing@1"));assertTrue(tree.folderOf("missing@1").isEmpty())
    }

    @Test fun `search groups names globally while old exact keys retain their version and siblings`() {
        val snapshot=forms.toMap();val tree=FormLibraryTree(forms)
        assertEquals("roof@2",tree.entries(listOf("Walls"),"cOpPeR").single().key)
        val old=tree.entries(query="roof@1").single()
        assertEquals("roof@1",old.key);assertSame(forms.getValue("roof@1"),old.form)
        assertEquals(listOf("roof@2","roof@1"),old.versions.map { it.key })
        assertEquals("roof@2",tree.entries(query="roof/edges").single().key)
        assertEquals(setOf("roof@2","cap@1"),tree.entries(query="roof").map { it.key }.toSet())
        assertTrue(tree.entries(query="unavailable").isEmpty())
        assertEquals(snapshot,forms)
    }

    @Test fun `path whitespace empty separators and same named folder and leaf remain navigable`() {
        val tree=FormLibraryTree(mapOf(
            "leaf@1" to Form(id="leaf",name=" / Group // Child / "),
            "leaf@2" to Form(id="leaf",version=2,name="Group/Child"),
            "group@1" to Form(id="group",name="Group"),
            "blank@1" to Form(id="blank",name=" / / ")))
        val root=tree.entries()
        assertEquals(listOf("Group","blank","Group"),root.map { it.label })
        assertTrue(root.first().folder);assertFalse(root.last().folder)
        assertEquals("leaf@2",tree.entries(listOf("Group")).single().key)
        assertEquals(2,tree.entries(listOf("Group")).single().versions.size)
        assertEquals(listOf("Group"),tree.folderOf("leaf@1"))
    }

    @Test fun `versions sort numerically newest first regardless of insertion order`() {
        val entries=listOf(2,1,12,7).associate { "part@$it" to Form(id="part",version=it,name="Part") }
        val group=FormLibraryTree(entries).entries().single()
        assertEquals(listOf(12,7,2,1),group.versions.map { it.form.version })
        assertEquals("part@12",group.key)
        assertEquals("part@1",FormLibraryTree(entries).entries(query="PART@1").single().key)
        assertEquals(group,FormLibraryTree(entries.toList().reversed().toMap()).entries().single())
    }

    @Test fun `same full names group across IDs and equal version numbers remain distinguishable`() {
        val entries=mapOf(
            "b@3" to Form(id="b",version=3,name="Frames / Cap"),
            "a@3" to Form(id="a",version=3,name="Frames / Cap"),
            "a@1" to Form(id="a",name="Frames / Cap"))
        val tree=FormLibraryTree(entries)
        val group=tree.entries(listOf("Frames")).single()
        assertEquals(listOf("a@3","b@3","a@1"),group.versions.map { it.key })
        assertEquals("b@3",tree.entries(query="b@3").single().key)
        assertEquals(group,tree.groupOf("b@3"))
        assertEquals(1,tree.entries().single().count)
    }

    @Test fun `same leaf names in different folders and renamed versions remain separate`() {
        val tree=FormLibraryTree(mapOf(
            "a@1" to Form(id="a",name="Roof / Cap"),
            "a@2" to Form(id="a",version=2,name="Roof / New cap"),
            "b@1" to Form(id="b",name="Wall / Cap")))
        assertEquals(setOf("Roof / Cap","Wall / Cap","Roof / New cap"),tree.entries(query="cap").map { it.fullName }.toSet())
        assertEquals(2,tree.entries(listOf("Roof")).size)
        assertTrue(tree.entries(query="cap").all { it.versions.size==1 })
    }

    @Test fun `picker groups form an immutable snapshot of the provided library`() {
        val tree=FormLibraryTree(forms)
        forms["roof@99"]=Form(id="roof",version=99,name="Roof / Edges / Copper")
        assertEquals("roof@2",tree.groupOf("roof@1")!!.key)
        assertEquals(2,tree.groupOf("roof@1")!!.versions.size)
    }
}
