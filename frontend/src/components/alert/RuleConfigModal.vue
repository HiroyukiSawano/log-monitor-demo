<template>
  <el-dialog
    v-model="visible"
    title="Configure Alert Rules"
    width="560px"
    class="!bg-surface !border !border-border rounded-xl custom-dialog"
    destroy-on-close
  >
    <div class="bg-bg text-text text-sm p-4 rounded-lg outline outline-1 outline-border mb-4">
      Rules are defined per-agent. Currently selected agent:
      <span class="text-cyan font-bold mx-1">{{ agent?.systemInfo?.hostname || store.activeAgentId }}</span>
    </div>

    <div class="flex flex-col gap-2 mb-4">
      <div 
        v-for="rule in rules" 
        :key="rule.id"
        class="flex items-center gap-3 px-3 py-2 bg-bg border border-border rounded-md"
      >
        <el-tag size="small" :type="rule.metricName === 'PROCESS_STATUS' ? 'info' : 'primary'" effect="dark">
          {{ rule.metricName }}
        </el-tag>
        
        <div class="flex-1 text-xs">
          <span class="text-text2">Condition: </span>
          <span class="font-bold border-b border-dashed border-text/50">
            {{ formatCondition(rule) }}
          </span>
        </div>

        <el-tag size="small" type="danger" effect="plain" v-if="rule.durationSecs">
          &gt; {{ rule.durationSecs }}s
        </el-tag>

        <button @click="deleteRule(rule.id)" class="text-text2 hover:text-red transition-colors ml-2">
          <el-icon><Delete /></el-icon>
        </button>
      </div>
      
      <div v-if="!rules.length" class="text-center py-6 text-text2 text-xs border border-dashed border-border rounded-md">
        No rules configured for this agent.
      </div>
    </div>

    <!-- New Rule Form -->
    <div class="bg-surface2 p-4 rounded-lg border border-border">
      <div class="text-xs font-bold mb-3 text-text2 uppercase tracking-wides">Add New Rule</div>
      <el-form :model="form" label-position="top" size="small">
        <div class="grid grid-cols-2 gap-4">
          <el-form-item label="Metric">
            <el-select v-model="form.metricName" class="w-full">
              <el-option label="CPU Usage" value="CPU_USAGE" />
              <el-option label="Memory Usage" value="MEMORY_USAGE" />
              <el-option label="Disk Usage" value="DISK_USAGE" />
              <el-option label="Process Status" value="PROCESS_STATUS" />
            </el-select>
          </el-form-item>
          <el-form-item label="Operator">
            <el-select v-model="form.operator" class="w-full">
              <el-option label="Greater Than (>)" value="GREATER_THAN" />
              <el-option label="Less Than (<)" value="LESS_THAN" />
              <el-option label="Equals (=)" value="EQUALS" />
              <el-option label="Not Equals (!=)" value="NOT_EQUALS" />
            </el-select>
          </el-form-item>
        </div>
        <div class="grid grid-cols-2 gap-4">
          <el-form-item label="Threshold (Value)">
            <el-input v-model="form.thresholdStr" placeholder="e.g. 90 or nginx" />
          </el-form-item>
          <el-form-item label="Duration (Seconds)">
            <el-input-number v-model="form.durationSecs" :min="0" :max="3600" class="!w-full" />
          </el-form-item>
        </div>
      </el-form>
      <div class="flex justify-end mt-2">
        <el-button type="primary" size="small" @click="submitRule" :loading="saving">Add Rule</el-button>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="visible = false">Close</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { Delete } from '@element-plus/icons-vue'
import { useMonitorStore } from '../../stores/monitorStore'
import { ElMessage } from 'element-plus'

const visible = defineModel('visible', { type: Boolean })

const store = useMonitorStore()
const agent = computed(() => store.activeAgent)

const rules = ref([])
const saving = ref(false)

const form = ref({
  metricName: 'CPU_USAGE',
  operator: 'GREATER_THAN',
  thresholdStr: '90',
  durationSecs: 60
})

watch(visible, async (newVal) => {
  if (newVal && store.activeAgentId) {
    await fetchRules(store.activeAgentId)
  }
})

async function fetchRules(agentId) {
  try {
    const res = await fetch(`/api/alerts/rules/${agentId}`)
    if (res.ok) {
      rules.value = await res.json()
    }
  } catch (e) {
    console.error('Failed to fetch rules:', e)
  }
}

async function submitRule() {
  if (!store.activeAgentId) return
  saving.value = true
  try {
    const payload = {
      agentId: store.activeAgentId,
      metricName: form.value.metricName,
      operator: form.value.operator,
      thresholdStr: form.value.thresholdStr,
      durationSecs: form.value.durationSecs
    }
    
    const res = await fetch(`/api/alerts/rules`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    
    if (res.ok) {
      ElMessage.success('Rule added successfully')
      await fetchRules(store.activeAgentId) // refresh
    } else {
      ElMessage.error('Failed to add rule')
    }
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function deleteRule(id) {
  try {
    const res = await fetch(`/api/alerts/rules/${id}`, {
      method: 'DELETE'
    })
    if (res.ok) {
      ElMessage.success('Rule deleted')
      rules.value = rules.value.filter(r => r.id !== id)
    }
  } catch(e) {
    ElMessage.error(e.message)
  }
}

function formatCondition(rule) {
  const ops = {
    'GREATER_THAN': '>',
    'LESS_THAN': '<',
    'EQUALS': '==',
    'NOT_EQUALS': '!='
  }
  return `${ops[rule.operator] || rule.operator} ${rule.thresholdStr}`
}
</script>

<style>
/* ElDialog darkMode overrides */
.custom-dialog .el-dialog__header {
  margin-right: 0;
  border-bottom: 1px solid var(--border);
  padding-bottom: 16px;
}
.custom-dialog .el-dialog__title {
  color: var(--text);
  font-weight: 600;
  font-size: 16px;
}
.custom-dialog .el-dialog__body {
  padding: 20px;
}
.custom-dialog .el-form-item__label {
  color: var(--text2);
}
.custom-dialog .el-input__wrapper,
.custom-dialog .el-select__wrapper {
  background-color: var(--bg);
  box-shadow: 0 0 0 1px var(--border) inset;
}
</style>
