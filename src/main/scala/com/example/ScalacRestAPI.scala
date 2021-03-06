package com.example

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server._
import spray.json.{DefaultJsonProtocol, PrettyPrinter, RootJsonFormat}
import ApiGuardian.{Command, Contributors, OrganizationRequest}
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.pattern.AskTimeoutException
import akka.util.Timeout

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration.DurationInt
import scala.io.StdIn


trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
    final case class Contributor(name: String, contributions: Int)
    implicit val printer: PrettyPrinter = PrettyPrinter
    implicit val contributorFormat: RootJsonFormat[Contributor] = jsonFormat2(Contributor)
}

object ScalacRestAPI extends Directives with JsonSupport {

    def main(args: Array[String]): Unit = {
        implicit val scalacSystem: ActorSystem[Command] = ActorSystem(ApiGuardian(30.seconds), "rest_api_system")
        implicit val executionContext: ExecutionContextExecutor = scalacSystem.executionContext
        implicit val timeout: Timeout = 30.seconds
        implicit def apiExceptionHandler: ExceptionHandler =
            ExceptionHandler {
                case _: AskTimeoutException =>
                    extractUri { uri =>
                      scalacSystem.log.info("Request Timeout: "+ uri)
                      complete(HttpResponse(StatusCodes.NotFound))
                    }
            }

        val routes = Route.seal({
            concat(
                (get & path("org" / Segment / "contributors")) {
                    org_name => {/*
                        concat(
                            parameter("page".as[Int]) {
                                page => {
                                    // system ? Organization(org_name)
                                    complete(HttpEntity(ContentTypes.`application/json`, "{\"msg\": \"Unimplemented!\"}"))
                                }
                            }, {*/
                                val response: Future[Command] = scalacSystem.ask(OrganizationRequest(org_name, _))
                                onSuccess(response) {
                                    case msg: Contributors =>
                                        complete(for(c <- msg.contributors) yield Contributor(c._1, c._2))
                                    case _ => complete(StatusCodes.NotFound)
                                }
                            //}
                        //)
                    }
                },
                (get & path("test")) {
                    complete(HttpEntity(ContentTypes.`application/json`, "{\"msg\": \"Hello World!\"}"))
                }
            )
        })
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
