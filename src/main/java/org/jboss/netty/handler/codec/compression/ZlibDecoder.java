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
package org.jboss.netty.handler.codec.compression;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneDecoder;
import org.jboss.netty.util.internal.jzlib.JZlib;
import org.jboss.netty.util.internal.jzlib.ZStream;


/**
 * Decompresses a {@link ChannelBuffer} using the deflate algorithm.
 * @apiviz.landmark
 * @apiviz.has org.jboss.netty.handler.codec.compression.ZlibWrapper
 */
public class ZlibDecoder extends OneToOneDecoder {

    private final ZStream z = new ZStream();
    private byte[] dictionary;
    private volatile boolean finished;

    /**
     * Maximum allowed size of the cumulative decompression buffer, in bytes.
     * {@code 0} means "no limit" which preserves the original behaviour. A positive
     * value caps how much a single decompressed stream may expand to, protecting
     * against decompression-bomb DoS attacks. See CVE-2020-11612 / sonatype-2020-0029
     * (https://github.com/netty/netty/pull/9924).
     */
    private final int maxAllocation;

    /**
     * Creates a new instance with the default wrapper ({@link ZlibWrapper#ZLIB})
     * and no decompression-buffer limit.
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public ZlibDecoder() {
        this(ZlibWrapper.ZLIB, 0);
    }

    /**
     * Creates a new instance with the default wrapper ({@link ZlibWrapper#ZLIB})
     * and the specified maximum decompression-buffer size.
     *
     * @param maxAllocation
     *          Maximum size of the decompression buffer. Must be &gt;= 0.
     *          If zero, no limit is enforced.
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public ZlibDecoder(int maxAllocation) {
        this(ZlibWrapper.ZLIB, maxAllocation);
    }

    /**
     * Creates a new instance with the specified wrapper and no decompression-buffer limit.
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public ZlibDecoder(ZlibWrapper wrapper) {
        this(wrapper, 0);
    }

    /**
     * Creates a new instance with the specified wrapper and maximum decompression-buffer size.
     *
     * @param maxAllocation
     *          Maximum size of the decompression buffer. Must be &gt;= 0.
     *          If zero, no limit is enforced.
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public ZlibDecoder(ZlibWrapper wrapper, int maxAllocation) {
        if (wrapper == null) {
            throw new NullPointerException("wrapper");
        }
        if (maxAllocation < 0) {
            throw new IllegalArgumentException("maxAllocation must be >= 0");
        }
        this.maxAllocation = maxAllocation;

        synchronized (z) {
            int resultCode = z.inflateInit(ZlibUtil.convertWrapperType(wrapper));
            if (resultCode != JZlib.Z_OK) {
                ZlibUtil.fail(z, "initialization failure", resultCode);
            }
        }
    }

    /**
     * Creates a new instance with the specified preset dictionary and no
     * decompression-buffer limit. The wrapper is always {@link ZlibWrapper#ZLIB}
     * because it is the only format that supports the preset dictionary.
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public ZlibDecoder(byte[] dictionary) {
        this(dictionary, 0);
    }

    /**
     * Creates a new instance with the specified preset dictionary and maximum
     * decompression-buffer size. The wrapper is always {@link ZlibWrapper#ZLIB}
     * because it is the only format that supports the preset dictionary.
     *
     * @param maxAllocation
     *          Maximum size of the decompression buffer. Must be &gt;= 0.
     *          If zero, no limit is enforced.
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public ZlibDecoder(byte[] dictionary, int maxAllocation) {
        if (dictionary == null) {
            throw new NullPointerException("dictionary");
        }
        if (maxAllocation < 0) {
            throw new IllegalArgumentException("maxAllocation must be >= 0");
        }
        this.maxAllocation = maxAllocation;
        this.dictionary = dictionary;

        synchronized (z) {
            int resultCode;
            resultCode = z.inflateInit(JZlib.W_ZLIB);
            if (resultCode != JZlib.Z_OK) {
                ZlibUtil.fail(z, "initialization failure", resultCode);
            }
        }
    }

    /**
     * Returns {@code true} if and only if the end of the compressed stream
     * has been reached.
     */
    public boolean isClosed() {
        return finished;
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
        if (!(msg instanceof ChannelBuffer) || finished) {
            return msg;
        }

        synchronized (z) {
            try {
                // Configure input.
                ChannelBuffer compressed = (ChannelBuffer) msg;
                byte[] in = new byte[compressed.readableBytes()];
                compressed.readBytes(in);
                z.next_in = in;
                z.next_in_index = 0;
                z.avail_in = in.length;

                // Configure output.
                byte[] out = new byte[in.length << 1];
                ChannelBuffer decompressed = ChannelBuffers.dynamicBuffer(
                        compressed.order(), out.length,
                        ctx.getChannel().getConfig().getBufferFactory());
                z.next_out = out;
                z.next_out_index = 0;
                z.avail_out = out.length;

                loop: for (;;) {
                    // Decompress 'in' into 'out'
                    int resultCode = z.inflate(JZlib.Z_SYNC_FLUSH);
                    if (z.next_out_index > 0) {
                        // Enforce the maximum decompression-buffer size so a small compressed
                        // payload cannot be inflated into an out-of-memory condition.
                        // See CVE-2020-11612 / sonatype-2020-0029.
                        if (maxAllocation != 0 &&
                            decompressed.writerIndex() + z.next_out_index > maxAllocation) {
                            finished = true; // Do not decode anymore.
                            z.inflateEnd();
                            throw new CompressionException(
                                    "Decompression buffer has reached maximum size: " + maxAllocation);
                        }
                        decompressed.writeBytes(out, 0, z.next_out_index);
                        z.avail_out = out.length;
                    }
                    z.next_out_index = 0;

                    switch (resultCode) {
                    case JZlib.Z_NEED_DICT:
                        if (dictionary == null) {
                            ZlibUtil.fail(z, "decompression failure", resultCode);
                        } else {
                            resultCode = z.inflateSetDictionary(dictionary, dictionary.length);
                            if (resultCode != JZlib.Z_OK) {
                                ZlibUtil.fail(z, "failed to set the dictionary", resultCode);
                            }
                        }
                        break;
                    case JZlib.Z_STREAM_END:
                        finished = true; // Do not decode anymore.
                        z.inflateEnd();
                        break loop;
                    case JZlib.Z_OK:
                        break;
                    case JZlib.Z_BUF_ERROR:
                        if (z.avail_in <= 0) {
                            break loop;
                        }
                        break;
                    default:
                        ZlibUtil.fail(z, "decompression failure", resultCode);
                    }
                }

                if (decompressed.writerIndex() != 0) { // readerIndex is always 0
                    return decompressed;
                } else {
                    return null;
                }
            } finally {
                // Deference the external references explicitly to tell the VM that
                // the allocated byte arrays are temporary so that the call stack
                // can be utilized.
                // I'm not sure if the modern VMs do this optimization though.
                z.next_in = null;
                z.next_out = null;
            }
        }
    }
}
