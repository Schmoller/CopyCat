package au.com.addstar.copycat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.plugin.java.JavaPlugin;

import au.com.addstar.copycat.commands.CopyCatCommand;

import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;

import au.com.mineauz.minigames.PlayerLoadout;
import au.com.mineauz.minigames.gametypes.MinigameType;
import au.com.mineauz.minigames.mechanics.GameMechanics;
import au.com.mineauz.minigames.minigame.Minigame;
import au.com.mineauz.minigames.minigame.modules.LoadoutModule;
import au.com.mineauz.minigames.minigame.modules.LobbySettingsModule;

public class CopyCatPlugin extends JavaPlugin
{
	private HashMap<World, HashMap<String, GameBoard>> mBoards = new HashMap<World, HashMap<String, GameBoard>>();
	private HashBiMap<String, GameBoard> mMinigameToBoard = HashBiMap.create();
	private SubjectStorage mStorage;
	
	public static CopyCatPlugin instance;
	public static final Pattern validNamePattern = Pattern.compile("^[a-zA-Z0-9_]+$");
	public static final Random rand = new Random();
	public static final List<ItemStack> blockTypes;
	
	static
	{
		blockTypes = ImmutableList.<ItemStack>builder()
			.add(new ItemStack(Material.STAINED_CLAY, 64, (short)0))
			.add(new ItemStack(Material.STAINED_CLAY, 64, (short)4))
			.add(new ItemStack(Material.STAINED_CLAY, 64, (short)3))
			.add(new ItemStack(Material.STAINED_CLAY, 64, (short)9))
			.add(new ItemStack(Material.STAINED_CLAY, 64, (short)12))
			.add(new ItemStack(Material.STAINED_CLAY, 64, (short)13))
			.add(new ItemStack(Material.STAINED_CLAY, 64, (short)14))
			.add(new ItemStack(Material.STAINED_CLAY, 64, (short)15))
			.build();
	}
	
	@Override
	public void onEnable()
	{
		instance = this;
		
		if(!getDataFolder().exists())
			getDataFolder().mkdirs();
		
		mStorage = new SubjectStorage(new File(getDataFolder(), "subjects"));
		
		new CopyCatCommand().registerAs(getCommand("copycat"));
		
		GameMechanics.addGameMechanic(new CopyCatLogic());
		
		for(World world : Bukkit.getWorlds())
			loadWorld(world);
		
		Bukkit.getPluginManager().registerEvents(new EventListener(), this);
	}
	
	@Override
	public void onDisable()
	{
	}
	
	public void loadWorld(World world)
	{
		File folder = new File(getDataFolder(), "boards");
		if(!folder.exists())
			return;
		
		HashMap<String, GameBoard> boards = new HashMap<String, GameBoard>();
		mBoards.put(world, boards);
		
		for(File file : folder.listFiles())
		{
			if(file.isFile() && file.getName().toLowerCase().endsWith(".yml") && file.getName().startsWith(world.getName().toLowerCase() + "-"))
			{
				String name = file.getName().split("\\-")[1].toLowerCase();
				name = name.substring(0, name.indexOf(".yml"));
				
				try
				{
					GameBoard board = new GameBoard(file, world);
					boards.put(name, board);
					mMinigameToBoard.put(board.getMinigameId(), board);
				}
				catch(IOException e)
				{
					getLogger().severe("Failed to load GameBoard " + name + " in world " + world.getName());
					e.printStackTrace();
				}
				catch(InvalidConfigurationException e)
				{
					getLogger().severe("Failed to load GameBoard " + name + " in world " + world.getName());
					e.printStackTrace();
				}
			}
		}
	}
	
	public void unloadWorld(World world)
	{
		HashMap<String, GameBoard> boards = mBoards.remove(world);
		if(boards == null)
			return;
		
		for(GameBoard board : boards.values())
			mMinigameToBoard.inverse().remove(board);
		
		boards.clear();
	}
	
