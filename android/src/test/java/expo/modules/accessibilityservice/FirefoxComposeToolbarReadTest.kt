package expo.modules.accessibilityservice

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/**
 * Firefox as the service sees it. The trees have the shape, the tags and the texts
 * that `uiautomator dump` gave for Firefox 157.0 on an emulator: the browser screen,
 * the search screen with the address bar at the bottom, and the home screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class FirefoxComposeToolbarReadTest {
    private val firefox = "org.mozilla.firefox"
    private val toolbarViewId = "org.mozilla.firefox:id/composable_toolbar"
    private val homeViewId = "org.mozilla.firefox:id/homepageView"
    private val pageDescription = " en.wikipedia.org/wiki/Example.com. Search or enter address"
    private val pageAddress = "en.wikipedia.org/wiki/Example.com"

    private lateinit var service: AccessibilityService

    @Before
    fun setUp() {
        AccessibilityService.resetForTesting()
        ShadowLog.clear()
        service = spy(Robolectric.buildService(AccessibilityService::class.java).create().get())
        service.onServiceConnected()
    }

    @After
    fun tearDown() {
        AccessibilityService.resetForTesting()
    }

    // ========== The browser screen ==========

    @Test
    fun `reads the address of the page Firefox shows`() {
        showInFront(browserScreen())

        assertEquals(
            AccessibilityService.ForegroundUrlBar(firefox, pageAddress, isEditing = false),
            AccessibilityService.readForegroundUrlBar(),
        )
    }

    @Test
    fun `announces the address of a page opened in Firefox`() {
        val listener = mock<AccessibilityService.EventListener>()
        AccessibilityService.addEventListener(listener)
        showInFront(browserScreen())

        readPageContent(times = 1)

        verify(listener).onUrlBarChanged(eq(firefox), eq(pageAddress), any(), eq(false))
    }

    @Test
    fun `does not walk the window, where the page is`() {
        val window = browserScreen()
        showInFront(window)

        AccessibilityService.readForegroundUrlBar()

        verify(window, never()).getChild(any())
    }

    @Test
    fun `recycles the toolbar view and the nodes it fetched`() {
        val siteInformation = node(tag = "browser.toolbar.site.info.secure")
        val addressBox = node(tag = "ADDRESSBAR_URL_BOX", description = pageDescription)
        val toolbar = toolbarView(addressBox, siteInformation)
        showInFront(window(toolbarViewId to toolbar))

        AccessibilityService.readForegroundUrlBar()

        verify(toolbar).recycle()
        verify(siteInformation).recycle()
        verify(addressBox).recycle()
    }

    @Test
    fun `stops walking a toolbar that holds no address after a bounded number of nodes`() {
        var nodesFetched = 0
        showInFront(window(toolbarViewId to endlessTree { nodesFetched++ }))

        assertNull(AccessibilityService.readForegroundUrlBar()?.text)
        assertEquals(FirefoxComposeToolbar.MAX_NODES_WALKED, nodesFetched)
    }

    // ========== The search screen and the home screen ==========

    @Test
    fun `reads what the user types in the address field`() {
        showInFront(searchScreen())

        assertEquals(
            AccessibilityService.ForegroundUrlBar(
                firefox,
                "https://en.wikipedia.org/wiki/Example.com",
                isEditing = true,
            ),
            AccessibilityService.readForegroundUrlBar(),
        )
    }

    @Test
    fun `does not enter the suggestions listed before the address field`() {
        val suggestions = node(tag = "mozac.awesomebar", children = listOf(node(), node()))
        showInFront(searchScreen(suggestions))

        AccessibilityService.readForegroundUrlBar()

        verify(suggestions, never()).getChild(any())
    }

    @Test
    fun `does not enter the feed of the home screen`() {
        val feed = node(tag = "homepage.view", children = listOf(node(), node()))
        showInFront(homeScreen(feed))

        AccessibilityService.readForegroundUrlBar()

        verify(feed, never()).getChild(any())
    }

    // ========== The blind-spot report ==========

    @Test
    fun `Firefox is not reported while its Compose toolbar yields the address`() {
        showInFront(browserScreen())

        visitFirefox(times = UrlBarBlindSpotDetector.MIN_MISSED_VISITS_BEFORE_REPORTING)

        assertEquals(0, staleUrlBarReports())
    }

    @Test
    fun `Firefox is not reported for visits to its home screen, which shows no address`() {
        showInFront(homeScreen())

        visitFirefox(times = UrlBarBlindSpotDetector.MIN_MISSED_VISITS_BEFORE_REPORTING)

        assertEquals(0, staleUrlBarReports())
    }

    @Test
    fun `Firefox is reported when its toolbar no longer holds an address node`() {
        val renamedAddressBox = node(tag = "RENAMED_URL_BOX", description = pageDescription)
        showInFront(window(toolbarViewId to toolbarView(renamedAddressBox)))

        visitFirefox(times = UrlBarBlindSpotDetector.MIN_MISSED_VISITS_BEFORE_REPORTING)

        assertEquals(1, staleUrlBarReports())
    }

    // ========== The trees ==========

    /** The browser screen: the toolbar is a view of its own, beside the page. */
    private fun browserScreen() = window(toolbarViewId to toolbarView())

    private fun toolbarView(
        addressBox: AccessibilityNodeInfo =
            node(tag = "ADDRESSBAR_URL_BOX", description = pageDescription),
        siteInformation: AccessibilityNodeInfo = node(tag = "browser.toolbar.site.info.secure"),
    ): AccessibilityNodeInfo {
        val bar = node(
            children = listOf(
                node(children = listOf(siteInformation, addressBox, node(description = "Reader view"))),
                node(description = "New tab"),
                node(tag = "ADDRESSBAR_TABS_COUNTER", children = listOf(node(), node())),
                node(description = "More options"),
            ),
        )
        return inside(wrappers = 3, bar)
    }

    /**
     * The search screen, with the address bar at the bottom: the toolbar is drawn
     * inside the home screen's view, and the suggestions come before the field.
     */
    private fun searchScreen(
        suggestions: AccessibilityNodeInfo = node(tag = "mozac.awesomebar"),
    ): AccessibilityNodeInfo {
        val addressField = node(
            tag = "ADDRESSBAR_SEARCH_BOX",
            text = "https://en.wikipedia.org/wiki/Example.com",
            focused = true,
        )
        val editBar = node(
            tag = "ADDRESSBAR_EDIT_MODE",
            children = listOf(
                node(
                    children = listOf(
                        node(tag = "SEARCH_SELECTOR", children = listOf(node(), node())),
                        addressField,
                        node(description = "Voice search"),
                        node(description = "Clear"),
                    ),
                ),
                node(tag = "ADDRESSBAR_EDIT_MODE_HORIZONTAL_DIVIDER"),
            ),
        )
        val toolbar = node(
            tag = toolbarViewId,
            children = listOf(node(children = listOf(suggestions)), inside(wrappers = 2, editBar)),
        )
        return window(homeViewId to inside(wrappers = 3, toolbar))
    }

    /** The home screen: the feed comes before the toolbar, whose description is the hint alone. */
    private fun homeScreen(
        feed: AccessibilityNodeInfo = node(tag = "homepage.view"),
    ): AccessibilityNodeInfo {
        val bar = node(
            children = listOf(
                node(
                    children = listOf(
                        node(tag = "SEARCH_SELECTOR", children = listOf(node(), node())),
                        node(tag = "ADDRESSBAR_URL_BOX", description = "Search"),
                        node(description = "Voice search"),
                    ),
                ),
                node(tag = "ADDRESSBAR_TABS_COUNTER", children = listOf(node(), node())),
            ),
        )
        val toolbar = node(tag = toolbarViewId, children = listOf(inside(wrappers = 2, bar)))
        return window(homeViewId to inside(wrappers = 2, node(children = listOf(feed, toolbar))))
    }

    /** A tree without an address, in which every node has two children. */
    private fun endlessTree(onFetch: () -> Unit): AccessibilityNodeInfo = mock {
        on { childCount } doReturn 2
        on { getChild(any()) } doAnswer {
            onFetch()
            endlessTree(onFetch)
        }
    }

    /** A Firefox window in which a lookup by view id finds [host] and nothing else. */
    private fun window(host: Pair<String, AccessibilityNodeInfo>): AccessibilityNodeInfo = mock {
        on { packageName } doReturn firefox
        on { childCount } doReturn 1
        on { findAccessibilityNodeInfosByViewId(host.first) } doReturn listOf(host.second)
    }

    private fun inside(wrappers: Int, innermost: AccessibilityNodeInfo): AccessibilityNodeInfo =
        (1..wrappers).fold(innermost) { wrapped, _ -> node(children = listOf(wrapped)) }

    private fun node(
        tag: String? = null,
        description: String? = null,
        text: String? = null,
        focused: Boolean = false,
        children: List<AccessibilityNodeInfo> = emptyList(),
    ): AccessibilityNodeInfo = mock {
        on { viewIdResourceName } doReturn tag
        on { contentDescription } doReturn description
        on { this.text } doReturn text
        on { isFocused } doReturn focused
        on { childCount } doReturn children.size
        children.forEachIndexed { index, child -> on { getChild(index) } doReturn child }
    }

    // ========== Driving the service ==========

    private fun showInFront(window: AccessibilityNodeInfo) {
        doReturn(window).`when`(service).rootInActiveWindow
    }

    /** As many visits, each making lookups, and the window change that judges the last one. */
    private fun visitFirefox(times: Int) {
        repeat(times) {
            openFirefox()
            readPageContent(times = 3)
        }
        openFirefox()
    }

    private fun openFirefox() {
        service.onAccessibilityEvent(
            mock<AccessibilityEvent> {
                on { eventType } doReturn AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                on { packageName } doReturn firefox
            },
        )
    }

    /** One tree lookup per content event: each waits out the 250ms query pacing. */
    private fun readPageContent(times: Int) {
        val contentChanged = mock<AccessibilityEvent> {
            on { eventType } doReturn AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            on { packageName } doReturn firefox
        }
        repeat(times) {
            ShadowSystemClock.advanceBy(Duration.ofMillis(250))
            service.onAccessibilityEvent(contentChanged)
        }
    }

    private fun staleUrlBarReports(): Int =
        ShadowLog.getLogsForTag("AccessibilityService").count {
            it.msg.startsWith("No URL bar found in $firefox")
        }
}
