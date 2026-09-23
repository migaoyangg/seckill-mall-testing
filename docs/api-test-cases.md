# 秒杀商城接口测试记录

## 1. 测试说明

本文档只记录已经实际执行过的接口测试，不记录尚未执行的测试计划。

- 基础地址：`http://localhost:8080/api`
- 测试工具：`curl`
- 测试日期：2026-09-18
- 测试环境：Spring Boot、MySQL、Redis、JDK 17
- 鉴权请求头：`Authorization: Bearer <token>`
- 测试账号：`migotest2020`（普通用户）

公共响应格式：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

判断接口结果时，同时检查 HTTP 状态码、JSON 业务码、message 和 data。

## 2. 商品接口

| 用例 ID | 接口和场景 | 请求数据 | 预期结果 | 实际结果 | 结论 |
| --- | --- | --- | --- | --- | --- |
| PROD-001 | 搜索商品成功 | `GET /product/search?keyword=phone&page=1&size=10` | HTTP 200，`code=200`，返回商品列表 | HTTP 200，返回 1 个 iPhone 商品 | 通过 |
| PROD-002 | 搜索缺少关键词 | `GET /product/search` | HTTP 400，`code=2001`，提示缺少 keyword | HTTP 400，`code=2001`，message 为“缺少必要参数: keyword” | 通过 |
| PROD-003 | 页码不是数字 | `GET /product/search?keyword=phone&page=abc` | HTTP 400，`code=2001`，提示 page 类型错误 | HTTP 400，`code=2001`，message 为“参数类型错误: page” | 通过 |
| PROD-004 | 查询存在的商品 | `GET /product/detail/1` | HTTP 200，`code=200`，返回商品 id=1 | HTTP 200，返回 iPhone 15 Pro Max | 通过 |
| PROD-005 | 查询不存在的商品 | `GET /product/detail/999` | 业务码 `3002`，data 为 null | HTTP 200，`code=3002`，message 为“商品不存在” | 通过 |

## 3. 用户注册接口

| 用例 ID | 接口和场景 | 请求数据 | 预期结果 | 实际结果 | 结论 |
| --- | --- | --- | --- | --- | --- |
| USER-001 | 注册成功 | 合法 username、password、nickname、phone | HTTP 200，`code=200` | HTTP 200，`code=200`，data 为 null | 通过 |
| USER-002 | 手机号格式错误 | `phone=12345678901` | HTTP 400，`code=2001` | HTTP 400，`code=2001`，message 为“手机号格式不正确” | 通过 |
| USER-003 | 用户名重复 | 已存在的 username | `code=1007` | `code=1007`，message 为“用户名已存在” | 通过 |
| USER-004 | 手机号重复 | 新 username + 已注册 phone | `code=1008` | `code=1008`，message 为“手机号已被注册” | 通过 |

## 4. 用户登录和认证接口

| 用例 ID | 接口和场景 | 请求数据 | 预期结果 | 实际结果 | 结论 |
| --- | --- | --- | --- | --- | --- |
| AUTH-001 | 登录成功 | 正确 username 和 password | HTTP 200，`code=200`，data.token 非空 | HTTP 200，`code=200`，返回 token，用户 role=0 | 通过 |
| AUTH-002 | 密码错误 | 正确 username + 错误 password | `code=1006`，不返回有效 token | `code=1006`，message 为“用户名或密码错误” | 通过 |
| AUTH-003 | 未携带 Token 查询个人信息 | `GET /user/info` | `code=1001` | HTTP 200，`code=1001`，message 为“未登录或Token已过期” | 通过 |
| AUTH-004 | 有效 Token 查询个人信息 | `GET /user/info` + 有效 Token | `code=200`，返回当前用户信息 | HTTP 200，`code=200`，返回 migotest2020 信息 | 通过 |
| AUTH-005 | 退出登录 | `POST /user/logout` + 有效 Token | `code=200` | HTTP 200，`code=200` | 通过 |
| AUTH-006 | 退出后继续访问 | 退出后使用原 Token 请求 `/user/info` | `code=1004` | HTTP 200，`code=1004`，message 为“Token已过期” | 通过 |
| AUTH-007 | Token 无效 | `GET /user/info` + 截断或伪造 Token | `code=1003` | HTTP 200，`code=1003`，message 为“Token无效” | 通过 |
| AUTH-008 | 登录缺少用户名 | `POST /user/login`，只传 password | HTTP 400，`code=2001`，提示用户名不能为空 | `code=2001`，message 为“用户名不能为空” | 通过 |
| AUTH-009 | 登录缺少密码 | `POST /user/login`，只传 username | HTTP 400，`code=2001`，提示密码不能为空 | HTTP 400，`code=2001`，message 为“密码不能为空” | 通过 |

## 5. 本轮测试结论

- 已执行用例：21 个。
- 通过：21 个。
- 失败：0 个。
- 当前覆盖范围：商品查询、注册校验、登录、Token 鉴权、管理员权限、订单列表和订单状态流转。
- 尚未记录的模块：支付、退款、秒杀接口、并发测试。

## 6. 结果判定规则

- HTTP 200 + `code=200`：接口和业务都成功。
- HTTP 400 + `code=2001`：请求参数不合法。
- HTTP 200 + 业务错误码：请求已被服务处理，但业务规则拒绝了操作。
- `code=1001`：没有登录或没有携带 Token。
- `code=1003`：Token 格式或签名无效。
- `code=1004`：Token 已退出登录或已过期。

## 7. 订单接口

| 用例 ID | 接口和场景 | 请求数据 | 预期结果 | 实际结果 | 结论 |
| --- | --- | --- | --- | --- | --- |
| ORDER-001 | 未登录查询订单列表 | `GET /order/list?page=1&size=10` | `code=1001` | HTTP 200，`code=1001`，未登录访问被拦截 | 通过 |
| ORDER-002 | 已登录查询空订单列表 | 普通用户 Token，`GET /order/list?page=1&size=10` | `code=200`，records 为空也算成功 | HTTP 200，`code=200`，total=0，records=[] | 通过 |
| ORDER-003 | 创建并取消普通订单 | `POST /order/create?productId=94840&quantity=1`，随后查询并取消 | 创建成功；初始 status=0；取消后 status=4 | 订单创建、详情查询、取消和状态确认全部成功 | 通过 |
| ORDER-004 | 创建订单数量为 0 | `POST /order/create?productId=94840&quantity=0` | HTTP 400，`code=2001` | HTTP 400，提示购买数量必须大于 0 | 通过 |
| ORDER-005 | 查询不存在订单 | `GET /order/detail/not-exist-order-999` | `code=3004` | HTTP 200，`code=3004`，订单不存在 | 通过 |
| ORDER-006 | 重复取消已取消订单 | 再次 `POST /order/cancel/{orderNo}` | `code=3005` | HTTP 200，`code=3005`，当前订单状态不允许取消 | 通过 |
