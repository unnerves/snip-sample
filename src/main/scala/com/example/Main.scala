package com.example

import com.example.config.Config
import com.example.route.HealthRoute

import akka.pattern.ask
import akka.util.Timeout
import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.{ Failure, Success }

import com.example.actor._
import com.example.model.Reddit.NewsItem
import com.example.actor.RedditStoreActor.GetNewsItems

//#main-class
object QuickstartServer extends App with Config with HealthRoute {

  // set up ActorSystem and other dependencies here
  //#main-class
  //#server-bootstrapping
  implicit val system: ActorSystem = ActorSystem("SnipSampleServer")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  //#server-bootstrapping

  // Needed for the Future and its methods flatMap/onComplete in the end
  implicit val executionContext: ExecutionContext = system.dispatcher

  val processorActor: ActorRef = system.actorOf(ProcessorActor.props, "processorActor")
  val processedStoreActor: ActorRef = system.actorOf(ProcessedStoreActor.props, "processStoreActor")
  val redditAuthActor: ActorRef = system.actorOf(RedditAuthActor.props, "redditAuthActor")
  val redditStoreActor: ActorRef = system.actorOf(RedditStoreActor.props, "redditStoreActor")
  val redditApiActor: ActorRef = system.actorOf(RedditApiActor.props(redditStoreActor, processorActor), "redditApiActor")
  val redditClientActor: ActorRef = system.actorOf(RedditClientActor.props(
    redditAuthActor,
    redditApiActor
  ))

  system.scheduler.schedule(0 milliseconds, interval minutes) {
    RedditClientActor.startFromSources(redditClientActor)
  }

  implicit lazy val timeout = Timeout(5.seconds)
  system.scheduler.schedule(interval minutes, interval minutes) {
    import ProcessorActor._
    import ProcessedStoreActor._

    (processorActor ? ProcessedItems).mapTo[Iterable[NewsItem]] onComplete {
      case Success(items) => processedStoreActor ! StoreProcessedItems(items)
      case Failure(ex) => println(s"Error occurred: $ex")
    }
  }

  lazy val routes: Route = healthRoute

  val serverBindingFuture: Future[ServerBinding] = Http().bindAndHandle(routes, host, port)

  println(s"Server online at http://$host:$port/\nPress RETURN to stop...")

  StdIn.readLine()

  serverBindingFuture
    .flatMap(_.unbind())
    .onComplete { done =>
      system.terminate()
    }
}
