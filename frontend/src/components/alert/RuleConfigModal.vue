<template>
  <el-dialog
    v-model="visible"
    :title="`告警规则 — ${agentId || '全局'}`"
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
          <span 
            class="text-[9px] font-bold px-1.5 py-[2px] rounded uppercase shrink-0 mt-0.5"
            :class="rule.alertLevel === 'CRITICAL' ? 'bg-red/10 text-red border border-red/20' : 'bg-yellow/10 text-yellow border border-yellow/20'"
          >
            {{ rule.alertLevel || 'WARNING' }}
          </span>
          
          <div class="flex-1 min-w-0">
            <div class="text-xs font-bold text-text flex items-center gap-2 mb-1">
              <span class="truncate">{{ rule.ruleName }}</span>
              <span v-if="rule.agentId === '*'" class="text-[9px] font-normal text-text2 bg-surface2 px-1 rounded">全局</span>
            </div>
            <div class="text-[10px] text-text2 leading-tight">
              {{ formatConditions(rule.conditions) }}
              · 冷却时间: {{ rule.cooldownSec }}秒 
              · {{ rule.enabled ? '✅ 已启用' : '❌ 已禁用' }}
            </div>
          </div>

          <button v-if="rule.agentId !== '*'" @click="deleteRule(rule.id)" class="text-text2 hover:text-red transition-colors shrink-0 p-1">
            <el-icon><Delete /></el-icon>
          </button>
        </div>
        
        <div v-if="!rules.length" class="text-center py-6 text-text2 text-xs border border-dashed border-border rounded-md">
          未配置任何规则，请点击下方创建。
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
          <div class="text-xs font-bold uppercase tracking-wide text-text2">新规则定义</div>
          <button @click="showAddForm = false" class="text-text2 hover:text-text"><el-icon><Close /></el-icon></button>
        </div>
        
        <div class="flex gap-3 mb-4">
          <div class="flex-1">
            <label class="block text-[10px] text-text2 mb-1">规则名称</label>
            <input v-model="form.ruleName" type="text" placeholder="例如：高CPU且低内存" class="w-full bg-bg border border-border text-xs px-2 py-1.5 rounded focus:border-accent outline-none text-text">
          </div>
          <div class="w-24">
            <label class="block text-[10px] text-text2 mb-1">告警级别</label>
            <select v-model="form.alertLevel" class="w-full bg-bg border border-border text-xs px-2 py-1.5 rounded focus:border-accent outline-none text-text">
              <option value="CRITICAL">CRITICAL</option>
              <option value="WARNING">WARNING</option>
            </select>
          </div>
          <div class="w-24">
            <label class="block text-[10px] text-text2 mb-1">冷却时间 (秒)</label>
            <input v-model.number="form.cooldownSec" type="number" min="0" class="w-full bg-bg border border-border text-xs px-2 py-1.5 rounded focus:border-accent outline-none text-text">
          </div>
        </div>

        <!-- Groups Builder -->
        <div class="border border-border rounded-lg bg-bg p-3 mb-4">
          <div class="flex items-center justify-between mb-2 pb-2 border-b border-border/50">
            <span class="text-[10px] font-bold text-text">条件组配置</span>
            <button @click="toggleTopLogic" class="text-[10px] px-2 py-0.5 rounded border transition-colors font-bold" :class="form.topLogic === 'AND' ? 'bg-accent/10 border-accent text-accent' : 'bg-surface border-border text-text2'">
              组间逻辑：{{ form.topLogic === 'AND' ? '条件全满足 (AND)' : '任一满足 (OR)' }}
            </button>
          </div>

          <div class="flex flex-col gap-2">
            <div v-for="(group, gIdx) in form.groups" :key="gIdx" class="border border-accent/30 rounded p-2 bg-surface2 relative">
              <div class="flex items-center justify-between mb-2">
                <span class="text-[10px] font-bold text-accent">条件组 {{ gIdx + 1 }}</span>
                <div class="flex items-center gap-2">
                  <button @click="toggleGroupLogic(group)" class="text-[9px] px-1.5 py-0.5 rounded border transition-colors" :class="group.logic === 'AND' ? 'bg-cyan/10 border-cyan text-cyan' : 'bg-surface border-border text-text2'">
                    组内逻辑：{{ group.logic === 'AND' ? '全满足(AND)' : '任一满足(OR)' }}
                  </button>
                  <button @click="addGroupItem(group)" class="text-[10px] text-cyan hover:text-[#00aaaa] px-1">+ 加条件</button>
                  <button @click="removeGroup(gIdx)" class="text-red hover:text-[#ff8a7a] ml-1"><el-icon><Close /></el-icon></button>
                </div>
              </div>

              <!-- Conditions in Group -->
              <div class="flex flex-col gap-1.5">
                <div v-for="(item, cIdx) in group.items" :key="cIdx" class="flex flex-wrap items-center gap-1 bg-surface border border-border rounded p-1.5 shadow-sm relative">
                  <!-- Metric -->
                  <select v-model="item.metricType" @change="onMetricChange(item)" class="bg-bg border border-border text-[10px] px-1.5 py-0.5 rounded min-w-[100px] outline-none text-text">
                    <option v-for="opt in metricOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                  </select>
                  
                  <!-- Target (e.g. Partition Name) -->
                  <select v-if="item.metricType === 'DISK_PARTITION' && cachedPartitions.length" v-model="item.targetName" class="bg-bg border border-border text-[10px] px-1.5 py-0.5 rounded outline-none w-20 text-text">
                    <option v-for="p in cachedPartitions" :key="p" :value="p">{{ p }}</option>
                  </select>
                  <input v-else-if="!['AGENT_OFFLINE', 'PROCESS_ABNORMAL'].includes(item.metricType)" v-model="item.targetName" placeholder="目标对象" class="bg-bg border border-border text-[10px] px-1.5 py-0.5 rounded outline-none w-16 text-text">

                  <!-- Operator & Threshold -->
                  <template v-if="!['PROCESS_ABNORMAL', 'AGENT_OFFLINE'].includes(item.metricType)">
                    <select v-model="item.operator" class="bg-bg border border-border text-[10px] px-1.5 py-0.5 rounded outline-none w-14 text-text">
                      <option v-for="op in opOptions" :key="op.value" :value="op.value">{{ op.label }}</option>
                    </select>
                    <input v-model.number="item.threshold" type="number" class="bg-bg border border-border text-[10px] px-1.5 py-0.5 rounded outline-none w-14 text-text">
                  </template>

                  <!-- Duration -->
                  <div class="flex items-center gap-1 border-l border-border pl-1 ml-1" title="持续时间(0=即时触发)">
                    <el-icon class="text-text2 text-[10px]"><Timer /></el-icon>
                    <input v-model.number="item.durationSec" type="number" placeholder="秒数" class="bg-bg border border-border text-[10px] px-1.5 py-0.5 rounded outline-none w-12 text-center text-text">
                  </div>

                  <button @click="removeGroupItem(group, cIdx)" class="text-red hover:text-[#ff8a7a] ml-auto px-1 opacity-50 hover:opacity-100"><el-icon size="12"><Close /></el-icon></button>
                </div>
              </div>

              <!-- Group Divider visual -->
              <div v-if="gIdx < form.groups.length - 1" class="absolute -bottom-3 left-1/2 -translate-x-1/2 z-10 px-2 bg-surface text-[9px] font-bold rounded-full border border-border text-accent">
                {{ form.topLogic === 'AND' ? 'AND' : 'OR' }}
              </div>
            </div>
          </div>
          <button @click="addGroup" class="mt-3 w-full border border-dashed border-cyan text-cyan hover:bg-cyan/5 rounded py-1 text-[10px] font-semibold transition-colors">
            + 添加条件组
          </button>
        </div>

        <div class="flex justify-end gap-2 mt-2">
          <button @click="showAddForm = false" class="px-4 py-1.5 rounded bg-surface border border-border text-text2 hover:text-text text-xs transition-colors">取消</button>
          <button @click="submitRule" :disabled="saving" class="px-4 py-1.5 rounded bg-accent text-white hover:bg-opacity-90 font-semibold text-xs transition-colors shadow">
            {{ saving ? '保存中...' : '保存规则' }}
          </button>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { Delete, Plus, Close, Timer } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const props = defineProps(['agentId'])
