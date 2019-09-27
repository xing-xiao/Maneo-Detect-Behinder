name := "Behinder"

version := "0.1"

scalaVersion := "2.12.8"

val flinkVersion = "1.8.1"

libraryDependencies ++= Seq(
  //"org.apache.flink" %% "flink-scala" % flinkVersion % "provided",
  //"org.apache.flink" %% "flink-streaming-scala" % flinkVersion % "provided",
  //"org.apache.flink" %% "flink-clients" % flinkVersion % "provided",
  "org.apache.flink" %% "flink-scala" % flinkVersion,
  "org.apache.flink" %% "flink-streaming-scala" % flinkVersion,
  "org.apache.flink" %% "flink-clients" % flinkVersion,
  //"org.apache.flink" %% "flink-table-planner" % flinkVersion % "provided",
  "org.apache.flink" %% "flink-table-planner" % flinkVersion,
  "org.apache.flink" %% "flink-table-api-scala-bridge" % flinkVersion,
  "org.apache.flink" % "flink-table-common" % flinkVersion % Test,
  "org.apache.flink" %% "flink-connector-kafka-0.11" % flinkVersion,
  "org.apache.flink" %% "flink-cep-scala" % flinkVersion,
  "com.alibaba" % "fastjson" % "1.2.58"
)

// exclude Scala library from assembly
assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false)