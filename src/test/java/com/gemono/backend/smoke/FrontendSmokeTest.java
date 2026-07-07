package com.gemono.backend.smoke;

import com.gemono.backend.selenium.BaseSeleniumTest;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * Frontend Smoke Tests — verify Vue app loads and core pages render.
 * Run: mvn test -Dgroups=smoke
 */
@Epic("Smoke")
@Feature("Frontend Sanity Check")
@Tag("smoke")
@DisplayName("E2E Smoke Test — Frontend Routes")
class FrontendSmokeTest extends BaseSeleniumTest {

    // TC-SMK-08
    @Test
    @Story("Page Load")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("TC-SMK-08 | Frontend app loads without blank screen")
    void TC_SMK_08_frontendLoadsWithoutBlankScreen() {
        driver.get(FRONTEND_URL);

        screenshot("TC-SMK-08", "initial_load");

        // Vue mounts #app — if blank, this element will be empty
        String bodyText = driver.findElement(By.tagName("body")).getText();
        Assertions.assertFalse(bodyText.isBlank(),
                "Frontend should not be a blank page — Vue app must have mounted");
    }

    // TC-SMK-09
    @Test
    @Story("Page Load")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("TC-SMK-09 | Login page loads with GEMONO brand")
    void TC_SMK_09_loginPageLoads() {
        driver.get(FRONTEND_URL + "/login");

        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".brand-name")));

        screenshot("TC-SMK-09", "login_page");

        String brand = driver.findElement(By.cssSelector(".brand-name")).getText();
        Assertions.assertEquals("GEMONO", brand,
                "Login page should show GEMONO brand name");
    }

    // TC-SMK-10
    @Test
    @Story("Page Load")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("TC-SMK-10 | Chat page loads for guest without redirect to login")
    void TC_SMK_10_chatPageLoadsAsGuest() {
        driver.get(FRONTEND_URL + "/chat");

        screenshot("TC-SMK-10", "chat_page_guest");

        Assertions.assertFalse(
                driver.getCurrentUrl().contains("/login"),
                "Guest should access /chat without being redirected to login"
        );
    }

    // TC-SMK-11
    @Test
    @Story("Page Load")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC-SMK-11 | Unknown route does not show blank screen (Vue router fallback)")
    void TC_SMK_11_unknownRouteHandled() {
        driver.get(FRONTEND_URL + "/this-route-does-not-exist");

        screenshot("TC-SMK-11", "unknown_route");

        // Should either redirect or show something — not blank
        String body = driver.findElement(By.tagName("body")).getText();
        Assertions.assertFalse(body.isBlank(),
                "Unknown route should not show blank screen");
    }
}