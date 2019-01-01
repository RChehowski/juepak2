package eu.chakhouski.juepak.util;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;

import static java.lang.String.join;
import static java.lang.System.lineSeparator;
import static java.util.Arrays.asList;

public class UE4Serializer
{
    private static final CharsetEncoder asciiEncoder = StandardCharsets.US_ASCII.newEncoder();
    private static final CharsetEncoder utf16Encoder = StandardCharsets.UTF_16LE.newEncoder();

    public static void WriteString(ByteBuffer b, String s)
    {
        final CharsetEncoder encoder;
        if (asciiEncoder.canEncode(s))
            encoder = asciiEncoder;
        else if (utf16Encoder.canEncode(s))
            encoder = utf16Encoder;
        else
            throw new IllegalArgumentException("No UE4-supported encoder can encode \"" + s + "\"");

        // Setup byte order
        b.order(ByteOrder.LITTLE_ENDIAN);

        // Write length
        // Length is negative for UTF-16 coded strings, this is UE4 string serializing convention
        b.putInt(((encoder == utf16Encoder) ? -s.length() : s.length()) + 1 /*1 extra char for '\0'*/);

        // Write encoded content
        b.put(s.getBytes(encoder.charset()));

        // Add 0-terminator bytes to the end (1 for ASCII, 2 for UTF-16)
        for (int i = 0; i < ((int) encoder.maxBytesPerChar()); i++)
            b.put((byte)0);
    }

    @SuppressWarnings("ForLoopReplaceableByForEach")
    public static void WriteStructArray(ByteBuffer b, Object[] array)
    {
        b.order(ByteOrder.LITTLE_ENDIAN);

        // Serialize array size
        b.putInt(array.length);

        for (int i = 0, length = array.length; i < length; i++)
        {
            final Object item = array[i];

            if (item instanceof UESerializable)
            {
                try {
                    ((UESerializable) item).Serialize(b);
                }
                catch (BufferUnderflowException | BufferOverflowException be) {
                    // Print buffer info
                    System.err.println("Buffer exception caused: " + b.toString());

                    // Rethrow exception, it is fatal, unfortunately :C
                    throw be;
                }
            }
            else
            {
                throw new IllegalArgumentException(join(lineSeparator(), asList(
                    "Unsupported element class",
                    "   Expected: Subclass of " + UESerializable.class,
                    "   Actual: " + item.getClass()
                )));
            }
        }
    }
}
