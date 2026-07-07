package com.gemono.backend.selenium;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

// Base class for all Selenium tests — handles driver setup/teardown and screenshot on failure
public abstract class BaseSeleniumTest {

    protected WebDriver driver;
    protected WebDriverWait wait;

    protected static final String FRONTEND_URL =
            System.getProperty("test.frontend.url", "http://localhost:5173");
    protected static final String BACKEND_URL =
            System.getProperty("test.backend.url", "http://localhost:8020");    

    @BeforeEach
    void setupDriver() {
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        // Run headless in CI — remove for local debugging
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--window-size=1440,900");
        options.addArguments("--disable-gpu");

        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
    }

    @AfterEach
    void teardownDriver() {
        if (driver != null) {
            driver.quit();
        }
    }

    // Capture a named checkpoint screenshot and attach to Allure
    protected void screenshot(String testId, String label) {
        ScreenshotUtil.capture(driver, testId, label);
    }

    // Called in catch/finally for failure shots
    protected void screenshotFailure(String testId) {
        ScreenshotUtil.captureFailure(driver, testId);
    }
}