package eu.chakhouski.juepak;

import eu.chakhouski.juepak.annotations.APIBridgeMethod;
import eu.chakhouski.juepak.util.PakExtractor;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

public final class FFileIterator implements Iterator<FPakEntry>
{
    /** Owner pak file. */
    private final FPakFile PakFile;
    /** Index iterator. */
    private Iterator<Entry<String, Map<String, FPakEntry>>> IndexIt;
    /** Directory iterator. */
    private Iterator<Entry<String, FPakEntry>> DirectoryIt;

    // Cached iterator state
    private String CachedFilename;
    private String CachedDirectory;
    private FPakEntry CachedEntry;

    // Determines whether the hasNext() has been called
    private boolean JustWeaklyAdvanced = false;


    FFileIterator(FPakFile InPakFile)
    {
        PakFile = InPakFile;

        final Map<String, Map<String, FPakEntry>> Index = PakFile.GetIndex();
        IndexIt = Index.entrySet().iterator();

        DirectoryIt = Collections.emptyIterator();
    }

    private boolean Advance(boolean Weak)
    {
        // Return immediately advance is weak and if node is valid
        // called from hasNext() it prevents an iterator from being advanced once again
        if (Weak && JustWeaklyAdvanced)
        {
            return true;
        }

        // Try advance directory iterator
        if (DirectoryIt.hasNext())
        {
            DoAdvance();
            return true;
        }
        else
        {
            // Discard directory iterator, it is depleted
            DirectoryIt = null;

            // Try advance index iterator
            while (IndexIt.hasNext())
            {
                final Entry<String, Map<String, FPakEntry>> CurrentIndex = IndexIt.next();

                CachedDirectory = CurrentIndex.getKey();
                final Map<String, FPakEntry> PakEntries = CurrentIndex.getValue();

                DirectoryIt = PakEntries.entrySet().iterator();

                // Try advance directory iterator
                if (DirectoryIt.hasNext())
                {
                    DoAdvance();
                    return true;
                }
            }
        }

        return false;
    }

    private void DoAdvance()
    {
        final Entry<String, FPakEntry> DirectoryEntry = DirectoryIt.next();

        // Update cached Filename
        CachedFilename = CachedDirectory + DirectoryEntry.getKey();

        // Update cached Entry
        CachedEntry = DirectoryEntry.getValue();

        JustWeaklyAdvanced = true;
    }

    // *** Iterator interface ***
    @Override
    public final boolean hasNext()
    {
        return Advance(true);
    }

    @Override
    public final FPakEntry next()
    {
        // Check whether the iterator was weakly advanced with hasNext() method
        if (!JustWeaklyAdvanced)
        {
            // Added a variable to debug this code easily, i'm not quite sure if this is correct
            final boolean bStrongAdvanceFailed = !Advance(false);

            if (bStrongAdvanceFailed)
            {
                throw new NoSuchElementException("Iterator depleted");
            }
        }

        // Anyway, invalidate we
        JustWeaklyAdvanced = false;
        return PakEntry();
    }

    // *** Additional public API ***
    public final String Filename()
    {
        return CachedFilename;
    }

    public final FPakEntry PakEntry()
    {
        return CachedEntry;
    }

    // *** API bridge ***
    @APIBridgeMethod
    public void extractMixed(String RootPath) throws IOException
    {
        extractMixed(Paths.get(RootPath));
    }

    @APIBridgeMethod
    public void extractMixed(Path RootPath) throws IOException
    {
        final Path AbsolutePath = RootPath.resolve(Filename());
        final Path AbsoluteDir = AbsolutePath.getParent();

        // Create a directory if none yet
        if (!Files.isDirectory(AbsoluteDir))
        {
            Files.createDirectories(AbsoluteDir);
        }

        // Extract to file channel
        try (final FileOutputStream FileOS = new FileOutputStream(AbsolutePath.toFile()))
        {
            PakExtractor.Extract(PakFile, PakEntry(), Channels.newChannel(FileOS));
        }
    }

    @APIBridgeMethod
    public void extractToMemory(final byte[] buffer) throws IOException
    {
        extractToMemory(buffer, 0);
    }

