# Deployment

klinekit ships as three docker images (Postgres + Spring Boot API + Next.js dashboard) and runs locally with `docker compose up`. This doc covers two cheap ways to host it on the public internet so a recruiter / friend can poke at the demo.

> **TL;DR — local-only:** `docker compose up --build` then open <http://localhost:3000>. Skip the rest of this file.

## Prereqs

- Docker Desktop 27+ (for `docker compose`)
- For Fly.io: [`flyctl`](https://fly.io/docs/hands-on/install-flyctl/) installed and `fly auth login`
- For Railway: [`railway`](https://docs.railway.com/develop/cli) installed and `railway login`

## Option A — Fly.io (recommended)

Two apps + one Postgres database. Fly gives a free `*.fly.dev` hostname per app and a Postgres tier suitable for a demo.

### 1. Provision Postgres

```bash
fly postgres create --name klinekit-pg --region sjc \
  --vm-size shared-cpu-1x --volume-size 1
# When prompted, save the DATABASE_URL it prints — you'll need it for step 2.
```

### 2. Deploy the API

```bash
fly launch --name klinekit-api --dockerfile api/Dockerfile \
  --no-deploy --copy-config --region sjc
fly secrets set \
  SPRING_DATASOURCE_URL='jdbc:postgresql://klinekit-pg.flycast:5432/klinekit?user=postgres&password=...' \
  --app klinekit-api
fly deploy --app klinekit-api
```

The generated `fly.toml` should expose port 8080. Verify:

```bash
curl https://klinekit-api.fly.dev/v3/api-docs | head -c 200
```

### 3. Deploy the dashboard

The dashboard bakes `NEXT_PUBLIC_KLINEKIT_API` at build time, so we pass it as a build arg:

```bash
cd web
fly launch --name klinekit-web --dockerfile Dockerfile --no-deploy --region sjc
fly deploy --app klinekit-web \
  --build-arg NEXT_PUBLIC_KLINEKIT_API=https://klinekit-api.fly.dev/api/v1
```

Open <https://klinekit-web.fly.dev>.

## Option B — Railway

Railway has the gentlest learning curve but the free tier sleeps after a few hours of idle. Useful for a 24-hour demo window.

```bash
railway init
railway up                      # uses docker-compose.yml at root
```

Then in the Railway dashboard:

1. Add the included Postgres plugin (one click).
2. For the **api** service, set `SPRING_DATASOURCE_URL` to the auto-generated JDBC URL.
3. For the **web** service, set the build arg `NEXT_PUBLIC_KLINEKIT_API` to the public URL of the api service.

## Hardening before public exposure

- Database: rotate the default `klinekit/klinekit/klinekit` credentials; pass real ones via secrets.
- API: tighten the `@CrossOrigin(origins = "*")` in `BacktestController.java` to your dashboard origin.
- API: set `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75` (already in the Dockerfile) and pick the smallest Fly VM that doesn't OOM (start with shared-cpu-1x 512MB).
- OKX: the API call `source: { provider: "okx", count: 5000 }` triggers up to 50 sequential pagination calls — reasonable for a single tab, but rate-limit if you ever expose this to the public.
