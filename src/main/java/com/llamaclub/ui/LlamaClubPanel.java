package com.llamaclub.ui;

import com.llamaclub.LlamaClubConfig;
import com.llamaclub.clan.ClanAdminService;
import com.llamaclub.clue.ClueTier;
import com.llamaclub.service.ClanSyncService;
import com.llamaclub.service.SyncService;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.net.URI;
import javax.inject.Inject;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import com.formdev.flatlaf.FlatClientProperties;
import com.llamaclub.ui.constants.UIConstants;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public class LlamaClubPanel extends PluginPanel
{
	private static final String LAST_SYNC_KEY = "lastSyncAt";
	private static final String LAST_CLAN_SYNC_KEY = "lastClanSyncAt";

	private final LlamaClubConfig config;
	private final ConfigManager configManager;
	private final SyncService syncService;
	private final ClanSyncService clanSyncService;
	private final ClanAdminService clanAdminService;
	private final ClientThread clientThread;

	private final JLabel statusLabel = new JLabel();
	private final JLabel syncLabel = new JLabel();
	private final JLabel errorLabel = new JLabel();
	private final JLabel clanSyncLabel = new JLabel();
	private final JLabel clanSyncErrorLabel = new JLabel();
	private final JPanel adminSectionContainer = new JPanel();

	private long lastSyncAt;
	private long lastClanSyncAt;
	private int lastClanSyncMemberCount;
	private int lastClanSyncJoinDateCount;
	private String lastError;
	private String lastClanSyncError;

	@Inject
	public LlamaClubPanel(
		LlamaClubConfig config,
		ConfigManager configManager,
		SyncService syncService,
		ClanSyncService clanSyncService,
		ClanAdminService clanAdminService,
		ClientThread clientThread
	)
	{
		this.config = config;
		this.configManager = configManager;
		this.syncService = syncService;
		this.clanSyncService = clanSyncService;
		this.clanAdminService = clanAdminService;
		this.clientThread = clientThread;
		this.lastSyncAt = loadPersistedTimestamp(LAST_SYNC_KEY);
		this.lastClanSyncAt = loadPersistedTimestamp(LAST_CLAN_SYNC_KEY);

		buildPanel();
		styleScrollPane();
		refreshStatus();
		refreshAdminAccess();
	}

	@Override
	public void onActivate()
	{
		styleScrollPane();
	}

	private void styleScrollPane()
	{
		JPanel wrappedPanel = getWrappedPanel();
		wrappedPanel.setBorder(null);
		wrappedPanel.setOpaque(true);
		wrappedPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JScrollPane scrollPane = getScrollPane();
		if (scrollPane == null)
		{
			return;
		}

		scrollPane.setBorder(null);
		scrollPane.setViewportBorder(null);
		scrollPane.setOpaque(true);
		scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrollPane.getViewport().setOpaque(true);
		scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);

		JScrollBar scrollBar = scrollPane.getVerticalScrollBar();
		scrollBar.putClientProperty(FlatClientProperties.SCROLL_BAR_SHOW_BUTTONS, false);
		scrollBar.putClientProperty(FlatClientProperties.STYLE,
			"width: 7;"
				+ "track: #" + toHex(ColorScheme.SCROLL_TRACK_COLOR) + ";"
				+ "thumb: #" + toHex(ColorScheme.MEDIUM_GRAY_COLOR) + ";"
				+ "hoverThumbColor: #" + toHex(ColorScheme.LIGHT_GRAY_COLOR));
		scrollBar.updateUI();
		scrollBar.setOpaque(true);
		scrollBar.setBackground(ColorScheme.SCROLL_TRACK_COLOR);
	}

	private static String toHex(Color color)
	{
		return String.format("%06x", color.getRGB() & 0xFFFFFF);
	}

	private void buildPanel()
	{
		JPanel statusBlock = new JPanel();
		statusBlock.setLayout(new net.runelite.client.ui.DynamicGridLayout(0, 1, 0, 4));
		statusBlock.setBackground(UIConstants.CARD_BG);
		statusBlock.setBorder(javax.swing.BorderFactory.createCompoundBorder(
			javax.swing.BorderFactory.createMatteBorder(0, 0, 1, 0, UIConstants.DIVIDER_COLOR),
			new javax.swing.border.EmptyBorder(8, 8, 8, 8)
		));
		statusBlock.add(LlamaClubPanelUi.styleStatusLabel(statusLabel));
		statusBlock.add(LlamaClubPanelUi.styleMutedStatusLabel(syncLabel));
		statusBlock.add(LlamaClubPanelUi.styleMutedStatusLabel(errorLabel));
		add(statusBlock);

		LlamaClubPanelUi.Section connection = LlamaClubPanelUi.createSection(
			this,
			"Connection",
			"Authentication",
			false
		);
		connection.add(LlamaClubPanelUi.createTextRow(
			configManager,
			"Plugin token",
			"pluginToken",
			"Generate this from your profile page on llamaclub.co.uk",
			config.pluginToken(),
			true
		));

		LlamaClubPanelUi.Section loot = LlamaClubPanelUi.createSection(
			this,
			"Loot filters",
			"Configure which loot drops trigger notifications",
			false
		);
		loot.add(LlamaClubPanelUi.createTextRow(
			configManager,
			"Item whitelist",
			"lootWhitelist",
			"Comma-separated item names that always notify regardless of value",
			config.lootWhitelist(),
			false
		));
		loot.add(LlamaClubPanelUi.createBooleanRow(
			configManager,
			"Include untradeables",
			"lootIncludeUntradeables",
			"Use HA value instead of GE value when checking untradeable items against the minimum",
			config.lootIncludeUntradeables(),
			null
		));

		LlamaClubPanelUi.Section clue = LlamaClubPanelUi.createSection(
			this,
			"Clue scroll filters",
			"Configure which clue completions trigger notifications",
			false
		);
		for (ClueTier tier : ClueTier.values())
		{
			clue.add(LlamaClubPanelUi.createBooleanRow(
				configManager,
				tier.getDisplayName(),
				tier.getConfigKey(),
				"Send notifications for " + tier.getDisplayName().toLowerCase() + " clue scroll completions",
				tier.isEnabled(config),
				null
			));
		}
		LlamaClubPanelUi.Section events = LlamaClubPanelUi.createSection(
			this,
			"Event notifications",
			"Toggle which events are sent to the website",
			false
		);
		events.add(eventToggle("Loot drops", "notifyLoot", config.notifyLoot(), "Track valuable loot drops"));
		events.add(eventToggle("Pet drops", "notifyPet", config.notifyPet(), "Track pet drops"));
		events.add(eventToggle("Level ups", "notifyLevel", config.notifyLevel(), "Track skill level ups"));
		events.add(eventToggle("Experience gains", "notifyExperience", config.notifyExperience(), "Track batched experience gains (every 60 seconds)"));
		events.add(eventToggle("Quest completions", "notifyQuest", config.notifyQuest(), "Track quest completions"));
		events.add(eventToggle("Boss kills", "notifyKillCount", config.notifyKillCount(), "Track boss KC and speed times on every kill"));
		events.add(eventToggle("Clue scrolls", "notifyClue", config.notifyClue(), "Track clue scroll completions"));
		events.add(eventToggle("Achievement diaries", "notifyDiary", config.notifyDiary(), "Track achievement diary tier completions"));
		events.add(eventToggle("Combat achievements", "notifyCombatAchievement", config.notifyCombatAchievement(), "Track combat achievement task completions"));
		events.add(eventToggle("Collection log", "notifyCollection", config.notifyCollection(), "Track new collection log items"));
		events.add(eventToggle("Player deaths", "notifyDeath", config.notifyDeath(), "Track player deaths"));
		events.add(LlamaClubPanelUi.createTextRow(
			configManager,
			"Collection log denylist",
			"collectionDenylist",
			"Comma-separated item names (supports * wildcards) excluded from collection log notifications",
			config.collectionDenylist(),
			false
		));

		LlamaClubPanelUi.Section screenshots = LlamaClubPanelUi.createSection(
			this,
			"Screenshot Settings",
			"Configure how event screenshots are captured",
			false
		);
		screenshots.add(LlamaClubPanelUi.createBooleanRow(
			configManager,
			"Include sidebar",
			"screenshotIncludeSidebar",
			"Include the plugin sidebar in screenshots (title bar is always excluded)",
			config.screenshotIncludeSidebar(),
			null
		));
		screenshots.add(LlamaClubPanelUi.createBooleanRow(
			configManager,
			"Hide private messages",
			"screenshotHidePrivateMessages",
			"Hide split private message tabs from screenshots. Only applies when Split private chat is enabled in OSRS settings. The PM area may briefly flicker during capture.",
			config.screenshotHidePrivateMessages(),
			null
		));

		LlamaClubPanelUi.Section codeword = LlamaClubPanelUi.createSection(
			this,
			"Event codeword",
			"Display a moveable codeword overlay on your screen",
			false
		);
		codeword.add(LlamaClubPanelUi.createBooleanRow(
			configManager,
			"Display codeword",
			"displayCodeword",
			"Show the configured codeword overlay in-game",
			config.displayCodeword(),
			null
		));
		codeword.add(LlamaClubPanelUi.createTextRow(
			configManager,
			"Codeword",
			"configuredCodeword",
			"The codeword text shown on the overlay",
			config.configuredCodeword(),
			false
		));
		codeword.add(LlamaClubPanelUi.createBooleanRow(
			configManager,
			"Show timestamp",
			"showCodewordTimestamp",
			"Append the current UTC date and time next to the codeword",
			config.showCodewordTimestamp(),
			null
		));
		codeword.add(LlamaClubPanelUi.createTextRow(
			configManager,
			"Codeword color",
			"codewordColor",
			"Hex color for the codeword text (e.g. #FD8F00)",
			config.codewordColor(),
			false
		));
		codeword.add(LlamaClubPanelUi.createTextRow(
			configManager,
			"Timestamp color",
			"timestampColor",
			"Hex color for the timestamp text (e.g. #FF9A00)",
			config.timestampColor(),
			false
		));

		LlamaClubPanelUi.Section sync = LlamaClubPanelUi.createSection(
			this,
			"Sync",
			"Player data sync settings",
			false
		);
		JPanel syncActions = new JPanel();
		syncActions.setLayout(new BoxLayout(syncActions, BoxLayout.Y_AXIS));
		syncActions.setOpaque(false);
		LlamaClubPanelUi.constrainSectionWidth(syncActions);
		JButton syncNow = LlamaClubPanelUi.createPanelButton("Sync now", this::runSync);
		JButton openWebsite = LlamaClubPanelUi.createPanelButton("Open website", this::openWebsite);
		LlamaClubPanelUi.constrainFullWidthButton(syncNow);
		LlamaClubPanelUi.constrainFullWidthButton(openWebsite);
		syncActions.add(syncNow);
		JComponent syncButtonGap = (JComponent) Box.createVerticalStrut(8);
		syncButtonGap.setAlignmentX(Component.LEFT_ALIGNMENT);
		syncActions.add(syncButtonGap);
		syncActions.add(openWebsite);
		sync.add(syncActions);

		buildAdminSection();
		add(adminSectionContainer);
		setAdminSectionVisible(false);
	}

	private void buildAdminSection()
	{
		adminSectionContainer.setLayout(new BoxLayout(adminSectionContainer, BoxLayout.Y_AXIS));
		adminSectionContainer.setOpaque(false);
		LlamaClubPanelUi.constrainSectionWidth(adminSectionContainer);

		LlamaClubPanelUi.Section admin = LlamaClubPanelUi.createSection(
			adminSectionContainer,
			"Admin",
			"Clan owner and deputy owner tools",
			false
		);

		JLabel accessLabel = LlamaClubPanelUi.styleMutedStatusLabel(new JLabel("Clan Owner / Deputy access"));
		admin.add(wrapLabel(accessLabel));

		JPanel clanSyncActions = new JPanel();
		clanSyncActions.setLayout(new BoxLayout(clanSyncActions, BoxLayout.Y_AXIS));
		clanSyncActions.setOpaque(false);
		LlamaClubPanelUi.constrainSectionWidth(clanSyncActions);

		JButton syncClanMembers = LlamaClubPanelUi.createPanelButton("Sync Clan Members", this::confirmClanSync);
		LlamaClubPanelUi.constrainFullWidthButton(syncClanMembers);
		clanSyncActions.add(syncClanMembers);

		JComponent clanButtonGap = (JComponent) Box.createVerticalStrut(8);
		clanButtonGap.setAlignmentX(Component.LEFT_ALIGNMENT);
		clanSyncActions.add(clanButtonGap);
		clanSyncActions.add(wrapLabel(LlamaClubPanelUi.styleMutedStatusLabel(clanSyncLabel)));
		clanSyncActions.add(wrapLabel(LlamaClubPanelUi.styleMutedStatusLabel(clanSyncErrorLabel)));
		admin.add(clanSyncActions);
	}

	private JPanel wrapLabel(JLabel label)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setOpaque(false);
		LlamaClubPanelUi.constrainSectionWidth(row);
		row.add(label);
		return row;
	}

	public void refreshAdminAccess()
	{
		clientThread.invokeLater(() ->
		{
			boolean canAccess = clanAdminService.canAccessAdmin();
			SwingUtilities.invokeLater(() -> setAdminSectionVisible(canAccess));
		});
	}

	private void setAdminSectionVisible(boolean visible)
	{
		adminSectionContainer.setVisible(visible);
		if (visible)
		{
			refreshClanSyncStatus();
		}
		revalidate();
		repaint();
	}

	private void refreshClanSyncStatus()
	{
		if (lastClanSyncAt > 0)
		{
			String when = new java.util.Date(lastClanSyncAt).toString();
			if (lastClanSyncMemberCount > 0)
			{
				clanSyncLabel.setText(String.format(
					"Last clan sync: %s (%d/%d join dates)",
					when,
					lastClanSyncJoinDateCount,
					lastClanSyncMemberCount
				));
			}
			else
			{
				clanSyncLabel.setText("Last clan sync: " + when);
			}
		}
		else
		{
			clanSyncLabel.setText("Last clan sync: never");
		}
		clanSyncErrorLabel.setText(lastClanSyncError != null && !lastClanSyncError.isBlank()
			? "Error: " + lastClanSyncError
			: " ");
		clanSyncErrorLabel.setForeground(
			lastClanSyncError != null && !lastClanSyncError.isBlank() ? UIConstants.ERROR : UIConstants.TEXT_MUTED
		);
	}

	private JPanel eventToggle(String label, String key, boolean initialValue, String description)
	{
		return LlamaClubPanelUi.createBooleanRow(
			configManager,
			label,
			key,
			description,
			initialValue,
			null
		);
	}

	private void confirmClanSync()
	{
		int choice = JOptionPane.showConfirmDialog(
			this,
			"<html>This will replace the website roster with your in-game clan list.<br><br>"
				+ "Before syncing, open <b>Clan Settings &rarr; Members</b> and set the columns to "
				+ "<b>Rank</b> and <b>Joined</b> so join dates can be read.<br><br>Proceed?</html>",
			"Sync Clan Members",
			JOptionPane.YES_NO_OPTION,
			JOptionPane.WARNING_MESSAGE
		);

		if (choice == JOptionPane.YES_OPTION)
		{
			runClanSync();
		}
	}

	private void runClanSync()
	{
		if (config.pluginToken() == null || config.pluginToken().isBlank())
		{
			lastClanSyncError = "Add your plugin token from the website profile page";
			refreshClanSyncStatus();
			return;
		}

		clanSyncService.syncNow((success, error) -> SwingUtilities.invokeLater(() ->
		{
			if (success)
			{
				recordSuccessfulClanSync();
			}
			else
			{
				if (error != null && !error.isBlank())
				{
					lastClanSyncError = error;
				}
				else
				{
					String webhookUrl = config.webhookUrl();
					if (webhookUrl != null && webhookUrl.contains("/tokens"))
					{
						lastClanSyncError = "Webhook URL must be /api/runelite/ingest (not /tokens)";
					}
					else
					{
						lastClanSyncError = "Clan sync failed — check webhook URL and that the site is running";
					}
				}
				refreshClanSyncStatus();
			}
		}));
	}

	private void runSync()
	{
		if (config.pluginToken() == null || config.pluginToken().isBlank())
		{
			setLastError("Add your plugin token from the website profile page");
			refreshStatus();
			return;
		}

		syncService.syncNow((success, error) -> SwingUtilities.invokeLater(() ->
		{
			if (success)
			{
				recordSuccessfulSync();
			}
			else
			{
				if (error != null && !error.isBlank())
				{
					setLastError(error);
				}
				else
				{
					String webhookUrl = config.webhookUrl();
					if (webhookUrl != null && webhookUrl.contains("/tokens"))
					{
						setLastError("Webhook URL must be /api/runelite/ingest (not /tokens)");
					}
					else
					{
						setLastError("Sync failed — check webhook URL and that the site is running");
					}
				}
				refreshStatus();
			}
		}));
	}

	public void refreshStatus()
	{
		boolean tokenConfigured = config.pluginToken() != null && !config.pluginToken().isBlank();
		statusLabel.setText(tokenConfigured ? "Status: Token configured" : "Status: Token missing — generate one on the website");
		syncLabel.setText(lastSyncAt > 0 ? "Last sync: " + new java.util.Date(lastSyncAt) : "Last sync: never");
		errorLabel.setText(lastError != null && !lastError.isBlank() ? "Error: " + lastError : " ");
		errorLabel.setForeground(
			lastError != null && !lastError.isBlank() ? UIConstants.ERROR : UIConstants.TEXT_MUTED
		);
	}

	public void reloadPersistedLastSyncAt()
	{
		this.lastSyncAt = loadPersistedTimestamp(LAST_SYNC_KEY);
		SwingUtilities.invokeLater(this::refreshStatus);
	}

	public void recordSuccessfulSync()
	{
		long now = System.currentTimeMillis();
		this.lastSyncAt = now;
		persistTimestamp(LAST_SYNC_KEY, now);
		setLastError(null);
		SwingUtilities.invokeLater(this::refreshStatus);
	}

	public void recordSuccessfulClanSync()
	{
		long now = System.currentTimeMillis();
		this.lastClanSyncAt = now;
		this.lastClanSyncMemberCount = clanSyncService.getLastCollectedMemberCount();
		this.lastClanSyncJoinDateCount = clanSyncService.getLastCollectedJoinDateCount();
		persistTimestamp(LAST_CLAN_SYNC_KEY, now);
		lastClanSyncError = null;
		SwingUtilities.invokeLater(this::refreshClanSyncStatus);
	}

	public void setLastError(String lastError)
	{
		this.lastError = lastError;
		SwingUtilities.invokeLater(this::refreshStatus);
	}

	private long loadPersistedTimestamp(String key)
	{
		Long profileSync = configManager.getRSProfileConfiguration(
			LlamaClubConfig.GROUP, key, long.class);
		if (profileSync != null && profileSync > 0)
		{
			return profileSync;
		}

		String globalSync = configManager.getConfiguration(LlamaClubConfig.GROUP, key);
		if (globalSync != null && !globalSync.isBlank())
		{
			try
			{
				long parsed = Long.parseLong(globalSync);
				if (parsed > 0)
				{
					return parsed;
				}
			}
			catch (NumberFormatException ignored)
			{
				// Ignore invalid stored value
			}
		}

		return 0;
	}

	private void persistTimestamp(String key, long timestamp)
	{
		if (configManager.getRSProfileKey() != null)
		{
			configManager.setRSProfileConfiguration(LlamaClubConfig.GROUP, key, timestamp);
		}
		else
		{
			configManager.setConfiguration(LlamaClubConfig.GROUP, key, Long.toString(timestamp));
		}
	}

	private void openWebsite()
	{
		try
		{
			Desktop.getDesktop().browse(URI.create(config.websiteUrl() + "/profile"));
		}
		catch (Exception ignored)
		{
			// Desktop may be unavailable in some environments
		}
	}
}
