package io.legado.app.web.mcp

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import io.legado.app.BuildConfig
import io.legado.app.help.ai.AiResolvedTool
import io.legado.app.help.ai.AiToolRegistry
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.LogUtils
import io.legado.app.utils.stackTraceStr
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * Streamable HTTP MCP Server（协议版本 2025-06-18，兼容 Claude Desktop / Cursor）
 *
 * 独立 NanoHTTPD 服务器：由「对外 MCP 服务」开关直接控制启停，
 * 不依赖 Web 服务。端点：POST /mcp（独立端口 AppConfig.aiMcpPort，默认 1123）。
 *
 * 方法：initialize / ping / tools/list / tools/call / prompts/list / prompts/get
 * 鉴权：配置 AppConfig.aiMcpToken 后要求 `Authorization: Bearer <token>`
 */
class McpServer private constructor(port: Int) : NanoHTTPD(port) {

    companion object {
        const val PROTOCOL_VERSION = "2025-06-18"
        private const val TOOLS_PAGE_SIZE = 50
        private const val TAG = "McpServer"

        /**
         * keep-alive 连接的空闲读超时（毫秒）。
         * 注：nanohttpd `start(timeout)` 中 timeout>0 才会对 accept 到的 socket 设置
         * SO_TIMEOUT（timeout=0 表示永不超时）。设 0 会让客户端已断开/半开的僵尸连接
         * 永久占住处理线程（越断越堵）；设合理值可自动清理僵尸连接释放线程。
         * 长耗时工具执行期间服务端没有 socket 读操作，SO_TIMEOUT 不会中断任务本身。
         */
        private const val SOCKET_READ_TIMEOUT_MS = 5 * 60 * 1000L

        private var instance: McpServer? = null

        val running: Boolean get() = instance?.isAlive == true

        /** 启动独立 MCP 服务器，返回是否成功 */
        @Synchronized
        fun start(port: Int): Boolean {
            stop()
            return try {
                val server = McpServer(port)
                // 非 0 读超时：自动清理客户端已断开/空闲过久的 keep-alive 僵尸连接，
                // 避免处理线程被半死连接无限占用（见 SOCKET_READ_TIMEOUT_MS 注释）。
                server.start(SOCKET_READ_TIMEOUT_MS.toInt())
                instance = server
                LogUtils.d(TAG) { "MCP server started on port $port (read timeout ${SOCKET_READ_TIMEOUT_MS}ms)" }
                true
            } catch (e: IOException) {
                LogUtils.d(TAG) { "MCP server start failed: $e\n${e.stackTraceStr}" }
                false
            }
        }

        @Synchronized
        fun stop() {
            instance?.stop()
            instance = null
            LogUtils.d(TAG) { "MCP server stopped" }
        }
    }

    override fun serve(session: IHTTPSession): Response {
        // CORS 预检
        if (session.method == NanoHTTPD.Method.OPTIONS) {
            return corsResponse(NanoHTTPD.newChunkedResponse(
                Response.Status.OK,
                "text/plain",
                ByteArrayInputStream(ByteArray(0))
            ))
        }
        if (session.method != NanoHTTPD.Method.POST) {
            return jsonRpcError(Response.Status.METHOD_NOT_ALLOWED, -32600, "Only POST allowed on /mcp", null)
        }

        // 必需协议版本头（兼容客户端不发送的情况：按 2025-06-18 处理）
        val protocolVer = session.headers["mcp-protocol-version"] ?: PROTOCOL_VERSION

        // Bearer token 鉴权（配置了 token 才校验）
        val token = AppConfig.aiMcpToken
        if (token.isNotBlank()) {
            val auth = session.headers["authorization"]
            val valid = auth?.startsWith("Bearer ") == true && auth.removePrefix("Bearer ").trim() == token
            if (!valid) {
                return jsonRpcError(Response.Status.FORBIDDEN, -32000, "Invalid or missing Authorization token", null)
            }
        }

        // 读取请求体
        // 坑：NanoHTTPD 对无 charset 的 content-type 用 US-ASCII 解码（有损，中文变 U+FFFD 乱码）。
        // 强制声明 UTF-8，让 parseBody 正确解码中文/emoji 参数。
        session.headers["content-type"] = "application/json; charset=UTF-8"
        val files = HashMap<String, String>()
        try {
            session.parseBody(files)
        } catch (e: Exception) {
            return jsonRpcError(Response.Status.BAD_REQUEST, -32700, "Parse error: ${e.message}", null)
        }
        val postData = files["postData"]?.trim()
        if (postData.isNullOrBlank()) {
            return jsonRpcError(Response.Status.BAD_REQUEST, -32700, "Empty request body", null)
        }

        // 解析 JSON-RPC 2.0
        val request: JSONObject = try {
            JSONObject(postData)
        } catch (e: Exception) {
            return jsonRpcError(Response.Status.BAD_REQUEST, -32700, "Invalid JSON: ${e.message}", null)
        }

        val id = request.opt("id")
        val method = request.optString("method")
        val params = request.optJSONObject("params") ?: JSONObject()

        // 头部与 body _meta 版本一致性校验
        val metaVer = params.optJSONObject("_meta")?.optString("io.modelcontextprotocol/protocolVersion")
        if (!metaVer.isNullOrBlank() && metaVer != protocolVer) {
            return jsonRpcError(Response.Status.BAD_REQUEST, -32020,
                "HeaderMismatch: MCP-Protocol-Version ($protocolVer) != _meta ($metaVer)", id)
        }

        // JSON-RPC notification（无 id，如 notifications/initialized）：202 Accepted
        if (request.isNull("id")) {
            return NanoHTTPD.newChunkedResponse(
                Response.Status.ACCEPTED,
                "text/plain",
                ByteArrayInputStream(ByteArray(0))
            )
        }

        val wantSse = (session.headers["accept"] ?: "").contains("text/event-stream")
        return try {
            dispatch(method, params, id, wantSse)
        } catch (e: Exception) {
            LogUtils.d(TAG) { "MCP dispatch error: $e\n${e.stackTraceStr}" }
            jsonRpcError(Response.Status.INTERNAL_ERROR, -32603, "Internal error: ${e.message}", id)
        }
    }

