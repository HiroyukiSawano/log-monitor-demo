<template>
  <div class="p-6 h-full text-text bg-bg overflow-auto flex flex-col gap-6">
    <!-- Header -->
    <header class="flex items-center gap-3 shrink-0">
      <div class="w-8 h-8 rounded-lg bg-yellow/20 border border-yellow/50 flex items-center justify-center text-yellow">
        <el-icon :size="18"><Setting /></el-icon>
      </div>
      <div>
        <h2 class="text-xl font-bold tracking-wide">监控调试沙箱</h2>
        <p class="text-[11px] text-text2 mt-0.5 uppercase tracking-wider">WebSocket连接测试及过滤规则管理</p>
      </div>
      <div class="ml-auto flex items-center gap-2">
         <div class="w-2.5 h-2.5 rounded-full" :class="wsConnected ? 'bg-green shadow-[0_0_8px_var(--green)]' : 'bg-red shadow-[0_0_8px_var(--red)]'"></div>
         <span class="text-xs font-semibold" :class="wsConnected ? 'text-green' : 'text-red'">{{ wsConnected ? '已连接' : '未连接' }}</span>
      </div>
    </header>

    <div class="grid grid-cols-1 xl:grid-cols-2 gap-6 min-h-0 shrink-0">
      <!-- Left: Connection & Quick Send -->
      <div class="bg-surface border border-border rounded-xl shadow-lg flex flex-col">
        <div class="px-5 py-3 border-b border-border bg-surface2">
          <span class="text-xs font-semibold uppercase tracking-wider text-text2">🔌 WebSocket 连接</span>
        </div>
        <div class="p-5 flex flex-col gap-5">
          <div class="flex gap-3 items-end">
            <div class="flex-1">
              <label class="block text-[10px] uppercase text-text2 mb-1">节点ID</label>
              <input v-model="agentId" type="text" class="w-full bg-bg border border-border text-text text-sm px-3 py-1.5 rounded focus:border-accent outline-none">
            </div>
            <div class="flex-1">
              <label class="block text-[10px] uppercase text-text2 mb-1">令牌(Token)</label>
              <input v-model="token" type="text" class="w-full bg-bg border border-border text-text text-sm px-3 py-1.5 rounded focus:border-accent outline-none">
            </div>
            <button @click="connectWS" :disabled="wsConnected" class="px-4 py-1.5 rounded text-xs font-semibold transition-colors" :class="wsConnected ? 'bg-surface2 text-text2' : 'bg-green text-white hover:bg-[#00d2a0]'">连接</button>
            <button @click="disconnectWS" :disabled="!wsConnected" class="px-4 py-1.5 rounded text-xs font-semibold transition-colors" :class="!wsConnected ? 'bg-surface2 text-text2' : 'bg-red text-white hover:bg-opacity-90'">断开</button>
          </div>

          <div class="border-t border-border pt-4">
            <span class="text-[10px] font-semibold text-text2 uppercase tracking-wider block mb-3">⚡ 快速操作</span>
            <div class="flex flex-wrap gap-2">
              <button @click="sendHeartbeat" class="px-3 py-1.5 rounded bg-accent/10 text-accent border border-accent/30 hover:bg-accent/20 text-xs font-medium transition-colors">💓 发送心跳</button>
              <button @click="sendMetrics" class="px-3 py-1.5 rounded bg-blue/10 text-blue border border-blue/30 hover:bg-blue/20 text-xs font-medium transition-colors">📊 发送指标</button>
              <button @click="sendNormalLog" class="px-3 py-1.5 rounded bg-surface2 text-text border border-border hover:border-text2 text-xs font-medium transition-colors">📝 普通日志</button>
              <button @click="sendErrorLog" class="px-3 py-1.5 rounded bg-yellow/10 text-yellow border border-yellow/30 hover:bg-yellow/20 text-xs font-medium transition-colors">⚠️ 错误日志</button>
              <button @click="sendCriticalLog" class="px-3 py-1.5 rounded bg-red/10 text-red border border-red/30 hover:bg-red/20 text-xs font-medium transition-colors">🔴 严重告警</button>
              <button @click="sendExcludeLog" class="px-3 py-1.5 rounded bg-green/10 text-green border border-green/30 hover:bg-green/20 text-xs font-medium transition-colors">✅ 排除日志</button>
            </div>
          </div>

          <div class="border-t border-border pt-4">
            <span class="text-[10px] font-semibold text-text2 uppercase tracking-wider block mb-3">📨 自定义日志</span>
            <div class="flex gap-2">
              <input v-model="appName" type="text" placeholder="应用名称" class="w-1/4 bg-bg border border-border text-text text-xs px-2 py-1.5 rounded focus:border-accent outline-none">
              <input v-model="customLog" type="text" placeholder="输入日志内容..." class="flex-1 bg-bg border border-border text-text text-xs px-2 py-1.5 rounded focus:border-accent outline-none">
              <button @click="sendCustomLog" class="px-3 py-1.5 rounded bg-accent text-white text-xs font-medium hover:bg-opacity-90 transition-colors">发送</button>
            </div>
          </div>

          <div class="border-t border-border pt-4">
            <span class="text-[10px] font-semibold text-text2 uppercase tracking-wider block mb-3">📦 原始 JSON 数据 (Raw)</span>
            <textarea v-model="rawJson" rows="2" class="w-full bg-bg border border-border text-text font-mono text-[10px] p-2 rounded focus:border-accent outline-none resize-none shadow-sm" placeholder='{"type":"HEARTBEAT", "payload": {}}'></textarea>
            <button @click="sendRaw" class="mt-2 px-3 py-1.5 rounded bg-surface text-text text-[10px] font-medium border border-border hover:bg-surface2 transition-colors">发送 JSON</button>
          </div>
        </div>
      </div>

      <!-- Right: Rule Management -->
      <div class="bg-surface border border-border rounded-xl shadow-lg flex flex-col">
        <div class="px-5 py-3 border-b border-border bg-surface2 flex items-center justify-between">
          <span class="text-xs font-semibold uppercase tracking-wider text-text2">📋 过滤规则管理</span>
          <div class="flex gap-2">
            <button @click="loadRules" class="text-[10px] text-blue hover:text-cyan transition-colors flex items-center gap-1"><el-icon><Refresh /></el-icon> 刷新</button>
            <button @click="rebuildMatchers" class="text-[10px] text-yellow hover:text-[#ffd97d] transition-colors flex items-center gap-1"><el-icon><Operation /></el-icon> 重建匹配树</button>
          </div>
        </div>
        <div class="p-5 flex flex-col h-full gap-4">
          <div class="flex gap-2 text-xs">
            <select v-model="ruleForm.ruleType" class="bg-bg border border-border text-text rounded px-2 focus:border-accent outline-none">
              <option value="CRITICAL">CRITICAL</option>
              <option value="EXCLUDE">EXCLUDE</option>
              <option value="BASIC">BASIC</option>
            </select>
            <input v-model="ruleForm.ruleName" type="text" placeholder="规则名称" class="bg-bg border border-border text-text rounded w-1/4 px-2 focus:border-accent outline-none">
            <input v-model="ruleForm.keyword" type="text" placeholder="匹配关键字" class="bg-bg border border-border text-text rounded flex-1 px-2 focus:border-accent outline-none">
            <select v-model="ruleForm.matchMode" class="bg-bg border border-border text-text rounded px-2 focus:border-accent outline-none text-[10px]">
              <option value="CONTAINS">CONTAINS</option>
              <option value="REGEX">REGEX</option>
              <option value="STARTS_WITH">STARTS_WITH</option>
              <option value="ENDS_WITH">ENDS_WITH</option>
            </select>
            <button @click="addRule" class="px-3 rounded bg-green hover:bg-[#00d2a0] text-white font-medium transition-colors cursor-pointer">添加</button>
          </div>

          <div class="flex-1 overflow-y-auto border border-border rounded bg-surface">
            <table class="w-full text-left text-xs">
              <thead class="bg-surface2 sticky top-0">
                <tr>
                  <th class="px-3 py-2 text-text2 font-medium border-b border-border">ID</th>
                  <th class="px-3 py-2 text-text2 font-medium border-b border-border">类型</th>
                  <th class="px-3 py-2 text-text2 font-medium border-b border-border">名称</th>
                  <th class="px-3 py-2 text-text2 font-medium border-b border-border">关键字</th>
                  <th class="px-3 py-2 text-text2 font-medium border-b border-border">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in rules" :key="r.id" class="border-b border-border hover:bg-surface2 transition-colors">
                  <td class="px-3 py-2 text-[10px] text-text2">{{ r.id }}</td>
                  <td class="px-3 py-2">
                    <span class="px-1.5 py-0.5 rounded text-[9px] font-bold" :class="{
                      'bg-red/10 text-red border border-red/20': r.ruleType === 'CRITICAL',
                      'bg-green/10 text-green border border-green/20': r.ruleType === 'EXCLUDE',
                      'bg-yellow/10 text-yellow border border-yellow/20': r.ruleType === 'BASIC'
                    }">{{ r.ruleType }}</span>
                  </td>
                  <td class="px-3 py-2 text-text">{{ r.ruleName }}</td>
                  <td class="px-3 py-2 font-mono text-[10px] text-text truncate max-w-[120px]">{{ r.keyword }}</td>
                  <td class="px-3 py-2">
                    <button @click="deleteRule(r.id)" class="text-text2 hover:text-red transition-colors"><el-icon><Delete /></el-icon></button>
                  </td>
                </tr>
                <tr v-if="rules.length === 0">
                  <td colspan="5" class="px-3 py-8 text-center text-text2 opacity-60">暂未配置过滤规则</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>

    <!-- Bottom: Log Console -->
    <div class="bg-surface border border-border rounded-xl shadow-lg flex flex-col flex-1 min-h-[300px]">
      <div class="px-5 py-2 border-b border-border bg-surface2 flex items-center justify-between">
        <span class="text-xs font-semibold uppercase tracking-wider text-text2">📜 通信交互日志</span>
        <button @click="clearLog" class="text-[10px] px-2 py-1 rounded border border-border bg-surface hover:bg-surface2 hover:text-red transition-colors flex items-center gap-1">
          <el-icon><Delete /></el-icon> 清空
        </button>
      </div>
      <div class="flex-1 bg-bg p-4 overflow-y-auto font-mono text-[11px] leading-relaxed shadow-inner" ref="logContainer">
        <div v-for="log in logs" :key="log.id" class="mb-1.5 break-all border-b border-transparent hover:border-border hover:bg-surface2 px-1 rounded transition-colors">
          <span class="text-text2">[{{ log.time }}]</span>
          <span class="font-bold inline-block w-16 ml-1" :class="{
            'text-blue': log.type === 'sent',
            'text-green': log.type === 'recv',
            'text-yellow': log.type === 'info',
            'text-red': log.type === 'error'
          }">[{{ log.dir }}]</span>
          <span :class="{'text-text': log.type !== 'error', 'text-red': log.type === 'error'}">{{ log.msg }}</span>
          
          <button v-if="log.cmdId" @click="replyCmd(log)" class="ml-2 px-1.5 py-0.5 rounded text-[9px] bg-accent text-white hover:bg-opacity-90 transition-colors" :class="{'opacity-50 cursor-not-allowed': log.replied}" :disabled="log.replied">
            {{ log.replied ? '✅ 已回复' : '📩 快捷回复' }}
          </button>
        </div>
        <div v-if="logs.length === 0" class="text-text2 opacity-50 italic">请连接 WebSocket 以查看日志...</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { Setting, Refresh, Operation, Delete } from '@element-plus/icons-vue'
