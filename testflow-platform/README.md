# TestFlow 测试任务调度与质量分析平台

TestFlow 是一个面向 pytest 自动化项目的轻量级测试基础设施。它把项目、环境、测试套件、执行任务、用例结果和测试证据统一到一个页面中，形成“创建任务、异步执行、解析结果、查看报告、分析趋势”的完整闭环。

## 已实现功能

- 项目、测试环境与测试套件 REST API 管理。
- 内置异步 Worker 与先进先出任务队列，Web 请求不会阻塞等待 pytest。
- 固定参数方式启动 pytest，限制工作目录、测试路径和 marker，避免命令注入与目录越界。
- 支持 `PENDING`、`RUNNING`、`PASSED`、`FAILED`、`TIMEOUT`、`CANCELLED` 六类任务状态。
- 支持任务超时终止和运行中任务取消。
- 支持对失败、超时和取消任务一键重跑，并记录原任务编号。
- 支持按分钟间隔配置定时计划，自动创建测试任务，可随时暂停或启用。
- 支持本地内存队列与 Redis 队列两种模式；Redis 模式可将 Web 服务与 Worker 分开部署。
- 支持账号登录与 `ADMIN`、`TESTER`、`VIEWER` 三类角色权限。
- 使用 PBKDF2-SHA256 加盐保存密码，登录令牌只以SHA-256摘要形式入库并设置过期时间。
- 提供Android设备中心，通过ADB同步在线、离线、未授权设备及系统版本。
- Android任务执行前自动申请设备锁并注入 `ANDROID_SERIAL`、`UDID`，任何结束状态都会释放设备。
- Android任务自动检测Appium健康状态；未启动时由平台拉起，任务结束后只关闭平台创建的实例。
- 任务详情通过WebSocket实时显示pytest输出，并保留执行日志供后续追溯。
- 为每个任务创建独立证据目录，避免并发任务覆盖报告。
- 自动生成 JUnit XML、pytest-html 报告和执行日志。
- 解析 JUnit XML，将用例名称、状态、耗时和错误信息写入 SQLite。
- 展示任务通过率、平均耗时、最近执行趋势和高频失败用例。
- 内置5条平台自检冒烟用例，启动后可以立即创建任务验证完整链路。
- 支持接入仓库现有 `tests/api` 与 `android-ui-tests` 测试套件。
- 自动识别测试套件目录中的 `.venv`/`venv`，使用套件自己的Python依赖环境执行。

## 架构

```text
浏览器管理页面
      │ REST API
      ▼
FastAPI ───── SQLite
      │          ├─ 项目/环境/套件
      │          ├─ 任务状态
      │          └─ 用例级结果
      ▼
内置任务队列 ── Worker
                    │ subprocess（无 shell）
                    ▼
                 pytest
                    │
                    ├─ junit.xml
                    ├─ report.html
                    └─ execution.log
```

本地模式默认使用进程内队列和嵌入式Worker，便于一条命令启动；部署模式可以切换到Redis，并通过独立Worker入口实现多进程消费。

## 快速启动

项目根目录已有运行所需依赖时：

```bash
cd testflow-platform
python3 run.py
```

首次安装：

```bash
cd testflow-platform
python3 -m pip install -r requirements.txt
python3 run.py
```

访问地址：

- 管理页面：`http://127.0.0.1:8090`
- OpenAPI 文档：`http://127.0.0.1:8090/docs`
- 健康检查：`http://127.0.0.1:8090/api/health`

本地首次启动默认管理员：

```text
用户名：admin
密码：testflow123
```

正式演示或部署前必须使用环境变量修改默认凭据：

```bash
TESTFLOW_ADMIN_USERNAME=your_admin \
TESTFLOW_ADMIN_PASSWORD='your-strong-password' \
python3 run.py
```

只有数据库中尚无用户时才会创建初始管理员。修改环境变量不会覆盖已有用户密码。

首次启动会自动创建：

- TestFlow 平台自检项目。
- 本地环境。
- 平台自检冒烟套件。
- 秒购商城自动化测试项目、本地联调环境、接口冒烟套件与Android UI全量回归套件。

