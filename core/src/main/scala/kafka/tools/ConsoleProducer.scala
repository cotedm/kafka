/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kafka.tools

import java.io._
import java.util.Properties

import joptsimple._
import kafka.message.{DefaultCompressionCodec, NoCompressionCodec}
import kafka.serializer._
import kafka.utils.{CommandLineUtils, ToolsUtils}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}

object ConsoleProducer {

  def main(args: Array[String]) {

    try {
        val config = new ProducerConfig(args)
        val topic = getReaderProps(config).getProperty("topic")
        val stdinReader: BufferedReader = new BufferedReader(new InputStreamReader(System.in))

        var message: String = stdinReader.readLine
        //val props: Properties = new Properties
        //props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.brokerList)
        //props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArraySerializer")
        //props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArraySerializer")
        val producer: KafkaProducer[Array[Byte], Array[Byte]] = new KafkaProducer[Array[Byte], Array[Byte]](getNewProducerProps(config))
        var record: ProducerRecord[Array[Byte], Array[Byte]] = null

        while (message != null) {
          {
            record = new ProducerRecord[Array[Byte], Array[Byte]](topic, message.getBytes)
            System.out.println("Record: " + record)
            producer.send(record)
            System.out.println("Produced" + message)
            message = stdinReader.readLine
          }
        }
    } catch {
      case e: Exception =>
        e.printStackTrace
        System.exit(1)
    }
    System.exit(0)
  }

  def getReaderProps(config: ProducerConfig): Properties = {
    val props = new Properties
    props.put("topic",config.topic)
    props.putAll(config.cmdLineProps)
    props
  }

  def getNewProducerProps(config: ProducerConfig): Properties = {
    val props: Properties = new Properties
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.brokerList)
    props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, config.compressionCodec)
    props.put(ProducerConfig.SEND_BUFFER_CONFIG, config.socketBuffer.toString)
    props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, config.retryBackoffMs.toString)
    props.put(ProducerConfig.METADATA_MAX_AGE_CONFIG, config.metadataExpiryMs.toString)
    props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, config.maxBlockMs.toString)
    props.put(ProducerConfig.ACKS_CONFIG, config.requestRequiredAcks.toString)
    props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, config.requestTimeoutMs.toString)
    props.put(ProducerConfig.RETRIES_CONFIG, config.messageSendMaxRetries.toString)
