import { webcrypto } from 'node:crypto';
import { runTests } from './suite.mjs';
if (!globalThis.crypto) globalThis.crypto = webcrypto;
const result = await runTests();
for (const test of result.results) console.log(`${test.ok ? 'PASS' : 'FAIL'} ${test.name}`);
console.log(`${result.passed} passed; ${result.failed} failed`);
if (result.failed) process.exitCode = 1;
