package com.therealmccoy.soundboardlibrary;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.SupportActivity;

/**
 * A DialogFragment that displays an alert dialog with an ok button
 * @author yeluapyeroc
 *
 */
public class SimpleAlertDialogFragment extends DialogFragment {

	private OnAlertDismissedListener mListener;

	public interface OnAlertDismissedListener {
		public void onDismiss(DialogInterface dialog, int requestCode);
	}

	@Override
	public void onAttach(SupportActivity activity) {
		super.onAttach(activity);
		try {
			mListener = (OnAlertDismissedListener) activity;
		}catch(ClassCastException e) {
			throw new ClassCastException(activity.toString()
					+ " must implement OnAlertDismissedListener interface");
		}
	}

	/**
	 * Creates a new instance of this DialogFragment and sets its message
	 *
	 * @param message - An id for the messages resource variable
	 * @return
	 */
	public static SimpleAlertDialogFragment newInstance(String message, int requestCode) {
		SimpleAlertDialogFragment frag = new SimpleAlertDialogFragment();
		Bundle args = new Bundle();
		args.putString("message", message);
		args.putInt("request_code", requestCode);
		frag.setArguments(args);
		return frag;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		String message = getArguments().getString("message");
		final int request_code = getArguments().getInt("request_code");

		return new AlertDialog.Builder(getActivity())
				.setMessage(message)
				.setCancelable(false)
				.setPositiveButton("Ok", new DialogInterface.OnClickListener() {

	    			public void onClick(DialogInterface dialog, int item) {
	    				mListener.onDismiss(dialog, request_code);
	    			}
	    		})
	    		.create();
	}
}