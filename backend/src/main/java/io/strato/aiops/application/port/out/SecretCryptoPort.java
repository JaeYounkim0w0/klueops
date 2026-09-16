package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.cluster.EncryptedSecret;

public interface SecretCryptoPort {

    /** SecretCryptoPort의 encrypt 처리 계약을 정의한다. */
    EncryptedSecret encrypt(String plaintext);

    /** SecretCryptoPort의 decrypt 처리 계약을 정의한다. */
    String decrypt(EncryptedSecret encryptedSecret);
}
