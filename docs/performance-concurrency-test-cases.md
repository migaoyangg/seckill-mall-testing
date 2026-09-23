# 秒杀商城性能并发测试用例

## 1. 测试目标

验证秒杀核心链路在高并发下的吞吐、响应时间、稳定性和数据一致性，重点覆盖：

- 秒杀入口 `POST /api/seckill/do/{seckillGoodsId}` 的并发受理能力。
- Redis 预扣库存、Lua 原子扣减、限购、重复请求防护是否生效。
- Redis Queue / RabbitMQ 异步落单后，订单、库存、用户限购数据是否一致。
- 支付回调、取消/退款/超时关单等状态流转在并发下是否幂等。
- 系统在库存售罄、单用户刷接口、消息堆积、数据库写入压力下是否稳定。

## 2. 测试范围

基础地址：`http://localhost:8080/api`

| 模块 | 接口 | 压测重点 |
| --- | --- | --- |
| 秒杀 | `POST /seckill/do/{seckillGoodsId}` | 高并发受理、限购、防超卖、防重复下单 |
| 秒杀 | `GET /seckill/result/{requestId}` | 异步结果查询、结果最终一致 |
| 订单 | `POST /order/pay/callback/{orderNo}` | 支付回调幂等、并发状态更新 |
| 订单 | `POST /order/cancel/{orderNo}` | 并发取消、库存回滚幂等 |
| 订单 | `POST /order/refund/{orderNo}` | 并发退款、库存回滚幂等 |
| 商品 | `GET /product/detail/{id}` | 热点商品缓存读取性能 |

## 3. 环境准备

依赖：

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+
- JMeter 5.6+ 或 wrk
- RabbitMQ 可选，仅当 `seckill.async.mode=rabbitmq` 时需要

启动服务：

```bash
mvn spring-boot:run
```

初始化数据库：

```bash
mysql -u root -p < src/main/resources/db/schema.sql
```

压测前建议关闭本机无关高负载进程，并记录测试机、应用机、MySQL、Redis 是否在同一台机器。所有测试结论需要附带环境规格，否则 TPS 和延迟无法横向比较。

## 4. 测试数据准备

### 4.1 秒杀活动

准备一个进行中的秒杀活动，建议库存设置为 `1000`，限购设置为 `1`。

可通过管理员接口创建，也可以在测试库中直接调整已有活动：

```sql
UPDATE seckill_goods
SET stock_count = 1000,
    start_time = DATE_SUB(NOW(), INTERVAL 10 MINUTE),
    end_time = DATE_ADD(NOW(), INTERVAL 30 MINUTE),
    limit_per_user = 1,
    status = 1,
    deleted = 0
WHERE id = 1;
```

活动开启后需要触发库存预热。推荐调用管理员状态接口：

```bash
curl -X PUT "http://localhost:8080/api/seckill/admin/status/1/1" \
  -H "Authorization: Bearer <admin-token>"
```

### 4.2 用户 Token

秒杀压测需要不同用户 token，避免单用户被限购和重复请求防护提前拦截。

项目提供了批量生成脚本，会自动注册/登录压测用户并输出 JMeter 可读取的 token 文件：

```bash
python3 scripts/jmeter/generate_tokens.py \
  --base-url http://localhost:8080/api \
  --count 500 \
  --start 1 \
  --output scripts/jmeter/tokens.csv \
  --users-output scripts/jmeter/users.csv
```

脚本会生成两份文件：

- `scripts/jmeter/tokens.csv`：JMeter 秒杀压测直接使用。
- `scripts/jmeter/users.csv`：本轮压测创建/登录过的用户账号记录。

`scripts/jmeter/tokens.csv` 格式必须包含表头，token 内容必须带 `Bearer` 前缀：

```csv
token
Bearer user-token-1
Bearer user-token-2
Bearer user-token-3
```

建议 token 数量不少于目标并发线程数。比如 500 并发准备至少 500 个普通用户 token。

### 4.3 清理历史数据

每轮秒杀压测前建议使用独立活动 ID。若复用同一个活动，需要清理订单和 Redis 秒杀相关 key，避免历史重复下单标记影响结果。

示例 SQL：

```sql
DELETE FROM order_detail
WHERE order_id IN (
  SELECT id FROM orders WHERE is_seckill = 1 AND seckill_goods_id = 1
);

DELETE FROM orders
WHERE is_seckill = 1 AND seckill_goods_id = 1;

UPDATE seckill_goods
SET stock_count = 1000, status = 1
WHERE id = 1;
```

Redis key 需按当前 `Constants` 前缀清理秒杀库存、限购、重复请求、结果和队列数据；不确定 key 名时优先新建活动，不建议在共享 Redis 上使用批量删除。

