$root = Split-Path -Parent $PSScriptRoot

$preferredJdk = "C:\Program Files\Eclipse Adoptium\jdk-11.0.26.4-hotspot"
if (Test-Path -LiteralPath (Join-Path $preferredJdk "bin\java.exe")) {
    $env:JAVA_HOME = $preferredJdk
}

$env:MAVEN_HOME = Join-Path $PSScriptRoot "apache-maven-3.9.16"
$env:CATALINA_HOME = Join-Path $PSScriptRoot "apache-tomcat-9.0.118"
$env:CATALINA_BASE = $env:CATALINA_HOME
$env:MAVEN_OPTS = "-Dfile.encoding=UTF-8"

$javaBin = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin" } else { "" }
$mavenBin = Join-Path $env:MAVEN_HOME "bin"
$tomcatBin = Join-Path $env:CATALINA_HOME "bin"
$mysqlBin = "C:\Program Files\MySQL\MySQL Server 8.0\bin"

function Add-PathFirst($pathToAdd) {
    if (-not $pathToAdd) {
        return
    }

    $parts = $env:Path -split ";" | Where-Object {
        $_ -and $_.Trim() -and ($_.Trim().ToLowerInvariant() -ne $pathToAdd.ToLowerInvariant())
    }
    $env:Path = @($pathToAdd) + $parts -join ";"
}

Add-PathFirst $javaBin
Add-PathFirst $mavenBin
Add-PathFirst $tomcatBin
if (Test-Path -LiteralPath (Join-Path $mysqlBin "mysql.exe")) {
    Add-PathFirst $mysqlBin
}
