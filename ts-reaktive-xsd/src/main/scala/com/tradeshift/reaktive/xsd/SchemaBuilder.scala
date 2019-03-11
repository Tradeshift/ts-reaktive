package com.tradeshift.reaktive.xsd

import scala.concurrent.Future
import akka.stream.scaladsl.Source
import javax.xml.stream.events.XMLEvent
import com.tradeshift.reaktive.marshal.scaladsl.Protocols._
import com.tradeshift.reaktive.marshal.scaladsl.Protocols.Implicits.widen
import com.tradeshift.reaktive.marshal.scaladsl.XMLProtocols._
import javax.xml.stream.events.Namespace
import SchemaBuilder._
import com.tradeshift.reaktive.marshal.ReadProtocol
import com.tradeshift.reaktive.marshal.stream.ProtocolReader
import akka.stream.stage.GraphStage
import akka.stream.SinkShape
import akka.stream.stage.GraphStageWithMaterializedValue
import akka.stream.Inlet
import akka.stream.stage.GraphStageLogic
import akka.stream.stage.InHandler
import akka.stream.Attributes
import scala.concurrent.Promise
import javax.xml.namespace.QName
import scala.collection.mutable
import akka.stream.scaladsl.Flow
import akka.NotUsed
import com.tradeshift.reaktive.marshal.scaladsl.StringMarshallable
import scala.reflect.ClassTag
import scala.util.{ Success, Try }

sealed trait SchemaItem

object SchemaItem {
  val occurs = StringMarshallable[Int]("occur bound", _ match {
    case "unbounded" => Success(-1)
    case s => Try(Integer.parseInt(s))
  }, _ match {
    case -1 => "unbounded"
    case i => i.toString
  })

  case class Import private (namespace: String, schemaLocation: Option[String]) extends SchemaItem
  object Import {
    def create(namespace: String, location: Option[String]) = Import(namespace, location.map { _ match {
      case s if s.contains("/") => s.substring(s.lastIndexOf("/") + 1)
      case s => s
    }})
  }
  case class AttributeDecl(name: String, attrType: String, use: Option[String])

  sealed trait ElementContent
  case class Element(name: String, elementType: String) extends SchemaItem with ElementContent
  case class RestrictionDecl(name: String, base: String, attributes: Seq[AttributeDecl],
    content: Option[ElementDecl]) extends SchemaItem
  case class ExtensionDecl(name: String, base: String, attributes: Seq[AttributeDecl],
    content: Option[ElementDecl]) extends SchemaItem 
  case class ContentComplexType(name: String, content: Option[ElementDecl],
    attributes: Seq[AttributeDecl]) extends SchemaItem
  case class ElementDeclRef(name: String) extends ElementContent
  case class ElementDecl(element: ElementContent, minOccurs: Int, maxOccurs: Int)
  case class SequenceDecl(contents: Seq[ElementDecl]) extends ElementContent
  case class ChoiceDecl(contents: Seq[ElementDecl]) extends ElementContent

  val attributeProto =
    tag(qname(xsd, "attribute"),
      attribute(qname("name")),
      attribute(qname("type")),
      option(attribute(qname("use"))),
      AttributeDecl.apply _
    )

  val elementRefProto =
    tag(qname(xsd, "element"),
      attribute(qname("ref")),
      withDefault(1, attribute(qname("minOccurs")).as(occurs)),
      withDefault(1, attribute(qname("maxOccurs")).as(occurs)),
      (ref:String, min:Int, max:Int) => ElementDecl(ElementDeclRef(ref), min, max)
    )

  val elementNamedProto =
    tag(qname(xsd, "element"),
      attribute(qname("name")),
      attribute(qname("type")),
      withDefault(1, attribute(qname("minOccurs")).as(occurs)),
      withDefault(1, attribute(qname("maxOccurs")).as(occurs)),
      (name:String, typ: String, min:Int, max:Int) => ElementDecl(Element(name, typ), min, max)
    )

