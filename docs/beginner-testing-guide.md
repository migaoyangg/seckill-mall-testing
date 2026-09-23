# 秒杀商城软件测试开发入门实战

这份教程假设你没有测试经验，目标是带你完成一次真实项目的测试工作：从看代码和需求开始，到写自动化测试、测接口、测并发、检查数据、记录缺陷，最后形成测试结论。

## 1. 先建立一个正确的认识

测试不是“点几下页面找问题”，而是用可重复的方法回答三个问题：

1. 功能是否符合需求？
2. 出错、重复请求、并发时是否仍然安全？
3. 数据、库存、订单状态是否始终一致？

本项目最重要的风险不是页面颜色，而是库存超卖、重复订单、支付重复回调、权限绕过、消息失败后的回滚和超时关单。

## 2. 认识项目结构

先按下面的顺序看代码，不要一开始试图读懂所有文件：

| 目录 | 你要理解的内容 | 测试重点 |
| --- | --- | --- |
| `controller` | HTTP 路径、参数、权限注解 | 状态码、响应体、参数校验、鉴权 |
| `service/impl` | 业务规则和状态流转 | 正常、异常、边界、幂等 |
| `mapper` | 数据库读写 | SQL 条件、乐观锁、唯一约束 |
| `utils`、`mq` | Redis、锁、消息和 JWT | 失败、超时、重复消费 |
| `src/test` | 已有 JUnit/Mockito 示例 | 学习测试写法并补齐缺口 |
| `docs`、`scripts` | 用例、接口脚本、压测方案 | 按文档执行并留下证据 |

业务主链路是：

```text
登录 → 浏览商品 → 创建订单/参与秒杀 → 异步落单 → 支付 → 发货/退款/超时关单
```

## 3. 测试分层

从快到慢依次执行：

| 层级 | 工具 | 是否需要 MySQL/Redis | 目的 |
| --- | --- | --- | --- |
| 单元测试 | JUnit 5 + Mockito | 否 | 验证一个 service 方法的业务规则 |
| 集成测试 | `@SpringBootTest` | 是 | 验证 Spring 配置和真实中间件连接 |
| 接口测试 | `scripts/api_test.py`、curl | 是 | 从 HTTP 角度验证完整接口 |
| 端到端流程 | API 脚本串联多个接口 | 是 | 验证真实用户链路 |
| 性能/并发 | JMeter、wrk | 是 | 验证吞吐、延迟、超卖和稳定性 |

先把单元测试跑通，再测接口和并发。不要用一次压测替代功能测试。

## 4. 第 0 步：准备环境

项目要求 JDK 17+、Maven 3.8+、MySQL 8 和 Redis 6。建议固定使用 JDK 17，检查命令：

```bash
java -version
mvn -version
redis-cli ping
mysqladmin ping -h127.0.0.1 -uroot -p
```

本机如果同时安装了多个 JDK，先设置 JDK 17（下面路径按本机实际修改）：

```bash
export JAVA_HOME=/Users/migaoyang/Library/Java/JavaVirtualMachines/ms-17.0.19/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

首次准备数据库：

```bash
mysql -u root -p < src/main/resources/db/schema.sql
```

不要把生产库或共享测试库用于练习。每轮秒杀尽量使用独立活动 ID。

## 5. 第 1 步：跑基线测试

默认命令只跑快速单元测试：

```bash
mvn test
```

你要看的是最后的 `Tests run`、`Failures`、`Errors` 和 `BUILD SUCCESS`。本项目当前有 24 个服务层单元测试，覆盖用户登录、商品缓存、普通订单、支付回调、秒杀预扣和库存同步。

按模块单独运行：

```bash
mvn -Dtest=UserServiceImplTest test
mvn -Dtest=OrderServiceImplTest test
mvn -Dtest=SeckillServiceImplTest test
```

完整上下文测试属于集成测试，需要 MySQL 和 Redis：

```bash
mvn -Dtest=SeckillMallApplicationIT test
```

如果错误里出现 `RedisConnectionException`，先检查 Redis；如果 Mockito 报 Byte Buddy agent 错误，确认测试资源文件 `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` 存在，并使用项目推荐的 JDK 17。

## 6. 第 2 步：学会读懂一条单元测试

以 `SeckillServiceImplTest` 为例，一条测试通常分三段（AAA）：

```java
// Arrange：准备活动、模拟 Redis 返回值
when(redisUtils.executeSeckillDeduct(11L, 22L, 1)).thenReturn(-1L);

