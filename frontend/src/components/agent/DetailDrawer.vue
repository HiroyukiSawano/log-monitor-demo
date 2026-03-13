<template>
  <el-drawer
    v-model="drawerVisible"
    :title="agent?.systemInfo?.hostname || '节点详情'"
    size="540px"
    class="!bg-surface !border-l !border-border text-text custom-drawer"
    :with-header="false"
  >
    <div v-if="agent" class="h-full flex flex-col pt-0 relative" v-loading="loading">
      <!-- Header Area -->
      <div class="px-5 py-4 border-b border-border bg-surface2 flex items-center justify-between sticky top-0 z-10">
        <h2 class="text-[16px] font-semibold text-text truncate">
          {{ agent.systemInfo?.hostname || agent.id }}
        </h2>
        <button 
          @click="drawerVisible = false" 
          class="text-text2 hover:bg-bg hover:text-text p-1.5 rounded transition-colors"
        >
          <el-icon :size="18"><Close /></el-icon>
        </button>
      </div>

      <!-- Main Content -->
      <div class="flex-1 overflow-y-auto p-5 scrollbar-thin">
        
        <!-- Hardware Box -->
        <div class="mb-6">
          <h3 class="text-xs font-semibold uppercase tracking-wide text-text2 mb-3 flex items-center gap-1.5">
            <el-icon><Monitor /></el-icon> 硬件规格
          </h3>
          <div class="grid grid-cols-2 gap-2">
            <div class="col-span-2 bg-bg border border-border rounded-md p-2.5">
              <div class="text-[10px] text-text2 mb-1">操作系统</div>
              <div class="text-xs font-semibold">{{ [deepDetails?.osName, deepDetails?.osVersion].filter(Boolean).join(' ') || agent.systemInfo?.osName || '未知' }}</div>
            </div>
            <div class="bg-bg border border-border rounded-md p-2.5">
              <div class="text-[10px] text-text2 mb-1">系统架构</div>
              <div class="text-xs font-semibold">{{ deepDetails?.osType || '-' }}</div>
            </div>
            <div class="bg-bg border border-border rounded-md p-2.5">
              <div class="text-[10px] text-text2 mb-1">逻辑核数</div>
              <div class="text-xs font-semibold">{{ deepDetails?.cpuCores || agent.systemInfo?.availableProcessors || '-' }}</div>
            </div>
            <div class="col-span-2 bg-bg border border-border rounded-md p-2.5">
              <div class="text-[10px] text-text2 mb-1">处理器型号</div>
              <div class="text-xs font-semibold">{{ deepDetails?.cpuType || agent.systemInfo?.cpuType || '-' }} ({{ deepDetails?.cpuSpeed || '-' }})</div>
            </div>
            <div class="col-span-2 bg-bg border border-border rounded-md p-2.5">
              <div class="text-[10px] text-text2 mb-1">内存使用</div>
              <div class="text-xs font-semibold">
                {{ deepDetails?.ramUsage || '?' }} / {{ deepDetails?.ramCapacity || formatBytes(agent.systemInfo?.totalMemory) || '?' }} 
                <span class="text-text2 font-normal">(可用: {{ deepDetails?.ramAvailable || '?' }})</span>
              </div>
            </div>
            <div class="col-span-2 bg-bg border border-border rounded-md p-2.5">
              <div class="text-[10px] text-text2 mb-1">内存规格</div>
              <div class="text-xs font-semibold">{{ [deepDetails?.ramType, deepDetails?.ramSpeed, deepDetails?.ramManufacturer].filter(Boolean).join(' · ') || '-' }}</div>
            </div>
            <div class="col-span-2 bg-bg border border-border rounded-md p-2.5">
              <div class="text-[10px] text-text2 mb-1">主板型号</div>
              <div class="text-xs font-semibold truncate" :title="deepDetails?.mainBoard || '-'">{{ deepDetails?.mainBoard || '-' }}</div>
            </div>
            <div class="col-span-2 bg-bg border border-border rounded-md p-2.5">
              <div class="text-[10px] text-text2 mb-1">磁盘总览</div>
              <div class="text-xs font-semibold truncate">{{ deepDetails?.disk || '-' }}</div>
            </div>
            <div class="col-span-2 bg-bg border border-border rounded-md p-2.5" v-if="deepDetails?.gpu">
              <div class="text-[10px] text-text2 mb-1">显卡</div>
              <div class="text-xs font-semibold whitespace-pre-line">{{ deepDetails?.gpu.split('|').filter(Boolean).join('\n') || '-' }}</div>
            </div>
            <div class="bg-bg border border-border rounded-md p-2.5">
              <div class="text-[10px] text-text2 mb-1">进程总数</div>
              <div class="text-xs font-semibold">{{ deepDetails?.processCount || '-' }}</div>
            </div>
            <div class="bg-bg border border-border rounded-md p-2.5">
              <div class="text-[10px] text-text2 mb-1">最后上报</div>
              <div class="text-[11px] font-mono mt-0.5">{{ deepDetails?.feedbackTime || '-' }}</div>
            </div>
          </div>
        </div>

        <!-- Disks List -->
        <div class="mb-6" v-if="deepDetails?.parts?.length || agent.systemInfo?.disks?.length">
          <h3 class="text-xs font-semibold uppercase tracking-wide text-text2 mb-3 flex items-center gap-1.5">
            <el-icon><DataBoard /></el-icon> 磁盘分区
          </h3>
          <div class="flex flex-col gap-2 relative">
            <div 
              v-for="disk in partitionList" 
              :key="disk.mountPoint"
              class="flex flex-wrap items-center gap-x-3 gap-y-1.5 px-3 py-2 bg-bg border border-border rounded-md"
            >
              <div class="text-[13px] font-bold min-w-[30px] max-w-[150px] text-cyan truncate" :title="disk.mountPoint">
                {{ disk.mountPoint }}
              </div>
              <div class="flex-1 min-w-[120px]">
                <div class="h-2 bg-surface2 rounded w-full overflow-hidden">
                  <div 
                    class="h-full rounded transition-all duration-500"
                    :class="getDiskColor(disk.percent)"
                    :style="{ width: `${disk.percent}%` }"
                  ></div>
                </div>
              </div>
              <div class="text-[10px] text-text2 whitespace-nowrap lg:w-[130px] lg:text-right shrink-0">
                {{ formatSize(disk.used) }} / {{ formatSize(disk.total) }} <span class="font-bold ml-1">({{ disk.percent }}%)</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Processes List -->
        <div class="mb-6" v-if="deepDetails?.processStatusList?.length || agent.processes?.length">
          <h3 class="text-xs font-semibold uppercase tracking-wide text-text2 mb-3 flex items-center gap-1.5">
            <el-icon><Cpu /></el-icon> 重点进程
          </h3>
          <div class="flex flex-col gap-1.5">
            <div 
              v-for="proc in getProcessList()" 
              :key="proc.name"
              class="flex items-center gap-3 px-3 py-2 bg-bg border border-border rounded-md hover:border-accent transition-colors cursor-pointer"
            >
              <div class="flex-1 text-xs font-medium truncate">{{ proc.name }}</div>
              <div 
                class="text-[11px] font-semibold px-2 py-0.5 rounded-sm"
                :class="proc.running ? 'bg-green/10 text-green' : 'bg-red/10 text-red'"
              >
                {{ proc.status }}
              </div>
            </div>
          </div>
        </div>

        <!-- Log Monitors -->
        <div class="mb-6">
          <h3 class="text-xs font-semibold uppercase tracking-wide text-text2 mb-3 flex items-center gap-1.5">
            <el-icon><FolderOpened /></el-icon> 日志监听管理
          </h3>
          <button 
            @click="openLogMonitorModal"
            class="w-full py-2.5 bg-surface2 border border-dashed border-border text-text2 rounded-lg text-xs hover:border-accent hover:text-text transition-all"
          >
            <el-icon class="mr-1"><Setting /></el-icon> 管理日志监听 ({{ agent?.id }})
          </button>
        </div>

        <!-- Filter Rules -->
        <div class="mb-6">
          <h3 class="text-xs font-semibold uppercase tracking-wide text-text2 mb-3 flex items-center gap-1.5">
            <el-icon><Document /></el-icon> 日志过滤规则
          </h3>
          <button 
            @click="openFilterRuleModal"
            class="w-full py-2.5 bg-surface2 border border-dashed border-border text-text2 rounded-lg text-xs hover:border-accent hover:text-text transition-all"
          >
            <el-icon class="mr-1"><Setting /></el-icon> 管理过滤规则 ({{ agent?.id }})
          </button>
        </div>

        <!-- Alert Rules -->
        <div class="mb-6">
          <h3 class="text-xs font-semibold uppercase tracking-wide text-text2 mb-3 flex items-center gap-1.5">
            <el-icon><Setting /></el-icon> 告警规则
          </h3>
          <button 
            @click="openRuleModal"
            class="w-full py-2.5 bg-surface2 border border-dashed border-border text-text2 rounded-lg text-xs hover:border-accent hover:text-text transition-all"
          >
            <el-icon class="mr-1"><Tools /></el-icon> 管理告警规则 ({{ agent?.id }})
          </button>
        </div>
        
        <!-- Hit Logs Timeline -->
        <div class="mb-2">
          <h3 class="text-xs font-semibold uppercase tracking-wide text-text2 mb-3 flex items-center gap-1.5">
            <el-icon><Warning /></el-icon> 近期命中日志 (最近50条)
          </h3>
          <div class="flex flex-col gap-2 relative">
             <div v-if="errorLogs.length === 0" class="text-center py-6 text-text2 text-xs border border-border rounded-lg bg-bg/50">
               暂无命中日志 ✨
             </div>
             
             <div 
               v-for="log in errorLogs" 
               :key="log.id"
               class="px-2.5 py-2 bg-bg border border-border rounded-md shadow-sm text-[11px]"
             >
               <div class="flex items-center justify-between mb-1">
                 <div class="text-[10px] text-text2">{{ formatTime(log.createTime) }}</div>
               </div>
               <div class="flex items-center gap-2 mb-1 flex-wrap">
                 <span class="px-1.5 py-[2px] rounded uppercase font-bold text-[9px]" :class="log.level === 'CRITICAL' ? 'bg-red/20 text-red' : 'bg-yellow/20 text-yellow'">{{ log.level }}</span>
                 <span class="text-text2 opacity-80" v-if="log.appName">[{{ log.appName }}]</span>
                 <span class="text-accent ml-1 font-semibold border-b border-accent/30 border-dashed" title="匹配规则名称">{{ log.matchedRuleName }}</span>
                 <span v-if="log.matchedKeyword" class="text-[10px] text-cyan bg-cyan/10 px-1 rounded ml-1" title="命中关键字">匹配: {{ log.matchedKeyword }}</span>
               </div>
               <div class="font-mono text-[10px] break-all max-h-[120px] overflow-y-auto text-text/90 select-text bg-surface2 p-1.5 rounded mt-1.5 border border-border/50">
                 {{ log.logContent }}
               </div>
             </div>
          </div>
        </div>

      </div>
    </div>
  </el-drawer>

  <RuleConfigModal v-model:visible="showRuleModal" :agent-id="agent?.id" />
  <FilterRuleConfigModal v-model:visible="showFilterRuleModal" :agent-id="agent?.id" />
  <LogMonitorConfigModal v-model:visible="showLogMonitorModal" :agent-id="agent?.id" />
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { Close, Monitor, Cpu, DataBoard, Document, Setting, Tools, Warning, FolderOpened } from '@element-plus/icons-vue'
import { useMonitorStore } from '../../stores/monitorStore'
import RuleConfigModal from '../alert/RuleConfigModal.vue'
import FilterRuleConfigModal from '../alert/FilterRuleConfigModal.vue'
import LogMonitorConfigModal from '../alert/LogMonitorConfigModal.vue'

