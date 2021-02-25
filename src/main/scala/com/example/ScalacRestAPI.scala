package com.example

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, PrettyPrinter, RootJsonFormat}
import ApiGuardian.{Command, Contributors, OrganizationRequest}
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.util.Timeout

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration.DurationInt
import scala.io.StdIn


trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val printer: PrettyPrinter = PrettyPrinter
    implicit val contributorsFormat: RootJsonFormat[Contributors] = jsonFormat2(Contributors)
}

object ScalacRestAPI extends Directives with JsonSupport {

    def main(args: Array[String]): Unit = {
        implicit val scalacSystem: ActorSystem[Command] = ActorSystem(ApiGuardian(30.seconds), "rest_api_system")
        implicit val executionContext: ExecutionContextExecutor = scalacSystem.executionContext
        implicit val timeout: Timeout = 10.seconds
        val routes = {
            concat(
                (get & path("org" / Segment / "contributors")) {
                    org_name => {
                        concat(
                            parameter("page".as[Int]) {
                                page => {
                                    // system ? Organization(org_name)
                                    complete(HttpEntity(ContentTypes.`application/json`, "{\"msg\": \"Unimplemented!\"}"))
                                }
                            }, {
                                val response: Future[Command] = scalacSystem.ask(OrganizationRequest(org_name, _))
                                onSuccess(response) {
                                    case msg: Contributors => complete(msg)
                                }
                            }
                        )
                    }
                },
                (get & path("test")) {
                    complete(HttpEntity(ContentTypes.`application/json`, "{\"msg\": \"Hello World!\"}"))
                }
            )
        }
        val hostname = "localhost"
        val port = 8080
        val bindingFuture = Http().newServerAt(hostname, port).bind(routes)
        scalacSystem.log.info("Starting Scalac Rest API Server at host: " + hostname + ", port: " + port)
        StdIn.readLine()
        bindingFuture
          .flatMap(_.unbind())
          .onComplete(_ => scalacSystem.terminate())
    }

}
