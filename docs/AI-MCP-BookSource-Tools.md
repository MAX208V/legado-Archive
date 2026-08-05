# MCP 书源工具定义（AiBookSourceTool）

> 来源：`app/src/main/java/io/legado/app/help/ai/AiBookSourceTool.kt`（5 个工具，分组「书源」）
> MCP `tools/list` 返回的 `title` 与「管理原生工具」中的中文名一致（`AiToolRegistry.displayNameOfTool`），
> `description` 前缀 `【管理原生工具】分组 · 中文名` 便于与 App 内列表一一对应。

| MCP name | 管理原生工具 label | 分组 |
|---|---|---|
| `create_book_source` | 创建书源 | 书源 |
| `get_book_source` | 读取书源详情 | 书源 |
| `update_book_source` | 更新书源 | 书源 |
| `fetch_source_html` | 抓取网页源码 | 书源 |
| `debug_book_source` | 调试书源规则 | 书源 |

所有书源工具默认**直接落盘**（`save` 默认 `true`，与 App 内 AI 对话一致）；显式传 `"save": false` 仅返回预览 JSON。
所有返回均为 JSON 文本（`ok: true/false`），执行失败时 `ok: false` + `error` 字段。

---

## 1. create_book_source（创建书源）

- **管理原生工具**：书源 · 创建书源
- **description**：`Legacy direct BookSource draft creator. Prefer the workspace workflow for normal work: workspace_create_book_source_file, workspace_edit_file, workspace_debug_book_source, then workspace_apply_book_source. Use this tool only for quick preview when workspace tools are unavailable. Default saves to DB (save=true).`

### inputSchema（parameters）

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `save` | boolean | 否 | 是否保存到本地书源库。默认 `true`（落盘），显式传 `false` 仅返回预览 JSON |
| `sourceJson` | string | 否* | 完整 BookSource JSON。传入时优先按此解析 |
| `bookSourceUrl` | string | 是* | 书源唯一 URL，通常是站点根地址 |
| `bookSourceName` | string | 否 | 书源名称 |
| `bookSourceGroup` | string | 否 | 书源分组 |
| `searchUrl` | string | 否 | 搜索 URL 规则 |
| `exploreUrl` | string | 否 | 发现 URL 规则 |
| `bookUrlPattern` | string | 否 | 详情页 URL 正则 |
| `ruleSearch` | object | 否 | 搜索规则对象 |
| `ruleBookInfo` | object | 否 | 详情规则对象 |
| `ruleToc` | object | 否 | 目录规则对象 |
| `ruleContent` | object | 否 | 正文规则对象 |
| `ruleExplore` | object | 否 | 发现规则对象 |
| `comment` | string | 否 | 书源注释 |

`additionalProperties: true`（可传任意 BookSource 顶层字段）。

### 执行行为（createBookSource）
1. `resolveSource(args, allowDbLookup = false)`：优先解析 `sourceJson`；否则用 `bookSourceUrl` + 各字段组装新 BookSource（不查库）。
2. `save` 默认 `true` → `appDb.bookSourceDao.insert(source)` 落盘。
3. 返回 `{ok: true, saved: bool, source: {完整 BookSource JSON}}`。

### 示例
```json
{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{
  "name":"create_book_source",
  "arguments":{
    "bookSourceUrl":"https://www.example.com",
    "bookSourceName":"示例书源",
    "searchUrl":"/search?q={{key}}&page={{page}}",
    "ruleToc":{"chapterList":".list dd","chapterName":"a@text","chapterUrl":"a@href"}
  }
}}
```

---

## 2. get_book_source（读取书源详情）

- **管理原生工具**：书源 · 读取书源详情
- **description**：`读取本地已保存的 Legado 书源。bookSourceUrl 精确读取；searchKey 可按名称、分组、URL、注释搜索。用于修改已有书源前获取当前完整 JSON。`

