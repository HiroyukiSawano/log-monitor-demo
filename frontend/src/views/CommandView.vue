<template>
  <div class="p-6 h-full text-text bg-bg overflow-auto flex flex-col gap-6">
    <!-- Header -->
    <header class="flex items-center gap-3 shrink-0">
      <div class="w-8 h-8 rounded-lg bg-accent/20 border border-accent/50 flex items-center justify-center text-accent">
        <el-icon :size="18"><Connection /></el-icon>
      </div>
      <div>
        <h2 class="text-xl font-bold tracking-wide">远程指令</h2>
        <p class="text-[11px] text-text2 mt-0.5 uppercase tracking-wider">执行参数并监听实时响应</p>
      </div>
    </header>

    <!-- Main Grid -->
    <div class="grid grid-cols-1 lg:grid-cols-12 gap-6 flex-1 min-h-[600px]">
      <!-- Left Column: Form -->
      <div class="lg:col-span-4 flex flex-col gap-4">
        <div class="bg-surface border border-border rounded-xl flex flex-col flex-1 overflow-hidden shadow-lg">
          <div class="px-5 py-3 border-b border-border bg-surface2 flex items-center justify-between">
            <span class="text-xs font-semibold uppercase tracking-wider text-text2">指令载荷</span>
          </div>
          <div class="p-5 flex flex-col gap-5 flex-1">
            <!-- Agent Selector -->
            <div>
              <div class="flex items-center justify-between mb-2">
                <label class="text-xs text-text2 font-medium">目标服务器</label>
                <button @click="loadOnlineAgents" class="text-[10px] text-blue hover:text-cyan transition-colors flex items-center gap-1">
                  <el-icon><RefreshRight /></el-icon> 刷新
                </button>
              </div>
              <select v-model="selectedAgent" @change="onAgentChange" class="w-full bg-surface border border-border text-sm text-text rounded-md px-3 py-2 focus:outline-none focus:border-accent">
                <option value="" disabled>-- 请选择在线服务器 --</option>
                <option v-for="a in onlineAgents" :key="a.agentId" :value="a.agentId">
                  {{ a.agentId }} ({{ a.open ? '在线' : '离线' }})
                </option>
              </select>
            </div>

            <!-- Content Area -->
            <div class="flex-1 flex flex-col">
              <label class="text-xs text-text2 font-medium mb-2">报文数据 (JSON)</label>
              <textarea v-model="commandContent" class="w-full flex-1 bg-bg border border-border text-text font-mono text-xs p-3 rounded-md focus:outline-none focus:border-accent resize-none shadow-sm" spellcheck="false"></textarea>
              <p class="text-[10px] text-text2 mt-2">💡 确保报文中包含有效的 <span class="font-mono text-accent">cmdID</span>。</p>
            </div>

            <!-- Send Button -->
            <button @click="sendCommand" :disabled="isSending" class="w-full py-2.5 rounded-md text-sm font-semibold transition-all flex items-center justify-center gap-2" :class="isSending ? 'bg-surface2 text-text2 cursor-not-allowed' : 'bg-accent text-white hover:bg-opacity-90 hover:shadow-lg'">
              <el-icon v-if="isSending" class="is-loading"><Loading /></el-icon>
              <el-icon v-else><Position /></el-icon>
              {{ isSending ? '发送中...' : '发送指令' }}
            </button>

            <!-- Presets -->
            <div class="pt-4 border-t border-border">
              <label class="text-[10px] text-text2 uppercase tracking-wide block mb-3">快捷指令模板</label>
              <div class="flex flex-wrap gap-2">
                <button v-for="(p, i) in presets" :key="i" @click="applyPreset(p.content)" class="px-2.5 py-1 rounded bg-bg border border-border hover:border-accent hover:text-accent text-xs transition-colors">
                  {{ p.label }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Right Column: History & WS -->
      <div class="lg:col-span-8 flex flex-col gap-6">
        <!-- Top Right: History & Result -->
        <div class="bg-surface border border-border rounded-xl flex flex-col h-1/2 shadow-lg">
          <div class="px-5 py-3 border-b border-border bg-surface2 flex items-center gap-4">
            <span class="text-xs font-semibold uppercase tracking-wider text-text2">执行历史</span>
            <button @click="loadHistory" class="ml-auto text-xs text-blue hover:text-cyan transition-colors flex items-center gap-1">
              <el-icon><Refresh /></el-icon> 刷新
            </button>
          </div>
          
          <div class="flex-1 flex overflow-hidden">
            <!-- History Table -->
            <div class="w-3/5 border-r border-border overflow-y-auto">
              <table class="w-full text-left text-xs">
                <thead class="bg-surface2 sticky top-0 z-10">
                  <tr>
                    <th class="px-3 py-2 font-medium text-text2 border-b border-border">时间</th>
                    <th class="px-3 py-2 font-medium text-text2 border-b border-border">服务器</th>
                    <th class="px-3 py-2 font-medium text-text2 border-b border-border">状态</th>
                    <th class="px-3 py-2 font-medium text-text2 border-b border-border">函数</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-if="historyList.length === 0">
                    <td colspan="4" class="p-8 text-center text-text2 opacity-60">
                      <el-icon :size="24" class="mb-2"><FolderOpened /></el-icon>
                      <p>暂无命令记录</p>
                    </td>
                  </tr>
                  <tr v-for="h in historyList" :key="h.cmdId" @click="selectHistory(h)" class="border-b border-border/50 hover:bg-surface2 cursor-pointer transition-colors" :class="{'bg-accent/10 border-l-2 border-l-accent': activeCmd?.cmdId === h.cmdId}">
                    <td class="px-3 py-2 font-mono text-[10px]">{{ formatTime(h.createTime) }}</td>
                    <td class="px-3 py-2 truncate max-w-[80px]">{{ h.agentId }}</td>
                    <td class="px-3 py-2">
                       <span class="inline-block px-1.5 py-0.5 rounded text-[9px] font-bold tracking-wide" 
                             :class="{
                               'bg-green/20 text-green': h.status === 'SUCCESS',
                               'bg-red/20 text-red': h.status === 'FAILED',
                               'bg-blue/20 text-blue': h.status === 'PENDING',
                               'bg-yellow/20 text-yellow': h.status === 'TIMEOUT'
                             }">
                         {{ h.status }}
                       </span>
                    </td>
                    <td class="px-3 py-2 font-mono text-[10px] text-text2 truncate max-w-[80px]">{{ h.action || '—' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            
            <!-- Details Panel -->
            <div class="w-2/5 p-4 overflow-y-auto bg-surface2 w-full">
              <div v-if="!activeCmd" class="flex items-center justify-center h-full text-text2 text-xs opacity-60">
                请选择一条记录查看详情
              </div>
              <div v-else class="flex flex-col gap-3 h-full">
                <div class="flex items-center gap-2">
                  <span class="inline-block px-2 py-0.5 rounded text-[10px] font-bold tracking-wide" 
                        :class="{
                          'bg-green/20 text-green': activeCmd.status === 'SUCCESS',
                          'bg-red/20 text-red': activeCmd.status === 'FAILED',
                          'bg-blue/20 text-blue': activeCmd.status === 'PENDING',
                          'bg-yellow/20 text-yellow': activeCmd.status === 'TIMEOUT'
                        }">
                    {{ activeCmd.status }}
                  </span>
                  <span class="text-[10px] text-text2 font-mono ml-auto">{{ formatTime(activeCmd.createTime) }}</span>
                </div>
                <div class="text-[11px] bg-bg p-2 rounded border border-border">
                  <div class="mb-1"><span class="text-text2 inline-block w-12">节点:</span> <span class="font-mono">{{ activeCmd.agentId }}</span></div>
                  <div class="mb-1"><span class="text-text2 inline-block w-12">操作:</span> <span class="font-mono">{{ activeCmd.action || '—' }}</span></div>
                  <div><span class="text-text2 inline-block w-12">指令ID:</span> <span class="font-mono">{{ activeCmd.cmdId }}</span></div>
                </div>
                <div class="flex-1 flex flex-col min-h-[120px]">
                  <span class="text-[10px] font-semibold text-text2 uppercase tracking-wider mb-1">响应结果</span>
                  <div class="flex-1 bg-bg border border-border rounded p-3 overflow-auto font-mono text-[11px] whitespace-pre-wrap break-all shadow-sm" :class="activeCmd.status === 'FAILED' ? 'text-red' : 'text-text'">{{ formatJson(activeCmd.response) }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Bottom Right: Live WS Terminal -->
        <div class="bg-surface border border-border rounded-xl flex flex-col h-1/2 shadow-lg">
          <div class="px-4 py-2 border-b border-border bg-surface2 flex items-center gap-3 shrink-0">
            <span class="text-xs font-semibold uppercase tracking-wider text-text2">实时监控</span>
            
            <div class="flex items-center gap-1.5 ml-4">
              <div class="w-2 h-2 rounded-full" :class="wsConnected ? 'bg-green shadow-[0_0_6px_var(--green)]' : 'bg-border'"></div>
              <span class="text-[10px] font-mono text-text2">{{ wsStatusText }}</span>
            </div>
            
            <button @click="clearLogs" class="ml-auto text-[10px] px-2 py-1 rounded border border-border bg-surface hover:bg-surface2 hover:text-red transition-colors flex items-center gap-1">
              <el-icon><Delete /></el-icon> 清空
            </button>
          </div>
          <div class="flex-1 bg-bg p-3 overflow-y-auto font-mono text-[11px] leading-relaxed shadow-inner" ref="logContainer">
            <div v-for="log in wsLogs" :key="log.id" class="mb-1 break-all flex gap-2 hover:bg-surface2 px-1 rounded transition-colors border-b border-transparent hover:border-border">
              <span class="text-text2 shrink-0">[{{ log.time }}]</span>
              <span class="font-bold shrink-0 w-16" :class="{
                'text-green': log.cat === 'up',
                'text-blue': log.cat === 'down',
                'text-yellow': log.cat === 'sys',
                'text-red': log.cat === 'error'
              }">[{{ log.dir }}]</span>
              <span class="text-text">{{ log.msg }}</span>
            </div>
            <div v-if="wsLogs.length === 0" class="text-text2 opacity-50 italic">等待接收消息...</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { Connection, Position, Reading, RefreshRight, Refresh, Loading, FolderOpened, Delete } from '@element-plus/icons-vue'

// State
const onlineAgents = ref([])
const selectedAgent = ref('')
const commandContent = ref(
`{
  "type": "cmd",
  "cmd": {
    "func": "logTail/add",
    "param": ["D:\\\\logs\\\\*.log", ""],
    "cmdID": "add-001"
  }
}`
)
const isSending = ref(false)
const historyList = ref([])
const activeCmd = ref(null)

// Presets
const presets = [
  { label: '📂 监听日志', content: { type: 'cmd', cmd: { func: 'logTail/add', param: ['D:\\logs\\*.log', ''], cmdID: 'add-001' } } },
  { label: '🛑 停止监听', content: { type: 'cmd', cmd: { func: 'logTail/remove', param: ['D:\\logs\\*.log'], cmdID: 'remove-001' } } },
  { label: '💳 读取终端', content: { type: 'cmd', cmd: { func: 'iDCardReader/read', param: [], cmdID: 'read-001' } } },
  { label: '📊 获取指标', content: { type: 'cmd', cmd: { func: 'metrics/get', param: [], cmdID: 'metrics-001' } } },
]

function applyPreset(obj) {
  commandContent.value = JSON.stringify(obj, null, 2)
}

// Helpers
function formatTime(val) {
  if (!val) return '—'
  const d = new Date(val)
  return d.toLocaleTimeString('zh-CN', { hour12: false }) + '.' + String(d.getMilliseconds()).padStart(3, '0')
}

function formatJson(str) {
  if (!str) return '(Empty)'
  try {
    const o = JSON.parse(str)
    return JSON.stringify(o, null, 2)
  } catch (e) {
    return String(str)
  }
}

// API Calls
async function loadOnlineAgents() {
  try {
    const res = await fetch('/api/agents/online')
    const json = await res.json()
    onlineAgents.value = json.data || []
    if (selectedAgent.value && !onlineAgents.value.some(a => a.agentId === selectedAgent.value)) {
      selectedAgent.value = ''
      disconnectMonitor()
    }
  } catch (e) {
    addWsLog('error', 'API', `Failed to load agents: ${e.message}`)
  }
}

async function loadHistory() {
  try {
    const res = await fetch('/api/commands')
    const json = await res.json()
    historyList.value = json.data || []
  } catch (e) {
    console.error(e)
  }
}

function selectHistory(cmd) {
  activeCmd.value = cmd
  // Fetch details if needed or use the overview
  fetchCommandDetail(cmd.cmdId)
}

async function fetchCommandDetail(cmdId) {
  try {
    const res = await fetch(`/api/commands/${cmdId}`)
    const json = await res.json()
    if (json.data && activeCmd.value?.cmdId === cmdId) {
      activeCmd.value = json.data
    }
  } catch (e) {
    console.error(e)
  }
}

async function sendCommand() {
  if (!selectedAgent.value) {
    alert('请选择目标服务器')
    return
  }
  if (!commandContent.value.trim()) return

  try {
    JSON.parse(commandContent.value)
  } catch (e) {
    alert('请求体必须是合法的JSON')
    return
  }

  isSending.value = true
  const tempCmd = {
    cmdId: '发送中...',
    agentId: selectedAgent.value,
    action: '处理中',
    status: 'PENDING',
    createTime: Date.now(),
    response: '等待执行结果...'
  }
  activeCmd.value = tempCmd

  try {
    const res = await fetch('/api/commands/send', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        agentId: selectedAgent.value,
        content: commandContent.value
      })
    })
    const json = await res.json()
    
    if (json.code === 200 && json.data) {
      activeCmd.value = json.data
    } else {
      tempCmd.status = 'FAILED'
      tempCmd.response = json.message || 'Request failed'
    }
  } catch (e) {
    tempCmd.status = 'FAILED'
    tempCmd.response = e.message
  } finally {
    isSending.value = false
    loadHistory()
  }
}

