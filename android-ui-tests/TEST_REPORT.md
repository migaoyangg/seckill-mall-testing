# Android UI 自动化测试记录

## 测试范围

- 应用：秒购商城 Android 客户端 Debug 包
- 链路：Android Emulator → Appium/UiAutomator2 → 商城客户端 → Spring Boot API → MySQL/Redis
- 用例：登录输入校验、错误密码、登录/退出、应用重启状态恢复、商品列表/详情、页面切换、订单列表、创建订单

## 执行策略

- 默认执行 12 条不创建业务数据的用例。
- 创建订单用例标记为 `mutating`，仅在显式设置 `RUN_MUTATING=true` 后执行。
- 下单前记录已有订单，下单后通过订单列表确认结果，测试结束后调用取消订单接口恢复库存。
- 失败时自动采集 PNG 截图、页面 XML 和 ADB Logcat，同时生成 pytest-html 与 Allure 原始结果。

## 验证结果（2026-09-23）

- Gradle Debug APK 构建：通过。
- pytest 用例收集：13 条。
- 冒烟测试初次执行：3 通过、1 失败；定位为 AppCompat 弹窗资源 ID 使用错误，修正后单用例复测通过。
- 默认回归初次执行：4 通过、1 失败、1 跳过；定位为异步错误文案首次读取为空，改为等待非空文本后单用例复测通过。
- 下单闭环单独验证：1 条通过，新增订单在测试结束后自动取消，状态校验为“已取消”。
- 最终全量回归：13 条全部通过、0 条跳过，耗时 202.47 秒；HTML 报告位于 `artifacts/full-regression-13-report.html`，Allure 原始结果位于 `artifacts/allure-full-regression-13/`。

以上问题均为自动化脚本稳定性问题，应用业务响应正常；失败现场文件保留在 `artifacts/` 目录，可用于复盘定位过程。
