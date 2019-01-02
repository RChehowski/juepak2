package eu.chakhouski.juepak.util;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;

import static eu.chakhouski.juepak.util.Sizeof.sizeof;
import static java.lang.String.join;
import static java.lang.System.lineSeparator;
import static java.util.Arrays.asList;

public class UE4Serializer
{
    private static final CharsetEncoder asciiEncoder = StandardCharsets.US_ASCII.newEncoder();
    private static final CharsetEncoder utf16Encoder = StandardCharsets.UTF_16LE.newEncoder();


    private static final int ENCODE_BUFFER_SIZE = 255;

    private static final CharBuffer srcEncodeBuffer = CharBuffer.allocate(ENCODE_BUFFER_SIZE);
    private static final ByteBuffer dstEncodeBuffer = ByteBuffer.allocate(ENCODE_BUFFER_SIZE * Character.BYTES)
            .order(ByteOrder.nativeOrder());


    public static int GetPreciseStringEncodeLength(String s)
    {
        int length = 0;

        // 1. Length of string is an integer
        length += sizeof(int.class);

        // Find encoder
        final CharsetEncoder encoder;
        if (asciiEncoder.canEncode(s))
            encoder = asciiEncoder;
        else if (utf16Encoder.canEncode(s))
            encoder = utf16Encoder;
        else
            throw new IllegalArgumentException("Can not encode \"" + s + "\"");

        // 2. Content bytes
        length += (s.length() * (int)encoder.maxBytesPerChar());

        // 3. 0-terminator bytes
        length += (int)encoder.maxBytesPerChar();

        return length;
    }

    public static void WriteString(ByteBuffer b, String s)
    {
        // Setup byte order
        b.order(ByteOrder.LITTLE_ENDIAN);

        // Here later we'll write a string length
        final int stringLengthPosition = b.position();
        b.putInt(0); // Reserve 4 bytes for length

        // Write encoded content
        final CharsetEncoder encoder;
        if (doEncodeAndWrite(asciiEncoder, b, s))
        {
            encoder = asciiEncoder;
        }
        else
        {
            if (doEncodeAndWrite(utf16Encoder, b, s))
                encoder = utf16Encoder;
            else
                throw new IllegalArgumentException("No UE4-supported encoder can encode \"" + s + "\"");
        }

        final int contentFinishPosition = b.position();

        // Write a length into stringLengthPosition
        b.position(stringLengthPosition);

        // 'bytesPerChar' extra char for '\0' (null-terminator)


        // Write length
        if (encoder == utf16Encoder)
            b.putInt(-(s.length() + /* 0-terminator */1));
        else
            b.putInt(s.length() + /* 0-terminator */1);

        b.position(contentFinishPosition);

        // Add 'bytesPerChar' of 0-terminator bytes to the end (1 for ASCII, 2 for UTF-16)
        final int bytesPerChar = (int) encoder.maxBytesPerChar();
        for (int i = 0; i < bytesPerChar; i++)
        {
            b.put((byte) 0);
        }
    }

    private static boolean doEncodeAndWrite(CharsetEncoder encoder, ByteBuffer b, String s)
    {
        final int initialPosition = b.position();

        encoder.reset();

        // Encode step by step
        final int stringLength = s.length();
        for (int i = 0; i < stringLength;)
        {
            srcEncodeBuffer.rewind().limit(srcEncodeBuffer.capacity());

            while (i < stringLength && srcEncodeBuffer.hasRemaining())
                srcEncodeBuffer.put(s.charAt(i++));

            srcEncodeBuffer.flip();
            dstEncodeBuffer.rewind().limit(dstEncodeBuffer.capacity());

            final boolean endOfInput = i >= stringLength;
            final CoderResult result = encoder.encode(srcEncodeBuffer, dstEncodeBuffer, endOfInput);

            if (result.isError())
            {
                b.position(initialPosition);
                return false;
            }

            dstEncodeBuffer.flip();
            b.put(dstEncodeBuffer);
        }

        return true;
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
