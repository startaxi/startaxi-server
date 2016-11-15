addSbtPlugin("com.lightbend.cinnamon" % "sbt-cinnamon" % "2.1.0")
resolvers += Resolver.url(
  "lightbend-commercial",
  url("https://repo.lightbend.com/commercial-releases")
)(Resolver.ivyStylePatterns)
