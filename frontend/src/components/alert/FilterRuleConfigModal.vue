<template>
  <el-dialog
    v-model="visible"
    :title="`日志过滤规则 — ${agentId || '全局'}`"
    width="680px"
    class="!bg-surface !border !border-border rounded-xl custom-dialog"
    destroy-on-close
  >
    <div v-loading="loading">
      <!-- Existing Rules List -->
      <div class="flex flex-col gap-2 mb-4 max-h-[250px] overflow-y-auto pr-2 scrollbar-thin">
        <div 
          v-for="rule in rules" 
          :key="rule.id"
          class="flex items-start gap-3 p-3 bg-bg border border-border rounded-md shadow-sm"
        >
          <!-- Rule Type Tag -->
          <span 
            class="text-[9px] font-bold px-1.5 py-[2px] rounded uppercase shrink-0 mt-0.5"
            :class="getRuleTypeClass(rule.ruleType)"
          >
            {{ formatRuleType(rule.ruleType) }}
          </span>
          
          <div class="flex-1 min-w-0">
            <div class="text-xs font-bold text-text flex items-center gap-2 mb-1">
              <span class="truncate">{{ rule.ruleName }}</span>
              <span v-if="rule.agentId === '*'" class="text-[9px] font-normal text-text2 bg-surface2 px-1 rounded">全局</span>
            </div>
            <div class="text-[10px] text-text2 leading-tight flex items-center gap-2">
              <span class="bg-surface border border-border px-1 rounded text-accent">{{ formatMatchMode(rule.matchMode) }}</span>
              <span class="font-mono bg-bg/50 px-1 rounded text-text/80">{{ rule.keyword }}</span>
            </div>
          </div>

          <button v-if="rule.agentId !== '*'" @click="deleteRule(rule.id)" class="text-text2 hover:text-red transition-colors shrink-0 p-1" title="删除">
            <el-icon><Delete /></el-icon>
          </button>
        </div>
        
        <div v-if="!rules.length" class="text-center py-6 text-text2 text-xs border border-dashed border-border rounded-md">
          当前实体暂无专属过滤规则（全局规则仍然生效）。
        </div>
      </div>

      <!-- Add Rule Toggle -->
      <button 
        v-if="!showAddForm" 
        @click="openAddForm"
        class="w-full py-2 bg-surface2 border border-dashed border-border text-accent rounded-lg text-xs hover:border-accent hover:bg-accent/5 transition-all"
      >
        <el-icon class="mr-1"><Plus /></el-icon> 创建新规则
      </button>

      <!-- New Rule Form -->
      <div v-else class="bg-surface2 p-4 rounded-lg border border-border shadow-sm">
        <div class="flex items-center justify-between mb-3 text-text">
          <div class="text-xs font-bold font-mono tracking-wide text-text2 uppercase">定义过滤规则</div>
          <button @click="showAddForm = false" class="text-text2 hover:text-text"><el-icon><Close /></el-icon></button>
        </div>
        
        <div class="flex flex-col gap-3">
          <div class="flex gap-3">
            <div class="flex-1">
              <label class="block text-[10px] text-text2 mb-1">规则名称</label>
              <input v-model="form.ruleName" type="text" placeholder="例如：排除心跳日志" class="w-full bg-bg border border-border text-xs px-2 py-1.5 rounded focus:border-accent outline-none text-text">
            </div>
          </div>
          
          <div class="flex gap-3">
            <div class="flex-1">
              <label class="block text-[10px] text-text2 mb-1">类型 (Action)</label>
              <select v-model="form.ruleType" class="w-full bg-bg border border-border text-xs px-2 py-1.5 rounded focus:border-accent outline-none text-text">
                <option value="CRITICAL">升级为 Critical (最高优, 告警)</option>
                <option value="EXCLUDE">直接截断丢弃 (Exclude)</option>
                <option value="BASIC">标记命中 (普通记录)</option>
              </select>
            </div>
            <div class="flex-1">
              <label class="block text-[10px] text-text2 mb-1">匹配模式</label>
              <select v-model="form.matchMode" class="w-full bg-bg border border-border text-xs px-2 py-1.5 rounded focus:border-accent outline-none text-text">
                <option value="CONTAINS">包含 (Contains)</option>
                <option value="EQUALS">全等 (Equals)</option>
                <option value="REGEX">正则 (Regex)</option>
                <option value="STARTS_WITH">前缀 (Starts With)</option>
                <option value="ENDS_WITH">后缀 (Ends With)</option>
              </select>
            </div>
          </div>

          <div class="flex gap-3">
            <div class="flex-1">
              <label class="block text-[10px] text-text2 mb-1">关键字 / 表达式</label>
              <input v-model="form.keyword" type="text" placeholder="匹配的文本" class="w-full bg-bg border border-border font-mono text-xs px-2 py-1.5 rounded focus:border-accent outline-none text-text">
            </div>
          </div>
        </div>

        <div class="flex justify-end gap-2 mt-4">
          <button @click="showAddForm = false" class="px-4 py-1.5 rounded bg-surface border border-border text-text2 hover:text-text text-xs transition-colors">取消</button>
          <button @click="submitRule" :disabled="saving" class="px-4 py-1.5 rounded bg-accent text-white hover:bg-opacity-90 font-semibold text-xs transition-colors shadow">
            {{ saving ? '保存中...' : '提交规则' }}
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