  val elementAnyProto =
    tag(qname(xsd, "any"),
      withDefault(1, attribute(qname("minOccurs")).as(occurs)),
      withDefault(1, attribute(qname("maxOccurs")).as(occurs)),
      { (min:Int, max:Int) =>
        ElementDecl(ElementDeclRef("xsd:any"), min, max) }
    )

  def mkElementDeclProto(level: Int = 3): ReadProtocol[XMLEvent,ElementDecl] = level match {
    case i if i > 0 =>
      anyOf(
        elementRefProto,
        elementNamedProto,
        elementAnyProto,
        tag(qname(xsd, "sequence"),
          withDefault(1, attribute(qname("minOccurs")).as(occurs)),
          withDefault(1, attribute(qname("maxOccurs")).as(occurs)),
          vector(
            mkElementDeclProto(level - 1)
          ),
          (min:Int, max:Int, elems:Seq[ElementDecl]) => ElementDecl(SequenceDecl(elems), min, max)
        ),
        tag(qname(xsd, "option"),
          withDefault(1, attribute(qname("minOccurs")).as(occurs)),
          withDefault(1, attribute(qname("maxOccurs")).as(occurs)),
          vector(
            mkElementDeclProto(level - 1)
          ),
          (min:Int, max:Int, elems:Seq[ElementDecl]) => ElementDecl(ChoiceDecl(elems), min, max)
        )
      )

    case _ =>
      anyOf(
        elementRefProto,
        elementNamedProto,
        elementAnyProto
      )
  }

  val elementDeclProto = mkElementDeclProto(3)

  val proto: ReadProtocol[XMLEvent, SchemaItem] = anyTag( // skip over the XSD root tag
    anyOf(
      tag(qname(xsd, "import"),
        attribute(qname("namespace")),
        option(attribute(qname("schemaLocation"))),
        Import.create _),
      tag(qname(xsd, "include"),
        attribute(qname("schemaLocation")),
        { s:String => Import.create("", Some(s)) }),
      tag(qname(xsd, "element"),
        attribute(qname("name")),
        attribute(qname("type")),
        Element.apply _),
      tag(qname(xsd, "complexType"),
        attribute(qname("name")),
        tag(qname(xsd, "simpleContent"),
          tag(qname(xsd, "restriction"),
            attribute(qname("base")),
            vector(
              attributeProto
            )
          )
        ),
        (name: String, t: (String, Seq[AttributeDecl])) => RestrictionDecl(name, t._1, t._2, None)
      ),
      tag(qname(xsd, "complexType"),
        attribute(qname("name")),
        tag(qname(xsd, "complexContent"),
          tag(qname(xsd, "restriction"),
            attribute(qname("base")),
            vector(
              attributeProto
            ),
            option(elementDeclProto),
            (b:String,a:Seq[AttributeDecl],e:Option[ElementDecl]) => (b,a,e)
          )
        ),
        (name: String, t: (String, Seq[AttributeDecl], Option[ElementDecl])) => RestrictionDecl(name, t._1, t._2, t._3)
      ),
      tag(qname(xsd, "complexType"),
        attribute(qname("name")),
        tag(qname(xsd, "simpleContent"),
          tag(qname(xsd, "extension"),
            attribute(qname("base")),
            vector(
              attributeProto
            )
          )
        ),
        (name: String, t: (String, Seq[AttributeDecl])) => ExtensionDecl(name, t._1, t._2, None)
      ),
      tag(qname(xsd, "complexType"),
        attribute(qname("name")),
        option(elementDeclProto),
        vector(attributeProto),
//        tag(qname(xsd, "sequence"),
//          vector(elementDeclProto)
//        ),
//        (name:String, items:Seq[ElementDecl]) => ContentComplexType(name, Some(SequenceDecl(items)), Seq.empty)
        (name: String, content: Option[ElementDecl], attr: Seq[AttributeDecl]) =>
          ContentComplexType(name, content, attr)
      ),
      /*
      tag(qname(xsd, "complexType"),
        attribute(qname("name")),
        tag(qname(xsd, "choice"),
          vector(elementDeclProto)
        ),
        (name:String, items:Seq[ElementDecl]) => ContentComplexType(name, Some(ChoiceDecl(items)), Seq.empty)
      ),
       */
      tag(qname(xsd, "simpleType"),
        attribute(qname("name")),
        tag(qname(xsd, "restriction"),
          attribute(qname("base")),
        ),
        (name: String, base: String) => RestrictionDecl(name, base, Seq.empty, None)
      )
    )
  )
}

