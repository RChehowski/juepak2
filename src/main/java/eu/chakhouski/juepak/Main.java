package eu.chakhouski.juepak;

import eu.chakhouski.juepak.ue4.FString;

import java.io.IOException;
import java.util.Arrays;

public class Main
{
    public static void main(String[] args)
    {
        final String file = "/Users/netherwire/Downloads/BloodOfHeroes_Client_Mac_139_master_shipping/boh_gdc" +
                                     ".app/Contents/UE4/boh_gdc/Content/Paks/boh_gdc-MacNoEditor.pak";


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

                It.extractMixed("/Users/netherwire/Desktop/extract");
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
