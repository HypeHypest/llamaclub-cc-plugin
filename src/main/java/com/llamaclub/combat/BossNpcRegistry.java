package com.llamaclub.combat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.gameval.NpcID;

/**
 * Maps boss NPC ids and names to leaderboard boss keys from event_botw_bosses.php.
 */
public final class BossNpcRegistry
{
	public static final String BARROWS_CHESTS = "Barrows Chests";
	public static final String LUNAR_CHEST = "Lunar Chest";

	private static final Map<Integer, String> NPC_ID_TO_BOSS = new HashMap<>();
	private static final Map<String, String> NPC_NAME_TO_BOSS = new HashMap<>();
	private static final Set<String> MULTI_PHASE_BOSSES = new HashSet<>();
	private static final Map<String, Set<Integer>> TERMINAL_NPC_IDS = new HashMap<>();
	private static final Set<String> EXCLUDED_BOSS_KEYS = new HashSet<>();
	private static final Set<String> RAID_BOSS_KEYS = new HashSet<>();

	static
	{
		registerRaidsAndExcluded();

		map("Abyssal Sire",
			NpcID.ABYSSALSIRE_SIRE_STASIS_AWAKE,
			NpcID.ABYSSALSIRE_SIRE_STASIS_STUNNED,
			NpcID.ABYSSALSIRE_SIRE_WANDERING,
			NpcID.ABYSSALSIRE_SIRE_PANICKING,
			NpcID.ABYSSALSIRE_SIRE_APOCALYPSE
		);
		mapMultiPhase("Alchemical Hydra",
			terminal(NpcID.HYDRABOSS_FINALDEATH),
			NpcID.HYDRABOSS,
			NpcID.HYDRABOSS_P1_TRANSITION,
			NpcID.HYDRABOSS_P2_TRANSITION,
			NpcID.HYDRABOSS_P3_TRANSITION,
			NpcID.HYDRABOSS_4,
			NpcID.HYDRABOSS_3,
			NpcID.HYDRABOSS_2,
			NpcID.HYDRABOSS_FINALDEATH
		);
		mapName("Amoxliatl", "Amoxliatl");
		map("Araxxor", NpcID.ARAXXOR, NpcID.ARAXXOR_DEAD);
		map("Artio", NpcID.CALLISTO_SINGLES);
		map("Bryophyta", NpcID.GB_MOSSGIANT);
		mapName("Brutus", "Brutus");
		mapName("Brutus", "Demonic Brutus");
		map("Brutus", 15626);
		map("Callisto", NpcID.CALLISTO);
		mapMultiPhase("Calvar'ion",
			terminal(NpcID.VETION_SINGLE, NpcID.VETION_2_SINGLE, NpcID.VETION_TRANS_SINGLE, NpcID.VETION_TRANS_2_SINGLE),
			NpcID.VETION_SINGLE,
			NpcID.VETION_2_SINGLE,
			NpcID.VETION_TRANS_SINGLE,
			NpcID.VETION_TRANS_2_SINGLE
		);
		map("Cerberus", NpcID.CERBERUS_ATTACKING, NpcID.CERBERUS_SITTING, NpcID.CERBERUS_RESETTING);
		map("Chaos Elemental", NpcID.CHAOSELEMENTAL);
		map("Chaos Fanatic", NpcID.CHAOS_FANATIC);
		map("Commander Zilyana", NpcID.GODWARS_SARADOMIN_AVATAR);
		map("Corporeal Beast", NpcID.CORP_BEAST);
		map("Crazy Archaeologist", NpcID.CRAZY_ARCHAEOLOGIST);
		map("Dagannoth Prime", NpcID.DAGCAVE_MAGIC_BOSS);
		map("Dagannoth Rex", NpcID.DAGCAVE_MELEE_BOSS);
		map("Dagannoth Supreme", NpcID.DAGCAVE_RANGED_BOSS);
		map("Deranged Archaeologist", NpcID.FOSSIL_CRAZY_ARCHAEOLOGIST);
		mapName("Doom of Mokhaiotl", "Doom of Mokhaiotl");
		map("Duke Sucellus",
			NpcID.DUKE_SUCELLUS_AWAKE,
			NpcID.DUKE_SUCELLUS_AWAKE_QUEST,
			NpcID.DUKE_SUCELLUS_DEAD,
			NpcID.DUKE_SUCELLUS_DEAD_QUEST
		);
		map("General Graardor", NpcID.GODWARS_BANDOS_AVATAR);
		map("Giant Mole", NpcID.MOLE_GIANT);
		mapMultiPhase("Grotesque Guardians",
			terminal(NpcID.GARGBOSS_DUSK_DEATH),
			NpcID.GARGBOSS_DUSK_PHASE1_DEFENSIVE,
			NpcID.GARGBOSS_DAWN_PHASE1,
			NpcID.GARGBOSS_DUSK_PHASE2_ATTACKING,
			NpcID.GARGBOSS_DUSK_PHASE3_DEFENSIVE,
			NpcID.GARGBOSS_DAWN_PHASE3,
			NpcID.GARGBOSS_DUSK_PHASE4,
			NpcID.GARGBOSS_DUSK_DEATH
		);
		mapName("Hueycoatl", "Hueycoatl");
		map("Kraken", NpcID.SLAYER_KRAKEN_BOSS);
		map("K'ril Tsutsaroth", NpcID.GODWARS_ZAMORAK_AVATAR);
		map("Kalphite Queen", NpcID.KALPHITE_QUEEN, NpcID.KALPHITE_FLYINGQUEEN);
		map("King Black Dragon", NpcID.KING_DRAGON);
		map("Kree'arra", NpcID.GODWARS_ARMADYL_AVATAR);
		map("Nex", NpcID.NEX);
		map("Obor", NpcID.HILLGIANT_BOSS);
		mapMultiPhase("Phantom Muspah",
			terminal(NpcID.MUSPAH_FINAL, NpcID.MUSPAH_FINAL_QUEST),
			NpcID.MUSPAH,
			NpcID.MUSPAH_MELEE,
			NpcID.MUSPAH_SOULSPLIT,
			NpcID.MUSPAH_FINAL,
			NpcID.MUSPAH_QUEST,
			NpcID.MUSPAH_MELEE_QUEST,
			NpcID.MUSPAH_SOULSPLIT_QUEST,
			NpcID.MUSPAH_FINAL_QUEST
		);
		mapMultiPhase("Royal Titans",
			terminal(NpcID.RT_FIRE_QUEEN_INACTIVE, NpcID.RT_ICE_KING_INACTIVE),
			NpcID.RT_FIRE_QUEEN,
			NpcID.RT_FIRE_QUEEN_INACTIVE,
			NpcID.RT_ICE_KING_INACTIVE
		);
		map("Sarachnis", NpcID.SARACHNIS);
		map("Scorpia", NpcID.SCORPIA);
		map("Scurrius", NpcID.RAT_BOSS_NORMAL, NpcID.RAT_BOSS_INSTANCE);
		map("Shellbane Gryphon", NpcID.GRYPHON_BOSS);
		map("Skotizo", NpcID.CATA_BOSS);
		mapName("Sol Heredit", "Sol Heredit");
		map("Spindel", NpcID.VENENATIS_SINGLES);
		map("The Leviathan", NpcID.LEVIATHAN, NpcID.LEVIATHAN_QUEST);
		mapName("The Mimic", "The Mimic");
		mapMultiPhase("The Nightmare",
			terminal(NpcID.NIGHTMARE_DYING, NpcID.NIGHTMARE_CHALLENGE_DYING),
			NpcID.NIGHTMARE_PHASE_01,
			NpcID.NIGHTMARE_PHASE_02,
			NpcID.NIGHTMARE_PHASE_03,
			NpcID.NIGHTMARE_INITIAL,
			NpcID.NIGHTMARE_DYING,
			NpcID.NIGHTMARE_CHALLENGE_PHASE_01,
			NpcID.NIGHTMARE_CHALLENGE_PHASE_02,
			NpcID.NIGHTMARE_CHALLENGE_PHASE_03,
			NpcID.NIGHTMARE_CHALLENGE_INITIAL,
			NpcID.NIGHTMARE_CHALLENGE_DYING
		);
		mapName("Phosani's Nightmare", "Phosani's Nightmare");
		map("The Whisperer",
			NpcID.WHISPERER,
			NpcID.WHISPERER_MELEE,
			NpcID.WHISPERER_QUEST,
			NpcID.WHISPERER_MELEE_QUEST
		);
		map("Thermonuclear Smoke Devil", NpcID.SMOKE_DEVIL_BOSS);
		map("TzTok-Jad", NpcID.TZHAAR_FIGHTCAVE_SWARM_BOSS);
		mapName("TzKal-Zuk", "TzKal-Zuk");
		map("Vardorvis", NpcID.VARDORVIS, NpcID.VARDORVIS_QUEST);
		map("Venenatis", NpcID.VENENATIS);
		map("Vet'ion", NpcID.VETION, NpcID.VETION_2);
		map("Vorkath", NpcID.VORKATH, NpcID.VORKATH_QUEST);
		map("Yama", NpcID.YAMA);
		map("Zulrah",
			NpcID.SNAKEBOSS_BOSS_RANGED,
			NpcID.SNAKEBOSS_BOSS_MELEE,
			NpcID.SNAKEBOSS_BOSS_MAGIC
		);
	}

