import play.sbt.routes.RoutesKeys
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings
import sbt.Tests.Group
import sbt.Tests.SubProcess

val appName = "common-transit-convention-traders"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory, sbtdocker.DockerPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .configs(IntegrationTest)
  .settings(DefaultBuildSettings.integrationTestSettings())
  .settings(SbtDistributablesPlugin.publishingSettings)
  .settings(inConfig(IntegrationTest)(itSettings))
  .settings(inConfig(IntegrationTest)(scalafmtSettings))
  .settings(inThisBuild(buildSettings))
  .settings(scoverageSettings)
  .settings(scalacSettings)
  .settings(
    majorVersion := 0,
    scalaVersion := "2.12.11",
    resolvers += Resolver.jcenterRepo,
    PlayKeys.playDefaultPort := 9487,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    unmanagedResourceDirectories in Compile += baseDirectory.value / "resources",
    // Import models by default in route files
    RoutesKeys.routesImport ++= Seq(
      "models._"
    ),
    javaOptions ++= Seq(
      "-Djdk.xml.maxOccurLimit=10000"
    ),
    docker / imageNames := Seq(
      ImageName(s"${organizationName.value}/${appName}:latest"),
      ImageName(s"${organizationName.value}/${appName}:${version.value}"),
    ),
    docker / dockerfile := {
      (Compile / Keys.`package`).value
      val tgzFile = SbtDistributablesPlugin.distTgzTask.value

      new sbtdocker.Dockerfile {
        from("adoptopenjdk/openjdk8:alpine-jre")
        expose(PlayKeys.playDefaultPort.value)
        run("apk", "add", "--no-cache", "bash")
        add(tgzFile, "/")
        workDir(s"/${appName}-${version.value}/bin")
        cmd(s"./$appName", s"-Dhttp.port=${PlayKeys.playDefaultPort.value}")
      }
    }
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
  }
  // Cannot be enabled yet - requires Scala 2.12.13 which suffers from https://github.com/scoverage/scalac-scoverage-plugin/issues/305
  // Disable warnings arising from generated routing code
  // scalacOptions += "-Wconf:src=routes/.*:silent",
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
  javaOptions ++= Seq(
    "-Dlogger.resource=it.logback.xml"
  ),
  unmanagedResourceDirectories += (baseDirectory.value / "it" / "resources"),
  // sbt-settings does not cause javaOptions to be passed to test groups by default
  // needed unless / until this PR is merged and released: https://github.com/hmrc/sbt-settings/pull/19/files
  testGrouping := {
    val tests          = (IntegrationTest / definedTests).value
    val forkJvmOptions = (IntegrationTest / javaOptions).value
    tests.map {
      test =>
        Group(
          test.name,
          Seq(test),
          SubProcess(
            ForkOptions().withRunJVMOptions(forkJvmOptions.toVector :+ ("-Dtest.name=" + test.name))
          )
        )
    }
  }
)
