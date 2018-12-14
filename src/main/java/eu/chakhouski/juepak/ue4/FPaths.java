package eu.chakhouski.juepak.ue4;

import java.util.function.Predicate;

public class FPaths
{
    public static int INDEX_NONE = -1;

    private static Predicate<Character> IsSlashOrBackslash      = C -> C == '\\' || C == '/';
    private static Predicate<Character> IsNotSlashOrBackslash   = C -> C != '\\' && C != '/';


    public static String GetPath(String InPath)
    {
        int Pos = FindLastCharByPredicate(InPath, IsSlashOrBackslash);

        String Result = "";
        if (Pos != INDEX_NONE)
        {
            Result = InPath.substring(0, Pos);
        }

        return Result;
    }

    public static String GetCleanFilename(String InPath)
    {
        if (!(INDEX_NONE == -1))
            throw new RuntimeException("INDEX_NONE assumed to be -1");

        int EndPos   = FindLastCharByPredicate(InPath, IsNotSlashOrBackslash) + 1;
        int StartPos = FindLastCharByPredicate(InPath, IsSlashOrBackslash, EndPos) + 1;

        String Result = InPath.substring(StartPos, EndPos);
        return Result;
    }

    private static int FindLastCharByPredicate(String Data, Predicate<Character> Pred)
    {
        return FindLastCharByPredicate(Data, Pred, Data.length());
    }

    private static int FindLastCharByPredicate(String Data, Predicate<Character> Pred, int Count)
    {
        assert Count >= 0 && Count <= Data.length();

        for (int i = Count - 1; i >= 0; i--)
        {
            if (Pred.test(Data.charAt(i)))
            {
                return i;
            }
        }

        return -1;
    }

}
