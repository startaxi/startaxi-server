organization := "startaxi"
name := "startaxi-server"

scalaVersion := "2.11.8"

libraryDependencies ++= {
  val akkaVersion = "2.4.12"
  val sprayVersion = "1.3.4"
  val unitsVersion = "0.2.1"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-contrib" % akkaVersion,
    "io.spray" %% "spray-routing-shapeless23" % sprayVersion,
    "io.spray" %% "spray-can" % sprayVersion,
    "io.spray" %% "spray-client" % sprayVersion,
    "io.spray" %% "spray-json" % "1.3.2",
    "com.github.nscala-time" %% "nscala-time" % "1.4.0",
    "com.wandoulabs.akka" %% "spray-websocket" % "0.1.3",
    "io.github.karols" %% "units" % unitsVersion,
    "io.github.karols" %% "units-joda" % unitsVersion
  )
}

resolvers += "spray repo" at "http://repo.spray.io"

enablePlugins(JavaAppPackaging, DockerPlugin)
