package eu.chakhouski.juepak;

import eu.chakhouski.juepak.ue4.FCoreDelegates;
import eu.chakhouski.juepak.util.PakCreator;

import java.io.FileInputStream;
import java.io.FileOutputStream;
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


        try {
            // Encode a String into bytes
            String inputString = "";
            byte[] input = inputString.getBytes("UTF-8");

            // Compress the bytes
            byte[] output = new byte[100];
            Deflater compresser = new Deflater();
            compresser.setInput(input);
            compresser.finish();
            int compressedDataLength = compresser.deflate(output);
            compresser.end();

            // Decompress the bytes
            Inflater decompresser = new Inflater();
            decompresser.setInput(output, 0, compressedDataLength);
            byte[] result = new byte[100];
            int resultLength = decompresser.inflate(result);
            decompresser.end();

            // Decode the bytes into a String
            String outputString = new String(result, 0, resultLength, "UTF-8");
            System.out.println(outputString);
        } catch(java.io.UnsupportedEncodingException ex) {
            // handle
        } catch (java.util.zip.DataFormatException ex) {

        }


        final PakCreator pakCreator = new PakCreator(FPakInfo.PakFile_Version_Latest);


        try (final FileOutputStream fos = new FileOutputStream("C:\\Users\\ASUS\\Desktop\\Sample.pak"))
        {
            final FPakEntry fPakEntry = pakCreator.deflateFile(
                    new FileInputStream("C:\\Users\\ASUS\\Desktop\\Content.txt"), fos.getChannel(), true,
                    64 * 1024);
        }


        final String brokenFile = "C:\\Users\\ASUS\\Desktop\\boh_gdc-WindowsNoEditor_broken.pa";

        // Broken
        try (final FPakFile fPakFile = new FPakFile(brokenFile))
        {
            for (FFileIterator iterator = fPakFile.iterator(); iterator.hasNext(); )
            {
                FPakEntry e = iterator.next();

                System.out.println(iterator.toString());
                iterator.extractToMemory(new byte[(int)e.UncompressedSize]);
            }
        }
    }
}
