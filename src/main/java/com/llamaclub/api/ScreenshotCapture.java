/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 */
package com.llamaclub.api;

import com.llamaclub.LlamaClubConfig;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.util.ImageCapture;
import net.runelite.client.util.ImageUtil;

/**
 * Captures the next rendered client frame for event screenshots.
 *
 * <p>Follows Dink's {@code Utils.captureScreenshot} pattern: the draw listener runs
 * synchronously and {@link ImageCapture#addClientFrame} is executed on a background
 * executor — not deferred via {@code ClientThread.invokeLater}, which could prevent
 * the callback from ever firing.
 */
@Slf4j
@Singleton
public class ScreenshotCapture
{
	@Inject
	private Client client;

	@Inject
	private LlamaClubConfig config;

	@Inject
	private ClientUI clientUi;

	@Inject
	private ClientThread clientThread;

	@Inject
	private DrawManager drawManager;

	@Inject
	private ImageCapture imageCapture;

	@Inject
	private ScheduledExecutorService scheduledExecutorService;

	public void captureNextFrame(Consumer<BufferedImage> callback)
	{
		if (callback == null)
		{
			return;
		}

		if (client.getGameState() == null)
		{
			log.debug("Game state unavailable; skipping screenshot frame");
			callback.accept(null);
			return;
		}

		boolean privateMessagesHidden = hidePrivateMessagesWidget();

		drawManager.requestNextFrameListener(frame ->
		{
			scheduledExecutorService.execute(() -> deliverFrame(frame, callback));
			restorePrivateMessagesWidget(privateMessagesHidden);
		});
	}

	private void deliverFrame(Image frame, Consumer<BufferedImage> callback)
	{
		try
		{
			if (!config.screenshotIncludeSidebar())
			{
				callback.accept(ImageUtil.bufferedImageFromImage(frame));
				return;
			}

			Image framed = imageCapture.addClientFrame(frame);
			if (framed == null)
			{
				callback.accept(ImageUtil.bufferedImageFromImage(frame));
				return;
			}

			BufferedImage screenshot = ImageUtil.bufferedImageFromImage(framed);
			callback.accept(cropTitleBar(screenshot));
		}
		catch (RuntimeException e)
		{
			log.warn("Failed to add client frame to screenshot", e);
			try
			{
				callback.accept(ImageUtil.bufferedImageFromImage(frame));
			}
			catch (RuntimeException fallbackError)
			{
				log.warn("Failed to convert screenshot frame", fallbackError);
				callback.accept(null);
			}
		}
	}

	private BufferedImage cropTitleBar(BufferedImage framed)
	{
		AffineTransform transform = clientUi.getGraphicsConfiguration().getDefaultTransform();
		Insets insets = clientUi.getInsets();

		Point canvasOffset = clientUi.getCanvasOffset();
		canvasOffset.x -= insets.left;
		canvasOffset.y -= insets.top;

		int cropY = getScaledValue(transform.getScaleY(), canvasOffset.y);
		int cropWidth = framed.getWidth();
		int cropHeight = framed.getHeight() - cropY;

		if (cropY <= 0 || cropHeight <= 0 || cropWidth <= 0)
		{
			return framed;
		}

		return framed.getSubimage(0, cropY, cropWidth, cropHeight);
	}

	private boolean hidePrivateMessagesWidget()
	{
		if (!config.screenshotHidePrivateMessages())
		{
			return false;
		}

		Widget widget = client.getWidget(InterfaceID.PmChat.CONTAINER);
		if (widget == null || widget.isHidden())
		{
			return false;
		}

		widget.setHidden(true);
		return true;
	}

	private void restorePrivateMessagesWidget(boolean shouldRestore)
	{
		if (!shouldRestore)
		{
			return;
		}

		clientThread.invoke(() ->
		{
			Widget widget = client.getWidget(InterfaceID.PmChat.CONTAINER);
			if (widget != null)
			{
				widget.setHidden(false);
			}
		});
	}

	private static int getScaledValue(double scale, int value)
	{
		return (int) (value * scale);
	}
}
