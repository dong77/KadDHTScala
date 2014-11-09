package com.coinport

import akka.actor.ActorSystem
import akka.actor.Actor
import akka.actor.Props
import org.specs2.mutable._
import com.google.protobuf.{ ByteString => BS }

class ProtobufEnvelopeSerializerSpec extends Specification {
  "ProtobufEnvelopeSerializer" should {
    "convert envelopes back and forth" in {
      val serializer = new ProtobufEnvelopeSerializer()
      val invocationId = Keys.randomKey
      val msg = Msg(findNode = Some(FIND_NODE(Keys.randomKey)))
      val node = Node(Contact("localhost", 12345, Some(Keys.randomKey)), Some(BS.copyFrom(new Array[Byte](100))))

      val e1 = Envelope(invocationId, msg, Some(node))
      val bs1 = serializer.serialize(e1)

      val e2 = serializer.deserialize(bs1.get)
      e2.get === e1
      val bs2 = serializer.serialize(e2.get)
      bs2 === bs1
    }
  }
}