Marshalling through lambda expressions
======================================

This module provides an XML and JSON marshalling DSL that is based on lambda expressions. It has the following advantages over the traditional annotation+reflection approach that has become popular in the  JAXB and Jackson worlds:

  - Trivial to add custom data types, delegates, and custom collection types
  - Ability to bind to format-specific idioms, e.g. nested JSON arrays, arbitrary JSON property maps, arbitrary XML tag maps.
  - Java data classes are completely unchanged (and even optional, tuples can be used instead for simple cases)
  - Java data classes can be actually immutable
  - Compile-time errors if anything is not mappable
  - Compile-time type safety on whether a type is readable, writable or both
  - Complete traceability on how a type is mapped, including custom types
  - Potential for higher performance since no reflection is used (no profiling has been done yet though)
  - Its push model makes it compatible with reactive non-blocking parsers, e.g. [Aalto XML](https://github.com/FasterXML/aalto-xml), to hook into reactive streams.

Tutorial
========

You can watch a tutorial presentation [here](http://htmlpreview.github.io/?https://github.com/Tradeshift/ts-reaktive/blob/master/ts-reaktive-marshal/doc/presentation.html)
  
TODO
====

   [ ] Have XML and JSON Reader base class keep the "nestedTag" and "nestedObjects" count globally (and only once), and make
       available to subclasses.