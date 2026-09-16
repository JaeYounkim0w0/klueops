package io.strato.aiops;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresProfileConfigurationTest {

    /** PostgresProfileConfigurationTest의 postgresProfileUsesFlywaySchemaValidationAndBoundedPool 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void postgresProfileUsesFlywaySchemaValidationAndBoundedPool() throws IOException {
        ClassPathResource resource = new ClassPathResource("application-postgres.yml");

        assertThat(resource.exists()).isTrue();
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("postgres", resource);

        assertThat(value(sources, "spring.datasource.driver-class-name"))
                .isEqualTo("org.postgresql.Driver");
        assertThat(value(sources, "spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(value(sources, "spring.flyway.enabled")).isEqualTo(true);
        assertThat(value(sources, "spring.datasource.hikari.maximum-pool-size"))
                .isEqualTo("${AIOPS_DB_POOL_MAX_SIZE:10}");
        assertThat(value(sources, "spring.datasource.hikari.connection-timeout"))
                .isEqualTo("${AIOPS_DB_CONNECTION_TIMEOUT_MS:10000}");
        assertThat(value(sources, "spring.datasource.hikari.validation-timeout"))
                .isEqualTo("${AIOPS_DB_VALIDATION_TIMEOUT_MS:5000}");
    }

    /** PostgresProfileConfigurationTest의 value 처리에 필요한 업무 로직을 수행한다. */
    private Object value(List<PropertySource<?>> sources, String name) {
        return sources.stream()
                .map(source -> source.getProperty(name))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
