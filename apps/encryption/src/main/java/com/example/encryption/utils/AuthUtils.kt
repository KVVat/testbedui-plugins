package com.example.encryption.utils

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.IOException
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.Cipher
import javax.security.cert.CertificateException

class AuthUtils {
    companion object {
        val KEY_NAME = "KEY_NAME"

        fun defaultKeyGenParametersBuilder(unlockDeviceRequired:Boolean,authRequired:Boolean,type:Int,timeout:Int=10): KeyGenParameterSpec.Builder {
            return KeyGenParameterSpec.Builder(KEY_NAME,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setUserAuthenticationParameters(timeout,type)
                .setUnlockedDeviceRequired(unlockDeviceRequired)
                .setUserAuthenticationRequired(authRequired)
        }
        fun generateSecretKey(keyGenParameterSpec: KeyGenParameterSpec) {
            try {
                //val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore")
                //keyStore.load(null)
                val keyGenerator: KeyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
                )
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            } catch (e: NoSuchAlgorithmException) {
                throw RuntimeException("Failed to create a symmetric key", e)
            } catch (e: NoSuchProviderException) {
                throw RuntimeException("Failed to create a symmetric key", e)
            } catch (e: InvalidAlgorithmParameterException) {
                throw RuntimeException("Failed to create a symmetric key", e)
            } catch (e: KeyStoreException) {
                throw RuntimeException("Failed to create a symmetric key", e)
            } catch (e: CertificateException) {
                throw RuntimeException("Failed to create a symmetric key", e)
            } catch (e: IOException) {
                throw RuntimeException("Failed to create a symmetric key", e)
            }
        }

        fun getSecretKey(): SecretKey? {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            // Before the keystore can be accessed, it must be loaded.
            keyStore.load(null)
            return keyStore.getKey(KEY_NAME, null) as SecretKey?
        }

        fun getCipher(): Cipher {
            //.setBlockModes(KeyProperties.BLOCK_MODE_GCM).setKeySize(256).setEncryptionPaddings(
            //    KeyProperties.ENCRYPTION_PADDING_NONE)

            return Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                    + KeyProperties.BLOCK_MODE_CBC + "/"
                    + KeyProperties.ENCRYPTION_PADDING_PKCS7)
        }

    }
}