package co.smartreceipts.android.fragments.preferences;

import co.smartreceipts.android.R;
import co.smartreceipts.android.model.Columns;

public class CSVColumnsListFragment extends ColumnsListFragment {

	public static String TAG = "CSVColumnsListFragment";

	public static ColumnsListFragment newInstance() {
		return new CSVColumnsListFragment();
	}

	@Override
	public void onResume() {
		super.onResume();
		getSupportActionBar().setTitle(R.string.menu_main_csv);
	}

	@Override
	public Columns getColumns() {
		return getPersistenceManager().getDatabase().getCSVColumns();
	}

	@Override
	public void addColumn() {
		getPersistenceManager().getDatabase().insertCSVColumn();
	}

	@Override
	public void deleteLastColumn() {
		getPersistenceManager().getDatabase().deleteCSVColumn();
	}

	@Override
	public void updateColumn(int arrayListIndex, int optionIndex) {
		getPersistenceManager().getDatabase().updateCSVColumn(arrayListIndex, optionIndex);
	}

}
