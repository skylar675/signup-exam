$ErrorActionPreference = "Continue"
Set-Location -LiteralPath $PSScriptRoot

$env:JAVA_HOME = "D:\机试\_tools\jdk\jdk-21.0.12.1+1"
$env:Path = "$env:JAVA_HOME\bin;D:\apache-maven-3.9.11\bin;" + $env:Path
$env:MAVEN_OPTS = "-Djava.net.preferIPv4Stack=true"
$env:MYSQL_HOST = "127.0.0.1"
$env:MYSQL_PORT = "3306"
$env:MYSQL_DATABASE = "signup_exam"
$env:MYSQL_USER = "signup_dev"

function Test-App {
    try {
        $response = Invoke-WebRequest -Uri "http://127.0.0.1:8080/" -UseBasicParsing -TimeoutSec 2
        return $response.StatusCode -ge 200
    } catch {
        return $false
    }
}

if (Test-App) {
    Write-Host "应用已在运行。正在打开页面。"
    Start-Process "http://127.0.0.1:8080/"
    Write-Host "关闭本窗口不会停止已经运行的应用。"
    Read-Host "按回车关闭"
    exit 0
}

Write-Host "请输入 signup_dev 的密码。输入时不会显示，也不会写入项目。"
$secure = Read-Host "MYSQL_PASSWORD" -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $env:MYSQL_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
}
if ([string]::IsNullOrEmpty($env:MYSQL_PASSWORD)) {
    Write-Host "密码为空，已取消。"
    Read-Host "按回车关闭"
    exit 2
}

Write-Host "正在启动。看到 Started SignupApplication 后即可使用。"
Write-Host "页面地址：http://127.0.0.1:8080/"
Write-Host "关闭本窗口会停止应用。"

Start-Job -ScriptBlock {
    for ($i = 0; $i -lt 40; $i++) {
        try {
            Invoke-WebRequest -Uri "http://127.0.0.1:8080/" -UseBasicParsing -TimeoutSec 2 | Out-Null
            Start-Process "http://127.0.0.1:8080/"
            break
        } catch {
            Start-Sleep -Seconds 2
        }
    }
} | Out-Null

& "$PSScriptRoot\mvnw.cmd" spring-boot:run
$code = $LASTEXITCODE
Remove-Item Env:MYSQL_PASSWORD -ErrorAction SilentlyContinue
Write-Host "应用已退出，退出码 $code"
Read-Host "按回车关闭"
exit $code
