package srs.me.sidesheetdemo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btnShowSideSheet).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DemoSideSheetDialogFragment dialog = new DemoSideSheetDialogFragment();
                dialog.setCancelable(false);
                dialog.show(getSupportFragmentManager(), DemoSideSheetDialogFragment.class.getName());
            }
        });

    }
}
