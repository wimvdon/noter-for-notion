// Tests for the sign-in helper, with Node's own test runner and a stand-in for Notion:
//   node --test worker/test/noter-sign-in.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { parseState, seal } from "../noter-sign-in.mjs";

const env = { NOTION_CLIENT_ID: "client", NOTION_CLIENT_SECRET: "secret" };
const tokens = { access_token: "ntn_access", refresh_token: "ntn_refresh", workspace_id: "ws", workspace_name: "Home" };

/** Replaces fetch with [answer] for one test; returns what was sent to Notion. */
function fakeNotion(t, answer) {
  const sent = [];
  const original = globalThis.fetch;
  globalThis.fetch = async (url, init) => {
    sent.push({ url, init, body: JSON.parse(init.body) });
    const [status, body] = answer;
    return new Response(JSON.stringify(body), { status });
  };
  t.after(() => {
    globalThis.fetch = original;
  });
  return sent;
}

async function phoneKeys() {
  const keys = await crypto.subtle.generateKey({ name: "ECDH", namedCurve: "P-256" }, false, ["deriveBits"]);
  const raw = new Uint8Array(await crypto.subtle.exportKey("raw", keys.publicKey));
  return { keys, raw, encoded: Buffer.from(raw).toString("base64url") };
}

/** What the app does with a sealed result (OAuthBox.open in the app). */
async function open(privateKey, sealed) {
  const [publicKey, nonce, ciphertext] = sealed.split(".").map((part) => Buffer.from(part, "base64url"));
  const theirs = await crypto.subtle.importKey("raw", publicKey, { name: "ECDH", namedCurve: "P-256" }, false, []);
  const shared = await crypto.subtle.deriveBits({ name: "ECDH", public: theirs }, privateKey, 256);
  const hkdf = await crypto.subtle.importKey("raw", shared, "HKDF", false, ["deriveKey"]);
  const key = await crypto.subtle.deriveKey(
    { name: "HKDF", hash: "SHA-256", salt: new Uint8Array(32), info: new TextEncoder().encode("noter-sign-in-v1") },
    hkdf,
    { name: "AES-GCM", length: 256 },
    false,
    ["decrypt"],
  );
  return new TextDecoder().decode(await crypto.subtle.decrypt({ name: "AES-GCM", iv: nonce }, key, ciphertext));
}

function linkIn(html) {
  return html.match(/<a href="([^"]+)"/)[1].replace(/&amp;/g, "&");
}

test("the callback trades the code with the secret and seals the tokens for the phone", async (t) => {
  const sent = fakeNotion(t, [200, tokens]);
  const phone = await phoneKeys();
  const state = `be.wimvandonck.noter~${phone.encoded}`;
  const response = await worker.fetch(new Request(`https://helper.example/callback?code=abc&state=${state}`), env);

  assert.equal(response.status, 200);
  assert.equal(sent.length, 1);
  assert.equal(sent[0].url, "https://api.notion.com/v1/oauth/token");
  assert.equal(sent[0].init.headers.Authorization, `Basic ${btoa("client:secret")}`);
  assert.deepEqual(sent[0].body, { grant_type: "authorization_code", code: "abc", redirect_uri: "https://helper.example/callback" });

  const html = await response.text();
  assert.ok(!html.includes("ntn_access"), "the page must not show the token");
  const link = new URL(linkIn(html));
  assert.equal(link.protocol, "be.wimvandonck.noter:");
  assert.deepEqual(JSON.parse(await open(phone.keys.privateKey, link.searchParams.get("result"))), tokens);
});

test("the debug app gets its own link", async (t) => {
  fakeNotion(t, [200, tokens]);
  const phone = await phoneKeys();
  const response = await worker.fetch(new Request(`https://h.example/callback?code=abc&state=be.wimvandonck.noter.debug~${phone.encoded}`), env);
  assert.ok(linkIn(await response.text()).startsWith("be.wimvandonck.noter.debug://oauth?result="));
});

