import { describe, expect, it, vi } from 'vitest'
import {
  awaitCreationPhase,
  buildCreationPollPromise,
  classifyStartGenerationCatchErrorSource,
  detachObservation,
  isTaskIdentityEstablished,
  shouldRunCreationErrorCleanup,
} from '@/pages/app/generationLifecycle'

describe('StreamFailureDoesNotReachStartGenerationCatchTest', () => {
  it('does not run START_GENERATION_ERROR cleanup for observation failures after task id exists', () => {
    expect(
      shouldRunCreationErrorCleanup({
        taskIdEstablished: true,
        errorSource: 'OBSERVATION',
      }),
    ).toBe(false)
  })
})

describe('CreateFailureStillTriggersGenerationErrorCleanupTest', () => {
  it('runs creation cleanup when create fails before task id is established', () => {
    expect(
      shouldRunCreationErrorCleanup({
        taskIdEstablished: false,
        errorSource: 'CREATION',
      }),
    ).toBe(true)
  })
})

describe('ObservationStartsDetachedFromCreationPromiseTest', () => {
  it('creation poll promise resolves without awaiting observation', async () => {
    const pollPromise = buildCreationPollPromise(async () => '124', { timeoutMs: 1000, intervalMs: 1 })

    const polledTaskId = await pollPromise
    expect(polledTaskId).toBe('124')

    let observationSettled = false
    detachObservation(
      () =>
        new Promise<void>((resolve) => {
          setTimeout(() => {
            observationSettled = true
            resolve()
          }, 50_000)
        }),
      () => undefined,
    )

    expect(observationSettled).toBe(false)
  })
})

describe('LongRunningStreamDoesNotKeepCreationPromisePendingTest', () => {
  it('creation phase settles while observation remains pending', async () => {
    const neverEndingObservation = new Promise<void>(() => {})

    const creation = await awaitCreationPhase(
      async () => ({ taskId: '125' }),
      async () => '125',
      { timeoutMs: 1000, intervalMs: 1 },
    )

    detachObservation(() => neverEndingObservation, () => undefined)

    expect(creation.created.taskId).toBe('125')
    expect(creation.polledTaskId).toBe('125')
  })
})

describe('ObservationFailurePreservesTaskIdentityTest', () => {
  it('preserves task identity flags when observation fails', () => {
    const boundTaskId = '126'
    const currentTaskId = '126'
    const observedTaskId = '126'

    expect(isTaskIdentityEstablished(boundTaskId, currentTaskId, observedTaskId)).toBe(true)
    expect(
      shouldRunCreationErrorCleanup({
        taskIdEstablished: true,
        errorSource: 'OBSERVATION',
      }),
    ).toBe(false)
  })
})

describe('TransientStreamFailureDelegatesToRecoveryControllerTest', () => {
  it('detaches observation errors from creation catch path', async () => {
    const onObservationError = vi.fn()
    detachObservation(async () => {
      throw new Error('sse transient failure')
    }, onObservationError)

    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(onObservationError).toHaveBeenCalledTimes(1)
    expect(
      shouldRunCreationErrorCleanup({
        taskIdEstablished: true,
        errorSource: 'OBSERVATION',
      }),
    ).toBe(false)
  })
})

describe('InitialBindingFailureIsNotCreateFailureTest', () => {
  it('does not clear identity when initial binding fails after task id exists', () => {
    expect(
      shouldRunCreationErrorCleanup({
        taskIdEstablished: true,
        errorSource: 'INITIAL_BINDING',
      }),
    ).toBe(false)
    expect(classifyStartGenerationCatchErrorSource(true)).toBe('INITIAL_BINDING')
  })
})
