// Version 1 envelope: RSA-OAEP(SHA-256) wraps an AES-256-GCM content key.
// QR data authenticates the TV key; the relay never supplies a replacement key.
import { forgeAvailable, forgeDigest, forgeEncrypt } from './lan-crypto.mjs';
const encoder = new TextEncoder();
const ID = /^[A-Za-z0-9_-]{32,64}$/;
const MAX_LIFETIME_MS = 180000;
export class PairingError extends Error {
  constructor(code) { super(code); this.name = 'PairingError'; this.code = code; }
}
export function encode64(bytes) {
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
export function decode64(text) {
  if (typeof text !== 'string' || !/^[A-Za-z0-9_-]+$/.test(text) || text.length > 2048) throw new PairingError('invalid_pairing');
  try {
    const binary = atob(text.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - text.length % 4) % 4));
    const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0));
    if (encode64(bytes) !== text) throw new Error();
    return bytes;
  } catch { throw new PairingError('invalid_pairing'); }
}
export function validatePairing(value, now = Date.now()) {
  if (!value || value.v !== 1 || !ID.test(value.id) || !Number.isSafeInteger(value.expiresAt) ||
      typeof value.deviceName !== 'string' || !value.deviceName.trim() || value.deviceName.length > 80 ||
      /[\x00-\x1F\x7F\u202A-\u202E\u2066-\u2069]/.test(value.deviceName) || typeof value.spki !== 'string') {
    throw new PairingError('invalid_pairing');
  }
  if (value.expiresAt <= now) throw new PairingError('expired');
  if (value.expiresAt - now > MAX_LIFETIME_MS) throw new PairingError('invalid_pairing');
  decode64(value.spki);
  return Object.freeze({ v: 1, id: value.id, expiresAt: value.expiresAt, deviceName: value.deviceName, spki: value.spki });
}
export function parseFragment(fragment, now = Date.now()) {
  if (typeof fragment !== 'string' || fragment.length > 4096 || !fragment.startsWith('#pair=')) throw new PairingError('invalid_pairing');
  try {
    const value = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(decode64(fragment.slice(6))));
    return validatePairing(value, now);
  } catch (error) {
    if (error instanceof PairingError) throw error;
    throw new PairingError('invalid_pairing');
  }
}
export function contextBytes(pairing) {
  return encoder.encode(JSON.stringify(['xview-pairing', 1, pairing.id, pairing.expiresAt, pairing.deviceName, pairing.spki]));
}
export async function verificationCode(pairing, cryptoApi = globalThis.crypto) {
  const digest = cryptoApi?.subtle ? new Uint8Array(await cryptoApi.subtle.digest('SHA-256', contextBytes(pairing))) : forgeDigest(contextBytes(pairing));
  const hex = Array.from(digest.slice(0, 6), (n) => n.toString(16).padStart(2, '0')).join('').toUpperCase();
  return hex.match(/.{4}/g).join('-');
}
export async function encryptSession(pairing, session, { now = Date.now(), cryptoApi = globalThis.crypto } = {}) {
  validatePairing(pairing, now);
  if (!cryptoApi?.subtle && !forgeAvailable()) throw new PairingError('crypto_unavailable');
  if (!session || Object.keys(session).sort().join(',') !== 'auth_token,ct0' ||
      !['auth_token', 'ct0'].every((key) => typeof session[key] === 'string' && session[key].length > 0)) throw new PairingError('invalid_session');
  if (!cryptoApi?.subtle) {
    try { return forgeEncrypt(pairing, session, contextBytes(pairing), decode64(pairing.spki), encode64); }
    catch (error) { throw new PairingError(error.message === 'crypto_unavailable' ? error.message : 'invalid_key'); }
  }
  let tvKey;
  try {
    tvKey = await cryptoApi.subtle.importKey('spki', decode64(pairing.spki), { name: 'RSA-OAEP', hash: 'SHA-256' }, false, ['wrapKey']);
    if (tvKey.algorithm.modulusLength !== 2048 || encode64(new Uint8Array(tvKey.algorithm.publicExponent)) !== 'AQAB') throw new Error();
  } catch { throw new PairingError('invalid_key'); }
  const contentKey = await cryptoApi.subtle.generateKey({ name: 'AES-GCM', length: 256 }, true, ['encrypt']);
  const iv = cryptoApi.getRandomValues(new Uint8Array(12));
  const plaintext = encoder.encode(JSON.stringify({ auth_token: session.auth_token, ct0: session.ct0 }));
  try {
    const ciphertext = await cryptoApi.subtle.encrypt({ name: 'AES-GCM', iv, additionalData: contextBytes(pairing), tagLength: 128 }, contentKey, plaintext);
    const wrappedKey = await cryptoApi.subtle.wrapKey('raw', contentKey, tvKey, { name: 'RSA-OAEP', label: contextBytes(pairing) });
    return Object.freeze({ v: 1, algorithm: 'RSA-OAEP-256+A256GCM', id: pairing.id, expiresAt: pairing.expiresAt,
      wrappedKey: encode64(new Uint8Array(wrappedKey)), iv: encode64(iv), ciphertext: encode64(new Uint8Array(ciphertext)) });
  } finally { plaintext.fill(0); }
}

// Do not retry a submission with an uncertain outcome: refresh pairing on the TV.
export async function submitEnvelope(envelope, fetchApi = globalThis.fetch) {
  if (envelope.expiresAt <= Date.now()) throw new PairingError('expired');
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 10000);
  try {
    const response = await fetchApi(`/api/v1/pairings/${envelope.id}/submission`, {
      method: 'POST', credentials: 'omit', cache: 'no-store', redirect: 'error', referrerPolicy: 'no-referrer',
      headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(envelope), signal: controller.signal,
    });
    if (response.status === 202) return;
    if (response.status === 409) throw new PairingError('consumed');
    if (response.status === 404 || response.status === 410) throw new PairingError('expired');
    if (response.status === 429) throw new PairingError('rate_limited');
    throw new PairingError('submit_failed');
  } catch (error) {
    if (error instanceof PairingError) throw error;
    throw new PairingError('outcome_unknown');
  } finally { clearTimeout(timer); }
}
