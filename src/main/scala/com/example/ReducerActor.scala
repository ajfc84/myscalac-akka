package com.example

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.example.GitHubApiGuardian.{Command, Contributors}
import com.example.MapperActor.Contributor

import scala.collection.immutable.HashMap

object ReducerActor {
  case class OnStart(organization: String) extends Command
  case class OnTerminate() extends Command
  case class State(org: String = "", result: HashMap[String, Int] = HashMap.empty, replyTo: ActorRef[Command])
  def apply(state: State): Behavior[Command] = onStart(state)

  private def onStart(state: State): Behavior[Command] =
    Behaviors.receive( (context, msg) => msg match {
      case OnStart(org: String) => onRequest(State(org, HashMap.empty, state.replyTo))
      case _ =>
        context.log.info("Unknown Command")
        Behaviors.same
    })

  private def onRequest(state: State): Behavior[Command] =
    Behaviors.receiveMessage {
      case Contributor(n, c) =>
        if (state.result.contains(n))
          onRequest(State(state.org, state.result + (n -> (state.result(n) + c)), state.replyTo))
        else
          onRequest(State(state.org, state.result + (n -> c), state.replyTo))
      case OnTerminate() =>
        state.replyTo ! Contributors(state.org, state.result)
        onStart(State(null, HashMap.empty, state.replyTo))
      case _ => Behaviors.unhandled
    }
}