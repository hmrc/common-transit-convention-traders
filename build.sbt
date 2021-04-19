import uk.gov.hmrc.DefaultBuildSettings
import uk.gov.hmrc.SbtArtifactory
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings

val appName = "common-transit-convention-traders"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(
    majorVersion                     := 0,
    libraryDependencies              ++= AppDependencies.compile ++ AppDependencies.test
  )
  .settings(publishingSettings: _*)
  .configs(IntegrationTest)
  .settings(DefaultBuildSettings.integrationTestSettings(): _*)
  .settings(inConfig(IntegrationTest)(itSettings): _*)
  .settings(inConfig(IntegrationTest)(ScalafmtPlugin.scalafmtConfigSettings): _*)
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(ScoverageSettings())
  .settings(PlayKeys.playDefaultPort := 9487)
  .settings(scalaVersion := "2.12.13")
  .settings(
    unmanagedResourceDirectories in Compile += baseDirectory.value / "resources",
    // Disable fatal warnings and warnings from discarding values
    scalacOptions ~= { opts => opts.filterNot(Set("-Xfatal-warnings", "-Ywarn-value-discard")) },
    // Disable warnings arising from generated routing code
    scalacOptions += "-Wconf:src=routes/.*:silent",
    // Disable dead code warning as it is triggered by Mockito any()
    Test / scalacOptions ~= { opts => opts.filterNot(Set("-Ywarn-dead-code")) },
  )
  .settings(
    javaOptions ++= Seq(
      "-Djdk.xml.maxOccurLimit=10000"
    )
  )

lazy val itSettings = Seq(
  javaOptions ++= Seq(
    "-Dlogger.resource=it.logback.xml"
  )
)