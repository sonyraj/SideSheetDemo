package srs.me.sidesheetdemo;

import android.app.Dialog;
import android.os.Bundle;
import android.support.v7.app.AppCompatDialogFragment;

/**
 * @author Sony Raj on 07-07-2017.
 */

public class SideSheetDialogFragment extends AppCompatDialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new SideSheetDialog(getContext(), getTheme());
    }
}
