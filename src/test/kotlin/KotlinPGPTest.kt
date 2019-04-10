package moe.tlaster.kotlinpgp.test

import com.google.common.collect.Iterators
import io.kotlintest.inspectors.forAll
import io.kotlintest.matchers.string.shouldNotBeEmpty
import io.kotlintest.matchers.types.shouldBeNull
import io.kotlintest.matchers.types.shouldNotBeNull
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrowExactly
import io.kotlintest.specs.FreeSpec
import moe.tlaster.kotlinpgp.*
import moe.tlaster.kotlinpgp.data.EncryptParameter
import moe.tlaster.kotlinpgp.data.GenerateKeyPairParameter
import moe.tlaster.kotlinpgp.data.PGPKeyPairData
import moe.tlaster.kotlinpgp.data.VerifyStatus
import moe.tlaster.kotlinpgp.utils.OpenPGPUtils
import moe.tlaster.kotlinpgp.utils.UserIdUtils
import org.bouncycastle.openpgp.PGPException


class KotlinPGPTest : FreeSpec({
    val keyName = "test"
    val keyEmail = "test@test.com"
    val keyPassword = "password"
    "generate key pair" - {
        "normal generation" {
            val keypair = KotlinPGP.generateKeyPair(
                GenerateKeyPairParameter(
                    name = keyName,
                    email = keyEmail
                )
            )
            testKeyPair(keypair)
        }
        "empty name" {
            val keypair = KotlinPGP.generateKeyPair(
                GenerateKeyPairParameter(
                    name = "",
                    email = keyEmail
                )
            )
            testKeyPair(keypair)
        }
        "empty email" {
            val keypair = KotlinPGP.generateKeyPair(
                GenerateKeyPairParameter(
                    name = keyName,
                    email = ""
                )
            )
            testKeyPair(keypair)
        }
        "with password" {
            val keypair = KotlinPGP.generateKeyPair(
                GenerateKeyPairParameter(
                    name = keyName,
                    email = keyEmail,
                    password = keyPassword
                )
            )
            testKeyPair(keypair)
        }
    }
    "with same key pair" - {
        val keypair = KotlinPGP.generateKeyPair(
            GenerateKeyPairParameter(
                name = keyName,
                email = keyEmail,
                password = keyPassword
            )
        )
        val keypair2 = KotlinPGP.generateKeyPair(
            GenerateKeyPairParameter(
                name = keyName,
                email = keyEmail,
                password = keyPassword
            )
        )
        val keypair3 = KotlinPGP.generateKeyPair(
            GenerateKeyPairParameter(
                name = keyName,
                email = keyEmail,
                password = keyPassword
            )
        )
        "restore key" - {
            "restore public key" {
                val publicKeyRing = KotlinPGP.getPublicKeyRingFromString(keypair.publicKey)
                publicKeyRing.shouldNotBeNull()
                Iterators.size(publicKeyRing.publicKeys) shouldBe 2
                Iterators.size(publicKeyRing.publicKey.userIDs) shouldBe 1
                val id = Iterators.get(publicKeyRing.publicKey.userIDs, 0)
                val userId = UserIdUtils.splitUserId(id)
                userId.name shouldBe keyName
                userId.email shouldBe keyEmail
                val publicKeyRingString = publicKeyRing.exportToString()
                publicKeyRingString shouldBe keypair.publicKey
            }
            "restore private key" {
                val privateKeyRing = KotlinPGP.getSecretKeyRingFromString(keypair.secretKey, keyPassword)
                privateKeyRing.shouldNotBeNull()
                Iterators.size(privateKeyRing.secretKeys) shouldBe 2
                val privateKeyRingString = privateKeyRing.exportToString()
                privateKeyRingString shouldBe keypair.secretKey
            }
            "restore private key with wrong password" {
                shouldThrowExactly<PGPException> {
                    KotlinPGP.getSecretKeyRingFromString(keypair.secretKey, "Wrong password!")
                }
            }
        }
        "encrypt" - {
            val contentToEncrypt = "Hello world!"
            val publicKeys = listOf(keypair.publicKey, keypair2.publicKey, keypair3.publicKey)
            val publicKeyRings = publicKeys.map {
                KotlinPGP.getPublicKeyRingFromString(it)
            }
            "encrypt data without signing" {
                val encryptResult = KotlinPGP.encrypt(EncryptParameter(
                    message = contentToEncrypt,
                    publicKey = publicKeys
                ))
                encryptResult should {
                    it.isPGPMessage
                }
            }
            "encrypt data with signing" {
                val encryptResult = KotlinPGP.encrypt(EncryptParameter(
                    message = contentToEncrypt,
                    publicKey = publicKeys,
                    enableSignature = true,
                    privateKey = keypair.secretKey,
                    password = keyPassword
                ))
                encryptResult should {
                    it.isPGPMessage
                }
            }
            "encrypt data without public key" {
                val encryptResult = KotlinPGP.encrypt(EncryptParameter(
                    message = contentToEncrypt,
                    publicKey = emptyList(),
                    enableSignature = true,
                    privateKey = keypair.secretKey,
                    password = keyPassword
                ))
                encryptResult should {
                    it.isPGPMessage
                }
            }
            "decrypt" - {
                val encryptedData = KotlinPGP.encrypt(EncryptParameter(
                    message = contentToEncrypt,
                    publicKey = publicKeys
                ))
                val signedEncryptedData = KotlinPGP.encrypt(EncryptParameter(
                    message = contentToEncrypt,
                    publicKey = publicKeys,
                    enableSignature = true,
                    privateKey = keypair.secretKey,
                    password = keyPassword
                ))
                val clearSignedData = KotlinPGP.encrypt(EncryptParameter(
                    message = contentToEncrypt,
                    publicKey = emptyList(),
                    enableSignature = true,
                    privateKey = keypair.secretKey,
                    password = keyPassword
                ))
                "decrypt unsigned data" {
                    val decryptResult = KotlinPGP.decrypt(keypair.secretKey, keyPassword, encryptedData)
                    decryptResult.shouldNotBeNull()
                    decryptResult.hasSignature shouldBe false
                    decryptResult.result shouldBe contentToEncrypt
                    decryptResult.includedKeys.size shouldBe 3
                    decryptResult.includedKeys.forAll { id ->
                        publicKeyRings.any { keyRing ->
                            OpenPGPUtils.getSubKeyPublicKey(keyRing)?.keyID == id
                        }
                    }
                    val signatureData = decryptResult.signatureData
                    signatureData.onePassSignatureList.shouldBeNull()
                    signatureData.signatureList.shouldBeNull()
                }
                "decrypt signed data" {
                    val decryptResult = KotlinPGP.decrypt(keypair.secretKey, keyPassword, signedEncryptedData)
                    decryptResult.shouldNotBeNull()
                    decryptResult.hasSignature shouldBe true
                    decryptResult.result shouldBe contentToEncrypt
                    decryptResult.includedKeys.size shouldBe 3
                    decryptResult.includedKeys.forAll { id ->
                        publicKeyRings.any { keyRing ->
                            OpenPGPUtils.getSubKeyPublicKey(keyRing)?.keyID == id
                        }
                    }
                    val signatureData = decryptResult.signatureData
                    signatureData.onePassSignatureList.shouldNotBeNull()
                    signatureData.signatureList.shouldNotBeNull()
                    signatureData.signatureList?.size() shouldBe 1
                    signatureData.onePassSignatureList?.size() shouldBe 1
                }
                "decrypt clear signed data" {
                    val decryptResult = KotlinPGP.decrypt(keypair.secretKey, keyPassword, clearSignedData)
                    decryptResult.shouldNotBeNull()
                    decryptResult.hasSignature shouldBe true
                    decryptResult.result shouldBe contentToEncrypt
                    decryptResult.includedKeys.size shouldBe 0
                    decryptResult.includedKeys.forAll { id ->
                        publicKeyRings.any { keyRing ->
                            OpenPGPUtils.getSubKeyPublicKey(keyRing)?.keyID == id
                        }
                    }
                    val signatureData = decryptResult.signatureData
                    signatureData.onePassSignatureList.shouldBeNull()
                    signatureData.signatureList.shouldNotBeNull()
                    signatureData.signatureList?.size() shouldBe 1
                }
                val decryptResult = KotlinPGP.decrypt(keypair.secretKey, keyPassword, encryptedData)
                val signedDecryptResult = KotlinPGP.decrypt(keypair.secretKey, keyPassword, signedEncryptedData)
                val clearSignedDecryptResult = KotlinPGP.decrypt(keypair.secretKey, keyPassword, clearSignedData)
                "verify" - {
                    "verify not signed data" {
                        val signatureData = decryptResult.signatureData
                        val verifyResult = KotlinPGP.verify(signatureData, publicKeys)
                        verifyResult.verifyStatus shouldBe VerifyStatus.NO_SIGNATURE
                    }
                    "verify signed data" {
                        val signatureData = signedDecryptResult.signatureData
                        val verifyResult = KotlinPGP.verify(signatureData, publicKeys)
                        verifyResult.shouldNotBeNull()
                        verifyResult.verifyStatus shouldBe VerifyStatus.SIGNATURE_OK
                        verifyResult.keyID shouldBe publicKeyRings[0].publicKey.keyID
                    }
                    "verify clear signed data" {
                        val signatureData = clearSignedDecryptResult.signatureData
                        val verifyResult = KotlinPGP.verify(signatureData, publicKeys)
                        verifyResult.shouldNotBeNull()
                        verifyResult.verifyStatus shouldBe VerifyStatus.SIGNATURE_OK
                        verifyResult.keyID shouldBe publicKeyRings[0].publicKey.keyID
                    }
                    val newKeyPair = KotlinPGP.generateKeyPair(
                        GenerateKeyPairParameter(
                            name = keyName,
                            email = keyEmail,
                            password = keyPassword
                        )
                    )
                    "verify unknown public key signed data" {
                        val signatureData = signedDecryptResult.signatureData
                        val verifyResult = KotlinPGP.verify(signatureData, listOf(newKeyPair.publicKey))
                        verifyResult.shouldNotBeNull()
                        verifyResult.verifyStatus shouldBe VerifyStatus.UNKNOWN_PUBLIC_KEY
                    }
                    "verify unknown public key clear signed data" {
                        val signatureData = clearSignedDecryptResult.signatureData
                        val verifyResult = KotlinPGP.verify(signatureData, listOf(newKeyPair.publicKey))
                        verifyResult.shouldNotBeNull()
                        verifyResult.verifyStatus shouldBe VerifyStatus.UNKNOWN_PUBLIC_KEY
                    }
                }
            }
        }
    }
})

private fun testKeyPair(keypair: PGPKeyPairData) {
    keypair.shouldNotBeNull()
    keypair.publicKey.shouldNotBeEmpty()
    keypair.secretKey.shouldNotBeEmpty()
    keypair.publicKey should {
        it.isPGPPublicKey
    }
    keypair.secretKey should {
        it.isPGPPrivateKey
    }
}