test("refusing access goes back to the app without asking Notion", async (t) => {
  const sent = fakeNotion(t, [200, tokens]);
  const phone = await phoneKeys();
  const response = await worker.fetch(new Request(`https://h.example/callback?error=access_denied&state=be.wimvandonck.noter~${phone.encoded}`), env);
  assert.equal(linkIn(await response.text()), "be.wimvandonck.noter://oauth?error=access_denied");
  assert.equal(sent.length, 0);
});

test("Notion refusing the code goes back to the app as an error", async (t) => {
  fakeNotion(t, [400, { error: "invalid_grant" }]);
  const phone = await phoneKeys();
  const response = await worker.fetch(new Request(`https://h.example/callback?code=old&state=be.wimvandonck.noter~${phone.encoded}`), env);
  assert.equal(linkIn(await response.text()), "be.wimvandonck.noter://oauth?error=invalid_grant");
});

test("a state that isn't Noter's is refused, and nothing is sent to Notion", async (t) => {
  const sent = fakeNotion(t, [200, tokens]);
  const phone = await phoneKeys();
  for (const state of [`evil.app~${phone.encoded}`, "be.wimvandonck.noter~short", "", `javascript~${phone.encoded}`]) {
    const response = await worker.fetch(new Request(`https://h.example/callback?code=abc&state=${encodeURIComponent(state)}`), env);
    assert.equal(response.status, 400, state);
  }
  assert.equal(sent.length, 0);
});

test("parseState accepts only a known scheme and a P-256 public key", async () => {
  const phone = await phoneKeys();
  assert.deepEqual(parseState(`be.wimvandonck.noter~${phone.encoded}`), { scheme: "be.wimvandonck.noter", key: phone.raw });
  assert.equal(parseState(`be.wimvandonck.noter~${phone.encoded}~more`), null);
  assert.equal(parseState(`be.wimvandonck.noter~${Buffer.alloc(65).toString("base64url")}`), null);
  assert.equal(parseState(null), null);
});

test("refresh adds the secret and passes Notion's answer on", async (t) => {
  const sent = fakeNotion(t, [200, tokens]);
  const response = await worker.fetch(
    new Request("https://h.example/refresh", { method: "POST", body: JSON.stringify({ refresh_token: "r1" }) }),
    env,
  );
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), tokens);
  assert.deepEqual(sent[0].body, { grant_type: "refresh_token", refresh_token: "r1" });
  assert.equal(sent[0].init.headers.Authorization, `Basic ${btoa("client:secret")}`);
});

test("refresh passes Notion's refusal on with its status", async (t) => {
  fakeNotion(t, [400, { error: "invalid_grant" }]);
  const response = await worker.fetch(
    new Request("https://h.example/refresh", { method: "POST", body: JSON.stringify({ refresh_token: "old" }) }),
    env,
  );
  assert.equal(response.status, 400);
  assert.deepEqual(await response.json(), { error: "invalid_grant" });
});

test("refresh without a token isn't sent to Notion", async (t) => {
  const sent = fakeNotion(t, [200, tokens]);
  for (const body of ["{}", "not json", JSON.stringify({ refresh_token: 5 })]) {
    const response = await worker.fetch(new Request("https://h.example/refresh", { method: "POST", body }), env);
    assert.equal(response.status, 400);
  }
  assert.equal(sent.length, 0);
});

test("other paths and methods are not found", async () => {
  assert.equal((await worker.fetch(new Request("https://h.example/refresh"), env)).status, 404);
  assert.equal((await worker.fetch(new Request("https://h.example/token", { method: "POST" }), env)).status, 404);
  assert.equal((await worker.fetch(new Request("https://h.example/"), env)).status, 200);
});

test("a sealed message opens only with the phone's key", async () => {
  const phone = await phoneKeys();
  const other = await phoneKeys();
  const sealed = await seal(phone.raw, "hello");
  assert.equal(await open(phone.keys.privateKey, sealed), "hello");
  await assert.rejects(open(other.keys.privateKey, sealed));
});
