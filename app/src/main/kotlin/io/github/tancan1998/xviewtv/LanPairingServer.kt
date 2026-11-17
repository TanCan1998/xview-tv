package io.github.tancan1998.xviewtv

import android.content.Context
import android.os.Build
import android.util.Base64
import io.github.tancan1998.xviewtv.core.network.WebSession
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.Closeable
import java.net.*
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread

/** Bound to one private LAN address, serves only a fixed asset allowlist for 180 seconds. */
internal class LanPairingServer(private val context: Context, private val receive: (WebSession) -> Unit) : Closeable {
    private val random = SecureRandom()
    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, random) }.generateKeyPair()
    private val id = encode(ByteArray(32).apply { random.nextBytes(this) })
    val expiresAt = System.currentTimeMillis() + 180000
    private val deviceName = Build.MODEL.take(80).replace(Regex("[\\p{Cc}\\p{Cf}]"), "").ifBlank { "XView TV" }
    private val spki = encode(keys.public.encoded)
    private val metadata = JSONObject().put("v", 1).put("id", id).put("expiresAt", expiresAt).put("deviceName", deviceName).put("spki", spki)
    private val aad = JSONArray().put("xview-pairing").put(1).put(id).put(expiresAt).put(deviceName).put(spki).toString().toByteArray(Charsets.UTF_8)
    val code = MessageDigest.getInstance("SHA-256").digest(aad).take(6).joinToString("") { "%02X".format(it) }.chunked(4).joinToString("-")
    private val address = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>().firstOrNull { it.isSiteLocalAddress }
        ?: throw IllegalStateException("未发现局域网地址，请连接同一 Wi-Fi 或有线网络。")
    private val socket = ServerSocket(0, 8, address).apply { soTimeout = 1000 }
    private val origin = "http://${address.hostAddress}:${socket.localPort}"
    val url = "$origin/#pair=${encode(metadata.toString().toByteArray(Charsets.UTF_8))}"
    private val running = AtomicBoolean(true)
    private val consumed = AtomicBoolean(false)
    private var count = 0
    @Volatile private var active: Socket? = null
    private val assets = mapOf("/" to "index.html", "/index.html" to "index.html", "/style.css" to "style.css",
        "/app.mjs" to "app.mjs", "/cookie.mjs" to "cookie.mjs", "/protocol.mjs" to "protocol.mjs",
        "/lan-crypto.mjs" to "lan-crypto.mjs", "/vendor/forge-1.4.3.min.js" to "vendor/forge-1.4.3.min.js")
    init {
        thread(name = "xview-lan-pairing", isDaemon = true) {
            try {
                while (running.get() && System.currentTimeMillis() < expiresAt && count < 180) {
                    try {
                        socket.accept().use { peer ->
                            active = peer
                            if (!peer.inetAddress.isSiteLocalAddress && !peer.inetAddress.isLoopbackAddress) return@use
                            peer.soTimeout = 4000; count++
                            runCatching { handle(peer) }
                            active = null
                        }
                    } catch (_: SocketTimeoutException) { /* check lifecycle */ }
                    catch (_: Exception) { if (socket.isClosed) break }
                }
            } finally { close() }
        }
    }
    private fun handle(peer: Socket) {
        val input = BufferedInputStream(peer.getInputStream())
        var headerBytes = 0
        fun line(): String {
            val bytes = java.io.ByteArrayOutputStream()
            while (true) {
                val c = input.read(); require(c >= 0 && ++headerBytes <= 8192)
                if (c == 10) break
                bytes.write(c)
            }
            return bytes.toString("US-ASCII").removeSuffix("\r")
        }
        val first = line().split(' '); require(first.size == 3 && first[2] == "HTTP/1.1")
        val headers = mutableMapOf<String, String>()
        while (true) {
            val next = line(); if (next.isEmpty()) break
            val at = next.indexOf(':'); require(at > 0)
            val key = next.substring(0, at).lowercase(); require(key !in headers)
            headers[key] = next.substring(at + 1).trim()
        }
        fun reply(status: Int, body: ByteArray = ByteArray(0), type: String = "text/plain; charset=utf-8") {
            val reason = when (status) { 200 -> "OK"; 202 -> "Accepted"; 409 -> "Conflict"; 410 -> "Gone"; else -> "Rejected" }
            val head = "HTTP/1.1 $status $reason\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'\r\nConnection: close\r\n\r\n"
            peer.getOutputStream().apply { write(head.toByteArray()); write(body); flush() }
        }
        if (headers["host"] != "${address.hostAddress}:${socket.localPort}" || headers.containsKey("transfer-encoding")) { reply(400); return }
        if (System.currentTimeMillis() >= expiresAt || !running.get()) { reply(410); return }
        if (first[0] == "GET") {
            if (consumed.get()) { reply(410); return }
            val asset = assets[first[1]] ?: run { reply(404); return }
            val type = when { asset.endsWith(".mjs") || asset.endsWith(".js") -> "text/javascript; charset=utf-8"; asset.endsWith(".css") -> "text/css; charset=utf-8"; else -> "text/html; charset=utf-8" }
            reply(200, context.assets.open(asset).use { it.readBytes() }, type); return
        }
        if (first[0] != "POST" || first[1] != "/api/v1/pairings/$id/submission") { reply(404); return }
        if (headers["origin"] != origin || headers["content-type"]?.substringBefore(';') != "application/json") { reply(403); return }
        if (consumed.get()) { reply(409); return }
        val size = headers["content-length"]?.toIntOrNull() ?: 0
        if (size !in 1..65536) { reply(400); return }
        val bytes = ByteArray(size); var offset = 0
        while (offset < size) { val read = input.read(bytes, offset, size - offset); require(read > 0); offset += read }
        val session = try { decrypt(JSONObject(String(bytes, Charsets.UTF_8))) }
        catch (_: Exception) { reply(400); return } finally { bytes.fill(0) }
        if (!consumed.compareAndSet(false, true)) { reply(409); return }
        // Deliver once even if the phone disconnects after sending; never duplicate verification.
        runCatching { reply(202) }
        if (running.get() && System.currentTimeMillis() < expiresAt) receive(session)
    }
    private fun decrypt(envelope: JSONObject): WebSession {
        require(envelope.getInt("v") == 1 && envelope.getString("id") == id && envelope.getLong("expiresAt") == expiresAt)
        require(envelope.getString("algorithm") == "RSA-OAEP-256+A256GCM")
        val wrapped = decode(envelope.getString("wrappedKey")); require(wrapped.size == 256)
        val iv = decode(envelope.getString("iv")); require(iv.size == 12)
        val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        cipher.init(Cipher.DECRYPT_MODE, keys.private, OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified(aad)))
        val aes = cipher.doFinal(wrapped); require(aes.size == 32)
        try {
            val gcm = Cipher.getInstance("AES/GCM/NoPadding")
            gcm.init(Cipher.DECRYPT_MODE, SecretKeySpec(aes, "AES"), GCMParameterSpec(128, iv)); gcm.updateAAD(aad)
            val plain = gcm.doFinal(decode(envelope.getString("ciphertext")))
            try {
                val json = JSONObject(String(plain, Charsets.UTF_8)); require(json.length() == 2)
                return WebSession.parse("auth_token=${json.getString("auth_token")};ct0=${json.getString("ct0")}")
            } finally { plain.fill(0) }
        } finally { aes.fill(0) }
    }
    override fun close() { running.set(false); runCatching { socket.close() }; runCatching { active?.close() }; active = null }
    companion object {
        private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        private fun decode(text: String): ByteArray {
            require(text.length <= 60000 && text.matches(Regex("[A-Za-z0-9_-]+")))
            return Base64.decode(text, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        }
    }
}
