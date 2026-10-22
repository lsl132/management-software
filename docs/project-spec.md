# 项目规范文档

## 1. 项目概述
- 项目名称：management-software
- 项目本地路径：`/Users/gangtise/IdeaProjects/management-software`
- 项目类型：Spring Boot 工程
- 编译工具：Maven
- 基础框架：Spring Boot 3.5.16
- 适用范围：仓库级统一规范，适用于当前所有模块及后续新增模块
- 项目使用 JDK 17
- 项目数据库为 PostgreSQL 16,配置文件中要指定数据库连接池为 HikariCP，连接参数增加环境变量
- 项目使用 mybatis-plus 作为 ORM，支持 Lambda 查询、分页插件、逻辑删除等功能
  - Spring Boot 3 必须使用 `mybatis-plus-spring-boot3-starter`，版本由 `mybatis-plus-bom` 统一管理，当前为 **3.5.12**
  - 原约定的 `3.5.3` 无法在 Spring Boot 3 上使用（该版本未提供 Spring Boot 3 专用 starter，且 Spring Boot 3.4+ 与 ≤3.5.9 存在自动装配时序冲突），故统一上调至 3.5.12
  - v3.5.9 起分页等 SQL 解析能力已从 starter 中拆出，需额外引入 `mybatis-plus-jsqlparser`
- 项目需要 redis 缓存，配置文件中要指定 redis 连接池为 lettuce，连接参数增加环境变量；单机模式为默认配置，集群模式通过 `cluster` profile 启用
- 指定 Maven 的字符编码为 UTF-8
- 项目使用 Spring Boot 3.5.16，界面使用一体化：视图层采用 Thymeleaf 服务端渲染，模板位于 `src/main/resources/templates`，静态资源位于 `src/main/resources/static`
- 项目遵从领域驱动设计原则，模块内保持清晰的分层结构（application、domain、infrastructure、interfaces）
- 接口传结构化数据时，用Jackson序列化，传递参数时使用DTO对象，避免直接传递实体对象
- 项目需要输出日志，日志级别为INFO，日志文件按天切割，保留7天
- 打包方式为 `jar`，产物统一命名为 `management-software.jar`
- 密码存储：使用 **BCrypt** 散列（`spring-security-crypto`），任何位置都不得保存或回传明文密码
- 登录传输加密：使用**国密 SM2** 非对称加密（BouncyCastle `bcprov-jdk18on`，版本 1.81 由 `bouncycastle.version` 锁定），前端 `sm-crypto` 加密、后端解密
- 前端交互：一律使用 **ajax** 提交数据，禁止使用表单原生提交（详见第 4 节）

## 2. 分层说明及主要目录介绍
- `com.management.software.interfaces`： 主要用于对外提供接口服务，包含 REST API、RPC 接口等，负责接收请求并调用应用层服务。
- `com.management.software.interfaces.facade`： 主要用于对外接口，返回数据的controller类都放这里。
- `com.management.software.interfaces.web`: 主要存放跳转页面的controller类，返回 Thymeleaf 视图名（不带 `.html` 后缀）；对外返回 JSON 的接口不要放在此包，避免与页面控制器混淆
- `com.management.software.application`： 主要用于处理业务逻辑，包含服务类、应用服务等，负责协调领域模型和基础设施。
- `com.management.software.application.service`： 存放应用层的service类，应用层的service负责粘合domain层服务和基础设施层工具类。
- `com.management.software.application.event`: 主要用于处理领域事件，包含事件发布。
- `com.management.software.application.dto`： 主要用于应用层的结构化参数传递。请求入参用普通类 + Lombok `@Data`（便于参数校验与 Jackson 反序列化）；仅供序列化返回的不可变对象可用 `record`；需要被 Thymeleaf 模板读取的对象**必须用普通类**（SpEL 不识别 record 的访问器）。
- `com.management.software.domain`： 主要用于封装业务规则和领域模型，包含实体、值对象、聚合根等，负责维护业务状态和行为。
- `com.management.software.domain.aggregate`： 主要用于封装领域根对象,领域根是模型的核心，业务主要围绕根进行。
- `com.management.software.domain.aggregate.entity`： 主要用于封装领域实体类，领域实体类也是领域模型的核心，包含业务属性和行为。
- `com.management.software.domain.service`： 主要用于封装领域服务类，领域服务类是领域模型的核心，包含业务逻辑和规则。
- `com.management.software.infrastructure`： 主要用于提供技术实现和基础设施，包含数据库访问、缓存、消息队列等，负责与外部系统交互。
- `com.management.software.infrastructure.repository`： 主要用于封装数据访问，Mapper 接口统一放在其子包 `infrastructure.repository.mapper` 下
- `com.management.software.infrastructure.config`： 主要用于封装基础设施的配置类，包含数据库、缓存、消息队列、Jackson、Thymeleaf 等的配置。
- `com.management.software.infrastructure.enums`： 主要用于封装基础设施的枚举类，包括各种预定义的状态配置等。
- `com.management.software.infrastructure.tasks`： 主要用于封装定时任务类。
- `com.management.software.infrastructure.utils`： 主要用于封装基础设施的工具类，包含常用的工具方法和辅助类。
- `com.management.software.infrastructure.exception`： 主要用于封装基础设施的异常类，包含自定义异常和异常处理器。
- `com.management.software.infrastructure.constant`： 主要用于封装基础设施的常量类，包含常用的常量和枚举类。
- `com.management.software.infrastructure.annotation`： 主要用于封装基础设施的注解类，包含自定义注解和注解处理器。
- `com.management.software.infrastructure.aspect`： 主要用于封装基础设施的切面类，包含自定义切面和切面处理器。
- `com.management.software.infrastructure.interceptor`： 主要用于封装基础设施的拦截器类，包含自定义拦截器和拦截器处理器。
- `com.management.software.infrastructure.filter`： 主要用于封装基础设施的过滤器类，包含自定义过滤器和过滤器处理器。



## 3. 目标
- 统一仓库内的模块组织方式
- 约束公共模块与业务模块的依赖边界
- 保持构建方式一致，便于后续扩展
- 降低多人协作时的维护成本


## 4. 页面（视图层）规范
- 视图层使用 Thymeleaf 服务端渲染（前后端一体化），依赖 `spring-boot-starter-thymeleaf`，不引入独立前端工程
- 模板根目录固定为 `src/main/resources/templates`，后缀 `.html`，编码 UTF-8
- 静态资源（CSS / JS / 图片）放在 `src/main/resources/static`，页面中统一用 `th:href="@{/css/xxx.css}"`、`th:src="@{/js/xxx.js}"` 引用，禁止写死绝对路径或外链 CDN
- 页面控制器统一放在 `com.management.software.interfaces.web`，方法返回视图名（如 `return "index"`），由 `spring.thymeleaf.prefix` + `suffix` 定位模板文件
- 页面控制器只负责组装视图模型，不承载业务逻辑，业务处理下沉到 `application.service`
- 公共片段统一放在 `templates/fragments/`。当前 `fragments/layout.html` 提供：
  - `head(title)`：页面 head（meta、标题、样式表）
  - `header(active)`：顶部二级导航。`active` 用于高亮当前项，取值是**路由的首段**（`/employees` → `employees`）：`home` / `employees` / `departments` / `users` / `roles` / `authorities` / `announcements` / `schedules` / `about`。
    菜单数据 `navMenus` 由 `PageController` 的 `@ModelAttribute` 统一注入（各页面不用自己挂），一级是 `authority` 里 `type=1` 的模块分组、二级是其下功能页，见 §12.19
  - `footer`：页脚
  - 引用方式：`<head th:replace="~{fragments/layout :: head('首页')}"></head>`
  - 调用带参片段时必须显式传参（如 `~{fragments/layout :: header('')}`），传空串而非省略参数，避免渲染异常难以定位
- 错误页统一放在 `templates/error/`，按状态码命名（`404.html`、`500.html`），Spring Boot 自动按状态码匹配
  - 注意：错误页仅在请求 `Accept` 包含 `text/html` 时生效；接口请求会返回 JSON 错误体
- 模板缓存：开发环境 `spring.thymeleaf.cache=false`（改完模板刷新即生效），**生产环境必须设为 `true`**，可通过环境变量 `THYMELEAF_CACHE` 控制
- 模板内联表达式注意事项：Thymeleaf 会把表达式中的 `__xxx__` 识别为预处理（preprocessing）语法，因此**不要在 `th:*` 表达式内书写含双下划线的 BEM 类名**（如 `nav__link`）；写在静态 `class` 属性上不受影响

### 4.1 数据交互：ajax 优先（强制）
- **所有数据的提交与查询一律使用 ajax（`fetch`），禁止使用表单原生提交（`<form action method>` 直接 POST）与整页刷新**
- 页面允许保留 `<form>` 元素，但仅用于语义与「回车提交」体验，提交事件必须 `event.preventDefault()`，再由 JS 收集数据以 JSON 提交
- 统一使用 `static/js/app.js` 中的请求封装（`App.get/post/put/del`），不要在页面里各写各的 `fetch`
- 后端统一返回结构 `ApiResult`：`{ code, message, data }`，`code = 0` 表示成功，前端只按 `code` 判断
- 未登录时接口返回 HTTP 401 + `code: 401` JSON，请求封装统一跳转登录页；后台页面请求未登录则由拦截器 302 到 `/login`
- 操作结果用轻提示（`App.toast`）反馈，不使用 `alert`（破坏性操作确认除外）
- 表单校验：前端做即时提示，后端以 `@Valid` 兜底，两边的错误信息都通过统一返回体回传
- **分页列表取数必须显式转数字**：`state.total / current / size` 一律写 `Number(data.xxx)`。全局 Jackson 策略会把 `Long` 序列化成字符串，若不转换，`state.current + 1` 会变成字符串拼接（`"1" + 1 === "11"`），点「下一页」直接跳到第 11 页；而「上一页」用的是减号（`"2" - 1 === 1`）不会暴露问题——所以这个 bug 只在下一页出现。四个分页页面（`employees` / `users` / `announcements` / `workflows`）写法必须一致，详见 §12.21
- **浏览器本地存储（localStorage）只允许存非敏感数据**：目前仅登录页的「记住登录账号」用账号名（键 `backend:login:remember-username`）。**禁止把密码、SM2 密文、会话令牌写入 localStorage / cookie**
- localStorage 读写必须 `try/catch` 兜底：隐私模式或被浏览器禁用时会直接抛异常，不能因此影响主流程

### 4.2 现有页面与路由
| 路径 | 视图 | 说明 | 登录要求 |
| --- | --- | --- | --- |
| `/login` | `login.html` | 登录页，密码加密后 ajax 提交；含「记住登录账号」勾选框 | 免登录（已登录则跳 `/home`） |
| `/home` | `home.html` | 个人工作台：我的登录记录（近 10 天折线图）+ 我的日历（带日程标点）+ 发布的公告（近 5 条） | 需要 |
| `/employees` | `employees.html` | 员工管理（列表 + 弹窗增删改查） | 需要 |
| `/departments` | `departments.html` | 部门管理（部门列表 + 弹窗增删改查；行内「职级」按钮打开该部门的职级维护弹窗） | 需要 |
| `/users` | `users.html` | 用户管理（系统账号分页列表 + 弹窗增删改查 + 设置角色 + 重置密码 + 冻结/解冻） | 需要 |
| `/roles` | `roles.html` | 角色管理（角色列表 + 弹窗增删改查 + 行内「挂载权限」打开两级权限树弹窗） | 需要 |
| `/authorities` | `authorities.html` | 权限管理（树形列表：分组行 + 缩进功能页行；弹窗增删改查） | 需要 |
| `/workflows` | `workflows.html` | 流程管理（审批规则：分页列表 + 弹窗内维护流程信息与节点链 + 详情 + 行内启停/删除） | 需要 |
| `/announcements` | `announcements.html` | 公告管理（分页列表 + 弹窗增删改查 + 行内发布/下线） | 需要 |
| `/profile` | `profile.html` | 个人信息（账号信息只读 + 显示名/手机号/邮箱可改），「我的」下第一项 | 需要 |
| `/profile/password` | `password.html` | 修改密码（SM2 加密提交，成功后强制退出并跳登录页） | 需要 |
| `/notices` | `notices.html` | 公司公告（员工只读视角：仅「已发布」，可按标题搜索 + 详情弹窗） | 需要 |
| `/schedules` | `schedules.html` | 我的日程（月历 + 当月列表 + 弹窗增删改查），已移入「我的」 | 需要 |
| `/about` | `about.html` | 运行信息 | 需要 |
| `/` | — | 重定向到 `/home` | 需要 |
| 其他 | `error/4xx、5xx.html` | 错误页 | — |


## 5. 命名规范
- Maven `artifactId` 使用小写和短横线风格
- Java 包名使用反向域名风格，例如 `com.management.software`
- 文档文件名使用英文小写短横线风格，例如 `project-spec.md`
- 配置文件、类名、方法名应保持语义清晰，避免缩写歧义
- Thymeleaf 模板文件名使用英文小写短横线风格，例如 `user-list.html`；公共片段统一放在 `templates/fragments/` 下
- 静态资源按类型分目录：`static/css`、`static/js`、`static/img`
- 品牌资源统一放 `static/img/`：`logo.png`（齿轮徽标，导航栏/登录卡用）、`logo-full.png`（含"逐锐科技"字样的完整版）、`favicon.png`（64x64）与 `favicon-32.png`（32x32）
- favicon 由公共片段 `fragments/layout :: head` 统一引入（32/64 双尺寸 + apple-touch-icon），页面不得自行引入；品牌文案统一为「逐锐科技 · 后台管理」
- JS 文件使用英文小写短横线风格，公共脚本放 `static/js/app.js`，页面脚本与模板同名（如 `employees.html` → `employees.js`）
- 公共/第三方 JS 一律本地化后放入 `static/js`，**禁止引用外链 CDN**（如前端国密库 `sm-crypto` 已本地化为 `static/js/sm2.js`）

