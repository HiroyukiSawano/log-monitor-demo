function buildCmdId(prefix) {
  return `${prefix}-${Date.now()}`
}

export function buildAgentCommand(func, param, cmdIdPrefix = 'cmd') {
  return {
    type: 'cmd',
    cmd: {
      func,
      param,
      cmdID: buildCmdId(cmdIdPrefix)
    }
  }
}

export function resolveTtydUrl(agentResp) {
  if (agentResp?.url) return agentResp.url
  if (agentResp?.port) return `http://localhost:${agentResp.port}`
  return ''
}

export async function sendAgentCommand(agentId, command) {
  const res = await fetch('/api/commands/send', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      agentId,
      content: JSON.stringify(command)
    })
  })

  if (!res.ok) {
    throw new Error('网络请求错误')
  }

  const json = await res.json()
  if (json.code !== 200 || !json.data) {
    throw new Error(json.message || '请求失败')
  }

  const cmdResp = json.data
  if (cmdResp.status !== 'SUCCESS') {
    throw new Error('Agent响应错误: ' + cmdResp.status)
  }

  let agentResp
  try {
    agentResp = JSON.parse(cmdResp.response)
  } catch (_) {
    throw new Error('Agent响应不是有效JSON')
  }

  if (!agentResp?.success) {
    throw new Error(agentResp?.message || '操作失败')
  }

  return agentResp
}
