package com.example.model.Reddit

import java.text.SimpleDateFormat

case class Listing(private val data: Data) {
  lazy val after: Option[String] = data.after
  lazy val count: Int = data.children.length
  lazy val newsItems = data.children.map(_.data)
}
case class Data(after: Option[String], children: List[Thing])
case class Thing(val data: NewsItem)
case class NewsItem(id: String, subreddit: String, score: Int, url: String, created: Long) {
  def rate: Int = score / (System.currentTimeMillis / 1000 - created).toInt
  def csv: String = {
    val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
    s"$id,$url,${dateFormat.format(created * 1000)}\n"
  }
}
case class BearerToken(
  access_token: String,
  scope: String,
  expires_in: Int,
  token_type: String
)