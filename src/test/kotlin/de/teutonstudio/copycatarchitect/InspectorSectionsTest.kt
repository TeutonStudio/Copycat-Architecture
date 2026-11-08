package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.client.InspectorSections
import org.junit.jupiter.api.Test
import kotlin.test.*

class InspectorSectionsTest {
    @Test fun `collapsing one section removes only its content and keeps later headers accessible`() {
        val state=InspectorSections()
        state.setExpanded("2d",true)
        state.toggle("properties")
        val layout=state.layout(listOf("properties" to 400,"2d" to 150,"3d" to 180))
        assertEquals(listOf(0,26,202),layout.map { it.top })
        assertEquals(listOf(0,150,180),layout.map { it.contentHeight })
        assertTrue(state.isExpanded("2d"));assertTrue(state.isExpanded("3d"))
        state.toggle("2d")
        assertTrue(state.isExpanded("3d"))
    }
    @Test fun `selection changes retain preferences even when a section temporarily disappears`() {
        val state=InspectorSections()
        state.setExpanded("properties",false);state.setExpanded("2d",true);state.setExpanded("3d",false)
        state.layout(listOf("properties" to 500,"2d" to 180,"3d" to 180))
        assertEquals(listOf(0),state.layout(listOf("properties" to 80)).map { it.contentHeight })
        val next=state.layout(listOf("properties" to 900,"2d" to 100,"3d" to 100))
        assertEquals(listOf(false,true,false),next.map { it.expanded })
        assertEquals(listOf(0,100,0),next.map { it.contentHeight })
    }
    @Test fun `view instances keep independent preferences and empty layouts are supported`() {
        val first=InspectorSections();val second=InspectorSections()
        first.setExpanded("connections",false);first.setExpanded("properties",false)
        assertFalse(first.isExpanded("connections"));assertTrue(second.isExpanded("connections"))
        assertTrue(second.isExpanded("properties"));assertFalse(second.isExpanded("2d"))
        assertEquals(emptyList(),first.layout(emptyList()))
    }
}
