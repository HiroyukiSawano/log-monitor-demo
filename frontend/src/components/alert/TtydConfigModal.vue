<template>
  <el-dialog
    v-model="visible"
    :title="`TTYD 管理 — ${agentId || '未知'}`"
    width="760px"
    class="!bg-surface !border !border-border rounded-xl custom-dialog"
    destroy-on-close
  >
    <div v-loading="loading">
      <div
        v-if="notice.visible"
        class="mb-4 rounded-lg border px-3 py-2 text-xs"
        :class="notice.type === 'success'
          ? 'border-green/30 bg-green/10 text-green'
          : 'border-red/30 bg-red/10 text-red'"
      >
        <div>{{ notice.message }}</div>
        <a
          v-if="notice.link"
          :href="notice.link"
          target="_blank"
          rel="noopener noreferrer"
          class="mt-1 inline-block break-all text-cyan underline underline-offset-2"
        >
          {{ notice.link }}
        </a>
        <div v-if="notice.extra" class="mt-1 text-text2">
          {{ notice.extra }}
        </div>
      </div>

      <div class="mb-4 max-h-[260px] overflow-y-auto pr-2 scrollbar-thin">
        <div v-if="services.length" class="flex flex-col gap-2">
          <div
            v-for="service in services"
            :key="`ttyd-${service.port}-${service.command || 'default'}`"
            class="flex items-start gap-3 rounded-md border border-border bg-bg p-3 shadow-sm"
          >
            <span
              class="mt-0.5 shrink-0 rounded border px-1.5 py-[2px] text-[9px] font-bold uppercase"
              :class="service.isRunning
                ? 'border-cyan/20 bg-cyan/10 text-cyan'
                : 'border-red/20 bg-red/10 text-red'"
            >
              {{ service.isRunning ? '运行中' : '已停止' }}
            </span>

            <div class="min-w-0 flex-1">
              <div class="mb-1 flex items-center gap-2 text-xs font-bold text-text">
                <span>Port {{ service.port || '-' }}</span>
                <span class="truncate text-[10px] font-normal text-text2">
                  {{ service.command || 'bash' }}
                </span>
              </div>
              <div class="break-all rounded bg-surface px-2 py-1 font-mono text-[10px] text-cyan/90">
                {{ getServiceUrl(service) || 'http://localhost' }}
              </div>
              <div class="mt-1 text-[10px] leading-tight text-text2">
                Writable: {{ service.writable ? 'true' : 'false' }} | Workdir: {{ service.workingDirectory || '/' }}
              </div>
            </div>

            <div class="flex shrink-0 items-center gap-2">
              <a
                v-if="getServiceUrl(service)"
                :href="getServiceUrl(service)"
                target="_blank"
                rel="noopener noreferrer"
                class="rounded border border-cyan/30 bg-cyan/10 px-3 py-1.5 text-[11px] font-semibold text-cyan transition-colors hover:border-cyan hover:bg-cyan/15"
              >
                打开
              </a>
              <button
                @click="stopService(service.port)"
                :disabled="stoppingPort === service.port || !service.port"
                class="rounded border border-red/30 bg-red/10 px-3 py-1.5 text-[11px] font-semibold text-red transition-colors hover:border-red hover:bg-red/15 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {{ stoppingPort === service.port ? '停止中...' : '停止' }}
              </button>
            </div>
          </div>
        </div>

        <div v-else class="rounded-md border border-dashed border-border py-6 text-center text-xs text-text2">
          当前没有运行中的 TTYD 服务。
        </div>
      </div>

      <button
        v-if="!showAddForm"
        @click="openAddForm"
        class="w-full rounded-lg border border-dashed border-border bg-surface2 py-2 text-xs text-accent transition-all hover:border-accent hover:bg-accent/5"
      >
        + 启动 TTYD
      </button>

      <div v-else class="rounded-lg border border-border bg-surface2 p-4 shadow-sm">
        <div class="mb-3 flex items-center justify-between text-text">
          <div class="font-mono text-xs font-bold uppercase tracking-wide text-text2">启动 TTYD</div>
          <button @click="showAddForm = false" class="text-text2 hover:text-text">
            <el-icon><Close /></el-icon>
          </button>
        </div>

        <div class="flex flex-col gap-3">
          <div class="flex gap-3">
            <div class="flex-1">
              <label class="mb-1 block text-[10px] text-text2">Port</label>
              <input
                v-model="form.port"
                type="number"
                min="1"
                max="65535"
                class="w-full rounded border border-border bg-bg px-2 py-1.5 text-xs text-text outline-none focus:border-accent"
              >
            </div>
            <div class="flex-1">
              <label class="mb-1 block text-[10px] text-text2">Writable</label>
              <label class="flex h-[31px] items-center rounded border border-border bg-bg px-3 text-xs text-text">
                <input v-model="form.writable" type="checkbox" class="mr-2">
                允许终端写入
              </label>
            </div>
          </div>

          <div class="flex gap-3">
            <div class="flex-1">
              <label class="mb-1 block text-[10px] text-text2">账号</label>
              <input
                v-model="form.username"
                type="text"
                autocomplete="off"
                class="w-full rounded border border-border bg-bg px-2 py-1.5 font-mono text-xs text-text outline-none focus:border-accent"
              >
            </div>
            <div class="flex-1">
              <label class="mb-1 block text-[10px] text-text2">密码</label>
              <input
                v-model="form.password"
                type="password"
                autocomplete="off"
                class="w-full rounded border border-border bg-bg px-2 py-1.5 font-mono text-xs text-text outline-none focus:border-accent"
              >
            </div>
          </div>
        </div>

        <div class="mt-3 text-[11px] text-text2">
          启动成功后会立即打开 TTYD 地址，认证由浏览器和 TTYD Basic Auth 处理。
        </div>

        <div class="mt-4 flex justify-end gap-2">
          <button
            @click="showAddForm = false"
            class="rounded border border-border bg-surface px-4 py-1.5 text-xs text-text2 transition-colors hover:text-text"
          >
            取消
          </button>
          <button
            @click="startService"
            :disabled="starting"
            class="rounded bg-accent px-4 py-1.5 text-xs font-semibold text-white shadow transition-colors hover:bg-opacity-90 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {{ starting ? '启动中...' : '启动并打开' }}
          </button>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { Close } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { buildAgentCommand, resolveTtydUrl, sendAgentCommand } from '../../utils/agentCommand'

