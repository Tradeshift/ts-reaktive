package com.tradeshift.reaktive.akka

import akka.actor.AbstractActor
import akka.actor.Stash
import akka.actor.UnrestrictedStash
import akka.actor.UnboundedStash
import akka.stream.actor.ActorPublisher

/**
 * Adds a Stash to AbstractActorPublisher
 * @see [[akka.stream.actor.ActorPublisher]] and [[akka.stream.actor.AbstractActorWithStash]]
 */
abstract class AbstractActorPublisherWithStash[T] extends AbstractActor with ActorPublisher[T] with Stash

/**
 * Adds an Unbounded Stash to AbstractActorPublisher
 * @see [[akka.stream.actor.ActorPublisher]] and [[akka.stream.actor.AbstractActorWithUnboundedStash]]
 */
abstract class AbstractActorPublisherWithUnboundedStash[T] extends AbstractActor with ActorPublisher[T] with UnboundedStash

/**
 * Adds an Unrestricted Stash to AbstractActorPublisher
 * @see [[akka.stream.actor.ActorPublisher]] and [[akka.stream.actor.AbstractActorWithUnrestrictedStash]]
 */
abstract class AbstractActorPublisherWithUnrestrictedStash[T] extends AbstractActor with ActorPublisher[T] with UnrestrictedStash
