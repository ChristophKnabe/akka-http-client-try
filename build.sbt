lazy val akkaHttpVersion = "10.0.9"
//lazy val akkaVersion    = "2.5.3"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization    := "com.example",
      scalaVersion    := "2.12.3"
    )),
    name := "Try HTTP Client",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http-core"         % akkaHttpVersion //,
      //"com.typesafe.akka" %% "akka-http-xml"     % akkaHttpVersion,
      //"com.typesafe.akka" %% "akka-stream"       % akkaVersion,
      //"com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
      //"org.scalatest"     %% "scalatest"         % "3.0.1"         % Test
    )
  )
