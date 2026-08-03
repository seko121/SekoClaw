param(
    [Parameter(Mandatory = $true)][string]$StagingPrefix,
    [ValidateSet('arm64-v8a')][string]$Abi = 'arm64-v8a'
)

$resolved = (Resolve-Path -LiteralPath $StagingPrefix).Path
$required = @('bin\bash', 'bin\pkg', 'bin\curl', 'bin\git')
foreach ($relative in $required) {
    $file = Join-Path $resolved $relative
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
        throw "Missing required bootstrap file: $relative"
    }
}

$forbidden = @('/data/data/com.termux', '/data/user/0/com.termux')
Get-ChildItem -LiteralPath $resolved -Recurse -File | ForEach-Object {
    $bytes = [System.IO.File]::ReadAllBytes($_.FullName)
    $text = [System.Text.Encoding]::Latin1.GetString($bytes)
    foreach ($needle in $forbidden) {
        if ($text.Contains($needle)) { throw "Forbidden prefix in $($_.FullName): $needle" }
    }
}

Write-Output "Siko prefix validation passed for $Abi"
