package com.vizor.unreal.util;

import com.vizor.unreal.annotations.FStruct;

import java.nio.ByteBuffer;

/**
 * Trivially deserializable UE4 structure.
 * 'Trivially' means that all fields are accessible and should serialized in order of appearance.
 *
 * NOTE: Target class must be annotated with {@link FStruct}
 */
public interface UE4Serializable
{
    /**
     * Called once the instance is being serialized.
     * @param b Drain byte buffer.
     */
    void Serialize(ByteBuffer b);
}
