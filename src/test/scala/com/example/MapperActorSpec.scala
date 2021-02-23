package com.example

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import com.example.GitHubApiGuardian.Command
import com.example.GitHubClientActor._
import com.example.MapperActor._
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
  val testData: RepositoriesResponse = RepositoriesResponse(testOrganization, testRepositories)
  val testOrganization2 = "scalac"
  val testRepositories2 = Set("test", "prod")
  val testContributions2: Map[String, Int] = HashMap("Skywalker, Anakin" -> 101, "T1, Terminator" -> 7000, "Spock, Mr" -> 25)
  val testData2: RepositoriesResponse = RepositoriesResponse(testOrganization2, testRepositories2)

  it should "receive the Organization's Repositories and send the Contributions requests" in {
    mapperActor ! testData
    routerProbe.expectMessage(StartContributionsRequest())
    for(d <- testData.repositories) {
      routerProbe.expectMessage(ContributionsRequest(testOrganization, d, mapperActor.ref))
    }
    routerProbe.expectMessage(TerminateContributionsRequest())
  }

  it should "receive the second Organization's Repositories and ignore while in the Contributions state" in {
    mapperActor ! testData2
    routerProbe.expectNoMessage()
  }

  it should "receive the GitHubClient Contributions from the first Organization and send each Contributor to the reducer" in {
    for(repo <- testRepositories) mapperActor ! ContributionsResponse(testOrganization, repo, testContributions)
    for((n, c) <- testContributions) reducerProbe.expectMessage(Contributor(n, c))
    for((n, c) <- testContributions) reducerProbe.expectMessage(Contributor(n, c))
  }

  it should "process the Repositories from the second organization that were waiting in the stash buffer" in {
    routerProbe.expectMessage(StartContributionsRequest())
    for(d <- testData2.repositories) {
      routerProbe.expectMessage(ContributionsRequest(testOrganization2, d, mapperActor.ref))
    }
    routerProbe.expectMessage(TerminateContributionsRequest())
  }

}
