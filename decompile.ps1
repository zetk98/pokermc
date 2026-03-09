# Decompile pokermc-1.0.0.jar
# 1. Đặt pokermc-1.0.0.jar vào thư mục này
# 2. Tải cfr-0.152.jar từ https://github.com/leibnitz27/cfr/releases
# 3. Đặt cfr-*.jar vào thư mục này
# 4. Chạy: .\decompile.ps1

$jar = "pokermc-1.0.0.jar"
$cfr = Get-ChildItem -Filter "cfr-*.jar" | Select-Object -First 1

if (-not (Test-Path $jar)) {
    Write-Host "Khong tim thay $jar - hay dat file vao thu muc: $PWD"
    exit 1
}
if (-not $cfr) {
    Write-Host "Khong tim thay cfr-*.jar - tai tu: https://github.com/leibnitz27/cfr/releases"
    exit 1
}

$out = "decompiled"
if (Test-Path $out) { Remove-Item -Recurse -Force $out }
java -jar $cfr.FullName $jar --outputdir $out
Write-Host "Xong! Xem trong thu muc: $out"
