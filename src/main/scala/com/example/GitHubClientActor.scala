package com.example

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCode, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.example.ApiGuardian.Command

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat


object GitHubClientActor {

  case class RepositoriesRequest(organization: String, replyTo: ActorRef[Command]) extends Command
  case class RepositoriesResponse(organization: String, repositories: Set[String]) extends Command
  case class StartContributionsRequest() extends Command
  case class ContributionsRequest(organization: String, repository: String, replyTo: ActorRef[Command]) extends Command
  case class ContributionsResponse(organization: String, repository: String, contributions: Map[String, Int]) extends Command

  case class TerminateContributionsRequest() extends Command
  case class GitHubRepositoryResponse(organization: String, repositories: Set[String], replyTo: ActorRef[Command]) extends Command
  case class GitHubContributorResponse(organization: String, repository: String, contributions: Map[String, Int], replyTo: ActorRef[Command]) extends Command

  case class Http2RepositoryJsonResult(json: Future[Set[GitHubRepository]], organization: String, replyTo: ActorRef[Command]) extends Command
  case class Http2ContributorJsonResult(json: Future[Set[GitHubContributor]], organization: String, repository: String, replyTo: ActorRef[Command]) extends Command

  case class MarshallerError() extends Command
  case class GitHubError() extends Command

  case class GitHubRepository(name: String)
  case class GitHubContributor(login: String, contributions: Int)

  implicit val gitHubRepositoryFormat: RootJsonFormat[GitHubRepository] = jsonFormat1(GitHubRepository)
  implicit val gitHubContributorFormat: RootJsonFormat[GitHubContributor] = jsonFormat2(GitHubContributor)

  def apply(): Behavior[Command] = onRequests()

  def onRequests(): Behavior[Command] = Behaviors.receive(
    (context, msg) => {
      implicit val executionContext: ExecutionContextExecutor = context.executionContext
      implicit val system: ActorSystem[Nothing] = context.system
      msg match {
        case RepositoriesRequest(org, replyTo) =>
          context.pipeToSelf(Http().singleRequest(HttpRequest(uri = "https://api.github.com/orgs/" + org + "/repos"))) {
            case Success(response@HttpResponse(StatusCodes.OK, _, _, _)) => Http2RepositoryJsonResult(Unmarshal(response).to[Set[GitHubRepository]], org, replyTo)
            case Success(response) =>
            response.discardEntityBytes()
            GitHubError()
          case Failure(_) => GitHubError()
          }
          Behaviors.same
        case Http2RepositoryJsonResult(json, org, replyTo) =>
          context.pipeToSelf(json) {
            case Success(result) =>
              val repos = for (r <- result) yield r.name
              GitHubRepositoryResponse(org, repos, replyTo)
            case Failure(_) => MarshallerError()
          }
        Behaviors.same
        case GitHubRepositoryResponse(org, repos, replyTo) =>
          replyTo ! RepositoriesResponse(org, repos)
          Behaviors.same
        case ContributionsRequest(org, repo, replyTo) =>
          context.pipeToSelf(Http().singleRequest(HttpRequest(uri = "https://api.github.com/repos/" + org + "/" + repo + "/contributors"))) {
            case Success(response@HttpResponse(StatusCodes.OK, _, _, _)) => Http2ContributorJsonResult(Unmarshal(response).to[Set[GitHubContributor]], org, repo, replyTo)
            case Success(response) =>
            response.discardEntityBytes()
            GitHubError()
            case Failure(_) => GitHubError()
          }
          Behaviors.same
        case Http2ContributorJsonResult(json, org, repo, replyTo) =>
          context.pipeToSelf(json) {
            case Success(result) =>
              val contributors = result.map(c => c.login -> c.contributions).toMap[String, Int]
              GitHubContributorResponse(org, repo, contributors, replyTo)
            case Failure(_) => MarshallerError()
          }
        Behaviors.same
        case GitHubContributorResponse(org, repo, contributions, replyTo) =>
          replyTo ! ContributionsResponse(org, repo, contributions)
          Behaviors.same
        case GitHubError() =>
          context.log.info("GitHub API Client Error: Repository discarded!")
          Behaviors.same
        case MarshallerError() =>
          context.log.info("Marshaller Error: Repository discarded!")
          Behaviors.same
        case _ =>
          context.log.info("Invalid Command: Message discarded!")
          Behaviors.same
      }
  })

}