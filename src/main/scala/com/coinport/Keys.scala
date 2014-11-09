package com.coinport

import com.google.protobuf.ByteString
/**
 * The key of dht. Keys are 160-bit arrays.
 */

object Keys {
  private val random = new scala.util.Random()

  def distance(key1: Key, key2: Key) = getKeyIntValue(key1) ^ getKeyIntValue(key2)

  def randomKey(): Key = {
    val array = new Array[Byte](20)
    random.nextBytes(array)
    Key(ByteString.copyFrom(array))
  }

  lazy val minKey = fromString("0" * 40)
  lazy val maxKey = fromString("F" * 40)

  lazy val keySpace = (getKeyIntValue(minKey), getKeyIntValue(maxKey) + 1 /* exclusive */ )
  lazy val keySpaceString = s"[${new RichKey(minKey).hexstr}, ${new RichKey(maxKey).hexstr}]"

  def fromString(str: String) = Key(ByteString.copyFrom(HexBytesUtil.hex2bytes(str)))
  def fromBigInt(int: BigInt) = Key(ByteString.copyFrom(int.toByteArray))

  private def bytes2Unsigned(bytes: Array[Byte]): BigInt = {
    def _bytes2Unsigned(index: Int = 0, decVal: BigInt = BigInt(0)): BigInt =
      if (index == bytes.length) decVal else _bytes2Unsigned(index + 1, (decVal << 8) + (bytes(index) & 0xff))
    _bytes2Unsigned()
  }

  def getKeyIntValue(key: Key) = bytes2Unsigned(key.raw.toByteArray)

  implicit def key2RichKey(key: Key) = new RichKey(key)
}

class RichKey(key: Key) {
  def isValid = { key.raw.size == 20 }
  def intValue = Keys.getKeyIntValue(key)
  def hexstr = HexBytesUtil.bytes2hex(key.raw.toByteArray)
  def distanceTo(another: Key): BigInt = Keys.distance(this.key, another)
}