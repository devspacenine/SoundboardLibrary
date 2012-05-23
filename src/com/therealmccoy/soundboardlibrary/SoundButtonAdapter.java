package com.therealmccoy.soundboardlibrary;

import java.util.ArrayList;
import java.util.Collections;

import android.app.Activity;
import android.graphics.Typeface;
import android.net.Uri;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

public class SoundButtonAdapter extends BaseAdapter implements OnClickListener,
	OnLongClickListener, OnCreateContextMenuListener, OnMenuItemClickListener {

	private Activity mCtx;
	private String mCategory;
	private ArrayList<String> mKeys;
	private SoundboardContextProvider mContextProvider;

	public SoundButtonAdapter(Activity context, String category, ArrayList<String> keys) {

		mCtx = context;
		try{
			mContextProvider = (SoundboardContextProvider) context;
		}catch(ClassCastException e) {
			throw new ClassCastException(context.toString()
					+ " must implement the SoundboardInfoProvider interface");
		}
		mCategory = category;
		mKeys = keys;
	}

	public int getCount() {
		return mKeys.size();
	}

	public Object getItem(int position) {
		return mKeys.get(position);
	}

	public long getItemId(int position) {
		return position;
	}

	// create a new Button for each soundboard referenced by the Adapter
	public View getView(int position, View convertView, ViewGroup parent) {

		ToggleButton button = new ToggleButton(mCtx);
		String key = mKeys.get(position);
		String[] parts = key.split("%%");
		String title = parts[1];
		String file_type = parts[2];
		button.setLines(2);
		button.setBackgroundResource(R.drawable.simple_button);
		button.setTextSize(16);
		button.setTypeface(Typeface.DEFAULT);
		button.setTextColor(mCtx.getResources().getColorStateList(R.drawable.button_color));
		button.setShadowLayer(0.0f, 1.0f, 0.75f, R.color.white);
		button.setText(title);
		button.setTextOff(title);
		button.setTextOn("Click to Stop");
		button.setTag(R.id.tag_key, key);
		button.setTag(R.id.tag_title, title);
		button.setTag(R.id.tag_file_name, SoundManager.cleanFileName(title));
		button.setTag(R.id.tag_file_type, file_type);
		button.setTag(R.id.tag_position, Integer.toString(position));
		button.setTag(R.id.tag_index, Integer.toString(position + 1));
		button.setId(position);
		button.setOnClickListener(this);
		button.setOnLongClickListener(this);
		button.setOnCreateContextMenuListener(this);

		// Return the final view
		return button;
	}

	/**
	 * Removes a list of keys from this adapter and updates it.
	 *
	 * @param keys - ArrayList<String> with keys to be removed.
	 */
	public void addSounds(ArrayList<String> keys) {

		for(String key : keys) {
			mKeys.add(key);
		}
		Collections.sort(mKeys);
		notifyDataSetChanged();
	}

	// Creates a context menu for sound buttons
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {

		// Make sure the view is a ToggleButton from the soundboard table
		boolean has_unused = false;
		String file_name = ((String)v.getTag(R.id.tag_file_name));
		String file_type = ((String)v.getTag(R.id.tag_file_type));
		int position = v.getId();

		// Get a sound manager
		SoundManager manager = mContextProvider.getSoundManager();

		// Inflate the header layout
		LinearLayout header = (LinearLayout) LayoutInflater.from(mCtx).inflate(R.layout.context_menu_header, null);
		// Set the title
		((TextView)header.findViewById(R.id.sound_title)).setText(
				(String)v.getTag(R.id.tag_title));

		// Add the menu items to the context menu
		if(manager.hasRingtone(file_name, file_type)) {
			// If the sound file is saved as a ringtone create a sub menu of options.
			// Otherwise add an item for saving this sound as a ringtone.
			if(manager.isCurrentRingtone(file_name)) {
				header.findViewById(R.id.default_ringtone).setVisibility(View.VISIBLE);
				header.findViewById(R.id.context_divider).setVisibility(View.VISIBLE);
			}else{
				menu.add(Menu.NONE, position, 1,
	        			SoundManager.SET_RINGTONE).setOnMenuItemClickListener(this);
				has_unused = true;
			}
			if(manager.isContactRingtone(file_name)) {
				header.findViewById(R.id.contact_ringtone).setVisibility(View.VISIBLE);
				header.findViewById(R.id.context_divider).setVisibility(View.VISIBLE);
				has_unused = false;
			}
		}else{
			menu.add(Menu.NONE, position, 1,
	    			SoundManager.SET_RINGTONE).setOnMenuItemClickListener(this);
		}

		if(manager.hasNotification(file_name, file_type)) {
			// If the sound file is saved as a notification create a sub menu of options.
			// Otherwise add an item for saving this sound as a notification.
			if(manager.isCurrentNotification(file_name)) {
				header.findViewById(R.id.default_notification).setVisibility(View.VISIBLE);
				header.findViewById(R.id.context_divider).setVisibility(View.VISIBLE);
			}else{
				menu.add(Menu.NONE, position, 2,
	        			SoundManager.SET_NOTIFICATION).setOnMenuItemClickListener(this);
				has_unused = true;
			}
		}else{
			menu.add(Menu.NONE, position, 2,
	    			SoundManager.SET_NOTIFICATION).setOnMenuItemClickListener(this);
		}

		if(manager.hasAlarm(file_name, file_type)) {
			// If the sound file is saved as an alarm create a sub menu of options.
			// Otherwise add an item for saving this sound as an alarm.
			if(manager.isCurrentAlarm(file_name)) {
				header.findViewById(R.id.default_alarm).setVisibility(View.VISIBLE);
				header.findViewById(R.id.context_divider).setVisibility(View.VISIBLE);
			}else{
				menu.add(Menu.NONE, position, 3,
	        			SoundManager.SET_ALARM).setOnMenuItemClickListener(this);
				has_unused = true;
			}
		}else{
			menu.add(Menu.NONE, position, 3,
	    			SoundManager.SET_ALARM).setOnMenuItemClickListener(this);
		}

		// If the file is not being used for any sounds then add an option to wipe it
		if(has_unused) {
			menu.add(Menu.NONE, position, 6,
					SoundManager.DELETE_UNUSED).setOnMenuItemClickListener(this);
			if(manager.isReadable()) {
				TextView unusedInfo = (TextView) header.findViewById(R.id.unused_info);
				unusedInfo.setText(manager.getUnusedInfo(file_name, file_type));
				unusedInfo.setVisibility(View.VISIBLE);
			}
		}

		if(manager.canSetContactRingtone())
	    	menu.add(Menu.NONE, position, 4,
	    			SoundManager.SET_CONTACT_RINGTONE).setOnMenuItemClickListener(this);

		if(!manager.isSavedToSd(file_name, file_type)) {
	    	menu.add(Menu.NONE, position, 5,
	    			SoundManager.SAVE_TO_SD).setOnMenuItemClickListener(this);
		}

		if(manager.isHidden((String)v.getTag(R.id.tag_key))) {
			menu.add(Menu.NONE, position, 7,
					SoundManager.SHOW_SOUND).setOnMenuItemClickListener(this);
		}else{
			menu.add(Menu.NONE, position, 7,
					SoundManager.HIDE_SOUND).setOnMenuItemClickListener(this);
		}

		menu.add(Menu.NONE, position, 8,
					SoundManager.SHARE_SOUND).setOnMenuItemClickListener(this);

		menu.setHeaderView(header);
	}

	public boolean onMenuItemClick(MenuItem item) {
		// Construct the resource URI, file name, and display name
		ToggleButton button = (ToggleButton) mCtx.findViewById(item.getItemId());
		int position = Integer.parseInt((String)button.getTag(R.id.tag_position));
		String sound_key = (String) button.getTag(R.id.tag_key);
		String file_name = (String) button.getTag(R.id.tag_file_name);
		String file_type = (String) button.getTag(R.id.tag_file_type);
		String sound_title = (String) button.getTag(R.id.tag_title);
		String item_title = (String) item.getTitle();

		// Get a sound manager
		SoundManager manager = mContextProvider.getSoundManager();

		// If the hide sound option is clicked, hide the button and break execution
		// of this method.
		if(item_title.equals(SoundManager.HIDE_SOUND)) {
			manager.hideSound(sound_key);
			mKeys.remove(position);
			notifyDataSetChanged();
			Toast.makeText(mCtx,
					"Press menu to view hidden sounds.",
					Toast.LENGTH_LONG).show();
			return true;
		}

		// If the show sound option is clicked, remove it from the hidden list
		// and break execution of this method.
		if(item_title.equals(SoundManager.SHOW_SOUND)) {
			manager.showSound(sound_key);
			mKeys.remove(position);
			notifyDataSetChanged();
			Toast.makeText(mCtx,
					"\"" + sound_title + "\" restored. Open the menu to undo this action.",
					Toast.LENGTH_LONG).show();
			return true;
		}

		// Try to retrieve the audio file from the appropriate external public storage directory.
		// If it doesn't exists then create a new file and save it in the external public
		// storage.
		Uri soundUri;
		// Make sure the external storage device can be read
		if(manager.isReadable()) {

	    	if(manager.hasAudio(file_name, file_type, item_title)) {

	    		if(item_title.equals(SoundManager.DELETE_UNUSED)) {

	    			if(manager.isWritable()) {

	    				long total_bytes = manager.deleteAudio(file_name, file_type);
	    				if(total_bytes > 0) {

		    				// Display a dialog that reports what was deleted and what was being used
		    	    		float total_megabytes = (float) total_bytes / 1048576;
		    	    		StringBuilder sb = new StringBuilder();
		    	    		sb.append(String.format("Deleted unused copies:\n\n\t\t%.2f Mb", total_megabytes));

		    	    		DialogFragment newFragment = SimpleAlertDialogFragment.newInstance(
		    	    				sb.toString(),
		    	    				SoundManager.DELETE_SINGLE_DIALOG);
		    	    		newFragment.show(((FragmentActivity)mCtx).getSupportFragmentManager(), "delete_single_dialog");
	    				}else{

	    					Toast.makeText(mCtx, "Error: could not delete files, make sure external storage "
	    							+ "device is mounted", Toast.LENGTH_SHORT).show();
	    				}
	    				return true;

	    			}else{

	    				Toast.makeText(mCtx, "Error: external storage device not currently "
	    						+ "writable", Toast.LENGTH_SHORT).show();
	    				return false;
	    			}
	    		}

	    		soundUri = manager.getAudioUri(file_name, item_title);
	    		if(soundUri == null) {
	    			Toast.makeText(mCtx, "Error: could not get a Uri for the file", Toast.LENGTH_SHORT).show();
	    			return false;
	    		}

	    	}else{
	    		// Make sure the external storage device can be written to
	    		if(manager.isWritable()) {

	    			soundUri = manager.createAudio(sound_key, file_name, file_type, sound_title,
	    					item_title, mCategory);
		    		if(soundUri == null) {
		    			Toast.makeText(mCtx, "Error: could not create the file", Toast.LENGTH_SHORT).show();
		    			return false;
		    		}

	    		}else{ // External storage device not writable
	    			Toast.makeText(mCtx, "Error: external storage device not currently "
							+ "writable", Toast.LENGTH_SHORT).show();
	    			return false;
	    		}
	    	}
		}else{ // External storage device not readable
			Toast.makeText(mCtx, "Error: external storage device not currently "
					+ "available. It must be mounted before you can edit sounds.", Toast.LENGTH_SHORT).show();
			return false;
		}

		if(item_title.equals(SoundManager.SET_CONTACT_RINGTONE)) {
			manager.pickContactForRingtone(soundUri);
			return true;
		}

		if(item_title.equals(SoundManager.SAVE_TO_SD)) {
			Toast.makeText(mCtx, "Successfully saved the \"" + sound_title + "\" to the SD card in the " +
					"\"Music\" directory.", Toast.LENGTH_LONG).show();
			return true;
		}

		// If the share sound option is clicked, start a send intent with the
		// the sound attached as data.
		if(item_title.equals(SoundManager.SHARE_SOUND)) {
			manager.shareSound(soundUri, sound_title);
			return true;
		}

		manager.setDefaultSound(soundUri, sound_title, item_title);
		return true;
	}

	public void onClick(View view) {

		ToggleButton button = (ToggleButton) view;

		// Get a sound manager
		SoundManager manager = mContextProvider.getSoundManager();

		if(button.isChecked()) {
			manager.playSound(button, mCategory);
		}else{
			manager.releasePlayer(button, mCategory);
		}
	}

	public boolean onLongClick(View v) {
		v.showContextMenu();
		return true;
	}
}