	private BossNpcRegistry()
	{
	}

	private static void registerRaidsAndExcluded()
	{
		RAID_BOSS_KEYS.add("Chambers of Xeric");
		RAID_BOSS_KEYS.add("Chambers of Xeric: Challenge Mode");
		RAID_BOSS_KEYS.add("Theatre of Blood");
		RAID_BOSS_KEYS.add("Theatre of Blood: Entry Mode");
		RAID_BOSS_KEYS.add("Theatre of Blood: Hard Mode");
		RAID_BOSS_KEYS.add("Tombs of Amascut");
		RAID_BOSS_KEYS.add("Tombs of Amascut: Entry Mode");
		RAID_BOSS_KEYS.add("Tombs of Amascut: Expert Mode");

		EXCLUDED_BOSS_KEYS.addAll(RAID_BOSS_KEYS);
		EXCLUDED_BOSS_KEYS.add(BARROWS_CHESTS);
		EXCLUDED_BOSS_KEYS.add("Wintertodt");
		EXCLUDED_BOSS_KEYS.add("Tempoross");
		EXCLUDED_BOSS_KEYS.add("Zalcano");
		EXCLUDED_BOSS_KEYS.add("Guardians of the Rift");
		EXCLUDED_BOSS_KEYS.add("Fortis Colosseum");
		EXCLUDED_BOSS_KEYS.add("Moons of Peril");
		EXCLUDED_BOSS_KEYS.add("TzHaar-Ket-Rak's Challenges");
		EXCLUDED_BOSS_KEYS.add(LUNAR_CHEST);
		EXCLUDED_BOSS_KEYS.add("Hespori");
		EXCLUDED_BOSS_KEYS.add("Gauntlet");
		EXCLUDED_BOSS_KEYS.add("Corrupted Gauntlet");
		EXCLUDED_BOSS_KEYS.add("Crystalline Hunllef");
		EXCLUDED_BOSS_KEYS.add("Corrupted Hunllef");
	}

