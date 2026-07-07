package com.gemono.backend.selenium;

import io.qameta.allure.*;
import org.junit.jupiter.api.*;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;

@Epic("Chat")
@Feature("Chat Page")
@DisplayName("E2E Test — Chat Page")
class ChatSeleniumTest extends BaseSeleniumTest {

    @Test
    @Story("Guest Access")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-SEL-06 | Chat page accessible without login (guest mode)")
    void TC_SEL_06_chatPageAccessibleAsGuest() {
        driver.get(FRONTEND_URL + "/chat");

        screenshot("TC-SEL-06", "page_loaded");

        // Should NOT redirect to login
        Assertions.assertFalse(
            driver.getCurrentUrl().contains("/login"),
            "Guest should be able to access /chat without login"
        );

        // Empty state should show
        WebElement greeting = wait.until(
            ExpectedConditions.presenceOfElementLocated(By.cssSelector(".welcome-greeting"))
        );

        screenshot("TC-SEL-06", "empty_state_shown");

        Assertions.assertTrue(greeting.isDisplayed());
    }

    @Test
    @Story("Input")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-SEL-08 | Chat textarea accepts text input")
    void TC_SEL_08_chatTextareaAcceptsInput() {
        driver.get(FRONTEND_URL + "/chat");

        WebElement textarea = wait.until(
            ExpectedConditions.presenceOfElementLocated(By.cssSelector(".chat-textarea"))
        );

        textarea.sendKeys("Hello Gemono!");

        screenshot("TC-SEL-08", "text_entered");

        Assertions.assertEquals("Hello Gemono!", textarea.getAttribute("value"));
    }

    @Test
    @Story("Input")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC-SEL-09 | Send button disabled when textarea is empty")
    void TC_SEL_09_sendButtonDisabledWhenEmpty() {
        driver.get(FRONTEND_URL + "/chat");

        WebElement sendBtn = wait.until(
            ExpectedConditions.presenceOfElementLocated(By.cssSelector(".send-btn"))
        );

        screenshot("TC-SEL-09", "send_button_disabled");

        Assertions.assertFalse(sendBtn.isEnabled(), "Send button should be disabled when textarea is empty");
    }

    @Test
    @Story("Input")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC-SEL-10 | Send button enabled when textarea has text")
    void TC_SEL_10_sendButtonEnabledWithText() {
        driver.get(FRONTEND_URL + "/chat");

        WebElement textarea = wait.until(
            ExpectedConditions.presenceOfElementLocated(By.cssSelector(".chat-textarea"))
        );
        textarea.sendKeys("Test message");

        WebElement sendBtn = driver.findElement(By.cssSelector(".send-btn"));

        screenshot("TC-SEL-10", "send_button_enabled");

        Assertions.assertTrue(sendBtn.isEnabled(), "Send button should be enabled when text is present");
    }

    @Test
    @Story("Sidebar")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC-SEL-11 | Sidebar renders brand name and new chat button")
    void TC_SEL_11_sidebarRendersCorrectly() {
        driver.get(FRONTEND_URL + "/chat");

        WebElement brand = wait.until(
            ExpectedConditions.presenceOfElementLocated(By.cssSelector(".brand-name"))
        );

        screenshot("TC-SEL-11", "sidebar_visible");

        Assertions.assertTrue(brand.isDisplayed());
        Assertions.assertEquals("GEMONO", brand.getText());
    }

    @Test
    @Story("Sidebar")
    @Severity(SeverityLevel.MINOR)
    @DisplayName("TC-SEL-12 | Mobile sidebar toggles on menu button click")
    void TC_SEL_12_mobileSidebarToggles() {
        // Set mobile viewport
        ((JavascriptExecutor) driver).executeScript("window.resizeTo(375, 812)");
        driver.manage().window().setSize(new Dimension(375, 812));

        driver.get(FRONTEND_URL + "/chat");

        WebElement menuBtn = wait.until(
            ExpectedConditions.elementToBeClickable(By.cssSelector(".menu-toggle-btn"))
        );

        screenshot("TC-SEL-12", "before_menu_open");

        menuBtn.click();

        WebElement sidebar = driver.findElement(By.cssSelector(".sidebar"));

        screenshot("TC-SEL-12", "sidebar_opened");

        Assertions.assertTrue(
            sidebar.getAttribute("class").contains("sidebar-open"),
            "Sidebar should have sidebar-open class after menu click"
        );
    }
}