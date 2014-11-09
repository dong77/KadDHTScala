package com.coinport

import akka.actor._
import akka.event.Logging

case class DHTConfig(K: Int, alpha: Int, rpcTimeoutSecond: Int)

object DHTConfig {
  val default = DHTConfig(
    K = 20,
    alpha = 3,
    rpcTimeoutSecond = 5)
}

/**
 * This actor basically do the following:
 * 1) Maintain the routing table
 * 2) Perform local requests for recursive lookups, republish, etc.
 * 3) Answers
 */
case class DHTContext(
  localNode: Node,
  config: DHTConfig,
  datastore: DataStore,
  serializer: EnvelopeSerializer = new ProtobufEnvelopeSerializer,
  timeProvider: TimeProvider = new SystemTimeProvider)

class DHT()(implicit dhtCtx: DHTContext) extends Actor with ActorLogging {

  val routingTable = new RoutingTable(dhtCtx.localNode, dhtCtx.timeProvider)
  //val network = context.actorOf(Props(new NetworkActor(routingTable)), "network")

  var seeds = Set.empty[Contact]
  val connection = context.actorOf(Props(new Connection()), "connection")
  val invocation = context.actorOf(Props(new InvocationActor(connection, routingTable)), "invocation")
  val rtupdater = context.actorOf(Props(new RoutingTableUpdater(connection, invocation, routingTable)), "rtupdater")

  log.info("connection  " + invocation.path)
  log.info("invocation  " + invocation.path)
  log.info("rtupdater  " + rtupdater.path)

  def receive = {
    case sm: SendMessage => invocation forward sm

    case AddSeeds(seeds) =>
      this.seeds ++= seeds
      this.seeds foreach { seed =>
        val msg = SendMessage(seed, Msg(ping = Some(PING())))
        println(msg)
        invocation ! msg
      }
      log.info(s"seeds: ${seeds}")
  }
}
