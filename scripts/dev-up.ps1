param(
    [switch]$DryRun,
    [switch]$NoFrontend,
    [switch]$NoBackend
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$runtimeDir = Join-Path $repoRoot '.dev-runtime'
$backendPidFile = Join-Path $runtimeDir 'backend.pid'
$frontendPidFile = Join-Path $runtimeDir 'frontend.pid'
$wtWindowName = 'plexus-dev'
$wtAvailable = $null -ne (Get-Command wt -ErrorAction SilentlyContinue)

function Write-Step([string]$Message) {
    Write-Host "[dev-up] $Message" -ForegroundColor Cyan
}

function Assert-ToolAvailable {
    param(
        [string]$Tool,
        [string]$Guidance
    )

    if ($null -eq (Get-Command $Tool -ErrorAction SilentlyContinue)) {
        throw "Required tool '$Tool' is not available in this shell. $Guidance"
    }
}

function Wait-ForHttpEndpoint {
    param(
        [string]$Url,
        [int]$TimeoutSeconds = 90
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
                return $true
            }
        } catch {
            # Backend may still be starting.
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    return $false
}

function Start-ManagedProcess {
    param(
        [string]$Name,
        [string]$PidFile,
        [string]$Command,
        [string]$TabTitle = 'dev-task'
    )

    if (Test-Path $PidFile) {
        $existingPid = Get-Content $PidFile -ErrorAction SilentlyContinue
        if ($existingPid -and (Get-Process -Id $existingPid -ErrorAction SilentlyContinue)) {
            Write-Step "$Name is already running (PID: $existingPid)."
            return
        }
        Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
    }

    if ($DryRun) {
        Write-Step "[dry-run] Would start $Name with command: $Command"
        if ($wtAvailable) {
            Write-Step "[dry-run] Would prefer Windows Terminal tab in window '$wtWindowName'."
        }
        return
    }

    $singleQuotedPidPath = $PidFile.Replace("'", "''")
    $managedCommand = @"
`$PID | Out-File -FilePath '$singleQuotedPidPath' -Encoding ascii -Force
$Command
"@

    if ($wtAvailable) {
        try {
            $safeName = (($TabTitle -replace '[^a-zA-Z0-9_-]', '-').ToLower())
            $launcherScript = Join-Path $runtimeDir ("launch-" + $safeName + ".ps1")

            # Keep the same command resolution context in launched tabs.
            $escapedPath = $env:PATH.Replace("'", "''")
            $escapedJavaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME.Replace("'", "''") } else { '' }
            $launcherPrefix = @"
`$env:PATH = '$escapedPath'
if ('$escapedJavaHome' -ne '') { `$env:JAVA_HOME = '$escapedJavaHome' }
"@

            Set-Content -Path $launcherScript -Value ($launcherPrefix + "`r`n" + $managedCommand) -Encoding UTF8

            $powerShellExe = Join-Path $PSHOME 'powershell.exe'
            Start-Process -FilePath "wt.exe" -ArgumentList @(
                "-w", $wtWindowName,
                "new-tab",
                "--title", $TabTitle,
                $powerShellExe,
                "-NoExit",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                $launcherScript
            ) | Out-Null

            $deadline = (Get-Date).AddSeconds(20)
            do {
                Start-Sleep -Seconds 1
                if (Test-Path $PidFile) {
                    $tabPid = Get-Content $PidFile -ErrorAction SilentlyContinue
                    if ($tabPid -and (Get-Process -Id $tabPid -ErrorAction SilentlyContinue)) {
                        Write-Step "Started $Name in Windows Terminal tab (PID: $tabPid)."
                        return
                    }
                }
            } while ((Get-Date) -lt $deadline)

            Write-Host "[dev-up] Could not confirm Windows Terminal tab PID for $Name. Falling back to separate PowerShell window." -ForegroundColor Yellow
            Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
        } catch {
            Write-Host "[dev-up] Windows Terminal launch failed for $Name. Falling back to separate PowerShell window." -ForegroundColor Yellow
        }
    }

    $proc = Start-Process -FilePath "powershell.exe" -ArgumentList "-NoExit", "-Command", $Command -PassThru
    $proc.Id | Out-File -FilePath $PidFile -Encoding ascii -Force
    Write-Step "Started $Name (PID: $($proc.Id))."
}

New-Item -ItemType Directory -Path $runtimeDir -Force | Out-Null

Write-Step "Starting Docker infrastructure (db, redis, oauth-mock, redis-ui)..."
if ($wtAvailable) {
    Write-Step "Windows Terminal detected. Local app processes will open as tabs in window '$wtWindowName'."
} else {
    Write-Step "Windows Terminal (wt) not found. Falling back to separate PowerShell windows."
}
if ($DryRun) {
    Write-Step "[dry-run] Would run: docker compose up -d db redis oauth-mock redis-ui"
} else {
    Push-Location $repoRoot
    try {
        docker compose up -d db redis oauth-mock redis-ui
    } finally {
        Pop-Location
    }
}

if ($DryRun) {
    Write-Step "[dry-run] Would run: docker compose stop app"
} else {
    Push-Location $repoRoot
    try {
        # Ensure the containerized app does not conflict with local bootRun on port 8080.
        docker compose stop app | Out-Null
    } finally {
        Pop-Location
    }
}

if (-not $NoBackend) {
    Assert-ToolAvailable -Tool 'java' -Guidance "Install JDK 21 and ensure JAVA_HOME/PATH are configured."

    $backendCommand = @"
Set-Location '$repoRoot'
`$env:SPRING_PROFILES_ACTIVE='dev'
`$env:SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5432/bankengine'
`$env:SPRING_DATASOURCE_DRIVER_CLASS_NAME='org.postgresql.Driver'
`$env:SPRING_DATASOURCE_USERNAME='user'
`$env:SPRING_DATASOURCE_PASSWORD='password'
`$env:SPRING_JPA_DATABASE_PLATFORM='org.hibernate.dialect.PostgreSQLDialect'
`$env:SPRING_JPA_HIBERNATE_DDL_AUTO='update'
`$env:SPRING_SESSION_STORE_TYPE='redis'
`$env:REDIS_HOST='localhost'
`$env:REDIS_PORT='6379'
`$env:APP_SECURITY_SYSTEM_ISSUER='http://identity-provider:9090/default'
`$env:JWT_ISSUER_URI='http://identity-provider:9090/default'
`$env:SWAGGER_AUTH_URL='http://identity-provider:9090/default/authorize'
`$env:SWAGGER_TOKEN_URL='http://identity-provider:9090/default/token'
./gradlew.bat bootRun
"@
    Start-ManagedProcess -Name 'backend (bootRun)' -PidFile $backendPidFile -Command $backendCommand -TabTitle 'backend'

    if ($DryRun) {
        Write-Step "[dry-run] Would wait for backend health: http://localhost:8080/actuator/health"
    } else {
        Write-Step "Waiting for local backend to become healthy on :8080..."
        if (-not (Wait-ForHttpEndpoint -Url 'http://localhost:8080/actuator/health' -TimeoutSeconds 120)) {
            Write-Host "[dev-up] Backend did not become healthy in time. Frontend may show proxy errors until backend finishes startup." -ForegroundColor Yellow
        } else {
            Write-Step "Backend is reachable on http://localhost:8080"
        }
    }
}

if (-not $NoFrontend) {
    Assert-ToolAvailable -Tool 'npm' -Guidance "Install Node.js and ensure npm is on PATH."

    $frontendCommand = @"
Set-Location '$repoRoot\src\main\frontend'
npm start
"@
    Start-ManagedProcess -Name 'frontend (npm start)' -PidFile $frontendPidFile -Command $frontendCommand -TabTitle 'frontend'
}

Write-Step "Done."
Write-Host "- Backend: http://localhost:8080" -ForegroundColor Green
Write-Host "- Frontend (hot reload): http://localhost:3000" -ForegroundColor Green
Write-Host "- Redis UI: http://localhost:8081" -ForegroundColor Green
Write-Host "Use ./scripts/dev-down.ps1 to stop dev processes and Docker infra." -ForegroundColor Yellow

