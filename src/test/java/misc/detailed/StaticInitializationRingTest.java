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

package misc.detailed;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * XMLBEANS-616: two threads that touch XMLBeans for the first time at once, one through {@code XmlObject.Factory} and
 * one through a generated type, must not wait for each other forever.
 *
 * <p>{@code XmlBeans.<clinit>} needs {@code SchemaTypeLoaderBase} (for {@code NO_TYPE}, through its subclass
 * {@code BuiltinSchemaTypeSystem}); a static field of {@code SchemaTypeLoaderBase} read {@code XmlBeans} in turn. The
 * first thread then held the initialization of {@code XmlBeans}, the second that of {@code SchemaTypeLoaderBase}, and
 * each waited for the other's. Every attempt loads XMLBeans from the test class path in a class loader of its own, so
 * its classes start uninitialized; the generated type is XMLBeans' own {@code SchemaDocument}.</p>
 */
public class StaticInitializationRingTest {

    private static final int ATTEMPTS = 20;

    private static final long TIMEOUT_MILLIS = 5000;

    @Test
    void firstContactFromBothEndsDoesNotDeadlock() throws Exception {
        URL[] classPath = testClassPath();
        int hung = 0;
        for (int attempt = 0; attempt < ATTEMPTS && hung == 0; attempt++) {
            URLClassLoader loader = new URLClassLoader(classPath, ClassLoader.getPlatformClassLoader());
            CountDownLatch start = new CountDownLatch(1);
            Thread untyped = touch(loader, start, "org.apache.xmlbeans.XmlObject");
            Thread typed = touch(loader, start, "org.apache.xmlbeans.impl.xb.xsdschema.SchemaDocument");
            start.countDown();
            untyped.join(TIMEOUT_MILLIS);
            typed.join(TIMEOUT_MILLIS);
            if (untyped.isAlive() || typed.isAlive()) {
                hung++;
            }
        }
        assertEquals(0, hung, "the first contact from both ends of the static initialization deadlocked");
    }

    private static URL[] testClassPath() throws MalformedURLException {
        List<URL> urls = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            urls.add(new File(entry).toURI().toURL());
        }
        return urls.toArray(new URL[0]);
    }

    private static Thread touch(ClassLoader loader, CountDownLatch start, String type) {
        Thread thread = new Thread(() -> {
            try {
                start.await();
                Object factory = Class.forName(type, true, loader).getField("Factory").get(null);
                factory.getClass().getMethod("newInstance").invoke(factory);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
