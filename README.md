# LiteLLM for Smart Workflow

[LiteLLM](https://docs.litellm.ai/) is an OpenAI-compatible proxy between Axon Ivy and the real
model providers. Ivy holds one *virtual key* per application; the proxy owns the provider
credentials, routing, budgets and rate limits — so swapping a model or rotating a key happens in
one place, without redeploying anything.

[compose.yml](docker/compose.yml) starts `litellm` (port `4000`, admin UI on `/ui`) with its
Postgres, plus `phoenix` (`6006`, AI tracing) and `opensearch` (`9200`, RAG vector store). Only the
first two are needed below.

## 1. Configure and start

```bash
cd docker
cp .env.example .env
```

`.env` needs two secrets:

- **`LITELLM_MASTER_KEY`** – root credential; creates virtual keys and logs into the UI.
  `echo "sk-$(openssl rand -hex 24)"`
- **`LITELLM_SALT_KEY`** – encrypts provider credentials in Postgres. `openssl rand -base64 32`.
  Generate once and **never change it**, or every model has to be added again.

Provider keys such as `OPENAI_API_KEY` are optional there — see step 2.

> Use long, high-entropy secrets: Ivy feeds the configured key into `SensitiveDataOutputGuardrail`
> as a literal blocklist entry, so a dictionary word would block every response containing it.

```bash
docker compose up -d litellm                      # proxy + database
curl http://localhost:4000/health/liveliness
```

## 2. Add a model and a virtual key

Both are done in the admin UI at <http://localhost:4000/ui> — log in as `admin` with your
`LITELLM_MASTER_KEY` as the password. `STORE_MODEL_IN_DB=True` is already set in
[compose.yml](docker/compose.yml), which is what makes UI-driven management possible and lets the
changes survive a restart.

**Add a model** under **Models + Endpoints** → *Add Model*: pick the provider, select the models to
expose, and paste the provider API key (or reuse one saved under the *LLM Credentials* tab).
*Test Connect* verifies the key before you save. The *Model Name* you publish is the alias Ivy
calls — it can point anywhere, and repointing it later is invisible to every Ivy process. Full
walkthrough: [Model Management](https://docs.litellm.ai/docs/proxy/model_management).

**Create a virtual key** under **Virtual Keys** → *Create New Key*: name it, and restrict it to the
models this application may call. Optionally give it a team and a budget. The `sk-…` is shown
**once** — copy it now. Details, incl. budgets and auto-rotation:
[Virtual Keys](https://docs.litellm.ai/docs/proxy/virtual_keys).

Ivy only ever holds a virtual key, never the master key.

> Models can also be declared statically in [litellm-config.yaml](docker/litellm-config.yaml) —
> useful for CI, and the place for per-model defaults like `temperature` (they do not belong in
> Ivy). Write the secret as `api_key: os.environ/OPENAI_API_KEY` so it stays in `.env` rather than
> in a committed file, then `docker compose restart litellm`.

Smoke-test the key before wiring it into Ivy:

```bash
curl http://localhost:4000/v1/chat/completions \
  -H "Authorization: Bearer sk-your-virtual-key" -H "Content-Type: application/json" \
  -d '{"model": "general-model", "messages": [{"role": "user", "content": "ping"}]}'
```

## 3. Configure Axon Ivy

Add `smart-workflow-lite-llm` as a dependency and set its
[variables](models/smart-workflow-lite-llm/config/variables.yaml):

```yaml
Variables:
  AI:
    DefaultProvider: LiteLLM
    Providers:
      LiteLLM:
        BaseUrl: http://localhost:4000/v1
        VirtualKeys:
          supplier-demo:
            #[password]
            APIKey: sk-your-virtual-key
            Models: general-model
```

- The entry name **is** the `key_alias` from LiteLLM — kebab-case, no dots. Since `APIKey` is
  stored encrypted, that name is the only readable hint about which key an entry holds.
- `Models` is mandatory; a key listing none can be selected by no Agent. Aliases are sent verbatim,
  so `openai/gpt-4o` and `llama3:8b` are fine.
- `BaseUrl` is mandatory and points at the one LiteLLM instance that serves every key.
- `#[password]` makes Engine Cockpit encrypt the value into `${decrypt:…}` on save. See
  [variables.example.yaml](models/smart-workflow-lite-llm/config/variables.example.yaml) for
  multi-key setups.

## 4. Use it in an Agent

Set **Provider** to `LiteLLM` and **Model** to an alias, e.g. `general-model` — there is no default
model. If two keys publish the same alias, qualify it as `supplier-demo/general-model`. The
[supplier demo](demo/smart-workflow-supplier-demo) is wired up exactly this way.

## Troubleshooting

| Symptom | Cause |
| --- | --- |
| Container exits complaining about `LITELLM_SALT_KEY` | It is empty; Compose refuses to start. |
| `Unknown LiteLLM model 'x'` | The Agent's *Model* field names an alias no key lists in `Models`. |
| `Model 'x' is held by more than one … key` | Two entries list it. Qualify as `keyAlias/model`. |
| `'x' is a LiteLLM virtual key, not a model` | The *Model* field holds a key alias. |
| `No 'APIKey' configured … Calling the proxy unauthenticated.` | Fine only for a proxy without a master key; otherwise a typo. |
| Calls fail after changing `LITELLM_SALT_KEY` | Stored credentials no longer decryptable. Restore the salt or re-add every model. |
| `401` from the proxy | Key revoked, expired, or over budget. Check **Virtual Keys** in the UI. |

```bash
docker compose logs -f litellm   # incl. every upstream request
docker compose down              # stop, keeping the database volume
docker compose down -v           # stop and wipe models, keys and spend history
```
