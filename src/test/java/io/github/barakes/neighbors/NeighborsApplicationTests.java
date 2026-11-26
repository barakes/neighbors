package io.github.barakes.neighbors;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Starts the whole server against a temporary PostgreSQL: Flyway creates the tables,
 * and Hibernate checks that the code matches them.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class NeighborsApplicationTests {

    @Test
    void contextLoads() {
    }
}
