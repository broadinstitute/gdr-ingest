inThisBuild(
  Seq(
    organization := "org.broadinstitute",
    scalaVersion := "2.12.6",
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
val circeVersion = "0.9.3"
val circeFs2Version = "0.9.0"
val commonsCodecVersion = "1.11"
val declineVersion = "0.5.0"
val fs2Version = "0.10.5"
val http4sVersion = "0.18.17"
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
      "com.github.pathikrit" %% "better-files" % betterFilesVersion,
      "com.monovore" %% "decline" % declineVersion,
      "commons-codec" % "commons-codec" % commonsCodecVersion,
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
