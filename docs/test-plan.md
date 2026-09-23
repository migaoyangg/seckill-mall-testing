# 秒杀商城测试计划与本轮执行记录

## 1. 文档信息

| 项目 | 内容 |
| --- | --- |
| 测试对象 | seckill-mall 高并发秒杀商城 |
| 测试日期 | 2026-08-09 |
| 测试范围 | 用户、商品、秒杀、订单、管理员、鉴权、异步下单 |
| 本轮结论 | 功能冒烟通过；高并发和 RabbitMQ 模式待补测 |
| 关联文档 | `docs/api-test-cases.md`、`docs/core-flow-test-cases.md`、`docs/performance-concurrency-test-cases.md` |

## 2. 测试目标

1. 验证应用能够在 MySQL、Redis 可用时正常启动。
2. 验证登录鉴权、商品查询、普通订单支付和管理员权限链路。
3. 验证秒杀活动的未开始/已结束拦截、Redis 预扣、异步入队、结果轮询和订单落库。
4. 验证重复请求、无效 Token、越权访问等关键异常场景。
5. 为后续 JMeter/wrk 并发测试定义数据准备、指标口径和验收标准。

## 3. 测试环境

| 组件 | 要求/本轮实际 |
| --- | --- |
| JDK | 项目要求 17+；本轮运行 Java 26.0.1 |
| Maven | 3.8+，执行 `mvn test`、`mvn spring-boot:run` |
| MySQL | 8.x，`127.0.0.1:3306/seckill_mall`，本轮可用 |
| Redis | 6.x，`127.0.0.1:6379`，本轮可用 |
| RabbitMQ | 可选；本轮未启动，RabbitMQ 模式未执行 |
| 应用 | `http://127.0.0.1:8080/api`，`seckill.async.mode=redis` |
| 压测工具 | JMeter 5.6.3、wrk（本轮未执行大规模压测） |

## 4. 测试前置与数据

```bash
mysql -u root -p < src/main/resources/db/schema.sql
mvn spring-boot:run
```

测试账号和密码必须通过环境变量注入，不在仓库中预置。秒杀测试需要一个状态为进行中、时间覆盖当前时刻、Redis 已预热的活动；压测时每个并发线程应使用不同用户 Token。

测试结束后删除本轮订单、恢复活动库存/时间，并清理 `seckill:*` 测试 Key。共享环境禁止直接执行全库删除，建议每轮使用独立活动 ID。

## 5. 完整执行流程

### 5.1 构建与自动化测试

```bash
mvn test
```

检查 `target/surefire-reports`，要求无 Failure/Error。

### 5.2 启动检查

```bash
mysqladmin ping -h127.0.0.1 -uroot -p123456
redis-cli -h 127.0.0.1 -p 6379 ping
curl -i http://127.0.0.1:8080/api/doc.html
curl -i http://127.0.0.1:8080/api/v3/api-docs
```

### 5.3 API 冒烟与核心链路

仓库自带脚本覆盖未登录拦截、普通/管理员登录、个人信息、商品列表/详情/搜索、管理员列表、普通用户越权、普通订单创建/支付、秒杀详情/参与和异步结果轮询：

```bash
python3 scripts/api_test.py \
  --base-url http://127.0.0.1:8080/api \
  --product-id 1 --seckill-id 3 \
  --include-mutating --include-seckill \
  --seckill-poll-times 8 --seckill-poll-interval 0.5
```

另执行一次已结束活动，确认返回 `4002`；使用无效 Token 请求秒杀接口，确认返回 `1003`；同一用户再次请求已消费活动，确认重复/限购业务码，不产生重复订单。

### 5.4 并发与一致性（发布前必测）

准备独立活动和 Token 文件后执行：

```bash
jmeter -n -t scripts/jmeter/seckill-entry.jmx \
  -Jthreads=100 -Jramp=10 -Jloops=1 -JgoodsId=<id> \
  -JtokenFile=scripts/jmeter/tokens.csv \
  -JresultFile=target/seckill-entry-100.jtl

mysql -u root -p seckill_mall < scripts/sql/seckill_check.sql
```

按 100、500、1000 并发逐级执行，并补充瞬时冲击、持续 10 分钟稳定性、支付回调幂等、取消/退款并发、Redis 模式与 RabbitMQ 模式对比。

