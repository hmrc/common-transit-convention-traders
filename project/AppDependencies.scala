import sbt.*

object AppDependencies {

  private val catsVersion          = "2.13.0"
  private val bootstrapPlayVersion = "10.7.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"      %% "bootstrap-backend-play-30"       % bootstrapPlayVersion,
    "org.typelevel"    %% "cats-core"                       % catsVersion,
    "org.json"          % "json"                            % "20251224",
    "io.lemonlabs"     %% "scala-uri"                       % "4.0.3",
    "org.apache.pekko" %% "pekko-connectors-xml"            % "1.3.0",
    "org.apache.pekko" %% "pekko-connectors-json-streaming" % "1.3.0",
    "org.apache.pekko" %% "pekko-protobuf-v3" % "1.5.0",
    "org.apache.pekko" %% "pekko-serialization-jackson" % "1.5.0",
    "org.apache.pekko" %% "pekko-stream" % "1.5.0",
    "org.apache.pekko" %% "pekko-actor-typed" % "1.5.0"
  )

  val test: Seq[ModuleID] = Seq(
    "org.mockito"        % "mockito-core"           % "5.24.0",
    "org.typelevel"     %% "cats-core"              % catsVersion,
    "org.scalacheck"    %% "scalacheck"             % "1.19.0",
    "org.typelevel"     %% "discipline-scalatest"   % "2.3.0",
    "uk.gov.hmrc"       %% "bootstrap-test-play-30" % bootstrapPlayVersion,
    "org.scalacheck"    %% "scalacheck"              % "1.19.0",
    "org.mockito"        % "mockito-core"            % "5.24.0",
    "org.scalatestplus" %% "mockito-5-12"            % "3.2.19.0",
    "org.scalacheck"    %% "scalacheck"              % "1.19.0",
    "org.scalatestplus" %% "scalacheck-1-18"         % "3.2.19.0",

  ).map(_ % Test)
}
