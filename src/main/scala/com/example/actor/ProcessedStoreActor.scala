package com.example.actor

import akka.actor.{ Actor, ActorSystem, ActorLogging, Props }

import scala.concurrent.{ Await, ExecutionContextExecutor, Future }

import com.example.model.Reddit._
import spray.json._
import com.example.model.JsonProtocol._
import com.example.config.ProcessorConfig

import java.io._
import scala.io.Source

object ProcessedStoreActor extends ProcessorConfig {
  final case class StoreProcessedItems(items: Iterable[NewsItem])

  def props: Props = Props(new ProcessedStoreActor(initialize))

  private def initialize: File = {
    if (!processedStore.exists) {
      processedStore.mkdirs
    }
    processedStore
  }
}

class ProcessedStoreActor(baseDir: File) extends Actor with ActorLogging {

  import RedditStoreActor._
  import ProcessedStoreActor._
  implicit val system: ActorSystem = ActorSystem("processed-store-service")
  implicit val executor = system.dispatcher

  def receive: Receive = {
    case StoreProcessedItems(items) =>
      store(items)
      sender() ! ActionPerformed(s"New Processed Item file Created")
  }

  private def store(items: Iterable[NewsItem]) = Future {
    val current = (System.currentTimeMillis / 1000).toInt.toString
    val location = new File(baseDir, s"$current.csv")
    location.createNewFile
    val fw = new FileWriter(location)
    items.foreach { item =>
      fw.write(item.csv)
    }
    fw.close
  }
}