// Act：调用被测方法
BusinessException ex = assertThrows(BusinessException.class,
        () -> service.doSeckill(11L, 22L));

// Assert：断言结果和副作用
assertEquals(ResultCode.SECKILL_STOCK_EMPTY.getCode(), ex.getCode());
verify(redisUtils).deleteSeckillRepeatFlag(11L, 22L);
```

记住四个常用 Mockito 语句：

- `when(...).thenReturn(...)`：规定依赖的返回值。
- `when(...).thenThrow(...)`：模拟依赖失败。
- `verify(...)`：确认某个动作确实发生。
- `never()`：确认某个危险动作没有发生，例如库存扣减。

每个测试只回答一个问题。测试名使用“场景 + 预期”，例如 `doSeckillRollsBackWhenProducerFails`。

## 7. 第 3 步：按风险补单元测试

按照下面顺序补，不要追求一开始 100% 覆盖率：

1. `UserServiceImpl`：注册重复、密码错误、禁用账号、修改密码后 token 失效。
2. `ProductServiceImpl`：缓存命中、缓存未命中、商品不存在、下架商品不可购买、分页和排序。
3. `OrderServiceImpl`：库存不足、取消回滚、支付回调幂等、退款幂等、超时关单。
4. `SeckillServiceImpl`：未开始、已结束、售罄、重复请求、限流、入队失败回滚。
5. `SeckillStockServiceImpl`：预热、同步库存、Redis 没有库存时不误更新数据库。

写每条测试前先做一个小表：

| 输入/前置条件 | 预期业务码或结果 | 不应发生的动作 |
| --- | --- | --- |
| 活动未开始 | `4001` | 不扣 Redis 库存、不发消息 |
| Redis 库存为 0 | `4003` | 不产生订单 |
| 队列发送失败 | `500`/业务失败 | Redis 预扣和重复标记必须回滚 |

测试通过后，再用 `mvn test` 回归全部单元测试。

## 8. 第 4 步：接口冒烟测试

启动应用：

```bash
mvn spring-boot:run
```

确认文档可访问：

```bash
curl -i http://127.0.0.1:8080/api/doc.html
curl -i http://127.0.0.1:8080/api/v3/api-docs
```

执行项目自带 API 脚本：

```bash
python3 scripts/api_test.py \
  --base-url http://127.0.0.1:8080/api \
  --product-id 1 --seckill-id 3 \
  --include-mutating --include-seckill \
  --seckill-poll-times 8 --seckill-poll-interval 0.5
```

先看只读接口，再执行会写数据的订单/秒杀接口。每次写数据测试后记录订单号和活动 ID，避免历史数据影响下一次结果。

接口测试重点检查：

- HTTP 状态码和业务 `code` 是否符合约定。
- 未登录、无效 token、普通用户访问管理接口是否被拒绝。
- 参数为空、类型错误、数量为 0/负数时是否返回 `2001`。
- 响应中不能泄露密码、其他用户订单或其他用户的秒杀结果。

详细用例见 [`api-test-cases.md`](api-test-cases.md) 和 [`core-flow-test-cases.md`](core-flow-test-cases.md)。

## 9. 第 5 步：验证完整业务流程

至少执行下面 5 条流程，并保存请求与数据库结果：

1. 普通订单：登录 → 商品详情 → 创建订单 → 查询详情 → 支付。
2. 取消订单：创建待支付订单 → 取消 → 检查状态为 4、库存恢复。
3. 退款：创建 → 支付 → 退款 → 检查状态为 7、库存只恢复一次。
4. 秒杀成功：管理员开启活动 → 用户参与 → 轮询 `requestId` → 查询秒杀订单。
5. 秒杀售罄：库存设为 1，两个用户同时参与，最多只能有一笔成功订单。

每条流程都检查三类结果：接口响应、订单状态、库存变化。只看接口返回“成功”是不够的。

## 10. 第 6 步：并发和性能测试

先生成不同用户 token：

```bash
python3 scripts/jmeter/generate_tokens.py \
  --base-url http://127.0.0.1:8080/api \
  --count 100 \
  --output scripts/jmeter/tokens.csv
