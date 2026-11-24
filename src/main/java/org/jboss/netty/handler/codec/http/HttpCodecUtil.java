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

import java.util.Iterator;
import java.util.List;

final class HttpCodecUtil {

    static void validateHeaderName(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        for (int i = 0; i < name.length(); i ++) {
            char c = name.charAt(i);
            if (c > 127) {
                throw new IllegalArgumentException(
                        "name contains non-ascii character: " + name);
            }

            // Check prohibited characters.
            switch (c) {
            case '\t': case '\n': case 0x0b: case '\f': case '\r':
            case ' ':  case ',':  case ':':  case ';':  case '=':
                throw new IllegalArgumentException(
                        "name contains one of the following prohibited characters: " +
                        "=,;: \\t\\r\\n\\v\\f: " + name);
            }
        }
    }

    static void validateHeaderValue(String value) {
        if (value == null) {
            throw new NullPointerException("value");
        }

        // 0 - the previous character was neither CR nor LF
        // 1 - the previous character was CR
        // 2 - the previous character was LF
        int state = 0;

        for (int i = 0; i < value.length(); i ++) {
            char c = value.charAt(i);

            // Check the absolutely prohibited characters.
            switch (c) {
            case 0x0b: // Vertical tab
                throw new IllegalArgumentException(
                        "value contains a prohibited character '\\v': " + value);
            case '\f':
                throw new IllegalArgumentException(
                        "value contains a prohibited character '\\f': " + value);
            }

            // Check the CRLF (HT | SP) pattern
            switch (state) {
            case 0:
                switch (c) {
                case '\r':
                    state = 1;
                    break;
                case '\n':
                    state = 2;
                    break;
                }
                break;
            case 1:
                switch (c) {
                case '\n':
                    state = 2;
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Only '\\n' is allowed after '\\r': " + value);
                }
                break;
            case 2:
                switch (c) {
                case '\t': case ' ':
                    state = 0;
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Only ' ' and '\\t' are allowed after '\\n': " + value);
                }
            }
        }

        if (state != 0) {
            throw new IllegalArgumentException(
                    "value must not end with '\\r' or '\\n':" + value);
        }
    }

    static boolean isTransferEncodingChunked(HttpMessage m) {
        List<String> chunked = m.getHeaders(HttpHeaders.Names.TRANSFER_ENCODING);
        if (chunked.isEmpty()) {
            return false;
        }

        for (String v: chunked) {
            if (v.equalsIgnoreCase(HttpHeaders.Values.CHUNKED)) {
                return true;
            }
        }
        return false;
    }

    static void removeTransferEncodingChunked(HttpMessage m) {
        List<String> values = m.getHeaders(HttpHeaders.Names.TRANSFER_ENCODING);
        if (values.isEmpty()) {
            return;
        }
        Iterator<String> valuesIt = values.iterator();
        while (valuesIt.hasNext()) {
            String value = valuesIt.next();
            if (value.equalsIgnoreCase(HttpHeaders.Values.CHUNKED)) {
                valuesIt.remove();
            }
        }
        if (values.isEmpty()) {
            m.removeHeader(HttpHeaders.Names.TRANSFER_ENCODING);
        } else {
            m.setHeader(HttpHeaders.Names.TRANSFER_ENCODING, values);
        }
    }

    static boolean isContentLengthSet(HttpMessage m) {
        List<String> contentLength = m.getHeaders(HttpHeaders.Names.CONTENT_LENGTH);
        return !contentLength.isEmpty();
    }

    /**
     * Validates, and optionally extracts the content length from headers. This method is not intended for
     * general use.
     *
     * @param contentLengthFields the content-length header fields.
     * @param isHttp10OrEarlier {@code true} if we are handling HTTP/1.0 or earlier
     * @param allowDuplicateContentLengths {@code true}  if multiple, identical-value content lengths should be allowed.
     * @return the normalized content length from the headers or {@code -1} if the fields were empty.
     * @throws IllegalArgumentException if the content-length fields are not valid
     */
    static long normalizeAndGetContentLength(
            List<String> contentLengthFields, boolean isHttp10OrEarlier,
            boolean allowDuplicateContentLengths) {
        if (contentLengthFields.isEmpty()) {
            return -1;
        }

        // Guard against multiple Content-Length headers as stated in
        // https://tools.ietf.org/html/rfc7230#section-3.3.2:
        //
        // If a message is received that has multiple Content-Length header
        //   fields with field-values consisting of the same decimal value, or a
        //   single Content-Length header field with a field value containing a
        //   list of identical decimal values (e.g., "Content-Length: 42, 42"),
        //   indicating that duplicate Content-Length header fields have been
        //   generated or combined by an upstream message processor, then the
        //   recipient MUST either reject the message as invalid or replace the
        //   duplicated field-values with a single valid Content-Length field
        //   containing that decimal value prior to determining the message body
        //   length or forwarding the message.
        String firstField = contentLengthFields.get(0);
        boolean multipleContentLengths =
                contentLengthFields.size() > 1 || firstField.indexOf(',') >= 0;

        if (multipleContentLengths && !isHttp10OrEarlier) {
            if (allowDuplicateContentLengths) {
                // Find and enforce that all Content-Length values are the same
                String firstValue = null;
                for (String field : contentLengthFields) {
                    String[] tokens = field.split(",", -1);
                    for (int i = 0; i < tokens.length; i++) {
                        String trimmed = tokens[i].trim();
                        if (firstValue == null) {
                            firstValue = trimmed;
                        } else if (!trimmed.equals(firstValue)) {
                            throw new IllegalArgumentException(
                                    "Multiple Content-Length values found: " + contentLengthFields);
                        }
                    }
                }
                // Replace the duplicated field-values with a single valid Content-Length field
                firstField = firstValue;
            } else {
                // Reject the message as invalid
                throw new IllegalArgumentException(
                        "Multiple Content-Length values found: " + contentLengthFields);
            }
        }
        // Ensure we not allow sign as part of the content-length:
        // See https://github.com/squid-cache/squid/security/advisories/GHSA-qf3v-rc95-96j5
        if (firstField.length() == 0 || !Character.isDigit(firstField.charAt(0))) {
            // Reject the message as invalid
            throw new IllegalArgumentException(
                    "Content-Length value is not a number: " + firstField);
        }
        try {
            final long value = Long.parseLong(firstField);
            if (value < 0) {
                // Reject the message as invalid
                throw new IllegalArgumentException(
                        "Content-Length value must be >=0: " + value);
            }
            return value;
        } catch (NumberFormatException e) {
            // Reject the message as invalid
            throw new IllegalArgumentException(
                    "Content-Length value is not a number: " + firstField, e);
        }
    }

    private HttpCodecUtil() {
    }
}
