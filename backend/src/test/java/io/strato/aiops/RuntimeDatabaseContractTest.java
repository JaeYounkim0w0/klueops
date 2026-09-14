package io.strato.aiops;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeDatabaseContractTest {

    @Test
    void defaultRuntimeUsesPostgresqlAndH2IsNotARuntimeDependency() throws Exception {
        String application = Files.readString(Path.of("src/main/resources/application.yml"));
        String pom = Files.readString(Path.of("pom.xml"));

        assertThat(application)
                .contains("jdbc:postgresql://127.0.0.1:5432/aiops")
                .doesNotContain("jdbc:h2:");
        assertThat(pom)
                .doesNotContain("<groupId>com.h2database</groupId>");
    }
}
