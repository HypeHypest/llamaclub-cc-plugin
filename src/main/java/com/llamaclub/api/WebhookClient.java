/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 */
package com.llamaclub.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.llamaclub.LlamaClubConfig;
import com.llamaclub.service.BingoLootWhitelistService;
import com.llamaclub.service.PluginFilterSettingsService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.zip.GZIPOutputStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
@Singleton
public class WebhookClient
{
	public static final String PLUGIN_VERSION = "1.0.0";
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final MediaType PNG = MediaType.parse("image/png");
	private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

	private final OkHttpClient httpClient;

	@Inject
	private Gson gson;

	@Inject
	private LlamaClubConfig config;

	@Inject
	private BingoLootWhitelistService bingoLootWhitelistService;

	@Inject
	private PluginFilterSettingsService pluginFilterSettingsService;

	@Inject
	public WebhookClient(OkHttpClient okHttpClient, Gson gson)
	{
		this.gson = gson;
		this.httpClient = okHttpClient.newBuilder()
			.followRedirects(false)
			.followSslRedirects(false)
			.build();
	}

	public void sendMultipartAsync(
		String url,
		Map<String, Object> payload,
		byte[] screenshotPng,
		Consumer<Boolean> callback
	)
	{
		String webhookUrl = normalizeWebhookUrl(url);
		if (webhookUrl == null || webhookUrl.isEmpty())
		{
			if (callback != null)
			{
				callback.accept(false);
			}
			return;
		}

		if (screenshotPng == null || screenshotPng.length == 0)
		{
			sendAsync(url, payload, callback);
			return;
		}

		try
		{
			MultipartBody body = new MultipartBody.Builder()
				.setType(MultipartBody.FORM)
				.addFormDataPart("payload", gson.toJson(payload))
				.addFormDataPart("screenshot", "screenshot.png", RequestBody.create(PNG, screenshotPng))
				.build();

			Request.Builder requestBuilder = new Request.Builder()
				.url(webhookUrl)
				.post(body)
				.addHeader("Accept", "application/json")
				.addHeader("User-Agent", "RuneLite-LlamaClub-Plugin/" + PLUGIN_VERSION);

			applyAuthHeader(requestBuilder, payload);
			enqueueBooleanRequest(
				requestBuilder.build(),
				webhookUrl,
				callback == null ? null : (success, error) -> callback.accept(success)
			);
		}
		catch (RuntimeException e)
		{
			log.warn("Failed to prepare multipart webhook payload: {}", e.getMessage());
			if (callback != null)
			{
				callback.accept(false);
			}
		}
	}

	public void sendAsync(String url, Map<String, Object> payload, Consumer<Boolean> callback)
	{
		sendAsync(url, payload, callback == null ? null : (success, error) -> callback.accept(success));
	}

	public void sendAsync(String url, Map<String, Object> payload, BiConsumer<Boolean, String> callback)
	{
		String webhookUrl = normalizeWebhookUrl(url);
		if (webhookUrl == null || webhookUrl.isEmpty())
		{
			if (callback != null)
			{
				callback.accept(false, "Webhook URL is not configured");
			}
			return;
		}

		if (url != null && !webhookUrl.equals(url.trim()))
		{
			log.warn("Webhook URL pointed at token endpoint; using ingest URL instead: {}", webhookUrl);
		}

		try
		{
			Request request = buildGzipJsonRequest(webhookUrl, payload);
			enqueueBooleanRequest(request, webhookUrl, callback);
		}
		catch (IOException e)
		{
			log.warn("Failed to prepare webhook payload: {}", e.getMessage());
			if (callback != null)
			{
				callback.accept(false, "Failed to prepare sync payload");
			}
		}
	}