// WS Monitor
const wsLogs = ref([])
const logContainer = ref(null)
let monitorWs = null
const wsConnected = ref(false)
const wsStatusText = ref('未连接')
let logId = 0

function ts() {
  const d = new Date()
  return d.toLocaleTimeString('zh-CN', { hour12: false }) + '.' + String(d.getMilliseconds()).padStart(3, '0')
}

function addWsLog(cat, dir, msg) {
  wsLogs.value.push({ id: ++logId, cat, dir, msg, time: ts() })
  if (wsLogs.value.length > 500) wsLogs.value.shift()
  nextTick(() => {
    if (logContainer.value) {
      logContainer.value.scrollTop = logContainer.value.scrollHeight
    }
  })
}

function clearLogs() {
  wsLogs.value = []
}

function onAgentChange() {
  connectMonitor(selectedAgent.value)
}

function connectMonitor(agentId) {
  disconnectMonitor()
  if (!agentId) return

  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const url = `${protocol}//${location.host}/ws/monitor?agentId=${encodeURIComponent(agentId)}`
  
  addWsLog('sys', 'SYS', `正在连接到 ${url}...`)
  wsStatusText.value = `正在连接 [${agentId}]...`

  try {
    monitorWs = new WebSocket(url)
  } catch (e) {
    addWsLog('error', 'ERR', `WS 创建失败: ${e.message}`)
    wsStatusText.value = '连接失败'
    return
  }

  monitorWs.onopen = () => {
    wsConnected.value = true
    wsStatusText.value = `已订阅 [${agentId}]`
    addWsLog('sys', 'SYS', `✅ 已连接并订阅`)
  }

  monitorWs.onmessage = (e) => {
    try {
      const msg = JSON.parse(e.data)
      const dir = msg.dir || 'UNKNOWN'
      const rawText = typeof msg.raw === 'string' ? msg.raw : JSON.stringify(msg.raw)

      if (dir === 'AGENT_UP') addWsLog('up', '◀ AGENT', rawText)
      else if (dir === 'SERVER_DOWN') addWsLog('down', '▶ SERVER', rawText)
      else addWsLog('sys', dir, rawText)
    } catch (_) {
      addWsLog('sys', 'RAW', e.data)
    }
  }

  monitorWs.onerror = () => {
    addWsLog('error', 'ERR', '❌ WebSocket 连接发生错误')
  }

  monitorWs.onclose = (e) => {
    wsConnected.value = false
    wsStatusText.value = `已断开连接 (${e.code})`
    addWsLog('sys', 'SYS', `🔌 连接已关闭，代码=${e.code}`)
    monitorWs = null
  }
}

function disconnectMonitor() {
  if (monitorWs) {
    monitorWs.close(1000, 'switch-agent')
    monitorWs = null
  }
  wsConnected.value = false
  wsStatusText.value = '未连接'
}

onMounted(() => {
  loadOnlineAgents()
  loadHistory()
})

onUnmounted(() => {
  disconnectMonitor()
})
</script>
