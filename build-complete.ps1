# Complete JNode Build: Maven + Ant

Write-Host ""
Write-Host "=========================================="
Write-Host "JNode Complete Build (Maven + Ant)"
Write-Host "=========================================="
Write-Host ""

# Find Java
if (-not $env:JAVA_HOME) {
    $jdkPath = "$env:USERPROFILE\.jdks\corretto-1.8.0_482"
    if (Test-Path "$jdkPath\bin\java.exe") {
        $env:JAVA_HOME = $jdkPath
    }
}

if ($env:JAVA_HOME) {
    Write-Host "JAVA_HOME: $env:JAVA_HOME"
    $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
}
Write-Host ""

# Step 1: Maven
Write-Host "=========================================="
Write-Host "Step 1: Maven Compilation"
Write-Host "=========================================="
Write-Host ""

mvn clean install -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "ERROR: Maven failed!"
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host ""
Write-Host "Maven finished successfully!"
Write-Host ""

# Step 2: Ant
Write-Host "=========================================="
Write-Host "Step 2: Ant ISO Generation"
Write-Host "=========================================="
Write-Host ""

# Check if build.bat exists
if (-not (Test-Path "build.bat")) {
    Write-Host "ERROR: build.bat not found!"
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host "Running: build.bat cd-x86-lite"
Write-Host ""

& cmd /c "build.bat cd-x86-lite"
$antExitCode = $LASTEXITCODE

Write-Host ""
Write-Host "Ant finished with code: $antExitCode"
Write-Host ""

if ($antExitCode -ne 0) {
    Write-Host "ERROR: Ant failed!"
    Read-Host "Press Enter to exit"
    exit 1
}

# Step 3: Verify ISO
Write-Host "=========================================="
Write-Host "Step 3: Verify ISO"
Write-Host "=========================================="
Write-Host ""

if (Test-Path "all\build\cdroms\jnode-x86-lite.iso") {
    Write-Host "SUCCESS! ISO created:"
    Write-Host ""
    Get-Item "all\build\cdroms\jnode-x86-lite.iso" | Format-Table Name, @{Name="Size (MB)"; Expression={[math]::Round($_.Length / 1MB, 2)}}
} else {
    Write-Host "ERROR: ISO not found!"
    Write-Host "Directory contents:"
    Get-ChildItem "all\build\cdroms\" -ErrorAction SilentlyContinue | Format-Table Name, Length
}

Write-Host ""
Read-Host "Press Enter to exit"
