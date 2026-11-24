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
package org.jboss.netty.handler.codec.http;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.embedder.CodecEmbedderException;
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.jboss.netty.util.CharsetUtil;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Backport of the tests added in netty/netty#10112 (sonatype-2020-0103) that exercise the
 * {@code maxInitialLineLength} budget for leading control characters.
 *
 * <p>Without the fix, an attacker could keep the decoder busy indefinitely by sending an
 * unbounded run of CRLF or whitespace bytes before the initial line because
 * {@link HttpMessageDecoder} skipped control characters without enforcing any limit
 * (see https://github.com/netty/netty/issues/10111).</p>
 */
public class HttpRequestDecoderTest {

    @Test
    public void testTooLargeInitialLineWithWSOnly() {
        testTooLargeInitialLineWithControlCharsOnly("                    ");
    }

    @Test
    public void testTooLargeInitialLineWithCRLFOnly() {
        testTooLargeInitialLineWithControlCharsOnly("\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n");
    }

    private static void testTooLargeInitialLineWithControlCharsOnly(String controlChars) {
        // The initial line itself fits in 15 bytes ("GET / HTTP/1.1") but together with the leading
        // control characters it must exceed the limit and be rejected.
        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpRequestDecoder(15, 1024, 1024));
        String requestStr = controlChars + "GET / HTTP/1.1\r\n" +
                "Host: localhost1\r\n\r\n";
        try {
            embedder.offer(ChannelBuffers.copiedBuffer(requestStr, CharsetUtil.US_ASCII));
            fail("Expected CodecEmbedderException");
        } catch (CodecEmbedderException e) {
            assertTrue("expected TooLongFrameException but was: " + e.getCause(),
                    e.getCause() instanceof TooLongFrameException);
        }
    }

    @Test
    public void testInitialLineWithLeadingControlChars() {
        // A small amount of leading control characters that fits within the default
        // maxInitialLineLength must still parse cleanly: this guards against accidentally
        // rejecting valid (if oddly framed) requests after the DoS fix.
        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpRequestDecoder());
        String crlf = "\r\n";
        String requestStr = crlf + "GET /some/path HTTP/1.1" + crlf +
                "Host: localhost" + crlf + crlf;
        embedder.offer(ChannelBuffers.copiedBuffer(requestStr, CharsetUtil.US_ASCII));
        HttpRequest req = (HttpRequest) embedder.poll();
        assertNotNull(req);
        assertEquals(HttpMethod.GET, req.getMethod());
        assertEquals("/some/path", req.getUri());
        assertEquals(HttpVersion.HTTP_1_1, req.getProtocolVersion());
    }
}
