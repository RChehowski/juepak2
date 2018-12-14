package eu.chakhouski.juepak;

import eu.chakhouski.juepak.util.Misc;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;

import static java.util.Comparator.comparingLong;

public class Main
{
    public static void main(String[] args) throws Exception
    {
        final FPakInfo fPakInfo = new FPakInfo();


        final String file = "C:\\Users\\ASUS\\Desktop\\boh_gdc-WindowsNoEditor.pak";//"/Users/netherwire/Downloads/BloodOfHeroes_Client_Mac_139_master_shipping/boh_gdc" +
                                    // ".app/Contents/UE4/boh_gdc/Content/Paks/boh_gdc-MacNoEditor.pak";

        final FPakFile fPakFile = new FPakFile(file);

        final Map<String, FPakEntry> Entries = Misc.GetSortedEntries(fPakFile, comparingLong(o -> o.Offset));


        System.out.println(Entries.size());
//        int n = 0;
//        for (FFileIterator iterator = fPakFile.iterator(); iterator.hasNext(); )
//        {
//            FPakEntry Entry = iterator.next();
//
//            System.out.println(String.join(" ", Arrays.asList(
//                ++n + ") ",
//                iterator.Filename(),
//                "offset: " + Entry.Offset,
//                "size: " + Entry.Size + " bytes",
//                "sha1: " + Misc.bytesToHex(Entry.Hash)
//            )));
//        }
    }
}
