package com.maneo.Behinder

import java.util.{Date, Properties}
import collection.JavaConverters._
import scala.collection.Map
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer011, FlinkKafkaProducer011}
import com.alibaba.fastjson.{JSON, JSONArray, JSONObject}
import org.apache.flink.cep.scala.{CEP, PatternStream}
import org.apache.flink.cep.scala.pattern.Pattern
//import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time


case class HttpEvent(src_ip: String, dst_ip: String, dst_port: Int, uri: String, pattern: Int, uuid: String)

object HttpString {
  def unapply(str: String): Option[HttpEvent] = {
    try {
      val j: JSONObject = JSON.parseObject(str)
      if (j.getString("event_name") != "bro-http" || j.getString("header_content_type") != "application/x-www-form-urlencoded") {
        return None
      }
      val patternUri = ".*\\?((?!&).)*=\\d+$"
      val patternBase64 = "^(([A-Za-z0-9+/]{4})+[A-Za-z0-9+/\\.]{0,6})+([A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==)?$"
      j.getString("method") match {
        case m: String if m.toUpperCase() == "GET" && j.getIntValue("response_body_len") == 16 && j.getString("uri").matches(patternUri) => Some(HttpEvent(j.getString("src_ip"), j.getString("dst_ip"), j.getIntValue("dst_port"), j.getString("uri").split("\\?")(0), 1, j.getString("uuid")))
        case m: String if m.toUpperCase() == "POST" && !j.getString("uri").contains("?") && j.getString("post_body").matches(patternBase64) && j.getString("body").matches(patternBase64) => Some(HttpEvent(j.getString("src_ip"), j.getString("dst_ip"), j.getIntValue("dst_port"), j.getString("uri"), 2, j.getString("uuid")))
        case _ => None
      }
    } catch { case _: Exception => None}
  }
}

object behinder {
  def main(args: Array[String]): Unit = {
    val AppName = "Webshell Behinder Detector"
    println(AppName + " Start!")

    /** get input parameters */
    val params: ParameterTool = ParameterTool.fromArgs(args)
    val kafkaBrokers: String = if (params.has("kafka.brokers")) { params.getRequired("kafka.brokers")} else {"127.0.0.1:9092"}
    val kafkaInTopic: java.util.List[String] = if (params.has("kafka.inTopic")) {params.getRequired("kafka.inTopic").split(",").toList.asJava} else { List("in").asJava }
    val kafkaOutTopic: String = if (params.has("kafka.outTopic")) {params.getRequired("kafka.outTopic")} else { "out" }
    println(
      "kafka brokers: " + kafkaBrokers + "\n"
        + "kafka input topic: " + kafkaInTopic.toString + "\n"
        + "kafka output topic: " + kafkaOutTopic.toString + "\n"
    )

    /** start flink execution */
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    // val tableEnv = TableEnvironment.getTableEnvironment(env)
    // val tableEnv = StreamTableEnvironment.create(env)
    //env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    //env.setParallelism(100)
    env.getConfig.disableSysoutLogging
    env.getConfig.setRestartStrategy(RestartStrategies.fixedDelayRestart(4, 10000))
    // create a checkpoint every 600 seconds
    env.enableCheckpointing(600000)
    // make parameters available in the web interface
    env.getConfig.setGlobalJobParameters(params)

    /** kafka consumer */
    val kafkaProperties = new Properties()
    kafkaProperties.setProperty("bootstrap.servers", kafkaBrokers)
    val eventStream: DataStream[HttpEvent] = env
      .addSource(
        new FlinkKafkaConsumer011(
          kafkaInTopic,
          new SimpleStringSchema,
          kafkaProperties)
      ).map(i => HttpString.unapply(i))
      .filter(_.nonEmpty)
      .map(i => i.get)


    eventStream.print()

    //val partitionedInput = eventStream.assignAscendingTimestamps(_.eventTime).keyBy("uri", "src_ip", "dst_ip", "dst_port")
    val partitionedInput = eventStream.keyBy("uri", "src_ip", "dst_ip", "dst_port")
    //val partitionedInput = eventStream.keyBy(_.uri)

    val pattern: Pattern[HttpEvent, HttpEvent] = Pattern.begin[HttpEvent]("start").where(_.pattern == 1)
        .next("middle").where(_.pattern == 1)
        .followedBy("end").where(_.pattern == 2)
      .within(Time.seconds(10))

    val patternStream: PatternStream[HttpEvent] = CEP.pattern(partitionedInput, pattern)

    val alertStream = patternStream.select(
      (pattern: Map[String, Iterable[HttpEvent]]) => {
        val start: HttpEvent = pattern.getOrElse("start", null).iterator.next()
        val middle: HttpEvent = pattern.getOrElse("middle", null).iterator.next()
        val end: HttpEvent = pattern.getOrElse("end", null).iterator.next()
        (start.uri, start.pattern, middle.pattern, end.pattern)
      }
    )

    alertStream.print()

    //val alertStream1 = eventStream.filter(_.pattern == 1)
    //alertStream1.print()
    env.execute(AppName)

  }
}
