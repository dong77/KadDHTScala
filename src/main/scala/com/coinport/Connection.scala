package com.coinport

import akka.actor._
import akka.event.Logging
import akka.io._

import scala.collection.immutable.Seq

import java.net.InetSocketAddress

import Keys._

class Connection()(implicit dhtCtx: DHTContext)
  extends Actor with Stash with ActorLogging {

  import dhtCtx._
  import context.system

  IO(Udp) ! Udp.Bind(self, getAddress(localNode))

  var listeners = Set.empty[ActorRef]

  def receive = {
    case Udp.Bound(local) =>
      context.become(ready(sender()))
      unstashAll()

    case Udp.Unbound => context.stop(self)

    case _ => stash()
  }

  def ready(socket: ActorRef): Receive = {

    case Udp.Unbind =>
      socket ! Udp.Unbind
      context unbecome ()

    case _: RegisterNetworkListener =>
      listeners += sender()
      sender() ! NetworkListenerRegistered()
      context watch sender()

    case out: OutgoingEnvelope =>
      if (samePhysicalNode(out.dest)) {
        log.debug(s"===>[blkd] ${out} -why? ignored envelope sent to myself")
      } else {
        val altered = out.envelope.copy(node = Some(localNode))

        serializer.serialize(altered) match {
          case Some(bytes) =>
            val address = getAddress(out.dest)
            socket ! Udp.Send(bytes, address)
            log.debug(s"===> ${out}")

          case None =>
            log.error(s"===>[blkd] -why? unable to serialize envelope: ${out}")
        }
      }

    case Udp.Received(bytes, source) =>
      serializer.deserialize(bytes) match {
        case Some(envelope: Envelope) =>
          if (samePhysicalNode(source)) {
            log.debug(s"<===[blkd] ${envelope} -why? envelope sent to myself ")
          } else {
            envelope.node match {
              case None =>
                log.debug(s"<===[blkd] ${envelope} -why? no sender node data, ignored")

              case Some(node) =>
                val contact = node.contact

                if (source.getHostName != node.contact.host ||
                  source.getPort != node.contact.port) {
                  log.debug(s"<===[blkd] ${envelope} -why? address mismatches real address ${source}, ignored")
                } else {
                  log.debug(s"<=== ${envelope}")
                  val in = ReceivedEnvelope(node.contact, envelope)
                  listeners.foreach { _ ! in }
                }
            }
          }

        case x =>
          log.warning(s"unable to deserialize received data: ${x}")
      }
  }

  private def getAddress(node: Node): InetSocketAddress = getAddress(node.contact)
  private def getAddress(contact: Contact): InetSocketAddress = new InetSocketAddress(contact.host, contact.port)

  private def samePhysicalNode(contact: Contact): Boolean = {
    assert(contact != null)
    contact.host == localNode.contact.host && contact.port == localNode.contact.port
  }

  private def samePhysicalNode(address: InetSocketAddress): Boolean = {
    assert(address != null)
    address.getHostName == localNode.contact.host && address.getPort == localNode.contact.port
  }
}

