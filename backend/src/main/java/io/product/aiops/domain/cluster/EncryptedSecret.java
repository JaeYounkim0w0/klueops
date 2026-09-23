package io.product.aiops.domain.cluster;

public record EncryptedSecret(
        String ciphertext,
        String keyId,
        String algorithm,
        String nonce
) {
}

