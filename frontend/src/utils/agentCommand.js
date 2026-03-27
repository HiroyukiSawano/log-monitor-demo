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

export function resolveTtydUrl(agentResp, agentId, credential = '') {
  if (!agentId) return '' // Needs agentId for proxy path
  const query = credential ? `?credential=${encodeURIComponent(window.btoa(credential))}` : ''
  // If agent returns a direct IP URL or a port, we route it through our backend proxy
  if (agentResp?.port) {
    return `${window.location.protocol}//${window.location.host}/api/proxy/ttyd/${agentId}/${agentResp.port}/${query}`
  }
  // If agent returned a full URL instead of just port, extract the port from it
  if (agentResp?.url) {
    try {
      const parsed = new URL(agentResp.url)
      return `${window.location.protocol}//${window.location.host}/api/proxy/ttyd/${agentId}/${parsed.port}/${query}`
    } catch (e) {
      console.warn('Failed to parse TTYD URL:', agentResp.url)
      return agentResp.url
    }
  }
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
