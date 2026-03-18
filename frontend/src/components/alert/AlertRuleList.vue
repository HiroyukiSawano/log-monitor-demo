<template>
  <div class="mb-4 max-h-[250px] overflow-y-auto pr-2 scrollbar-thin">
    <div v-if="rules.length" class="flex flex-col gap-2">
      <div
        v-for="rule in rules"
        :key="rule.id"
        class="flex items-start gap-3 rounded-md border border-border bg-bg p-3 shadow-sm"
      >
        <span
          class="mt-0.5 shrink-0 rounded border px-1.5 py-[2px] text-[9px] font-bold uppercase"
          :class="rule.alertLevel === 'CRITICAL'
            ? 'border-red/20 bg-red/10 text-red'
            : 'border-yellow/20 bg-yellow/10 text-yellow'"
        >
          {{ rule.alertLevel || 'WARNING' }}
        </span>

        <div class="min-w-0 flex-1">
          <div class="mb-1 flex items-center gap-2 text-xs font-bold text-text">
            <span class="truncate">{{ rule.ruleName }}</span>
            <span
              v-if="rule.agentId === '*'"
              class="rounded bg-surface2 px-1 text-[9px] font-normal text-text2"
            >
              全局
            </span>
          </div>
          <div class="text-[10px] leading-tight text-text2">
            {{ rule.formattedConditions }}
            · 冷却时间: {{ rule.cooldownSec }}秒<span v-if="rule.cooldownSec > 0 && rule.cooldownSec < 60"> ⚠️</span>
            · {{ rule.enabled ? '✅ 已启用' : '❌ 已禁用' }}
          </div>
        </div>

        <button
          v-if="rule.agentId !== '*'"
          @click="$emit('delete', rule.id)"
          class="shrink-0 p-1 text-text2 transition-colors hover:text-red"
          title="删除"
        >
          <el-icon><Delete /></el-icon>
        </button>
      </div>
    </div>

    <div v-else class="rounded-md border border-dashed border-border py-6 text-center text-xs text-text2">
      未配置任何规则，请点击下方创建。
    </div>
  </div>
</template>

<script setup>
import { Delete } from '@element-plus/icons-vue'

defineProps({
  rules: {
    type: Array,
    default: () => []
  }
})

defineEmits(['delete'])
</script>
