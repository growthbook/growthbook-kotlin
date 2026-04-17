package com.sdk.growthbook.util

import com.sdk.growthbook.utils.Resource
import com.sdk.growthbook.utils.readSse
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SseReaderTest {
    private fun channelOf(text: String): ByteReadChannel =
        ByteReadChannel(text.toByteArray())

    @Test
    fun `single data event emits success`() = runBlocking {

        val channel = channelOf("data: hello\n\n")

        val results = mutableListOf<Resource<String>>()
        channel.readSse { results.add(it) }

        assertEquals(1, results.size)
        assertTrue(results[0] is Resource.Success)
        assertEquals("hello", (results[0] as Resource.Success).data)
    }

    @Test
    fun `multiple data events emit multiple successes`() = runBlocking {
        val channel = channelOf(
            "data: first\n\n" +
                "data: second\n\n" +
                "data: third\n\n"
        )

        val results = mutableListOf<Resource<String>>()
        channel.readSse { results.add(it) }

        assertEquals(3, results.size)
        assertEquals("first", (results[0] as Resource.Success).data)
        assertEquals("second", (results[1] as Resource.Success).data)
        assertEquals("third", (results[2] as Resource.Success).data)
    }

    @Test
    fun `empty channel emits nothing`() = runBlocking {
        val channel = channelOf("")

        val results = mutableListOf<Resource<String>>()
        channel.readSse { results.add(it) }

        assertTrue(results.isEmpty())
    }

    @Test
    fun `empty data value is ignored`() = runBlocking {
        val channel = channelOf("data: \n\n")

        val results = mutableListOf<Resource<String>>()
        channel.readSse { results.add(it) }

        assertTrue(results.isEmpty())
    }

    @Test
    fun `event without trailing blank line is not emitted`() = runBlocking {

        val channel = channelOf("data: incomplete")

        val results = mutableListOf<Resource<String>>()
        channel.readSse { results.add(it) }

        assertTrue(results.isEmpty())
    }

    @Test
    fun `unknown field is ignored and does not emit`() = runBlocking {
        val channel = channelOf("comment: something\n\n")

        val results = mutableListOf<Resource<String>>()
        channel.readSse { results.add(it) }

        assertTrue(results.isEmpty())
    }

    @Test
    fun `data with colon in value is parsed correctly`() = runBlocking {
        val channel = channelOf("data: key:value\n\n")

        val results = mutableListOf<Resource<String>>()
        channel.readSse { results.add(it) }

        assertEquals("key:value", (results[0] as Resource.Success).data)
    }

    @Test
    fun `unknown fields between valid events do not affect output`() = runBlocking {
        val channel = channelOf(
            "comment: ignore me\n" +
                "data: real\n\n"
        )

        val results = mutableListOf<Resource<String>>()
        channel.readSse { results.add(it) }

        assertEquals(1, results.size)
        assertEquals("real", (results[0] as Resource.Success).data)
    }

    @Test
    fun `last data wins when multiple data lines before blank line`() = runBlocking {

        val channel = channelOf(
            "data: first\n" +
                "data: second\n\n"
        )

        val results = mutableListOf<Resource<String>>()
        channel.readSse { results.add(it) }

        assertEquals(1, results.size)
        assertEquals("second", (results[0] as Resource.Success).data)
    }
}
