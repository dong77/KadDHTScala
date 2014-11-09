package com.coinport

trait TimeProvider {
  def now(): Long
}

final class SystemTimeProvider extends TimeProvider {
  def now() = System.currentTimeMillis
}