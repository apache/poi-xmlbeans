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

import org.apache.xmlbeans.SystemProperties;
import org.apache.xmlbeans.impl.common.IOUtil;
import org.apache.xmlbeans.impl.util.ExceptionUtil;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class CodeGenUtil {
    public static final String DEFAULT_MEM_START = "8m";
    public static final String DEFAULT_MEM_MAX = "256m";
    public static final String DEFAULT_COMPILER = "javac";

    //workaround for Sun bug # 4723726
    public static URI resolve(URI base, URI child) {
        URI ruri = base.resolve(child);

        //fix up normalization bug
        if ("file".equals(ruri.getScheme()) && !child.equals(ruri)) {
            if (base.getPath().startsWith("//") && !ruri.getPath().startsWith("//")) {
                String path = "///".concat(ruri.getPath());
                try {
                    ruri = new URI("file", null, path, ruri.getQuery(), ruri.getFragment());
                } catch (URISyntaxException ignored) {
                }
            }
        }
        return ruri;
    }

    static void addAllJavaFiles(List<File> srcFiles, List<String> args) {
        for (File f : srcFiles) {
            if (f.isDirectory()) {
                File[] files = f.listFiles(
                    file -> (file.isFile() && file.getName().endsWith(".java")) || file.isDirectory()
                );
                if (files != null) {
                    addAllJavaFiles(Arrays.asList(files), args);
                }
            } else {
                args.add(quoteAndEscapeFilename(f.getAbsolutePath()));
            }
        }
    }

    static private String quoteAndEscapeFilename(String filename) {
        // don't quote if there's no space
        if (!filename.contains(" ")) {
            return filename;
        }

        // bizarre.  javac expects backslash escaping if we quote the classpath
        return "\"" + filename.replace("\\", "\\\\") + "\"";
    }


    /**
     * Invokes javac on the generated source files in order to turn them
     * into binary files in the output directory.  This will return a list of
     * {@code GenFile}s for all of the classes produced or null if an
     * error occurred.
     *
     * @deprecated
     */
    public static boolean externalCompile(List<File> srcFiles, File outdir, File[] cp, boolean debug) {
        return externalCompile(srcFiles, outdir, cp, debug, DEFAULT_COMPILER, null, DEFAULT_MEM_START, DEFAULT_MEM_MAX,
            false, false, null);
    }

    /**
     * Invokes javac on the generated source files in order to turn them
     * into binary files in the output directory.  This will return a list of
     * {@code GenFile}s for all of the classes produced or null if an
     * error occurred.
     *
     * @deprecated
     */
    public static boolean externalCompile(List<File> srcFiles, File outdir, File[] cp, boolean debug, String javacPath, String memStart, String memMax,
                                          boolean quiet, boolean verbose) {
        return externalCompile(srcFiles, outdir, cp, debug, javacPath, null, memStart, memMax, quiet, verbose, null);
    }

    /**
     * Invokes javac on the generated source files in order to turn them
     * into binary files in the output directory.  This will return a list of
     * {@code GenFile}s for all of the classes produced or null if an
     * error occurred.
     *
     * @deprecated
     */
    public static boolean externalCompile(List<File> srcFiles, File outdir, File[] cp, boolean debug, String javacPath, String memStart, String memMax,
                                          boolean quiet, boolean verbose, String sourceCodeEncoding) {
        return externalCompile(srcFiles, outdir, cp, debug, javacPath, null, memStart, memMax, quiet, verbose, sourceCodeEncoding);
    }

    /**
     * Invokes javac on the generated source files in order to turn them
     * into binary files in the output directory.  This will return a list of
     * {@code GenFile}s for all of the classes produced or null if an
     * error occurred.
     *
     * @deprecated
     */
    public static boolean externalCompile(List<File> srcFiles, File outdir, File[] cp, boolean debug, String javacPath, String genver, String memStart, String memMax,
                                          boolean quiet, boolean verbose) {
        return externalCompile(srcFiles, outdir, cp, debug, javacPath, genver, memStart, memMax, quiet, verbose, null);
    }

    /**
     * Invokes javac on the generated source files in order to turn them
     * into binary files in the output directory.  This will return a list of
     * {@code GenFile}s for all of the classes produced or null if an
     * error occurred.
     */
    public static boolean externalCompile(List<File> srcFiles, File outdir, File[] cp, boolean debug, String javacPath, String genver, String memStart, String memMax,
                                          boolean quiet, boolean verbose, String sourceCodeEncoding) {
        List<String> args = new ArrayList<>();

        File javac = findJavaTool(javacPath == null ? DEFAULT_COMPILER : javacPath);
        assert (javac.exists()) : "compiler not found " + javac;
        args.add(javac.getAbsolutePath());

        if (outdir == null) {
            outdir = new File(".");
        } else {
            args.add("-d");
            args.add(quoteAndEscapeFilename(outdir.getAbsolutePath()));
        }

        if (cp == null) {
            cp = systemClasspath();
        }

        if(sourceCodeEncoding != null && !sourceCodeEncoding.isEmpty()) {
            args.add("-encoding");
            args.add(sourceCodeEncoding);
        }

        if (cp.length > 0) {
            StringBuilder classPath = new StringBuilder();
            // Add the output directory to the classpath.  We do this so that
            // javac will be able to find classes that were compiled
            // previously but are not in the list of sources this time.
            classPath.append(outdir.getAbsolutePath());

            // Add everything on our classpath.
            for (File file : cp) {
                classPath.append(File.pathSeparator);
                classPath.append(file.getAbsolutePath());
            }

            args.add("-classpath");

            // bizarre.  javac expects backslash escaping if we quote the classpath
            args.add(quoteAndEscapeFilename(classPath.toString()));
        }

        if (genver == null) {
            genver = "1.8";
        }

        args.add("-source");
        args.add(genver);

        args.add("-target");
        args.add(genver);

        args.add(debug ? "-g" : "-g:none");

        if (verbose) {
            args.add("-verbose");
        }

        addAllJavaFiles(srcFiles, args);

        File clFile = null;
        try {
            clFile = Files.createTempFile(IOUtil.getTempDir(), "javac", ".tmp").toFile();
            try (Writer fw = Files.newBufferedWriter(clFile.toPath(), Charset.defaultCharset())) {
                Iterator<String> i = args.iterator();
                for (i.next(); i.hasNext(); ) {
                    String arg = i.next();
                    fw.write(arg);
                    fw.write('\n');
                }
            }
            List<String> newargs = new ArrayList<>();
            newargs.add(args.get(0));

            if (memStart != null && !memStart.isEmpty()) {
                newargs.add("-J-Xms" + memStart);
            }
            if (memMax != null && !memMax.isEmpty()) {
                newargs.add("-J-Xmx" + memMax);
            }

            newargs.add("@" + clFile.getAbsolutePath());
            args = newargs;
        } catch (Exception e) {
            System.err.println("Could not create command-line file for javac");
        }

        try {
            String[] strArgs = args.toArray(new String[0]);

            if (verbose) {
                System.out.print("compile command:");
                for (String strArg : strArgs) {
                    System.out.print(" " + strArg);
                }
                System.out.println();
            }

            final Process proc = Runtime.getRuntime().exec(strArgs);

            StringBuilder errorBuffer = new StringBuilder();
            StringBuilder outputBuffer = new StringBuilder();

            try {
                // the compiler reads nothing from stdin - leaving the pipe open just
                // holds a file descriptor until the Process is collected
                proc.getOutputStream().close();

                Thread out = copy(proc.getInputStream(), outputBuffer);
                Thread err = copy(proc.getErrorStream(), errorBuffer);

                proc.waitFor();

                // the readers can still be draining the pipes after the process exits,
                // so join before reporting what they collected
                out.join();
                err.join();
            } finally {
                proc.destroy();
            }

            if (verbose || proc.exitValue() != 0) {
                if (outputBuffer.length() > 0) {
                    System.out.println(outputBuffer.toString());
                    System.out.flush();
                }
                if (errorBuffer.length() > 0) {
                    System.err.println(errorBuffer.toString());
                    System.err.flush();
                }

                if (proc.exitValue() != 0) {
                    return false;
                }
            }
        } catch (Throwable e) {
            if (ExceptionUtil.isFatal(e)) {
                ExceptionUtil.rethrow(e);
            }
            System.err.println(e.toString());
            System.err.println(e.getCause());
            e.printStackTrace(System.err);
            return false;
        } finally {
            if (!debug && clFile != null) {
                clFile.delete();
            }
        }

        return true;
    }

    public static File[] systemClasspath() {
        List<File> cp = new ArrayList<>();
        CodeSource cs = CodeGenUtil.class.getProtectionDomain().getCodeSource();
        if (cs != null) {
            cp.add(new File(cs.getLocation().getPath()));
        } else {
            System.err.println("Can't determine path of xmlbeans-*.jar - specify classpath explicitly!");
        }

        String jcp = SystemProperties.getProperty("java.class.path");
        if (jcp != null) {
            String[] systemcp = jcp.split(File.pathSeparator);
            for (String s : systemcp) {
                cp.add(new File(s));
            }
        }
        return cp.toArray(new File[0]);
    }


    /**
     * Look for tool in current directory and ${JAVA_HOME}/../bin and
     * try with .exe file extension.
     */
    private static File findJavaTool(String tool) {
        File toolFile = new File(tool);
        if (toolFile.isFile()) {
            return toolFile;
        }

        File result = new File(tool + ".exe");
        if (result.isFile()) {
            return result;
        }

        String home = SystemProperties.getProperty("java.home");

        String sep = File.separator;
        result = new File(home + sep + ".." + sep + "bin", tool);

        if (result.isFile()) {
            return result;
        }

        result = new File(result.getPath() + ".exe");
        if (result.isFile()) {
            return result;
        }

        result = new File(home + sep + "bin", tool);
        if (result.isFile()) {
            return result;
        }

        result = new File(result.getPath() + ".exe");
        if (result.isFile()) {
            return result;
        }

        // just return the original toolFile and hope that it is on the PATH.
        return toolFile;
    }

    /**
     * Reads the given input stream into the given buffer until there is
     * nothing left to read.
     */
    private static Thread copy(InputStream stream, final StringBuilder output) {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(stream, Charset.defaultCharset()));
        Thread readerThread = new Thread(() -> {
            try (BufferedReader r = reader) {
                r.lines().forEach(s -> output.append(s).append('\n'));
            } catch (IOException e) {
                // nothing useful to do with a failure to drain the pipe
            }
        });
        readerThread.start();
        return readerThread;
    }
}
