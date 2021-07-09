import play.sbt.routes.RoutesKeys
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings
import sbt.Tests.Group
import sbt.Tests.SubProcess

val appName = "common-transit-convention-traders"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .configs(IntegrationTest)
  .settings(DefaultBuildSettings.integrationTestSettings())
  .settings(SbtDistributablesPlugin.publishingSettings)
  .settings(inConfig(IntegrationTest)(itSettings))
  .settings(inConfig(IntegrationTest)(scalafmtSettings))
  .settings(inThisBuild(buildSettings))
  .settings(scoverageSettings)
  .settings(scalacSettings)
  .settings(addCompilerPlugin("io.tryp" % "splain" % "0.5.8" cross CrossVersion.patch))
  .settings(
    majorVersion := 0,
    scalaVersion := "2.12.13",
    resolvers += Resolver.jcenterRepo,
    PlayKeys.playDefaultPort := 9487,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    unmanagedResourceDirectories in Compile += baseDirectory.value / "resources",
    // Import models by default in route files
    RoutesKeys.routesImport ++= Seq(
      "models._",
      "models.domain._",
      "models.Binders._",
      "java.time.OffsetDateTime"
    ),
    javaOptions ++= Seq(
      "-Djdk.xml.maxOccurLimit=10000"
    )
  )

// Settings for the whole build
lazy val buildSettings = Def.settings(
  // scalafmtOnCompile := true,
  useSuperShell := false
)

// Scalac options
lazy val scalacSettings = Def.settings(
  // Disable fatal warnings and warnings from discarding values
  scalacOptions ~= {
    opts =>
      opts.filterNot(Set("-Xfatal-warnings", "-Ywarn-value-discard"))
  },
  // Disable dead code warning as it is triggered by Mockito any()
  Test / scalacOptions ~= {
    opts =>
      opts.filterNot(Set("-Ywarn-dead-code"))
  },
  // Cannot be enabled yet - requires Scala 2.12.13 which suffers from https://github.com/scoverage/scalac-scoverage-plugin/issues/305
  // Disable warnings arising from generated routing code
  scalacOptions += "-Wconf:src=routes/.*:silent"
)

// Scoverage exclusions and minimums
lazy val scoverageSettings = Def.settings(
  parallelExecution in Test := false,
  ScoverageKeys.coverageMinimum := 90.00,
  ScoverageKeys.coverageFailOnMinimum := true,
  ScoverageKeys.coverageHighlighting := true,
  ScoverageKeys.coverageExcludedPackages := Seq(
    "<empty>",
    "Reverse.*",
    ".*(config|views.*)",
    ".*(BuildInfo|Routes).*"
  ).mkString(";"),
  ScoverageKeys.coverageExcludedFiles := Seq(
    "<empty>",
    "Reverse.*",
    ".*repositories.*",
    ".*documentation.*",
    ".*BuildInfo.*",
    ".*javascript.*",
    ".*Routes.*",
    ".*GuiceInjector",
    ".*Test.*"
  ).mkString(";")
)

lazy val itSettings = Seq(
  // Must fork so that config system properties are set
  fork := true,
  unmanagedResourceDirectories += (baseDirectory.value / "it" / "resources"),
  javaOptions ++= Seq(
    "-Dlogger.resource=it.logback.xml"
  )
)