## 6. 用例与验收标准

| 用例 | 场景 | 通过标准 |
| --- | --- | --- |
| T-001 | Maven 单元测试 | 服务层单元测试无 Failure/Error |
| T-002 | Spring 上下文/服务依赖 | 集成测试上下文可加载；MySQL、Redis PONG/连接成功；应用监听 8080 |
| T-003 | API 文档 | doc.html 和 OpenAPI HTTP 200 |
| T-004 | 鉴权/权限 | 未登录 `1001`，无效 Token `1003`，普通用户访问管理接口 `1002` |
| T-005 | 普通订单 | 创建、详情、支付成功，订单状态由待支付变为已支付 |
| T-006 | 秒杀时间校验 | 未开始 `4001`，已结束 `4002` |
| T-007 | 秒杀成功链路 | 入口受理，结果从 `PROCESSING` 进入成功终态，订单 `is_seckill=1` |
| T-008 | 防重复/限购 | 同一用户同一活动最多一笔订单，无重复扣库存 |
| T-009 | 并发一致性 | 成功订单数不超过初始库存，重复订单为 0，SQL `diff=0` |
| T-010 | 稳定性 | HTTP 500 集中错误为 0，压测后应用、Redis、MySQL 资源恢复 |

建议的性能阈值（需结合部署规格复核）：秒杀入口 P95 <= 300 ms、P99 <= 800 ms；预期业务失败（售罄/重复/限购）不计入 HTTP 错误率，技术错误率 < 1%。

## 7. 本轮实际结果

| 步骤 | 结果 | 证据/说明 |
| --- | --- | --- |
| `mvn test`（JDK 17） | 通过 | 24 tests, 0 failures, 0 errors；默认仅执行单元测试 |
| `mvn -Dtest=SeckillMallApplicationIT test` | 待执行 | 需要本机 MySQL、Redis；当前环境 Redis/数据库未启动 |
| MySQL/Redis 检查 | 通过 | `mysqld is alive`、`PONG` |
| 应用启动 | 通过 | Tomcat 8080，context-path `/api`；Redis 连接和库存预热日志正常 |
| API 文档 | 通过 | doc.html HTTP 200；OpenAPI HTTP 200，42 KB |
| 普通/管理/订单流程 | 通过 | API 脚本 17/17 |
| 秒杀成功异步流程 | 通过 | API 脚本 15/15；入口 code=200，结果轮询成功 |
| 秒杀结束分支 | 通过 | API 脚本 13/13；参与接口 code=4002 |
| 无效 Token | 通过 | 秒杀接口返回 code=1003 |
| 重复下单/限购 | 待测 | 本轮临时活动在复测前已到期，未取得 `4004`/`4005` 的独立实测结果 |
| 高并发 JMeter/wrk | 待测 | 本轮未产生可用于发布判定的吞吐、P95/P99 数据 |
| RabbitMQ 模式 | 待测 | 本机未发现 5672 服务 |

## 8. 测试发现与处理

1. **测试账号与初始化脚本漂移**：数据库中 `admin`、`testuser` 的 BCrypt 哈希与 `schema.sql` 不一致，文档密码登录首次返回 `1006`。本轮仅为本地验证临时恢复密码并在结束时恢复原哈希；正式测试前应重新导入 schema 或更新测试凭据管理。
2. **自动化覆盖不足**：当前 `src/test` 已有服务层单元测试，但 Controller/MockMvc、真实数据库映射和 RabbitMQ 仍需补充；建议继续覆盖状态机、库存回滚、支付回调幂等和权限。
3. **JDK 版本差异**：本轮使用 JDK 26，而项目基线为 JDK 17；发布前需在 JDK 17 CI 环境复跑。

## 9. 发布门禁与回归策略

功能回归必须满足 T-001 至 T-008；发布前还必须满足 T-009、T-010，并分别验证 `seckill.async.mode=redis` 和 `rabbitmq`。任何超卖、重复订单、库存 `diff != 0`、集中 500 或支付状态非幂等均阻断发布。每次修改订单状态机、Lua 脚本、消息消费者、库存回滚或鉴权拦截器后，重新执行完整流程和至少一轮 500 并发压测。
