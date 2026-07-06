package com.gemono.backend.cucumber;

import io.cucumber.core.cli.Main;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("requires-groq") // 🔥 SAKTI: Membuat script .ps1 kamu meloloskan Runner ini!
public class CucumberRunnerTest {

    @Test
    void runCucumberTests() {
        String[] argv = new String[]{
            "src/test/resources/com/gemono/backend/cucumber", 
            "--glue", "com.gemono.backend.cucumber",          
            // Jika kamu ingin menjalankan SEMUA file .feature yang ada di folder tersebut:
            "--plugin", "io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm",
            "--plugin", "pretty",
            "--plugin", "html:target/cucumber-reports/report.html"
        };
        
        byte exitCode = Main.run(argv, Thread.currentThread().getContextClassLoader());
        assertEquals(0, exitCode, "Ada skenario Cucumber yang gagal!");
    }
}