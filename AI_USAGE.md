# AI 使用记录

实现范围以 `README.md` 的对照表为准。密码没有写入项目。

工具：Cursor 内的编码助手。用于阅读任务书、对照已有代码、修改数据源配置、编译，以及整理本说明。业务规则以《机试任务书》和已经锁定的实现为准，助手不能自行加管理端、用户表或 Redis 扣库存。

## 采纳

数据源改为 `signup_dev`，密码只保留 `${MYSQL_PASSWORD}`，去掉文件里的默认账号 `signup` / `signup_exam_local`，并去掉 `createDatabaseIfNotExist`。库已经存在，应用账号也不应负责建库。

Maven 首次解析父 POM 时出现 `repo.maven.apache.org` 未知主机。改用 `-Djava.net.preferIPv4Stack=true` 后 `mvnw.cmd clean compile` 成功。这是网络参数，没有改业务代码。

## 否决

曾出现一份更宽的实现要求：活动创建、用户表、操作记录、管理功能、活动未开始、Redis 缓存库存或幂等键、成功响应增加 traceId、按 controller/service/impl 重排包。这些与任务书和已锁定实现冲突，已明确不采纳。

密码不写入 `application.yml`、Git、日志或仓库脚本。`signup_dev` 密码只在本机窗口输入。

## 验证

编译已实际执行并通过。MySQL 用 `signup_dev` 执行 `SELECT 1` 与 `CURRENT_USER()` 已得到 `signup_dev@localhost`。Spring Boot 启动后，活动列表返回 7 条固定数据。

对账工具只读，不自动改数据。一致样例退出码 0，不一致样例连续两次退出码 1。`mvnw.cmd test` 已实际跑完：24 个测试，0 失败。真实库对账两次退出码都是 0。细节在 `TEST_REPORT.md`。
