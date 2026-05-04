# docs/images — 文档图片资源目录

> **版本**: v1.0 | **更新日期**: 2026-05-04

---

## 使用说明

- 架构图、流程图、界面截图等图片资源统一放置在此目录下
- 图片命名规范：`模块名-描述-序号.png`，例如：`device-architecture-01.png`
- 推荐使用 PNG 或 SVG 格式

## 路径引用方式

图片引用路径因文档所在位置不同而异：

| 引用来源 | 使用路径 |
|----------|----------|
| 项目根目录 `README.md` | `docs/images/architecture.png` |
| `docs/README.md` | `images/architecture.png` |
| 模块 `README.md`（如 `entity/`） | `../../../../../../docs/images/architecture.png` |

> **建议**：优先在项目根目录的 `README.md` 中使用图片，路径最为简洁。模块文档中如需使用图片，建议使用绝对路径（如 `/docs/images/architecture.png`），但需注意 GitHub 上绝对路径基于仓库根目录。

> ---
> 
> **© 2026 IoT-Platform Project.**
> 
> 本文档采用 [MIT License](../../LICENSE) 授权。文档内容仅供参考，项目维护者不对因使用本文档而产生的任何直接或间接损失承担责任。