### inputSchema（parameters）

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `bookSourceUrl` | string | 否* | 本地已保存书源的唯一 URL |
| `bookSourceUrls` | array\<string\> | 否 | 批量读取时传多个书源 URL |
| `searchKey` | string | 否* | 搜索关键词，可匹配书源名、分组、URL、注释 |
| `searchKeys` | array\<string\> | 否 | 批量搜索时传多个关键词 |
| `limit` | integer | 否 | 搜索返回数量，默认 10，最大 30 |

`additionalProperties: true`。`bookSourceUrl`/`searchKey` 至少传其一。

### 执行行为（getBookSource）
1. 有 `bookSourceUrl`/`bookSourceUrls` → 逐个 `appDb.bookSourceDao.getBookSource(url)` 精确读取；全部未找到返回错误。
2. 否则按 `searchKeys` 对 `bookSourceDao.all` 过滤（书源名/URL/分组/注释 contains，不区分大小写），取前 `limit` 条。
3. 返回 `{ok: true, source?: {单条完整 JSON}, sources?: [...]}`；搜索模式附带 `count` 与精简字段（bookSourceUrl/bookSourceName/bookSourceGroup/enabled/enabledExplore/hasSearchUrl/hasExploreUrl/comment）。

---

## 3. update_book_source（更新书源）

- **管理原生工具**：书源 · 更新书源
- **description**：`Legacy direct BookSource updater. Prefer workspace_replace_text, workspace_replace_regex, workspace_edit_lines, or workspace_insert_text on a workspace file for normal edits because they support focused changes, previews, and automatic backups. Use this tool only when workspace tools are disabled or for emergency direct database writes explicitly requested by the user.`

### inputSchema（parameters）

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `bookSourceUrl` | string | 否* | 本地已保存书源 URL。没有 sourceJson 时用它读取基底；保存时也作为目标主键 |
| `sourceJson` | string | 否* | 当前草稿完整 BookSource JSON。传入时优先作为修改基底 |
| `patch` | object | 否 | 要合并到书源里的字段。支持嵌套对象，`null` 可清空字段 |
| `save` | boolean | 否 | 默认 `true`（落盘），显式传 `false` 仅返回预览 |
| `bookSourceName` / `bookSourceGroup` | string | 否 | 可直接修改的名称 / 分组 |
| `searchUrl` / `exploreUrl` / `bookUrlPattern` | string | 否 | 可直接修改的 URL 规则 |
| `ruleSearch` / `ruleBookInfo` / `ruleToc` / `ruleContent` / `ruleExplore` | object | 否 | 可直接替换或修改的规则对象 |
| `comment` | string | 否 | 可直接修改的书源注释 |

`additionalProperties: true`。`bookSourceUrl` 与 `patch`（或 `sourceJson`、任一可修改字段）至少传其一，否则返回“缺少修改内容”错误。

### 执行行为（updateBookSource）
1. `resolveSource(args, allowDbLookup = true)`：`sourceJson` 优先；否则按 `bookSourceUrl` 从库中读取现有书源作基底。
2. 合并修改：
   - `readPatch(args)`：收集 `patch` 对象 + 所有非保留键（`sourceJson`/`bookSourceUrl`/`patch`/`save`）的顶层字段（`comment` → `bookSourceComment`）→ 合并到基底。
   - 无任何修改内容时报错并提示 patch 示例。
3. 重新解析为 `BookSource`，失败返回“无法解析”。
4. `save` 默认 `true` → `appDb.bookSourceDao.insert(updated)`（insert 即 upsert，主键 `bookSourceUrl`）。
5. 返回 `{ok: true, saved: bool, source: {修改后完整 BookSource JSON}}`。

### 示例（patch 方式）
```json
{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{
  "name":"update_book_source",
  "arguments":{
    "bookSourceUrl":"https://www.example.com",
    "patch":{"ruleToc":{"chapterList":".list dd","chapterName":"a@text","chapterUrl":"a@href"}}
  }
}}
```

---

## 4. fetch_source_html（抓取网页源码）

