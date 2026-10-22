package com.example.fucknotes

import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fucknotes.data.ink.InkCache
import com.example.fucknotes.data.ink.InkCodec
import com.example.fucknotes.data.ink.InkStroke
import com.example.fucknotes.data.model.InkTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/** Streaming the ink in batches, in parallel, gives the same page as decoding it in one go. */
@RunWith(AndroidJUnit4::class)
class InkDecodeTest {
    private fun page(count: Int): String {
        val strokes = (0 until count).map { n ->
            val tool = if (n % 7 == 0) InkTool.HIGHLIGHTER else InkTool.PEN
            val colour = (0xFF000000L or ((n * 2654435761L) and 0xFFFFFF)).toInt()
            val brush = InkCodec.brushFor(tool, colour, 2f + n % 5, 1f)
            val inputs = MutableStrokeInputBatch()
            repeat(5 + n % 9) { i -> inputs.add(InputToolType.STYLUS, 10f * n + i, 30f + i * 3f, i * 16L) }
            InkStroke(Stroke(brush, inputs), tool)
        }
        return InkCodec.encode(strokes)
    }

    @Test fun batchedParallelDecodeMatchesTheWholeDecode() = runBlocking {
        val json = page(300)
        val whole = InkCodec.decode(json)
        val dtos = InkCodec.parse(json)
        assertEquals(300, dtos.size)
        val streamed = coroutineScope {
            dtos.chunked(37).map { batch -> async(Dispatchers.Default) { batch.mapNotNull { InkCodec.decodeStroke(it) } } }
                .flatMap { it.await() }
        }
        assertEquals(whole.size, streamed.size)
        whole.zip(streamed).forEach { (a, b) ->
            assertEquals(a.tool, b.tool)
            assertEquals(a.stroke.brush, b.stroke.brush)
            assertEquals(a.stroke.inputs.size, b.stroke.inputs.size)
        }
        // And the page re-encodes to the same bytes, so the cache key holds.
        assertEquals(json, InkCodec.encode(streamed))
    }

    @Test fun theCacheAnswersOnlyForTheJsonItWasBuiltFrom() {
        val json = page(3)
        val strokes = InkCodec.decode(json)
        InkCache.put(42L, json, strokes)
        assertSame(strokes, InkCache.get(42L, json))
        assertSame(strokes, InkCache.get(42L, String(json.toCharArray())))
        assertNull(InkCache.get(42L, page(4)))
        assertNull(InkCache.get(43L, json))
        InkCache.evict(42L)
        assertNull(InkCache.get(42L, json))
    }
}
