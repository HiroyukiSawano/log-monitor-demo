<template>
  <div class="rounded-lg border border-border bg-surface2 p-4 shadow-sm">
    <div class="mb-3 flex items-center justify-between text-text">
      <div class="text-xs font-bold uppercase tracking-wide text-text2">新规则定义</div>
      <button @click="$emit('cancel')" class="text-text2 hover:text-text"><el-icon><Close /></el-icon></button>
    </div>

    <div class="mb-4 flex items-start gap-1.5 rounded-md border border-blue/30 bg-blue/10 p-2 text-[10px] text-blue">
      <span class="text-base leading-none">⏱</span>
      <span>检测间隔说明：告警检测频率取决于 Agent 上报间隔（当前约 60 秒）。持续时间和冷却时间的实际精度受此间隔约束。</span>
    </div>

    <div class="mb-4 flex gap-3">
      <div class="flex-1">
        <label class="mb-1 block text-[10px] text-text2">规则名称</label>
        <input
          v-model="draft.ruleName"
          type="text"
          placeholder="例如：高CPU且低内存"
          class="w-full rounded border border-border bg-bg px-2 py-1.5 text-xs text-text outline-none focus:border-accent"
        >
      </div>
      <div class="w-24">
        <label class="mb-1 block text-[10px] text-text2">告警级别</label>
        <select
          v-model="draft.alertLevel"
          class="w-full rounded border border-border bg-bg px-2 py-1.5 text-xs text-text outline-none focus:border-accent"
        >
          <option value="CRITICAL">CRITICAL</option>
          <option value="WARNING">WARNING</option>
        </select>
      </div>
      <div class="w-32">
        <label class="mb-1 block text-[10px] text-text2">冷却(秒) — 建议 ≥ 60</label>
        <input
          v-model.number="draft.cooldownSec"
          type="number"
          min="1"
          class="w-full rounded border border-border bg-bg px-2 py-1.5 text-xs text-text outline-none focus:border-accent"
        >
      </div>
    </div>

    <div class="mb-4 rounded-lg border border-border bg-bg p-3">
      <div class="mb-2 flex items-center justify-between border-b border-border/50 pb-2">
        <span class="text-[10px] font-bold text-text">条件组配置</span>
        <button
          @click="toggleTopLogic"
          class="rounded border px-2 py-0.5 text-[10px] font-bold transition-colors"
          :class="draft.topLogic === 'AND' ? 'border-accent bg-accent/10 text-accent' : 'border-border bg-surface text-text2'"
        >
          组间逻辑：{{ draft.topLogic === 'AND' ? '条件全满足 (AND)' : '任一满足 (OR)' }}
        </button>
      </div>

      <div class="flex flex-col gap-2">
        <div
          v-for="(group, gIdx) in draft.groups"
          :key="group.id"
          class="relative rounded border border-accent/30 bg-surface2 p-2"
        >
          <div class="mb-2 flex items-center justify-between">
            <span class="text-[10px] font-bold text-accent">条件组 {{ gIdx + 1 }}</span>
            <div class="flex items-center gap-2">
              <button
                @click="toggleGroupLogic(group)"
                class="rounded border px-1.5 py-0.5 text-[9px] transition-colors"
                :class="group.logic === 'AND' ? 'border-cyan bg-cyan/10 text-cyan' : 'border-border bg-surface text-text2'"
              >
                组内逻辑：{{ group.logic === 'AND' ? '全满足(AND)' : '任一满足(OR)' }}
              </button>
              <button @click="addGroupItem(group)" class="px-1 text-[10px] text-cyan hover:text-[#00aaaa]">+ 加条件</button>
              <button @click="removeGroup(gIdx)" class="ml-1 text-red hover:text-[#ff8a7a]"><el-icon><Close /></el-icon></button>
            </div>
          </div>

          <div class="relative flex flex-col gap-1.5 align-middle">
            <template v-for="(item, cIdx) in group.items" :key="item.id">
              <RuleConditionItemRow
                :item="item"
                :cached-partitions="cachedPartitions"
                :metric-options="metricOptions"
                :op-options="opOptions"
                @patch="patchItem(group, cIdx, $event)"
                @remove="removeGroupItem(group, cIdx)"
              />

              <div v-if="cIdx < group.items.length - 1" class="z-0 -my-1.5 ml-6 flex items-center gap-2">
                <div class="h-5 w-px bg-border"></div>
                <span class="flex items-center justify-center rounded border border-border bg-surface px-1.5 py-[1px] text-[9px] font-bold leading-none text-cyan opacity-80 shadow-sm">
                  {{ group.logic === 'AND' ? 'AND' : 'OR' }}
                </span>
              </div>
            </template>
          </div>

          <div
            v-if="gIdx < draft.groups.length - 1"
            class="absolute -bottom-3 left-1/2 z-10 -translate-x-1/2 rounded-full border border-border bg-surface px-2 text-[9px] font-bold text-accent"
          >
            {{ draft.topLogic === 'AND' ? 'AND' : 'OR' }}
          </div>
        </div>
      </div>

      <button
        @click="addGroup"
        class="mt-3 w-full rounded border border-dashed border-cyan py-1 text-[10px] font-semibold text-cyan transition-colors hover:bg-cyan/5"
      >
        + 添加条件组
      </button>
    </div>

    <div class="mt-2 flex justify-end gap-2">
      <button
        @click="$emit('cancel')"
        class="rounded border border-border bg-surface px-4 py-1.5 text-xs text-text2 transition-colors hover:text-text"
      >
        取消
      </button>
      <button
        @click="$emit('submit')"
        :disabled="saving"
        class="rounded bg-accent px-4 py-1.5 text-xs font-semibold text-white shadow transition-colors hover:bg-opacity-90"
      >
        {{ saving ? '保存中...' : '保存规则' }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { onUpdated } from 'vue'
import { Close } from '@element-plus/icons-vue'
import RuleConditionItemRow from './RuleConditionItemRow.vue'

const props = defineProps({
  draft: {
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
  },
  saving: {
    type: Boolean,
    default: false
  },
  createGroupDraft: {
    type: Function,
    required: true
  },
  createItemDraft: {
    type: Function,
    required: true
  }
})

defineEmits(['cancel', 'submit'])

if (import.meta.env.DEV) {
  onUpdated(() => {
    console.count('[render] RuleConditionEditor')
  })
}

function toggleTopLogic() {
  props.draft.topLogic = props.draft.topLogic === 'AND' ? 'OR' : 'AND'
}

function toggleGroupLogic(group) {
  group.logic = group.logic === 'AND' ? 'OR' : 'AND'
}

function addGroup() {
  props.draft.groups.push(props.createGroupDraft())
}

function removeGroup(idx) {
  props.draft.groups.splice(idx, 1)
}

function addGroupItem(group) {
  group.items.push(props.createItemDraft())
}

function removeGroupItem(group, idx) {
  group.items.splice(idx, 1)
  if (group.items.length === 0) {
    const groupIndex = props.draft.groups.findIndex((candidate) => candidate.id === group.id)
    if (groupIndex > -1) removeGroup(groupIndex)
  }
}

function patchItem(group, idx, patch) {
  Object.assign(group.items[idx], patch)
}
</script>
