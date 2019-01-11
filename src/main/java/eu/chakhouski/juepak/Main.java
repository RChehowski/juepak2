package eu.chakhouski.juepak;

import eu.chakhouski.juepak.pak.FFileIterator;
import eu.chakhouski.juepak.pak.FPakEntry;
import eu.chakhouski.juepak.pak.FPakFile;
import eu.chakhouski.juepak.pak.FPakInfo;
import eu.chakhouski.juepak.ue4.FCoreDelegates;
import eu.chakhouski.juepak.util.Packer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public class Main
{
    private static final String extractDirectory = "/Users/netherwire/Desktop/Extract";
    private static final String packingDirectory = "/Users/netherwire/Pictures";

    private static final Path archiveFile = Paths.get("/Users/netherwire/Desktop/Example.umap");



    private static List<File> deflateFile(InputStream is, boolean bEncrypt, int MaxCompressionBlockSize)
            throws IOException
    {
        final byte[] buffer = new byte[1024];


        final List<File> tempFiles = new ArrayList<>();
        final Deflater deflater = new Deflater();

        try {
            boolean readIsDone = false;

            long bytesReadTotal = 0;
            long bytesWrittenTotal = 0;

            while (!readIsDone)
            {
                final File tempFile = File.createTempFile("pak_temp_", ".chunk");

                int bytesWrittenPerBlock = 0;
                int bytesReadPerBlock = 0;

                try (final FileOutputStream fos = new FileOutputStream(tempFile))
                {
                    final OutputStream dos = new DeflaterOutputStream(fos, deflater, true);

                    int bytesReadPerTransmission;
                    while ((bytesWrittenPerBlock < MaxCompressionBlockSize - buffer.length) && ((bytesReadPerTransmission = is.read(buffer)) > 0))
                    {
                        bytesReadPerBlock += bytesReadPerTransmission;


                        final long p1 = deflater.getBytesWritten();

                        dos.write(buffer, 0, bytesReadPerTransmission);
                        dos.flush();

                        final long p2 = deflater.getBytesWritten();

                        bytesWrittenPerBlock += (p2 - p1);
                    }

                    if (bytesWrittenPerBlock >= MaxCompressionBlockSize)
                    {
                        throw new IllegalStateException("Too huge block: " + bytesWrittenPerBlock + " bytes, allowed: " +
                                MaxCompressionBlockSize);
                    }
                }

                bytesReadTotal += bytesReadPerBlock;
                bytesWrittenTotal += bytesWrittenPerBlock;

                if (bytesReadPerBlock == 0)
                {
                    tempFile.delete();
                    readIsDone = true;
                }
                else
                {
                    tempFiles.add(tempFile);
                }
            }


            System.out.println("Compressing done. Read: " + bytesReadTotal + ", Written: " + bytesWrittenTotal +
                    ", Chunks: " + tempFiles.size() + "\n")
//                    "Size " + () + " by " + );
            
        }
        catch (RuntimeException | IOException e)
        {
            for (File tempFile : tempFiles)
                tempFile.delete();

            tempFiles.clear();

            // Rethrow
            throw e;
        }

        return tempFiles;
    }



    public static void main(String[] args) throws Exception
    {
        final List<File> files = deflateFile(new FileInputStream(archiveFile.toFile()), false, 64 * 1024);

        for (File file : files)
        {
            System.out.println(file.toString() + " " + file.length());
            file.delete();
        }



        FCoreDelegates.GetPakEncryptionKeyDelegate().BindLambda(bytes ->
        {
            final byte[] decode = Base64.getDecoder().decode("55K1xvTGDiR9Sz1lQtY/eCDOIIHvsVyIg1WGXRvUh58=");
            System.arraycopy(decode, 0, bytes, 0, bytes.length);
        });

        // Prepare packer
        final Packer packer = Packer.builder()
                .encryptIndex(false)
                .encryptContent(true)
                .compressContent(true)
                .pakVersion(FPakInfo.PakFile_Version_RelativeChunkOffsets)
                .build();

        // Packing
        final Path folder = Paths.get(packingDirectory);

        final List<Path> pathsToPack = Files.walk(folder)
                .filter(Files::isRegularFile)
                .collect(Collectors.toList());

        // Add files to pack
        for (Path path : pathsToPack)
            packer.add(path);

        packer.closeAndWrite(archiveFile);

        // Read (unpack)
        try (final FPakFile fPakFile = new FPakFile(archiveFile.toString()))
        {
            for (FFileIterator iterator = fPakFile.iterator(); iterator.hasNext(); )
            {
                final FPakEntry e = iterator.next();
                iterator.extractMixed(extractDirectory);
            }
        }
    }
}
