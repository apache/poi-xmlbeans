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

package org.apache.xmlbeans.impl.tool;

import org.xml.sax.EntityResolver;

import javax.xml.catalog.Catalog;
import javax.xml.catalog.CatalogFeatures;
import javax.xml.catalog.CatalogManager;
import java.io.File;

/**
 * Helper class for XML catalogs, based on the JDK {@code javax.xml.catalog} API
 */
public class MavenPluginResolver {
    public static EntityResolver getResolver(String catalogLocation) {
        if (catalogLocation == null) {
            return null;
        }

        CatalogFeatures features = CatalogFeatures.builder()
            .with(CatalogFeatures.Feature.PREFER, "system")
            .build();
        Catalog catalog = CatalogManager.catalog(features, new File(catalogLocation).toURI());
        return CatalogManager.catalogResolver(catalog);
    }
}
