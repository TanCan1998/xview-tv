import { encode64 } from '../protocol.mjs';

export async function runUiTests() {
  const results = [];
  const assert = (value) => { if (!value) throw new Error('assertion'); };
  const delay = () => new Promise((resolve) => setTimeout(resolve, 25));
  const waitFor = async (check) => {
    for (let i = 0; i < 120; i++) { if (check()) return; await delay(); }
    throw new Error('timeout');
  };
  const keys = await crypto.subtle.generateKey({ name: 'RSA-OAEP', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' }, true, ['wrapKey', 'unwrapKey']);
  const base = { v: 1, id: 'u'.repeat(43), expiresAt: Date.now() + 170000, deviceName: '<script>synthetic</script>', spki: encode64(new Uint8Array(await crypto.subtle.exportKey('spki', keys.publicKey))) };
  const test = async (name, task, metadata = base) => {
    const iframe = document.createElement('iframe');
    iframe.title = name;
    iframe.style.width = '380px'; iframe.style.height = '300px';
    try {
      const loaded = new Promise((resolve) => iframe.addEventListener('load', resolve, { once: true }));
      iframe.src = '../index.html' + (metadata ? '#pair=' + encode64(new TextEncoder().encode(JSON.stringify(metadata))) : '');
      document.body.append(iframe);
      await loaded;
      const win = iframe.contentWindow;
      const get = (id) => win.document.getElementById(id);
      if (metadata) await waitFor(() => !get('fields').disabled);
      else await waitFor(() => get('status').textContent.includes('无效'));
      await task(win, get);
      results.push({ name, ok: true });
    } catch { results.push({ name, ok: false }); }
    finally { iframe.remove(); }
  };
  const fill = (get) => { get('cookie').value = 'auth_token=ui-test-token;ct0=ui-test-csrf;other=discard'; get('confirm').checked = true; };
  await test('UI invalid QR keeps form disabled', (_, get) => assert(get('fields').disabled), null);
  await test('UI masks input and escapes device name', (win, get) => {
    assert(get('cookie').type === 'password');
    assert(get('device-name').textContent === base.deviceName && !get('device-name').querySelector('script'));
    assert(win.location.hash === '');
    get('visibility').click(); assert(get('cookie').type === 'text');
    get('visibility').click(); assert(get('cookie').type === 'password');
  });
  await test('UI rejects missing fields and clears input', async (_, get) => {
    get('cookie').value = 'auth_token=bad'; get('confirm').checked = true;
    get('pairing-form').requestSubmit();
    await waitFor(() => get('status').textContent.includes('ct0'));
    assert(get('cookie').value === '' && !get('confirm').checked && !get('fields').disabled);
    assert(!get('status').textContent.includes('bad'));
  });
  await test('UI refuses multiline paste before browser normalization', (win, get) => {
    const clipboardData = new win.DataTransfer();
    clipboardData.setData('text/plain', 'Cookie: auth_token=synthetic\r\nct0=synthetic');
    const event = new win.ClipboardEvent('paste', { clipboardData, bubbles: true, cancelable: true });
    get('cookie').dispatchEvent(event);
    assert(event.defaultPrevented && get('cookie').value === '');
    assert(get('status').textContent.includes('换行'));
  });
  await test('UI requires explicit device confirmation', async (win, get) => {
    let calls = 0; win.fetch = async () => { calls++; return { status: 202 }; };
    fill(get); get('confirm').checked = false;
    get('pairing-form').dispatchEvent(new win.Event('submit', { cancelable: true }));
    await delay(); assert(calls === 0);
  });
  await test('UI submits encrypted fields once and never claims login success', async (win, get) => {
    let calls = 0;
    win.fetch = async (url, options) => {
      calls++;
      assert(!options.body.includes('ui-test') && !options.body.includes('discard'));
      assert(!url.includes('auth_token'));
      return { status: 202 };
    };
    fill(get); get('pairing-form').requestSubmit();
    get('pairing-form').dispatchEvent(new win.Event('submit', { cancelable: true }));
    await waitFor(() => get('status').textContent.includes('已提交'));
    assert(calls === 1 && get('fields').disabled && get('cookie').value === '');
    assert(get('status').textContent.includes('等待验证') && !get('status').textContent.includes('登录成功'));
  });
  await test('UI uncertain network result locks submission', async (win, get) => {
    let calls = 0; win.fetch = async () => { calls++; throw new Error('never show raw detail'); };
    fill(get); get('pairing-form').requestSubmit();
    await waitFor(() => get('status').textContent.includes('未能确认'));
    assert(get('cookie').value === '' && get('fields').disabled && calls === 1);
    assert(!get('status').textContent.includes('raw detail'));
  });
  await test('UI expiry clears and disables credentials', async (_, get) => {
    fill(get);
    await waitFor(() => get('status').textContent.includes('过期'));
    assert(get('cookie').value === '' && !get('confirm').checked && get('fields').disabled);
  }, { ...base, expiresAt: Date.now() + 2500 });
  return results;
}
