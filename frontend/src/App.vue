<template>
  <div class="flex flex-col h-screen text-text bg-bg">
    <!-- Platform Header -->
    <header class="bg-surface shadow-sm border-b border-border px-6 py-0 flex items-center gap-6 shrink-0 h-14 z-20">
      <div class="flex items-center gap-3">
        <div class="w-8 h-8 rounded-lg bg-accent/10 border border-accent/30 flex items-center justify-center text-accent">
          <el-icon :size="18"><Monitor /></el-icon>
        </div>
        <h1 class="text-lg font-bold tracking-wide text-text">运维监控系统</h1>
      </div>
      
      <!-- Navigation Tabs -->
      <nav class="flex items-center h-full ml-6">
        <router-link to="/" class="nav-item group" active-class="active">
          <el-icon class="mr-2 opacity-70 group-hover:opacity-100 transition-opacity"><DataBoard /></el-icon>
          监控大盘
        </router-link>
        <router-link to="/command" class="nav-item group" active-class="active" v-show="false">
          <el-icon class="mr-2 opacity-70 group-hover:opacity-100 transition-opacity"><Connection /></el-icon>
          远程指令
        </router-link>
        <router-link to="/debug" class="nav-item group" active-class="active" v-show="false">
          <el-icon class="mr-2 opacity-70 group-hover:opacity-100 transition-opacity"><Setting /></el-icon>
          调试沙箱
        </router-link>
        <router-link to="/logs" class="nav-item group" active-class="active" v-show="false">
          <el-icon class="mr-2 opacity-70 group-hover:opacity-100 transition-opacity"><Document /></el-icon>
          日志审计
        </router-link>
      </nav>

    </header>

    <!-- Main Content Area -->
    <div class="flex-1 overflow-hidden relative">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </div>
  </div>
</template>

<script setup>
import { Monitor, DataBoard, Connection, Setting, Document, Link } from '@element-plus/icons-vue'
// The store is initialized here or globally so that WebSockets and data persist across views.
import { useMonitorStore } from './stores/monitorStore'
const store = useMonitorStore()
// The monitor store starts automatically because its initial state calls ws connection logic or we do it here if needed.
// Actually, monitorStore already sets up listeners in its setup block likely, or we can just call it to ensure it's instantiated.
</script>

<style>
/* Global scrollbar styling */
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

/* Nav Item Styling */
.nav-item {
  display: flex;
  align-items: center;
  height: 100%;
  padding: 0 20px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text2);
  text-decoration: none;
  position: relative;
  transition: all 0.2s ease;
}

.nav-item:hover {
  color: var(--accent);
  background: rgba(64, 158, 255, 0.05);
}

.nav-item.active {
  color: var(--accent);
  background: rgba(64, 158, 255, 0.1);
}

.nav-item.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 2px;
  background: var(--accent);
}

/* Transitions */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: scale(0.995);
}
</style>
