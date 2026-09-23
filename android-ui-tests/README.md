# 秒购商城 Android 客户端自动化测试框架

基于 Appium 3、UiAutomator2、Python、pytest 和 Page Object，对 Android 商城客户端的登录、商品浏览、商品详情、下单、订单查询和登录状态恢复流程进行自动化验证。

## 已实现能力

- Page Object 分离元素定位、页面操作和业务断言。
- pytest fixture 管理 Driver 和登录前置条件。
- smoke、regression、mutating 三类用例标签。
- 下单用例默认受开关保护；显式执行后通过业务 API 自动取消新增订单并恢复库存。
- 用例失败时保存截图、页面 XML 和 Logcat，并附加到 Allure 报告。
- 设备、APK、账号和等待时间均通过环境变量配置。

## 目录结构

```text
android-ui-tests/
├── config.py
├── conftest.py
├── pages/
├── tests/
├── utils/
├── artifacts/
├── pytest.ini
└── requirements.txt
```

## 环境准备

1. 安装 Android SDK、启动模拟器并确认 `adb devices` 能看到设备。
2. 安装项目固定版本的 Appium 和 UiAutomator2 Driver：

```bash
cd android-ui-tests
npm install
```

3. 创建 Python 虚拟环境并安装依赖：

```bash
cd android-ui-tests
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
```

4. 构建 APK，并确保宿主机上的商城后端、MySQL 和 Redis 已启动：

```bash
cd ../android-app
./gradlew assembleDebug
```

模拟器默认通过 `http://10.0.2.2:8080/api/` 访问宿主机后端。测试账号不写入仓库，运行前必须通过 `TEST_USERNAME` 和 `TEST_PASSWORD` 环境变量注入。

## 执行

先设置 Android SDK 路径并启动 Appium：

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
npm run appium
```

执行不修改业务数据的冒烟用例：

```bash
cd android-ui-tests
TEST_USERNAME=your_user TEST_PASSWORD=your_password \
.venv/bin/pytest -m smoke --html=artifacts/report.html --self-contained-html
```

生成 Allure 原始结果：

```bash
TEST_USERNAME=your_user TEST_PASSWORD=your_password \
.venv/bin/pytest --alluredir=artifacts/allure-results
allure serve artifacts/allure-results
```

显式执行会创建订单的用例：

```bash
TEST_USERNAME=your_user TEST_PASSWORD=your_password \
RUN_MUTATING=true .venv/bin/pytest -m mutating
```

## 常用环境变量

参考 `.env.example`。本框架不会自动读取 `.env`，可通过 shell、IDE 或 CI 注入变量。

## 当前用例

共 13 条：登录双空、账号为空、密码为空、错误密码、正确登录退出、登录状态重启恢复、退出状态重启保持、商品列表、商品详情、关闭详情、订单页签、商品/订单切换，以及创建订单并自动清理。

## 持续集成

`.github/workflows/android-ui.yml` 在 Pull Request 或手动触发时启动 MySQL、Redis、RabbitMQ、Spring Boot 和 Android Emulator，构建 APK，启动 Appium，执行 smoke 用例，并归档 HTML、Allure、截图与服务日志。

## 已验证结果

- Debug APK 可通过 Gradle 正常构建。
- pytest 可收集 13 条 UI 自动化用例。
- `RUN_MUTATING=true` 全量回归 13 条全部通过；下单后通过业务接口自动取消测试订单。
- 已在 Android Emulator + 本地商城后端环境完成真实端到端执行。

最近一次验证记录见 [`TEST_REPORT.md`](TEST_REPORT.md)。
