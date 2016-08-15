package com.tradeshift.reaktive.replication;


import static com.tradeshift.reaktive.ListenableFutures.toJava;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import com.datastax.driver.core.Session;
import com.tradeshift.reaktive.cassandra.CassandraSession;
import com.typesafe.config.Config;

import akka.actor.ActorSystem;
import akka.persistence.cassandra.CassandraPluginConfig;
import akka.stream.Materializer;
import scala.collection.JavaConversions;

public class VisibilityCassandraSession extends CassandraSession {
    private final String keyspace;

    public VisibilityCassandraSession(ActorSystem system, Materializer materializer, String metricsCategory) {
        super(system, materializer, metricsCategory, createKeyspaceAndTable(system.settings().config().getConfig("ts-reaktive.replication.cassandra")));
        this.keyspace = system.settings().config().getString("ts-reaktive.replication.cassandra.keyspace");
    }
    
    public String getKeyspace() {
        return keyspace;
    }
    
    private static Function<Session,CompletionStage<Void>> createKeyspaceAndTable(Config config) {
        String replStrategy = CassandraPluginConfig.getReplicationStrategy(
            config.getString("replication-strategy"),
            config.getInt("replication-factor"),
            JavaConversions.asScalaBuffer(config.getStringList("data-center-replication-factors")));
        String keyspace = config.getString("keyspace");
        
        return s ->
            toJava(s.executeAsync("CREATE KEYSPACE IF NOT EXISTS " + keyspace + " WITH REPLICATION = { 'class' : " + replStrategy + " } ")).thenCompose(rs -> 
            toJava(s.executeAsync("CREATE TABLE IF NOT EXISTS " + keyspace + ".meta (datacenter text, tag text, lastEventOffset bigint, PRIMARY KEY(datacenter, tag))"))).thenCompose(rs -> 
            toJava(s.executeAsync("CREATE TABLE IF NOT EXISTS " + keyspace + ".visibility (persistenceid text PRIMARY KEY, master boolean, datacenters set<text>)"))).thenApply(rs -> null);
        
    }
}
