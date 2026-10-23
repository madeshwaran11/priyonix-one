# Runs the end-to-end authentication check of the frontend.
#
# It starts the current backend build, a preview server for the frontend and
# a headless Chrome, and drives the sign-in screens through the DevTools
# Protocol.
#
# The frontend is built with a VITE_API_BASE that points at the backend of
# this check, so the build that is left behind at the end is the normal one.

$ErrorActionPreference = "Stop"

$backendDir = "M:\priyonix-one\backend"
$frontendDir = "M:\priyonix-one\frontend"
$jar = Join-Path $backendDir "target\backend-0.0.1-SNAPSHOT.jar"
$envFile = Join-Path $backendDir ".env"

$apiPort = 8093
$appPort = 4174
$cdpPort = 9222

$apiBase = "http://localhost:$apiPort"
$appUrl = "http://localhost:$appPort"

# ----- load the environment -----------------------------------------------
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()

    if ($line -eq "" -or $line.StartsWith("#")) { return }

    $index = $line.IndexOf("=")

    if ($index -lt 1) { return }

    Set-Item -Path ("env:" + $line.Substring(0, $index).Trim()) `
        -Value $line.Substring($index + 1).Trim()
}

$env:CORS_ALLOWED_ORIGINS = "http://localhost:5173,$appUrl"

$backend = $null
$preview = $null
$chrome = $null

function Stop-Process_($process) {
    if (-not $process) { return }

    $processId = $process.Id

    if (-not $processId) { return }

    # The whole tree is stopped: the wrapper processes (npm, chrome) leave
    # children behind that would keep the redirected output pipes open and
    # block the end of this script.
    #
    # taskkill reports on stderr when a process is already gone, so the
    # preference is relaxed around the call.
    $ErrorActionPreference = "Continue"
    & taskkill.exe /PID $processId /T /F 2>&1 | Out-Null
    $ErrorActionPreference = "Stop"
}

try {
    # ----- backend -------------------------------------------------------
    $backendLog = Join-Path $env:TEMP "priyonix-e2e-backend.log"
    $backend = Start-Process -FilePath "java" `
        -ArgumentList "-jar", $jar, "--server.port=$apiPort" `
        -WorkingDirectory $backendDir `
        -RedirectStandardOutput $backendLog `
        -RedirectStandardError "$backendLog.err" `
        -PassThru -NoNewWindow

    $ready = $false

    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 2

        try {
            Invoke-RestMethod -Uri "$apiBase/api/health" -TimeoutSec 3 | Out-Null
            $ready = $true
            break
        } catch {
            if ($backend.HasExited) { break }
        }
    }

    if (-not $ready) {
        throw "the backend did not become ready"
    }

    Write-Output "RESULT the backend is up on $apiBase"

    # ----- frontend build and preview -------------------------------------
    Push-Location $frontendDir

    try {
        $env:VITE_API_BASE = $apiBase

        # The build writes progress notes to stderr; with "Stop" that would
        # abort the script, so the preference is relaxed around the call and
        # the exit code is checked instead.
        $ErrorActionPreference = "Continue"
        & npm.cmd run build 2>&1 | Out-Null
        $buildCode = $LASTEXITCODE
        $ErrorActionPreference = "Stop"
        $env:VITE_API_BASE = $null

        if ($buildCode -ne 0) {
            throw "the frontend build failed with exit code $buildCode"
        }

        $preview = Start-Process -FilePath "npm.cmd" `
            -ArgumentList "run", "preview", "--", "--port", "$appPort", "--strictPort" `
            -WorkingDirectory $frontendDir `
            -RedirectStandardOutput (Join-Path $env:TEMP "priyonix-e2e-preview.log") `
            -RedirectStandardError (Join-Path $env:TEMP "priyonix-e2e-preview.err") `
            -PassThru -NoNewWindow

        $serving = $false

        for ($i = 0; $i -lt 40; $i++) {
            Start-Sleep -Seconds 1

            try {
                Invoke-WebRequest -Uri "$appUrl/login" -UseBasicParsing -TimeoutSec 3 | Out-Null
                $serving = $true
                break
            } catch {
                if ($preview.HasExited) { break }
            }
        }

        if (-not $serving) {
            throw "the preview server did not become ready"
        }

        Write-Output "RESULT the frontend is served on $appUrl"

        # ----- headless chrome --------------------------------------------
        $chromeExe = "C:\Program Files\Google\Chrome\Application\chrome.exe"
        $profile = Join-Path $env:TEMP ("priyonix-e2e-chrome-" + $PID)

        # A browser that is still listening on the debugging port would make
        # the readiness probe succeed while the new instance walks away, so
        # the old one is stopped first.
        Get-NetTCPConnection -State Listen -LocalPort $cdpPort -ErrorAction SilentlyContinue |
            ForEach-Object {
                Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue
            }

        Start-Sleep -Seconds 1

        $chrome = Start-Process -FilePath $chromeExe `
            -ArgumentList "--headless=new", "--disable-gpu", "--no-sandbox", `
                "--remote-debugging-port=$cdpPort", "--user-data-dir=$profile", `
                "--window-size=1400,900", "about:blank" `
            -RedirectStandardOutput (Join-Path $env:TEMP "priyonix-e2e-chrome.log") `
            -RedirectStandardError (Join-Path $env:TEMP "priyonix-e2e-chrome.err") `
            -PassThru -NoNewWindow

        $devtools = $false

        for ($i = 0; $i -lt 40; $i++) {
            Start-Sleep -Seconds 1

            try {
                Invoke-WebRequest -Uri "http://127.0.0.1:$cdpPort/json/version" `
                    -UseBasicParsing -TimeoutSec 3 | Out-Null
                $devtools = $true
                break
            } catch {
                if ($chrome.HasExited) { break }
            }
        }

        if (-not $devtools) {
            throw "the browser did not become ready"
        }

        # ----- the check ----------------------------------------------------
        $env:CDP_PORT = $cdpPort
        $env:APP_URL = $appUrl
        $env:API_BASE = $apiBase

        Write-Output "RESULT the check is starting"

        & node (Join-Path $frontendDir "scripts\e2e-auth.mjs")
        $code = $LASTEXITCODE
    } finally {
        Pop-Location
    }

    exit $code
} finally {
    Stop-Process_ $chrome
    Stop-Process_ $preview
    Stop-Process_ $backend

    # The preview server is started through npm, which detaches the server
    # from its own process, so the ports are freed separately.
    foreach ($port in @($appPort, $cdpPort, $apiPort)) {
        Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue |
            ForEach-Object {
                Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue
            }
    }
}
