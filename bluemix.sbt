dockerRepository := Some("registry.eu-gb.bluemix.net/doublem")
version in Docker := "latest"
bashScriptExtraDefines ++= Seq(
  """addJava "-Xmx128m"""",
  """addJava "-Xms128m""""
)
