import httpClient from './http'

type StreamChunkCallback = (chunk: string) => void
type StreamDoneCallback = () => void

export interface StreamConnection {
  close: () => void
}

interface OpenSseOptions {
  path: string
  message: string
  chatId?: string
  onChunk: StreamChunkCallback
  onDone: StreamDoneCallback
}

function parseSsePayload(payload: string): string {
  const normalized = payload.replace(/\r\n/g, '\n')
  const lines = normalized.split('\n')
  const dataParts: string[] = []

  for (const line of lines) {
    if (line.startsWith('data:')) {
      dataParts.push(line.slice(5).trimStart())
    }
  }

  if (dataParts.length > 0) {
    return dataParts.join('\n')
  }

  return payload.trim()
}

function openSseConnection(options: OpenSseOptions): StreamConnection {
  const streamUrl = httpClient.getUri({
    url: options.path,
    params: {
      message: options.message,
      chatId: options.chatId,
    },
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
          const text = parseSsePayload(eventPayload)
          if (text && text !== '[DONE]') {
            options.onChunk(text)
          }
        }
      }

      const lastText = parseSsePayload(buffer)
      if (lastText && lastText !== '[DONE]') {
        options.onChunk(lastText)
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

export function streamManusReply(
  message: string,
  onChunk: StreamChunkCallback,
  onDone: StreamDoneCallback,
): StreamConnection {
  return openSseConnection({
    path: '/ai/manus/chat',
    message,
    onChunk,
    onDone,
  })
}
