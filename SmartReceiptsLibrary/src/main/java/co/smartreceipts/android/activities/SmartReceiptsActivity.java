package co.smartreceipts.android.activities;

import co.smartreceipts.android.model.Trip;
import wb.android.dialog.BetterDialogBuilder;
import wb.android.ui.UpNavigationSlidingPaneLayout;
import wb.android.util.AppRating;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import co.smartreceipts.android.BuildConfig;
import co.smartreceipts.android.R;
import co.smartreceipts.android.fragments.ReceiptsChartFragment;
import co.smartreceipts.android.fragments.ReceiptsFragment;
import co.smartreceipts.android.fragments.ReceiptsListFragment;
import co.smartreceipts.android.fragments.TripFragment;
import co.smartreceipts.android.model.Attachment;
import co.smartreceipts.android.persistence.Preferences;

public class SmartReceiptsActivity extends WBActivity implements Navigable, Attachable {

	// logging variables
	static final String TAG = "SmartReceiptsActivity";

	// Camera Request Extras
	public static final String STRING_DATA = "strData";
	public static final int DIR = 0;
	public static final int NAME = 1;

	// Action Send Extras
	public static final String ACTION_SEND_URI = "actionSendUri";

	// Receiver Settings
	protected static final String FILTER_ACTION = "co.smartreceipts.android";

	// AppRating (Use a combination of launches and a timer for the app rating
	// to ensure that we aren't prompting new users too soon
	private static final int LAUNCHES_UNTIL_PROMPT = 30;
	private static final int DAYS_UNTIL_PROMPT = 7;

	// Instace Vars
	private UpNavigationSlidingPaneLayout mSlidingPaneLayout;
	private boolean mIsDualPane;
	private Attachment mAttachment;

