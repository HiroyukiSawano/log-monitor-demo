<template>
  <div class="flex flex-wrap items-center gap-1 rounded border border-border bg-surface p-1.5 shadow-sm">
    <select
      :value="item.metricType"
      @change="handleMetricChange"
      class="min-w-[100px] rounded border border-border bg-bg px-1.5 py-0.5 text-[10px] text-text outline-none"
    >
      <option v-for="opt in metricOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
    </select>

    <select
      v-if="item.metricType === 'DISK_PARTITION' && cachedPartitions.length"
      :value="item.targetName"
      @change="emitPatch({ targetName: $event.target.value })"
      class="w-20 rounded border border-border bg-bg px-1.5 py-0.5 text-[10px] text-text outline-none"
    >
      <option v-for="partition in cachedPartitions" :key="partition" :value="partition">{{ partition }}</option>
    </select>

    <input
      v-else-if="['DISK_PARTITION', 'PROCESS_ABNORMAL'].includes(item.metricType)"
      :value="item.targetName"
      @input="emitPatch({ targetName: $event.target.value })"
      :placeholder="item.metricType === 'PROCESS_ABNORMAL' ? '进程名(可选)' : '目标对象'"
      class="w-24 rounded border border-border bg-bg px-1.5 py-0.5 text-[10px] text-text outline-none"
    >

    <template v-if="!['PROCESS_ABNORMAL', 'AGENT_OFFLINE'].includes(item.metricType)">
      <select
        :value="item.operator"
        @change="emitPatch({ operator: $event.target.value })"
        class="w-14 rounded border border-border bg-bg px-1.5 py-0.5 text-[10px] text-text outline-none"
      >
        <option v-for="op in opOptions" :key="op.value" :value="op.value">{{ op.label }}</option>
      </select>
      <input
        :value="item.threshold"
        @input="emitPatch({ threshold: Number($event.target.value) })"
        type="number"
        class="w-14 rounded border border-border bg-bg px-1.5 py-0.5 text-[10px] text-text outline-none"
      >
    </template>

    <div class="ml-1 flex items-center gap-1 border-l border-border pl-1" :title="durationHint">
      <el-icon class="text-[10px] text-text2"><Timer /></el-icon>
      <input
        :value="item.durationSec"
        @input="emitPatch({ durationSec: Number($event.target.value) })"
        type="number"
        min="0"
        :placeholder="durationPlaceholder"
        class="w-full max-w-[80px] rounded border border-border bg-bg px-1.5 py-0.5 text-center text-[10px] text-text outline-none"
      >
    </div>

    <button
      @click="$emit('remove')"
      class="ml-auto px-1 text-red opacity-50 transition-opacity hover:opacity-100"
    >
      <el-icon size="12"><Close /></el-icon>
    </button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Close, Timer } from '@element-plus/icons-vue'

const props = defineProps({
  item: {
    type: Object,
    required: true
  },
  cachedPartitions: {
    type: Array,
    default: () => []
  },
  metricOptions: {
    type: Array,
    default: () => []
  },
  opOptions: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['patch', 'remove'])

const durationPlaceholder = computed(() => (
  ['LOG_HIT_CRITICAL', 'LOG_HIT_TOTAL'].includes(props.item.metricType) ? '窗口(秒)' : '持续(秒)≥60'
))

const durationHint = computed(() => (
  ['LOG_HIT_CRITICAL', 'LOG_HIT_TOTAL'].includes(props.item.metricType)
    ? '统计时间窗口(秒)，在此窗口内累计命中数。\n默认300秒(5分钟)。'
    : '持续时间(秒)，0=瞬时判定。\n注意：检测间隔取决于Agent上报频率(约60s)，\n小于60s的值实际精度等于上报间隔。'
))

function emitPatch(patch) {
  emit('patch', patch)
}

function handleMetricChange(event) {
  const metricType = event.target.value
  const opt = props.metricOptions.find((candidate) => candidate.value === metricType)
  const patch = { metricType }

  if (!opt?.numeric) {
    patch.operator = 'EQ'
    patch.threshold = 0
  }

  if (['LOG_HIT_CRITICAL', 'LOG_HIT_TOTAL'].includes(metricType) && !props.item.durationSec) {
    patch.durationSec = 300
  }

  if (metricType === 'DISK_PARTITION') {
    patch.targetName = props.cachedPartitions[0] || ''
  } else if (metricType !== 'PROCESS_ABNORMAL') {
    patch.targetName = ''
  }

  emitPatch(patch)
}
</script>
