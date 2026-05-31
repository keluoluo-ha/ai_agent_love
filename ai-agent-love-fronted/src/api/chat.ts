import httpClient from './http'

type StreamChunkCallback = (chunk: string) => void
type StreamDoneCallback = () => void

export interface StreamConnection {
  close: () => void
}

export interface AskHumanPayload {
  runId: string
  chatId?: string
  question: string
  reason?: string
  options?: string[]
  status?: string
}

interface OpenSseOptions {
  path: string
  message: string
  chatId?: string
  runId?: string
  replyType?: 'NEW_TASK' | 'AGENT_REPLY'
  onChunk: StreamChunkCallback
  onDone: StreamDoneCallback
  onAskHuman?: (payload: AskHumanPayload) => void
}

interface ParsedSseEvent {
  event?: string
  data: string
}

function parseSseEvent(payload: string): ParsedSseEvent {
  const normalized = payload.replace(/\r\n/g, '\n')
  const lines = normalized.split('\n')
  let eventName: string | undefined
  const dataParts: string[] = []

  for (const line of lines) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataParts.push(line.slice(5).trimStart())
    }
  }

  if (dataParts.length > 0) {
    return { event: eventName, data: dataParts.join('\n') }
  }

  return { event: eventName, data: payload.trim() }
}

function tryParseAskHuman(data: string): AskHumanPayload | null {
  try {
    const parsed = JSON.parse(data) as AskHumanPayload
    if (parsed?.runId && parsed?.question) {
      return parsed
    }
  } catch {
    return null
  }
  return null
}

function openSseConnection(options: OpenSseOptions): StreamConnection {
  const params: Record<string, string> = {
    message: options.message,
  }
  if (options.chatId) {
    params.chatId = options.chatId
  }
  if (options.runId) {
    params.runId = options.runId
  }
  if (options.replyType) {
    params.replyType = options.replyType
  }

  const streamUrl = httpClient.getUri({
    url: options.path,
    params,
  })

  const abortController = new AbortController()

  ;(async () => {
    try {
      const response = await fetch(streamUrl, {
        method: 'GET',
        headers: {
          Accept: 'text/event-stream',
        },
        signal: abortController.signal,
      })

      if (!response.ok || !response.body) {
        options.onDone()
        return
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) {
          break
        }

        buffer += decoder.decode(value, { stream: true })
        const normalizedBuffer = buffer.replace(/\r\n/g, '\n')
        const events = normalizedBuffer.split('\n\n')
        buffer = events.pop() ?? ''

        for (const eventPayload of events) {
          const { event, data } = parseSseEvent(eventPayload)
          if (!data || data === '[DONE]') {
            continue
          }

          if (event === 'ask_human') {
            const payload = tryParseAskHuman(data)
            if (payload) {
              options.onAskHuman?.(payload)
            }
            continue
          }

          if (event === 'done' || event === 'error') {
            continue
          }

          options.onChunk(data)
        }
      }

      const lastEvent = parseSseEvent(buffer)
      if (lastEvent.data && lastEvent.data !== '[DONE]') {
        if (lastEvent.event === 'ask_human') {
          const payload = tryParseAskHuman(lastEvent.data)
          if (payload) {
            options.onAskHuman?.(payload)
          }
        } else if (lastEvent.event !== 'done' && lastEvent.event !== 'error') {
          options.onChunk(lastEvent.data)
        }
      }
    } catch {
      // ignore abort / network exceptions,统一由 onDone 收尾
    } finally {
      options.onDone()
    }
  })()

  return {
    close: () => {
      abortController.abort()
    },
  }
}

export function streamLoveReply(
  message: string,
  chatId: string,
  onChunk: StreamChunkCallback,
  onDone: StreamDoneCallback,
): StreamConnection {
  return openSseConnection({
    path: '/ai/love_app/chat/sse',
    message,
    chatId,
    onChunk,
    onDone,
  })
}

export interface StreamManusOptions {
  chatId?: string
  runId?: string
  replyType?: 'NEW_TASK' | 'AGENT_REPLY'
  onAskHuman?: (payload: AskHumanPayload) => void
}

export function streamManusReply(
  message: string,
  onChunk: StreamChunkCallback,
  onDone: StreamDoneCallback,
  options?: StreamManusOptions,
): StreamConnection {
  return openSseConnection({
    path: '/ai/manus/chat',
    message,
    chatId: options?.chatId,
    runId: options?.runId,
    replyType: options?.replyType,
    onChunk,
    onDone,
    onAskHuman: options?.onAskHuman,
  })
}

export async function syncManusReply(
  message: string,
  options?: StreamManusOptions,
): Promise<AskHumanPayload | { type: 'FINAL'; content: string; runId?: string } | { type: 'ERROR'; content: string }> {
  const params: Record<string, string> = { message }
  if (options?.chatId) {
    params.chatId = options.chatId
  }
  if (options?.runId) {
    params.runId = options.runId
  }
  if (options?.replyType) {
    params.replyType = options.replyType
  }

  const response = await httpClient.get('/ai/manus/chat/sync', { params })
  const data = response.data

  if (data?.type === 'ASK_HUMAN') {
    return {
      runId: data.runId,
      chatId: data.chatId,
      question: data.question,
      reason: data.reason,
      options: data.options,
      status: data.status,
    }
  }
  if (data?.type === 'FINAL') {
    return { type: 'FINAL', content: data.content, runId: data.runId }
  }
  return { type: 'ERROR', content: data.content ?? '请求失败' }
}