    @APIBridgeMethod
    public void extractToMemory(final byte[] buffer, final int offset) throws IOException
    {
        PakExtractor.Extract(PakFile, PakEntry(), Channels.newChannel(new OutputStream() {
            int position = 0;

            @Override
            public void write(int b) { throw new IllegalStateException("Should not get here"); }

            @Override
            public void write(byte[] InBuffer, int InOffset, int InLength)
            {
                System.arraycopy(InBuffer, InOffset, buffer, offset + position, InLength);
                position += InLength;
            }
        }));
    }

//    private static class ShifterInputStream implements InputStream
//    {
//        private long BytesToRead = Entry.UncompressedSize;
//
//        @Override
//        public int read() throws IOException
//        {
//            return BytesToRead-- <= 0 ? -1 : PakFileStream.read();
//        }
//
//        @Override
//        public int read(byte[] b, int off, int len) throws IOException
//        {
//            final int BytesToCopy = Math.min(len, (int)BytesToRead);
//            if (BytesToCopy > 0)
//            {
//                if (BytesToCopy < (long) PakFileStream.read(b, off, len))
//                {
//                    throw new IOException("Can not read that much bytes from a particular file");
//                }
//
//                BytesToRead -= BytesToCopy;
//            }
//
//            return BytesToCopy;
//        }
//    }

//    public InputStream getInputStream() throws IOException
//    {
//        final FPakEntry Entry = PakEntry();
//        final FPakInfo Info = PakFile.Info;
//
//        final FileInputStream PakFileStream = PakFile.InputStream;
//        final FileChannel PakFileChannel = PakFileStream.getChannel();
//
//        final long SerializedSize = Entry.GetSerializedSize(Info.Version);
//
//        // Deserialize check entry once again
//        final FPakEntry CheckEntry = new FPakEntry();
//        CheckEntry.Deserialize(PakFileChannel.map(MapMode.READ_ONLY, Entry.Offset, SerializedSize), Info.Version);
//
//        // Check entry
//        if (!Entry.equals(CheckEntry))
//        {
//            throw new IllegalStateException("Invalid check entry");
//        }
//
//        // Set position
//        PakFileChannel.position(Entry.Offset + SerializedSize);
//
//        // Properties
//        final int EntryCompressionMethod = Entry.CompressionMethod;
//        final boolean bEntryIsEncrypted = BOOL(Entry.bEncrypted);
//        final boolean bEntryIsCompressed = EntryCompressionMethod != ECompressionFlags.COMPRESS_None;
//
//        // Create shifter stream
//        final InputStream shifterInputStream = new InputStream() {
//            long BytesToRead = Entry.UncompressedSize;
//
//            @Override
//            public int read() throws IOException
//            {
//                return BytesToRead-- <= 0 ? -1 : PakFileStream.read();
//            }
//
//            @Override
//            public int read(byte[] b, int off, int len) throws IOException
//            {
//                final int BytesToCopy = Math.min(len, (int)BytesToRead);
//                if (BytesToCopy > 0)
//                {
//                    if (BytesToCopy > PakFileStream.read(b, off, len))
//                        throw new IOException("Can not read that much bytes from a particular file");
//
//                    BytesToRead -= BytesToCopy;
//                }
//
//                return BytesToCopy;
//            }
//        };
//
//        final Cipher cipher;
//        try {
//            cipher = Cipher.getInstance("AES/ECB/NoPadding");
//
//
//
//            final byte[] SharedKeyBytes = new byte[32];
//            FCoreDelegates.GetPakEncryptionKeyDelegate().Execute(SharedKeyBytes);
//
//            final SecretKeySpec secretKeySpec = new SecretKeySpec(SharedKeyBytes, 0, SharedKeyBytes.length, "AES");
//            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
//        }
//        catch (GeneralSecurityException e) {
//            throw new SecurityException();
//        }
//
//
//
//        final CipherInputStream cipherInputStream = new CipherInputStream(shifterInputStream, cipher);
//        return cipherInputStream;
//    }
}
