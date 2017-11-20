package com.example.actor

import java.io.IOException

import akka.actor.{ Actor, ActorLogging, ActorSystem, ActorRef, Props, FSM }
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.OutgoingConnection
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import akka.stream.ActorMaterializer

import scala.util.{ Failure, Success }
import scala.concurrent.{ Await, ExecutionContextExecutor, Future }
import scala.collection.immutable.Queue
import scala.math.min

import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout

import com.example.model.Reddit._
import com.example.model.JsonProtocol
import com.example.config.RedditConfig

final case class Window(path: String, page: Option[String] = None, count: Option[Int] = None) {
  lazy val query: Map[String, String] = (page, count) match {
    case (Some(p), Some(c)) => Map("after" -> p, "count" -> s"$c")
    case (_, _) => Map.empty[String, String]
  }
}

sealed trait State
case object Idle extends State
case object Active extends State

case object Tick
case class Rate(numberOfCalls: Int, duration: FiniteDuration) {
  def durationInMillis = duration.toMillis
}

sealed case class Data(
  bearerToken: Option[BearerToken],
  remaining: Int,
  queue: Queue[Window]
)

object RedditClientActor extends RedditConfig {
  def props(auth: ActorRef, api: ActorRef, rate: Rate = Rate(apiRate / 60, 1 second)): Props = Props(new RedditClientActor(auth, api, rate))

  def startFromSources(ref: ActorRef) = sources.foreach { source =>
    ref ! Enqueue(Window(source))
  }

  final case class Enqueue(window: Window)
  final case class AuthenticationCompleted(token: BearerToken)
  final case object AuthenticationFailure
}

class RedditClientActor(
    auth: ActorRef,
    api: ActorRef,
    rate: Rate
) extends Actor with ActorLogging with FSM[State, Data] {
  implicit val system: ActorSystem = ActorSystem("reddit-client-service")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executor = system.dispatcher

  import RedditClientActor._
  import RedditAuthActor._
  import RedditApiActor._

  startWith(Idle, Data(None, rate.numberOfCalls, Queue.empty[Window]))

  when(Idle) {
    // Queueing
    case Event(Enqueue(window), data @ Data(None, _, _)) =>
      log.info("Received Enqueue event when Idle and not Authn => Stay")
      stay using authenticate(Data(data.bearerToken, data.remaining, data.queue.enqueue(window)))
    case Event(Enqueue(window), data @ Data(Some(_), _, _)) =>
      log.info("Received Enqueue event when Idle when Authn => Active")
      goto(Active) using deliver(Data(data.bearerToken, data.remaining, data.queue.enqueue(window)))

    // Authentication
    case Event(AuthenticationCompleted(token), data) if !data.queue.isEmpty =>
      log.info("Received Authn event when Idle and Queue not empty => Active")
      goto(Active) using deliver(Data(Some(token), data.remaining, data.queue))
    case Event(AuthenticationCompleted(token), data) =>
      log.info("Received Authn event when Idle and no Queue => Stay")
      stay using Data(Some(token), data.remaining, data.queue)
  }

  when(Active) {
    // Queueing
    case Event(Enqueue(window), data @ Data(_, 0, queue)) =>
      log.info("Received Enqueue event when Active but Full => Stay, Queue")
      stay using Data(data.bearerToken, data.remaining, queue.enqueue(window))
    case Event(Enqueue(window), data @ Data(_, _, queue)) =>
      log.info("Received Enqueue event when Active but not Full => Stay")
      stay using deliver(Data(data.bearerToken, data.remaining, queue.enqueue(window)))
    case Event(Tick, data @ Data(_, _, Seq())) =>
      log.info("Received Tick event when Active but Empty => Idle")
      goto(Idle)
    case Event(Tick, data @ Data(_, _, _)) =>
      log.info("Received Tick event when Active with Queue => Stay")
      stay using deliver(Data(data.bearerToken, rate.numberOfCalls, data.queue))

    // Authentication
    case Event(AuthenticationFailure, data) =>
      log.info("Received No Authn event when Active => Idle, Queue")
      goto(Idle) using Data(None, data.remaining, data.queue)
    case Event(AuthenticationCompleted(token), data) =>
      log.info("Received Authn event when Active => Stay")
      stay using Data(Some(token), data.remaining, data.queue)
  }

  onTransition {
    case Idle -> Active => setTimer("moreVouchers", Tick, rate.duration, repeat = true)
    case Active -> Idle => cancelTimer("moreVouchers")
  }

  initialize

  private def deliver(data: Data): Data = {
    val toSend = min(data.queue.length, data.remaining)
    data.queue.take(toSend).map(RetrieveWindow(_, data.bearerToken)).foreach {
      window =>
        api ! window
    }

    Data(
      data.bearerToken,
      data.remaining - toSend,
      data.queue.drop(toSend)
    )
  }

  private def authenticate(data: Data): Data = {
    auth ! Authenticate
    Data(data.bearerToken, data.remaining, data.queue)
  }
}

