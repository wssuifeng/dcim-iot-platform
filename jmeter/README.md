# JMeter 压测说明

## 环境要求

- JMeter 5.6+
- 后端服务已启动（MySQL + Redis + 应用）
- 默认测试账号：`admin` / `Admin@123`（请在 JMeter 用户变量中替换为实际账号）

## 通过标准

| 指标 | 目标 |
|------|------|
| P95 响应时间 | ≤ 500ms |
| P99 响应时间 | ≤ 1000ms |
| 错误率 | < 0.1% |
| 并发数 | 50 |
| 持续稳定时间 | 5 分钟（含 1 分钟 ramp-up） |

## 执行步骤

1. 启动本地服务：`mvn spring-boot:run`
2. 打开 JMeter，导入 `jmeter/iot-platform-load-test.jmx`
3. 修改测试计划中的 `BASE_URL`、`BASE_PORT`、`USERNAME`、`PASSWORD` 为实际值
4. 点击运行
5. 查看 Summary Report 与 Graph Results

## 结果记录

压测完成后，请将 `summary-report.csv` 中的关键指标记录到本文件下方的结果表中：

| 日期 | 场景 | 并发 | 持续时间 | P95(ms) | P99(ms) | 错误率 | 是否通过 |
|------|------|------|----------|---------|---------|--------|----------|
|      |      |      |          |         |         |        |          |
