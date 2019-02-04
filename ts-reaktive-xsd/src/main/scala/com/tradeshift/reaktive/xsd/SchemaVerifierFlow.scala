package com.tradeshift.reaktive.xsd

import akka.stream.stage.{ GraphStageLogic, InHandler }
import akka.stream.{ Attributes, Inlet, SinkShape }
import akka.stream.stage.GraphStage
import javax.xml.namespace.QName
import javax.xml.stream.events.{ EndElement, StartElement, XMLEvent }
import scala.annotation.tailrec


class SchemaVerifierFlow(Schema: Schema) extends GraphStage[SinkShape[XMLEvent]] {
  val in: Inlet[XMLEvent] = Inlet("in")
  override val shape = SinkShape(in)

  override def createLogic(attr: Attributes) = new GraphStageLogic(shape) {

    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        val event = grab(in)

      }
    })
  }
}

object SchemaVerifierFlow {
  type Errors = Seq[String]

  def validateAttributeValue(value: String, t: RootType): Errors = t match {
    case builtin:XsdBuiltinType =>
      if (builtin.isValid(value))
        Nil
      else
        s"Invalid attribute value '${value}' for type ${t.name}" :: Nil
    case _ => throw new UnsupportedOperationException("TODO: Implement attribute support for " + t)
  }

  def validateAttributes(e: StartElement, t: RootType): Errors = {
    val errors = Vector.newBuilder[String]
    val it = e.getAttributes
    while (it.hasNext()) {
      val attr = it.next().asInstanceOf[javax.xml.stream.events.Attribute]
      t.allowedAttributes.find(_.name == attr.getName) match {
        case Some(allowed) =>
          errors ++= validateAttributeValue(attr.getValue, allowed.attrType)
        case None =>
          errors += s"Tag ${e.getName} has invalid attribute ${attr.getName}"
      }
    }
    errors.result()
  }

  sealed abstract class Context {
    def accept(event: XMLEvent): (Context, Errors) = event match {
      case e if e.isStartElement() => acceptStartElement(e.asStartElement)
      case e if e.isEndElement() => acceptEndElement(e.asEndElement)
      case _ => (this, Nil)
    }

    protected def acceptStartElement(e: StartElement): (Context, Errors) = (this, Nil)
    protected def acceptEndElement(e: EndElement): (Context, Errors) = (this, Nil)
  }

  case class RootContext(schema: Schema) extends Context {
    override def acceptStartElement(e: StartElement) = {
      schema.namespaces.find(_.ns == e.getName.getNamespaceURI) match {
        case None =>
          (IgnoringTagContext(this), s"Namespace of root element ${e.getName} is not known to schema." :: Nil)
        case Some(ns) =>
          ns.elements.get(e.getName) match {
            case None =>
              (IgnoringTagContext(this), s"Root tag ${e.getName} is not known to schema." :: Nil)
            case Some(elem) =>
              (TagContext(this, elem.elementType), validateAttributes(e, elem.elementType))
          }
      }
    }
  }

  case class TagWithContentContext(parent: TagContext, content: Content, seen: Seq[QName]) extends Context {
    override def acceptStartElement(e: StartElement) = {
      ???
    }

    /** Returns Content for any further sub-tags of the current tag,
      * or None if the current tag was found to be invalid
      */
    private def find(name: QName, content: Content): Option[Content] = {
      if (content.maxOccurs <= 1) None else content.element match {
        // when found, always continue with minOccurs and maxOccurs lowered by 1

        case AnyElement =>
          Some(Content(AnyElement, content.minOccurs - 1, content.maxOccurs - 1))
        case e:RootElement =>
          if (e.name == name)
            Some(Content(e, content.minOccurs - 1, content.maxOccurs - 1))
          else
            None
        case e:RootElementRef =>
          if (e.name == name)
            Some(Content(e, content.minOccurs - 1, content.maxOccurs - 1))
          else
            None
        case Sequence(contents) =>
        // iterate over contents, and find the first one that either:
        // - is required but doesn't match the [name] => not found
        // - matches [name] => found, return Content of remaining expected tags (paying attention to min/max)
        // - nothing found => not found

          @tailrec def search(c: Seq[Content]): Option[Option[Content]] = {
            if (c.isEmpty) None else {
              find(name, c.head) match {
                case Some(newContent) =>
                  // We found a match inside the current sequence.
                  // [newContent] is whatever is remaining on this element.
                  val remaining = if (newContent.maxOccurs > 0) newContent +: c.tail else c.tail

                  if (content.maxOccurs == 1) {
                    // The whole sequence only occurs once, so we only expect the remaining.
                    Some(Some(content.copy(element = Sequence(remaining))))
                  } else if (!remaining.isEmpty) {
                    // The whole sequence can occur more than once, and remaining items are remaining.
                    // Hence, we expect first the remaining items and then (potentially) the sequence
                    // again.
                    Some(Some(content.copy(element = Sequence(Seq(
                      Content(Sequence(remaining), 1, 1),
                      Content(Sequence(contents), content.minOccurs - 1, content.maxOccurs - 1)
                    )))))
                  } else {
                    // We only expect (potentially) the whole sequence again.
                    Some(Some(Content(Sequence(contents), content.minOccurs - 1, content.maxOccurs - 1)))
                  }
                case None if c.head.minOccurs > 0 =>
                  Some(None)
                case _ =>
                  search(c.tail)
              }
            }
          }

          // TODO "search" can probably just return Option[Content] directly.
          search(contents) match {
            case Some(result) => result
            case None => None
          }
        case Choice(contents) =>
        // iterate over contents, and find all that allow the element.
        // Return Choice(those) as the next Content alternative.
        // If not at least one path allows the element => not found
          ???
      }
    }
  }

  case class TagContext(parent: Context, currentType: RootType) extends Context {
    override def acceptStartElement(e: StartElement) = currentType match {

      case r: RestrictionType if r.content.isDefined =>
        throw new UnsupportedOperationException("TODO: Implement restricted types with tag content for " + r)
      case e: ExtensionType if e.content.isDefined =>
        throw new UnsupportedOperationException("TODO: Implement extension types with tag content for " + e)
      case _ =>
        (IgnoringTagContext(this),
          s"Type ${currentType.name} is not allowed any sub-tags, but got ${e.getName}." :: Nil)
    }

    // TODO investigate if Aalto already picks up mismatched start/end tags, otherwise we have to.
    override def acceptEndElement(e: EndElement) = (parent, Nil)
  }

  case class IgnoringTagContext(parent: Context) extends Context {
    override def acceptStartElement(e: StartElement) = (IgnoringTagContext(this), Nil)
    override def acceptEndElement(e: EndElement) = (parent, Nil)
  }
}
