package co.smartreceipts.android.activities;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.File;

import co.smartreceipts.android.fragments.ReceiptCreateEditFragment;
import co.smartreceipts.android.fragments.ReceiptsListFragment;
import co.smartreceipts.android.fragments.ReportInfoFragment;
import co.smartreceipts.android.fragments.TripFragment;
import co.smartreceipts.android.model.Receipt;
import co.smartreceipts.android.model.Trip;

public interface FragmentProvider {

    /**
     * Builds a {@link co.smartreceipts.android.fragments.TripFragment} instance
     *
     * @return a new trip fragment
     */
    @NonNull
    TripFragment newTripFragmentInstance();

    /**
     * Builds a {@link co.smartreceipts.android.fragments.ReportInfoFragment} instance
     *
     * @return a new report info fragment
     */
    @NonNull
    ReportInfoFragment newReportInfoFragment(@NonNull Trip trip);

    /**
     * Creates a {@link co.smartreceipts.android.fragments.ReceiptCreateEditFragment} for a new receipt
     *
     * @param trip the parent trip of this receipt
     * @param file the file associated with this receipt or null if we do not have one
     * @return the new instance of this fragment
     */
    @NonNull
    ReceiptCreateEditFragment newCreateReceiptFragment(@NonNull Trip trip, @Nullable File file);

    /**
     * Creates a {@link co.smartreceipts.android.fragments.ReceiptCreateEditFragment} to edit an existing receipt
     *
     * @param trip the parent trip of this receipt
     * @param receiptToEdit the receipt to edit
     * @return the new instance of this fragment
     */
    @NonNull
    ReceiptCreateEditFragment newEditReceiptFragment(@NonNull Trip trip, @NonNull Receipt receiptToEdit);
}