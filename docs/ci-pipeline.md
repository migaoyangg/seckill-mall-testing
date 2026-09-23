# 服务端自动化测试持续集成

## 流程

`.github/workflows/test.yml`在代码推送和合并请求时执行以下质量检查：

1. 使用JDK 17执行38条JUnit、Mockito和MockMvc测试。
2. 使用`compose.ci.yml`启动MySQL、Redis和RabbitMQ，并等待容器健康检查通过。
3. 打包并启动Spring Boot应用，轮询`/api/actuator/health`直至状态为`UP`。
4. 通过注册接口创建本次运行专用的普通用户和管理员，再将管理员角色写入隔离数据库。
5. 执行28条真实HTTP接口回归，并另外执行5条MySQL/Redis数据一致性用例。
6. 无论成功或失败，归档JUnit XML、单文件HTML报告、应用日志、依赖服务日志和Surefire报告，保留14天。
7. 关闭容器并删除测试数据卷，避免不同流水线运行相互污染。

## GitHub Secrets

仓库需配置以下Actions Secrets，值不写入代码或工作流：

- `CI_MYSQL_PASSWORD`
- `CI_REDIS_PASSWORD`
- `CI_RABBITMQ_USERNAME`
- `CI_RABBITMQ_PASSWORD`
- `CI_JWT_SECRET`
- `CI_TEST_USERNAME`
- `CI_TEST_PASSWORD`
- `CI_ADMIN_USERNAME`
- `CI_ADMIN_PASSWORD`

这些凭据仅用于临时CI环境，不应复用开发、演示或生产环境凭据。

## 失败定位

下载Actions运行产物`test-evidence-<run number>`：

- `api-report.html`：完整接口回归结果。
- `data-consistency-report.html`：MySQL/Redis一致性结果。
- `application.log`：Spring Boot启动及业务日志。
- `dependencies.log`：MySQL、Redis、RabbitMQ容器日志。
- `surefire-reports/`：Java测试明细。

健康检查或账号初始化失败时，后续接口回归不会执行，但日志归档步骤仍会运行。
