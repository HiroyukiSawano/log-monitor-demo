<template>
  <el-dialog
    v-model="visible"
    :title="`日志监听管理 — ${agentId || '未知'}`"
    width="680px"
    class="!bg-surface !border !border-border rounded-xl custom-dialog"
    destroy-on-close
  >
    <div v-loading="loading">
      <!-- Existing Rules List -->
      <div class="flex flex-col gap-2 mb-4 max-h-[250px] overflow-y-auto pr-2 scrollbar-thin">
        <div 
          v-for="(rule, index) in rules" 
          :key="index"
          class="flex items-start gap-3 p-3 bg-bg border border-border rounded-md shadow-sm"
        >
          <!-- Rule Type Tag -->
          <span 
            class="text-[9px] font-bold px-1.5 py-[2px] rounded uppercase shrink-0 mt-0.5"
            :class="rule.isRunning ? 'bg-cyan/10 text-cyan border border-cyan/20 opacity-80' : 'bg-red/10 text-red border border-red/20'"
          >
            {{ rule.isRunning ? '运行中' : '已停止' }}
          </span>
          
          <div class="flex-1 min-w-0">
            <div class="text-xs font-bold text-text flex items-center gap-2 mb-1">
              <span class="truncate">{{ rule.appName || '—' }}</span>
              <span class="text-[10px] text-text2 font-normal">({{ rule.fileCount }} 个文件)</span>
            </div>
            <div class="text-[10px] text-text2 leading-tight flex items-center gap-2">
              <span class="font-mono bg-bg/50 px-1 rounded text-cyan/80">{{ rule.path }}</span>
            </div>
          </div>

          <button @click="deleteRule(rule.path)" class="text-text2 hover:text-red transition-colors shrink-0 p-1" title="移除监听">
            <el-icon><Delete /></el-icon>
          </button>
        </div>
        
        <div v-if="!rules.length" class="text-center py-6 text-text2 text-xs border border-dashed border-border rounded-md">
          当前实体暂无日志监听路径，点击下方添加。
        </div>
      </div>

      <!-- Add Rule Toggle -->
      <button 
        v-if="!showAddForm" 
        @click="openAddForm"
        class="w-full py-2 bg-surface2 border border-dashed border-border text-accent rounded-lg text-xs hover:border-accent hover:bg-accent/5 transition-all"
      >
        <el-icon class="mr-1"><Plus /></el-icon> 添加日志监听
      </button>

      <!-- New Rule Form -->
      <div v-else class="bg-surface2 p-4 rounded-lg border border-border shadow-sm">
        <div class="flex items-center justify-between mb-3 text-text">
          <div class="text-xs font-bold font-mono tracking-wide text-text2 uppercase">添加监听路径</div>
          <button @click="showAddForm = false" class="text-text2 hover:text-text"><el-icon><Close /></el-icon></button>
        </div>
        
        <div class="flex flex-col gap-3">
          <div class="flex gap-3">
            <div class="flex-1">
              <label class="block text-[10px] text-text2 mb-1">应用名称</label>
              <input v-model="form.appName" type="text" placeholder="例如：受理系统" class="w-full bg-bg border border-border text-xs px-2 py-1.5 rounded focus:border-accent outline-none text-text">
            </div>
          </div>
          
          <div class="flex gap-3">
            <div class="flex-1">
              <label class="block text-[10px] text-text2 mb-1">监听路径 (支持通配符)</label>
              <input v-model="form.path" type="text" placeholder="例如：D:\logs\*.log" class="w-full bg-bg border border-border font-mono text-xs px-2 py-1.5 rounded focus:border-accent outline-none text-text">
            </div>
          </div>
        </div>

        <div class="flex justify-end gap-2 mt-4">
          <button @click="showAddForm = false" class="px-4 py-1.5 rounded bg-surface border border-border text-text2 hover:text-text text-xs transition-colors">取消</button>
          <button @click="submitRule" :disabled="saving" class="px-4 py-1.5 rounded bg-accent text-white hover:bg-opacity-90 font-semibold text-xs transition-colors shadow">
            {{ saving ? '添加中...' : '添加监听' }}
          </button>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { Delete, Plus, Close } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { buildAgentCommand, sendAgentCommand } from '../../utils/agentCommand'

const props = defineProps(['agentId'])
const visible = defineModel('visible', { type: Boolean })

const loading = ref(false)
const saving = ref(false)
const rules = ref([])

const showAddForm = ref(false)

const form = ref({
  appName: '',
  path: ''
})

watch(visible, async (newVal) => {
  if (newVal && props.agentId) {
    showAddForm.value = false
    await fetchRules()
  }
})

async function fetchRules() {
  if (!props.agentId) return
  
  loading.value = true
  try {
    const agentResp = await sendAgentCommand(
      props.agentId,
      buildAgentCommand('LogTail/list', [], 'logtail-list')
    )
    rules.value = agentResp.monitors || []
  } catch (e) {
    ElMessage.error('查询日志监听列表失败: ' + e.message)
    rules.value = []
  } finally {
    loading.value = false
  }
}

function openAddForm() {
  form.value = {
    appName: '',
    path: ''
  }
  showAddForm.value = true
}

async function submitRule() {
  const path = form.value.path?.trim()
  if (!path) {
    ElMessage.warning('监听路径不能为空')
    return
  }
  
  saving.value = true
  const appName = form.value.appName || '未知应用'
  try {
    await sendAgentCommand(
      props.agentId,
      buildAgentCommand('LogTail/add', [path, appName, ''], 'logtail-add')
    )
    ElMessage.success('日志监听添加成功')
    showAddForm.value = false
    await fetchRules()
  } catch (e) {
    ElMessage.error('添加失败: ' + e.message)
  } finally {
    saving.value = false
  }
}

async function deleteRule(path) {
  try {
    await sendAgentCommand(
      props.agentId,
      buildAgentCommand('LogTail/remove', [path], 'logtail-remove')
    )
    ElMessage.success('移除监听成功')
    await fetchRules()
  } catch(e) {
    ElMessage.error('移除失败: ' + e.message)
  }
}
</script>

<style scoped>
/* Inherit custom-dialog styling if needed */
</style>