const store = useMonitorStore()

const drawerVisible = computed({
  get: () => !!store.activeAgentId,
  set: (val) => {
    if (!val) store.setActiveAgent(null)
  }
})

const agent = computed(() => store.activeAgent)

const showRuleModal = ref(false)
const showFilterRuleModal = ref(false)
const showLogMonitorModal = ref(false)

const loading = ref(false)
const deepDetails = ref(null)
const errorLogs = ref([])

watch(agent, async (newVal) => {
  if (newVal?.id) {
    loadDetails(newVal.id)
  } else {
    deepDetails.value = null
    errorLogs.value = []
  }
})

async function loadDetails(agentId) {
  loading.value = true
  try {
    const [detailResp, logsResp] = await Promise.all([
        fetch(`/api/dashboard/agents/${encodeURIComponent(agentId)}`),
        fetch(`/api/dashboard/agents/${encodeURIComponent(agentId)}/logs?limit=50`)
    ])
    
    if (detailResp.ok) {
        const detailsJson = await detailResp.json()
        deepDetails.value = detailsJson.data || {}
    }
    
    if (logsResp.ok) {
        const logsJson = await logsResp.json()
        errorLogs.value = logsJson.data || []
    }
  } catch(e) {
    console.error('Failed to load deep details', e)
  } finally {
    loading.value = false
  }
}

