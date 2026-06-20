/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 */
package com.llamaclub.events;

import com.llamaclub.LlamaClubConfig;
import com.llamaclub.api.PayloadBuilder;
import com.llamaclub.api.ScreenshotCapture;
import com.llamaclub.api.ScreenshotEncoder;
import com.llamaclub.api.WebhookClient;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;

@Slf4j
public abstract class BaseNotifier
{
	private static final int MAX_SCREENSHOT_BYTES = 7 * 1024 * 1024;

	@Inject
	protected Client client;

	@Inject
	protected LlamaClubConfig config;

	@Inject
	protected PayloadBuilder payloadBuilder;

	@Inject
	protected WebhookClient webhookClient;

	@Inject
	private ScreenshotCapture screenshotCapture;

	public abstract boolean isEnabled();

	protected abstract String getEventKind();

	protected void sendEvent(Map<String, Object> eventData)
	{
		sendEvent(eventData, config.sendEventScreenshots());
	}

	protected void sendEvent(Map<String, Object> eventData, boolean includeScreenshot)
	{
		dispatchEvent(getEventKind(), eventData, includeScreenshot);
	}

	protected void sendEventOfKind(String kind, Map<String, Object> eventData)
	{
		dispatchEvent(kind, eventData, config.sendEventScreenshots());
	}

	protected void sendEventOfKind(String kind, Map<String, Object> eventData, boolean includeScreenshot)
	{
		dispatchEvent(kind, eventData, includeScreenshot);
	}

	private void dispatchEvent(String kind, Map<String, Object> eventData, boolean includeScreenshot)
	{
		if (!isEnabled())
		{
			return;
		}

		Map<String, Object> payload = buildEventPayload(kind, eventData);
		if (payload == null)
		{
			log.debug("Skipping {} event: webhooks disabled or plugin token missing", kind);
			return;
		}

		String pluginToken = extractPluginToken(payload);

		if (!includeScreenshot)
		{
			webhookClient.sendEventAsync(config.webhookUrl(), payload, null);
			return;
		}

		AtomicReference<ScreenshotEncoder.EncodedScreenshot> screenshotPayload = new AtomicReference<>();
		AtomicReference<Long> eventId = new AtomicReference<>();
		AtomicBoolean screenshotUploaded = new AtomicBoolean(false);

		Runnable tryUploadScreenshot = () ->
		{
			Long id = eventId.get();
			ScreenshotEncoder.EncodedScreenshot screenshot = screenshotPayload.get();
			if (id == null || screenshot == null || !screenshotUploaded.compareAndSet(false, true))
			{
				return;
			}

			webhookClient.uploadEventScreenshotAsync(
				config.webhookUrl(),
				id,
				pluginToken,
				screenshot.getFormat(),
				screenshot.getBytes(),
				success ->
				{
					if (success)
					{
						log.debug("Screenshot attached for {} event {}", kind, id);
					}
					else
					{
						log.warn(
							"Screenshot upload failed for {} event {} — check server logs and storage permissions",
							kind,
							id
						);
					}
				}
			);
		};

		screenshotCapture.captureNextFrame(image ->
		{
			ScreenshotEncoder.EncodedScreenshot encoded = sanitizeScreenshot(encodeScreenshot(image));
			if (encoded == null)
			{
				log.debug("Screenshot capture returned no image for {} event", kind);
			}
			screenshotPayload.set(encoded);
			tryUploadScreenshot.run();
		});

		webhookClient.sendEventAsync(config.webhookUrl(), payload, id ->
		{
			if (id == null)
			{
				return;
			}

			eventId.set(id);
			tryUploadScreenshot.run();
		});
	}

	private static ScreenshotEncoder.EncodedScreenshot encodeScreenshot(BufferedImage image)
	{
		if (image == null)
		{
			return null;
		}

		try
		{
			return ScreenshotEncoder.encodeForUpload(image);
		}
		catch (IOException e)
		{
			log.warn("Failed to encode event screenshot", e);
			return null;
		}
	}

	private Map<String, Object> buildEventPayload(String kind, Map<String, Object> eventData)
	{
		if (!config.webhookEnabled() || config.pluginToken() == null || config.pluginToken().isBlank())
		{
			return null;
		}

		Map<String, Object> enriched = new HashMap<>(eventData);
		enriched.put("occurredAt", System.currentTimeMillis());

		if (client.getLocalPlayer() != null)
		{
			WorldPoint location = client.getLocalPlayer().getWorldLocation();
			enriched.put("worldX", location.getX());
			enriched.put("worldY", location.getY());
			enriched.put("plane", location.getPlane());
		}

		return payloadBuilder.buildEvent(kind, enriched);
	}

	private static String extractPluginToken(Map<String, Object> payload)
	{
		Object identity = payload.get("identity");
		if (identity instanceof Map)
		{
			Object token = ((Map<?, ?>) identity).get("pluginToken");
			if (token instanceof String)
			{
				return (String) token;
			}
		}

		return "";
	}

	private static ScreenshotEncoder.EncodedScreenshot sanitizeScreenshot(ScreenshotEncoder.EncodedScreenshot screenshot)
	{
		if (screenshot == null || screenshot.getBytes().length == 0)
		{
			return null;
		}

		if (screenshot.getBytes().length > MAX_SCREENSHOT_BYTES)
		{
			log.warn(
				"Screenshot too large ({} bytes); skipping screenshot upload",
				screenshot.getBytes().length
			);
			return null;
		}

		return screenshot;
	}

	protected static boolean isGameMessage(ChatMessage event)
	{
		return event.getType() == ChatMessageType.GAMEMESSAGE
			|| event.getType() == ChatMessageType.SPAM;
	}
}
