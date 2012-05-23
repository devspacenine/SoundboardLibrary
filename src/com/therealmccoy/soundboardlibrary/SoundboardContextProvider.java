package com.therealmccoy.soundboardlibrary;

import android.support.v4.app.FragmentManager;

public interface SoundboardContextProvider {

	public FragmentManager getDefaultFragmentManager();
	public SoundManager getSoundManager();
}
