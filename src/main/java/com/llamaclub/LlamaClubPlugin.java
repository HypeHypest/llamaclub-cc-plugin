package com.llamaclub;

import com.google.inject.Binder;
import com.google.inject.Singleton;
import com.llamaclub.api.WebhookClient;
import com.llamaclub.auth.AccountIdentity;
import com.llamaclub.combat.BarrowsBrotherCombatTracker;
import com.llamaclub.combat.LunarMoonCombatTracker;
import com.llamaclub.combat.BossFightTracker;
import com.llamaclub.combat.SpecAttributionService;
import com.llamaclub.events.ClueNotifier;
import com.llamaclub.events.CollectionLogNotifier;
import com.llamaclub.events.CombatAchievementNotifier;
import com.llamaclub.events.DeathNotifier;
import com.llamaclub.events.DiaryNotifier;
import com.llamaclub.events.KillCountNotifier;
import com.llamaclub.events.LevelNotifier;
import com.llamaclub.events.LootNotifier;
import com.llamaclub.events.PetNotifier;
import com.llamaclub.events.QuestNotifier;
import com.llamaclub.loot.RecentLootTracker;
import com.llamaclub.service.SyncService;
import com.llamaclub.sync.CombatAchievementBossWidgetListener;
import com.llamaclub.ui.CodeWordOverlay;
import com.llamaclub.ui.LlamaClubPanel;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.api.events.GameTick;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Llama Club",
	description = "Sync player data and send clan notifications to llamaclub.co.uk",
	tags = {"clan", "llama", "llamaclub", "webhook"}
)
public class LlamaClubPlugin extends Plugin
{
	@Inject
	private EventBus eventBus;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private LlamaClubConfig config;

	@Inject
	private LlamaClubPanel panel;

	@Inject
	private SyncService syncService;

	@Inject
	private WebhookClient webhookClient;

	@Inject
	private LootNotifier lootNotifier;

	@Inject
	private PetNotifier petNotifier;

	@Inject
	private LevelNotifier levelNotifier;

	@Inject
	private QuestNotifier questNotifier;

	@Inject
	private KillCountNotifier killCountNotifier;

	@Inject
	private BarrowsBrotherCombatTracker barrowsBrotherCombatTracker;

	@Inject
	private LunarMoonCombatTracker lunarMoonCombatTracker;

	@Inject
	private BossFightTracker bossFightTracker;

	@Inject
	private SpecAttributionService specAttributionService;

	@Inject
	private ClueNotifier clueNotifier;

	@Inject
	private DiaryNotifier diaryNotifier;

	@Inject
	private CombatAchievementNotifier combatAchievementNotifier;

	@Inject
	private CollectionLogNotifier collectionLogNotifier;

	@Inject
	private DeathNotifier deathNotifier;

	@Inject
	private RecentLootTracker recentLootTracker;

	@Inject
	private ClientThread clientThread;

	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private CodeWordOverlay codeWordOverlay;

	@Inject
	private AccountIdentity accountIdentity;

	@Inject
	private CombatAchievementBossWidgetListener combatAchievementBossWidgetListener;

	private NavigationButton navButton;
	private GameState previousGameState;
	private int adminCheckTickCounter;
	private volatile boolean sessionEndSyncDispatched;

	@Override
	public void configure(Binder binder)
	{
		binder.bind(LlamaClubConfig.class).to(LlamaClubConfigImpl.class).in(Singleton.class);
	}

