# Team-Odyssey

COMP3019J Web Application Development — Online Shopping Website

面向本地零售商的在线购物项目，目标是让服装和家居用品的线上购物流程与门店日常运营保持一致。团队成员暂用 **A、B、C、D** 表示。

> 当前版本是可运行的基础框架：主页和商品列表已经实现，默认 demo 模式商品来自内存；mysql 模式读取数据库商品表（见第 10 节）。账号、购物车、真实库存或下单功能尚未实现。页面 EUR 价格仅为示例，不代表已确定的业务币种。

## 1. 技术选择

| 技术 | 当前状态 | 作用 |
| --- | --- | --- |
| Java 17 | 已使用 | 与当前开发机 JDK 对齐；JDK 21 也可运行，项目编译目标为 17 |
| Spring Boot 4.1.1 / Spring MVC | 已使用 | 启动应用、接收浏览器请求 |
| Thymeleaf | 已使用 | 服务端渲染 HTML 页面 |
| Maven Wrapper | 已提供 | 统一 Maven 版本，无需单独安装 Maven |
| HTML / CSS | 已使用 | 页面结构和响应式样式 |
| Bean Validation | 已引入 | 后续校验表单输入 |
| Spring Boot DevTools | 已引入 | 辅助本地开发 |
| JUnit / MockMvc | 已使用 | 应用启动和网页集成测试 |
| MySQL + Spring JDBC | mysql 模式已接入 | 商品数据持久化；JPA 尚未使用 |
| Spring Security | 后续接入 | 登录和角色权限 |

采用一个 Spring Boot 应用，前后端放在同一仓库中，不需要 Node.js 或单独的前端服务。尚未覆盖于课堂的技术应按课程要求在设计报告中说明用途和适用理由。

## 2. 快速启动

### IntelliJ IDEA

1. 克隆此仓库并打开根目录（包含 `pom.xml` 的目录）。
2. 将 Project SDK 和 Maven 的运行 JDK 设置为 **17 或 21**。
3. 如果 IDEA 没有自动识别 Maven，右键 `pom.xml` → **Add as Maven Project**，然后加载依赖。
4. 运行 `src/main/java/com/odyssey/retailshop/RetailShopApplication.java` 中的 `main` 方法。
5. 打开 <http://localhost:8080/>；商品列表为 <http://localhost:8080/products>。

首次启动需要联网下载依赖。当前阶段**不需要安装 MySQL，也不需要配置数据库密码**。

### 终端

在仓库根目录执行，要求已安装 JDK 并可执行 `java -version`：

```bash
# macOS / Linux
./mvnw spring-boot:run

# Windows PowerShell
.\mvnw.cmd spring-boot:run
```

若 macOS / Linux 提示脚本没有执行权限，先执行 `chmod +x mvnw`。
若 8080 端口已被占用，可在 IDEA 运行配置的环境变量中设置 `PORT=8081`，然后访问 <http://localhost:8081/>。

### 测试与打包

```bash
# macOS / Linux；Windows 将 ./mvnw 换成 .\mvnw.cmd
./mvnw verify
java -jar target/retail-shop-0.0.1-SNAPSHOT.jar
```

`verify` 会运行测试并打包。现有测试覆盖应用启动、首页到商品列表的导航，以及商品从 Repository 经 Service、Controller 到 Thymeleaf 的页面渲染。

## 3. 项目结构与请求流程

```text
Team-Odyssey/
├── pom.xml
├── mvnw / mvnw.cmd / .mvn/       Maven Wrapper
├── src/main/java/com/odyssey/retailshop/
│   ├── RetailShopApplication.java
│   ├── controller/              接收请求、向页面传递数据
│   │   ├── HomeController.java
│   │   └── ProductController.java
│   ├── service/                 业务逻辑
│   │   └── ProductService.java
│   ├── repository/              数据访问
│   │   ├── ProductRepository.java
│   │   └── InMemoryProductRepository.java
│   └── model/                   数据对象
│       └── Product.java
├── src/main/resources/
│   ├── application.properties
│   ├── templates/
│   │   ├── home.html
│   │   ├── products.html
│   │   └── fragments/layout.html
│   └── static/css/style.css
└── src/test/java/com/odyssey/retailshop/
    ├── RetailShopApplicationTests.java
    └── CatalogueWebTests.java
```

访问商品列表时：

