package com.example

import akka.actor.testkit.typed.scaladsl.{LoggingTestKit, ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import com.example.ApiGuardian.{Command, Organization}
import com.example.GitHubClientActor._
import com.example.MapperActor._
import com.example.ReducerActor.{OnStart, OnTerminate}
import org.scalatest.flatspec.AnyFlatSpecLike

import scala.collection.immutable.HashMap

class MapperActorSpec extends ScalaTestWithActorTestKit with AnyFlatSpecLike {

  behavior of "MapperActor"

  val routerProbe: TestProbe[Command] = createTestProbe[Command]()
  val reducerProbe: TestProbe[Command] = createTestProbe[Command]()
  val mapperActor: ActorRef[Command] = spawn(MapperActor(routerProbe.ref, reducerProbe.ref))
  val testOrganization = "scalac"
  val testRepositories = Set("test", "prod")
  val testContributions: Map[String, Int] = HashMap("Jedi, Master" -> 7, "T1000, Terminator" -> 70, "Kirk, Captain" -> 1)
  val testOrganization2 = "spaceX"
  val testRepositories2 = Set("dev", "beta")
  val testContributions2: Map[String, Int] = HashMap("Skywalker, Anakin" -> 101, "T1, Terminator" -> 7000, "Spock, Mr" -> 25)
  val testOrganization3 = "Virgin Galactic"

  it should "Send a Repository Request for each received Organization message" in {
    mapperActor ! Organization(testOrganization)
    mapperActor ! Organization(testOrganization2)
    routerProbe.expectMessage(RepositoriesRequest(testOrganization, mapperActor.ref))
    routerProbe.expectMessage(RepositoriesRequest(testOrganization2, mapperActor.ref))
  }

  it should "discard any ContributionResponse while in the Repositories state" in {
    mapperActor ! ContributionsResponse("garbage", "garbage", Map("garbage" -> 0))
    routerProbe.expectNoMessage()
    reducerProbe.expectNoMessage()
  }

  it should "receive the first Organization's RepositoriesResponse, send the Contributions requests and jump to onContributions state" in {
    mapperActor ! RepositoriesResponse(testOrganization, testRepositories)
    for(d <- testRepositories) {
      routerProbe.expectMessage(ContributionsRequest(testOrganization, d, mapperActor.ref))
    }
    reducerProbe.expectMessage(OnStart(testOrganization))
  }

  it should "receive the second RepositoriesResponse and stash it while in the Contributions state" in {
    mapperActor ! RepositoriesResponse(testOrganization2, testRepositories2)
    routerProbe.expectNoMessage()
  }

  it should "receive the third Organization msg and stash it while in the Contributions state" in {
    mapperActor ! Organization(testOrganization3)
    routerProbe.expectNoMessage()
  }

  it should "receive the GitHubClient Contributions from the first Organization and send each Contributor to the reducer" in {
    for(repo <- testRepositories) mapperActor ! ContributionsResponse(testOrganization, repo, testContributions)
    for((n, c) <- testContributions) reducerProbe.expectMessage(Contributor(n, c))
    for((n, c) <- testContributions) reducerProbe.expectMessage(Contributor(n, c))
    reducerProbe.expectMessage(OnTerminate())
  }

  it should "process the RepositoryResponse Message from the second organization that was waiting in the stash buffer and send the ContributionsRequest" in {
    for(d <- testRepositories2) {
      routerProbe.expectMessage(ContributionsRequest(testOrganization2, d, mapperActor.ref))
    }
    reducerProbe.expectMessage(OnStart(testOrganization2))
  }

  it should "receive the GitHubClient Contributions from the second Organization and send each Contributor to the reducer" in {
    for(repo <- testRepositories2) mapperActor ! ContributionsResponse(testOrganization2, repo, testContributions2)
    for((n, c) <- testContributions2) reducerProbe.expectMessage(Contributor(n, c))
    for((n, c) <- testContributions2) reducerProbe.expectMessage(Contributor(n, c))
    reducerProbe.expectMessage(OnTerminate())
  }

  it should "process the third Organization's message that was waiting in the stash buffer and send the RepositoriesRequest" in {
    routerProbe.expectMessage(RepositoriesRequest(testOrganization3, mapperActor.ref))
  }

}