## 6. 依赖规范
- 避免循环依赖
- 优先依赖接口与抽象，减少模块间耦合

## 7. 构建与发布规范
- 仓库采用 Maven 统一构建
- 当前为单模块工程，根 `pom.xml` 即应用工程（`packaging` 为 `jar`，产物 `backend-management.jar`），源码位于根目录 `src` 下
- 若后续拆分为多模块，根 POM 改回聚合职责，不直接承载业务代码
- 新增模块需继承父 POM，统一版本与插件管理策略
- 根 POM 统一管理 Spring Boot 依赖版本与插件版本，子模块只声明业务依赖
- 提交前应保证 `mvn test` 或至少 `mvn -q -DskipTests package` 可通过

## 8. 质量规范
- 代码应保持可读、可维护、可测试
- 公共能力变更需同步更新相关文档
- 对外暴露的接口或工具类应尽量保持向后兼容
- 重要逻辑建议补充单元测试

## 9. 变更原则
- 优先最小化修改范围
- 新增模块或公共能力前，先确认是否已有可复用实现
- 变更涉及模块边界时，优先更新规范文档，再实施代码调整
- 新增业务模块时，需同步更新根 `pom.xml`、本规范和项目计划文档

## 10. 适用说明
- 当前仓库为单模块 Spring Boot 工程（根 `pom.xml` 即应用工程），已完成日志、数据库、Redis、Thymeleaf 页面四项基础设施的落地
- 第 2 节约定的分层包（application / domain / infrastructure / interfaces）已建立，可直接在此基础上扩展业务
- 领域模型（7 个聚合根 + 5 个实体）已完成建表与 ORM 落地，详见第 12 节，新增实体时请同步 `docs/db/schema.sql` 与 Mapper
- 组织与职级：5 个部门 + 23 条职级已初始化，**职级归属部门**，员工填职级须先选部门（见第 12.6 / 12.7 节）
- 已完成后台管理基础功能：登录（国密 SM2 传输加密 + BCrypt 校验）、系统欢迎页、员工增删改查（ajax），管理员账号 `admin` 由启动时自动初始化
- 安全相关约定见第 13 节，前端交互约定见第 4.1 节
- 后续如果新增服务模块、接口模块或基础设施模块，请先更新本规范，再扩展实现
- 若后续拆分为 Maven 多模块聚合工程，需同步更新根 `pom.xml`、本规范与项目计划文档


## 11. 配置约定
统一配置文件为 `src/main/resources/application.yml`，日志配置为 `src/main/resources/logback-spring.xml`。
所有连接参数均支持环境变量覆盖，禁止将生产密钥硬编码进代码。

### 11.1 数据库（PostgreSQL + HikariCP）
| 配置项 | 默认值 | 环境变量 |
| --- | --- | --- |
| 连接 URL（整体覆盖） | 由下方分项自动拼装 | `DB_URL`，**设置后优先级最高，忽略下方分项** |
| 主机 | localhost | `DB_HOST` |
| 端口 | 5432 | `DB_PORT` |
| 库名 | backend_management | `DB_NAME` |
| schema | public | `DB_SCHEMA` |
| 用户名 | postgres | `DB_USERNAME` |
| 密码 | 12345678 | `DB_PASSWORD` |
| 连接池大小 | 5 ~ 20 | `DB_POOL_MIN_IDLE` / `DB_POOL_MAX_SIZE` |

### 11.2 缓存（Redis + Lettuce 连接池）
| 配置项 | 默认值 | 环境变量 |
| --- | --- | --- |
| 主机 | 127.0.0.1 | `REDIS_HOST` |
| 端口 | 6379 | `REDIS_PORT` |
| 密码 | 12345678 | `REDIS_PASSWORD` |
| 库序号 | 0 | `REDIS_DATABASE` |
| 连接池 | 2 ~ 16 | `REDIS_POOL_MIN_IDLE` / `REDIS_POOL_MAX_IDLE` / `REDIS_POOL_MAX_ACTIVE` |
| 集群节点 | 127.0.0.1:6379 | `REDIS_CLUSTER_NODES`（配合 `--spring.profiles.active=cluster`） |

缓存 key 统一前缀 `backend:cache:`，默认 TTL 30 分钟；单机为默认模式，集群模式通过 `cluster` profile 启用。

### 11.3 日志
- 级别 INFO，按天切割（单文件超过 100MB 再按序号递增），保留 7 天，总量上限 2GB
- 输出目标：控制台 + `logs/backend-management.log`（异步写入）+ `logs/backend-management-error.log`（仅 ERROR）
- 日志目录可通过环境变量 `LOG_PATH` 修改，`logs/` 已在 `.gitignore` 中忽略

### 11.4 基础设施配置类
| 类 | 职责 |
| --- | --- |
| `infrastructure.config.MybatisPlusConfig` | 分页插件（PostgreSQL 方言，单页上限 500）、乐观锁、防全表更新删除 |
| `infrastructure.config.MyMetaObjectHandler` | createTime / updateTime / deleted 公共字段自动填充（更新时强制刷新 updateTime） |
| `infrastructure.config.RedisConfig` | RedisTemplate JSON 序列化、RedisCacheManager、`@EnableCaching` |
| `infrastructure.config.JacksonConfig` | LocalDateTime 统一 `yyyy-MM-dd HH:mm:ss`、GMT+8 时区、Long → String 防前端精度丢失（唯一例外是分页计数，见 §12.21） |
| `infrastructure.config.CryptoConfig` | BCrypt `PasswordEncoder`（只引入 spring-security-crypto，不引入完整 Spring Security） |
| `infrastructure.config.WebMvcConfig` | 注册 `LoginInterceptor`，放行登录页/登录接口/静态资源/错误页 |

### 11.5 测试约定
- 涉及中间件的集成测试应放在 `src/test/java` 下并以 `*Test` 结尾，需要本地 PostgreSQL / Redis 可用
- 纯逻辑单测不得依赖外部中间件；无法保证中间件可用时，可使用 `@Disabled` 或独立 profile 隔离
- 现有测试：
  - `InfrastructureSmokeTest`：数据源 / Redis 连通性
  - `DomainPersistenceTest`：实体与表映射、主键回填、审计字段填充、保留字表名
  - `Sm2CryptoTest`：SM2 与前端 sm-crypto 的格式互通（含真实密文向量）、自解回环、非法密文
  - `AuthApiTest` / `EmployeeApiTest`：MockMvc 接口层集成测试，继承 `support.ApiTestSupport`（统一准备测试账号与登录会话）
- 涉及写库的集成测试必须加 `@Transactional` 由测试框架回滚，避免污染本地库
- **注意**：`@SpringBootTest` 会执行 `AdminAccountInitializer`，因此首次跑测试会在库里创建 admin 账号（这是预期行为）

### 11.6 应用自身配置（`app.*`）
| 配置项 | 默认值 | 环境变量 | 说明 |
| --- | --- | --- | --- |
| `app.admin.enabled` | true | `ADMIN_INIT_ENABLED` | 是否在启动时初始化管理员 |
| `app.admin.username` | admin | `ADMIN_USERNAME` | 管理员登录账号 |
| `app.admin.password` | admin123 | `ADMIN_PASSWORD` | 仅用于首次初始化，以 BCrypt 入库，生产环境必须覆盖 |
| `app.admin.role-name` | 超级管理员 | `ADMIN_ROLE_NAME` | 管理员角色名，不存在时自动创建 |
| `app.security.sm2.key-ttl` | 5m | `SM2_KEY_TTL` | SM2 私钥在 Redis 中的有效期 |
| `app.attendance.enabled` | true | `ATTENDANCE_ENABLED` | 考勤统计定时任务开关，关闭后只记日志不写库 |
| `app.attendance.work-start` | 09:00 | `ATTENDANCE_WORK_START` | 上班时间，最早打卡晚于它记迟到（见 §12.17） |
| `app.attendance.work-end` | 18:00 | `ATTENDANCE_WORK_END` | 下班时间，最晚打卡早于它记早退，晚于它的部分算加班 |
| `app.attendance.zone` | Asia/Shanghai | `ATTENDANCE_ZONE` | 判定打卡归属哪一天的时区，**不要用 JVM 默认值**（容器多为 UTC） |
| `app.attendance.statistics-cron` | `0 0 8 * * ?` | `ATTENDANCE_STATISTICS_CRON` | 考勤统计任务执行时间，默认每天 08:00 汇总前一天 |
| `app.attendance.allowed-networks` | `127.0.0.1/32,::1/128` | `ATTENDANCE_ALLOWED_NETWORKS` | 允许调用打卡接口的来源网段（CIDR）。**打卡接口不校验登录，这就是它唯一的门禁**，部署时必须显式加入办公网段（见 §12.17） |


## 12. 数据模型与持久化约定

### 12.1 建表脚本
- 表结构统一维护在 `docs/db/schema.sql`，与 `domain` 下实体一一对应；**新增或调整实体时必须同步更新该脚本**
- 脚本为幂等（`create table if not exists`），可重复执行：
  `psql -h localhost -p 5432 -U postgres -d backend_management -f docs/db/schema.sql`
- 命名规则：表名 = 类名下划线风格，列名 = 属性名下划线风格；依赖 `map-underscore-to-camel-case` 与 `table-underline`，普通字段无需逐列写 `@TableField`
- 主键统一 identity 自增列，`Integer/Long` ↔ `integer/bigint`
- 金额字段 `numeric(18,2)`、时长/天数字段 `numeric(10,2)`，Java 侧统一映射为 `Double`
- 时间字段统一 `timestamp`（无时区），对应 Java 侧 `LocalDateTime`
- 不使用外键约束，关联列只建普通索引，便于后续服务拆分与数据迁移
- `user` 是 PostgreSQL 保留字，建表与查询必须加双引号，实体上以 `@TableName("\"user\"")` 处理

### 12.2 表清单（19 张，与实体一一对应）
| 表 | 实体类 | 唯一约束 |
| --- | --- | --- |
| `department` | `domain.aggregate.Department` | — |
| `employee` | `domain.aggregate.Employee` | `work_no` |
| `department_rank` | `domain.aggregate.entity.DepartmentRank` | `code` |
| `employee_salary` | `domain.aggregate.entity.EmployeeSalary` | `work_no` |
| `attendance_record` | `domain.aggregate.entity.AttendanceRecord` | — |
| `attendance` | `domain.aggregate.Attendance` | `(work_no, day)` |
| `role` | `domain.aggregate.Role` | `name` |
| `authority` | `domain.aggregate.Authority` | `mnemonic` |
| `role_authority` | `domain.aggregate.entity.RoleAuthority` | `(role_id, mnemonic)` |
| `"user"` | `domain.aggregate.User` | `username` |
| `user_account` | `domain.aggregate.entity.UserAccount` | `identifier` |
| `settlement_salary` | `domain.aggregate.SettlementSalary` | — |
| `user_login_record` | `domain.aggregate.entity.UserLoginRecord` | — |
| `user_schedule` | `domain.aggregate.entity.UserSchedule` | — |
| `announcement` | `domain.aggregate.entity.Announcement` | — |
| `workflow` | `domain.aggregate.Workflow` | — |
| `workflow_node` | `domain.aggregate.entity.WorkflowNode` | — |
| `work_order` | `domain.aggregate.WorkOrder` | — |
| `work_order_activity` | `domain.aggregate.entity.WorkOrderActivity` | — |

> `authority` 为**两级权限**：`type` = 1 模块分组（菜单）/ 2 功能页，助记符的万位区分所属模块分组，见 §12.6。
> `authority.path` 是功能页的路由，**同时是顶部二级导航的跳转地址**；为空表示该功能页尚未实现，导航里置灰，见 §12.19。

> **考勤（`attendance_record` / `attendance`）**：明细与汇总的关系——前者是一次刷卡一行（只追加），
> 后者是一人一天一行（可重算）。`attendance` 的业务键 `(work_no, day)` 由唯一索引
> `uk_attendance_work_no_day` 兜底，同时是定时任务幂等 upsert 的冲突键。见 §12.17。

> **流程与工单（`workflow` / `workflow_node` / `work_order` / `work_order_activity`，当前仅数据模型，暂无页面与接口）**：
> `workflow` 存流程定义（`template` 为节点编排 JSON），节点明细在 `workflow_node`——`sort` 决定串联顺序，
> `type` = 0 起始 / 1 审批 / 2 结束；审批节点再以 `approval_type` = 1 指定人员 / 2 直属上级 / 3 指定角色
> 配合 `approval_mode` = 1 或签（一人通过即生效）/ 2 会签（全员通过才生效）描述审批规则，
> `approval_values` 存配置值（指定人员存用户ID、多个逗号分隔；指定角色存角色ID）。
> `work_order` 是按模板发起的一次审批实例（`start_time` 起始、`end_time` 截止，
> `status` = 0 审批中 / 1 已通过 / 2 已拒绝 / 3 已撤销），
> `work_order_activity` 记录工单在每个节点的流转留痕（`node_name` 存节点快照，改名不影响历史；
> `status` = 0 未开始 / 1 待处理 / 2 已通过 / 3 已转交 / 9 驳回）。
> 关联列类型与被引用主键保持一致：`workflow_node.workflow_id` / `work_order.workflow_id` 为 `integer`，
> `work_order_activity.work_order_id` 为 `bigint`（对应 `work_order.id`）。

> **薪酬（`employee_salary`）**：一人一行，业务键是 `work_no`，由唯一索引 `uk_employee_salary_work_no`
> 兜底并作为 upsert 的冲突键；金额列是 `numeric(18,2)`，接口边界用 `BigDecimal` 保证两位小数。见 §12.18。

**业务键唯一性（必须保持）**：五个业务键由数据库唯一索引兜底，禁止重复写入。

