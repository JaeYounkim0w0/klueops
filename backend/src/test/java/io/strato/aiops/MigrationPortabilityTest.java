package io.strato.aiops;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationPortabilityTest {

    /** MigrationPortabilityTest의 migrationsAvoidNonPortableLargeObjectTypes 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void migrationsAvoidNonPortableLargeObjectTypes() throws IOException {
        Path migrations = Path.of("src/main/resources/db/migration");

        try (var files = Files.list(migrations)) {
            var incompatible = files
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .filter(path -> containsH2OnlyType(path, "clob") || containsH2OnlyType(path, "blob"))
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();

            assertThat(incompatible)
                    .as("Flyway migrations must remain portable across supported PostgreSQL versions")
                    .isEmpty();
        }
    }

    /** MigrationPortabilityTest의 containsH2OnlyType 처리에 필요한 업무 로직을 수행한다. */
    private boolean containsH2OnlyType(Path path, String type) {
        try {
            String sql = Files.readString(path).toLowerCase(Locale.ROOT);
            return sql.matches("(?s).*\\b" + type + "\\b.*");
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to inspect migration " + path, exception);
        }
    }
}
