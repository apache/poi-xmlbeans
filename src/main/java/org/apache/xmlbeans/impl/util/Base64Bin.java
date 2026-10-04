/*   Licensed to the Apache Software Foundation (ASF) under one or more
 *   contributor license agreements.  See the NOTICE file distributed with
 *   this work for additional information regarding copyright ownership.
 *   The ASF licenses this file to You under the Apache License, Version 2.0
 *   (the "License"); you may not use this file except in compliance with
 *   the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.apache.xmlbeans.impl.util;

import org.apache.xmlbeans.impl.common.XMLChar;

import java.util.Base64;

/**
 * Strict decoding of the xsd:base64Binary lexical space.
 * <p>
 *     This class is for internal use in Apache XMLBeans. If you need to do your own base64
 *     encoding/decoding, use {@link java.util.Base64}.
 * </p>
 */
public final class Base64Bin {

    private Base64Bin() {
    }

    /**
     * Decodes an xsd:base64Binary value. XML whitespace is ignored; anything else
     * must be in the base64 alphabet, in groups of four characters with
     * {@code =} padding only at the end.
     * <p>
     * The JDK MIME decoder is deliberately not used: it silently drops characters
     * outside the alphabet and accepts unpadded input, both of which fall outside
     * the base64Binary lexical space.
     *
     * @param value the lexical value
     * @return decoded bytes, or null if the input is null or not valid base64Binary
     */
    public static byte[] decode(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0, len = value.length(); i < len; i++) {
            char ch = value.charAt(i);
            if (!XMLChar.isSpace(ch)) {
                sb.append(ch);
            }
        }
        if (sb.length() % 4 != 0) {
            return null;
        }
        try {
            // the basic decoder rejects any char outside the alphabet and
            // misplaced padding
            return Base64.getDecoder().decode(sb.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
