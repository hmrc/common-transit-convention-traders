import sbt._

object AppDependencies {

  private val catsVersion          = "2.6.1"
  private val bootstrapPlayVersion = "8.3.0"

  val compile = Seq(
    "uk.gov.hmrc"      %% "bootstrap-backend-play-30"       % bootstrapPlayVersion,
    "org.typelevel"    %% "cats-core"                       % catsVersion,
    "org.json"          % "json"                            % "20230227",
    "io.lemonlabs"     %% "scala-uri"                       % "3.6.0",
    "org.apache.pekko" %% "pekko-connectors-xml"            % "1.0.1",
    "org.apache.pekko" %% "pekko-connectors-json-streaming" % "1.0.1"
  )

  val test = Seq(
    "org.mockito"        % "mockito-core"           % "3.9.0",
    "org.typelevel"     %% "cats-core"              % catsVersion,
    "org.pegdown"        % "pegdown"                % "1.6.0",
    "org.scalatestplus" %% "mockito-3-2"            % "3.1.2.0",
    "org.scalacheck"    %% "scalacheck"             % "1.15.4",
    "org.typelevel"     %% "discipline-scalatest"   % "2.1.5",
    "uk.gov.hmrc"       %% "bootstrap-test-play-30" % bootstrapPlayVersion
  ).map(_ % Test)

  val integration = Seq.empty[ModuleID]
}