const props = defineProps(['agentId'])
const visible = defineModel('visible', { type: Boolean })

const loading = ref(false)
const saving = ref(false)
const rules = ref([])

const showAddForm = ref(false)

const form = ref({
  ruleName: '',
  ruleType: 'CRITICAL',
  matchMode: 'CONTAINS',
  keyword: ''
})

watch(visible, async (newVal) => {
  if (newVal && props.agentId !== undefined) {
    showAddForm.value = false
    await fetchRules()
  }
})

async function fetchRules() {
  loading.value = true
  try {
    const aid = props.agentId || '*'
    const res = await fetch(`/api/rules?agentId=${encodeURIComponent(aid)}`)
    if (res.ok) {
      const json = await res.json()
      rules.value = json.data || []
    }
  } catch (e) {
    ElMessage.error('查询日志过滤规则失败')
  } finally {
    loading.value = false
  }
}

function openAddForm() {
  form.value = {
    ruleName: '',
    ruleType: 'CRITICAL',
    matchMode: 'CONTAINS',
    keyword: ''
  }
  showAddForm.value = true
}

async function submitRule() {
  if (!form.value.ruleName || !form.value.keyword) {
    ElMessage.warning('规则名称和关键字不能为空')
    return
  }
  
  saving.value = true
  try {
    const payload = {
      agentId: props.agentId || '*',
      ruleName: form.value.ruleName,
      ruleType: form.value.ruleType,
      matchMode: form.value.matchMode,
      keyword: form.value.keyword
    }
    
    // Add rule
    const res = await fetch(`/api/rules`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    
    if (res.ok) {
      ElMessage.success('日志过滤规则创建成功')
      showAddForm.value = false
      await fetchRules()
      
      // Auto-trigger rebuild on backend if needed, although backend handles it internally
      await fetch(`/api/rules/rebuild/${encodeURIComponent(payload.agentId)}`, { method: 'POST' })
    } else {
      ElMessage.error('规则创建失败')
    }
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function deleteRule(id) {
  try {
    const res = await fetch(`/api/rules/${id}`, { method: 'DELETE' })
    if (res.ok) {
      ElMessage.success('规则已删除')
      rules.value = rules.value.filter(r => r.id !== id)
      
      const aid = props.agentId || '*'
      await fetch(`/api/rules/rebuild/${encodeURIComponent(aid)}`, { method: 'POST' })
    }
  } catch(e) {
    ElMessage.error(e.message)
  }
}

function getRuleTypeClass(type) {
  if (type === 'CRITICAL') return 'bg-red/10 text-red border border-red/20'
  if (type === 'EXCLUDE') return 'bg-cyan/10 text-cyan border border-cyan/20 opacity-80'
  return 'bg-surface border border-border text-text2'
}

function formatRuleType(t) {
  const map = { CRITICAL: 'CRITICAL', EXCLUDE: '截断忽略', BASIC: '普通记录' }
  return map[t] || t
}

function formatMatchMode(m) {
  const map = { CONTAINS: '包含', EQUALS: '全等', REGEX: '正则', STARTS_WITH: '前缀', ENDS_WITH: '后缀' }
  return map[m] || m
}
</script>

<style scoped>
/* Inherit custom-dialog styling if needed */
</style>
