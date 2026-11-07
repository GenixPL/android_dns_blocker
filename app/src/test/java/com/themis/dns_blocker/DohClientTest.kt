package com.themis.dns_blocker

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

class DohClientTest {

    private lateinit var server: HttpServer
    private var seenContentType: String? = null
    private var seenBody: ByteArray? = null
    private var status = 200

    private val reply = byteArrayOf(9, 8, 7)

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/dns-query") { ex ->
            seenContentType = ex.requestHeaders.getFirst("Content-Type")
            seenBody = ex.requestBody.readBytes()
            ex.sendResponseHeaders(status, if (status == 200) reply.size.toLong() else -1)
            if (status == 200) ex.responseBody.use { it.write(reply) } else ex.close()
        }
        server.start()
    }

    @After
    fun tearDown() = server.stop(0)

    private fun client() = DohClient("http://127.0.0.1:${server.address.port}/dns-query")

    @Test
    fun postsWireFormatAndReturnsBody() {
        val q = byteArrayOf(1, 2, 3, 4)
        assertArrayEquals(reply, client().query(q))
        assertEquals("application/dns-message", seenContentType)
        assertArrayEquals(q, seenBody)
    }

    @Test
    fun returnsNullOnHttpError() {
        status = 500
        assertNull(client().query(byteArrayOf(1)))
    }

    @Test
    fun returnsNullWhenUnreachable() {
        server.stop(0)
        assertNull(client().query(byteArrayOf(1)))
    }
}
