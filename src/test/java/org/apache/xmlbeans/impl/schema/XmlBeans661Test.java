/*   Copyright 2004 The Apache Software Foundation
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.xmlbeans.impl.schema;

import org.junit.jupiter.api.Test;
import org.labkey.data.xml.queryCustomView.FilterType;
import org.labkey.etl.xml.EtlDocument;
import org.labkey.etl.xml.EtlType;
import org.labkey.etl.xml.SourceObjectType;
import org.labkey.etl.xml.TransformType;

import java.net.URL;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// test for https://issues.apache.org/jira/browse/XMLBEANS-661
// lots of classes and related xsds were copied into the test src
// to avoid the issue of having to regenerate them in the build
public class XmlBeans661Test {

    @Test
    public void testXmlBeans661() throws Exception {
        URL dataUrl = XmlBeans661Test.class.getClassLoader()
                .getResource("xbean/labkey/SourceToTarget2WithFilter.xml");
        assertNotNull(dataUrl, "Test data file not found");
        EtlDocument document = EtlDocument.Factory.parse(dataUrl.openStream());
        EtlType etlXml = document.getEtl();
        for (TransformType transformXml : etlXml.getTransforms().getTransformArray()) {
            SourceObjectType source = transformXml.getSource();
            assertNotNull(source, "Source not found");
            assertTrue(source.isSetSourceFilters(), "Source filters not set");
            // This is where the failure happened with the following error:
            // Exception in thread "main" java.lang.ArrayStoreException: arraycopy: element type mismatch: can not cast one of the elements of java.lang.Object[] to the type of the destination array, org.labkey.data.xml.queryCustomView.FilterType
            FilterType[] filterTypes = source.getSourceFilters().getSourceFilterArray();
            assertNotNull(filterTypes, "Source filters should not be null");
            assertNotEquals(0, filterTypes.length, "Source filters should not be empty");
            Arrays.stream(filterTypes).map(Object::toString).forEach(System.out::println);
        }
    }
}