```text
浏览器 GET /products
    → ProductController
    → ProductService
    → ProductRepository（demo: InMemoryProductRepository；mysql: JdbcProductRepository）
    → 返回商品数据
    → products.html 渲染
    → 浏览器显示网页
```

- **Controller**：处理路径和表单，不直接编写 SQL 或库存计算。
- **Service**：放置业务规则，后续订单事务在这里协调。
- **Repository**：只负责数据读写。通过 Spring profile 在内存实现与 JDBC 实现之间切换，避免两个同接口 Bean 同时启用。
- **Model**：描述业务数据。当前 `Product` 是普通 Java 类，不是数据库实体。
- **Templates / Static**：分别存放动态 HTML 和 CSS、图片、JavaScript。

后续按功能需要增加类，不必先创建大量空类或空方法。

## 4. 推荐分工：A / B / C / D

每位成员都负责自己模块的页面、后端、测试和说明文档。下面是建议分工，团队应共同确认；它不是已完成贡献的声明。

| 成员 | 主要模块 | 建议负责内容 | 核心交付 |
| --- | --- | --- | --- |
| **A** | 商品与目录 | 商品分类、列表、详情、搜索；商品维护；SKU（颜色、尺码等规格）定义；商品相关页面 | 顾客能找到商品，员工能维护商品；与 B 共同确定 SKU 数据结构 |
| **B** | 库存与门店操作 | 接入数据库基础配置；SKU 库存、预留记录、库存流水；门店销售、留货、释放、退货入库；并发库存控制 | 库存接口及管理页面；验证库存不足、并发预留、取消释放等场景 |
| **C** | 购物车与订单 | 购物车、结算、订单及明细；配送/自取；准备前修改与取消；员工备货和订单状态 | 从购物车到订单完成的主流程；调用 B 提供的库存能力 |
| **D** | 用户、权限与咨询 | 注册登录、顾客/员工/管理员权限；咨询与回复；公共页面布局；组织集成及演示 | 可用的身份和权限体系、咨询流程、共享页面；安排团队联调 |

### 共同承担

- 四人共同确定需求、数据库关系、业务规则、验收标准和报告内容。
- B 协调数据库接入，各成员负责自己模块的实体和数据访问代码；表结构变更提前沟通。
- D 组织集成，但不承担所有人的测试、修复和报告工作。
- 每位成员都要理解整个应用，以及商品 → 订单 → 库存之间的调用关系。
- 每周互相演示已完成的功能，保存任务记录、提交记录和会议记录。

### 模块协作边界

| 协作双方 | 先约定什么 |
| --- | --- |
| A ↔ B | Product 与 SKU 的区别；订单和库存统一使用 SKU 标识 |
| B ↔ C | 预留、释放、出库接口；库存不足如何返回；事务和锁的边界 |
| C ↔ D | 当前顾客身份；订单所有权检查；顾客与员工操作权限 |
| A/B/C ↔ D | 公共页头页脚、导航、登录态、权限错误展示 |

B/C 可围绕 `reserveStock`、`releaseStock`、`fulfillStock`、`recordStoreSale` 讨论接口。这些方法**尚未实现**；正式参数、返回值及异常处理应先共同确定。创建订单和预留库存必须处于同一个数据库事务内，不能各自独立提交。

## 5. 业务规则草案（待团队确认）

### 库存

- 按 SKU 管理库存；同款衣服的不同颜色/尺码有独立库存。
- `可售库存 = 实物库存 - 预留库存`。
- 加入购物车不预留；成功下单时预留；取消时释放。
- 发货/自取交付时同时扣减实物与预留数量。
- 门店销售也必须登记，并校验可售数量；门店留货需要可追溯的预留记录。
- 退货经员工确认可以重新销售后才入库。
- 并发库存控制需要数据库事务以及条件更新或锁；仅“先查库存再减库存”不足以防止超卖。

### 订单

```text
待备货 → 备货中 → 已发货 → 已完成
                → 待取货 → 已完成
待备货 → 已取消
```

- 第一版仅在待备货时允许取消、修改配送地址或切换配送/自取。
- 修改商品/数量可先采用取消后重新下单；如需直接修改，另行实现库存差额处理。
- 修改与开始备货可能并发发生，后端必须原子地检查状态并更新。
- 订单明细保存下单时的名称和单价快照；金额使用 `BigDecimal` 并由后端计算。
- 支付、物流对接、优惠券不在当前框架内；是否加入由团队按时间和需求决定。

## 6. 推荐开发顺序

