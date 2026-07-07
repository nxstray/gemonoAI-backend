# 1. Tentukan jalur folder
$allureResults = "target/allure-results"
$allureReport = "target/allure-report"
$backupDir = "allure-history-backup"

# 2. Amankan folder history lama jika ada
if (Test-Path "$allureReport/history") {
    Write-Host "Mengamankan history Allure lama ke folder backup..." -ForegroundColor Green
    if (!(Test-Path $backupDir)) { New-Item -ItemType Directory -Path $backupDir | Out-Null }
    # Memastikan folder history disalin dengan rapi ke backup
    Copy-Item -Path "$allureReport/history" -Destination $backupDir -Recurse -Force
} else {
    Write-Host "Tidak ditemukan history lama. Memulai tren baru." -ForegroundColor Yellow
}

# 3. Jalankan Maven Clean Compile dan Install dengan aman
Write-Host "Menjalankan mvn clean compile..." -ForegroundColor Cyan
mvn clean compile

# Dilanjutkan dengan install tanpa clean agar kompilasi tadi tidak terhapus sia-sia
Write-Host "Menjalankan mvn install -DskipTests..." -ForegroundColor Cyan
mvn install -DskipTests

# 4. Kembalikan folder history ke target/allure-results agar grafik terbaca
if (Test-Path "$backupDir/history") {
    Write-Host "Mengembalikan history ke $allureResults..." -ForegroundColor Green
    if (!(Test-Path "$allureResults/history")) { 
        New-Item -ItemType Directory -Path "$allureResults/history" | Out-Null 
    }
    Copy-Item -Path "$backupDir/history/*" -Destination "$allureResults/history" -Recurse -Force
}

# 5. Generate laporan Allure yang baru
if (Test-Path $allureResults) {
    Write-Host "Membuat laporan Allure baru..." -ForegroundColor Cyan
    allure generate $allureResults --clean -o $allureReport
    
    # 6. Bersihkan folder backup sementara setelah selesai
    if (Test-Path $backupDir) { Remove-Item -Path $backupDir -Recurse -Force }
    Write-Host "Proses selesai! Laporan Allure berhasil diperbarui dengan history aman." -ForegroundColor Green
} else {
    Write-Host "Folder allure-results tidak ditemukan atau kosong karena skipTests. Laporan Allure lama dipertahankan." -ForegroundColor Yellow
}