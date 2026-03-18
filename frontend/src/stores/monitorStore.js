import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useWebSocket } from '@vueuse/core'

export const useMonitorStore = defineStore('monitor', () => {
    const agents = ref(new Map())
    const alerts = ref([])
    const activeAgentId = ref(null)
    const autoRefreshPauseCount = ref(0)
    const refreshInFlight = ref(false)

    const wsUrl = `ws://${location.host}/ws/monitor`
    const { status, send } = useWebSocket(wsUrl, {
        autoReconnect: {
            retries: 5,
            delay: 5000,
        },
        onMessage: (ws, event) => {
            try {
                const msg = JSON.parse(event.data)
                handleSocketMessage(msg)
            } catch (e) {
                console.error('Failed to parse WS message:', e)
            }
        }
    })

    const activeAgent = computed(() => (
        activeAgentId.value ? agents.value.get(activeAgentId.value) : null
    ))

    const agentList = computed(() => {
        return Array.from(agents.value.values()).sort((a, b) => {
            if (a.online !== b.online) return b.online ? 1 : -1
            return a.systemInfo.hostname.localeCompare(b.systemInfo.hostname)
        })
    })

    const totalAgents = computed(() => agents.value.size)
    const onlineAgents = computed(() => agentList.value.filter((agent) => agent.online).length)
    const criticalAlertsCount = computed(() => alerts.value.filter((alert) => alert.alertLevel === 'CRITICAL').length)

    function handleSocketMessage(msg) {
        switch (msg.type) {
            case 'FULL_SYNC':
                reconcileFullSync(msg.data || [])
                break

            case 'AGENT_ONLINE':
            case 'AGENT_OFFLINE':
            case 'METRIC_UPDATE':
            case 'LOG_EVENT':
            case 'PROCESS_UPDATE':
                if (agents.value.has(msg.agentId)) {
                    const agent = agents.value.get(msg.agentId)
                    if (msg.type === 'AGENT_ONLINE') agent.online = true
                    else if (msg.type === 'AGENT_OFFLINE') agent.online = false
                    else if (msg.type === 'METRIC_UPDATE') Object.assign(agent.systemInfo, msg.data)
                    else if (msg.type === 'LOG_EVENT') {
                        if (!agent.logs) agent.logs = []
                        agent.logs.unshift(msg.data)
                        if (agent.logs.length > 50) agent.logs.pop()
                    } else if (msg.type === 'PROCESS_UPDATE') {
                        agent.processes = msg.data
                    }

                    agents.value.set(msg.agentId, agent)
                }
                break

            case 'NEW_ALERT':
                alerts.value.unshift(msg.data)
                break
        }
    }

    function setActiveAgent(agentId) {
        activeAgentId.value = agentId
    }

    function pauseAutoRefresh() {
        autoRefreshPauseCount.value += 1
    }

    function resumeAutoRefresh() {
        autoRefreshPauseCount.value = Math.max(0, autoRefreshPauseCount.value - 1)
    }

    async function ackAlert(id) {
        try {
            await fetch(`/api/alert/events/${id}/ack`, { method: 'POST' })
            alerts.value = alerts.value.filter((alert) => alert.id !== id)
        } catch (e) {
            console.error('Failed to ack alert', e)
        }
    }

    async function ackAllAlerts() {
        try {
            await fetch(`/api/alert/events/ack-all`, { method: 'POST' })
            alerts.value = []
        } catch (e) {
            console.error('Failed to ack all alerts', e)
        }
    }

    async function loadDashboard() {
        if (autoRefreshPauseCount.value > 0 || refreshInFlight.value) {
            return
        }

        refreshInFlight.value = true
        try {
            const [agentResp, alertResp] = await Promise.all([
                fetch(`/api/dashboard/agents`),
                fetch(`/api/alert/events/unacknowledged`)
            ])
            const agentResult = await agentResp.json()
            const alertResult = await alertResp.json()

            if (agentResult.code === 200 && agentResult.data) {
                reconcileDashboardAgents(agentResult.data)
            }

            if (alertResult.code === 200 && alertResult.data) {
                alerts.value = alertResult.data
            }
        } catch (e) {
            console.error('Failed to load initial dashboard data:', e)
        } finally {
            refreshInFlight.value = false
        }
    }

    function reconcileFullSync(agentListPayload) {
        const seenIds = new Set()

        agentListPayload.forEach((agent) => {
            seenIds.add(agent.id)
            const existing = agents.value.get(agent.id)
            if (existing) {
                Object.assign(existing, agent)
                agents.value.set(agent.id, existing)
            } else {
                agents.value.set(agent.id, agent)
            }
        })

        Array.from(agents.value.keys()).forEach((id) => {
            if (!seenIds.has(id)) {
                agents.value.delete(id)
            }
        })
    }

    function reconcileDashboardAgents(agentListPayload) {
        const seenIds = new Set()

        agentListPayload.forEach((agentPayload) => {
            const normalized = normalizeDashboardAgent(agentPayload, agents.value.get(agentPayload.agentId)?.logs || [])
            seenIds.add(normalized.id)

            const existing = agents.value.get(normalized.id)
            if (existing) {
                existing.online = normalized.online
                existing.systemInfo = existing.systemInfo || {}
                Object.assign(existing.systemInfo, normalized.systemInfo)
                existing.systemInfo.disks = normalized.systemInfo.disks
                existing.processes = normalized.processes
                existing.logs = existing.logs || normalized.logs
                agents.value.set(normalized.id, existing)
            } else {
                agents.value.set(normalized.id, normalized)
            }
        })

        Array.from(agents.value.keys()).forEach((id) => {
            if (!seenIds.has(id)) {
                agents.value.delete(id)
            }
        })
    }

    loadDashboard()
    setInterval(loadDashboard, 15000)

    return {
        agents,
        alerts,
        activeAgentId,
        activeAgent,
        agentList,
        totalAgents,
        onlineAgents,
        criticalAlertsCount,
        pauseAutoRefresh,
        resumeAutoRefresh,
        status,
        setActiveAgent,
        ackAlert,
        ackAllAlerts,
        send
    }
})

function normalizeDashboardAgent(agent, existingLogs = []) {
    return {
        id: agent.agentId,
        online: agent.online,
        systemInfo: {
            hostname: agent.mainframeName,
            osName: agent.osName,
            osVersion: agent.osVersion,
            osType: agent.osType,
            cpuType: agent.cpuType,
            cpuUsage: agent.cpuUsage ? parseFloat(agent.cpuUsage) : 0,
            totalMemory: agent.ramCapacity ? parseFloat(agent.ramCapacity) : 0,
            freeMemory: agent.ramAvailable ? parseFloat(agent.ramAvailable) : 0,
            availableProcessors: agent.cpuCores,
            disks: (agent.parts || []).map((part) => ({
                mountPoint: part.mountPoint,
                totalSpace: part.capacity ? parseFloat(part.capacity) : 0,
                usableSpace: part.availableCapacity ? parseFloat(part.availableCapacity) : 0
            }))
        },
        processes: agent.processStatusList?.map((process) => ({
            name: process.processName,
            running: process.status === '正常',
            pid: process.processName
        })) || [],
        logs: existingLogs
    }
}
