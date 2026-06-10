package com.codesolutions.akka.aws.domain

import java.time.Instant

/**
 * Order domain — same as akka-scala-base but in its own package so the
 * AWS project is self-contained.
 */
final case class Order(
    id: String,
    customerId: String,
    amount: Double,
    status: String,
    createdAt: Instant
)
