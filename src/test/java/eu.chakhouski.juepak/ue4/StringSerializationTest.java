package eu.chakhouski.juepak.ue4;

import eu.chakhouski.juepak.util.UE4Deserializer;
import eu.chakhouski.juepak.util.UE4Serializer;
import org.junit.Test;
import org.testng.Assert;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static java.util.Arrays.stream;

public class StringSerializationTest
{
    @Test
    public void simpleTest() throws Exception
    {
        final int VERY_LONG_STRING_LENGTH = 4096;
        
        final StringBuilder veryLongEnglishString = new StringBuilder(VERY_LONG_STRING_LENGTH);
        for (int i = 0; i < VERY_LONG_STRING_LENGTH; i++)
            veryLongEnglishString.append('a');

        final StringBuilder veryLongRussianString = new StringBuilder(VERY_LONG_STRING_LENGTH);
        for (int i = 0; i < VERY_LONG_STRING_LENGTH; i++)
            veryLongRussianString.append('я');

        final StringBuilder veryLongNumericString = new StringBuilder(VERY_LONG_STRING_LENGTH);
        for (int i = 0; i < VERY_LONG_STRING_LENGTH; i++)
            veryLongNumericString.append('1');

        final String[] samples = {
            // Latin
            "hello, world",
            "HELLO, WORLD",
            "Hello, World",
            "Hello, World!",
            "Hello, #World!",
            "Hello, @#$%^&!",

            // Cyrillic
            "привет, мир",
            "ПРИВЕТ, МИР",
            "Привет, Мир",
            "Привет, Мир!",
            "Привет, #Мир!",
            "Привет, @#$%^&!",

            // Latin-cyrillic variations
            "hello, мир",
            "HELLO, МИР",
            "Hello, Мир",
            "Hello, Мир!",
            "Hello, #Мир!",
            "Hello, @#$%^&(мир)!",

            // Empty string
            "",

            // Numeric variations
            "123",
            "aaa123",
            "123aaa",
            "яяя123",
            "123яяя",
            "#$%123",
            "123#$%",

            // Very long strings
            veryLongEnglishString.toString(),
            veryLongRussianString.toString(),
            veryLongNumericString.toString()
        };

        // Calculate a precise serialize capacity, this is also tested
        final int preciseCapacity = stream(samples)
                .mapToInt(UE4Serializer::GetPreciseStringEncodeLength)
                .reduce((l, r) -> l + r)
                .getAsInt();

        final ByteBuffer buffer = ByteBuffer.allocateDirect(preciseCapacity).order(ByteOrder.LITTLE_ENDIAN);

        for (final String s : samples)
            UE4Serializer.Write(buffer, s);

        // Assert the whole buffer is used
        Assert.assertEquals(buffer.position(), buffer.capacity(), "The whole buffer must be used");

        // Rewind before read
        buffer.rewind();

        for (String srcSample : samples)
        {
            Assert.assertEquals(UE4Deserializer.Read(buffer, String.class), srcSample);
        }
    }
}
