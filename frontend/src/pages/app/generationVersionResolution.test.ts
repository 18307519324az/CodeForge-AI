import { describe, expect, it } from 'vitest'
import { normalizeLongId } from '@/utils/normalizeLongId'
import { resolveGenerationResultVersionId } from '@/pages/app/generationVersionResolution'

describe('normalizeLongId', () => {
  it('accepts pure decimal strings', () => {
    expect(normalizeLongId('50')).toBe('50')
    expect(normalizeLongId('2074335007354310656')).toBe('2074335007354310656')
  })

  it('rejects malformed float-like ids', () => {
    expect(normalizeLongId('50.0')).toBeNull()
  })

  it('never converts numbers', () => {
    expect(normalizeLongId(50)).toBeNull()
  })
})

describe('resolveGenerationResultVersionId', () => {
  it('falls back to currentVersionId when event versionId is malformed', () => {
    const versionId = resolveGenerationResultVersionId({
      currentVersionId: '50',
      events: [{ data: { versionId: '50.0' } }],
    })
    expect(versionId).toBe('50')
  })

  it('prefers currentVersionId over event versionId', () => {
    const versionId = resolveGenerationResultVersionId({
      currentVersionId: '50',
      events: [{ data: { versionId: '99' } }],
    })
    expect(versionId).toBe('50')
  })
})

describe('Task83RegressionTest', () => {
  it('uses authoritative version 50 instead of malformed event 50.0', () => {
    const versionId = resolveGenerationResultVersionId({
      currentVersionId: '50',
      events: [
        { data: { versionNo: 1 } },
        { data: { versionId: '50.0', versionNo: 1 } },
      ],
    })
    expect(versionId).toBe('50')
    expect(versionId).not.toBe('50.0')
  })
})

describe('VersionIdNeverConvertedToNumberTest', () => {
  it('does not produce numeric type from resolution', () => {
    const versionId = resolveGenerationResultVersionId({
      currentVersionId: '50',
      events: [],
    })
    expect(typeof versionId).toBe('string')
  })
})
