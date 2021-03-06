package co.smartreceipts.android.fragments;

import java.io.File;

import wb.android.google.camera.Util;
import wb.android.storage.StorageManager;
import wb.android.ui.PinchToZoomImageView;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import co.smartreceipts.android.BuildConfig;
import co.smartreceipts.android.R;
import co.smartreceipts.android.legacycamera.MyCameraActivity;
import co.smartreceipts.android.model.Receipt;
import co.smartreceipts.android.model.Trip;
import co.smartreceipts.android.persistence.SharedPreferenceDefinitions;
import co.smartreceipts.android.utils.Utils;
import co.smartreceipts.android.workers.ImageGalleryWorker;

public class ReceiptImageFragment extends WBFragment {

	public static final String TAG = "ReceiptImageFragment";

	// Activity Request ints
	private static final int RETAKE_PHOTO_CAMERA_REQUEST = 1;
	private static final int NATIVE_RETAKE_PHOTO_CAMERA_REQUEST = 2;

	// Settings
	private static final int FADE_IN_TIME = 75;

	// Preferences
	private static final String PREFERENCES = SharedPreferenceDefinitions.ReceiptImageFragment_Preferences.toString();
	private static final String PREFERENCE_RECEIPT_ID = "receiptId";
	private static final String PREFERENCE_RECEIPT_PATH = "receiptPath";
	private static final String PREFERENCE_RECEIPT_IMAGE_URI = "receiptImageUri";

	private Receipt mCurrentReceipt;
	private String mReceiptPath;
	private PinchToZoomImageView mImageView;
	private LinearLayout mFooter;
	private ProgressBar mProgress;
	private boolean mIsRotateOngoing;
	private Uri mImageUri;

	public static ReceiptImageFragment newInstance() {
		return new ReceiptImageFragment();
	}

