# 秒杀商城 Android App

这是独立于商城后端的 Android 客户端，当前第一版通过 Retrofit 调用现有 Spring Boot 接口，支持：

- 用户登录和 JWT 保存
- 商品列表
- 商品详情
- 立即下单
- 订单列表
- 退出登录

## 打开和运行

使用 Android Studio 打开 `android-app` 目录，等待 Gradle 同步后运行 `app`。

默认接口地址是：

```text
http://10.0.2.2:8080/api/
```

`10.0.2.2` 是 Android 模拟器访问宿主机 `localhost` 的特殊地址。运行前请先启动商城后端，并确认它监听 `8080` 端口。

如果是真机或其他模拟器，需要把地址改成电脑在同一局域网中的 IP，例如：

```bash
./gradlew assembleDebug -PapiBaseUrl=http://192.168.1.10:8080/api/
```

也可以在 Android Studio 的 Gradle 参数中填写 `-PapiBaseUrl=...`。后端已经允许明文 HTTP，适合本地开发测试；正式环境应改成 HTTPS。

## 测试账号

客户端不预置账号或密码。请在本地后端注册测试用户；自动化执行时通过`TEST_USERNAME`和`TEST_PASSWORD`环境变量注入凭据。

## 自动化测试

配套 Appium 自动化框架位于 [`../android-ui-tests/`](../android-ui-tests/README.md)，已覆盖登录校验、正确登录与退出、商品详情、订单列表和创建订单流程。测试失败时会自动保留截图、页面 XML 和 Logcat，便于定位客户端、接口或环境问题。

当前客户端属于可运行 MVP；后续还可继续补充注册、秒杀活动、网络异常、弱网和兼容性场景。
