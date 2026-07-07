Clear-Host
Write-Host "Running Groq-dependent tests safely with mock profile..." -ForegroundColor Cyan
Write-Host "Ensuring no real external token limits are hit." -ForegroundColor Yellow
Write-Host ""

# NOTE: Make sure the backend server is already running on port 8020 before running this script
mvn test `
    "-DexcludedGroups=requires-external" `
    "-Dgroups=requires-groq,performance" `
    "-Dspring.profiles.active=test" `
    "-Dallure.results.directory=target/allure-results"

Write-Host ""
Write-Host "Done. Now run .\run-test.ps1 to merge results and generate report." -ForegroundColor Green