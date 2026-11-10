param([Parameter(Mandatory=$true)][string]$TestDatabase)
$ErrorActionPreference='Stop'
if($TestDatabase -notmatch '^uninest_landlord_check_[a-f0-9]{12}$' -or $env:UNINEST_DB_URL -notlike "jdbc:mysql://127.0.0.1:3306/$TestDatabase`?*"){throw 'Disposable loopback database required'}
. (Join-Path $PSScriptRoot 'java-env.ps1')
$root=Split-Path -Parent $PSScriptRoot
$tomcatHome=Join-Path $root 'tools\apache-tomcat-9.0.118'
$base=Join-Path $root "test-runtime\$TestDatabase\tomcat"
foreach($dir in @('conf','logs','temp','webapps','work')){New-Item -ItemType Directory -Force -Path (Join-Path $base $dir) | Out-Null}
Copy-Item -Path "$tomcatHome\conf\*" -Destination "$base\conf" -Recurse -Force
$listener=[System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback,0)
$listener.Start()
$port=$listener.LocalEndpoint.Port
$listener.Stop()
[xml]$config=Get-Content -LiteralPath "$base\conf\server.xml"
$config.Server.SetAttribute('port','-1')
$connector=$config.SelectSingleNode('//Connector')
$connector.SetAttribute('port',"$port")
$connector.SetAttribute('address','127.0.0.1')
$config.Save("$base\conf\server.xml")
Copy-Item -LiteralPath "$root\target\uninest.war" -Destination "$base\webapps\uninest.war"
$oldPublic=$env:UNINEST_PUBLIC_URL
$oldBase=$env:UNINEST_TEST_BASE
$oldTestUrl=$env:UNINEST_TEST_URL
$oldNode=$env:NODE_PATH
$process=$null
try {
    $env:UNINEST_PUBLIC_URL="http://127.0.0.1:$port/uninest"
    $env:UNINEST_TEST_BASE=$env:UNINEST_PUBLIC_URL
    $env:UNINEST_TEST_URL=$env:UNINEST_PUBLIC_URL
    $env:NODE_PATH=Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\node\node_modules'
    $args=@("-Dcatalina.home=`"$tomcatHome`"","-Dcatalina.base=`"$base`"",'-cp',"`"$tomcatHome\bin\bootstrap.jar;$tomcatHome\bin\tomcat-juli.jar`"",'org.apache.catalina.startup.Bootstrap','start')
    $process=Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" -ArgumentList $args -PassThru -WindowStyle Hidden -RedirectStandardOutput "$base\logs\stdout.log" -RedirectStandardError "$base\logs\stderr.log"
    $ready=$false
    for($i=0;$i -lt 90;$i++) {
        try { $r=Invoke-WebRequest "$env:UNINEST_TEST_BASE/CsrfTokenServlet" -UseBasicParsing -TimeoutSec 2; if($r.StatusCode -eq 200){$ready=$true;break} } catch { }
        if($process.HasExited){throw 'Isolated Tomcat exited before startup'}
        Start-Sleep -Seconds 1
    }
    if(-not $ready){throw 'Isolated Tomcat did not become ready'}
    $node=Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
    & $node "$PSScriptRoot\check-landlord-browser.cjs"
    if($LASTEXITCODE -ne 0){throw 'Landlord browser checks failed'}
    & $node "$PSScriptRoot\check-csrf-browser.cjs"
    if($LASTEXITCODE -ne 0){throw 'CSRF browser checks failed'}
    & "$PSScriptRoot\check-signout-http.ps1" -BaseUrl $env:UNINEST_TEST_BASE
    & "$PSScriptRoot\check-login-throttle-http.ps1" -BaseUrl $env:UNINEST_TEST_BASE
    if(Select-String -Path "$base\logs\stderr.log" -Pattern 'SEVERE|Exception' -Quiet){throw 'Unexpected error in isolated runtime log'}
    Write-Output "PASS: isolated runtime log has no SEVERE/Exception entries ($TestDatabase)"
} finally {
    if($process -and -not $process.HasExited){Stop-Process -Id $process.Id}
    $env:UNINEST_PUBLIC_URL=$oldPublic
    $env:UNINEST_TEST_BASE=$oldBase
    $env:UNINEST_TEST_URL=$oldTestUrl
    $env:NODE_PATH=$oldNode
}
