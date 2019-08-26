package org.dbpedia.spotlight.db.concurrent

import java.io.IOException
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.ask
import akka.routing.{ActorRefRoutee, Router, SmallestMailboxRoutingLogic}
import akka.util
import org.dbpedia.spotlight.model.{SurfaceFormOccurrence, Text}
import org.dbpedia.spotlight.spot.Spotter

import scala.concurrent.Await


/**
 * A Wrapper for Spotter workers.
 *
 * @author Joachim Daiber
 */

class SpotterWrapper(val spotters: Seq[Spotter]) extends Spotter {

  var requestTimeout = 60

  implicit val timeout = util.Timeout(requestTimeout, TimeUnit.SECONDS)

  val system = ActorSystem()
  var router = {
    val routees = spotters.map { spotter =>
      ActorRefRoutee(system.actorOf(Props(new SpotterActor(spotter))))
    }.toIndexedSeq
    val r = Router(SmallestMailboxRoutingLogic(), routees)
    system.actorOf(Props(new SmallestMailboxRouterActor(r)))
  }

  def size: Int = spotters.size

  def extract(text: Text): java.util.List[SurfaceFormOccurrence] = {
    val futureResult = router ? SpotterRequest(text)
    Await.result(futureResult, timeout.duration).asInstanceOf[java.util.List[SurfaceFormOccurrence]]
  }

  def close() {
    system.shutdown()
  }

  def getName: String = "SpotterWrapper[%s]".format(spotters.head.getClass.getSimpleName)

  def setName(name: String) {}
}

class SpotterActor(val spotter: Spotter) extends Actor {

  def receive = {
    case SpotterRequest(text) => {
      try {
        sender ! spotter.extract(text)

      } catch {
        case e: NullPointerException => throw new IOException("Could not tokenize.")
      }
    }
  }

}

case class SpotterRequest(text: Text)
