package com.coinport

import akka.actor._
import akka.event.Logging
import akka.util._
import com.typesafe.config.ConfigFactory
import collection.immutable.Seq
import Keys._

abstract class M(i: Int) extends App {

  val config = ConfigFactory.load

  val localNodeId = Keys.fromBigInt(i)
  val localNode = Node(Contact("localhost", 10000 + i, Some(localNodeId)))

  println(s"LOCAL NODE:{id = ${localNodeId.hexstr} $localNode")
  val dhgConfig = DHTConfig.default

  val dummyDataStore = new DataStore {
    def save(content: Array[Byte]) {} // save, modify, remove, index
    def find(key: Key) = None
  }

  implicit val dhtCtx = DHTContext(localNode, dhgConfig, dummyDataStore)

  val system = ActorSystem("DHTSys", config)
  val dht = system.actorOf(Props(new DHT()), "dht")

  Thread.sleep(1000)
  dht ! AddSeeds(Seq(Contact("localhost", 10001)))
  // TODO dht ! FIND_NODE(localNode.id.get)
}

object Main1 extends M(1)
object Main2 extends M(2)