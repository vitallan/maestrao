# Maestrao

Maestrao is a simple homelab tool I use to manage a small set of machines over SSH.
It collects remote log files into a database and lets me run ad-hoc or scheduled jobs across hosts.

> AI Disclaimer: AI was EXTENSIVELY used on this tool, and I run it only on my personal homelab. This is not a "production level" application, and it does not intend to be so 

## Main Features

- Logs: tail remote log files over SSH and store appended lines in the database (end-of-file only, no backlog).
- Jobs: define commands/scripts, assign hosts, run on-demand, or schedule runs.
- Search: rudimentary, database `LIKE` over ingested lines. Useful for quick greps, not a full log platform.

## Typical Workflow

1. Create a credential (username + password or key).
2. Create one or more hosts (IP/hostname + SSH port + credential).
3. Logs:
   - Add a log source (remote file path) and enable it to start collecting.
4. Jobs:
   - Create a job definition (command/script).
   - Assign hosts.
   - Run now, or enable a schedule.

## Running

### Docker (H2 in-memory by default)

Build:

`docker build -t maestrao:local --build-arg APP_VERSION=local .`

Run:

`docker run --rm -p 8080:8080 -e MAESTRAO_CREDENTIALS_ENCRYPTION_KEY='CHANGE_ME' maestrao:local`

Open: `http://localhost:8080`

Default login (override via config if needed):

- Username: `admin`
- Password: `admin`

To override these configs through variables use:

- `MAESTRAO_SECURITY_ADMIN_USERNAME`
- `MAESTRAO_SECURITY_ADMIN_PASSWORD`

### Optional: MySQL via environment variables

Set these when running the container:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_DATASOURCE_DRIVER_CLASS_NAME`
- `MAESTRAO_CREDENTIALS_ENCRYPTION_KEY`

Driver class options (currently supported):

- H2: `org.h2.Driver`
- MySQL: `com.mysql.cj.jdbc.Driver`

Database URL examples:

- H2 (in-memory): `jdbc:h2:mem:mydb;DB_CLOSE_DELAY=-1`
- MySQL: `jdbc:mysql://<host>:3306/maestrao`

## Notes

- Data is stored in the configured database. With the default H2 in-memory setup, all data is lost when the app stops.
- This project is intentionally small and pragmatic. It is not meant to compete with full observability stacks.

## Maven Proxy Cache (optional)

Maestrao can run a proxy-only Maven cache endpoint at `/maven/*`.

- Enable with: `MAESTRAO_ARTIFACT_PROXY_ENABLED=true`
- Cache root (filesystem): `MAESTRAO_ARTIFACT_PROXY_CACHE_ROOT=/data/artifact-cache`
- Endpoint is intentionally open for LAN usage (no per-client auth in current scope).

### Docker persistence for cached artifacts

The image declares `/data/artifact-cache` as a volume. Persist it with a bind/volume mount:

`docker run --rm -p 8080:8080 -e MAESTRAO_CREDENTIALS_ENCRYPTION_KEY='CHANGE_ME' -e MAESTRAO_ARTIFACT_PROXY_ENABLED=true -v maestrao-artifacts:/data/artifact-cache maestrao:local`

This keeps downloaded Maven artifacts across container restarts.
