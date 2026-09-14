package io.strato.aiops.domain.cluster;

public record EncryptedSecret(
        String ciphertext,
        String keyId,
        String algorithm,
        String nonce
) {
}

