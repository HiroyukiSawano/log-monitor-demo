<template>
  <div class="p-6 h-full text-text bg-bg overflow-auto flex flex-col gap-6">
    <!-- Header -->
    <header class="flex items-center gap-3 shrink-0">
      <div class="w-8 h-8 rounded-lg bg-orange-500/20 border border-orange-500/50 flex text-orange-500 items-center justify-center">
        <el-icon :size="18"><Document /></el-icon>
      </div>
      <div>
        <h2 class="text-xl font-bold tracking-wide">日志审计</h2>
        <p class="text-[11px] text-text2 mt-0.5 uppercase tracking-wider">查看匹配的日志记录与告警触发情况</p>
      </div>
    </header>

    <!-- Stats Bar -->
    <div class="flex gap-6 shrink-0">
      <div class="bg-surface border border-red/20 shadow-[0_0_15px_rgba(225,112,85,0.05)] rounded-xl p-5 flex-1 flex flex-col items-center justify-center relative overflow-hidden group">
        <div class="absolute inset-0 bg-red/5 opacity-0 group-hover:opacity-100 transition-opacity"></div>
        <div class="text-[36px] font-bold text-red leading-none mb-1">{{ stats.CRITICAL || 0 }}</div>
        <div class="text-xs text-text2 uppercase tracking-widest font-semibold flex items-center gap-1.5"><div class="w-2 h-2 rounded-full bg-red shadow-[0_0_8px_var(--red)]"></div> 严重告警 (CRITICAL)</div>
      </div>
      <div class="bg-surface border border-yellow/20 shadow-[0_0_15px_rgba(253,203,110,0.05)] rounded-xl p-5 flex-1 flex flex-col items-center justify-center relative overflow-hidden group">
        <div class="absolute inset-0 bg-yellow/5 opacity-0 group-hover:opacity-100 transition-opacity"></div>
        <div class="text-[36px] font-bold text-yellow leading-none mb-1">{{ stats.UNKNOWN_ERROR || 0 }}</div>
        <div class="text-xs text-text2 uppercase tracking-widest font-semibold flex items-center gap-1.5"><div class="w-2 h-2 rounded-full bg-yellow shadow-[0_0_8px_var(--yellow)]"></div> 常规匹配 (BASIC/UNKNOWN)</div>
      </div>
    </div>

    <!-- Filter & Table -->
    <div class="bg-surface border border-border rounded-xl shadow-lg flex flex-col flex-1 min-h-[400px]">
      <!-- Filters -->
      <div class="px-5 py-4 border-b border-border bg-surface2 flex items-center gap-4 shrink-0 flex-wrap">
        <div class="flex items-center gap-2">
          <label class="text-[10px] text-text2 uppercase font-semibold">服务器</label>
          <input v-model="filters.agentId" type="text" placeholder="全部" class="w-32 bg-bg border border-border text-text text-xs px-3 py-1.5 rounded focus:border-accent outline-none transition-colors">
        </div>
        <div class="flex items-center gap-2">
          <label class="text-[10px] text-text2 uppercase font-semibold">级别</label>
          <select v-model="filters.level" class="w-32 bg-bg border border-border text-text text-xs px-3 py-1.5 rounded focus:border-accent outline-none">
            <option value="">全部</option>
            <option value="CRITICAL">CRITICAL</option>
            <option value="UNKNOWN_ERROR">UNKNOWN_ERROR</option>
          </select>
        </div>
        <div class="flex items-center gap-2">
          <label class="text-[10px] text-text2 uppercase font-semibold">显示数量</label>
          <select v-model="filters.limit" class="w-20 bg-bg border border-border text-text text-xs px-3 py-1.5 rounded focus:border-accent outline-none">
            <option value="50">50</option>
            <option value="100">100</option>
            <option value="200">200</option>
            <option value="500">500</option>
          </select>
        </div>
        <button @click="loadData" class="ml-2 px-4 py-1.5 rounded bg-accent text-white text-xs font-semibold hover:bg-opacity-90 transition-all shadow-sm flex items-center gap-1.5">
          <el-icon><Search /></el-icon> 查询
        </button>
        <button @click="loadData" class="text-blue hover:text-cyan text-xs transition-colors p-2 flex items-center justify-center">
          <el-icon><Refresh /></el-icon>
        </button>
      </div>

      <!-- Table Area -->
      <div class="flex-1 overflow-auto rounded-b-xl relative">
        <table class="w-full text-left text-xs">
          <thead class="bg-surface2 sticky top-0 z-10 shadow-sm">
            <tr>
              <th class="px-4 py-3 text-text2 font-semibold border-b border-border w-[140px]">时间</th>
              <th class="px-4 py-3 text-text2 font-semibold border-b border-border w-[120px]">服务器</th>
              <th class="px-4 py-3 text-text2 font-semibold border-b border-border w-[120px]">应用</th>
              <th class="px-4 py-3 text-text2 font-semibold border-b border-border w-[100px]">级别</th>
              <th class="px-4 py-3 text-text2 font-semibold border-b border-border w-[200px]">命中规则</th>
              <th class="px-4 py-3 text-text2 font-semibold border-b border-border">日志摘要</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="records.length === 0">
              <td colspan="6" class="p-12 text-center text-text2 opacity-60">
                <el-icon :size="32" class="mb-3"><DocumentDelete /></el-icon>
                <p>没有符合条件的日志记录。</p>
              </td>
            </tr>
            <tr v-for="r in records" :key="r.id" @click="viewDetail(r)" class="border-b border-border/50 hover:bg-surface2 cursor-pointer transition-colors">
              <td class="px-4 py-3 font-mono text-[10px] text-text2">{{ formatTime(r.logTime) }}</td>
              <td class="px-4 py-3 truncate max-w-[120px]">{{ r.agentId }}</td>
              <td class="px-4 py-3 truncate max-w-[120px]">{{ r.appName || '-' }}</td>
              <td class="px-4 py-3">
                <span class="px-1.5 py-0.5 rounded text-[9px] font-bold" :class="r.level === 'CRITICAL' ? 'bg-red/20 text-red' : 'bg-yellow/20 text-yellow'">{{ r.level }}</span>
              </td>
              <td class="px-4 py-3 truncate max-w-[200px]">
                {{ r.matchedRuleName || '—' }} <span class="font-mono text-[10px] text-text2 ml-1">{{ r.matchedKeyword }}</span>
              </td>
              <td class="px-4 py-3 font-mono text-[10px] truncate max-w-[300px] xl:max-w-[500px]" :class="r.level === 'CRITICAL' ? 'text-red/80' : 'text-yellow/80'">{{ r.logContent }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Detail Drawer -->
    <el-drawer
      v-model="drawerVisible"
      title="日志审计详情"
      direction="rtl"
      size="500px"
      :show-close="true"
      custom-class="!bg-surface !border-l !border-border custom-drawer"
    >
      <div v-if="activeRecord" class="flex flex-col gap-5 text-sm h-full pb-4">
        
        <div class="flex items-center justify-between border-b border-border pb-3">
           <span class="px-2 py-1 rounded text-[10px] font-bold tracking-wider" :class="activeRecord.level === 'CRITICAL' ? 'bg-red/10 text-red border border-red/20' : 'bg-yellow/10 text-yellow border border-yellow/20'">{{ activeRecord.level }}</span>
           <span class="font-mono text-text2 text-[11px]">{{ formatTime(activeRecord.logTime) }}</span>
        </div>

        <div class="grid grid-cols-2 gap-4 border-b border-border pb-4">
          <div>
            <div class="text-[10px] text-text2 uppercase tracking-widest font-semibold mb-1">节点ID</div>
            <div class="font-medium text-text">{{ activeRecord.agentId }}</div>
          </div>
          <div>
            <div class="text-[10px] text-text2 uppercase tracking-widest font-semibold mb-1">应用名称</div>
            <div class="font-medium text-text">{{ activeRecord.appName || '—' }}</div>
          </div>
          <div class="col-span-2">
            <div class="text-[10px] text-text2 uppercase tracking-widest font-semibold mb-1">日志路径</div>
            <div class="font-mono text-xs bg-bg border border-border text-text rounded px-2 py-1 break-all">{{ activeRecord.logPath || '—' }}</div>
          </div>
        </div>

        <div class="border-b border-border pb-4">
          <div class="text-[10px] text-text2 uppercase tracking-widest font-semibold mb-1">命中规则</div>
          <div class="font-medium text-text bg-surface2 border border-border px-3 py-2 rounded">
             {{ activeRecord.matchedRuleName || '—' }}
             <div class="font-mono text-[11px] text-accent mt-1">{{ activeRecord.matchedKeyword }}</div>
          </div>
        </div>

        <div class="flex-1 flex flex-col min-h-[150px]">
          <div class="text-[10px] text-text2 uppercase tracking-widest font-semibold mb-2">原始日志内容</div>
          <div class="flex-1 bg-bg border border-border rounded p-3 overflow-y-auto font-mono text-[11px] leading-relaxed break-all shadow-sm" :class="activeRecord.level === 'CRITICAL' ? 'text-red' : 'text-text'">
            {{ activeRecord.logContent || '(空)' }}
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { Document, Search, Refresh, DocumentDelete } from '@element-plus/icons-vue'
import { ref, onMounted } from 'vue'

const stats = ref({})
const records = ref([])
const filters = ref({
  agentId: '',
  level: '',
  limit: '100'
})

const drawerVisible = ref(false)
const activeRecord = ref(null)

function formatTime(val) {
  if (!val) return '—'
  const d = new Date(val)
  return d.toLocaleString('zh-CN', { hour12: false })
}

async function loadStats() {
  try {
    const res = await fetch('/api/loghits/stats')
    const json = await res.json()
    stats.value = json.data || {}
  } catch (e) {
    console.error('Failed to load stats', e)
  }
}

async function loadRecords() {
  const params = new URLSearchParams()
  if (filters.value.agentId) params.set('agentId', filters.value.agentId)
  if (filters.value.level) params.set('level', filters.value.level)
  if (filters.value.limit) params.set('limit', filters.value.limit)

  try {
    const res = await fetch(`/api/loghits?${params.toString()}`)
    const json = await res.json()
    records.value = json.data || []
  } catch (e) {
    console.error('Failed to load records', e)
  }
}

function loadData() {
  loadStats()
  loadRecords()
}

async function viewDetail(row) {
  try {
    const res = await fetch(`/api/loghits/${row.id}`)
    const json = await res.json()
    if (json.data) {
      activeRecord.value = json.data
      drawerVisible.value = true
    }
  } catch (e) {
    console.error(e)
  }
}

onMounted(() => {
  loadData()
})
</script>

<style>
.custom-drawer .el-drawer__header {
  margin-bottom: 0;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
  color: var(--text);
  font-weight: 600;
}
.custom-drawer .el-drawer__body {
  padding: 20px;
  background-color: var(--bg);
}
</style>
