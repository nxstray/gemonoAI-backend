package com.gemono.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Application Context Sanity Test")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
