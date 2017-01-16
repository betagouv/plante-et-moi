name := """plante-et-moi-backend"""
organization := "fr.gouv.beta"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"

//libraryDependencies += filters

libraryDependencies ++= Seq(
  ws,
  jdbc
)

libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test

libraryDependencies ++= Seq(
  "org.webjars" %% "webjars-play" % "2.5.0",
  "org.webjars.bower" % "material-design-lite" % "1.3.0",
  "org.webjars.bower" % "material-design-icons" % "3.0.1",
  "org.webjars.npm" % "leaflet" % "1.0.2",
  "org.postgresql" % "postgresql" % "9.4.1210"
)

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "fr.gouv.beta.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "fr.gouv.beta.binders._"
