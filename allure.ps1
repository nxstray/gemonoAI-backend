# copy history if exists
if (Test-Path "target\allure-report\history") {
    Copy-Item -Path "target\allure-report\history" -Destination "target\allure-results\history" -Recurse -Force
}

# generate + open
allure generate target\allure-results --clean -o target\allure-report
allure open target\allure-report