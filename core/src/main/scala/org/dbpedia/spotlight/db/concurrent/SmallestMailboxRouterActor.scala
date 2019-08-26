package org.dbpedia.spotlight.db.concurrent

import java.io.IOException

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, OneForOneStrategy, SupervisorStrategy}
import akka.routing.Router

class SmallestMailboxRouterActor(router: Router) extends Actor {

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy(maxNrOfRetries = 10) {
    case _: IOException => Restart
  }

  override def receive: Receive = {
    case work => router.route(work, sender())
  }
}
