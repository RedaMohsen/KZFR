package creek.fm.doublea.kzfr.adapters;

import android.content.Context;
import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import creek.fm.doublea.kzfr.R;
import creek.fm.doublea.kzfr.activities.ProgramActivity;
import creek.fm.doublea.kzfr.models.Airtime;
import creek.fm.doublea.kzfr.models.Category;
import creek.fm.doublea.kzfr.models.Program;
import creek.fm.doublea.kzfr.utils.Utils;

/**
 * Created by Aaron on 7/6/2015.
 */
public class ProgramRecyclerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    abstract class ProgramBaseViewHolder extends RecyclerView.ViewHolder {

        public ProgramBaseViewHolder(View itemView) {
            super(itemView);
        }

        public abstract void bind(int position, Program program);
    }

    class AirtimeViewHolder extends ProgramBaseViewHolder {
        @Bind(R.id.program_airtimes)
        TextView mProgramAirtimes;

        static final int viewType = 0;

        public AirtimeViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }

        @Override
        public void bind(int position, Program program) {
            List<Airtime> airtimes = program.getAirtimes();
            if (!airtimes.isEmpty()) {
                mProgramAirtimes.setText(Utils.getFriendlyAirtimes(airtimes));
            } else {
                mProgramAirtimes.setText(
                        Utils.getFriendlyAirTime(program.getAirtime()));
            }
        }
    }

    class DescriptionViewHolder extends ProgramBaseViewHolder {
        @Bind(R.id.description_card_layout)
        TextView mProgramDescription;

        static final int viewType = 1;

        public DescriptionViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }

        @Override
        public void bind(int position, Program program) {
            String programDescription = program.getFullDescription();
            if (programDescription.isEmpty()) {
                programDescription = program.getShortDescription();
            }
            if (!programDescription.isEmpty()) {
                mProgramDescription.setText(Html.fromHtml(programDescription));
            }
        }
    }

    class HostsViewHolder extends ProgramBaseViewHolder {
        @Bind(R.id.card_view_host_name)
        TextView mProgramHostName;

        static final int viewType = 2;

        public HostsViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }

        @Override
        public void bind(int position, Program program) {
            mProgramHostName.setText(program.getHosts().get(position - 2).getDisplayName());
        }
    }

    class CategoriesViewHolder extends ProgramBaseViewHolder {
        @Bind(R.id.program_categories)
        TextView mProgramCategories;
        static final int viewType = 42;

        public CategoriesViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }

        @Override
        public void bind(int position, Program program) {
            List<Category> categoryList = program.getCategories();
            StringBuilder stringBuilder = new StringBuilder();
            for (Category category : categoryList) {
                stringBuilder.append(category.getTitle())
                        .append(" ");
            }
            mProgramCategories.setText(stringBuilder.toString());
        }
    }

    class UpNextViewHolder extends ProgramBaseViewHolder {
        @Bind(R.id.up_next_image_view)
        ImageView mUpNextImageView;
        static final int viewType = 43;

        public UpNextViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }

        @Override
        public void bind(int position, Program program) {

            mUpNextImageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent programIntent = new Intent(mContext, ProgramActivity.class);
                    programIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    programIntent.putExtra(ProgramActivity.PROGRAM_ID_KEY, Integer.valueOf(mNextProgramId));
                    mContext.startActivity(programIntent);
                }
            });
        }
    }

    private Context mContext;
    private LayoutInflater mInflater;
    private Program mProgramData;
    private int mNextProgramId = -1;
    private boolean mHasAirTimes;

    public ProgramRecyclerAdapter(Context context) {
        mContext = context;
        mInflater = LayoutInflater.from(mContext);
    }

    /**
     * This method is very specific to the layout desired. The desired layout is as follows.
     * If there are any airtimes then the airtimes view is first. The description view will be
     * second unless there is no airtimes view then it will be first. After the description there
     * will be 0 or more hosts views followed by 0 or more categories in a single card view. If the
     * program view has a next program id then the last view is an up next button. this should only
     * happen if the program view is the current program running and was reached by clicking on the
     * media player bar.
     *
     * @param position the current position of the view about to be created.
     * @return the view type
     */
    @Override
    public int getItemViewType(int position) {
        if (position == AirtimeViewHolder.viewType && mHasAirTimes) {
            return AirtimeViewHolder.viewType;
        } else if (position <= DescriptionViewHolder.viewType) {
            return DescriptionViewHolder.viewType;
        } else if (position > DescriptionViewHolder.viewType
                && position <= DescriptionViewHolder.viewType + getHostsNum()) {
            return position;
        } else if (position == getItemCount() - 1 && mNextProgramId != -1) {
            return UpNextViewHolder.viewType;
        } else {
            return CategoriesViewHolder.viewType;
        }
    }

    @Override
    public int getItemCount() {
        return 1 + getHasAirtimes() + getHostsNum() + getCategoriesNum() + hasNextProgram();
    }

    private int getHasAirtimes() {
        mHasAirTimes = (mProgramData != null && (!mProgramData.getAirtimes().isEmpty() || mProgramData.getAirtime() != null));
        return mHasAirTimes ? 1 : 0;
    }

    private int getHostsNum() {
        if (mProgramData != null) {
            return mProgramData.getHosts().size();
        }
        return 0;
    }

    private int getCategoriesNum() {
        return (mProgramData != null && mProgramData.getCategories().size() > 0) ? 1 : 0;
    }

    private int hasNextProgram() {
        return (mNextProgramId != -1) ? 1 : 0;
    }


    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == AirtimeViewHolder.viewType) {
            return new AirtimeViewHolder(mInflater.inflate(R.layout.airtime_card_layout, parent, false));
        } else if (viewType == DescriptionViewHolder.viewType) {
            return new DescriptionViewHolder(mInflater.inflate(R.layout.description_card_layout, parent, false));
        } else if (viewType > DescriptionViewHolder.viewType
                && viewType <= DescriptionViewHolder.viewType + getHostsNum()) {
            return new HostsViewHolder(mInflater.inflate(R.layout.hosts_card_layout, parent, false));
        } else if (viewType == UpNextViewHolder.viewType) {
            return new UpNextViewHolder(mInflater.inflate(R.layout.up_next_layout, parent, false));
        } else
            return new CategoriesViewHolder(mInflater.inflate(R.layout.categories_card_layout, parent, false));
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        ProgramBaseViewHolder baseViewHolder = (ProgramBaseViewHolder) holder;
        if (mProgramData != null) {
            baseViewHolder.bind(position, mProgramData);
        }
    }

    public void setProgramData(Program program) {
        mProgramData = program;
    }

    public void setNextProgramId(int nextProgramId) {
        mNextProgramId = nextProgramId;
    }

    public Program getProgramData() {
        return mProgramData;
    }

    public int getNextProgramId() {
        return mNextProgramId;
    }

    public boolean isEmpty() {
        return mProgramData == null;
    }
}