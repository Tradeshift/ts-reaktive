package com.tradeshift.reaktive.replication;

import com.tradeshift.reaktive.actors.AbstractState;
import com.tradeshift.reaktive.replication.TestData.TestEvent;

public class TestActorState extends AbstractState<TestData.TestEvent, TestActorState> {
    public static final TestActorState EMPTY = new TestActorState(null);

    private final String msg;

    private TestActorState(String msg) {
        this.msg = msg;
    }
    
    public String getMsg() {
        return msg;
    }

    @Override
    public TestActorState apply(TestEvent event) {
        return new TestActorState(event.getMsg());
    }

}
