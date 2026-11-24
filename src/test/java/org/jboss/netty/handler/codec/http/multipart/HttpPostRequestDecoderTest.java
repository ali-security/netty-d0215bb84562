/*
 * Copyright 2013 The Netty Project
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
import org.jboss.netty.handler.codec.http.DefaultHttpChunk;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder.ErrorDataDecoderException;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder.IncompatibleDataDecoderException;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder.TooLongFormFieldException;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder.TooManyFormFieldsException;
import org.jboss.netty.util.CharsetUtil;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/** {@link HttpPostRequestDecoder} test case. */
public class HttpPostRequestDecoderTest {

    private static HttpRequest newChunkedRequest() {
        HttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
        req.setChunked(true);
        return req;
    }

    @Test
    public void testTooManyFormFieldsPostStandardDecoder() throws Exception {
        HttpRequest req = newChunkedRequest();

        HttpPostRequestDecoder decoder =
                new HttpPostRequestDecoder(req, HttpPostRequestDecoder.DEFAULT_MAX_FIELDS, -1);

        StringBuilder body = new StringBuilder();
        for (int i = 0; i <= HttpPostRequestDecoder.DEFAULT_MAX_FIELDS; i++) {
            body.append("k").append(i).append("=v&");
        }

        try {
            decoder.offer(new DefaultHttpChunk(ChannelBuffers.wrappedBuffer(body.toString().getBytes())));
            fail();
        } catch (ErrorDataDecoderException e) {
            assertEquals(TooManyFormFieldsException.class, e.getClass());
        }
    }

    @Test
    public void testStandardDecoderAcceptsUpToMaxFields() throws Exception {
        HttpRequest req = newChunkedRequest();

        HttpPostRequestDecoder decoder =
                new HttpPostRequestDecoder(req, HttpPostRequestDecoder.DEFAULT_MAX_FIELDS, -1);

        StringBuilder body = new StringBuilder();
        for (int i = 0; i < HttpPostRequestDecoder.DEFAULT_MAX_FIELDS; i++) {
            body.append("k").append(i).append("=v&");
        }

        decoder.offer(new DefaultHttpChunk(ChannelBuffers.wrappedBuffer(body.toString().getBytes())));
    }

    @Test
    public void testTooManyFormFieldsPostMultipartDecoder() throws Exception {
        HttpRequest req = newChunkedRequest();
        req.addHeader("Content-Type", "multipart/form-data; boundary=be38b42a9ad2713f");

        HttpPostRequestDecoder decoder =
                new HttpPostRequestDecoder(req, HttpPostRequestDecoder.DEFAULT_MAX_FIELDS, -1);

        StringBuilder body = new StringBuilder();
        body.append("--be38b42a9ad2713f\r\n");
        for (int i = 0; i <= HttpPostRequestDecoder.DEFAULT_MAX_FIELDS; i++) {
            body.append("Content-Disposition: form-data; name=\"k").append(i).append("\"\r\n")
                .append("\r\n")
                .append("v").append(i).append("\r\n")
                .append("--be38b42a9ad2713f");
            if (i == HttpPostRequestDecoder.DEFAULT_MAX_FIELDS) {
                body.append("--\r\n");
            } else {
                body.append("\r\n");
            }
        }

        try {
            decoder.offer(new DefaultHttpChunk(ChannelBuffers.wrappedBuffer(body.toString().getBytes())));
            fail();
        } catch (ErrorDataDecoderException e) {
            assertEquals(TooManyFormFieldsException.class, e.getClass());
        }
    }

    @Test
    public void testTooLongFormFieldStandardDecoder() throws Exception {
        HttpRequest req = newChunkedRequest();

        HttpPostRequestDecoder decoder =
                new HttpPostRequestDecoder(req, -1, HttpPostRequestDecoder.DEFAULT_MAX_BUFFERED_BYTES);

        try {
            decoder.offer(new DefaultHttpChunk(ChannelBuffers.wrappedBuffer(
                    new byte[HttpPostRequestDecoder.DEFAULT_MAX_BUFFERED_BYTES + 1])));
            fail();
        } catch (ErrorDataDecoderException e) {
            assertEquals(TooLongFormFieldException.class, e.getClass());
        }
    }

