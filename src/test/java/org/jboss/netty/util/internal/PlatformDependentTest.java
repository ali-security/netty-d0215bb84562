/*
 * Copyright 2021 The Netty Project
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

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.Assert.*;
import static org.junit.Assume.*;

/**
 * Unit test for {@link PlatformDependent}.
 */
public class PlatformDependentTest {

    @Test
    public void testCreateTempFileWithNullDirectory() throws IOException {
        File file = PlatformDependent.createTempFile("netty-test-", ".tmp", null);
        try {
            assertNotNull(file);
            assertTrue(file.isFile());
            assertEquals(0, file.length());
            assertTrue(file.getName().startsWith("netty-test-"));
            assertTrue(file.getName().endsWith(".tmp"));
            assertEquals(new File(System.getProperty("java.io.tmpdir")).getCanonicalFile(),
                    file.getParentFile().getCanonicalFile());
        } finally {
            file.delete();
        }
    }

    @Test
    public void testCreateTempFileWithDirectory() throws IOException {
        File dir = newTempDirectory();
        try {
            File file = PlatformDependent.createTempFile("netty-test-", ".tmp", dir);
            try {
                assertNotNull(file);
                assertTrue(file.isFile());
                assertTrue(file.getName().startsWith("netty-test-"));
                assertTrue(file.getName().endsWith(".tmp"));
                assertEquals(dir.getCanonicalFile(), file.getParentFile().getCanonicalFile());
            } finally {
                file.delete();
            }
        } finally {
            dir.delete();
        }
    }

    @Test(expected = IOException.class)
    public void testCreateTempFileWithMissingDirectory() throws IOException {
        File dir = newTempDirectory();
        assertTrue(dir.delete());
        PlatformDependent.createTempFile("netty-test-", ".tmp", dir);
    }

    @Test
    public void testCreateTempFileIsNotReadableByOthers() throws IOException {
        File file = PlatformDependent.createTempFile("netty-perm-", ".tmp", null);
        try {
            assertOwnerOnly(file);
        } finally {
            file.delete();
        }
    }

    @Test
    public void testCreateTempFileInDirectoryIsNotReadableByOthers() throws IOException {
        File dir = newTempDirectory();
        try {
            File file = PlatformDependent.createTempFile("netty-perm-", ".tmp", dir);
            try {
                assertOwnerOnly(file);
            } finally {
                file.delete();
            }
        } finally {
            dir.delete();
        }
    }

    private static File newTempDirectory() throws IOException {
        File dir = File.createTempFile("netty-dir-", "");
        assertTrue(dir.delete());
        assertTrue(dir.mkdir());
        return dir;
    }

    /**
     * Asserts that only the owner of the given file is able to read it, so that
     * other local users can not access its content.
     */
    public static void assertOwnerOnly(File file) throws IOException {
        String permissions = posixPermissions(file);
        assumeNotNull(permissions);
        assertEquals("owner must be able to read and write " + file + ": " + permissions,
                "rw", permissions.substring(0, 2));
        assertEquals("group must not be able to read " + file + ": " + permissions,
                "-", permissions.substring(3, 4));
        assertEquals("others must not be able to read " + file + ": " + permissions,
                "-", permissions.substring(6, 7));
        if (DetectionUtil.javaVersion() >= 7) {
            assertEquals("rw-------", permissions);
        }
    }

    /**
     * Returns the POSIX permissions of the given file (e.g. {@code rw-r--r--}),
     * or {@code null} if they can not be determined on this platform.
     */
    private static String posixPermissions(File file) throws IOException {
        Method getPosixFilePermissions;
        Method toPath;
        Method permissionsToString;
        Object noLinkOptions;
        try {
            Class<?> filesClass = Class.forName("java.nio.file.Files");
            Class<?> pathClass = Class.forName("java.nio.file.Path");
            Class<?> linkOptionClass = Class.forName("java.nio.file.LinkOption");
            Class<?> permissionsClass = Class.forName("java.nio.file.attribute.PosixFilePermissions");
            noLinkOptions = Array.newInstance(linkOptionClass, 0);
            getPosixFilePermissions = filesClass.getMethod(
                    "getPosixFilePermissions", pathClass, noLinkOptions.getClass());
            toPath = File.class.getMethod("toPath");
            permissionsToString = permissionsClass.getMethod("toString", Set.class);
        } catch (Exception e) {
            // Java 6 or older.
            return null;
        }
        try {
            Object permissions = getPosixFilePermissions.invoke(null, toPath.invoke(file), noLinkOptions);
            return (String) permissionsToString.invoke(null, permissions);
        } catch (Exception e) {
            if (e.getCause() instanceof UnsupportedOperationException) {
                // Not a POSIX file system (e.g. Windows)
                return null;
            }
            IOException ioe = new IOException("Failed to read the permissions of " + file);
            ioe.initCause(e);
            throw ioe;
        }
    }
}
