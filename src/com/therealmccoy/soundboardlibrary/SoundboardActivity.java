package com.therealmccoy.soundboardlibrary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.mobclix.android.sdk.Mobclix;
import com.therealmccoy.soundboardlibrary.SimpleAlertDialogFragment.OnAlertDismissedListener;

public class SoundboardActivity extends FragmentActivity implements OnAlertDismissedListener,
	SoundboardContextProvider {

	// Interface for generating random sounds
	private interface RandomSounds {
		public int getCount();
		public void playRandomSound(int pos, SoundManager manager, Menu menu);
	}

	// Intent categories
	public static final String BASE_CATEGORY = "com.therealmccoy.soundboardlibrary.category.SOUNDBOARD";

	// Sound manager
	private SoundManager mSoundManager;
	private Random mRandomGenerator;
	private Menu mOptionsMenu;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {

    	super.onCreate(savedInstanceState);

    	// Initialize the Mobclix view immediately for faster ad serving.
    	Mobclix.onCreate(this);

    	// Initialize a SoundManager for this activity.
    	mSoundManager = new SoundManager(this);

    	// Initialize the random generator
    	mRandomGenerator = new Random();

    	// Set the layout of the activity.
    	setContentView(R.layout.main);

        Fragment soundboard;
        // Determine if this soundboard is categorized or not and build the appropriate fragment
        if(mSoundManager.isCategorized()) {
        	soundboard = CategoryTabsFragment.newInstance(mSoundManager, true);
        }else{
        	soundboard = SoundboardFragment.newInstance(mSoundManager.getCategories().get(0),
        			mSoundManager.getAllVisibleKeys());
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

    	switch(requestCode) {

    	case SoundManager.SET_CONTACT_REQUEST:
	    	if(resultCode == RESULT_OK) {
	    		mSoundManager.setContactRingtone(data.getData());
	    	}
	    	break;

    	case SoundManager.HIDDEN_SOUNDS:
    		if(resultCode == RESULT_OK) {
    			SoundboardFragment fragment = (SoundboardFragment) getSupportFragmentManager().findFragmentById(R.id.soundboard_frame);
    			fragment.getAdapter().addSounds(data.getStringArrayListExtra(SoundManager.EXTRA_SHOWN_SOUNDS));
    		}
    		break;

    	default:
    		super.onActivityResult(requestCode, resultCode, data);
    	}
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

    	super.onCreateOptionsMenu(menu);

    	// Inflate the menu from XML.
    	MenuInflater inflater = getMenuInflater();
    	inflater.inflate(R.menu.main, menu);
    	mOptionsMenu = menu;

    	return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

    	if (item.getItemId() == R.id.clear) {
			mSoundManager.clear();
			return true;
		} else if (item.getItemId() == R.id.storage_details) {
			mSoundManager.showStorageDetails();
			return true;
		} else if (item.getItemId() == R.id.hidden_sounds) {
			Intent intent = new Intent(this, HiddenSoundsActivity.class);
			startActivityForResult(intent, SoundManager.HIDDEN_SOUNDS);
			return true;
		} else if (item.getItemId() == R.id.explore_soundboards) {
			startActivity(new Intent(SoundboardActivity.this, ExploreActivity.class));
			return true;
		} else if (item.getItemId() == R.id.random_sound) {
			RandomSounds fragment = (RandomSounds) getSupportFragmentManager().findFragmentById(R.id.soundboard_frame);
			int randomPosition = mRandomGenerator.nextInt(fragment.getCount());
			mOptionsMenu.setGroupVisible(R.id.play_random_group, false);
			mOptionsMenu.setGroupEnabled(R.id.play_random_group, false);
			mOptionsMenu.setGroupVisible(R.id.stop_random_group, true);
			mOptionsMenu.setGroupEnabled(R.id.stop_random_group, true);
			fragment.playRandomSound(randomPosition, mSoundManager, mOptionsMenu);
			return true;
		} else if (item.getItemId() == R.id.stop_random_sound) {
			mOptionsMenu.setGroupVisible(R.id.play_random_group, true);
			mOptionsMenu.setGroupEnabled(R.id.play_random_group, true);
			mOptionsMenu.setGroupVisible(R.id.stop_random_group, false);
			mOptionsMenu.setGroupEnabled(R.id.stop_random_group, false);
			mSoundManager.stopManagingPlayer("random");
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
    }

	public void onDismiss(DialogInterface dialog, int requestCode) {

		dialog.dismiss();
	}

	/**
	 * This is the soundboard fragment.
	 */
	public static class SoundboardFragment extends Fragment implements RandomSounds {

		private GridView mGrid;
		private SoundButtonAdapter mAdapter;
		private String mCategory;

		/**
		 * Create a new instance of SoundboardFragment, initialized to show the sounds
		 * in 'category'.
		 */
		public static SoundboardFragment newInstance(String category, ArrayList<String> keys) {

			SoundboardFragment fragment = new SoundboardFragment();

			// Supply the category as an argument.
			Bundle args = new Bundle();
			args.putString("category", category);
			args.putStringArrayList("keys", keys);
			fragment.setArguments(args);

			return fragment;
		}

		/**
		 * When creating, retrieve this instance's category from its arguments or set the
		 * category as the default if there are no arguments.
		 */
		@Override
		public void onCreate(Bundle savedInstanceState) {

			super.onCreate(savedInstanceState);
			mCategory = (getArguments() != null) ? getArguments().getString("category") :
				SoundManager.DEFAULT_CATEGORY;
			ArrayList<String> keys = (getArguments() != null) ? getArguments().getStringArrayList("keys") :
				new ArrayList<String>();

			mAdapter = new SoundButtonAdapter(getActivity(), mCategory, keys);
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {

			View v = inflater.inflate(R.layout.soundboard_grid, container, false);
			mGrid = (GridView) v.findViewById(R.id.soundboard_grid);

			return v;
		}

		@Override
		public void onActivityCreated(Bundle savedInstanceState) {

			super.onActivityCreated(savedInstanceState);
			mGrid.setAdapter(mAdapter);
		}

		public SoundButtonAdapter getAdapter() {
			return mAdapter;
		}

		/**
		 * Gets the number of sound clips in this fragment.
		 *
		 * @return - SoundBoardAdapter with this fragments sound buttons
		 */
		public int getCount() {
			return mAdapter.getCount();
		}

		public void playRandomSound(int position, SoundManager manager, Menu menu) {

			ToggleButton button = (ToggleButton) mAdapter.getView(position, null, null);
			button.setChecked(true);
			manager.playSound(button, mCategory, menu);
		}

		public void playRandomSound(Activity context, int position, SoundManager manager, Menu menu) {
			if(mAdapter == null) {
				mCategory = (getArguments() != null) ? getArguments().getString("category") :
					SoundManager.DEFAULT_CATEGORY;
				ArrayList<String> keys = (getArguments() != null) ? getArguments().getStringArrayList("keys") :
					new ArrayList<String>();
				mAdapter = new SoundButtonAdapter(context, mCategory, keys);
			}
			playRandomSound(position, manager, menu);
		}
	}

	/**
	 * This is the category tabs fragment
	 */
	public static class CategoryTabsFragment extends Fragment implements RandomSounds {

		private TabHost mTabHost;
		private ViewPager mViewPager;
		private TabsAdapter mTabsAdapter;
		private ArrayList<String> mCategories;
		private HashMap<String, ArrayList<String>> mKeys;
		private Activity mCtx;

		/**
		 * Create a new instance of CategoryTabsFragment, initialized with tabs for
		 * each value in 'categories'
		 * @param categories - A String[] with all of the available categories
		 * @return - A new CategoryTabsFragment
		 */
		public static CategoryTabsFragment newInstance(SoundManager soundManager, boolean visible) {

			CategoryTabsFragment fragment = new CategoryTabsFragment();

			// Supply the list of categories and a map of keys as arguments.
			Bundle args = new Bundle();

			ArrayList<String> categories = soundManager.getCategories();
			args.putStringArrayList("categories", categories);

			for(String category : categories) {
				if(visible) {
					args.putStringArrayList(category, soundManager.getVisibleKeys(category));
				}else{
					args.putStringArrayList(category, soundManager.getHiddenKeys(category));
				}
			}

			fragment.setArguments(args);

			return fragment;
		}

		/**
		 * When creating, retrieve the list of categories from its arguments or set the
		 * list as only the default category.
		 */
		@Override
		public void onCreate(Bundle savedInstanceState) {

			super.onCreate(savedInstanceState);
			mKeys = new HashMap<String, ArrayList<String>>();
			if(getArguments() != null) {
				mCategories = getArguments().getStringArrayList("categories");
				for(String category : mCategories) {
					mKeys.put(category, getArguments().getStringArrayList(category));
				}
			}else{
				mCategories = new ArrayList<String>();
				mCategories.add(SoundManager.DEFAULT_CATEGORY);
			}

			mCtx = getActivity();
		}

		@Override
		public void onSaveInstanceState(Bundle outState) {

			super.onSaveInstanceState(outState);
			outState.putString("tab", mTabHost.getCurrentTabTag());
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

			View v = inflater.inflate(R.layout.tabbed_soundboard, container, false);

			mTabHost = (TabHost) v;
			mTabHost.setup();

			mViewPager = (ViewPager) v.findViewById(R.id.pager);

			mTabsAdapter = new TabsAdapter((FragmentActivity)getActivity(), mTabHost, mViewPager);

			for(String category : mCategories) {
				Bundle args = new Bundle();
				args.putString("category", category);
				args.putStringArrayList("keys", mKeys.get(category));
				View tabContent = inflater.inflate(R.layout.tab_content, null);
				((TextView)tabContent.findViewById(R.id.tab_title)).setText(WordUtils.capitalize(category.replaceFirst("category\\-(\\d\\-)+?", "")));
				mTabsAdapter.addTab(mTabHost.newTabSpec(category).setIndicator(tabContent),
						SoundboardFragment.class, args);
			}

			if(savedInstanceState != null) {
				mTabHost.setCurrentTabByTag(savedInstanceState.getString("tab"));
			}

			return v;
		}

		/**
		 * Gets the total count of sound clips in all tab fragments.
		 *
		 * @return - SoundBoardAdapter with this fragments sound buttons
		 */
		public int getCount() {
			return mTabsAdapter.getSoundCount();
		}

		public void playRandomSound(int position, SoundManager manager, Menu menu) {

			mTabsAdapter.playRandomSound(mCtx, position, manager, menu);
		}

		/**
	     * This is a helper class that implements the management of tabs and all
	     * details of connecting a ViewPager with associated TabHost. It listens to changes
	     * in tabs, and takes care of switch to the correct paged in the ViewPager whenever
	     * the selected tab changes.
	     */
		public static class TabsAdapter extends FragmentPagerAdapter
			implements TabHost.OnTabChangeListener, ViewPager.OnPageChangeListener {

			private final Context mContext;
	        private final TabHost mTabHost;
	        private final ViewPager mViewPager;
	        private final ArrayList<TabInfo> mTabs = new ArrayList<TabInfo>();

	        static final class TabInfo {

	        	private final String tag;
	            private final Class<?> clss;
	            private final Bundle args;

	            TabInfo(String _tag, Class<?> _class, Bundle _args) {
	                tag = _tag;
	                clss = _class;
	                args = _args;
	            }
	        }

	        static class DummyTabFactory implements TabHost.TabContentFactory {

	        	private final Context mContext;

	            public DummyTabFactory(Context context) {

	            	// Make sure the context implements the SoundboardContextProvider interface
	            	if(context instanceof SoundboardContextProvider)
	            		mContext = context;
	            	else
		    			throw new ClassCastException(context.toString()
		    					+ " must implement the SoundboardInfoProvider interface");
	            }

	            public View createTabContent(String tag) {

	            	LayoutInflater inflater = LayoutInflater.from(mContext);
	                View v = inflater.inflate(R.layout.soundboard_grid, null);
	                return v;
	            }
	        }

	        public TabsAdapter(FragmentActivity activity, TabHost tabHost, ViewPager pager) {

	        	super(activity.getSupportFragmentManager());

	        	// Make sure the activity implements the SoundboardContextProvider interface
	        	if(activity instanceof SoundboardContextProvider)
	        		mContext = activity;
	        	else
	    			throw new ClassCastException(activity.toString()
	    					+ " must implement the SoundboardInfoProvider interface");

	            mTabHost = tabHost;
	            mViewPager = pager;
	            mTabHost.setOnTabChangedListener(this);
	            mViewPager.setAdapter(this);
	            mViewPager.setOnPageChangeListener(this);
	        }

	        public void addTab(TabHost.TabSpec tabSpec, Class<?> clss, Bundle args) {

	        	tabSpec.setContent(new DummyTabFactory(mContext));
	            String tag = tabSpec.getTag();

	            TabInfo info = new TabInfo(tag, clss, args);
	            mTabs.add(info);
	            mTabHost.addTab(tabSpec);
	            notifyDataSetChanged();
	        }

	        public int getSoundCount() {
	        	int count = 0;
	        	for(int i=0; i<mTabs.size(); i++) {
					count += mTabs.get(i).args.getStringArrayList("keys").size();
				}
	        	return count;
	        }

	        public void playRandomSound(Activity context, int pos, SoundManager manager, Menu menu) {

	        	int token = pos;
	        	for(int i=0; i<mTabs.size(); i++) {
	        		ArrayList<String> keys = mTabs.get(i).args.getStringArrayList("keys");
	        		if(token > keys.size()-1) {
	        			token -= keys.size();
	        		}else{
	        			((SoundboardFragment)getItem(i)).playRandomSound(context, token, manager, menu);
	        			break;
	        		}
	        	}
	        }

	        @Override
	        public int getCount() {

	        	return mTabs.size();
	        }

	        @Override
	        public Fragment getItem(int position) {

	        	TabInfo info = mTabs.get(position);
	            return Fragment.instantiate((Context)mContext, info.clss.getName(), info.args);
	        }

	        public void onTabChanged(String tabId) {

	        	int position = mTabHost.getCurrentTab();
	            mViewPager.setCurrentItem(position);
	        }

	        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
	        }

	        public void onPageSelected(int position) {

	        	mTabHost.setCurrentTab(position);
	        }

	        public void onPageScrollStateChanged(int state) {
	        }
		}
	}
}