$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$packRoot = Join-Path $root "packs\dreamcraft-bedrock"
$manifest = Join-Path $packRoot "manifest.json"
$packIcon = Join-Path $packRoot "pack_icon.png"
$dist = Join-Path $root "dist"
$output = Join-Path $dist "dreamcraft-bedrock.mcpack"

# Fail-fast validations (mismo patrón que validate.ps1 / build.ps1)
if (-not (Test-Path -LiteralPath $packRoot -PathType Container)) {
  throw "Missing Bedrock resource pack root: $packRoot"
}

if (-not (Test-Path -LiteralPath $manifest -PathType Leaf)) {
  throw "Missing manifest.json in Bedrock pack. Confirm manifest.json exists before building."
}

if (-not (Test-Path -LiteralPath $packIcon -PathType Leaf)) {
  throw "Missing pack_icon.png in Bedrock pack. Confirm pack_icon.png exists before building."
}

# manifest.json must parse as valid JSON
try {
  $manifestContent = Get-Content -LiteralPath $manifest -Raw | ConvertFrom-Json
} catch {
  throw "Invalid manifest.json: $_"
}

# Validate format_version, header, and modules in manifest.json
if (-not $manifestContent.format_version -or -not $manifestContent.header -or -not $manifestContent.modules) {
  throw "manifest.json is missing required fields (format_version, header, modules)."
}

if (-not $manifestContent.header.uuid -or -not $manifestContent.modules[0].uuid) {
  throw "manifest.json is missing UUIDs in header or modules."
}

New-Item -ItemType Directory -Force -Path $dist | Out-Null

if (Test-Path -LiteralPath $output -PathType Leaf) {
  Remove-Item -LiteralPath $output
}

# Build the archive manually so entry names always use forward slashes;
# Compress-Archive writes backslashes, which Minecraft cannot resolve.
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$zip = [System.IO.Compression.ZipFile]::Open($output, [System.IO.Compression.ZipArchiveMode]::Create)
try {
  Get-ChildItem -LiteralPath $packRoot -Recurse -File |
    Where-Object { $_.Name -ne ".gitkeep" } |
    ForEach-Object {
      $entryName = $_.FullName.Substring($packRoot.Length + 1).Replace("\", "/")
      [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
        $zip, $_.FullName, $entryName,
        [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
}
finally {
  $zip.Dispose()
}

Write-Output "Built $output"
