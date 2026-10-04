/* ====================================================================
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */

module org.apache.xmlbeans {
    requires java.xml;
    requires jdk.xml.dom;
    requires org.apache.logging.log4j;
    requires static ant;
    requires static com.github.javaparser.core;
    requires static Saxon.HE;

    exports org.apache.xmlbeans;
    exports org.apache.xmlbeans.soap;
    // exports org.apache.xmlbeans.impl;
    exports org.apache.xmlbeans.impl.xpathgen;
    exports org.apache.xmlbeans.impl.validator;
    exports org.apache.xmlbeans.impl.repackage;
    exports org.apache.xmlbeans.impl.common;
    exports org.apache.xmlbeans.impl.config;
    exports org.apache.xmlbeans.impl.richParser;
    exports org.apache.xmlbeans.impl.soap;
    exports org.apache.xmlbeans.impl.xpath;
    exports org.apache.xmlbeans.impl.xpath.saxon;
    exports org.apache.xmlbeans.impl.xpath.xmlbeans;
    exports org.apache.xmlbeans.impl.regex;
    exports org.apache.xmlbeans.impl.tool;
    exports org.apache.xmlbeans.impl.schema;
    exports org.apache.xmlbeans.impl.xsd2inst;
    exports org.apache.xmlbeans.impl.values;
    exports org.apache.xmlbeans.impl.inst2xsd;
    exports org.apache.xmlbeans.impl.inst2xsd.util;
    exports org.apache.xmlbeans.impl.store;
    exports org.apache.xmlbeans.impl.util;
    // exports org.apache.xmlbeans.xml;
    exports org.apache.xmlbeans.xml.stream;
    exports org.apache.xmlbeans.xml.stream.events;
    // exports org.apache.xmlbeans.xml.stream.utils;

    exports org.apache.xmlbeans.impl.xb.xmlconfig;
    exports org.apache.xmlbeans.impl.xb.xmlschema;
    exports org.apache.xmlbeans.impl.xb.xsdschema;
    exports org.apache.xmlbeans.impl.xb.xsdownload;

    opens org.apache.xmlbeans.metadata.system.sXMLCONFIG;
    opens org.apache.xmlbeans.metadata.system.sXMLLANG;
    opens org.apache.xmlbeans.metadata.system.sXMLSCHEMA;
    opens org.apache.xmlbeans.metadata.system.sXMLTOOLS;
}