const props = defineProps(['agentId'])
const visible = defineModel('visible', { type: Boolean })

const loading = ref(false)
const starting = ref(false)
const stoppingPort = ref(null)
const services = ref([])
const showAddForm = ref(false)
const form = reactive(createDefaultForm())
const notice = reactive(createDefaultNotice())

watch(visible, async (newVal) => {
  if (!newVal || !props.agentId) return

  resetNotice()
  resetForm()
  showAddForm.value = false
  await fetchServices()
})

function createDefaultForm() {
  return {
    port: '7681',
    username: 'admin',
    password: 'password',
    writable: true
  }
}

function createDefaultNotice() {
  return {
    visible: false,
    type: 'success',
    message: '',
    link: '',
    extra: ''
  }
}

function resetForm() {
  Object.assign(form, createDefaultForm())
}

function resetNotice() {
  Object.assign(notice, createDefaultNotice())
}

function setNotice(type, message, options = {}) {
  notice.visible = true
  notice.type = type
  notice.message = message
  notice.link = options.link || ''
  notice.extra = options.extra || ''
}

function openAddForm() {
  resetForm()
  resetNotice()
  showAddForm.value = true
}

function getServiceUrl(service) {
  return resolveTtydUrl(service, props.agentId)
}

async function fetchServices() {
  if (!props.agentId) return

  loading.value = true
  try {
    const agentResp = await sendAgentCommand(
      props.agentId,
      buildAgentCommand('TtydService/list', '[]', 'ttyd-list')
    )
    services.value = agentResp.services || []
  } catch (e) {
    services.value = []
    setNotice('error', '加载失败: ' + e.message)
    ElMessage.error('查询TTYD服务失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

async function startService() {
  const port = Number(form.port)
  const username = form.username.trim()
  const password = form.password
  const credential = `${username}:${password}`

  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    ElMessage.warning('端口必须是 1-65535 之间的整数')
    return
  }
  if (!username) {
    ElMessage.warning('账号不能为空')
    return
  }
  if (!password) {
    ElMessage.warning('密码不能为空')
    return
  }

  resetNotice()
  starting.value = true

  let popup = null
  try {
    popup = window.open('about:blank', '_blank')
    if (popup?.document) {
      popup.document.title = '正在打开 TTYD...'
      popup.document.body.innerHTML = '<div style="font-family:Segoe UI,sans-serif;padding:24px;color:#303133;">TTYD 启动中，正在打开终端...</div>'
    }
  } catch (_) {
    popup = null
  }

  try {
    const agentResp = await sendAgentCommand(
      props.agentId,
      buildAgentCommand(
        'TtydService/start',
        [JSON.stringify({ Port: port, Credential: credential, Writable: form.writable })],
        'ttyd-start'
      )
    )

    const ttydUrl = resolveTtydUrl(agentResp, props.agentId, credential)
    if (!ttydUrl) {
      throw new Error('TTYD 已启动，但未返回可访问地址')
    }

    if (popup && !popup.closed) {
      popup.location.href = ttydUrl
      setNotice('success', agentResp.message || 'TTYD 已启动。', { link: ttydUrl })
    } else {
      setNotice('success', agentResp.message || 'TTYD 已启动。', {
        link: ttydUrl,
        extra: '浏览器可能拦截了新窗口，请点击上方链接手动打开。'
      })
    }

    showAddForm.value = false
    await fetchServices()
  } catch (e) {
    if (popup && !popup.closed) {
      popup.close()
    }
    setNotice('error', '启动失败: ' + e.message)
    ElMessage.error('启动TTYD失败: ' + e.message)
  } finally {
    starting.value = false
  }
}

async function stopService(port) {
  try {
    await ElMessageBox.confirm(`确定停止该 TTYD 服务？\n端口: ${port}`, '停止 TTYD', {
      type: 'warning',
      confirmButtonText: '停止',
      cancelButtonText: '取消'
    })
  } catch (_) {
    return
  }

  stoppingPort.value = port
  try {
    const agentResp = await sendAgentCommand(
      props.agentId,
      buildAgentCommand('TtydService/stopByPort', [port], 'ttyd-stop')
    )
    setNotice('success', `已停止端口 ${port}`, { extra: agentResp.message || '' })
    await fetchServices()
  } catch (e) {
    setNotice('error', '停止失败: ' + e.message)
    ElMessage.error('停止TTYD失败: ' + e.message)
  } finally {
    stoppingPort.value = null
  }
}
</script>

<style scoped>
</style>
