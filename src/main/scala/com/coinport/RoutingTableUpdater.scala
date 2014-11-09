package com.coinport

import akka.actor._
import akka.event.Logging

case object RoutingTableUpdated

class RoutingTableUpdater(
  connection: ActorRef,
  invocation: ActorRef,
  routingTable: RoutingTable) extends Actor with ActorLogging {

  connection ! RegisterNetworkListener()
  invocation ! RegisterInvocationTimeoutListener()

  def receive = {
    case InvocationTimeout(contacts) => contacts.foreach(handleInactive)
    case ReceivedEnvelope(contact, _) => handleActive(contact)
  }

  def handleActive(contact: Contact) = {
    log.info("(+): " + contact)
  }

  def handleInactive(contact: Contact) = {
    log.info("(-): " + contact)
  }
}