package eu.chakhouski.juepak;

import eu.chakhouski.juepak.pak.FFileIterator;
import eu.chakhouski.juepak.pak.FPakFile;
import eu.chakhouski.juepak.pak.FPakInfo;
import eu.chakhouski.juepak.ue4.FCoreDelegates;
import eu.chakhouski.juepak.util.Packer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

public class Main
{
    private static final String extractDirectory = "C:\\Users\\ASUS\\Desktop\\Extract";
    private static final String packingDirectory = "C:\\Users\\ASUS\\Pictures";

    private static final Path archiveFile = Paths.get("C:\\Users\\ASUS\\Desktop\\Archive.pak");




    public static void main(String[] args) throws Exception
    {
        FCoreDelegates.GetPakEncryptionKeyDelegate().BindLambda(bytes ->
        {
            final byte[] decode = Base64.getDecoder().decode("55K1xvTGDiR9Sz1lQtY/eCDOIIHvsVyIg1WGXRvUh58=");
            System.arraycopy(decode, 0, bytes, 0, bytes.length);
        });

        // Prepare packer
        final Packer packer = Packer.builder()
                .encryptIndex(false)
                .encryptContent(false)
                .compressContent(true)
                .pakVersion(FPakInfo.PakFile_Version_Latest - 2)
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
            packer.add(path);

        packer.close();

        // Read (unpack)
        try (final FPakFile fPakFile = new FPakFile(archiveFile.toString()))
        {
            for (final FPakFile.Entry entry : fPakFile)
            {
//                final FPakEntry e = iterator.next();
//                iterator.extractMixed(extractDirectory);

                System.out.println(entry.toString());
            }
        }
    }
}
