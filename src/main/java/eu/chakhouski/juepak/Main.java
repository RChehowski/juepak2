package eu.chakhouski.juepak;

import eu.chakhouski.juepak.ue4.FString;
import eu.chakhouski.juepak.util.Misc;

import java.util.Arrays;
import java.util.Map;

import static java.util.Comparator.comparingLong;

public class Main
{
    public static void main(String[] args) throws Exception
    {
        final String file = "/Users/netherwire/Downloads/BloodOfHeroes_Client_Mac_139_master_shipping/boh_gdc" +
                                    ".app/Contents/UE4/boh_gdc/Content/Paks/boh_gdc-MacNoEditor.pak";

        final FPakFile fPakFile = new FPakFile(file);

        final Map<String, FPakEntry> Entries = Misc.GetSortedEntries(fPakFile, comparingLong(o -> o.Offset));

        Entries.forEach((Filename, Entry) -> System.out.println(String.join(" ", Arrays.asList(
            Filename,
            "offset: " + Entry.Offset,
            "size: " + Entry.Size + " bytes",
            "sha1: " + FString.BytesToHex(Entry.Hash)
        ))));
    }
}
