package eu.chakhouski.juepak;

public class Main
{
    public static void main(String[] args) throws Exception
    {
        final FPakInfo fPakInfo = new FPakInfo();


        final String file = "C:\\Users\\ASUS\\Desktop\\boh_gdc-WindowsNoEditor.pak";

        final FPakPlatformFile fPakPlatformFile = new FPakPlatformFile(file);

        final int sizeof = Sizeof.sizeof(FPakCompressedBlock.class);


        System.out.println("Hello");
    }
}
