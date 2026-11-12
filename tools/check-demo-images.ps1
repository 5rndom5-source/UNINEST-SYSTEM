$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'java-env.ps1')
$root=Split-Path -Parent $PSScriptRoot
$name='uninest_demo_image_check_'+[Guid]::NewGuid().ToString('N').Substring(0,12)
$mysql='C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$password=$env:UNINEST_LOCAL_DB_PASSWORD
if(-not $password){$password=$env:UNINEST_DB_PASSWORD}
$saved=@{}
foreach($key in @('MYSQL_PWD','UNINEST_DB_URL','UNINEST_DB_USER','UNINEST_DB_PASSWORD','UNINEST_UPLOAD_DIR','UNINEST_DEMO_IMAGE_FALLBACK','UNINEST_PUBLIC_URL','UNINEST_TEST_BASE','UNINEST_DEMO_TEST_MODE','NODE_PATH')){$saved[$key]=[Environment]::GetEnvironmentVariable($key)}
$process=$null
try {
    $env:MYSQL_PWD=$password
    $schema=Get-Content -LiteralPath "$root\Database\Uninest_DB.sql" -Raw
    $schema=$schema.Replace('CREATE DATABASE IF NOT EXISTS uninest_db','CREATE DATABASE IF NOT EXISTS '+$name).Replace('USE uninest_db;','USE '+$name+';')
    $schema | & $mysql --host=127.0.0.1 --port=3306 --user=root
    if($LASTEXITCODE -ne 0){throw 'Disposable schema creation failed'}
    # Only a disposable, hardcoded loopback database; never production configuration.
    $url="jdbc:mysql://127.0.0.1:3306/${name}?serverTimezone=UTC&sslMode=DISABLED&allowPublicKeyRetrieval=true&connectTimeout=2000&socketTimeout=30000"
    $env:UNINEST_DB_URL=$url;$env:UNINEST_DB_USER='root';$env:UNINEST_DB_PASSWORD=$password
    $env:UNINEST_UPLOAD_DIR=Join-Path $root "test-runtime\$name\uploads"
    $cp="$root\target\classes;$root\target\test-classes;$root\target\uninest\WEB-INF\lib\*;$root\.m2\repository\javax\servlet\javax.servlet-api\4.0.1\javax.servlet-api-4.0.1.jar"
    & "$env:JAVA_HOME\bin\java.exe" -ea -cp $cp com.uninest.DemoImageChecks seed
    if($LASTEXITCODE -ne 0){throw 'Demo fixtures failed'}
    $tomcatHome=Join-Path $root 'tools\apache-tomcat-9.0.118'
    $env:NODE_PATH=Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\node\node_modules'
    $node=Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
    foreach($mode in @('disabled','enabled','database-failure')) {
        $base=Join-Path $root "test-runtime\$name\$mode"
        foreach($dir in @('conf','logs','temp','webapps','work')){New-Item -ItemType Directory -Force -Path (Join-Path $base $dir) | Out-Null}
        Copy-Item -Path "$tomcatHome\conf\*" -Destination "$base\conf" -Recurse -Force
        $listener=[System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback,0);$listener.Start();$port=$listener.LocalEndpoint.Port;$listener.Stop()
        [xml]$config=Get-Content -LiteralPath "$base\conf\server.xml"
        $config.Server.SetAttribute('port','-1');$connector=$config.SelectSingleNode('//Connector');$connector.SetAttribute('port',"$port");$connector.SetAttribute('address','127.0.0.1');$config.Save("$base\conf\server.xml")
        Copy-Item -LiteralPath "$root\target\uninest.war" -Destination "$base\webapps\uninest.war"
        $env:UNINEST_DEMO_IMAGE_FALLBACK=if($mode -eq 'disabled'){'false'}else{'true'}
        $env:UNINEST_DB_URL=if($mode -eq 'database-failure'){$url.Replace($name,$name+'_absent')}else{$url}
        $env:UNINEST_PUBLIC_URL="http://127.0.0.1:$port/uninest";$env:UNINEST_TEST_BASE=$env:UNINEST_PUBLIC_URL;$env:UNINEST_DEMO_TEST_MODE=$mode
        try {
            $arguments=@("-Dcatalina.home=`"$tomcatHome`"","-Dcatalina.base=`"$base`"",'-cp',"`"$tomcatHome\bin\bootstrap.jar;$tomcatHome\bin\tomcat-juli.jar`"",'org.apache.catalina.startup.Bootstrap','start')
            $process=Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" -ArgumentList $arguments -PassThru -WindowStyle Hidden -RedirectStandardOutput "$base\logs\stdout.log" -RedirectStandardError "$base\logs\stderr.log"
            $ready=$false
            for($i=0;$i -lt 90;$i++){try{$r=Invoke-WebRequest "$env:UNINEST_TEST_BASE/CsrfTokenServlet" -UseBasicParsing -TimeoutSec 2;if($r.StatusCode -eq 200){$ready=$true;break}}catch{};if($process.HasExited){throw 'Test server exited'};Start-Sleep -Seconds 1}
            if(-not $ready){throw 'Isolated server not ready'}
            & $node "$PSScriptRoot\check-demo-images-browser.cjs"
            if($LASTEXITCODE -ne 0){throw "Demo image checks failed: $mode"}
            if($mode -ne 'database-failure' -and (Select-String -Path "$base\logs\stderr.log" -Pattern 'SEVERE|Exception' -Quiet)){throw 'Unexpected test runtime error'}
        } finally {if($process -and -not $process.HasExited){Stop-Process -Id $process.Id};$process=$null}
    }
    $env:UNINEST_DB_URL=$url
    & "$env:JAVA_HOME\bin\java.exe" -ea -cp $cp com.uninest.DemoImageChecks verify
    if($LASTEXITCODE -ne 0){throw 'Demo packaging/preservation checks failed'}
    Write-Output "PASS: isolated test databases/files retained at test-runtime\$name; production untouched"
} finally {if($process -and -not $process.HasExited){Stop-Process -Id $process.Id};foreach($key in $saved.Keys){[Environment]::SetEnvironmentVariable($key,$saved[$key])}}
