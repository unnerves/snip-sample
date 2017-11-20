package com.example.actor

import java.io.IOException

import akka.actor.{ Actor, ActorLogging, ActorSystem, ActorRef, Props }
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.OutgoingConnection
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }
import akka.stream.scaladsl.{ Flow, Sink, Source }

import scala.concurrent.{ ExecutionContextExecutor, Future }

import com.example.model.Reddit.BearerToken
import com.example.model.JsonProtocol

case class UnauthorizedException(message: String = "Unauthorized", cause: Throwable = None.orNull) extends Exception

trait HttpSupport {

  implicit val baseUrl: String
  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  implicit val executor: ExecutionContextExecutor

  implicit lazy val serviceConnection: Flow[HttpRequest, HttpResponse, Future[OutgoingConnection]] = Http().outgoingConnectionHttps(baseUrl)

  def makeRequest(request: HttpRequest): Future[HttpResponse] = {
    Source.single(request).via(serviceConnection).runWith(Sink.head)
  }
}

trait AuthorizedHttpSupport extends HttpSupport {

  implicit val userAgent: String

  lazy private val commonHeaders = List(
    Accept(MediaTypes.`application/json`)
  )

  def simpleRequest(
    path: String,
    query: Map[String, String],
    headers: List[HttpHeader]
  ) = HttpRequest(
    HttpMethods.GET,
    Uri(path).withQuery(Query(query)),
    commonHeaders ++ headers
  )
}

trait AuthorizationSupport extends HttpSupport with JsonProtocol {
  implicit val clientId: String
  implicit val clientSecret: String
  implicit val authBaseUrl: String
  implicit val authorizationPath: String

  override lazy val serviceConnection: Flow[HttpRequest, HttpResponse, Future[OutgoingConnection]] = Http().outgoingConnectionHttps(authBaseUrl)

  lazy private val authHeaders = List(
    Accept(MediaTypes.`application/json`),
    Authorization(BasicHttpCredentials(clientId, clientSecret))
  )

  def retrieveBearerToken(): Future[BearerToken] = {
    val request = HttpRequest(
      HttpMethods.POST,
      Uri(authorizationPath),
      authHeaders,
      FormData("grant_type" -> "client_credentials").toEntity
    )

    makeRequest(request).flatMap { response =>
      response.status match {
        case StatusCodes.OK =>
          val raw = Unmarshal(response.entity).to[String]
          println(raw)
          Unmarshal(response.entity).to[BearerToken]
        case _ =>
          Future.failed(new IOException(response.toString))
      }
    }
  }
}