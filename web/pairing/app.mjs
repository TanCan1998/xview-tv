import { parseCookie, CookieInputError } from './cookie.mjs';
import { parseFragment, verificationCode, encryptSession, submitEnvelope, PairingError } from './protocol.mjs';

const useChinese = (navigator.language || 'en').toLowerCase().startsWith('zh');
const english = {
  "XView · 连接电视": "XView · Connect your TV",
  "连接你的电视": "Connect your TV",
  "请先核对电视上的设备名称与校验码，再提交你的 X 会话。": "Check the TV device name and verification code before submitting your X session.",
  "配对设备": "Pairing device",
  "等待有效的电视二维码": "Waiting for a valid TV QR code",
  "有效期 3 分钟": "Expires in 3 minutes",
  "完整 Cookie": "Complete Cookie",
  "从已登录 X 的浏览器复制完整 Cookie 字符串，粘贴到这里。网页会自动提取需要的字段。": "Copy the complete Cookie from a browser signed in to X. Only required fields are extracted.",
  "显示": "Show",
  "隐藏": "Hide",
  "我已核对电视上的设备名称和校验码": "I have checked the device name and code on the TV",
  "加密提交到电视": "Encrypt and submit to TV",
  "请扫描电视上的二维码打开此页。": "Scan the QR code on your TV to open this page.",
  "只在你信任的设备上操作。页面不保存 Cookie；电视会先验证账号。此页面属于 XView，不是 X 官方登录页。": "Use a device you trust. This page does not save Cookies. The TV verifies your account. This is an XView page, not an official X sign-in page.",
  "配对信息无效，请重新扫描电视二维码。": "Invalid pairing. Scan a new QR code on the TV.",
  "电视加密信息无效，请在电视上重新配对。": "Invalid TV encryption key. Start a new pairing on the TV.",
  "配对已过期，请在电视上重新生成二维码。": "Pairing expired. Generate a new QR code on the TV.",
  "此配对已提交或取消，请返回电视查看。": "Pairing submitted or cancelled. Check the TV.",
  "请求过于频繁，请稍后在电视上重新配对。": "Too many requests. Wait, then start a new pairing.",
  "提交失败，请在电视上重新配对。": "Submission failed. Start a new pairing on the TV.",
  "未能确认提交结果，请查看电视；请勿重复提交。": "Submission outcome unknown. Check the TV; do not submit again.",
  "当前浏览器无法安全加密，请使用新版浏览器。": "Secure encryption unavailable. Use a current browser.",
  "无法处理此配对，请重新扫描电视二维码。": "Could not process pairing. Scan a new QR code.",
  "核对设备与校验码后，粘贴完整 Cookie。": "Check the device and code, then paste the complete Cookie.",
  "请只粘贴单个 Cookie 字符串，不要包含换行或整段请求头。": "Paste one Cookie string without line breaks or other request headers.",
  "Cookie 格式不正确。": "Invalid Cookie. Paste a complete request Cookie.",
  "正在加密并提交…": "Encrypting and submitting…",
  "加密会话已提交。请返回电视，等待验证并确认账号。": "Encrypted session submitted. Return to the TV to verify and confirm the account.",
  "页面已关闭，请重新扫描电视二维码。": "Page closed. Scan a new QR code on the TV."
};
const t = (text) => useChinese ? text : (english[text] || text);
if (!useChinese) {
  document.documentElement.lang = 'en';
  document.title = t(document.title);
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  let node;
  while ((node = walker.nextNode())) {
    const trimmed = node.textContent.trim();
    if (english[trimmed]) node.textContent = node.textContent.replace(trimmed, english[trimmed]);
  }
  document.querySelectorAll('[aria-label]').forEach((element) => element.setAttribute('aria-label', t(element.getAttribute('aria-label'))));
}