## 5. 性能指标口径

| 指标 | 目标值 | 说明 |
| --- | --- | --- |
| HTTP 错误率 | `< 1%` | 不包含库存不足、重复秒杀、限购等业务预期失败 |
| 秒杀入口 P95 | `<= 300 ms` | 本机单实例参考目标，以实际环境为准 |
| 秒杀入口 P99 | `<= 800 ms` | 本机单实例参考目标，以实际环境为准 |
| 成功受理数 | `<= 初始库存` | 不允许超卖 |
| 重复订单数 | `0` | 同一用户同一活动不能产生多笔订单 |
| 库存一致性差值 | `0` | `初始库存 - 成功订单数 - 剩余库存 = 0` |
| 应用异常 | `0` | 日志中不能出现集中 500、死锁、连接耗尽 |
| Redis/DB 资源 | 无持续打满 | CPU、连接数、慢查询、内存需可恢复 |

## 6. 执行命令

### 6.1 JMeter 秒杀入口压测

```bash
jmeter -n -t scripts/jmeter/seckill-entry.jmx \
  -Jhost=localhost \
  -Jport=8080 \
  -Jthreads=500 \
  -Jramp=10 \
  -Jloops=1 \
  -JgoodsId=1 \
  -JtokenFile=scripts/jmeter/tokens.csv \
  -JresultFile=target/seckill-entry-500.jtl
```

生成 HTML 报告：

```bash
jmeter -g target/seckill-entry-500.jtl -o target/jmeter-report-500
```

### 6.2 wrk 秒杀入口压测

```bash
TOKEN_FILE=scripts/jmeter/tokens.csv SECKILL_GOODS_ID=1 \
  wrk -t8 -c500 -d30s -s scripts/wrk/seckill.lua http://localhost:8080
```

说明：`wrk` 适合观察入口吞吐和延迟，JMeter 更适合沉淀报告和参数化结果文件。

### 6.3 压测后 SQL 校验

执行前修改 `scripts/sql/seckill_check.sql` 中的：

```sql
SET @seckill_goods_id = 1;
SET @initial_stock = 1000;
```

执行校验：

```bash
mysql -u root -p seckill_mall < scripts/sql/seckill_check.sql
```

验收：

- `success_order_count <= @initial_stock`
- 重复下单查询结果为空
- `diff = 0`

## 7. 测试用例

| 用例ID | 场景 | 前置条件 | 执行方式 | 预期结果 |
| --- | --- | --- | --- | --- |
| PERF-001 | 秒杀入口 100 并发基准 | 活动进行中，库存 1000，100 个不同用户 token | JMeter `threads=100,ramp=10,loops=1` | HTTP 200 为主，入口 P95 <= 300 ms；成功受理数 <= 100；无 500 |
| PERF-002 | 秒杀入口 500 并发 | 活动进行中，库存 1000，500 个不同用户 token | JMeter `threads=500,ramp=10,loops=1` | 成功受理数 <= 500；无超卖、无重复订单；应用无连接池耗尽 |
| PERF-003 | 秒杀入口 1000 并发售罄 | 活动进行中，库存 300，1000 个不同用户 token | JMeter `threads=1000,ramp=5,loops=1` | 最多 300 笔订单；其余返回库存不足或排队失败；SQL 校验 `diff=0` |
| PERF-004 | 瞬时流量冲击 | 活动进行中，库存 1000，500 个不同用户 token | JMeter `threads=500,ramp=1,loops=1` | 系统不崩溃；无集中 500；Redis 和 DB 连接数恢复正常 |
| PERF-005 | 持续压力稳定性 | 活动库存足够，token 数足够，避免重复用户循环抢同一活动 | wrk `-t8 -c300 -d10m` 或多活动轮换 | 入口延迟无持续劣化；应用内存无明显泄漏；日志无异常堆积 |
| PERF-006 | 单用户刷秒杀接口 | 1 个普通用户 token，活动进行中 | 1 秒内连续请求 10 次同一活动 | 触发限流、重复秒杀或限购；最多受理 1 次；不产生多订单 |
| PERF-007 | 重复 token 并发 | token 文件中故意放入同一个用户 token 100 行 | JMeter `threads=100,ramp=1,loops=1` | 最多 1 次受理；其余为重复秒杀/限购/限流；无重复订单 |
| PERF-008 | 异步落单最终一致 | 秒杀入口压测完成后等待消费者处理 | 压测后轮询结果或等待 10-30 秒后查库 | `orders` 订单数、`seckill_goods.stock_count`、Redis 剩余库存一致 |
| PERF-009 | Redis 队列削峰 | `seckill.async.mode=redis`，库存 1000 | 500-1000 并发请求秒杀入口 | 请求快速返回 `PROCESSING`；后台持续落单；队列最终消费完 |
| PERF-010 | RabbitMQ 削峰 | `seckill.async.mode=rabbitmq`，RabbitMQ 可用 | 同 PERF-009 | 消息无大量堆积；消费者最终落单；失败消息有日志可追踪 |
| PERF-011 | 支付回调同流水号并发幂等 | 创建一笔待支付订单 | 对同一 `orderNo`、同一 `transactionNo` 并发请求 20 次 | 多次调用不重复支付；订单状态最终为已支付；流水号唯一 |
| PERF-012 | 支付回调不同流水号竞争 | 创建一笔待支付订单 | 对同一 `orderNo`、不同 `transactionNo` 并发请求 20 次 | 仅 1 个流水号成功；其余返回订单状态异常；无脏写 |
| PERF-013 | 待支付订单并发取消 | 创建一笔待支付普通订单 | 并发调用 `/order/cancel/{orderNo}` 20 次 | 最多 1 次真正取消并回滚库存；订单最终为已取消；库存只回滚一次 |
| PERF-014 | 已支付订单并发退款 | 创建并支付一笔订单 | 并发调用 `/order/refund/{orderNo}` 20 次 | 退款幂等；订单最终为已退款；库存只回滚一次 |
| PERF-015 | 热点商品详情读取 | 商品详情已访问过并进入缓存 | wrk/JMeter 并发请求 `/product/detail/{id}` | P95 稳定；DB 查询压力明显低于无缓存；无 500 |
| PERF-016 | 未登录流量冲击 | 不带 token 请求秒杀入口 | JMeter/wrk 500 并发 | 快速返回未登录业务码；不会进入扣库存或落单逻辑 |
| PERF-017 | 活动未开始/已结束冲击 | 活动状态不在进行中 | JMeter/wrk 500 并发 | 快速返回未开始/已结束；库存、订单不变 |
| PERF-018 | 数据库乐观锁兜底 | Redis 预扣正常，DB 写入出现竞争 | 高并发秒杀同一活动 | 不出现负库存；失败落单需要回滚 Redis 预扣和限购标记 |

