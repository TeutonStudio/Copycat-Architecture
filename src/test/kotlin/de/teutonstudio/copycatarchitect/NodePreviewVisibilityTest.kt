package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.client.NodePreviewVisibility
import org.junit.jupiter.api.Test
import kotlin.test.*

class NodePreviewVisibilityTest {
    @Test fun `only result previews are expanded before a view override`() {
        val visibility=NodePreviewVisibility()
        assertFalse(visibility.isExpanded("source"))
        assertFalse(visibility.isExpanded("operation",false))
        assertTrue(visibility.isExpanded("result",true))
    }

    @Test fun `opening sources and closing results retain independent explicit preferences`() {
        val visibility=NodePreviewVisibility()
        assertTrue(visibility.toggle("source"))
        assertFalse(visibility.toggle("result",true))
        repeat(3) {
            assertTrue(visibility.isExpanded("source"))
            assertFalse(visibility.isExpanded("result",true))
            assertFalse(visibility.isExpanded("other_source"))
            assertTrue(visibility.isExpanded("other_result",true))
        }
        assertFalse(visibility.toggle("source"))
        assertTrue(visibility.toggle("result",true))
    }

    @Test fun `explicit preferences survive a changed default`() {
        val visibility=NodePreviewVisibility()
        visibility.setExpanded("opened",true)
        visibility.setExpanded("closed",false)
        assertTrue(visibility.isExpanded("opened",false))
        assertTrue(visibility.isExpanded("opened",true))
        assertFalse(visibility.isExpanded("closed",true))
        assertFalse(visibility.isExpanded("closed",false))
    }

    @Test fun `removed document nodes can release overrides without resetting retained nodes`() {
        val visibility=NodePreviewVisibility()
        visibility.setExpanded("removed",true)
        visibility.setExpanded("source",true)
        visibility.setExpanded("result",false)
        visibility.retainAll(setOf("source","result"))
        assertFalse(visibility.isExpanded("removed"))
        assertTrue(visibility.isExpanded("source"))
        assertFalse(visibility.isExpanded("result",true))
    }
}
