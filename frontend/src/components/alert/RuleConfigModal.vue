<template>
  <el-dialog
    v-model="visible"
    :title="`告警规则 — ${agentId || '全局'}`"
    width="680px"
    class="!bg-surface !border !border-border rounded-xl custom-dialog"
    destroy-on-close
  >
    <div v-loading="loading">
      <AlertRuleList :rules="rules" @delete="deleteRule" />

      <button
        v-if="!showAddForm"
        @click="openAddForm"
        class="w-full rounded-lg border border-dashed border-border bg-surface2 py-2 text-xs text-accent transition-all hover:border-accent hover:bg-accent/5"
      >
        <el-icon class="mr-1"><Plus /></el-icon> 创建新规则
      </button>

      <RuleConditionEditor
        v-else
        :draft="form"
        :cached-partitions="cachedPartitions"
        :metric-options="metricOptions"
        :op-options="opOptions"
        :saving="saving"
        :create-group-draft="createGroupDraft"
        :create-item-draft="createItemDraft"
        @cancel="showAddForm = false"
        @submit="submitRule"
      />
    </div>
  </el-dialog>
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useMonitorStore } from '../../stores/monitorStore'
import AlertRuleList from './AlertRuleList.vue'
import RuleConditionEditor from './RuleConditionEditor.vue'

const props = defineProps(['agentId'])
const visible = defineModel('visible', { type: Boolean })
const store = useMonitorStore()

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

let draftId = 0
const form = ref(createEmptyDraft())

watch(visible, async (newVal, oldVal) => {
  if (newVal) {
    store.pauseAutoRefresh()
    showAddForm.value = false
    await Promise.all([fetchRules(), prefetchPartitions()])
    return
  }

  if (oldVal) {
    showAddForm.value = false
    store.resumeAutoRefresh()
  }
})

onBeforeUnmount(() => {
  if (visible.value) {
    store.resumeAutoRefresh()
  }
})

function createEmptyDraft() {
  return {
    ruleName: '',
    alertLevel: 'CRITICAL',
    cooldownSec: 60,
    topLogic: 'AND',
    groups: []
  }
}

function nextDraftId() {
  draftId += 1
  return `draft-${draftId}`
}

function createItemDraft(overrides = {}) {
  return {
    id: nextDraftId(),
    metricType: 'CPU_USAGE',
    operator: 'GT',
    threshold: 80,
    targetName: '',
    durationSec: 0,
    ...overrides
  }
}

function createGroupDraft(overrides = {}) {
  const sourceItems = overrides.items?.length ? overrides.items : [createItemDraft()]
  return {
    id: nextDraftId(),
    logic: overrides.logic || 'OR',
    items: sourceItems.map((item) => createItemDraft(item))
  }
}

async function fetchRules() {
  loading.value = true
  try {
    const res = await fetch(`/api/alert/rules/applicable?agentId=${encodeURIComponent(props.agentId)}`)
    if (res.ok) {
      const json = await res.json()
      rules.value = (json.data || []).map((rule) => ({
        ...rule,
        formattedConditions: formatConditions(rule.conditions)
      }))
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
      cachedPartitions.value = (json.data?.parts || []).map((part) => part.mountPoint)
    }
  } catch (e) {
    cachedPartitions.value = []
  }
}

function openAddForm() {
  form.value = createEmptyDraft()
  form.value.groups = [createGroupDraft()]
  showAddForm.value = true
}

async function submitRule() {
  if (!form.value.ruleName) {
    ElMessage.warning('请输入规则名称')
    return
  }

  const validGroups = sanitizeGroups(form.value.groups)
  if (!validGroups.length) {
    ElMessage.warning('至少需要添加一个判断条件')
    return
  }

  const REPORT_INTERVAL = 60
  const cooldownVal = form.value.cooldownSec
  const smallDurations = []

  validGroups.forEach((group) => {
    group.items.forEach((item) => {
      if (item.durationSec > 0 && item.durationSec < REPORT_INTERVAL) {
        smallDurations.push(item.durationSec)
      }
    })
  })

  try {
    if (cooldownVal > 0 && cooldownVal < REPORT_INTERVAL) {
      await ElMessageBox.confirm(
        `冷却时间 ${cooldownVal}s 小于 Agent 上报间隔(${REPORT_INTERVAL}s)，实际冷却效果约等于上报间隔。\n是否继续？`,
        '提示',
        { confirmButtonText: '继续保存', cancelButtonText: '取消', type: 'warning' }
      )
    }

    if (smallDurations.length > 0) {
      const distinctDurations = [...new Set(smallDurations)].join('s, ') + 's'
      await ElMessageBox.confirm(
        `持续时间 ${distinctDurations} 小于 Agent 上报间隔(${REPORT_INTERVAL}s)，实际精度受上报间隔约束。\n是否继续？`,
        '提示',
        { confirmButtonText: '继续保存', cancelButtonText: '取消', type: 'warning' }
      )
    }
  } catch (_) {
    return
  }

  saving.value = true
  try {
    const payload = {
      agentId: props.agentId,
      ruleName: form.value.ruleName,
      alertLevel: form.value.alertLevel,
      cooldownSec: form.value.cooldownSec,
      enabled: true,
      conditions: JSON.stringify({
        logic: form.value.topLogic,
        groups: validGroups
      })
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
      rules.value = rules.value.filter((rule) => rule.id !== id)
    }
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function sanitizeGroups(groups) {
  return groups
    .filter((group) => group.items.length > 0)
    .map(({ id, items, ...group }) => ({
      ...group,
      items: items.map(({ id: itemId, ...item }) => item)
    }))
}

function formatConditions(jsonStr) {
  if (!jsonStr) return '无条件配置'
  try {
    const expr = JSON.parse(jsonStr)
    const topLogic = expr.logic === 'AND' ? ' AND ' : ' OR '
    const groups = expr.groups || []

    const labels = {
      CPU_USAGE: 'CPU',
      RAM_USAGE: '内存',
      DISK_USAGE: '磁盘',
      DISK_PARTITION: '分区',
      PROCESS_ABNORMAL: '进程异常',
      AGENT_OFFLINE: '离线',
      LOG_HIT_CRITICAL: 'CRITICAL命中',
      LOG_HIT_TOTAL: '命中总数'
    }
    const ops = { GT: '>', GTE: '>=', LT: '<', LTE: '<=', EQ: '=' }

    const groupStrings = groups.map((group) => {
      const groupLogic = group.logic === 'AND' ? ' && ' : ' || '
      const items = (group.items || []).map((item) => {
        const metric = labels[item.metricType] || item.metricType
        const target = item.targetName ? `[${item.targetName}]` : ''
        const operator = ops[item.operator] || item.operator
        const durationIcon = item.durationSec > 0 && item.durationSec < 60 ? ' ⚠️' : ''
        const duration = item.durationSec ? ` (持续≥${item.durationSec}s${durationIcon})` : ''

        if (['PROCESS_ABNORMAL', 'AGENT_OFFLINE'].includes(item.metricType)) {
          return `${metric}${target}${duration}`
        }

        const unit = item.metricType.includes('USAGE') ? '%' : ''
        return `${metric}${target}${operator}${item.threshold}${unit}${duration}`
      })
      return items.length > 1 ? `(${items.join(groupLogic)})` : items[0]
    })

    return groupStrings.join(topLogic)
  } catch (e) {
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