| 业务键 | 唯一索引 | 说明 |
| --- | --- | --- |
| `employee.work_no` | `uk_employee_work_no` | 员工工号，应用层 `EmployeeService.ensureWorkNoUnique` 先查重给友好提示 |
| `authority.mnemonic` | `uk_authority_mnemonic` | 权限助记符，同时是 seed 幂等 upsert 的冲突键 |
| `user_account.identifier` | `uk_user_account_identifier` | 登录标识，一个登录名只能绑定一个账号 |
| `attendance.(work_no, day)` | `uk_attendance_work_no_day` | 考勤统计「一人一天一行」，同时是定时任务幂等 upsert 的冲突键（见 §12.17） |
| `employee_salary.work_no` | `uk_employee_salary_work_no` | 员工薪酬「一人一行」，同时是 upsert 的冲突键（见 §12.18） |

- 唯一性统一用 `create unique index` 表达（**不用** `alter table ... add constraint`）：既能被 `on conflict (列) do update` 直接用作冲突键，也便于后续按需降级为普通索引
- 重复写入抛 `DuplicateKeyException`，由 `GlobalExceptionHandler` 转成「数据已存在…」的业务提示，不会落到 500
- 回归测试：`UniqueConstraintTest`（元数据断言唯一索引存在 + 行为断言重复写入被拒）

### 12.3 实体注解约定
- 实体统一加 Lombok `@Data` 并 `implements Serializable`（MyBatis 反射赋值与 Redis 缓存都需要）
- `@TableName("xxx")` 显式声明表名，不依赖默认驼峰推导
- 主键：`@TableId(value = "id", type = IdType.AUTO)`，与全局 `mybatis-plus.global-config.db-config.id-type: auto` 保持一致
- 审计字段：`create_time` 用 `@TableField(value = "create_time", fill = FieldFill.INSERT)`，`update_time` 用 `FieldFill.INSERT_UPDATE`
- 审计字段类型统一为 `java.time.LocalDateTime`，**不要使用 `java.util.Date`**（自动填充与 Jackson 均按 `LocalDateTime` 配置）
- 审计字段不在业务代码里手工赋值，交由 `MyMetaObjectHandler` 填充；数据库侧同时保留 `default current_timestamp` 兜底

### 12.4 Mapper 约定
- Mapper 接口统一放在 `com.management.software.infrastructure.repository.mapper`，继承 `BaseMapper<T>`，无需 `@Mapper` 注解（启动类 `@MapperScan("com.management.software.infrastructure.repository")` 已覆盖）
- 简单 CRUD 直接用 `BaseMapper`；复杂查询优先 Lambda Wrapper，确实需要才写 XML（位置 `classpath*:/mapper/**/*.xml`）

### 12.5 已知坑位（务必遵守）
1. **更新时 `updateTime` 不生效**：mybatis-plus 的 `strictUpdateFill` 语义是「字段为 null 时才填充」，而更新场景实体通常已持有从库中查出的旧值，会导致永远填不进去；`MyMetaObjectHandler.updateFill` 必须改用 `setFieldValByName` 强制刷新。
2. `mybatis-plus.type-aliases-package` 必须覆盖到实际实体包（当前为 `com.management.software.domain`，同时包含 `aggregate` 与 `aggregate.entity`），否则别名引用找不到类。
3. 全局 `logic-delete-field: deleted` 只在实体上存在同名字段时才生效；当前各实体用 `status` 表达业务状态，未启用软删除。如需启用，补 `deleted` 列并在实体加 `@TableLogic`。
4. 主键策略全局为 `auto`，实体侧仍显式标注 `IdType.AUTO`，避免配置漂移后主键生成方式被悄悄改变。
5. **`updateStrategy = FieldStrategy.ALWAYS` 与 NOT NULL 约束会冲突**：`Employee` 的可选字段用 ALWAYS 实现「表单未填即清空」，若列上有 `NOT NULL` 约束，更新时会直接抛约束异常；因此这些列必须是**可空**的（`gender` / `education` / `rank` 已去掉 NOT NULL 与默认值，避免用 0 与枚举有效值混淆）。同时注意：ALWAYS 意味着直接调用 Mapper 做部分更新会把未传字段置空，部分更新场景请自行补齐字段或改用 `UpdateWrapper`。
6. **自定义 SQL（`@Insert`/`@Select` 注解或 XML）不走自动填充**：`MyMetaObjectHandler` 只在 MyBatis-Plus 自身注入的方法里生效，自己写的 SQL 必须显式带上 `create_time` / `update_time`（参考 `AttendanceMapper#upsert` 用 `current_timestamp`）。
7. **`on conflict` upsert 的 null 参数要显式写 `jdbcType`**：如 `#{first,jdbcType=BIGINT}`，否则 Postgres 驱动遇 null 可能报 `JdbcType OTHER` 无法推断类型。

### 12.6 字典数据初始化
- 业务字典的初始数据放在 `docs/db/seed-*.sql`（按表拆分：`seed-department.sql`、`seed-department-rank.sql`、`seed-authority.sql`），与建表脚本分开维护
- seed 脚本**必须幂等**：使用 `insert ... on conflict (唯一键) do update`，保证可重复执行且不产生重复行
- 存在依赖关系的 seed 必须**按序执行**（职级依赖部门）：
  ```bash
  psql -h localhost -p 5432 -U postgres -d backend_management -f docs/db/schema.sql
  psql -h localhost -p 5432 -U postgres -d backend_management -f docs/db/seed-department.sql
  psql -h localhost -p 5432 -U postgres -d backend_management -f docs/db/seed-department-rank.sql
  psql -h localhost -p 5432 -U postgres -d backend_management -f docs/db/seed-authority.sql
  ```
- 跨表引用（如职级隶属部门）在 seed 中用**子查询按自然键取 id**，不硬编码自增主键：
  `(select id from department where depart_name = '生产部')`
- 字典表的码值必须在 `infrastructure/enums` 下建对应枚举（如 `department_rank.type` → `RankTypeEnum`、`authority.type` → `AuthorityTypeEnum`），Java 侧不得散落魔法值
- 下拉类字典的查询须显式 `orderByAsc(type, code)` 等业务排序，不依赖插入顺序
- 收紧非空约束等 DDL 兜底动作若依赖 seed 结果，放在 seed 末尾并用 `do $$ ... $$` 加存在性判断，保证重复执行不中断

**部门字典（`department`，5 条）**：部门名称唯一（`uk_department_depart_name`），同时作为 seed 幂等键。

| 部门 | 说明 | 对应职级序列 |
| --- | --- | --- |
| 生产部 | 一线操作工种 | O 序列（5 级） |
| 技术研发部 | 技术/研发 | T 序列（5 级） |
| 行政后勤部 | 后勤支持 | F 序列（4 级） |
| 管理总部 | 管理层 | M 序列（5 级） |
| 销售部 | 销售业务 | S 序列（4 级） |

**部门职级字典（`department_rank`，23 条）**：`code` 为职级编码（全公司唯一），`intro` 为职级名称，`type` 为序列（见 `RankTypeEnum`），`department` 为**归属部门**（必填）。

| type | 序列 | 归属部门 | 职级（code） |
| --- | --- | --- | --- |
| 1 | O 一线操作工种 | 生产部 | O1 初级工 / O2 中级工 / O3 高级工 / O4 高级技师 / O5 车间主任 |
| 2 | T 技术/研发 | 技术研发部 | T1 初级工程师 / T2 中级工程师 / T3 高级工程师 / T4 架构师 / T5 首席 |
| 3 | F 后勤 | 行政后勤部 | F1 助理 / F2 专员 / F3 主管 / F4 总监 |
| 4 | M 管理 | 管理总部 | M1 组长 / M2 经理 / M3 总监 / M4 副总 / M5 CEO |
| 5 | S 销售 | 销售部 | S1 销售代表 / S2 销售经理 / S3 区域经理 / S4 销售总监 |

**权限字典（`authority`，seed 24 条）**：`type` 见 `AuthorityTypeEnum`（1 模块分组 / 2 功能页），`mnemonic` 为全公司唯一助记符——**万位即所属模块分组**（如 20003 属于 20000 人力资源），因此功能页不需要额外存父级 ID。`path` 是功能页的路由（**留空 = 该功能页尚未实现**，导航里置灰）。下表是 seed 基线，之后可在「权限管理」（§12.16）里继续新增。

| 模块分组（mnemonic / type=1） | 功能页（mnemonic / type=2，括号内为 path） |
| --- | --- |
| 系统设置（10000） | 10001 系统用户（`/users`）/ 10002 权限管理（`/authorities`）/ 10003 角色管理（`/roles`）/ 10004 流程管理（`/workflows`）/ 10005 公告管理（`/announcements`） |
| 人力资源（20000） | 20001 员工管理（`/employees`）/ 20002 薪酬绩效（—）/ 20003 组织架构（`/departments`） |
| 财务管理（30000） | 30001 应收应付（—）/ 30002 资产清单（—）/ 30003 税务报表（—）/ 30004 预算管控（—）/ 30005 财务计划（—） |
| 生产制造（40000） | 40001 订单管理（—）/ 40002 库存管理（—）/ 40003 生产排程（—）/ 40004 物料需求（—） |
| 战略决策（50000） | 50001 经营报表（—）/ 50002 销售趋势（—） |

- 权限为**两级**结构：模块分组只作菜单分组展示（助记符为整万位），功能页挂在其下
- `role_authority.mnemonic` 引用 `authority.mnemonic`（非主键关联）；分组与功能页可分别授权，前端按万位组织菜单树
- `path` **只对功能页有效**：模块分组是下拉容器、本身没有页面，服务端一律把分组的路由归一成 null（`AuthorityService.normalizePath`），
  不依赖调用方自觉——留着路由会让导航渲染出一个「能点但打不开」的一级入口
- seed 重跑会把 `path` 覆盖回基线值（与 `auth_name` / `type` 同一策略）：它是字典基线，不是运维数据
- **注意**：seed 里 24 条中只有 7 条填了 `path`，其余 12 个功能页都是「规划中」——菜单照常列出但置灰，见 §12.19
- 字典校验测试 `AuthorityDictionaryTest`（4 例）：**seed 基线完整性（包含式，不限制运维新增）**、功能页万位归属、分组助记符整万位与名称抽样核对、**路由约定（分组无路由、功能页路由为站内绝对路径）**

### 12.7 部门与职级的隶属关系（重要业务规则）
- **职级隶属于部门**：`department_rank.department` 非空，不同部门有各自的职级体系；职级编码 `code` 仍保持全公司唯一；实体类 `domain.aggregate.entity.DepartmentRank` 与表 `department_rank` 命名一一对应
- **员工隶属部门**：`employee.department` 关联 `department.id`；员工填写职级时**必须先选部门**，且所选职级必须属于该部门
- 后端强校验集中在 `EmployeeService.ensureRankBelongsToDepartment`（新增/修改均生效），错误提示分别为
  「请先选择所属部门，再选择该部门下的职级」「所选职级不属于该部门，请重新选择职级」
- 前端**级联下拉**（ajax）：`GET /api/options/ranks?department={id}` 按部门返回职级选项
  - 未选部门：职级下拉 `disabled`，提示「请先选择所属部门」
  - 切换部门：重新加载职级并清空原选择，避免残留跨部门职级
  - 因此职级下拉**不再由服务端 `th:each` 渲染**，页面只保留空壳 `<select id="rank" disabled>`
- 页面表单中「所属部门」必须排在「职级」之前，符合「先部门后职级」的填写顺序
- 集成测试（`OptionApiTest` / `EmployeeApiTest`）依赖上述字典数据，故要求先执行 `schema.sql` + 两个 seed 脚本

### 12.8 部门管理与部门职级维护（页面功能）
- 入口：顶部导航「部门管理」→ `/departments`（`PageController` 渲染 `departments.html`，`active = departments`）
- 页面结构：部门列表（不分页，支持按名称模糊查询）+ 部门表单弹窗 + 职级管理弹窗（宽弹窗 `modal__panel--wide`）；
  职级维护以「部门」为入口，弹窗内为「新增/编辑职级表单 + 该部门职级表格」的上下结构，不嵌套二级弹窗
- 部门接口（`DepartmentController`）：
  - `GET /api/departments?keyword=` 列表（附 `rankCount` / `employeeCount` 归属统计，`id` 升序）
  - `GET /api/departments/{id}` 详情；`POST` 新增；`PUT /{id}` 修改；`DELETE /{id}` 删除
- 职级接口（`DepartmentRankController`，嵌套路由表达隶属关系）：
  - `GET /api/departments/{departmentId}/ranks` 列表（按 `type` + `code` 排序）
  - `POST /api/departments/{departmentId}/ranks` 新增；`PUT .../ranks/{rankId}` 修改；`DELETE .../ranks/{rankId}` 删除
  - **归属部门由路径决定，请求体不接受部门参数**；服务端校验职级归属与路径部门一致，不一致返回「该职级不属于当前部门，请刷新页面后重试」
- 校验与删除保护（集中在 Service 层，先于 SQL 给出友好提示）：
  - 部门名称全公司唯一；上级部门必须存在，且**不能选择本部门或其子孙部门**（沿父级链防环）；`parentId` / `description` 可空，实体用 `FieldStrategy.ALWAYS` 支持「保存时清空」
  - 删除部门：存在**子部门 / 职级 / 员工**任一归属时直接拒绝（提示具体数量）
  - 职级编码全公司唯一；序列 `type` 取值限定 `RankTypeEnum`（1~5）；`intro` 可空（ALWAYS 支持清空）
  - 删除职级：已被员工引用时直接拒绝
- 相关测试：`DepartmentApiTest`（9 例）、`DepartmentRankApiTest`（8 例），全部自建数据并随事务回滚，不依赖 seed

