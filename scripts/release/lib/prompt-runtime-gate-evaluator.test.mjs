import assert from 'node:assert/strict'
import test from 'node:test'
import {
  evaluateReleaseGate,
  GATE_MODES,
  gateOutputContainsSecrets,
  resolveExitCode,
  sanitizeUrl,
} from './prompt-runtime-gate-evaluator.mjs'

const passFingerprint = {
  matchesPinnedVersion: true,
  matchesLatestVersion: false,
  v1SystemMatch: true,
  v1UserMatch: true,
  v1CombinedMatch: true,
  v2SystemMatch: false,
  v2UserMatch: false,
  v2CombinedMatch: false,
}

const passArtifact = {
  appId: '1',
  appVersionId: '95',
  bindingSource: 'VERSION_CREATED_EVENT',
  versionResolved: true,
  fileListHttp: 200,
  indexHttp: 200,
  indexLength: 100,
}

const passBrowser = {
  skipped: false,
  fileTreeVisible: true,
  indexVisible: true,
  indexContentVisible: true,
  refreshPass: true,
  consoleErrors: 0,
  pageErrors: 0,
  http5xx: 0,
  http5xxEvents: [],
}

const passBudget = { before: 10, after: 10, delta: 0 }

function baseInput(overrides = {}) {
  return {
    mode: GATE_MODES.RELEASE,
    fingerprint: passFingerprint,
    artifact: passArtifact,
    browser: passBrowser,
    aiCallBudget: passBudget,
    ...overrides,
  }
}

test('GateFailsWhenArtifactVersionUnresolvedTest', () => {
  const result = evaluateReleaseGate(baseInput({
    artifact: { ...passArtifact, versionResolved: false, bindingErrorCode: 'ARTIFACT_VERSION_UNRESOLVED' },
  }))
  assert.equal(result.passed, false)
  assert.ok(result.failures.some((f) => f.code === 'ARTIFACT_VERSION_UNRESOLVED'))
})

test('GateFailsWhenBrowserSkippedTest', () => {
  const result = evaluateReleaseGate(baseInput({
    browser: { ...passBrowser, skipped: true },
  }))
  assert.equal(result.passed, false)
  assert.ok(result.failures.some((f) => f.code === 'BROWSER_SKIPPED'))
})

test('GateFailsWhenBrowserLaunchFailsTest', () => {
  const result = evaluateReleaseGate(baseInput({
    browser: { ...passBrowser, launchError: 'BROWSER_LAUNCH_FAILED' },
  }))
  assert.equal(result.passed, false)
  assert.ok(result.failures.some((f) => f.code === 'BROWSER_LAUNCH_FAILED'))
})

test('GateFailsWhenFileTreeNotVisibleTest', () => {
  const result = evaluateReleaseGate(baseInput({
    browser: { ...passBrowser, fileTreeVisible: false },
  }))
  assert.equal(result.passed, false)
  assert.ok(result.failures.some((f) => f.code === 'BROWSER_FILE_TREE_MISSING'))
})

test('GateFailsWhenRefreshFailsTest', () => {
  const result = evaluateReleaseGate(baseInput({
    browser: { ...passBrowser, refreshPass: false },
  }))
  assert.equal(result.passed, false)
  assert.ok(result.failures.some((f) => f.code === 'BROWSER_REFRESH_FAILED'))
})

test('GateFailsWhenConsoleErrorExistsTest', () => {
  const result = evaluateReleaseGate(baseInput({
    browser: { ...passBrowser, consoleErrors: 1 },
  }))
  assert.equal(result.passed, false)
  assert.ok(result.failures.some((f) => f.code === 'BROWSER_CONSOLE_ERRORS'))
})

test('GateFailsWhenPageErrorExistsTest', () => {
  const result = evaluateReleaseGate(baseInput({
    browser: { ...passBrowser, pageErrors: 1 },
  }))
  assert.equal(result.passed, false)
  assert.ok(result.failures.some((f) => f.code === 'BROWSER_PAGE_ERRORS'))
})

test('GateFailsWhenHttp5xxExistsTest', () => {
  const result = evaluateReleaseGate(baseInput({
    browser: {
      ...passBrowser,
      http5xx: 1,
      http5xxEvents: [{ method: 'GET', status: 500, path: '/api/v1/apps/1/files' }],
    },
  }))
  assert.equal(result.passed, false)
  assert.ok(result.failures.some((f) => f.code === 'BROWSER_HTTP_5XX'))
})

test('GateFailsWhenAiCallDeltaIsNonZeroTest', () => {
  const result = evaluateReleaseGate(baseInput({
    aiCallBudget: { before: 10, after: 11, delta: 1 },
  }))
  assert.equal(result.passed, false)
  assert.ok(result.failures.some((f) => f.code === 'UNEXPECTED_AI_CALL_DELTA'))
})

test('GatePassesOnlyWhenEveryRequiredConditionPassesTest', () => {
  const result = evaluateReleaseGate(baseInput())
  assert.equal(result.passed, true)
  assert.equal(result.failures.length, 0)
})

test('ApiOnlyModeCannotReturnReleasePassTest', () => {
  const result = evaluateReleaseGate(baseInput({ mode: GATE_MODES.API_ONLY }))
  assert.equal(result.passed, false)
  assert.equal(result.mode, GATE_MODES.API_ONLY)
  assert.ok(result.failures.some((f) => f.code === 'PENDING_BROWSER'))
})

test('GateOutputDoesNotContainSecretsTest', () => {
  const result = evaluateReleaseGate(baseInput())
  assert.equal(gateOutputContainsSecrets(result), false)
  assert.equal(gateOutputContainsSecrets({ accessToken: 'secret-token-value' }), true)
})

test('CurrentVersionFallbackIsForbiddenTest', () => {
  const result = evaluateReleaseGate(baseInput({
    artifact: {
      ...passArtifact,
      versionResolved: false,
      bindingSource: 'CURRENT_APP_VERSION',
      bindingErrorCode: 'ARTIFACT_VERSION_UNRESOLVED',
    },
  }))
  assert.equal(result.passed, false)
  assert.ok(result.failures.some((f) => f.code === 'ARTIFACT_VERSION_UNRESOLVED'))
})

test('CorrectTaskVersionRemainsStableAfterNewVersionTest', () => {
  const result = evaluateReleaseGate(baseInput({
    artifact: {
      ...passArtifact,
      appVersionId: '95',
      bindingSource: 'VERSION_CREATED_EVENT',
      versionResolved: true,
    },
  }))
  assert.equal(result.artifact.appVersionId, '95')
  assert.equal(result.artifact.bindingSource, 'VERSION_CREATED_EVENT')
})

test('sanitizeUrl removes token query params', () => {
  const sanitized = sanitizeUrl('http://127.0.0.1:5182/apps/1/files?versionId=95&accessToken=secret')
  assert.equal(sanitized.includes('accessToken'), false)
})

test('resolveExitCode returns browser unavailable code', () => {
  assert.equal(resolveExitCode({ passed: false }, true), 3)
  assert.equal(resolveExitCode({ passed: true }, false), 0)
  assert.equal(resolveExitCode({ passed: false }, false), 1)
})
