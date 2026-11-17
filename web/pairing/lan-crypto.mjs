// Local bundled node-forge supports browsers where HTTP disables SubtleCrypto.
// All entropy comes from the browser CSPRNG, never Math.random.
export function forgeAvailable() { return !!globalThis.forge && !!globalThis.crypto?.getRandomValues; }
const binary = (bytes) => String.fromCharCode(...bytes);
export function forgeDigest(bytes) {
  const md = globalThis.forge.md.sha256.create(); md.update(binary(bytes));
  return Uint8Array.from(md.digest().getBytes(), (c) => c.charCodeAt(0));
}
export function forgeEncrypt(pairing, session, context, spki, encode64) {
  if (!forgeAvailable()) throw new Error('crypto_unavailable');
  const f = globalThis.forge;
  const key = f.pki.publicKeyFromAsn1(f.asn1.fromDer(binary(spki)));
  if (key.n.bitLength() !== 2048 || key.e.intValue() !== 65537) throw new Error('invalid_key');
  const aes = crypto.getRandomValues(new Uint8Array(32));
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const seed = crypto.getRandomValues(new Uint8Array(32));
  const plain = new TextEncoder().encode(JSON.stringify({ auth_token: session.auth_token, ct0: session.ct0 }));
  try {
    const cipher = f.cipher.createCipher('AES-GCM', binary(aes));
    cipher.start({ iv: binary(iv), additionalData: binary(context), tagLength: 128 });
    cipher.update(f.util.createBuffer(binary(plain))); if (!cipher.finish()) throw new Error();
    const encrypted = cipher.output.getBytes() + cipher.mode.tag.getBytes();
    const wrapped = key.encrypt(binary(aes), 'RSA-OAEP', { md: f.md.sha256.create(), mgf1: { md: f.md.sha256.create() }, label: binary(context), seed: binary(seed) });
    const bytes = (text) => Uint8Array.from(text, (c) => c.charCodeAt(0));
    return Object.freeze({ v: 1, algorithm: 'RSA-OAEP-256+A256GCM', id: pairing.id, expiresAt: pairing.expiresAt,
      wrappedKey: encode64(bytes(wrapped)), iv: encode64(iv), ciphertext: encode64(bytes(encrypted)) });
  } finally { aes.fill(0); seed.fill(0); plain.fill(0); }
}
