/**
 * Pure release gate evaluation logic. No secrets, prompts, or hashes in output.
 */

export const GATE_MODES = {
  RELEASE: 'RELEASE',
  API_ONLY: 'API_ONLY_DIAGNOSTIC',
}

export const EXIT_CODES = {
  PASS: 0,
  FAIL: 1,
  CONFIG: 2,
  BROWSER_UNAVAILABLE: 3,
}

const SECRET_VALUE_PATTERNS = [
  /"password"\s*:\s*"/,
  /"accessToken"\s*:\s*"/,
  /"refreshToken"\s*:\s*"/,
  /Bearer\s+[A-Za-z0-9._-]{8,}/,
]

export function sanitizeUrl(url) {
  if (!url) return url
  try {
    const parsed = new URL(url)
    for (const key of [...parsed.searchParams.keys()]) {
      if (/token|password|secret|credential|auth/i.test(key)) {
        parsed.searchParams.delete(key)
      }
    }
    return `${parsed.pathname}${parsed.search}`
  } catch {
    return String(url).split('?')[0]
  }
}

export function evaluateReleaseGate(input) {
  const mode = input.mode === GATE_MODES.API_ONLY ? GATE_MODES.API_ONLY : GATE_MODES.RELEASE
  const failures = []

  const fingerprint = input.fingerprint || {}
  const artifact = input.artifact || {}
  const browser = input.browser || {}
  const aiCallBudget = input.aiCallBudget || {}

  if (fingerprint.matchesPinnedVersion !== true) {
    failures.push({ code: 'FINGERPRINT_PINNED_MISMATCH', message: 'Pinned template fingerprint mismatch' })
  }
  if (fingerprint.matchesLatestVersion === true) {
    failures.push({ code: 'FINGERPRINT_LATEST_MATCH', message: 'Latest template fingerprint must not match' })
  }

  if (artifact.versionResolved !== true) {
    failures.push({
      code: artifact.bindingErrorCode || 'ARTIFACT_VERSION_UNRESOLVED',
      message: 'Exact task artifact version could not be resolved',
    })
  }
  if (artifact.fileListHttp !== 200) {
    failures.push({ code: 'ARTIFACT_FILE_LIST_HTTP', message: `Artifact file list HTTP ${artifact.fileListHttp}` })
  }
  if (artifact.indexHttp !== 200) {
    failures.push({ code: 'ARTIFACT_INDEX_HTTP', message: `Artifact index HTTP ${artifact.indexHttp}` })
  }
  if (!(artifact.indexLength > 0)) {
    failures.push({ code: 'ARTIFACT_INDEX_EMPTY', message: 'Artifact index content length must be > 0' })
  }

  if (input.foreignArtifact) {
    if (input.foreignArtifact.fileListHttp !== 403 && input.foreignArtifact.fileListHttp !== 404) {
      failures.push({
        code: 'FOREIGN_ARTIFACT_NOT_DENIED',
        message: `Foreign artifact file list HTTP ${input.foreignArtifact.fileListHttp}`,
      })
    }
    if (input.foreignArtifact.indexHttp !== 403 && input.foreignArtifact.indexHttp !== 404) {
      failures.push({
        code: 'FOREIGN_INDEX_NOT_DENIED',
        message: `Foreign artifact index HTTP ${input.foreignArtifact.indexHttp}`,
      })
    }
  }

  if (mode === GATE_MODES.RELEASE) {
    if (browser.skipped === true) {
      failures.push({ code: 'BROWSER_SKIPPED', message: 'Browser gate skipped in RELEASE mode' })
    }
    if (browser.launchError) {
      failures.push({ code: 'BROWSER_LAUNCH_FAILED', message: 'Browser launch failed' })
    }
    if (browser.fileTreeVisible !== true) {
      failures.push({ code: 'BROWSER_FILE_TREE_MISSING', message: 'File tree not visible' })
    }
    if (browser.indexVisible !== true) {
      failures.push({ code: 'BROWSER_INDEX_MISSING', message: 'index.html node not visible' })
    }
    if (browser.indexContentVisible !== true) {
      failures.push({ code: 'BROWSER_INDEX_CONTENT_MISSING', message: 'index.html content not visible' })
    }
    if (browser.refreshPass !== true) {
      failures.push({ code: 'BROWSER_REFRESH_FAILED', message: 'Refresh gate failed' })
    }
    if ((browser.consoleErrors ?? 0) > 0) {
      failures.push({ code: 'BROWSER_CONSOLE_ERRORS', message: `Console errors=${browser.consoleErrors}` })
    }
    if ((browser.pageErrors ?? 0) > 0) {
      failures.push({ code: 'BROWSER_PAGE_ERRORS', message: `Page errors=${browser.pageErrors}` })
    }
    if ((browser.http5xx ?? 0) > 0) {
      failures.push({ code: 'BROWSER_HTTP_5XX', message: `HTTP 5xx count=${browser.http5xx}` })
    }
  } else {
    failures.push({ code: 'PENDING_BROWSER', message: 'API-only diagnostic mode cannot pass release gate' })
  }

  if (aiCallBudget.delta !== 0) {
    failures.push({ code: 'UNEXPECTED_AI_CALL_DELTA', message: `model_call_log delta=${aiCallBudget.delta}` })
  }

  const passed = failures.length === 0
  return {
    mode,
    passed,
    failures,
    fingerprint: summarizeFingerprint(fingerprint),
    artifact: summarizeArtifact(artifact),
    browser: summarizeBrowser(browser),
    aiCallBudget: {
      before: aiCallBudget.before,
      after: aiCallBudget.after,
      delta: aiCallBudget.delta,
      enforced: true,
    },
  }
}