    @Test
    public void testFieldGreaterThanMaxBufferedBytesStandardDecoder() throws Exception {
        HttpRequest req = newChunkedRequest();

        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(req, -1, 6);

        decoder.offer(new DefaultHttpChunk(ChannelBuffers.wrappedBuffer("foo=bar".getBytes())));
    }

    @Test
    public void testTooLongFormFieldMultipartDecoder() throws Exception {
        HttpRequest req = newChunkedRequest();
        req.addHeader("Content-Type", "multipart/form-data; boundary=be38b42a9ad2713f");

        HttpPostRequestDecoder decoder =
                new HttpPostRequestDecoder(req, -1, HttpPostRequestDecoder.DEFAULT_MAX_BUFFERED_BYTES);

        try {
            decoder.offer(new DefaultHttpChunk(ChannelBuffers.wrappedBuffer(
                    new byte[HttpPostRequestDecoder.DEFAULT_MAX_BUFFERED_BYTES + 1])));
            fail();
        } catch (ErrorDataDecoderException e) {
            assertEquals(TooLongFormFieldException.class, e.getClass());
        }
    }

    @Test
    public void testFieldGreaterThanMaxBufferedBytesMultipartDecoder() throws Exception {
        HttpRequest req = newChunkedRequest();
        req.addHeader("Content-Type", "multipart/form-data; boundary=be38b42a9ad2713f");

        byte[] bodyBytes = ("--be38b42a9ad2713f\r\n" +
                "Content-Disposition: form-data; name=\"title\"\r\n" +
                "Content-Length: 10\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "\r\n" +
                "bar-stream\r\n" +
                "--be38b42a9ad2713f--\r\n").getBytes();

        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(req, -1, bodyBytes.length - 1);

        decoder.offer(new DefaultHttpChunk(ChannelBuffers.wrappedBuffer(bodyBytes)));
    }

    @Test(expected = IncompatibleDataDecoderException.class)
    public void testNonPostRequestRejected() throws Exception {
        HttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        new HttpPostRequestDecoder(req);
    }

    @Test
    public void testBinaryStreamUpload() throws Exception {
        final String boundary = "dLV9Wyq26L_-JQxk6ferf-RT153LhOO";

        final DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "http://localhost");

        req.setContent(ChannelBuffers.EMPTY_BUFFER);
        req.setHeader(HttpHeaders.Names.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);
        req.setHeader(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
        req.setChunked(true);

        // Force to use memory-based data.
        final DefaultHttpDataFactory inMemoryFactory = new DefaultHttpDataFactory(false);

        for (String data : Arrays.asList("", "\r", "\r\r", "\r\r\r")) {
            final String body =
                    "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"tmp-0.txt\"\r\n" +
                    "Content-Type: image/gif\r\n" +
                    "\r\n" +
                    data + "\r\n" +
                    "--" + boundary + "--\r\n";

            // Create decoder instance to test.
            final HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(inMemoryFactory, req);

            decoder.offer(new DefaultHttpChunk(ChannelBuffers.copiedBuffer(body, CharsetUtil.UTF_8)));
            decoder.offer(new DefaultHttpChunk(ChannelBuffers.EMPTY_BUFFER));

            // Validate it's enough chunks to decode upload.
            assertTrue(decoder.hasNext());

            // Decode binary upload.
            MemoryFileUpload upload = (MemoryFileUpload) decoder.next();

            // Validate data has been parsed correctly as it was passed into request.
            assertArrayEquals("Invalid decoded data [data=" + data + "upload=" + upload + ']',
                data.getBytes(), upload.get());
        }
    }
}
