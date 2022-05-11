import play.core.PlayVersion.current
import sbt._

object AppDependencies {

  private val catsVersion = "2.6.1"

  val compile = Seq(
    "uk.gov.hmrc"   %% "bootstrap-backend-play-28" % "5.24.0",

    // TODO: When bootstrap includes this version of http-verbs (or later), we can ditch this line
    "uk.gov.hmrc"   %% "http-verbs-play-28"        % "14.1.0",
    "org.typelevel" %% "cats-core"                 % catsVersion,
    "org.json"       % "json"                      % "20210307",
    "io.lemonlabs"  %% "scala-uri"                 % "3.6.0"
  )

  val test = Seq(
    "org.mockito"             % "mockito-core"         % "3.9.0",
    "org.scalatest"          %% "scalatest"            % "3.2.10",
    "org.typelevel"          %% "cats-core"            % catsVersion,
    "com.typesafe.play"      %% "play-test"            % current,
    "org.pegdown"             % "pegdown"              % "1.6.0",
    "org.scalatestplus.play" %% "scalatestplus-play"   % "4.0.3",
    "org.scalatestplus"      %% "mockito-3-2"          % "3.1.2.0",
    "org.scalacheck"         %% "scalacheck"           % "1.15.4",
    "com.github.tomakehurst"  % "wiremock-standalone"  % "2.27.2",
    "org.typelevel"          %% "discipline-scalatest" % "2.1.5",
    "com.vladsch.flexmark"    % "flexmark-all"         % "0.62.2"
  ).map(_ % "test, it")
}
