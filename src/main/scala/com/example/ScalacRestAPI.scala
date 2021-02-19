package com.example

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._

import scala.io.StdIn

object ScalacRestAPI extends App {
    implicit val system = ActorSystem(Behaviors.empty, "rest_api_system")
    implicit val executionContext = system.executionContext

    val route =
      concat(
        (get & path("org" / Segment / "contributors")) {
          org_name => {
            concat(
              (parameter("page".as[Int])) {
                page =>
                  complete(HttpEntity(ContentTypes.`application/json`, "{\"msg\": \"Hello " + org_name + ", on page " + page + "!\"}"))
              },
              complete(HttpEntity(ContentTypes.`application/json`, "{\"msg\": \"Hello " + org_name + "!\"}"))
            )
          }
        },
        (get & path("test")) {
            complete(HttpEntity(ContentTypes.`application/json`, "{\"msg\": \"Hello World!\"}"))
        }
      )
    val hostname = "localhost"
    val port = 8080
    val bindingFuture = Http().newServerAt(hostname, port).bind(route)
    system.log.info(s"Starting Rest Api at ${hostname}:${port}")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
}
