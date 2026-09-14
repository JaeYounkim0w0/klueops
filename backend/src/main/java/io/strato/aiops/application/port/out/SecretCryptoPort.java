package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.cluster.EncryptedSecret;

public interface SecretCryptoPort {

    EncryptedSecret encrypt(String plaintext);

    String decrypt(EncryptedSecret encryptedSecret);
}