	// private ActionBarController mActionBarController;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (BuildConfig.DEBUG) {
			Log.d(TAG, "onCreate");
		}
		setContentView(R.layout.activity_main);

		mIsDualPane = getResources().getBoolean(R.bool.isTablet);
		if (!mIsDualPane) {
			mSlidingPaneLayout = (UpNavigationSlidingPaneLayout) findViewById(R.id.slidingpanelayout);
			mSlidingPaneLayout.setPanelSlideListener(this);
			mSlidingPaneLayout.openPane();
		}

		// mActionBarController = new ActionBarController(this, getSupportActionBar(),
		// getResources().getStringArray(R.array.actionbar_subtitles));

		// savedInstanceState is non-null when there is fragment state saved from previous configurations of this
		// activity
		// (e.g. when rotating the screen from portrait to landscape). In this case, the fragment will automatically be
		// re-added
		// to its container so we don't need to manually add it. Since the SlidingPaneLayout is controlled at the
		// activity
		// layer, however, as opposed to the fragment layer, it will need to be recreated regardless of what the current
		// fragment
		// state is
		if (savedInstanceState == null) {
			displayTripsLayout();
			AppRating.initialize(this).setMinimumLaunchesUntilPrompt(LAUNCHES_UNTIL_PROMPT).setMinimumDaysUntilPrompt(DAYS_UNTIL_PROMPT).hideIfAppCrashed(true).setPackageName(getPackageName()).showDialog(true).onLaunch();
		}

	}

	private void displayTripsLayout() {
		if (BuildConfig.DEBUG) {
			Log.d(TAG, "displayTripsLayout");
		}
		getSupportFragmentManager().beginTransaction().replace(R.id.content_list, getTripsFragment(), TripFragment.TAG).commit();
		if (getIntent().hasExtra(Trip.PARCEL_KEY)) { // We already have a feed we're looking to use
			final Trip trip = (Trip) getIntent().getParcelableExtra(Trip.PARCEL_KEY);
			viewReceiptsAsList(trip);
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		if (BuildConfig.DEBUG) {
			Log.d(TAG, "onStart");
		}
		if (!getSmartReceiptsApplication().getPersistenceManager().getStorageManager().isExternal()) {
			Toast.makeText(SmartReceiptsActivity.this, getSmartReceiptsApplication().getFlex().getString(this, R.string.SD_WARNING), Toast.LENGTH_LONG).show();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (BuildConfig.DEBUG) {
			Log.d(TAG, "onResume");
		}
		// Present dialog for viewing an attachment
		final Attachment attachment = new Attachment(getIntent(), getContentResolver());
		setAttachment(attachment);
		if (attachment.isValid() && attachment.isDirectlyAttachable()) {
			final Preferences preferences = getSmartReceiptsApplication().getPersistenceManager().getPreferences();
			final int stringId = attachment.isPDF() ? R.string.pdf : R.string.image;
			if (preferences.showActionSendHelpDialog()) {
				BetterDialogBuilder builder = new BetterDialogBuilder(this);
				builder.setTitle(getString(R.string.dialog_attachment_title, getString(stringId))).setMessage(getString(R.string.dialog_attachment_text, getString(stringId))).setPositiveButton(R.string.dialog_attachment_positive, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.cancel();
					}
				}).setNegativeButton(R.string.dialog_attachment_negative, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						preferences.setShowActionSendHelpDialog(false);
						preferences.commit();
						dialog.cancel();
					}
				}).show();
			}
			else {
				Toast.makeText(this, getString(R.string.dialog_attachment_text, getString(stringId)), Toast.LENGTH_LONG).show();
			}
		}
	}

	@Override
	protected void onDestroy() {
		getSmartReceiptsApplication().getPersistenceManager().getDatabase().onDestroy();
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if (mIsDualPane) {
			getMenuInflater().inflate(R.menu.menu_trip, menu);
		}
		else {
			if (mSlidingPaneLayout.isOpen()) {
				getMenuInflater().inflate(R.menu.menu_trip, menu);
			}
			else {
				getMenuInflater().inflate(R.menu.menu_receipts, menu);
			}
		}
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			if (!mIsDualPane) { // Home should never be enabled for tablets anyway but just in case
				mSlidingPaneLayout.openPane();
			}
			return true;
		}
		else if (item.getItemId() == R.id.menu_main_settings) {
			// getSmartReceiptsApplication().getSettings().showSettingsMenu(this);
			SRNavUtils.showSettings(this);
			return true;
		}
		else if (item.getItemId() == R.id.menu_main_export) {
			final Fragment tripsFragment = getSupportFragmentManager().findFragmentByTag(TripFragment.TAG);
			getSmartReceiptsApplication().getSettings().showExport(tripsFragment);
			return true;
		}/*
		 * else if (item.getItemId() == R.id.menu_main_settings) { final Intent intent = new Intent(this,
		 * SettingsActivity.class);; startActivity(intent); return true; }
		 */
		else {
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		// mActionBarController.refreshActionBar(); // Used to refresh the ActionBar view on rotation
	}

	@Override
	public void setTitle(CharSequence title) {
		super.setTitle(title);
		getSupportActionBar().setTitle(title);
		// mActionBarController.setTitle(title);
	}

	@Override
	public void setTitle(int titleId) {
		super.setTitle(titleId);
		getSupportActionBar().setTitle(titleId);
		// mActionBarController.setTitle(titleId);
	}

	@Override
	public String getTag() {
		return TAG;
	}

	@Override
	public void onPanelSlide(View panel, float slideOffset) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onPanelOpened(View panel) { // onTripsBeingViewed
		if (BuildConfig.DEBUG) {
			Log.d(TAG, "onPanelOpened");
		}
		if (!mIsDualPane) {
			// mActionBarController.displayStandardActionBar();
			enableUpNavigation(false);
			setTitle(getSmartReceiptsApplication().getFlex().getString(this, R.string.sr_app_name));
			getSupportActionBar().setSubtitle(null);
			Fragment fragment = getSupportFragmentManager().findFragmentByTag(ReceiptsFragment.TAG);
			if (fragment != null) {
				getSupportFragmentManager().beginTransaction().remove(fragment).commitAllowingStateLoss();
			}
			supportInvalidateOptionsMenu();
		}
	}

	@Override
	public void onPanelClosed(View panel) { // onReceiptsBeingViewed
		if (BuildConfig.DEBUG) {
			Log.d(TAG, "onPanelClosed");
		}
		if (!mIsDualPane) {
			// mActionBarController.displayNaviagationActionBar();
			enableUpNavigation(true);
			supportInvalidateOptionsMenu();
		}
	}

	@Override
	public void viewReceiptsAsList(Trip trip) {
		getSupportFragmentManager().beginTransaction().replace(R.id.content_details, getReceiptsListFragment(trip), ReceiptsListFragment.TAG).commitAllowingStateLoss();
		if (!mIsDualPane) {
			if (mSlidingPaneLayout.isOpen()) {
				enableUpNavigation(true);
				mSlidingPaneLayout.closePane();
			}
		}
	}

	@Override
	public void viewReceiptsAsChart(Trip trip) {
		getSupportFragmentManager().beginTransaction().replace(R.id.content_details, getReceiptsChartFragment(trip), ReceiptsChartFragment.TAG).commitAllowingStateLoss();
		if (!mIsDualPane) {
			if (mSlidingPaneLayout.isOpen()) {
				enableUpNavigation(true);
				mSlidingPaneLayout.closePane();
			}
		}
	}

	@Override
	public void viewTrips() {
		Fragment fragment = getSupportFragmentManager().findFragmentByTag(ReceiptsFragment.TAG);
		if (fragment != null) {
			getSupportFragmentManager().beginTransaction().remove(fragment).commit();
		}
		if (!mIsDualPane) {
			mSlidingPaneLayout.openPane();
		}
	}

	@Override
	public void onBackPressed() {
		if (!mIsDualPane) {
			if (!mSlidingPaneLayout.isOpen()) {
				mSlidingPaneLayout.openPane();
			}
			else {
				super.onBackPressed();
			}
		}
		else {
			super.onBackPressed();
		}
	}

	/**
	 * Returns the attachment that is generated via the main activity
	 * 
	 * @return
	 */
	@Override
	public Attachment getAttachment() {
		return mAttachment;
	}

	/**
	 * Stores the main attachment details for later
	 */
	@Override
	public void setAttachment(Attachment attachment) {
		mAttachment = attachment;
	}

	/**
	 * @return - an instance of our {@link TripFragment} class. Allows for subclass flexibility
	 */
	protected TripFragment getTripsFragment() {
		return TripFragment.newInstance();
	}

	/**
	 * @return - an instance of our {@link ReceiptsListFragment} class. Allows for subclass flexibility
	 */
	protected ReceiptsListFragment getReceiptsListFragment(Trip trip) {
		return ReceiptsFragment.newListInstance(trip);
	}

	/**
	 * @return - an instance of our {@link ReceiptsChartFragment} class. Allows for subclass flexibility
	 */
	protected ReceiptsChartFragment getReceiptsChartFragment(Trip trip) {
		return ReceiptsFragment.newChartInstance(trip);
	}

}