import { ref, onMounted, onUnmounted, nextTick } from 'vue'

// ---------- WS Connection State ----------
const agentId = ref('debug-agent-01')
const token = ref('default-pre-shared-key')
const wsConnected = ref(false)
let ws = null

const appName = ref('payment-service')
const customLog = ref('')
const rawJson = ref('')

const logs = ref([])
const logContainer = ref(null)
let logIdCounter = 0

function ts() {
  const d = new Date()
  return d.toLocaleTimeString('zh-CN', { hour12: false }) + '.' + String(d.getMilliseconds()).padStart(3, '0')
}

function addLog(type, dir, msg, cmdId = null, func = null) {
  logs.value.push({ id: ++logIdCounter, type, dir, msg, time: ts(), cmdId, func, replied: false })
  if (logs.value.length > 500) logs.value.shift()
  nextTick(() => {
    if (logContainer.value) logContainer.value.scrollTop = logContainer.value.scrollHeight
  })
}

function clearLog() { logs.value = [] }

function connectWS() {
  if (wsConnected.value) return
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const url = `${protocol}//${location.host}/ws/agent?agentId=${encodeURIComponent(agentId.value)}&token=${encodeURIComponent(token.value)}`
  
  addLog('info', 'SYS', `正在连接到 ${url} ...`)
  try { ws = new WebSocket(url) }
  catch (e) { addLog('error', 'ERR', `连接失败: ${e.message}`); return }

  ws.onopen = () => {
    wsConnected.value = true
    addLog('info', 'SYS', '✅ WebSocket 连接成功!')
  }
  
  ws.onmessage = (e) => {
    try {
      const msg = JSON.parse(e.data)
      if (msg.type === 'FILTER_RESULT') {
        const p = msg.payload
        const icons = { CRITICAL: '🔴', UNKNOWN_ERROR: '🟡', EXCLUDED: '✅', NORMAL: '⚪' }
        const icon = icons[p.level] || '❓'
        const detail = p.matched ? `Rule="${p.matchedRuleName}" Keyword="${p.matchedKeyword}"` : 'No match'
        addLog(p.level === 'CRITICAL' ? 'error' : p.level === 'UNKNOWN_ERROR' ? 'info' : 'recv', `${icon} ${p.level}`, `${detail} | ${p.logSnippet || ''}`)
        return
      }
      if (msg.type === 'CMD_REQUEST') {
        addLog('recv', '◀ CMD', e.data, msg.payload?.cmdId, msg.payload?.action)
        return
      }
      if (msg.type === 'cmd' && msg.cmd?.cmdID) {
        addLog('recv', '◀ CMD', e.data, msg.cmd.cmdID, msg.cmd.func)
        return
      }
    } catch (_) {}
    addLog('recv', '◀ RECV', e.data)
  }
  
  ws.onerror = () => addLog('error', 'ERR', '❌ WebSocket 发生错误')
  ws.onclose = (e) => {
    wsConnected.value = false
    addLog('info', 'SYS', `🔌 连接已断开 (代码=${e.code})`)
    ws = null
  }
}

