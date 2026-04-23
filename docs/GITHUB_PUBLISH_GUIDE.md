# GitHub 上传与干净发布指南

> 这份文档记录 DD_Rag 当前采用的 GitHub 发布流程。核心目标是：不携带旧 Git 历史中的敏感信息，只把当前代码快照以干净历史发布到 GitHub。

## 1. 适用场景

适用于以下情况：

- 原仓库历史里出现过密钥、Token、密码等敏感信息。
- 只想把当前项目状态作为新的 GitHub 首次提交。
- 本地项目文件很多，需要先复制一个精简副本再发布。
- GitHub 远端仓库已经创建，但还没有真正的业务代码。

不适用于以下情况：

- 需要保留完整 Git 历史。
- 需要多人协作保留远端提交。
- 远端仓库已经有重要代码，不能覆盖历史。

## 2. 当前项目发布约定

当前 DD_Rag 使用两个目录：

```text
原始开发仓库：E:\AICode\DD_Rag
干净发布仓库：E:\AICode\DD_Rag_clean
```

GitHub 远端：

```text
https://github.com/t1804330987/DD_Rag.git
git@github.com:t1804330987/DD_Rag.git
```

推荐发布目标是 `E:\AICode\DD_Rag_clean`，不要直接推送 `E:\AICode\DD_Rag` 的旧历史。

原因：

- 原始仓库历史中曾出现过 DashScope API Key。
- 即使当前文件已经删除密钥，旧提交历史仍可能被 GitHub 扫描到。
- clean 仓库只保留当前快照，可以避免把旧提交带上去。

## 3. 发布前准备

### 3.1 创建或更新 clean 目录

如果还没有 clean 目录，可以复制一份精简副本：

```powershell
Copy-Item -Recurse E:\AICode\DD_Rag E:\AICode\DD_Rag_clean
```

复制后必须删除不应发布的目录：

```powershell
Remove-Item -Recurse -Force E:\AICode\DD_Rag_clean\.git
Remove-Item -Recurse -Force E:\AICode\DD_Rag_clean\target
Remove-Item -Recurse -Force E:\AICode\DD_Rag_clean\frontend\node_modules
Remove-Item -Recurse -Force E:\AICode\DD_Rag_clean\frontend\dist
```

如果存在 IDE 或本地工具目录，也应删除：

```powershell
Remove-Item -Recurse -Force E:\AICode\DD_Rag_clean\.idea
Remove-Item -Recurse -Force E:\AICode\DD_Rag_clean\.superpowers
```

注意：删除前先确认目标路径确实是 clean 副本，不能在原始开发仓库误删。

### 3.2 初始化 Git

```powershell
cd E:\AICode\DD_Rag_clean
git init -b main
git remote add origin https://github.com/t1804330987/DD_Rag.git
```

如果已经初始化过，可以检查：

```powershell
git status
git remote -v
```

## 4. 敏感信息扫描

提交前至少做一次简单扫描：

```powershell
Select-String -Path 'E:\AICode\DD_Rag_clean\**\*' `
  -Pattern 'sk-[A-Za-z0-9]' `
  -ErrorAction SilentlyContinue
```

再检查常见敏感字段：

```powershell
Select-String -Path `
  'E:\AICode\DD_Rag_clean\src\main\resources\application-dev.yml',`
  'E:\AICode\DD_Rag_clean\docker-compose.yml',`
  'E:\AICode\DD_Rag_clean\deploy\two-node\*.yml',`
  'E:\AICode\DD_Rag_clean\deploy\two-node\*.example' `
  -Pattern 'DASHSCOPE_API_KEY|api-key|password|secret|token' `
  -CaseSensitive:$false
```

允许出现：

- `${DASHSCOPE_API_KEY}` 这类环境变量引用。
- `your-dashscope-api-key` 这类示例占位符。
- 开发环境默认密码，例如本地 PostgreSQL、MinIO、dev admin。

不允许出现：

- 真实 API Key。
- 真实线上数据库密码。
- 真实 JWT Secret。
- 真实云服务器密钥。
- 私钥文件内容。

如果发现真实密钥已经进入过原始 Git 历史，应到对应平台立即轮换密钥。

## 5. 提交 clean 快照

暂存全部文件：

```powershell
cd E:\AICode\DD_Rag_clean
git add .
git status --short
```

提交：

```powershell
git commit -s -m "feat: publish DD_Rag source and docker deployment" -m "以干净 Git 历史发布当前项目快照，避免把旧提交中的敏感密钥带入 GitHub。本次提交包含前后端源码、Docker Compose 单机部署、双机部署配置、部署记录和项目阅读指南，便于后续在 Linux 服务器上复现安装流程。"
```

提交后检查：

```powershell
git log --oneline -1
git status --short
```

## 6. HTTPS 推送

先确保 remote 是 HTTPS：

```powershell
git remote set-url origin https://github.com/t1804330987/DD_Rag.git
git remote -v
```

推送：

```powershell
git push -u origin main
```

如果出现：

```text
Failed to connect to github.com port 443
Recv failure: Connection was reset
```

说明是网络连接被阻断或重置，不是代码问题。可以配置代理。

常见代理端口 `7890`：

```powershell
git config --global http.proxy http://127.0.0.1:7890
git config --global https.proxy http://127.0.0.1:7890
git push -u origin main
```

常见代理端口 `10809`：

```powershell
git config --global http.proxy http://127.0.0.1:10809
git config --global https.proxy http://127.0.0.1:10809
git push -u origin main
```

如果代理配置错了，清除：

