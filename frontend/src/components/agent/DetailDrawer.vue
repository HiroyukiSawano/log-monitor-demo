<template>
  <el-drawer
    v-model="drawerVisible"
    :title="agent?.systemInfo?.hostname || 'Agent Details'"
    size="500px"
    class="!bg-surface !border-l !border-border text-text"
    :with-header="false"
  >
    <div v-if="agent" class="h-full flex flex-col pt-0">
      <!-- Header Area -->
      <div class="px-5 py-4 border-b border-border bg-surface2 flex items-center justify-between">
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
            <el-icon><Monitor /></el-icon> Hardware Specs
          </h3>
          <div class="grid grid-cols-2 gap-2">
            <div class="col-span-2 bg-bg border border-border rounded-md p-2.5">
              <div class="text-[10px] text-text2 mb-1">OS Version</div>
              <div class="text-xs font-semibold">{{ agent.systemInfo?.osName || 'Unknown' }}</div>
            </div>
            <div class="bg-bg border border-border rounded-md p-2.5">
              <div class="text-[10px] text-text2 mb-1">Total Memory</div>
              <div class="text-xs font-semibold">{{ formatBytes(agent.systemInfo?.totalMemory) }}</div>
            </div>
            <div class="bg-bg border border-border rounded-md p-2.5">
              <div class="text-[10px] text-text2 mb-1">Logical Cores</div>
              <div class="text-xs font-semibold">{{ agent.systemInfo?.availableProcessors || '-' }}</div>
            </div>
          </div>
        </div>

        <!-- Processes List -->
        <div class="mb-6" v-if="agent.processes?.length">
          <h3 class="text-xs font-semibold uppercase tracking-wide text-text2 mb-3 flex items-center gap-1.5">
            <el-icon><Cpu /></el-icon> Top Processes
          </h3>
          <div class="flex flex-col gap-1.5">
            <div 
              v-for="proc in agent.processes" 
              :key="proc.pid"
              class="flex items-center gap-3 px-3 py-2 bg-bg border border-border rounded-md hover:border-accent transition-colors cursor-pointer"
            >
              <div class="flex-1 text-xs font-medium truncate">{{ proc.name }}</div>
              <div 
                class="text-[11px] font-semibold px-2 py-0.5 rounded-sm"
                :class="proc.running ? 'bg-green/10 text-green' : 'bg-red/10 text-red'"
              >
                {{ proc.running ? 'RUNNING' : 'STOPPED' }}
              </div>
            </div>
          </div>
        </div>

        <!-- Disks List -->
        <div class="mb-6" v-if="agent.systemInfo?.disks?.length">
          <h3 class="text-xs font-semibold uppercase tracking-wide text-text2 mb-3 flex items-center gap-1.5">
            <el-icon><DataBoard /></el-icon> Disks & Partitions
          </h3>
          <div class="flex flex-col gap-2 relative">
            <div 
              v-for="disk in agent.systemInfo.disks" 
              :key="disk.mountPoint"
              class="flex items-center gap-3 px-3 py-2 bg-bg border border-border rounded-md"
            >
              <div class="text-[13px] font-bold min-w-[30px] max-w-[120px] text-cyan truncate" :title="disk.mountPoint">
                {{ disk.mountPoint }}
              </div>
              <div class="flex-1">
                <div class="h-2 bg-surface2 rounded w-full overflow-hidden">
                  <div 
                    class="h-full rounded transition-all duration-500"
                    :class="getDiskColor(getDiskUsagePercent(disk))"
                    :style="{ width: `${getDiskUsagePercent(disk)}%` }"
                  ></div>
                </div>
              </div>
              <div class="text-[10px] text-text2 whitespace-nowrap w-[70px] text-right">
                {{ formatBytes(disk.totalSpace - disk.usableSpace) }} / {{ formatBytes(disk.totalSpace) }}
              </div>
            </div>
          </div>
        </div>
        
        <!-- Log Timeline -->
        <div class="mb-2" v-if="agent.logs?.length">
          <h3 class="text-xs font-semibold uppercase tracking-wide text-text2 mb-3 flex items-center gap-1.5">
            <el-icon><Document /></el-icon> Recent Logs
          </h3>
          <div class="flex flex-col gap-2 border border-border p-2 rounded-lg bg-bg/50">
            <div 
              v-for="log in agent.logs" 
              :key="log.timestamp"
              class="px-2.5 py-2 bg-bg border-l-2 text-[11px] shadow-sm rounded-r-md"
              :class="getLogLevelBorder(log.level)"
            >
              <div class="text-[10px] text-text2 mb-0.5">{{ formatTime(log.timestamp) }}</div>
              <div class="font-mono text-[10px] break-all max-h-[100px] overflow-y-auto text-text/90 select-text">
                <span class="font-bold mr-1" :class="getLogLevelColor(log.level)">{{ log.level }}</span>
                {{ log.content }}
              </div>
            </div>
          </div>
        </div>

      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed } from 'vue'
import { Close, Monitor, Cpu, DataBoard, Document } from '@element-plus/icons-vue'
import { useMonitorStore } from '../../stores/monitorStore'

const store = useMonitorStore()

const drawerVisible = computed({
  get: () => !!store.activeAgentId,
  set: (val) => {
    if (!val) store.setActiveAgent(null)
  }
})

const agent = computed(() => store.activeAgent)

function formatBytes(bytes) {
  if (bytes === 0 || !bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]
}

function getDiskUsagePercent(disk) {
  if (!disk.totalSpace) return 0
  return ((disk.totalSpace - disk.usableSpace) / disk.totalSpace) * 100
}

function getDiskColor(percent) {
  if (percent > 90) return 'bg-red'
  if (percent > 75) return 'bg-yellow'
  return 'bg-cyan'
}

function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  return d.toLocaleTimeString('zh-CN', { hour12: false })
}

function getLogLevelBorder(level) {
  if (level === 'CRITICAL' || level === 'ERROR') return 'border-red bg-red/5'
  if (level === 'UNKNOWN_ERROR' || level === 'WARN') return 'border-yellow bg-yellow/5'
  return 'border-border'
}

function getLogLevelColor(level) {
  if (level === 'CRITICAL' || level === 'ERROR') return 'text-red'
  if (level === 'UNKNOWN_ERROR' || level === 'WARN') return 'text-yellow'
  return 'text-accent'
}
</script>

<style>
/* Override Element Plus base styles for drawer to match dark theme without touching el vars everywhere */
.el-drawer__body {
  padding: 0 !important;
}
.el-overlay {
  background-color: rgba(0, 0, 0, 0.6) !important;
  backdrop-filter: blur(2px);
}
</style>
