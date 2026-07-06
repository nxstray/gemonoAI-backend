package com.gemono.backend.cucumber;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@CucumberContextConfiguration
// 1. Ubah WebEnvironment menjadi MOCK agar Spring Security & Filter JWT aktif di memory
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK) 
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.security.oauth2.client.registration.google.client-id=mock",
    "spring.security.oauth2.client.registration.google.client-secret=mock",
    "spring.security.oauth2.client.registration.google.scope=email,profile",
    "spring.security.oauth2.client.registration.google.redirect-uri=http://localhost/login/oauth2/code/google",
    // 2. Kembalikan tipe aplikasi ke servlet agar filter keamanan web mau memproses token JWT
    "spring.main.web-application-type=servlet", 
    "cors.allowed-origins=http://localhost:3000"
})
public class CucumberSpringConfig {

    // 3. Kita jinakkan pencarian OAuth2 Client Registration dengan menyuntikkan Mock Bean
    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;
}