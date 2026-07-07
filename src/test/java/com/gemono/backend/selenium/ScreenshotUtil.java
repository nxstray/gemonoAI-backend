package com.gemono.backend.selenium;

import io.qameta.allure.Allure;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// Utility for capturing and attaching screenshots to Allure reports
public class ScreenshotUtil {

    private static final String SCREENSHOT_DIR = "target/screenshots";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    // Take screenshot and attach to Allure report
    public static void capture(WebDriver driver, String testId, String label) {
        if (!(driver instanceof TakesScreenshot)) return;

        try {
            byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            String filename = testId + "_" + label.replaceAll("\\s+", "_") + "_" + LocalDateTime.now().format(FMT) + ".png";

            // Save to disk
            Path dir = Paths.get(SCREENSHOT_DIR);
            Files.createDirectories(dir);
            Files.write(dir.resolve(filename), bytes);

            // Attach to Allure
            Allure.addAttachment(testId + " — " + label, "image/png", new ByteArrayInputStream(bytes), ".png");

        } catch (IOException e) {
            System.err.println("[Screenshot] Failed to capture: " + e.getMessage());
        }
    }

    // Capture on test failure
    public static void captureFailure(WebDriver driver, String testId) {
        capture(driver, testId, "FAILURE");
    }
}