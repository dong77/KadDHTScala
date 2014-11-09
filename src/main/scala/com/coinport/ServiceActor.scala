package com.coinport

import akka.actor._
import akka.event.Logging
import com.google.protobuf.ByteString

import scala.collection.immutable.{ Seq => ISeq }

class ServiceActor(
  buckets: ISeq[KBucket])(implicit dhtCtx: DHTContext)
  extends Actor with ActorLogging {

  val datastore = dhtCtx.datastore

  def receive = {
    case ReceivedEnvelope(src, Envelope(invocationId, msg, node)) =>

      if (msg.ping.isDefined) replyOk()
      else if (msg.findNode.isDefined) handleFindNode()
      else if (msg.findValue.isDefined) handleFindValue()
      else if (msg.store.isDefined) handleStore()
      else replyOk()

      context.stop(self)

      // internal messages

      def handleFindNode() {
        val id = msg.findNode.get.id
        val closestNodes = new BucketsQuerier(buckets).getClosestNodes(id, dhtCtx.config.K).map(_.node)
        reply(Msg(findNodeResp = Some(FIND_NODE_RESP(closestNodes))))
      }

      def handleFindValue() {
        val key = msg.findValue.get.key
        datastore.find(key) match {
          case Some(value) =>
            val findValueResp = FIND_VALUE_RESP(key = Some(key), value = Some(ByteString.copyFrom(value)))
            reply(Msg(findValueResp = Some(findValueResp)))

          case None =>
            val closestNodes = new BucketsQuerier(buckets).getClosestNodes(key, dhtCtx.config.K).map(_.node)
            val findValueResp = FIND_VALUE_RESP(nodes = closestNodes)
            reply(Msg(findValueResp = Some(findValueResp)))
        }
      }

      def handleStore() {
        try {
          val content = msg.store.get.content
          datastore.save(content.toByteArray)
        } catch {
          case e: Throwable => log.warning("fail to execute STORE command: " + e)
        } finally {
          replyOk() //reply ok regardless if the persister succeeds or not.
        }
      }

      def reply(reply: Msg) {
        val out = OutgoingEnvelope(src, Envelope(invocationId, reply, Some(dhtCtx.localNode)))
        log.debug(s"--->[resp] ${out}")
        sender() ! out
      }

      def replyOk() = reply(Msg(ok = Some(OK())))
  }
}
