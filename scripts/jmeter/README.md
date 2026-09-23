# JMeter load scenarios

These plans are intended for non-GUI execution. Generate real user tokens before running authenticated scenarios:

```bash
JMETER_USER_PASSWORD="$JMETER_USER_PASSWORD" \
  python3 scripts/jmeter/generate_tokens.py \
  --base-url http://127.0.0.1:8080/api --count 100
```

## Scenarios

`seckill-entry.jmx` sends one `POST /api/seckill/do/{goodsId}` per user. It is the high-concurrency entry-point test.

`seckill-result-polling.jmx` submits a request and polls `/api/seckill/result/{requestId}` several times. It measures asynchronous result traffic as well as the entry request.

`normal-order-lifecycle.jmx` creates a normal order, reads its detail, pays it, and reads the detail again. Use a product with enough stock because each loop creates one order.

`read-mix.jmx` generates public read traffic for product and seckill list/detail APIs and does not require tokens.

Example runs:

```bash
jmeter -n -t scripts/jmeter/seckill-entry.jmx \
  -Jthreads=500 -Jramp=30 -Jloops=1 -JgoodsId=1 \
  -JtokenFile=scripts/jmeter/tokens.csv \
  -l target/seckill-entry-500.jtl -e -o target/report-seckill-entry-500

jmeter -n -t scripts/jmeter/seckill-result-polling.jmx \
  -Jthreads=100 -Jramp=10 -Jpolls=5 -JgoodsId=1 \
  -JtokenFile=scripts/jmeter/tokens.csv \
  -l target/seckill-polling-100.jtl -e -o target/report-seckill-polling-100

jmeter -n -t scripts/jmeter/normal-order-lifecycle.jmx \
  -Jthreads=20 -Jramp=5 -JproductId=1 -Jquantity=1 \
  -JtokenFile=scripts/jmeter/tokens.csv \
  -l target/normal-order-20.jtl -e -o target/report-normal-order-20

jmeter -n -t scripts/jmeter/read-mix.jmx \
  -Jthreads=500 -Jramp=30 -Jloops=20 -JproductId=1 -JgoodsId=1 \
  -l target/read-mix-500.jtl -e -o target/report-read-mix-500
```

Use a fresh result path for every run. Treat expected business responses such as sold-out (`4003`) and duplicate requests (`4004`/`4005`) separately from transport errors; the JTL file preserves HTTP status and response time for that analysis.

`seckill-entry.jmx`同时断言HTTP 200、业务码200和`PROCESSING`状态，避免把HTTP 200中的登录失效等业务异常误判为成功。Token具有有效期，每轮测试前应重新执行`generate_tokens.py`。

可使用活动管理脚本创建隔离数据并输出一致性结果：

```bash
python3 scripts/jmeter/manage_seckill_activity.py prepare --stock 100
python3 scripts/jmeter/manage_seckill_activity.py inspect
python3 scripts/jmeter/manage_seckill_activity.py finish
```
