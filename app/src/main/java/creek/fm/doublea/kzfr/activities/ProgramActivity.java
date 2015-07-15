package creek.fm.doublea.kzfr.activities;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import creek.fm.doublea.kzfr.R;
import creek.fm.doublea.kzfr.adapters.ProgramRecyclerAdapter;
import creek.fm.doublea.kzfr.api.ApiClient;
import creek.fm.doublea.kzfr.api.KZFRRetrofitCallback;
import creek.fm.doublea.kzfr.models.Image;
import creek.fm.doublea.kzfr.models.Program;
import creek.fm.doublea.kzfr.utils.PaletteTransformation;
import retrofit.RetrofitError;
import retrofit.client.Response;

/**
 * Created by Aaron on 7/6/2015.
 */
public class ProgramActivity extends MainActivity implements View.OnClickListener {
    private static final String TAG = ProgramActivity.class.getSimpleName();
    public static final String PROGRAM_ID_KEY = TAG + ".program_id_key";
    public static final String PROGRAM_DATA_KEY = TAG + ".program_data_key";
    public static final String NEXT_PROGRAM_ID_KEY = TAG + ".next_program_data_key";

    private int mProgramId, mNextProgramId;
    private RecyclerView mHostRecyclerView;
    private ProgramRecyclerAdapter mProgramRecyclerAdapter;
    private ImageView mProgramImageView;
    private CollapsingToolbarLayout mCollapsingToolbarLayout;
    private Toolbar mToolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LayoutInflater inflater = (LayoutInflater) getSystemService((Context.LAYOUT_INFLATER_SERVICE));
        View content = inflater.inflate(R.layout.activity_program, mContentView, true);
        mHostRecyclerView = (RecyclerView) content.findViewById(R.id.host_recycler_view);
        mCollapsingToolbarLayout = (CollapsingToolbarLayout) content.findViewById(R.id.collapsing_toolbar);
        mProgramImageView = (ImageView) content.findViewById(R.id.program_collapsing_image_view);
        mToolbar = (Toolbar) content.findViewById(R.id.toolbar);
        setupRecyclerView();

        if (savedInstanceState != null) {
            Program savedProgram = (Program) savedInstanceState.getParcelable(PROGRAM_DATA_KEY);
            addDataToAdapter(savedProgram);
            mNextProgramId = savedInstanceState.getInt(NEXT_PROGRAM_ID_KEY);
            setNextProgramId(mNextProgramId);
        }
    }

    private void setNextProgramId(int nextProgramId) {
        if (mProgramRecyclerAdapter != null) {
            mProgramRecyclerAdapter.setNextProgramId(mNextProgramId);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        getDataFromIntent();
        if (mProgramId != -1) {
            showProgressBar(true);
            executeProgramApiCall(mProgramId);
        }
    }

    private void getDataFromIntent() {
        Intent currentIntent = getIntent();
        mProgramId = currentIntent.getIntExtra(PROGRAM_ID_KEY, -1);
        mNextProgramId = currentIntent.getIntExtra(NEXT_PROGRAM_ID_KEY, -1);
        setNextProgramId(mNextProgramId);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mProgramRecyclerAdapter != null && !mProgramRecyclerAdapter.isEmpty()) {
            outState.putParcelable(PROGRAM_DATA_KEY, mProgramRecyclerAdapter.getProgramData());
            outState.putInt(NEXT_PROGRAM_ID_KEY, mProgramRecyclerAdapter.getNextProgramId());
        }
    }

    private void executeProgramApiCall(int programId) {
        ApiClient.getKZFRApiClient(this).getProgram(programId, new KZFRRetrofitCallback<Program>() {

            @Override
            public void success(Program program, Response response) {
                super.success(program, response);
                addDataToAdapter(program);
            }

            @Override
            public void failure(RetrofitError error) {
                super.failure(error);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showProgressBar(false);
                    }
                });
            }
        });
    }

    private void addDataToAdapter(final Program program) {
        mProgramRecyclerAdapter.setProgramData(program);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProgramRecyclerAdapter.notifyDataSetChanged();
                if (program != null) {
                    setCollapsingToolbarLayoutTitle(program.getTitle());
                    setupProgramImage(program.getImage());
                }
                showProgressBar(false);
            }
        });

    }

    /**
     * This method is a solution to a bug in the collapsing toolbar layout that causes the title to
     * not update unless the text size has changed. The method simply changes the text size and
     * changes it back forcing the title to update.
     * http://stackoverflow.com/questions/30682548/collapsingtoolbarlayout-settitle-does-not-update-unless-collapsed/31309381#31309381
     *
     * @param title the new title
     */
    private void setCollapsingToolbarLayoutTitle(String title) {
        mCollapsingToolbarLayout.setTitle(title);
        mCollapsingToolbarLayout.setExpandedTitleTextAppearance(R.style.ExpandedAppBar);
        mCollapsingToolbarLayout.setCollapsedTitleTextAppearance(R.style.CollapsedAppBar);
        mCollapsingToolbarLayout.setExpandedTitleTextAppearance(R.style.ExpandedAppBarPlus1);
        mCollapsingToolbarLayout.setCollapsedTitleTextAppearance(R.style.CollapsedAppBarPlus1);
    }

    private void setupRecyclerView() {
        mHostRecyclerView.setHasFixedSize(true);
        mHostRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mProgramRecyclerAdapter = new ProgramRecyclerAdapter(this);
        mHostRecyclerView.setAdapter(mProgramRecyclerAdapter);
    }

    private void setupProgramImage(Image image) {
        if (image != null) {
            Picasso.with(this)
                    .load(image.getUrlLg())
                    .transform(PaletteTransformation.instance())
                    .placeholder(R.drawable.kzfr_logo)
                    .into(mProgramImageView, new Callback.EmptyCallback() {
                        @Override
                        public void onSuccess() {
                            Bitmap bitmap = ((BitmapDrawable) mProgramImageView.getDrawable()).getBitmap();
                            Palette palette = PaletteTransformation.getPalette(bitmap);
                            int darkVibrantColor = palette.getDarkVibrantColor(R.color.dark);
                            mCollapsingToolbarLayout.setContentScrimColor(darkVibrantColor);
                        }
                    });
            mProgramImageView.setOnClickListener(this);
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.program_collapsing_image_view) {
            if (mProgramImageView.getScaleType() == ImageView.ScaleType.CENTER_CROP) {
                mProgramImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            } else {
                mProgramImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            }
        }
    }
}
