<template>
  <div class="flex flex-col h-screen text-text">
    <AlertBanner />

    <!-- Header -->
    <header class="bg-gradient-to-br from-surface to-surface2 border-b border-border px-6 py-3.5 flex items-center gap-4 shrink-0">
      <h1 class="text-lg font-semibold tracking-wide flex-1">Monitoring Dashboard</h1>
      <button 
        v-if="store.activeAgentId"
        @click="showRuleModal = true"
        class="text-xs text-blue hover:text-cyan transition-colors"
      >
        Alert Rules ({{ store.activeAgentId }})
      </button>
      <a href="/debug.html" target="_blank" class="text-xs text-blue opacity-80 hover:opacity-100 transition-opacity">
        Debug Tools
      </a>
      <a href="https://github.com/zzh" target="_blank" class="text-xs text-blue opacity-80 hover:opacity-100 transition-opacity">
        GitHub
      </a>
    </header>

    <!-- Toolbar -->
    <div class="px-6 py-2.5 border-b border-border bg-surface flex items-center gap-3 shrink-0 auto-cols-auto">
      <label class="text-xs tracking-wider uppercase text-text2 font-semibold">Filter</label>
      <input 
        v-model="searchQuery" 
        type="text" 
        placeholder="Hostname / OS / IP..."
        class="bg-bg border border-border text-text px-3 py-1.5 rounded-md text-xs w-52 focus:outline-none focus:border-accent transition-colors"
      >
      
      <div v-if="store.activeAgentId" class="text-xs ml-4 flex items-center gap-2">
         <span class="text-text2">Agent ID:</span>
         <span class="font-mono text-text bg-bg px-2 py-0.5 rounded border border-border">{{ store.activeAgentId }}</span>
      </div>

      <div class="ml-auto text-[11px] text-text2 flex items-center gap-2">
        <div 
          class="w-2 h-2 rounded-full"
          :class="store.status === 'OPEN' ? 'bg-green shadow-[0_0_8px_var(--green)]' : 'bg-red'"
        ></div>
        {{ store.status === 'OPEN' ? 'Connected (Live Data)' : 'Disconnected' }}
      </div>
    </div>

    <!-- Stats Bar -->
    <div class="flex gap-4 px-6 py-3 bg-surface2 border-b border-border shrink-0">
      <div class="bg-surface border border-border rounded-lg p-3 flex-1 flex items-center gap-3">
        <el-icon :size="24" class="text-accent"><Monitor /></el-icon>
        <div>
          <div class="text-[11px] text-text2 mb-0.5">Total Agents</div>
          <div class="text-[22px] font-bold leading-none">{{ store.totalAgents }}</div>
        </div>
      </div>
      <div class="bg-surface border border-border rounded-lg p-3 flex-1 flex items-center gap-3">
        <el-icon :size="24" class="text-green"><CircleCheckFilled /></el-icon>
        <div>
          <div class="text-[11px] text-text2 mb-0.5">Online</div>
          <div class="text-[22px] font-bold leading-none">{{ store.onlineAgents }}</div>
        </div>
      </div>
      <div class="bg-surface border border-border rounded-lg p-3 flex-1 flex items-center gap-3">
        <el-icon :size="24" class="text-red"><WarningFilled /></el-icon>
        <div>
          <div class="text-[11px] text-text2 mb-0.5">Critical Alerts</div>
          <div class="text-[22px] font-bold leading-none">{{ store.criticalAlertsCount }}</div>
        </div>
      </div>
    </div>

    <!-- Main Grid Area -->
    <main class="flex-1 overflow-y-auto p-4 bg-bg">
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
      
      <div v-if="filteredAgents.length === 0" class="flex flex-col items-center justify-center h-full text-text2 opacity-60">
        <el-icon :size="48" class="mb-4"><Monitor /></el-icon>
        <p>No agents found matching your criteria</p>
      </div>
    </main>

    <!-- Modals & Drawers -->
    <DetailDrawer />
    <RuleConfigModal v-model:visible="showRuleModal" />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Monitor, CircleCheckFilled, WarningFilled } from '@element-plus/icons-vue'
import { useMonitorStore } from './stores/monitorStore'

import AlertBanner from './components/alert/AlertBanner.vue'
import AgentCard from './components/agent/AgentCard.vue'
import DetailDrawer from './components/agent/DetailDrawer.vue'
import RuleConfigModal from './components/alert/RuleConfigModal.vue'

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
</script>

<style>
/* For custom scrollbars matching dark theme */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}
::-webkit-scrollbar-track {
  background: var(--bg);
}
::-webkit-scrollbar-thumb {
  background: var(--border);
  border-radius: 3px;
}
::-webkit-scrollbar-thumb:hover {
  background: var(--text2);
}
</style>
