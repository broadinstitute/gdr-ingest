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
sbt:gdr-ingest> encode-ingest/run prep-ingest --help
```
