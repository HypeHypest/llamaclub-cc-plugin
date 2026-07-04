package com.llamaclub.api;

import com.google.gson.Gson;
import org.junit.Assert;
import org.junit.Test;

public class WebhookClientErrorMessageTest
{
	private final Gson gson = new Gson();

	@Test
	public void prefersValidationDetailOverGenericIngestRejectedMessage()
	{
		String body = gson.toJson(java.util.Map.of(
			"message", "Ingest rejected.",
			"errors", java.util.Map.of(
				"identity.username", java.util.List.of("Player is not in the clan roster. Sync clan data from RuneLite first.")
			)
		));

		Assert.assertEquals(
			"Player is not in the clan roster. Sync clan data from RuneLite first.",
			WebhookClientErrorMessages.describeFailure(422, body)
		);
	}

	@Test
	public void mapsServerErrorResponsesToActionableText()
	{
		Assert.assertEquals(
			"Website server error during ingest — contact a clan admin",
			WebhookClientErrorMessages.describeFailure(500, "{\"message\":\"Server Error\"}")
		);
	}

	@Test
	public void mapsRateLimitResponses()
	{
		Assert.assertEquals(
			"Too many requests — wait a minute and try again",
			WebhookClientErrorMessages.describeFailure(429, "{\"message\":\"Too Many Attempts.\"}")
		);
	}
}