//    props.put(ProducerConfig.LINGER_MS_CONFIG, config.sendTimeout.toString)
//    props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, config.maxMemoryBytes.toString)
    props.put(ProducerConfig.BATCH_SIZE_CONFIG, config.maxPartitionMemoryBytes.toString)
    props.put(ProducerConfig.CLIENT_ID_CONFIG, "console-producer")
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArraySerializer")
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArraySerializer")
    System.out.println(props)

    props
  }

  class ProducerConfig(args: Array[String]) {
    val parser = new OptionParser
    val topicOpt = parser.accepts("topic", "REQUIRED: The topic id to produce messages to.")
      .withRequiredArg
      .describedAs("topic")
      .ofType(classOf[String])
    val brokerListOpt = parser.accepts("broker-list", "REQUIRED: The broker list string in the form HOST1:PORT1,HOST2:PORT2.")
      .withRequiredArg
      .describedAs("broker-list")
      .ofType(classOf[String])
    val syncOpt = parser.accepts("sync", "If set message send requests to the brokers are synchronously, one at a time as they arrive.")
    val compressionCodecOpt = parser.accepts("compression-codec", "The compression codec: either 'none', 'gzip', 'snappy', or 'lz4'." +
      "If specified without value, then it defaults to 'gzip'")
      .withOptionalArg()
      .describedAs("compression-codec")
      .ofType(classOf[String])
    val batchSizeOpt = parser.accepts("batch-size", "Number of messages to send in a single batch if they are not being sent synchronously.")
      .withRequiredArg
      .describedAs("size")
      .ofType(classOf[java.lang.Integer])
      .defaultsTo(200)
    val messageSendMaxRetriesOpt = parser.accepts("message-send-max-retries", "Brokers can fail receiving the message for multiple reasons, and being unavailable transiently is just one of them. This property specifies the number of retires before the producer give up and drop this message.")
      .withRequiredArg
      .ofType(classOf[java.lang.Integer])
      .defaultsTo(3)
    val retryBackoffMsOpt = parser.accepts("retry-backoff-ms", "Before each retry, the producer refreshes the metadata of relevant topics. Since leader election takes a bit of time, this property specifies the amount of time that the producer waits before refreshing the metadata.")
      .withRequiredArg
      .ofType(classOf[java.lang.Integer])
      .defaultsTo(100)
    val sendTimeoutOpt = parser.accepts("timeout", "If set and the producer is running in asynchronous mode, this gives the maximum amount of time" +
      " a message will queue awaiting sufficient batch size. The value is given in ms.")
      .withRequiredArg
      .describedAs("timeout_ms")
      .ofType(classOf[java.lang.Integer])
      .defaultsTo(1000)
    val queueSizeOpt = parser.accepts("queue-size", "If set and the producer is running in asynchronous mode, this gives the maximum amount of " +
      " messages will queue awaiting sufficient batch size.")
      .withRequiredArg
      .describedAs("queue_size")
      .ofType(classOf[java.lang.Integer])
      .defaultsTo(10000)
    val queueEnqueueTimeoutMsOpt = parser.accepts("queue-enqueuetimeout-ms", "Timeout for event enqueue")
      .withRequiredArg
      .describedAs("queue enqueuetimeout ms")
      .ofType(classOf[java.lang.Integer])
      .defaultsTo(Int.MaxValue)
    val requestRequiredAcksOpt = parser.accepts("request-required-acks", "The required acks of the producer requests")
      .withRequiredArg
      .describedAs("request required acks")
      .ofType(classOf[java.lang.Integer])
      .defaultsTo(0)
    val requestTimeoutMsOpt = parser.accepts("request-timeout-ms", "The ack timeout of the producer requests. Value must be non-negative and non-zero")
      .withRequiredArg
      .describedAs("request timeout ms")
      .ofType(classOf[java.lang.Integer])
      .defaultsTo(1500)
    val metadataExpiryMsOpt = parser.accepts("metadata-expiry-ms",
      "The period of time in milliseconds after which we force a refresh of metadata even if we haven't seen any leadership changes.")
      .withRequiredArg
      .describedAs("metadata expiration interval")
      .ofType(classOf[java.lang.Long])
      .defaultsTo(5*60*1000L)
    val maxBlockMsOpt = parser.accepts("max-block-ms",
      "The max time that the producer will block for during a send request")
      .withRequiredArg
      .describedAs("max block on send")
      .ofType(classOf[java.lang.Long])
      .defaultsTo(60*1000L)
    val maxMemoryBytesOpt = parser.accepts("max-memory-bytes",
      "The total memory used by the producer to buffer records waiting to be sent to the server.")
      .withRequiredArg
      .describedAs("total memory in bytes")
      .ofType(classOf[java.lang.Long])
      .defaultsTo(32 * 1024 * 1024L)
    val maxPartitionMemoryBytesOpt = parser.accepts("max-partition-memory-bytes",
      "The buffer size allocated for a partition. When records are received which are smaller than this size the producer " +
        "will attempt to optimistically group them together until this size is reached.")
      .withRequiredArg
      .describedAs("memory in bytes per partition")
      .ofType(classOf[java.lang.Long])
      .defaultsTo(16 * 1024L)
    val valueEncoderOpt = parser.accepts("value-serializer", "The class name of the message encoder implementation to use for serializing values.")
      .withRequiredArg
      .describedAs("encoder_class")
      .ofType(classOf[java.lang.String])
      .defaultsTo(classOf[DefaultEncoder].getName)
    val keyEncoderOpt = parser.accepts("key-serializer", "The class name of the message encoder implementation to use for serializing keys.")
      .withRequiredArg
      .describedAs("encoder_class")
      .ofType(classOf[java.lang.String])
      .defaultsTo(classOf[DefaultEncoder].getName)
    val socketBufferSizeOpt = parser.accepts("socket-buffer-size", "The size of the tcp RECV size.")
      .withRequiredArg
      .describedAs("size")
      .ofType(classOf[java.lang.Integer])
      .defaultsTo(1024*100)
    val propertyOpt = parser.accepts("property", "A mechanism to pass user-defined properties in the form key=value to the message reader. " +
      "This allows custom configuration for a user-defined message reader.")
      .withRequiredArg
      .describedAs("prop")
      .ofType(classOf[String])
    val producerPropertyOpt = parser.accepts("producer-property", "A mechanism to pass user-defined properties in the form key=value to the producer. ")
      .withRequiredArg
      .describedAs("producer_prop")
      .ofType(classOf[String])
    val producerConfigOpt = parser.accepts("producer.config", s"Producer config properties file. Note that $producerPropertyOpt takes precedence over this config.")
      .withRequiredArg
      .describedAs("config file")
      .ofType(classOf[String])
    val useOldProducerOpt = parser.accepts("old-producer", "Use the old producer implementation.")

    val options = parser.parse(args : _*)
    if(args.length == 0)
      CommandLineUtils.printUsageAndDie(parser, "Read data from standard input and publish it to Kafka.")
    CommandLineUtils.checkRequiredArgs(parser, options, topicOpt, brokerListOpt)

    import scala.collection.JavaConversions._
    val useOldProducer = options.has(useOldProducerOpt)
    val topic = options.valueOf(topicOpt)
    val brokerList = options.valueOf(brokerListOpt)
    ToolsUtils.validatePortOrDie(parser,brokerList)
    val sync = options.has(syncOpt)
    val compressionCodecOptionValue = options.valueOf(compressionCodecOpt)
    val compressionCodec = if (options.has(compressionCodecOpt))
      if (compressionCodecOptionValue == null || compressionCodecOptionValue.isEmpty)
        DefaultCompressionCodec.name
      else compressionCodecOptionValue
    else NoCompressionCodec.name
    val batchSize = options.valueOf(batchSizeOpt)
    val sendTimeout = options.valueOf(sendTimeoutOpt)
    val queueSize = options.valueOf(queueSizeOpt)
    val queueEnqueueTimeoutMs = options.valueOf(queueEnqueueTimeoutMsOpt)
    val requestRequiredAcks = options.valueOf(requestRequiredAcksOpt)
    val requestTimeoutMs = options.valueOf(requestTimeoutMsOpt)
    val messageSendMaxRetries = options.valueOf(messageSendMaxRetriesOpt)
    val retryBackoffMs = options.valueOf(retryBackoffMsOpt)
    val keyEncoderClass = options.valueOf(keyEncoderOpt)
    val valueEncoderClass = options.valueOf(valueEncoderOpt)
    val socketBuffer = options.valueOf(socketBufferSizeOpt)
    val cmdLineProps = CommandLineUtils.parseKeyValueArgs(options.valuesOf(propertyOpt))
    val extraProducerProps = CommandLineUtils.parseKeyValueArgs(options.valuesOf(producerPropertyOpt))
    /* new producer related configs */
    val maxMemoryBytes = options.valueOf(maxMemoryBytesOpt)
    val maxPartitionMemoryBytes = options.valueOf(maxPartitionMemoryBytesOpt)
    val metadataExpiryMs = options.valueOf(metadataExpiryMsOpt)
    val maxBlockMs = options.valueOf(maxBlockMsOpt)
  }
}