const element = (id) => document.getElementById(id);
const fields = element('fields');
const cookie = element('cookie');
const status = element('status');
const messages = {
  invalid_pairing: t("配对信息无效，请重新扫描电视二维码。"),
  invalid_key: t("电视加密信息无效，请在电视上重新配对。"),
  expired: t("配对已过期，请在电视上重新生成二维码。"),
  consumed: t("此配对已提交或取消，请返回电视查看。"),
  rate_limited: t("请求过于频繁，请稍后在电视上重新配对。"),
  submit_failed: t("提交失败，请在电视上重新配对。"),
  outcome_unknown: t("未能确认提交结果，请查看电视；请勿重复提交。"),
  crypto_unavailable: t("当前浏览器无法安全加密，请使用新版浏览器。"),
};
let pairing = null;
let busy = false;
let finished = false;
function clearInput() {
  cookie.value = '';
  cookie.type = 'password';
  element('visibility').textContent = t("显示");
  element('visibility').setAttribute('aria-pressed', 'false');
  element('confirm').checked = false;
}
function fail(error) {
  status.textContent = messages[error?.code] || t("无法处理此配对，请重新扫描电视二维码。");
}
async function initialize() {
  const fragment = location.hash;
  // Pairing metadata has no credentials. Remove even that from visible history.
  history.replaceState(null, '', location.pathname);
  if (!globalThis.crypto?.getRandomValues || (!globalThis.crypto?.subtle && !globalThis.forge)) throw new PairingError('crypto_unavailable');
  pairing = parseFragment(fragment);
  const code = await verificationCode(pairing);
  element('device-name').textContent = pairing.deviceName;
  element('verification-code').textContent = code;
  tick();
  if (pairing) {
    fields.disabled = false;
    status.textContent = t("核对设备与校验码后，粘贴完整 Cookie。");
  }
}
function tick() {
  if (!pairing || finished) return;
  const seconds = Math.ceil((pairing.expiresAt - Date.now()) / 1000);
  if (seconds <= 0) {
    fields.disabled = true;
    clearInput();
    pairing = null;
    fail(new PairingError('expired'));
    return;
  }
  element('expires').textContent = `${useChinese ? "剩余 " : "Remaining "}${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}
cookie.addEventListener('paste', (event) => {
  // Password inputs strip line breaks before validation; inspect the raw paste first.
  const text = event.clipboardData?.getData('text');
  if (text && /[\r\n]/.test(text)) {
    event.preventDefault();
    clearInput();
    status.textContent = t("请只粘贴单个 Cookie 字符串，不要包含换行或整段请求头。");
  }
});
element('visibility').addEventListener('click', () => {
  const visible = cookie.type === 'password';
  cookie.type = visible ? 'text' : 'password';
  element('visibility').textContent = visible ? t("隐藏") : t("显示");
  element('visibility').setAttribute('aria-pressed', String(visible));
});
element('pairing-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  if (busy || finished || !pairing || !element('confirm').checked) return;
  busy = true;
  fields.disabled = true;
  let session;
  try {
    session = parseCookie(cookie.value);
  } catch (error) {
    clearInput();
    fields.disabled = false;
    busy = false;
    status.textContent = error instanceof CookieInputError && useChinese ? error.message : t("Cookie 格式不正确。");
    tick();
    return;
  }
  clearInput();
  status.textContent = t("正在加密并提交…");
  try {
    const envelope = await encryptSession(pairing, session);
    session = null;
    await submitEnvelope(envelope);
    finished = true;
    status.textContent = t("加密会话已提交。请返回电视，等待验证并确认账号。");
  } catch (error) {
    finished = true; // A new TV pairing is required, including uncertain POST outcomes.
    fail(error);
  } finally {
    session = null;
    pairing = null;
    busy = false;
    clearInput();
    fields.disabled = true;
  }
});
// Sensitive strings cannot be guaranteed zeroized by JavaScript GC.
addEventListener('pagehide', () => { clearInput(); pairing = null; finished = true; fields.disabled = true; });
addEventListener('pageshow', (event) => { if (event.persisted) status.textContent = t("页面已关闭，请重新扫描电视二维码。"); });
setInterval(tick, 1000);
initialize().catch((error) => { fields.disabled = true; clearInput(); pairing = null; fail(error); });
