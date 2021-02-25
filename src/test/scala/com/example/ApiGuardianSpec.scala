package com.example

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import com.example.ApiGuardian.{Command, Contributors, Organization, OrganizationRequest}
import com.example.GitHubClientActor.RepositoriesRequest
import org.scalatest.flatspec.AnyFlatSpecLike

import scala.collection.immutable.HashMap
import scala.concurrent.duration.DurationInt


class ApiGuardianSpec extends ScalaTestWithActorTestKit with AnyFlatSpecLike {

  behavior of "ApiGuardian"

  val gitHubClientProbe: TestProbe[Command] = createTestProbe[Command]()
  val scalacRestAPIProbe: TestProbe[Command] = createTestProbe[Command]()
  val mapperProbe: TestProbe[Command] = createTestProbe[Command]()
  val gitHubApiGuardian: ActorRef[Command] = spawn[Command](ApiGuardian(gitHubClientProbe.ref, mapperProbe.ref, 5.seconds))
  val testOrganization = "scalac"
  val testContributors: Map[String, Int] = HashMap("Jedi, Master" -> 7, "T1000, Terminator" -> 70)

  it should "receive an Organization message from ScalacRestApi and send a RepositoriesRequest to GitHubClient" in {
    gitHubApiGuardian ! OrganizationRequest(testOrganization, scalacRestAPIProbe.ref)
    mapperProbe.expectMessage(Organization(testOrganization))
  }

  it should "send a Contributors message to ScalacRestApi server if it receives the results from the ReducerActor" in {
    gitHubApiGuardian ! Contributors(testOrganization, testContributors)
    scalacRestAPIProbe.expectMessage(Contributors(testOrganization, testContributors))
  }

  it should "return a Contributors message immediately if it is already in the Cache" in {
    gitHubApiGuardian ! OrganizationRequest(testOrganization, scalacRestAPIProbe.ref)
    scalacRestAPIProbe.expectMessage(Contributors(testOrganization, testContributors))
    gitHubClientProbe.expectNoMessage()
  }

  it should "if it receives an Organization message from ScalacRestApi and the TTL of the Cache value is expired it should send a RepositoriesRequest to GitHubClient"  in {
    eventually (timeout(7.seconds)) {
      Thread.sleep(5.seconds.toMillis)
      gitHubApiGuardian ! OrganizationRequest(testOrganization, scalacRestAPIProbe.ref)
      mapperProbe.expectMessage(Organization(testOrganization))
    }
  }
}
