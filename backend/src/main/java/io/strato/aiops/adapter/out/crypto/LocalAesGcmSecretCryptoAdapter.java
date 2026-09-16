package io.strato.aiops.adapter.out.crypto;

import io.strato.aiops.application.port.out.SecretCryptoPort;
import io.strato.aiops.domain.cluster.EncryptedSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class LocalAesGcmSecretCryptoAdapter implements SecretCryptoPort {

    private static final String ALGORITHM = "AES-256-GCM";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int NONCE_LENGTH_BYTES = 12;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec secretKeySpec;

    /** LocalAesGcmSecretCryptoAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public LocalAesGcmSecretCryptoAdapter(@Value("${aiops.crypto.local-master-key:local-development-master-key}") String localMasterKey) {
        this.secretKeySpec = new SecretKeySpec(sha256(localMasterKey), "AES");
    }

    /** LocalAesGcmSecretCryptoAdapter의 encrypt 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public EncryptedSecret encrypt(String plaintext) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH_BYTES];
            secureRandom.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            return new EncryptedSecret(
                    Base64.getEncoder().encodeToString(ciphertext),
                    "local",
                    ALGORITHM,
                    Base64.getEncoder().encodeToString(nonce)
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to encrypt secret", exception);
        }
    }

    /** LocalAesGcmSecretCryptoAdapter의 decrypt 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public String decrypt(EncryptedSecret encryptedSecret) {
        try {
            if (!ALGORITHM.equals(encryptedSecret.algorithm())) {
                throw new IllegalArgumentException("Unsupported secret encryption algorithm");
            }

            byte[] nonce = Base64.getDecoder().decode(encryptedSecret.nonce());
            byte[] ciphertext = Base64.getDecoder().decode(encryptedSecret.ciphertext());

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to decrypt secret", exception);
        }
    }

    /** LocalAesGcmSecretCryptoAdapter의 sha256 처리에 필요한 업무 로직을 수행한다. */
    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to derive local encryption key", exception);
        }
    }
}
