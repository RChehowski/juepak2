package com.vizor.unreal.util;

import com.vizor.unreal.annotations.FStruct;

import java.nio.ByteBuffer;

/**
 * Trivially deserializable UE4 structure.
 * 'Trivially' means that all fields are accessible and should serialized in order of appearance.
 *
 * NOTE: Target class must be annotated with {@link FStruct}
 * NOTE: Target class must have a trivial (no argument) constructor to be able to be instantiated
 */
public interface UE4Deserializable
{
    /**
     * Called once the instance is being de-serialized.
     * @param b Source byte buffer.
     */
    void Deserialize(ByteBuffer b);
}
