# 图表源文件

本目录提供可直接用 [draw.io](https://app.diagrams.net/) / VS Code Draw.io Integration 打开的源文件，便于课程报告排版时调整和导出 PNG/SVG。

| 文件 | 内容 | 对应文档 |
| --- | --- | --- |
| [`architecture.drawio`](architecture.drawio) | 系统总体架构、分层与请求链路 | `docs/ARCHITECTURE.md` 第 1–2 节 |
| [`er.drawio`](er.drawio) | 8 张表的 E-R 图与关系基数 | `docs/DATABASE.md` 第 1 节 |
| [`article-workflow.drawio`](article-workflow.drawio) | 文章六状态状态机、角色边界与删除规则 | `docs/ARCHITECTURE.md` 第 4 节 |

## 使用方式

1. 打开 <https://app.diagrams.net/>，选择“打开现有图表”，导入 `.drawio` 文件；
2. 或安装 VS Code 的 Draw.io Integration 插件后直接打开；
3. 导出时建议选择 PNG（300 dpi）或 SVG；
4. 插入报告后按“图 3-1”“图 3-2”等统一编号。

Mermaid 版本的同一批图已内嵌在 Markdown 文档中，可在 GitHub、Typora、VS Code 预览中直接渲染；draw.io 版本用于需要二次编辑或固定排版的场景。