| 阶段 | 工作 | 完成标准 |
| --- | --- | --- |
| 基础框架（当前） | 项目启动、分层、主页、示例目录、测试、README | 每个人都能克隆并运行 |
| 数据与身份 | MySQL/JPA、商品与 SKU、用户和权限 | 商品从数据库读取，角色访问受控 |
| 主流程 | 购物车 → 下单预留 → 员工备货 → 配送/自取 | 模块接通，可作为 Beta 主演示流程 |
| 门店与异常流程 | 门店销售/留货/退货、订单取消与修改、咨询 | 库存与订单状态保持一致 |
| 最终完善 | 根据 Beta 反馈修正，测试、报告、演示 | 主要功能完成，成员能解释实现 |

课程具体截止日期与提交格式以正式要求为准。最终贡献说明须记录真实贡献，由每位成员审阅确认；README 中的推荐分工不能代替贡献说明。

## 7. GitHub 协作约定

1. 开始新任务前切回 `main` 并 Pull，确保本地工作已提交或妥善保存。
2. 从最新 `main` 建立功能分支，例如 `feature/a-products`、`feature/b-inventory`、`feature/c-orders`、`feature/d-auth`。
3. 小步提交，说明具体改动，例如 `Add product details page`。
4. 推送分支后发起 Pull Request，至少由一名组员检查；合并前运行 `./mvnw verify`。
5. 合并后其他成员同步 `main`。不要强制推送覆盖共享分支。

提交源码、模板、`pom.xml`、Maven Wrapper、测试和文档。不提交 `.idea/`、`target/`、密码、Token 或 `.env`。数据库密码以后通过环境变量配置，示例配置只能含占位符。

## 8. 资料与说明