case class SchemaEntry(namespace: String, item: SchemaItem)
object SchemaEntry {
  def unmarshaller(namespace: String): Flow[XMLEvent, SchemaEntry, NotUsed] = 
    Flow[XMLEvent].via(ProtocolReader.of(SchemaItem.proto)).map{ _ match {
      case SchemaItem.Import("", location) => SchemaEntry(namespace, SchemaItem.Import(namespace, location))
      case i => SchemaEntry(namespace, i)
    } }
}

object SchemaBuilder {
  val xsd = ns("http://www.w3.org/2001/XMLSchema")
  val PrefixedName = "(.+):(.+)".r
}

class SchemaBuilder(base: Schema = Schema.empty) extends GraphStageWithMaterializedValue[SinkShape[SchemaEntry],Future[Schema]] {
  import SchemaItem._
  import SchemaBuilder._
  
  val in: Inlet[SchemaEntry] = Inlet("in")
  override val shape:SinkShape[SchemaEntry] = SinkShape(in)
  override def createLogicAndMaterializedValue(attr: Attributes) = {
    val mat = Promise[Schema]
    def safely[T](block: =>T): T = try { block } catch { case x:Throwable => mat.failure(x); throw x }
    
    (new GraphStageLogic(shape) {
      /** a cache of Namespace instances */ 
      val namespace = Map.empty[String,Namespace].withDefault(s => ns(s))
      val types = new ResolveMap[QName,RootType](XsdBuiltinType.all ++ base.rootTypes)
      var elements = new ResolveMap[QName,RootElement](base.rootElements)
      
      setHandler(in, new InHandler {
        override def onPush: Unit = safely {
          handle(grab(in))
          pull(in)
        }
        
        override def onUpstreamFinish: Unit = safely {
          if (!(types.isResolved && elements.isResolved)) {
            val x = new IllegalStateException("Unresolved types/elements after reading all schemas: \n" +
              (types.missingKeys ++ elements.missingKeys).toSeq.map(_.toString).sorted.mkString(",\n"))
            mat.failure(x)
            throw x 
          }
          val elem = elements.values.groupBy(_.name.getNamespaceURI)
          val typs = types.values.groupBy(_.name.getNamespaceURI)
          val namespaces = (elem.keys ++ typs.keys).map(s =>
            SchemaNamespace(s, elem.get(s).getOrElse(Nil), typs.get(s).getOrElse(Nil)))
          mat.success(Schema(namespaces.toSeq))
          super.onUpstreamFinish()
        }

        override def onUpstreamFailure(x: Throwable): Unit = {
          mat.failure(x)
        }
      })
      
      override def preStart() = pull(in)
      
      def handle(item: SchemaEntry): Unit = {
        def toQName(name: String): QName = {
          // FIXME actually use the prefix declarations at the document
          // FIXME take this from the first StartElement
          name match {
            case PrefixedName("udt", localname) => 
              qname(namespace("urn:oasis:names:specification:ubl:schema:xsd:UnqualifiedDataTypes-2"), localname)
            case PrefixedName("qdt", localname) => 
              qname(namespace("urn:oasis:names:specification:ubl:schema:xsd:QualifiedDataTypes-2"), localname)
            case PrefixedName("ccts-cct", localname) => 
              qname(namespace("urn:un:unece:uncefact:data:specification:CoreComponentTypeSchemaModule:2"), localname)
            case PrefixedName("cbc", localname) => 
              qname(namespace("urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"), localname)
            case PrefixedName("cac", localname) => 
              qname(namespace("urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"), localname)
            case PrefixedName("xsd", localname) => 
              qname(namespace("http://www.w3.org/2001/XMLSchema"), localname)
            case PrefixedName("ds", localname) => 
              qname(namespace("http://www.w3.org/2000/09/xmldsig#"), localname)
            case PrefixedName("dsig11", localname) =>
              qname(namespace("http://www.w3.org/2009/xmldsig11#"), localname)
            case PrefixedName("xades", localname) =>
              qname(namespace("http://uri.etsi.org/01903/v1.3.2#"), localname)
            case PrefixedName("sac", localname) =>
              qname(namespace("urn:oasis:names:specification:ubl:schema:xsd:SignatureAggregateComponents-2"), localname)
            case PrefixedName("sbc", localname) =>
              qname(namespace("urn:oasis:names:specification:ubl:schema:xsd:SignatureBasicComponents-2"), localname)
            case PrefixedName("ext", localname) =>
              qname(namespace("urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2"), localname)
            case PrefixedName(prefix, localname) =>
              println("Oh-oh, I forgot " + prefix)
              qname(namespace(prefix), name)
            case fromXsd if item.namespace == "http://www.w3.org/2000/09/xmldsig#" =>
              // this one has XSD as the root namespace. Yes, we need real namespaces.
              qname(xsd, fromXsd)
            case other =>
              qname(namespace(item.namespace), other)
          }
        }

        def localQName(name: String) = qname(namespace(item.namespace), name)

        def resolveElementDecls(elems: Seq[ElementDecl]): Seq[Content] = {
          elems.map { e =>
            val resolved = e.element match {
              case ElementDeclRef("xsd:any") =>
                AnyElement
              case ElementDeclRef(ref) =>
                RootElementRef(elements(toQName(ref)))
              case Element(name, typeName) =>
                RootElement(toQName(name), types(toQName(typeName)))
              case SequenceDecl(subDecls) =>
                Sequence(resolveElementDecls(subDecls))
              case ChoiceDecl(subDecls) =>
                Choice(resolveElementDecls(subDecls))
            }
            Content(resolved, e.minOccurs, e.maxOccurs)
          }
        }

        def resolveAttributeDecls(attributes: Seq[AttributeDecl]): Seq[Attribute] = {
          attributes.map { a =>
            Attribute(toQName(a.name), types(toQName(a.attrType)))
          }
        }

        item.item match {
          case Import(namespace, schemaLocation) =>
            // ignore, imports are already handled at this point.
          case Element(name, elementType) =>
            val qname = localQName(name)
            elements(qname) = RootElement(qname, types(toQName(elementType)))
          case RestrictionDecl(name, base, attributes, content) =>
            // FIXME resolve and pass on content
            val qName = localQName(name)
            types(qName) = RestrictionType(qName, types(toQName(base)), resolveAttributeDecls(attributes), None)
          case ExtensionDecl(name, base, attributes, content) =>
            // FIXME resolve and pass on content
            val qName = localQName(name)
            types(qName) = ExtensionType(qName, types(toQName(base)), resolveAttributeDecls(attributes), None)
          case ContentComplexType(name, Some(ElementDecl(SequenceDecl(elems), min, max)), attributes) =>
            val qName = localQName(name)
            types(qName) = ComplexType(qName, Some(Content(Sequence(resolveElementDecls(elems)), min, max)),
              resolveAttributeDecls(attributes))
          case ContentComplexType(name, Some(ElementDecl(ChoiceDecl(elems), min, max)), attributes) =>
            val qName = localQName(name)
            types(qName) = ComplexType(qName, Some(Content(Choice(resolveElementDecls(elems)), min, max)),
              resolveAttributeDecls(attributes))
          case ContentComplexType(name, None, attributes) =>
            val qName = localQName(name)
            types(qName) = ComplexType(qName, None, resolveAttributeDecls(attributes))
        }
      }

      
    }, mat.future)
  }
  
}
