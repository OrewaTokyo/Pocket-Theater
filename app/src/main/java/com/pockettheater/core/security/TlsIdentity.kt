package com.pockettheater.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocketFactory
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

/**
 * The phone's own certificate for HTTPS. The phone makes it once, and the private key stays inside Android's
 * key store, where the app (and anyone who copies the app's files) cannot read it out.
 * The certificate is self-signed, so a browser warns the first time. The fingerprint lets you check it is the right one.
 */
object TlsIdentity {
    private const val ALIAS = "pockettheater_web"
    private const val DAY_MS = 86_400_000L
    private const val VALID_DAYS = 5L * 365

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    /** Makes the certificate if it is missing or about to expire, and returns the key store that holds it. */
    @Synchronized
    private fun ready(): KeyStore {
        val store = keyStore()
        val cert = store.getCertificate(ALIAS) as? X509Certificate
        if (cert == null || cert.notAfter.time < System.currentTimeMillis() + 30 * DAY_MS) {
            if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS)
            generate()
        }
        return keyStore()
    }

    private fun generate() {
        val now = System.currentTimeMillis()
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
            .setCertificateSubject(X500Principal("CN=Pocket Theater"))
            .setCertificateSerialNumber(BigInteger.valueOf(now))
            .setCertificateNotBefore(Date(now - DAY_MS))
            .setCertificateNotAfter(Date(now + VALID_DAYS * DAY_MS))
            .build()
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        generator.initialize(spec)
        generator.generateKeyPair()
    }

    fun certificate(): X509Certificate = ready().getCertificate(ALIAS) as X509Certificate

    /** The SHA-256 fingerprint, in the form browsers show it (AB:CD:EF...). */
    fun fingerprint(): String =
        MessageDigest.getInstance("SHA-256").digest(certificate().encoded)
            .joinToString(":") { "%02X".format(it.toInt() and 0xFF) }

    /** The fingerprint if the certificate already exists. Never makes a new one, so it is quick and safe to call from a screen. */
    fun fingerprintIfExists(): String? {
        val cert = keyStore().getCertificate(ALIAS) as? X509Certificate ?: return null
        return MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            .joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
    }

    /** Throws the old certificate away and makes a new one. Browsers will warn again. */
    @Synchronized
    fun renew() {
        val store = keyStore()
        if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS)
        generate()
    }

    fun socketFactory(): SSLServerSocketFactory {
        val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        factory.init(ready(), null)
        val context = SSLContext.getInstance("TLS")
        context.init(factory.keyManagers, null, null)
        return context.serverSocketFactory
    }

    /**
     * Connects to the phone's own secure port and completes a handshake, trusting only this phone's certificate.
     * If this fails, HTTPS would not work for anyone, so the app falls back to plain HTTP and says so.
     */
    fun handshakeWorks(port: Int): Boolean {
        val ours = certificate()
        val worked = AtomicBoolean(false)
        val worker = Thread {
            try {
                val pinned = object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit

                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                        if (chain.isEmpty() || !chain[0].encoded.contentEquals(ours.encoded)) {
                            throw CertificateException("Not this phone's certificate")
                        }
                    }

                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                }
                val context = SSLContext.getInstance("TLS")
                context.init(null, arrayOf<TrustManager>(pinned), null)
                context.socketFactory.createSocket("127.0.0.1", port).use { socket ->
                    socket.soTimeout = 3000
                    (socket as SSLSocket).startHandshake()
                }
                worked.set(true)
            } catch (e: Exception) {
                // Left as false.
            }
        }
        worker.start()
        worker.join(6000)
        return worked.get()
    }
}
