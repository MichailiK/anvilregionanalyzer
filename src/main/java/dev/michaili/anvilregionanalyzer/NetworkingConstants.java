package dev.michaili.anvilregionanalyzer;

import net.minecraft.util.Identifier;

public class NetworkingConstants {
    public static final String NAMESPACE = "anvilregionanalyzer";

    public static final Identifier ANVIL_PROFILING_START = new Identifier(NAMESPACE, "anvil_profiling_start");
    public static final Identifier ANVIL_PROFILING_STOP = new Identifier(NAMESPACE, "anvil_profiling_stop");

    public static final Identifier ANVIL_PROFILING_REGION_STREAM = new Identifier(NAMESPACE, "anvil_profiling_region_stream");
}
