# 架构说明

哪些已经实现、哪些没有实现，看 `README.md` 开头的对照表。本文只说明已经落地的结构。

## 模块

- `api`：HTTP、请求头、状态码和响应体。不写 SQL，不访问 Redis。
- `application`：报名顺序、事务外的重放/冲突/已报名判断，以及唯一约束冲突回滚后的再次分类。`RegistrationTxWorker` 上的 `@Transactional` 只包住一次报名写入。
- `persistence`：MyBatis 接口与行对象。SQL 在 `resources/mapper`。
- `domain`：业务错误码。不把数据库异常原文返回给调用方。
- `cache`：只服务活动详情。键 `activity:{id}`，值含 id、title、status、totalQuota，不含剩余名额。TTL 配置项 `signup.activity-cache-ttl-seconds`，默认 60 秒。

增加一个查询字段时，改对应 Mapper、响应对象和页面。增加一种报名拒绝原因时，改 `ErrorCode`、`RegistrationTxWorker` 的判定和测试。不要把规则写进 Controller。

## 数据

`activity`：主键 id，状态 OPEN/CLOSED，`remaining_quota >= 0` 且不超过 `total_quota`。

`registration`：`UNIQUE(user_id, activity_id)`，`UNIQUE(user_id, request_id)`，`request_id` 使用 ascii 二进制排序，外键指向活动。没有用户表。

固定活动 1001–1007 由 `data.sql` 写入。`reset.sql` 清空报名并恢复这些活动的标题、名额和状态。

## 事务、并发与幂等

事务内第一条业务 SQL 是 `SELECT ... FROM activity WHERE id=? FOR UPDATE`。随后再次检查请求键和用户活动，再按状态与剩余名额拒绝。扣减语句是：

```sql
UPDATE activity
SET remaining_quota = remaining_quota - 1
WHERE id = ? AND status = 'OPEN' AND remaining_quota > 0
```

影响行数不是 1 就不再插入。插入与扣减在同一事务。插入前失败时整个事务回滚，避免只扣名额没有记录。

不使用 `synchronized`、进程内锁或 Redis 锁。并发靠行锁、条件更新和唯一约束。唯一约束冲突抛出后离开事务代理，由外层按已提交数据分类：同键同活动为 200 重放，同键不同活动为 409 `IDEMPOTENCY_CONFLICT`，同活动换键为 409 `ALREADY_REGISTERED`，否则再看关闭或满额。不按异常里的索引名分类。死锁只重试一次。

## 缓存

未命中则读 MySQL 并写入缓存。这一次 Redis 读失败则本次详情回源 MySQL，并打日志，不把失败当成活动不存在。连接超时 200ms，读超时 300ms。报名写路径不调用 Redis。

## 与生产的差距

`X-User-Id` 可被调用方随意填写。没有登录、授权、审计、限流和多实例会话。Redis 宕机只影响详情缓存，不影响名额正确性。单库事务不能替代跨库一致性；本题没有第二套业务库。

`tools/reconcile.py` 是只读对账，不参与报名。连库时先把会话设为 `REPEATABLE READ` 再 `START TRANSACTION`，在同一事务里读取活动名额和报名行，然后提交。这样并发报名不会让总名额、剩余名额和成功数来自不同时刻。工具没有修复开关，发现不一致只打印 JSON 并以退出码 1 结束。
