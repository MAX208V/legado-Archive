#!/usr/bin/env bash
# 阅读 Archive MCP 服务器冒烟测试
# 用法: ./mcp-smoke-test.sh <手机IP:端口> [Bearer Token]
# 示例: ./mcp-smoke-test.sh 192.168.1.100:1123
#       ./mcp-smoke-test.sh 192.168.1.100:1123 mytoken123
#       ./mcp-smoke-test.sh 192.168.1.100:1123/mcp   （也兼容，带 /mcp 会自动归一化）
set -uo pipefail

ENDPOINT="${1:?用法: $0 <手机IP:端口> [Bearer Token]}"
TOKEN="${2:-}"
# URL 归一化：容忍 "IP:端口/mcp"、"IP:端口/"、"IP:端口"
BASE="$(echo "$ENDPOINT" | sed -E 's:/+$::; s:/*mcp$::')"
URL="http://$BASE/mcp"
echo ">>> 目标端点: $URL"
AUTH=()
[ -n "$TOKEN" ] && AUTH=(-H "Authorization: Bearer $TOKEN")

# 发送 JSON-RPC 请求，返回(状态码 响应体)；响应体的多行合并为单行
req() { # <id> <method> <params-json>
  local id="$1" method="$2" params="$3"
  local out code
  out=$(curl -s --max-time 60 -X POST "$URL" \
    "${AUTH[@]}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json, text/event-stream" \
    -H "MCP-Protocol-Version: 2025-06-18" \
    -w $'\n__HTTP__%{http_code}' \
    -d "{\"jsonrpc\":\"2.0\",\"id\":$id,\"method\":\"$method\",\"params\":$params}")
  code=$(printf '%s' "$out" | sed -n 's/.*__HTTP__\([0-9]\{3\}\)$/\1/p')
  body=$(printf '%s' "$out" | sed 's/__HTTP__[0-9]\{3\}$//' | tr '\n' ' ' | sed 's/  */ /g')
  printf '%s\n%s' "$body" "$code"
}

# 打印错误并把服务器原文透出，方便定位
fail() {
  echo "  ✗ FAIL：$2"
  echo "    HTTP状态码: $1"
  echo "    服务器原文: ${3:-（空）}"
  exit 1
}

echo "== 1. initialize =="
INIT=$(req 1 "initialize" '{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"smoke","version":"1.0"}}')
INIT_BODY=$(echo "$INIT" | sed -n '1p'); INIT_CODE=$(echo "$INIT" | sed -n '2p')
echo "$INIT_BODY" | python3 -c "
import sys, json
s = sys.stdin.read()
if not s: print('  ✗ 空响应'); sys.exit(1)
try: r = json.loads(s)
except Exception: print('  ✗ 非JSON: %s' % s[:200]); sys.exit(1)
if 'error' in r: print('  ✗ RPC错误:', r['error']); sys.exit(1)
res = r.get('result', {})
print('  protocolVersion:', res.get('protocolVersion'))
print('  serverInfo:', res.get('serverInfo'))
assert res.get('protocolVersion') == '2025-06-18', '版本不匹配'
assert 'tools' in res.get('capabilities', {}), '缺少 tools capability'
print('  OK')
" || fail "$INIT_CODE" "initialize 解析失败" "$INIT_BODY"

echo "== 2. notifications/initialized（期望 202）=="
NTF=$(req 0 "notifications/initialized" '{}')
NTF_CODE=$(echo "$NTF" | sed -n '2p')
echo "  HTTP $NTF_CODE"
[ "$NTF_CODE" = "202" ] || { echo "  ✗ 期望 202，实得 $NTF_CODE（能收到此码说明端点可达）"; exit 1; }
echo "  OK"

echo "== 3. tools/list =="
TL=$(req 2 "tools/list" '{}')
TL_BODY=$(echo "$TL" | sed -n '1p'); TL_CODE=$(echo "$TL" | sed -n '2p')
echo "$TL_BODY" | python3 -c "
import sys, json
s = sys.stdin.read()
try: r = json.loads(s)
except Exception: print('  ✗ 非JSON: %s' % s[:200]); sys.exit(1)
if 'error' in r: print('  ✗ RPC错误:', r['error']); sys.exit(1)
tools = r.get('result', {}).get('tools', [])
print('  工具数:', len(tools))
assert len(tools) > 0, '工具列表为空'
print('  前 5 个:', [t['name'] for t in tools[:5]])
assert all(t.get('inputSchema') for t in tools), '工具缺少 inputSchema'
print('  OK')
"
[ $? -eq 0 ] || fail "$TL_CODE" "tools/list 失败" "$TL_BODY"

echo "== 4. tools/call（get_app_settings）=="
CALL=$(req 3 "tools/call" '{"name":"get_app_settings","arguments":{}}')
CALL_BODY=$(echo "$CALL" | sed -n '1p'); CALL_CODE=$(echo "$CALL" | sed -n '2p')
echo "$CALL_BODY" | python3 -c "
import sys, json
s = sys.stdin.read()
try: r = json.loads(s)
except Exception: print('  ✗ 非JSON: %s' % s[:200]); sys.exit(1)
if 'error' in r: print('  ✗ RPC错误:', r['error']); sys.exit(1)
content = r.get('result', {}).get('content', [])
text = content[0].get('text', '') if content else ''
print('  返回长度:', len(text))
assert content and text, 'tools/call 无返回内容'
print('  片段:', text[:120].replace(chr(10), ' '))
print('  OK')
"
[ $? -eq 0 ] || fail "$CALL_CODE" "tools/call 失败" "$CALL_BODY"

echo "== 5. prompts/list =="
PR=$(req 4 "prompts/list" '{}')
PR_BODY=$(echo "$PR" | sed -n '1p'); PR_CODE=$(echo "$PR" | sed -n '2p')
echo "$PR_BODY" | python3 -c "
import sys, json
try: r = json.loads(sys.stdin.read())
except Exception: print('  （非JSON，忽略）'); sys.exit(0)
if 'error' in r: print('  RPC错误:', r['error']); sys.exit(1)
print('  Skill(Prompt) 数:', len(r.get('result', {}).get('prompts', [])))
print('  OK')
"

echo ""
echo "✅ 全部通过！MCP 服务器工作正常。"