- [Spring Boot](https://docs.spring.io/spring-boot/)
- [Spring MVC 与 Thymeleaf 入门](https://spring.io/guides/gs/serving-web-content/)
- [Spring Data JPA 入门](https://spring.io/guides/gs/accessing-data-jpa/)
- [Spring Security 入门](https://spring.io/guides/gs/securing-web/)

初始 Maven 项目和 Wrapper 来自 Spring Initializr；本基础框架、示例页面、测试与 README 使用了 Codex 辅助生成。团队须根据课程和学院对 AI、外部资源的实际政策核对允许范围及披露要求，审阅并理解代码后再作为课程成果提交。该记录不代表课程已允许所有 AI 用法。


## 9. 与 Week 1–6 课件对齐的开发习惯

当前保留 Java / Spring Boot / Thymeleaf。课堂示例使用 Python / Flask / Jinja；下面是概念对照，不表示两套框架的语法、默认行为完全相同，也不表示课程要求必须使用某一框架。

| 课件内容 | 本项目对应写法 | 当前状态 |
| --- | --- | --- |
| Week 1：请求、响应、结构/表现/行为分工 | Controller 接收请求，模板生成 HTML，CSS 管理外观 | 已有首页和目录的 GET 请求 |
| Week 2：语义标签、外部 CSS、Grid/Flex、媒体查询 | `header/nav/main/section/article/footer`、`static/css/style.css`、`@media` | 已使用；CSS 已展开为多行便于阅读 |
| Week 3：route、render_template、模板循环和复用 | `@GetMapping`、`Model` 加数据后返回模板名、`th:each`、共享 fragments | 已使用；后端语言不同 |
| Week 4：表单校验、POST/Redirect/GET、ORM 与事务 | 后续使用表单对象、Bean Validation、重定向、JPA 与事务 | JDBC 商品读取已接入；表单、ORM 尚未实现 |
| Week 5：身份、权限、密码哈希、CSRF | 后续通过 Spring Security 实现，并逐个操作检查角色与订单所有权 | 尚未实现登录和权限 |
| Week 6：DOM、事件、渐进增强 | 后续在外部 JS 中使用 `defer`、`querySelector`、`addEventListener`、`textContent` | 当前没有自定义 JS；悬停效果来自 CSS |

### 已有页面的写法

- HTML 负责内容和语义，CSS 放在外部文件；一个声明一行，保留规则顺序，避免难读的一行式媒体查询。
- 用 `h1/h2/h3` 表示内容层级，不能只按字号挑标签。链接负责导航，按钮负责操作。
- 共享页头页脚只维护一份。Thymeleaf 的 fragment 与 Jinja 的 base/block 都用于复用，但不是相同语法。
- 模板使用 `th:text` 输出文本，不把用户输入当作 HTML 输出；列表继续使用 `th:each`，后续搜索要补充无结果提示。
- `th:href="@{/products}"` 处理应用路径，但不像 Flask 的 `url_for('endpoint')` 那样直接按视图函数名生成地址。
- 首页的 SVG 椭圆、渐变、遮罩、层叠与过渡属于额外视觉增强，不是声称 Week 2 已教过的内容。它们不应承载库存、权限等业务规则。
- 当前分类入口是目录中的位置跳转，不是分类筛选；demo 模式商品来自内存列表，mysql 模式来自数据库。

### 后续功能按课堂流程实现

1. 搜索等读取操作使用 GET；保存、删除、取消订单等修改操作使用 POST，并由后端校验。
2. 表单字段保留可见 label、id、name，设置适合的 type、required 和长度/范围；出错时保留输入并显示字段错误。
3. 保存成功后采用 POST/Redirect/GET；需要与课堂严格对应时显式使用 303，而不是假设框架默认重定向一定是 303。
4. 浏览器校验只改善体验，服务器仍须检查类型、范围、业务规则和权限；数据库再用唯一、非空、外键等约束保护数据。
5. 创建订单与预留库存放在同一事务中；失败回滚，不能显示保存成功。库存并发控制是本项目需要额外设计的业务内容。
6. 身份认证与授权分开：每次读写私有订单都检查所有权，员工操作检查角色，不能只隐藏按钮。
7. 密码使用安全库提供的哈希功能；修改类表单启用 CSRF 防护；密钥放环境变量。不要照搬 Flask 签名 Cookie 的内部机制到 Spring Session。
8. JavaScript 用具名函数和事件监听组织，DOM 文本用 textContent 更新；先做可用的服务端流程，再增加即时筛选和输入提示。
9. 上传文件时检查大小、实际内容并生成文件名；私有附件不放 static。数据库回滚不等于删除已保存文件，需要单独清理。

对应课件：Week 2 第 18–32、65–74、86–92 页；Week 3 第 18–43、61–64 页；Week 4 第 9–29、35–48、57–68 页；Week 5 第 14–18、41–60 页；Week 6 第 9、28–53、64 页。以课件原文和正式项目要求为准。

## 10. 数据库：我和组员怎么启动、登录

当前已提供 **MySQL + JDBC** 商品读取。默认 `demo` 模式仍使用 4 个内存示例商品，方便独立运行和测试；下面脚本启用 `mysql` 模式，商品页面读取共享数据库的 `products` 表。MySQL 模式不会自动生成/删除表，也不会失败后偷偷回退到示例数据。SKU、库存、订单表和网页商品编辑功能尚未实现。

### 先看这个：谁负责启动什么

| 人员 | 需要运行 | 数据库客户端连接 |
| --- | --- | --- |
| A：这台 Mac 的管理员 | Docker Desktop，然后 Host-Start | `127.0.0.1:3307` |
| B / C / D：组员 | Member-Connect，保持窗口打开；再运行 Member-Website | `127.0.0.1:13307` |

所有人数据库名是 `odyssey`。当前数据库账号是 `odyssey_app`（团队开发共享账号，与每个人的 SSH 登录账号不同），密码私下分发，不能写入 README。成员不必安装 Docker/MySQL，只需 Java 17+、Python 3、OpenSSH；首次运行 Maven 需要联网下载依赖。

**远程登录尚未配置完成：需要成员公钥、主机开启 SSH，并从另一台电脑验证校园网允许互访。脚本不能代替这些步骤。** 目前只能确认主机本地连接。

### A：主机一键启动

先打开 Docker Desktop。Mac 在 Finder 中双击 [Host-Start.command](scripts/Host-Start.command)。它依次启动数据库、创建缺失的商品表、启动网站。不会覆盖现有商品。网站地址：<http://localhost:8080/products>。

也可以在 IntelliJ README 的 Shell 代码块旁点击运行按钮（若有），或复制到项目终端：

```bash
python3 scripts/database.py host-start
python3 scripts/database.py init
python3 scripts/database.py web-host
```

首次在新主机上部署才需要复制 `.env.mysql.example` 为 `.env.mysql`，设置两个不同的密码。这台已配置主机不要重复覆盖密码文件。

只打开 MySQL 命令行，直接执行，不用粘贴密码：

```bash
python3 scripts/database.py login
```

在 `mysql>` 后输入 SQL，以分号结束；输入 `exit;` 退出。密码来自本机 `.env.mysql`，不会显示在命令中。不要把这个主机文件发给组员，因为它也含 root 密码。

查看状态：

```bash
python3 scripts/database.py status
```

原来的 Maven 命令仍可用：`./mvnw -Pdatabase exec:exec@db-up`、`exec:exec@db-status`、`exec:exec@db-stop`。它们只管理数据库，不会启动网站或建表。

### B / C / D：先把公钥给管理员（仅第一次）

Mac / Linux 终端查看：

```bash
cat ~/.ssh/id_ed25519.pub
```

Windows PowerShell 查看：

```powershell
Get-Content $HOME\.ssh\id_ed25519.pub
```

如果提示文件不存在，运行下面命令，按提示生成。默认保存位置可按 Enter；建议为私钥设置口令。若提示文件已存在，**不要覆盖已有密钥**。

```bash
ssh-keygen -t ed25519
```

再次运行查看公钥命令，将 `ssh-ed25519` 开头的一整行发给管理员，并注明 B / C / D。**只能发 `.pub` 公钥，不能发送 `id_ed25519` 私钥。**

管理员需配置专用 SSH 账号及公钥，仅授予数据库隧道需要的权限（限制转发目标到 `127.0.0.1:3307`、禁用终端/代理/X11 转发），再通过 macOS「通用 → 共享 → 远程登录」允许该指定账号。不要让成员使用主机管理员密码或给予完整磁盘访问。账号、公钥和限制需要实际部署后才可连接。

### B / C / D：填写本机配置（仅第一次）

复制仓库 `.env.team.example` 为 `.env.team`，填写管理员提供的信息：

```text
SSH_HOST=主机当前局域网IP
SSH_USER=管理员分配的SSH账号
SSH_KEY=自己的私钥文件路径
DB_USERNAME=odyssey_app
DB_PASSWORD=管理员私下提供的数据库普通账号密码
DB_PORT=13307
```

这不是网站注册登录。`SSH_USER` 是进入加密隧道的账号；`DB_USERNAME` 是读写数据库的账号。路径可以使用 `~/.ssh/id_ed25519`；Windows 可用 `C:/Users/你的用户名/.ssh/id_ed25519`。不在值的两边添加引号。

`.env.team` 已被 Git 忽略。Mac/Linux 可以执行 `chmod 600 .env.team` 限制读取权限。

### B / C / D：以后每天怎么使用

Mac 双击 [Member-Connect.command](scripts/Member-Connect.command)，输入自己的 SSH 私钥口令（如设置了），保持窗口打开。首次连接应与管理员核对主机指纹后再确认。没有持续输出通常是正常的。

然后双击 [Member-Website.command](scripts/Member-Website.command)，启动自己的 Java 网站，它会读取主机上的共享库。

终端等效操作（开两个终端）：

```bash
# 终端 1：保持运行
python3 scripts/database.py tunnel
```

```bash
# 终端 2：启动网站
python3 scripts/database.py web-member
```

Windows 用 `py -3` 替换 `python3`。普通 Markdown 链接只会打开脚本，不保证执行；Mac 真正双击运行需要在 Finder 中打开 `.command`，Windows 使用上述终端命令。

IntelliJ Database 中新建 **普通 MySQL** 数据源：主机 `127.0.0.1`，组员端口 `13307`（主机本人用 `3307`），数据库 `odyssey`，用户 `odyssey_app`，密码填写普通数据库密码。下载缺失驱动后点击「测试连接」。不要选择 Amazon Aurora。

### 添加第一件商品

在上述 MySQL 命令行，或 IntelliJ 的数据库查询控制台执行：

```sql
USE odyssey;
INSERT INTO products (name, category, description, price)
VALUES ('Cotton T-shirt', 'Clothing', 'Soft cotton for everyday wear.', 19.99);
SELECT * FROM products;
```

每执行一次 INSERT 就会新增一条。也可以在 IntelliJ 展开 `odyssey → tables → products`，打开数据表、添加行，再提交更改。运行 mysql 模式网站后刷新 `/products` 即可看到已提交记录。默认 demo 模式不读取这张表。

正式商品表定义保存在 `src/main/resources/db/001-products.sql`，未来修改结构需新增版本脚本并由管理员协调执行。不要以删除数据卷或清表的方式解决配置错误。表结构脚本可提交 Git；数据库中的记录不会通过 Git Push 同步。

### 主机在线、密码与数据保存

- 主机必须开机、联网、Docker 运行且不休眠。开发时接电源、不要合盖，可运行 `caffeinate -i` 防止空闲睡眠，Ctrl+C 结束。
- Docker 已设置登录 Mac 时启动；这不代表关机、未登录或休眠时数据库仍可访问。
- 数据保存在 Docker 命名卷 `odyssey-shared-db_mysql_data`，不要执行 `down -v` 或删除此卷。数据卷不等于备份。
- 数据库只绑定 `127.0.0.1:3307`。组员必须通过 SSH 隧道访问，不要把 MySQL 端口直接公开到校园网或公网。
- 同一个校园 Wi-Fi 仍可能隔离设备。连接超时时检查主机 IP、SSH 设置、休眠和网络互访限制。
- `.env.mysql` 修改密码不会更新已有 MySQL 账号。需要管理员先修改数据库内密码，再同步文件和客户端；不要删卷。组员 `.env.team` 也要更新。
- 退出网站不会停止数据库；关闭隧道只断开该成员连接。所有成员修改同一份数据，避免运行清库脚本。

### 上传 GitHub

可以提交源码、SQL 表结构、`scripts/`、Compose、Maven 配置、README、`.env.mysql.example` 和 `.env.team.example` 空模板。

不能提交 `.env.mysql`、`.env.team`、SSH 私钥、真实密码和数据库备份。提交前检查 `git status --short`，并运行：

```bash
git check-ignore .env.mysql .env.team
```

示例文件应保持没有真实密码。本轮配置不会自动 commit 或 push。

### 防止重复启动与自动打开网页

Host-Start / Member-Website 会等待商品页面能够正常读取 MySQL 后，自动打开默认浏览器。
再次启动时：已运行的 MySQL 模式网站会被复用，不再启动第二份 Java；仍在启动中的脚本会等待同一任务。
如果端口被 demo 模式、旧版网站或其他程序占用，会明确提示，不会擅自关闭其他程序。先停止旧程序，再启动脚本。
启动等待上限为 180 秒，超时会清理本次创建的进程。正常使用请保持终端打开；Ctrl+C 停止本次 Maven/Java 进程树，Docker 数据库继续运行。
更换端口可先设置环境变量 `PORT`，默认仍为 8080。关闭浏览器只关闭页面，不会停止服务器。

## 11. 服装样本与商品详情

MySQL 目录包含 20 款服装（10 款男装、10 款女装）。点击商品卡片查看价格、演示库存及两张图片。每两款同性别服装共用一组模特形象，共 10 组；图片由 AI 生成，仅用于课程演示。

在数据库主机的项目目录导入：

```bash
python3 scripts/database.py init
python3 scripts/seed-clothing.py
```

脚本按商品名称避免重复导入，重复运行更新图片路径，但保留已有库存和价格。组员共用同一数据库，无需再次导入。启动 `scripts/Host-Start.command` 后访问 `/products`；默认 demo 模式仍使用原来的 4 个内存样本。

商品定义在 `scripts/data/clothing-samples.json`，生成提示记录在 `scripts/data/clothing-image-prompts.json`，WebP 图片在 `src/main/resources/static/images/products/`。`002-product-details.sql` 创建关联详情表，保存受众、库存和图片路径。库存目前是商品级演示数量，尚未实现尺码 SKU、下单预留或支付扣减。

详情页每 5.5 秒自动切换，提供前后箭头、图片标签、暂停按钮、键盘方向键和触屏滑动。鼠标悬停、键盘焦点进入图库或页面切到后台时暂停；系统减少动态效果设置默认关闭自动播放。

新增服装可扩展 JSON、准备图片后执行导入脚本；修改已有价格或库存应在数据库中更新，导入脚本不会覆盖这些数值。

## 12. 购物车

商品列表和详情页均提供 `Add to bag` 按钮，顶部 `Bag` 显示商品总件数。购物车支持修改数量、移除商品和计算 EUR 小计；同款商品再次添加会合并数量。

购物车保存在 Java `HttpSession` 中，按浏览器会话隔离，不写入共享 MySQL。刷新页面仍保留；会话过期或服务器重启后可能清空，暂不支持跨设备同步。数量限制为 1–999，且不能超过数据库当前库存；库存未知或售罄时禁止添加。价格和库存均由后端重新读取，表单使用会话令牌校验。

加入购物车不会预留或扣减库存，尚未提供结算、支付和创建订单。未来下单时仍需使用数据库事务重新校验库存并预留。`CartService` 管理购物车状态，`CartController` 处理表单，`cart.html` 展示购物车。
