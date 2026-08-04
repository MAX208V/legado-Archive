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

legado 的全部原生 AI 工具，来自 `AiToolRegistry.allNativeTools()`，名称与 `inputSchema` 与 App 内 AI 工具一致。示例分组：

- 书架：`query_bookshelf`、`get_bookshelf_book_info`、`add_book`、`remove_book` 等
- 书源：`search_book_source`、`import_book_source`、`update_book_source` 等
- 章节/正文：`list_book_chapters`、`read_book_chapter_content` 等
- 角色：`list_book_characters`、`generate_book_character_avatar` 等
- 配音/BGM：`list_read_aloud_roles`、`edit_read_aloud_role` 等
- 工作书：`list_world_book`、`get_world_book`、`save_world_book` 等
- 个人文库：`list_workspace_books`、`workspace_import_book_source` 等
- 系统：`get_app_settings`、`set_app_setting`、`generate_image`、`search_web_tavily` 等

### prompts（`prompts/list` + `prompts/get`）

App「AI 设置 → 技能」中所有 **启用** 的 Skill，作为 MCP Prompts 暴露（`name=skill.id`），通过 `prompts/get` 传入 `input` 参数拉取技能内容。

### resources

当前未暴露 Resource（`resources/list` 返回空），如需书架/书源等作为资源读取，请在后续版本启用。

---

## 5. 技术实现

- 独立服务器：`app/src/main/java/io/legado/app/web/mcp/McpServer.kt`（独立 NanoHTTPD 实例，开关控制启停，App 启动时按开关状态恢复）
- 启停入口：`AiConfigFragment` 开关 → `McpServer.start(port)/stop()`；通知「关闭」→ `McpServerActionReceiver`
- 常驻通知：`app/src/main/java/io/legado/app/web/mcp/McpServerNotification.kt`（channel `channel_ai_mcp`）
- 工具来源：`AiToolRegistry.allNativeTools()`
- Skill 来源：`AppConfig.aiSkillList`
- 对外开关：`AppConfig.aiMcpEnabled`（`PreferKey.aiMcpEnabled`，默认关闭）
- 独立端口：`AppConfig.aiMcpPort`（`PreferKey.aiMcpPort`，默认 1123，避开 Web 服务 1122）
- 鉴权令牌：`AppConfig.aiMcpToken`（`PreferKey.aiMcpToken`）
- 设置入口：`AiConfigFragment`「对外 MCP 服务」区块