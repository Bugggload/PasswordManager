// Tayler Carter
package services;

import java.util.Random;

public class PasswordGenerator
{
	private int defaultlength;
	private String allowedsymbols;

	public PasswordGenerator()
	{
		defaultlength = 12;
		allowedsymbols = "~!@#$%^&*_+?;";
	}

	public String generatePassword(int length, boolean useUpper, boolean useLower, boolean useNumbers, boolean useSymbols)
	{
		if(!validateSettings(length))
		{
			length = defaultlength;
		}

		String pool = getallowedchars(useUpper, useLower, useNumbers, useSymbols);

		if(pool.length() == 0)
		{
			return "";
		}

		Random rand = new Random();
		String password = "";

		for(int i = 0; i < length; i++)
		{
			int randomindex = rand.nextInt(pool.length());
			password += pool.charAt(randomindex);
		}

		return password;
	}

	public boolean validateSettings(int length)
	{
		if(length >= 8 && length <= 64)
		{
			return true;
		}
		else
		{
			return false;
		}
	}

	public void setAllowedSymbols(String symbols)
	{
		if(symbols != null && symbols.length() > 0)
		{
			allowedsymbols = symbols;
		}
	}

	private String getallowedchars(boolean useUpper, boolean useLower, boolean useNumbers, boolean useSymbols)
	{
		String pool = "";

		if(useUpper)
		{
			for(int i = 65; i <= 90; i++)
			{
				pool += (char)i;
			}
		}

		if(useLower)
		{
			for(int i = 97; i <= 122; i++)
			{
				pool += (char)i;
			}
		}

		if(useNumbers)
		{
			for(int i = 48; i <= 57; i++)
			{
				pool += (char)i;
			}
		}

		if(useSymbols)
		{
			pool += allowedsymbols;
		}

		return pool;
	}
}
