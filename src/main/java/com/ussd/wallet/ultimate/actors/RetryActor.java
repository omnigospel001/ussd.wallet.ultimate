package com.ussd.wallet.ultimate.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryActor extends AbstractBehavior<String> {

    private static final Logger log = LoggerFactory.getLogger(RetryActor.class);

    public static Behavior<String> create() {
        return Behaviors.setup(RetryActor::new);
    }

    private RetryActor(ActorContext<String> context) {
        super(context);
    }

    @Override
    public Receive<String> createReceive() {
        return newReceiveBuilder()
                .onMessage(String.class, this::onMsg)
                .build();
    }

    private Behavior<String> onMsg(String msg) {
        log.info("[AKKA] RetryActor processing: {}", msg);
        return this;
    }
}