function disconnectWS() { if (ws) ws.close(1000, 'user-close') }

function wsSend(obj) {
  if (!ws || ws.readyState !== WebSocket.OPEN) {
    addLog('error', 'ERR', '未连接，请先连接 WebSocket。')
    return
  }
  const json = JSON.stringify(obj)
  ws.send(json)
  addLog('sent', '▶ 发送', json)
}

// ---------- Quick Actions ----------
const sendHeartbeat = () => wsSend({ type: 'HEARTBEAT', agentId: agentId.value, timestamp: Date.now(), payload: {} })
const sendMetrics = () => wsSend({
  type: 'METRICS', agentId: agentId.value, timestamp: Date.now(),
  payload: {
    cpu: { usagePercent: +(Math.random() * 100).toFixed(1), coreCount: 8 },
    memory: { totalMB: 16384, usedMB: Math.floor(Math.random() * 16384), usagePercent: +(Math.random() * 100).toFixed(1) },
    disk: { totalGB: 500, usedGB: Math.floor(Math.random() * 500), usagePercent: +(Math.random() * 100).toFixed(1) },
    network: { bytesSentPerSec: Math.floor(Math.random() * 500000), bytesRecvPerSec: Math.floor(Math.random() * 500000) }
  }
})

const sendLogLine = (line) => wsSend({
  type: 'LOG_LINE', agentId: agentId.value, timestamp: Date.now(),
  payload: { appName: appName.value, logPath: '/opt/logs/app.log', line }
})

