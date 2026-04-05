$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$runtimeDir = Join-Path $repoRoot '.dev-runtime'
$backendPidFile = Join-Path $runtimeDir 'backend.pid'
$frontendPidFile = Join-Path $runtimeDir 'frontend.pid'
$wtWindowName = 'plexus-dev'

function Write-Step([string]$Message) {
    Write-Host "[dev-down] $Message" -ForegroundColor Cyan
}

function Stop-ManagedProcess {
    param(
        [string]$Name,
        [string]$PidFile,
        [string]$ProcessPattern = $null
    )

    if (-not (Test-Path $PidFile)) {
        Write-Step "$Name is not tracked (no PID file)."
        return
    }

    $processId = Get-Content $PidFile -ErrorAction SilentlyContinue
    if ($processId) {
        $proc = Get-Process -Id $processId -ErrorAction SilentlyContinue
        if ($proc) {
            try {
                # Build list of processes to kill:
                # 1. The PowerShell parent process
                # 2. Any related child processes (java.exe for backend, node.exe for frontend)
                $toKill = @($proc)

                # Add any matching child processes
                if ($ProcessPattern) {
                    $childProcs = Get-Process -Name $ProcessPattern -ErrorAction SilentlyContinue |
                        Where-Object { $_.Parent.Id -eq $processId -or $_.SessionId -eq $proc.SessionId } |
                        Select-Object -First 1

                    if ($childProcs) {
                        $toKill += @($childProcs)
                    }
                }

                # Kill the processes in order (highest PID first to kill children before parents)
                foreach ($p in ($toKill | Sort-Object -Property Id -Descending)) {
                    try {
                        if ($p -and -not $p.HasExited) {
                            # Try graceful close for the main process
                            if ($p.Id -eq $processId) {
                                $p.CloseMainWindow() | Out-Null
                                $p.WaitForExit(2000) | Out-Null
                            }

                            # Force kill if still running
                            if (-not $p.HasExited) {
                                Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
                            }
                        }
                    } catch {
                        # Process may have already terminated
                    }
                }

                # Also kill any remaining java or npm processes that might be orphaned
                if ($Name -like "*backend*") {
                    Get-Process -Name "java" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
                } elseif ($Name -like "*frontend*") {
                    Get-Process -Name "node" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
                }

                Write-Step "Stopped $Name (PID: $processId)."
            } catch {
                Write-Host "[dev-down] Error stopping $Name : $_" -ForegroundColor Yellow
            }
        } else {
            Write-Step "$Name was not running."
        }
    } else {
        Write-Step "$Name was not running."
    }

    Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
}

Write-Step "Stopping local dev processes..."
Stop-ManagedProcess -Name 'backend (bootRun)' -PidFile $backendPidFile -ProcessPattern 'java'
Stop-ManagedProcess -Name 'frontend (npm start)' -PidFile $frontendPidFile -ProcessPattern 'node'

Write-Step "Stopping Docker infrastructure..."
Push-Location $repoRoot
try {
    docker compose stop db redis oauth-mock redis-ui
} finally {
    Pop-Location
}

Write-Step "Done."

