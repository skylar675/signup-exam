# 测试记录

这是 2026-09-30 20:13 实际跑出来的结果。完成清单看 `README.md`。没有执行过的项目不记为通过。

命令：`mvnw.cmd test`  
时间：2026-09-30 20:13  
环境：JDK 21.0.12.1，Spring Boot 3.4.5，MySQL `signup_exam` / `signup_dev`，Redis `127.0.0.1:6379`。  
日志：本次 Maven 输出约 44 秒，`BUILD SUCCESS`。汇总为 Tests run: 24, Failures: 0, Errors: 0, Skipped: 0。

密码没有写入仓库。下面只记录已经执行的结果。

## 报名与并发

| 检查 | 测试 | 结果 |
|---|---|---|
| 正常报名，首次 201 | `SignupFlowTest.firstRegistrationIs201AndReplayIs200` | PASS |
| 同一请求键重放 200，不重复扣名额 | 同上 | PASS |
| 换 requestId 不能再次报名 | `differentRequestIdCannotBypassDedup` | PASS |
| 同键改报另一活动，不扣名额 | `sameRequestIdOnAnotherActivityConflictsWithoutDeducting` | PASS |
| 售罄失败不绑定请求键 | `failedSoldOutDoesNotBindRequestId` | PASS |
| 关闭活动不绑定请求键 | `closedActivityDoesNotBindRequestId` | PASS |
| 成功后再满额，重放仍返回原记录 | `replayAfterSoldOutReturnsOriginalRow` | PASS |
| 成功后再关闭，重放仍返回原记录 | `replayAfterClosedReturnsOriginalRow` | PASS |
| 不能读取他人报名 | `userCanOnlyReadOwnRegistration` | PASS |
| 非法用户或请求键返回 400 | `invalidUserAndRequestAre400` | PASS |
| 不同用户可以各自使用同一个请求键 | `sameRequestIdIsScopedPerUser` | PASS |
| 请求键区分大小写 | `requestIdCaseIsDistinct` | PASS |
| 50 个用户抢 10 个名额 | `fiftyUsersCompeteForTenSeats` | PASS |
| 同用户同键并发 20 次 | `sameUserSameRequestIdTwentyTimesConverges` | PASS |
| 同用户不同键并发 20 次 | `sameUserDifferentRequestIdsAllowOnlyOneSuccess` | PASS |
| 同键同时报两个活动 | `sameRequestIdAcrossActivitiesAllowsOnlyOneBinding` | PASS |

`SignupFlowTest`：18 个测试，0 失败，0 错误，耗时 3.259 秒。

50 人抢 10 个名额的断言是：10 次 201、40 次 `SOLD_OUT`、报名 10 条、剩余 0，且总名额等于剩余名额加成功数。同键 20 次的断言是：1 次新建、19 次重放、同一个报名 ID、只扣 1 个名额。不同键 20 次的断言是：1 次成功、19 次 `ALREADY_REGISTERED`。

日志里出现过 `Duplicate entry '1101-cross_key'`。这是同键并发时数据库唯一约束拦住第二次插入，测试随后按已提交记录返回冲突，该测试通过。异常原文没有作为接口响应交给调用方。

活动列表、详情剩余名额来自 MySQL、未知活动 404、Redis 未命中后命中再过期，也在这 18 个测试里，均为 PASS。

## Redis 不可用

`RedisDownTest.activityDetailAndRegistrationWorkWhenRedisIsUnreachable`：1 个测试，0 失败，耗时 6.175 秒。PASS。

Redis 被指到 `127.0.0.1:6399`，本机 6379 没有被改掉。活动详情仍返回「Java 实战分享」和剩余名额 5，随后报名 201，剩余变为 4。

## 插入前失败回滚

`RegistrationFaultTest.quotaRollsBackWhenInsertIsAborted`：1 个测试，0 失败，耗时 0.710 秒。PASS。

`signup.fault.before-insert=true` 时接口返回 500，响应体不含 `SQL` 或 `Duplicate entry`。剩余名额和报名行数与提交前相同，`fault_key` 没有留下记录。

## 参数规则

`InputRulesTest`：4 个测试，0 失败，耗时 0.057 秒。PASS。覆盖分页默认值与非法值、请求键大小写、用户 ID、畸形报名 ID。

## 对账

`python tools/reconcile.py` 只读，不修改数据。

| 命令 | 退出码 | 结果 |
|---|---|---|
| `--fixture tools/samples/consistent.json` | 0 | PASS，名额公式成立 |
| `--fixture tools/samples/inconsistent.json` | 1 | PASS，检出 1002 名额不一致且重复报名、1003 剩余为负、请求键 `same_key` 重复 |
| 同上再执行一次 | 1 | PASS，结果相同，没有改数据 |
| 连接 `signup_exam` | 0 | PASS |
| 再连接一次 | 0 | PASS，仍一致 |

真实库两次读取都是活动 1001–1007、成功报名 0 条，剩余名额等于初始名额。测试之后那次 `reset.sql` 的客户端退出码是 1，当时没有单独保存错误输出。2026-09-30 20:18 重新执行 `source reset.sql`，退出码 **0**。随后查询：活动 7 行，报名 0 行，1001–1007 的状态和名额与 `data.sql` 一致。

## 重启后持久性

应用已在 8080 运行时提交报名，然后停掉进程再启动，不重新插入。

| 步骤 | 结果 |
|---|---|
| `POST /api/registrations`，用户 1101，活动 1001，`requestId=persist_restart_1` | HTTP 201，报名 id 29 |
| 重启前 `GET /api/activities/1001` | 剩余名额 4 |
| 停掉 `spring-boot:run` 后再启动 | 新进程能访问 8080 |
| 重启后 `GET /api/activities/1001` | 剩余名额仍是 4 |
| 重启后 `GET /api/registrations`，`X-User-Id: 1101` | 仍是 id 29、`persist_restart_1`、`REGISTERED`，total 1 |

核对完成后再次执行 `reset.sql`，退出码 0，报名恢复为 0，便于页面回到种子数据。这次检查 PASS。

## 未执行

没有跑消息队列、MongoDB 或 Elasticsearch。任务书的深化项只实现了 Python 对账。
