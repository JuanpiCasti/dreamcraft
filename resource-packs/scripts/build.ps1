$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$packRoot = Join-Path $root "packs\dreamcraft"
$packMeta = Join-Path $packRoot "pack.mcmeta"
$dist = Join-Path $root "dist"
$output = Join-Path $dist "dreamcraft-resource-pack.zip"

if (-not (Test-Path -LiteralPath $packMeta -PathType Leaf)) {
  throw "Missing pack.mcmeta. Confirm the Minecraft version and pack_format before building."
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