```powershell
git config --global --unset http.proxy
git config --global --unset https.proxy
```

## 7. SSH 推送

### 7.1 查找 Git 自带 SSH

如果系统没有 `ssh` 命令，可以找 Git 自带的 ssh。

本机当前 Git 位置示例：

```text
D:\anzhuang_file\Git\cmd\git.exe
```

对应 SSH 常见路径：

```powershell
& "D:\anzhuang_file\Git\usr\bin\ssh.exe" -T git@github.com
```

如果首次连接出现：

```text
Are you sure you want to continue connecting (yes/no/[fingerprint])?
```

输入：

```text
yes
```

成功时会看到：

```text
Hi t1804330987! You've successfully authenticated, but GitHub does not provide shell access.
```

### 7.2 生成 SSH Key

如果还没有 SSH Key：

```powershell
& "D:\anzhuang_file\Git\usr\bin\ssh-keygen.exe" -t ed25519 -C "1804330987@qq.com"
```

一路回车即可。默认生成：

```text
C:\Users\18043\.ssh\id_ed25519
C:\Users\18043\.ssh\id_ed25519.pub
```

查看公钥：

```powershell
Get-Content $env:USERPROFILE\.ssh\id_ed25519.pub
```

只复制 `.pub` 文件内容。不要复制没有 `.pub` 的私钥文件。

### 7.3 GitHub 添加 SSH 公钥

进入 GitHub：

```text
头像 -> Settings -> SSH and GPG keys -> New SSH key
```

填写：

```text
Title: Windows-DDRag
Key type: Authentication Key
Key: 粘贴 id_ed25519.pub 的整行内容
```

保存后测试：

```powershell
& "D:\anzhuang_file\Git\usr\bin\ssh.exe" -T git@github.com
```

### 7.4 使用 SSH remote 推送

```powershell
cd E:\AICode\DD_Rag_clean
git remote set-url origin git@github.com:t1804330987/DD_Rag.git
git config core.sshCommand "D:/anzhuang_file/Git/usr/bin/ssh.exe"
git push -u origin main
```

如果想全局固定 Git 使用这个 SSH：

```powershell
git config --global core.sshCommand "D:/anzhuang_file/Git/usr/bin/ssh.exe"
```

## 8. 远端已有 Initial commit 的处理

如果推送时报：

```text
! [rejected] main -> main (fetch first)
Updates were rejected because the remote contains work that you do not have locally.
```

先看远端有什么：

```powershell
git ls-remote --heads origin
git fetch origin main --depth=5
git log --oneline --decorate --max-count=5 origin/main
```

如果远端只有 GitHub 创建仓库时生成的 `Initial commit`，而你明确要用 clean 快照作为新历史，可以覆盖远端：

```powershell
git push --force-with-lease -u origin main
```

注意：

- `--force-with-lease` 会改写远端 `main` 历史。
- 只应在确认远端没有重要提交时使用。
- 如果远端已有别人提交的业务代码，不要覆盖，应先沟通或合并。

## 9. 常见错误与处理

### 9.1 `ssh-keygen` 不是命令

现象：

```text
ssh-keygen : 无法将“ssh-keygen”项识别为 cmdlet
```

处理：

```powershell
& "D:\anzhuang_file\Git\usr\bin\ssh-keygen.exe" -t ed25519 -C "1804330987@qq.com"
```

如果路径不存在，安装 Windows OpenSSH Client：

```powershell
Get-WindowsCapability -Online | Where-Object Name -like 'OpenSSH.Client*'
Add-WindowsCapability -Online -Name OpenSSH.Client~~~~0.0.1.0
```

### 9.2 `ssh` 不是命令

现象：

```text
ssh : 无法将“ssh”项识别为 cmdlet
```

处理：

```powershell
& "D:\anzhuang_file\Git\usr\bin\ssh.exe" -T git@github.com
```

### 9.3 HTTPS 连接被重置

现象：

```text
Recv failure: Connection was reset
```

处理：

- 开启代理。
- 配置 Git 代理。
- 或改用 SSH 推送。

### 9.4 推送被拒绝

现象：

```text
fetch first
```

处理：

- 先 `git fetch origin main --depth=5` 查看远端提交。
- 如果远端只有初始化提交，使用 `git push --force-with-lease -u origin main`。
- 如果远端有重要提交，不要覆盖。

## 10. 发布后检查

推送成功后检查：

```powershell
git status --short
git branch -vv
git log --oneline -3
```

打开 GitHub 仓库确认：

```text
https://github.com/t1804330987/DD_Rag
```

重点确认：

- GitHub 上能看到前后端源码。
- 能看到 `docker-compose.yml`。
- 能看到 `deploy/two-node/`。
- 能看到 `docs/DEPLOYMENT_RECORD.md`。
- 能看到 `docs/PROJECT_READING_GUIDE.md`。
- 没有 `.env`、私钥、真实 API Key。

## 11. 后续复用清单

每次发布前按这个清单执行：

1. 确认发布目录是 clean 仓库，不是原始仓库。
2. 确认 `.gitignore` 排除了构建产物、依赖目录和本地配置。
3. 执行敏感信息扫描。
4. 确认 `DASHSCOPE_API_KEY` 等密钥只来自环境变量。
5. `git add .`
6. `git commit -s`
7. 优先 SSH 推送。
8. 如果远端只有初始化提交，才允许 `--force-with-lease`。
9. GitHub 页面人工检查文件。
10. 如果旧历史泄漏过密钥，完成平台侧密钥轮换。
