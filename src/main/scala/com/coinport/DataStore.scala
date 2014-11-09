package com.coinport

trait DataStore {
  def find(key: Key): Option[Array[Byte]]
  def save(content: Array[Byte]): Unit
}
