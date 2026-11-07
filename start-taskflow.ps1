param([ValidateSet('start', 'stop')][string]$Action = 'start')

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$runtime = Join-Path $root '.taskflow'
$data = Join-Path $runtime 'postgres-data'
$bin = Join-Path $runtime 'node_modules\@embedded-postgres\windows-x64\native\bin'
$pgCtl = Join-Path $bin 'pg_ctl.exe'
$passwordFile = Join-Path $runtime 'db-password'
$databaseReady = Join-Path $runtime 'database.ready'
$processFile = Join-Path $runtime 'server.json'
$serverLog = Join-Path $runtime 'server.log'
$errorLog = Join-Path $runtime 'server-error.log'
$jar = Join-Path $root 'target\taskflow-1.0.0.jar'
$dbPort = 55432
$webPort = 8080

function Find-Java {
    if ($env:JAVA_HOME) {
        $java = Join-Path $env:JAVA_HOME 'bin\java.exe'
        if (Test-Path -LiteralPath $java) { return $java }
    }

    $jdkFolders = Get-ChildItem -Path "$env:ProgramFiles\Java\jdk-*", "$env:USERPROFILE\.jdks\*" `
        -Directory -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending
    foreach ($folder in $jdkFolders) {
        $java = Join-Path $folder.FullName 'bin\java.exe'
        if (Test-Path -LiteralPath $java) { return $java }
    }

    $command = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    throw 'Install JDK 21 or newer and set JAVA_HOME.'
}

function Invoke-Checked($Executable, $Arguments) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$(Split-Path $Executable -Leaf) exited with code $LASTEXITCODE." }
}

function Get-TaskFlowProcess {
    if (!(Test-Path -LiteralPath $processFile)) { return }

    $record = Get-Content -LiteralPath $processFile -Raw | ConvertFrom-Json
    $process = Get-Process -Id $record.id -ErrorAction SilentlyContinue
    if ($process -and $process.StartTime.ToFileTimeUtc().ToString() -eq $record.started) {
        return $process
    }
}

function Test-DatabaseRunning {
    if (!(Test-Path -LiteralPath $pgCtl) -or !(Test-Path -LiteralPath (Join-Path $data 'PG_VERSION'))) {
        return $false
    }

    & $pgCtl status -D $data *> $null
    return $LASTEXITCODE -eq 0
}

function Test-ServerReady {
    try {
        $response = Invoke-WebRequest -Uri "http://127.0.0.1:$webPort/login" -UseBasicParsing -TimeoutSec 2
        return $response.StatusCode -eq 200
    } catch {
        return $false
    }
}

function Show-Addresses {
    Write-Host "TaskFlow is running at http://localhost:$webPort"
    $lan = Get-NetIPConfiguration -ErrorAction SilentlyContinue |
        Where-Object { $_.IPv4DefaultGateway } |
        ForEach-Object { $_.IPv4Address.IPAddress } |
        Where-Object { $_ -match '^(192\.168\.|10\.|172\.(1[6-9]|2[0-9]|3[01])\.)' } |
        Select-Object -Unique
    foreach ($address in $lan) {
        Write-Host "Android on the same Wi-Fi: http://${address}:$webPort"
    }
    Write-Host 'To stop: start-taskflow.cmd stop'
}

New-Item -Path $runtime -ItemType Directory -Force | Out-Null
$lock = [IO.File]::Open((Join-Path $runtime 'launcher.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
try {
    $server = Get-TaskFlowProcess
    if ($Action -eq 'stop') {
        if ($server) { $server | Stop-Process -PassThru | Wait-Process }
        if (Test-Path -LiteralPath $processFile) { Remove-Item -LiteralPath $processFile }
        if (Test-DatabaseRunning) {
            Invoke-Checked $pgCtl @('stop', '-D', $data, '-m', 'fast', '-w')
        }
        Write-Host 'TaskFlow is stopped. Your database is saved.'
        return
    }

    if ($server -and (Test-ServerReady)) {
        Show-Addresses
        return
    }
    if ($server) { throw "TaskFlow is running but /login is unavailable. Check $serverLog" }
    if (Test-ServerReady) { throw "Port $webPort is already serving /login. Stop the other server first." }

    $java = Find-Java
    if (!(Test-Path -LiteralPath $jar)) {
        $env:JAVA_HOME = Split-Path (Split-Path $java -Parent) -Parent
        Invoke-Checked (Join-Path $root 'mvnw.cmd') @('-f', (Join-Path $root 'pom.xml'), '-DskipTests', 'package')
    }

    if (!(Test-Path -LiteralPath $pgCtl)) {
        $npm = Get-Command npm.cmd -ErrorAction SilentlyContinue
        if (!$npm) { throw 'Install Node.js for the first PostgreSQL setup, or use an existing PostgreSQL server from IntelliJ.' }
        Invoke-Checked $npm.Source @('install', '--prefix', $runtime, '--ignore-scripts', '--no-audit',
            '--no-fund', '--no-save', '--package-lock=false', '@embedded-postgres/windows-x64@18.6.0-beta.19')
    }

    if (!(Test-Path -LiteralPath (Join-Path $data 'PG_VERSION'))) {
        if (!(Test-Path -LiteralPath $passwordFile)) {
            $random = [Security.Cryptography.RandomNumberGenerator]::Create()
            try {
                $bytes = New-Object byte[] 32
                $random.GetBytes($bytes)
                [IO.File]::WriteAllText($passwordFile, [Convert]::ToBase64String($bytes))
            } finally {
                $random.Dispose()
            }
        }

        Invoke-Checked (Join-Path $bin 'initdb.exe') @('-D', $data, '-U', 'taskflow', '-A', 'scram-sha-256',
            '--encoding=UTF8', '--locale=C', '--locale-provider=builtin', '--builtin-locale=C.UTF-8',
            "--pwfile=$passwordFile")
    }

    if (!(Test-Path -LiteralPath $databaseReady)) {
        $postgres = Join-Path $bin 'postgres.exe'
        # PostgreSQL also writes normal checkpoint messages to stderr.
        try {
            $ErrorActionPreference = 'Continue'
            $output = 'CREATE DATABASE taskflow_db;' | & $postgres --single -D $data postgres 2>&1 | Out-String
            $exitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = 'Stop'
        }
        $alreadyCreated = $output -match 'database "taskflow_db" already exists'
        if ($exitCode -ne 0 -or ($output -match 'ERROR:' -and !$alreadyCreated)) {
            throw "Could not create taskflow_db. $output"
        }
        New-Item -Path $databaseReady -ItemType File -Force | Out-Null
    }

    if (!(Test-DatabaseRunning)) {
        Invoke-Checked $pgCtl @('start', '-D', $data, '-l', (Join-Path $runtime 'postgres.log'),
            '-o', "-h 127.0.0.1 -p $dbPort", '-w')
    }

    $previousPassword = $env:SPRING_DATASOURCE_PASSWORD
    try {
        $env:SPRING_DATASOURCE_PASSWORD = [IO.File]::ReadAllText($passwordFile)
        $arguments = @('-jar', ('"' + $jar + '"'), "--server.port=$webPort", '--server.address=0.0.0.0',
            "--spring.datasource.url=jdbc:postgresql://127.0.0.1:$dbPort/taskflow_db",
            '--spring.datasource.username=taskflow', '--server.servlet.session.cookie.secure=false',
            '--server.forward-headers-strategy=none')
        $server = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $root -WindowStyle Hidden `
            -RedirectStandardOutput $serverLog -RedirectStandardError $errorLog -PassThru
    } finally {
        $env:SPRING_DATASOURCE_PASSWORD = $previousPassword
    }

    @{ id = $server.Id; started = $server.StartTime.ToFileTimeUtc().ToString() } |
        ConvertTo-Json -Compress | Set-Content -LiteralPath $processFile

    $deadline = (Get-Date).AddSeconds(75)
    while ((Get-Date) -lt $deadline) {
        if ($server.HasExited) { throw "TaskFlow exited. See $serverLog and $errorLog" }
        if (Test-ServerReady) {
            Show-Addresses
            return
        }
        Start-Sleep -Milliseconds 500
    }
    throw "TaskFlow did not respond in time. See $serverLog and $errorLog"
} finally {
    $lock.Dispose()
}
