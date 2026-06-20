/*
 * Portions of this file are derived from or inspired by the Wise Old Man plugin
 * Copyright (c) 2020, dekvall
 * Copyright (c) 2021, Rorro
 * Licensed under the BSD 2-Clause License
 * See LICENSES/wom-LICENSE.txt for full license text
 */
package com.llamaclub.ui;

import com.llamaclub.LlamaClubConfig;
import com.llamaclub.LlamaClubPlugin;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class CodeWordOverlay extends OverlayPanel
{
	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'UTC'")
		.withZone(ZoneOffset.UTC);

	private final LlamaClubConfig config;

	@Inject
	private CodeWordOverlay(LlamaClubPlugin plugin, LlamaClubConfig config)
	{
		super(plugin);
		setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
		setPriority(PRIORITY_LOW);
		this.config = config;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		String codeword = config.configuredCodeword();
		if (!config.displayCodeword() || codeword == null || codeword.isBlank())
		{
			return null;
		}

		panelComponent.getChildren().clear();

		if (config.showCodewordTimestamp())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(codeword)
				.leftColor(parseColor(config.codewordColor(), new Color(0xFD8F00)))
				.right(FORMATTER.format(Instant.now()))
				.rightColor(parseColor(config.timestampColor(), new Color(0xFF9A00)))
				.build());
		}
		else
		{
			panelComponent.getChildren().add(TitleComponent.builder()
				.text(codeword)
				.color(parseColor(config.codewordColor(), new Color(0xFD8F00)))
				.build());
		}

		return super.render(graphics);
	}

	private static Color parseColor(String hex, Color fallback)
	{
		if (hex == null || hex.isBlank())
		{
			return fallback;
		}

		try
		{
			return Color.decode(hex.startsWith("#") ? hex : "#" + hex);
		}
		catch (NumberFormatException e)
		{
			return fallback;
		}
	}
}