function openRuleModal() {
  showRuleModal.value = true
}

function openFilterRuleModal() {
  showFilterRuleModal.value = true
}

function openLogMonitorModal() {
  showLogMonitorModal.value = true
}

function parseMB(str) {
  if (!str) return 0
  let v = parseFloat(str)
  return isNaN(v) ? 0 : v
}

function formatSize(mb) {
  if (mb >= 1024) return (mb / 1024).toFixed(1) + ' GB'
  return mb + ' MB'
}

const partitionList = computed(() => {
  const parts = deepDetails.value?.parts || []
  if (parts.length > 0) {
    return parts.map(p => {
        const total = parseMB(p.capacity)
        const avail = parseMB(p.availableCapacity)
        const used = total - avail
        const percent = total > 0 ? Math.round((used / total) * 100) : 0
        return {
            mountPoint: p.mountPoint,
            total,
            used,
            percent
        }
    })
  }
  
  // Fallback to basic agent summary stats
  const basicDisks = agent.value?.systemInfo?.disks || []
  return basicDisks.map(d => {
      const used = d.totalSpace - d.usableSpace
      return {
          mountPoint: d.mountPoint,
          total: d.totalSpace,
          used: used,
          percent: getDiskUsagePercent(d)
      }
  })
})

function getProcessList() {
    const deepProcs = deepDetails.value?.processStatusList || []
    if (deepProcs.length > 0) {
        return deepProcs.map(p => ({
            name: p.processName,
            status: p.status,
            running: p.status === '正常'
        }))
    }
    
    // Fallback
    return agent.value?.processes || []
}

function formatBytes(bytes) {
  if (bytes === 0 || !bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]
}

function getDiskUsagePercent(disk) {
  if (!disk.totalSpace) return 0
  return Math.round(((disk.totalSpace - disk.usableSpace) / disk.totalSpace) * 100)
}

function getDiskColor(percent) {
  if (percent > 90) return 'bg-red'
  if (percent > 75) return 'bg-yellow'
  return 'bg-cyan'
}

function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  return d.toLocaleString('zh-CN', { hour12: false })
}
</script>

<style>
/* Override Element Plus base styles for drawer to match dark theme without touching el vars everywhere */
.custom-drawer .el-drawer__body {
  padding: 0 !important;
  background-color: transparent !important;
}
.el-overlay {
  background-color: rgba(0, 0, 0, 0.6) !important;
  backdrop-filter: blur(2px);
}
</style>
