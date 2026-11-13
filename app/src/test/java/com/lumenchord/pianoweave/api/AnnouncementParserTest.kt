package com.lumenchord.pianoweave.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementParserTest {

    private val server = "https://server.test/"

    /** The example from the server's API docs. */
    private val documented = """
        {"announcements": [
          {"id": "play-takedown", "severity": "critical", "language": "pl",
           "title": "Zaktualizuj aplikację inaczej",
           "body": "Aplikacji nie ma już w Google Play. Zobacz instrukcję (https://example.com/update).",
           "blocks": [
             {"type": "paragraph", "spans": [
               {"text": "Aplikacji nie ma już w ", "bold": false, "italic": false, "link": null},
               {"text": "Google Play", "bold": true, "italic": false, "link": null},
               {"text": ". Zobacz ", "bold": false, "italic": false, "link": null},
               {"text": "instrukcję", "bold": false, "italic": false, "link": "https://example.com/update"},
               {"text": ".", "bold": false, "italic": false, "link": null}]}],
           "link": {"url": "https://example.com/update", "label": "Jak zaktualizować"},
           "dismissible": false, "starts_at": 1794000000, "ends_at": null}
        ]}
    """.trimIndent()

    private fun parse(json: String) = AnnouncementParser.parseResponse(json, server)!!

    private fun one(fields: String) = parse("""{"announcements": [{"id": "a", "title": "T", $fields}]}""").single()

    @Test
    fun readsTheDocumentedExample() {
        val a = parse(documented).single()
        assertEquals("play-takedown", a.id)
        assertEquals(AnnouncementSeverity.CRITICAL, a.severity)
        assertTrue(a.isBlocking)
        assertEquals(AnnouncementLink("https://example.com/update", "Jak zaktualizować"), a.link)
        assertEquals(1794000000L, a.startsAt)
        assertNull(a.endsAt)
        val spans = (a.blocks.single() as AnnouncementBlock.Paragraph).spans
        assertEquals(5, spans.size)
        assertTrue(spans[1].bold)
        assertEquals("https://example.com/update", spans[3].link)
        assertEquals("$server play-takedown", a.key)
    }

    @Test
    fun headingsAndListsAreRead() {
        val a = one(""""blocks": [
            {"type": "heading", "level": 2, "spans": [{"text": "H"}]},
            {"type": "list", "ordered": true, "start": 3, "items": [[{"text": "x"}], [{"text": "y", "italic": true}]]}
        ]""")
        assertEquals(AnnouncementBlock.Heading(2, listOf(AnnouncementSpan("H"))), a.blocks[0])
        val list = a.blocks[1] as AnnouncementBlock.ItemList
        assertTrue(list.ordered)
        assertEquals(3, list.start)
        assertTrue(list.items[1].single().italic)
    }

    @Test
    fun unknownBlockTypesAreSkipped() {
        val a = one(""""blocks": [{"type": "image", "src": "x"}, {"type": "paragraph", "spans": [{"text": "kept"}]}]""")
        assertEquals(listOf(AnnouncementBlock.Paragraph(listOf(AnnouncementSpan("kept")))), a.blocks)
    }

    @Test
    fun withoutBlocksThePlainBodyIsShownAsParagraphs() {
        val a = one(""""body": "First.\n\nSecond\nline."""")
        assertEquals(
            listOf("First.", "Second\nline."),
            a.blocks.map { (it as AnnouncementBlock.Paragraph).spans.single().text }
        )
    }

    @Test
    fun linksThatArentHttpsAreDropped() {
        val a = one(""""link": {"url": "http://example.com"},
            "blocks": [{"type": "paragraph", "spans": [
              {"text": "a", "link": "javascript:alert(1)"}, {"text": "b", "link": "intent://x"}, {"text": "c", "link": "https://ok.test/p"}]}]""")
        assertNull(a.link)
        val spans = (a.blocks.single() as AnnouncementBlock.Paragraph).spans
        assertEquals(listOf(null, null, "https://ok.test/p"), spans.map { it.link })
    }

    @Test
    fun aLinkWithoutLabelKeepsANullLabel() {
        assertEquals(AnnouncementLink("https://x.test/", null), one(""""link": {"url": "https://x.test/", "label": null}""").link)
    }

    @Test
    fun dismissibleDefaultsToFalseOnlyForCritical() {
        assertFalse(one(""""severity": "critical"""").dismissible)
        assertTrue(one(""""severity": "warning"""").dismissible)
        assertTrue(one(""""severity": "critical", "dismissible": true""").dismissible)
    }

    @Test
    fun anUnknownSeverityShowsAsInfo() {
        assertEquals(AnnouncementSeverity.INFO, one(""""severity": "emergency"""").severity)
    }

    @Test
    fun entriesWithoutIdOrTextAreSkipped() {
        val list = parse("""{"announcements": [{"title": "no id"}, {"id": "empty"}, 5, {"id": "ok", "body": "b"}]}""")
        assertEquals(listOf("ok"), list.map { it.id })
    }

    @Test
    fun aResponseThatIsntAListIsNull() {
        assertNull(AnnouncementParser.parseResponse("""{"detail": "Not Found"}""", server))
        assertNull(AnnouncementParser.parseResponse("<html>", server))
    }

    @Test
    fun activeUntilItEndsWhateverTheDeviceClockSaysOfTheStart() {
        val a = one(""""starts_at": 100, "ends_at": 200""")
        assertTrue(a.isActive(99))
        assertFalse(a.isActive(200))
        assertTrue(one(""""starts_at": 100""").isActive(Long.MAX_VALUE))
    }

    @Test
    fun anUppercaseSchemeIsNormalized() {
        assertEquals("https://x.test/a", AnnouncementParser.safeHttpsUrl(" HTTPS://X.test/a "))
    }

    @Test
    fun mostSevereFirstThenNewest() {
        val list = parse("""{"announcements": [
            {"id": "old-info", "title": "t", "severity": "info", "starts_at": 1},
            {"id": "warn", "title": "t", "severity": "warning", "starts_at": 1},
            {"id": "new-info", "title": "t", "severity": "info", "starts_at": 5}]}""")
        assertEquals(listOf("warn", "new-info", "old-info"), list.inDisplayOrder().map { it.id })
    }

    @Test
    fun dismissalsOfEndedAnnouncementsAreForgottenPerServer() {
        val other = "https://other.test/"
        val keys = setOf(announcementKey(server, "still"), announcementKey(server, "ended"), announcementKey(other, "ended"))
        val listed = parse("""{"announcements": [{"id": "still", "title": "t"}]}""")
        assertEquals(
            setOf(announcementKey(server, "still"), announcementKey(other, "ended")),
            prunedKeys(keys, server, listed)
        )
    }
}