object RedditAuthActor {
  def props = Props[RedditAuthActor]

  final case object Authenticate
}

class RedditAuthActor extends Actor with ActorLogging with AuthorizationSupport with RedditConfig {
  implicit val system: ActorSystem = ActorSystem("reddit-auth-service")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executor = system.dispatcher

  import RedditAuthActor._
  import RedditClientActor.AuthenticationCompleted

  private var authenticating = false

  def receive: Receive = {
    case Authenticate if !authenticating =>
      val currentSender = sender()
      authenticating = true
      retrieveBearerToken onComplete {
        case Success(token) =>
          currentSender ! AuthenticationCompleted(token)
          authenticating = false
        case Failure(ex) => log.error(s"Failure occurred: $ex")
      }
    case Authenticate if authenticating =>
      log.info("Currently authenticating...")
  }
}

object RedditApiActor {

  def props(store: ActorRef, process: ActorRef): Props = Props(new RedditApiActor(store, process))
  final case class RetrieveWindow(window: Window, token: Option[BearerToken])
}

class RedditApiActor(store: ActorRef, process: ActorRef) extends Actor with ActorLogging with RedditSupport {
  implicit val system: ActorSystem = ActorSystem("reddit-api-service")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executor = system.dispatcher
  implicit lazy val timeout = Timeout(5.seconds)

  import RedditApiActor._
  import RedditStoreActor._
  import RedditClientActor._
  import ProcessorActor._

  def receive: Receive = {
    case RetrieveWindow(window, token) =>
      val currentSender = sender()
      listingForWindow(window, token) onComplete {
        case Success(listing) =>
          paginate(listing, window) match {
            case Some(page) => currentSender ! Enqueue(page)
            case None => log.info("No more pages to retrieve")
          }
          listing.newsItems.filter { item =>
            item.created > (currentTime - day)
          }.foreach { item =>
            process ! Process(item)
          }
          (store ? AddNewsItems(listing)).mapTo[ActionPerformed] onComplete {
            case Success(reply) => println(s"Replied: ${reply.description}")
            case Failure(ex) => println(s"Failed when storing: $ex")
          }
        case Failure(ex @ UnauthorizedException(_, _)) =>
          currentSender ! AuthenticationFailure
          currentSender ! Enqueue(window) // re-queue the previous attempt
        case Failure(ex) => log.error(s"Failure occurred: $ex")
      }
  }

  private def paginate(listing: Listing, window: Window): Option[Window] = {
    listing.after match {
      case Some(page) => Some(Window(window.path, Some(page), Some(window.count.getOrElse(0) + listing.count)))
      case None => None
    }
  }
}

trait RedditSupport extends AuthorizedHttpSupport with JsonProtocol with RedditConfig {

  val currentTime: Long = (System.currentTimeMillis / 1000)

  def listingForWindow(window: Window, bearerToken: Option[BearerToken]): Future[Listing] = {
    val token = bearerToken match {
      case Some(bt) => bt.access_token
      case None => ""
    }
    makeRequest(simpleRequest(
      s"$apiPath${window.path}",
      window.query,
      List(RawHeader("Authorization", s"bearer $token"))
    )).flatMap { response =>
      response.status match {
        case StatusCodes.OK =>
          Unmarshal(response.entity).to[Listing]
        case StatusCodes.Unauthorized =>
          Future.failed(new UnauthorizedException)
        case _ =>
          Future.failed(new IOException(response.toString))
      }
    }
  }
}