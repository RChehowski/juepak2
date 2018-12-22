package eu.chakhouski.juepak.util;

import eu.chakhouski.juepak.ECompressionFlags;
import eu.chakhouski.juepak.FPakCompressedBlock;
import eu.chakhouski.juepak.FPakEntry;
import eu.chakhouski.juepak.FPakFile;
import eu.chakhouski.juepak.FPakInfo;
import eu.chakhouski.juepak.ue4.FAES;
import eu.chakhouski.juepak.ue4.FCoreDelegates;
import eu.chakhouski.juepak.ue4.FMemory;
import eu.chakhouski.juepak.ue4.FSHA1;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.channels.WritableByteChannel;
import java.util.List;

import static eu.chakhouski.juepak.util.Misc.BOOL;
import static eu.chakhouski.juepak.util.Sizeof.sizeof;

public class PakExtractor
{
    private static final FPakEntry CheckEntry = new FPakEntry();

    private static final ByteBuffer TempBuffer = ByteBuffer.allocate(32 * 1024).order(ByteOrder.LITTLE_ENDIAN);
    private static final byte[] SharedKeyBytes = new byte[32];


    public synchronized static void Extract(FPakFile PakFile, FPakEntry Entry, WritableByteChannel ExtractionTarget)
            throws IOException
    {
        final FPakInfo PakInfo = PakFile.Info;
        final FileInputStream PakInputStream = PakFile.InputStream;

        // Might use cached channel if any has already created, this must be stable
        final FileChannel Channel = PakInputStream.getChannel();

        // Deserialize header once again
        final long EntrySerializedSize = Entry.GetSerializedSize(PakInfo.Version);
        CheckEntry.Deserialize(Channel.map(MapMode.READ_ONLY, Entry.Offset, EntrySerializedSize), PakInfo.Version);

        // Compare entries
        if (!Entry.equals(CheckEntry))
        {
            throw new RuntimeException("Entry is invalid\n  Expected: " + Entry + "\n  Got: " + CheckEntry);
        }

        // Extract if not matching
        final long EntryDataOffset = Entry.Offset + EntrySerializedSize;
        final long EntryUncompressedSize = Entry.UncompressedSize;

        // Check if an entry is encrypted
        if (BOOL(Entry.bEncrypted))
        {
            // Check if an entry is compressed
            if (Entry.CompressionMethod == ECompressionFlags.COMPRESS_None)
            {
                FCoreDelegates.GetPakEncryptionKeyDelegate().Execute(SharedKeyBytes);

                // Reset limit
                TempBuffer.limit(TempBuffer.capacity());

                int bytesReadPerTransmission;

                // Read into buffer
                while ((bytesReadPerTransmission = Channel.read(((ByteBuffer) TempBuffer.position(0)))) != 0)
                {
                    FAES.DecryptData(TempBuffer.array(), bytesReadPerTransmission, SharedKeyBytes);

                    // Enable limit if we've read less than full buffer size
                    if (bytesReadPerTransmission != TempBuffer.capacity())
                    {
                        TempBuffer.limit(bytesReadPerTransmission);
                    }

                    // Write into target, setting position to beginning.
                    ExtractionTarget.write(((ByteBuffer) TempBuffer.position(0)));
                }

                // Erase password bytes
                FMemory.Memset(SharedKeyBytes, 0, sizeof(SharedKeyBytes));
            }
//            else
//            {
//                final FPakCompressedBlock[] compressionBlocks = Entry.CompressionBlocks;
//
//                throw new UnsupportedOperationException("Compressed extraction is not supported: " +
//                        ECompressionFlags.StaticToString(Entry.CompressionMethod));
//            }
        }
        else
        {
            // Check if an entry is compressed
            if (Entry.CompressionMethod == ECompressionFlags.COMPRESS_None)
            {
                Channel.transferTo(EntryDataOffset, EntryUncompressedSize, ExtractionTarget);
            }
//            else
//            {
//                final FPakCompressedBlock[] compressionBlocks = Entry.CompressionBlocks;
//
//                throw new UnsupportedOperationException("Compressed extraction is not supported: " +
//                        ECompressionFlags.StaticToString(Entry.CompressionMethod));
//            }
        }
    }
}
