import { parseCookie, CookieInputError } from '../cookie.mjs';
import { encode64, decode64, parseFragment, validatePairing, contextBytes, verificationCode, encryptSession, submitEnvelope } from '../protocol.mjs';

export async function runTests() {
  const results = [];
  const test = async (name, task) => {
    try { await task(); results.push({ name, ok: true }); }
    catch { results.push({ name, ok: false }); }
  };
  const assert = (value) => { if (!value) throw new Error('assertion'); };
  const rejects = async (task, code) => {
    let rejected = false;
    try { await task(); } catch (error) { rejected = true; if (code) assert(error.code === code); }
    assert(rejected);
  };
  await test('extract required fields and discard extras', () => {
    const result = parseCookie('theme=dark; auth_token=synthetic-token; ct0=synthetic-csrf; other=unused');
    assert(JSON.stringify(result) === '{"auth_token":"synthetic-token","ct0":"synthetic-csrf"}');
    assert(Object.isFrozen(result));
  });
  await test('browser JSON, quoted and duplicate extras are discarded', () => {
    const result = parseCookie('__cuid=one; __cuid=one; g_state={"i_l":0,"i_e":{"flag":24}}; auth_token=synthetic; ct0=csrf; __cf_bm=one; __cf_bm=two; personalization_id="v1_sample=="');
    assert(result.auth_token === 'synthetic' && result.ct0 === 'csrf' && Object.keys(result).length === 2);
  });
  await test('prefix whitespace equals and trailing separator', () => {
    const result = parseCookie('  Cookie: theme = dark; auth_token = test==; ct0 = csrf=;  ');
    assert(result.auth_token === 'test==' && result.ct0 === 'csrf=');
  });
  await test('identical duplicate required fields accepted', () => {
    assert(parseCookie('auth_token=a; ct0=b; auth_token=a').auth_token === 'a');
  });
  for (const [name, input, code] of [
    ['missing token', 'ct0=b', 'missing'], ['missing csrf', 'auth_token=a', 'missing'],
    ['empty token', 'auth_token=; ct0=b', 'empty'], ['empty csrf', 'auth_token=a; ct0= ', 'empty'],
    ['conflicting duplicate token', 'auth_token=a; ct0=b; auth_token=c', 'duplicate'],
    ['conflicting duplicate csrf', 'auth_token=a; ct0=b; ct0=c', 'duplicate'],
    ['header injection', 'auth_token=a; ct0=b\r\nX-Test: bad', 'format'],
    ['newline', 'auth_token=a;\nct0=b', 'format'], ['bare name', 'auth_token=a; ct0=b; bad', 'format'],
    ['empty interior field', 'auth_token=a;;ct0=b', 'format'],
    ['multiple trailing separators', 'auth_token=a;ct0=b;;', 'format'],
    ['header block', 'GET / HTTP/1.1\nCookie: auth_token=a;ct0=b', 'format'],
    ['Set-Cookie attributes', 'auth_token=a;ct0=b;Path=/', 'format'],
    ['Set-Cookie header', 'Set-Cookie: auth_token=a;ct0=b', 'format'],
    ['quoted value', 'auth_token="a";ct0=b', 'format'],
    ['invalid key', 'auth_token=a;ct0=b;bad:name=x', 'format'],
    ['unicode value', 'auth_token=a;ct0=秘密', 'format'],
    ['oversized input', 'auth_token=' + 'x'.repeat(32768) + ';ct0=b', 'format'],
  ]) await test(`reject ${name} without echo`, async () => {
    try { parseCookie(input); throw new Error(); }
    catch (error) {
      assert(error instanceof CookieInputError && error.code === code);
      assert(!error.message.includes(input));
    }
  });
  const keys = await crypto.subtle.generateKey({ name: 'RSA-OAEP', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' }, true, ['wrapKey', 'unwrapKey']);
  const pairing = { v: 1, id: 'p'.repeat(43), expiresAt: Date.now() + 170000, deviceName: '客厅电视', spki: encode64(new Uint8Array(await crypto.subtle.exportKey('spki', keys.publicKey))) };
  await test('QR metadata roundtrip', () => {
    const fragment = '#pair=' + encode64(new TextEncoder().encode(JSON.stringify(pairing)));
    assert(JSON.stringify(parseFragment(fragment)) === JSON.stringify(pairing));
  });
  await test('expired metadata', () => rejects(() => validatePairing({ ...pairing, expiresAt: Date.now() - 1 }), 'expired'));
  await test('excess lifetime', () => rejects(() => validatePairing({ ...pairing, expiresAt: Date.now() + 180001 }), 'invalid_pairing'));
  await test('unpredictable ID shape required', () => rejects(() => validatePairing({ ...pairing, id: '1234' }), 'invalid_pairing'));
  await test('device name control characters', () => rejects(() => validatePairing({ ...pairing, deviceName: '\u202Efake' }), 'invalid_pairing'));
  await test('invalid QR', () => rejects(() => parseFragment('#pair=broken'), 'invalid_pairing'));
  await test('verification code binds all context', async () => {
    const code = await verificationCode(pairing);
    assert(/^[A-F0-9]{4}(-[A-F0-9]{4}){2}$/.test(code));
    for (const changed of [{ id: 'q'.repeat(43) }, { expiresAt: pairing.expiresAt - 1 }, { deviceName: '另一台电视' }, { spki: pairing.spki.slice(0, -1) + 'A' }]) {
      assert(await verificationCode({ ...pairing, ...changed }) !== code);
    }
  });
  const session = parseCookie('auth_token=synthetic-secret-token;ct0=synthetic-secret-csrf;ignore=private-extra');
  const envelope = await encryptSession(pairing, session);
  const unwrap = () => crypto.subtle.unwrapKey('raw', decode64(envelope.wrappedKey), keys.privateKey,
    { name: 'RSA-OAEP', label: contextBytes(pairing) }, { name: 'AES-GCM', length: 256 }, false, ['decrypt']);
  const decrypt = async (context, ciphertext = decode64(envelope.ciphertext)) => crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: decode64(envelope.iv), additionalData: contextBytes(context), tagLength: 128 }, await unwrap(), ciphertext);
  await test('encrypted envelope roundtrip', async () => {
    const plaintext = new TextDecoder().decode(await decrypt(pairing));
    assert(plaintext === JSON.stringify(session));
    assert(!plaintext.includes('private-extra'));
    const wire = JSON.stringify(envelope);
    assert(!wire.includes(session.auth_token) && !wire.includes(session.ct0) && !wire.includes('private-extra'));
    assert(decode64(envelope.iv).length === 12);
  });
  await test('ciphertext tampering rejected', async () => {
    const tampered = decode64(envelope.ciphertext); tampered[0] ^= 1;
    await rejects(() => decrypt(pairing, tampered));
  });
  await test('context tampering rejected', () => rejects(() => decrypt({ ...pairing, id: 'r'.repeat(43) })));
  await test('wrong receiver rejected', async () => {
    const other = await crypto.subtle.generateKey({ name: 'RSA-OAEP', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' }, true, ['unwrapKey', 'wrapKey']);
    await rejects(() => crypto.subtle.unwrapKey('raw', decode64(envelope.wrappedKey), other.privateKey,
      { name: 'RSA-OAEP', label: contextBytes(pairing) }, { name: 'AES-GCM', length: 256 }, false, ['decrypt']));
  });
  await test('extra fields refused at encryption boundary', () => rejects(() => encryptSession(pairing, { ...session, extra: 'never-send' }), 'invalid_session'));
  await test('invalid public key refused', () => rejects(() => encryptSession({ ...pairing, spki: 'AAAA' }, session), 'invalid_key'));
  await test('secure submission options and no credentials in URL', async () => {
    await submitEnvelope(envelope, async (url, options) => {
      assert(url === `/api/v1/pairings/${pairing.id}/submission`);
      assert(options.credentials === 'omit' && options.redirect === 'error' && options.cache === 'no-store');
      assert(options.referrerPolicy === 'no-referrer' && options.method === 'POST');
      assert(options.body === JSON.stringify(envelope));
      return { status: 202 };
    });
  });
  for (const [status, code] of [[409, 'consumed'], [404, 'expired'], [410, 'expired'], [429, 'rate_limited'], [500, 'submit_failed'], [200, 'submit_failed']]) {
    await test(`submission status ${status}`, () => rejects(() => submitEnvelope(envelope, async () => ({ status })), code));
  }
  await test('network uncertainty never retries', async () => {
    let calls = 0;
    await rejects(() => submitEnvelope(envelope, async () => { calls++; throw new Error('sensitive server content'); }), 'outcome_unknown');
    assert(calls === 1);
  });
  await test('expired envelope never submitted', async () => {
    let calls = 0;
    await rejects(() => submitEnvelope({ ...envelope, expiresAt: Date.now() - 1 }, async () => { calls++; }), 'expired');
    assert(calls === 0);
  });
  return { passed: results.filter((r) => r.ok).length, failed: results.filter((r) => !r.ok).length, results };
}
