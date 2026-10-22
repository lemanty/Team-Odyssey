# Windows 10/11 environment setup. Run as the teammate's normal Windows user.
# Installers may request elevation. No database passwords or SSH keys are collected.
[CmdletBinding()]
param([switch]$CheckOnly, [switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root

function Refresh-Path {
    $env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')
}
function Find-Jdk {
    $candidates = @($env:JAVA_HOME, [Environment]::GetEnvironmentVariable('JAVA_HOME','User'), [Environment]::GetEnvironmentVariable('JAVA_HOME','Machine'))
    $java = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($java) { $candidates += Split-Path -Parent (Split-Path -Parent $java.Source) }
    foreach ($parent in @("$env:ProgramFiles\Microsoft", "$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:USERPROFILE\.jdks")) {
        if (Test-Path -LiteralPath $parent) {
            $candidates += @(Get-ChildItem -LiteralPath $parent -Directory | Sort-Object Name -Descending | Select-Object -ExpandProperty FullName)
        }
    }
    foreach ($jdkDirectory in $candidates | Select-Object -Unique) {
        if (-not $jdkDirectory) { continue }
        $compiler = Join-Path $jdkDirectory 'bin\javac.exe'
        if ((Test-Path -LiteralPath $compiler) -and (Test-Path -LiteralPath (Join-Path $jdkDirectory 'bin\java.exe'))) {
            $version = (& $compiler -version 2>&1 | Out-String)
            if ($LASTEXITCODE -eq 0 -and $version -match 'javac (\d+)' -and [int]$Matches[1] -ge 17) { return $jdkDirectory }
        }
    }
    return $null
}
function Find-Python {
    $candidates = @()
    $launcher = Get-Command py.exe -ErrorAction SilentlyContinue
    if ($launcher) {
        try {
            $exe = & $launcher.Source -3 -c 'import sys; print(sys.executable)' 2>$null
            if ($LASTEXITCODE -eq 0) { $candidates += $exe }
        } catch { } # A launcher without an installed interpreter is not usable.
    }
    foreach ($name in @('python.exe','python3.exe')) {
        $command = Get-Command $name -ErrorAction SilentlyContinue
        if ($command -and $command.Source -notlike '*\WindowsApps\*') { $candidates += $command.Source }
    }
    foreach ($parent in @("$env:LOCALAPPDATA\Programs\Python", "$env:ProgramFiles\Python")) {
        if (Test-Path -LiteralPath $parent) {
            $candidates += @(Get-ChildItem -LiteralPath $parent -Filter python.exe -Recurse | Select-Object -ExpandProperty FullName)
        }
    }
    foreach ($exe in $candidates | Select-Object -Unique) {
        if (-not (Test-Path -LiteralPath $exe)) { continue }
        try {
            & $exe -c 'import sys; sys.exit(0 if sys.version_info >= (3,9) else 1)' 2>$null
            if ($LASTEXITCODE -eq 0) { return $exe }
        } catch { } # Continue to another interpreter.
    }
    return $null
}
function Find-Ssh {
    $command = Get-Command ssh.exe -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $path = "$env:WINDIR\System32\OpenSSH\ssh.exe"
    if (Test-Path -LiteralPath $path) { return $path }
    return $null
}
function Install-Package([string]$id) {
    $winget = Get-Command winget.exe -ErrorAction SilentlyContinue
    if (-not $winget) { throw 'WinGet is missing. Install/update Microsoft App Installer from Microsoft Store, then rerun Windows-Setup.cmd.' }
    Write-Host "Installing $id through WinGet. Review any installer/agreement prompts."
    & $winget.Source install --id $id --exact --source winget
    if ($LASTEXITCODE -ne 0) { throw "WinGet failed ($LASTEXITCODE). Review the message above and rerun; no existing software is removed." }
    Refresh-Path
}
function Add-UserPath([string]$directory) {
    $current = [Environment]::GetEnvironmentVariable('Path','User')
    if (@($current -split ';' | ForEach-Object { $_.TrimEnd('\') }) -notcontains $directory.TrimEnd('\')) {
        # Do not use setx: it can truncate long PATH values.
        [Environment]::SetEnvironmentVariable('Path', ($directory + ';' + $current).TrimEnd(';'), 'User')
    }
}

try {
    Write-Host 'Team Odyssey - Windows environment setup'
    Write-Host 'Requires Internet. Database access is configured separately in .env.team.'
    $jdk = Find-Jdk
    $python = Find-Python
    $ssh = Find-Ssh
    if ($CheckOnly) {
        if ($jdk) { Write-Host '[OK] JDK 17+ found.' } else { Write-Host '[MISSING] JDK 17+ (including javac).' }
        if ($python) { Write-Host '[OK] Python 3.9+ found.' } else { Write-Host '[MISSING] Python 3.9+.' }
        if ($ssh) { Write-Host '[OK] OpenSSH client found.' } else { Write-Host '[MISSING] OpenSSH client.' }
        if (-not $jdk -or -not $python -or -not $ssh) { exit 1 }
        Write-Host 'Environment prerequisites found. This does not verify database credentials or the SSH tunnel.'
        exit 0
    }
    if (-not $jdk) { Install-Package 'Microsoft.OpenJDK.21'; $jdk = Find-Jdk }
    if (-not $jdk) { throw 'JDK installation was not detected. Install JDK 17 or 21 and rerun.' }
    if (-not $python) { Install-Package 'Python.Python.3.12'; $python = Find-Python }
    if (-not $python) { throw 'Python installation was not detected. Reopen this script after installation.' }
    if (-not $ssh) {
        Write-Host 'Installing the Windows OpenSSH CLIENT. Windows will ask for administrator approval.'
        $sshInstallArgs = @('-NoProfile','-Command','"$ErrorActionPreference=''Stop''; try { Add-WindowsCapability -Online -Name OpenSSH.Client~~~~0.0.1.0 | Out-Null; exit 0 } catch { Write-Error $_; exit 1 }"')
        $process = Start-Process -FilePath "$env:WINDIR\System32\WindowsPowerShell\v1.0\powershell.exe" -Verb RunAs -ArgumentList $sshInstallArgs -Wait -PassThru
        if ($process.ExitCode -ne 0) { throw 'OpenSSH client installation failed. Check Windows Update or ask the device administrator to enable OpenSSH Client in Optional Features.' }
        $ssh = Find-Ssh
        if (-not $ssh) { throw 'OpenSSH client is not available yet. Restart Windows if requested and rerun.' }
    }
    [Environment]::SetEnvironmentVariable('JAVA_HOME',$jdk,'User')
    Add-UserPath (Join-Path $jdk 'bin')
    Add-UserPath (Split-Path -Parent $python)
    Add-UserPath (Split-Path -Parent $ssh)
    Refresh-Path
    $env:JAVA_HOME = $jdk
    $env:PYTHONUTF8 = '1'
    $env:PYTHONIOENCODING = 'utf-8'
    if (-not (Test-Path -LiteralPath '.env.team')) {
        Copy-Item -LiteralPath '.env.team.example' -Destination '.env.team'
        Write-Host 'Created .env.team with placeholders. Get connection details from the database host.'
    } else { Write-Host 'Kept existing .env.team unchanged.' }
    & (Join-Path $jdk 'bin\java.exe') -version
    if ($LASTEXITCODE -ne 0) { throw 'Java validation failed.' }
    & $python --version
    if ($LASTEXITCODE -ne 0) { throw 'Python validation failed.' }
    & $ssh -V
    if ($LASTEXITCODE -ne 0) { throw 'OpenSSH validation failed.' }
    if (-not $SkipBuild) {
        Write-Host 'Downloading Maven/dependencies and compiling (no database needed). First run may take several minutes.'
        & $env:ComSpec /d /c 'mvnw.cmd -B -DskipTests compile'
        if ($LASTEXITCODE -ne 0) { throw 'Build failed. Check network access to Maven Central and the build error above. Rerun to retry.' }
    }
    Write-Host '[OK] Environment setup complete.'
    Write-Host 'Close and reopen IntelliJ/terminals to reload environment variables.'
    Write-Host 'Next: fill .env.team; have the host authorize your SSH public key; run Member-Connect.cmd, then Member-Website.cmd.'
    Write-Host 'No Docker, database server, SSH server or firewall changes were installed/configured on this member computer.'
    exit 0
} catch {
    Write-Host ('[FAILED] ' + $_.Exception.Message) -ForegroundColor Red
    exit 1
}
