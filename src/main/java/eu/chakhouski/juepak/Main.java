package eu.chakhouski.juepak;

import eu.chakhouski.juepak.pak.FPakFile;
import eu.chakhouski.juepak.ue4.FCoreDelegates;
import eu.chakhouski.juepak.ue4.PakVersion;
import eu.chakhouski.juepak.util.Packer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

public class Main
{
    private static final String extractDirectory = "/Users/netherwire/Desktop/Extract";
    private static final String packingDirectory = "/Volumes/Samsung/Projects/UnrealEngine/Samples/StarterContent/Content/StarterContent/Maps";


    private static final Path archiveFile = Paths.get("/Users/netherwire/Desktop/Archive.pak");


    public static void main(String[] args) throws Exception
    {
//        final int test = PakVersion.getByEngineVersion("4.16");
//        System.out.println(test);


        FCoreDelegates.GetPakEncryptionKeyDelegate().BindLambda(bytes ->
        {
            final byte[] decode = Base64.getDecoder().decode("55K1xvTGDiR9Sz1lQtY/eCDOIIHvsVyIg1WGXRvUh58=");
            System.arraycopy(decode, 0, bytes, 0, bytes.length);
        });

        // Prepare packer
        final Packer packer = Packer.builder()
                .encryptIndex(true)
                .engineVersion("3.20")
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
            packer.add(path, new Packer.PackParameters().compress().encrypt());


        packer.addProgressListener(p -> System.out.println("Progress is: " + p));

        packer.close();

        // Read (unpack)
        try (final FPakFile fPakFile = new FPakFile(archiveFile.toString()))
        {
            System.out.println(fPakFile);

            for (final FPakFile.Entry entry : fPakFile)
            {
//                final FPakEntry e = iterator.next();
                entry.extractMixed(extractDirectory);

                System.out.println(entry.toString());
            }
        }
    }
}
