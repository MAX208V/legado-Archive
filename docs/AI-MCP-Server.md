# 阅读 Archive · AI MCP 服务器

legado 内置 **MCP（Model Context Protocol）服务器**，以 **Streamable HTTP** 传输方式对外暴露本机全部原生 AI 工具（80+，含读/写操作）与 AI 设置中配置的 Skill（以 MCP Prompts 形式提供）。

Claude Desktop / Cursor 等支持远程 Streamable HTTP 的 MCP 客户端，可以直接连接手机，像调用普通工具一样操作书架、书源、章节、角色、配音、世界书、工作区、设置等。

---

## 一、端点与协议

| 项 | 值 |
|---|---|
| 端点 | `POST /mcp` |
| 协议版本 | `2025-06-18`（兼容 Claude Desktop / Cursor） |
| 传输 | Streamable HTTP（每请求独立 POST；响应为 `application/json` 或 `text/event-stream` SSE） |
| 方法 | `initialize` / `ping` / `tools/list` / `tools/call` / `prompts/list` / `prompts/get` / `resources/list` |
| Host | 独立 NanoHTTPD 服务器（默认端口 **1123**，可在 AI 设置中修改），与 Web 服务互不影响，仅局域网内设备可访问 |

### 开启方式

默认 **关闭**，需在 App 内显式开启：

1. 打开 App「设置 → 通用 → AI 设置」→ 找到「**对外 MCP 服务**」区块。
2. 打开「对外开放 /mcp 端点」开关（默认关闭）。开启后，独立端口（默认 **1123**）立即开始监听，**无需开启 Web 服务**。
3. （可选）点击「**端口**」修改监听端口（默认 1123，建议避开 Web 服务 1122）。
4. （推荐）点击「**访问令牌**」设置一个 Bearer 令牌：
   - 设置后，所有 `/mcp` 请求必须携带 `Authorization: Bearer <令牌>`，否则返回 `403`。
   - 留空则 **无鉴权** —— 请务必仅在受信局域网内使用。
5. 手机确保连接 WiFi 局域网（无 AP 隔离）。

> 📌 开启「对外 MCP 服务」开关后，App 会**发送一条常驻通知**，直接显示可访问地址（如 `http://192.168.1.100:1123/mcp`）：
> - 点击通知 → 跳转回 AI 设置页；
> - 通知内「关闭」按钮 → 关闭 MCP 服务并移除通知。
> 「已开启」的开关文案也会同步显示该地址（默认端口 1123，可在 AI 设置中修改）。

> ⚠️ 安全提醒：该端点暴露的是**完整读写能力**（可改书架、书源、读本地文件、调用 TTS 等）。请务必：
> - 仅在受信局域网使用，勿直接暴露公网；
> - 强烈建议设置访问令牌；
> - 客户端鉴权信息不要提交到公开仓库。

### 连接测试

仓库提供了冒烟测试脚本，可在电脑上一键验证（仅需手机已开启 MCP 服务开关，无需开 Web 服务）：

```bash
bash docs/mcp-smoke-test.sh <手机IP:端口> [Bearer Token]
# 例：bash docs/mcp-smoke-test.sh 192.168.1.100:1123
#     bash docs/mcp-smoke-test.sh 192.168.1.100:1123 mytoken123
# 也兼容带 /mcp 后缀：192.168.1.100:1123/mcp
```

---

## 2. 客户端连接示例

> 以下 `url` 均为示例；请替换为手机实际地址（手机在「对外 MCP 服务」开关文案或常驻通知中可看到）。

### Claude Code

`claude mcp add` Streamable HTTP 服务器：

```bash
claude mcp add legado \
  --transport http \
  "http://192.168.1.100:1123/mcp"
```

可附带 Bearer 令牌（若配置）：

```bash
claude mcp add legado \
  --transport http \
  "http://192.168.1.100:1123/mcp" \
  --header "Authorization: Bearer <token>"
```

### Cursor

在 `~/.cursor/mcp.json`（或项目 `.cursor/mcp.json`）添加：

```json
{
  "mcpServers": {
    "legado": {
      "url": "http://192.168.1.100:1123/mcp",
      "type": "http"
    }
  }
}
```

带令牌：

```json
{
  "mcpServers": {
    "legado": {
      "url": "http://192.168.1.100:1123/mcp",
      "type": "http",
      "headers": {
        "Authorization": "Bearer <token>"
      }
    }
  }
}
```

> Cursor 支持 Streamable HTTP 类型（`type: "http"`）。若客户端仅支持 `stdio` 本地服务器，也可依赖会话中继到手机，但不推荐将令牌写入仓库。

---

## 3. 暴露内容

### tools（`tools/list` + `tools/call`）

暴露 legado 的原生 AI 工具，**以「AI 设置 → 管理原生工具」中启用的工具为准**（未启用的不会出现在 `tools/list`，也无法通过 `tools/call` 调用），名称与 `inputSchema` 与 App 内 AI 工具一致。示例分组：

