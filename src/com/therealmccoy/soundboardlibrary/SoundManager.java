package com.therealmccoy.soundboardlibrary;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import android.app.Activity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.graphics.drawable.AnimationDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.DialogFragment;
import android.support.v4.view.Menu;
import android.util.Log;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.devspacenine.contactmanagerlibrary.ContactManager;
import com.devspacenine.externalstoragelibrary.ExternalStorageManager;

public class SoundManager {

	// Context menu item titles
	public static final String SET_RINGTONE = "Set as Default Ringtone";
	public static final String SET_NOTIFICATION = "Set as Default Notification";
	public static final String SET_ALARM = "Set as Default Alarm";
	public static final String DELETE_UNUSED = "Delete Unused Copies";
	public static final String SET_CONTACT_RINGTONE = "Set as Contact\'s Ringtone";
	public static final String SAVE_TO_SD = "Save to SD Card";
	public static final String HIDE_SOUND = "Hide This Sound";
	public static final String SHOW_SOUND = "Show This Sound";
	public static final String SHARE_SOUND = "Share This Sound";

	// Extra data keys
	public static final String EXTRA_SHOWN_SOUNDS = "shown sounds";

	// request codes
	public static final int DELETE_ALL_DIALOG = 1;
	public static final int DELETE_SINGLE_DIALOG = 2;
	public static final int SET_CONTACT_REQUEST = 3;
	public static final int STORAGE_DETAILS_DIALOG = 4;
	public static final int HIDDEN_SOUNDS = 5;

	// common database column values
	public static final String SOUND_ARTIST = "TheRealMcCoy";

	// Base path for sound files
	public static final String SOUNDS_PATH = "sounds";

	// Default category name for uncategorized soundboards
	public static final String DEFAULT_CATEGORY = "category-1-default";

	// Resource content URI and the package name of this app
	public String PACKAGE_NAME;
	public String BASE_RESOURCE_URI;

	// the provider we are going to use to get info about the sound files
	private SoundboardContextProvider mContextProvider;
	private final ContactManager mContactManager;
	private final ExternalStorageManager mExternalStorageManager;
	private AssetManager mAssetManager;

	private String mDirectoryName;
	private Activity mCtx;
	private Uri mTempUri;

	// collection of categories, keys, and hidden sounds HashMaps
	private ArrayList<String> mCategories; // <Title>
	private HashMap<String, ArrayList<String>> mKeys; // <Category, Labels<?>>
	private HashMap<String, String> mAssetFileNames; // <Label, "File Name">

	// Switch to signify whether this soundboard is categorized.
	private boolean mIsCategorized;

	// Data that is returned by a HiddenSoundsActivity. Label mappings should
	// be in the format <String(label), int(stack position)>.
	private ArrayList<String> mShownSounds;

	// Stack counter for the ShownSounds data.
	private int mStackCounter;

	// Toast messages
	private Toast mSoundTitleToast;

	// Temporary store for MediaPlayers and AnimationDrawables.
	private HashMap<String, MediaPlayer> mMediaPlayers;
	private HashMap<String, AnimationDrawable> mAnimations;
	private HashMap<String, File> mCacheFiles;

	public static String cleanLabel(String rawLabel) {
		return WordUtils.capitalize(rawLabel.replaceAll("_", " ").replaceAll("901", "\'")
				.replaceAll("902", "-").replaceAll("903", ".").replaceAll("904", ":")
				.replaceAll("905", ",").replaceAll("906", "?").replaceAll("907", "!")
				.replaceAll("908", "(").replaceAll("909", ")"));
	}

	public static String cleanFileName(String rawFileName) {
		return rawFileName.replaceAll("['\\.\\:\\,\\?\\!\\(\\)]", "");
	}

	public SoundManager(Activity activity) {

		// initialize member variables and public identifiers.
		PACKAGE_NAME = activity.getPackageName();
		BASE_RESOURCE_URI = "android.resource://" + PACKAGE_NAME + "/";
		mCtx = activity;
		mContactManager = ContactManager.getInstance();
		mExternalStorageManager = ExternalStorageManager.getInstance();
		mAssetManager = activity.getAssets();
		mShownSounds = new ArrayList<String>();
		mStackCounter = 0;
		mSoundTitleToast = Toast.makeText(activity, "", Toast.LENGTH_LONG);

		// Get a name for storage sub-directory from the application's meta tag.
		try {
			ApplicationInfo ai = mCtx.getPackageManager().getApplicationInfo(
					mCtx.getPackageName(), PackageManager.GET_META_DATA);
			Bundle b = ai.metaData;
			mDirectoryName = b.getString("com.therealmccoy.soundboardlibrary.DIRECTORY_NAME");
		} catch (NameNotFoundException e) {
			mDirectoryName = "";
			e.printStackTrace();
		}

		// Make sure that the context activity is implementing SoundboardContextProvider.
		try{
			mContextProvider = (SoundboardContextProvider) activity;
		}catch(ClassCastException e) {
			throw new ClassCastException(activity.toString()
					+ " must implement the SoundboardInfoProvider interface");
		}

		// Initialize the MediaPlayer and AnimationDrawable stores.
		mMediaPlayers = new HashMap<String, MediaPlayer>();
    	mAnimations = new HashMap<String, AnimationDrawable>();
    	mCacheFiles = new HashMap<String, File>();
    	mKeys = new HashMap<String, ArrayList<String>>();
    	mAssetFileNames = new HashMap<String, String>();

    	// Determine how many categories there are by counting the subdirectories
    	// in the 'sounds' directory.
    	String[] categories;
    	try {
    		categories = mAssetManager.list(SOUNDS_PATH);
    	}catch(IOException e) {
    		categories = new String[] {DEFAULT_CATEGORY};
    		Log.e("DSN Debug", "Error listing sounds directory in assets", e);
    	}

    	// Populate the list of categories with the values in the list retrieved
    	// by the AssetManager.
    	mCategories = new ArrayList<String>();
    	for(String category : categories) {
    		// Make sure the asset is a directory with the format
    		// "category-<number>-<title>".
    		String[] asset = category.split("-");
    		if(asset.length >= 3 && asset[0].equals("category") && Integer.parseInt(asset[1]) > 0) {
	    		try {
	    			String[] sounds = mAssetManager.list(SOUNDS_PATH + "/" + category);
	    			if(sounds.length > 0) {
	    				mCategories.add(category);
	    			}
	    		}catch(IOException e) {
	    			Log.e("DSN Debug", "Error listing category directory: " + category, e);
	    		}
    		}
    	}

    	if(mCategories.size() > 1) {
    		// There are multiple categories. Set the categorized flag to true.
    		mIsCategorized = true;
    	}else{
    		// There is only one category. Set the categorized flag to false.
    		mIsCategorized = false;
    	}

    	// Iterate over the subdirectories in 'sounds' to map keys and file names
    	// to the correct category.
		for(String category : categories) {
			try {
				String[] files = mAssetManager.list(SOUNDS_PATH + "/" + category);
				ArrayList<String> keys = new ArrayList<String>();
				for(String file : files) {
					String label = cleanLabel(file.replaceAll("\\.ogg|\\.mp3", ""));
					String fileType = file.substring(file.lastIndexOf('.')+1);
					String key = category + "%%" + label + "%%" + fileType;
					keys.add(key);
					mAssetFileNames.put(key, file);
				}
				mKeys.put(category, keys);
			}catch(IOException e) {
				Log.e("DSN Debug", "Error listing subdirectory: " + category, e);
			}
		}
	}

