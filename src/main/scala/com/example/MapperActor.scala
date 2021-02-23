package com.example

import akka.actor.typed.scaladsl._
import akka.actor.typed.{ActorRef, Behavior}
import com.example.GitHubClientActor._
import com.example.GitHubApiGuardian._
import com.example.ReducerActor.OnTerminate

import scala.concurrent.duration.DurationInt

object MapperActor {
  case class Contributor(name: String, contributions: Int) extends Command
  private case class Conf(routerRef: ActorRef[Command], reducerRef: ActorRef[Command], buffer: StashBuffer[Command])
  def apply(gitHubClientRouterRef: ActorRef[Command], reducerActorRef: ActorRef[Command]): Behavior[Command] = {
    Behaviors.withStash[Command](100) { buffer =>
      onRepositories(Conf(gitHubClientRouterRef, reducerActorRef, buffer))
    }
  }

  def onRepositories(conf: Conf): Behavior[Command] =
    Behaviors.receive[Command]( (context, msg) => msg match {
      case RepositoriesResponse(org, repositories) =>
        conf.routerRef ! StartContributionsRequest()
        for (repo <- repositories) conf.routerRef ! ContributionsRequest(org, repo, context.self)
        conf.routerRef ! TerminateContributionsRequest ()
        onContributions(conf, repositories)
      case _ =>
        context.log.info("Message not understood!")
        Behaviors.same
    })

  def onContributions(conf: Conf, repos: Set[String]): Behavior[Command] =
    Behaviors.withTimers[Command] { timers =>
      timers.startSingleTimer(OnTerminate(), 3.second)
      Behaviors.receive[Command] { (context, msg) =>
        msg match {
          case RepositoriesResponse(_, _) =>
            conf.buffer.stash(msg)
            Behaviors.same
          case ContributionsResponse(_, repository, contributions) if repos.contains(repository) =>
            for ((n, c) <- contributions) conf.reducerRef ! Contributor(n, c)
            val remainingRepos = repos - repository
            if (remainingRepos.isEmpty) {
              conf.reducerRef ! OnTerminate()
              timers.cancel(OnTerminate())
              conf.buffer.unstashAll(onRepositories(conf))
            }
            else
              onContributions(conf, remainingRepos)
          case OnTerminate() =>
            context.log.info("MapperActor Timeout awaiting for response from GitHubClientActor")
            conf.reducerRef ! OnTerminate()
            timers.cancel(OnTerminate())
            conf.buffer.unstashAll(onRepositories(conf))
          case _ =>
            context.log.info("Message not understood!")
            Behaviors.same
        }
      }
    }

}