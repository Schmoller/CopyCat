
package au.com.addstar.copycat.flags;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;

import au.com.addstar.copycat.Util;
import au.com.addstar.copycat.commands.BadArgumentException;

public class EnumFlag<T extends Enum<T>> extends Flag<T>
{
	private Class<T> mEnumClass;
	private EnumSet<T> mEnum;
	private ArrayList<String> mNameList;
	
	public EnumFlag(Class<T> clazz)
	{
		mEnumClass = clazz;
		mEnum = EnumSet.allOf(clazz);
		mNameList = new ArrayList<String>(mEnum.size());
		for(T e : mEnum)
			mNameList.add(e.name());
	}
	
	public EnumFlag()
	{
	}
	
	@Override
	public T parse( Player sender, String[] args ) throws IllegalArgumentException, BadArgumentException
	{
		if(args.length != 1)
			throw new IllegalArgumentException("<value>");
	
		for(T e : mEnum)
		{
			if(e.name().equalsIgnoreCase(args[0]))
				return e;
		}
		
		throw new BadArgumentException(0, "Unknown value");
	}

	@Override
	public List<String> tabComplete( Player sender, String[] args )
	{
		if(args.length == 1)
			return Util.matchString(args[0], mNameList);
		
		return null;
	}

	@Override
	public void save( ConfigurationSection section )
	{
		section.set("value", value.name());
		section.set("enum", mEnumClass.getName());
	}

	@SuppressWarnings( "unchecked" )
	@Override
	public void read( ConfigurationSection section ) throws InvalidConfigurationException
	{
		String name = section.getString("value");
		for(T e : mEnum)
		{
			if(e.name().equals(name))
			{
				value = e;
				break;
			}
		}
		
		try
		{
			mEnumClass = (Class<T>)Class.forName(section.getString("enum"));
			mEnum = EnumSet.allOf(mEnumClass);
			mNameList = new ArrayList<String>(mEnum.size());
			for(T e : mEnum)
				mNameList.add(e.name());
		}
		catch(Exception e)
		{
			e.printStackTrace();
			throw new InvalidConfigurationException("Could not find enum " + section.getString("enum"));
		}
	}

	@Override
	public String getValueString()
	{
		return value.name();
	}

}
