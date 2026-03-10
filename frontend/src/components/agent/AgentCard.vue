<template>
  <div 
    class="agent-card bg-surface border border-border rounded-xl overflow-hidden cursor-pointer transition-all duration-200 relative group"
    :class="{ 
      'border-accent ring-2 ring-accent/30': active,
      'opacity-65 grayscale-[50%]': !agent.online,
      'hover:border-accent hover:shadow-[0_4px_20px_rgba(108,92,231,0.15)] hover:-translate-y-0.5': true
    }"
    @click="$emit('click', agent.id)"
  >
    <!-- Header -->
    <div class="px-4 py-3 flex items-center gap-3">
      <div 
        class="w-2.5 h-2.5 rounded-full shrink-0"
        :class="agent.online ? 'bg-green shadow-[0_0_8px_var(--green)] animate-pulse' : 'bg-red'"
      ></div>
      <div class="font-semibold text-sm flex-1 truncate">
        {{ agent.systemInfo?.hostname || agent.id }}
      </div>
      <div v-if="alertCount > 0" class="bg-red text-white text-[10px] font-bold px-1.5 py-0.5 rounded-full min-w-[20px] text-center">
        {{ alertCount }}
      </div>
    </div>
    
    <div class="px-4 text-[11px] text-text2 mb-2 truncate">
      {{ agent.systemInfo?.osName || '未知操作系统' }}
    </div>

    <!-- Metrics -->
    <div class="px-4 pb-3 flex flex-col gap-2">
      <!-- CPU -->
      <div class="flex items-center gap-2 text-[11px]">
        <div class="w-9 text-text2 font-semibold shrink-0">处理器</div>
        <div class="flex-1 h-1.5 bg-bg rounded-full overflow-hidden">
          <div 
            class="h-full rounded-full transition-all duration-500"
            :class="getColorClass(cpuUsage)"
            :style="{ width: `${cpuUsage}%` }"
          ></div>
        </div>
        <div class="w-10 text-right font-semibold text-[10px]">{{ cpuUsage.toFixed(1) }}%</div>
      </div>
      <!-- Memory -->
      <div class="flex items-center gap-2 text-[11px]">
        <div class="w-9 text-text2 font-semibold shrink-0">内存</div>
        <div class="flex-1 h-1.5 bg-bg rounded-full overflow-hidden">
          <div 
            class="h-full rounded-full transition-all duration-500"
            :class="getColorClass(memUsage)"
            :style="{ width: `${memUsage}%` }"
          ></div>
        </div>
        <div class="w-10 text-right font-semibold text-[10px]">{{ memUsage.toFixed(1) }}%</div>
      </div>
      <!-- Disk -->
      <div class="flex items-center gap-2 text-[11px]">
        <div class="w-9 text-text2 font-semibold shrink-0">磁盘</div>
        <div class="flex-1 h-1.5 bg-bg rounded-full overflow-hidden">
          <div 
            class="h-full rounded-full transition-all duration-500"
            :class="getColorClass(diskUsage)"
            :style="{ width: `${diskUsage}%` }"
          ></div>
        </div>
        <div class="w-10 text-right font-semibold text-[10px]">{{ diskUsage.toFixed(1) }}%</div>
      </div>
    </div>

    <!-- Footer -->
    <div class="px-4 py-1.5 text-[10px] text-text2 border-t border-border bg-surface2 flex items-center gap-2 font-mono opacity-70 truncate">
      {{ agent.id }}
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  agent: {
    type: Object,
    required: true
  },
  active: {
    type: Boolean,
    default: false
  },
  alertCount: {
    type: Number,
    default: 0
  }
})

defineEmits(['click'])

const cpuUsage = computed(() => props.agent.systemInfo?.cpuUsage || 0)
const memUsage = computed(() => {
  const info = props.agent.systemInfo
  if (!info || !info.totalMemory) return 0
  return ((info.totalMemory - info.freeMemory) / info.totalMemory) * 100
})
const diskUsage = computed(() => {
  const disks = props.agent.systemInfo?.disks || []
  if (!disks.length) return 0
  let total = 0
  let usable = 0
  disks.forEach(d => {
    total += d.totalSpace || 0
    usable += d.usableSpace || 0
  })
  if (total === 0) return 0
  return ((total - usable) / total) * 100
})

function getColorClass(val) {
  if (val >= 90) return 'bg-red'
  if (val >= 75) return 'bg-yellow'
  return 'bg-accent'
}
</script>
