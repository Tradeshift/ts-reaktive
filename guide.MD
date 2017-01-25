User guide
==========

The [`ts-reaktive`](https://github.com/Tradeshift/ts-reaktive/) libraries are an attempt to summarize 
"good practices" of writing event-sourced applications using 
Java 8 and akka. This can of course be done successfully without bring in additional libraries, but we try to solve
common pitfalls by providing a few extra guidelines and features, streamlining the Akka experience somewhat.

[*Event sourcing*](http://microservices.io/patterns/data/event-sourcing.html) is an architectural pattern in which 
*events* are the main source of data in an application. All other data must be derived from those events. Events only
have an ordering guarantee within a single *aggregate*. This implies that two events emitted by two different 
aggregates are not guaranteed to be observed in the same order by 2 connected clients. However, one is of course free
to add a timestamp to events and instruct clients to use that for ordering.   

This use guide assumes basic familiarity with [akka's documentation](http://doc.akka.io/docs/akka/current/java.html), 
especially the akka persistence sections. All of `ts-reaktive` assumes deployment into a clustered akka setup.

When to use event sourcing
--------------------------
Event sourcing is a great choice if you expect to have use cases in the following categories:

- Historic data affects current actions
- You need business analytics on user trends, that change regularly
- Strong audit requirements on loggin user's actions 
- Huge horizontal scale across application and datastore, while also needing application-level 
  in-memory state that is not easily addressed with a distributed cache 

Unfortunately, many of these requirements often don't surface until _after_ an application was released and has become
popular, so it pays to think ahead.

When not to use event sourcing
------------------------------

Although akka and `ts-reaktive` do what they can to make event sourcing accessible, there is a certain overhead, since
any kind of reasoning across aggregate boundaries requires you to write custom processing. There also is a learning
curve if one is only accustomed to writing relational database, single-server systems.

The following requirements may be signs that event sourcing isn't a good fit:
- You have a lot of existing data in a schema you can't migrate
- You actually need for data to be updated in-place for security reasons
- You have trouble defining aggregate boundaries (see the next section)

Deciding on aggregate boundaries
--------------------------------
Deciding on what is an aggregate in an event-sourced system is not always trivial. Should it be a complete user account,
one of the user's documents, a single paragraph in a document? Any of those could be correct. Roughly said:

- Within an aggregate, you can reason about atomicity, event order, and whether incoming commands should be allowed or not
- Across aggregates, you get concurrency and load balancing

So you should pick your aggregate sizes big enough to allow important business logic to be implemented (for which 
"eventual consistency" isn't good enough). At the same time, the aggregates need to be small enough so that your
millions of concurrently connected users still represent millions of aggregates, so they can be distributed across
a cluster.

Writing an event-sourced actor
------------------------------
Within `ts-reaktive`, an event-sourced actor defines concrete type for the _commands_ and _events_ it deals with. This
is contrary to plain akka, where both of these are just `Object`. In addition, an event-sourced actor externalizes
its complete in-memory state into an external type. That "state" class can then be unit tested without any actor 
dependencies.  

There are two main classes you can derive your actor class from:
- AbstractStatefulPersistentActor (in case you won't need multi-datacenter replication)
- ReplicatedActor (in case you need to selectively copy part of your events to remote read-only replicas)

At the moment, you **can't** "upgrade" an existing AbstractStatefulPersistentActor to become replicated later on.

As an example, let's look at the [ConversationActor](https://github.com/jypma/ts-reaktive-examples/blob/master/example-2-persistence/src/main/java/demo/reaktive/conversation/ConversationActor.java)
from `ts-reaktive-examples`, which implements an imaginary real-time chat conversation service.

```java
public class ConversationActor extends AbstractStatefulPersistentActor<ConversationCommand, ConversationEvent, ConversationState> {
    public ConversationActor() {
        super(ConversationCommand.class, ConversationEvent.class);
    }

    static abstract class Handler extends AbstractCommandHandler<ConversationCommand, ConversationEvent, ConversationState> {
        public Handler(ConversationState state, ConversationCommand cmd) {
            super(state, cmd);
        }
    }
    
    @Override
    protected PartialFunction<ConversationCommand, Handler> applyCommand() {
        return new PFBuilder<ConversationCommand,Handler>()
            .match(GetMessageList.class, msg -> new GetMessageListHandler(getState(), msg))
            .match(PostMessage.class, msg -> new PostMessageHandler(getState(), msg))
            .build();
    }

    @Override
    protected ConversationState initialState() {
        return ConversationState.EMPTY;
    }
}
```

There isn't any direct business logic in the main actor class. Rather, it delegates to the following extra types:
- [`ConversationCommand`](https://github.com/jypma/ts-reaktive-examples/blob/master/example-2-persistence/src/main/java/demo/reaktive/conversation/ConversationCommand.java)
  which is the base class for all command objects that can be sent to the actor
- [`ConversationEvent`](https://github.com/jypma/ts-reaktive-examples/blob/master/example-2-persistence/src/main/java/demo/reaktive/conversation/ConversationEvent.java)
  which is the base class for all events that are emitted by the actor into the journal
- [`ConversationState`](https://github.com/jypma/ts-reaktive-examples/blob/master/example-2-persistence/src/main/java/demo/reaktive/conversation/ConversationState.java)
  which is the data class holding the in-memory state of a conversation. For the example, that includes all messages, but a production system of course wouldn't do that.
- [`GetMessageListHandler`](https://github.com/jypma/ts-reaktive-examples/blob/master/example-2-persistence/src/main/java/demo/reaktive/conversation/GetMessageListHandler.java)
  which implements the actor business logic for responding to "get message list" commands  
- [`PostMessageHandler`](https://github.com/jypma/ts-reaktive-examples/blob/master/example-2-persistence/src/main/java/demo/reaktive/conversation/PostMessageHandler.java)
  which implements the actor business logic for responding to "post message" commands  

Follow the links to see how each of the auxilliary types is implemented, and note how none of them have any 
akka or actor dependencies; only the main actor has. All types also are immutable.
 
These two last points also are the main design goal behind `ts-reaktive`.
