# Local smoke test for the PriyoniX One authentication flow.
#
# It starts the packaged backend with the variables of backend/.env, then
# exercises sign-in, the role guard, the "Remember me" cookie, the session
# refresh and the sign out against the running server.
#
# Nothing is printed except the result of each step: no token, no cookie
# value and no secret ever reaches the console.

$ErrorActionPreference = "Stop"

$backendDir = "M:\priyonix-one\backend"
$jar = Join-Path $backendDir "target\backend-0.0.1-SNAPSHOT.jar"
$envFile = Join-Path $backendDir ".env"

# The port can be chosen so the check never collides with a server that is
# already running on the default port.
$port = if ($args.Count -ge 1) { $args[0] } else { "8080" }
$apiBase = "http://localhost:$port"

# ----- load the environment -----------------------------------------------
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()

    if ($line -eq "" -or $line.StartsWith("#")) {
        return
    }

    $index = $line.IndexOf("=")

    if ($index -lt 1) {
        return
    }

    $name = $line.Substring(0, $index).Trim()
    $value = $line.Substring($index + 1).Trim()

    Set-Item -Path "env:$name" -Value $value
}

# The frontend of this project runs on the Vite dev server.
$env:CORS_ALLOWED_ORIGINS = "http://localhost:5173"

# ----- start the backend --------------------------------------------------
$logFile = Join-Path $env:TEMP "priyonix-backend-smoke.log"

if (Test-Path $logFile) {
    Remove-Item $logFile -Force
}

$process = Start-Process -FilePath "java" `
    -ArgumentList "-jar", $jar, "--server.port=$port" `
    -WorkingDirectory $backendDir `
    -RedirectStandardOutput $logFile `
    -RedirectStandardError "$logFile.err" `
    -PassThru `
    -NoNewWindow

function Stop-Backend {
    if ($process -and -not $process.HasExited) {
        $process.Kill()
    }
}

try {
    $ready = $false

    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 2

        try {
            $health = Invoke-RestMethod -Uri "$apiBase/api/health" -TimeoutSec 3
            $ready = $true
            break
        } catch {
            if ($process.HasExited) {
                break
            }
        }
    }

    if (-not $ready) {
        Write-Output "RESULT backend did not become ready"
        Get-Content $logFile -Tail 30 | ForEach-Object { "LOG $_" }
        exit 1
    }

    Write-Output "RESULT backend is up ($($health.status))"

    # ----- sign in -------------------------------------------------------
    $loginBody = @{
        email = $env:ADMIN_EMAIL
        password = $env:ADMIN_PASSWORD
        role = "ADMIN"
        rememberMe = $true
    } | ConvertTo-Json

    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $login = Invoke-RestMethod -Uri "$apiBase/api/auth/login" `
        -Method Post -ContentType "application/json" -Body $loginBody `
        -WebSession $session -TimeoutSec 15

    Write-Output "RESULT login status=$($login.status) role=$($login.role) rememberMe=$($login.rememberMe)"

    $token = $login.token
    $headers = @{ Authorization = "Bearer $token" }

    # ----- the refresh cookie is HttpOnly --------------------------------
    # The cookie is scoped to /api/auth, so it is read from that path.
    $cookie = $session.Cookies.GetCookies("$apiBase/api/auth") |
        Where-Object { $_.Name -eq "px_refresh" }
    Write-Output "RESULT refresh cookie present=$([bool]$cookie) httpOnly=$($cookie.HttpOnly) path=$($cookie.Path) sameSite=$($cookie.SameSite)"

    # ----- the admin workspace is reachable -------------------------------
    $adminOverview = Invoke-RestMethod -Uri "$apiBase/api/admin/overview" -Headers $headers -TimeoutSec 15
    Write-Output "RESULT admin overview status=$($adminOverview.status)"

    # ----- the employee workspace is not ---------------------------------
    $employeeCode = 0

    try {
        Invoke-RestMethod -Uri "$apiBase/api/employee/overview" -Headers $headers -TimeoutSec 15 | Out-Null
        $employeeCode = 200
    } catch {
        $employeeCode = [int]$_.Exception.Response.StatusCode
    }

    Write-Output "RESULT admin on employee workspace http=$employeeCode (expected 403)"

    # ----- refresh the session -------------------------------------------
    $refresh = Invoke-RestMethod -Uri "$apiBase/api/auth/refresh" `
        -Method Post -WebSession $session -TimeoutSec 15
    Write-Output "RESULT refresh status=$($refresh.status) role=$($refresh.role) newToken=$([bool]$refresh.token)"

    # ----- sign out ------------------------------------------------------
    $logout = Invoke-RestMethod -Uri "$apiBase/api/auth/logout" `
        -Method Post -WebSession $session -TimeoutSec 15
    Write-Output "RESULT logout status=$($logout.status)"

    $afterLogout = 0

    try {
        Invoke-RestMethod -Uri "$apiBase/api/auth/refresh" -Method Post `
            -WebSession $session -TimeoutSec 15 | Out-Null
        $afterLogout = 200
    } catch {
        $afterLogout = [int]$_.Exception.Response.StatusCode
    }

    Write-Output "RESULT refresh after logout http=$afterLogout (expected 401)"
} finally {
    Stop-Backend
}
