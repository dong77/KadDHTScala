package com.coinport

import Keys._

import scala.collection.immutable.{ Seq => ISeq }

case class BoxedNode(node: Node, lastActive: Long)

case class BucketMeta(created: Long = 0, lastModified: Long = 0)

// each bucket is an immutable case class,any modification will create a new clone.
case class KBucket(
  depth: Int,
  rangeMin: BigInt,
  rangeMax: BigInt,
  bnodes: ISeq[BoxedNode],
  rcache: ISeq[BoxedNode] = Nil,
  meta: BucketMeta = BucketMeta())(implicit time: TimeProvider) {
  assert(rangeMin < rangeMax)

  def size() = bnodes.size

  def coversId(key: Key, min: BigInt = rangeMin, max: BigInt = rangeMax): Boolean = {
    key.intValue >= rangeMin && key.intValue < rangeMax
  }

  def split(localNodeId: Key, maxDepth: Int): Option[ISeq[KBucket]] = {
    if (!coversId(localNodeId) && depth < maxDepth || rangeMax - rangeMin == 1) {
      None
    } else {
      val splittingPoint = rangeMin + (rangeMax - rangeMin) / 2

      val now = time.now()

      val meta1 = meta.copy(created = now, lastModified = now)
      val bnodes1 = bnodes.filter { bnode => coversId(bnode.node.contact.id.get, rangeMin, splittingPoint) }
      val rcache1 = rcache.filter { bnode => coversId(bnode.node.contact.id.get, rangeMin, splittingPoint) }
      val bucket1 = KBucket(depth + 1, rangeMin, splittingPoint, bnodes1, rcache1, meta1)

      val meta2 = meta.copy(created = now, lastModified = now)
      val bnodes2 = bnodes.filter { bnode => coversId(bnode.node.contact.id.get, splittingPoint, rangeMax) }
      val rcache2 = rcache.filter { bnode => coversId(bnode.node.contact.id.get, splittingPoint, rangeMax) }
      val bucket2 = KBucket(depth + 1, splittingPoint, rangeMax, bnodes2, rcache2, meta2)

      Some(ISeq(bucket1, bucket2))
    }
  }

  def removeNode(node: Node): Option[KBucket] = {
    node.contact.id match {
      case Some(id) =>
        assert(coversId(id), "attempted to remove a node outside this kbucket's range")

        val updatedNodes = bnodes.filterNot { _.node.contact.id.get == id }
        if (updatedNodes.size == bnodes.size) None
        else {
          val updatedMeta = meta.copy(lastModified = time.now)
          val updated = copy(bnodes = updatedNodes, meta = updatedMeta)
          Some(updated)
        }

      case None => None
    }
  }

  def touchNode(node: Node): Option[KBucket] = {
    node.contact.id match {
      case Some(id) =>
        assert(coversId(id), "attempted to touch a node outside this kbucket's range")

        val updatedNodes = bnodes.filterNot { _.node.contact.id.get == id }
        if (updatedNodes.size == bnodes.size) None
        else {
          val updatedMeta = meta.copy(lastModified = time.now)
          val updated = copy(bnodes = updatedNodes, meta = updatedMeta)
          Some(updated)
        }

      case None => None
    }
  }

  def getNode(id: Key): Option[BoxedNode] = {
    assert(coversId(id), "attempted to touch a node outside this kbucket's range")
    bnodes.filter(_.node.contact.id.get == id).headOption
  }

  def addNode(node: Node): KBucket = {
    assert(node.contact.id.nonEmpty)
    val id = node.contact.id.get
    assert(coversId(id))
    val updated = bnodes.filter(_.node.contact.id.get != id) :+ BoxedNode(node, time.now)
    copy(bnodes = updated)
  }

  // TODO(dongw): binary search
  def nodeIndex(id: Key): Int = {
    bnodes.zipWithIndex.filter { case (bnode, _) => bnode.node.contact.id.get == id }(0)._2
  }

  override def toString() = {
    val nodeStr = bnodes.mkString("\t\t", "\n\t\t", "\n")
    s"BUCKET ${depth} [${rangeMin}, ${rangeMax}) - ${bnodes.size} bnodes:\n{nodesStr}"
  }
}