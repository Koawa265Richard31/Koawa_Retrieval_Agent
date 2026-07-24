param(
    [string]$SshHost = "jd-ecs",
    [int]$LocalPort = 15432
)

$ErrorActionPreference = "Stop"
$envFile = Join-Path $PSScriptRoot ".env"
$composeFile = Join-Path $PSScriptRoot "compose.yaml"

if (-not (Test-Path -LiteralPath $envFile)) {
    throw "Missing deploy/.env"
}

$listener = Get-NetTCPConnection `
    -LocalPort $LocalPort `
    -State Listen `
    -ErrorAction SilentlyContinue

if (-not $listener) {
    Start-Process `
        -FilePath "ssh.exe" `
        -ArgumentList @(
            "-N",
            "-L", "${LocalPort}:127.0.0.1:5432",
            "-o", "ExitOnForwardFailure=yes",
            "-o", "ServerAliveInterval=30",
            "-o", "ServerAliveCountMax=3",
            $SshHost
        ) `
        -WindowStyle Hidden
}

$deadline = (Get-Date).AddSeconds(20)
do {
    if (Test-NetConnection -ComputerName "127.0.0.1" -Port $LocalPort `
            -InformationLevel Quiet) {
        break
    }
    Start-Sleep -Milliseconds 500
} while ((Get-Date) -lt $deadline)

if (-not (Test-NetConnection -ComputerName "127.0.0.1" -Port $LocalPort `
        -InformationLevel Quiet)) {
    throw "SSH database tunnel did not become ready on port $LocalPort"
}

docker compose `
    --env-file $envFile `
    -f $composeFile `
    up -d --no-deps --force-recreate app

Write-Output "Local app now uses the production PostgreSQL tunnel on port $LocalPort."