### 12.9 用户日程（`user_schedule`）
- 表 `user_schedule`（实体 `domain.aggregate.entity.UserSchedule`）字段与语义：
  | 列 | Java 类型 | 可空 | 说明 |
  | --- | --- | --- | --- |
  | `id` | `Long` | 否 | 自增主键 |
  | `user_id` | `Integer` | 否 | 用户ID，关联 `"user".id`（不建外键，仅普通索引） |
  | `schedule_time` | `LocalDateTime` | 否 | **预约时间**（日程开始时间） |
  | `remind_time` | `LocalDateTime` | 是 | **提醒时间**，为空表示不提醒，**不要落成 0 值或当前时间** |
  | `title` | `String` | 否 | 标题，`varchar(128)` |
  | `intro` | `String` | 是 | 简介，`varchar(255)` |
  | `create_time` | `LocalDateTime` | 否 | 创建时间，`FieldFill.INSERT` 自动填充 |
- 只有 `create_time`，**没有 `update_time`**：日程支持编辑但不记录「最后修改时间」；若将来需要留痕，需同步补列与实体字段
- `remind_time` / `intro` 用 `updateStrategy = FieldStrategy.ALWAYS`：编辑时传 null 即「取消提醒」「清空简介」（两列均可空，不存在与 NOT NULL 冲突的问题）
- 索引按访问路径设计：`(user_id, schedule_time)` 支撑「我的日程列表（按预约时间排序）」，`remind_time` 支撑「扫描待提醒日程（`remind_time <= now()`）」
- 相关测试：`DomainPersistenceTest#insertUserScheduleWorks`（主键回填、`create_time` 自动填充、`LocalDateTime` 往返一致、`remind_time` 留空为 `null`）

### 12.10 公告（`announcement`）
- 表 `announcement`（实体 `domain.aggregate.entity.Announcement`）字段与语义：
  | 列 | Java 类型 | 可空 | 说明 |
  | --- | --- | --- | --- |
  | `id` | `Long` | 否 | 自增主键 |
  | `title` | `String` | 否 | 标题，`varchar(200)` |
  | `content` | `String` | 否 | 正文，`text`（长文本，不设列长度上限）；**富文本 HTML**，见 §12.11 |
  | `attachment` | `String` | 是 | **单个**附件，存文件路径或 URL（`varchar(255)`），为空表示无附件 |
  | `status` | `Integer` | 否 | 状态，见 `AnnouncementStatusEnum`，默认 **1 草稿** |
  | `publish_time` | `LocalDateTime` | 是 | 发布时间：**草稿态为空**，发布时写入 |
  | `create_time` | `LocalDateTime` | 否 | 创建时间，`FieldFill.INSERT` 自动填充 |
  | `author` | `String` | 否 | 作者，存**姓名快照**（`varchar(64)`），不关联用户ID |
- 状态取值 `AnnouncementStatusEnum`：`1 草稿` → `2 已发布` → `3 已下线`，生命周期单向推进，取值变化时需同步 `publish_time`
- **作者为什么存姓名而不是用户ID**：公告是「已发出的事实」，姓名快照保证人员改名或离职后历史公告仍显示当时的署名；代价是无法按作者做规范化筛选，若后续要做「我发布的公告」需另加 `author_id` 列
- **该表没有 `update_time`**：公告支持编辑但不记录「最后修改时间」；若将来需要留痕，需同步补列与实体字段
- `attachment` 用 `updateStrategy = FieldStrategy.ALWAYS`：编辑时传 null 即「移除附件」；`publish_time` 保持默认策略（null 不参与更新），因此编辑不会抹掉首次发布时间
- 索引按访问路径设计：`(status, publish_time)` 支撑「已发布公告列表（`status = ?` 按发布时间倒序）」，`create_time` 支撑后台按创建时间浏览全部公告
- 附件是**单值列**而非子表：一条公告最多一个附件；若将来需要多附件，再拆 `announcement_attachment` 子表并迁移该列
- **`content` 存的是富文本 HTML**（2026-10-16 起）：老数据仍是纯文本，两种形态在库里共存，渲染侧按是否含块级标签分流（见 §12.11）
- 相关测试：`DomainPersistenceTest#insertAnnouncementWorks`（草稿态 `publish_time`/`attachment` 为 `null`、已发布态时间与附件往返一致、`create_time` 自动填充）

### 12.11 公告管理（页面与接口）
- 入口：顶部导航「公告管理」→ `/announcements`（`active = announcements`）
- 接口（`AnnouncementController`，`/api/announcements`）：
  - `GET /api/announcements?current=&size=&keyword=&status=` 分页列表（按 `create_time` 倒序，附 `statusText`）
  - `GET /api/announcements/recent?limit=10` 最近的**已发布**公告（发布时间倒序，供欢迎页）
  - `GET /{id}` 详情；`POST` 新增；`PUT /{id}` 修改；`DELETE /{id}` 删除
  - `POST /{id}/publish` / `POST /{id}/offline` 发布与下线——列表行内直接操作，**不必把整篇正文回传**
- **作者由服务端写入**：新增时取会话中的登录用户名，请求体里的 `author` 会被忽略，避免伪造署名
- **发布时间只在首次发布时落定**：`publish` 时若已有 `publish_time` 则沿用（下线后重新发布不会刷新时间），保证「什么时候发出去的」可追溯
- 状态流转校验（`AnnouncementService`）：重复发布 → 「该公告已是发布状态，无需重复发布」；下线草稿 → 「只有已发布的公告才能下线」
- 前端交互：列表行按状态给出「发布」或「下线」按钮；新建弹窗底部是「保存草稿 / 保存并发布」两个提交按钮，编辑时只保留「保存」且**不传 `status`**，避免「只想改错别字却把草稿发出去了」
- **正文是富文本**（2026-10-16 起）：编辑用本地内置的 Quill 2（`static/vendor/quill/`），渲染前用本地内置的 DOMPurify 清洗
  - **三处白名单必须一致**：编辑器工具栏与 `formats`（`announcements.js`）、前端 `RICH_ALLOWED_TAGS` / `RICH_ALLOWED_ATTR`（`app.js`）、服务端 `RichTextUtils.SAFELIST`。要放开新格式必须三处同改，否则内容会被静默丢掉
  - **当前是「完整经典」工具栏**（2026-10-18 起，此前为只开放四种排版的「精简」方案）：标题 1~3 级 / 加粗·斜体·下划线·删除线 / 上下标 / 文字颜色·背景色 / 对齐 / 缩进 / 有序·无序列表 / 引用 / 代码块 / 链接 / 清除格式
    - 标签：`p br strong b em i u s sub sup h1 h2 h3 ol ul li blockquote pre code a span`
    - 属性只放行四类：`a[href/target/rel]`、`span[style]`、块级元素 `[class]`
    - **属性是「先放行、再按值过滤」**（`RichTextUtils.filterStyle` / `filterClass`）：`style` 只留 `color` / `background-color` / `text-align` 三条声明且值必须匹配十六进制或 `rgb()/rgba()`，`class` 只留 `ql-align-*` / `ql-indent-1~8` / `ql-syntax`。整包放行 `style`/`class` 等于把「长什么样」交给正文作者：`style` 是 XSS 经典载体，`class` 能命中本站已有样式（藏元素、把正文伪装成按钮）
    - 链接协议与 Quill 的 `PROTOCOL_WHITELIST` 对齐（`http/https/mailto/tel/sms`）；`target=_blank` 一律强制补 `rel="noopener noreferrer"`，否则目标页能通过 `window.opener` 反向操纵本页
    - 代码块在编辑器里带 `data-language="plain"`（Quill 加的），服务端不放行该属性 → 每次落库都会被剥掉。这不影响内容，但写断言时不能拿「编辑器输出」和「库里的值」直接做全等比较
  - 编辑器同时收窄 `formats`（不只看工具栏）：工具栏决定按钮，`formats` 才决定「粘贴进来的内容能保留多少格式」；图片/文件的拖入与粘贴在事件层直接拦掉
  - 落库前服务端**再清洗一次**（`RichTextUtils.sanitize`，jsoup `Safelist`）：前端 DOMPurify 只保护「页面自己渲染」这条路，绕过页面直接调接口能塞进 `<script>`，最终防线必须在服务端。清洗输出用 `prettyPrint(false)`，避免 jsoup 补的换行缩进被原样存库
  - 正文长度按**纯文字**计（`RichTextUtils.toPlainText`）：上限 20000 字，排版标记不占额度；`AnnouncementSaveDTO.content` 上的 `@Size(max = 400000)` 只是防滥用粗筛（**20 倍于纯文字上限**：颜色/对齐会让每个字外套一层带 `style` 的 `span`，按原先的 10 万卡会出现「内容不长却报长度超限」）
  - 清洗后没有实际文字的内容被拒（「公告内容不能为空」），防止只存了一堆空标签
  - 存库取 `getSemanticHTML()`：Quill 编辑器内部是 `<li data-list="ordered"><span class="ql-ui">…`，直接取 `innerHTML` 会把私有属性带进库
- **编辑器资源本地内置、不走 CDN**：放 `static/vendor/**`（与 `sm2.js` 同一思路）；该目录已加入 `WebMvcConfig.EXCLUDE_PATTERNS`，与 `/css`、`/js`、`/img` 同级放行
- 列表摘要与欢迎页摘要：正文先经 `App.htmlToText` 转纯文本再截断（40 / 48 字），否则标签会被截进来一起显示
- **查看详情（只读）**：列表只展示标题与 40 字摘要，正文不可见。行内「详情」按钮或**直接点击标题**打开只读弹窗 `#announcementDetailModal`
  - 数据按 id 现取 `GET /api/announcements/{id}`，避免列表缓存与库内不一致
  - 弹窗内只有元信息（状态徽标 / 作者 / 发布时间 / 创建时间 / 公告 ID）+ 正文 + 附件，**无任何输入控件与提交按钮**（只读语义，不复用编辑表单）
  - 正文按内容形态分流（`renderContent`）：含块级标签 → DOMPurify 清洗后 `innerHTML`（`.ann-detail__content` 走常规文档流，另补一套只读排版样式：`p/ul/ol/li/strong/em/u` + 标题 `h1~h3`、`blockquote`、`pre/code`、`a`、`sub/sup`、`ql-align-*`、`ql-indent-*`）。**Quill 自带样式只作用于 `.ql-editor`（编辑器内）**，详情弹窗与公司公告页不在这个作用域里，不补样式的话标题会跟正文一样大、引用与代码块完全看不出区别；不含标签 → 原样 `textContent` 并加 `.ann-detail__content--plain`，用 `white-space: pre-wrap` **保住历史纯文本公告的换行与空行**。长内容在 `max-height: 42vh` 内滚动，不撑爆弹窗
  - 空值兜底：草稿的发布时间显示 `—`，无附件显示「该公告没有附件」
  - 关闭方式与编辑弹窗统一：× / 关闭按钮 / 点遮罩 / `Esc`（`bindDismiss(modal, onClose)` 两个弹窗共用）
- **从详情弹窗直接转编辑**（2026-10-16 起）：详情弹窗 footer 是「编辑 + 关闭」，点「编辑」先 `closeDetailModal()` 再按缓存的 `state.detailRecord` 打开编辑弹窗并回填——**不再多发一次 `GET /{id}`**，因为详情数据刚取过
- **编辑回填必须把纯文本转成 HTML 段落**（`plainTextToHtml`）：历史公告正文是含换行的纯文本，若直接塞进 Quill 会被当成**单个段落**，换行全部丢失。转换规则：
  - 先把 `\r\n` 归一成 `\n`，去掉末尾空行，再 `split('\n')` 逐行成段
  - 普通行 → `<p>转义后的行</p>`；空行 → `<p><br></p>`（**不能靠 Quill 保留空行**，它会把连续 `<br>` 归一并压掉空段）
  - 输入全是空白时函数**自身返回空串**，避免产出 `<p><br></p>` 这种「看着有内容」的假数据
  - `setEditorContent` 按「是否含块级标签」分流：含标签 → 直接 `dangerouslyPasteHTML`；不含 → 先过 `plainTextToHtml`
  - 已知副作用：Quill 会把 `<br>` **归一成多个独立 `<p>`**，所以「一行 `<br>`」与「两行 `<p>`」在编辑器里视觉一致但 DOM 不同；写断言时不要断言 `<br>` 数量为 0
- **列表操作列吸附右侧**（2026-10-16 起）：`1440/1280` 宽度下操作列原本会被裁掉（实测溢出 256px），导致「有编辑按钮但点不到」。`.table-wrap .table` 最后一列 `position: sticky; right: 0`，并给表头、hover 态、左侧阴影补齐背景，滚动时操作列常驻可见
- 标题列限宽 `.ann-cell { max-width: 318px }`：这是按**当前最长标题 23 字 ≈ 渲染 311px** 反推的值（内边距收窄到 12px 后），改小会把长标题截断。加吸附列后总宽收窄，需与标题限宽一起调，二者是耦合的
- 时间列统一 `formatListTime` **去掉秒**（`yyyy-MM-dd HH:mm`）：秒对列表无信息量，且会把时间列撑宽
- 相关测试：`AnnouncementApiTest` 覆盖详情弹窗编辑按钮存在性

