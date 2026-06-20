package com.llamaclub.ui.constants;

import java.awt.Color;

/**
 * Centralized UI constants aligned with the Llama Club website palette
 * ({@code rs.*} tokens in Website/staging/tailwind.config.js).
 */
public final class UIConstants
{
	// ========== Background Colors ==========

	/** Elevated blocks such as the status header ({@code rs.card}) */
	public static final Color CARD_BG = color(0x1c, 0x1c, 0x1c);

	/** Form inputs, spinners, combo boxes, and buttons ({@code rs.elevated}) */
	public static final Color INPUT_BG = color(0x24, 0x24, 0x24);

	// ========== Border Colors ==========

	/** Form control and checkbox borders ({@code rs.border}) */
	public static final Color BORDER_COLOR = color(0x2a, 0x2a, 0x2a);

	/** Section dividers and separators ({@code rs.subtle}) */
	public static final Color DIVIDER_COLOR = color(0x5a, 0x50, 0x40);

	// ========== Text Colors ==========

	/** Primary label and input text ({@code rs.text}) */
	public static final Color TEXT_PRIMARY = color(0xf0, 0xea, 0xd6);

	/** Secondary status and helper text ({@code rs.muted}) */
	public static final Color TEXT_MUTED = color(0x9a, 0x8c, 0x6e);

	// ========== Accent Colors ==========

	/** Section headers and highlights ({@code rs.gold}) */
	public static final Color ACCENT = color(0xc8, 0xa8, 0x4b);

	/** Text selection background in inputs (gold at ~25% opacity) */
	public static final Color SELECTION = new Color(200, 168, 75, 64);

	/** Yellow checkmark on checked boxes ({@code #e9e335}) */
	public static final Color CHECKMARK = color(0xe9, 0xe3, 0x35);

	/** Error text ({@code destructive} on the website) */
	public static final Color ERROR = color(0xd3, 0x2c, 0x2c);

	private UIConstants()
	{
	}

	public static String toHex(Color color)
	{
		return String.format("#%06x", color.getRGB() & 0xFFFFFF);
	}

	private static Color color(int r, int g, int b)
	{
		return new Color(r, g, b);
	}
}
