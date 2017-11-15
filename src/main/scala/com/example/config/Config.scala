package com.example.config

import com.typesafe.config._

trait Config {
  private val config = ConfigFactory.load
  private val httpConfig = config.getConfig("http")

  lazy val host = httpConfig.getString("host")
  lazy val port = httpConfig.getInt("port")
}