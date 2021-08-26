resolvers += "HMRC-open-artefacts-maven" at "https://open.artefacts.tax.service.gov.uk/maven2"

resolvers += Resolver.url("HMRC-open-artefacts-ivy", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(Resolver.ivyStylePatterns)

addSbtPlugin("uk.gov.hmrc" % "sbt-auto-build" % "3.4.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-git-versioning" % "2.4.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-distributables" % "2.1.0")

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.7.9")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.8.2")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.3")

addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.6.0")

addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.1.20")
