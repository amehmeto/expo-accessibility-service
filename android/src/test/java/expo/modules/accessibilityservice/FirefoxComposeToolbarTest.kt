package expo.modules.accessibilityservice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The descriptions are the ones Firefox 157.0 gave its `ADDRESSBAR_URL_BOX` node on an
 * emulator, copied from `uiautomator dump`. Firefox composes them as
 * `"$title $url. $hint"`: the title is empty outside a custom tab, and the hint is
 * translated.
 */
class FirefoxComposeToolbarTest {

    @Test
    fun `reads the address of a page`() {
        assertEquals(
            "example.com",
            FirefoxComposeToolbar.extractDisplayedAddress(" example.com. Search or enter address"),
        )
    }

    @Test
    fun `reads an address that itself ends like a domain`() {
        assertEquals(
            "en.wikipedia.org/wiki/Example.com",
            FirefoxComposeToolbar.extractDisplayedAddress(
                " en.wikipedia.org/wiki/Example.com. Search or enter address",
            ),
        )
    }

    @Test
    fun `reads an address with a long path and query`() {
        assertEquals(
            "en.wikipedia.org/w/index.php?title=Special:Search&search=android+accessibility+service" +
                "&ns0=1&fulltext=1&profile=advanced",
            FirefoxComposeToolbar.extractDisplayedAddress(
                " en.wikipedia.org/w/index.php?title=Special:Search" +
                    "&search=android+accessibility+service&ns0=1&fulltext=1&profile=advanced" +
                    ". Search or enter address",
            ),
        )
    }

    @Test
    fun `reads an address with a fragment`() {
        assertEquals(
            "firefox.com/en-US/?redirect_source=mozilla-org#download",
            FirefoxComposeToolbar.extractDisplayedAddress(
                " firefox.com/en-US/?redirect_source=mozilla-org#download. Search or enter address",
            ),
        )
    }

    @Test
    fun `reads an address whose encoded spaces follow a dot`() {
        assertEquals(
            "example.com/a%20b.%20c/d?q=e.%20f",
            FirefoxComposeToolbar.extractDisplayedAddress(
                " example.com/a%20b.%20c/d?q=e.%20f. Search or enter address",
            ),
        )
    }

    @Test
    fun `reads the address of a custom tab, not the domain in its title`() {
        assertEquals(
            "en.wikipedia.org",
            FirefoxComposeToolbar.extractDisplayedAddress(
                "example.com - Wikipedia en.wikipedia.org. Search or enter address",
            ),
        )
    }

    @Test
    fun `reads the address whatever the language of the hint`() {
        // The hint is `search_hint` in French, from the resource table of the same APK.
        assertEquals(
            "example.com",
            FirefoxComposeToolbar.extractDisplayedAddress(" example.com. Recherche ou adresse"),
        )
    }

    @Test
    fun `reads no address on the home screen, where the description is the hint alone`() {
        assertNull(FirefoxComposeToolbar.extractDisplayedAddress("Search"))
    }

    @Test
    fun `reads no address in a node that has no description`() {
        assertNull(FirefoxComposeToolbar.extractDisplayedAddress(null))
    }
}