### 12.12 我的日程（页面与接口）
- 入口：顶部导航「我的日程」→ `/schedules`（`active = schedules`）
- 接口（`ScheduleController`，`/api/schedules`）：`GET ?month=yyyy-MM&keyword=`、`GET /{id}`、`POST`、`PUT /{id}`、`DELETE /{id}`；`month` 为空取当前月
- **用户ID一律取会话**：所有读写都带 `user_id` 条件（`ScheduleService.requireOwned`），别人的日程既查不到也改不了；提示统一为「日程不存在或不属于当前登录用户」，不额外暴露记录是否存在
- `month` 格式非法直接拒绝（「月份格式不正确，应为 yyyy-MM」），避免静默返回全量数据
- 时间校验：`remindTime` 不为空时不能晚于 `scheduleTime`；`remindTime` / `intro` 传 null 即清空
- **提醒时间用「提前量」下拉，不用自由时刻输入**（2026-10-14 起）：选项为 开始时 / 前5分钟 / 前10分钟 / 前15分钟 / 前30分钟（值 = 提前分钟数 0/5/10/15/30），**提醒时刻由前端换算**：`提醒时刻 = 预约时间 - 提前量`，并实时预览（`#remindPreview`）；改预约时间时提醒自动跟随
  - 新建默认「前15分钟」；编辑时按 `预约时间 - remindTime` 反解提前量回填
  - 兜底档仅在需要时出现：反解值不在预设内追加「自定义（前N分钟）」，日程未设提醒则追加「不提醒」——避免编辑一次就把既有提醒静默改写
  - 公共能力收敛在 `app.js`：`App.REMIND_PRESETS` / `App.DEFAULT_REMIND_MINUTES` / `App.shiftMinutes` / `App.remindOffsetMinutes` / `App.remindLabel`，勿在页面脚本里重写
  - 列表与欢迎页日程条目优先展示预设文案（如「前30分钟 09:00:00」「前15分钟提醒」）
- 前端：`<input type="month">` + 月历 + 当月表格；点击月历日期只筛当天（再点一次取消筛选）；新建的日程若落在其他月份，保存后自动切到那个月，避免「保存完看不到」
- `datetime-local` 控件与后端格式的转换统一走 `App.toDateTimeInputValue` / `App.fromDateTimeInputValue`
  （`yyyy-MM-ddTHH:mm` ↔ `yyyy-MM-dd HH:mm:ss`），不要在页面脚本里各写一遍

### 12.13 个人工作台（欢迎页）
- 定位：`/home` **只展示与登录人相关的内容**，已移除员工总数、部门数量、系统用户等与个人无关的统计与架构介绍卡片
- 页面只渲染用户名、角色、今天日期；三块数据全部 ajax 拉取：
  1. **我的登录记录**：`GET /api/dashboard/login-trend?days=10` → 近 10 天每日登录次数 + 最近一次登录（时间/IP）；**没有登录的日期补 0**，保证折线横轴连续
  2. **我的日历**：复用 `GET /api/schedules?month=`，圆点表示当天有日程，默认选中今天并展示当天安排
  3. **发布的公告**：`GET /api/announcements/recent?limit=5`，发布时间倒序，摘要单行省略（与日历卡片高度接近）
- 折线图为 `home.js` **自绘 SVG**（网格线 + `polyline` + `circle`，圆点带 `<title>` 悬浮提示），**不引入任何外链图表库**（项目禁止 CDN 外链）；容器宽度变化时按缓存数据重绘，避免拉伸变形
- 月历渲染收敛在 `app.js` 的 `App.buildCalendar({month, markedDates, today, selected, maxDots})`，日程页与欢迎页共用，避免两份实现
- **日历与公告两张卡片必须等高**：`.dash-grid` 用 `align-items: stretch`，卡片内 `.day-schedules` / `.ann-list` 弹性伸展 + 内部滚动吸收余量；公告摘要 `.ann-list__meta--summary` 单行省略保持条目均匀（2026-10-14 调整）
- 测试：`DashboardApiTest`（6 例：趋势连续性与今天计数、只统计本人、天数钳制 1~90、欢迎页内容与登录保护）


### 12.14 用户管理（系统账号 `"user"` + `user_account`）
- 入口：导航「用户管理」→ `/users`；页面 = 分页列表 + 新增/编辑弹窗 + 设置角色弹窗
- 身份两个字段各有分工，**不可混用**：
  - `user.username`：**展示名**，导航/欢迎页显示的名字，允许中文、可随时修改，登录不认它
  - `user_account.identifier`：**登录标识**，全公司唯一（`uk_user_account_identifier`），登录时按它查账号
- 接口：`GET /api/users`（分页 + 关键字/角色/状态过滤）、`GET /api/users/{id}`、
  `POST /api/users`、`PUT /api/users/{id}`、`POST /api/users/{id}/role`（设置角色，`roleId=null` 即取消绑定）、
  `POST /api/users/{id}/password/reset`、`POST /api/users/{id}/freeze`、`POST /api/users/{id}/unfreeze`
- **密码一律不由管理员指定**：新用户与「重置密码」统一写入系统初始密码
  （`app.user.default-password`，默认 `Init@123456`，生产用环境变量 `USER_DEFAULT_PASSWORD` 覆盖），
  BCrypt 散列后存 `user_account.credential`；**接口与页面都不下发该明文**，页面只写「请向管理员索取初始密码」
- **不提供物理删除**：登录记录（`user_login_record`）等审计数据按 `user_id` 关联，删除会让留痕断链。
  废弃账号走「冻结」：`AuthService` 登录时校验状态并返回「账号已被冻结，请联系管理员」；
  冻结自**下次登录**起生效，已建立的会话不受影响（如需强制下线，需另加会话级状态校验）
- 防呆：**禁止冻结当前登录账号**（服务端按会话 userId 校验，前端同时把按钮置灰）
- 关键字命中登录标识的做法：先按 `identifier like` 查出 `user_id` 集合，再用 `in` 过滤，
  **不用 `inSql` 拼字符串**（避免 SQL 注入面）
- 半初始化自愈：用户行存在但没有凭证时，「修改用户」与「重置密码」都会补建 `user_account`
- 测试：`UserApiTest`（12 例：统一初始密码可登录、展示名/登录标识唯一、中文展示名不能登录、
  设置/取消角色、重置密码、冻结后登录被拒、禁止冻结自己、关键字/角色/状态筛选、页面渲染）


### 12.15 角色管理与权限挂载（`role` / `authority` / `role_authority`）
- 入口：导航「角色管理」→ `/roles`；页面 = 角色列表（带统计）+ 表单弹窗 + 权限树弹窗
- 接口：`GET /api/roles`（列表，带 `userCount`/`authorityCount`）、`GET /api/roles/{id}`、
  `POST /api/roles`、`PUT /api/roles/{id}`、`DELETE /api/roles/{id}`、
  `GET /api/roles/{id}/authorities`、`PUT /api/roles/{id}/authorities`（全量覆盖）
- 权限树：`GET /api/authorities` 返回「模块分组 → 功能页」两级结构（`AuthorityTreeVO`）。
  权限表**不存父级 ID**，功能页靠助记符万位归组（`AuthorityService.groupMnemonicOf`：10001 → 10000）；
  找不到分组的孤儿功能页只记 warn 并在树上跳过，不下发也授权不了
- **挂载按助记符**（`role_authority.mnemonic` → `authority.mnemonic`），与权限主键解耦，权限字典重建后挂载关系仍有效
- **父子联动（前后端双重保障）**，`RoleService.saveAuthorities` 保存前的归一化规则：
  1. 勾了分组且其下一个功能页都没勾 → 整组授权，补全该分组下全部功能页（「勾父 = 全选子」）；
  2. 勾了任意功能页 → 自动补上所属分组（否则菜单不显示、页面等于进不去）；
  3. 勾了分组又只保留部分子页（勾父后取消几个子项）→ **不**补回兄弟页面，细粒度授权不丢；
  4. 助记符不存在 → 直接拒绝（`权限不存在，请刷新页面后重试：xxx`）
  前端勾选框同样联动：勾分组全选子页、子页全取消则分组取消、部分勾选时分组呈 `indeterminate`（半选，保存时一并提交）
- 删除角色：有用户引用时拒绝（提示还有几个用户）；删除成功时**连带清理该角色的 `role_authority` 记录**
- 角色状态 `RoleStatusEnum`：1 启用 / 2 停用（`role.status`，`AdminAccountInitializer` 创建的角色为启用）
- 说明：`AuthorityTypeEnum`（1 模块分组 / 2 功能页）用于组织权限树；权限字典由 `docs/db/seed-authority.sql`（5 分组 + 18 功能页）初始化，**之后可通过「权限管理」页维护**（见 §12.16）
- 测试：`RoleApiTest`（12 例：CRUD、名称唯一、在用角色不可删、单页授权不扩大兄弟页、整组授权补全、
  全量覆盖、清空、非法助记符、删除连带清理挂载、列表统计、页面渲染）

### 12.16 权限管理（`authority` 的增删改查）
- 入口：导航「权限管理」→ `/authorities`；页面 = 树形表格（分组行加粗 + 功能页行缩进带 `└`）+ 表单弹窗
- 接口（`AuthorityController`，`/api/authorities`）：
  - `GET /api/authorities?keyword=` 权限树（`AuthorityTreeVO` 含 `id` / `roleCount` / `children`）
  - `GET /{id}` 详情（`AuthorityVO` 额外带 `childCount` / `roleCount`，供编辑回填与依赖提示）
  - `POST` 新增、`PUT /{id}` 修改、`DELETE /{id}` 删除
- **路径参数用主键 `id`**：助记符允许被修改，不适合作稳定标识（`role_authority` 仍按助记符引用）
- **关键字过滤语义**（`AuthorityService.tree`）：命中分组 → 展开其全部功能页；命中功能页 → 只保留命中的那个
  并带出所属分组（父节点不丢，否则子页会变成孤儿）；两者用不同的集合区分，避免「搜一个子页带出一堆兄弟页」
- **写操作三条硬约束**：
  1. **格式自洽**：分组的助记符必须是整万位；功能页不能是整万位，且**其万位分组必须已存在**
     （否则新增出来的是树里看不到、授权不了的孤儿）；助记符全局唯一（应用层前置查重给友好提示）
  2. **改助记符要级联**：修改成功时在**同一事务**里把 `role_authority` 中引用旧助记符的记录一并改成新值，
     角色已有的授权不会断链（用户拍板：允许改且自动同步）。带子页的**分组**不允许改万位——静默迁移子页
     等于改动多个权限的业务键，风险更大，直接拒绝并提示先调整子页
  3. **删除要有保护**：被任意角色引用 → 拒绝（提示「已被 N 个角色引用」）；分组下还有功能页 → 拒绝。
     与部门/职级/角色的删除保护一致。前端会用列表里的 `roleCount` / 子页数**先本地拦一次**，
     服务端仍是最终权威（绕过页面直接调接口一样被拒）
- 前端交互：类型切换时「所属模块分组」下拉按需显隐；分组行的「加功能页」按钮会预选该分组并自动给出
  下一个可用助记符（该分组下最大功能页 +1）；手改助记符时反向同步分组下拉；提交前做与后端同规则的格式校验
- `authority.description` 用 `FieldStrategy.ALWAYS`：表单留空即清空
- **路由（`path`）在同一个弹窗里维护**：类型切到「功能页」时才显示，切回「分组」会清空输入框；
  列表不单开一列，而是把路由缀在权限名称下方（`/employees` 等宽字体；未设路由的用告警色标「未设路由 · 导航置灰」）——
  这张表已有 7 列，「说明」一列本就把表撑得很宽，再加列会让操作列更早被挤出可视区
- **路由格式校验**（两边同规则）：可空；非空时必须以 `/` 开头、不含空白、不超 128。
  - 相对路径被拒的理由：它会跟着当前页 URL 变，同一个菜单项在不同页面点开跳到不同地方
  - 空串与留空等价（服务端 `trimToNull` 归一成 null）：库里只允许 `null` 一种「没有路由」，否则判空到处都要写两遍
- 测试：`AuthorityApiTest`（11 例：登录保护、万位归组、助记符唯一、节点带 id/roleCount、关键字过滤、
  CRUD 全流程、格式与归属校验、**路由存取与归一（分组强制置空、留空即清空）**、**路由格式校验**、
  改助记符级联同步 role_authority、删除保护、页面渲染）；
  `AuthorityDictionaryTest`（4 例，含路由约定）改为**包含式**断言 seed 的 24 条基线完整，不再限制运维后新增的权限


### 12.17 考勤（打卡 + 每日统计）

**两张表的分工是「明细 → 汇总」**：`attendance_record` 存原始打卡（一次刷卡一行，只追加），`attendance` 存按人按天的统计（一人一天一行，可重算）。规则（几点算迟到、加班怎么算）随时会变，而原始记录一旦落库就不该再改——改规则时重算汇总即可，不必动明细。

#### 打卡接口
- `POST /api/attendance/punch`，入参 `AttendancePunchDTO`：`workNo`（必填，≤32）+ `location`（可选，≤255）
- **打卡时间一律取服务端时钟**（`System.currentTimeMillis()`），入参里**没有** `punchTime` 字段
  - 允许客户端传时间的代价是改一下本机时间就能伪造考勤，而事后从数据上根本看不出哪条是假的；离线设备补录历史应走带审计的独立入口，而不是让常规打卡接口接受任意时间
- 只放行**在职**员工（试用期 / 正式）：工号不存在 → 「工号 X 不存在，无法打卡」；已离职/辞退 → 「员工「X」已离职，无法打卡」。离职人员打卡没有业务意义，只会让当天凭空多一行统计
- 判定统一用 `EmployeeStatusEnum.isActive(Integer)`，不要在各处手写 `status == 0 || status == 1`
- 落库固定 `attendance_record.status = 1 有效`；「无效」是事后作废动作（见下）

**访问控制：不校验登录，按来源网段收敛**
- 打卡接口是**唯一**从 `LoginInterceptor` 管辖里摘出来的 `/api/**` 接口（见 `WebMvcConfig`）。
  理由：调用方是**打卡机这类无会话设备**，让设备先登录既别扭，又会在服务重启/会话过期时
  让打卡静默失败；而登录用户是这个管理软件的管理员（HR、行政），不是员工
- 门禁落在 `AttendancePunchSourceInterceptor`：来源命中 `app.attendance.allowed-networks` 才放行，
  否则 **403 + `code=403`** 并记 `WARN`（带来源地址与生效网段，便于排查）
- **只读 `request.getRemoteAddr()`，绝不读 `X-Forwarded-For`**：透传头由调用方完全可控，
  读它等于把白名单交给对方自己填。`AttendanceApiTest` 里有一条用例专门锁这件事
  （伪造 `X-Forwarded-For: 127.0.0.1` 仍须 403）
