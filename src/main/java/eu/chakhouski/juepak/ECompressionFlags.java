package eu.chakhouski.juepak;

public class ECompressionFlags
{
    /** No compression																*/
    public static int COMPRESS_None					= 0x00;
    /** Compress with ZLIB															*/
    public static int COMPRESS_ZLIB 					= 0x01;
    /** Compress with GZIP															*/
    public static int COMPRESS_GZIP					= 0x02;
    /** Compress with user defined callbacks                                        */
    public static int COMPRESS_Custom                 = 0x04;
    /** Prefer compression that compresses smaller (ONLY VALID FOR COMPRESSION)		*/
    public static int COMPRESS_BiasMemory 			= 0x10;
    /** Prefer compression that compresses faster (ONLY VALID FOR COMPRESSION)		*/
    public static int COMPRESS_BiasSpeed				= 0x20;
    /* Override Platform Compression (use library Compression_Method even on platforms with platform specific compression */
    public static int COMPRESS_OverridePlatform		= 0x40;
};