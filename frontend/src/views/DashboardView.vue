<template>
  <div class="flex flex-col h-full bg-bg">
    <AlertBanner />

    <!-- Toolbar -->
    <div class="px-6 py-2.5 border-b border-border bg-surface flex items-center gap-3 shrink-0 auto-cols-auto">
      <label class="text-xs tracking-wider uppercase text-text2 font-semibold">筛选</label>
      <input 
        v-model="searchQuery" 
        type="text" 
        placeholder="主机名 / 操作系统 / IP..."
        class="bg-bg border border-border text-text px-3 py-1.5 rounded-md text-xs w-52 focus:outline-none focus:border-accent transition-colors"
      >
      
      <div v-if="store.activeAgentId" class="text-xs ml-4 flex items-center gap-2">
         <span class="text-text2">节点 ID:</span>
         <span class="font-mono text-text bg-bg px-2 py-0.5 rounded border border-border">{{ store.activeAgentId }}</span>
      </div>

      <div class="ml-auto text-[11px] text-text2 flex items-center gap-2">
        <div 
          class="w-2 h-2 rounded-full"
          :class="store.status === 'OPEN' ? 'bg-green shadow-[0_0_8px_var(--green)]' : 'bg-red'"
        ></div>
        {{ store.status === 'OPEN' ? '已连接(实时数据)' : '未连接' }}
      </div>
    </div>

    <!-- Stats Bar -->
    <div class="flex gap-4 px-6 py-3 bg-surface2 border-b border-border shrink-0">
      <div class="bg-surface border border-border rounded-lg p-3 flex-1 flex items-center gap-3">
        <el-icon :size="24" class="text-accent"><Monitor /></el-icon>
        <div>
          <div class="text-[11px] text-text2 mb-0.5">总服务器数</div>
          <div class="text-[22px] font-bold leading-none">{{ store.totalAgents }}</div>
        </div>
      </div>
      <div class="bg-surface border border-border rounded-lg p-3 flex-1 flex items-center gap-3">
        <el-icon :size="24" class="text-green"><CircleCheckFilled /></el-icon>
        <div>
          <div class="text-[11px] text-text2 mb-0.5">在线</div>
          <div class="text-[22px] font-bold leading-none text-green">{{ store.onlineAgents }}</div>
        </div>
      </div>
      <div class="bg-surface border border-border rounded-lg p-3 flex-1 flex items-center gap-3">
        <el-icon :size="24" class="text-red"><CircleCloseFilled /></el-icon>
        <div>
          <div class="text-[11px] text-text2 mb-0.5">离线</div>
          <div class="text-[22px] font-bold leading-none text-red">{{ store.totalAgents - store.onlineAgents }}</div>
        </div>
      </div>
      <div class="bg-surface border border-border rounded-lg p-3 flex-1 flex items-center gap-3">
        <el-icon :size="24" class="text-yellow"><WarningFilled /></el-icon>
        <div>
          <div class="text-[11px] text-text2 mb-0.5">进程异常</div>
          <div class="text-[22px] font-bold leading-none text-yellow">{{ alertsCount }}</div>
        </div>
      </div>
    </div>

    <!-- Main Grid Area -->
    <main class="flex-1 overflow-y-auto p-4 bg-bg relative">
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
        <AgentCard 
          v-for="agent in filteredAgents" 
          :key="agent.id"
          :agent="agent"
          :active="store.activeAgentId === agent.id"
          :alert-count="getAlertCountForAgent(agent.id)"
          @click="store.setActiveAgent($event)"
        />
      </div>
      
      <div v-if="filteredAgents.length === 0" class="absolute inset-0 flex flex-col items-center justify-center text-text2 opacity-60">
        <el-icon :size="48" class="mb-4"><Monitor /></el-icon>
        <p>没有找到符合条件的服务器</p>
      </div>
    </main>

    <!-- Modals & Drawers -->
    <DetailDrawer />
    <!-- Emitted event logic needed to show Rule Config Modal, or we can handle it globally.
         Let's just put RuleConfigModal here and expose a method, or use a store for it.
         For ease, we will keep showRuleModal in the store or emit it. 
         Wait, App.vue previously had showRuleModal in its state. 
         Let's just keep RuleConfigModal here, and use a ref. 
         How was it opened? The header had a button.
         We can move the Agent ID header part into the toolbar. -->
    <RuleConfigModal v-model:visible="showRuleModal" />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Monitor, CircleCheckFilled, CircleCloseFilled, WarningFilled, Setting } from '@element-plus/icons-vue'
import { useMonitorStore } from '../stores/monitorStore'

import AlertBanner from '../components/alert/AlertBanner.vue'
import AgentCard from '../components/agent/AgentCard.vue'
import DetailDrawer from '../components/agent/DetailDrawer.vue'
import RuleConfigModal from '../components/alert/RuleConfigModal.vue'

const store = useMonitorStore()

const searchQuery = ref('')
const showRuleModal = ref(false)

const filteredAgents = computed(() => {
  const q = searchQuery.value.toLowerCase()
  if (!q) return store.agentList
  return store.agentList.filter(a => {
    return a.id.toLowerCase().includes(q) || 
           (a.systemInfo?.hostname || '').toLowerCase().includes(q) ||
           (a.systemInfo?.osName || '').toLowerCase().includes(q)
  })
})

function getAlertCountForAgent(agentId) {
  return store.alerts.filter(a => a.agentId === agentId).length
}

const alertsCount = computed(() => {
  let count = 0
  store.agentList.forEach(a => {
    if (a.processes) {
      a.processes.forEach(p => {
        if (!p.running) count++
      })
    }
  })
  return count
})
</script>
