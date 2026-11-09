package com.lumenchord.pianoweave.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class MetronomeClickTest {

    private val font = MetronomeClick.soundFont(
        listOf(
            MetronomeClick.ClickSample("Wood beat", 77, MetronomeClick.woodblock(940.0, seed = 1)),
            MetronomeClick.ClickSample("Wood bar", 76, MetronomeClick.woodblock(1240.0, seed = 2))
        )
    )

    /** The chunks of a RIFF list body, by id (the LIST chunks by their type). */
    private fun chunks(bytes: ByteArray, from: Int, until: Int): Map<String, ByteArray> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val found = mutableMapOf<String, ByteArray>()
        var position = from
        while (position < until) {
            val id = String(bytes, position, 4)
            val size = buffer.getInt(position + 4)
            val data = bytes.copyOfRange(position + 8, position + 8 + size)
            found[if (id == "LIST") String(data, 0, 4) else id] = data
            position += 8 + size + size % 2
        }
        assertEquals("chunks end exactly at their container's end", until, position)
        return found
    }

    @Test
    fun soundFontIsAWellFormedRiff() {
        val buffer = ByteBuffer.wrap(font).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(font, 0, 4))
        assertEquals(font.size - 8, buffer.getInt(4))
        assertEquals("sfbk", String(font, 8, 4))
        val top = chunks(font, 12, font.size)
        assertEquals(setOf("INFO", "sdta", "pdta"), top.keys)

        val pdta = top.getValue("pdta")
        val records = chunks(pdta, 4, pdta.size)
        val sizes = mapOf("phdr" to 38, "pbag" to 4, "pmod" to 10, "pgen" to 4, "inst" to 22, "ibag" to 4, "imod" to 10, "igen" to 4, "shdr" to 46)
        assertEquals(sizes.keys, records.keys)
        sizes.forEach { (id, size) -> assertEquals("$id holds whole records", 0, records.getValue(id).size % size) }
        // Two samples and the terminal record; the drum kit in bank 128
        assertEquals(3, records.getValue("shdr").size / 46)
        assertEquals(128, ByteBuffer.wrap(records.getValue("phdr")).order(ByteOrder.LITTLE_ENDIAN).getShort(22).toInt())

        val sdta = top.getValue("sdta")
        val smpl = chunks(sdta, 4, sdta.size).getValue("smpl")
        val shdr = ByteBuffer.wrap(records.getValue("shdr")).order(ByteOrder.LITTLE_ENDIAN)
        // The second sample ends inside the sample data, with the 46 required zero points after it
        assertEquals(smpl.size / 2 - 46, shdr.getInt(46 + 24))
    }

    @Test
    fun strokeStartsQuietlyAndRingsOut() {
        val pcm = MetronomeClick.woodblock(940.0, seed = 1)
        val peak = pcm.maxOf { abs(it.toInt()) }
        assertTrue("headroom left: $peak", peak in 15_000..18_500)
        assertEquals(0, pcm.first().toInt())
        assertTrue("silent at the end", abs(pcm.last().toInt()) < 10)
    }
}
