package eu.chakhouski.juepak;

import eu.chakhouski.juepak.pak.FFileIterator;
import eu.chakhouski.juepak.pak.FPakEntry;
import eu.chakhouski.juepak.pak.FPakFile;
import eu.chakhouski.juepak.pak.FPakInfo;
import eu.chakhouski.juepak.ue4.FCoreDelegates;
import eu.chakhouski.juepak.util.PakCreator;
import eu.chakhouski.juepak.pak.packing.FPakInputPair;
import eu.chakhouski.juepak.util.UE4Serializer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

public class Main
{
    public static void main(String[] args) throws Exception
    {
        final byte[] bytes = "MANIFEST".getBytes(StandardCharsets.US_ASCII);

        FCoreDelegates.GetPakEncryptionKeyDelegate().BindLambda(new Consumer<byte[]>()
        {
            @Override
            public void accept(byte[] bytes)
            {
                final byte[] decode = Base64.getDecoder().decode("55K1xvTGDiR9Sz1lQtY/eCDOIIHvsVyIg1WGXRvUh58=");

                System.arraycopy(decode, 0, bytes, 0, bytes.length);
            }
        });


        final ByteBuffer allocate = ByteBuffer.allocate(100);
        UE4Serializer.WriteString(allocate, "abc");


        final String createdPak = "C:\\Users\\ASUS\\Desktop\\Sample.pak";
        try (final PakCreator pakCreator = new PakCreator(createdPak, FPakInfo.PakFile_Version_Latest))
        {
            final List<FPakInputPair> pairs = new ArrayList<>();

            final Path folder = Paths.get("C:\\Users\\ASUS\\Desktop\\Extract");

            Files.walk(folder)
                .filter(Files::isRegularFile)
                .forEach(path -> {
//                    final Path dest = folder.relativize(path);
//                    System.out.println(path.toString() + " -> " + dest.toString());

                    final String srcPath = path.toString().replaceAll("\\\\", "/");
                    final String dstPath = path.toString().replaceAll("\\\\", "/");

                    final FPakInputPair pair = new FPakInputPair(srcPath, dstPath);

                    pairs.add(pair);
                });

            final String s = PakCreator.GetCommonRootPath(pairs);
            System.out.println("S: \"" + s + "\"");
        }




//        try (final FileOutputStream fos = new FileOutputStream("C:\\Users\\ASUS\\Desktop\\Sample.pak"))
//        {
//            final FPakEntry fPakEntry = pakCreator.deflateFile(
//                    new FileInputStream("C:\\Users\\ASUS\\Desktop\\Content.txt"), fos.getChannel(), true,
//                    64 * 1024);
//        }
//
//
        final String brokenFile = "C:\\Users\\ASUS\\Desktop\\boh_gdc-WindowsNoEditor_broken.pak";

        // Broken
        try (final FPakFile fPakFile = new FPakFile(createdPak))
        {
            for (FFileIterator iterator = fPakFile.iterator(); iterator.hasNext(); )
            {
                FPakEntry e = iterator.next();
                System.out.println(iterator.toString());
            }
        }
    }
}
