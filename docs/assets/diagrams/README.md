# 图示资源 / Diagram assets

`*.svg` 为矢量图，在 GitHub、IDE 预览与浏览器中均可直接显示；不依赖 Mermaid 渲染器。

These **SVG** files render everywhere (GitHub, browsers, IDEs). The `agent-system.md` document embeds them **above** the Mermaid source blocks so diagrams are visible even when Mermaid is not rendered.

如需从 Mermaid 源码重新导出位图，可在本机安装 `@mermaid-js/mermaid-cli` 后自行转换。

To regenerate bitmaps from Mermaid locally, install `@mermaid-js/mermaid-cli` and run `mmdc` against the code blocks in `../agent-system.md`.
