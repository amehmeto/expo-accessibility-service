package expo.modules.accessibilityservice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlBarEmissionGateTest {

    private val chrome = "com.android.chrome"
    private val firefox = "org.mozilla.firefox"

    private var now = 1_000L
    private fun gate(intervalMs: Long = 250L) =
        UrlBarEmissionGate(minQueryIntervalMs = intervalMs, clock = { now })

    // ── query pacing ─────────────────────────────────────────────────────────

    @Test
    fun `the first full-tree query is always allowed`() {
        assertTrue(gate().tryAcquireQuerySlot())
    }

    @Test
    fun `a second query inside the interval is refused`() {
        val gate = gate()

        assertTrue(gate.tryAcquireQuerySlot())
        now += 249
        assertFalse(gate.tryAcquireQuerySlot())
    }

    @Test
    fun `a query is allowed again once the interval has elapsed`() {
        val gate = gate()

        assertTrue(gate.tryAcquireQuerySlot())
        now += 250
        assertTrue(gate.tryAcquireQuerySlot())
    }

    @Test
    fun `a refused attempt does not push the interval back`() {
        val gate = gate()

        assertTrue(gate.tryAcquireQuerySlot())
        // A browser fires content events per keystroke: several refusals in a
        // row must not keep postponing the next real query.
        now += 100
        assertFalse(gate.tryAcquireQuerySlot())
        now += 100
        assertFalse(gate.tryAcquireQuerySlot())
        now += 50
        assertTrue(gate.tryAcquireQuerySlot())
    }

    @Test
    fun `the interval is configurable`() {
        val gate = gate(intervalMs = 1_000L)

        assertTrue(gate.tryAcquireQuerySlot())
        now += 999
        assertFalse(gate.tryAcquireQuerySlot())
        now += 1
        assertTrue(gate.tryAcquireQuerySlot())
    }

    // ── emission de-duplication ──────────────────────────────────────────────

    @Test
    fun `the first url of a browser is announced`() {
        assertTrue(gate().tryClaimEmission(chrome, "facebook.com"))
    }

    @Test
    fun `the same url in the same browser is announced once`() {
        val gate = gate()

        assertTrue(gate.tryClaimEmission(chrome, "facebook.com"))
        assertFalse(gate.tryClaimEmission(chrome, "facebook.com"))
        assertFalse(gate.tryClaimEmission(chrome, "facebook.com"))
    }

    @Test
    fun `a different url in the same browser is announced`() {
        val gate = gate()

        assertTrue(gate.tryClaimEmission(chrome, "facebook.com"))
        assertTrue(gate.tryClaimEmission(chrome, "reddit.com"))
    }

    @Test
    fun `the same url in a different browser is announced`() {
        val gate = gate()

        assertTrue(gate.tryClaimEmission(chrome, "facebook.com"))
        assertTrue(gate.tryClaimEmission(firefox, "facebook.com"))
    }

    @Test
    fun `returning to a previous url is announced again`() {
        val gate = gate()

        assertTrue(gate.tryClaimEmission(chrome, "facebook.com"))
        assertTrue(gate.tryClaimEmission(chrome, "reddit.com"))
        assertTrue(gate.tryClaimEmission(chrome, "facebook.com"))
    }

    @Test
    fun `a window change lets the same url be announced again`() {
        val gate = gate()

        assertTrue(gate.tryClaimEmission(chrome, "facebook.com"))
        assertFalse(gate.tryClaimEmission(chrome, "facebook.com"))

        gate.forgetLastEmission()

        assertTrue(gate.tryClaimEmission(chrome, "facebook.com"))
    }

    @Test
    fun `two readings that would collide once concatenated are told apart`() {
        val gate = gate()

        // Guards the triple against ever being flattened into one string:
        // "com.android.chrome" + "x.com" would then read the same as
        // "com.android.chromex" + ".com".
        assertTrue(gate.tryClaimEmission("com.android.chrome", "x.com"))
        assertTrue(gate.tryClaimEmission("com.android.chromex", ".com"))
    }

    @Test
    fun `the same address is announced again once the user leaves the URL bar`() {
        val gate = gate()

        // Typed, completed inline, then entered: the text never changes, so keying on
        // it alone swallowed the navigation itself.
        assertTrue(gate.tryClaimEmission(chrome, "facebook.com", isEditing = true))
        assertFalse(gate.tryClaimEmission(chrome, "facebook.com", isEditing = true))
        assertTrue(gate.tryClaimEmission(chrome, "facebook.com", isEditing = false))
    }

    @Test
    fun `the loaded page redrawing its address is still announced once`() {
        val gate = gate()

        assertTrue(gate.tryClaimEmission(chrome, "facebook.com", isEditing = false))
        assertFalse(gate.tryClaimEmission(chrome, "facebook.com", isEditing = false))
    }

    @Test
    fun `going back to the URL bar on the same address is announced`() {
        val gate = gate()

        assertTrue(gate.tryClaimEmission(chrome, "facebook.com", isEditing = false))
        assertTrue(gate.tryClaimEmission(chrome, "facebook.com", isEditing = true))
    }

    // ── the two rules are independent ────────────────────────────────────────

    @Test
    fun `pacing a query does not consume an emission claim`() {
        val gate = gate()

        assertTrue(gate.tryAcquireQuerySlot())
        assertTrue(gate.tryClaimEmission(chrome, "facebook.com"))

        now += 250
        assertTrue(gate.tryAcquireQuerySlot())
        assertFalse(gate.tryClaimEmission(chrome, "facebook.com"))
    }
}
