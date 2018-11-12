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
val fs2Version = "1.0.0"
val http4sVersion = "0.20.0-M2"
val logbackVersion = "1.2.3"

lazy val `gdr-ingest` = project
  .in(file("."))
  .aggregate(`encode-ingest`)

lazy val `encode-ingest` = project
  .settings(
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % betterMonadicForVersion),
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
    ),
    scalacOptions in (Compile, console) --= Seq(
      "-Xfatal-warnings",
      "-Xlint",
      "-Ywarn-unused"
    )
  )
