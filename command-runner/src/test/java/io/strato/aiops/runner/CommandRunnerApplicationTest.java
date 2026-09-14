package io.strato.aiops.runner;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "runner.token=0123456789abcdef0123456789abcdef"
)
class CommandRunnerApplicationTest {
    @Test
    void applicationContextStarts() {
    }
}
