package com.example.actor

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.event.LoggingAdapter

import com.example.model.Reddit.NewsItem
import com.example.config.ProcessorConfig

object ProcessorActor {

  def props = Props[ProcessActor]

  case class Process(item: NewsItem)
  case object ProcessedItems
  case class ItemProcessed(description: String)
}

class ProcessActor extends Actor with ActorLogging with ProcessorConfig {

  import ProcessorActor._

  var processedItems = scala.collection.mutable.TreeSet.empty[NewsItem](NewsItemOrdering)
  var itemIndex = scala.collection.mutable.Map.empty[String, NewsItem]

  def receive: Receive = {
    case Process(item) =>
      // if an object has already been seen, remove it and add the new object
      if (itemIndex.contains(item.id)) {
        processedItems.remove(itemIndex(item.id))

      }
      itemIndex(item.id) = item
      processedItems.add(item)
      if (processedItems.size > maxItems) {
        processedItems.remove(processedItems.head)
      }
      sender() ! ItemProcessed(s"Processed item $item")
    case ProcessedItems =>
      sender() ! processedItems.toList.reverse
  }
}

object NewsItemOrdering extends Ordering[NewsItem] with ProcessorConfig {
  def compare(aqui: NewsItem, alla: NewsItem) = {
    (aqui.score * scoreNormalization) compare (alla.score * scoreNormalization)
  }
}