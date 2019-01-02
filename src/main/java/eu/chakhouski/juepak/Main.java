package eu.chakhouski.juepak;

import eu.chakhouski.juepak.ue4.FCoreDelegates;
import eu.chakhouski.juepak.util.PakCreator;
import eu.chakhouski.juepak.util.UE4Serializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Base64;
import java.util.function.Consumer;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class Main
{
    public static void main(String[] args) throws Exception
    {
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


        final PakCreator pakCreator = new PakCreator("/Users/netherwire/Desktop/Sample.pak", FPakInfo.PakFile_Version_Latest);

        final File file = new File("/Users/netherwire/Pictures");
        file.listFiles(new FilenameFilter()
        {
            @Override
            public boolean accept(File dir, String name)
            {
                final Path absPath = dir.toPath().resolve(name);

                System.out.println(absPath.toString());
                pakCreator.addFile(absPath);

                return false;
            }
        });

        pakCreator.finalizeWrite();


//        try (final FileOutputStream fos = new FileOutputStream("C:\\Users\\ASUS\\Desktop\\Sample.pak"))
//        {
//            final FPakEntry fPakEntry = pakCreator.deflateFile(
//                    new FileInputStream("C:\\Users\\ASUS\\Desktop\\Content.txt"), fos.getChannel(), true,
//                    64 * 1024);
//        }
//
//
//        final String brokenFile = "C:\\Users\\ASUS\\Desktop\\boh_gdc-WindowsNoEditor_broken.pak";
//
//        // Broken
//        try (final FPakFile fPakFile = new FPakFile(brokenFile))
//        {
//            for (FFileIterator iterator = fPakFile.iterator(); iterator.hasNext(); )
//            {
//                FPakEntry e = iterator.next();
//
//                System.out.println(iterator.toString());
//                iterator.extractToMemory(new byte[(int)e.UncompressedSize]);
//            }
//        }
    }
}
