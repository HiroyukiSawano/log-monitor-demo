import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '../views/DashboardView.vue'
import CommandView from '../views/CommandView.vue'
import DebugView from '../views/DebugView.vue'
import LogsView from '../views/LogsView.vue'

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
        {
            path: '/',
            name: 'dashboard',
            component: DashboardView,
            alias: '/index.html'
        },
        {
            path: '/command',
            name: 'command',
            component: CommandView
        },
        {
            path: '/debug',
            name: 'debug',
            component: DebugView
        },
        {
            path: '/logs',
            name: 'logs',
            component: LogsView
        },
        {
            path: '/:pathMatch(.*)*',
            redirect: '/'
        }
    ]
})

export default router