## 8. 结果记录模板

| 字段 | 记录 |
| --- | --- |
| 测试日期 |  |
| Git/代码版本 |  |
| 应用配置 | `seckill.async.mode=redis/rabbitmq` |
| 应用实例数 |  |
| MySQL/Redis/RabbitMQ 规格 |  |
| 压测工具 | JMeter/wrk |
| 并发/线程数 |  |
| Ramp-up/持续时间 |  |
| 秒杀活动 ID |  |
| 初始库存 |  |
| Token 数量 |  |
| 平均响应时间 |  |
| P95/P99 |  |
| TPS/QPS |  |
| HTTP 错误率 |  |
| 业务成功数 |  |
| 库存不足数 |  |
| 重复/限购/限流数 |  |
| SQL 校验 diff |  |
| 结论 | 通过/不通过 |

## 9. 通过标准

本轮压测同时满足以下条件才算通过：

1. 秒杀成功订单数不超过初始库存。
2. 同一用户同一秒杀活动无重复订单。
3. `初始库存 - 成功订单数 - 剩余库存 = 0`。
4. 入口接口无集中 HTTP 500。
5. 异步队列最终消费完成，结果可查询。
6. 支付、取消、退款等并发状态流转没有重复扣减或重复回滚。
7. 压测停止后应用、Redis、MySQL 资源占用能恢复到正常水平。

## 10. 常见问题排查

| 现象 | 可能原因 | 排查方向 |
| --- | --- | --- |
| 大量 `1001` 未登录 | token 文件缺少 `Bearer` 前缀或 token 过期 | 重新登录生成 token，检查 CSV 表头和内容 |
| 大量重复秒杀 | token 数不足，同一用户反复请求同一活动 | 增加用户数量或每轮使用新活动 |
| 库存没有预扣 | 活动未开启或 Redis 未预热库存 | 调用 `/seckill/admin/status/{id}/1` |
| 入口大量 500 | Redis/MySQL 连接不足、脚本异常、消息队列不可用 | 查看应用日志、Redis 慢日志、连接池配置 |
| SQL 校验 `diff != 0` | Redis 剩余库存未同步到 MySQL、落单失败回滚异常 | 检查消费者日志、回滚 Lua、库存同步逻辑 |
| JMeter 线程提前停止 | token 文件行数少于线程数且 CSV 不循环 | 增加 token 行数或调整 JMeter CSV 配置 |
| wrk 报 no tokens loaded | `TOKEN_FILE` 路径错误或文件只有表头 | 检查路径和 token 文件内容 |
