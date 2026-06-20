package com.llamaclub.sync;

final class BossTimeFormat
{
	private BossTimeFormat()
	{
	}

	static String formatSeconds(double totalSeconds)
	{
		long hours = (long) (totalSeconds / 3600);
		long minutes = (long) ((totalSeconds % 3600) / 60);
		double seconds = totalSeconds % 60;
		long wholeSeconds = (long) seconds;
		long tenths = Math.round((seconds - wholeSeconds) * 10);

		if (hours > 0)
		{
			return String.format("%d:%02d:%02d.%d", hours, minutes, wholeSeconds, tenths);
		}

		return String.format("%d:%02d.%d", minutes, wholeSeconds, tenths);
	}

	static Double parseTimeString(String time)
	{
		if (time == null)
		{
			return null;
		}

		String trimmed = time.trim().toLowerCase();
		trimmed = trimmed.replace(" ", "");
		trimmed = trimmed.replace('s', ' ');
		trimmed = trimmed.trim();

		if (trimmed.isEmpty())
		{
			return null;
		}

		if (trimmed.contains(":"))
		{
			String[] parts = trimmed.split(":");
			if (parts.length == 2)
			{
				try
				{
					int minutes = Integer.parseInt(parts[0]);
					double seconds = Double.parseDouble(parts[1]);
					return (minutes * 60D) + seconds;
				}
				catch (NumberFormatException ignored)
				{
					return null;
				}
			}

			if (parts.length == 3)
			{
				try
				{
					int hours = Integer.parseInt(parts[0]);
					int minutes = Integer.parseInt(parts[1]);
					double seconds = Double.parseDouble(parts[2]);
					return (hours * 3600D) + (minutes * 60D) + seconds;
				}
				catch (NumberFormatException ignored)
				{
					return null;
				}
			}
		}

		try
		{
			return Double.parseDouble(trimmed);
		}
		catch (NumberFormatException ignored)
		{
			return null;
		}
	}
}
