package com.oncue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "oncue.auth.signing-secret=test-signing-secret-for-context-only")
class OncueApplicationTest {

    @Test
    void applicationContextLoads() {
    }
}