	@Override
	protected void startUp()
	{
		BufferedImage icon = loadIcon();

		navButton = NavigationButton.builder()
			.tooltip("Llama Club")
			.icon(icon)
			.priority(5)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		overlayManager.add(codeWordOverlay);
		registerNotifiers();
		levelNotifier.resetSession();
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			levelNotifier.beginLoginHydration();
		}
		previousGameState = client.getGameState();
		sessionEndSyncDispatched = false;
		panel.reloadPersistedLastSyncAt();
		panel.refreshStatus();
		panel.refreshAdminAccess();
		adminCheckTickCounter = 0;
		log.info("Llama Club plugin started");
	}

	@Override
	protected void shutDown()
	{
		if (config.syncOnLogout() && !sessionEndSyncDispatched)
		{
			sessionEndSyncDispatched = true;
			syncService.syncOnSessionEnd(success ->
			{
				if (success)
				{
					panel.recordSuccessfulSync();
				}
			});
		}

		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		unregisterNotifiers();
		levelNotifier.resetSession();
		overlayManager.remove(codeWordOverlay);
		log.info("Llama Club plugin stopped");
	}

	private void registerNotifiers()
	{
		eventBus.register(lootNotifier);
		eventBus.register(petNotifier);
		eventBus.register(levelNotifier);
		eventBus.register(questNotifier);
		eventBus.register(killCountNotifier);
		eventBus.register(barrowsBrotherCombatTracker);
		eventBus.register(lunarMoonCombatTracker);
		eventBus.register(bossFightTracker);
		eventBus.register(specAttributionService);
		eventBus.register(clueNotifier);
		eventBus.register(diaryNotifier);
		eventBus.register(combatAchievementNotifier);
		eventBus.register(collectionLogNotifier);
		eventBus.register(deathNotifier);
		eventBus.register(recentLootTracker);
		eventBus.register(combatAchievementBossWidgetListener);
	}

	private void unregisterNotifiers()
	{
		eventBus.unregister(lootNotifier);
		eventBus.unregister(petNotifier);
		eventBus.unregister(levelNotifier);
		eventBus.unregister(questNotifier);
		eventBus.unregister(killCountNotifier);
		eventBus.unregister(barrowsBrotherCombatTracker);
		eventBus.unregister(lunarMoonCombatTracker);
		eventBus.unregister(bossFightTracker);
		eventBus.unregister(specAttributionService);
		eventBus.unregister(clueNotifier);
		eventBus.unregister(diaryNotifier);
		eventBus.unregister(combatAchievementNotifier);
		eventBus.unregister(collectionLogNotifier);
		eventBus.unregister(deathNotifier);
		eventBus.unregister(recentLootTracker);
		eventBus.unregister(combatAchievementBossWidgetListener);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState newState = event.getGameState();

		if (GameSessionStates.shouldEndPlaySession(previousGameState, newState))
		{
			levelNotifier.flushPendingExperience();
		}

		if (config.syncOnLogout() && GameSessionStates.shouldEndPlaySession(previousGameState, newState)
			&& !sessionEndSyncDispatched)
		{
			sessionEndSyncDispatched = true;
			syncService.syncOnSessionEnd(success ->
			{
				if (success)
				{
					panel.recordSuccessfulSync();
				}
				else
				{
					log.debug("Logout sync did not complete successfully");
				}
			});
		}

		if (newState == GameState.LOGGED_IN)
		{
			sessionEndSyncDispatched = false;
			panel.reloadPersistedLastSyncAt();
			adminCheckTickCounter = 0;
			panel.refreshAdminAccess();
			webhookClient.fetchBingoLootWhitelistAsync();
			webhookClient.fetchPluginFilterSettingsAsync();
		}

		previousGameState = newState;
		panel.refreshStatus();
	}

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		accountIdentity.refreshCachedUsername();

		if (adminCheckTickCounter < 10)
		{
			adminCheckTickCounter++;
			if (adminCheckTickCounter == 5 || adminCheckTickCounter == 10)
			{
				panel.refreshAdminAccess();
			}
		}
	}

	private BufferedImage loadIcon()
	{
		try
		{
			return ImageUtil.loadImageResource(getClass(), "llama_icon.png");
		}
		catch (IllegalArgumentException e)
		{
			log.warn("Missing llama_icon.png, using default sidebar icon");
			return createDefaultIcon();
		}
	}

	private static BufferedImage createDefaultIcon()
	{
		BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = icon.createGraphics();
		graphics.setColor(new Color(183, 142, 66));
		graphics.fillOval(2, 2, 12, 12);
		graphics.dispose();
		return icon;
	}
}
