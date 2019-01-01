package eu.chakhouski.juepak.util;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

import static java.lang.String.join;
import static java.lang.System.lineSeparator;
import static java.util.Arrays.asList;

public class UE4Serializer
{
    @SuppressWarnings("ForLoopReplaceableByForEach")
    public static void WriteStructArray(ByteBuffer b, Object[] array)
    {
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
