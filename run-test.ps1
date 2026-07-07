# 1. Define folder paths
$allureResults = "target/allure-results"
$allureReport  = "target/allure-report"
$safeBackupDir = "allure-history-safe-backup"
$groqResultsBackup = "allure-groq-backup"

# 2. Backup old Allure history before clean
if (Test-Path "$allureReport/history") {
    Write-Host "Backing up old Allure history..." -ForegroundColor Green
    if (!(Test-Path $safeBackupDir)) { New-Item -ItemType Directory -Path $safeBackupDir | Out-Null }
    Copy-Item -Path "$allureReport/history" -Destination $safeBackupDir -Recurse -Force
} else {
    Write-Host "No previous history found. Starting fresh trend." -ForegroundColor Yellow
}

# 3. Backup groq test results before clean
if (Test-Path $allureResults) {
    Write-Host "Backing up groq test results..." -ForegroundColor Green
    Copy-Item -Path $allureResults -Destination $groqResultsBackup -Recurse -Force
}

# 4. Force recompile and run clean test
Write-Host "Force recompiling Java test classes..." -ForegroundColor Cyan
mvn test-compile

Write-Host "Running mvn clean test..." -ForegroundColor Cyan
mvn clean test

# 5. Restore groq test results after clean test
if (Test-Path $groqResultsBackup) {
    Write-Host "Restoring groq test results into allure-results..." -ForegroundColor Green
    Copy-Item -Path "$groqResultsBackup/*" -Destination $allureResults -Recurse -Force
    Remove-Item -Path $groqResultsBackup -Recurse -Force
}

# 6. Restore Allure history
if (Test-Path "$safeBackupDir/history") {
    Write-Host "Restoring Allure history into allure-results..." -ForegroundColor Green
    if (!(Test-Path "$allureResults/history")) {
        New-Item -ItemType Directory -Path "$allureResults/history" | Out-Null
    }
    Copy-Item -Path "$safeBackupDir/history/*" -Destination "$allureResults/history" -Recurse -Force
}

# 7. Generate fresh Allure report
if (Test-Path $allureResults) {
    Write-Host "Generating Allure report..." -ForegroundColor Cyan
    allure generate $allureResults --clean -o $allureReport

    if (Test-Path $safeBackupDir) { Remove-Item -Path $safeBackupDir -Recurse -Force }
    Write-Host "Done! All tests run and Allure history preserved." -ForegroundColor Green
} else {
    Write-Host "allure-results folder not found. Report could not be generated." -ForegroundColor Red
}