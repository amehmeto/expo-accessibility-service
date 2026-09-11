package expo.modules.accessibilityservice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The candidate loop and its recycling — the part of URL-bar detection that shipped
 * two real defects and had no test.
 *
 * Both were about failure handling, not about happy paths: a `try` placed around the
 * loop instead of inside it abandoned every candidate after the first that threw, and
 * a node that matched but held no text was reported as "no node found", which told the
 * blind-spot detector a working browser was broken.
 */
class UrlBarTreeReaderTest {

    private val chromeUrlBar = "com.android.chrome:id/url_bar"
    private val focusOwnId = "org.mozilla.focus:id/display_url"
    private val focusMozacId = "org.mozilla.focus:id/mozac_browser_toolbar_url_view"

    private class FakeNode(
        override val text: String?,
        override val isFocused: Boolean = false,
        private val throwOnRecycle: Boolean = false,
    ) : UrlBarNode {
        var recycled = false
            private set

        override fun recycle() {
            recycled = true
            if (throwOnRecycle) throw IllegalStateException("already reclaimed")
        }
    }

    /** A tree answering [nodesByViewId], and recording what was asked of it. */
    private class FakeTree(
        private val nodesByViewId: Map<String, List<UrlBarNode>>,
        private val throwFor: Set<String> = emptySet(),
    ) : UrlBarNodeSource {
        val queried = mutableListOf<String>()

        override fun findByViewId(viewId: String): List<UrlBarNode>? {
            queried.add(viewId)
            if (viewId in throwFor) throw IllegalStateException("tree in a bad state")
            return nodesByViewId[viewId]
        }
    }

    @Test
    fun `reads the address from the node carrying the candidate id`() {
        val tree = FakeTree(mapOf(chromeUrlBar to listOf(FakeNode("facebook.com"))))

        val lookup = UrlBarTreeReader.read(tree, listOf(chromeUrlBar))

        assertEquals(UrlBarLookup.Read(UrlBarReading("facebook.com", isEditing = false)), lookup)
    }

    @Test
    fun `carries the focus state of the node it read`() {
        val tree = FakeTree(
            mapOf(chromeUrlBar to listOf(FakeNode("facebook.com", isFocused = true))),
        )

        val lookup = UrlBarTreeReader.read(tree, listOf(chromeUrlBar))

        assertEquals(UrlBarLookup.Read(UrlBarReading("facebook.com", isEditing = true)), lookup)
    }

    @Test
    fun `trims what the node holds`() {
        val tree = FakeTree(mapOf(chromeUrlBar to listOf(FakeNode("  facebook.com  "))))

        val lookup = UrlBarTreeReader.read(tree, listOf(chromeUrlBar))

        assertEquals(UrlBarLookup.Read(UrlBarReading("facebook.com", isEditing = false)), lookup)
    }

    @Test
    fun `a node holding no text is Empty, not a miss`() {
        // The id is RIGHT — a blank new tab or a page mid-load. Reporting this as a
        // miss is what had the blind-spot detector calling working browsers broken.
        val tree = FakeTree(mapOf(chromeUrlBar to listOf(FakeNode("   "))))

        assertEquals(UrlBarLookup.Empty, UrlBarTreeReader.read(tree, listOf(chromeUrlBar)))
    }

    @Test
    fun `a node holding null text is Empty, not a miss`() {
        val tree = FakeTree(mapOf(chromeUrlBar to listOf(FakeNode(null))))

        assertEquals(UrlBarLookup.Empty, UrlBarTreeReader.read(tree, listOf(chromeUrlBar)))
    }

    @Test
    fun `no node carrying any candidate id is the miss`() {
        val tree = FakeTree(emptyMap())

        assertEquals(
            UrlBarLookup.NoNodeMatched,
            UrlBarTreeReader.read(tree, listOf(focusOwnId, focusMozacId)),
        )
    }

    @Test
    fun `an empty node list is the same as no node`() {
        val tree = FakeTree(mapOf(chromeUrlBar to emptyList()))

        assertEquals(UrlBarLookup.NoNodeMatched, UrlBarTreeReader.read(tree, listOf(chromeUrlBar)))
    }

    // ========== The candidates ==========

    @Test
    fun `falls through to the second candidate when the first matches nothing`() {
        val tree = FakeTree(mapOf(focusMozacId to listOf(FakeNode("facebook.com"))))

        val lookup = UrlBarTreeReader.read(tree, listOf(focusOwnId, focusMozacId))

        assertEquals(UrlBarLookup.Read(UrlBarReading("facebook.com", isEditing = false)), lookup)
        assertEquals(listOf(focusOwnId, focusMozacId), tree.queried)
    }

