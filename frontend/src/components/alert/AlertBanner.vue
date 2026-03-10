<template>
  <div 
    v-show="alerts.length > 0"
    class="alert-banner bg-gradient-to-r from-red/5 to-yellow/5 border-b border-red/20 max-h-[120px] overflow-y-auto"
  >
    <div class="sticky top-0 bg-surface/90 backdrop-blur-sm z-10 px-6 py-1.5 flex items-center gap-2 border-b border-border/50">
      <div class="text-xs font-semibold text-red flex-1 flex items-center gap-2">
        <el-icon><Warning /></el-icon> 当前活动告警 ({{ alerts.length }})
      </div>
      <button 
        @click="store.ackAllAlerts"
        class="text-[10px] px-2.5 py-0.5 border border-border bg-surface text-text2 rounded hover:border-accent hover:text-text transition-colors"
      >
        全部确认
      </button>
    </div>
    
    <div 
      v-for="alert in alerts" 
      :key="alert.id"
      class="flex items-center gap-2.5 px-6 py-1 text-[11px] border-t border-border/30 first:border-0 hover:bg-white/5 transition-colors"
    >
      <span 
        class="text-[9px] font-bold px-1.5 py-[1px] rounded-[3px]"
        :class="alert.level === 'CRITICAL' ? 'bg-red/20 text-red' : 'bg-yellow/20 text-yellow'"
      >
        {{ alert.level }}
      </span>
      <span class="text-text font-mono opacity-80">[{{ alert.agentId }}]</span>
      <span class="flex-1 text-text">{{ alert.message || alert.ruleName || '告警' }}</span>
      <span class="text-text2 text-[10px]">{{ formatTime(alert.createTime) }}</span>
      <button 
        @click="store.ackAlert(alert.id)"
        class="text-[10px] px-2 py-0.5 border border-border bg-transparent text-text2 rounded hover:bg-green hover:border-green hover:text-white transition-colors ml-2 shadow-sm"
      >
        确认 ✓
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useMonitorStore } from '../../stores/monitorStore'
import { Warning } from '@element-plus/icons-vue'

const store = useMonitorStore()
const alerts = computed(() => store.alerts)

function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  return d.toLocaleTimeString('zh-CN', { hour12: false })
}
</script>
