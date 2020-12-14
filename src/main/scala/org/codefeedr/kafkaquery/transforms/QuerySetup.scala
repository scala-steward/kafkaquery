package org.codefeedr.kafkaquery.transforms

import org.apache.avro.Schema

import scala.collection.JavaConverters._

object QuerySetup {

  /** Extract the table names from the query and return the ones that are supported.
    *
    * @param query            sql query to be matched
    * @param supportedPlugins list of supported plugins from zookeeper
    * @return the subset of supported plugins
    */
  def extractTopics(
      query: String,
      supportedPlugins: List[String]
  ): List[String] = {
    supportedPlugins.intersect(query.split("\\s+|,|;|\\(|\\)|`"))
  }

  /** Returns the Flink DDL for creating the specified temporary table.
    * @param name name of the table/Kafka topic
    * @param tableFields DDL StringBuilder of the table fields
    * @param kafkaAddr Kafka server address
    * @param checkLatest record retrieval strategy (from LATEST or EARLIEST)
    * @return temporary table DDL string
    */
  def getTableCreationCommand(
      name: String,
      tableFields: java.lang.StringBuilder,
      kafkaAddr: String,
      checkLatest: Boolean
  ): String = {
    "CREATE TEMPORARY TABLE `" + name + "` (" + tableFields + ") " +
      "WITH (" +
      "'connector' = 'kafka', " +
      "'topic' = '" + name + "', " +
      "'properties.bootstrap.servers' = '" + kafkaAddr + "', " +
      "'properties.group.id' = 'kq', " +
      "'scan.startup.mode' = '" +
      (if (checkLatest) "latest-offset" else "earliest-offset") +
      "', " +
      "'properties.default.api.timeout.ms' = '5000', " + // TODO create option for setting this value
      "'format' = 'json', " +
      "'json.timestamp-format.standard' = 'ISO-8601', " +
      "'json.ignore-parse-errors' = 'true', " +
      "'json.fail-on-missing-field' = 'false'" +
      ")"
  }

  /** Generate a Flink table schema from an Avro Schema.
    *
    * @param avroSchema  the Avro Schema
    * @return a java.lang.StringBuilder with the DDL description of the schema
    */
  def generateTableSchema(
      avroSchema: org.apache.avro.Schema,
      fieldGetter: (String, Schema) => (String, java.lang.StringBuilder)
  ): java.lang.StringBuilder = {
    val res = new java.lang.StringBuilder()

    val rowtimeEnabled = !"false".equals(avroSchema.getProp("rowtime"))

    var rowtimeFound = false

    for (field <- avroSchema.getFields.asScala) {
      val fieldInfo = fieldGetter(field.name(), field.schema())

      if (
        rowtimeEnabled && !rowtimeFound && field.getProp("rowtime") == "true"
      ) {
        rowtimeFound = true
        res.append(
          fieldInfo._1 + " TIMESTAMP(3) WITH LOCAL TIME ZONE, "
        )
      } else {
        res.append(fieldInfo._1 + " " + fieldInfo._2 + ", ")
      }
    }

    if (rowtimeEnabled && !rowtimeFound) {
      res.append("`kafka_time` TIMESTAMP(3) METADATA FROM 'timestamp', ")
    }

    if (res.length() - 2 == res.lastIndexOf(", "))
      res.delete(res.length() - 2, res.length())

    res
  }

}
