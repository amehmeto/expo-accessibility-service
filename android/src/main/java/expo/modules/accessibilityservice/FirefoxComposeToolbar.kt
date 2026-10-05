package expo.modules.accessibilityservice

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Reads the address out of the toolbar that Firefox draws with Compose.
 *
 * Firefox 152 removed its view toolbar: the address is a Compose semantics node, not
 * a view. A lookup by view id cannot reach it: Android resolves the id to a resource
 * and searches views
 * (`AccessibilityInteractionController.findAccessibilityNodeInfosByViewIdUiThread`).
 * It does reach the VIEW that hosts the Compose tree, and from there the address is a
 * few children down, carrying a test tag where a view would carry its id. Firefox
 * sets `testTagsAsResourceId`, and Compose then writes the tag into
 * `viewIdResourceName` itself (`AndroidComposeViewAccessibilityDelegateCompat`),
 * without the `flagReportViewIds` a view waits for.
 *
 * So the walk starts at the host, never at the window: the page is a sibling of the
 * host, not a descendant, and no part of it is fetched.
 *
 * Shapes, tags and texts were read on Firefox 157.0 with `uiautomator dump`. Like a
 * view id, all of it is private to the browser and can change in any release:
 * [UrlBarBlindSpotDetector] reports it when it does.
 */
internal object FirefoxComposeToolbar {

    /**
     * The views hosting the toolbar, WITHOUT the `package:id/` prefix. The browser
     * screen gives the toolbar a view of its own. The home screen, which is also the
     * search screen, draws it inside its own view.
     */
    val HOST_FIELD_IDS = listOf("composable_toolbar", "homepageView")

    /** The address of the page shown. It has no text: the address is in its description. */
    private const val DISPLAYED_ADDRESS_TAG = "ADDRESSBAR_URL_BOX"

    /** The field the user types in. */
    private const val EDITED_ADDRESS_TAG = "ADDRESSBAR_SEARCH_BOX"

    /**
     * The feed of the home screen and the search suggestions: large, and before the
     * toolbar in their parent, the suggestions when the address bar is at the bottom.
     */
    private val SKIPPED_TAGS = setOf("homepage.view", "mozac.awesomebar")

    /**
     * Each node fetched can cost one call into Firefox. On every screen dumped the
     * address was between the 6th and the 13th: a toolbar that lost its tags must not
     * turn each lookup into a walk of everything its host holds.
     */
    const val MAX_NODES_WALKED = 40

    fun isHostId(viewId: String): Boolean = viewId.substringAfter(":id/") in HOST_FIELD_IDS

    /**
     * The address node under [host], or null when the toolbar under it holds none.
     * [host] is recycled either way.
     */
    fun findAddress(host: AccessibilityNodeInfo): UrlBarNode? =
        try {
            Walk().findAddressUnder(host)?.let { AddressNode(it) }
        } finally {
            host.recycle()
        }

    /**
     * The address in the description of the displayed-address node, which Firefox
     * composes as `"$title $url. $hint"` (`Origin.kt`). The title is empty outside a
     * custom tab, the hint is translated, and the address holds no space: it is the
     * last word before the last `". "`. Null when the description is the hint alone,
     * as on the home screen.
     */
    fun extractDisplayedAddress(description: String?): String? =
        description
            ?.substringBeforeLast(". ", missingDelimiterValue = "")
            ?.substringAfterLast(' ')
            ?.ifEmpty { null }

    /** One walk, depth first and in document order, with its budget of nodes. */
    private class Walk {
        private var nodesLeft = MAX_NODES_WALKED

        fun findAddressUnder(parent: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            for (index in 0 until parent.childCount) {
                if (nodesLeft == 0) return null
                nodesLeft--
                val child = parent.getChild(index) ?: continue
                val tag = child.viewIdResourceName
                if (tag == DISPLAYED_ADDRESS_TAG || tag == EDITED_ADDRESS_TAG) return child
                val found = try {
                    if (tag in SKIPPED_TAGS) null else findAddressUnder(child)
                } finally {
                    child.recycle()
                }
                if (found != null) return found
            }
            return null
        }
    }

    private class AddressNode(private val node: AccessibilityNodeInfo) : UrlBarNode {
        override val text: String?
            get() = when (node.viewIdResourceName) {
                EDITED_ADDRESS_TAG -> node.text?.toString()
                else -> extractDisplayedAddress(node.contentDescription?.toString())
            }
        override val isFocused: Boolean get() = node.isFocused
        override fun recycle() = node.recycle()
    }
}