	public boolean isCategorized() {

		return mIsCategorized;
	}

	public ArrayList<String> getCategories() {

		return mCategories;
	}

	public String getFileName(String key) {

		return cleanFileName(cleanLabel(mAssetFileNames.get(key)));
	}

	/**
	 * Retrieves an ArrayList of keys for visible sounds in the given
	 * category.
	 *
	 * @param category - String identifying the target category.
	 *
	 * @return - ArrayList of visible keys as Strings.
	 */
	public ArrayList<String> getVisibleKeys(String category) {

		ArrayList<String> keys = mKeys.get(category);
		SharedPreferences hidden_sounds = mCtx.getSharedPreferences("HiddenSounds", Context.MODE_PRIVATE);

		// Remove any keys that are in the hidden sounds preferences and
		// return the ArrayList.
		for(String key : hidden_sounds.getAll().keySet()) {
			keys.remove(key);
		}
		return keys;
	}

	/**
	 * Retrieves an ArrayList of keys for hidden sounds in the given
	 * category.
	 *
	 * @param category - String identifying the target category.
	 *
	 * @return - ArrayList of hidden keys as Strings.
	 */
	public ArrayList<String> getHiddenKeys(String category) {

		ArrayList<String> all_keys = mKeys.get(category);
		SharedPreferences hidden_sounds = mCtx.getSharedPreferences("HiddenSounds", Context.MODE_PRIVATE);
		ArrayList<String> hidden_keys = new ArrayList<String>();

		// Only keep the keys that are in the hidden sounds preferences and
		// return the ArrayList.
		for(String key : all_keys) {
			if(hidden_sounds.contains(key)) {
				hidden_keys.add(key);
			}
		}
		return hidden_keys;
	}

	/**
	 * Retrieves a list of all the keys on this soundboard.
	 *
	 * @return - ArrayList<String> with all keys on this soundboard.
	 */
	public ArrayList<String> getAllKeys() {

		ArrayList<String> keyList = new ArrayList<String>();

		// Collect all of the keys and return the ArrayList.
		for(ArrayList<String> keys : mKeys.values()) {
			for(String key : keys) {
				keyList.add(key);
			}
		}
		return keyList;
	}

	/**
	 * Retrieves a list of all the visible keys on this soundboard.
	 *
	 * @return - ArrayList<String> with all visible keys on this soundboard.
	 */
	public ArrayList<String> getAllVisibleKeys() {

		ArrayList<String> keyList = new ArrayList<String>();
		SharedPreferences hidden_sounds = mCtx.getSharedPreferences("HiddenSounds", Context.MODE_PRIVATE);

		// Collect all of the keys first
		for(ArrayList<String> keys : mKeys.values()) {
			for(String key : keys) {
				keyList.add(key);
			}
		}

		// Remove any keys that are in the hidden sounds preferences and
		// return the ArrayList.
		for(String key : hidden_sounds.getAll().keySet()) {
			keyList.remove(key);
		}
		return keyList;
	}

	/**
	 * Retrieves a list of all the hidden keys on this soundboard.
	 *
	 * @return - ArrayList<String> with all hidden keys on this soundboard.
	 */
	public ArrayList<String> getAllHiddenKeys() {

		ArrayList<String> keyList = new ArrayList<String>();
		SharedPreferences hidden_sounds = mCtx.getSharedPreferences("HiddenSounds", Context.MODE_PRIVATE);

		// Build an ArrayList with all of the labels in the hidden sounds
		// preferences.
		for(String key : hidden_sounds.getAll().keySet()) {
			keyList.add(key);
		}
		return keyList;
	}

	/**
	 * Determines whether or not there are any hidden sounds.
	 *
	 * @return - Boolean indicating whether or not there are hidden sounds.
	 */
	public boolean hasHiddenSounds() {

		SharedPreferences hidden_sounds = mCtx.getSharedPreferences("HiddenSounds", Context.MODE_PRIVATE);

		// If there are any hidden sounds in the map, return true, otherwise
		// return false.
		if(hidden_sounds.getAll().size() > 0) {
			return true;
		}
		return false;
	}

	/**
	 * Determines whether a key is hidden or not.
	 *
	 * @param label - A string that identifies the sound for hashmaps.
	 *
	 * @return - A boolean indicating whether or not the sound is hidden.
	 */
	public boolean isHidden(String key) {

		SharedPreferences hidden_sounds = mCtx.getSharedPreferences("HiddenSounds", Context.MODE_PRIVATE);

		if(hidden_sounds.contains(key)) {
			return true;
		}
		return false;
	}

