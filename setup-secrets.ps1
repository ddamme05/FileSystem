# Setup local secrets (PowerShell) - ASCII only, no emojis, no 'else'

Write-Host "Setting up local secrets..."

$secretsDir = ".\.secrets"
if (-not (Test-Path $secretsDir)) {
  New-Item -ItemType Directory -Force $secretsDir | Out-Null
}

function Write-SecretFile { param([string]$Name,[string]$Value)
  $path = Join-Path $secretsDir $Name
  Set-Content -Path $path -Value $Value -NoNewline
  Write-Host ("Wrote " + $Name)
}

# 1) Datadog API Key (secure prompt)
$path = Join-Path $secretsDir "dd_api_key"
if (Test-Path $path) { Write-Host "dd_api_key already exists" }
if (-not (Test-Path $path)) {
  $sec = Read-Host -Prompt 'Datadog API Key' -AsSecureString
  $b = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec)
  try { $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($b) }
  finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($b) }
  Write-SecretFile -Name "dd_api_key" -Value $plain
}

# 2) dd_site
$path = Join-Path $secretsDir "dd_site"
if (Test-Path $path) { Write-Host "dd_site already exists" }
if (-not (Test-Path $path)) {
  $val = Read-Host -Prompt 'Datadog Site [us5.datadoghq.com]'
  if ([string]::IsNullOrWhiteSpace($val)) { $val = "us5.datadoghq.com" }
  Write-SecretFile -Name "dd_site" -Value $val
}

# 3) db_password
$path = Join-Path $secretsDir "db_password"
if (Test-Path $path) { Write-Host "db_password already exists" }
if (-not (Test-Path $path)) {
  $val = Read-Host -Prompt 'Database Password [password]'
  if ([string]::IsNullOrWhiteSpace($val)) { $val = "password" }
  Write-SecretFile -Name "db_password" -Value $val
}

# 4) dd_pg_password
$path = Join-Path $secretsDir "dd_pg_password"
if (Test-Path $path) { Write-Host "dd_pg_password already exists" }
if (-not (Test-Path $path)) {
  $val = Read-Host -Prompt 'Datadog Postgres Monitoring Password [strong-datadog-password]'
  if ([string]::IsNullOrWhiteSpace($val)) { $val = "strong-datadog-password" }
  Write-SecretFile -Name "dd_pg_password" -Value $val
}

# 5) jwt_secret (generate once)
$path = Join-Path $secretsDir "jwt_secret"
if (Test-Path $path) { Write-Host "jwt_secret already exists" }
if (-not (Test-Path $path)) {
  $bytes = New-Object byte[] 32
  [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
  $jwt = [Convert]::ToBase64String($bytes)
  Write-SecretFile -Name "jwt_secret" -Value $jwt
}

# 6) aws_s3_bucket
$path = Join-Path $secretsDir "aws_s3_bucket"
if (Test-Path $path) { Write-Host "aws_s3_bucket already exists" }
if (-not (Test-Path $path)) {
  $val = Read-Host -Prompt 'AWS S3 Bucket Name [filesystem-s3]'
  if ([string]::IsNullOrWhiteSpace($val)) { $val = "filesystem-s3" }
  Write-SecretFile -Name "aws_s3_bucket" -Value $val
}

# 7) aws_region
$path = Join-Path $secretsDir "aws_region"
if (Test-Path $path) { Write-Host "aws_region already exists" }
if (-not (Test-Path $path)) {
  $val = Read-Host -Prompt 'AWS Region [us-east-1]'
  if ([string]::IsNullOrWhiteSpace($val)) { $val = "us-east-1" }
  Write-SecretFile -Name "aws_region" -Value $val
}

Write-Host ""
Write-Host "Local secrets setup complete (created in .\.secrets)"
