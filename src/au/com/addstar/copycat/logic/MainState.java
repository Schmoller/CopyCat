package au.com.addstar.copycat.logic;

import java.util.HashSet;

import com.pauldavdesign.mineauz.minigames.MinigamePlayer;
import com.pauldavdesign.mineauz.minigames.Minigames;
import com.pauldavdesign.mineauz.minigames.minigame.Minigame;

import au.com.addstar.copycat.GameBoard;
import au.com.addstar.copycat.PlayerStation;

public class MainState extends State<GameBoard>
{
	private HashSet<MinigamePlayer> mWaiting = new HashSet<MinigamePlayer>();
	
	@Override
	public void onStart( StateEngine<GameBoard> engine, GameBoard game )
	{
		Minigame minigame = game.getMinigame();
		game.broadcast("Start copying", null);
		mWaiting.clear();
		mWaiting.addAll(minigame.getPlayers());
		
		for(PlayerStation station : game.getStations())
		{
			if(station.getPlayer() != null)
			{
				station.setCanModify(true);
				station.drawSubject();
			}
		}
		
		for(MinigamePlayer player : minigame.getPlayers())
			minigame.getDefaultPlayerLoadout().equiptLoadout(player);
	}
	
	@Override
	public void onEnd( StateEngine<GameBoard> engine, GameBoard game )
	{
		for(PlayerStation station : game.getStations())
		{
			station.setCanModify(false);
			station.clearStation();
		}
		
		Minigame minigame = game.getMinigame();
		MinigamePlayer player = mWaiting.iterator().next();
		player.addDeath();

		player.sendMessage("You did not finish in time. You have lost a life", "error");
		game.broadcast(player.getDisplayName() + " lost a life.", player);
		
		if(player.getDeaths() >= minigame.getLives())
		{
			player.sendMessage("You were eliminated from the game.", "error");
			
			Minigames.plugin.pdata.quitMinigame(player, true);
			if(minigame.getPlayers().size() > 1)
				game.broadcast(player.getDisplayName() + " was eliminated. Only " + (minigame.getPlayers().size() - 1) + " players remain.", player);
			else
				game.broadcast(player.getDisplayName() + " was eliminated.", player);
		}
	}
	
	@Override
	public void onEvent( String name, Object data, StateEngine<GameBoard> engine, GameBoard game )
	{
		if(name.equals("leave"))
		{
			MinigamePlayer player = (MinigamePlayer)data;
			mWaiting.remove(player);
			// TODO: Handle end game?
		}
		else if(name.equals("place"))
		{
			MinigamePlayer player = (MinigamePlayer)data;
			
			PlayerStation station = game.getStation(player);
			if(game.getSubject().matches(station.getPlayLocation(), station.getFacing()))
			{
				game.broadcast(player.getDisplayName() + " has completed the pattern!", null);
				mWaiting.remove(player);
				
				if(mWaiting.size() <= 1)
					engine.setState(new PreRoundState());
			}
		}
	}
}
