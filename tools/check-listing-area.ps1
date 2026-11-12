$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'java-env.ps1')
$root=Split-Path -Parent $PSScriptRoot
$name='uninest_area_check_'+[Guid]::NewGuid().ToString('N').Substring(0,12)
$mysql='C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$localPassword=$env:UNINEST_LOCAL_DB_PASSWORD
if(-not $localPassword){$localPassword=$env:UNINEST_DB_PASSWORD}
$saved=@{}
foreach($key in @('MYSQL_PWD','UNINEST_DB_URL','UNINEST_DB_USER','UNINEST_DB_PASSWORD','UNINEST_UPLOAD_DIR')){$saved[$key]=[Environment]::GetEnvironmentVariable($key)}
try {
    $env:MYSQL_PWD=$localPassword
    # This URL is exclusively for the disposable loopback database, not production.
    $env:UNINEST_DB_URL="jdbc:mysql://127.0.0.1:3306/${name}?serverTimezone=UTC&sslMode=DISABLED&allowPublicKeyRetrieval=true&connectTimeout=5000&socketTimeout=30000"
    $env:UNINEST_DB_USER='root'
    $env:UNINEST_DB_PASSWORD=$localPassword
    $env:UNINEST_UPLOAD_DIR=Join-Path $root "test-runtime\$name\uploads"
    $schema=Get-Content -LiteralPath "$root\Database\Uninest_DB.sql" -Raw
    $schema=$schema.Replace('CREATE DATABASE IF NOT EXISTS uninest_db','CREATE DATABASE IF NOT EXISTS '+$name).Replace('USE uninest_db;','USE '+$name+';')
    # Reconstruct the old schema, seed existing records, then test the actual additive migration twice.
    $schema=$schema.Replace(' area VARCHAR(100) NULL DEFAULT NULL,','')
    $schema | & $mysql --host=127.0.0.1 --port=3306 --user=root
    if($LASTEXITCODE -ne 0){throw 'Disposable schema setup failed'}
    $cp="$root\target\classes;$root\target\test-classes;$root\target\uninest\WEB-INF\lib\*;$root\.m2\repository\javax\servlet\javax.servlet-api\4.0.1\javax.servlet-api-4.0.1.jar"
    & "$env:JAVA_HOME\bin\java.exe" -ea -cp $cp com.uninest.ListingAreaChecks seed
    if($LASTEXITCODE -ne 0){throw 'Area fixtures failed'}
    foreach($pass in 1..2){
        Get-Content -LiteralPath "$root\Database\migrations\004-listing-area.sql" -Raw | & $mysql --host=127.0.0.1 --port=3306 --user=root "--database=$name"
        if($LASTEXITCODE -ne 0){throw 'Area migration failed'}
    }
    & "$env:JAVA_HOME\bin\java.exe" -ea -cp $cp com.uninest.ListingAreaChecks verify
    if($LASTEXITCODE -ne 0){throw 'Area verification failed'}
    & "$PSScriptRoot\check-landlord-http.ps1" -TestDatabase $name -ListingArea
    & "$env:JAVA_HOME\bin\java.exe" -ea -cp $cp com.uninest.ListingAreaChecks after
    if($LASTEXITCODE -ne 0){throw 'Post-browser preservation check failed'}
    Write-Output "Disposable area database retained: $name"
} finally {foreach($key in $saved.Keys){[Environment]::SetEnvironmentVariable($key,$saved[$key])}}