- **管理原生工具**：书源 · 抓取网页源码
- **description**：`按 Legado 的 AnalyzeUrl/书源配置真实获取网页 HTML，用于书源 agent 分析搜索页、详情页、目录页、正文页。可传 sourceJson 或 bookSourceUrl 复用书源 header/cookie/webView 配置；返回状态码、最终 URL、HTML 片段。`

### inputSchema（parameters）

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `url` | string | **是** | 要获取的搜索页、详情页、目录页或正文页 URL。支持 Legado URL 规则 |
| `bookSourceUrl` | string | 否 | 本地已保存书源 URL，用于复用 header/cookie 等配置 |
| `sourceJson` | string | 否 | 临时书源完整 JSON，用于复用 header/cookie 等配置 |
| `useWebView` | boolean | 否 | 是否允许使用书源 URL 规则中的 WebView 配置，默认 `true` |
| `js` | string | 否 | 页面加载后执行的 JS |
| `sourceRegex` | string | 否 | WebView 抓取源码的匹配规则 |
| `timeoutMs` | integer | 否 | 请求超时毫秒，默认 45000，最大 90000 |
| `maxChars` | integer | 否 | 返回 HTML 最大字符数，默认 20000，最大 80000 |

`required: ["url"]`，`additionalProperties: false`。

### 执行行为（fetchSourceHtml）
1. `resolveSource(args, allowDbLookup = true)` 取书源配置；没有时用 `temporarySourceFor(url)`（以 URL 的 scheme://host 为 bookSourceUrl）。
2. `AnalyzeUrl(...).getStrResponseAwait(js, sourceRegex, useWebView, isTest = true)` 真实请求。
3. 返回 `{ok: true, url, finalUrl, statusCode, message, callTime, htmlLength, truncated, html}`（html 截断到 maxChars）。

---

## 5. debug_book_source（调试书源规则）

- **管理原生工具**：书源 · 调试书源规则
- **description**：`Use the native Legado debug flow for a BookSource. For normal source repair, prefer workspace_debug_book_source on a workspace file; if debug fails, edit that file with workspace_replace_text, workspace_replace_regex, workspace_edit_lines, or workspace_insert_text and retry. This legacy direct debug tool accepts sourceJson for compatibility.`

### inputSchema（parameters）

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `bookSourceUrl` | string | 否* | 本地已保存书源的 URL |
| `sourceJson` | string | 否* | 临时调试用完整 BookSource JSON |
| `key` | string | 否 | 调试入口：普通关键词为搜索；绝对 URL 为详情；`title::url` 为发现；`++url` 为目录；`--url` 为正文。默认使用规则里的 `checkKeyWord` 或「我的」 |
| `timeoutMs` | integer | 否 | 调试超时毫秒，默认 45000，最大 90000 |

`additionalProperties: false`。`bookSourceUrl`/`sourceJson` 至少传其一。

### 执行行为（debugBookSource）
1. `resolveSource(args, allowDbLookup = true)` 解析书源。
2. `Debug.startDebug(debugScope, source, key)` 走 legado 原生调试流（`Debug.Callback.printLog` 收集日志），`withTimeoutOrNull(timeoutMs)` 等待结束（state -1 失败 / 1000 成功），超时或结束后 `Debug.cancelDebug()`。
3. 返回 `{ok: true, bookSourceUrl, key, finished: bool, success: state == 1000, logs: [最近 80 条调试日志]}`。

---

## 附录：工具间协作建议（书源修复工作流）

```
1. get_book_source       读取目标书源当前完整 JSON（确认基底）
2. fetch_source_html     抓取搜索页/详情页/目录页/正文页 HTML（分析真实结构）
3. update_book_source    用 patch 精确修改 ruleSearch/ruleToc/ruleContent 等（默认落盘）
4. debug_book_source     用 key 入口跑原生调试验证规则
5. 回到 3 继续修，直到 debug_book_source 返回 success: true
```
