# MySQL与Redis数据一致性测试

## 固定测试范围

数据一致性套件由`pytest -m data_validation`独立执行，共收集5条用例：

| 场景 | MySQL校验 | Redis校验 |
| --- | --- | --- |
| 普通订单退款 | `orders.status=7`、`refund_time`非空、`product.stock`恢复 | 无 |
| 普通订单取消 | 业务状态通过接口回查 | `order:timeout:{orderNo}`创建后被删除 |
| 秒杀未开始 | 动态活动状态与时间窗口 | 不扣减库存 |
| 秒杀已结束 | 动态活动状态与时间窗口 | 不扣减库存 |
| 秒杀成功及重复请求 | `orders.is_seckill=1`、活动ID正确、只生成一笔订单 | `seckill:stock:{id}`正确扣减，重复请求被拦截 |

## 数据创建和回收

- 普通订单由`order_factory`创建；待支付订单在拆卸阶段取消，已支付订单退款，均通过业务接口恢复库存。
- 秒杀用例为每条测试创建名称唯一的商品和活动，不复用固定活动。
- 秒杀订单先通过取消接口恢复库存，再结束并删除活动、删除商品。
- Redis只删除当前活动和用户对应的精确Key，包括`limit`、`repeat`、`rate`、`result`与请求所有者Key，不使用通配符扫描。
- CI完成后删除Docker数据卷，保证流水线之间不存在历史数据污染。

## 报告

CI同时输出：

- `data-consistency-junit.xml`，供持续集成平台解析趋势和失败用例。
- `data-consistency-report.html`，供人工查看请求日志、断言和耗时。

2026-09-23本地实测：完整接口回归`28/28`通过，独立一致性套件`5/5`通过。CI首次在线执行成功后，应以Actions产物中的时间和结果作为公开项目的持续证据。
