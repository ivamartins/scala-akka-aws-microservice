name := "scala-akka-aws-microservice"
version := "0.1.0-SNAPSHOT"
scalaVersion := "2.13.14"

val akkaVersion = "2.8.5"
val akkaHttpVersion = "10.5.3"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
  // AWS SDK v2
  "software.amazon.awssdk" % "dynamodb" % "2.25.40",
  "software.amazon.awssdk" % "rds" % "2.25.40",
  "software.amazon.awssdk" % "cloudwatch" % "2.25.40",
  "software.amazon.awssdk" % "sts" % "2.25.40",
  "io.spray" %% "spray-json" % "1.3.6",
  "ch.qos.logback" % "logback-classic" % "1.4.14",
  // Test
  "org.scalatest" %% "scalatest" % "3.2.17" % Test,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
  "com.typesafe.akka" %% "akka-persistence-testkit" % akkaVersion % Test
)

// Fat jar for Fargate
assembly / mainClass := Some("com.codesolutions.akka.aws.Main")
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}
