package com.example

import com.example.config.Config
import com.example.route.HealthRoute

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

import scala.concurrent.{ ExecutionContext, Future }
import scala.io.StdIn

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

  // val userRegistryActor: ActorRef = system.actorOf(UserRegistryActor.props, "userRegistryActor")

  //#main-class
  // from the UserRoutes trait
  lazy val routes: Route = healthRoute
  //#main-class

  //#http-server
  val serverBindingFuture: Future[ServerBinding] = Http().bindAndHandle(routes, host, port)

  println(s"Server online at http://$host:$port/\nPress RETURN to stop...")

  StdIn.readLine()

  serverBindingFuture
    .flatMap(_.unbind())
    .onComplete { done =>
      system.terminate()
    }
  //#http-server
  //#main-class
}
//#main-class
