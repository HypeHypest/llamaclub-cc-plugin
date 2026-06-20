/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 */
package com.llamaclub.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class LootMathUtils
{
	private static final int[] FACTORIALS;

	public double binomialProbability(double p, int nTrials, int kSuccess)
	{
		return binomialCoefficient(nTrials, kSuccess) * Math.pow(p, kSuccess) * Math.pow(1 - p, nTrials - kSuccess);
	}

	private int binomialCoefficient(int n, int k)
	{
		return FACTORIALS[n] / (FACTORIALS[k] * FACTORIALS[n - k]);
	}

	static
	{
		int n = 10;
		int[] facts = new int[n];
		facts[0] = 1;
		for (int i = 1; i < n; i++)
		{
			facts[i] = i * facts[i - 1];
		}
		FACTORIALS = facts;
	}
}