	public static ReceiptImageFragment newInstance(Receipt currentReceipt, Trip trip) {
		ReceiptImageFragment fragment = new ReceiptImageFragment();
		Bundle args = new Bundle();
		args.putString(Trip.PARCEL_KEY, trip.getDirectoryPath());
		args.putParcelable(Receipt.PARCEL_KEY, currentReceipt);
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		mIsRotateOngoing = false;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View rootView = inflater.inflate(getLayoutId(), container, false);
		mImageView = (PinchToZoomImageView) rootView.findViewById(R.id.receiptimagefragment_imageview);
		mFooter = (LinearLayout) rootView.findViewById(R.id.footer);
		mProgress = (ProgressBar) rootView.findViewById(R.id.progress);
		LinearLayout rotateCCW = (LinearLayout) rootView.findViewById(R.id.rotate_ccw);
		LinearLayout retakePhoto = (LinearLayout) rootView.findViewById(R.id.retake_photo);
		LinearLayout rotateCW = (LinearLayout) rootView.findViewById(R.id.rotate_cw);
		rotateCCW.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				getWorkerManager().getLogger().logEvent(ReceiptImageFragment.this, "Rotate_CCW");
				rotate(-90);
			}
		});
		retakePhoto.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				getWorkerManager().getLogger().logEvent(ReceiptImageFragment.this, "Retake_Photo");
				if (getPersistenceManager().getPreferences().useNativeCamera()) {
					final Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
					mImageUri = Uri.fromFile(new File(mReceiptPath, mCurrentReceipt.getImage().getName()));
					intent.putExtra(MediaStore.EXTRA_OUTPUT, mImageUri);
					startActivityForResult(intent, NATIVE_RETAKE_PHOTO_CAMERA_REQUEST);
				}
				else {
					if (wb.android.google.camera.common.ApiHelper.NEW_SR_CAMERA_IS_SUPPORTED) {
						final Intent intent = new Intent(getActivity(), wb.android.google.camera.CameraActivity.class);
						mImageUri = Uri.fromFile(new File(mReceiptPath, mCurrentReceipt.getImage().getName()));
						intent.putExtra(MediaStore.EXTRA_OUTPUT, mImageUri);
						startActivityForResult(intent, RETAKE_PHOTO_CAMERA_REQUEST);
					}
					else {
						final Intent intent = new Intent(getActivity(), MyCameraActivity.class);
						String[] strings = new String[] { mReceiptPath, mCurrentReceipt.getImage().getName() };
						intent.putExtra(MyCameraActivity.STRING_DATA, strings);
						startActivityForResult(intent, RETAKE_PHOTO_CAMERA_REQUEST);
					}
				}
			}
		});
		rotateCW.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				getWorkerManager().getLogger().logEvent(ReceiptImageFragment.this, "Rotate_CW");
				rotate(90);
			}
		});
		return rootView;
	}

	public int getLayoutId() {
		return R.layout.receiptimagefragment;
	}

	@Override
	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	public void onPause() {
		if (mCurrentReceipt != null) {
			// Save persistent data state
			SharedPreferences preferences = getActivity().getSharedPreferences(PREFERENCES, 0);
			SharedPreferences.Editor editor = preferences.edit();
			editor.putInt(PREFERENCE_RECEIPT_ID, mCurrentReceipt.getId());
			editor.putString(PREFERENCE_RECEIPT_PATH, mReceiptPath);
			final String uriPath = (mImageUri == null) ? null : mImageUri.toString();
			editor.putString(PREFERENCE_RECEIPT_IMAGE_URI, uriPath);
			if (Utils.ApiHelper.hasGingerbread()) {
				editor.apply();
			}
			else {
				editor.commit();
			}
		}
		super.onPause();
	}

	@Override
	public void onResume() {
		super.onResume();
		if (mCurrentReceipt == null) {
			if (getArguments() != null) {
				mReceiptPath = getArguments().getString(Trip.PARCEL_KEY);
				Parcelable parcel = getArguments().getParcelable(Receipt.PARCEL_KEY);
				if (parcel == null || !(parcel instanceof Receipt)) {
					restoreData();
				}
				else {
					setCurrentReceipt((Receipt) parcel);
				}
			}
			else {
				restoreData();
			}
		}
	}

	private void setCurrentReceipt(Receipt receipt) {
		mCurrentReceipt = receipt;
		if (receipt == null || !mCurrentReceipt.hasImage()) {
			mProgress.setVisibility(View.GONE);
			Toast.makeText(getActivity(), getFlexString(R.string.IMG_OPEN_ERROR), Toast.LENGTH_SHORT).show();
		}
		else {
			getSupportActionBar().setTitle(mCurrentReceipt.getName());
			(new ImageLoader()).execute(mCurrentReceipt.getImage().getAbsolutePath());
		}
	}

	// Restore persistent data
	private void restoreData() {
		restoreDataHelper(getActivity().getSharedPreferences(PREFERENCES, 0));
	}

	private void restoreDataHelper(SharedPreferences preferences) {
		if (mReceiptPath == null) {
			final int receiptId = preferences.getInt(PREFERENCE_RECEIPT_ID, -1);
			setCurrentReceipt(getPersistenceManager().getDatabase().getReceiptByID(receiptId));
			mReceiptPath = preferences.getString(PREFERENCE_RECEIPT_PATH, "");
		}
		if (mImageUri == null) {
			final String uriPath = preferences.getString(PREFERENCE_RECEIPT_IMAGE_URI, null);
			if (uriPath != null) {
				mImageUri = Uri.parse(uriPath);
			}
		}
	}

	@Override
	public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		if (BuildConfig.DEBUG) {
			Log.d(TAG, "Result Code: " + resultCode);
		}
		if (resultCode == Activity.RESULT_OK) { // -1
			restoreDataHelper(getActivity().getSharedPreferences(PREFERENCES, 0)); // Added here since onActivityResult
																					// is called before onResume
			final ImageGalleryWorker worker = getWorkerManager().getImageGalleryWorker();
			File imgFile = worker.transformNativeCameraBitmap(mImageUri, data, null);
			if (imgFile == null) {
				Toast.makeText(getActivity(), getFlexString(R.string.IMG_SAVE_ERROR), Toast.LENGTH_SHORT).show();
				return;
			}
			switch (requestCode) {
				case NATIVE_RETAKE_PHOTO_CAMERA_REQUEST:
				case RETAKE_PHOTO_CAMERA_REQUEST:
					final Receipt retakeReceipt = getPersistenceManager().getDatabase().updateReceiptFile(mCurrentReceipt, imgFile);
					if (retakeReceipt != null) {
						mImageView.setImageBitmap(BitmapFactory.decodeFile(mCurrentReceipt.getImage().getAbsolutePath()));
					}
					else {
						Toast.makeText(getActivity(), getFlexString(R.string.DB_ERROR), Toast.LENGTH_SHORT).show();
						// Add overwrite rollback here
						return;
					}
					break;
				default:
					if (BuildConfig.DEBUG) {
						Log.e(TAG, "Unrecognized Request Code: " + requestCode);
					}
					super.onActivityResult(requestCode, resultCode, data);
					break;
			}
		}
		else if (resultCode == MyCameraActivity.PICTURE_SUCCESS) { // 51
			restoreDataHelper(getActivity().getSharedPreferences(PREFERENCES, 0)); // Added here since onActivityResult
																					// is called before onResume
			switch (requestCode) {
				case RETAKE_PHOTO_CAMERA_REQUEST:
					File retakeImg = new File(data.getStringExtra(MyCameraActivity.IMG_FILE));
					final Receipt retakeReceipt = getPersistenceManager().getDatabase().updateReceiptFile(mCurrentReceipt, retakeImg);
					if (retakeReceipt != null) {
						mImageView.setImageBitmap(BitmapFactory.decodeFile(mCurrentReceipt.getImage().getAbsolutePath()));
					}
					else {
						Toast.makeText(getActivity(), getFlexString(R.string.DB_ERROR), Toast.LENGTH_SHORT).show();
						// Add overwrite rollback here
						return;
					}
					break;
				default:
					Log.e(TAG, "Unrecognized Request Code: " + requestCode);
					super.onActivityResult(requestCode, resultCode, data);
					break;
			}
		}
		else {
			if (BuildConfig.DEBUG) {
				Log.e(TAG, "Unrecgonized Result Code: " + resultCode);
			}
			super.onActivityResult(requestCode, resultCode, data);
		}
	}

	private void rotate(int orientation) {
		if (mIsRotateOngoing) {
			return;
		}
		mIsRotateOngoing = true;
		mProgress.setVisibility(View.VISIBLE);
		(new ImageRotater(orientation, mCurrentReceipt.getImage())).execute(new Void[0]);
	}

	private void onRotateComplete(boolean success) {
		if (!success) {
			Toast.makeText(getActivity(), "Image Rotate Failed", Toast.LENGTH_SHORT).show();
		}
		mIsRotateOngoing = false;
		mProgress.setVisibility(View.GONE);
	}

	private class ImageLoader extends AsyncTask<String, Void, Bitmap> {

		@Override
		protected Bitmap doInBackground(String... args) {
			if (args != null && args.length > 0 && !TextUtils.isEmpty(args[0])) {
				return BitmapFactory.decodeFile(args[0]);
			}
			else {
				return null;
			}
		}

		@Override
		protected void onPostExecute(Bitmap result) {
			mProgress.setVisibility(View.GONE);
			if (result != null) {
				mImageView.setVisibility(View.VISIBLE);
				mFooter.setVisibility(View.VISIBLE);
				final TransitionDrawable td = new TransitionDrawable(new Drawable[] { new ColorDrawable(android.R.color.transparent), new BitmapDrawable(getResources(), result) });
				mImageView.setImageDrawable(td);
				td.startTransition(FADE_IN_TIME);
			}
			else {
				Toast.makeText(getActivity(), getFlexString(R.string.IMG_OPEN_ERROR), Toast.LENGTH_SHORT).show();
			}
		}

	}

	private class ImageRotater extends AsyncTask<Void, Void, Bitmap> {

		private final int mOrientation;
		private final File mImg;

		public ImageRotater(int orientation, File img) {
			mOrientation = orientation;
			mImg = img;
		}

		@Override
		protected Bitmap doInBackground(Void... params) {
			try {
				StorageManager storage = getPersistenceManager().getStorageManager();
				File root = mImg.getParentFile();
				String filename = mImg.getName();
				Bitmap bitmap = storage.getBitmap(root, filename);
				bitmap = Util.rotate(bitmap, mOrientation);
				storage.writeBitmap(root, bitmap, filename, CompressFormat.JPEG, 85);
				return bitmap;
			}
			catch (Exception e) {
				if (BuildConfig.DEBUG) {
					Log.e(TAG, e.toString());
				}
				return null;
			}
		}

		@Override
		protected void onPostExecute(Bitmap result) {
			if (result == null) {
				onRotateComplete(false);
			}
			else {
				mImageView.setImageBitmap(result);
				onRotateComplete(true);
			}
		}
	}

}