- 书架：`query_bookshelf`、`get_bookshelf_book_info`、`add_book`、`remove_book` 等
- 书源：`search_book_source`、`import_book_source`、`update_book_source` 等
- 章节/正文：`list_book_chapters`、`read_book_chapter_content` 等
- 角色：`list_book_characters`、`generate_book_character_avatar` 等
- 配音/BGM：`list_read_aloud_roles`、`edit_read_aloud_role` 等
- 工作书：`list_world_book`、`get_world_book`、`save_world_book` 等
- 个人文库：`list_workspace_books`、`workspace_import_book_source` 等
- 系统：`get_app_settings`、`set_app_setting`、`generate_image`、`search_web_tavily` 等

> 💡 中文/emoji 参数：传输层已强制 UTF-8 解码（NanoHTTPD 对无 charset 的请求默认 US-ASCII 会破坏中文），
> `searchKey` 等中文字段可放心传递。

### prompts（`prompts/list` + `prompts/get`）

App「AI 设置 → 技能」中所有 **启用** 的 Skill，作为 MCP Prompts 暴露（`name=skill.id`），通过 `prompts/get` 传入 `input` 参数拉取技能内容。

### resources

当前未暴露 Resource（`resources/list` 返回空），如需书架/书源等作为资源读取，请在后续版本启用。

---

## 5. 技术实现

- 独立服务器：`app/src/main/java/io/legado/app/web/mcp/McpServer.kt`（独立 NanoHTTPD 实例，开关控制启停，App 启动时按开关状态恢复）
- 前台服务保活：`app/src/main/java/io/legado/app/web/mcp/McpServerService.kt`（`specialUse` 前台服务，`START_STICKY` 自愈；熄屏/后台不被杀，进程被回收后自动恢复。与 RelayService 同类型，无 dataSync 的 6 小时时限）
- 启停入口：`AiConfigFragment` 开关 → `McpServerService.startForeground()/stop()`；通知「关闭」→ `McpServerActionReceiver` → `McpServerService.stop()`
- 常驻通知：`app/src/main/java/io/legado/app/web/mcp/McpServerNotification.kt`（channel `channel_ai_mcp`，由前台服务持有展示）
- 工具来源：`AiToolRegistry.allNativeTools()`
- Skill 来源：`AppConfig.aiSkillList`
- 对外开关：`AppConfig.aiMcpEnabled`（`PreferKey.aiMcpEnabled`，默认关闭）
- 独立端口：`AppConfig.aiMcpPort`（`PreferKey.aiMcpPort`，默认 1123，避开 Web 服务 1122）
- 鉴权令牌：`AppConfig.aiMcpToken`（`PreferKey.aiMcpToken`）
- 设置入口：`AiConfigFragment`「对外 MCP 服务」区块

---

## 6. 常见问题：连接经常断开

MCP 服务是「长耗时工具 + 多请求复用连接」的场景，断开大多来自以下三处，按概率排序：

### 6.1 长耗时工具阻塞连接（客户端读超时）——最常见

`tools/call`（如 `debug_book_source` 整本抓取）在服务端**同步执行**，耗时可能几十秒到几分钟；期间同一 TCP 连接上排队的其他请求（客户端心跳 `ping`、下一个工具调用）都会等待。MCP 客户端（Claude Desktop / Cursor / Claude Code）普遍设有 30~60 秒的读超时与心跳间隔，超时即主动断开——表现为「调用大工具后连接断了，下次要重连/重新初始化」。

- **服务端已做的缓解**：socket 空闲读超时 5 分钟，自动清理「客户端已断开」的僵尸连接，避免线程被无限占用导致越断越堵；`tools/call` 在日志中记录每个工具的耗时，便于定位慢工具。
- **客户端侧建议**：
  - 调大 HTTP 读超时（如 300s+）再连；Claude Code 可用 `--timeout` / 环境变量，Cursor 可调高网络请求超时；
  - 避免在长任务进行中对同一会话发起新的同步调用；
  - 重连本身是 Streamable HTTP 的正常行为（无状态，每次请求都是完整 POST），客户端会自动重新 `initialize`，无需人工干预。

### 6.2 手机后台/熄屏导致网络被掐

- 本版本已将 MCP 服务升级为 **`specialUse` 前台服务**：熄屏/后台运行不会被 Doze 或厂商省电随意杀，进程被系统回收后 `START_STICKY` 自动重启。
- 仍建议：在系统「电池优化」中把「阅读」设为**不优化**（设置页开启开关时会请求一次）；长任务调试时保持屏幕常亮或插电。
- 若切换了 WiFi/热点（IP 变化），请以通知里显示的新地址为准。

### 6.3 端口被占用 / 启动失败

- 服务启动失败会 toast 提示并自动回滚开关；可换一个端口（默认 1123，避开 Web 服务 1122）。
- 确认手机和客户端在同一局域网（无 AP 隔离），且防火墙未拦截。

> 📌 排查时可在 App「日志」中检索 `McpServer` / `McpServerService` 标签，查看服务启停与每个工具耗时。