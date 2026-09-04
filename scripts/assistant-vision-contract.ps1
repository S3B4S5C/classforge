param(
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $repo 'backend'

Push-Location $backend
try {
    if ($Clean) {
        & .\gradlew.bat clean
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }

    & .\gradlew.bat test `
        --tests 'com.classforge.assistant.vision.AssistantImageInputValidatorTests' `
        --tests 'com.classforge.assistant.vision.VisionProposalGroundingValidatorTests' `
        --tests 'com.classforge.assistant.vision.VisionProposalCompilerTests' `
        --tests 'com.classforge.assistant.vision.VisionEvidenceBoundsValidatorTests' `
        --tests 'com.classforge.assistant.vision.LlamaCppVisionModelGatewayTests' `
        --tests 'com.classforge.assistant.vision.AssistantImagePlanIntegrationTests'

    exit $LASTEXITCODE
} finally {
    Pop-Location
}
