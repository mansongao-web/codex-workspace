$ErrorActionPreference = "Stop"

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$buildDir = Join-Path $root "build"
$classesDir = Join-Path $buildDir "classes\main"
$jarFile = Join-Path $buildDir "legacy-ai-knowledge-base.jar"

if (Test-Path $classesDir) {
    Remove-Item -Recurse -Force $classesDir
}
New-Item -ItemType Directory -Force -Path $classesDir | Out-Null

$sources = Get-ChildItem -Path (Join-Path $root "src\main\java") -Recurse -Filter *.java
if ($sources.Count -eq 0) {
    throw "No Java sources found."
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$sourceList = Join-Path $buildDir "sources-main.txt"
[System.IO.File]::WriteAllLines($sourceList, $sources.FullName, $utf8NoBom)

& javac -encoding UTF-8 -d $classesDir "@$sourceList"
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}

$resourcesDir = Join-Path $root "src\main\resources"
if (Test-Path $resourcesDir) {
    Copy-Item -Path (Join-Path $resourcesDir "*") -Destination $classesDir -Recurse -Force
}

$manifest = Join-Path $buildDir "MANIFEST.MF"
[System.IO.File]::WriteAllText($manifest, "Main-Class: com.example.legacyai.LegacyAiApplication`n", $utf8NoBom)

& jar cfm $jarFile $manifest -C $classesDir .
if ($LASTEXITCODE -ne 0) {
    throw "jar failed with exit code $LASTEXITCODE"
}

Write-Host "Built $jarFile"
