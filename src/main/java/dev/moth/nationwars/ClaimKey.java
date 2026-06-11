/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.level.ChunkPos
 */
package dev.moth.nationwars;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

public record ClaimKey(String dimension, int x, int z) {
    public static ClaimKey of(ServerLevel level, ChunkPos chunkPos) {
        return new ClaimKey(level.dimension().location().toString(), chunkPos.x, chunkPos.z);
    }

    public static ClaimKey parse(String value) {
        String[] parts = value.split(":", 4);
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid claim key: " + value);
        }
        return new ClaimKey(parts[0] + ":" + parts[1], Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
    }

    public String id() {
        return this.dimension + ":" + this.x + ":" + this.z;
    }

    public String shortName() {
        return this.dimension + " [" + this.x + ", " + this.z + "]";
    }

    public boolean touches(ClaimKey other) {
        return this.dimension.equals(other.dimension) && Math.abs(this.x - other.x) + Math.abs(this.z - other.z) == 1;
    }
}

