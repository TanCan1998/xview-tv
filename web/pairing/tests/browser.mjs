import { runTests } from './suite.mjs';
import { runUiTests } from './ui.mjs';
try {
  const protocol = await runTests();
  const results = [...protocol.results, ...await runUiTests()];
  globalThis.testResults = { passed: results.filter((r) => r.ok).length, failed: results.filter((r) => !r.ok).length, results };
  document.getElementById('result').textContent = JSON.stringify(globalThis.testResults, null, 2);
} catch {
  globalThis.testResults = { failed: 1, error: 'Test harness failed' };
  document.getElementById('result').textContent = 'Test harness failed';
}
