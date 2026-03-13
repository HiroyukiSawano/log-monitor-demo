<template>
  <el-dialog
    v-model="internalVisible"
    :title="`系统告警 - ${agentId}`"
    width="680px"
    class="custom-dialog"
    destroy-on-close
    append-to-body
  >
    <div class="flex flex-col gap-4 max-h-[60vh] overflow-y-auto pr-2 scrollbar-thin">
      
      <div v-if="activeAlerts.length > 0">
        <div class="flex items-center justify-between mb-3">
          <h3 class="text-sm font-semibold flex items-center gap-1.5" :class="hasCriticals ? 'text-red' : 'text-yellow'">
            <el-icon><Warning /></el-icon> 未处理告警 ({{ activeAlerts.length }})
          </h3>
          <button 
            @click="handleAckAll"
            class="text-xs px-2.5 py-1 border border-border bg-surface text-text2 rounded hover:border-accent hover:text-text transition-colors"
          >
            全部确认
          </button>
        </div>
        
        <div class="flex flex-col gap-2">
          <div 
            v-for="alert in activeAlerts" 
            :key="alert.id"
            class="bg-surface2 border border-border rounded-lg p-3 hover:border-accent transition-all relative group shadow-sm"
          >
            <div class="flex items-start justify-between mb-2">
              <div class="flex items-center gap-2">
                <span 
                  class="text-[11px] font-bold px-1.5 py-[2px] rounded-[4px]"
                  :class="alert.alertLevel === 'CRITICAL' ? 'bg-red/20 text-red' : 'bg-yellow/20 text-yellow'"
                >
                  {{ alert.alertLevel }}
                </span>
              </div>
              <span class="text-text2 text-[10px]">{{ formatTime(alert.createTime) }}</span>
            </div>
            
            <div class="text-[12px] text-text mb-3 leading-relaxed">
              {{ alert.message || alert.ruleName || '未知告警内容' }}
            </div>
            
            <div class="flex justify-end">
              <button 
                @click="handleAck(alert.id)"
                class="text-[11px] px-2.5 py-1 border border-border bg-surface text-text2 rounded hover:bg-green hover:border-green hover:text-white transition-colors flex items-center gap-1"
              >
                <el-icon :size="12"><Check /></el-icon> 确认
              </button>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="text-center py-8 text-text2 text-sm border border-border rounded-lg bg-surface2/50">
        <el-icon :size="32" class="mb-2 text-green"><Select /></el-icon>
        <p>暂无未处理的告警 ✨</p>
      </div>

    </div>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import { Warning, Check, Select } from '@element-plus/icons-vue'
import { useMonitorStore } from '../../stores/monitorStore'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  agentId: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:visible'])

const store = useMonitorStore()

const internalVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

const activeAlerts = computed(() => {
  if (!props.agentId) return []
  return store.alerts.filter(a => a.agentId === props.agentId)
})

const hasCriticals = computed(() => activeAlerts.value.some(a => a.alertLevel === 'CRITICAL'))

function handleAck(id) {
  store.ackAlert(id)
}

function handleAckAll() {
  const ids = activeAlerts.value.map(a => a.id)
  ids.forEach(id => store.ackAlert(id))
}

function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  return d.toLocaleString('zh-CN', { hour12: false })
}
</script>

<style>
.custom-dialog {
  background-color: var(--surface) !important;
  border: 1px solid var(--border) !important;
  border-radius: 12px;
}
.custom-dialog .el-dialog__title {
  color: var(--text) !important;
  font-weight: 600;
}
.custom-dialog .el-dialog__header {
  border-bottom: 1px solid var(--border);
  margin-right: 0;
  padding-bottom: 15px;
}
.custom-dialog .el-dialog__body {
  padding-top: 20px;
}
</style>
