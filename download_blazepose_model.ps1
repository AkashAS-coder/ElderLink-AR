# PowerShell script to download BlazePose model
$modelUrl = "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_heavy/float16/1/pose_landmarker_heavy.task"
$outputDir = "app\src\main\assets"
$outputFile = "$outputDir\pose_landmarker_heavy.task"

# Create assets directory if it doesn't exist
if (-not (Test-Path -Path $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir | Out-Null
    Write-Host "Created directory: $outputDir"
}

# Download the model file
Write-Host "Downloading BlazePose model (heavy) from $modelUrl..."
Invoke-WebRequest -Uri $modelUrl -OutFile $outputFile

# Verify download
if (Test-Path -Path $outputFile) {
    $fileSize = (Get-Item $outputFile).Length / 1MB
    Write-Host "Successfully downloaded BlazePose model to $outputFile (${[math]::Round($fileSize, 2)} MB)"
} else {
    Write-Error "Failed to download the model file. Please check your internet connection and try again."
}
