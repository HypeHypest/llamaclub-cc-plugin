package com.llamaclub.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class WebhookClientErrorMessages
{
	private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();
	private static final Set<String> GENERIC_ERROR_MESSAGES = Set.of(
		"Ingest rejected.",
		"Screenshot attachment rejected.",
		"Server Error",
		"Ingest failed due to a server error."
	);

	private WebhookClientErrorMessages()
	{
	}

	static String describeFailure(Gson gson, int statusCode, String responseBody)
	{
		if (statusCode >= 300 && statusCode < 400)
		{
			return "Webhook request was redirected — use /api/runelite/ingest";
		}

		if (statusCode == 401)
		{
			return "Invalid plugin token — generate a new one on the website";
		}

		if (statusCode == 503)
		{
			return "RuneLite ingest is disabled on the server";
		}

		if (statusCode == 404)
		{
			return "API endpoint not found — the RuneLite backend may not be deployed to this URL yet";
		}

		if (statusCode == 429)
		{
			return "Too many requests — wait a minute and try again";
		}

		if (statusCode >= 500)
		{
			String fromBody = extractFirstErrorMessage(gson, responseBody);
			if (fromBody != null && !GENERIC_ERROR_MESSAGES.contains(fromBody))
			{
				return fromBody;
			}

			return "Website server error during ingest — contact a clan admin";
		}

		String fromBody = extractFirstErrorMessage(gson, responseBody);
		if (fromBody != null)
		{
			return fromBody;
		}

		if (statusCode == 403)
		{
			return "Player is not a clan member — sync while logged into a clan character";
		}

		return "Sync failed (HTTP " + statusCode + ")";
	}

	private static String extractFirstErrorMessage(Gson gson, String responseBody)
	{
		if (responseBody == null || responseBody.isBlank())
		{
			return null;
		}

		try
		{
			Map<String, Object> parsed = gson.fromJson(responseBody, MAP_TYPE);
			String fromErrors = extractFirstValidationError(parsed.get("errors"));
			if (fromErrors != null)
			{
				return fromErrors;
			}

			Object message = parsed.get("message");
			if (message instanceof String && !((String) message).isBlank())
			{
				String text = (String) message;
				if (!GENERIC_ERROR_MESSAGES.contains(text))
				{
					return text;
				}
			}
		}
		catch (RuntimeException ignored)
		{
			// Response body may not be JSON
		}

		return null;
	}

	private static String extractFirstValidationError(Object errors)
	{
		if (!(errors instanceof Map))
		{
			return null;
		}

		for (Object value : ((Map<?, ?>) errors).values())
		{
			if (value instanceof List && !((List<?>) value).isEmpty())
			{
				Object first = ((List<?>) value).get(0);
				if (first instanceof String && !((String) first).isBlank())
				{
					return (String) first;
				}
			}
		}

		return null;
	}
}
