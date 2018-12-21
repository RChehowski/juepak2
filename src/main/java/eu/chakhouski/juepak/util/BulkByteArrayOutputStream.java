package eu.chakhouski.juepak.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static java.lang.System.lineSeparator;
import static java.util.Arrays.asList;

/**
 * Bulk byte array stream, backing incoming bytes to a byte array. It can not expand, throwing a
 * {@link ArrayIndexOutOfBoundsException} if capacity has ben exceeded.
 * Unlike {@link ByteArrayOutputStream} it's {@link #toByteArray()} method does not produce
 */
public class BulkByteArrayOutputStream extends ByteArrayOutputStream
{
    public BulkByteArrayOutputStream()
    {
        super();
    }

    public BulkByteArrayOutputStream(int size)
    {
        super(size);
    }

    public BulkByteArrayOutputStream(byte[] backBuffer)
    {
        buf = backBuffer;
    }


    @Override
    public synchronized byte[] toByteArray()
    {
        return buf;
    }

    @Override
    public synchronized void write(int b)
    {
        ensureCapacity(count + 1);
        super.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException
    {
        ensureCapacity(b.length);
        super.write(b);
    }

    @Override
    public synchronized void write(byte[] b, int off, int len)
    {
        ensureCapacity(len);
        super.write(b, off, len);
    }


    private void ensureCapacity(int requiredCapacity)
    {
        if (requiredCapacity >= buf.length)
        {
            final String exceptionText = String.join(lineSeparator(), asList(
                    "Bulk buffer can not take that much data:",
                    "   Required: " + requiredCapacity,
                    "   Actual capacity: " + buf.length
            ));

            throw new ArrayIndexOutOfBoundsException(exceptionText);
        }
    }
}