const visible = defineModel('visible', { type: Boolean })

const loading = ref(false)
const saving = ref(false)
const rules = ref([])

const showAddForm = ref(false)
const cachedPartitions = ref([])

const metricOptions = [
  { value: 'CPU_USAGE', label: 'CPU使用率(%)', numeric: true },
  { value: 'RAM_USAGE', label: '内存使用率(%)', numeric: true },
  { value: 'DISK_USAGE', label: '总磁盘使用率(%)', numeric: true },
  { value: 'DISK_PARTITION', label: '分区可用空间(MB)', numeric: true },
  { value: 'PROCESS_ABNORMAL', label: '进程异常', numeric: false },
  { value: 'AGENT_OFFLINE', label: '节点离线', numeric: false },
  { value: 'LOG_HIT_CRITICAL', label: 'CRITICAL命中数', numeric: true },
  { value: 'LOG_HIT_TOTAL', label: '日志命中总数', numeric: true }
]

const opOptions = [
  { value: 'GT', label: '>' },
  { value: 'GTE', label: '>=' },
  { value: 'LT', label: '<' },
  { value: 'LTE', label: '<=' },
  { value: 'EQ', label: '=' }
]

const defaultItem = () => ({ metricType: 'CPU_USAGE', operator: 'GT', threshold: 80, targetName: '', durationSec: 0 })
const defaultGroup = () => ({ logic: 'OR', items: [defaultItem()] })

