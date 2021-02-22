package com.example

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.example.GitHubClientActor.ContributionsResponse
import org.scalatest.flatspec.AnyFlatSpecLike

import scala.collection.immutable.HashMap

class ReducerActorSpec extends ScalaTestWithActorTestKit with AnyFlatSpecLike {
  import ReducerActor._
  import GitHubApiGuardian._

  behavior of "ReducerActor"

  it should "receive an Organization Repositories Contributions and return an Organization Contributors" in {
    val replyProbe = createTestProbe[Command]()
    val underTest = spawn(ReducerActor(State(replyTo = replyProbe.ref)))
    underTest ! OnStart("scalac")
    underTest ! ContributionsResponse("Jedi, Master", 7)
    underTest ! ContributionsResponse("T1000, Terminator", 70)
    underTest ! ContributionsResponse("Jedi, Master", 7)
    underTest ! OnTerminate()
    var result: Map[String, Int] = HashMap("Jedi, Master" -> 14, "T1000, Terminator" -> 70)
    replyProbe.expectMessage(Contributors("scalac", result))

  }

}