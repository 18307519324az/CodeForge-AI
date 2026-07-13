export interface SseStreamEvent<T = unknown> {
  id?: string
  type: string
  data: T
}

export class SseStreamError extends Error {
  readonly status?: number

  readonly retryable: boolean

  constructor(message: string, status?: number, retryable = false) {
    super(message)
    this.name = 'SseStreamError'
    this.status = status
    this.retryable = retryable
  }
}

export interface ConsumeSseStreamOptions<T = unknown> {
  url: string
  token: string
  signal?: AbortSignal
  lastEventId?: string
  onConnected?: () => void
  onEvent: (event: SseStreamEvent<T>) => void
  onTerminal?: (event: SseStreamEvent<T>) => void
  onMalformedFrame?: (segment: string) => void
  onActivity?: () => void
}

const EVENT_SEPARATOR = /\r?\n\r?\n/

export function isRetryableHttpStatus(status: number): boolean {
  if (status === 429) {
    return true
  }
  if (status >= 500 && status <= 599) {
    return true
  }
  return false
}

export function classifySseStreamError(error: unknown): {
  retryable: boolean
  status?: number
  kind: 'aborted' | 'network' | 'auth' | 'forbidden' | 'not_found' | 'bad_request' | 'rate_limit' | 'server' | 'disconnect' | 'unknown'
} {
  if (error instanceof SseStreamError) {
    if (error.message.includes('aborted') || error.message.includes('BodyStreamBuffer was aborted')) {
      return { retryable: false, status: error.status, kind: 'aborted' }
    }
    if (error.status === 401) {
      return { retryable: false, status: 401, kind: 'auth' }
    }
    if (error.status === 403) {
      return { retryable: false, status: 403, kind: 'forbidden' }
    }
    if (error.status === 404) {
      return { retryable: false, status: 404, kind: 'not_found' }
    }
    if (error.status === 400) {
      return { retryable: false, status: 400, kind: 'bad_request' }
    }
    if (error.status === 429) {
      return { retryable: true, status: 429, kind: 'rate_limit' }
    }
    if (error.status && error.status >= 500) {
      return { retryable: true, status: error.status, kind: 'server' }
    }
    if (error.retryable) {
      return { retryable: true, status: error.status, kind: 'disconnect' }
    }
    return { retryable: false, status: error.status, kind: 'unknown' }
  }

  if (error instanceof Error) {
    if (error.name === 'AbortError' || error.message.includes('aborted')) {
      return { retryable: false, kind: 'aborted' }
    }
    return { retryable: true, kind: 'network' }
  }

  return { retryable: true, kind: 'network' }
}

export async function consumeSseStream<T = unknown>(options: ConsumeSseStreamOptions<T>) {
  const headers: Record<string, string> = {
    Authorization: `Bearer ${options.token}`,
    Accept: 'text/event-stream',
  }
  if (options.lastEventId) {
    headers['Last-Event-ID'] = options.lastEventId
  }

  let response: Response
  try {
    response = await fetch(options.url, {
      method: 'GET',
      headers,
      signal: options.signal,
    })
  } catch (error: unknown) {
    if (options.signal?.aborted) {
      throw new SseStreamError('SSE connection aborted', undefined, false)
    }
    throw new SseStreamError(
      error instanceof Error ? error.message : 'SSE network failure',
      undefined,
      true,
    )
  }

  if (!response.ok || !response.body) {
    throw new SseStreamError(
      `SSE connection failed: ${response.status}`,
      response.status,
      isRetryableHttpStatus(response.status),
    )
  }

  options.onConnected?.()
  options.onActivity?.()

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let terminalReceived = false

  const dispatchEvent = (event: SseStreamEvent<T>) => {
    if (event.type === 'HEARTBEAT') {
      options.onActivity?.()
      return
    }
    options.onActivity?.()
    options.onEvent(event)
    if (event.data && typeof event.data === 'object' && 'terminal' in event.data && event.data.terminal === true) {
      terminalReceived = true
      options.onTerminal?.(event)
    }
  }

  while (true) {
    let readResult: ReadableStreamReadResult<Uint8Array>
    try {
      readResult = await reader.read()
    } catch (error: unknown) {
      if (options.signal?.aborted) {
        throw new SseStreamError('SSE connection aborted', undefined, false)
      }
      throw new SseStreamError(
        error instanceof Error ? error.message : 'SSE read failure',
        undefined,
        true,
      )
    }

    const { value, done } = readResult
    buffer += decoder.decode(value ?? new Uint8Array(), { stream: !done })

    const segments = buffer.split(EVENT_SEPARATOR)
    buffer = done ? '' : segments.pop() || ''

    for (const segment of segments) {
      const event = parseSseSegment<T>(segment, options.onMalformedFrame)
      if (!event) {
        continue
      }
      dispatchEvent(event)
    }

    if (done) {
      if (buffer.trim()) {
        const tailEvent = parseSseSegment<T>(buffer, options.onMalformedFrame)
        if (tailEvent) {
          dispatchEvent(tailEvent)
        }
      }
      break
    }
  }

  if (!terminalReceived && !options.signal?.aborted) {
    throw new SseStreamError('SSE stream closed before terminal event', undefined, true)
  }
}

function parseSseSegment<T = unknown>(
  segment: string,
  onMalformedFrame?: (segment: string) => void,
): SseStreamEvent<T> | null {
  const trimmedSegment = segment.trim()
  if (!trimmedSegment) {
    return null
  }

  const lines = trimmedSegment.split(/\r?\n/)
  let eventType = 'message'
  let eventId: string | undefined
  const dataLines: string[] = []

  for (const rawLine of lines) {
    const line = rawLine.trimEnd()
    if (!line || line.startsWith(':')) {
      continue
    }
    if (line.startsWith('event:')) {
      eventType = line.slice('event:'.length).trim()
      continue
    }
    if (line.startsWith('id:')) {
      eventId = line.slice('id:'.length).trim()
      continue
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trim())
    }
  }

  if (dataLines.length === 0) {
    return null
  }

  const rawData = dataLines.join('\n')
  try {
    return {
      id: eventId,
      type: eventType,
      data: JSON.parse(rawData) as T,
    }
  } catch {
    onMalformedFrame?.(trimmedSegment)
    return {
      id: eventId,
      type: eventType,
      data: rawData as T,
    }
  }
}
