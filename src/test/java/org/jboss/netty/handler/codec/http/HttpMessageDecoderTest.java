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

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.embedder.CodecEmbedderException;
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;
import org.jboss.netty.util.CharsetUtil;

import org.junit.Test;

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
}
