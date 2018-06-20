package com.tradeshift.reaktive.xsd

import scala.concurrent.Future
import akka.stream.scaladsl.Source
import javax.xml.stream.events.XMLEvent
import akka.stream.scaladsl.RunnableGraph
import akka.stream.scaladsl.GraphDSL
import akka.stream.ClosedShape
import akka.stream.Materializer
import com.tradeshift.reaktive.xsd.SchemaItem.Import
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Merge
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Broadcast
import java.util.concurrent.CompletionStage
import scala.compat.java8.FutureConverters._
import akka.stream.scaladsl.Sink
import scala.concurrent.Promise
import akka.stream.scaladsl.MergePreferred
import akka.NotUsed
import akka.stream.OverflowStrategy
import akka.stream.SinkShape
import akka.Done

/**
  * You should validate the schema itself by passing it through Xerces before accepting it here. Only minimal checks are performed.
  */
object SchemaLoader {
  /**
    * Loads a schema and related files, describing a set of XSD namespaces, into an immutable Schema object.
    * @param start Main first namespaces to load (import and include tags are followed from there,
    *                      recursively.)
    * @param load Function that provides the XML content for the XSD of each namespace.
    * @param normalize Gets applied for each import and include, to allow e.g. paths to be normalized, in
    *        order to only load each XSD file exactly once.
    */
  def apply(base: Schema, mainNamespaces: Set[Import],
    normalize: Import => Import, load: Import => Source[XMLEvent,_])(implicit m: Materializer): Future[Schema] = {

    RunnableGraph.fromGraph(GraphDSL.create(new SchemaBuilder(base)) { implicit b => schemaBuilder =>
      import GraphDSL.Implicits._

      val packageNextImports: Flow[AnyRef,Set[Import],NotUsed] = Flow[AnyRef].statefulMapConcat { () =>
        var nextImports = Set.empty[Import]
        elem => elem match {
          case SchemaEntry(_, i@Import(ns,_)) =>
            if (!base.namespaces.exists(_.ns == ns)) {
              nextImports += normalize(i)
            }
            Nil
          case Done =>
            val result = nextImports
            nextImports = Set.empty
            result :: Nil
          case _ =>
            Nil
        }
      }

      val main = Flow[Set[Import]].statefulMapConcat { () =>
        var loaded = Set.empty[Import]
        requested => {
          val needed = requested -- loaded
          loaded ++= requested
          Vector(needed)
        }
      }
      .takeWhile(_.size > 0)
      .flatMapConcat(imports => 
        mergedSource(imports.map(i => load(i).via(SchemaEntry.unmarshaller(i.namespace))))
          .concat(Source.single(Done))
      )

      val stripMarkers = Flow[AnyRef].collect { case s:SchemaEntry => s }

      val kick = Source.single(mainNamespaces.map(normalize))

      val start = b.add(MergePreferred[Set[Import]](1))

      val end = b.add(Broadcast[AnyRef](2))

      kick ~> start
      start ~> main ~> end
      end ~> packageNextImports ~> start.preferred
      end ~> stripMarkers ~> schemaBuilder

      ClosedShape
    }).run
  }

  private def mergedSource[T](sources: Iterable[Source[T,_]]): Source[T,NotUsed] = {
    sources.fold(Source.empty)(_ merge _).mapMaterializedValue(_ => NotUsed)
  }
}
