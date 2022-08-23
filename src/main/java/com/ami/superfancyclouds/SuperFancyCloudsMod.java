package com.ami.superfancyclouds;

import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.qsl.base.api.entrypoint.client.ClientModInitializer;
import org.quiltmc.qsl.lifecycle.api.client.event.ClientTickEvents;
import org.quiltmc.qsl.networking.api.client.ClientPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SuperFancyCloudsMod implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("SuperFancyClouds");

	public static final SuperFancyCloudsRenderer RENDERER = new SuperFancyCloudsRenderer();

	@Override
	public void onInitializeClient(ModContainer mod) {

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> RENDERER.init());
		ClientTickEvents.START.register((client) -> RENDERER.tick());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> RENDERER.clean());
	}
}