	/**
	 * Hides a sound button from the user and adds it to hidden sounds
	 * preferences.
	 *
	 * @param label - A string that identifies the sound for hashmaps.
	 */
	public void hideSound(String key) {

		SharedPreferences hidden_sounds = mCtx.getSharedPreferences("HiddenSounds", Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = hidden_sounds.edit();

		// Return if the key is already in the hidden sounds
		// preferences.
		if(hidden_sounds.contains(key)) {
			return;
		}

		// Add the key to the hidden sounds preferences.
		editor.putBoolean(key, true);
		editor.commit();
	}

	/**
	 * Shows a sound button that has been hidden and removes it from the
	 * hidden sounds preferences.
	 *
	 * @param label
	 */
	public void showSound(String key) {

		SharedPreferences hidden_sounds = mCtx.getSharedPreferences("HiddenSounds", Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = hidden_sounds.edit();

		// Return if the key is not in the hidden sounds
		// preferences.
		if(!hidden_sounds.contains(key)) {
			return;
		}

		// Remove the key from the hidden sounds preferences.
		editor.remove(key);
		editor.commit();

		// Add the key to the result data and increment the stack
		mShownSounds.add(key);

		// Update result data and set the result of the activity to RESULT_OK.
		Intent data = new Intent();
		data.putStringArrayListExtra(EXTRA_SHOWN_SOUNDS, mShownSounds);
		mCtx.setResult(Activity.RESULT_OK, data);
	}

	/**
	 * Shows all of the currently hidden sounds and removes them from the
	 * hidden sounds preferences.
	 */
	public void showAllHidden() {

		SharedPreferences hidden_sounds = mCtx.getSharedPreferences("HiddenSounds", Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = hidden_sounds.edit();
		Set<String> keys = hidden_sounds.getAll().keySet();

		// Break execution if their are no keys in the hidden
		// sounds preferences.
		if(keys.size() == 0) {
			return;
		}

		for(String key : keys) {
			// Remove every key from the hidden sounds preferences.
			editor.remove(key);
			mShownSounds.add(key);
		}
		editor.commit();

		// Update result data and set the result of the activity to RESULT_OK.
		Intent data = new Intent();
		data.putStringArrayListExtra(EXTRA_SHOWN_SOUNDS, mShownSounds);
		mCtx.setResult(Activity.RESULT_OK, data);
	}

	/**
	 * Gets an ArrayList with the keys of all the shown sounds.
	 */
	public ArrayList<String> getShownSounds() {
		return mShownSounds;
	}

	/**
	 * Starts an intent for sharing a sound by email, SMS, or some other method.
	 *
	 * @param key - Unique key identifying the sound
	 */
	public void shareSound(Uri soundUri, String soundTitle) {

		// Get the soundboard title from resources
		String boardTitle = mCtx.getResources().getString(R.string.title);
		Intent sendIntent = new Intent(Intent.ACTION_SEND);
		sendIntent.putExtra("sms_body", boardTitle + " sound clip: " + soundTitle);
		sendIntent.putExtra(Intent.EXTRA_STREAM, soundUri);
		sendIntent.setType("audio/*");
		mCtx.startActivity(Intent.createChooser(sendIntent, "Share \"" + soundTitle + "\""));
	}

	public void playSound(ToggleButton targetButton, String category) {

		// Make a copy of the button id for listeners.
		final ToggleButton button = targetButton;

		// A MediaPlayer has to be created using an asset file descriptor.
		String fileName = mAssetFileNames.get((String)button.getTag(R.id.tag_key));
		File temp = new File(mCtx.getCacheDir(), fileName);
		MediaPlayer player = new MediaPlayer();
		player.setAudioStreamType(AudioManager.STREAM_MUSIC);

		try {

			InputStream is = mAssetManager.open(String.format("%s/%s/%s", SOUNDS_PATH, category, fileName));
			temp.createNewFile();

			// Save the sound file to the cache directory for playback.
			FileOutputStream out = new FileOutputStream(temp);
			byte[] readData = new byte[2048];

			int i = is.read(readData);
			while(i != -1) {
    			out.write(readData, 0, i);
    			i = is.read(readData);
    		}

			is.close();
			out.close();

			FileInputStream fis = new FileInputStream(temp);
			player.setDataSource(fis.getFD());
			player.prepare();

		}catch(Exception e) {
			Log.e("DSN Debug", "Error trying to create media player.", e);
			Toast.makeText(mCtx, "Error trying to create media player.", Toast.LENGTH_LONG).show();
			if(temp.exists())
				temp.delete();
    		return;
		}

		player.setLooping(false);

		// Construct an animation for this player's button.
		AnimationDrawable animation = getAnimation(player);

		// Set the button's animation.
		button.setBackgroundDrawable(animation);

		// start the player and it's button animation
    	player.start();
		animation.start();

		// Start managing the player's resources
		startManagingPlayer((String)button.getTag(R.id.tag_key), player, animation, temp);

		// Set the player's OnCompleteListener
		player.setOnCompletionListener(new OnCompletionListener() {

    		public void onCompletion(MediaPlayer player) {
    			stopManagingPlayer((String)button.getTag(R.id.tag_key));
    			// Reset the button's background resource and uncheck it
    			button.setBackgroundResource(R.drawable.simple_button);
    			button.setChecked(false);
    		}
    	});
	}

	public void playSound(ToggleButton targetButton, String category, Menu targetMenu) {

		// Make a copy of the button id and MenuItem for listeners.
		final ToggleButton button = targetButton;
		final Menu menu = targetMenu;

		// A MediaPlayer has to be created using an asset file descriptor.
		String fileName = mAssetFileNames.get((String)button.getTag(R.id.tag_key));
		String title = (String) button.getTag(R.id.tag_title);
		File temp = new File(mCtx.getCacheDir(), fileName);
		MediaPlayer player = new MediaPlayer();
		player.setAudioStreamType(AudioManager.STREAM_MUSIC);

		try {

			InputStream is = mAssetManager.open(String.format("%s/%s/%s", SOUNDS_PATH, category, fileName));
			temp.createNewFile();

			// Save the sound file to the cache directory for playback.
			FileOutputStream out = new FileOutputStream(temp);
			byte[] readData = new byte[2048];

			int i = is.read(readData);
			while(i != -1) {
    			out.write(readData, 0, i);
    			i = is.read(readData);
    		}

			is.close();
			out.close();

			FileInputStream fis = new FileInputStream(temp);
			player.setDataSource(fis.getFD());
			player.prepare();

		}catch(Exception e) {
			Log.e("DSN Debug", "Error trying to create media player.", e);
			Toast.makeText(mCtx, "Error trying to create media player.", Toast.LENGTH_LONG).show();
			if(temp.exists())
				temp.delete();
    		return;
		}

		player.setLooping(false);

		// start the player
    	player.start();

		// Start managing the player's resources
		startManagingPlayer("random", player, null, temp);

		mSoundTitleToast.setText(title);
		mSoundTitleToast.show();

		// Set the player's OnCompleteListener
		player.setOnCompletionListener(new OnCompletionListener() {

    		public void onCompletion(MediaPlayer player) {
    			stopManagingPlayer("random");
    			// Reset the button's background resource and uncheck it
    			menu.setGroupVisible(R.id.play_random_group, true);
    			menu.setGroupEnabled(R.id.play_random_group, true);
    			menu.setGroupVisible(R.id.stop_random_group, false);
    			menu.setGroupEnabled(R.id.stop_random_group, false);
    		}
    	});
	}

	public void releasePlayer(ToggleButton button, String category) {

		stopManagingPlayer((String)button.getTag(R.id.tag_key));
		// Reset the button's background resource and uncheck it
		button.setBackgroundResource(R.drawable.simple_button);
		button.setChecked(false);
	}

	public void releaseAllPlayers() {

		// Iterate over the MediaPlayer store and stop managing each player.
		for(MediaPlayer player : mMediaPlayers.values()) {
			player.stop();
			player.release();
		}
		mMediaPlayers.clear();

		// Iterate over the Animation store and stop each animation
		for(AnimationDrawable animation : mAnimations.values())
			animation.stop();
		mAnimations.clear();

		// Iterate over the Cached Files store and delete each file
		for(File file : mCacheFiles.values())
			file.delete();
		mCacheFiles.clear();
	}

	private synchronized void startManagingPlayer(String key, MediaPlayer player,
			AnimationDrawable animation, File cachedSound) {

		// Save a reference to the player so it can be stopped and released later.
		mMediaPlayers.put(key, player);
		// Save a reference to the animation, if it isn't null, so it can be stopped later.
		if(animation != null) {
			mAnimations.put(key, animation);
		}
		// Save a reference to the cached file so it can be deleted later.
		mCacheFiles.put(key, cachedSound);
	}

	public synchronized void stopManagingPlayer(String key) {

		// Check if the player for this button is stored
		if(mMediaPlayers.containsKey(key)) {
			MediaPlayer player = mMediaPlayers.remove(key);
			player.stop();
			player.release();
		}

		// Check if the animation for this button is stored
		if(mAnimations.containsKey(key))
			mAnimations.remove(key).stop();

		// Make sure any cached sound files are deleted.
		if(mCacheFiles.containsKey(key))
			mCacheFiles.remove(key).delete();
	}

	public AnimationDrawable getAnimation(MediaPlayer player) {

    	int duration = player.getDuration();
    	int seconds = duration / 1000;
    	int frame_duration;
    	AnimationDrawable animation = new AnimationDrawable();
    	Resources appResources = mCtx.getResources();
    	if(seconds <= 1) {
    		frame_duration = duration / 3;
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_2), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_10), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_20), (duration % 3));
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_32), frame_duration);
    	}else if(seconds <= 2) {
    		frame_duration = duration / 5;
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_0), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_6), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_13), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_21), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_26), (duration % 5));
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_32), frame_duration);
    	}else if(seconds <= 5) {
    		frame_duration = duration / 9;
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_0), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_3), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_7), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_12), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_16), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_20), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_24), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_28), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_30), (duration % 9));
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_32), frame_duration);
    	}else if(seconds <= 8) {
    		frame_duration = duration / 15;
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_0), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_2), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_4), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_6), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_8), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_11), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_13), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_16), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_18), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_20), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_23), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_25), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_27), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_29), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_30), (duration % 15));
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_32), frame_duration);
    	}else{
    		frame_duration = duration / 24;
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_0), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_1), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_2), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_3), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_4), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_5), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_6), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_8), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_9), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_10), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_11), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_13), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_14), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_15), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_17), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_18), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_19), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_20), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_22), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_24), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_25), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_27), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_29), frame_duration);
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_30), (duration % 24));
    		animation.addFrame(appResources.getDrawable(R.drawable.light_button_playing_32), frame_duration);
    	}
    	animation.setOneShot(true);

    	return animation;
    }

	public boolean isCurrentSound(String fileName) {

		if(isCurrentRingtone(fileName) || isCurrentNotification(fileName)
				|| isCurrentAlarm(fileName) || isContactRingtone(fileName))
			return true;
		return false;
	}

	public boolean isCurrentRingtone(String fileName) {

		Uri defaultRingtoneUri = RingtoneManager.getActualDefaultRingtoneUri(
				mCtx, RingtoneManager.TYPE_RINGTONE);
		if(defaultRingtoneUri == null)
			return false;

		Cursor cursor = getAudioCursor(defaultRingtoneUri);
		if(cursor == null)
			return false;

		boolean isRingtone = false;

		if(cursor.moveToFirst())
			if(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)).equals(fileName))
				isRingtone = true;

		cursor.close();
		return isRingtone;
	}

	public boolean isCurrentNotification(String fileName) {

		Uri defaultNotificationUri = RingtoneManager.getActualDefaultRingtoneUri(
				mCtx, RingtoneManager.TYPE_NOTIFICATION);
		if(defaultNotificationUri == null)
			return false;

		Cursor cursor = getAudioCursor(defaultNotificationUri);
		if(cursor == null)
			return false;

		boolean isNotification = false;

		if(cursor.moveToFirst())
			if(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)).equals(fileName))
				isNotification = true;

		cursor.close();
		return isNotification;
	}

	public boolean isCurrentAlarm(String fileName) {

		Uri defaultAlarmUri = RingtoneManager.getActualDefaultRingtoneUri(
				mCtx, RingtoneManager.TYPE_ALARM);
		if(defaultAlarmUri == null)
			return false;

		Cursor cursor = getAudioCursor(defaultAlarmUri);
		if(cursor == null)
			return false;

		boolean isAlarm = false;

		if(cursor.moveToFirst())
			if(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)).equals(fileName))
				isAlarm = true;

		cursor.close();
		return isAlarm;
	}

	public boolean isContactRingtone(String fileName) {

		Uri ringtoneUri = getRingtoneUri(fileName);

		if(ringtoneUri == null)
			return false;

		return mContactManager.isRingtoneUsed(mCtx.getContentResolver(), ringtoneUri);
	}

	public boolean canSetContactRingtone() {

		if(mTempUri == null)
			return true;
		return false;
	}

	/**
	 * Get a string of info about how many copies of this file are unused on this device
	 * and how much space is used by those copies.
	 *
	 * @param fileName - A string representing the name of the file to retrieve info about
	 *
	 * @return - A string with the unused copy information formatted for a context menu
	 */
	public String getUnusedInfo(String fileName, String fileType) {

		File file;
		long total_bytes = 0;
		int file_count = 0;

		if(hasRingtone(fileName, fileType) && !isCurrentRingtone(fileName) && !isContactRingtone(fileName)) {
			file = getRingtone(fileName, fileType);
			if(file.exists()) {
				file_count += 1;
				total_bytes += file.length();
			}
		}

		if(hasNotification(fileName, fileType) && !isCurrentNotification(fileName)) {
			file = getNotification(fileName, fileType);
			if(file.exists()) {
				file_count += 1;
				total_bytes += file.length();
			}
		}

		if(hasAlarm(fileName, fileType) && !isCurrentAlarm(fileName)) {
			file = getAlarm(fileName, fileType);
			if(file.exists()) {
				file_count += 1;
				total_bytes += file.length();
			}
		}

		return String.format("%d Unused %s:\t%.2f Mb", file_count,
				(file_count>1 ? "Copies":"Copy"), ((float)total_bytes / 1048576));
	}

	/**
	 * Check if the external storage is readable.
	 *
	 * @return - True if the external storage is readable, false otherwise
	 */
	public boolean isReadable() {

		String state = Environment.getExternalStorageState();

		if(Environment.MEDIA_MOUNTED.equals(state)) {
			// We can read and write the media
			return true;
		}else if(Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			// We can only read the media
			return true;
		}else{
			// Something else is wrong. It may be one of many other states, but all we need
		    // to know is we can neither read nor write
			return false;
		}
	}

	/**
	 * Check if the external storage is writable.
	 *
	 * @return - True if the external storage is writable, false otherwise
	 */
	public boolean isWritable() {

		String state = Environment.getExternalStorageState();

		if(Environment.MEDIA_MOUNTED.equals(state)) {
			// We can read and write to the media
			return true;
		}else{
			return false;
		}
	}

	/**
	 * Constructs a file from a resource uri with the given file name and returns it.
	 * Checks the title of the menu item selected to decide which public folder to save
	 * the file in.
	 *
	 * @param ctx - The Context of the calling activity
	 * @param uri - A URI of the format "android.resource://<package-name>/<resource-id>"
	 * @param fileName - The name of the file to be created
	 * @param selectedTitle - The title of the menu item that was selected
	 *
	 * @return - A File object or null if the resource uri could not be resolved
	 */
	public Uri createAudio(String soundKey, String fileName, String fileType, String soundTitle, String action, String category) {

		File path;

		if(action.equals(SET_RINGTONE) || action.equals(SET_CONTACT_RINGTONE))
			path = getRingtoneDirectory();
		else if(action.equals(SET_NOTIFICATION))
			path = getNotificationDirectory();
		else if(action.equals(SET_ALARM))
			path = getAlarmDirectory();
		else if(action.equals(SAVE_TO_SD) || action.equals(SHARE_SOUND))
			path = getMusicDirectory();
		else
			return null;

		// Create a path where we will place our audio file in the user's
	    // public audio directory.
		File new_file = new File(path, fileName + "." + fileType);
		if(new_file.exists()) {
			new_file.delete();
		}

		InputStream fis;

		String assetName = mAssetFileNames.get(soundKey);
		try {
			fis = mAssetManager.open(String.format("%s/%s/%s", SOUNDS_PATH, category, assetName));
		}catch(IOException e) {
			Log.e("DSN Debug", "Error: could not get input stream for file", e);
    		Toast.makeText(mCtx, "Error: could not get input stream for file", Toast.LENGTH_SHORT).show();
    		return null;
		}

    	new_file = createFileFromInputStream(new_file, fis);
    	if(new_file == null)
    		return null;

    	// Construct this audio file's column values for the MediaStore database
    	ContentValues values = new ContentValues();
    	values.put(MediaStore.MediaColumns.DATA, new_file.getAbsolutePath());
    	values.put(MediaStore.MediaColumns.TITLE, fileName);
    	values.put(MediaStore.MediaColumns.DISPLAY_NAME, soundTitle);
    	if(fileType.equals("ogg")) {
    		values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/ogg");
    	}else if(fileType.equals("mp3")) {
    		values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg");
    	}else if(fileType.equals("wav")) {
    		values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav");
    	}
    	values.put(MediaStore.MediaColumns.SIZE, new_file.length());
    	values.put(MediaStore.Audio.Media.ARTIST, SOUND_ARTIST);

    	// If the save to card option is selected, then only create the new file. It will
    	// not be available as an option when setting ringtones, but it will be available
    	// to media players.
    	if(action.equals(SET_RINGTONE) || action.equals(SET_CONTACT_RINGTONE))
    		values.put(MediaStore.Audio.Media.IS_RINGTONE, true);
    	else if(action.equals(SET_NOTIFICATION))
    		values.put(MediaStore.Audio.Media.IS_NOTIFICATION, true);
    	else if(action.equals(SET_ALARM))
    		values.put(MediaStore.Audio.Media.IS_ALARM, true);
    	else if(action.equals(SAVE_TO_SD) || action.equals(SHARE_SOUND))
    		values.put(MediaStore.Audio.Media.IS_MUSIC, true);
    	else{
    		new_file.delete();
    		return null;
    	}

    	Uri ringtoneUri = mCtx.getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
    	return ringtoneUri;
	}

	/**
	 * Constructs a new file from a resource asset
	 *
	 * @param ctx - The context of the calling activity
	 * @param newFile - A File object representing the new file to be created
	 * @param asset - An AssetFileDesriptor representing the resource asset to copy
	 *
	 * @return - A File object representing the newly created File or null if creation failed
	 */
	private File createFileFromInputStream(File newFile, InputStream in) {

		try{

    		// Make sure the audio directory exists
    		newFile.getParentFile().mkdirs();

    		// Get an output stream from the asset file descriptor and an input stream
    		// for the external public file. If external storage is not currently mounted this
    		// will fail.
    		byte[] read_data = new byte[1024];
    		FileOutputStream out = new FileOutputStream(newFile);

    		// Copy the data from the input stream to the output stream
    		int i = in.read(read_data);
    		while(i != -1) {
    			out.write(read_data, 0, i);
    			i = in.read(read_data);
    		}

    		// Close the input and output streams
    		in.close();
    		out.close();
    	}catch(IOException io) {

    		// Unable to create file, likely because external storage is
            // not currently mounted.
    		Toast.makeText(mCtx, "Error: external storage not mounted", Toast.LENGTH_SHORT).show();
    		Log.e("DSN Debug", "IOException", io);
    		return null;
    	}

    	return newFile;
	}

	public boolean setDefaultSound(Uri ringtoneUri, String title, String type) {

    	// If the save to card option was selected, return true since the file has been created and
    	// added to the database. Otherwise, try to set the default sound for the chosen menu option
    	// using the RingtoneManager and the new audio URI
    	try{
	    	if(type.equals(SET_RINGTONE)) {
	    		RingtoneManager.setActualDefaultRingtoneUri(mCtx, RingtoneManager.TYPE_RINGTONE, ringtoneUri);
	    		Toast.makeText(mCtx, "Successfully set \"" + title
	    				+ "\" as the default ringtone", Toast.LENGTH_SHORT).show();
	    		return true;
	    	}else if(type.equals(SET_NOTIFICATION)) {
	    		RingtoneManager.setActualDefaultRingtoneUri(mCtx, RingtoneManager.TYPE_NOTIFICATION, ringtoneUri);
	    		Toast.makeText(mCtx, "Successfully set \"" + title
	    				+ "\" as the default notification sound", Toast.LENGTH_SHORT).show();
	    		return true;
	    	}else if(type.equals(SET_ALARM)) {
	    		RingtoneManager.setActualDefaultRingtoneUri(mCtx, RingtoneManager.TYPE_ALARM, ringtoneUri);
	    		Toast.makeText(mCtx, "Successfully set \"" + title
	    				+ "\" as the default alarm sound", Toast.LENGTH_SHORT).show();
	    		return true;
	    	}else{
	    		return false;
	    	}
    	}catch(Exception e){
    		Toast.makeText(mCtx, "Error: could not set default ringtone", Toast.LENGTH_SHORT).show();
    		return false;
    	}
	}

	public void pickContactForRingtone(Uri ringtoneUri) {
		if(mTempUri == null) {
			mTempUri = ringtoneUri;
			((Activity)mCtx).startActivityForResult(mContactManager.getContactPickerIntent(), SET_CONTACT_REQUEST);
		}else
			Toast.makeText(mCtx, "Error: Could not set contact\'s ringtone", Toast.LENGTH_SHORT);
	}

	public void setContactRingtone(Uri contactUri) {

		Uri ringToneUri = mTempUri;
		mTempUri = null;

		boolean success = mContactManager.setCustomRingtone(mCtx.getContentResolver(), contactUri, ringToneUri);
		if(success)
			Toast.makeText(mCtx, "Successfully set contact\'s ringtone", Toast.LENGTH_SHORT).show();
		else
			Toast.makeText(mCtx, "Error: could not set contact\'s ringtone", Toast.LENGTH_SHORT).show();
		return;
	}

	/**
	 * Retrieves an audio file from the specified external public storage directory.
	 *
	 * @param fileName - the file name to retrieve
	 *
	 * @return - A File object or null if it didn't exist
	 */
	public File getAudio(String fileName, String fileType) {

		File file;

		file = getRingtone(fileName, fileType);
		if(file.exists())
			return file;

		file = getNotification(fileName, fileType);
		if(file.exists())
			return file;

		file = getAlarm(fileName, fileType);
		if(file.exists())
			return file;

		return null;
	}

	public Cursor getAudioCursor(Uri contentUri) {

		try {
		String[] proj = new String[] {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.TITLE,
				MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.IS_RINGTONE,
				MediaStore.Audio.Media.IS_NOTIFICATION, MediaStore.Audio.Media.IS_ALARM};
		Cursor cursor = mCtx.getContentResolver().query(contentUri, proj, null, null, null);
		return cursor;
		}catch(SQLiteException e) {
			Log.e("DSN Debug", "Error accessing the MediaStore database", e);
			return null;
		}
	}

	public Uri getAudioUri(String fileName, String type) {

		if(type.equals(SET_RINGTONE) || type.equals(SET_CONTACT_RINGTONE))
			return getRingtoneUri(fileName);
		if(type.equals(SET_NOTIFICATION))
			return getNotificationUri(fileName);
		if(type.equals(SET_ALARM))
			return getAlarmUri(fileName);
		if(type.equals(SAVE_TO_SD) || type.equals(SHARE_SOUND))
			return getMusicUri(fileName);
		return null;
	}

	public Uri getRingtoneUri(String fileName) {

		String[] proj = new String[] {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.TITLE,
				MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.IS_RINGTONE};
		Cursor cursor = mCtx.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
				proj, MediaStore.MediaColumns.TITLE + "=? and " + MediaStore.Audio.Media.IS_RINGTONE + "=?",
				new String[]{fileName, "1"}, null);

		if(cursor.moveToFirst()) {
			Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
					cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID)));
			cursor.close();
			return uri;
		}
		cursor.close();
		return null;
	}

	public Uri getNotificationUri(String fileName) {

		String[] proj = new String[] {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.TITLE,
				MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.IS_NOTIFICATION};
		Cursor cursor = mCtx.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
				proj, MediaStore.MediaColumns.TITLE + "=? and " + MediaStore.Audio.Media.IS_NOTIFICATION + "=?",
				new String[]{fileName, "1"}, null);

		if(cursor.moveToFirst()) {
			Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
					cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID)));
			cursor.close();
			return uri;
		}
		cursor.close();
		return null;
	}

	public Uri getAlarmUri(String fileName) {

		String[] proj = new String[] {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.TITLE,
				MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.IS_ALARM};
		Cursor cursor = mCtx.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
				proj, MediaStore.MediaColumns.TITLE + "=? and " + MediaStore.Audio.Media.IS_ALARM + "=?",
				new String[]{fileName, "1"}, null);

		if(cursor.moveToFirst()) {
			Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
					cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID)));
			cursor.close();
			return uri;
		}
		cursor.close();
		return null;
	}

	public Uri getMusicUri(String fileName) {

		String[] proj = new String[] {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.TITLE,
				MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.IS_MUSIC};
		Cursor cursor = mCtx.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
				proj, MediaStore.MediaColumns.TITLE + "=? and " + MediaStore.Audio.Media.IS_MUSIC + "=?",
				new String[]{fileName, "1"}, null);

		if(cursor.moveToFirst()) {
			Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
					cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID)));
			cursor.close();
			return uri;
		}
		cursor.close();
		return null;
	}

	/**
	 * Retrieves a sound file from the public ringtones directory
	 *
	 * @param fileName - The name of the file as a String
	 *
	 * @return - A File object that represents the target sound file
	 */
	private File getRingtone(String fileName, String fileType) {
		return new File(getRingtoneDirectory(), fileName+"."+fileType);
	}

	/**
	 * Retrieves a sound file from the public notifications directory
	 * File te
	 * @param fileName - The name of the file as a String
	 *
	 * @return - A File object that represents the target sound file
	 */
	private File getNotification(String fileName, String fileType) {
		return new File(getNotificationDirectory(), fileName+"."+fileType);
	}

	/**
	 * Retrieves a sound file from the public alarms directory
	 *
	 * @param fileName - The name of the file as a String
	 * @return - A File object that represents the target sound file
	 */
	private File getAlarm(String fileName, String fileType) {
		return new File(getAlarmDirectory(), fileName+"."+fileType);
	}

	/**
	 * Retrieves a sound file from the public music directory
	 *
	 * @param fileName - The name of the file as a String
	 * @return - A File object that represents the target sound file
	 */
	private File getMusic(String fileName, String fileType) {
		return new File(getMusicDirectory(), fileName+"."+fileType);
	}

	/**
	 * Deletes an audio file in all of the external public storage directories.
	 *
	 * @param ctx - The Context of the calling activity
	 * @param fileName - The file name of the audio file to delete
	 */
	public long deleteAudio(String fileName, String fileType) {

		long total_bytes = 0;

		if(!isCurrentRingtone(fileName) && !isContactRingtone(fileName))
			total_bytes += deleteRingtone(fileName, fileType);
		if(!isCurrentNotification(fileName))
			total_bytes += deleteNotification(fileName, fileType);
		if(!isCurrentAlarm(fileName))
			total_bytes += deleteAlarm(fileName, fileType);

		return total_bytes;
	}

	public long deleteRingtone(String fileName, String fileType) {

		long total_bytes = 0;
		File file = getRingtone(fileName, fileType);
		if(file.exists()) {
			total_bytes = file.length();
			Uri fileUri = getRingtoneUri(fileName);
			if(fileUri == null)
				return 0;

			mCtx.getContentResolver().delete(fileUri, null, null);

			if(file.delete())
				return total_bytes;
			return 0;
		}
		return total_bytes;
	}

	public long deleteNotification(String fileName, String fileType) {

		long total_bytes = 0;
		File file = getNotification(fileName, fileType);
		if(file.exists()) {
			total_bytes = file.length();
			Uri fileUri = getNotificationUri(fileName);
			if(fileUri == null)
				return 0;

			mCtx.getContentResolver().delete(fileUri, null, null);

			if(file.delete())
				return total_bytes;
			return 0;
		}
		return total_bytes;
	}

	public long deleteAlarm(String fileName, String fileType) {

		long total_bytes = 0;
		File file = getAlarm(fileName, fileType);
		if(file.exists()) {
			total_bytes = file.length();
			Uri fileUri = getAlarmUri(fileName);
			if(fileUri == null)
				return 0;

			mCtx.getContentResolver().delete(fileUri, null, null);

			if(file.delete())
				return total_bytes;
			return 0;
		}
		return total_bytes;
	}

	public boolean clear() {
		if(isWritable()) {
			int deleted_files = 0;
			long total_bytes = 0;

			ArrayList<String> keys = getAllKeys();

    		for(String key : keys) {
    			String[] parts = key.split("%%");
    			String file_name = cleanFileName(parts[1]);
    			String file_type = parts[2];
    			if(hasRingtone(file_name, file_type)) {
    				if(!isCurrentRingtone(file_name) && !isContactRingtone(file_name)) {
    					long bytes = deleteRingtone(file_name, file_type);
	    				if(bytes > 0) {
	    					deleted_files += 1;
	    					total_bytes = total_bytes + bytes;
	    				}
    				}
    			}
    			if(hasNotification(file_name, file_type)) {
    				if(!isCurrentNotification(file_name)) {
    					long bytes = deleteNotification(file_name, file_type);
	    				if(bytes > 0) {
	    					deleted_files += 1;
	    					total_bytes = total_bytes + bytes;
	    				}
    				}
    			}
    			if(hasAlarm(file_name, file_type)) {
    				if(!isCurrentAlarm(file_name)) {
    					long bytes = deleteAlarm(file_name, file_type);
	    				if(bytes > 0) {
	    					deleted_files += 1;
	    					total_bytes = total_bytes + bytes;
	    				}
    				}
    			}
    		}

    		// Display a dialog that reports what was deleted and what was being used
    		float total_megabytes = (float) total_bytes / 1048576;
    		StringBuilder sb = new StringBuilder();
    		sb.append(deleted_files + " ");
    		if(deleted_files == 1)
    			sb.append("sound");
    		else
    			sb.append("sounds");
    		sb.append(String.format(" deleted from storage:\n\n\t\t%.2f Mb", total_megabytes));

    		DialogFragment newFragment = SimpleAlertDialogFragment.newInstance(
    				sb.toString(),
    				DELETE_ALL_DIALOG);
    		newFragment.show(mContextProvider.getDefaultFragmentManager(), "delete_all_dialog");
    		return true;
		}else{
			Toast.makeText(mCtx, "Error: external storage device not currently "
					+ "writable", Toast.LENGTH_SHORT).show();
			return false;
		}
	}

	public void showStorageDetails() {

		if(isReadable()) {
			int ringtoneCount = 0, notificationCount = 0, alarmCount = 0;
			long ringtoneBytes = 0, notificationBytes = 0, alarmBytes = 0;

			ArrayList<String> keys = getAllKeys();
			// Count the ringtones, notifications, alarms, and compute the space they occupy
			// on the drive
			for(String key : keys) {
				String[] parts = key.split("%%");
				String fileName = cleanFileName(parts[1]);
				String fileType = parts[2];
				if(hasRingtone(fileName, fileType)) {
					File ringtone = getRingtone(fileName, fileType);
					if(ringtone.exists()) {
						ringtoneCount++;
						ringtoneBytes = ringtoneBytes + ringtone.length();
					}
				}
				if(hasNotification(fileName, fileType)) {
					File notification = getNotification(fileName, fileType);
					if(notification.exists()) {
						notificationCount++;
						notificationBytes = notificationBytes + notification.length();
					}
				}
				if(hasAlarm(fileName, fileType)) {
					File alarm = getAlarm(fileName, fileType);
					if(alarm.exists()) {
						alarmCount++;
						alarmBytes = alarmBytes + alarm.length();
					}
				}
			}

			// Convert the bytes to megabytes
			float ringtoneMegabytes = (float) ringtoneBytes / 1048576;
			float notificationMegabytes = (float) notificationBytes / 1048576;
			float alarmMegabytes = (float) alarmBytes / 1048576;
			float totalMegabytes = ringtoneMegabytes + notificationMegabytes + alarmMegabytes;

			// Display the dialog fragment with the storage details
			StringBuilder sb = new StringBuilder();
    		sb.append(String.format("Ringtones:\t%d (%.2f Mb)\n\n", ringtoneCount, ringtoneMegabytes));
    		sb.append(String.format("Notifications:\t%d (%.2f Mb)\n\n", notificationCount,
    				notificationMegabytes));
    		sb.append(String.format("Alarms:\t%d (%.2f Mb)\n\n", alarmCount, alarmMegabytes));
    		sb.append(String.format("Total Used Space:\t%.2f Mb", totalMegabytes));

    		DialogFragment newFragment = SimpleAlertDialogFragment.newInstance(
    				sb.toString(),
    				SoundManager.STORAGE_DETAILS_DIALOG);
    		newFragment.show(mContextProvider.getDefaultFragmentManager(), "storage_details_dialog");

		}else{
			Toast.makeText(mCtx, "Error: external storage device not mounted", Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * Checks to see if an audio file exists in the specified external public storage
	 * directory.
	 *
	 * @param fileName - The name of the file to check for
	 *
	 * @return - True if it exists and false otherwise
	 */
	public boolean hasAudio(String fileName, String fileType) {

		return (hasRingtone(fileName, fileType) || hasNotification(fileName, fileType) || hasAlarm(fileName, fileType));
	}

	/**
	 * Checks to see if an audio file exists in the external public storage
	 * directory specified by the context item selected.
	 *
	 * @param fileName - The name of the file to check for
	 * @param selectedTitle - The title of the context item selected
	 * @return - True if it exists and false otherwise
	 */
	public boolean hasAudio(String fileName, String fileType, String action) {

		if(action.equals(SET_RINGTONE) || action.equals(SET_CONTACT_RINGTONE))
			return hasRingtone(fileName, fileType);
		if(action.equals(SET_NOTIFICATION))
			return hasNotification(fileName, fileType);
		if(action.equals(SET_ALARM))
			return hasAlarm(fileName, fileType);
		if(action.equals(DELETE_UNUSED))
			return hasAudio(fileName, fileType);
		if(action.equals(SAVE_TO_SD) || action.equals(SHARE_SOUND))
			return isSavedToSd(fileName, fileType);
		return false;
	}

	/**
	 * Checks to see if a sound file exists in the public ringtones directory
	 *
	 * @param fileName - The name of the file as a String
	 *
	 * @return - True if the file exists, false otherwise
	 */
	public boolean hasRingtone(String fileName, String fileType) {

		// Check if the file exists.  If external storage is not currently mounted this will
		// think the picture doesn't exist.
	    File file = getRingtone(fileName, fileType);
	    return file.exists();
	}

	/**
	 * Checks to see if a sound file exists in the public notifications directory
	 *
	 * @param fileName - The name of the file as a String
	 *
	 * @return - True if the file exists, false otherwise
	 */
	public boolean hasNotification(String fileName, String fileType) {

		// Check if the file exists.  If external storage is not currently mounted this will
		// think the picture doesn't exist.
	    File file = getNotification(fileName, fileType);
	    return file.exists();
	}

	/**
	 * Checks to see if a sound file exists in the public alarms directory
	 *
	 * @param fileName - The name of the file as a String
	 *
	 * @return - True if the file exists, false otherwise
	 */
	public boolean hasAlarm(String fileName, String fileType) {

		// Check if the file exists.  If external storage is not currently mounted this will
		// think the picture doesn't exist.
	    File file = getAlarm(fileName, fileType);
	    return file.exists();
	}

	/**
	 * Checks to see if a sound file exists in the public music directory
	 *
	 * @param fileName - The name of the file as a String
	 *
	 * @return - True if the file exists, false otherwise
	 */
	public boolean isSavedToSd(String fileName, String fileType) {

		// Check if the file exists.  If external storage is not currently mounted this will
		// think the picture doesn't exist.
	    File file = getMusic(fileName, fileType);
	    return file.exists();
	}

	/**
	 * Gets the device's external storage directory for public ringtones
	 *
	 * @return - A File object that represents the path of the public ringtones directory
	 */
	private File getRingtoneDirectory() {
		return mExternalStorageManager.getRingtoneDirectory(mDirectoryName);
	}

	/**
	 * Gets the device's external storage directory for public notifications
	 *
	 * @return - A File object that represents the path of the public notifications directory
	 */
	private File getNotificationDirectory() {
		return mExternalStorageManager.getNotificationDirectory(mDirectoryName);
	}

	/**
	 * Gets the device's external storage directory for public alarms
	 *
	 * @return - A File object that represents the path of the public alarms directory
	 */
	private File getAlarmDirectory() {
		return mExternalStorageManager.getAlarmDirectory(mDirectoryName);
	}

	/**
	 * Gets the device's external storage directory for music
	 *
	 * @return - A File object that represents the path of the public music directory
	 */
	private File getMusicDirectory() {
		return mExternalStorageManager.getMusicDirectory(mDirectoryName);
	}
}