- 网段解析在 `infrastructure.utils.IpUtils#parseNetworks`：支持 CIDR、裸 IP（按单地址）、
  IPv4 与 IPv6，并归一 `::ffff:127.0.0.1`（双栈容器里回环常是这个写法）与 `fe80::1%en0`（作用域标识）。
  **非法配置直接抛异常让启动失败**，不允许静默变成「什么都不匹配」（打卡全挂）或「什么都匹配」
- 白名单为空 → **拒绝一切来源**（fail closed）。宁可是「配漏了、打卡立刻全失败且日志写清原因」，
  也不要「接口对全网敞开而无人察觉」
- 默认值收敛到回环，**不含任何办公网段**：放行内网必须靠显式配置，不能靠默认值
- 服务部署在 nginx 之后时，用 `server.forward-headers-strategy=framework` 让容器（只信任直连代理）
  改写 remoteAddr，**不要**把代理地址加进白名单——那等于放行代理背后的所有人
- 遗留：设备身份只能靠 IP 识别，无法区分「哪一台打卡机」。若将来要给每台设备单独发凭证并支持吊销，
  应新增设备表 + `X-Device-Token`，届时白名单退化为第二道防线

#### 每日统计定时任务
- `infrastructure.tasks.AttendanceStatisticsTask`，`@Scheduled(cron = "${app.attendance.statistics-cron:0 0 8 * * ?}")`，**每天 08:00 汇总前一天**
  - 为什么是「昨天」：当天打卡还没结束（可能有人加班到深夜），8 点统计今天等于把半天数据当全天
  - 为什么是 8 点：早于上班时间，员工到岗时昨天的考勤已算好
- 主类已加 `@EnableScheduling`（新增 `@Scheduled` 任务不再需要额外开关）
- 任务只做「挑日子 + 记日志」，判定规则全在 `AttendanceService#statisticsFor(LocalDate)`——日期是入参，人工补算任意一天直接调服务即可，不必绕过调度的壳
- **统计范围是全部在职员工**，不只是当天打过卡的人：没打卡的人同样占一行（`status = 0 缺卡`）。否则「少一行」和「没这个人」无法区分，缺勤根本查不出来
- **判定规则**（`app.attendance.work-start` / `work-end`，默认 09:00 / 18:00）：
  | 条件 | `status` | 说明 |
  | --- | --- | --- |
  | 当天无有效打卡 | `0 缺卡` | `first`/`last` 均为 **null**（填 0 会变成 1970-01-01，混进时间字段后任何排序/做差都荒谬） |
  | 最早打卡 **>** 上班时间 | 计入迟到 | 等于上班时间**不算**迟到 |
  | 最晚打卡 **<** 下班时间 | 计入早退 | 等于下班时间**不算**早退 |
  | 两者都中 | `4 迟到且早退` | 组合规则集中在 `AttendanceStatusEnum.of(late, earlyLeave)` |
  | 兜底 | `1 正常` | |
- `first` = 当天最早打卡，`last` = 当天最晚打卡，**与插入顺序无关**（取 min/max）
- `extra`（加班）= `(last - 下班时间) / 3600000` 小时，不足 0 取 0，**保留 2 位小数**（与 `numeric(10,2)` 对齐）
- 一天的时间窗口是**左闭右开** `[当天 00:00, 次日 00:00)`：当天 00:00:00 整算当天，次日 00:00:00 整算次日
- **跨天加班是已知局限**：干到次日凌晨 1 点会落到第二天的窗口，被算成第二天的最早打卡。要正确处理得引入班次（shift）概念，当前表结构里没有这个维度
- **失效记录不参与统计**：只取 `attendance_record.status = 1 有效` 的打卡
- **时区必须显式配置**（`app.attendance.zone`，默认 `Asia/Shanghai`）：容器镜像默认多半是 UTC，用 JVM 默认时区会把早 8 点的卡算到前一天，迟到早退全判错。`@ConfigurationProperties` 里存 String（不是 `ZoneId`），经 `zoneId()` 解析，避免类型绑定的不确定性

#### 幂等与并发
- `attendance` 的业务键是 **`(work_no, day)`**，唯一索引 `uk_attendance_work_no_day`（原普通索引 `idx_attendance_work_no_day` 已升级并移除）
- 写入走 `AttendanceMapper.upsert` 的 `on conflict (work_no, day) do update`：用「先查后插/改」会留出并发窗口，两边都查不到就会插出重复行
- 因此定时任务**可以放心重跑**（多实例同时触发、人工补算、改完上下班时间重算）
- **没有加分布式锁**：多副本 K8s 下每个实例都会在 8 点触发，重复执行的代价只是白算一遍；而引入 Redis 锁反而多出一个「锁没释放导致当天彻底不跑」的故障模式
- `create_time` 只在 insert 分支写入，冲突走 update 时不刷新，保留「这行第一次是什么时候生成的」
- 自定义 `@Insert` 的 SQL **必须自己带 `create_time`**：MyBatis-Plus 的自动填充只在它自身注入的方法里生效
- 整天（`statisticsFor`）在**一个事务**里：几百人规模下代价可忽略，换来要么全算对、要么一行不写，不留算一半的中间状态
- 任务里吞掉异常并记 `ERROR` 日志（带日期便于排查/补算）；判定逻辑本身在 `statisticsFor` 里，异常会正常抛给调用方

#### 配置（`app.attendance.*`）
| 配置 | 默认 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 统计任务开关，关闭后任务只记日志不写库 |
| `work-start` | `09:00` | 上班时间，晚于它记迟到 |
| `work-end` | `18:00` | 下班时间，早于它记早退，晚于它的部分算加班 |
| `zone` | `Asia/Shanghai` | 判定归属哪一天的时区 |
| `statistics-cron` | `0 0 8 * * ?` | 任务执行时间 |
| `allowed-networks` | `127.0.0.1/32,::1/128` | 打卡接口允许的来源网段（CIDR，逗号分隔）。**打卡接口不校验登录，这是它唯一的门禁**；留空即拒绝一切来源 |

> **踩坑**：占位符的默认值**不能加引号**。写成 `${ATTENDANCE_WORK_START:"09:00"}` 时默认值会带上字面引号，绑定 `LocalTime` 直接失败（YAML 不需要引号——值里有 `${` 就已是字符串，不会被当成时间量）。

#### 测试
- `AttendanceApiTest`（14 例）：**免登录即可打卡**、**默认白名单只有回环**、来源不在网段内 403、
  **伪造 `X-Forwarded-For` 不能绕过**、IPv4 映射与 IPv6 回环都命中、在职打卡成功（落一条有效记录 +
  时间取服务端时钟 + 回参与落库一致）、试用期可打卡且位置为空落 null、**客户端传的 `punchTime` 被忽略**（防伪造）、
  工号不存在、离职/辞退被拒、工号为空与超长、位置超长、缺请求体判 400
- `AttendancePunchSourceTest`（4 例）：整体替换 `app.attendance.allowed-networks` 后断言
  **配置项确实生效**（网段内放行、网段外 403、回环仍可用）。单元测试证明不了「配置被读到」这件事，
  这类「配了但不生效」的故障只会表现成「内网全部打不上卡」
- `IpNetworkTest`（15 例）：IPv4/IPv6 网段、非整字节前缀（`/25`）、主机位掩码归一、`/0` 标记、
  裸 IP 等价 `/32`、IPv4 映射写法归一、作用域标识、协议族互不匹配、非法配置抛异常、非法地址不匹配
- `AttendanceStatisticsTaskTest`（15 例）：最早/最晚与插入顺序无关、零点归属、左右边界、正常/迟到/早退/迟到且早退/缺卡、单次打卡同时算迟到早退、无效记录不参与、有效无效混合只按有效判定、只统计在职员工、**重跑幂等且覆盖**、定时任务汇总昨天 + 开关关闭不写库、`yyyyMMdd` 补零
- `UniqueConstraintTest` 补 `attendance.work_no` 唯一索引元数据断言（5 个业务键）

### 12.18 员工薪酬（`employee_salary`）

**入口位置**：员工管理页列表行内的「薪酬」按钮 → 薪酬弹窗。不做独立菜单页——薪酬必须依附于某个具体员工，
脱离员工的薪酬列表没有业务含义；也**不并进员工编辑弹窗**：人事信息与薪酬的敏感度不同，
改个电话不该顺带暴露年薪。

#### 接口
- `GET /api/employees/{employeeId}/salary`，`employeeId` 是**员工主键**
- `PUT /api/employees/{employeeId}/salary`，入参 `EmployeeSalarySaveDTO`：`currency` + `monthSalary` / `yearSalary` / `daySalary`
- 做成员工的子资源而不是独立的 `/api/salaries`：URL 上带着员工主键，服务端能顺手校验「这个员工真的存在」
- **未设置过时 `data` 为空**（不是一条全 0 记录）：「没设过」和「设成 0」是两件事，
  前者前端提示「尚未设置」，后者是明确的业务值
- 沿用 `/api/**` 的登录态拦截，**未登录 401**；保存时把当前登录人写进 `update_by`
- **权限现状**：只校验「已登录」。项目的权限字典（`authority` / `role_authority`）目前不参与请求鉴权，
  所以能登录的人就能改薪酬。权限字典里已有 `20002 薪酬绩效`，将来要做接口级鉴权可直接挂上去

#### 关联键与级联（必须知道）
- **关联键是工号不是员工主键**：`employee_salary.work_no` 才是这张表的业务键，接口路径上的员工主键
  由 `EmployeeSalaryService` 换算成工号。这样换主键不影响薪酬数据
- **删除员工必须连带删薪酬**：`EmployeeService.delete` 在同事务里按工号清理。
  不清理就会留下孤儿行——员工工号是业务键、现实中会被再次启用，新员工届时会**静默继承前任的薪酬**，
  这种错不报任何异常，只会在某天被 HR 发现

#### 一人一行与幂等
- 业务键 `work_no`，唯一索引 `uk_employee_salary_work_no`（原普通索引 `idx_employee_salary_work_no` 已升级并移除）
- 写入走 `EmployeeSalaryMapper.upsert` 的 `on conflict (work_no) do update`：两个人同时打开同一员工的
  薪酬弹窗时，「都查不到 → 都插入」会插出两行同工号记录，之后谁在改哪一行就说不清了
- `create_time` 只在 insert 分支写（保留「这条薪酬第一次是什么时候设的」），
  `update_time` / `update_by` 每次写都刷新（回答「现在的值是谁什么时候改的」）
- 自定义 `@Insert` 必须自己写 `create_time` / `update_time`：MyBatis-Plus 的自动填充只对它自身注入的方法生效

#### 金额约定
- 列类型 `numeric(18,2)`；DTO 与 VO 都用 `BigDecimal`，落库前 `setScale(2, HALF_UP)`，
  回参统一两位小数（`1000.5` 回成 `1000.50`）
  - 实体沿用既有的 `Double`，但**接口边界不用浮点**：让钱先掉进二进制浮点再落库，
    迟早会出现 `1234.5600000000001` 这类值
- **三个金额都不可缺省**：`0` 表示「该项不适用」是合法语义，所以不能用「没传 = 0」兜底——
  那会让客户端漏传一个字段变成静默清零。宁可 400
- 校验：`@DecimalMin(0)` 拒绝负数，`@Digits(integer = 16, fraction = 2)` 与 `numeric(18,2)` 对齐
- 币种走 `CurrencyEnum` **白名单**而不是格式校验：`currency` 是 varchar，格式校验放得过 `"ABC"`，
  而「支持的币种」本来就是一份封闭清单；入库前 `trim + toUpperCase` 归一
- `currencyText` 由服务端拼好下发：让前端自己维护「码值 → 中文」的映射迟早会与后端枚举漂移

#### 月薪是换算基准（前端）
- 改月薪即刷新年薪（`×12`）与日薪（`÷ 21.75`，月计薪天数），随后**可手工覆盖**
- **不做「三者必须自洽」的校验**：现实里年薪常含年终奖，不等于月薪 × 12
- 换算只在前端做、服务端按传入值原样落库：这样才能容纳手工覆盖；若服务端也按公式重算，
  手工改过的值会被静默改回去
- 弹窗里写明「改这里会同时刷新年薪与日薪」，避免出现「我只改了月薪，年薪怎么也变了」

#### 审计列
- 表原有 `create_time`，补 `update_time` + `update_by`（`user_account.identifier`，登录标识）
- **审计一律用 `identifier`，不用 `user.username`**：后者是可随时改成中文姓名的展示名，
  一改历史记录就对不上人（见 §13.1 的同一条教训）。为此 `CurrentUserVO` 补了 `identifier` 字段
- 已有库补列必须写成 `alter table ... add column if not exists`，且**排在 `comment on column` 之前**：
  `create table if not exists` 对已存在的表不会加列，而注释语句引用不存在的列会直接打断整个脚本

#### 测试
- `EmployeeSalaryApiTest`（13 例）：未登录不可读写、未设置时 data 为空、员工不存在报错、
  保存成功（币种中文 + 操作人 + 恰好一行）、**重复保存覆盖同一行**、
  **覆盖不刷新 create_time**（先用标记时间改写 create_time 再保存一次，而不是比较两次时间差——
  整套用例在同一个事务里，`current_timestamp` 取的是事务开始时间）、金额补两位小数、
  币种白名单、负数与三位小数、金额缺省、币种为空、币种大小写与空格归一、**删除员工级联清理薪酬**

### 12.19 顶部二级导航（由权限字典驱动）

**菜单不再硬编码在模板里**：一级入口是 `authority` 中 `type=1` 的模块分组（5 个），二级是该分组下 `type=2` 的功能页。
菜单结构（有哪些模块、每个模块下有哪些功能页）在权限管理页里本来就要维护一份，硬编码在模板里等于维护两份，
新增功能页时要记得改两处、漏改也不报错。

