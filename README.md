# 🔥 高并发秒杀商城系统

基于 Spring Boot 3 的高并发秒杀商城系统，适合互联网大厂校招后端面试。

## ✨ 项目亮点

- 🚀 **高并发秒杀**: Redis 预减库存 + Lua 脚本原子扣减 + 分布式锁
- 🧵 **异步下单削峰**: 秒杀接口快速受理并返回 requestId，后台消费者异步创建订单
- 🐇 **可切换消息通道**: 默认 Redis 队列异步下单，可通过 `seckill.async.mode=rabbitmq` 切换 RabbitMQ
- 🔒 **多级防超卖**: Redis 预扣 → MySQL 乐观锁 → 用户-活动唯一索引，多重保障
- ♻️ **失败回滚**: 入队失败、落单失败后回滚 Redis 库存与用户限购计数
- 🧱 **订单幂等**: requestId、orderNo、唯一索引和订单回查共同防止重复消费
- ⏱ **超时关单**: 定时扫描兜底，RabbitMQ 模式支持 TTL + 死信队列延迟关单
- 💳 **支付/退款状态机**: 支持支付回调幂等、取消、超时关闭、退款等状态流转
- 🎫 **JWT 认证**: JWT + Redis 双重校验，支持主动失效
- ⚡ **Redis 缓存**: 缓存穿透/击穿/雪崩解决方案
- 🔐 **分布式锁**: Redisson 实现集群环境并发安全

## 🛠 技术栈

| 技术 | 说明 |
|------|------|
| Spring Boot 3 | 基础框架 |
| MySQL 8 | 数据存储 |
| Redis | 缓存、库存预减 |
| RabbitMQ | 异步下单、延迟关单 |
| Redisson | 分布式锁 |
| MyBatis Plus | ORM 框架 |
| JWT | 认证方案 |
| Knife4j | API 文档 |

## 🚀 快速启动

### 1. 环境准备

- JDK 17+
- MySQL 8.0+
- Redis 6.0+
- Maven 3.8+

### 2. 数据库初始化

```bash
# 创建数据库并导入数据
mysql -u root -p < src/main/resources/db/schema.sql
```

### 3. 配置本地环境变量

复制环境变量模板，在本地填写实际值；`.env` 已被 Git 忽略：

```bash
cp .env.example .env
set -a
source .env
set +a
```

### 4. 启动项目

```bash
mvn spring-boot:run
```

### 5. 访问接口文档

浏览器打开: http://localhost:8080/api/doc.html

### 6. 从零学习软件测试

测试开发实战教程见 [`docs/beginner-testing-guide.md`](docs/beginner-testing-guide.md)，包含单元测试、集成测试、接口冒烟、核心流程、并发压测、缺陷记录和发布门禁。默认先执行：

```bash
mvn test
```

## 📝 核心接口

| 模块 | 接口 | 说明 |
|------|------|------|
| 用户 | POST /api/user/register | 注册 |
| 用户 | POST /api/user/login | 登录 |
| 商品 | GET /api/product/list | 商品列表 |
| 商品 | GET /api/product/detail/{id} | 商品详情 |
| 秒杀 | GET /api/seckill/list | 秒杀活动列表 |
| 秒杀 | POST /api/seckill/do/{id} | 参与秒杀 |
| 秒杀 | GET /api/seckill/result/{requestId} | 查询异步下单结果 |
| 订单 | POST /api/order/create | 创建订单 |
| 订单 | POST /api/order/pay/callback/{orderNo} | 支付回调幂等 |
| 订单 | POST /api/order/refund/{orderNo} | 模拟退款 |
| 订单 | GET /api/order/list | 订单列表 |
| 管理员 | POST /api/admin/product/add | 新增商品 |
| 管理员 | POST /api/admin/seckill/create | 创建秒杀活动 |

## 🔑 测试账号

仓库不预置账号或密码。通过注册接口创建本地测试账号，并使用环境变量向自动化测试注入凭据；CI 使用 GitHub Actions Secrets 创建一次性测试账号。

完整持续集成流程见 [`docs/ci-pipeline.md`](docs/ci-pipeline.md)。

## 📐 系统架构

```
用户请求 → Spring Boot → Redis Lua (限流/限购/库存预扣)
                        → Redis Queue / RabbitMQ (削峰)
                        → 后台消费者 → MySQL (幂等建单/乐观锁扣库存)
                        → Redis Result (异步结果查询)
```

## 压测脚本

项目内置 JMeter、wrk 和 SQL 校验脚本：

```bash
# JMeter 示例
jmeter -n -t scripts/jmeter/seckill-entry.jmx \
  -Jthreads=500 -Jramp=10 -Jloops=1 -JgoodsId=1 \
  -JtokenFile=scripts/jmeter/tokens.csv \
  -JresultFile=target/seckill-entry.jtl

# wrk 示例
TOKEN_FILE=scripts/jmeter/tokens.csv SECKILL_GOODS_ID=1 \
  wrk -t8 -c500 -d30s -s scripts/wrk/seckill.lua http://localhost:8080
```

压测后执行 `scripts/sql/seckill_check.sql` 校验订单数、重复下单和库存一致性。

详细的性能并发测试用例见：`docs/performance-concurrency-test-cases.md`

## Android 客户端

`android-app/` 是独立的 Android 商城客户端工程，业务后端保持不变。当前支持登录、商品列表、商品详情、下单和订单查询。具体启动方式见 [`android-app/README.md`](android-app/README.md)。

配套的 Android UI 自动化项目位于 [`android-ui-tests/`](android-ui-tests/README.md)，采用 Appium + UiAutomator2 + Python + pytest + Page Object，覆盖登录、商品详情、订单查询和下单主链路，并支持失败截图、页面 XML、Logcat、HTML/Allure 报告以及环境变量配置。

## 简历与面试说明

项目升级点、推荐简历写法和面试追问答案见: `docs/seckill-resume-guide.md`。

## 📁 项目结构

```
seckill-mall/
├── src/main/java/com/seckill/mall/
│   ├── controller/        # 控制层
│   ├── service/           # 业务层
│   ├── mapper/            # 持久层
│   ├── entity/            # 实体类
│   ├── dto/               # 请求参数
│   ├── vo/                # 响应结果
│   ├── common/            # 通用组件
│   ├── config/            # 配置类
│   ├── interceptor/       # 拦截器
│   ├── annotation/        # 自定义注解
│   ├── exception/         # 异常处理
│   ├── utils/             # 工具类
│   └── schedule/          # 定时任务
└── src/main/resources/
    ├── application.yml    # 主配置
    ├── lua/               # Lua脚本
    └── db/                # 数据库脚本
```
