# 秒购商城接口自动化测试

该目录是项目一的 Python 接口自动化框架，统一管理环境配置、HTTP 客户端、公共断言、认证会话、用例分类和测试报告。

## 目录结构

```text
tests/api/
├── framework/
│   ├── config.py       # 环境变量与运行配置
│   ├── client.py       # HTTP Session、Token、超时和请求日志
│   ├── assertions.py   # HTTP 状态码、业务码和统一响应断言
│   └── data_probes.py  # MySQL/Redis数据一致性校验
├── conftest.py         # 认证会话、订单及秒杀动态数据工厂
├── test_user_api.py
├── test_product_api.py
├── test_admin_api.py
├── test_order_api.py
└── test_seckill_api.py
```

## 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `BASE_URL` | `http://127.0.0.1:8080/api` | 被测服务地址 |
| `API_TEST_TIMEOUT` | `5` | 单次HTTP请求超时秒数 |
| `API_TEST_USERNAME` | 必填 | 普通用户账号 |
| `API_TEST_PASSWORD` | 必填 | 普通用户密码 |
| `API_TEST_ADMIN_USERNAME` | 必填 | 管理员账号 |
| `API_TEST_ADMIN_PASSWORD` | 必填 | 管理员密码 |
| `API_TEST_PRODUCT_ID` | `1` | 可下单商品ID |
| `API_TEST_DATA_VALIDATION` | `false` | 是否执行MySQL/Redis一致性用例 |
| `API_TEST_MYSQL_HOST` | `127.0.0.1` | MySQL地址 |
| `API_TEST_MYSQL_PORT` | `3306` | MySQL端口 |
| `API_TEST_MYSQL_DATABASE` | `seckill_mall` | 测试数据库 |
| `API_TEST_MYSQL_USERNAME` | `root` | MySQL用户 |
| `API_TEST_MYSQL_PASSWORD` | 开启数据校验时必填 | MySQL密码 |
| `API_TEST_REDIS_HOST` | `127.0.0.1` | Redis地址 |
| `API_TEST_REDIS_PORT` | `6379` | Redis端口 |
| `API_TEST_REDIS_PASSWORD` | 空 | Redis密码；无认证的本地环境可留空 |

敏感账号和密码应通过本地环境变量或CI Secret传入，不要提交真实生产凭据。

## 执行命令

安装依赖：

```bash
python3 -m pip install -r requirements-test.txt
```

执行完整接口回归并输出JUnit XML：

```bash
python3 -m pytest -q \
  --junitxml=target/pytest-reports/api-junit.xml
```

项目Java基线使用JDK 17：

```bash
mvn test
```

启用MySQL与Redis一致性校验：

```bash
API_TEST_DATA_VALIDATION=true python3 -m pytest -m data_validation \
  --junitxml=target/pytest-reports/data-consistency-junit.xml \
  --html=target/pytest-reports/data-consistency-report.html \
  --self-contained-html
```

只执行会修改业务数据的用例：

```bash
python3 -m pytest -m destructive
```

安装 `pytest-html` 后生成单文件HTML报告：

```bash
python3 -m pytest \
  --html=target/pytest-reports/api-report.html \
  --self-contained-html
```

## 当前基线

- Maven单元、Web层及调度测试：`38/38`通过。
- pytest接口回归：`28/28`通过，耗时 `6.13s`。
- JUnit XML：`target/pytest-reports/api-junit.xml`。
- 单文件HTML报告：`target/pytest-reports/api-report.html`。
- 已覆盖用户、商品、管理员、普通订单、支付与退款幂等、超时关单调度、秒杀时间边界、防重复请求、异步结果轮询，以及MySQL/Redis一致性校验。
- 秒杀用例动态创建独立商品和活动，结束后通过业务接口及精确Redis Key自动回收测试数据。
- 数据一致性套件共`5`条：校验`orders.status/refund_time`、普通商品库存、秒杀订单归属、`order:timeout:*`和`seckill:stock:*`；详细口径见[`docs/data-consistency-report.md`](../../docs/data-consistency-report.md)。
- JMeter秒杀入口基准：100用户、1秒Ramp-up，业务及技术错误率`0.00%`，平均`81.87 ms`、P95 `255.6 ms`、P99 `283.9 ms`、报告吞吐`123.30请求/秒`；100笔订单全部异步落库，重复用户订单、库存差值与队列积压均为`0`。详见[`docs/performance-result-20260922.md`](../../docs/performance-result-20260922.md)。
