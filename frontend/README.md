# 动环平台前端

Vue 3 + Vite + Element Plus + Pinia + ECharts，提供机房概览、设备与指标数据、告警处理及管理界面。项目用途、截图和已验证范围见 [根 README](../README.md)。

## 开发

需要 Node.js 22.12+ 或兼容版本，并先启动平台后端。


```sh
cd frontend
npm ci
npm run dev
# 在另一终端：
npm test
npm run build
```

默认前端 http://localhost:5173，/api 转发至 http://localhost:8080。多项目并行可在 PowerShell 设置：

```powershell
$env:DEV_PORT="5189"
$env:API_TARGET="http://localhost:8089"
npm run dev
```

本轮在 Node.js 24.12.0 下完成 17 项测试和生产构建；截图来自 Vite + 真实平台 + 隔离合成数据，不能作为生产部署验收。完整步骤见 [本地联调](../docs/70_部署运行/本地开发与仿真联调.md)。
