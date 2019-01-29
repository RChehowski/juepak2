package com.vizor.unreal;

import com.vizor.unreal.packer.Packer;
import com.vizor.unreal.packer.PakEntryParameters;
import com.vizor.unreal.pak.FPakFile;
import com.vizor.unreal.pak.PakIteratorEntry;
import com.vizor.unreal.ue4.FCoreDelegates;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

public class Main
{
    private static final String extractDirectory = "/Users/netherwire/Desktop/Extract";
    private static final String packingDirectory = "/Volumes/Samsung/Projects/UnrealEngine/Engine/Build";
    private static final Path archiveFile = Paths.get("/Users/netherwire/Desktop/Archive.pak");


    public static void main(String[] args) throws Exception
    {
        FCoreDelegates.GetPakEncryptionKeyDelegate().BindLambda(bytes -> {
            final byte[] decode = Base64.getDecoder().decode("55K1xvTGDiR9Sz1lQtY/eCDOIIHvsVyIg1WGXRvUh58=");
            System.arraycopy(decode, 0, bytes, 0, bytes.length);
        });

        compressDecompress();
    }

    private static void compressDecompress() throws IOException
    {
        // Prepare packer
        final Packer packer = Packer.builder()
                .encryptIndex(false)
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
            packer.add(path, new PakEntryParameters().compress().encrypt());


        packer.addProgressListener(p -> System.out.println("Packing: " + p * 100.0f + "%"));
        packer.close();

        // Read (unpack)
        try (final FPakFile fPakFile = new FPakFile(archiveFile))
        {
            for (PakIteratorEntry entry : fPakFile) {
                entry.extractMixed(extractDirectory, x -> System.out.println("Extracting: " + x * 100.0f + "%"));
            }
        }
    }
}
