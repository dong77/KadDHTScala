package com.coinport

// Not Thread safe, can only be accessed by one actor/thread
import scala.collection.immutable.{ Seq => ISeq }

trait Provider[T <: AnyRef] {
  def get(): T
}
trait Holder[T <: AnyRef] extends Provider[T] {
  def set(t: T): T
}

abstract class SimpleHolder[T <: AnyRef] extends Holder[T] {
  private var t: T = null.asInstanceOf[T]
  def set(newValue: T): T = {
    val oldValue = t
    t = newValue
    oldValue
  }

  def get() = t
}

class RoutingTable(localNode: Node, timeProvider: TimeProvider) extends SimpleHolder[ISeq[KBucket]] {

  implicit val time = timeProvider

  val now = time.now
  val meta = BucketMeta(now, now)
  val (rangeMin, rangeMax) = Keys.keySpace
  val bucket = KBucket(0, rangeMin, rangeMax, Nil, Nil, meta)
  val bucketWithLocalNode = bucket.addNode(localNode)

  set(ISeq(bucketWithLocalNode))

  /*  def replaceBucket(bucket: KBucket) {
    val idx = getBucketIndex(bucket)
    set(get.take(idx) :+ bucket) ++ get.drop(idx + 1)
  }

  // TODO(dongw): binary search
  private def getBucketIndex(key: Key): Int = {
    get.zipWithIndex.filter {
      case (bucket, _) => bucket.coversKey(key)
    }(0)._2
  }

  private def getBucketIndex(bucket: KBucket): Int = {
    get.zipWithIndex.filter {
      case (b, _) => b.rangeMin == bucket.rangeMin && b.rangeMax == bucket.rangeMax
    }(0)._2
  }*/

  override def toString() = {
    val bucketsStr = get.mkString("\n\n")
    s"ROUTING_TABLE (id: ${localNode}) contains ${get.size} buckets:\n${bucketsStr}"
  }
}

class BucketsQuerier(buckets: ISeq[KBucket]) {
  def getClosestNodes(key: Key, limit: Int): ISeq[BoxedNode] = Nil
}

