import play.core.PlayVersion.current
import sbt._

object AppDependencies {

  private val catsVersion = "2.5.0"

  val compile = Seq(
    "uk.gov.hmrc"   %% "bootstrap-backend-play-27" % "3.4.0",
    "org.typelevel" %% "cats-core"                 % catsVersion,
    "org.json"       % "json"                      % "20200518",
    "io.lemonlabs"  %% "scala-uri"                 % "3.2.0"
  )

  val test = Seq(
    "org.mockito"             % "mockito-core"         % "3.9.0",
    "org.scalatest"          %% "scalatest"            % "3.2.8",
    "org.typelevel"          %% "cats-core"            % catsVersion,
    "com.typesafe.play"      %% "play-test"            % current,
    "org.pegdown"             % "pegdown"              % "1.6.0",
    "org.scalatestplus.play" %% "scalatestplus-play"   % "4.0.3",
    "org.scalatestplus"      %% "mockito-3-2"          % "3.1.2.0",
    "org.scalacheck"         %% "scalacheck"           % "1.14.3",
    "com.github.tomakehurst"  % "wiremock-standalone"  % "2.27.2",
    "org.typelevel"          %% "discipline-scalatest" % "2.1.4",
    "com.vladsch.flexmark"    % "flexmark-all"         % "0.35.10"
  ).map(_ % "test, it")
}
