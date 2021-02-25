package com.example

import akka.actor.typed.scaladsl._
import akka.actor.typed.{ActorRef, Behavior}
import com.example.GitHubClientActor._
import com.example.ApiGuardian._
import com.example.ReducerActor.{OnStart, OnTerminate}

import scala.concurrent.duration.DurationInt

object MapperActor {
  case class Contributor(name: String, contributions: Int) extends Command
  private case class UnStashed(command: Command) extends Command
  private case class Conf(routerRef: ActorRef[Command], reducerRef: ActorRef[Command], buffer: StashBuffer[Command])

  def apply(gitHubClientRouterRef: ActorRef[Command], reducerActorRef: ActorRef[Command]): Behavior[Command] = {
    Behaviors.withStash[Command](100) { buffer =>
      onRepositories(Conf(gitHubClientRouterRef, reducerActorRef, buffer))
    }
  }

  def onRepositories(conf: Conf): Behavior[Command] =
    Behaviors.receive[Command]( (context, msg) => msg match {
      case Organization(org) =>
        conf.routerRef ! RepositoriesRequest(org, context.self)
        Behaviors.same
      case RepositoriesResponse(org, repositories) =>
        for (repo <- repositories) conf.routerRef ! ContributionsRequest(org, repo, context.self)
        conf.reducerRef ! OnStart(org)
        onContributions(conf, repositories)
      case ukn =>
        context.log.info("Organization or Repository not understood: " + ukn)
        Behaviors.same
    })

  def onContributions(conf: Conf, repos: Set[String]): Behavior[Command] =
    Behaviors.withTimers[Command] { timers =>
      timers.startSingleTimer(OnTerminate(), 10.second)
      Behaviors.receive[Command] { (context, msg) =>
        msg match {
          case Organization(_) | RepositoriesResponse(_, _)=>
            if(conf.buffer.isFull) context.log.warn("Mapper Actor StashBuffer full: " + msg + " dropped!")
            else conf.buffer.stash(msg)
            Behaviors.same
          case ContributionsResponse(_, repository, contributions) if repos.contains(repository) =>
            for ((n, c) <- contributions) conf.reducerRef ! Contributor(n, c)
            val remainingRepos = repos - repository
            if (remainingRepos.isEmpty) {
              conf.reducerRef ! OnTerminate()
              timers.cancel(OnTerminate())
              conf.buffer.unstash(onUnStashed(conf), 1, UnStashed)
            }
            else
              onContributions(conf, remainingRepos)
          case OnTerminate() =>
            context.log.info("MapperActor Timeout awaiting for response from GitHubClientActor")
            conf.reducerRef ! OnTerminate()
            timers.cancel(OnTerminate())
            conf.buffer.unstash(onUnStashed(conf), 1, UnStashed)
          case ukn =>
            context.log.info("Contribution not understood: " + ukn)
            Behaviors.same
        }
      }
    }

  def onUnStashed(conf: Conf): Behavior[Command] =
    Behaviors.receive[Command]( (context, msg) => msg match {
      case UnStashed(Organization(org)) =>
        conf.routerRef ! RepositoriesRequest(org, context.self)
        if(conf.buffer.isEmpty) onRepositories(conf)
        else conf.buffer.unstash(onUnStashed(conf), 1, UnStashed)
      case UnStashed(RepositoriesResponse(org, repositories)) =>
        for (repo <- repositories) conf.routerRef ! ContributionsRequest(org, repo, context.self)
        conf.reducerRef ! OnStart(org)
        onContributions(conf, repositories)
    })
}