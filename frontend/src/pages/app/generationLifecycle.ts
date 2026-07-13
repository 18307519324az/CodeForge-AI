import { waitForLatestTaskId } from '@/pages/app/generationTaskObservation'
import type { LongId } from '@/types/id'

export type GenerationErrorSource = 'CREATION' | 'INITIAL_BINDING' | 'OBSERVATION'

export interface CreationErrorCleanupInput {
  taskIdEstablished: boolean
  errorSource: GenerationErrorSource
}

export interface CreationPhaseResult<TCreate> {
  created: TCreate
  polledTaskId: string | undefined
}

/**
 * CREATION catch cleanup must only run when task identity was never established.
 * OBSERVATION failures must never propagate into CREATION catch semantics.
 */
export function shouldRunCreationErrorCleanup(input: CreationErrorCleanupInput): boolean {
  if (input.errorSource === 'OBSERVATION') {
    return false
  }
  return !input.taskIdEstablished
}

export function isTaskIdentityEstablished(
  boundTaskId: string | undefined,
  currentTaskId: string | undefined,
  observedTaskId: string | undefined,
): boolean {
  return Boolean(boundTaskId || currentTaskId || observedTaskId)
}

/**
 * Poll latest task id only. Must not await observation / SSE lifecycle.
 */
export function buildCreationPollPromise(
  fetchLatestTaskId: () => Promise<LongId | undefined>,
  options?: { timeoutMs?: number; intervalMs?: number },
): Promise<string | undefined> {
  return waitForLatestTaskId(fetchLatestTaskId, options)
}

export async function awaitCreationPhase<TCreate>(
  createTask: () => Promise<TCreate>,
  fetchLatestTaskId: () => Promise<LongId | undefined>,
  pollOptions?: { timeoutMs?: number; intervalMs?: number },
): Promise<CreationPhaseResult<TCreate>> {
  const [created, polledTaskId] = await Promise.all([
    createTask(),
    buildCreationPollPromise(fetchLatestTaskId, pollOptions),
  ])
  return { created, polledTaskId }
}

/**
 * Long-lived observation must not keep CREATION promise pending.
 */
export function detachObservation(
  startObservation: () => Promise<void>,
  onObservationError: (error: unknown) => void,
): void {
  void startObservation().catch(onObservationError)
}

export function classifyStartGenerationCatchErrorSource(taskIdEstablished: boolean): GenerationErrorSource {
  return taskIdEstablished ? 'INITIAL_BINDING' : 'CREATION'
}
