// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.markseen;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;

import org.junit.Ignore;

@Ignore
public class BaseTest {
    protected static byte[] byteArrayFromResource(String resourcePath) throws IOException {
        InputStream resourceStream = BaseTest.class.getClassLoader().getResourceAsStream(resourcePath);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int nRead;

        while ((nRead = resourceStream.read(buffer, 0, buffer.length)) != -1) {
            outputStream.write(buffer, 0, nRead);
        }
        outputStream.flush();

        return outputStream.toByteArray();
    }
}