进入“测试任务”，创建并执行自检任务，应得到 `5 passed`。

## Redis 队列与独立 Worker

先启动 Redis，然后使用相同数据库路径分别启动 Web 服务和 Worker。

终端一：

```bash
cd testflow-platform
TESTFLOW_QUEUE_BACKEND=redis \
TESTFLOW_REDIS_URL=redis://127.0.0.1:6379/0 \
TESTFLOW_EMBEDDED_WORKER=false \
python3 run.py
```

终端二：

```bash
cd testflow-platform
TESTFLOW_QUEUE_BACKEND=redis \
TESTFLOW_REDIS_URL=redis://127.0.0.1:6379/0 \
python3 worker.py
```

多个Worker进程可以共同消费Redis队列。任务执行前会再次检查数据库状态，因此重复队列消息不会重复运行已经完成的任务。

## 接入秒购商城接口测试

通过 API 或管理页面创建：

1. 项目工作目录：仓库根目录。
2. 环境服务地址：`http://127.0.0.1:8080/api`。
3. 测试路径：`tests/api`。
4. pytest 标签：`smoke` 或 `regression`。
5. 在环境变量 JSON 中填写测试账号等非敏感配置。

敏感密码应通过启动平台进程时的系统环境变量注入，不要写入仓库。环境配置中的变量只会注入目标 pytest 子进程。

## 接入 Android UI 自动化

创建测试套件：

```json
{
  "name": "Android 冒烟回归",
  "test_type": "android",
  "test_path": "android-ui-tests",
  "marker": "smoke",
  "timeout_seconds": 1800,
  "device_required": true
}
```

执行前需保证模拟器已启动，并通过平台“设备中心”扫描到在线设备。Appium无需手动启动：平台会优先复用健康实例，否则使用 `android-ui-tests/node_modules/.bin/appium` 自动拉起，并在任务结束后关闭该实例。商城服务、测试账号等业务依赖仍需提前准备，账号密码通过 `TEST_USERNAME`、`TEST_PASSWORD` 等进程环境变量注入。

## 主要接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET/POST | `/api/projects` | 查询或创建项目 |
| GET/POST | `/api/environments` | 查询或创建环境 |
| GET/POST | `/api/suites` | 查询或创建测试套件 |
| GET/POST | `/api/runs` | 查询或创建执行任务 |
| GET | `/api/runs/{id}` | 查看任务、用例和证据详情 |
| GET | `/api/runs/{id}/log` | 读取任务历史执行日志 |
| WS | `/ws/runs/{id}/logs?token=...` | 订阅任务实时执行日志 |
| POST | `/api/runs/{id}/cancel` | 取消等待中或运行中任务 |
| POST | `/api/runs/{id}/retry` | 重跑失败、超时或取消任务 |
| GET/POST | `/api/schedules` | 查询或创建定时计划 |
| POST | `/api/schedules/{id}/toggle` | 暂停或启用计划 |
| GET | `/api/dashboard` | 获取质量指标和趋势 |
| POST | `/api/auth/login` | 登录并签发限时会话令牌 |
| GET | `/api/auth/me` | 查询当前用户与角色 |
| GET/POST | `/api/users` | 管理员查询或创建用户 |
| GET | `/api/devices` | 查询Android设备与锁状态 |
| POST | `/api/devices/scan` | 通过ADB重新发现设备 |

## 数据与证据

本地运行数据默认保存到：

```text
testflow-platform/data/
├── testflow.db
└── artifacts/
    └── RUN-.../
        ├── execution.log
        ├── appium.log
        ├── junit.xml
        └── report.html
```

`data/` 已加入 `.gitignore`，不会把本地数据库、测试日志或报告提交到仓库。

## 当前边界与下一阶段

当前版本已形成单机完整闭环，并具备Redis可选队列、独立Worker、定时计划、角色权限、Android设备锁、Appium生命周期管理和实时日志。后续可继续增加敏感变量加密、失败通知以及面向持续集成环境的部署方案。
