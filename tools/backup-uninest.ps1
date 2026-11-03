#requires -Version 7.0
param([switch]$WritesPaused)
$ErrorActionPreference = 'Stop'
if (-not $WritesPaused) { throw 'Pause application writes first, then pass -WritesPaused. Database and files need a consistent maintenance window.' }
$root = Split-Path -Parent $PSScriptRoot
$uri = [Uri]($env:UNINEST_DB_URL -replace '^jdbc:', '')
$database = $uri.AbsolutePath.Trim('/')
if ($database -notmatch '^[A-Za-z0-9_]+$') { throw 'Invalid configured database name' }
$mysqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin'
$destination = Join-Path $root ('test-runtime\backups\' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8))
$uploads = if ($env:UNINEST_UPLOAD_DIR) { $env:UNINEST_UPLOAD_DIR } else { Join-Path $HOME '.uninest' }
New-Item -ItemType Directory -Path $destination -Force | Out-Null
$oldPassword = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = $env:UNINEST_DB_PASSWORD
    $dump = Join-Path $destination 'database.sql'
    & "$mysqlBin\mysqldump.exe" "--host=$($uri.Host)" "--port=$($uri.Port)" "--user=$env:UNINEST_DB_USER" --single-transaction --quick --hex-blob --no-tablespaces --set-gtid-purged=OFF --default-character-set=utf8mb4 --routines --events --triggers "--result-file=$dump" $database
    if ($LASTEXITCODE -ne 0) { throw 'Database dump failed; incomplete backup must not be used' }
    $connectionArgs = @("--host=$($uri.Host)","--port=$($uri.Port)","--user=$env:UNINEST_DB_USER","--database=$database",'--batch','--skip-column-names')
    $tables = @(& "$mysqlBin\mysql.exe" @connectionArgs --execute='SHOW TABLES')
    if ($LASTEXITCODE -ne 0) { throw 'Could not inventory database tables' }
    $counts = @{}
    foreach ($table in $tables) {
        if ($table -notmatch '^[A-Za-z0-9_]+$') { throw 'Unexpected table name' }
        $count = & "$mysqlBin\mysql.exe" @connectionArgs "--execute=SELECT COUNT(*) FROM ``$table``"
        if ($LASTEXITCODE -ne 0) { throw 'Could not count database records' }
        $counts[$table] = [long]$count
    }
    $files = @()
    foreach ($folder in @('profile-images','property-images','message-attachments')) {
        $source = Join-Path $uploads $folder
        if (-not (Test-Path -LiteralPath $source)) { continue }
        $entries = @((Get-Item -LiteralPath $source)) + @(Get-ChildItem -LiteralPath $source -Force -Recurse)
        if ($entries | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }) { throw 'Upload tree contains a symbolic link or junction; review it before backup' }
        foreach ($file in ($entries | Where-Object { -not $_.PSIsContainer })) {
            $relative = [IO.Path]::GetRelativePath($uploads, $file.FullName)
            $copy = Join-Path (Join-Path $destination 'uploads') $relative
            New-Item -ItemType Directory -Path (Split-Path -Parent $copy) -Force | Out-Null
            Copy-Item -LiteralPath $file.FullName -Destination $copy
            $files += @{ path=$relative; sha256=(Get-FileHash -LiteralPath $copy -Algorithm SHA256).Hash; bytes=$file.Length }
        }
    }
    $manifest = @{ version=1; createdUtc=[DateTime]::UtcNow.ToString('o'); database=$database; databaseSha256=(Get-FileHash -LiteralPath $dump -Algorithm SHA256).Hash; uploads=$files; writesPaused=$true; rowCounts=$counts }
    $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $destination 'manifest.json') -Encoding utf8
    Write-Output "Backup complete: $destination"
    Write-Output "Upload files: $($files.Count). Contains private data; keep outside Git and encrypt off-device copies."
} finally { $env:MYSQL_PWD = $oldPassword }
