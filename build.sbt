name := "startaxi-api-server"

version := "0.0.1-SNAPSHOT"

scalaVersion := "2.11.2"

libraryDependencies ++= {
  val akkaVersion = "2.3.6"
  val sprayVersion = "1.3.2"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "io.spray" %% "spray-routing" % sprayVersion,
    "io.spray" %% "spray-can" % sprayVersion,
    "io.spray" %% "spray-client" % sprayVersion,
    "io.spray" %% "spray-json" % "1.2.6",
    "com.github.nscala-time" %% "nscala-time" % "1.4.0",
    "com.wandoulabs.akka" %% "spray-websocket" % "0.1.3"
  )
}

resolvers += "spray repo" at "http://repo.spray.io"
