package ktx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Tests for [decodeHtmlEntities], the pure-common HTML entity decoder that fixes
 * RSS news titles rendering literal `&#8217;` etc. Covers numeric (decimal/hex,
 * incl. astral surrogate pairs), named refs, and the "leave non-references alone"
 * guarantee.
 */
class HtmlEntitiesTest {

    @Test
    fun decodesDecimalNumericRefs() {
        assertEquals("Cowen’s", "Cowen&#8217;s".decodeHtmlEntities())      // right single quote
        assertEquals("A – B", "A &#8211; B".decodeHtmlEntities())          // en dash
        assertEquals("A — B", "A &#8212; B".decodeHtmlEntities())          // em dash
        assertEquals("can't", "can&#39;t".decodeHtmlEntities())                 // ASCII apostrophe
        assertEquals("100€", "100&#8364;".decodeHtmlEntities())            // euro
    }

    @Test
    fun decodesHexNumericRefs() {
        assertEquals("’", "&#x2019;".decodeHtmlEntities())
        assertEquals("’", "&#X2019;".decodeHtmlEntities())                 // uppercase X
        assertEquals("😀", "&#x1F600;".decodeHtmlEntities())          // 😀 — above the BMP (surrogate pair)
    }

    @Test
    fun decodesNamedRefs() {
        assertEquals("a & b", "a &amp; b".decodeHtmlEntities())
        assertEquals("<tag>", "&lt;tag&gt;".decodeHtmlEntities())
        assertEquals("\"quoted\"", "&quot;quoted&quot;".decodeHtmlEntities())
        assertEquals("it's", "it&apos;s".decodeHtmlEntities())
        assertEquals("“curly”", "&ldquo;curly&rdquo;".decodeHtmlEntities())
        assertEquals("em—dash", "em&mdash;dash".decodeHtmlEntities())
        assertEquals("Beyoncé", "Beyonc&eacute;".decodeHtmlEntities())
    }

    @Test
    fun decodesRealWorldTitles() {
        assertEquals(
            "Benjamin Cowen’s New Memo Points to Q4 Bitcoin Bottom Near \$44,000",
            "Benjamin Cowen&#8217;s New Memo Points to Q4 Bitcoin Bottom Near \$44,000".decodeHtmlEntities(),
        )
        assertEquals(
            "Bitcoin treasury company offers 10% income and still can’t sell nearly half its shares",
            "Bitcoin treasury company offers 10% income and still can&#8217;t sell nearly half its shares".decodeHtmlEntities(),
        )
        assertEquals(
            "Bitmine nears its Ethereum buying limit – Now it needs demand",
            "Bitmine nears its Ethereum buying limit &#8211; Now it needs demand".decodeHtmlEntities(),
        )
    }

    @Test
    fun leavesNonReferencesUntouched() {
        assertEquals("Tom & Jerry", "Tom & Jerry".decodeHtmlEntities())
        assertEquals("R&D; team", "R&D; team".decodeHtmlEntities())
        assertEquals("100% & up", "100% & up".decodeHtmlEntities())
        assertEquals("plain text", "plain text".decodeHtmlEntities())
        // no '&' at all → returns the same instance (fast path)
        val plain = "nothing to decode here"
        assertSame(plain, plain.decodeHtmlEntities())
    }

    @Test
    fun leavesMalformedOrUnknownRefsAsIs() {
        assertEquals("&", "&".decodeHtmlEntities())
        assertEquals("&;", "&;".decodeHtmlEntities())
        assertEquals("&#;", "&#;".decodeHtmlEntities())
        assertEquals("&#8217", "&#8217".decodeHtmlEntities())        // no semicolon
        assertEquals("&#xZZ;", "&#xZZ;".decodeHtmlEntities())        // invalid hex
        assertEquals("&foobar;", "&foobar;".decodeHtmlEntities())    // unknown named ref
        assertEquals("&#xD800;", "&#xD800;".decodeHtmlEntities())    // lone surrogate → invalid, left as-is
        assertEquals("&#0;", "&#0;".decodeHtmlEntities())            // null code point → left as-is
    }

    @Test
    fun handlesMultipleAndAdjacentRefs() {
        assertEquals("a’b’c", "a&#8217;b&#8217;c".decodeHtmlEntities())
        assertEquals("<>&", "&lt;&gt;&amp;".decodeHtmlEntities())
        assertEquals("&&&", "&&&".decodeHtmlEntities())
        assertEquals("", "".decodeHtmlEntities())
    }
}
