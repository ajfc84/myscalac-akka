package com.example

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import com.example.ApiGuardian.Command
import org.scalatest.flatspec.AnyFlatSpecLike

class GitHubClientActorSpec extends ScalaTestWithActorTestKit with AnyFlatSpecLike {

  behavior of "GitHubClientActor"

  val mapperProbe: TestProbe[Command] = createTestProbe[Command]()
  val gitHubClientActor: ActorRef[Command] = spawn(GitHubClientActor())

  val testOrganization = "angular"
  val testRepositories = Set("test", "prod")

  it should "send a request to GitHubApi when it receives a RepositoriesRequest" in {

  }

}