    @Test
    fun `stops at the first candidate that matches`() {
        val tree = FakeTree(
            mapOf(
                focusOwnId to listOf(FakeNode("facebook.com")),
                focusMozacId to listOf(FakeNode("should-not-be-read.com")),
            ),
        )

        val lookup = UrlBarTreeReader.read(tree, listOf(focusOwnId, focusMozacId))

        assertEquals(UrlBarLookup.Read(UrlBarReading("facebook.com", isEditing = false)), lookup)
        assertEquals(listOf(focusOwnId), tree.queried)
    }

    @Test
    fun `a candidate that throws does not cost the remaining ones`() {
        // The defect this file exists for. A try around the loop instead of inside it
        // meant the fallback id — the whole reason a browser has several — was never
        // reached once the first candidate misbehaved.
        val tree = FakeTree(
            nodesByViewId = mapOf(focusMozacId to listOf(FakeNode("facebook.com"))),
            throwFor = setOf(focusOwnId),
        )

        val lookup = UrlBarTreeReader.read(tree, listOf(focusOwnId, focusMozacId))

        assertEquals(UrlBarLookup.Read(UrlBarReading("facebook.com", isEditing = false)), lookup)
        assertEquals(listOf(focusOwnId, focusMozacId), tree.queried)
    }

    @Test
    fun `every candidate throwing is a miss, not a crash`() {
        val tree = FakeTree(emptyMap(), throwFor = setOf(focusOwnId, focusMozacId))

        assertEquals(
            UrlBarLookup.NoNodeMatched,
            UrlBarTreeReader.read(tree, listOf(focusOwnId, focusMozacId)),
        )
    }

    @Test
    fun `no candidates at all is a miss`() {
        assertEquals(UrlBarLookup.NoNodeMatched, UrlBarTreeReader.read(FakeTree(emptyMap()), emptyList()))
    }

    // ========== Recycling ==========

    @Test
    fun `recycles the nodes it read`() {
        val node = FakeNode("facebook.com")
        val tree = FakeTree(mapOf(chromeUrlBar to listOf(node)))

        UrlBarTreeReader.read(tree, listOf(chromeUrlBar))

        assertTrue(node.recycled)
    }

    @Test
    fun `recycles every node a candidate returned, not just the first`() {
        val read = FakeNode("facebook.com")
        val ignored = FakeNode("other.com")
        val tree = FakeTree(mapOf(chromeUrlBar to listOf(read, ignored)))

        UrlBarTreeReader.read(tree, listOf(chromeUrlBar))

        assertTrue(read.recycled)
        assertTrue(ignored.recycled)
    }

    @Test
    fun `recycles the nodes of a candidate that matched nothing useful`() {
        val empty = FakeNode(null)
        val tree = FakeTree(mapOf(chromeUrlBar to listOf(empty)))

        UrlBarTreeReader.read(tree, listOf(chromeUrlBar))

        assertTrue(empty.recycled)
    }

    @Test
    fun `a node throwing on recycle does not cost the remaining candidates`() {
        // Same bug through the other door: an exception escaping the finally would
        // abandon the loop just as surely as one escaping the body.
        val reclaimed = FakeNode(null, throwOnRecycle = true)
        val tree = FakeTree(
            mapOf(
                focusOwnId to listOf(reclaimed),
                focusMozacId to listOf(FakeNode("facebook.com")),
            ),
        )

        val lookup = UrlBarTreeReader.read(tree, listOf(focusOwnId, focusMozacId))

        // focusOwnId matched a node holding nothing, so it answered Empty and the loop
        // stopped there — with the recycle throwing and being swallowed.
        assertEquals(UrlBarLookup.Empty, lookup)
        assertTrue(reclaimed.recycled)
    }

    @Test
    fun `a node throwing on recycle still lets a later candidate be tried`() {
        val reclaimed = FakeNode("", throwOnRecycle = true)
        val tree = FakeTree(
            nodesByViewId = mapOf(focusMozacId to listOf(FakeNode("facebook.com"))),
            throwFor = setOf(focusOwnId),
        )
        // The throwing candidate returns no nodes at all, so the loop moves on and the
        // second candidate answers.
        val lookup = UrlBarTreeReader.read(tree, listOf(focusOwnId, focusMozacId))

        assertEquals(UrlBarLookup.Read(UrlBarReading("facebook.com", isEditing = false)), lookup)
        assertTrue(!reclaimed.recycled)
    }
}