const sendNormalLog = () => sendLogLine(`${new Date().toISOString()} INFO com.app.Main - Application running normally`)
const sendErrorLog = () => sendLogLine(`${new Date().toISOString()} ERROR com.app.Service - NullPointerException at line 42`)
const sendCriticalLog = () => sendLogLine(`${new Date().toISOString()} ERROR com.pay.Service - Third party timeout! traceId=abc123`)
const sendExcludeLog = () => sendLogLine(`${new Date().toISOString()} INFO com.app.Health - HealthCheck OK`)
const sendCustomLog = () => { if(customLog.value) sendLogLine(customLog.value) }

function sendRaw() {
  try { wsSend(JSON.parse(rawJson.value)) }
  catch (e) { addLog('error', 'ERR', `JSON Parse Error: ${e.message}`) }
}

function replyCmd(log) {
  const result = prompt(`回复操作: ${log.func || '命令'}\ncmdID: ${log.cmdId}\n请输入回复响应体内容:`, `{"status":"ok","message":"${log.func} success"}`)
  if (result === null) return
  
  wsSend({ type: `${log.func}@Return`, result: result || `${log.func} success`, cmdID: log.cmdId })
  log.replied = true
}

// ---------- Rules Management ----------
const rules = ref([])
const ruleForm = ref({ ruleType: 'CRITICAL', ruleName: '', keyword: '', matchMode: 'CONTAINS' })

