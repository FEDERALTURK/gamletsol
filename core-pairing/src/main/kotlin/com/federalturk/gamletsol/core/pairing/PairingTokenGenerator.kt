package com.federalturk.gamletsol.core.pairing

import java.security.SecureRandom

/**
 * [PairingToken.value] için rastgele, insan tarafından yazılabilir kısa kodlar
 * üretir — `0/O`, `1/I/L` gibi karıştırılabilir karakterler kasıtlı olarak
 * alfabenin dışında tutuldu (Crockford Base32'ye benzer bir seçim).
 */
object PairingTokenGenerator {
    private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    private const val CODE_LENGTH = 8
    private val secureRandom = SecureRandom()

    fun generate(nowMillis: Long, ttlMillis: Long = PairingToken.DEFAULT_TTL_MILLIS): PairingToken {
        val code = buildString {
            repeat(CODE_LENGTH) {
                if (it == 4) append('-')
                append(ALPHABET[secureRandom.nextInt(ALPHABET.length)])
            }
        }
        return PairingToken(value = code, issuedAtMillis = nowMillis, ttlMillis = ttlMillis)
    }
}