	public void fetchBingoLootWhitelistAsync()
	{
		if (!config.webhookEnabled())
		{
			return;
		}

		String token = config.pluginToken();
		if (token == null || token.isBlank())
		{
			return;
		}

		String whitelistUrl = bingoLootWhitelistUrl(config.webhookUrl());
		if (whitelistUrl == null || whitelistUrl.isEmpty())
		{
			return;
		}

		Request request = new Request.Builder()
			.url(whitelistUrl)
			.get()
			.addHeader("Accept", "application/json")
			.addHeader("Authorization", "Bearer " + token.trim())
			.addHeader("User-Agent", "RuneLite-LlamaClub-Plugin/" + PLUGIN_VERSION)
			.build();

		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Bingo loot whitelist fetch failed: {}", e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				String responseBody = readBody(response);
				if (response.isSuccessful())
				{
					bingoLootWhitelistService.updateFromResponseBody(responseBody);
				}
				response.close();
			}
		});
	}

	public void fetchPluginFilterSettingsAsync()
	{
		if (!config.webhookEnabled())
		{
			return;
		}

		String token = config.pluginToken();
		if (token == null || token.isBlank())
		{
			return;
		}

		String settingsUrl = pluginFilterSettingsUrl(config.webhookUrl());
		if (settingsUrl == null || settingsUrl.isEmpty())
		{
			return;
		}

		Request request = new Request.Builder()
			.url(settingsUrl)
			.get()
			.addHeader("Accept", "application/json")
			.addHeader("Authorization", "Bearer " + token.trim())
			.addHeader("User-Agent", "RuneLite-LlamaClub-Plugin/" + PLUGIN_VERSION)
			.build();

		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Plugin filter settings fetch failed: {}", e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				String responseBody = readBody(response);
				if (response.isSuccessful())
				{
					pluginFilterSettingsService.updateFromResponseBody(responseBody);
				}
				response.close();
			}
		});
	}

	/**
	 * Sends an event payload immediately and returns the recorded event id (null on failure).
	 */
	public void sendEventAsync(String url, Map<String, Object> payload, Consumer<Long> callback)
	{
		String webhookUrl = normalizeWebhookUrl(url);
		if (webhookUrl == null || webhookUrl.isEmpty())
		{
			if (callback != null)
			{
				callback.accept(null);
			}
			return;
		}

		try
		{
			Request request = buildGzipJsonRequest(webhookUrl, payload);
			enqueueEventRequest(request, webhookUrl, callback);
		}
		catch (IOException e)
		{
			log.warn("Failed to prepare event payload: {}", e.getMessage());
			if (callback != null)
			{
				callback.accept(null);
			}
		}
	}

	/**
	 * Attaches a screenshot to a previously recorded event via gzip JSON (same transport as ingest).
	 */
	public void uploadEventScreenshotAsync(
		String ingestUrl,
		long eventId,
		String bearerToken,
		String format,
		byte[] imageBytes,
		Consumer<Boolean> callback
	)
	{
		String uploadUrl = eventScreenshotUrl(ingestUrl, eventId);
		String authToken = resolveBearerToken(null);
		if (authToken == null)
		{
			authToken = bearerToken != null ? bearerToken.trim() : null;
		}
		if (uploadUrl == null || uploadUrl.isEmpty()
			|| authToken == null || authToken.isBlank()
			|| format == null || format.isBlank()
			|| imageBytes == null || imageBytes.length == 0)
		{
			if (callback != null)
			{
				callback.accept(false);
			}
			return;
		}

		try
		{
			Map<String, Object> payload = new HashMap<>();
			payload.put("format", format);
			payload.put("data", Base64.getEncoder().encodeToString(imageBytes));

			byte[] body = compressJson(gson.toJson(payload));
			Request request = new Request.Builder()
				.url(uploadUrl)
				.post(RequestBody.create(JSON, body))
				.addHeader("Accept", "application/json")
				.addHeader("Content-Type", "application/json")
				.addHeader("Content-Encoding", "gzip")
				.addHeader("Authorization", "Bearer " + authToken)
				.addHeader("User-Agent", "RuneLite-LlamaClub-Plugin/" + PLUGIN_VERSION)
				.build();

			enqueueScreenshotRequest(request, uploadUrl, callback);
		}
		catch (IOException e)
		{
			log.warn("Failed to prepare screenshot upload: {}", e.getMessage());
			if (callback != null)
			{
				callback.accept(false);
			}
		}
	}

	private Request buildGzipJsonRequest(String webhookUrl, Map<String, Object> payload) throws IOException
	{
		byte[] body = compressJson(gson.toJson(payload));
		Request.Builder requestBuilder = new Request.Builder()
			.url(webhookUrl)
			.post(RequestBody.create(JSON, body))
			.addHeader("Accept", "application/json")
			.addHeader("Content-Type", "application/json")
			.addHeader("Content-Encoding", "gzip")
			.addHeader("User-Agent", "RuneLite-LlamaClub-Plugin/" + PLUGIN_VERSION);

		applyAuthHeader(requestBuilder, payload);
		return requestBuilder.build();
	}

	private void applyAuthHeader(Request.Builder requestBuilder, Map<String, Object> payload)
	{
		String token = resolveBearerToken(payload);
		if (token != null)
		{
			requestBuilder.addHeader("Authorization", "Bearer " + token);
		}
	}

	private String resolveBearerToken(Map<String, Object> payload)
	{
		String configured = config.pluginToken();
		if (configured != null)
		{
			configured = configured.trim();
			if (!configured.isEmpty())
			{
				return configured;
			}
		}

		if (payload == null)
		{
			return null;
		}

		Object identity = payload.get("identity");
		if (identity instanceof Map)
		{
			Object token = ((Map<?, ?>) identity).get("pluginToken");
			if (token instanceof String && !((String) token).isBlank())
			{
				return ((String) token).trim();
			}
		}

		return null;
	}

	private void enqueueBooleanRequest(Request request, String webhookUrl, BiConsumer<Boolean, String> callback)
	{
		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("Webhook request failed: {}", e.getMessage());
				if (callback != null)
				{
					callback.accept(false, "Could not reach the website — check the webhook URL");
				}
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				String responseBody = readBody(response);
				boolean success = isSuccessfulIngestResponse(response, responseBody);
				if (!success)
				{
					logWebhookFailure(response, webhookUrl, responseBody);
				}
				else
				{
					logIngestSuccess(responseBody);
					bingoLootWhitelistService.updateFromResponseBody(responseBody);
					pluginFilterSettingsService.updateFromResponseBody(responseBody);
				}
				response.close();
				if (callback != null)
				{
					callback.accept(success, success ? null : describeIngestFailure(response.code(), responseBody));
				}
			}
		});
	}

	private void enqueueEventRequest(Request request, String webhookUrl, Consumer<Long> callback)
	{
		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("Event webhook request failed: {}", e.getMessage());
				if (callback != null)
				{
					callback.accept(null);
				}
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				String responseBody = readBody(response);
				Long eventId = parseRecordedEventId(response, responseBody);
				if (eventId == null)
				{
					logWebhookFailure(response, webhookUrl, responseBody);
				}
				else
				{
					bingoLootWhitelistService.updateFromResponseBody(responseBody);
					pluginFilterSettingsService.updateFromResponseBody(responseBody);
				}
				response.close();
				if (callback != null)
				{
					callback.accept(eventId);
				}
			}
		});
	}

	private void enqueueScreenshotRequest(Request request, String uploadUrl, Consumer<Boolean> callback)
	{
		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("Screenshot upload failed: {}", e.getMessage());
				if (callback != null)
				{
					callback.accept(false);
				}
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				String responseBody = readBody(response);
				boolean success = isSuccessfulScreenshotResponse(response, responseBody);
				if (!success)
				{
					log.warn("Screenshot upload returned HTTP {} for {}: {}",
						response.code(), uploadUrl, responseBody);
				}
				response.close();
				if (callback != null)
				{
					callback.accept(success);
				}
			}
		});
	}

	private void logWebhookFailure(Response response, String webhookUrl, String responseBody)
	{
		log.warn("Webhook returned HTTP {} for {}: {}", response.code(), webhookUrl, responseBody);
		if (webhookUrl.contains("/tokens"))
		{
			log.warn("Use /api/runelite/ingest for sync and events, not /api/runelite/tokens");
		}
	}

	private void logIngestSuccess(String responseBody)
	{
		if (responseBody == null || responseBody.isBlank())
		{
			return;
		}

		try
		{
			Map<String, Object> parsed = gson.fromJson(responseBody, MAP_TYPE);
			Object status = parsed.get("status");
			if (!"roster_synced".equals(status))
			{
				return;
			}

			log.info(
				"Clan roster stored on server: memberCount={}, joinDateCount={}",
				parsed.get("memberCount"),
				parsed.get("joinDateCount")
			);
		}
		catch (RuntimeException ignored)
		{
			// Response body may not be JSON
		}
	}

	private static String readBody(Response response)
	{
		try
		{
			if (response.body() != null)
			{
				return response.body().string();
			}
		}
		catch (IOException ignored)
		{
			// Response body may be unavailable
		}

		return "";
	}

	private Long parseRecordedEventId(Response response, String responseBody)
	{
		if (!isSuccessfulIngestResponse(response, responseBody))
		{
			return null;
		}

		try
		{
			Map<String, Object> parsed = gson.fromJson(responseBody, MAP_TYPE);
			return parseEventId(parsed.get("eventId"));
		}
		catch (RuntimeException e)
		{
			log.warn("Event response did not include a valid eventId");
			return null;
		}
	}

	private static Long parseEventId(Object eventId)
	{
		if (eventId instanceof Number)
		{
			return ((Number) eventId).longValue();
		}

		if (eventId instanceof String)
		{
			try
			{
				return Long.parseLong((String) eventId);
			}
			catch (NumberFormatException ignored)
			{
				return null;
			}
		}

		return null;
	}

	private boolean isSuccessfulIngestResponse(Response response, String responseBody)
	{
		if (response.code() >= 300 && response.code() < 400)
		{
			return false;
		}

		if (!response.isSuccessful())
		{
			return false;
		}

		if (responseBody == null || responseBody.isBlank())
		{
			return false;
		}

		try
		{
			Map<String, Object> parsed = gson.fromJson(responseBody, MAP_TYPE);
			Object status = parsed.get("status");
			return "synced".equals(status) || "recorded".equals(status) || "roster_synced".equals(status);
		}
		catch (RuntimeException e)
		{
			log.warn("Webhook response was not valid ingest JSON");
			return false;
		}
	}

	private String describeIngestFailure(int statusCode, String responseBody)
	{
		return WebhookClientErrorMessages.describeFailure(gson, statusCode, responseBody);
	}

	private boolean isSuccessfulScreenshotResponse(Response response, String responseBody)
	{
		if (!response.isSuccessful() || responseBody == null || responseBody.isBlank())
		{
			return false;
		}

		try
		{
			Map<String, Object> parsed = gson.fromJson(responseBody, MAP_TYPE);
			return "screenshot_attached".equals(parsed.get("status"));
		}
		catch (RuntimeException e)
		{
			return false;
		}
	}

	private static byte[] compressJson(String json) throws IOException
	{
		byte[] jsonBytes = json.getBytes("UTF-8");
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		try (GZIPOutputStream gzipStream = new GZIPOutputStream(byteStream))
		{
			gzipStream.write(jsonBytes);
		}
		return byteStream.toByteArray();
	}

	static String normalizeWebhookUrl(String url)
	{
		if (url == null)
		{
			return null;
		}

		String trimmed = url.trim();
		if (trimmed.isEmpty())
		{
			return trimmed;
		}

		if (trimmed.contains("/api/runelite/tokens"))
		{
			return trimmed.replace("/api/runelite/tokens", "/api/runelite/ingest");
		}

		return trimmed;
	}

	static String bingoLootWhitelistUrl(String ingestUrl)
	{
		String normalized = normalizeWebhookUrl(ingestUrl);
		if (normalized == null || normalized.isEmpty())
		{
			return null;
		}

		if (normalized.endsWith("/ingest"))
		{
			return normalized.substring(0, normalized.length() - "/ingest".length())
				+ "/bingo/loot-whitelist";
		}

		return normalized + "/bingo/loot-whitelist";
	}

	static String pluginFilterSettingsUrl(String ingestUrl)
	{
		String normalized = normalizeWebhookUrl(ingestUrl);
		if (normalized == null || normalized.isEmpty())
		{
			return null;
		}

		if (normalized.endsWith("/ingest"))
		{
			return normalized.substring(0, normalized.length() - "/ingest".length())
				+ "/plugin-filter-settings";
		}

		return normalized + "/plugin-filter-settings";
	}

	static String eventScreenshotUrl(String ingestUrl, long eventId)
	{
		String normalized = normalizeWebhookUrl(ingestUrl);
		if (normalized == null || normalized.isEmpty())
		{
			return null;
		}

		if (normalized.endsWith("/ingest"))
		{
			return normalized.substring(0, normalized.length() - "/ingest".length())
				+ "/events/" + eventId + "/screenshot";
		}

		return normalized + "/events/" + eventId + "/screenshot";
	}
}
