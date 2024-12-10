package net.kurasava.smoothnight;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.loader.api.FabricLoader;
import net.kurasava.smoothnight.config.ModConfig;
import org.apache.logging.log4j.core.lookup.JavaLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmoothNight implements ModInitializer {

	public static SmoothNight INSTANCE;
	public ModConfig config;

	@Override
	public void onInitialize() {
		INSTANCE = this;
		this.config = new ModConfig();
	}
}