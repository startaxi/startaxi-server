name := "startaxi-server"

version := "0.0.1-SNAPSHOT"

scalaVersion := "2.11.4"

libraryDependencies ++= {
  val akkaVersion = "2.3.6"
  val sprayVersion = "1.3.2"
  val unitsVersion = "0.2.1"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-contrib" % akkaVersion,
    "io.spray" %% "spray-routing" % sprayVersion,
    "io.spray" %% "spray-can" % sprayVersion,
    "io.spray" %% "spray-client" % sprayVersion,
    "io.spray" %% "spray-json" % "1.2.6",
    "com.github.nscala-time" %% "nscala-time" % "1.4.0",
    "com.wandoulabs.akka" %% "spray-websocket" % "0.1.3",
    "io.github.karols" %% "units" % unitsVersion,
    "io.github.karols" %% "units-joda" % unitsVersion
  )
}

resolvers += "spray repo" at "http://repo.spray.io"

enablePlugins(JavaAppPackaging)