const form = ref({
  ruleName: '',
  alertLevel: 'CRITICAL',
  cooldownSec: 300,
  topLogic: 'AND',
  groups: []
})

watch(visible, async (newVal) => {
  if (newVal && props.agentId) {
    showAddForm.value = false
    await fetchRules()
    await prefetchPartitions()
  }
})

async function fetchRules() {
  loading.value = true
  try {
    const res = await fetch(`/api/alert/rules/applicable?agentId=${encodeURIComponent(props.agentId)}`)
    if (res.ok) {
      const json = await res.json()
      rules.value = json.data || []
    }
  } catch (e) {
    ElMessage.error('Failed to fetch rules')
  } finally {
    loading.value = false
  }
}

async function prefetchPartitions() {
  try {
    const res = await fetch(`/api/dashboard/agents/${encodeURIComponent(props.agentId)}`)
    if (res.ok) {
      const json = await res.json()
      cachedPartitions.value = (json.data?.parts || []).map(p => p.mountPoint)
    }
  } catch (e) {
    cachedPartitions.value = []
  }
}

function openAddForm() {
  form.value = {
    ruleName: '',
    alertLevel: 'CRITICAL',
    cooldownSec: 300,
    topLogic: 'AND',
    groups: [defaultGroup()]
  }
  showAddForm.value = true
}

// Logic Toggles
function toggleTopLogic() {
  form.value.topLogic = form.value.topLogic === 'AND' ? 'OR' : 'AND'
}
function toggleGroupLogic(grp) {
  grp.logic = grp.logic === 'AND' ? 'OR' : 'AND'
}

