package com.rasifiel;

import java.util.HashSet;

public class WordsList extends HashSet<String> {
	private static final long serialVersionUID = 7858129886366158579L;

	public WordsList(String[] list) {
		super(list.length);
		for (String s : list)
			this.add(s);
	}
}
