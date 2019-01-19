package eu.chakhouski.juepak.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;

import static eu.chakhouski.juepak.util.Sizeof.sizeof;

public class UE4Serializer
{
    private static final CharsetEncoder asciiEncoder = StandardCharsets.US_ASCII.newEncoder();
    private static final CharsetEncoder utf16Encoder = StandardCharsets.UTF_16LE.newEncoder();

    /**
     * Initial encoding buffer size (in characters), long string will be encoded divided by chunks.
     */
    private static final int ENCODE_BUFFER_SIZE = 255;

    private static final CharBuffer srcEncodeBuffer = CharBuffer.allocate(ENCODE_BUFFER_SIZE);
    private static final ByteBuffer dstEncodeBuffer = ByteBuffer.allocate(ENCODE_BUFFER_SIZE * Character.BYTES)
            .order(ByteOrder.nativeOrder());

    /**
     * Determines the exact number of bytes that can contain a certain string. Takes all possible overheads
     * into account.
     * NOTE: This method is synchronized as it uses shared encoders.
     *
     * @param s A string, which serialization size is about to be determined.
     * @return Number of bytes.
     */
    public synchronized static int GetSerializeSize(String s)
    {
        int length = 0;

        // 1. Length of string is an integer
        length += sizeof(int.class);

        asciiEncoder.reset();

        // Find encoder
        final CharsetEncoder encoder;
        if (asciiEncoder.canEncode(s))
            encoder = asciiEncoder;
        else
        {
            utf16Encoder.reset();

            if (utf16Encoder.canEncode(s))
                encoder = utf16Encoder;
            else
                throw new IllegalArgumentException("Can not encode \"" + s + "\"");
        }

        // 2. Content bytes
        length += (s.length() * (int)encoder.maxBytesPerChar());

        // 3. 0-terminator bytes
        length += (int)encoder.maxBytesPerChar();

        return length;
    }

    public static void Write(ByteBuffer b, byte value)
    {
        // Setup byte order
        b.order(ByteOrder.LITTLE_ENDIAN);

        // Put data
        b.put(value);
    }

    public static void Write(ByteBuffer b, int value)
    {
        // Setup byte order
        b.order(ByteOrder.LITTLE_ENDIAN);

        // Put data
        b.putInt(value);
    }

    public static void Write(ByteBuffer b, long value)
    {
        // Setup byte order
        b.order(ByteOrder.LITTLE_ENDIAN);

        // Put data
        b.putLong(value);
    }

    public static synchronized void Write(ByteBuffer b, String value)
    {
        // Setup byte order
        b.order(ByteOrder.LITTLE_ENDIAN);

        // Here later we'll write a string length
        final int stringLengthPosition = b.position();
        b.putInt(0); // Reserve 4 bytes for length

        // Write encoded content
        final CharsetEncoder encoder;
        if (doEncodeAndWrite(asciiEncoder, b, value))
        {
            encoder = asciiEncoder;
        }
        else
        {
            if (doEncodeAndWrite(utf16Encoder, b, value))
                encoder = utf16Encoder;
            else
                throw new IllegalArgumentException("No UE4-supported encoder can encode \"" + value + "\"");
        }

        final int contentFinishPosition = b.position();

        // Write a length into stringLengthPosition
        b.position(stringLengthPosition);

        // 'bytesPerChar' extra char for '\0' (null-terminator)


        // Write length
        if (encoder == utf16Encoder)
            b.putInt(-(value.length() + /* 0-terminator */1));
        else
            b.putInt(value.length() + /* 0-terminator */1);

        b.position(contentFinishPosition);

        // Add 'bytesPerChar' of 0-terminator bytes to the end (1 for ASCII, 2 for UTF-16)
        final int bytesPerChar = (int) encoder.maxBytesPerChar();
        for (int i = 0; i < bytesPerChar; i++)
        {
            b.put((byte) 0);
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static <T extends UE4Serializable> void Write(ByteBuffer b, Collection<T> items)
    {
        if (items != null)
        {
            b.order(ByteOrder.LITTLE_ENDIAN);

            // Serialize array size
            b.putInt(items.size());

            for (final UE4Serializable item : items)
            {
                if (item != null)
                    item.Serialize(b);
            }
        }
    }

    public static void Write(ByteBuffer b, UE4Serializable... items)
    {
        Write(b, Arrays.asList(items));
    }

    public static void Write(ByteBuffer b, byte... bytes)
    {
        if (bytes != null)
        {
            b.order(ByteOrder.LITTLE_ENDIAN);
            b.put(bytes);
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
}
