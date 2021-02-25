package com.example

import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, Routers}
import com.example.ApiGuardian.{Actors, Command}
import com.example.GitHubClientActor._

import scala.collection.mutable
import scala.concurrent.duration.{Duration, DurationInt}


object ApiGuardian {

  trait Command
  final case class OrganizationRequest(name: String, replyTo: ActorRef[Command]) extends Command
  final case class Organization(name: String) extends Command
  final case class Contributors(organization: String, contributors: Map[String, Int]) extends Command

  private val organizationsCache = mutable.HashMap[String, (Map[String, Int], Long)]()
  private val requestsTable = mutable.HashMap[String, ActorRef[Command]]()

  private final case class Actors(routerRef: ActorRef[Command], mapperRef: ActorRef[Command])

  def apply(ttl: Duration = 5.seconds): Behavior[Command] = Behaviors.setup(
    context => {
      val gitHubClientPool = Routers.pool(5)(GitHubClientActor()).withBroadcastPredicate(
        msg => msg.isInstanceOf[StartContributionsRequest] ||
          msg.isInstanceOf[TerminateContributionsRequest])
//      val clientRouterRef = context.spawn(gitHubClientPool, "github-client-pool")
      val clientRouterRef = context.spawn(GitHubClientActor(), "github-client-pool")
      val reducerActorRef = context.spawn(ReducerActor(context.self), "reducer_actor")
      val mapperActorRef = context.spawn(MapperActor(clientRouterRef, reducerActorRef), "mapper_actor")

      new ApiGuardian(context, Actors(clientRouterRef, mapperActorRef), ttl)
    }
  )

  def apply(clientActorRef: ActorRef[Command], mapperActorRef: ActorRef[Command], ttl: Duration): Behavior[Command] = Behaviors.setup(
      new ApiGuardian(_, Actors(clientActorRef, mapperActorRef), ttl)
  )

}

class ApiGuardian(context: ActorContext[Command], actors: Actors, ttl: Duration) extends AbstractBehavior[Command](context) {
  context.log.info("Starting Scalac Rest API System")
  import ApiGuardian._

  override def onMessage(msg: Command): Behavior[Command] = msg match {
      case OrganizationRequest(org, replyTo) =>
        if(organizationsCache.contains(org) && (System.currentTimeMillis() - organizationsCache(org)._2) < ttl.toMillis)
          replyTo ! Contributors(org, organizationsCache(org)._1)
        else {
          actors.mapperRef ! Organization(org)
          requestsTable += (org -> replyTo)
        }
        Behaviors.same
      case Contributors(org, count) =>
        val ttl = System.currentTimeMillis()
        organizationsCache += (org -> (count, ttl))
        requestsTable(org) ! Contributors(org, organizationsCache(org)._1)
        requestsTable -= org
        Behaviors.same
  }
  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      context.log.info("Stopping Scalac Rest API")
      this
  }

}
