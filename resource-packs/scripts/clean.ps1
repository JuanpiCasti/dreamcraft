$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$dist = Join-Path $root "dist"

New-Item -ItemType Directory -Force -Path $dist | Out-Null
Get-ChildItem -LiteralPath $dist -File | Where-Object { $_.Name -ne ".gitkeep" } | Remove-Item

Write-Output "Cleaned resource-packs dist directory."
