// Noter's sign-in helper: a Cloudflare Worker that does the two OAuth steps Notion only allows
// with the connection's client secret, which can't be kept inside an app.
//
//   GET  /callback  Notion sends the person back here after "Allow access". The helper trades the
//                   code for tokens, seals them for the phone that asked (only that phone can open
//                   them) and sends them on to the app.
//   POST /refresh   The app sends a refresh token, the helper adds the secret and returns Notion's
//                   answer (a new access token and refresh token).
//
// It stores nothing and logs nothing. Settings (Cloudflare dashboard → the Worker → Settings →
// Variables and secrets): NOTION_CLIENT_ID and NOTION_CLIENT_SECRET, both as secrets.
// See worker/README.md.

const NOTION_TOKEN_URL = "https://api.notion.com/v1/oauth/token";

/** The apps that may receive tokens: the released app and the debug build ("Noter Dev"). */
const APP_SCHEMES = ["be.wimvandonck.noter", "be.wimvandonck.noter.debug"];

/** Mixed into the key that seals the tokens; the app uses the same words (OAuthBox.kt). */
const SEAL_INFO = "noter-sign-in-v1";

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    try {
      if (url.pathname === "/callback" && request.method === "GET") return await callback(url, env);
      if (url.pathname === "/refresh" && request.method === "POST") return await refresh(request, env);
      if (url.pathname === "/") return text(200, "Noter's sign-in helper. It stores nothing.");
      return text(404, "Not found");
    } catch {
      // Never echo what went wrong: it could hold a token.
      return text(500, "Something went wrong");
    }
  },
};

/**
 * Notion's redirect after the person allowed (or refused) access. `state` is what the app sent:
 * its scheme and a fresh public key, `<scheme>~<key>`.
 */
async function callback(url, env) {
  const state = parseState(url.searchParams.get("state"));
  if (!state) return text(400, "This link didn't come from Noter. Start again from Noter's settings.");
  const back = `${state.scheme}://oauth`;
  const code = url.searchParams.get("code");
  if (!code) return openApp(`${back}?error=${encodeURIComponent(url.searchParams.get("error") || "cancelled")}`);

  const answer = await notionToken(env, {
    grant_type: "authorization_code",
    code,
    redirect_uri: `${url.origin}/callback`,
  });
  if (!answer.ok) return openApp(`${back}?error=${encodeURIComponent(answer.body.error || "notion")}`);
  const sealed = await seal(state.key, JSON.stringify(answer.body));
  return openApp(`${back}?result=${sealed}`);
}

/** A new access token for a refresh token. Notion's answer goes back as it is. */
async function refresh(request, env) {
  let token;
  try {
    token = (await request.json()).refresh_token;
  } catch {
    token = null;
  }
  if (typeof token !== "string" || token.length === 0) return json(400, { error: "invalid_request" });
  const answer = await notionToken(env, { grant_type: "refresh_token", refresh_token: token });
  return json(answer.status, answer.body);
}

async function notionToken(env, body) {
  const response = await fetch(NOTION_TOKEN_URL, {
    method: "POST",
    headers: {
      Authorization: `Basic ${btoa(`${env.NOTION_CLIENT_ID}:${env.NOTION_CLIENT_SECRET}`)}`,
      "Content-Type": "application/json",
      "Notion-Version": "2026-03-11",
    },
    body: JSON.stringify(body),
  });
  let data;
  try {
    data = await response.json();
  } catch {
    data = { error: "notion" };
  }
  return { ok: response.ok, status: response.status, body: data };
}

/** `<scheme>~<public key>` with a known scheme and a P-256 public key (65 bytes), else null. */
export function parseState(state) {
  if (typeof state !== "string") return null;
  const [scheme, key, ...rest] = state.split("~");
  if (rest.length > 0 || !APP_SCHEMES.includes(scheme)) return null;
  let raw;
  try {
    raw = fromBase64Url(key);
  } catch {
    return null;
  }
  if (raw.length !== 65 || raw[0] !== 4) return null;
  return { scheme, key: raw };
}

/**
 * Seals [plaintext] for the holder of the private key of [recipient] (a raw P-256 public key):
 * ECDH with a one-time key, HKDF-SHA-256 and AES-256-GCM. The result is
 * `<one-time public key>.<nonce>.<ciphertext>`, each part in base64url.
 */
export async function seal(recipient, plaintext) {
  const theirs = await crypto.subtle.importKey("raw", recipient, { name: "ECDH", namedCurve: "P-256" }, false, []);
  const mine = await crypto.subtle.generateKey({ name: "ECDH", namedCurve: "P-256" }, false, ["deriveBits"]);
  const shared = await crypto.subtle.deriveBits({ name: "ECDH", public: theirs }, mine.privateKey, 256);
  const hkdf = await crypto.subtle.importKey("raw", shared, "HKDF", false, ["deriveKey"]);
  const key = await crypto.subtle.deriveKey(
    { name: "HKDF", hash: "SHA-256", salt: new Uint8Array(32), info: new TextEncoder().encode(SEAL_INFO) },
    hkdf,
    { name: "AES-GCM", length: 256 },
    false,
    ["encrypt"],
  );
  const nonce = crypto.getRandomValues(new Uint8Array(12));
  const ciphertext = await crypto.subtle.encrypt({ name: "AES-GCM", iv: nonce }, key, new TextEncoder().encode(plaintext));
  const publicKey = await crypto.subtle.exportKey("raw", mine.publicKey);
  return [publicKey, nonce, ciphertext].map((part) => toBase64Url(new Uint8Array(part))).join(".");
}

/**
 * A page that opens the app at [link]: at once where the browser allows it, else with the button.
 */
function openApp(link) {
  const href = link.replace(/&/g, "&amp;").replace(/"/g, "&quot;");
  const page = `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<meta http-equiv="refresh" content="0;url=${href}"><title>Noter</title>
<style>body{font-family:system-ui,sans-serif;margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#fff8f0;color:#1f1b16}
@media (prefers-color-scheme:dark){body{background:#1f1b16;color:#ece0d4}}
a{background:#e8710a;color:#fff;text-decoration:none;padding:14px 28px;border-radius:100px;font-size:16px}</style></head>
<body><a href="${href}">Open Noter</a></body></html>`;
  return new Response(page, {
    status: 200,
    headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store", "Referrer-Policy": "no-referrer" },
  });
}

function text(status, body) {
  return new Response(body, { status, headers: { "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "no-store" } });
}

function json(status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}

function toBase64Url(bytes) {
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function fromBase64Url(text) {
  if (!/^[A-Za-z0-9_-]*$/.test(text)) throw new Error("not base64url");
  const binary = atob(text.replace(/-/g, "+").replace(/_/g, "/"));
  return Uint8Array.from(binary, (c) => c.charCodeAt(0));
}
