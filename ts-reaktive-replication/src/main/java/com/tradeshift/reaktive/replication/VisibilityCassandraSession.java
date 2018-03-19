package com.tradeshift.reaktive.replication;


import com.tradeshift.reaktive.cassandra.CassandraSession;
import com.typesafe.config.Config;

import akka.actor.ActorSystem;
import akka.persistence.cassandra.CassandraPluginConfig;
import io.vavr.collection.Vector;
import scala.collection.JavaConverters;

public class VisibilityCassandraSession extends CassandraSession {
    private final String keyspace;

    public VisibilityCassandraSession(ActorSystem system, String metricsCategory) {
        super(system, metricsCategory, initialStatements(system.settings().config().getConfig("ts-reaktive.replication.cassandra")));
        this.keyspace = system.settings().config().getString("ts-reaktive.replication.cassandra.keyspace");
    }

    public String getKeyspace() {
        return keyspace;
    }

    private static Vector<String> initialStatements(Config config) {
        String replStrategy = CassandraPluginConfig.getReplicationStrategy(
            config.getString("replication-strategy"),
            config.getInt("replication-factor"),
            JavaConverters.asScalaBuffer(config.getStringList("data-center-replication-factors")));
        String keyspace = config.getString("keyspace");

        return Vector.of(
            "CREATE KEYSPACE IF NOT EXISTS " + keyspace + " WITH REPLICATION = { 'class' : " + replStrategy + " } ",
            "CREATE TABLE IF NOT EXISTS " + keyspace + ".meta (datacenter text, tag text, lastEventOffset bigint, PRIMARY KEY(datacenter, tag))",
            "CREATE TABLE IF NOT EXISTS " + keyspace + ".visibility (persistenceid text PRIMARY KEY, master boolean, datacenters set<text>)"
        );
    }
}
