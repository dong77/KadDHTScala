name := """KanDHTScala"""

version := "0.1"

scalaVersion := "2.11.2"

resolvers ++= Seq("snapshots", "releases").map(Resolver.sonatypeRepo)

resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.6",
  "com.typesafe.akka" %% "akka-slf4j" % "2.3.6" % "runtime",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.6" % "test",
  "net.sandrogrzicic" %% "scalabuff-runtime" % "1.3.9",
  "ch.qos.logback" % "logback-classic" % "1.1.2",
  "ch.qos.logback" % "logback-core" % "1.1.2",
  "org.specs2" %% "specs2" % "2.4.9" % "test")
  
