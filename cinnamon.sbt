enablePlugins(Cinnamon)

cinnamon in run := true
libraryDependencies ++= Seq(
  Cinnamon.library.cinnamonSandbox,
  Cinnamon.library.cinnamonAkka,
  Cinnamon.library.cinnamonCHMetrics
)
