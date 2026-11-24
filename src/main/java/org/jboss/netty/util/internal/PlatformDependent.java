/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.util.internal;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Utility that provides access to platform specific functionality which is
 * not available on every Java version supported by Netty.
 * <p>
 * The Java 6 and Java 7 APIs used here are accessed via reflection so that
 * this class still works on Java 5.
 */
public final class PlatformDependent {

    /** {@code java.nio.file.Files.createTempFile(String, String, FileAttribute...)} (Java 7+) */
    private static final Method FILES_CREATE_TEMP_FILE;
    /** {@code java.nio.file.Files.createTempFile(Path, String, String, FileAttribute...)} (Java 7+) */
    private static final Method FILES_CREATE_TEMP_FILE_IN_DIR;
    /** {@code java.io.File.toPath()} (Java 7+) */
    private static final Method FILE_TO_PATH;
    /** {@code java.nio.file.Path.toFile()} (Java 7+) */
    private static final Method PATH_TO_FILE;
    /** An empty {@code java.nio.file.attribute.FileAttribute[]} (Java 7+) */
    private static final Object EMPTY_FILE_ATTRIBUTES;
    /** {@code java.io.File.setReadable(boolean, boolean)} (Java 6+) */
    private static final Method FILE_SET_READABLE;

    static {
        Method createTempFile = null;
        Method createTempFileInDir = null;
        Method toPath = null;
        Method toFile = null;
        Object emptyAttributes = null;
        if (DetectionUtil.javaVersion() >= 7) {
            try {
                ClassLoader loader = File.class.getClassLoader();
                Class<?> filesClass = Class.forName("java.nio.file.Files", false, loader);
                Class<?> pathClass = Class.forName("java.nio.file.Path", false, loader);
                Class<?> attributeClass = Class.forName("java.nio.file.attribute.FileAttribute", false, loader);
                Object attributes = Array.newInstance(attributeClass, 0);
                Class<?> attributesClass = attributes.getClass();

                createTempFile = filesClass.getMethod(
                        "createTempFile", String.class, String.class, attributesClass);
                createTempFileInDir = filesClass.getMethod(
                        "createTempFile", pathClass, String.class, String.class, attributesClass);
                toPath = File.class.getMethod("toPath");
                toFile = pathClass.getMethod("toFile");
                emptyAttributes = attributes;
            } catch (Throwable t) {
                // Not available, fall back to java.io.File below.
                createTempFile = null;
                createTempFileInDir = null;
                toPath = null;
                toFile = null;
                emptyAttributes = null;
            }
        }
        FILES_CREATE_TEMP_FILE = createTempFile;
        FILES_CREATE_TEMP_FILE_IN_DIR = createTempFileInDir;
        FILE_TO_PATH = toPath;
        PATH_TO_FILE = toFile;
        EMPTY_FILE_ATTRIBUTES = emptyAttributes;

        Method setReadable;
        try {
            setReadable = File.class.getMethod("setReadable", boolean.class, boolean.class);
        } catch (Throwable t) {
            // Java 5: there is no way to adjust the permissions of a file.
            setReadable = null;
        }
        FILE_SET_READABLE = setReadable;
    }

    /**
     * Creates a new empty file in the specified directory (or in the default
     * temporary-file directory if {@code directory} is {@code null}) which is
     * only readable by its owner.
     * <p>
     * Unlike {@link File#createTempFile(String, String, File)}, which creates
     * the file with the default permissions of the process (usually readable
     * by every local user), on Java 7+ this uses
     * {@code java.nio.file.Files#createTempFile} which restricts access to the
     * owner of the file, and on Java 6 the read permission of everyone but the
     * owner is removed.
     *
     * @see <a href="https://github.com/netty/netty/security/advisories/GHSA-5mcr-gq6c-3hq2">GHSA-5mcr-gq6c-3hq2</a>
     */
    public static File createTempFile(String prefix, String suffix, File directory) throws IOException {
        if (FILES_CREATE_TEMP_FILE != null) {
            final Object path;
            if (directory == null) {
                path = invoke(FILES_CREATE_TEMP_FILE, null, prefix, suffix, EMPTY_FILE_ATTRIBUTES);
            } else {
                path = invoke(FILES_CREATE_TEMP_FILE_IN_DIR, null,
                        invoke(FILE_TO_PATH, directory), prefix, suffix, EMPTY_FILE_ATTRIBUTES);
            }
            return (File) invoke(PATH_TO_FILE, path);
        }
        final File file;
        if (directory == null) {
            file = File.createTempFile(prefix, suffix);
        } else {
            file = File.createTempFile(prefix, suffix, directory);
        }

        // Try to adjust the perms, if this fails there is not much else we can do...
        if (FILE_SET_READABLE != null) {
            if (!setReadable(file, false, false)) {
                throw new IOException("Failed to set permissions on temporary file " + file);
            }
            if (!setReadable(file, true, true)) {
                throw new IOException("Failed to set permissions on temporary file " + file);
            }
        }
        return file;
    }

    private static boolean setReadable(File file, boolean readable, boolean ownerOnly) throws IOException {
        return Boolean.TRUE.equals(invoke(FILE_SET_READABLE, file, readable, ownerOnly));
    }

    private static Object invoke(Method method, Object target, Object... args) throws IOException {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            IOException ioe = new IOException("Failed to invoke " + method);
            ioe.initCause(cause);
            throw ioe;
        } catch (IllegalAccessException e) {
            IOException ioe = new IOException("Failed to invoke " + method);
            ioe.initCause(e);
            throw ioe;
        }
    }

    private PlatformDependent() {
        // only static method supported
    }
}