    // ===== 方法分发 =====
    private fun dispatch(method: String, params: JSONObject, id: Any?, wantSse: Boolean): Response {
        return when (method) {
            "initialize" -> handleInitialize(id, wantSse)
            "ping" -> handlePing(id, wantSse)
            "tools/list" -> handleToolsList(params, id, wantSse)
            "tools/call" -> handleToolsCall(params, id, wantSse)
            "prompts/list" -> handlePromptsList(id, wantSse)
            "prompts/get" -> handlePromptsGet(params, id, wantSse)
            "resources/list" -> handleResourcesList(id, wantSse)
            "resources/read" -> jsonRpcError(Response.Status.BAD_REQUEST, -32601, "Resources not supported", id)
            else -> jsonRpcError(Response.Status.BAD_REQUEST, -32601, "Method not found: $method", id)
        }
    }

    // ===== initialize =====
    private fun handleInitialize(id: Any?, wantSse: Boolean): Response {
        val result = JSONObject().apply {
            put("protocolVersion", PROTOCOL_VERSION)
            put("capabilities", JSONObject().apply {
                put("tools", JSONObject().put("listChanged", false))
                put("prompts", JSONObject().put("listChanged", false))
                put("resources", JSONObject().apply {
                    put("subscribe", false)
                    put("listChanged", false)
                })
            })
            put("serverInfo", JSONObject().apply {
                put("name", "Legado MCP")
                put("version", BuildConfig.VERSION_NAME)
            })
        }
        return rpcResponse(id, result, wantSse)
    }

    // ===== ping =====
    private fun handlePing(id: Any?, wantSse: Boolean): Response {
        return rpcResponse(id, JSONObject(), wantSse)
    }

    // ===== 工具来源：以「管理原生工具」启用的为准 =====
    private fun enabledNativeTools(): List<AiResolvedTool> {
        val enabled = AiToolRegistry.effectiveEnabledToolNames()
        return AiToolRegistry.allNativeTools().filter { it.name in enabled }
    }

