# Team-Odyssey

COMP3019J Web Application Development — Online Shopping Website

面向本地零售商的在线购物项目，目标是让服装和家居用品的线上购物流程与门店日常运营保持一致。团队成员暂用 **A、B、C、D** 表示。

> 当前版本是可运行的基础框架：主页和商品列表已经实现，商品来自内存中的示例数据。尚未接入数据库、账号、购物车、真实库存或下单功能。页面 EUR 价格仅为示例，不代表已确定的业务币种。

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
| MySQL + Spring Data JPA | 后续接入 | 数据持久化 |
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
    → ProductRepository（当前实现为 InMemoryProductRepository）
    → 返回商品数据
    → products.html 渲染
    → 浏览器显示网页
```

- **Controller**：处理路径和表单，不直接编写 SQL 或库存计算。
- **Service**：放置业务规则，后续订单事务在这里协调。
- **Repository**：只负责数据读写。接入 JPA 时用数据库实现替换内存实现，避免两个同接口 Bean 同时启用。
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
