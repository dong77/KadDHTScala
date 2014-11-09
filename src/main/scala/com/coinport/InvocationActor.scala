package com.coinport

import akka.actor._
import akka.event.Logging

import scala.collection.immutable.{ Seq => ISeq }
import scala.concurrent.duration._

case class PendingInvocation(client: ActorRef, dest: Contact, timestamp: Long)

// An actor to manage invocation req/resp mapping.
class InvocationActor(
  connection: ActorRef,
  bucketsProvider: Provider[ISeq[KBucket]])(implicit dhtCtx: DHTContext)
  extends Actor with Stash with ActorLogging {

  val time = dhtCtx.timeProvider
  val timeoutMillis = dhtCtx.config.rpcTimeoutSecond * 1000
  var invocations = Map.empty[Key, PendingInvocation]
  val localNode = dhtCtx.localNode
  var timeoutListeners = Set.empty[ActorRef]

  case class CheckTimeout(unused: Int = 0)
  import context.dispatcher

  // TODO : check timeout and signal client and RouitingTableUpdater

  connection ! RegisterNetworkListener()

  def receive = {
    case _: NetworkListenerRegistered =>
      context become ready(sender())
      context.system.scheduler.schedule(1 second, 1 second, self, CheckTimeout())
      log.info(s"InvocationActor ready: ${self.path}")
      unstashAll()

    case _ => stash()
  }

  def ready(connection: ActorRef): Receive = {
    case _: RegisterInvocationTimeoutListener =>
      if (!timeoutListeners.contains(sender())) {
        timeoutListeners += sender()
        context watch sender()

      }
      sender() ! InvocationTimeoutListenerRegistered()

    case CheckTimeout(_) =>
      // find PendingInvocation that have timed out
      val timeoutMap = invocations.filter {
        case (k, v) => time.now - v.timestamp > timeoutMillis
      }

      if (timeoutMap.nonEmpty) {
        invocations = invocations -- timeoutMap.keys

        // among timed out PendingInvocations, get those with a nodeId
        val contacts = timeoutMap.values.map(_.dest).filter(_.id.nonEmpty).to[ISeq]

        // RPC to these nodes have been timed out
        if (contacts.nonEmpty) {
          val clients = timeoutMap.values.map(_.client)
          (timeoutListeners ++ clients).foreach {
            _ ! InvocationTimeout(contacts)
          }
        }

        log.debug("timedout invocations: " + timeoutMap.keys.mkString(",") +
          "; timedout contacts: " + contacts.mkString(", "))
      }

    case sm @ SendMessage(dest, msg) =>
      log.debug(s"--->[req] ${sm}")
      val invocationId = Keys.randomKey
      invocations += invocationId -> PendingInvocation(sender(), dest, time.now)
      context watch sender()
      connection ! OutgoingEnvelope(dest, Envelope(invocationId, msg))

    case Terminated(watched) =>
      invocations = invocations.filter { case (_, pi) => pi.client != watched }
      timeoutListeners -= watched

    case received @ ReceivedEnvelope(src, envelope) =>

      val msg = envelope.msg

      if (msg.ping.isDefined || msg.findNode.isDefined || msg.findValue.isDefined || msg.store.isDefined) {
        log.debug(s"<---[req] ${received}")

        val props = Props(new ServiceActor(bucketsProvider.get))
        val service = context.actorOf(props)
        service forward received

      } else if (msg.ok.isDefined || msg.findNodeResp.isDefined || msg.findValueResp.isDefined) {
        log.debug(s"<---[resp] ${received}")
        val invocationId = envelope.invocationId

        invocations.get(invocationId) foreach { pi =>
          pi.client ! envelope.msg
          // Do NOT uncomment the following code, so invocations end only by timeout and will
          // be able to get multiple responses from the network.
          // invocations -= invocationId
        }
      } else {
        log.debug(s"<--[blkd] ${received} -why? unknown msg")
      }
    case x => log.warning("TOTALLY UNEXPECTED MSG: " + x)
  }

  private def isValid(envelope: Envelope): Boolean = {
    !envelope.node.isDefined ||
      envelope.node.get.contact == dhtCtx.localNode.contact
  }
}

