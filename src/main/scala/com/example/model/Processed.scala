package com.example.model.Processed

import com.example.model.Reddit.NewsItem

object ProcessedItem {
  def apply(list: List[NewsItem]): ProcessedItem = {
    list.length match {
      case 0 => throw new Exception("Invalid List")
      case 1 =>
        val item = list.head
        ProcessedItem(item.id, item.url, item.score)
      case _ =>
        val item = list.last
        ProcessedItem(item.id, item.url, item.score)
    }
  }
}
case class ProcessedItem(id: String, url: String, score: Int, rate: Int = 0)