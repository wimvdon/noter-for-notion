# Noter's sign-in helper

**Connect to Notion** (OAuth) needs a secret that Notion gives to Noter's public connection. A
secret inside an app isn't secret, so this small helper keeps it. It runs as a free Cloudflare
Worker and does only two things:

- **`/callback`**: Notion sends you here after you tap *Allow access*. The helper trades Notion's
  code for your tokens, seals them so that only the phone that asked can read them, and opens
  Noter again.
- **`/refresh`**: when Notion wants a new token, the app sends its refresh token here and gets the
  new tokens back.

It stores nothing and logs no tokens (see `PRIVACY.md`). The code is one file with no
dependencies: `noter-sign-in.mjs`. Tests: `node --test worker/test/noter-sign-in.test.mjs`.

## Setting it up (once)

Until this is done, the app only offers tokens, as before.

### 1. Create the Worker
1. Make a free account at <https://dash.cloudflare.com>.
2. **Workers & Pages › Create › Worker** (start from "Hello World"), name it `noter-sign-in` and
   tap **Deploy**.
3. **Edit code**: replace everything with the contents of `worker/noter-sign-in.mjs` and tap
   **Deploy**.
4. Note its address, for example `https://noter-sign-in.your-name.workers.dev`. Opening it shows
   "Noter's sign-in helper. It stores nothing."

### 2. Create Noter's public connection in Notion
1. Open <https://www.notion.so/profile/integrations> and make a **new integration** of the
   **public** type, named **Noter**, with the Noter icon.
2. **Redirect URI**: the Worker's address followed by `/callback`, for example
   `https://noter-sign-in.your-name.workers.dev/callback`.
3. Fill in what Notion asks for (website, privacy policy: the link to `PRIVACY.md` on GitHub,
   support email).
4. **Capabilities**: read, update and insert content, and read user information (without email
   addresses).
5. Save, then copy the **OAuth client ID** and **OAuth client secret**.

### 3. Give the Worker the secret
In Cloudflare: the Worker › **Settings › Variables and secrets** › add two of the **Secret** type:

- `NOTION_CLIENT_ID`: the OAuth client ID
- `NOTION_CLIENT_SECRET`: the OAuth client secret

Never put the secret anywhere else (not in GitHub, not in the app).

### 4. Turn it on in the app
Give Claude the OAuth **client ID** (it isn't secret) and the Worker's **address**. They go in
`app/src/main/kotlin/be/wimvandonck/noter/data/api/SignInConfig.kt`, and from the next build
Settings shows **Connect to Notion**.

## Updating the helper
Change `noter-sign-in.mjs` in a pull request (the tests run in CI), then paste the new version in
Cloudflare's editor and tap **Deploy**. The app and the helper must agree on the sealing (see
`SealedBox.kt`); a change there needs a new `SEAL_INFO` and a matching app release.
