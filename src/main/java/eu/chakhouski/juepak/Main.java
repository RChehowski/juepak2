package eu.chakhouski.juepak;

import eu.chakhouski.juepak.ue4.FAES;
import eu.chakhouski.juepak.ue4.FCoreDelegates;
import eu.chakhouski.juepak.ue4.FString;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.function.Consumer;

public class Main
{
    public static void main(String[] args) throws Exception
    {
        FCoreDelegates.GetPakEncryptionKeyDelegate().BindLambda(new Consumer<byte[]>()
        {
            @Override
            public void accept(byte[] bytes)
            {
                final byte[] decode = Base64.getDecoder().decode("qgI1qdSNOmIUU7vQ2w6PorzF7Maiu/a1wulgdk+ssu0=");

                System.arraycopy(decode, 0, bytes, 0, bytes.length);
            }
        });



        final String file = "C:\\Users\\ASUS\\VizorGamesLauncher\\games\\boh_gdc\\boh_gdc\\Content\\Paks\\boh_gdc-WindowsNoEditor.pak";

        try (final FPakFile fPakFile = new FPakFile(file))
        {
            for (FFileIterator It = fPakFile.iterator(); It.hasNext();)
            {
                final FPakEntry Entry = It.next();
                final String Filename = It.Filename();

                System.out.println(String.join(" ", Arrays.asList(
                        "Extracting",
                        Filename,
                        "offset: " + Entry.Offset,
                        "size: " + Entry.Size + " bytes",
                        "sha1: " + FString.BytesToHex(Entry.Hash)
                )));

                It.extractMixed("C:\\Users\\ASUS\\Desktop\\Extract");
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
