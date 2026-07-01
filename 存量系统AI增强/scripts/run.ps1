$ErrorActionPreference = "Stop"

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
& (Join-Path $PSScriptRoot "build.ps1")
& java -jar (Join-Path $root "build\legacy-ai-knowledge-base.jar")
