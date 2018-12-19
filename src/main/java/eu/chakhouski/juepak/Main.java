package eu.chakhouski.juepak;

import eu.chakhouski.juepak.ue4.FAES;
import eu.chakhouski.juepak.ue4.FString;
import eu.chakhouski.juepak.util.Misc;
import eu.chakhouski.juepak.util.PakExtractor;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.function.BiConsumer;

import static java.util.Comparator.comparingLong;

public class Main
{
    public static void main(String[] args) throws Exception
    {
        final byte[] plainText = "Hello, world".getBytes();


        FAES.FAESKey key = new FAES.FAESKey();
        key.Key = "12341234123412341234123412341234".getBytes();
//

        final byte[] encryptData = FAES.EncryptData(plainText, plainText.length, key);
        final byte[] decryptData = FAES.DecryptData(encryptData, encryptData.length, key);
//
//        FAES.DecryptData(key.Key, key.Key.length, key);

        final String decryptedText = new String(decryptData);
        System.out.println(decryptedText);


//        final FPakInfo fPakInfo = new FPakInfo();
//
//
//        final String file = "C:\\Users\\ASUS\\Desktop\\boh_gdc-WindowsNoEditor.pak";//"/Users/netherwire/Downloads/BloodOfHeroes_Client_Mac_139_master_shipping/boh_gdc" +
//                                    // ".app/Contents/UE4/boh_gdc/Content/Paks/boh_gdc-MacNoEditor.pak";
//
//        final FPakFile fPakFile = new FPakFile(file);
//
//        final Map<String, FPakEntry> Entries = Misc.GetSortedEntries(fPakFile, comparingLong(o -> o.Offset));
//
//        Entries.forEach((Filename, Entry) -> System.out.println(String.join(" ", Arrays.asList(
//            Filename,
//            "offset: " + Entry.Offset,
//            "size: " + Entry.Size + " bytes",
//            "sha1: " + FString.BytesToHex(Entry.Hash)
//        ))));

//        PakExtractor.Extract(fPakFile, Paths.get(file), Entries, Paths.get("C:\\Users\\ASUS\\Desktop\\Extract"));

    }
}
