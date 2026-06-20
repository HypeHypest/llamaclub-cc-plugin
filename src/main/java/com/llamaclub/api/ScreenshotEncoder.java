/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 */
package com.llamaclub.api;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

public final class ScreenshotEncoder
{
	/** Keep uploads small enough for PHP built-in server POST buffering on Windows. */
	private static final int TARGET_UPLOAD_BYTES = 350_000;

	private ScreenshotEncoder()
	{
	}

	public static final class EncodedScreenshot
	{
		private final String format;
		private final byte[] bytes;

		public EncodedScreenshot(String format, byte[] bytes)
		{
			this.format = format;
			this.bytes = bytes;
		}

		public String getFormat()
		{
			return format;
		}

		public byte[] getBytes()
		{
			return bytes;
		}
	}

	public static byte[] toPngBytes(BufferedImage image) throws IOException
	{
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		if (!ImageIO.write(image, "png", output))
		{
			throw new IOException("PNG writer unavailable");
		}
		return output.toByteArray();
	}

	public static EncodedScreenshot encodeForUpload(BufferedImage image) throws IOException
	{
		byte[] png = toPngBytes(image);
		if (png.length <= TARGET_UPLOAD_BYTES)
		{
			return new EncodedScreenshot("png", png);
		}

		double[] scales = {0.75, 0.5, 0.35, 0.25};
		float[] qualities = {0.85f, 0.8f, 0.75f, 0.7f};

		for (int i = 0; i < scales.length; i++)
		{
			BufferedImage scaled = rescale(image, scales[i]);
			byte[] jpeg = toJpegBytes(scaled, qualities[i]);
			if (jpeg.length <= TARGET_UPLOAD_BYTES || i == scales.length - 1)
			{
				return new EncodedScreenshot("jpeg", jpeg);
			}
		}

		return new EncodedScreenshot("jpeg", toJpegBytes(rescale(image, 0.25), 0.7f));
	}

	private static BufferedImage rescale(BufferedImage input, double scale)
	{
		if (scale >= 1.0)
		{
			return input;
		}

		int width = Math.max(1, (int) (input.getWidth() * scale));
		int height = Math.max(1, (int) (input.getHeight() * scale));
		BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = output.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		graphics.drawImage(input, 0, 0, width, height, null);
		graphics.dispose();
		return output;
	}

	private static byte[] toJpegBytes(BufferedImage image, float quality) throws IOException
	{
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		ImageWriteParam param = writer.getDefaultWriteParam();
		param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		param.setCompressionQuality(quality);

		try (ImageOutputStream stream = ImageIO.createImageOutputStream(output))
		{
			writer.setOutput(stream);
			writer.write(null, new IIOImage(image, null, null), param);
		}
		finally
		{
			writer.dispose();
		}

		return output.toByteArray();
	}
}