function summarizeFingerprint(fingerprint) {
  return {
    matchesPinnedVersion: boolLabel(fingerprint.matchesPinnedVersion),
    matchesLatestVersion: boolLabel(fingerprint.matchesLatestVersion),
    v1SystemMatch: boolLabel(fingerprint.v1SystemMatch),
    v1UserMatch: boolLabel(fingerprint.v1UserMatch),
    v1CombinedMatch: boolLabel(fingerprint.v1CombinedMatch),
    v2SystemMatch: boolLabel(fingerprint.v2SystemMatch),
    v2UserMatch: boolLabel(fingerprint.v2UserMatch),
    v2CombinedMatch: boolLabel(fingerprint.v2CombinedMatch),
  }
}

function summarizeArtifact(artifact) {
  return {
    appId: artifact.appId ?? null,
    appVersionId: artifact.appVersionId ?? null,
    bindingSource: artifact.bindingSource ?? null,
    versionResolved: boolLabel(artifact.versionResolved),
    bindingErrorCode: artifact.bindingErrorCode ?? null,
    fileListHttp: artifact.fileListHttp ?? null,
    indexHttp: artifact.indexHttp ?? null,
    indexLength: artifact.indexLength ?? 0,
  }
}

function summarizeBrowser(browser) {
  return {
    skipped: browser.skipped === true,
    launchError: browser.launchError ? 'YES' : null,
    route: browser.route ?? null,
    fileTreeVisible: boolLabel(browser.fileTreeVisible),
    indexVisible: boolLabel(browser.indexVisible),
    indexContentVisible: boolLabel(browser.indexContentVisible),
    refreshPass: boolLabel(browser.refreshPass),
    consoleErrors: browser.consoleErrors ?? 0,
    pageErrors: browser.pageErrors ?? 0,
    http5xx: browser.http5xx ?? 0,
    http5xxEvents: (browser.http5xxEvents || []).map((event) => ({
      method: event.method,
      status: event.status,
      path: sanitizeUrl(event.path),
    })),
  }
}

function boolLabel(value) {
  if (value === true) return 'YES'
  if (value === false) return 'NO'
  return 'NO'
}

export function gateOutputContainsSecrets(output) {
  const text = JSON.stringify(output)
  return SECRET_VALUE_PATTERNS.some((pattern) => pattern.test(text))
}

export function resolveExitCode(result, browserUnavailable) {
  if (browserUnavailable) return EXIT_CODES.BROWSER_UNAVAILABLE
  return result.passed ? EXIT_CODES.PASS : EXIT_CODES.FAIL
}
