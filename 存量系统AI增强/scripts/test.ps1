$ErrorActionPreference = "Stop"

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
& (Join-Path $PSScriptRoot "build.ps1")

$buildDir = Join-Path $root "build"
$mainClasses = Join-Path $buildDir "classes\main"
$testClasses = Join-Path $buildDir "classes\test"

if (Test-Path $testClasses) {
    Remove-Item -Recurse -Force $testClasses
}
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null

$sources = Get-ChildItem -Path (Join-Path $root "src\test\java") -Recurse -Filter *.java
if ($sources.Count -eq 0) {
    Write-Host "No tests found."
    exit 0
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$sourceList = Join-Path $buildDir "sources-test.txt"
[System.IO.File]::WriteAllLines($sourceList, $sources.FullName, $utf8NoBom)

& javac -encoding UTF-8 -cp $mainClasses -d $testClasses "@$sourceList"
if ($LASTEXITCODE -ne 0) {
    throw "test javac failed with exit code $LASTEXITCODE"
}

& java -cp "$mainClasses;$testClasses" com.example.legacyai.SmokeTests
if ($LASTEXITCODE -ne 0) {
    throw "tests failed with exit code $LASTEXITCODE"
}
