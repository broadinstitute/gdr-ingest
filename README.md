# GDR Ingest

Custom logic for slurping external datasets into Broad data repositories.

## Building the project

The project is built using [`sbt`](https://www.scala-sbt.org/download.html). Once the tool is installed,
you can compile with:
```bash
$ cd ${PROJECT_ROOT}
$ sbt
# Much garbage logging, before dropping into the sbt repl
sbt:gdr-ingest> compile
```

You can also compile by running `sbt compile` from the project root in bash, but that will eat the build tool's
startup cost on every call.

## Running ENCODE ingest

From within the `sbt` repl, run:
```bash
sbt:gdr-ingest> encode-ingest/run --help
```

You can also run "help" for specific sub-commands to see their options, i.e.
```bash
sbt:gdr-ingest> encode-ingest/run download-metadata --help
```

## Testing ENCODE ingest

There aren't any automated tests (yet). Manual testing has been done by comparing outputs to a
[test workspace](https://portal.firecloud.org/#workspaces/broad-cil-devel-fc/ENCODE_test5) in FireCloud containing a
subset of all data.

## Running the Explorer API

### Running locally

First, make sure Postgres 9.6 is installed, running, and seeded with data:
```bash
$ brew install postgresql@9.6
$ brew services start postgresql@9.6
$ psql postgres < encode/explorer/db/tables.sql
$ psql postgres <(gsutil cat gs://broad-gdr-encode-transfer-tmp/snapshot.sql)
```

Then, add configuration for accessing the DB to `encode/explorer/src/main/resources/application.conf`. The needed
fields are:
* org.broadinstitute.gdr.encode.explorer.db.username
* org.broadinstitute.gdr.encode.explorer.db.password

Finally, from within the `sbt` repl run:
```bash
sbt:gdr-ingest> encode-explorer/run
```
If you see ASCII art, the server is successfully running.

You can then query the API by `curl`-ing localhost:
```bash
$ curl localhost:8080/api/facets
```

### Running in GCP

Run `encode/explorer/deploy.sh` to push the API server into App Engine.

## Configuring the Explorer API

Default configuration is listed in `encode/explorer/src/main/resources/reference.conf`. To tweak the defaults, you
can either modify `reference.conf` or add overrides to `application.conf`.
