package com.sdk.growthbook.sandbox

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.io.FileInputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Actual Implementation for Caching in Android - As expected in KMM
 */
internal actual object CachingImpl {
    actual fun getLayer(localEncryptionKey: String?): CachingLayer {
        return CachingAndroid(localEncryptionKey)
    }
}

/**
 * Android Caching Layer
 */
class CachingAndroid(localEncryptionKey: String? = null): CachingLayer {

    /**
     * JSON Parser SetUp
     */
    private val json = Json { prettyPrint = true; isLenient = true; ignoreUnknownKeys = true }

    /**
     * For Secret Key Generation
     */
    val localSecretKey = localEncryptionKey?.let {
        getFixedSecretKey(it)
    }

    private val iv = generateIv()

    /**
     * Save Content in Android App Specific Internal Memory
     */
    override fun saveContent(fileName: String, content: JsonElement) {
        val file = getTargetFile(fileName)

        if (file != null) {
            // If File already exists - delete that
            if (file.exists()) {
                file.delete()
            }

            // Create New File
            file.createNewFile()

            val jsonContents = json.encodeToString(JsonElement.serializer(), content)

            if (localSecretKey == null) {
                // Save contents in file
                file.writeText(jsonContents)
            } else {
                // Save contents as encrypt string
                val encryptedContent = encrypt(jsonContents, localSecretKey, iv)
                file.writeBytes(encryptedContent)
            }

        }
    }

    /**
     * Retrieve Content from Android App Specific Internal Memory
     */
    override fun getContent(fileName: String): JsonElement? {

        val file = getTargetFile(fileName)

        if (file != null && file.exists()) {
            // Read File Contents
            if (localSecretKey == null) {
                // Read File Contents
                val inputAsString = FileInputStream(file).bufferedReader().use { it.readText() }
                // return File Contents
                return json.decodeFromString(JsonElement.serializer(), inputAsString)
            } else {
                // Read File Contents
                val encryptedData = FileInputStream(file).use { it.readBytes() }
                val decryptedContent = decrypt(encryptedData, localSecretKey, iv)

                // return File Contents after decrypt
                return json.decodeFromString(JsonElement.serializer(), decryptedContent)
            }

        }

        // Return null if file doesn't exist
        return null
    }

    /**
     * Get Target File - with complete path in internal memory
     */
    fun getTargetFile(fileName: String): File? {
        if (filesDir == null) {
            return null
        }

        // Create Directory in Internal Memory
        val letDirectory = File(filesDir, "GrowthBook-KMM")
        letDirectory.mkdirs()
        var targetFileName = fileName
        if (fileName.endsWith(".txt", true)) {
            targetFileName = fileName.removeSuffix(".txt")
        }
        return File(letDirectory, "$targetFileName.txt")
    }

    /**
     * To encrypt the cache file content
     */
    private fun encrypt(data: String, secretKey: SecretKey, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
        return cipher.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    /**
     * To decrypt the cache file content
     */
    private fun decrypt(data: ByteArray, secretKey: SecretKey, iv: ByteArray): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }

    companion object {
        internal var filesDir: File? = null

        /**
         * Retrieves filesDir from context
         */
        fun consumeContext(context: Context) {
            filesDir = context.filesDir
        }

        fun getFixedSecretKey(localSecretKey: String): SecretKey {
            val keyBytes = localSecretKey.toByteArray(Charsets.UTF_8)
            val keyBytesPadded = keyBytes.copyOf(32) // Ensure 256-bit (32 bytes)
            return SecretKeySpec(keyBytesPadded, "AES")
        }

        fun generateSecretKey(): SecretKey {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256)
            return keyGen.generateKey()
        }

        fun generateIv(): ByteArray {
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)
            return iv
        }
    }
}