```

从小到大执行 100、500、1000 并发，不要直接从 1000 开始：

```bash
jmeter -n -t scripts/jmeter/seckill-entry.jmx \
  -Jthreads=100 -Jramp=10 -Jloops=1 -JgoodsId=1 \
  -JtokenFile=scripts/jmeter/tokens.csv \
  -l target/seckill-entry-100.jtl
```

压测后执行数据校验：

```bash
mysql -u root -p seckill_mall < scripts/sql/seckill_check.sql
```

重点指标不是“请求越多越好”，而是：成功订单数不超过初始库存、重复订单为 0、`diff=0`、HTTP 500 受控、P95/P99 可接受。预期的售罄、重复、限购业务失败不能简单当成系统故障。

完整参数和场景见 [`performance-concurrency-test-cases.md`](performance-concurrency-test-cases.md) 与 [`scripts/jmeter/README.md`](../scripts/jmeter/README.md)。

## 11. 第 7 步：发现问题后怎么记录

一条合格缺陷至少包含：

```text
标题：支付回调使用同一流水号并发时产生两次状态更新
环境：JDK 17、Redis、MySQL、代码版本
前置条件：订单 O-1 待支付
步骤：并发调用回调接口 20 次
预期：只有一次状态从 0 变为 1，流水号唯一
实际：出现两次更新/HTTP 500/库存异常
证据：请求、响应、日志、SQL 查询结果
严重程度：阻断/严重/一般/轻微
```

修复后必须做两件事：重新执行原用例，并执行受影响模块的回归用例。不要只验证“这次不报错”。

## 12. 第 8 步：测试报告和发布门禁

每轮测试结束写一页报告，包含：测试范围、环境、命令、通过/失败数量、缺陷、性能数据、遗留风险和发布结论。

建议的发布门禁：

- 单元测试无 Failure/Error。
- 鉴权和越权用例全部通过。
- 秒杀不超卖、无重复订单、库存校验 `diff=0`。
- 支付、取消、退款、超时关单在重复/并发下幂等。
- 无未评估的阻断或严重缺陷。

## 13. 你的 7 天练习计划

| 天数 | 练习 | 完成标准 |
| --- | --- | --- |
| 第 1 天 | 看目录、Controller、Service，画出秒杀流程 | 能说清一次请求经过哪些组件 |
| 第 2 天 | 跑 `mvn test`，逐条阅读已有测试 | 能解释 `when`、`verify`、`assertThrows` |
| 第 3 天 | 为商品服务补 4 条单元测试 | 覆盖缓存命中/未命中、下架、不存在 |
| 第 4 天 | 启动 MySQL/Redis，跑上下文和 API 冒烟 | 能定位连接失败和业务失败的区别 |
| 第 5 天 | 执行 5 条核心业务流程 | 每条都有接口、订单、库存证据 |
| 第 6 天 | 做库存为 1 的双用户并发测试 | 最多一笔成功，SQL 校验一致 |
| 第 7 天 | 写缺陷和测试报告 | 其他人能按报告复现结论 |

今天先完成这三个动作：

```bash
mvn test
mvn -Dtest=SeckillServiceImplTest test
sed -n '1,220p' src/test/java/com/seckill/mall/service/impl/SeckillServiceImplTest.java
```

你能解释这三个测试场景后，就已经掌握了本项目测试开发的基本方法：准备输入、隔离依赖、调用业务、断言结果和副作用。
