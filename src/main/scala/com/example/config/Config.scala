package com.example.config

import java.io.File
import com.typesafe.config._
import scala.collection.JavaConverters._

trait Config {
  val config = ConfigFactory.load
  private val httpConfig = config.getConfig("http")

  lazy val host = httpConfig.getString("host")
  lazy val port = httpConfig.getInt("port")

  lazy val interval = config.getInt("interval")
}

trait RedditConfig extends Config {
  private val redditConfig = config.getConfig("reddit")

  lazy val day = redditConfig.getLong("day")
  lazy val baseUrl = redditConfig.getString("baseUrl")
  lazy val authBaseUrl = redditConfig.getString("authBaseUrl")
  lazy val userAgent = redditConfig.getString("userAgent")
  lazy val apiPath = redditConfig.getString("path.api")
  lazy val authorizationPath = redditConfig.getString("path.auth")
  lazy val clientId = redditConfig.getString("clientId")
  lazy val clientSecret = redditConfig.getString("clientSecret")
  lazy val sources = redditConfig.getStringList("sources").asScala
  lazy val apiRate = redditConfig.getInt("rate")
  lazy val rawRedditStore = new File(redditConfig.getString("store"))
}

trait ProcessorConfig extends Config {
  private val processorConfig = config.getConfig("process")

  lazy val maxItems = processorConfig.getInt("maxItems")
  lazy val rateNormalization = processorConfig.getInt("normalize.rate")
  lazy val scoreNormalization = processorConfig.getInt("normalize.score")
  lazy val processedStore = new File(processorConfig.getString("store"))
}