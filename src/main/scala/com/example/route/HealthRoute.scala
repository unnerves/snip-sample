package com.example.route

import akka.actor.{ ActorRef, ActorSystem }
import akka.event.Logging

import scala.concurrent.duration._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.delete
import akka.http.scaladsl.server.directives.MethodDirectives.get
import akka.http.scaladsl.server.directives.MethodDirectives.post
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.http.scaladsl.server.directives.PathDirectives.path

//#user-routes-class
trait HealthRoute {
  //#user-routes-class

  // we leave these abstract, since they will be provided by the App
  implicit def system: ActorSystem

  lazy val healthRoute: Route =
    pathPrefix("__health") {
      pathEnd {
        get {
          complete(StatusCodes.OK)
        }
      }
    }
}