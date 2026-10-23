# Debug helper: prints the raw sign-in response with the token redacted.

$ErrorActionPreference = "Stop"

$backendDir = "M:\priyonix-one\backend"
$jar = Join-Path $backendDir "target\backend-0.0.1-SNAPSHOT.jar"
$envFile = Join-Path $backendDir ".env"
$apiBase = "http://localhost:8080"

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()

    if ($line -eq "" -or $line.StartsWith("#")) { return }

    $index = $line.IndexOf("=")

    if ($index -lt 1) { return }

    Set-Item -Path ("env:" + $line.Substring(0, $index).Trim()) `
        -Value $line.Substring($index + 1).Trim()
}

$env:CORS_ALLOWED_ORIGINS = "http://localhost:5173"

$logFile = Join-Path $env:TEMP "priyonix-backend-debug.log"
$process = Start-Process -FilePath "java" -ArgumentList "-jar", $jar `
    -WorkingDirectory $backendDir -RedirectStandardOutput $logFile `
    -RedirectStandardError "$logFile.err" -PassThru -NoNewWindow

try {
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 2

        try {
            Invoke-RestMethod -Uri "$apiBase/api/health" -TimeoutSec 3 | Out-Null
            break
        } catch {
            if ($process.HasExited) { break }
        }
    }

    $body = @{
        email = $env:ADMIN_EMAIL
        password = $env:ADMIN_PASSWORD
        role = "ADMIN"
        rememberMe = $true
    } | ConvertTo-Json -Compress

    Write-Output "REQUEST $body"

    $raw = Invoke-WebRequest -Uri "$apiBase/api/auth/login" -Method Post `
        -ContentType "application/json" -Body $body -TimeoutSec 15 `
        -UseBasicParsing

    $text = $raw.Content -replace '"token":"[^"]+"', '"token":"<redacted>"'
    Write-Output "RESPONSE $text"

    Write-Output "SET-COOKIE $($raw.Headers['Set-Cookie'] -join ' | ')"
} finally {
    if ($process -and -not $process.HasExited) {
        $process.Kill()
    }
}
