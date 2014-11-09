package com.coinport

import akka.util.ByteString
import com.google.protobuf.{ ByteString => BS, CodedOutputStream => COS }

trait EnvelopeSerializer {
  def serialize(e: Envelope): Option[ByteString]
  def deserialize(bytes: ByteString): Option[Envelope]
}

final class ProtobufEnvelopeSerializer extends EnvelopeSerializer {
  def serialize(e: Envelope): Option[ByteString] = try {
    val bytes = new Array[Byte](e.getSerializedSize)
    val cos = COS.newInstance(bytes)
    e.writeTo(cos)
    Some(ByteString(bytes))
  } catch {
    case _: Throwable => None
  }

  def deserialize(bytes: ByteString): Option[Envelope] = try {
    Some(Envelope.parseFrom(BS.copyFrom(bytes.toArray)))
  } catch {
    case _: Throwable => None
  }
}