#!/usr/bin/env bash
# 阅读 Archive MCP 服务器冒烟测试
# 用法: ./mcp-smoke-test.sh <手机IP:端口> [Bearer Token]
# 示例: ./mcp-smoke-test.sh 192.168.1.100:8080
#       ./mcp-smoke-test.sh 192.168.1.100:8080 mytoken123
set -euo pipefail

ENDPOINT="${1:?用法: $0 <手机IP:端口> [Bearer Token]}"
TOKEN="${2:-}"
URL="http://$ENDPOINT/mcp"
AUTH=()
[ -n "$TOKEN" ] && AUTH=(-H "Authorization: Bearer $TOKEN")

req() { # <id> <method> <params-json>
  local id="$1" method="$2" params="$3"
  curl -s --max-time 60 -X POST "$URL" \
    "${AUTH[@]}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json, text/event-stream" \
    -H "MCP-Protocol-Version: 2025-06-18" \
    -d "{\"jsonrpc\":\"2.0\",\"id\":$id,\"method\":\"$method\",\"params\":$params}"
}

echo "== 1. initialize =="
INIT=$(req 1 "initialize" '{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"smoke","version":"1.0"}}')
echo "$INIT" | python3 -c "
import sys, json
r = json.load(sys.stdin)
res = r.get('result', {})
print('  protocolVersion:', res.get('protocolVersion'))
print('  serverInfo:', res.get('serverInfo'))
assert res.get('protocolVersion') == '2025-06-18', '版本不匹配'
assert 'tools' in res.get('capabilities', {}), '缺少 tools capability'
print('  OK')
"

echo "== 2. notifications/initialized（期望 202）=="
HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$URL" "${AUTH[@]}" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "MCP-Protocol-Version: 2025-06-18" \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized"}')
echo "  HTTP $HTTP"
[ "$HTTP" = "202" ] || { echo "  FAIL"; exit 1; }
echo "  OK"

echo "== 3. tools/list =="
TOOLS=$(req 2 "tools/list" '{}')
echo "$TOOLS" | python3 -c "
import sys, json
r = json.load(sys.stdin)
tools = r.get('result', {}).get('tools', [])
print('  工具数:', len(tools))
assert len(tools) > 0, '工具列表为空'
names = [t['name'] for t in tools]
print('  前 5 个:', names[:5])
assert all(t.get('inputSchema') for t in tools), '工具缺少 inputSchema'
print('  OK')
"

echo "== 4. tools/call（get_app_settings）=="
CALL=$(req 3 "tools/call" '{"name":"get_app_settings","arguments":{}}')
echo "$CALL" | python3 -c "
import sys, json
r = json.load(sys.stdin)
content = r.get('result', {}).get('content', [])
text = content[0].get('text', '') if content else ''
print('  返回长度:', len(text))
assert content and text, 'tools/call 无返回内容'
print('  片段:', text[:120].replace(chr(10), ' '))
print('  OK')
"

echo "== 5. prompts/list =="
PROMPTS=$(req 4 "prompts/list" '{}')
echo "$PROMPTS" | python3 -c "
import sys, json
r = json.load(sys.stdin)
prompts = r.get('result', {}).get('prompts', [])
print('  Skill(Prompt) 数:', len(prompts))
print('  OK')
"

echo ""
echo "✅ 全部通过！MCP 服务器工作正常。"
