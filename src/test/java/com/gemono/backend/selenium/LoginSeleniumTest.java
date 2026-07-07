package com.gemono.backend.selenium;

import io.qameta.allure.*;
import org.junit.jupiter.api.*;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;

@Epic("Authentication")
@Feature("Login Page")
@DisplayName("E2E Test — Login Page")
class LoginSeleniumTest extends BaseSeleniumTest {

    @BeforeEach
    void navigateToLogin() {
        // Ensure Selenium navigates to the login page and waits for the body element to load completely
        driver.get(FRONTEND_URL + "/login");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
    }

    @Test
    @Story("Render")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-SEL-01 | Login page renders correctly")
    void TC_SEL_01_loginPageRendersCorrectly() {
        // Verify the brand name 'GEMONO' is visible on the login interface
        WebElement brand = wait.until(
            ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".brand-name"))
        );
        screenshot("TC-SEL-01", "login_page_loaded");
        Assertions.assertTrue(brand.isDisplayed());
        Assertions.assertEquals("GEMONO", brand.getText());
    }

    @Test
    @Story("Google Login")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-SEL-02 | Google login button present and enabled")
    void TC_SEL_02_googleButtonPresentAndEnabled() {
        // Verify the Google OAuth button is present, visible, and interactive
        WebElement googleBtn = wait.until(
            ExpectedConditions.elementToBeClickable(By.cssSelector("[data-testid='google-login-btn']"))
        );
        screenshot("TC-SEL-02", "google_btn_ready");
        Assertions.assertTrue(googleBtn.isDisplayed());
        Assertions.assertTrue(googleBtn.isEnabled());
    }

    @Test
    @Story("Magic Link Input")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-SEL-03 | Email input accepts text")
    void TC_SEL_03_emailInputAcceptsText() {
        // Verify that the email input field successfully receives typed keyboard inputs
        WebElement textarea = wait.until(
            ExpectedConditions.presenceOfElementLocated(By.cssSelector("[data-testid='email-input']"))
        );
        textarea.clear();
        textarea.sendKeys("user@example.com");
        screenshot("TC-SEL-03", "email_entered");
        Assertions.assertEquals("user@example.com", textarea.getAttribute("value"));
    }

    @Test
    @Story("Magic Link Input")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC-SEL-04 | Send link button disabled when email is empty")
    void TC_SEL_04_sendLinkButtonDisabledWhenEmpty() {
        WebElement textarea = wait.until(
            ExpectedConditions.presenceOfElementLocated(By.cssSelector("[data-testid='email-input']"))
        );
        // Force clear the input using keyboard shortcuts to guarantee it triggers reactive Vue binding
        textarea.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.BACK_SPACE);
        
        WebElement sendBtn = wait.until(
            ExpectedConditions.presenceOfElementLocated(By.cssSelector("[data-testid='send-link-btn']"))
        );
        screenshot("TC-SEL-04", "send_button_disabled");
        Assertions.assertFalse(sendBtn.isEnabled(), "Button should be disabled when email is empty");
    }

    @Test
    @Story("Magic Link Input")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC-SEL-05 | Send link button enabled when email has text")
    void TC_SEL_05_sendLinkButtonEnabledWithEmail() {
        WebElement textarea = wait.until(
            ExpectedConditions.presenceOfElementLocated(By.cssSelector("[data-testid='email-input']"))
        );
        textarea.sendKeys("test@gemono.com");

        WebElement sendBtn = wait.until(
            ExpectedConditions.presenceOfElementLocated(By.cssSelector("[data-testid='send-link-btn']"))
        );
        screenshot("TC-SEL-05", "send_button_enabled");
        Assertions.assertTrue(sendBtn.isEnabled(), "Button should be enabled when text is present");
    }

    @Test
    @Story("Magic Link Flow")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-SEL-06 | Sent confirmation shows after valid submission")
    void TC_SEL_06_sentConfirmationShows() {
        WebElement textarea = wait.until(
            ExpectedConditions.presenceOfElementLocated(By.cssSelector("[data-testid='email-input']"))
        );
        textarea.sendKeys("success@gemono.com");

        WebElement sendBtn = wait.until(
            ExpectedConditions.elementToBeClickable(By.cssSelector("[data-testid='send-link-btn']"))
        );
        sendBtn.click();

        // Wait for State B (v-else conditional block) to safely animate and manifest in the DOM
        WebElement sentTitle = wait.until(
            ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".sent-title"))
        );
        screenshot("TC-SEL-06", "confirmation_page_shown");
        
        Assertions.assertTrue(sentTitle.isDisplayed());
        Assertions.assertEquals("Check your inbox", sentTitle.getText());
    }
}