#### 组成

| 部分 | 来源 | 说明 |
| --- | --- | --- |
| 欢迎页 `/home` | 模板写死 | 固定基础项，不属于任何业务模块 |
| 5 个模块分组 | `navMenus`（`NavigationService`） | 一级，`<button>` 触发器 + 下拉浮层 |
| 分组下的功能页 | 同上 | 二级；`path` 非空渲染成 `<a>`，为空渲染成置灰的 `<span>` |
| 我的 `/profile` `/profile/password` `/notices` `/schedules` | 模板写死 | 固定一级项 + 4 个固定二级项，排在业务模块之后、关于之前 |
| 关于 `/about` | 模板写死 | 固定基础项，排在业务模块之后 |

> 固定基础项（欢迎页 / 我的 / 关于）**不占权限字典**：它们是系统自身页面，与「业务模块能授权给谁」无关，
> 写死反而更稳——否则管理员误删字典里对应条目会连退出登录之外的入口一起消失。
> 「我的」尤其不能进字典：它装的是账号自助能力，一旦按角色配置，漏配某个角色就等于该角色的人**连自己改密码的入口都没有**。
> 详见 §12.22。

#### 装配与注入
- `NavigationService.menus()` 读 `authorityService.listAll()` 装配（单表、二十来行，未加缓存；
  加缓存要处理「权限管理页改完菜单要立刻生效」的一致性问题，为省一次微不足道的查询引入难查的脏数据故障不划算）
- 两类数据跳过并记 warn：找不到所属分组的功能页（孤儿）、一个功能页都没有的分组（点开是空浮层）
- `PageController` 用 `@ModelAttribute("navMenus")` 注入，**每个页面只写一次**，新增页面时不会漏挂；
  只在页面渲染时执行，不影响 REST 接口（facade 层的控制器不会带上这个模型属性）
- `NavItemVO.key` = 路由**首段**（`/employees` → `employees`），与各页面控制器传给 `header` 片段的 `active` 对齐。
  取首段而非整串，是为了让 `/employees/detail` 这类子路由也能点亮「员工管理」这一级
- **例外**：固定项「我的」下的 `/profile` 与 `/profile/password` 同前缀，靠首段会同时点亮两项，
  因此这四项的 `active` 由 `PageController` 显式给语义值（`profile` / `password` / `notices` / `schedules`），
  见 §12.22

#### 「规划中」的功能页：显示但置灰
字典里 19 个功能页中只有 7 个填了 `path`，其余 12 个还没有页面。这类菜单项：
- **照常列出**（菜单要体现系统整体规划，否则「系统到底规划了哪些模块」只能去权限管理页翻）
- **置灰且不可点**：渲染成 `<span class="nav__submenu-link is-off">` 而不是 `<a>`——
  视觉置灰拦不住点击，只要带 `href` 就会 404，必须在标签层面就不是链接
- 可点性**完全来自数据**（`path` 是否为空），不额外维护白名单

#### 交互与样式
- 展开：CSS `:hover` / `:focus-within` 覆盖桌面悬停与键盘 Tab；`app.js` 的 `bindNavMenus()` 补点击开关
  （iOS Safari 点 `<button>` 不给它焦点，只靠 CSS 打不开），点别处或按 `Esc` 收起
- `.nav` 与 `.nav__item` 用 `align-self: stretch` 撑满头部高度：否则浮层与触发器之间会隔着头部内边距留一条缝，
  鼠标移过去的路上菜单就收起来了
- 一级项高亮 = `#lists.contains(group.keys, active)`；二级项高亮 = `key == active`

#### 已知调整
- 菜单文案改由字典决定，与旧导航有几处**名称变化**：`部门管理` → **组织架构**（字典 20003）、`用户管理` → **系统用户**（字典 10001）、`审批规则` → **流程管理**（字典 10004，落地时按实体语义改名，路由 `/workflows`）
- `authority.path` 缺失的 12 个功能页一旦补上页面，**只需要在权限管理页填个路由**，导航自动出现可点入口，不用改代码

#### 测试
- `NavigationServiceTest`（5 例）：层级与升序、可点性与 `key` 来自路由、子路径取首段、整组置灰的分组照样下发、公告管理挂进系统设置
- `NavigationPageTest`（6 例，**真实渲染整页后用 jsoup 按 DOM 结构断言**，不做字符串包含）：
  两级结构、置灰项必须是 `<span>` 且不带 `href`、当前页与所属分组同时高亮且只点亮一个、
  `active` 取首段使分组在子页面也保持高亮、固定基础项在每个页面都在、关于页已加载 `/js/app.js`

> 为什么用 jsoup 而不是 `content().string(containsString(...))`：包含式断言只能证明「页面上出现过这几个字」，
> 证明不了它在正确的位置、是否可点、是否被点亮。`nav__link` 这类 BEM 类名也不能写进 `th:*` 表达式
> （会被 Thymeleaf 当成 `__xxx__` 预处理语法，见 §4），所以断言走独立的 `class` 选择器。

### 12.20 流程管理（审批规则 `workflow` + `workflow_node`）

**定位**：权限字典里的 10004 原名「审批规则」，对应的就是 `workflow` / `workflow_node` 两张表，
落地时按实体语义更名「流程管理」，路由 `/workflows`。它只负责**定义规则**，不负责跑审批——
审批实例是 `work_order`（工单），尚未实现（见文末「已知缺口」）。

**一条规则 = 一个流程头（`workflow`）+ 一串有序节点（`workflow_node`）**，形状固定为
「起始 → 若干审批 → 结束」。节点数量、类型、审批配置全部服务端校验，任一条不满足直接拒绝：

| 约束 | 说明 |
| --- | --- |
| 节点数 ≥ 2 | 只有起始 + 结束也合法（等同于「提交即结束」） |
| 起始节点恰好 1 个且排第一 | `type=0` |
| 结束节点恰好 1 个且排最后 | `type=2` |
| 审批节点必须有审批方式 | `approval_type` 见 `ApprovalTypeEnum` |
| 审批节点必须有审批对象 | 指定人员 → `"user".id`；指定角色 → `role.id`；**必须在库里真实存在** |
| 审批节点必须有审批模式 | `approval_mode` 见 `ApprovalModeEnum`（或签 / 会签） |
| 起始与结束节点**不带**审批配置 | 客户端硬塞进来也由服务端归一成 `null`（实体上是 `FieldStrategy.ALWAYS`，置 null 即清空历史值） |

**节点顺序不由客户端传**：直接取 `nodes` 数组下标，服务端统一重排为 `0,1,2…`。
顺序本身就是数组，再传一份 `sort` 只会出现两份可以互相矛盾的次序。

**流程名称全公司唯一**（`uk_workflow_name`）：列表里出现两条同名流程，审批人分不清该提交哪一个。
服务侧 `WorkflowService#ensureNameUnique` 另做一次前置校验，只为给出可读提示，真正的兜底在库侧。

#### 节点写入是差量同步，不是整批重建
`WorkflowService#syncNodes`：**有 `id` 的改、无 `id` 的插、本次未提交的删**。
不用「先按 `workflow_id` 全删再插」的原因：`work_order.create_node_id` 指向具体节点，
整批重建会让在途工单的当前节点变成悬空引用——不报错，只是那张单子再也推不动。

两处配套校验：
- 提交了**不属于本流程**的节点 `id` → 400「节点数据已变化（id=x），请刷新页面后重新编辑」，
  而不是悄悄改到别的流程上
- **重复执行**同一份提交是幂等的：第二次提交同样的节点集合，既不新增也不删除

> 踩过的坑：实体上 `workflow_id` 是 `FieldStrategy.ALWAYS`，更新分支若不带值会把列写成 `NULL`，
> 而它是 `not null`，报出来是一个看不懂的约束冲突。所以同步时**统一先补齐 `workflowId`** 再决定 insert / update。

#### 「直属上级」当前不可用
`ApprovalTypeEnum.SUPERIOR(2)` 需要「员工的上级是谁」这份数据，而 `employee` 表没有上级字段、
`user` 与 `employee` 之间也没有关联列——即便配出来，运行时也找不到该由谁来点通过，
只会得到一条永远推不动的规则。因此枚举里保留这个码值（字典基线不残缺），
但 `isSupported()` 返回 `false`：页面把它渲染成 `disabled` 选项并标注「暂不支持」，
服务端保存时直接拒绝并提示改用「指定人员」或「指定角色」。

#### 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/workflows` | 分页（`current` / `size` / `keyword` 匹配名称与描述 / `status`），列表**不带节点明细**，只有 `nodeSummary` 摘要 |
| GET | `/api/workflows/{id}` | 详情，带完整节点链与可读的审批人 / 角色名 |
| POST | `/api/workflows` | 新增（流程头 + 节点链整体提交，单事务），返回流程ID |
| PUT | `/api/workflows/{id}` | 修改（整体覆盖，节点走差量同步） |
| DELETE | `/api/workflows/{id}` | 删除，**同事务级联删掉全部节点**（孤儿节点查不出来也删不掉，只会越积越多） |
| POST | `/api/workflows/{id}/status` | 启用 / 禁用（列表行内快捷操作）；重复设置同一状态直接提示 |
| GET | `/api/options/users` | 审批人候选：**只列正常状态的系统账号**（冻结账号连登录都过不去，选它等于把流程卡死） |

- 列表不塞节点明细：每条流程都带一遍节点，翻一页要多传几十个对象，而列表上一个字都用不到
- 一次把这页的节点全查回来再按 `workflow_id` 分组，避免 N+1
- `approval_values` 落库是逗号分隔的字符串（与列注释一致）；读取时非法段跳过并记 warn，
  已删除的对象在展示层保留 `#id` 而不是留白

#### 页面与交互（`workflows.html` / `workflows.js`）
- 列表列：ID / 流程名称 / 说明 / 节点链路 / 状态 / 更新时间 / 操作（详情 · 编辑 · 启用|禁用 · 删除）
- 编辑弹窗内完成**流程信息 + 节点编排**，不跳第二个页面；节点链只有一条，分开保存会出现「流程已改、节点还是老的」这种半截状态
- 起始 / 结束节点是**固定行**：守在首尾，不可增删移动，只允许改名称（默认「发起申请」「审批完成」）
- 审批节点可增删、可上下移动；上移 / 下移只在审批节点区间内交换，越过起始或结束会被拦下
- 审批对象用原生 `<select multiple>`（零依赖）；按住 Ctrl / Cmd 多选
- **已配置但当前不在候选里的值**（账号被冻结、角色被删）会补一个「#id（已不可选）」并保持选中：
  不补的话，用户只是打开弹窗再保存，这条审批人就被静默抹掉了，而这种丢失不会报任何错
- 前端校验只为省掉一次注定失败的请求，服务端有同一套规则做真正的兜底

#### 测试
- `WorkflowApiTest`（15 例）：未登录全接口 401 且不动数据、节点按序落库、
  起始/结束的审批配置被清空、重名拒绝、**节点链 5 类畸形形态**、审批节点三要素缺失、
  「直属上级」被拒、审批人/角色不存在、**差量同步保住老节点 id**、外来节点 id 被拒、
  详情可读文案与列表不带明细、按状态筛选、启停规则、级联删除、流程不存在
- `AuthorityDictionaryTest` / `NavigationServiceTest` / `NavigationPageTest` 同步更新：
  10004 改为「流程管理」并断言路由为 `/workflows`；原「审批规则置灰」的断言改用「薪酬绩效」，
  并新增「流程管理是链接且不置灰」
- 真实浏览器 E2E 33/33：入口可点与高亮、节点编排交互（增删移 / 审批方式切换 / 直属上级置灰 /
  候选随方式切换而清空）、新增-详情-编辑回填-启停-重名-删除全链路、
  **1440 / 1280 / 1120 三档均无横向溢出且操作列按钮在视口内**

#### 已知缺口
- **删除流程没有校验 `work_order` 是否引用它**：工单功能尚未实现（只有建表语句与实体，
  没有 Mapper / Service / 页面），表里恒为空。等工单落地后必须在这里补上引用校验，
  否则会把在途的单子指向一个已不存在的流程
- 流程头还有一列 `template`（流程模板 JSON）本轮未启用：当前没有任何地方生成或消费它，
  因此编辑弹窗不触碰该字段（表单范围外，不做「顺手清空」）

### 12.21 分页返回结构 `PageResult` 的类型契约（重要）

所有分页接口统一返回 `PageResult<T>`：`{ total, current, size, records }`，四个分页页面
（`employees` / `users` / `announcements` / `workflows`）都消费这个结构。

**`total` / `current` / `size` 是 JSON 数字，不是字符串**——这是全局 Jackson 策略的一处**有意例外**：

- 全局把 `Long` / `long` 序列化成字符串，目的是防雪花 ID 在前端 JS 侧丢精度（见 §11.4 `JacksonConfig`）；
- 分页计数恰好是原生 `long`，会被这条策略连带成字符串。此时前端 `state.current + 1` 走的是
  **字符串拼接**：`"1" + 1 === "11"`，点一次「下一页」就去请求 `current=11`，页面显示 `11 / 2` 且列表为空。
  而「上一页」用的是减号（`"2" - 1 === 1`），所以**只有下一页会坏**，上一页看起来是好的；
- 计数的量级（行数、页码）离 `2^53` 差着十几个数量级，不存在精度问题，因此在 `PageResult`
  上显式指定了 `CountAsNumber` 序列化器，把三个计数还原成数字。想要新增数字字段时**不要照抄这个注解**，
  先确认它确实是与 ID 同量级的计数，而不是业务数据。

**双保险**：前端四个分页脚本仍统一写 `Number(data.total / current / size)`。
后端已经保证是数字，这层转换是对接口类型的兜底——两者都不该去掉，任何一侧被改回去都不会再让翻页坏掉。

