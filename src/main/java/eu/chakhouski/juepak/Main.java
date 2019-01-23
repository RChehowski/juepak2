package eu.chakhouski.juepak;

import eu.chakhouski.juepak.pak.FPakFile;
import eu.chakhouski.juepak.ue4.FCoreDelegates;
import eu.chakhouski.juepak.util.Packer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Main
{
    private static final String extractDirectory = "/Users/netherwire/Desktop/Extract";
    private static final String packingDirectory = "/Users/netherwire/Projects/MyUnrealProject/Config";


    private static final Path archiveFile = Paths.get("/Users/netherwire/Desktop/Archive.pak");


    public static void main(String[] args) throws Exception
    {
        FCoreDelegates.GetPakEncryptionKeyDelegate().BindLambda(new Consumer<byte[]>()
        {
            private final byte[] decode = Base64.getDecoder().decode("55K1xvTGDiR9Sz1lQtY/eCDOIIHvsVyIg1WGXRvUh58=");

            @Override
            public void accept(byte[] bytes)
            {
                System.arraycopy(decode, 0, bytes, 0, bytes.length);
            }
        });


        compressDecompress();
    }

    private static void compressDecompress() throws IOException
    {
        // Prepare packer
        final Packer packer = Packer.builder()
                .encryptIndex(true)
                .engineVersion("4.20")
                .customMountPoint("../../../")
                .archiveFile(archiveFile)
                .build();

        // Packing
        final Path folder = Paths.get(packingDirectory);

        final List<Path> pathsToPack = Files.walk(folder)
                .filter(Files::isRegularFile)
                .collect(Collectors.toList());

        // Add files to pack
        for (Path path : pathsToPack)
            packer.add(path, new Packer.PackParameters().compress());


        packer.addProgressListener(p -> System.out.println("Progress is: " + p));
        packer.close();

        // Read (unpack)
        try (final FPakFile fPakFile = new FPakFile(archiveFile.toString()))
        {
            System.out.println(fPakFile);

            for (final FPakFile.Entry entry : fPakFile)
            {
//                final FPakEntry e = iterator.next();
                entry.extractMixed(extractDirectory, value -> {
                    System.out.println("Extracting " + entry.Filename + " progress: " + value);
                });

                System.out.println(entry.toString());
            }
        }
    }
}
