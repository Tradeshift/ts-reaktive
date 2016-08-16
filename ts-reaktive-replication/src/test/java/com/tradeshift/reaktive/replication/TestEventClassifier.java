package com.tradeshift.reaktive.replication;

import com.tradeshift.reaktive.replication.TestData.TestEvent;

import javaslang.collection.Seq;
import javaslang.collection.Vector;

public class TestEventClassifier implements EventClassifier<TestEvent>{
    @Override
    public Seq<String> getDataCenterNames(TestEvent e) {
        if (e.getMsg().startsWith("dc:")) {
            return Vector.of(e.getMsg().substring(3));
        } else {
            return Vector.empty();
        }
    }

}