async function loadRules() {
  try {
    const res = await fetch('/api/rules')
    const json = await res.json()
    rules.value = json.data || []
  } catch (e) {
    addLog('error', 'API', `Load rules failed: ${e.message}`)
  }
}

async function addRule() {
  if (!ruleForm.value.ruleName || !ruleForm.value.keyword) {
    addLog('error', 'API', 'Rule Name and Keyword cannot be empty')
    return
  }
  const rule = { ...ruleForm.value, enabled: true, priority: 0 }
  try {
    const res = await fetch('/api/rules', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(rule) })
    const json = await res.json()
    addLog('info', 'API', `Rule added: id=${json.data?.id}`)
    ruleForm.value.ruleName = ''
    ruleForm.value.keyword = ''
    loadRules()
  } catch (e) {
    addLog('error', 'API', `Add rule failed: ${e.message}`)
  }
}

async function deleteRule(id) {
  try {
    await fetch(`/api/rules/${id}`, { method: 'DELETE' })
    addLog('info', 'API', `Rule #${id} deleted`)
    loadRules()
  } catch (e) {
    addLog('error', 'API', `Delete rule failed: ${e.message}`)
  }
}

async function rebuildMatchers() {
  try {
    const res = await fetch('/api/rules/rebuild', { method: 'POST' })
    const json = await res.json()
    addLog('info', 'API', `Matchers rebuilt: ${json.data}`)
  } catch (e) {
    addLog('error', 'API', `Rebuild failed: ${e.message}`)
  }
}

onMounted(() => { loadRules() })
onUnmounted(() => { disconnectWS() })
</script>
