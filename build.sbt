import play.sbt.routes.RoutesKeys
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings

val appName = "common-transit-convention-traders"

ThisBuild / majorVersion := 0
ThisBuild / scalaVersion := "3.6.3"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(inConfig(Test)(ScalafmtPlugin.scalafmtConfigSettings))
  .settings(inThisBuild(buildSettings))
  .settings(scoverageSettings)
  .settings(scalacSettings)
  .settings(
    resolvers += Resolver.jcenterRepo,
    PlayKeys.playDefaultPort := 9487,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    Compile / unmanagedResourceDirectories += baseDirectory.value / "resources",
    // Import models by default in route files
    RoutesKeys.routesImport ++= Seq(
      "models.common._",
      "models.Binders._",
      "v2_1.models.Bindings._",
      "java.time.OffsetDateTime",
    ),
    javaOptions ++= Seq(
      "-Djdk.xml.maxOccurLimit=10000"
    )
  )

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test") // the "test->test" allows reusing test code and test dependencies
  .settings(DefaultBuildSettings.itSettings())
  .settings(
    libraryDependencies ++= AppDependencies.test,
    Test / fork := true,
    Test / unmanagedResourceDirectories += baseDirectory.value / "it" / "test" / "resources",
    javaOptions ++= Seq(
      "-Djdk.xml.maxOccurLimit=10000",
      "-Dlogger.resource=it.logback.xml"
    )
  )
  .settings(scalacSettings)
  .settings(scoverageSettings)
  .settings(inConfig(Test)(ScalafmtPlugin.scalafmtConfigSettings))

// Settings for the whole build
lazy val buildSettings = Def.settings(
  scalafmtOnCompile := true,
  useSuperShell := false
)

// Scalac options
lazy val scalacSettings = Def.settings(
  // Disable dead code warning as it is triggered by Mockito any()
  Test / scalacOptions ~= {
    opts =>
      opts.filterNot(Set("-Wdead-code"))
  },
  scalacOptions ++= Seq(
    "-Wconf:src=routes/.*:s",
    "-Wconf:msg=Flag.*repeatedly:s"
  ),
  scalacOptions := scalacOptions.value.map {
    case "-Ykind-projector" => "-Xkind-projector"
    case option             => option
  }
)

// Scoverage exclusions and minimums
lazy val scoverageSettings = Def.settings(
  Test / parallelExecution := false,
  ScoverageKeys.coverageMinimumStmtTotal := 90,
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
    ".*models.*",
    ".*GuiceInjector",
    ".*Test.*"
  ).mkString(";")
)
