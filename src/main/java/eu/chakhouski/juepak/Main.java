package eu.chakhouski.juepak;

public class Main
{
    public static void main(String[] args) throws Exception
    {
        final FPakInfo fPakInfo = new FPakInfo();


        final String file = "/Users/netherwire/Downloads/BloodOfHeroes_Client_Mac_139_master_shipping/boh_gdc.app/Contents/UE4/boh_gdc/Content/Paks/boh_gdc-MacNoEditor.pak";

        final FPakPlatformFile fPakPlatformFile = new FPakPlatformFile(file);
    }
}
