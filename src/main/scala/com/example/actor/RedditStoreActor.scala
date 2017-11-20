package com.example.actor

import akka.actor.{ Actor, ActorSystem, ActorLogging, Props }

import scala.concurrent.{ Await, ExecutionContextExecutor, Future }

import com.example.model.Reddit._
import spray.json._
import com.example.model.JsonProtocol._
import com.example.config.RedditConfig

import java.io._
import scala.io.Source

object RedditStoreActor extends RedditConfig {

  final case class ActionPerformed(description: String)
  final case object GetNewsItems
  final case class AddNewsItems(listing: Listing)

  def props: Props = Props(new RedditStoreActor(initialize))

  private def initialize: File = {
    if (!rawRedditStore.exists) {
      rawRedditStore.mkdirs
    }
    rawRedditStore
  }
}

class RedditStoreActor(baseDir: File) extends Actor with ActorLogging {

  import RedditStoreActor._
  implicit val system: ActorSystem = ActorSystem("reddit-store-service")
  implicit val executor = system.dispatcher

  def receive: Receive = {
    case GetNewsItems =>
      sender() ! retrieve
    case AddNewsItems(listing) =>

      store(listing.newsItems)

      sender() ! ActionPerformed(s"${listing.count} News Items added.")
  }

  private def store(items: List[NewsItem]) = Future {
    items.foreach { item =>
      val location = new File(baseDir, item.id)
      if (!location.exists) {
        location.createNewFile
      }
      val fw = new FileWriter(location, true)
      fw.write(item.toJson.compactPrint)
      fw.close
    }
  }

  private def retrieve(): Iterator[File] = {
    baseDir.listFiles.toIterator
  }
}