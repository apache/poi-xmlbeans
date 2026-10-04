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

package org.apache.xmlbeans.impl.common;

import java.io.*;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class IOUtil {

    // temporary directory location
    private static final Path tmpdir = Paths.get(System.getProperty("java.io.tmpdir"));

    public static void copyCompletely(InputStream input, OutputStream output)
        throws IOException {
        try {
            //if both are file streams, use channel IO
            if ((output instanceof FileOutputStream fos) && (input instanceof FileInputStream fis)) {
                try {
                    FileChannel target = fos.getChannel();
                    FileChannel source = fis.getChannel();

                    source.transferTo(0, Integer.MAX_VALUE, target);

                    source.close();
                    target.close();

                    return;
                } catch (Exception e) { /* failover to byte stream version */ }
            }

            byte[] buf = new byte[8192];
            while (true) {
                int length = input.read(buf);
                if (length < 0) {
                    break;
                }
                output.write(buf, 0, length);
            }
        } finally {
            try {
                input.close();
            } catch (IOException ignore) {
            }
            try {
                output.close();
            } catch (IOException ignore) {
            }
        }
    }

    public static void copyCompletely(Reader input, Writer output)
        throws IOException {
        try {
            char[] buf = new char[8192];
            while (true) {
                int length = input.read(buf);
                if (length < 0) {
                    break;
                }
                output.write(buf, 0, length);
            }
        } finally {
            try {
                input.close();
            } catch (IOException ignore) {
            }
            try {
                output.close();
            } catch (IOException ignore) {
            }
        }
    }

    public static void copyCompletely(URI input, URI output) throws IOException {
        File out = new File(output);
        File dir = out.getParentFile();
        dir.mkdirs();

        try (InputStream in = urlToStream(input);
             OutputStream os = Files.newOutputStream(out.toPath())) {
            IOUtil.copyCompletely(in, os);
        } catch (IllegalArgumentException e) {
            throw new IOException("Cannot copy to " + output);
        }
    }

    private static InputStream urlToStream(URI input) throws IOException {
        try {
            File f = new File(input);
            if (f.exists()) {
                return Files.newInputStream(f.toPath());
            }
        } catch (Exception ignored) {
            // notAFile
        }

        return input.toURL().openStream();
    }

    public static File createDir(File rootdir, String subdir) {
        File newdir = (subdir == null) ? rootdir : new File(rootdir, subdir);
        boolean created = (newdir.exists() && newdir.isDirectory()) || newdir.mkdirs();
        assert (created) : "Could not create " + newdir.getAbsolutePath();
        return newdir;
    }

    public static Path getTempDir() {
        return tmpdir;
    }
}
