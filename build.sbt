name := "cats-effect-exercises"

version := "1.0"

scalaVersion := "2.12.8"

resolvers += Resolver.sonatypeRepo("snapshots")
resolvers += Resolver.sonatypeRepo("releases")

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.8")

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core" % "1.6.0",
  "org.typelevel" %% "cats-effect" % "1.2.0",
  "com.olegpy" %% "better-monadic-for" % "0.3.0"
)

scalacOptions += "-Ypartial-unification"