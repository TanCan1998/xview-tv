// Pure parsing: never include input or values in errors.
export class CookieInputError extends Error {
  constructor(code, field = null) {
    super(field ? `请检查 ${field} 字段。` : 'Cookie 格式不正确，请粘贴单个完整 Cookie 字符串。');
    this.name = 'CookieInputError';
    this.code = code;
    this.field = field;
  }
}

const TOKEN = /^[!#$%&'*+\-.^_`|~0-9A-Za-z]+$/;
const VALUE = /^[\x21\x23-\x2B\x2D-\x3A\x3C-\x5B\x5D-\x7E]*$/;
const ATTRIBUTES = new Set(['domain', 'path', 'expires', 'max-age', 'secure', 'httponly', 'samesite', 'partitioned']);
const REQUIRED = new Set(['auth_token', 'ct0']);
export const MAX_COOKIE_LENGTH = 32768;

export function parseCookie(input) {
  if (typeof input !== 'string' || !input.length || input.length > MAX_COOKIE_LENGTH || /[\r\n\x00-\x08\x0B-\x1F\x7F]/.test(input)) {
    throw new CookieInputError('format');
  }
  const text = input.trim().replace(/^cookie\s*:\s*/i, '');
  const segments = text.split(';');
  if (!segments[segments.length - 1].trim()) segments.pop();
  const session = Object.create(null);
  for (const segment of segments) {
    const part = segment.trim();
    const index = part.indexOf('=');
    if (index < 1) throw new CookieInputError('format');
    const name = part.slice(0, index).trim();
    const value = part.slice(index + 1).trim();
    if (!TOKEN.test(name) || ATTRIBUTES.has(name.toLowerCase())) {
      throw new CookieInputError('format');
    }
    if (!REQUIRED.has(name)) continue;
    if (!value) throw new CookieInputError('empty', name);
    if (!VALUE.test(value)) throw new CookieInputError('format', name);
    if (session[name] !== undefined && session[name] !== value) throw new CookieInputError('duplicate', name);
    session[name] = value;
  }
  for (const name of REQUIRED) {
    if (!session[name]) throw new CookieInputError('missing', name);
  }
  // Only these fields leave the parser; extra cookies are discarded.
  return Object.freeze({ auth_token: session.auth_token, ct0: session.ct0 });
}
