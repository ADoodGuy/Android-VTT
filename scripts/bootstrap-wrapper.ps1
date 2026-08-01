$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$Target = Join-Path $Root "gradle\wrapper\gradle-wrapper.jar"
$Url = "https://raw.githubusercontent.com/gradle/gradle/v9.5.0/gradle/wrapper/gradle-wrapper.jar"
$Expected = "497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7"

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Target) | Out-Null
Invoke-WebRequest -Uri $Url -OutFile $Target

$Actual = (Get-FileHash -Path $Target -Algorithm SHA256).Hash.ToLowerInvariant()
if ($Actual -ne $Expected) {
    Remove-Item $Target -Force
    throw "Gradle wrapper checksum mismatch."
}

Write-Host "Gradle wrapper installed and verified: $Target"
