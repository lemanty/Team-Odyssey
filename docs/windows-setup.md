# Windows 组员：一键配置环境

适用：Windows 10/11，在自己的电脑上运行网站、通过 SSH 连接团队共享数据库。组员不需要安装 Docker、MySQL 或单独安装 Maven。

## 第一次运行

1. 拉取最新代码，或将 ZIP **完整解压**到本地文件夹。不要直接在压缩包中双击文件。
2. 双击 `scripts/Windows-Setup.cmd`，使用自己的普通 Windows 账号运行。安装器可能出现许可确认或 UAC 管理员提示，请阅读并自行确认。
3. 脚本检查并按需安装：
   - JDK 17+（有合适 JDK 就复用，否则通过 WinGet 安装 Microsoft OpenJDK 21）；
   - Python 3.9+（缺少时通过 WinGet 安装 Python 3.12）；
   - OpenSSH **客户端**（缺少时通过 Windows 可选功能安装，需要管理员批准）。
4. 脚本设置当前用户的 `JAVA_HOME`，向用户 PATH 添加所需目录，保留原 PATH；复制缺失的 `.env.team` 模板，**不会覆盖已有文件**。
5. 脚本使用项目自带 Maven Wrapper 下载依赖并编译。第一次可能较慢；出现 `[OK] Environment setup complete.` 后关闭并重新打开 IntelliJ、终端。

它不会安装 SSH 服务器、修改防火墙、创建数据库、收集密码、生成或分发 SSH 密钥，也不会上传项目。`.cmd` 仅为本次 PowerShell 进程设置 RemoteSigned，不更改机器或用户的永久执行策略；组织策略仍然生效。

如果没有 WinGet，请先从 Microsoft Store 安装或更新 **App Installer（应用安装程序）**，再运行。安装失败时保留窗口里的具体错误；再次运行会重新检查已安装组件。

## 团队连接配置仍需填写

环境安装完成不等于获得共享数据库权限。向数据库主机负责人获取主机 IP、SSH 用户名、数据库普通账号及密码，并让负责人配置你的 SSH 公钥授权。

编辑项目根目录 `.env.team`，不要保存成 `.env.team.txt`：

```dotenv
SSH_HOST=主机地址
SSH_USER=自己的SSH账号
SSH_KEY=C:/Users/自己的Windows用户名/.ssh/id_ed25519
DB_USERNAME=odyssey_app
DB_PASSWORD=私下提供的数据库普通账号密码
DB_PORT=13307
```

密钥路径可以使用双引号包住含空格的路径。私钥只能保存在自己电脑；不要提交或发送私钥和 `.env.team`。主机负责人也不要将含 root 密码的 `.env.mysql` 发给组员。

然后：

1. 双击 `scripts/Member-Connect.cmd`，保持隧道窗口打开。首次连接先向负责人核对主机指纹，再决定是否信任。
2. 双击 `scripts/Member-Website.cmd`，网站就绪后自动打开浏览器。
3. 主机需保持开机、网络可达、SSH 和数据库服务可用，并执行最新数据库迁移。缺表错误需要主机更新数据库；重新安装 Java 不能解决。

## 自检与常见问题

双击 `scripts/Windows-Check.cmd`：仅检查能否找到 JDK、Python、OpenSSH，不安装、不修改配置。它不验证数据库密码、SSH 权限或数据库表结构。

| 现象 | 处理方式 |
| --- | --- |
| WinGet 找不到 | 更新 Microsoft Store 的 App Installer，再重新运行配置脚本。 |
| 脚本被组织策略阻止或提示未签名 | 先核对代码来源；若 ZIP 下载文件被标记为来自互联网，可在确认内容后通过文件属性解除该文件的阻止；组织管理的设备请联系管理员，不要全局关闭执行策略。 |
| JDK 已装但 IntelliJ 仍编译失败 | 重新打开 IDEA；Project SDK、Maven Runner 的 JRE 选择 JDK 17 或 21，不使用 Java 8/11 或单独的 JRE。 |
| 只装了 IDEA 自带的运行环境 | IDEA 能打开不代表项目的 `java`、`javac` 已配置；运行一键配置脚本。 |
| 下载 Maven 或依赖失败 | 检查到 Maven Central 的网络/代理和报错；不要关闭 TLS 校验。恢复网络后重跑，下载缓存会复用。 |
| 本地数据库端口不可达 | 先运行 Member-Connect 并保持窗口打开；核对 DB_PORT 和隧道窗口的错误。 |
| SSH 超时 | 核对主机 IP、休眠状态、SSH 服务与校园网设备隔离；这不是 Java 环境问题。 |
| Public key / Permission denied | 负责人核对公钥授权、SSH 用户名；组员核对私钥路径和权限。 |
| 端口 8080 被占用 | 停止自己之前启动的网站，或在新 PowerShell 中设置 `$env:PORT='8081'` 后运行 Member-Website.cmd。脚本不会自动结束别人的程序。 |
| 网页能打开但不能注册登录 | 确认运行的是 MySQL 模式；直接运行 Maven 默认是 demo 模式，不能代替共享数据库启动流程。 |

首次启动等待上限为 600 秒；必要时在 PowerShell 设置 `$env:STARTUP_TIMEOUT='900'` 后运行启动脚本，允许范围为 60–3600 秒。

## 验证范围

启动器的 Python 自动化测试可执行：`py -3 -m unittest discover -s scripts/tests -v`。当前修改已在 macOS 上验证启动器逻辑，但 Windows 安装器、UAC、WinGet 和双击流程仍需 Windows 实机验收，不能据此保证每台 Windows 电脑都已配置成功。

官方参考：[Microsoft Java 安装说明](https://learn.microsoft.com/en-us/windows/dev-environment/java)、[WinGet](https://learn.microsoft.com/en-us/windows/package-manager/winget/)、[OpenSSH 可选功能](https://learn.microsoft.com/en-us/troubleshoot/windows-server/system-management-components/cant-install-openssh-features)。
