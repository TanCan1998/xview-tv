# Cookie 配对网页组件

此目录随正式APK打包，由电视在局域网临时提供：完整Cookie单框解析、设备校验码、180秒有效期、密文提交与TV解密接收已实现。手机同电视联网，扫描电视二维码；不使用公网HTTPS或中继。电视通过X网页验证账号并要求用户确认后才加密保存。实现与信任边界见[ADR-006](../../docs/adr/006-qr-cookie-login.md)。

## 本地验证

使用 Python 3 启动静态服务，仅监听回环地址：

```sh
python3 -m http.server 8765 --bind 127.0.0.1 --directory web/pairing
```

浏览器打开 `http://127.0.0.1:8765/tests/index.html`，运行 43 项解析/加密/提交单测与 8 项真实 DOM 交互测试，全部只使用合成凭据。Node 20+ 可运行不依赖 DOM 的单测：

```sh
node web/pairing/tests/node.mjs
node web/pairing/tests/lan-node.mjs
```

回环HTTP是Web Crypto的开发例外；真实局域网HTTP使用内置node-forge1.4.3，熵始终来自浏览器crypto.getRandomValues。lan-node测试验证与WebCrypto的校验码、OAEP/MGF1/label、AES-GCM互操作与篡改拒绝。静态测试服务没有TV接收端，不用于真实Cookie。`tests/`不被服务允许列表提供。HTTP页面脚本不具备TLS完整性，须在可信局域网使用；应用层加密不能抵御页面脚本被主动替换。

## v1 配对数据及密文格式

手机端与TV/JCA接收端已完成真实互操作；仍须完成其他系统版本、安全审查与完整异常矩阵。

TV 使用 CSPRNG 生成至少 32 字节随机配对 ID，Base64URL 无填充编码（43字符），每次配对创建新的 RSA-2048 / e=65537 临时密钥。二维码只包含页面地址和 `#pair=<Base64URL(UTF8(JSON))>`；JSON 字段为：

```json
{
  "v": 1,
  "id": "<random pairing id>",
  "expiresAt": 1794834000000,
  "deviceName": "客厅电视",
  "spki": "<Base64URL DER SubjectPublicKeyInfo>"
}
```

`expiresAt` 为 Unix 毫秒，最长180秒。页面拒绝未知版本、畸形ID、过期或超过最大时限的会话；设备名称作为纯文本显示。二维码中的公钥直接由 TV 提供，页面不会向中继查询或接受替代公钥。二维码元数据会在初始化时从当前历史项移除，不含 Cookie 或 TV 私钥/领取令牌。

UTF-8 上下文精确为无空白 JSON 数组（中文字符不转义）：

`["xview-pairing",1,id,expiresAt,deviceName,spki]`

校验码为该上下文 SHA-256 的前6字节大写十六进制，按4字符分组：`XXXX-XXXX-XXXX`。TV 必须独立计算并显示相同校验码，手机要求用户明确核对；设备名称不构成身份认证。该比较用于核对TV密钥，不可省略。通过HTTP传输的页面脚本本身不受TLS保护；局域网主动篡改页面时，应用层密文与校验码不能保护被替换脚本中的Cookie输入，因此依赖可信局域网和浏览器。

每次提交生成 AES-256-GCM 密钥与12字节随机 IV，将且仅将 `{auth_token,ct0}` 的 JSON UTF-8 加密。GCM 的 AAD 为上述上下文，标签128位，密文字段包含尾部标签。AES原始密钥使用 RSA-OAEP 包装：SHA-256、MGF1-SHA-256、label 为上述上下文。TV/JCA 实现必须显式配置 MGF1 SHA-256 和 label，不能依赖 Android Provider 默认值；最低支持API23，需持续覆盖不同厂商的Provider。

页面 POST `/api/v1/pairings/{id}/submission`，JSON 字段如下；所有二进制均为无填充 Base64URL：

```json
{
  "v": 1,
  "algorithm": "RSA-OAEP-256+A256GCM",
  "id": "<pairing id>",
  "expiresAt": 1794834000000,
  "wrappedKey": "<RSA ciphertext>",
  "iv": "<12 bytes>",
  "ciphertext": "<AES ciphertext and GCM tag>"
}
```

手机不发送认证 Cookie、跨域请求或跟随重定向。只接受202为提交已接收，409已消费/取消，404/410过期，429限流；其余为失败。10秒超时或网络错误视为结果未知，不自动重试。成功文案仅要求返回TV等待验证，不能表示已经登录。

## TV 局域网服务与生命周期

- TV绑定一个私有IPv4地址与随机端口；仅允许页面、CSS、模块和固定密码库，Host精确匹配，POST要求同源Origin与application/json；禁止分块请求，头部8KiB、正文64KiB上限，单连接4秒超时、总180请求上限。
- ID与RSA临时密钥每轮随机创建；提交绑定版本、算法、ID、原始有效期及完整AAD，AES-GCM认证成功后原子单次消费。无注册/领取API，无公网中继。未知路径与第三方资源不提供，不记录Cookie或正文。
- 取消、过期、切后台、离开登录页均关闭端口；重新扫码创建新密钥与ID。TV验证身份后显示候选，用户确认才替换现有账号；失败保留旧账号。
- 响应提供CSP（frame-ancestors none）、no-store、no-referrer、nosniff及Connection close。浏览器本地网络权限未授予时，在应用权限手动允许后重新扫码。
- 404/410/409状态是网页协议可处理的失败；实际TV消费或取消后通常立即关闭端口，重复连接可能表现为网络失败，页面要求回电视查看，不自动重试。

## 输入清理边界

接受可选 `Cookie:` 前缀、字段周围空白、值内等号、一个末尾分号、值相同的重复必需项。拒绝换行/控制字符、畸形字段、Set-Cookie头及属性、缺失/空项、必需项冲突。值遵守未加引号的Cookie字符范围；最大32KiB。错误只显示格式或字段名。

输入默认遮蔽，可显式显示；成功解析后、解析失败、过期及pagehide立即清空表单，返回缓存页面要求重新扫码。不使用持久存储、自动剪贴板或遥测。编码后的明文字节会清零，但JavaScript字符串和CryptoKey仍由浏览器GC管理，不能承诺内存取证级清零。
