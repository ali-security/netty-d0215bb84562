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
package org.jboss.netty.handler.codec.http.multipart;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.util.CharsetUtil;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.jboss.netty.util.internal.PlatformDependentTest.*;
import static org.junit.Assert.*;

/**
 * Temporary files holding uploaded data must not be readable by other local users.
 *
 * @see <a href="https://github.com/netty/netty/security/advisories/GHSA-5mcr-gq6c-3hq2">GHSA-5mcr-gq6c-3hq2</a>
 */
public class DiskFileUploadTest {

    @Test
    public void testSetContentCreatesFileOnlyReadableByOwner() throws IOException {
        DiskFileUpload upload = new DiskFileUpload("file", "secret.txt", "text/plain", null, null, 0);
        try {
            upload.setContent(ChannelBuffers.copiedBuffer("secret", CharsetUtil.UTF_8));
            File file = upload.getFile();
            assertNotNull(file);
            assertTrue(file.isFile());
            assertOwnerOnly(file);
            assertEquals("secret", upload.getString(CharsetUtil.UTF_8));
        } finally {
            upload.delete();
        }
    }

    @Test
    public void testAddContentCreatesFileOnlyReadableByOwner() throws IOException {
        DiskFileUpload upload = new DiskFileUpload("file", "secret.txt", "text/plain", null, null, 0);
        try {
            upload.addContent(ChannelBuffers.copiedBuffer("sec", CharsetUtil.UTF_8), false);
            upload.addContent(ChannelBuffers.copiedBuffer("ret", CharsetUtil.UTF_8), true);
            File file = upload.getFile();
            assertNotNull(file);
            assertOwnerOnly(file);
            assertEquals("secret", upload.getString(CharsetUtil.UTF_8));
        } finally {
            upload.delete();
        }
    }

    @Test
    public void testBaseDirectoryFileOnlyReadableByOwner() throws IOException {
        File dir = File.createTempFile("netty-upload-", "");
        assertTrue(dir.delete());
        assertTrue(dir.mkdir());
        String oldBaseDirectory = DiskFileUpload.baseDirectory;
        DiskFileUpload.baseDirectory = dir.getAbsolutePath();
        try {
            DiskFileUpload upload = new DiskFileUpload("file", "secret.txt", "text/plain", null, null, 0);
            try {
                upload.setContent(ChannelBuffers.copiedBuffer("secret", CharsetUtil.UTF_8));
                File file = upload.getFile();
                assertNotNull(file);
                assertEquals(dir.getCanonicalFile(), file.getParentFile().getCanonicalFile());
                assertOwnerOnly(file);
            } finally {
                upload.delete();
            }
        } finally {
            DiskFileUpload.baseDirectory = oldBaseDirectory;
            dir.delete();
        }
    }

    @Test
    public void testDiskAttributeFileOnlyReadableByOwner() throws IOException {
        DiskAttribute attribute = new DiskAttribute("password");
        try {
            attribute.setValue("secret");
            File file = attribute.getFile();
            assertNotNull(file);
            assertOwnerOnly(file);
            assertEquals("secret", attribute.getValue());
        } finally {
            attribute.delete();
        }
    }

    @Test
    public void testDecodedMultipartUploadOnDiskOnlyReadableByOwner() throws Exception {
        final String boundary = "dLV9Wyq26L_-JQxk6ferf-RT153LhOO";
        final String body =
                "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"password\"\r\n" +
                "\r\n" +
                "secret-field\r\n" +
                "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"secret.txt\"\r\n" +
                "Content-Type: text/plain\r\n" +
                "\r\n" +
                "secret-file\r\n" +
                "--" + boundary + "--\r\n";

        final DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "http://localhost");
        req.setHeader(HttpHeaders.Names.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);
        req.setContent(ChannelBuffers.copiedBuffer(body, CharsetUtil.UTF_8));

        // Force to use disk-based data.
        final DefaultHttpDataFactory diskFactory = new DefaultHttpDataFactory(true);
        final HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(diskFactory, req);
        try {
            DiskAttribute attribute = (DiskAttribute) decoder.getBodyHttpData("password");
            assertNotNull(attribute);
            assertEquals("secret-field", attribute.getValue());
            assertOwnerOnly(attribute.getFile());

            DiskFileUpload upload = (DiskFileUpload) decoder.getBodyHttpData("file");
            assertNotNull(upload);
            assertEquals("secret-file", upload.getString(CharsetUtil.UTF_8));
            assertOwnerOnly(upload.getFile());
        } finally {
            decoder.cleanFiles();
        }
    }
}
