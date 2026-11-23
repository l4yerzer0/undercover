package com.undercover.vpn.utils

import com.undercover.vpn.routing.RoutingConfig
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Performs HTTPS GET requests through the authenticated loopback `mixed` proxy inbound
 * (`undercover-probe-in`) using raw HTTP CONNECT + `Proxy-Authorization: Basic` + TLS.
 *
 * Using an explicit socket avoids Java's `HttpURLConnection` quirks (which strip
 * `Proxy-Authorization` on HTTPS tunnels by default) and ensures the destination hostname
 * is resolved strictly inside the VPN tunnel.
 */
object TunnelHttpClient {

    data class Response(val statusCode: Int, val body: String)

    @OptIn(ExperimentalEncodingApi::class)
    fun httpsGet(
        urlString: String,
        endpoint: RoutingConfig.ProbeEndpoint,
        timeoutMs: Int = 5_000,
    ): Response {
        val url = URL(urlString)
        val host = url.host
        val port = if (url.port != -1) url.port else 443
        val path = buildString {
            append(url.path.ifEmpty { "/" })
            if (!url.query.isNullOrEmpty()) {
                append('?')
                append(url.query)
            }
        }

        val rawSocket = Socket()
        try {
            rawSocket.soTimeout = timeoutMs
            rawSocket.connect(InetSocketAddress("127.0.0.1", endpoint.port), timeoutMs)

            val authToken = Base64.encode(
                "${endpoint.username}:${endpoint.password}".toByteArray(Charsets.UTF_8),
            )
            val connectReq = buildString {
                append("CONNECT $host:$port HTTP/1.1\r\n")
                append("Host: $host:$port\r\n")
                append("Proxy-Authorization: Basic $authToken\r\n")
                append("\r\n")
            }
            rawSocket.getOutputStream().write(connectReq.toByteArray(Charsets.ISO_8859_1))
            rawSocket.getOutputStream().flush()

            val proxyIn = BufferedInputStream(rawSocket.getInputStream())
            val connectHeaders = readHeaderBlock(proxyIn)
            val connectStatus = parseStatusCode(connectHeaders)
            if (connectStatus != 200) {
                throw IllegalStateException("Proxy CONNECT failed with HTTP $connectStatus")
            }

            val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val sslSocket = sslFactory.createSocket(rawSocket, host, port, true) as SSLSocket
            sslSocket.soTimeout = timeoutMs
            runCatching {
                val params = sslSocket.sslParameters
                params.serverNames = listOf(SNIHostName(host))
                params.endpointIdentificationAlgorithm = "HTTPS"
                sslSocket.sslParameters = params
            }
            sslSocket.startHandshake()

            val httpReq = buildString {
                append("GET $path HTTP/1.1\r\n")
                append("Host: $host\r\n")
                append("User-Agent: Undercover/0.5\r\n")
                append("Accept: application/json, */*\r\n")
                append("Accept-Encoding: identity\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }
            sslSocket.outputStream.write(httpReq.toByteArray(Charsets.ISO_8859_1))
            sslSocket.outputStream.flush()

            val tlsIn = BufferedInputStream(sslSocket.inputStream)
            val respHeaders = readHeaderBlock(tlsIn)
            val statusCode = parseStatusCode(respHeaders)
            val isChunked = respHeaders.lineSequence().any {
                it.startsWith("transfer-encoding:", ignoreCase = true) && it.contains("chunked", ignoreCase = true)
            }
            val rawBody = readAllBytes(tlsIn)
            val bodyStr = if (isChunked) {
                decodeChunked(rawBody)
            } else {
                String(rawBody, Charsets.UTF_8)
            }
            return Response(statusCode, bodyStr)
        } finally {
            runCatching { rawSocket.close() }
        }
    }

    private fun readHeaderBlock(input: BufferedInputStream): String {
        val out = ByteArrayOutputStream()
        var state = 0
        while (true) {
            val b = input.read()
            if (b == -1) break
            out.write(b)
            state = when {
                state == 0 && b == '\r'.code -> 1
                state == 1 && b == '\n'.code -> 2
                state == 2 && b == '\r'.code -> 3
                state == 3 && b == '\n'.code -> 4
                b == '\r'.code -> 1
                else -> 0
            }
            if (state == 4) break
            if (out.size() > 32 * 1024) break
        }
        return out.toString(Charsets.ISO_8859_1.name())
    }

    private fun parseStatusCode(headerBlock: String): Int {
        val statusLine = headerBlock.lineSequence().firstOrNull()?.trim() ?: return -1
        val parts = statusLine.split(' ')
        return if (parts.size >= 2) parts[1].toIntOrNull() ?: -1 else -1
    }

    private fun readAllBytes(input: BufferedInputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            out.write(buf, 0, n)
            if (out.size() > 256 * 1024) break
        }
        return out.toByteArray()
    }

    private fun decodeChunked(bytes: ByteArray): String {
        val out = ByteArrayOutputStream()
        var pos = 0
        while (pos < bytes.size) {
            val lineEnd = findCrlf(bytes, pos)
            if (lineEnd == -1) break
            val sizeHex = String(bytes, pos, lineEnd - pos, Charsets.ISO_8859_1).substringBefore(';').trim()
            val chunkSize = sizeHex.toIntOrNull(16) ?: break
            pos = lineEnd + 2
            if (chunkSize == 0) break
            val copyLen = minOf(chunkSize, bytes.size - pos)
            if (copyLen > 0) out.write(bytes, pos, copyLen)
            pos += copyLen + 2
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun findCrlf(bytes: ByteArray, start: Int): Int {
        for (i in start until bytes.size - 1) {
            if (bytes[i] == '\r'.code.toByte() && bytes[i + 1] == '\n'.code.toByte()) {
                return i
            }
        }
        return -1
    }
}
