package eu.chakhouski.juepak;

import eu.chakhouski.juepak.pak.FFileIterator;
import eu.chakhouski.juepak.pak.FPakEntry;
import eu.chakhouski.juepak.pak.FPakFile;
import eu.chakhouski.juepak.pak.FPakInfo;
import eu.chakhouski.juepak.ue4.FCoreDelegates;
import eu.chakhouski.juepak.util.Packer;
import eu.chakhouski.juepak.util.UE4Serializer;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

public class Main
{
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
                .pakVersion(FPakInfo.PakFile_Version_RelativeChunkOffsets)
                .savePath(Paths.get("/Users/netherwire/Desktop/Created.pak"))
                .build();


        // Packing
        final Path folder = Paths.get("/Volumes/Samsung/Projects/UnrealEngine/FeaturePacks");
        final List<Path> pathsToPack = Files.walk(folder).filter(Files::isRegularFile).collect(Collectors.toList());

        for (Path path : pathsToPack)
        {
            packer.add(path);
        }
        packer.close();


        // Read (unpack)
        try (final FPakFile fPakFile = new FPakFile("/Users/netherwire/Desktop/Created.pak"))
        {
            for (FFileIterator iterator = fPakFile.iterator(); iterator.hasNext(); )
            {
                FPakEntry e = iterator.next();
                System.out.println(iterator.toString());

                iterator.extractMixed("/Users/netherwire/Desktop/Extract");
            }
        }
    }
}
