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

package misc.checkin;

import org.apache.xmlbeans.impl.util.Base64Bin;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class Base64BinTest {

    private static final byte[] HELLO = "Hello".getBytes(StandardCharsets.US_ASCII);

    @Test
    void decodesValidValues() {
        assertArrayEquals(HELLO, Base64Bin.decode("SGVsbG8="));
        assertArrayEquals(HELLO, Base64Bin.decode(" SGVs bG8= "));
        assertArrayEquals(HELLO, Base64Bin.decode("SGVs\r\n\tbG8="));
        assertArrayEquals(new byte[]{'H'}, Base64Bin.decode("SA=="));
        assertArrayEquals(new byte[0], Base64Bin.decode(""));
        assertArrayEquals(new byte[0], Base64Bin.decode("  \n "));
    }

    @Test
    void rejectsInvalidValues() {
        assertNull(Base64Bin.decode(null));
        // chars outside the alphabet
        assertNull(Base64Bin.decode("SGVsbG8=!!!!"));
        assertNull(Base64Bin.decode("SGV!!!sbG8="));
        assertNull(Base64Bin.decode("!!!!"));
        // URL-safe alphabet is not base64Binary
        assertNull(Base64Bin.decode("-_-_"));
        // missing padding / wrong length
        assertNull(Base64Bin.decode("SGVsbG8"));
        assertNull(Base64Bin.decode("SGVsbG"));
        assertNull(Base64Bin.decode("A"));
        // misplaced or excess padding
        assertNull(Base64Bin.decode("SG=VsbG8="));
        assertNull(Base64Bin.decode("SGVsbG8=SGVs"));
        assertNull(Base64Bin.decode("SGVsbG8=="));
        assertNull(Base64Bin.decode("S==="));
        assertNull(Base64Bin.decode("===="));
    }
}