**测试**：`EmployeeApiTest#pageCountersAreJsonNumbers` 断言这三个字段是 `Number` 而非 `String`，
它在这条注解加上之前是**红的**，是这条契约的守门用例。

#### 排查提示
页面出现「点下一页提示空列表 / 页码变成 11 / 2」这类现象时，先看网络面板里请求的 `current` 参数：
若是拼接出来的畸形页码，就是报文类型问题，而不是分页 SQL 或数据问题。

### 12.22 「我的」固定导航与账号自助（个人信息 / 修改密码 / 公司公告）

顶部一级「我的」是**固定项**（不进权限字典，理由见 §12.19），下挂四个二级项：

| 二级项 | 路由 | `active` | 接口 |
| --- | --- | --- | --- |
| 个人信息 | `/profile` | `profile` | `GET/PUT /api/profile` |
| 修改密码 | `/profile/password` | `password` | `PUT /api/profile/password` |
| 公司公告 | `/notices` | `notices` | `GET /api/notices`、`GET /api/notices/{id}` |
| 我的日程 | `/schedules` | `schedules` | 见 §12.12（原本是顶级项，此轮收进「我的」） |

#### 一级菜单高亮不能靠路由首段
`/profile` 与 `/profile/password` 首段相同，若沿用 §12.19 的「首段 = key」规则会**同时点亮两项**。
因此这四项的 `active` 一律由各 `@GetMapping` 显式指定（`PageController` 里的 `VIEW_PROFILE` / `VIEW_PASSWORD` / `VIEW_NOTICES` 常量），
模板里一级「我的」用 `or` 聚合四个值判定 `is-active`。
`NavigationPageTest#mineItemsLightUpOneAtATime` 锁死这条：一次只允许亮一个二级项，且不误亮业务分组。

#### 个人信息：三项可改，其余只读
- 可改：`username`（显示名）、`mobile`、`email`
- 只读：登录账号 `identifier`、角色、账号状态、创建时间、最近登录（IP 与时间取自 `user_login_record` 最新一条）
- `ProfileSaveDTO` **刻意不含 `roleId` / `status`**：DTO 里没有的字段，前端就算手工构造请求也提不了权
- `ProfileService.update` 必须用 `LambdaUpdateWrapper` 按列 `set`，**不能用 `updateById`**：
  `User.mobile` / `email` / `roleId` 是 `FieldStrategy.ALWAYS`，局部更新会把没传的字段写成 NULL——用 `updateById`
  改一次手机号就会把用户的角色清空。`ProfileApiTest#saveOnlyTouchesEditableColumns` 用 `roleId=4242` 钉住这一点
- 手机号/邮箱留空语义是「清空」（`trimToNull`），不是「不修改」：页面上清空输入框再保存，期望就是清掉
- 保存后除落库还要**回写会话里的显示名**（`session.setAttribute(SESSION_USER, ...)`），否则顶栏名字要等下次登录才变

#### 修改密码：两段密码必须合成一个密文
- 请求体是 `{keyId, cipher}`，`cipher` 的明文是 JSON：`{"oldPassword":"...","newPassword":"..."}`
- **不能拆成两个 cipher**：SM2 一次一密（`CryptoService.decryptOnce` 解密即删私钥），同一个 `keyId` 只能解一次
- 校验顺序：解出明文 → 校验原密码（`passwordEncoder.matches`）→ 校验新密码规则 → 写新散列
- 新密码规则：8~32 位、必须同时含字母与数字、不得与原密码相同（前端即时提示 + 后端兜底）
- 成功后 `session.invalidate()`，前端 `toast` 后跳 `/login`；**不自动用新密码登录**——改完密码重新认证一次是常规预期，
  也避免「改密后旧会话仍然有效」这类安全隐患
- `CryptoService` 的错误文案已从「登录凭证解析失败」改为中性的「加密凭证解析失败」，因为它现在同时服务登录与改密

#### 公司公告：员工视角只读，只有「已发布」
- `AnnouncementService.publishedPage(query)` **硬编码 `eq(status, PUBLISHED)`，忽略请求里的 `status`**：
  「前端没传 status」不是权限边界，改个 URL 就能翻草稿；`NoticeApiTest#statusParamCannotExposeDrafts` 专门打这个
- `publishedDetail(id)`：非已发布一律抛「公告不存在或已下线」，不给「存在但你没权限」这种可枚举信号
- 页面只读：无新增/编辑/发布按钮，正文用 `App.renderRichContent` 渲染（与公告管理同一套白名单）
- 公告管理与公司公告职责分离：前者是管理员视角（含草稿、可改），后者是员工视角（只读、仅已发布）

#### 富文本渲染能力已上收到 `app.js`
`RICH_ALLOWED_TAGS` / `RICH_BLOCK_TAGS` / `looksLikeRichText` / `sanitizeRich` / `renderRichContent`
统一挂在 `window.App` 上，`announcements.js` 与 `notices.js` 共用。
原先这套白名单写在 `announcements.js` 里，第二个消费方出现时必须上收——两份拷贝迟早发散，
而白名单发散的直接后果是 XSS。

## 13. 安全与认证约定

### 13.1 登录链路（国密 SM2 传输加密 + BCrypt 存储）
1. 打开登录页，ajax 调 `GET /api/auth/public-key` 取一次性公钥；
2. 服务端每次请求生成一对 SM2 密钥，私钥写入 Redis（`backend:crypto:sm2:{keyId}`，默认 5 分钟），只把带 `04` 前缀的公钥返回前端；
3. 前端用 `sm-crypto` 的 `sm2.doEncrypt(password, publicKey, 1)` 加密（`cipherMode = 1` 即 **C1C3C2**）；
4. 提交 `POST /api/auth/login`：`{ username, keyId, cipher }`，**全链路不出现明文密码**；
5. 服务端按 `keyId` 取私钥并**先删除再解密**（一次性，防重放）；登录名按 **`user_account.identifier`（登录标识，唯一索引）** 查账号，再经 `user_id` 关联出用户，解密结果与 `user_account.credential` 的 BCrypt 散列比对。
   **`user.username` 只是展示名，禁止用于身份判定**——允许随时改成中文姓名，若用它查账号，改名会直接锁死登录（2026-10-14 实际发生过：username 被改为「管理员」后 admin 无法登录，且初始化器误判账号不存在而重复开户，撞唯一索引导致应用启动失败）；
6. 校验通过后重建会话（防会话固定）并写入 `LOGIN_USER`，返回 `{ redirect: "/home" }`，前端跳转系统欢迎页；欢迎页/导航展示的是展示名 `user.username`。

### 13.2 加解密格式约定（前后端必须一致）
- 公钥：130 位十六进制、带非压缩前缀 `04`（`sm-crypto` 的 `doEncrypt` 只接受该格式）
- 私钥：64 位十六进制（D 值左补零）
- 密文：`C1(64B) || C3(32B) || C2(n)`，**C1 不带 `04` 前缀**；服务端解密前统一补 `04`
- 实现：`infrastructure.utils.Sm2Utils`（BouncyCastle）、`application.service.CryptoService`（密钥下发 + 一次性解密）
- **一次一密**：`issueKeyPair()` 下发 `keyId` + 公钥，私钥存 Redis，`decryptOnce(keyId, cipher)` 解密即删。
  同一 `keyId` 只能解一次，因此**一个业务动作若要传多段敏感数据，必须合并成一份 JSON 一次加密**（如改密的原密码 + 新密码，见 §12.22）
- 任一侧实现改动后必须跑 `Sm2CryptoTest`（内含 `sm-crypto` 生成的真实密文向量，可直接验证互通）

### 13.3 密码存储与管理员初始化
- 密码一律 BCrypt 散列（强度 10），只存 `user_account.credential`，禁止明文或可逆加密
- 管理员初始化：`infrastructure.tasks.AdminAccountInitializer`（`ApplicationRunner`，幂等）
  - 账号 `admin`、初始密码 `admin123`（可用 `app.admin.password` 覆盖），同时幂等创建角色「超级管理员」并建立 `user` → `user_account` 关联
  - **幂等判据是 `user_account.identifier`**（初始化器真正写入且带唯一索引的业务键），不能用 `user.username`——它是可改的展示名，误判会重复开户并让 `ApplicationRunner` 抛异常、**整个应用启动失败**
  - 用户行按展示名复用：可能存在「用户已建、凭证未建」的半初始化状态，此时只补凭证，不重复建用户
- 回归测试：`AdminAccountInitializerTest`（展示名与登录标识不一致时判重、半初始化补凭证、全新建户、开关关闭）
- 日志与接口返回都不得输出密码明文
- **系统用户的统一初始密码**：`app.user.default-password`（默认 `Init@123456`，生产用 `USER_DEFAULT_PASSWORD` 覆盖），
  用于「新增用户」与「重置密码」；该明文只参与服务端 BCrypt 散列，**接口与页面都不下发**，
  用户管理页只提示「请向系统管理员索取初始密码」。管理员改密码请直接改配置项，不要写入数据库

### 13.4 会话与访问控制
- 登录态存于 `HttpSession`，key 为 `LoginInterceptor.SESSION_USER`，值类型 `CurrentUserVO`
- `LoginInterceptor` + `WebMvcConfig`：`/api/**` 未登录返回 401 JSON；后台页面未登录 302 到 `/login`
- 放行清单：`/login`、`/api/auth/**`、`/css/**`、`/js/**`、`/img/**`、`/vendor/**`、`/favicon.ico`、`/error/**`
- **`/api/attendance/punch` 是唯一的业务例外**：打卡机这类无会话设备调用，登录拦截对它没有意义，
  已从登录拦截摘出，改由 `AttendancePunchSourceInterceptor` 按来源网段管控（见 §12.17）。
  两个拦截器管辖范围互不重叠——登录拦截排除该路径，网段拦截只挂该路径
- 退出登录：`POST /api/auth/logout` 执行 `session.invalidate()`

### 13.5 登录页展示约定与「记住登录账号」
- **登录页只面向最终用户，不展示技术实现细节**：页面上只出现业务语言（登录账号、登录密码、记住登录账号），
  **不得出现加密算法、密钥、散列等字样**（如「国密 SM2」「BCrypt」「公钥一次一密」）
  - 技术说明的归属：写入 `docs/project-spec.md`（本节）与代码注释，不上页面
  - 组件加载失败等异常提示也用业务语言（「页面未能正常加载，请刷新后重试」），不暴露底层组件名
  - 回归测试：`AuthApiTest#loginPageRenders` 断言页面含 `id="rememberMe"`，且**不含**「国密 / SM2 / BCrypt」
- **「记住登录账号」勾选框**（`login.html` + `login.js`）：
  - 勾选后登录成功，把**账号名**写入 `localStorage`，键 `backend:login:remember-username`；取消勾选则删除该键
  - 下次进入登录页自动回填账号并回勾选框，焦点直接落在密码框；已回填时不覆盖浏览器自动填充的内容
  - **只记住账号，绝不保存密码**：密码每次手动输入，避免明文（或可逆密文）落在浏览器本地，
    否则等于绕过 SM2 传输加密；会话保持不在本页职责内，靠 `HttpSession`
  - localStorage 读写全部 `try/catch` 兜底，隐私模式或存储被禁时静默降级为「未记住」，不影响登录

### 13.6 登录记录（审计）
- 表 `user_login_record`（实体 `domain.aggregate.entity.UserLoginRecord`）：`user_id` 关联 `"user".id`、`login_ip` 客户端 IP、`create_time` 登录时间（由 `MyMetaObjectHandler` 填充）
- **只在登录成功时写入**：解密失败、账号不存在、账号冻结、密码错误都不写记录（失败原因靠 `[AUTH]` 日志排查，不落库以免被刷）
- 写入点在 `AuthService.recordLogin`，位于密码校验通过之后
  - 该方法**不加 `@Transactional`**：审计插入独立提交，配合 `try/catch` 保证「记录表异常不阻断登录」，同时用 `log.error` 留下告警线索（若加了事务，catch 后外层会被标记 rollback-only，反而让登录失败）
- IP 解析：`infrastructure.utils.IpUtils.getClientIp(HttpServletRequest)`
  - 顺序：`X-Forwarded-For`（取**首个**地址，即最靠近客户端的那个）→ `X-Real-IP` → `Proxy-Client-IP` → `WL-Proxy-Client-IP` → `HTTP_CLIENT_IP` → `HTTP_X_FORWARDED_FOR` → `request.getRemoteAddr()`
  - 值为 `unknown` 时忽略并继续向下解析
  - **白名单校验**：只有能解析成 IPv4（严格四段 0~255）或 IPv6（含冒号、≤45 字符）的值才被采用，其余一律判为解析失败——不要用「字符过滤」的思路，那样会把 SQL 片段洗成 `dabeee` 之类的垃圾值仍然写进库
  - **安全提醒**：这些请求头客户端可伪造，仅作审计参考，**不得用于鉴权或风控判定**；IP 在会话重建之前解析
- 索引：`(user_id, create_time)` 支撑「某用户最近登录」，`create_time` 支撑全局「最近登录记录」
- 测试：`IpUtilsTest`（9 例，纯单测）、`AuthApiTest#loginWritesLoginRecord` / `#loginRecordUsesRemoteAddrWithoutProxyHeader` / `#failedLoginWritesNoRecord`

### 13.7 尚未覆盖、上线前需补齐的安全项
- **HTTPS 必须由网关/反向代理启用**：SM2 只保护密码字段，其余数据仍依赖 TLS
- 未做登录失败次数限制与图形/短信验证码（防暴力破解）
- 未启用 CSRF 防护（未引入完整 Spring Security），跨域部署前需评估
- 会话未接入 Spring Session + Redis，重启或滚动发布会丢失登录态
- 「记住登录账号」只回填账号，**不延长会话**；如需「下次免登录」，要另做长效 remember-me token（服务端改造）
- 暂无修改密码 / 用户管理界面；账号冻结状态已在登录时校验（`UserStatusEnum`）