	private static Set<Integer> terminal(int... ids)
	{
		Set<Integer> set = new HashSet<>();
		for (int id : ids)
		{
			set.add(id);
		}
		return set;
	}

	private static void map(String bossKey, int... npcIds)
	{
		for (int npcId : npcIds)
		{
			NPC_ID_TO_BOSS.put(npcId, bossKey);
		}
	}

	private static void mapName(String bossKey, String npcName)
	{
		NPC_NAME_TO_BOSS.put(normalizeName(npcName), bossKey);
	}

	private static void mapMultiPhase(String bossKey, Set<Integer> terminalIds, int... npcIds)
	{
		MULTI_PHASE_BOSSES.add(bossKey);
		TERMINAL_NPC_IDS.put(bossKey, terminalIds);
		map(bossKey, npcIds);
	}

	public static String resolveBossKey(NPC npc)
	{
		if (npc == null)
		{
			return null;
		}

		String byId = NPC_ID_TO_BOSS.get(npc.getId());
		if (byId != null)
		{
			return byId;
		}

		String name = npcDisplayName(npc);
		if (name == null)
		{
			return null;
		}

		return NPC_NAME_TO_BOSS.get(normalizeName(name));
	}

	public static String npcDisplayName(NPC npc)
	{
		if (npc == null)
		{
			return null;
		}

		String name = npc.getName();
		if (name != null && !name.isEmpty())
		{
			return name;
		}

		NPCComposition composition = npc.getTransformedComposition();
		if (composition == null)
		{
			composition = npc.getComposition();
		}

		return composition != null ? composition.getName() : null;
	}

	public static String resolveBossKey(int npcId)
	{
		return NPC_ID_TO_BOSS.get(npcId);
	}

	public static String resolveBossKeyByName(String npcName)
	{
		if (npcName == null)
		{
			return null;
		}

		return NPC_NAME_TO_BOSS.get(normalizeName(npcName));
	}

	public static boolean isTrackableBossKey(String bossKey)
	{
		return bossKey != null && !EXCLUDED_BOSS_KEYS.contains(bossKey);
	}

	public static boolean isExcludedRaid(String bossKey)
	{
		return bossKey != null && RAID_BOSS_KEYS.contains(bossKey);
	}

	public static boolean isMultiPhaseBoss(String bossKey)
	{
		return bossKey != null && MULTI_PHASE_BOSSES.contains(bossKey);
	}

	public static boolean isTerminalNpc(String bossKey, int npcId)
	{
		if (bossKey == null)
		{
			return true;
		}

		Set<Integer> terminalIds = TERMINAL_NPC_IDS.get(bossKey);
		if (terminalIds == null)
		{
			return true;
		}

		return terminalIds.contains(npcId);
	}

	static String normalizeName(String name)
	{
		return name.trim().toLowerCase();
	}
}
