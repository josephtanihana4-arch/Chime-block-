package com.example.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Cryptographic PBKDF2WithHmacSHA256 PIN hashing and constant-time verification.
 */
object PinHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 65_536
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    data class HashedPin(
        val hashBase64: String,
        val saltBase64: String
    )

    fun hashPin(pin: String): HashedPin {
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        val derived = deriveKey(pin.toCharArray(), salt)
        return HashedPin(
            hashBase64 = Base64.encodeToString(derived, Base64.NO_WRAP),
            saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        )
    }

    fun verifyPin(pin: String, expectedHashBase64: String, saltBase64: String): Boolean {
        if (expectedHashBase64.isBlank() || saltBase64.isBlank()) return false
        return try {
            val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
            val expectedBytes = Base64.decode(expectedHashBase64, Base64.NO_WRAP)
            val candidateBytes = deriveKey(pin.toCharArray(), salt)
            MessageDigest.isEqual(candidateBytes, expectedBytes)
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun deriveKey(chars: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(chars, salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