	public boolean registerGame(GameBoard board, String name)
	{
		name = name.toLowerCase();
		
		HashMap<String, GameBoard> boards = mBoards.get(board.getWorld());
		if(boards == null)
		{
			boards = new HashMap<String, GameBoard>();
			mBoards.put(board.getWorld(), boards);
		}
		
		if(boards.containsKey(name))
			return false;
		
		boards.put(name, board);
		mMinigameToBoard.put(board.getMinigameId(), board);
		
		File folder = new File(getDataFolder(), "boards");
		if(!folder.exists())
			folder.mkdirs();
		
		try
		{
			board.write(new File(folder, board.getWorld().getName().toLowerCase() + "-" + name + ".yml"));
		}
		catch(IOException e)
		{
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	public boolean saveBoard(String name, World world)
	{
		name = name.toLowerCase();
		GameBoard board = getBoard(name, world);
		if(board == null)
			return false;

		File folder = new File(getDataFolder(), "boards");
		if(!folder.exists())
			folder.mkdirs();
		
		try
		{
			board.write(new File(folder, world.getName().toLowerCase() + "-" + name + ".yml"));
		}
		catch(IOException e)
		{
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	public void deleteBoard(String name, World world) throws IllegalArgumentException
	{
		name = name.toLowerCase();
		GameBoard board = getBoard(name, world); 
		if(board == null)
			throw new IllegalArgumentException("Unknown board " + name + " in " + world.getName());
		
		Minigame game = board.getMinigame();
		
		if(game != null && !game.getPlayers().isEmpty())
			throw new IllegalArgumentException("That game is in progress");
		
		File folder = new File(getDataFolder(), "boards");
		File file = new File(folder, world.getName().toLowerCase() + "-" + name + ".yml");
		
		if(file.exists())
			file.delete();
		
		HashMap<String, GameBoard> boards = mBoards.get(world);
		boards.remove(name);
		if(boards.isEmpty())
			mBoards.remove(world);
		
		mMinigameToBoard.remove(board.getMinigameId());
	}
	
	public GameBoard getBoard(String name, World world)
	{
		HashMap<String, GameBoard> boards = mBoards.get(world);
		if(boards == null)
			return null;
		
		return boards.get(name.toLowerCase());
	}
	
	public GameBoard getBoardByGame(String minigame)
	{
		return mMinigameToBoard.get(minigame);
	}
	
	public static void applyDefaultsForGame(GameBoard board)
	{
		Minigame game = board.getMinigame();
		if(game == null)
			return;
		
		applyDefaults(game);
		game.setMaxPlayers(board.getStationCount());
		
		game.getStartLocations().clear();
		game.addStartLocation(board.getStation(0).getSpawnLocation());
		game.saveMinigame();
	}
	
	public static void applyDefaults(Minigame minigame)
	{
		minigame.setMechanic("CopyCat");
		minigame.setBlocksdrop(true);
		minigame.setCanBlockBreak(true);
		minigame.setCanBlockPlace(true);
		LoadoutModule module = LoadoutModule.getMinigameModule(minigame);
		PlayerLoadout loadout = module.getLoadout("default");
		loadout.clearLoadout();
		loadout.addItem(new ItemStack(Material.DIAMOND_PICKAXE), 0);
		for(int i = 0; i < blockTypes.size(); ++i)
			loadout.addItem(blockTypes.get(i).clone(), i+1);
		
		minigame.setDefaultGamemode(GameMode.SURVIVAL);
		minigame.setGametypeName("Copy Cat");
		minigame.setObjective("Copy the shown pattern");
		LobbySettingsModule.getMinigameModule(minigame).setTeleportOnStart(false);
		minigame.setType(MinigameType.MULTIPLAYER);
		minigame.setLives(3);
	}
	
	public SubjectStorage getSubjectStorage()
	{
		return mStorage;
	}
	
	public List<String> matchBoard(String name, World world)
	{
		name = name.toLowerCase();
		ArrayList<String> names = new ArrayList<String>();
		for(String key : mBoards.get(world).keySet())
		{
			if(key.toLowerCase().startsWith(name))
				names.add(key);
		}
		
		return names;
	}
	
	@SuppressWarnings( "deprecation" )
	public static boolean isValidBlockType(MaterialData data)
	{
		for(ItemStack item : blockTypes)
		{
			if(item.getType() == data.getItemType() && item.getDurability() == data.getData())
				return true;
		}
		
		return false;
	}
}
