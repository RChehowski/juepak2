package eu.chakhouski.juepak.util;

import java.nio.ByteBuffer;

/**
 * Each UEDeserializable must have a trivial (no argument) constructor to be able to be instantiated
 */
public interface UEDeserializable
{
    void Deserialize(ByteBuffer b);
}
