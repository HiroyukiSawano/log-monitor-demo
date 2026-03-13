<template>
  <el-dialog
    v-model="internalVisible"
    :title="`命中日志 - ${agentId}`"
    width="720px"
    class="custom-dialog"
    destroy-on-close
    append-to-body
  >
    <div class="max-h-[60vh] overflow-y-auto pr-2 scrollbar-thin" v-loading="loadingLogs">
      <div v-if="errorLogs.length === 0 && !loadingLogs" class="text-center py-8 text-text2 text-sm border border-border rounded-lg bg-surface2/50">
        暂无命中日志记录
      </div>
      
      <div class="flex flex-col gap-2" v-else>
        <div 
          v-for="log in errorLogs" 
          :key="log.id"
          class="px-3 py-2.5 bg-surface2 border border-border rounded-md shadow-sm text-xs"
        >
          <div class="flex items-center justify-between mb-1.5">
            <div class="text-[11px] text-text2">{{ formatTime(log.createTime) }}</div>
          </div>
          <div class="flex items-center gap-2 mb-2 flex-wrap">
            <span class="px-1.5 py-[2px] rounded uppercase font-bold text-[10px]" :class="log.level === 'CRITICAL' ? 'bg-red/20 text-red' : 'bg-yellow/20 text-yellow'">{{ log.level }}</span>
            <span class="text-text2 opacity-80" v-if="log.appName">[{{ log.appName }}]</span>
            <span class="text-accent ml-1 font-semibold border-b border-accent/30 border-dashed" title="匹配规则名称">{{ log.matchedRuleName }}</span>
            <span v-if="log.matchedKeyword" class="text-[11px] text-cyan bg-cyan/10 px-1.5 rounded ml-1" title="命中关键字">匹配: {{ log.matchedKeyword }}</span>
          </div>
          <div class="font-mono text-[11px] break-all max-h-[160px] overflow-y-auto text-text/90 select-text bg-bg p-2 rounded mt-1.5 border border-border/50">
            {{ log.logContent }}
          </div>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

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

const internalVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

const errorLogs = ref([])
const loadingLogs = ref(false)

watch(() => props.visible, (newVal) => {
  if (newVal && props.agentId) {
    loadLogs(props.agentId)
  }
})

async function loadLogs(id) {
  loadingLogs.value = true
  try {
    const res = await fetch(`/api/dashboard/agents/${encodeURIComponent(id)}/logs?limit=50`)
    if (res.ok) {
      const json = await res.json()
      errorLogs.value = json.data || []
    }
  } catch (e) {
    console.error('Failed to load hit logs', e)
  } finally {
    loadingLogs.value = false
  }
}

function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  return d.toLocaleString('zh-CN', { hour12: false })
}
</script>
