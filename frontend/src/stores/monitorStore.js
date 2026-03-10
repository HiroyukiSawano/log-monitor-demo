import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useWebSocket } from '@vueuse/core'

export const useMonitorStore = defineStore('monitor', () => {
    // State
    const agents = ref(new Map()) // agentId -> agent data
    const alerts = ref([])
    const activeAgentId = ref(null)

    // WebSocket Connection
    const wsUrl = `ws://${location.host}/ws/monitor`
    const { status, data, send, close } = useWebSocket(wsUrl, {
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

    // Getters
    const activeAgent = computed(() => {
        return activeAgentId.value ? agents.value.get(activeAgentId.value) : null
    })

    const agentList = computed(() => {
        return Array.from(agents.value.values()).sort((a, b) => {
            if (a.online !== b.online) return b.online ? 1 : -1
            return a.systemInfo.hostname.localeCompare(b.systemInfo.hostname)
        })
    })

    const totalAgents = computed(() => agents.value.size)
    const onlineAgents = computed(() => agentList.value.filter(a => a.online).length)
    const criticalAlertsCount = computed(() => alerts.value.filter(a => a.level === 'CRITICAL').length)

    // Actions
    function handleSocketMessage(msg) {
        switch (msg.type) {
            case 'FULL_SYNC':
                // Initialize agents map
                const newMap = new Map()
                msg.data.forEach(agent => newMap.set(agent.id, agent))
                agents.value = newMap
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
                        if (agent.logs.length > 50) agent.logs.pop() // keep last 50
                    }
                    else if (msg.type === 'PROCESS_UPDATE') agent.processes = msg.data

                    // trigger reactivity for Map
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

    async function ackAlert(id) {
        try {
            await fetch(`/api/alert/events/${id}/ack`, { method: 'POST' })
            alerts.value = alerts.value.filter(a => a.id !== id)
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
        try {
            const [agentResp, alertResp] = await Promise.all([
                fetch(`/api/dashboard/agents`),
                fetch(`/api/alert/events/unacknowledged`)
            ])
            const agentResult = await agentResp.json()
            const alertResult = await alertResp.json()

            if (agentResult.code === 200 && agentResult.data) {
                const newMap = new Map()
                agentResult.data.forEach(agent => {
                    // Normalize data structure from backend to match frontend expectations
                    const a = {
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
                            disks: (agent.parts || []).map(p => ({
                                mountPoint: p.mountPoint,
                                totalSpace: p.capacity ? parseFloat(p.capacity) : 0,
                                usableSpace: p.availableCapacity ? parseFloat(p.availableCapacity) : 0
                            }))
                        },
                        processes: agent.processStatusList?.map(p => ({
                            name: p.processName,
                            running: p.status === '正常',
                            pid: p.processName // using name as pid fallback
                        })) || [],
                        logs: []
                    }
                    newMap.set(a.id, a)
                })
                agents.value = newMap
            }

            if (alertResult.code === 200 && alertResult.data) {
                alerts.value = alertResult.data
            }
        } catch (e) {
            console.error('Failed to load initial dashboard data:', e)
        }
    }

    // Call init fetch immediately
    loadDashboard()

    return {
        agents,
        alerts,
        activeAgentId,
        activeAgent,
        agentList,
        totalAgents,
        onlineAgents,
        criticalAlertsCount,
        status,
        setActiveAgent,
        ackAlert,
        ackAllAlerts,
        send // expose raw WS send if needed
    }
})
