package eu.chakhouski.juepak;

import eu.chakhouski.juepak.pak.FPakEntry;
import eu.chakhouski.juepak.pak.FPakFile;
import eu.chakhouski.juepak.ue4.FAES;
import eu.chakhouski.juepak.ue4.FMath;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static eu.chakhouski.juepak.ECompressionFlags.COMPRESS_None;
import static eu.chakhouski.juepak.ue4.AlignmentTemplates.Align;
import static eu.chakhouski.juepak.util.Misc.TEXT;
import static eu.chakhouski.juepak.util.Misc.checkf;

public class PackFactory
{
    // Utility function to extract a pak entry out of the memory reader containing the pak file and place in the destination archive.
    // Uses Buffer or PersistentCompressionBuffer depending on whether the entry is compressed or not.
    public static void ExtractFile(
            final FPakEntry Entry,
            InputStream PakReader,
            byte[] Buffer,
            byte[] PersistentCompressionBuffer,
            OutputStream DestAr,
            final FPakFile PakFile)
        throws IOException
    {
        if (Entry.CompressionMethod == COMPRESS_None)
        {
            PackFactory.BufferedCopyFile(DestAr, PakReader, Entry, Buffer);
        }
        else
        {
            PackFactory.UncompressCopyFile(DestAr, PakReader, Entry, PersistentCompressionBuffer, PakFile);
        }
    }

    // Utility function to copy a single pak entry out of the Source archive and in to the Destination archive using Buffer as temporary space
    private static boolean BufferedCopyFile(OutputStream DestAr, InputStream Source, final FPakEntry Entry, byte[] Buffer)
        throws IOException
    {
        // Align down
		final long BufferSize = Buffer.length & ~(FAES.AESBlockSize-1);
        long RemainingSizeToCopy = Entry.Size;
        while (RemainingSizeToCopy > 0)
        {
			final long SizeToCopy = (int)FMath.Min(BufferSize, RemainingSizeToCopy);
            // If file is encrypted so we need to account for padding
            long SizeToRead = Entry.IsEncrypted() ? Align(SizeToCopy, FAES.AESBlockSize) : SizeToCopy;

            assert Source.read(Buffer, 0, (int)SizeToRead) == SizeToRead;
            if (Entry.IsEncrypted())
            {
                FAES.FAESKey Key = new FAES.FAESKey();
                FPakFile.GetPakEncryptionKey(Key);
                checkf(Key.IsValid(), TEXT("Trying to copy an encrypted file between pak files, but no decryption key is available"));
                FAES.DecryptData(Buffer, (int)SizeToRead, Key);
            }

            DestAr.write(Buffer, 0, (int)SizeToCopy);
            RemainingSizeToCopy -= SizeToRead;
        }
        return true;
    }

    // Utility function to uncompress and copy a single pak entry out of the Source archive and in to the Destination archive using PersistentBuffer as temporary space
    private static boolean UncompressCopyFile(OutputStream DestAr, InputStream Source, final FPakEntry Entry, byte[] PersistentBuffer, final FPakFile PakFile)
    {
//        if (Entry.UncompressedSize == 0)
//        {
//            return false;
//        }
//
//        long WorkingSize = Entry.CompressionBlockSize;
//        int MaxCompressionBlockSize = FCompression::CompressMemoryBound(Entry.CompressionMethod, WorkingSize, FPlatformMisc::GetPlatformCompression()->GetCompressionBitWindow());
//        WorkingSize += MaxCompressionBlockSize;
//        if (PersistentBuffer.length < WorkingSize)
//        {
//            PersistentBuffer.SetNumUninitialized(WorkingSize);
//        }
//
//        uint8* UncompressedBuffer = PersistentBuffer.GetData() + MaxCompressionBlockSize;
//
//        for (int BlockIndex=0, BlockIndexNum=Entry.CompressionBlocks.length; BlockIndex < BlockIndexNum; ++BlockIndex)
//        {
//            long CompressedBlockSize = Entry.CompressionBlocks[BlockIndex].CompressedEnd - Entry.CompressionBlocks[BlockIndex].CompressedStart;
//            long UncompressedBlockSize = FMath.Min(Entry.UncompressedSize - Entry.CompressionBlockSize*BlockIndex, Entry.CompressionBlockSize);
//            Source.Seek(Entry.CompressionBlocks[BlockIndex].CompressedStart + (PakFile.GetInfo().HasRelativeCompressedChunkOffsets() ? Entry.Offset : 0));
//            long SizeToRead = BOOL(Entry.Flags) ? Align(CompressedBlockSize, FAES.AESBlockSize) : CompressedBlockSize;
//            Source.Serialize(PersistentBuffer.GetData(), SizeToRead);
//
//            if (BOOL(Entry.Flags))
//            {
//                FAES.FAESKey Key = new FAES.FAESKey();
//                FPakFile.GetPakEncryptionKey(Key);
//                checkf(Key.IsValid(), TEXT("Trying to copy an encrypted file between pak files, but no decryption key is available"));
//                FAES.DecryptData(PersistentBuffer.GetData(), SizeToRead, Key);
//            }
//
//            if(!FCompression::UncompressMemory(Entry.CompressionMethod,UncompressedBuffer,UncompressedBlockSize,PersistentBuffer.GetData(),CompressedBlockSize, false, FPlatformMisc::GetPlatformCompression()->GetCompressionBitWindow()))
//            {
//                return false;
//            }
//            DestAr.Serialize(UncompressedBuffer,UncompressedBlockSize);
//        }

        return true;
    }
}
