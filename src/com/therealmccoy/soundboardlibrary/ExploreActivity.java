package com.therealmccoy.soundboardlibrary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActionBar.OnNavigationListener;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ExploreActivity extends FragmentActivity implements OnNavigationListener {

	// An OnTouchListener that disables scrolling
	public static class DisabledScroll implements OnTouchListener {

		public DisabledScroll() {}

		public boolean onTouch(View v, MotionEvent event) {
			if(event.getAction() == MotionEvent.ACTION_MOVE) {
				return true;
			}
			return false;
		}
	}

	private HashMap<String, ArrayList<ResolveInfo>> mPackages;
	private ArrayList<String> mLabels;
	private ArrayList<String> mGroups;

	// Current soundboard info
	private String mGroupKey;
	private String mGroupName;
	private String mPackageName;

	// Adapters
	private ArrayAdapter<String> mSpinnerAdapter;

	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.explorer);

		// PackageManager for this context
		PackageManager pm = getPackageManager();

		// Initialize the current soundboard's group info
		mGroupKey = getResources().getString(R.string.group_key);
		mGroupName = getResources().getString(R.string.group_name);
		mPackageName = getPackageName();

		// Initialize group and package lists
		mGroups = new ArrayList<String>();
		mLabels = new ArrayList<String>();
		mPackages = new HashMap<String, ArrayList<ResolveInfo>>();

		// Get a list of all installed soundboards by DevSpaceNine.
		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.addCategory(getResources().getString(R.string.base_category));
		List<ResolveInfo> all_soundboards = pm.queryIntentActivities(intent, 0);

		// Iterate over the installed soundboards to build the map of
		// sound groups.
		for(ResolveInfo info : all_soundboards) {
			if(info.activityInfo != null) {
				Resources res;
				try {
					res = pm.getResourcesForApplication(info.activityInfo.packageName);
				} catch (NameNotFoundException e) {
					Log.e("DSN Debug", "Error getting application info: " + e.getMessage(), e);
					continue;
				}
				int groupKeyResId = res.getIdentifier("group_key", "string", info.activityInfo.packageName);
				int groupLabelResId = res.getIdentifier("group_name", "string", info.activityInfo.packageName);
				String groupKey = res.getString(groupKeyResId);
				String groupLabel = res.getString(groupLabelResId);
				if(!groupKey.equals(Intent.CATEGORY_DEFAULT) && !groupKey.equals(SoundboardActivity.BASE_CATEGORY)) {
					if(mGroups.contains(groupKey)) {
						if(!info.activityInfo.packageName.equals(mPackageName)) {
							mPackages.get(groupKey).add(info);
						}
					}else{
						mGroups.add(groupKey);
						mLabels.add(groupLabel);
						ArrayList<ResolveInfo> new_group = new ArrayList<ResolveInfo>();
						if(!info.activityInfo.packageName.equals(mPackageName)) {
							new_group.add(info);
						}
						mPackages.put(groupKey, new_group);
					}
				}
			}
		}

		// Create a SpinnerAdapter for the action bar
		mSpinnerAdapter = new ArrayAdapter<String>(this, R.layout.abs__simple_spinner_item, android.R.id.text1, mLabels);
		mSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);

		getSupportActionBar().setListNavigationCallbacks(mSpinnerAdapter, this);

		/*
		SoundGroupFragment fragment = SoundGroupFragment.newInstance(mGroupKey, mGroupName, mPackages.get(mGroupKey));

		getSupportFragmentManager().beginTransaction()
			.replace(R.id.sound_group_frame, fragment).commit();*/
	}

	public boolean onNavigationItemSelected(int position, long itemId) {

		String key = mGroups.get(position);
		String label = mLabels.get(position);
		ArrayList<ResolveInfo> apps = mPackages.get(key);
		SoundGroupFragment fragment = SoundGroupFragment.newInstance(key, label, apps);
		getSupportFragmentManager().beginTransaction().replace(R.id.sound_group_frame, fragment, key).commit();
		return true;
	}

	@Override
    public boolean onCreateOptionsMenu(Menu menu) {

		// Inflate the menu from an XML resource
    	MenuInflater inflater = getMenuInflater();
    	inflater.inflate(R.menu.explore, menu);

    	return true;
    }

	@Override
    public boolean onOptionsItemSelected(MenuItem item) {

		if (item.getItemId() == android.R.id.home) {
    		Intent intent = new Intent(this, SoundboardActivity.class);
    		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    		startActivity(intent);
    		return true;
    	} else if (item.getItemId() == R.id.download) {
    		Intent intent = new Intent(Intent.ACTION_VIEW);
    		intent.setData(Uri.parse("market://search?q=sound therealmccoy"));
    		startActivity(intent);
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
    }

	public static class SoundGroupFragment extends Fragment {

		private String mGroupKey;
		private String mGroupLabel;
		private SoundGroupAdapter mAdapter;

		public static SoundGroupFragment newInstance(String key, String label, ArrayList<ResolveInfo> apps) {

			SoundGroupFragment fragment = new SoundGroupFragment();

			Bundle args = new Bundle();
			args.putString("groupKey", key);
			args.putString("groupLabel", label);
			args.putParcelableArrayList("apps", apps);
			fragment.setArguments(args);

			return fragment;
		}

		public String getShownIndex() {
			return getArguments().getString("groupKey");
		}

		@Override
		public void onCreate(Bundle savedInstanceState) {

			super.onCreate(savedInstanceState);

			Bundle args = getArguments();
			if(args != null) {
				// Initialize group info and adapter with arguments if available.
				mGroupKey = args.getString("groupKey");
				mGroupLabel = args.getString("groupLabel");
				ArrayList<ResolveInfo> apps = args.getParcelableArrayList("apps");
				mAdapter = new SoundGroupAdapter(getActivity(), apps);
			}else{
				// No arguments. Set group info to current context's info
				// and initialize an empty adapter.
				mGroupKey = getActivity().getResources().getString(R.string.group_key);
				mGroupLabel = getActivity().getResources().getString(R.string.group_name);
				mAdapter = new SoundGroupAdapter(getActivity(), new ArrayList<ResolveInfo>());
			}
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

			View v = inflater.inflate(R.layout.sound_group, container, false);

			// Set the adapter for the gridview
			GridView grid = (GridView) v.findViewById(R.id.group_grid);
			grid.setOnTouchListener(new DisabledScroll());
			grid.setAdapter(mAdapter);

			// Set the text for the group name
			TextView header = (TextView) v.findViewById(R.id.group_header);
			header.setText(mGroupLabel);

			return v;
		}

		// Custom adapter for populating a GridView of Soundboard apps
		public static class SoundGroupAdapter extends BaseAdapter implements OnClickListener {

			private Context mCtx;
			private final LayoutInflater mInflater;
			private ArrayList<ResolveInfo> mSoundboards;

			public SoundGroupAdapter(Context context, ArrayList<ResolveInfo> soundboards) {
				mCtx = context;
				mInflater = LayoutInflater.from(context);
				mSoundboards = soundboards;
			}

			public int getCount() {
				return mSoundboards.size();
			}

			public Object getItem(int position) {
				return mSoundboards.get(position);
			}

			public long getItemId(int position) {
				return 0;
			}

			// create a new Button for each soundboard referenced by the Adapter
			public View getView(int position, View convertView, ViewGroup parent) {

				LinearLayout ll;

				// if its not recycled, inflate a new button layout
				if(convertView == null) {
					ll = (LinearLayout) mInflater.inflate(R.layout.application_button, parent, false);
				}else{ // recycle the convertView if it's not null
					ll = (LinearLayout) convertView;
				}

				// Package manager
				PackageManager pm = mCtx.getPackageManager();

				// ResolveInfo for this position
				ResolveInfo info = (ResolveInfo) getItem(position);

				// Set the image drawable for the button as the ResolveInfo's icon and
				// set the onClickListener for the button.
				ImageButton button = (ImageButton) ll.findViewById(R.id.soundboard_icon);
				button.setImageDrawable(info.loadIcon(pm));
				button.setContentDescription(info.activityInfo.loadLabel(pm));
				button.setTag(R.id.tag_position, position);
				button.setOnClickListener(this);

				// Set the label for the button as the ResolveInfo's label
				TextView label = (TextView) ll.findViewById(R.id.soundboard_label);
				label.setText(info.loadLabel(pm));

				// Return the final layout
				return ll;
			}

			public void onClick(View v) {

				Intent intent = new Intent();
				int position = Integer.parseInt(v.getTag(R.id.tag_position).toString());
				ActivityInfo info = ((ResolveInfo)getItem(position)).activityInfo;
				Log.d("DSN Debug", "Package: "+info.packageName+" Activity: "+info.name);
				intent.setClassName(info.packageName, info.name);
				mCtx.startActivity(intent);
			}
		}
	}
}