package com.example.model

import com.example.model.Reddit._

import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

trait JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val newsItemFormat = jsonFormat5(NewsItem.apply)
  implicit val thingFormat = jsonFormat(Thing, "data")
  implicit val dataFormat = jsonFormat2(Data.apply)
  implicit val listingFormat = jsonFormat(Listing, "data")
  implicit val bearerTokenFormat = jsonFormat4(BearerToken.apply)
}

object JsonProtocol extends JsonProtocol {}