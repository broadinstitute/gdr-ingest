inThisBuild(
  Seq(
    organization := "org.broadinstitute",
    scalaVersion := "2.12.7",
    scalafmtConfig := Some((baseDirectory in ThisBuild)(_ / ".scalafmt.conf").value),
    scalafmtOnCompile := true,
    scalacOptions ++= Seq(
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-explaintypes",
      "-feature",
      "-target:jvm-1.8",
      "-unchecked",
      "-Xcheckinit",
      "-Xfatal-warnings",
      "-Xfuture",
      "-Xlint",
      "-Xmax-classfile-name",
      "200",
      "-Yno-adapted-args",
      "-Ypartial-unification",
      "-Ywarn-dead-code",
      "-Ywarn-extra-implicit",
      "-Ywarn-inaccessible",
      "-Ywarn-infer-any",
      "-Ywarn-nullary-override",
      "-Ywarn-nullary-unit",
      "-Ywarn-numeric-widen",
      "-Ywarn-unused",
      "-Ywarn-value-discard"
    )
  )
)

val betterFilesVersion = "3.6.0"
val betterMonadicForVersion = "0.2.4"
val caseAppVersion = "2.0.0-M5"
val circeVersion = "0.10.1"
val circeDerivationVersion = "0.10.0-M1"
val circeFs2Version = "0.10.0"
val commonsCodecVersion = "1.11"
val doobieVersion = "0.6.0"
val fs2Version = "1.0.0"
val http4sVersion = "0.20.0-M3"
val logbackVersion = "1.2.3"
val paradiseVersion = "2.1.1"
val pureConfigVersion = "0.10.0"

val commonSettings = Seq(
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % betterMonadicForVersion),
  addCompilerPlugin("org.scalamacros" % "paradise" % paradiseVersion cross CrossVersion.full),
  scalacOptions in (Compile, console) --= Seq(
    "-Xfatal-warnings",
    "-Xlint",
    "-Ywarn-unused"
  )
)

lazy val `gdr-ingest` = project
  .in(file("."))
  .aggregate(`encode-ingest`, `encode-explorer`)

lazy val `encode-ingest` = project
  .in(file("encode/ingest"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % logbackVersion,
      "co.fs2" %% "fs2-io" % fs2Version,
      "com.github.alexarchambault" %% "case-app" % caseAppVersion,
      "com.github.pathikrit" %% "better-files" % betterFilesVersion,
      "commons-codec" % "commons-codec" % commonsCodecVersion,
      "io.circe" %% "circe-derivation" % circeDerivationVersion % Provided,
      "io.circe" %% "circe-fs2" % circeFs2Version,
      "io.circe" %% "circe-literal" % circeVersion,
      "org.http4s" %% "http4s-blaze-client" % http4sVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion
    ),
    dependencyOverrides ++= Seq(
      "co.fs2" %% "fs2-core" % fs2Version,
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion
    )
  )

lazy val `encode-explorer` = project
  .in(file("encode/explorer"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % logbackVersion,
      "com.github.pureconfig" %% "pureconfig" % pureConfigVersion,
      "com.github.pureconfig" %% "pureconfig-cats-effect" % pureConfigVersion,
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-derivation-annotations" % circeDerivationVersion,
      "org.http4s" %% "http4s-blaze-server" % http4sVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.tpolecat" %% "doobie-core" % doobieVersion,
      "org.tpolecat" %% "doobie-hikari" % doobieVersion,
      "org.tpolecat" %% "doobie-postgres" % doobieVersion
    ),
    dependencyOverrides ++= Seq(
      "co.fs2" %% "fs2-core" % fs2Version
    )
  )
