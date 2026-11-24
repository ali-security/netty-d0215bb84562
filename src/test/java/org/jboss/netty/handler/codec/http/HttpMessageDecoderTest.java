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
package org.jboss.netty.handler.codec.http;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.embedder.CodecEmbedderException;
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;
import org.jboss.netty.util.CharsetUtil;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class HttpMessageDecoderTest {

    @Test
    public void testHeaderWithNoValueAndMissingColon() {
        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpRequestDecoder());
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 0\r\n" +
                "Host:\r\n" +
                "netty.io\r\n\r\n";

        try {
            embedder.offer(ChannelBuffers.copiedBuffer(requestStr, CharsetUtil.US_ASCII));
            fail("Expected CodecEmbedderException");
        } catch (CodecEmbedderException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testContentLengthHeaderWithPositiveSign() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: +1\r\n\r\n" +
                "b";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testContentLengthHeaderWithNegativeSign() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: -1\r\n\r\n";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testContentLengthHeaderWithNegativeValue() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: -10\r\n\r\n";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testContentLengthHeaderWithPositiveSignHttp10() {
        String requestStr = "GET /some/path HTTP/1.0\r\n" +
                "Content-Length: +1\r\n\r\n" +
                "b";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testContentLengthHeaderEmpty() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length:\r\n\r\n";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testContentLengthHeaderNotANumber() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: foo\r\n\r\n";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testMultipleContentLengthHeadersSameValue() {
        // When allowDuplicateContentLengths is false (default), even identical values should be rejected
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 0\r\n" +
                "Content-Length: 0\r\n\r\n";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testMultipleContentLengthHeadersDifferentValues() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 1\r\n" +
                "Content-Length: 2\r\n\r\n" +
                "bb";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testMultipleContentLengthHeadersSameValueWithComma() {
        // When allowDuplicateContentLengths is false (default), even identical comma-separated values
        // should be rejected
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 0, 0\r\n\r\n";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testMultipleContentLengthHeaders() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 1\r\n" +
                "Content-Length: 0\r\n\r\n" +
                "b";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testMultipleContentLengthHeaders2() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 1\r\n" +
                "Connection: close\r\n" +
                "Content-Length: 0\r\n\r\n" +
                "b";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testContentLengthHeaderWithCommaValue() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 1,1\r\n\r\n" +
                "b";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testMultipleContentLengthHeadersWithFolding() {
        String requestStr = "POST / HTTP/1.1\r\n" +
                "Host: example.com\r\n" +
                "Connection: close\r\n" +
                "Content-Length: 5\r\n" +
                "Content-Length:\r\n" +
                "\t6\r\n\r\n" +
                "123456";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testContentLengthHeaderAndChunked() {
        String requestStr = "POST / HTTP/1.1\r\n" +
                "Host: example.com\r\n" +
                "Connection: close\r\n" +
                "Content-Length: 5\r\n" +
                "Transfer-Encoding: chunked\r\n\r\n" +
                "0\r\n\r\n";

        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpRequestDecoder());
        assertTrue(embedder.offer(ChannelBuffers.copiedBuffer(requestStr, CharsetUtil.US_ASCII)));
        HttpRequest request = (HttpRequest) embedder.poll();
        assertNotNull(request);
        assertTrue(request.isChunked());
        assertTrue(HttpHeaders.Values.CHUNKED.equalsIgnoreCase(
                request.getHeader(HttpHeaders.Names.TRANSFER_ENCODING)));
        assertFalse(request.containsHeader(HttpHeaders.Names.CONTENT_LENGTH));
        HttpChunk chunk = (HttpChunk) embedder.poll();
        assertNotNull(chunk);
        assertTrue(chunk.isLast());
        assertFalse(embedder.finish());
    }

    @Test
    public void testResponseContentLengthHeaderAndChunked() {
        String responseStr = "HTTP/1.1 200 OK\r\n" +
                "Content-Length: 5\r\n" +
                "Transfer-Encoding: chunked\r\n\r\n" +
                "0\r\n\r\n";

        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpResponseDecoder());
        assertTrue(embedder.offer(ChannelBuffers.copiedBuffer(responseStr, CharsetUtil.US_ASCII)));
        HttpResponse response = (HttpResponse) embedder.poll();
        assertNotNull(response);
        assertTrue(response.isChunked());
        assertTrue(HttpHeaders.Values.CHUNKED.equalsIgnoreCase(
                response.getHeader(HttpHeaders.Names.TRANSFER_ENCODING)));
        assertFalse(response.containsHeader(HttpHeaders.Names.CONTENT_LENGTH));
        HttpChunk chunk = (HttpChunk) embedder.poll();
        assertNotNull(chunk);
        assertTrue(chunk.isLast());
        assertFalse(embedder.finish());
    }

    @Test
    public void testResponseContentLengthHeaderWithPositiveSign() {
        String responseStr = "HTTP/1.1 200 OK\r\n" +
                "Content-Length: +1\r\n\r\n" +
                "b";
        testInvalidHeaders0(new HttpResponseDecoder(), responseStr);
    }

    @Test
    public void testAllowDuplicateContentLengthsSameValue() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 1\r\n" +
                "Content-Length: 1\r\n\r\n" +
                "b";
        testAllowedDuplicateContentLengths0(requestStr);
    }

    @Test
    public void testAllowDuplicateContentLengthsSameValueWithComma() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 1, 1\r\n\r\n" +
                "b";
        testAllowedDuplicateContentLengths0(requestStr);
    }

    @Test
    public void testAllowDuplicateContentLengthsDifferentValues() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 1\r\n" +
                "Content-Length: 2\r\n\r\n" +
                "bb";
        testInvalidHeaders0(new HttpRequestDecoder(4096, 8192, 8192, true), requestStr);
    }

    @Test
    public void testAllowDuplicateContentLengthsWithSign() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: +1, +1\r\n\r\n" +
                "b";
        testInvalidHeaders0(new HttpRequestDecoder(4096, 8192, 8192, true), requestStr);
    }

    @Test
    public void testNormalizeAndGetContentLength() {
        assertEquals(-1, HttpCodecUtil.normalizeAndGetContentLength(
                new ArrayList<String>(), false, false));
        assertEquals(42, HttpCodecUtil.normalizeAndGetContentLength(
                Collections.singletonList("42"), false, false));
        assertEquals(42, HttpCodecUtil.normalizeAndGetContentLength(
                Arrays.asList("42", "42"), false, true));
        assertEquals(42, HttpCodecUtil.normalizeAndGetContentLength(
                Collections.singletonList("42, 42"), false, true));
        assertEquals(42, HttpCodecUtil.normalizeAndGetContentLength(
                Arrays.asList("42", "43"), true, false));
    }

    @Test
    public void testNormalizeAndGetContentLengthInvalid() {
        testNormalizeAndGetContentLengthInvalid0(Collections.singletonList(""), false, false);
        testNormalizeAndGetContentLengthInvalid0(Collections.singletonList("foo"), false, false);
        testNormalizeAndGetContentLengthInvalid0(Collections.singletonList("-1"), false, false);
        testNormalizeAndGetContentLengthInvalid0(Collections.singletonList("+1"), false, false);
        testNormalizeAndGetContentLengthInvalid0(Collections.singletonList("+1"), true, false);
        testNormalizeAndGetContentLengthInvalid0(Arrays.asList("1", "1"), false, false);
        testNormalizeAndGetContentLengthInvalid0(Collections.singletonList("1, 1"), false, false);
        testNormalizeAndGetContentLengthInvalid0(Arrays.asList("1", "2"), false, true);
        testNormalizeAndGetContentLengthInvalid0(Collections.singletonList("1, 2"), false, true);
        testNormalizeAndGetContentLengthInvalid0(Collections.singletonList(", "), false, true);
    }

    private static void testNormalizeAndGetContentLengthInvalid0(
            List<String> contentLengthFields, boolean isHttp10OrEarlier, boolean allowDuplicateContentLengths) {
        try {
            HttpCodecUtil.normalizeAndGetContentLength(
                    contentLengthFields, isHttp10OrEarlier, allowDuplicateContentLengths);
            fail("Expected IllegalArgumentException for " + contentLengthFields);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void testAllowedDuplicateContentLengths0(String requestStr) {
        DecoderEmbedder<Object> embedder =
                new DecoderEmbedder<Object>(new HttpRequestDecoder(4096, 8192, 8192, true));
        assertTrue(embedder.offer(ChannelBuffers.copiedBuffer(requestStr, CharsetUtil.US_ASCII)));
        HttpRequest request = (HttpRequest) embedder.poll();
        assertNotNull(request);
        List<String> contentLengths = request.getHeaders(HttpHeaders.Names.CONTENT_LENGTH);
        assertEquals(1, contentLengths.size());
        assertEquals("1", contentLengths.get(0));
        assertEquals(1, HttpHeaders.getContentLength(request));
        assertEquals("b", request.getContent().toString(CharsetUtil.US_ASCII));
        assertNull(embedder.poll());
    }

    // ---------------------------------------------------------------------
    // GHSA-wx5j-54mm-rqqq: header names must be bracketed by OWS only.
    // The decoder used to silently strip every Character.isWhitespace char,
    // which let an attacker smuggle control characters around a header name.
    // The strict OWS check now rejects 0x0c (FF) and 0x1c-0x1f (FS/GS/RS/US)
    // both before the name and between the name and the colon.
    // ---------------------------------------------------------------------

    @Test
    public void testRequestHeaderNameStartsWithControlChar1c() {
        testRequestHeaderNameStartsWithControlChar(0x1c);
    }

    @Test
    public void testRequestHeaderNameStartsWithControlChar1d() {
        testRequestHeaderNameStartsWithControlChar(0x1d);
    }

    @Test
    public void testRequestHeaderNameStartsWithControlChar1e() {
        testRequestHeaderNameStartsWithControlChar(0x1e);
    }

    @Test
    public void testRequestHeaderNameStartsWithControlChar1f() {
        testRequestHeaderNameStartsWithControlChar(0x1f);
    }

    @Test
    public void testRequestHeaderNameStartsWithControlChar0c() {
        testRequestHeaderNameStartsWithControlChar(0x0c);
    }

    private static void testRequestHeaderNameStartsWithControlChar(int controlChar) {
        ChannelBuffer requestBuffer = ChannelBuffers.dynamicBuffer();
        requestBuffer.writeBytes("GET /some/path HTTP/1.1\r\nHost: netty.io\r\n".getBytes(CharsetUtil.US_ASCII));
        requestBuffer.writeByte(controlChar);
        requestBuffer.writeBytes("Transfer-Encoding: chunked\r\n\r\n".getBytes(CharsetUtil.US_ASCII));
        testInvalidHeaders0(new HttpRequestDecoder(), requestBuffer);
    }

    @Test
    public void testRequestHeaderNameEndsWithControlChar1c() {
        testRequestHeaderNameEndsWithControlChar(0x1c);
    }

    @Test
    public void testRequestHeaderNameEndsWithControlChar1d() {
        testRequestHeaderNameEndsWithControlChar(0x1d);
    }

    @Test
    public void testRequestHeaderNameEndsWithControlChar1e() {
        testRequestHeaderNameEndsWithControlChar(0x1e);
    }

    @Test
    public void testRequestHeaderNameEndsWithControlChar1f() {
        testRequestHeaderNameEndsWithControlChar(0x1f);
    }

    @Test
    public void testRequestHeaderNameEndsWithControlChar0c() {
        testRequestHeaderNameEndsWithControlChar(0x0c);
    }

    private static void testRequestHeaderNameEndsWithControlChar(int controlChar) {
        ChannelBuffer requestBuffer = ChannelBuffers.dynamicBuffer();
        requestBuffer.writeBytes("GET /some/path HTTP/1.1\r\nHost: netty.io\r\n".getBytes(CharsetUtil.US_ASCII));
        requestBuffer.writeBytes("Transfer-Encoding".getBytes(CharsetUtil.US_ASCII));
        requestBuffer.writeByte(controlChar);
        requestBuffer.writeBytes(": chunked\r\n\r\n".getBytes(CharsetUtil.US_ASCII));
        testInvalidHeaders0(new HttpRequestDecoder(), requestBuffer);
    }

    @Test
    public void testResponseHeaderNameStartsWithControlChar1c() {
        testResponseHeaderNameStartsWithControlChar(0x1c);
    }

    @Test
    public void testResponseHeaderNameStartsWithControlChar1d() {
        testResponseHeaderNameStartsWithControlChar(0x1d);
    }

    @Test
    public void testResponseHeaderNameStartsWithControlChar1e() {
        testResponseHeaderNameStartsWithControlChar(0x1e);
    }

    @Test
    public void testResponseHeaderNameStartsWithControlChar1f() {
        testResponseHeaderNameStartsWithControlChar(0x1f);
    }

    @Test
    public void testResponseHeaderNameStartsWithControlChar0c() {
        testResponseHeaderNameStartsWithControlChar(0x0c);
    }

    private static void testResponseHeaderNameStartsWithControlChar(int controlChar) {
        ChannelBuffer responseBuffer = ChannelBuffers.dynamicBuffer();
        responseBuffer.writeBytes("HTTP/1.1 200 OK\r\nHost: netty.io\r\n".getBytes(CharsetUtil.US_ASCII));
        responseBuffer.writeByte(controlChar);
        responseBuffer.writeBytes("Transfer-Encoding: chunked\r\n\r\n".getBytes(CharsetUtil.US_ASCII));
        testInvalidHeaders0(new HttpResponseDecoder(), responseBuffer);
    }

    @Test
    public void testResponseHeaderNameEndsWithControlChar1c() {
        testResponseHeaderNameEndsWithControlChar(0x1c);
    }

    @Test
    public void testResponseHeaderNameEndsWithControlChar1d() {
        testResponseHeaderNameEndsWithControlChar(0x1d);
    }

    @Test
    public void testResponseHeaderNameEndsWithControlChar1e() {
        testResponseHeaderNameEndsWithControlChar(0x1e);
    }

    @Test
    public void testResponseHeaderNameEndsWithControlChar1f() {
        testResponseHeaderNameEndsWithControlChar(0x1f);
    }

    @Test
    public void testResponseHeaderNameEndsWithControlChar0c() {
        testResponseHeaderNameEndsWithControlChar(0x0c);
    }

    private static void testResponseHeaderNameEndsWithControlChar(int controlChar) {
        ChannelBuffer responseBuffer = ChannelBuffers.dynamicBuffer();
        responseBuffer.writeBytes("HTTP/1.1 200 OK\r\nHost: netty.io\r\n".getBytes(CharsetUtil.US_ASCII));
        responseBuffer.writeBytes("Transfer-Encoding".getBytes(CharsetUtil.US_ASCII));
        responseBuffer.writeByte(controlChar);
        responseBuffer.writeBytes(": chunked\r\n\r\n".getBytes(CharsetUtil.US_ASCII));
        testInvalidHeaders0(new HttpResponseDecoder(), responseBuffer);
    }

    @Test
    public void testWhitespaceInRequestHeaderName() {
        testInvalidHeaders0("GET /some/path HTTP/1.1\r\n" +
                "Transfer-Encoding : chunked\r\n" +
                "Host: netty.io\r\n\r\n");
    }

    @Test
    public void testWhitespaceInResponseHeaderName() {
        String responseStr = "HTTP/1.1 200 OK\r\n" +
                "Transfer-Encoding : chunked\r\n" +
                "Host: netty.io\r\n\r\n";
        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpResponseDecoder());
        embedder.offer(ChannelBuffers.copiedBuffer(responseStr, CharsetUtil.US_ASCII));
        Object msg = embedder.poll();
        assertTrue(msg instanceof HttpResponse);
        HttpResponse response = (HttpResponse) msg;
        assertEquals(HttpHeaders.Values.CHUNKED, response.getHeader(HttpHeaders.Names.TRANSFER_ENCODING));
        assertEquals("netty.io", response.getHeader(HttpHeaders.Names.HOST));
    }

    private static void testInvalidHeaders0(String requestStr) {
        testInvalidHeaders0(new HttpRequestDecoder(), requestStr);
    }

    private static void testInvalidHeaders0(HttpMessageDecoder decoder, String messageStr) {
        testInvalidHeaders0(decoder, ChannelBuffers.copiedBuffer(messageStr, CharsetUtil.US_ASCII));
    }

    private static void testInvalidHeaders0(HttpMessageDecoder decoder, ChannelBuffer messageBuffer) {
        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(decoder);
        try {
            embedder.offer(messageBuffer);
            fail("Expected CodecEmbedderException");
        } catch (CodecEmbedderException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }
}
