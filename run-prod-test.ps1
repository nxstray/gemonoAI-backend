Clear-Host
Write-Host "WARNING: Running tests against PRODUCTION deployment..." -ForegroundColor Red
Write-Host ""

$backendUrl  = "https://xxx.hf.space"
$frontendUrl = "https://gemono.vercel.app"

Write-Host "Backend : $backendUrl" -ForegroundColor Yellow
Write-Host "Frontend: $frontendUrl" -ForegroundColor Yellow
Write-Host ""

# performance      -> jangan load-test server production
# requires-external -> jangan kirim email Resend asli berkali-kali
# prod-unsafe      -> test yang memang sengaja gagal/tidak relevan di production (mis. Swagger dimatikan)
mvn test `
    "-Dtest.backend.url=$backendUrl" `
    "-Dtest.frontend.url=$frontendUrl" `
    "-DexcludedGroups=performance,requires-external,prod-unsafe" `
    "-Dallure.results.directory=target/allure-results"

Write-Host ""
Write-Host "Done. Now run .\run-test.ps1 to merge results and generate report." -ForegroundColor Green