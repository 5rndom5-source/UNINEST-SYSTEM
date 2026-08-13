$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$tools = $PSScriptRoot
$downloads = Join-Path $tools "downloads"

New-Item -ItemType Directory -Force -Path $downloads | Out-Null

$packages = @(
    @{
        Name = "Apache Maven"
        Version = "3.9.16"
        Url = "https://dlcdn.apache.org/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip"
        ShaUrl = "https://downloads.apache.org/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip.sha512"
        ZipName = "apache-maven-3.9.16-bin.zip"
        Target = "apache-maven-3.9.16"
    },
    @{
        Name = "Apache Tomcat"
        Version = "9.0.118"
        Url = "https://dlcdn.apache.org/tomcat/tomcat-9/v9.0.118/bin/apache-tomcat-9.0.118-windows-x64.zip"
        ShaUrl = "https://downloads.apache.org/tomcat/tomcat-9/v9.0.118/bin/apache-tomcat-9.0.118-windows-x64.zip.sha512"
        ZipName = "apache-tomcat-9.0.118-windows-x64.zip"
        Target = "apache-tomcat-9.0.118"
    }
)

foreach ($package in $packages) {
    $zipPath = Join-Path $downloads $package.ZipName
    $shaPath = "$zipPath.sha512"
    $targetPath = Join-Path $tools $package.Target

    if (-not (Test-Path -LiteralPath $zipPath)) {
        Write-Host "Downloading $($package.Name) $($package.Version)..."
        Invoke-WebRequest -Uri $package.Url -OutFile $zipPath
    }

    if (-not (Test-Path -LiteralPath $shaPath)) {
        Write-Host "Downloading checksum for $($package.Name)..."
        Invoke-WebRequest -Uri $package.ShaUrl -OutFile $shaPath
    }

    $expected = ((Get-Content -LiteralPath $shaPath -Raw) -split "\s+")[0].ToLowerInvariant()
    $actual = (Get-FileHash -LiteralPath $zipPath -Algorithm SHA512).Hash.ToLowerInvariant()

    if ($expected -ne $actual) {
        throw "Checksum mismatch for $($package.ZipName)"
    }

    if (-not (Test-Path -LiteralPath $targetPath)) {
        Write-Host "Extracting $($package.Name)..."
        Expand-Archive -LiteralPath $zipPath -DestinationPath $tools -Force
    }
}

Write-Host "Java stack installed under $tools"
Write-Host "Use .\tools\run-uninest.ps1 to build and start Tomcat."
