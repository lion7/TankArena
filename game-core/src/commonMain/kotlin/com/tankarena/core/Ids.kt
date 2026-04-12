package com.tankarena.core

@JvmInline
value class EntityId(val value: Long)

@JvmInline
value class Tick(val value: Long) {
    operator fun plus(other: Long): Tick = Tick(value + other)
}

