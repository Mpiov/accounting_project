param(
    [ValidateSet("qwen3_1_7b_q8_0", "qwen3_0_6b_q8_0")]
    [string]$ModelId = "qwen3_1_7b_q8_0",
    [string]$OutputRoot = "E:\accounting_project\models"
)

$ErrorActionPreference = "Stop"

function Get-ModelSpec {
    param(
        [string]$Id
    )

    switch ($Id) {
        "qwen3_1_7b_q8_0" {
            return @{
                Name = "Qwen3-1.7B-Q8_0.gguf"
                Url = "https://www.modelscope.cn/api/v1/models/Qwen/Qwen3-1.7B-GGUF/repo?Revision=master&FilePath=Qwen3-1.7B-Q8_0.gguf"
                Folder = "qwen3_1_7b"
            }
        }
        "qwen3_0_6b_q8_0" {
            return @{
                Name = "Qwen3-0.6B-Q8_0.gguf"
                Url = "https://www.modelscope.cn/api/v1/models/Qwen/Qwen3-0.6B-GGUF/repo?Revision=master&FilePath=Qwen3-0.6B-Q8_0.gguf"
                Folder = "qwen3_0_6b"
            }
        }
        default {
            throw "未知模型标识：$Id"
        }
    }
}

<#
用途说明：
- 将推荐的 Qwen3 GGUF 模型下载到非 C 盘目录，便于本地调试和后续接入真实运行时。
- 当前脚本优先面向开发机下载，不修改任何仓库跟踪文件。

参数说明：
- ModelId：要下载的模型标识。
- OutputRoot：下载根目录，默认放在 E:\accounting_project\models。
#>
$spec = Get-ModelSpec -Id $ModelId
$targetDir = Join-Path $OutputRoot $spec.Folder
$targetFile = Join-Path $targetDir $spec.Name

New-Item -ItemType Directory -Force -Path $targetDir | Out-Null

if (Test-Path -LiteralPath $targetFile) {
    Write-Host "模型已存在：$targetFile"
    exit 0
}

Write-Host "开始下载 $($spec.Name) 到 $targetFile"
curl.exe -L -o $targetFile $spec.Url
if ($LASTEXITCODE -ne 0) {
    throw "模型下载失败：$($spec.Name)"
}

Write-Host "下载完成：$targetFile"
