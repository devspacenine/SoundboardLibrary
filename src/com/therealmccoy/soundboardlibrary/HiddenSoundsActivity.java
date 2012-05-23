package com.therealmccoy.soundboardlibrary;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.view.MenuInflater;
import android.widget.Toast;

import com.therealmccoy.soundboardlibrary.SimpleAlertDialogFragment.OnAlertDismissedListener;
import com.therealmccoy.soundboardlibrary.SoundboardActivity.CategoryTabsFragment;
import com.therealmccoy.soundboardlibrary.SoundboardActivity.SoundboardFragment;

public class HiddenSoundsActivity extends FragmentActivity implements OnAlertDismissedListener,
	SoundboardContextProvider {

	// Sound manager
	private SoundManager mSoundManager;

	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		// Initiate a SoundManager for this activity.
		mSoundManager = new SoundManager(this);

		// Set the layout of the activity.
		setContentView(R.layout.main);

		// The result defaults to RESULT_CANCELED until an action is
		// performed.
		setResult(RESULT_CANCELED);

        Fragment soundboard;
        // Determine if this soundboard is categorized or not and build the appropriate fragment
        if(mSoundManager.isCategorized()) {
        	soundboard = CategoryTabsFragment.newInstance(mSoundManager, false);
        }else{
        	soundboard = SoundboardFragment.newInstance(mSoundManager.getCategories().get(0),
        			mSoundManager.getAllHiddenKeys());
        }

        // Switch to the new soundboard fragment
        getSupportFragmentManager().beginTransaction()
        	.replace(R.id.soundboard_frame, soundboard)
        	.commit();
	}

	@Override
    public void onDestroy() {
    	mSoundManager.releaseAllPlayers();
    	super.onDestroy();
    }

	public SoundManager getSoundManager() {

		return mSoundManager;
	}

	public FragmentManager getDefaultFragmentManager() {

		return getSupportFragmentManager();
	}

	@Override
    public boolean onCreateOptionsMenu(Menu menu) {

		// Inflate the menu from an XML resource
    	MenuInflater inflater = getMenuInflater();
    	inflater.inflate(R.menu.hidden_sounds, menu);
    	/*
    	// If an action can be undone, make the undo option visible
    	if(mSoundManager.canUndo()) {
    		menu.findItem(R.id.undo_show_action).setVisible(true);
    	}*/

    	return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

    	if (item.getItemId() == android.R.id.home) {
    		Intent intent = new Intent(this, SoundboardActivity.class);
    		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    		startActivity(intent);
    		return true;
    	} else if (item.getItemId() == R.id.show_all_hidden) {
			mSoundManager.showAllHidden();
			Toast.makeText(this,
    				"All hidden sounds have been restored.",
    				Toast.LENGTH_LONG).show();
			finish();
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
    }

	public void onDismiss(DialogInterface dialog, int requestCode) {

		dialog.dismiss();
	}
}
