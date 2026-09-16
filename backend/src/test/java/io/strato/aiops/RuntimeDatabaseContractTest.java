package io.strato.aiops;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeDatabaseContractTest {

    /** RuntimeDatabaseContractTest의 defaultRuntimeUsesPostgresqlAndH2IsNotARuntimeDependency 처리에 필요한 업무 로직을 수행한다. */
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
