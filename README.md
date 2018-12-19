JUEPAK II
========

JuepakII [ʤuːpæk] (**J**ava **U**nreal **E**ngine **PAK**-archiver) is an Unreal Engine 4
**\*.PAK** archiver, written in pure java8. It can browse PAK-archives, extract them from
such archives, and make new archives from any files. It is completely compatible with 
**UE4.20** and below.


##Coding standard
A code standard within this project might be a bit confusing, but let me explain clearly why 
is it as it is. We're using **two independent coding standards** in this project:
- [Epic Games coding standard](https://docs.unrealengine.com/en-us/Programming/Development/CodingStandard)
in some UE4-related classes to replicate UE4 functionality. Since Unreal Engine and it's packing
mechanism is continuously developing, we need a stable and robust way to import upstream changes.
That also means that you can see **F**-notation for structures, usage of `checkf()`, `TEXT()` and
some other macros, parts of ported core-UE classes and, of course, old but gold `sizeof()` with
some limitations. It is very important to understand that I did not set myself the goal of completely
replicate Epic Games's code 
(for example, below the facade of ported
[FAES](https://api.unrealengine.com/INT/API/Runtime/Core/Misc/FAES/) is actually a plain 
java [Cipher](https://docs.oracle.com/javase/7/docs/api/javax/crypto/Cipher.html)),
but instead I tried to make the porting of the Epic company code simple and convenient.
- TODO: Write (plain java coding standard).


##Pak archive structure
- TODO: Write

## Compression
- TODO: Write

## Encryption
You may want to [Encrypt your PAK archives](https://docs.unrealengine.com/en-us/Engine/Basics/Projects/Packaging)
(see 'Signing and Encryption'), fortunately, juepak II can process encrypted PAK-files as well. Of course, you will
need the right key, otherwise the archiver can not operate.

### Approach
UE4 uses [reference implementation](http://www.efgh.com/software/rijndael.htm) of `AES/CBC` to encrypt and decrypt
data. The key is provided to the packing/unpacking code by
[FCoreDelegates::GetPakEncryptionKeyDelegate()](https://api.unrealengine.com/INT/API/Runtime/Core/Misc/FCoreDelegates/GetPakEncryptionKeyDelegate/index.html)
which is provided by ... **TODO: FINISH**

### API
 - TODO: write
 
 
 
Benchmarks
=========
 * TODO: Write