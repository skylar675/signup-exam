# 限量活动报名系统

Java 21、Spring Boot 3.4.5、MyBatis 3.0.4、MySQL 8、Redis。单体应用。活动详情缓存活动基础信息；剩余名额只读 MySQL。`X-User-Id` 只是本机演示标记，不是登录或生产认证。

数据库密码只通过环境变量 `MYSQL_PASSWORD` 传入，不在本文件、配置或代码里保存。

## 做了什么，没做什么

| 事项 | 状态 | 位置 |
|---|---|---|
| 活动列表、活动详情 | 已做 | `src/main/java/com/exam/signup/api/ActivityController.java` |
| 提交报名、我的报名、报名详情 | 已做 | `api/RegistrationController.java` |
| 名额条件更新、唯一约束、同一事务 | 已做 | `application/RegistrationTxWorker.java`，`db/schema.sql` |
| requestId 重放、冲突、失败不占键 | 已做 | `application/RegistrationService.java` |
| Redis 只缓存活动基础信息，剩余名额读 MySQL | 已做 | `cache/ActivityCache.java` |
| Redis 失败时详情回源，报名不依赖 Redis | 已做，并已测试 | `RedisDownTest` |
| 单页演示 | 已做 | `src/main/resources/static/index.html`，地址 http://127.0.0.1:8080/ |
| 固定活动 1001–1007，报名初始为空 | 已做 | `db/data.sql`，`db/reset.sql` |
| 真实 MySQL / Redis 测试 | 已跑：24 个，0 失败 | `TEST_REPORT.md` |
| `reset.sql` 清空报名并恢复名额 | 已执行，退出码 0 | `db/reset.sql`，记录在 `TEST_REPORT.md` |
| 停掉应用再启动，报名和名额仍在 | 已核对 | `TEST_REPORT.md` 的「重启后持久性」 |
| Python 只读对账（深化项，只选这一个） | 已做，并已执行 | `tools/reconcile.py` |
| 活动管理后台、支付、取消报名、短信、登录、微服务 | 未做 | 任务书明确不做 |
| MQ、MongoDB、Elasticsearch | 未做 | 本机没有这些服务，不伪造 |
| Git 仓库 | 已完成本地首次提交 | 在项目目录执行 `git log` |

说明文件：`TASKS.md` 写取舍，`ARCHITECTURE.md` 写模块和事务，`TEST_REPORT.md` 写实测，`AI_USAGE.md` 写工具使用。

## 运行条件

- JDK 21。本机使用 `D:\机试\_tools\jdk\jdk-21.0.12.1+1`。机器 PATH 里若仍有 JDK 8，请先设置 `JAVA_HOME` 再启动。
- Maven Wrapper：`mvnw.cmd`（Maven 3.9.11）。访问 Maven Central 失败时加上 `MAVEN_OPTS=-Djava.net.preferIPv4Stack=true`。
- MySQL 8：`127.0.0.1:3306`，库 `signup_exam`，账号 `signup_dev`。不要用 root 启动应用。
- Redis：`127.0.0.1:6379`。

密码只通过环境变量 `MYSQL_PASSWORD` 提供，不要写入本仓库。

## 初始化与启动

库和账号需要事先存在。`signup_dev` 需要在 `signup_exam` 上具备建表、改表和增删改查权限。应用启动时执行 `src/main/resources/db/schema.sql` 与 `data.sql`（`CREATE TABLE IF NOT EXISTS`、`INSERT IGNORE`）。报名表初始没有业务数据。重置固定活动与清空报名使用 `src/main/resources/db/reset.sql`。

双击项目里的 `启动.cmd`。它会询问 `signup_dev` 密码，启动成功后打开 http://127.0.0.1:8080/ 。如果应用已经在运行，它只打开页面。关闭该窗口会停止由它启动的应用。

也可以在窗口里手动启动：

```powershell
$env:JAVA_HOME = "D:\机试\_tools\jdk\jdk-21.0.12.1+1"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
$env:MAVEN_OPTS = "-Djava.net.preferIPv4Stack=true"
$env:MYSQL_USER = "signup_dev"
$env:MYSQL_PASSWORD = Read-Host "MYSQL_PASSWORD"
.\mvnw.cmd spring-boot:run
```

`Read-Host` 会把密码留在当前窗口的环境变量里，不会写进项目文件。页面入口：<http://localhost:8080/>。

## 测试

```powershell
.\mvnw.cmd test
```

测试使用真实 MySQL 与真实 Redis，不用内存数据库代替。结果以实际执行记录为准，见 `TEST_REPORT.md`。

对账不改数据库。正常样例退出码 0，异常样例退出码 1：

```powershell
python tools\reconcile.py --fixture tools\samples\consistent.json
python tools\reconcile.py --fixture tools\samples\inconsistent.json
python tools\reconcile.py
```

最后一条连真实库，密码仍用当前窗口里的 `MYSQL_PASSWORD`。它在一个 `REPEATABLE READ` 事务里只读活动和报名，避免并发写入把同一次核对拆成两个时刻。

## 已知限制

- 演示身份不是认证。知道别人的正整数用户标记就可以用该标记调用接口。
- 活动由固定数据提供，没有创建活动的接口。
- 编译曾提示 `RegistrationService` 使用了过时 API，构建仍然成功。未为此改业务逻辑。