// Add/Remove
function addGroup() {
  form.value.groups.push(defaultGroup())
}
function removeGroup(idx) {
  form.value.groups.splice(idx, 1)
}
function addGroupItem(grp) {
  grp.items.push(defaultItem())
}
function removeGroupItem(grp, idx) {
  grp.items.splice(idx, 1)
  if (grp.items.length === 0) {
    const gIdx = form.value.groups.indexOf(grp)
    if (gIdx > -1) removeGroup(gIdx)
  }
}

function onMetricChange(item) {
  const opt = metricOptions.find(o => o.value === item.metricType)
  if (!opt?.numeric) {
    item.operator = 'EQ'
    item.threshold = 0
  }
  if (item.metricType === 'DISK_PARTITION' && cachedPartitions.value.length) {
    item.targetName = cachedPartitions.value[0]
  } else {
    item.targetName = ''
  }
}

async function submitRule() {
  if (!form.value.ruleName) {
    ElMessage.warning('请输入规则名称')
    return
  }
  
  // Clean empty groups
  const validGroups = form.value.groups.filter(g => g.items.length > 0)
  if (!validGroups.length) {
    ElMessage.warning('至少需要添加一个判断条件')
    return
  }

  saving.value = true
  try {
    const expression = {
        logic: form.value.topLogic,
        groups: validGroups
    }
    
    const payload = {
      agentId: props.agentId,
      ruleName: form.value.ruleName,
      alertLevel: form.value.alertLevel,
      cooldownSec: form.value.cooldownSec,
      enabled: true,
      conditions: JSON.stringify(expression)
    }
    
    const res = await fetch(`/api/alert/rules`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    
    if (res.ok) {
      ElMessage.success('规则创建成功')
      showAddForm.value = false
      await fetchRules()
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
    const res = await fetch(`/api/alert/rules/${id}`, { method: 'DELETE' })
    if (res.ok) {
      ElMessage.success('Rule deleted')
      rules.value = rules.value.filter(r => r.id !== id)
    }
  } catch(e) {
    ElMessage.error(e.message)
  }
}

function formatConditions(jsonStr) {
  if (!jsonStr) return '无条件配置'
  try {
    const expr = JSON.parse(jsonStr)
    const tl = expr.logic === 'AND' ? ' AND ' : ' OR '
    const groups = expr.groups || []
    
    const labels = {
        CPU_USAGE: 'CPU', RAM_USAGE: '内存', DISK_USAGE: '磁盘', DISK_PARTITION: '分区', PROCESS_ABNORMAL: '进程异常', AGENT_OFFLINE: '离线', LOG_HIT_CRITICAL: 'CRITICAL命中', LOG_HIT_TOTAL: '命中总数'
    }
    const ops = { GT: '>', GTE: '>=', LT: '<', LTE: '<=', EQ: '=' }
    
    const gStrs = groups.map(g => {
        const gl = g.logic === 'AND' ? ' && ' : ' || '
        const items = (g.items || []).map(c => {
            const m = labels[c.metricType] || c.metricType
            const tgt = c.targetName ? `[${c.targetName}]` : ''
            const op = ops[c.operator] || c.operator
            const dur = c.durationSec ? ` (持续${c.durationSec}s)` : ''
            
            if (['PROCESS_ABNORMAL', 'AGENT_OFFLINE'].includes(c.metricType)) return `${m}${tgt}${dur}`
            
            const unit = c.metricType.includes('USAGE') ? '%' : ''
            return `${m}${tgt}${op}${c.threshold}${unit}${dur}`
        })
        return items.length > 1 ? `(${items.join(gl)})` : items[0]
    })
    
    return gStrs.join(tl)
  } catch(e) {
    return '无效规则格式'
  }
}
</script>

<style>
.custom-dialog .el-dialog__header {
  margin-right: 0;
  border-bottom: 1px solid var(--border);
  padding-bottom: 16px;
}
.custom-dialog .el-dialog__title {
  color: var(--text);
  font-weight: 600;
  font-size: 14px;
}
.custom-dialog .el-dialog__body {
  padding: 20px;
}
</style>
