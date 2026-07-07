Clear-Host
Write-Host "WARNING: Running Groq tests against REAL Production API..." -ForegroundColor Red
Write-Host "This will consume real token quotas!" -ForegroundColor Yellow
Write-Host ""

# NOTE: Make sure the backend server is already running on port 8020 before running this script
mvn test `
    "-DexcludedGroups=requires-external" `
    "-Dgroups=requires-groq,performance" `
    "-Dspring.profiles.active=prod-test" `
    "-Dallure.results.directory=target/allure-results"

Write-Host ""
Write-Host "Done. Now run .\run-test.ps1 to merge results and generate report." -ForegroundColor Green