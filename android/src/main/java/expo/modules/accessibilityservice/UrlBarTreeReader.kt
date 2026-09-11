package expo.modules.accessibilityservice

/** The URL bar's text, and whether the user was editing it — see [AccessibilityService.EventListener]. */
internal data class UrlBarReading(val text: String, val isEditing: Boolean)

/**
 * What one attempt to read the URL bar came back with. Four outcomes, not two,
 * because [UrlBarBlindSpotDetector] must not confuse them:
 *
 *  - [NotAttempted] — the query slot was refused or there was no window root. We did
 *    not look, so this says nothing about whether the id is right.
 *  - [NoNodeMatched] — we looked and no node carried any candidate id. THE miss.
 *  - [Empty] — a node matched and held no text (blank new tab, mid-load). The id is
 *    right; there is simply nothing to read.
 *  - [Read] — a node matched and held an address.
 */
internal sealed interface UrlBarLookup {
    object NotAttempted : UrlBarLookup
    object NoNodeMatched : UrlBarLookup
    object Empty : UrlBarLookup
    data class Read(val reading: UrlBarReading) : UrlBarLookup
}

/** What [UrlBarTreeReader] needs of an accessibility node, and nothing more. */
internal interface UrlBarNode {
    val text: String?
    val isFocused: Boolean
    fun recycle()
}

/** Where [UrlBarTreeReader] looks for them — one window's tree, in practice. */
internal fun interface UrlBarNodeSource {
    /** The nodes carrying [viewId], empty or null when none does. */
    fun findByViewId(viewId: String): List<UrlBarNode>?
}

/**
 * Walks a window's tree for the first node carrying one of a browser's candidate
 * address-bar ids.
 *
 * Extracted from [AccessibilityService] because this is where the interesting
 * mistakes live, and none of them needed a real accessibility service to make: a
 * `try` around the loop instead of inside it silently abandoned every candidate after
 * the first that threw — which is the fallback id the candidate list exists for. It
 * shipped, and only a review caught it. Behind this seam the same logic is a handful
 * of fast unit tests.
 */
internal object UrlBarTreeReader {

    /**
     * Tries [viewIds] in order and returns the first node that matches — [Empty] when
     * it holds no text, since a node that exists means the id is RIGHT.
     *
     * Every node this obtains is recycled, including on the paths that throw. Failure
     * on one candidate degrades to "no match here, try the next": whatever the tree is
     * doing, it must not cost us the remaining candidates.
     */
    fun read(source: UrlBarNodeSource, viewIds: List<String>): UrlBarLookup =
        viewIds.firstNotNullOfOrNull { viewId -> readCandidate(source, viewId) }
            ?: UrlBarLookup.NoNodeMatched

    /** One candidate. Null means "nothing here" — the caller moves to the next id. */
    private fun readCandidate(source: UrlBarNodeSource, viewId: String): UrlBarLookup? {
        var nodes: List<UrlBarNode>? = null
        return try {
            nodes = source.findByViewId(viewId)
            val urlBar = nodes?.firstOrNull() ?: return null
            val text = urlBar.text?.trim()
            if (text.isNullOrEmpty()) UrlBarLookup.Empty else UrlBarLookup.Read(
                UrlBarReading(text, urlBar.isFocused),
            )
        } catch (_: Exception) {
            null
        } finally {
            recycleQuietly(nodes)
        }
    }

    /**
     * A node the framework already reclaimed throws on recycle. Letting that escape
     * would abandon the remaining candidates from inside the `finally`, which is the
     * same bug by another door.
     */
    private fun recycleQuietly(nodes: List<UrlBarNode>?) {
        nodes?.forEach {
            try {
                it.recycle()
            } catch (_: Exception) {
                // Nothing to do and nothing to lose: the node is gone either way.
            }
        }
    }
}