    // ===== tools/list（cursor 分页）=====
    private fun handleToolsList(params: JSONObject, id: Any?, wantSse: Boolean): Response {
        val start = params.optString("cursor").toIntOrNull() ?: 0
        val allTools = enabledNativeTools()
        val end = (start + TOOLS_PAGE_SIZE).coerceAtMost(allTools.size)

        val toolsJson = JSONArray()
        for (i in start until end) {
            val tool = allTools[i]
            val func = tool.definition.optJSONObject("function")
            val schema = func?.optJSONObject("parameters") ?: JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject())
            }
            val label = AiToolRegistry.displayNameOfTool(tool.name)
            val group = AiToolRegistry.groupLabelOfTool(tool.name)
            val rawDesc = func?.optString("description") ?: ""
            toolsJson.put(JSONObject().apply {
                put("name", tool.name)
                put("title", label)
                put("description", buildString {
                    append("【管理原生工具】$group · $label")
                    if (rawDesc.isNotBlank()) append("。").append(rawDesc)
                })
                put("inputSchema", schema)
            })
        }

        val result = JSONObject().apply {
            put("tools", toolsJson)
            if (end < allTools.size) put("nextCursor", end.toString())
        }
        return rpcResponse(id, result, wantSse)
    }

    // ===== tools/call =====
    private fun handleToolsCall(params: JSONObject, id: Any?, wantSse: Boolean): Response {
        val name = params.optString("name")
        val arguments = params.optJSONObject("arguments") ?: JSONObject()
        val allTools = enabledNativeTools()
        val tool = allTools.find { it.name == name }
            ?: return jsonRpcError(Response.Status.BAD_REQUEST, -32602, "Tool not found: $name", id)

        val text = try {
            val startedAt = System.currentTimeMillis()
            val result = runBlocking { tool.execute(arguments) }
            LogUtils.d(TAG) { "tools/call '$name' ok, took ${System.currentTimeMillis() - startedAt}ms" }
            result
        } catch (e: Exception) {
            LogUtils.d(TAG) { "tools/call '$name' error: ${e.message ?: e.javaClass.simpleName}" }
            JSONObject().apply {
                put("ok", false)
                put("error", e.message ?: e.javaClass.simpleName)
            }.toString()
        }

        val result = JSONObject().apply {
            put("content", JSONArray().put(JSONObject().apply {
                put("type", "text")
                put("text", text)
            }))
            put("isError", false)
        }
        return rpcResponse(id, result, wantSse)
    }

    // ===== prompts/list（AI 设置中启用的 Skill）=====
    private fun handlePromptsList(id: Any?, wantSse: Boolean): Response {
        val skills = AppConfig.aiSkillList
        val promptsJson = JSONArray()
        skills.filter { it.enabled }.forEach { skill ->
            promptsJson.put(JSONObject().apply {
                put("name", skill.id)
                put("title", skill.name)
                put("description", skill.description.ifBlank { skill.content.take(100) })
                put("arguments", JSONArray().put(JSONObject().apply {
                    put("name", "input")
                    put("description", "User input to the skill")
                    put("required", true)
                    put("schema", JSONObject().apply { put("type", "string") })
                }))
            })
        }
        val result = JSONObject().put("prompts", promptsJson)
        return rpcResponse(id, result, wantSse)
    }

    // ===== prompts/get =====
    private fun handlePromptsGet(params: JSONObject, id: Any?, wantSse: Boolean): Response {
        val name = params.optString("name")
        val input = params.optJSONObject("arguments")?.optString("input", "").orEmpty()
        val skill = AppConfig.aiSkillList.find { it.id == name && it.enabled }
            ?: return jsonRpcError(Response.Status.BAD_REQUEST, -32602, "Prompt not found: $name", id)

        val result = JSONObject().apply {
            put("description", skill.description)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", JSONObject().apply {
                    put("type", "text")
                    put("text", buildString {
                        append(skill.content)
                        if (input.isNotBlank()) {
                            append("\n\nInput: ").append(input)
                        }
                    })
                })
            }))
        }
        return rpcResponse(id, result, wantSse)
    }

    // ===== resources/list（占位，无资源）=====
    private fun handleResourcesList(id: Any?, wantSse: Boolean): Response {
        return rpcResponse(id, JSONObject().put("resources", JSONArray()), wantSse)
    }

    // ===== JSON-RPC 响应构建 =====
    private fun rpcResponse(id: Any?, result: JSONObject, wantSse: Boolean): Response {
        val json = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("result", result)
        }
        return if (wantSse) sseResponse(json) else jsonResponse(json)
    }

    private fun jsonResponse(json: JSONObject): Response {
        return corsResponse(NanoHTTPD.newChunkedResponse(
            Response.Status.OK,
            "application/json",
            ByteArrayInputStream(json.toString().toByteArray())
        ))
    }

    private fun sseResponse(json: JSONObject): Response {
        // SSE 单条消息：event: message + data + 空行结束
        val payload = "event: message\ndata: ${json.toString()}\n\n"
        return corsResponse(NanoHTTPD.newChunkedResponse(
            Response.Status.OK,
            "text/event-stream",
            ByteArrayInputStream(payload.toByteArray())
        ))
    }

    private fun jsonRpcError(
        httpStatus: Response.Status,
        code: Int,
        message: String,
        id: Any?
    ): Response {
        val json = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id ?: JSONObject.NULL)
            put("error", JSONObject().apply {
                put("code", code)
                put("message", message)
            })
        }
        return corsResponse(NanoHTTPD.newChunkedResponse(
            httpStatus,
            "application/json",
            ByteArrayInputStream(json.toString().toByteArray())
        ))
    }

    private fun corsResponse(response: Response): Response {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "POST, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Accept, MCP-Protocol-Version, Authorization")
        response.addHeader("Access-Control-Max-Age", "86400")
        return response
    }
}