scalaVersion in ThisBuild := "2.11.7"

resolvers ++= Seq(
  "OSS Sonatype snapshot repository" at "https://oss.sonatype.org/content/repositories/snapshots"
)

libraryDependencies in ThisBuild ++= Seq(
  "org.powerapi" % "powerapi-core_2.11" % "3.3"
)

fork in ThisBuild := true

mainClass in (Compile, run) := Some("Monitor")